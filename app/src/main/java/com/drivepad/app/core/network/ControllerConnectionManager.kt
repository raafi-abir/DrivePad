package com.drivepad.app.core.network

import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.data.repository.ControllerInputRepository
import com.drivepad.app.domain.model.ConnectionDiagnosticsState
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.NetworkRouteStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

interface MonotonicClock {
    fun nanoTime(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nanoTime(): Long = System.nanoTime()
}

data class ScheduledStep(
    val nextDeadlineNs: Long,
    val skippedIntervals: Long
)

object MonotonicSchedulerMath {
    fun intervalNanosForHz(targetHz: Int): Long {
        val safeHz = targetHz.coerceIn(20, 500)
        return 1_000_000_000L / safeHz
    }

    /**
     * Advances a monotonic transmit deadline without bursting stale packets when scheduling
     * stalls exceed one or more intervals.
     */
    fun computeNextDeadlineNanos(
        nowNs: Long,
        currentDeadlineNs: Long,
        intervalNs: Long
    ): ScheduledStep {
        val safeInterval = intervalNs.coerceAtLeast(1_000_000L)
        val nextBase = currentDeadlineNs + safeInterval
        return if (nowNs >= nextBase) {
            val skippedCount = ((nowNs - nextBase) / safeInterval) + 1L
            ScheduledStep(
                nextDeadlineNs = nextBase + skippedCount * safeInterval,
                skippedIntervals = skippedCount
            )
        } else {
            ScheduledStep(
                nextDeadlineNs = nextBase,
                skippedIntervals = 0L
            )
        }
    }
}

interface ControllerConnectionManager {
    val diagnosticsState: StateFlow<ConnectionDiagnosticsState>

