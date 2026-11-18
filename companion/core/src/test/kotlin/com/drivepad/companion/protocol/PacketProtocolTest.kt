package com.drivepad.companion.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PacketProtocolTest {

    private fun buildRawPacket(
        version: Byte = 1,
        messageType: Byte = 3,
        sequence: Int = 42,
        timestampNanos: Long = 123_456_789L,
        steering: Float = 0.0f,
        throttle: Float = 0.0f,
        brake: Float = 0.0f,
        handbrake: Byte = 0,
        gear: Byte = 1,
        transmission: Byte = 0,
        flags: Int = 0
    ): ByteArray {
        val buf = ByteArray(ProtocolConstants.PACKET_SIZE_BYTES)
        val bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0, version)
        bb.put(1, messageType)
        bb.putInt(2, sequence)
        bb.putLong(6, timestampNanos)
        bb.putFloat(14, steering)
        bb.putFloat(18, throttle)
        bb.putFloat(22, brake)
        bb.put(26, handbrake)
        bb.put(27, gear)
        bb.put(28, transmission)
        bb.putInt(29, flags)
        return buf
    }

    @Test
    fun `decodes exact 33-byte Little-Endian Android CONTROLLER_STATE packet`() {
        val raw = buildRawPacket(
            version = 1,
            messageType = PacketMessageType.CONTROLLER_STATE.wireValue.toByte(),
            sequence = 9_876,
            timestampNanos = 55_000_111_222L,
            steering = -0.625f,
            throttle = 1.0f,
            brake = 0.0f,
            handbrake = 1,
            gear = -1, // Reverse
            transmission = 1, // Manual
            flags = (VehicleButtonFlag.LIGHTS or VehicleButtonFlag.LEFT_INDICATOR or VehicleButtonFlag.ENGINE).toInt()
        )

        val result = PacketDecoder.decode(raw, raw.size)
        assertTrue(result is PacketDecodeResult.Valid)
        val pkt = (result as PacketDecodeResult.Valid).packet

        assertEquals(1, pkt.protocolVersion)
        assertEquals(PacketMessageType.CONTROLLER_STATE, pkt.messageType)
        assertEquals(9_876L, pkt.sequenceNumber)
        assertEquals(55_000_111_222L, pkt.monotonicTimestampNanos)
        assertEquals(-0.625f, pkt.steering, 0.0001f)
        assertEquals(1.0f, pkt.throttle, 0.0f)
        assertEquals(0.0f, pkt.brake, 0.0f)
        assertTrue(pkt.handbrake)
        assertEquals(-1, pkt.currentGear)
        assertEquals(TransmissionMode.MANUAL, pkt.transmissionMode)
        assertEquals(
            VehicleButtonFlag.LIGHTS or VehicleButtonFlag.LEFT_INDICATOR or VehicleButtonFlag.ENGINE,
            pkt.vehicleButtonFlags
        )
    }

    @Test
    fun `decodes unsigned 32-bit high sequence numbers cleanly`() {
        val raw = buildRawPacket(sequence = 0xFFFFFFFF.toInt())
        val res = PacketDecoder.decode(raw) as PacketDecodeResult.Valid
        assertEquals(0xFFFFFFFFL, res.packet.sequenceNumber)
    }

    @Test
    fun `encodes HELLO_ACK and PONG replies echoing exact monotonic timestamp`() {
        val out = ByteArray(ProtocolConstants.PACKET_SIZE_BYTES)
        val echoedTs = 987_654_321_012L
        val len = PacketDecoder.encodeReplyInto(
            messageType = PacketMessageType.PONG,
            sequenceNumber = 7L,
            echoedTimestampNanos = echoedTs,
            outBuffer = out
        )

        assertEquals(33, len)
        val decoded = PacketDecoder.decode(out, len) as PacketDecodeResult.Valid
        assertEquals(PacketMessageType.PONG, decoded.packet.messageType)
        assertEquals(7L, decoded.packet.sequenceNumber)
        assertEquals(echoedTs, decoded.packet.monotonicTimestampNanos)
        assertEquals(0.0f, decoded.packet.steering, 0.0f)
        assertFalse(decoded.packet.handbrake)
    }

    @Test
    fun `rejects packets with incorrect byte lengths`() {
        assertTrue(PacketDecoder.decode(ByteArray(0), 0) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(ByteArray(32), 32) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(ByteArray(34), 34) is PacketDecodeResult.Invalid)
    }

    @Test
    fun `rejects unsupported protocol version and unknown message types`() {
        val badVer = buildRawPacket(version = 2)
        assertTrue(PacketDecoder.decode(badVer) is PacketDecodeResult.Invalid)

        val badType = buildRawPacket(messageType = 99)
        assertTrue(PacketDecoder.decode(badType) is PacketDecodeResult.Invalid)
    }

    @Test
    fun `rejects NaN Infinite and out-of-range steering`() {
        assertTrue(PacketDecoder.decode(buildRawPacket(steering = Float.NaN)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(steering = Float.POSITIVE_INFINITY)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(steering = -1.05f)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(steering = 1.05f)) is PacketDecodeResult.Invalid)
    }

    @Test
    fun `enforces strictly digital throttle and brake pedals`() {
        assertTrue(PacketDecoder.decode(buildRawPacket(throttle = 0.5f)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(throttle = -0.1f)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(throttle = Float.NaN)) is PacketDecodeResult.Invalid)

        assertTrue(PacketDecoder.decode(buildRawPacket(brake = 0.25f)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(brake = 1.1f)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(brake = Float.NEGATIVE_INFINITY)) is PacketDecodeResult.Invalid)

        assertTrue(PacketDecoder.decode(buildRawPacket(throttle = 1.0f, brake = 1.0f)) is PacketDecodeResult.Valid)
    }

    @Test
    fun `rejects invalid handbrake gear transmission and reserved button flags`() {
        assertTrue(PacketDecoder.decode(buildRawPacket(handbrake = 2)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(gear = -3)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(gear = 13)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(transmission = 2)) is PacketDecodeResult.Invalid)
        assertTrue(PacketDecoder.decode(buildRawPacket(flags = 0x100)) is PacketDecodeResult.Invalid)

        assertTrue(PacketDecoder.decode(buildRawPacket(gear = -2)) is PacketDecodeResult.Valid)
        assertTrue(PacketDecoder.decode(buildRawPacket(gear = 12)) is PacketDecodeResult.Valid)
    }
}
