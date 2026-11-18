/*
 * DrivePad Phase 4 — Temporary Arch Linux UDP Test Receiver
 *
 * PURPOSE: Temporary CLI utility to verify Android -> USB Tethering -> UDP -> Arch Linux
 * binary telemetry, HELLO/HELLO_ACK handshake, PING/PONG RTT, sequence loss, and 250 Hz rate.
 * (NOT the Phase 5 production companion / uinput virtual gamepad.)
 *
 * SECURITY NOTICE:
 * Raw UDP packets do NOT authenticate sender identity. Only run this receiver on a trusted
 * local USB tether / LAN subnet and bind to your specific USB tether interface IP when possible.
 *
 * Build on Arch Linux:
 *   kotlinc DrivePadTestReceiver.kt -include-runtime -d DrivePadTestReceiver.jar
 * Run:
 *   java -jar DrivePadTestReceiver.jar --port 42100 --bind 0.0.0.0
 */

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import kotlin.math.roundToInt

private const val PROTOCOL_VERSION_V1: Int = 1
private const val PACKET_SIZE_BYTES: Int = 33
private const val KNOWN_FLAGS_MASK: UInt = 0xFFu

private enum class MessageType(val wireValue: Int) {
    HELLO(1),
    HELLO_ACK(2),
    CONTROLLER_STATE(3),
    PING(4),
    PONG(5),
    DISCONNECT(6);

    companion object {
        fun fromWire(value: Int): MessageType? = entries.find { it.wireValue == value }
    }
}

private data class DecodedPacket(
    val protocolVersion: Int,
    val messageType: MessageType,
    val sequenceNumber: Long,
    val monotonicTimestampNanos: Long,
    val steering: Float,
    val throttle: Float,
    val brake: Float,
    val handbrake: Boolean,
    val currentGear: Int,
    val transmissionMode: Int,
    val vehicleButtonFlags: UInt
)

private sealed class DecodeOutcome {
    data class Valid(val packet: DecodedPacket) : DecodeOutcome()
    data class Invalid(val reason: String) : DecodeOutcome()
}

private object ReceiverCodec {
    fun decode(bytes: ByteArray, length: Int): DecodeOutcome {
        if (length != PACKET_SIZE_BYTES || bytes.size < PACKET_SIZE_BYTES) {
            return DecodeOutcome.Invalid("Expected $PACKET_SIZE_BYTES bytes, got $length")
        }

        val bb = ByteBuffer.wrap(bytes, 0, PACKET_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val version = bb.get(0).toInt() and 0xFF
        if (version != PROTOCOL_VERSION_V1) {
            return DecodeOutcome.Invalid("Unsupported protocol version: $version")
        }

        val rawType = bb.get(1).toInt() and 0xFF
        val type = MessageType.fromWire(rawType)
            ?: return DecodeOutcome.Invalid("Unknown message type: $rawType")

        val seq = bb.getInt(2).toLong() and 0xFFFFFFFFL
        val timestampNs = bb.getLong(6)

        val steering = bb.getFloat(14)
        if (steering.isNaN() || steering.isInfinite() || steering !in -1.0f..1.0f) {
            return DecodeOutcome.Invalid("Steering out of bounds: $steering")
        }

        val throttle = bb.getFloat(18)
        if (throttle.isNaN() || throttle.isInfinite() || (throttle != 0.0f && throttle != 1.0f)) {
            return DecodeOutcome.Invalid("Throttle must be strictly 0.0 or 1.0, got $throttle")
        }

        val brake = bb.getFloat(22)
        if (brake.isNaN() || brake.isInfinite() || (brake != 0.0f && brake != 1.0f)) {
            return DecodeOutcome.Invalid("Brake must be strictly 0.0 or 1.0, got $brake")
        }

        val handbrakeByte = bb.get(26).toInt() and 0xFF
        if (handbrakeByte != 0 && handbrakeByte != 1) {
            return DecodeOutcome.Invalid("Invalid handbrake byte: $handbrakeByte")
        }

        val gear = bb.get(27).toInt() // signed Int8 [-2..12]
        if (gear < -2 || gear > 12) {
            return DecodeOutcome.Invalid("Gear out of bounds [-2..12]: $gear")
        }

        val transByte = bb.get(28).toInt() and 0xFF
        if (transByte != 0 && transByte != 1) {
            return DecodeOutcome.Invalid("Invalid transmission mode byte: $transByte")
        }

        val flags = bb.getInt(29).toUInt()
        if ((flags and KNOWN_FLAGS_MASK.inv()) != 0u) {
            return DecodeOutcome.Invalid("Reserved button flag bits set: 0x${flags.toString(16)}")
        }

        return DecodeOutcome.Valid(
            DecodedPacket(
                protocolVersion = version,
                messageType = type,
                sequenceNumber = seq,
                monotonicTimestampNanos = timestampNs,
                steering = steering,
                throttle = throttle,
                brake = brake,
                handbrake = handbrakeByte == 1,
                currentGear = gear,
                transmissionMode = transByte,
                vehicleButtonFlags = flags
            )
        )
    }

    fun encodeReply(
        type: MessageType,
        receiverSequence: Long,
        echoedClientTimestampNanos: Long,
        outBuffer: ByteArray
    ): Int {
        val bb = ByteBuffer.wrap(outBuffer, 0, PACKET_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0, PROTOCOL_VERSION_V1.toByte())
        bb.put(1, type.wireValue.toByte())
        bb.putInt(2, (receiverSequence and 0xFFFFFFFFL).toInt())
        bb.putLong(6, echoedClientTimestampNanos)
        bb.putFloat(14, 0.0f)
        bb.putFloat(18, 0.0f)
        bb.putFloat(22, 0.0f)
        bb.put(26, 0)
        bb.put(27, 0)
        bb.put(28, 0)
        bb.putInt(29, 0)
        return PACKET_SIZE_BYTES
    }
}

private class SessionTracker {
    var totalPacketsValid: Long = 0L
    var totalPacketsInvalid: Long = 0L
    var statePackets: Long = 0L
    var sequenceGaps: Long = 0L
    var outOfOrder: Long = 0L
    var expectedClientSeq: Long? = null
    var receiverTxSeq: Long = 1L

