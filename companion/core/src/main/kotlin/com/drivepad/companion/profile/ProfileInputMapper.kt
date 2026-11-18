package com.drivepad.companion.profile

import com.drivepad.companion.diagnostics.MonotonicClock
import com.drivepad.companion.diagnostics.SystemMonotonicClock
import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.protocol.TransmissionMode
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

/**
 * Pure, stateless steering calibration and curve transform applied per [ControllerProfile].
 * Never adds temporal smoothing lag so responsive steering stays immediate.
 */
object SteeringTransform {
    fun apply(rawSteering: Float, profile: ControllerProfile): Float {
        if (rawSteering.isNaN() || rawSteering.isInfinite()) return 0.0f
        val clamped = rawSteering.coerceIn(-1.0f, 1.0f)
        val absSteer = abs(clamped)
        val deadzone = profile.steeringDeadzone

        if (absSteer <= deadzone) return 0.0f

        val rescaled = ((absSteer - deadzone) / (1.0f - deadzone)).coerceIn(0.0f, 1.0f)
        val curved = when (profile.steeringCurve) {
            SteeringCurve.LINEAR -> rescaled
            SteeringCurve.PROGRESSIVE -> rescaled.toDouble().pow(profile.steeringCurveGamma.toDouble()).toFloat()
        }
        val scaled = (curved * profile.steeringSensitivity).coerceIn(0.0f, 1.0f)
        val signed = sign(clamped) * scaled
        val output = if (profile.steeringInvert) -signed else signed
        return if (output == 0.0f) 0.0f else output.coerceIn(-1.0f, 1.0f)
    }
}

/**
 * Platform-independent normalized controller output produced by [ProfileInputMapper].
 * Platform backends (`:linux` uinput / `:windows` ViGEm X360) translate this output to
 * their native axis scales and button bitmasks.
 */
data class NormalizedGamepadOutput(
    val steeringNormalized: Float,
    val throttleActive: Boolean,
    val brakeActive: Boolean,
    val buttons: BooleanArray
) {
    val steeringNeutral: Boolean get() = steeringNormalized == 0.0f
    val throttleNormalized: Float get() = if (throttleActive) 1.0f else 0.0f
    val brakeNormalized: Float get() = if (brakeActive) 1.0f else 0.0f
}

/**
 * Deterministic stateful mapper converting DrivePad [ControllerState] snapshots into platform-neutral
 * normalized gamepad outputs according to a [ControllerProfile].
 */
