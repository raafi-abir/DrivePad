package com.drivepad.app.core.network

import com.drivepad.app.domain.model.NormalizedControllerState
import com.drivepad.app.domain.model.TransmissionMode
import java.nio.ByteBuffer
import java.nio.ByteOrder

enum class PacketMessageType(val wireValue: UByte) {
    HELLO(1u),
    HELLO_ACK(2u),
    CONTROLLER_STATE(3u),
    PING(4u),
    PONG(5u),
    DISCONNECT(6u);

    companion object {
        fun fromWireValue(value: UByte): PacketMessageType? = entries.find { it.wireValue == value }
    }
}

object VehicleButtonFlag {
    const val LIGHTS: UInt = 0x01u
    const val HORN: UInt = 0x02u
    const val WIPERS: UInt = 0x04u
    const val HAZARD_LIGHTS: UInt = 0x08u
    const val LEFT_INDICATOR: UInt = 0x10u
    const val RIGHT_INDICATOR: UInt = 0x20u
    const val CRUISE_CONTROL: UInt = 0x40u
    const val ENGINE: UInt = 0x80u
    const val KNOWN_MASK: UInt = 0xFFu

    fun fromState(state: NormalizedControllerState): UInt {
        var flags = 0u
        if (state.lights) flags = flags or LIGHTS
        if (state.horn) flags = flags or HORN
        if (state.wipers) flags = flags or WIPERS
        if (state.hazardLights) flags = flags or HAZARD_LIGHTS
        if (state.leftIndicator) flags = flags or LEFT_INDICATOR
        if (state.rightIndicator) flags = flags or RIGHT_INDICATOR
        if (state.cruiseControl) flags = flags or CRUISE_CONTROL
        if (state.engine) flags = flags or ENGINE
        return flags
    }
}

/**
 * Fixed 33-byte Little-Endian Binary Packet Layout (Protocol v1):
 *
 * Offset | Size | Type    | Field
 * -------+------+---------+-------------------------
 *   0    |  1   | UInt8   | protocolVersion (1)
 *   1    |  1   | UInt8   | messageType (1..6)
 *   2    |  4   | UInt32  | sequenceNumber
 *   6    |  8   | Int64   | monotonicTimestampNanos
 *  14    |  4   | Float32 | steering (-1.0f..1.0f)
 *  18    |  4   | Float32 | throttle (0.0f or 1.0f)
 *  22    |  4   | Float32 | brake (0.0f or 1.0f)
 *  26    |  1   | UInt8   | handbrake (0 or 1)
 *  27    |  1   | Int8    | currentGear (-2..12)
 *  28    |  1   | UInt8   | transmissionMode (0=AUTO, 1=MANUAL)
 *  29    |  4   | UInt32  | vehicleButtonFlags
 * -------+------+---------+-------------------------
 * Total  | 33 bytes
 */
data class DrivePadPacket(
    val protocolVersion: UByte = PROTOCOL_VERSION_V1,
    val messageType: PacketMessageType,
    val sequenceNumber: UInt,
    val monotonicTimestampNanos: Long,
    val steering: Float = 0.0f,
    val throttle: Float = 0.0f,
    val brake: Float = 0.0f,
    val handbrake: Boolean = false,
    val currentGear: Byte = 1,
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val vehicleButtonFlags: UInt = 0u
) {
    companion object {
        const val PROTOCOL_VERSION_V1: UByte = 1u
        const val PACKET_SIZE_BYTES: Int = 33
        const val MIN_GEAR: Int = -2
        const val MAX_GEAR: Int = 12

        fun fromControllerState(
            state: NormalizedControllerState,
            messageType: PacketMessageType = PacketMessageType.CONTROLLER_STATE,
            sequenceNumber: UInt,
            monotonicTimestampNanos: Long
        ): DrivePadPacket = DrivePadPacket(
            protocolVersion = PROTOCOL_VERSION_V1,
            messageType = messageType,
            sequenceNumber = sequenceNumber,
            monotonicTimestampNanos = monotonicTimestampNanos,
            steering = state.steering,
            throttle = state.throttle,
            brake = state.brake,
            handbrake = state.handbrake,
            currentGear = state.currentGear.coerceIn(MIN_GEAR, MAX_GEAR).toByte(),
            transmissionMode = state.transmissionMode,
            vehicleButtonFlags = VehicleButtonFlag.fromState(state)
        )

        fun controlPacket(
            messageType: PacketMessageType,
            sequenceNumber: UInt,
            monotonicTimestampNanos: Long
        ): DrivePadPacket = DrivePadPacket(
            protocolVersion = PROTOCOL_VERSION_V1,
            messageType = messageType,
            sequenceNumber = sequenceNumber,
            monotonicTimestampNanos = monotonicTimestampNanos
        )
    }
}