    var windowStartNs: Long = System.nanoTime()
    var windowPackets: Long = 0L
    var measuredHz: Double = 0.0

    var lastClientAddress: SocketAddress? = null
    var lastState: DecodedPacket? = null

    fun nextReplySeq(): Long {
        val s = receiverTxSeq
        receiverTxSeq = if (s >= 0xFFFFFFFFL) 1L else s + 1L
        return s
    }

    fun resetForNewClient(addr: SocketAddress) {
        lastClientAddress = addr
        expectedClientSeq = null
        sequenceGaps = 0L
        outOfOrder = 0L
        statePackets = 0L
        windowStartNs = System.nanoTime()
        windowPackets = 0L
        measuredHz = 0.0
    }

    fun recordClientPacket(packet: DecodedPacket, nowNs: Long) {
        totalPacketsValid++
        windowPackets++
        val elapsedNs = nowNs - windowStartNs
        if (elapsedNs >= 500_000_000L) {
            measuredHz = windowPackets.toDouble() * 1_000_000_000.0 / elapsedNs.toDouble()
            windowStartNs = nowNs
            windowPackets = 0L
        }

        val exp = expectedClientSeq
        if (exp == null) {
            expectedClientSeq = packet.sequenceNumber + 1L
        } else if (packet.sequenceNumber == exp) {
            expectedClientSeq = packet.sequenceNumber + 1L
        } else if (packet.sequenceNumber > exp) {
            sequenceGaps += (packet.sequenceNumber - exp)
            expectedClientSeq = packet.sequenceNumber + 1L
        } else {
            outOfOrder++
        }

        if (packet.messageType == MessageType.CONTROLLER_STATE) {
            statePackets++
            lastState = packet
        }
    }