class ProfileInputMapper(
    val profile: ControllerProfile = ProfilePresets.generic()
) {
    private val orderedButtons: List<LogicalButton> = LogicalButton.entries
    private val pulseDurationNs: Long = profile.pulseDurationMs * 1_000_000L
    private val pulseGapNs: Long = profile.pulseGapMs * 1_000_000L

    private val previousToggleValues = BooleanArray(orderedButtons.size)
    private val pulseReleaseDeadlinesNs = LongArray(orderedButtons.size)

    private var lastEffectiveGear: Int? = null
    private var queuedShiftSteps: Int = 0
    private var shiftPhase: ShiftPhase = ShiftPhase.IDLE
    private var shiftTimerDeadlineNs: Long = 0L

    private enum class ShiftPhase {
        IDLE,
        PULSING_UP,
        PULSING_DOWN,
        GAP
    }

    fun reset(): NormalizedGamepadOutput {
        previousToggleValues.fill(false)
        pulseReleaseDeadlinesNs.fill(0L)
        lastEffectiveGear = null
        queuedShiftSteps = 0
        shiftPhase = ShiftPhase.IDLE
        shiftTimerDeadlineNs = 0L

        return neutralOutput()
    }

    fun processState(state: ControllerState, nowNanos: Long): NormalizedGamepadOutput {
        if (state.isSafeNeutral) {
            return reset()
        }

        val transformedSteering = SteeringTransform.apply(state.steering, profile)
        val throttlePressed = state.throttle >= 1.0f
        val brakePressed = state.brake >= 1.0f
        val throttleEffective = if (profile.throttleInvert) !throttlePressed else throttlePressed
        val brakeEffective = if (profile.brakeInvert) !brakePressed else brakePressed

        for (i in orderedButtons.indices) {
            val logical = orderedButtons[i]
            if (logical == LogicalButton.SHIFT_UP || logical == LogicalButton.SHIFT_DOWN) continue

            val binding = profile.buttonBindings.getValue(logical)
            val currentActive = logical.readState(state)
            val previousActive = previousToggleValues[i]

            when (binding.behavior) {
                ButtonBehavior.HOLD -> pulseReleaseDeadlinesNs[i] = 0L
                ButtonBehavior.PULSE_ON_CHANGE -> {
                    if (currentActive != previousActive) {
                        pulseReleaseDeadlinesNs[i] = nowNanos + pulseDurationNs
                    }
                }
                ButtonBehavior.PULSE_ON_PRESS -> {
                    if (!previousActive && currentActive) {
                        pulseReleaseDeadlinesNs[i] = nowNanos + pulseDurationNs
                    }
                }
            }
            previousToggleValues[i] = currentActive
        }

        val effectiveGear = effectiveGearForProfile(state)
        val prevGear = lastEffectiveGear
        if (prevGear != null && effectiveGear != prevGear) {
            val delta = effectiveGear - prevGear
            val stepCount = if (profile.multiStepAutoShift && state.transmissionMode == TransmissionMode.AUTO) {
                delta
            } else {
                if (delta > 0) 1 else -1
            }
            queuedShiftSteps += stepCount
        }
        lastEffectiveGear = effectiveGear

        advanceShiftStateMachine(nowNanos)

        return buildOutput(transformedSteering, throttleEffective, brakeEffective, state, nowNanos)
    }

    fun onTimerTick(lastState: ControllerState?, nowNanos: Long): NormalizedGamepadOutput {
        if (lastState == null || lastState.isSafeNeutral) {
            return reset()
        }
        val transformedSteering = SteeringTransform.apply(lastState.steering, profile)
        val throttlePressed = lastState.throttle >= 1.0f
        val brakePressed = lastState.brake >= 1.0f
        val throttleEffective = if (profile.throttleInvert) !throttlePressed else throttlePressed
        val brakeEffective = if (profile.brakeInvert) !brakePressed else brakePressed

        for (i in pulseReleaseDeadlinesNs.indices) {
            if (pulseReleaseDeadlinesNs[i] != 0L && nowNanos >= pulseReleaseDeadlinesNs[i]) {
                pulseReleaseDeadlinesNs[i] = 0L
            }
        }
        advanceShiftStateMachine(nowNanos)

        return buildOutput(transformedSteering, throttleEffective, brakeEffective, lastState, nowNanos)
    }

    fun nextTimerDeadlineNanos(): Long? {
        var earliest: Long? = null
        for (deadline in pulseReleaseDeadlinesNs) {
            if (deadline > 0L && (earliest == null || deadline < earliest)) {
                earliest = deadline
            }
        }
        if (shiftPhase != ShiftPhase.IDLE && shiftTimerDeadlineNs > 0L) {
            if (earliest == null || shiftTimerDeadlineNs < earliest) {
                earliest = shiftTimerDeadlineNs
            }
        }
        return earliest
    }

    fun hasPendingTimers(): Boolean = nextTimerDeadlineNanos() != null || queuedShiftSteps != 0

    private fun advanceShiftStateMachine(nowNanos: Long) {
        when (shiftPhase) {
            ShiftPhase.PULSING_UP, ShiftPhase.PULSING_DOWN -> {
                if (nowNanos >= shiftTimerDeadlineNs) {
                    if (queuedShiftSteps != 0) {
                        shiftPhase = ShiftPhase.GAP
                        shiftTimerDeadlineNs = nowNanos + pulseGapNs
                    } else {
                        shiftPhase = ShiftPhase.IDLE
                        shiftTimerDeadlineNs = 0L
                    }
                }
            }
            ShiftPhase.GAP -> {
                if (nowNanos >= shiftTimerDeadlineNs) {
                    startNextShiftPulseIfQueued(nowNanos)
                }
            }
            ShiftPhase.IDLE -> {
                startNextShiftPulseIfQueued(nowNanos)
            }
        }
    }

    private fun startNextShiftPulseIfQueued(nowNanos: Long) {
        when {
            queuedShiftSteps > 0 -> {
                queuedShiftSteps -= 1
                shiftPhase = ShiftPhase.PULSING_UP
                shiftTimerDeadlineNs = nowNanos + pulseDurationNs
            }
            queuedShiftSteps < 0 -> {
                queuedShiftSteps += 1
                shiftPhase = ShiftPhase.PULSING_DOWN
                shiftTimerDeadlineNs = nowNanos + pulseDurationNs
            }
            else -> {
                shiftPhase = ShiftPhase.IDLE
                shiftTimerDeadlineNs = 0L
            }
        }
    }

    private fun buildOutput(
        steeringNormalized: Float,
        throttleActive: Boolean,
        brakeActive: Boolean,
        state: ControllerState,
        nowNanos: Long
    ): NormalizedGamepadOutput {
        val buttons = BooleanArray(orderedButtons.size)
        for (i in orderedButtons.indices) {
            val logical = orderedButtons[i]
            val binding = profile.buttonBindings.getValue(logical)
            buttons[i] = when (logical) {
                LogicalButton.SHIFT_UP -> shiftPhase == ShiftPhase.PULSING_UP
                LogicalButton.SHIFT_DOWN -> shiftPhase == ShiftPhase.PULSING_DOWN
                else -> when (binding.behavior) {
                    ButtonBehavior.HOLD -> logical.readState(state)
                    ButtonBehavior.PULSE_ON_CHANGE,
                    ButtonBehavior.PULSE_ON_PRESS -> {
                        val deadline = pulseReleaseDeadlinesNs[i]
                        if (deadline != 0L && nowNanos < deadline) {
                            true
                        } else {
                            pulseReleaseDeadlinesNs[i] = 0L
                            false
                        }
                    }
                }
            }
        }
        return NormalizedGamepadOutput(
            steeringNormalized = steeringNormalized,
            throttleActive = throttleActive,
            brakeActive = brakeActive,
            buttons = buttons
        )
    }

    private fun effectiveGearForProfile(state: ControllerState): Int {
        if (profile.clampParkToNeutralInAuto &&
            state.transmissionMode == TransmissionMode.AUTO &&
            state.currentGear < -1
        ) {
            return 0
        }
        return state.currentGear
    }

    fun neutralOutput(): NormalizedGamepadOutput = NormalizedGamepadOutput(
        steeringNormalized = 0.0f,
        throttleActive = if (!profile.throttleAxis.isSignedFullAxis) profile.throttleInvert else false,
        brakeActive = if (!profile.brakeAxis.isSignedFullAxis) profile.brakeInvert else false,
        buttons = BooleanArray(orderedButtons.size)
    )
}

