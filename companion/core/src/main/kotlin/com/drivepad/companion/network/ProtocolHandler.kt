package com.drivepad.companion.network

import com.drivepad.companion.diagnostics.MonotonicClock
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.diagnostics.SystemMonotonicClock
import com.drivepad.companion.domain.ControllerStateProcessor
import com.drivepad.companion.domain.InputValidationResult
import com.drivepad.companion.monitoring.ConnectionMonitor
import com.drivepad.companion.monitoring.LatencyMonitor
import com.drivepad.companion.monitoring.PacketStatistics
import com.drivepad.companion.protocol.PacketDecodeResult
import com.drivepad.companion.protocol.PacketDecoder
import com.drivepad.companion.protocol.PacketMessageType
import com.drivepad.companion.safety.InputWatchdog
import java.net.InetSocketAddress

data class ProtocolReply(
    val messageType: PacketMessageType,
    val sequenceNumber: Long,
    val echoedTimestampNanos: Long
)

/**
 * Pure protocol handler coordinating packet decoding, handshake verification,
 * sequence/latency telemetry, controller state updates, and watchdog feeding.
 */
class ProtocolHandler(
    private val connectionManager: ConnectionManager,
    private val connectionMonitor: ConnectionMonitor,
    private val stateProcessor: ControllerStateProcessor,
    private val packetStatistics: PacketStatistics,
    private val latencyMonitor: LatencyMonitor,
    private val watchdog: InputWatchdog,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val logger: StructuredLogger = StructuredLogger()
) {
    private var invalidLogCounter: Long = 0L
    private var replayLogCounter: Long = 0L
    private var helloLogCounter: Long = 0L
    private var hijackLogCounter: Long = 0L
    private var lastAppliedStateSeq: Long? = null
    private var lastAppliedPingSeq: Long? = null

    private fun isStrictlyNewerSeq(seq: Long, previousSeq: Long?): Boolean {
        if (previousSeq == null) return true
        // Signed 32-bit delta handles 0xFFFFFFFF -> 0 unsigned wrap-around cleanly
        return ((seq - previousSeq).toInt()) > 0
    }

    fun handleIncomingBytes(
        bytes: ByteArray,
        length: Int,
        sender: InetSocketAddress
    ): ProtocolReply? {
        val nowNs = clock.nanoTime()

        return when (val decoded = PacketDecoder.decode(bytes, length)) {
            is PacketDecodeResult.Invalid -> {
                packetStatistics.recordInvalidPacket()
                invalidLogCounter++
                // Rate-limit malformed packet logs so floods cannot stall the UDP loop
                if (invalidLogCounter <= 5L || invalidLogCounter % 100L == 0L) {
                    logger.warn(
                        "ProtocolHandler",
                        "Rejected malformed packet",
                        "sender" to "${sender.address?.hostAddress}:${sender.port}",
                        "reason" to decoded.reason,
                        "invalid_count" to invalidLogCounter
                    )
                }
                null
            }
            is PacketDecodeResult.Valid -> {
                val packet = decoded.packet
                when (packet.messageType) {
                    PacketMessageType.HELLO -> {
                        val replySeq = connectionManager.tryAcceptHandshake(
                            clientAddress = sender,
                            protocolVersion = packet.protocolVersion,
                            nowNanos = nowNs
                        )
                        if (replySeq == null) {
                            packetStatistics.recordInvalidPacket()
                            hijackLogCounter++
                            if (hijackLogCounter <= 5L || hijackLogCounter % 100L == 0L) {
                                logger.warn(
                                    "ProtocolHandler",
                                    "Rejected HELLO from competing host while active session is live",
                                    "sender" to "${sender.address?.hostAddress}:${sender.port}",
                                    "active_client" to (connectionManager.activeClientSummary() ?: "unknown"),
                                    "rejected_count" to hijackLogCounter
                                )
                            }
                            return null
                        }
                        lastAppliedStateSeq = null
                        lastAppliedPingSeq = null
                        if (!stateProcessor.controllerState.value.isSafeNeutral) {
                            stateProcessor.resetToFailsafe(nowNs)
                        }
                        packetStatistics.resetSessionSequenceTracking()
                        latencyMonitor.resetRateWindow()
                        packetStatistics.recordValidPacket(packet.sequenceNumber)
                        latencyMonitor.recordPacketArrival(nowNs)
                        watchdog.arm(nowNs)
                        helloLogCounter++
                        if (helloLogCounter <= 10L || helloLogCounter % 100L == 0L) {
                            logger.info(
                                "ProtocolHandler",
                                "Client handshake accepted",
                                "client" to "${sender.address?.hostAddress}:${sender.port}",
                                "protocol" to packet.protocolVersion,
                                "seq" to packet.sequenceNumber
                            )
                        }
                        ProtocolReply(
                            messageType = PacketMessageType.HELLO_ACK,
                            sequenceNumber = replySeq,
                            echoedTimestampNanos = packet.monotonicTimestampNanos
                        )
                    }

                    PacketMessageType.PING -> {
                        if (!connectionManager.isConnectedTo(sender)) {
                            packetStatistics.recordInvalidPacket()
                            return null
                        }
                        if (!isStrictlyNewerSeq(packet.sequenceNumber, lastAppliedPingSeq)) {
                            packetStatistics.recordInvalidPacket()
                            return null
                        }
                        lastAppliedPingSeq = packet.sequenceNumber
                        packetStatistics.recordValidPacket(packet.sequenceNumber)
                        latencyMonitor.recordPacketArrival(nowNs)
                        connectionMonitor.onValidPacketReceived(nowNs)
                        connectionManager.touchActivity(nowNs)
                        watchdog.feed(nowNs)

                        ProtocolReply(
                            messageType = PacketMessageType.PONG,
                            sequenceNumber = connectionManager.nextServerReplySequence(),
                            echoedTimestampNanos = packet.monotonicTimestampNanos
                        )
                    }

                    PacketMessageType.CONTROLLER_STATE -> {
                        if (!connectionManager.isConnectedTo(sender)) {
                            packetStatistics.recordInvalidPacket()
                            return null
                        }
                        if (!isStrictlyNewerSeq(packet.sequenceNumber, lastAppliedStateSeq)) {
                            packetStatistics.recordInvalidPacket()
                            replayLogCounter++
                            if (replayLogCounter <= 5L || replayLogCounter % 100L == 0L) {
                                logger.warn(
                                    "ProtocolHandler",
                                    "Dropped stale or replayed CONTROLLER_STATE packet",
                                    "seq" to packet.sequenceNumber,
                                    "last_applied_seq" to (lastAppliedStateSeq ?: -1L),
                                    "replay_count" to replayLogCounter
                                )
                            }
                            return null
                        }
                        when (val validated = stateProcessor.applyDecodedPacket(packet, nowNs)) {
                            is InputValidationResult.Rejected -> {
                                packetStatistics.recordInvalidPacket()
                                logger.warn(
                                    "ProtocolHandler",
                                    "Controller state validation failed",
                                    "reason" to validated.reason
                                )
                            }
                            is InputValidationResult.Valid -> {
                                lastAppliedStateSeq = packet.sequenceNumber
                                packetStatistics.recordValidPacket(packet.sequenceNumber)
                                latencyMonitor.recordPacketArrival(nowNs)
                                connectionMonitor.onValidPacketReceived(nowNs)
                                connectionManager.touchActivity(nowNs)
                                watchdog.feed(nowNs)
                            }
                        }
                        null
                    }

                    PacketMessageType.DISCONNECT -> {
                        if (!connectionManager.isConnectedTo(sender)) {
                            packetStatistics.recordInvalidPacket()
                            return null
                        }
                        lastAppliedStateSeq = null
                        lastAppliedPingSeq = null
                        packetStatistics.recordValidPacket(packet.sequenceNumber)
                        watchdog.disarm()
                        connectionManager.disconnectClient(sender)
                        stateProcessor.resetToFailsafe(nowNs)
                        logger.info(
                            "ProtocolHandler",
                            "Client disconnected cleanly; failsafe neutral applied",
                            "client" to "${sender.address?.hostAddress}:${sender.port}",
                            "seq" to packet.sequenceNumber
                        )
                        null
                    }

                    PacketMessageType.HELLO_ACK, PacketMessageType.PONG -> {
                        // Server ignores server-originated message types if sent to companion port
                        packetStatistics.recordInvalidPacket()
                        null
                    }
                }
            }
        }
    }
}
