package com.drivepad.companion.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

sealed class PacketDecodeResult {
    data class Valid(val packet: DecodedPacket) : PacketDecodeResult()
    data class Invalid(val reason: String) : PacketDecodeResult()
}

/**
 * Decodes and encodes the exact Phase 4 33-byte Little-Endian binary UDP packet format:
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
 */
object PacketDecoder {
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

    fun decode(bytes: ByteArray, length: Int = bytes.size): PacketDecodeResult {
        when (val sizeCheck = PacketValidator.validateRawLength(bytes, length)) {
            is ValidationOutcome.Invalid -> return PacketDecodeResult.Invalid(sizeCheck.reason)
            ValidationOutcome.Valid -> Unit
        }

        val bb = ByteBuffer.wrap(bytes, 0, ProtocolConstants.PACKET_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        val version = bb.get(OFFSET_VERSION).toInt() and 0xFF
        val rawType = bb.get(OFFSET_TYPE).toInt() and 0xFF
        val sequenceNumber = bb.getInt(OFFSET_SEQUENCE).toLong() and 0xFFFFFFFFL
        val timestampNanos = bb.getLong(OFFSET_TIMESTAMP)
        val steering = bb.getFloat(OFFSET_STEERING)
        val throttle = bb.getFloat(OFFSET_THROTTLE)
        val brake = bb.getFloat(OFFSET_BRAKE)
        val handbrakeByte = bb.get(OFFSET_HANDBRAKE).toInt() and 0xFF
        val gear = bb.get(OFFSET_GEAR).toInt() // Signed Int8 [-2..12]
        val transByte = bb.get(OFFSET_TRANSMISSION).toInt() and 0xFF
        val flags = bb.getInt(OFFSET_FLAGS).toUInt()

        when (
            val fieldCheck = PacketValidator.validateFields(
                protocolVersion = version,
                rawMessageType = rawType,
                steering = steering,
                throttle = throttle,
                brake = brake,
                handbrakeByte = handbrakeByte,
                gear = gear,
                transmissionByte = transByte,
                buttonFlags = flags
            )
        ) {
            is ValidationOutcome.Invalid -> return PacketDecodeResult.Invalid(fieldCheck.reason)
            ValidationOutcome.Valid -> Unit
        }

        val messageType = PacketMessageType.fromWireValue(rawType)!!
        val transmissionMode = TransmissionMode.fromWireValue(transByte)!!

        return PacketDecodeResult.Valid(
            DecodedPacket(
                protocolVersion = version,
                messageType = messageType,
                sequenceNumber = sequenceNumber,
                monotonicTimestampNanos = timestampNanos,
                steering = steering,
                throttle = throttle,
                brake = brake,
                handbrake = handbrakeByte == 1,
                currentGear = gear,
                transmissionMode = transmissionMode,
                vehicleButtonFlags = flags
            )
        )
    }

    /**
     * Encodes a reply control packet (`HELLO_ACK`, `PONG`, `DISCONNECT`) into [outBuffer],
     * echoing [echoedTimestampNanos] so the Android sender can compute accurate Round-Trip Time.
     */
    fun encodeReplyInto(
        messageType: PacketMessageType,
        sequenceNumber: Long,
        echoedTimestampNanos: Long,
        outBuffer: ByteArray
    ): Int {
        require(outBuffer.size >= ProtocolConstants.PACKET_SIZE_BYTES) {
            "Output buffer must be at least ${ProtocolConstants.PACKET_SIZE_BYTES} bytes"
        }
        val bb = ByteBuffer.wrap(outBuffer, 0, ProtocolConstants.PACKET_SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(OFFSET_VERSION, ProtocolConstants.PROTOCOL_VERSION_V1.toByte())
        bb.put(OFFSET_TYPE, messageType.wireValue.toByte())
        bb.putInt(OFFSET_SEQUENCE, (sequenceNumber and 0xFFFFFFFFL).toInt())
        bb.putLong(OFFSET_TIMESTAMP, echoedTimestampNanos)
        bb.putFloat(OFFSET_STEERING, 0.0f)
        bb.putFloat(OFFSET_THROTTLE, 0.0f)
        bb.putFloat(OFFSET_BRAKE, 0.0f)
        bb.put(OFFSET_HANDBRAKE, 0)
        bb.put(OFFSET_GEAR, 0)
        bb.put(OFFSET_TRANSMISSION, 0)
        bb.putInt(OFFSET_FLAGS, 0)
        return ProtocolConstants.PACKET_SIZE_BYTES
    }
}
