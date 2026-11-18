package com.drivepad.companion.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PacketStatisticsAndLatencyTest {

    @Test
    fun `tracks contiguous sequences cleanly across 32-bit unsigned wraparound`() {
        val stats = PacketStatistics()
        stats.recordValidPacket(0xFFFFFFFEL)
        stats.recordValidPacket(0xFFFFFFFFL)
        stats.recordValidPacket(0L)
        stats.recordValidPacket(1L)

        val snap = stats.snapshot()
        assertEquals(4L, snap.totalPacketsReceived)
        assertEquals(4L, snap.validPackets)
        assertEquals(0L, snap.sequenceGaps)
        assertEquals(0L, snap.duplicatePackets)
        assertEquals(0L, snap.outOfOrderPackets)
        assertEquals(0.0, snap.estimatedPacketLossPercent, 0.0001)
    }

    @Test
    fun `detects sequence gaps duplicate packets and late out-of-order arrivals`() {
        val stats = PacketStatistics()
        stats.recordValidPacket(1L)
        stats.recordValidPacket(2L)
        // Skip seq 3 -> gap of 1
        stats.recordValidPacket(4L)
        // Duplicate seq 4
        stats.recordValidPacket(4L)
        // Late out-of-order seq 3 arrives inside 64-packet window -> fills previous gap
        stats.recordValidPacket(3L)
        // Duplicate seq 3 arrives
        stats.recordValidPacket(3L)
        // Skip seq 5 and 6 -> gap of 2
        stats.recordValidPacket(7L)

        val snap = stats.snapshot()
        assertEquals(7L, snap.totalPacketsReceived)
        assertEquals(7L, snap.validPackets)
        assertEquals(2L, snap.duplicatePackets)
        assertEquals(1L, snap.outOfOrderPackets)
        assertEquals(2L, snap.sequenceGaps)

        // Unique valid packets = 5 (1,2,3,4,7), gaps = 2 => 2 / 7 * 100 = 28.57%
        assertEquals((2.0 / 7.0) * 100.0, snap.estimatedPacketLossPercent, 0.05)
    }

    @Test
    fun `tracks invalid packets separately from sequence loss`() {
        val stats = PacketStatistics()
        stats.recordValidPacket(1L)
        stats.recordInvalidPacket()
        stats.recordValidPacket(2L)

        val snap = stats.snapshot()
        assertEquals(3L, snap.totalPacketsReceived)
        assertEquals(2L, snap.validPackets)
        assertEquals(1L, snap.invalidPackets)
        assertEquals(0L, snap.sequenceGaps)
    }

    @Test
    fun `latency monitor computes update rate jitter and decays Hz after silence`() {
        val monitor = LatencyMonitor()
        var t = 1_000_000_000L

        // Feed 250 packets at exact 4 ms (250 Hz) intervals for 1 second
        repeat(251) {
            monitor.recordPacketArrival(t)
            t += 4_000_000L
        }

        val activeSnap = monitor.snapshot(t)
        assertEquals(4.0, activeSnap.averageIntervalMs, 0.1)
        assertEquals(250.0, activeSnap.actualUpdateHz, 5.0)
        assertTrue(activeSnap.interArrivalJitterMs < 0.05)
        assertEquals(4L, activeSnap.lastPacketAgeMs)
        assertNull(activeSnap.rttMs)
        assertTrue(activeSnap.rttNote.contains("PONG"))

        // After 2 seconds of silence, actual Hz decays cleanly to 0
        val idleSnap = monitor.snapshot(t + 2_000_000_000L)
        assertEquals(0.0, idleSnap.actualUpdateHz, 0.0)
        assertTrue((idleSnap.lastPacketAgeMs ?: 0L) >= 2_000L)
    }
}
