package com.drivepad.companion.monitoring

data class PacketStatsSnapshot(
    val totalPacketsReceived: Long = 0L,
    val validPackets: Long = 0L,
    val invalidPackets: Long = 0L,
    val sequenceGaps: Long = 0L,
    val duplicatePackets: Long = 0L,
    val outOfOrderPackets: Long = 0L,
    val estimatedPacketLossPercent: Double = 0.0
)

/**
 * Tracks UDP packet reception quality using a 64-bit sliding sequence bitmap to
 * detect in-order progression, sequence gaps (estimated loss), duplicate packets,
 * and late out-of-order arrivals with zero heap allocations on the 250 Hz hot path.
 *
 * Note: Because UDP has no delivery confirmation from sender to receiver, missing
 * sequence numbers represent an *estimate* of packet loss rather than confirmed loss.
 */
class PacketStatistics {
    private val lock = Any()

    private var totalReceived: Long = 0L
    private var validCount: Long = 0L
    private var invalidCount: Long = 0L
    private var gapCount: Long = 0L
    private var duplicateCount: Long = 0L
    private var outOfOrderCount: Long = 0L

    private var highestSeq: Long? = null
    private var seenWindowMask: Long = 0L

    fun recordInvalidPacket() {
        synchronized(lock) {
            totalReceived++
            invalidCount++
        }
    }

    fun recordValidPacket(sequenceNumber: Long) {
        synchronized(lock) {
            totalReceived++
            validCount++
            trackSequenceLocked(sequenceNumber)
        }
    }

    fun resetSessionSequenceTracking() {
        synchronized(lock) {
            highestSeq = null
            seenWindowMask = 0L
        }
    }

    fun resetAll() {
        synchronized(lock) {
            totalReceived = 0L
            validCount = 0L
            invalidCount = 0L
            gapCount = 0L
            duplicateCount = 0L
            outOfOrderCount = 0L
            highestSeq = null
            seenWindowMask = 0L
        }
    }

    private fun trackSequenceLocked(seq: Long) {
        val currentHigh = highestSeq
        if (currentHigh == null) {
            highestSeq = seq
            seenWindowMask = 1L
            return
        }

        // Support 32-bit unsigned sequence wrap-around cleanly
        val signedDiff = ((seq - currentHigh).toInt()).toLong()
        when {
            signedDiff == 0L -> {
                duplicateCount++
            }
            signedDiff > 0L -> {
                if (signedDiff > 1L) {
                    gapCount += (signedDiff - 1L)
                }
                seenWindowMask = if (signedDiff >= 64L) {
                    1L
                } else {
                    (seenWindowMask shl signedDiff.toInt()) or 1L
                }
                highestSeq = seq
            }
            else -> {
                // signedDiff < 0 -> packet older than highestSeq
                val age = (-signedDiff).toInt()
                if (age < 64) {
                    val bit = 1L shl age
                    if ((seenWindowMask and bit) != 0L) {
                        duplicateCount++
                    } else {
                        seenWindowMask = seenWindowMask or bit
                        outOfOrderCount++
                        if (gapCount > 0L) {
                            gapCount--
                        }
                    }
                } else {
                    outOfOrderCount++
                }
            }
        }
    }

    fun snapshot(): PacketStatsSnapshot = synchronized(lock) {
        // Estimated loss relative to expected unique in-session sequence count (valid unique + gaps)
        val uniqueValid = (validCount - duplicateCount).coerceAtLeast(0L)
        val totalExpected = uniqueValid + gapCount
        val estimatedLossPct = if (totalExpected > 0L) {
            ((gapCount.toDouble() / totalExpected.toDouble()) * 100.0).coerceIn(0.0, 100.0)
        } else {
            0.0
        }
        PacketStatsSnapshot(
            totalPacketsReceived = totalReceived,
            validPackets = validCount,
            invalidPackets = invalidCount,
            sequenceGaps = gapCount,
            duplicatePackets = duplicateCount,
            outOfOrderPackets = outOfOrderCount,
            estimatedPacketLossPercent = estimatedLossPct
        )
    }
}