sealed class PacketDecodeResult {
    data class Valid(val packet: DrivePadPacket) : PacketDecodeResult()
    data class Invalid(val reason: String) : PacketDecodeResult()
}

object PacketCodec {
    private const val OFFSET_VERSION = 0
    private const val OFFSET_TYPE = 1
    private const val OFFSET_SEQUENCE = 2
    private const val OFFSET_TIMESTAMP = 6
    private const val OFFSET_STEERING = 14
    private const val OFFSET_THROTTLE = 18
    private const val OFFSET_BRAKE = 22
    private const val OFFSET_HANDBRAKE = 26
    private const val OFFSET_GEAR = 27
    private const val OFFSET_TRANSMISSION = 28
    private const val OFFSET_FLAGS = 29

    fun validate(packet: DrivePadPacket): String? {
        if (packet.protocolVersion != DrivePadPacket.PROTOCOL_VERSION_V1) {
            return "Unsupported protocol version: ${packet.protocolVersion}"
        }
        if (packet.steering.isNaN() || packet.steering.isInfinite() || packet.steering !in -1.0f..1.0f) {
            return "Steering out of range [-1.0, 1.0]: ${packet.steering}"
        }
        if (packet.throttle.isNaN() || packet.throttle.isInfinite() || (packet.throttle != 0.0f && packet.throttle != 1.0f)) {
            return "Throttle must be strictly 0.0 or 1.0, was ${packet.throttle}"
        }
        if (packet.brake.isNaN() || packet.brake.isInfinite() || (packet.brake != 0.0f && packet.brake != 1.0f)) {
            return "Brake must be strictly 0.0 or 1.0, was ${packet.brake}"
        }
        if (packet.currentGear < DrivePadPacket.MIN_GEAR || packet.currentGear > DrivePadPacket.MAX_GEAR) {
            return "Gear out of range [${DrivePadPacket.MIN_GEAR}..${DrivePadPacket.MAX_GEAR}]: ${packet.currentGear}"
        }
        if ((packet.vehicleButtonFlags and VehicleButtonFlag.KNOWN_MASK.inv()) != 0u) {
            return "Unknown reserved vehicleButtonFlags bits: 0x${packet.vehicleButtonFlags.toString(16)}"
        }
        return null
    }

