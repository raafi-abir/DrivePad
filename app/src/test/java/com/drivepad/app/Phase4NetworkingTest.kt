package com.drivepad.app

import com.drivepad.app.core.network.ControllerConnectionManager
import com.drivepad.app.core.network.DatagramSocketUdpTransport
import com.drivepad.app.core.network.DrivePadPacket
import com.drivepad.app.core.network.MonotonicSchedulerMath
import com.drivepad.app.core.network.NetworkAddressValidator
import com.drivepad.app.core.network.NetworkRouteInspector
import com.drivepad.app.core.network.PacketCodec
import com.drivepad.app.core.network.PacketDecodeResult
import com.drivepad.app.core.network.PacketMessageType
import com.drivepad.app.core.network.UdpControllerConnectionManager
import com.drivepad.app.core.network.VehicleButtonFlag
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.data.repository.ControllerInputRepository
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.LocalNetworkInterface
import com.drivepad.app.domain.model.NetworkRouteStatus
import com.drivepad.app.domain.model.NormalizedControllerState
import com.drivepad.app.domain.model.SteeringResponseCurve
import com.drivepad.app.domain.model.TransmissionMode
import com.drivepad.app.feature.racing.AutoGear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class Phase4NetworkingTest {

    // 1. Binary Packet Exact Size, Little-Endian Byte Order & Round-Trip
    @Test
    fun packetCodec_roundTripAndLittleEndianLayoutExact33Bytes() {
        val state = NormalizedControllerState(
            steering = -0.625f,
            throttle = 1.0f,
            brake = 0.0f,
            handbrake = true,
            currentGear = -2, // Park
            transmissionMode = TransmissionMode.MANUAL,
            lights = true,
            horn = true,
            wipers = false,
            hazardLights = true,
            leftIndicator = false,
            rightIndicator = true,
            cruiseControl = true,
            engine = true
        )
        val packet = DrivePadPacket.fromControllerState(
            state = state,
            messageType = PacketMessageType.CONTROLLER_STATE,
            sequenceNumber = 0x11223344u,
            monotonicTimestampNanos = 0x0102030405060708L
        )

        val bytes = PacketCodec.encode(packet)
        assertEquals(33, DrivePadPacket.PACKET_SIZE_BYTES)
        assertEquals(33, bytes.size)

        // Verify explicit little-endian byte positions
        assertEquals(1.toByte(), bytes[0]) // protocolVersion
        assertEquals(3.toByte(), bytes[1]) // CONTROLLER_STATE
        // sequenceNumber 0x11223344 little-endian => 44, 33, 22, 11
        assertEquals(0x44.toByte(), bytes[2])
        assertEquals(0x33.toByte(), bytes[3])
        assertEquals(0x22.toByte(), bytes[4])
        assertEquals(0x11.toByte(), bytes[5])
        // timestamp LSB = 0x08 at offset 6
        assertEquals(0x08.toByte(), bytes[6])
        assertEquals(0x01.toByte(), bytes[13])
        // handbrake = 1 at offset 26
        assertEquals(1.toByte(), bytes[26])
        // currentGear = -2 at offset 27
        assertEquals((-2).toByte(), bytes[27])
        // transmissionMode MANUAL = 1 at offset 28
        assertEquals(1.toByte(), bytes[28])

        val decoded = PacketCodec.decode(bytes)
        assertTrue(decoded is PacketDecodeResult.Valid)
        val roundTripped = (decoded as PacketDecodeResult.Valid).packet
        assertEquals(packet, roundTripped)
        assertTrue((roundTripped.vehicleButtonFlags and VehicleButtonFlag.LIGHTS) != 0u)
        assertTrue((roundTripped.vehicleButtonFlags and VehicleButtonFlag.ENGINE) != 0u)
        assertFalse((roundTripped.vehicleButtonFlags and VehicleButtonFlag.WIPERS) != 0u)
    }

    // 2. Malformed & Out-of-Range Packet Rejection (Pre-Send and Post-Receive)
    @Test
    fun packetCodec_rejectsMalformedAndOutOfRangePackets() {
        val validBytes = PacketCodec.encode(
            DrivePadPacket.controlPacket(PacketMessageType.PING, 7u, 123456L)
        )

        // Wrong length
        assertTrue(PacketCodec.decode(validBytes.copyOf(32)) is PacketDecodeResult.Invalid)
        assertTrue(PacketCodec.decode(validBytes, 20) is PacketDecodeResult.Invalid)

        // Wrong protocol version
        val badVersion = validBytes.clone().also { it[0] = 99 }
        assertTrue(PacketCodec.decode(badVersion) is PacketDecodeResult.Invalid)

        // Unknown message type
        val badType = validBytes.clone().also { it[1] = 42 }
        assertTrue(PacketCodec.decode(badType) is PacketDecodeResult.Invalid)

        // NaN steering
        val nanSteering = validBytes.clone()
        ByteBuffer.wrap(nanSteering).order(ByteOrder.LITTLE_ENDIAN).putFloat(14, Float.NaN)
        assertTrue(PacketCodec.decode(nanSteering) is PacketDecodeResult.Invalid)

        // Out-of-bounds steering
        val oobSteering = validBytes.clone()
        ByteBuffer.wrap(oobSteering).order(ByteOrder.LITTLE_ENDIAN).putFloat(14, 1.25f)
        assertTrue(PacketCodec.decode(oobSteering) is PacketDecodeResult.Invalid)

        // Non-digital throttle (0.5f violates mandatory 0.0 / 1.0 rule)
        val analogThrottle = validBytes.clone()
        ByteBuffer.wrap(analogThrottle).order(ByteOrder.LITTLE_ENDIAN).putFloat(18, 0.5f)
        assertTrue(PacketCodec.decode(analogThrottle) is PacketDecodeResult.Invalid)

        // Non-digital brake
        val analogBrake = validBytes.clone()
        ByteBuffer.wrap(analogBrake).order(ByteOrder.LITTLE_ENDIAN).putFloat(22, 0.25f)
        assertTrue(PacketCodec.decode(analogBrake) is PacketDecodeResult.Invalid)

        // Invalid gear
        val badGear = validBytes.clone().also { it[27] = 15 }
        assertTrue(PacketCodec.decode(badGear) is PacketDecodeResult.Invalid)

        // Invalid reserved button flags
        val badFlags = validBytes.clone()
        ByteBuffer.wrap(badFlags).order(ByteOrder.LITTLE_ENDIAN).putInt(29, 0x00010000)
        assertTrue(PacketCodec.decode(badFlags) is PacketDecodeResult.Invalid)

        // Pre-serialize validation rejects invalid DrivePadPacket
        var threw = false
        try {
            PacketCodec.encode(
                DrivePadPacket(
                    messageType = PacketMessageType.CONTROLLER_STATE,
                    sequenceNumber = 1u,
                    monotonicTimestampNanos = 1L,
                    throttle = 0.5f
                )
            )
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue("Pre-serialize validation must reject analog throttle", threw)
    }

    // 3. Network Address & Port Validator
    @Test
    fun networkAddressValidator_validatesIpv4PortAndFrequency() {
        assertTrue(NetworkAddressValidator.isValidTargetIpv4("192.168.42.1"))
        assertTrue(NetworkAddressValidator.isValidTargetIpv4("127.0.0.1"))
        assertFalse(NetworkAddressValidator.isValidTargetIpv4("0.0.0.0"))
        assertFalse(NetworkAddressValidator.isValidTargetIpv4("256.168.42.1"))
        assertFalse(NetworkAddressValidator.isValidTargetIpv4("192.168.042.1"))
        assertFalse(NetworkAddressValidator.isValidTargetIpv4("arch-laptop"))

        assertTrue(NetworkAddressValidator.isValidPort(42100))
        assertFalse(NetworkAddressValidator.isValidPort(0))
        assertFalse(NetworkAddressValidator.isValidPort(65536))

        assertTrue(NetworkAddressValidator.isValidFrequencyHz(250))
        assertFalse(NetworkAddressValidator.isValidFrequencyHz(10))
        assertFalse(NetworkAddressValidator.isValidFrequencyHz(1000))
    }

    // 4. Monotonic Scheduler Missed-Interval Skipping (Zero Burst)
    @Test
    fun monotonicScheduler_skipsObsoleteIntervalsWithoutBursting() {
        val intervalNs = MonotonicSchedulerMath.intervalNanosForHz(250)
        assertEquals(4_000_000L, intervalNs)

        // Normal step (no missed intervals)
        val normalStep = MonotonicSchedulerMath.computeNextDeadlineNanos(
            nowNs = 4_100_000L,
            currentDeadlineNs = 4_000_000L,
            intervalNs = intervalNs
        )
        assertEquals(0L, normalStep.skippedIntervals)
        assertEquals(8_000_000L, normalStep.nextDeadlineNs)

        // Simulated 22 ms OS stall -> jumps past obsolete slots instead of firing a catch-up burst
        val stallStep = MonotonicSchedulerMath.computeNextDeadlineNanos(
            nowNs = 26_500_000L,
            currentDeadlineNs = 4_000_000L,
            intervalNs = intervalNs
        )
        assertEquals(5L, stallStep.skippedIntervals)
        assertEquals(28_000_000L, stallStep.nextDeadlineNs)
    }

    // 5. Cellular-Only Fallback Protection
    @Test
    fun connectionManager_blocksTransmissionWhenOnlyCellularIsActive() {
        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val prefs = FakePreferencesRepo(ControllerConfig(targetHostIp = "192.168.42.1", udpPort = 42100))
        val inputRepo = FakeInputRepo()
        val cellularRoute = FakeRouteInspector(
            NetworkRouteStatus(
                hasLocalRoute = false,
                isLikelyUsbTether = false,
                onlyCellularActive = true,
                targetHostIp = "192.168.42.1",
                isTargetIpValid = true,
                isTargetPortValid = true,
                routeSummary = "Cellular only",
                warningMessage = "Mobile data is active without a local USB/LAN interface. Cellular fallback is blocked."
            )
        )

        val manager = UdpControllerConnectionManager(
            preferencesRepository = prefs,
            inputRepository = inputRepo,
            routeInspector = cellularRoute,
            scope = managerScope
        )

        manager.connect()
        val diag = manager.diagnosticsState.value
        assertEquals(ConnectionStatus.DISCONNECTED, diag.status)
        assertFalse(diag.isTransmitting)
        assertTrue(diag.lastError?.contains("Cellular", ignoreCase = true) == true)

        managerScope.cancel()
    }

    // 6. Real Localhost UDP Loopback Integration:
    // Covers Handshake (HELLO/HELLO_ACK), 250Hz State Stream, Digital Pedals, PING/PONG RTT/Jitter,
    // Sequence Gap Loss Estimation, Watchdog Timeout, and Clean Reconnect Without App Restart.
    @Test
    fun localhostLoopback_fullLifecycleHandshakeTelemetryLossWatchdogAndReconnect() = kotlinx.coroutines.runBlocking {
        val receiverSocket = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val receiverPort = receiverSocket.localPort
        receiverSocket.soTimeout = 50

        val respondEnabled = AtomicBoolean(true)
        val simulateSeqGapNextPong = AtomicBoolean(false)
        val serverRunning = AtomicBoolean(true)
        val receivedPackets = CopyOnWriteArrayList<DrivePadPacket>()
        val serverSeq = AtomicLong(1L)

        val serverThread = Thread {
            val rxBuf = ByteArray(256)
            val txBuf = ByteArray(DrivePadPacket.PACKET_SIZE_BYTES)
            val rxPkt = DatagramPacket(rxBuf, rxBuf.size)
            while (serverRunning.get()) {
                try {
                    rxPkt.setData(rxBuf, 0, rxBuf.size)
                    receiverSocket.receive(rxPkt)
                    when (val decoded = PacketCodec.decode(rxBuf, rxPkt.length)) {
                        is PacketDecodeResult.Valid -> {
                            val p = decoded.packet
                            receivedPackets.add(p)
                            if (!respondEnabled.get()) continue

                            when (p.messageType) {
                                PacketMessageType.HELLO -> {
                                    val reply = DrivePadPacket.controlPacket(
                                        messageType = PacketMessageType.HELLO_ACK,
                                        sequenceNumber = serverSeq.getAndIncrement().toUInt(),
                                        monotonicTimestampNanos = p.monotonicTimestampNanos
                                    )
                                    val len = PacketCodec.encodeInto(reply, txBuf)
                                    receiverSocket.send(DatagramPacket(txBuf, len, rxPkt.socketAddress))
                                }
                                PacketMessageType.PING -> {
                                    if (simulateSeqGapNextPong.compareAndSet(true, false)) {
                                        // Skip 2 sequence numbers intentionally to test RX gap/loss detection
                                        serverSeq.addAndGet(2L)
                                    }
                                    val reply = DrivePadPacket.controlPacket(
                                        messageType = PacketMessageType.PONG,
                                        sequenceNumber = serverSeq.getAndIncrement().toUInt(),
                                        monotonicTimestampNanos = p.monotonicTimestampNanos
                                    )
                                    val len = PacketCodec.encodeInto(reply, txBuf)
                                    receiverSocket.send(DatagramPacket(txBuf, len, rxPkt.socketAddress))
                                }
                                else -> Unit
                            }
                        }
                        is PacketDecodeResult.Invalid -> Unit
                    }
                } catch (_: Exception) {
                    // Timeout or shutdown
                }
            }
        }.apply { isDaemon = true; start() }

        val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val config = ControllerConfig(
                targetHostIp = "127.0.0.1",
                udpPort = receiverPort,
                targetUpdateFrequencyHz = 250
            )
            val prefs = FakePreferencesRepo(config)
            val inputRepo = FakeInputRepo()
            val routeInspector = FakeRouteInspector(
                NetworkRouteStatus(
                    hasLocalRoute = true,
                    isLikelyUsbTether = true,
                    onlyCellularActive = false,
                    activeInterfaces = listOf(LocalNetworkInterface("usb0", "192.168.42.129", true)),
                    targetHostIp = "127.0.0.1",
                    isTargetIpValid = true,
                    isTargetPortValid = true,
                    routeSummary = "Loopback test route"
                )
            )

            val manager: ControllerConnectionManager = UdpControllerConnectionManager(
                preferencesRepository = prefs,
                inputRepository = inputRepo,
                routeInspector = routeInspector,
                transportFactory = { DatagramSocketUdpTransport() },
                ioDispatcher = Dispatchers.IO,
                scope = managerScope,
                handshakeTimeoutMs = 1_500L,
                helloRetryIntervalMs = 50L,
                pingIntervalMs = 80L,
                watchdogTimeoutMs = 450L,
                receivePollTimeoutMs = 40
            )

            // Wait for init collector
            kotlinx.coroutines.delay(50)
            manager.connect()

            // A) Wait for CONNECTED state via real HELLO/HELLO_ACK over UDP
            withTimeout(2_000) {
                while (manager.diagnosticsState.value.status != ConnectionStatus.CONNECTED) {
                    kotlinx.coroutines.delay(15)
                }
            }

            // B) Mutate controller inputs & verify digital pedals + steering arrive at receiver
            inputRepo.setState(
                NormalizedControllerState(
                    steering = 0.75f,
                    throttle = 1.0f,
                    brake = 0.0f,
                    handbrake = true,
                    currentGear = 4,
                    transmissionMode = TransmissionMode.MANUAL,
                    lights = true,
                    engine = true
                )
            )
            simulateSeqGapNextPong.set(true)

            withTimeout(2_500) {
                while (
                    manager.diagnosticsState.value.packetsReceived < 3 ||
                    manager.diagnosticsState.value.actualUpdateHz <= 0.0 ||
                    receivedPackets.none {
                        it.messageType == PacketMessageType.CONTROLLER_STATE &&
                            it.throttle == 1.0f &&
                            it.steering == 0.75f &&
                            it.currentGear.toInt() == 4
                    }
                ) {
                    kotlinx.coroutines.delay(25)
                }
            }

            val activeDiag = manager.diagnosticsState.value
            assertTrue("Should have sent multiple packets", activeDiag.packetsSent > 10)
            assertTrue("Should have measured RTT", activeDiag.rttMs != null)
            assertTrue("Should have detected simulated RX sequence gap", activeDiag.sequenceGapsDetected >= 2)
            assertTrue("Should report non-zero estimated packet loss", activeDiag.estimatedPacketLossPercent > 0.0)
            assertTrue("Actual Hz should be positive", activeDiag.actualUpdateHz > 0.0)

            // C) Silence receiver -> Watchdog triggers fail-safe & stops TX cleanly
            respondEnabled.set(false)
            withTimeout(2_000) {
                while (manager.diagnosticsState.value.status != ConnectionStatus.WATCHDOG_TIMEOUT) {
                    kotlinx.coroutines.delay(20)
                }
            }
            assertFalse(manager.diagnosticsState.value.isTransmitting)
            assertTrue(manager.diagnosticsState.value.watchdogTriggerCount >= 1)
            assertTrue("Watchdog must trigger safetyResetAllInputs", inputRepo.safetyResetCount >= 1)

            // D) Re-enable receiver -> Reconnect without restarting app
            respondEnabled.set(true)
            manager.connect()
            withTimeout(2_000) {
                while (manager.diagnosticsState.value.status != ConnectionStatus.CONNECTED) {
                    kotlinx.coroutines.delay(15)
                }
            }

            // E) Disconnect cleanly sends DISCONNECT packet and stops transmitting
            val countBeforeDisconnect = receivedPackets.size
            manager.disconnect()
            withTimeout(1_500) {
                while (manager.diagnosticsState.value.status == ConnectionStatus.CONNECTED) {
                    kotlinx.coroutines.delay(15)
                }
            }
            kotlinx.coroutines.delay(80)
            val disconnectPacket = receivedPackets.drop(countBeforeDisconnect).find {
                it.messageType == PacketMessageType.DISCONNECT
            }
            assertNotNull("Clean disconnect must emit DISCONNECT packet", disconnectPacket)
            assertFalse(manager.diagnosticsState.value.isTransmitting)
            assertTrue("Disconnect must trigger safetyResetAllInputs", inputRepo.safetyResetCount >= 2)
        } finally {
            serverRunning.set(false)
            receiverSocket.close()
            serverThread.join(500)
            managerScope.cancel()
        }
    }

    private class FakePreferencesRepo(initialConfig: ControllerConfig) : UserPreferencesRepository {
        private val configFlow = MutableStateFlow(initialConfig)
        override val controllerConfigFlow: Flow<ControllerConfig> = configFlow.asStateFlow()
        override val selectedProfileIdFlow: Flow<String> = MutableStateFlow("ets2_truck")
        override suspend fun updateSteeringSensitivity(sensitivity: Float) {}
        override suspend fun updateSteeringDeadzone(deadzone: Float) {}
        override suspend fun updateMaxSteeringAngle(angle: Float) {}
        override suspend fun updateSteeringResponseCurve(curve: SteeringResponseCurve) {}
        override suspend fun updateInvertSteering(invert: Boolean) {}
        override suspend fun updateMotionSteeringEnabled(enabled: Boolean) {}
        override suspend fun updateMotionSmoothing(factor: Float) {}
        override suspend fun updateHapticFeedback(enabled: Boolean) {}
        override suspend fun updateReturnToCenter(enabled: Boolean) {}
        override suspend fun updateDefaultTransmission(mode: TransmissionMode) {}
        override suspend fun updateNetworkSettings(port: Int, hostIp: String) {
            configFlow.value = configFlow.value.copy(udpPort = port, targetHostIp = hostIp)
        }
        override suspend fun updateTargetUpdateFrequency(frequencyHz: Int) {
            configFlow.value = configFlow.value.copy(targetUpdateFrequencyHz = frequencyHz)
        }
        override suspend fun updateSelectedProfileId(profileId: String) {}
        override suspend fun resetToDefaults() {}
    }

    private class FakeInputRepo : ControllerInputRepository {
        private val _state = MutableStateFlow(NormalizedControllerState())
        override val controllerState: StateFlow<NormalizedControllerState> = _state.asStateFlow()
        override val isSensorAvailable: Boolean = true
        override val isMotionSteeringActive: Boolean = false
        override val isTouchInteracting: StateFlow<Boolean> = MutableStateFlow(false)

        fun setState(newState: NormalizedControllerState) {
            _state.value = newState
        }

        override fun onSteeringDrag(deltaAngleDegrees: Float) {}
        override fun onSteeringReleased() {}
        override fun setAcceleratorPressed(isPressed: Boolean) {}
        override fun setBrakePressed(isPressed: Boolean) {}
        override fun toggleHandbrake() {}
        override fun setTransmissionMode(mode: TransmissionMode) {}
        override fun selectAutoGear(gear: AutoGear) {}
        override fun shiftUp(maxGear: Int) {}
        override fun shiftDown(minGear: Int) {}
        override fun toggleLights() {}
        override fun setHorn(isPressed: Boolean) {}
        override fun toggleWipers() {}
        override fun toggleHazard() {}
        override fun toggleCruise() {}
        override fun toggleEngine() {}
        override fun toggleLeftIndicator() {}
        override fun toggleRightIndicator() {}
        override fun toggleMotionSteering(enabled: Boolean) {}
        override fun calibrateSteering(): Boolean = true
        override fun resetSteeringCalibration() {}
        @Volatile
        var safetyResetCount: Int = 0
            private set

        override fun safetyResetAllInputs() {
            safetyResetCount++
            _state.value = _state.value.copy(throttle = 0.0f, brake = 0.0f, horn = false)
        }
        override fun startSensors() {}
        override fun stopSensors() {}
    }

    private class FakeRouteInspector(initial: NetworkRouteStatus) : NetworkRouteInspector {
        private val _route = MutableStateFlow(initial)
        override val routeStatus: StateFlow<NetworkRouteStatus> = _route.asStateFlow()
        override fun evaluateRoute(targetHostIp: String, targetPort: Int): NetworkRouteStatus =
            _route.value.copy(targetHostIp = targetHostIp)
        override fun startMonitoring() {}
        override fun stopMonitoring() {}
    }
}