    fun connect()
    fun disconnect()
    fun sendPingNow()
    fun refreshNetworkRoute()
}

class UdpControllerConnectionManager(
    private val preferencesRepository: UserPreferencesRepository,
    private val inputRepository: ControllerInputRepository,
    private val routeInspector: NetworkRouteInspector,
    private val transportFactory: () -> UdpTransport = { DatagramSocketUdpTransport() },
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val handshakeTimeoutMs: Long = 2_500L,
    private val helloRetryIntervalMs: Long = 250L,
    private val pingIntervalMs: Long = 250L,
    private val watchdogTimeoutMs: Long = 2_000L,
    private val receivePollTimeoutMs: Int = 100
) : ControllerConnectionManager {

    private val _diagnosticsState = MutableStateFlow(ConnectionDiagnosticsState())
    override val diagnosticsState: StateFlow<ConnectionDiagnosticsState> = _diagnosticsState.asStateFlow()

    @Volatile
    private var activeConfig = ControllerConfig()

    @Volatile
    private var activeTransport: UdpTransport? = null

    private val sessionLock = Any()
    private val controlLock = Any()
    private var rxJob: Job? = null
    private var txJob: Job? = null
    private var handshakeJob: Job? = null
    private var watchdogJob: Job? = null

    private var txSequence: Long = 1L
    private var expectedReceiverSeq: Long? = null
    private var lastSuccessfulRxNs: Long = 0L
    private var lastRttMs: Double? = null
    private var jitterAccumulatorMs: Double = 0.0
    private var smoothedRttMs: Double? = null
    private var rxWindowStartNs: Long = 0L
    private var txWindowCount: Long = 0L

    private val pendingPings = ConcurrentHashMap<Long, Long>()
    private val txBuffer = ByteArray(DrivePadPacket.PACKET_SIZE_BYTES)
    private val controlBuffer = ByteArray(DrivePadPacket.PACKET_SIZE_BYTES)

    init {
        scope.launch(ioDispatcher) {
            routeInspector.startMonitoring()
        }

        scope.launch(ioDispatcher) {
            preferencesRepository.controllerConfigFlow.collect { config ->
                val previous = activeConfig
                activeConfig = config
                val route = routeInspector.evaluateRoute(config.targetHostIp, config.udpPort)

                val endpointChanged = previous.targetHostIp != config.targetHostIp ||
                    previous.udpPort != config.udpPort

                _diagnosticsState.update { current ->
                    current.copy(
                        targetHostIp = config.targetHostIp,
                        targetPort = config.udpPort,
                        targetUpdateHz = config.targetUpdateFrequencyHz,
                        networkRoute = route
                    )
                }

                if (endpointChanged &&
                    (_diagnosticsState.value.status == ConnectionStatus.CONNECTED ||
                        _diagnosticsState.value.status == ConnectionStatus.CONNECTING)
                ) {
                    disconnectInternal(reasonError = "Receiver endpoint updated; reconnect required")
                } else {
                    syncIdleStatusWithRoute(route)
                }
            }
        }

        scope.launch {
            routeInspector.routeStatus.collect { route ->
                _diagnosticsState.update { it.copy(networkRoute = route) }
                syncIdleStatusWithRoute(route)
            }
        }
    }

    override fun refreshNetworkRoute() {
        val route = routeInspector.evaluateRoute(activeConfig.targetHostIp, activeConfig.udpPort)
        _diagnosticsState.update { it.copy(networkRoute = route) }
        syncIdleStatusWithRoute(route)
    }

    private fun syncIdleStatusWithRoute(route: NetworkRouteStatus) {
        _diagnosticsState.update { current ->
            if (current.status == ConnectionStatus.CONNECTED ||
                current.status == ConnectionStatus.CONNECTING ||
                current.status == ConnectionStatus.WATCHDOG_TIMEOUT
            ) {
                current
            } else {
                val idleStatus = if (route.hasLocalRoute && !route.onlyCellularActive) {
                    ConnectionStatus.TETHER_DETECTED
                } else {
                    ConnectionStatus.DISCONNECTED
                }
                current.copy(status = idleStatus)
            }
        }
    }

    override fun connect() {
        val config = activeConfig
        val route = routeInspector.evaluateRoute(config.targetHostIp, config.udpPort)

        if (!route.isTargetIpValid || !route.isTargetPortValid) {
            _diagnosticsState.update {
                it.copy(
                    status = ConnectionStatus.DISCONNECTED,
                    lastError = route.warningMessage ?: "Invalid receiver IPv4 or UDP port",
                    networkRoute = route
                )
            }
            return
        }

        if (route.onlyCellularActive) {
            _diagnosticsState.update {
                it.copy(
                    status = ConnectionStatus.DISCONNECTED,
                    lastError = route.warningMessage
                        ?: "Mobile data only — enable USB tethering (cellular fallback blocked).",
                    networkRoute = route
                )
            }
            return
        }

        synchronized(sessionLock) {
            cancelSessionJobsLocked()
            closeTransportLocked()
            resetSessionCountersLocked()
        }

        _diagnosticsState.update {
            it.copy(
                status = ConnectionStatus.CONNECTING,
                targetHostIp = config.targetHostIp,
                targetPort = config.udpPort,
                targetUpdateHz = config.targetUpdateFrequencyHz,
                actualUpdateHz = 0.0,
                packetsSent = 0L,
                packetsReceived = 0L,
                sequenceGapsDetected = 0L,
                outOfOrderPackets = 0L,
                estimatedPacketLossPercent = 0.0,
                rttMs = null,
                smoothedRttMs = null,
                jitterMs = null,
                lastSuccessfulCommElapsedMs = null,
                lastAckSequence = null,
                lastError = null,
                isTransmitting = false,
                networkRoute = route
            )
        }

        scope.launch {
            val transport = transportFactory()
            val openResult = withContext(ioDispatcher) {
                transport.open(config.targetHostIp, config.udpPort)
            }

            if (openResult.isFailure) {
                val err = openResult.exceptionOrNull()?.message ?: "Failed to open UDP socket"
                synchronized(sessionLock) {
                    transport.close()
                }
                _diagnosticsState.update {
                    it.copy(
                        status = if (route.hasLocalRoute) ConnectionStatus.TETHER_DETECTED else ConnectionStatus.DISCONNECTED,
                        lastError = err,
                        isTransmitting = false
                    )
                }
                return@launch
            }

            synchronized(sessionLock) {
                if (_diagnosticsState.value.status != ConnectionStatus.CONNECTING) {
                    transport.close()
                    return@launch
                }
                activeTransport = transport
                lastSuccessfulRxNs = clock.nanoTime()
                rxJob = launchReceiveLoop(transport, config.targetHostIp, config.udpPort)
                handshakeJob = launchHandshakeLoop(transport)
            }
        }
    }

    override fun disconnect() {
        disconnectInternal(reasonError = null)
    }

    private fun disconnectInternal(reasonError: String?) {
        val transportToClose: UdpTransport?
        synchronized(sessionLock) {
            cancelSessionJobsLocked()
            transportToClose = activeTransport
            activeTransport = null
            pendingPings.clear()
        }
        inputRepository.safetyResetAllInputs()

        if (transportToClose != null) {
            scope.launch(ioDispatcher) {
                try {
                    synchronized(controlLock) {
                        val seq = nextTxSequence()
                        val pkt = DrivePadPacket.controlPacket(
                            messageType = PacketMessageType.DISCONNECT,
                            sequenceNumber = seq,
                            monotonicTimestampNanos = clock.nanoTime()
                        )
                        val len = PacketCodec.encodeInto(pkt, controlBuffer)
                        transportToClose.sendBlocking(controlBuffer, len)
                    }
                } catch (_: Exception) {
                    // Best-effort disconnect notification
                } finally {
                    transportToClose.close()
                }
            }
        }

        val route = routeInspector.evaluateRoute(activeConfig.targetHostIp, activeConfig.udpPort)
        val idleStatus = if (route.hasLocalRoute && !route.onlyCellularActive) {
            ConnectionStatus.TETHER_DETECTED
        } else {
            ConnectionStatus.DISCONNECTED
        }

        _diagnosticsState.update {
            it.copy(
                status = idleStatus,
                actualUpdateHz = 0.0,
                isTransmitting = false,
                lastError = reasonError,
                networkRoute = route
            )
        }
    }

    override fun sendPingNow() {
        val transport = activeTransport ?: return
        if (!transport.isOpen) return
        scope.launch(ioDispatcher) {
            sendPingPacket(transport)
        }
    }

    private fun launchHandshakeLoop(transport: UdpTransport): Job {
        return scope.launch(ioDispatcher) {
            val startNs = clock.nanoTime()
            val timeoutNs = handshakeTimeoutMs * 1_000_000L

            while (isActive && _diagnosticsState.value.status == ConnectionStatus.CONNECTING) {
                val nowNs = clock.nanoTime()
                if (nowNs - startNs >= timeoutNs) {
                    triggerWatchdogOrHandshakeFailure(
                        message = "HELLO handshake timed out after ${handshakeTimeoutMs}ms (${activeConfig.targetHostIp}:${activeConfig.udpPort})"
                    )
                    break
                }

                val sentOk = sendControlPacketBlocking(transport, PacketMessageType.HELLO, nowNs)
                if (!sentOk) {
                    break
                }
                delay(helloRetryIntervalMs)
            }
        }
    }

    private fun launchReceiveLoop(
        transport: UdpTransport,
        expectedHost: String,
        expectedPort: Int
    ): Job {
        return scope.launch(ioDispatcher) {
            val rxBuffer = ByteArray(256)
            while (isActive && transport.isOpen) {
                when (val result = transport.receiveBlocking(rxBuffer, receivePollTimeoutMs)) {
                    is UdpReceiveResult.Packet -> {
                        val isExpectedSender =
                            (result.senderHost == expectedHost || expectedHost == "127.0.0.1" && result.senderHost == "localhost") &&
                                result.senderPort == expectedPort
                        if (!isExpectedSender) {
                            continue
                        }
                        handleIncomingPacket(rxBuffer, result.length)
                    }
                    UdpReceiveResult.Timeout -> Unit
                    UdpReceiveResult.Closed -> break
                    is UdpReceiveResult.Error -> {
                        if (_diagnosticsState.value.status == ConnectionStatus.CONNECTED ||
                            _diagnosticsState.value.status == ConnectionStatus.CONNECTING
                        ) {
                            triggerWatchdogOrHandshakeFailure(
                                message = "UDP receive error: ${result.message}"
                            )
                        }
                        break
                    }
                }
            }
        }
    }

    private fun handleIncomingPacket(buffer: ByteArray, length: Int) {
        when (val decoded = PacketCodec.decode(buffer, length)) {
            is PacketDecodeResult.Invalid -> {
                _diagnosticsState.update { it.copy(lastError = "Malformed RX packet ignored: ${decoded.reason}") }
            }
            is PacketDecodeResult.Valid -> {
                val packet = decoded.packet
                val nowNs = clock.nanoTime()
                lastSuccessfulRxNs = nowNs

                var rttSampleMs: Double? = null
                if (packet.messageType == PacketMessageType.PONG || packet.messageType == PacketMessageType.HELLO_ACK) {
                    val sentAtNs = pendingPings.remove(packet.monotonicTimestampNanos)
                    if (sentAtNs != null && nowNs >= sentAtNs) {
                        val computedRtt = (nowNs - sentAtNs) / 1_000_000.0
                        if (computedRtt in 0.0..10_000.0) {
                            rttSampleMs = computedRtt
                            updateRttAndJitter(computedRtt)
                        }
                    }
                }

                updateRxSequenceStats(packet.sequenceNumber.toLong())

                val shouldActivateSession =
                    packet.messageType == PacketMessageType.HELLO_ACK &&
                        _diagnosticsState.value.status == ConnectionStatus.CONNECTING

                _diagnosticsState.update { current ->
                    val totalRx = current.packetsReceived + 1L
                    val totalExpected = totalRx + current.sequenceGapsDetected
                    val lossPct = if (totalExpected > 0L) {
                        (current.sequenceGapsDetected.toDouble() / totalExpected.toDouble()) * 100.0
                    } else {
                        0.0
                    }
                    current.copy(
                        packetsReceived = totalRx,
                        estimatedPacketLossPercent = lossPct.coerceIn(0.0, 100.0),
                        rttMs = rttSampleMs ?: current.rttMs,
                        smoothedRttMs = smoothedRttMs ?: current.smoothedRttMs,
                        jitterMs = if (lastRttMs != null) jitterAccumulatorMs else current.jitterMs,
                        lastSuccessfulCommElapsedMs = 0L,
                        lastAckSequence = packet.sequenceNumber.toLong(),
                        lastError = null
                    )
                }

                if (shouldActivateSession) {
                    onHandshakeSucceeded()
                } else if (packet.messageType == PacketMessageType.DISCONNECT) {
                    triggerWatchdogOrHandshakeFailure("Receiver requested session disconnect")
                }
            }
        }
    }

    private fun onHandshakeSucceeded() {
        val transport = activeTransport ?: return
        synchronized(sessionLock) {
            handshakeJob?.cancel()
            handshakeJob = null
            if (!transport.isOpen) return

            _diagnosticsState.update {
                it.copy(
                    status = ConnectionStatus.CONNECTED,
                    isTransmitting = true,
                    lastError = null
                )
            }

            txJob = launchTransmitLoop(transport)
            watchdogJob = launchPingAndWatchdogLoop(transport)
        }
    }

    private fun launchTransmitLoop(transport: UdpTransport): Job {
        return scope.launch(ioDispatcher) {
            var nextDeadlineNs = clock.nanoTime()
            rxWindowStartNs = nextDeadlineNs
            txWindowCount = 0L

            while (isActive && _diagnosticsState.value.status == ConnectionStatus.CONNECTED && transport.isOpen) {
                val currentHz = activeConfig.targetUpdateFrequencyHz.coerceIn(20, 500)
                val intervalNs = MonotonicSchedulerMath.intervalNanosForHz(currentHz)
                val nowNs = clock.nanoTime()

                if (nowNs < nextDeadlineNs) {
                    val remainingNs = nextDeadlineNs - nowNs
                    val sleepMs = remainingNs / 1_000_000L
                    if (sleepMs > 1L) {
                        delay(sleepMs - 1L)
                        continue
                    } else if (clock !== SystemMonotonicClock) {
                        delay(1L)
                        continue
                    } else if (remainingNs > 50_000L) {
                        // Sub-millisecond yield on the dedicated IO dispatcher without hogging main thread
                        Thread.yield()
                        continue
                    }
                }

                val sendTimeNs = clock.nanoTime()
                val latestState = inputRepository.controllerState.value
                val seq = nextTxSequence()
                val packet = DrivePadPacket.fromControllerState(
                    state = latestState,
                    messageType = PacketMessageType.CONTROLLER_STATE,
                    sequenceNumber = seq,
                    monotonicTimestampNanos = sendTimeNs
                )

                val encodedLen = try {
                    PacketCodec.encodeInto(packet, txBuffer)
                } catch (e: IllegalArgumentException) {
                    _diagnosticsState.update { it.copy(lastError = e.message) }
                    nextDeadlineNs = MonotonicSchedulerMath.computeNextDeadlineNanos(
                        nowNs = sendTimeNs,
                        currentDeadlineNs = nextDeadlineNs,
                        intervalNs = intervalNs
                    ).nextDeadlineNs
                    continue
                }

                val sendResult = transport.sendBlocking(txBuffer, encodedLen)
                if (sendResult.isFailure) {
                    val errMsg = sendResult.exceptionOrNull()?.message ?: "UDP send error"
                    triggerWatchdogOrHandshakeFailure("Socket TX failed: $errMsg")
                    break
                }

                txWindowCount++
                val windowElapsedNs = sendTimeNs - rxWindowStartNs
                if (windowElapsedNs >= 500_000_000L) {
                    val measuredHz = txWindowCount.toDouble() * 1_000_000_000.0 / windowElapsedNs.toDouble()
                    rxWindowStartNs = sendTimeNs
                    txWindowCount = 0L
                    _diagnosticsState.update {
                        it.copy(
                            packetsSent = it.packetsSent + 1L,
                            actualUpdateHz = measuredHz
                        )
                    }
                } else {
                    _diagnosticsState.update {
                        it.copy(packetsSent = it.packetsSent + 1L)
                    }
                }

                nextDeadlineNs = MonotonicSchedulerMath.computeNextDeadlineNanos(
                    nowNs = clock.nanoTime(),
                    currentDeadlineNs = nextDeadlineNs,
                    intervalNs = intervalNs
                ).nextDeadlineNs
            }
        }
    }

    private fun launchPingAndWatchdogLoop(transport: UdpTransport): Job {
        return scope.launch(ioDispatcher) {
            val watchdogTimeoutNs = watchdogTimeoutMs * 1_000_000L
            while (isActive && _diagnosticsState.value.status == ConnectionStatus.CONNECTED && transport.isOpen) {
                sendPingPacket(transport)
                delay(pingIntervalMs)

                val nowNs = clock.nanoTime()
                val elapsedSinceRxNs = (nowNs - lastSuccessfulRxNs).coerceAtLeast(0L)
                val elapsedSinceRxMs = elapsedSinceRxNs / 1_000_000L

                // Prune stale unanswered ping timestamps older than watchdog window
                pendingPings.entries.removeIf { (_, sentNs) -> (nowNs - sentNs) > watchdogTimeoutNs * 2L }

                if (elapsedSinceRxNs > watchdogTimeoutNs) {
                    triggerWatchdogOrHandshakeFailure(
                        message = "Watchdog timeout: no response from receiver in ${elapsedSinceRxMs}ms"
                    )
                    break
                } else {
                    _diagnosticsState.update {
                        it.copy(lastSuccessfulCommElapsedMs = elapsedSinceRxMs)
                    }
                }
            }
        }
    }

    private fun sendPingPacket(transport: UdpTransport) {
        val sentNs = clock.nanoTime()
        if (pendingPings.size > 256) {
            pendingPings.clear()
        }
        pendingPings[sentNs] = sentNs
        sendControlPacketBlocking(transport, PacketMessageType.PING, sentNs)
    }

    private fun sendControlPacketBlocking(
        transport: UdpTransport,
        type: PacketMessageType,
        timestampNs: Long
    ): Boolean {
        val seq = nextTxSequence()
        if (type == PacketMessageType.HELLO) {
            if (pendingPings.size > 256) {
                pendingPings.clear()
            }
            pendingPings[timestampNs] = timestampNs
        }
        val packet = DrivePadPacket.controlPacket(
            messageType = type,
            sequenceNumber = seq,
            monotonicTimestampNanos = timestampNs
        )
        return try {
            val result = synchronized(controlLock) {
                val len = PacketCodec.encodeInto(packet, controlBuffer)
                transport.sendBlocking(controlBuffer, len)
            }
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message ?: "Control packet TX failed"
                triggerWatchdogOrHandshakeFailure("Socket TX failed ($type): $msg")
                false
            } else {
                _diagnosticsState.update { it.copy(packetsSent = it.packetsSent + 1L) }
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            triggerWatchdogOrHandshakeFailure("Control packet serialization failed: ${e.message}")
            false
        }
    }

    private fun triggerWatchdogOrHandshakeFailure(message: String) {
        synchronized(sessionLock) {
            if (_diagnosticsState.value.status == ConnectionStatus.WATCHDOG_TIMEOUT ||
                _diagnosticsState.value.status == ConnectionStatus.DISCONNECTED ||
                _diagnosticsState.value.status == ConnectionStatus.TETHER_DETECTED
            ) {
                return
            }
            cancelSessionJobsLocked()
            closeTransportLocked()
            pendingPings.clear()
        }
        inputRepository.safetyResetAllInputs()

        val route = routeInspector.evaluateRoute(activeConfig.targetHostIp, activeConfig.udpPort)
        _diagnosticsState.update {
            it.copy(
                status = ConnectionStatus.WATCHDOG_TIMEOUT,
                actualUpdateHz = 0.0,
                isTransmitting = false,
                watchdogTriggerCount = it.watchdogTriggerCount + 1L,
                lastError = message,
                networkRoute = route
            )
        }
    }

    @Synchronized
    private fun nextTxSequence(): UInt {
        val current = txSequence
        txSequence = if (current >= UInt.MAX_VALUE.toLong()) 1L else current + 1L
        return current.toUInt()
    }

    @Synchronized
    private fun updateRxSequenceStats(receivedSeq: Long) {
        val expected = expectedReceiverSeq
        if (expected == null) {
            expectedReceiverSeq = receivedSeq + 1L
            return
        }

        if (receivedSeq == expected) {
            expectedReceiverSeq = receivedSeq + 1L
        } else if (receivedSeq > expected) {
            val gap = receivedSeq - expected
            _diagnosticsState.update {
                it.copy(sequenceGapsDetected = it.sequenceGapsDetected + gap)
            }
            expectedReceiverSeq = receivedSeq + 1L
        } else {
            _diagnosticsState.update {
                it.copy(outOfOrderPackets = it.outOfOrderPackets + 1L)
            }
        }
    }

    @Synchronized
    private fun updateRttAndJitter(rttMs: Double) {
        val prev = lastRttMs
        if (prev != null) {
            val diff = abs(rttMs - prev)
            // RFC 3550 exponential moving jitter estimate
            jitterAccumulatorMs += (diff - jitterAccumulatorMs) / 16.0
        }
        lastRttMs = rttMs
        smoothedRttMs = smoothedRttMs?.let { (it * 0.8) + (rttMs * 0.2) } ?: rttMs
    }

    private fun cancelSessionJobsLocked() {
        handshakeJob?.cancel()
        handshakeJob = null
        txJob?.cancel()
        txJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        rxJob?.cancel()
        rxJob = null
    }

    private fun closeTransportLocked() {
        activeTransport?.close()
        activeTransport = null
    }

    private fun resetSessionCountersLocked() {
        txSequence = 1L
        expectedReceiverSeq = null
        lastRttMs = null
        jitterAccumulatorMs = 0.0
        smoothedRttMs = null
        txWindowCount = 0L
        pendingPings.clear()
    }
}