    fun lossPercent(): Double {
        val denom = totalPacketsValid + sequenceGaps
        return if (denom > 0L) (sequenceGaps.toDouble() / denom.toDouble()) * 100.0 else 0.0
    }
}

fun main(args: Array<String>) {
    var port = 42100
    var bindAddress = "0.0.0.0"
    var verbose = false

    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--port", "-p" -> {
                port = args.getOrNull(++i)?.toIntOrNull()
                    ?: error("Invalid value for --port")
                require(port in 1..65535) { "Port must be in 1..65535" }
            }
            "--bind", "-b" -> {
                bindAddress = args.getOrNull(++i) ?: error("Missing value for --bind")
            }
            "--verbose", "-v" -> verbose = true
            "--help", "-h" -> {
                println(
                    """
                    DrivePad Temporary Test Receiver (Phase 4)
                    Usage: java -jar DrivePadTestReceiver.jar [--port 42100] [--bind 0.0.0.0] [--verbose]
                    """.trimIndent()
                )
                return
            }
            else -> {
                System.err.println("Unknown argument: ${args[i]}")
                return
            }
        }
        i++
    }

    println("==============================================================")
    println("  DrivePad Temporary Test Receiver (Phase 4 - Arch Linux)")
    println("  Protocol: v1 (33-byte Little-Endian Binary UDP)")
    println("  Listening on: $bindAddress:$port")
    println("  SECURITY NOTE: UDP does not authenticate senders. Run only")
    println("  on your trusted local USB tethering interface/subnet.")
    println("==============================================================")

    val socket = DatagramSocket(port, InetAddress.getByName(bindAddress))
    val rxBuffer = ByteArray(256)
    val txBuffer = ByteArray(PACKET_SIZE_BYTES)
    val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
    val tracker = SessionTracker()
    var lastPrintNs = 0L

    Runtime.getRuntime().addShutdownHook(Thread {
        println("\nShutting down DrivePadTestReceiver. Valid RX=${tracker.totalPacketsValid}, Gaps=${tracker.sequenceGaps}, Invalid=${tracker.totalPacketsInvalid}")
        socket.close()
    })

    while (!socket.isClosed) {
        try {
            rxPacket.setData(rxBuffer, 0, rxBuffer.size)
            socket.receive(rxPacket)
            val nowNs = System.nanoTime()

            when (val decoded = ReceiverCodec.decode(rxBuffer, rxPacket.length)) {
                is DecodeOutcome.Invalid -> {
                    tracker.totalPacketsInvalid++
                    if (verbose) {
                        System.err.println("[WARN] Rejected packet from ${rxPacket.socketAddress}: ${decoded.reason}")
                    }
                }
                is DecodeOutcome.Valid -> {
                    val pkt = decoded.packet
                    when (pkt.messageType) {
                        MessageType.HELLO -> {
                            tracker.resetForNewClient(rxPacket.socketAddress)
                            tracker.recordClientPacket(pkt, nowNs)
                            val len = ReceiverCodec.encodeReply(
                                type = MessageType.HELLO_ACK,
                                receiverSequence = tracker.nextReplySeq(),
                                echoedClientTimestampNanos = pkt.monotonicTimestampNanos,
                                outBuffer = txBuffer
                            )
                            socket.send(DatagramPacket(txBuffer, len, rxPacket.socketAddress))
                            println("[SESSION] HELLO from ${rxPacket.socketAddress} (seq=${pkt.sequenceNumber}) -> sent HELLO_ACK")
                        }
                        MessageType.PING -> {
                            tracker.recordClientPacket(pkt, nowNs)
                            val len = ReceiverCodec.encodeReply(
                                type = MessageType.PONG,
                                receiverSequence = tracker.nextReplySeq(),
                                echoedClientTimestampNanos = pkt.monotonicTimestampNanos,
                                outBuffer = txBuffer
                            )
                            socket.send(DatagramPacket(txBuffer, len, rxPacket.socketAddress))
                            if (verbose) {
                                println("[PING] from ${rxPacket.socketAddress} seq=${pkt.sequenceNumber} -> sent PONG")
                            }
                        }
                        MessageType.DISCONNECT -> {
                            tracker.recordClientPacket(pkt, nowNs)
                            println("\n[SESSION] Client ${rxPacket.socketAddress} disconnected cleanly (seq=${pkt.sequenceNumber})")
                        }
                        MessageType.CONTROLLER_STATE -> {
                            tracker.recordClientPacket(pkt, nowNs)
                        }
                        MessageType.HELLO_ACK, MessageType.PONG -> {
                            // Receiver ignores server-originated reply types
                        }
                    }

                    // Throttled 10 Hz console status update so terminal I/O never stalls 250 Hz socket RX
                    if (nowNs - lastPrintNs >= 100_000_000L && tracker.lastState != null) {
                        lastPrintNs = nowNs
                        printLiveDashboard(tracker)
                    }
                }
            }
        } catch (e: Exception) {
            if (!socket.isClosed) {
                System.err.println("[ERROR] Socket loop error: ${e.message}")
            }
        }
    }
}

private fun printLiveDashboard(tracker: SessionTracker) {
    val s = tracker.lastState ?: return
    val gearStr = when (s.currentGear) {
        -2 -> "P"
        -1 -> "R"
        0 -> "N"
        else -> s.currentGear.toString()
    }
    val modeStr = if (s.transmissionMode == 1) "MAN" else "AUTO"
    val steerBar = formatSteeringBar(s.steering)
    val thrStr = if (s.throttle >= 1.0f) "ON " else "OFF"
    val brkStr = if (s.brake >= 1.0f) "ON " else "OFF"
    val hbStr = if (s.handbrake) "ON " else "OFF"
    val flagsStr = formatFlags(s.vehicleButtonFlags)

    print(
        String.format(
            Locale.US,
            "\r[%6.1f Hz | RX:%d | Loss:%.2f%% (Gaps:%d)] STEER %s %+6.3f | THR:%s BRK:%s HB:%s | %s:%-2s | %s   ",
            tracker.measuredHz,
            tracker.totalPacketsValid,
            tracker.lossPercent(),
            tracker.sequenceGaps,
            steerBar,
            s.steering,
            thrStr,
            brkStr,
            hbStr,
            modeStr,
            gearStr,
            flagsStr
        )
    )
    System.out.flush()
}

private fun formatSteeringBar(steering: Float): String {
    val width = 15
    val center = width / 2
    val pos = ((steering + 1.0f) * 0.5f * (width - 1)).roundToInt().coerceIn(0, width - 1)
    return buildString {
        append('[')
        for (i in 0 until width) {
            when {
                i == pos -> append('#')
                i == center -> append('|')
                else -> append('-')
            }
        }
        append(']')
    }
}

private fun formatFlags(flags: UInt): String {
    val active = mutableListOf<String>()
    if ((flags and 0x01u) != 0u) active.add("LGT")
    if ((flags and 0x02u) != 0u) active.add("HRN")
    if ((flags and 0x04u) != 0u) active.add("WPR")
    if ((flags and 0x08u) != 0u) active.add("HZD")
    if ((flags and 0x10u) != 0u) active.add("L")
    if ((flags and 0x20u) != 0u) active.add("R")
    if ((flags and 0x40u) != 0u) active.add("CRS")
    if ((flags and 0x80u) != 0u) active.add("ENG")
    return if (active.isEmpty()) "NONE" else active.joinToString(",")
}
