package com.drivepad.companion.safety

import com.drivepad.companion.diagnostics.MonotonicClock
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.diagnostics.SystemMonotonicClock
import com.drivepad.companion.domain.ControllerStateProcessor
import com.drivepad.companion.monitoring.ConnectionMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Independent monotonic-clock watchdog. Monitors time elapsed since the last valid
 * packet from the active Android controller. If packets stop beyond [timeoutMs]
 * (e.g., app crash, Wi-Fi/USB cable unplugged, phone power loss), the watchdog:
 * 1. Marks the connection disconnected/failsafe-timed-out.
 * 2. Resets steering, digital throttle, digital brake, handbrake, buttons, and gear to safe neutral.
 * 3. Publishes the safe [ControllerState] through [ControllerStateProcessor].
 */
class InputWatchdog(
    private val timeoutMs: Long,
    private val stateProcessor: ControllerStateProcessor,
    private val connectionMonitor: ConnectionMonitor,
    private val onSessionTimedOut: () -> Unit,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val logger: StructuredLogger = StructuredLogger(),
    private val checkIntervalMs: Long = 25L
) {
    private val timeoutNs: Long = timeoutMs.coerceAtLeast(100L) * 1_000_000L

    @Volatile
    private var armed: Boolean = false

    @Volatile
    private var lastActivityNanos: Long = 0L

    @Volatile
    private var checkJob: Job? = null

    fun arm(nowNanos: Long = clock.nanoTime()) {
        lastActivityNanos = nowNanos
        armed = true
    }

    fun feed(nowNanos: Long = clock.nanoTime()) {
        lastActivityNanos = nowNanos
    }

    fun disarm() {
        armed = false
    }

    fun isArmed(): Boolean = armed

    fun start(scope: CoroutineScope): Job {
        checkJob?.cancel()
        val job = scope.launch {
            while (isActive) {
                evaluateNow()
                delay(checkIntervalMs)
            }
        }
        checkJob = job
        return job
    }

    fun stop() {
        checkJob?.cancel()
        checkJob = null
        armed = false
    }

    /**
     * Evaluates timeout deterministically against [nowNanos]; returns true if failsafe triggered.
     */
    fun evaluateNow(nowNanos: Long = clock.nanoTime()): Boolean {
        if (!armed) return false
        val elapsedNs = nowNanos - lastActivityNanos
        if (elapsedNs > timeoutNs) {
            armed = false
            onSessionTimedOut()
            connectionMonitor.onWatchdogFailsafeTriggered()
            stateProcessor.resetToFailsafe(nowNanos)
            logger.warn(
                "InputWatchdog",
                "Failsafe triggered: packets silent beyond timeout",
                "elapsed_ms" to (elapsedNs / 1_000_000L),
                "timeout_ms" to timeoutMs
            )
            return true
        }
        return false
    }
}