/**
 * Shared single-writer-thread async scheduler for platform backends (`:linux` uinput & `:windows` ViGEm).
 * Eliminates duplication of priority SafeNeutral handling, pulse timer wakeups, and thread lifecycle.
 */
class AsyncProfileWriterLoop(
    private val profile: ControllerProfile,
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val threadName: String,
    private val onApplyNormalized: (NormalizedGamepadOutput) -> Unit,
    private val onResetToNeutral: () -> Unit,
    private val onError: (Throwable) -> Unit
) {
    private val mapper = ProfileInputMapper(profile)
    private val queueLock = ReentrantLock()
    private val wakeCondition = queueLock.newCondition()
    private val idleCondition = queueLock.newCondition()

    private var workerThread: Thread? = null
    private var pendingActiveState: ControllerState? = null
    private var pendingSafeReset: ControllerState? = null
    private var processing: Boolean = false
    private var stopping: Boolean = false
    private var lastAppliedState: ControllerState? = null
    private var nextTimerDeadlineNs: Long? = null

    fun start() {
        queueLock.withLock {
            stopping = false
            pendingActiveState = null
            pendingSafeReset = null
            lastAppliedState = ControllerState.safeNeutral()
            mapper.reset()
            nextTimerDeadlineNs = null
        }
        workerThread = Thread(::runLoop, threadName).apply {
            isDaemon = true
            start()
        }
    }

    fun enqueueState(state: ControllerState) {
        queueLock.withLock {
            if (stopping) return
            pendingActiveState = state
            wakeCondition.signal()
        }
    }

    fun enqueueSafeReset(state: ControllerState) {
        queueLock.withLock {
            if (stopping) return
            pendingActiveState = null
            pendingSafeReset = if (state.isSafeNeutral) state else ControllerState.safeNeutral(state.lastUpdatedNanos)
            wakeCondition.signal()
        }
    }

    fun awaitIdle(timeoutMs: Long = 2_000L): Boolean {
        val deadlineNs = System.nanoTime() + timeoutMs * 1_000_000L
        queueLock.withLock {
            while (pendingActiveState != null || pendingSafeReset != null || processing || nextTimerDeadlineNs != null) {
                val remaining = deadlineNs - System.nanoTime()
                if (remaining <= 0L) return false
                idleCondition.awaitNanos(remaining)
            }
            return true
        }
    }

    fun stopAndReset() {
        val toJoin: Thread?
        queueLock.withLock {
            stopping = true
            toJoin = workerThread
            workerThread = null
            wakeCondition.signalAll()
        }
        if (toJoin != null) {
            runCatching { toJoin.join(1_000L) }
        }
        queueLock.withLock {
            mapper.reset()
            nextTimerDeadlineNs = null
            pendingActiveState = null
            pendingSafeReset = null
            processing = false
        }
    }

    private fun runLoop() {
        while (true) {
            var resetCmd: ControllerState? = null
            var stateCmd: ControllerState? = null
            var timerTickNow = false
            var shouldExit = false

            queueLock.withLock {
                while (true) {
                    if (stopping) {
                        shouldExit = true
                        break
                    }
                    if (pendingSafeReset != null) {
                        resetCmd = pendingSafeReset
                        pendingSafeReset = null
                        processing = true
                        break
                    }
                    if (pendingActiveState != null) {
                        stateCmd = pendingActiveState
                        pendingActiveState = null
                        processing = true
                        break
                    }

                    val deadline = nextTimerDeadlineNs
                    if (deadline != null) {
                        val remaining = deadline - clock.nanoTime()
                        if (remaining <= 0L) {
                            timerTickNow = true
                            processing = true
                            break
                        }
                        wakeCondition.awaitNanos(remaining)
                    } else {
                        idleCondition.signalAll()
                        wakeCondition.await()
                    }
                }
            }

            if (shouldExit) break

            try {
                if (resetCmd != null) {
                    mapper.reset()
                    lastAppliedState = resetCmd
                    onResetToNeutral()
                } else if (stateCmd != null) {
                    val next = stateCmd!!
                    if (next.isSafeNeutral) {
                        mapper.reset()
                        lastAppliedState = next
                        onResetToNeutral()
                    } else {
                        lastAppliedState = next
                        val mapped = mapper.processState(next, clock.nanoTime())
                        onApplyNormalized(mapped)
                    }
                } else if (timerTickNow) {
                    val mapped = mapper.onTimerTick(lastAppliedState, clock.nanoTime())
                    onApplyNormalized(mapped)
                }
            } catch (t: Throwable) {
                onError(t)
            } finally {
                val nextDeadline = mapper.nextTimerDeadlineNanos()
                queueLock.withLock {
                    nextTimerDeadlineNs = nextDeadline
                    processing = false
                    if (pendingActiveState == null && pendingSafeReset == null && nextTimerDeadlineNs == null) {
                        idleCondition.signalAll()
                    }
                }
            }
        }
    }
}
