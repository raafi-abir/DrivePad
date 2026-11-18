package com.drivepad.companion.protocol

object ProtocolConstants {
    const val PROTOCOL_VERSION_V1: Int = 1
    const val PACKET_SIZE_BYTES: Int = 33
    const val MIN_GEAR: Int = -2
    const val MAX_GEAR: Int = 12
    const val KNOWN_FLAGS_MASK: UInt = 0xFFu
}

enum class PacketMessageType(val wireValue: Int) {
    HELLO(1),
    HELLO_ACK(2),
    CONTROLLER_STATE(3),
    PING(4),
    PONG(5),
    DISCONNECT(6);

    companion object {
        fun fromWireValue(value: Int): PacketMessageType? = when (value) {
            1 -> HELLO
            2 -> HELLO_ACK
            3 -> CONTROLLER_STATE
            4 -> PING
            5 -> PONG
            6 -> DISCONNECT
            else -> null
        }
    }
}

enum class TransmissionMode(val wireValue: Int, val displayName: String) {
    AUTO(0, "Automatic"),
    MANUAL(1, "Manual Sequential");

    companion object {
        fun fromWireValue(value: Int): TransmissionMode? = when (value) {
            0 -> AUTO
            1 -> MANUAL
            else -> null
        }
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
}

data class DecodedPacket(
    val protocolVersion: Int,
    val messageType: PacketMessageType,
    val sequenceNumber: Long,
    val monotonicTimestampNanos: Long,
    val steering: Float,
    val throttle: Float,
    val brake: Float,
    val handbrake: Boolean,
    val currentGear: Int,
    val transmissionMode: TransmissionMode,
    val vehicleButtonFlags: UInt
)