    /**
     * Serializes a validated [DrivePadPacket] into a caller-provided [buffer] of at least
     * [DrivePadPacket.PACKET_SIZE_BYTES] bytes using Little-Endian byte order.
     * Avoids per-packet heap allocations on the 250 Hz transmit loop.
     */
    fun encodeInto(packet: DrivePadPacket, buffer: ByteArray): Int {
        require(buffer.size >= DrivePadPacket.PACKET_SIZE_BYTES) {
            "Buffer must be at least ${DrivePadPacket.PACKET_SIZE_BYTES} bytes"
        }
        val validationError = validate(packet)
        require(validationError == null) { "Invalid DrivePadPacket before serialize: $validationError" }

        val bb = ByteBuffer.wrap(buffer, 0, DrivePadPacket.PACKET_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(OFFSET_VERSION, packet.protocolVersion.toByte())
        bb.put(OFFSET_TYPE, packet.messageType.wireValue.toByte())
        bb.putInt(OFFSET_SEQUENCE, packet.sequenceNumber.toInt())
        bb.putLong(OFFSET_TIMESTAMP, packet.monotonicTimestampNanos)
        bb.putFloat(OFFSET_STEERING, packet.steering)
        bb.putFloat(OFFSET_THROTTLE, packet.throttle)
        bb.putFloat(OFFSET_BRAKE, packet.brake)
        bb.put(OFFSET_HANDBRAKE, if (packet.handbrake) 1 else 0)
        bb.put(OFFSET_GEAR, packet.currentGear)
        bb.put(OFFSET_TRANSMISSION, if (packet.transmissionMode == TransmissionMode.MANUAL) 1 else 0)
        bb.putInt(OFFSET_FLAGS, packet.vehicleButtonFlags.toInt())

        return DrivePadPacket.PACKET_SIZE_BYTES
    }

    fun encode(packet: DrivePadPacket): ByteArray {
        val out = ByteArray(DrivePadPacket.PACKET_SIZE_BYTES)
        encodeInto(packet, out)
        return out
    }

    fun decode(bytes: ByteArray, length: Int = bytes.size): PacketDecodeResult {
        if (length != DrivePadPacket.PACKET_SIZE_BYTES || bytes.size < DrivePadPacket.PACKET_SIZE_BYTES) {
            return PacketDecodeResult.Invalid(
                "Invalid packet length: expected ${DrivePadPacket.PACKET_SIZE_BYTES} bytes, got $length"
            )
        }

        val bb = ByteBuffer.wrap(bytes, 0, DrivePadPacket.PACKET_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val version = bb.get(OFFSET_VERSION).toUByte()
        if (version != DrivePadPacket.PROTOCOL_VERSION_V1) {
            return PacketDecodeResult.Invalid("Unsupported protocol version: $version")
        }

        val rawType = bb.get(OFFSET_TYPE).toUByte()
        val messageType = PacketMessageType.fromWireValue(rawType)
            ?: return PacketDecodeResult.Invalid("Unknown message type: $rawType")

        val sequenceNumber = bb.getInt(OFFSET_SEQUENCE).toUInt()
        val timestampNanos = bb.getLong(OFFSET_TIMESTAMP)

        val steering = bb.getFloat(OFFSET_STEERING)
        if (steering.isNaN() || steering.isInfinite() || steering !in -1.0f..1.0f) {
            return PacketDecodeResult.Invalid("Steering out of bounds [-1.0, 1.0]: $steering")
        }

        val throttle = bb.getFloat(OFFSET_THROTTLE)
        if (throttle.isNaN() || throttle.isInfinite() || (throttle != 0.0f && throttle != 1.0f)) {
            return PacketDecodeResult.Invalid("Throttle must be strictly 0.0 or 1.0: $throttle")
        }

        val brake = bb.getFloat(OFFSET_BRAKE)
        if (brake.isNaN() || brake.isInfinite() || (brake != 0.0f && brake != 1.0f)) {
            return PacketDecodeResult.Invalid("Brake must be strictly 0.0 or 1.0: $brake")
        }

        val handbrakeByte = bb.get(OFFSET_HANDBRAKE).toUByte()
        if (handbrakeByte != 0u.toUByte() && handbrakeByte != 1u.toUByte()) {
            return PacketDecodeResult.Invalid("Invalid handbrake flag byte: $handbrakeByte")
        }

        val gear = bb.get(OFFSET_GEAR)
        if (gear < DrivePadPacket.MIN_GEAR || gear > DrivePadPacket.MAX_GEAR) {
            return PacketDecodeResult.Invalid("Gear out of bounds [-2, 12]: $gear")
        }

        val transByte = bb.get(OFFSET_TRANSMISSION).toUByte()
        val transmissionMode = when (transByte.toInt()) {
            0 -> TransmissionMode.AUTO
            1 -> TransmissionMode.MANUAL
            else -> return PacketDecodeResult.Invalid("Invalid transmission mode byte: $transByte")
        }

        val flags = bb.getInt(OFFSET_FLAGS).toUInt()
        if ((flags and VehicleButtonFlag.KNOWN_MASK.inv()) != 0u) {
            return PacketDecodeResult.Invalid("Invalid reserved button flag bits: 0x${flags.toString(16)}")
        }

        val packet = DrivePadPacket(
            protocolVersion = version,
            messageType = messageType,
            sequenceNumber = sequenceNumber,
            monotonicTimestampNanos = timestampNanos,
            steering = steering,
            throttle = throttle,
            brake = brake,
            handbrake = handbrakeByte == 1u.toUByte(),
            currentGear = gear,
            transmissionMode = transmissionMode,
            vehicleButtonFlags = flags
        )
        return PacketDecodeResult.Valid(packet)
    }
}
