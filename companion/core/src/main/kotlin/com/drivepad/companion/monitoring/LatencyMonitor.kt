package com.drivepad.companion.monitoring

import kotlin.math.abs

data class LatencySnapshot(
    val averageIntervalMs: Double = 0.0,
    val actualUpdateHz: Double = 0.0,
    val interArrivalJitterMs: Double = 0.0,
    val lastPacketMonotonicNanos: Long = 0L,
    val lastPacketAgeMs: Long? = null,
    val rttMs: Double? = null,
    val rttNote: String = "Echoed via PONG (sender-measured RTT)"
)

/**
 * Computes monotonic inter-arrival interval, update frequency (Hz), and RFC3550-style
 * inter-arrival jitter. Never relies on wall-clock timestamps and never mislabels RTT
 * as one-way latency.
 */
class LatencyMonitor {
    private val lock = Any()

    private var lastArrivalNs: Long = 0L
    private var avgIntervalNs: Double = 0.0
    private var jitterNs: Double = 0.0

    private var rateWindowStartNs: Long = 0L
    private var rateWindowPackets: Long = 0L
    private var measuredHz: Double = 0.0

    fun recordPacketArrival(arrivalNanos: Long) {
        synchronized(lock) {
            if (rateWindowStartNs == 0L) {
                rateWindowStartNs = arrivalNanos
            }
            rateWindowPackets++
            val windowElapsedNs = arrivalNanos - rateWindowStartNs
            if (windowElapsedNs >= 500_000_000L) {
                measuredHz = rateWindowPackets.toDouble() * 1_000_000_000.0 / windowElapsedNs.toDouble()
                rateWindowStartNs = arrivalNanos
                rateWindowPackets = 0L
            }

            val prev = lastArrivalNs
            if (prev > 0L && arrivalNanos >= prev) {
                val intervalNs = (arrivalNanos - prev).toDouble()
                if (avgIntervalNs <= 0.0) {
                    avgIntervalNs = intervalNs
                } else {
                    val delta = abs(intervalNs - avgIntervalNs)
                    // RFC 3550 smoothed jitter estimator
                    jitterNs += (delta - jitterNs) / 16.0
                    avgIntervalNs = (avgIntervalNs * 0.9) + (intervalNs * 0.1)
                }
            }
            lastArrivalNs = arrivalNanos
        }
    }

    fun resetRateWindow() {
        synchronized(lock) {
            lastArrivalNs = 0L
            avgIntervalNs = 0.0
            jitterNs = 0.0
            rateWindowStartNs = 0L
            rateWindowPackets = 0L
            measuredHz = 0.0
        }
    }

    fun snapshot(nowNanos: Long): LatencySnapshot = synchronized(lock) {
        val last = lastArrivalNs
        val ageMs = if (last > 0L && nowNanos >= last) (nowNanos - last) / 1_000_000L else null
        // Decay reported Hz cleanly if packets stopped arriving past 1.5s
        val effectiveHz = if (ageMs != null && ageMs > 1_500L) 0.0 else measuredHz
        LatencySnapshot(
            averageIntervalMs = avgIntervalNs / 1_000_000.0,
            actualUpdateHz = effectiveHz,
            interArrivalJitterMs = jitterNs / 1_000_000.0,
            lastPacketMonotonicNanos = last,
            lastPacketAgeMs = ageMs,
            rttMs = null,
            rttNote = "Echoed via PONG (sender-measured RTT)"
        )
    }
}
