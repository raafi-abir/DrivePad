package com.drivepad.companion.backend.uinput

import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.profile.StandardAxisCode
import kotlin.math.roundToInt

/**
 * Maps normalized DrivePad axes to Linux `EV_ABS` gamepad axes:
 * - Steering (`-1.0f .. +1.0f`) -> `ABS_X` (`-32768 .. +32767`, neutral = `0`)
 * - Throttle (`0.0f` or `1.0f`)  -> `ABS_Z` (`0` or `255`, strictly digital)
 * - Brake    (`0.0f` or `1.0f`)  -> `ABS_RZ` (`0` or `255`, strictly digital)
 */
object GamepadAxisMapping {
    const val STEERING_MIN: Int = -32768
    const val STEERING_NEUTRAL: Int = 0
    const val STEERING_MAX: Int = 32767

    const val PEDAL_MIN: Int = 0
    const val PEDAL_MAX: Int = 255

    val steeringAxis = UInputAxisSpec(
        code = LinuxInputConstants.ABS_X,
        name = "ABS_X (Steering)",
        minimum = STEERING_MIN,
        maximum = STEERING_MAX,
        initialValue = STEERING_NEUTRAL
    )

    val throttleAxis = UInputAxisSpec(
        code = LinuxInputConstants.ABS_Z,
        name = "ABS_Z (Throttle)",
        minimum = PEDAL_MIN,
        maximum = PEDAL_MAX,
        initialValue = PEDAL_MIN
    )

    val brakeAxis = UInputAxisSpec(
        code = LinuxInputConstants.ABS_RZ,
        name = "ABS_RZ (Brake)",
        minimum = PEDAL_MIN,
        maximum = PEDAL_MAX,
        initialValue = PEDAL_MIN
    )

    val axisSpecs: List<UInputAxisSpec> = listOf(steeringAxis, throttleAxis, brakeAxis)

    fun toLinuxAbsCode(axis: StandardAxisCode): Int = when (axis) {
        StandardAxisCode.LEFT_X -> LinuxInputConstants.ABS_X
        StandardAxisCode.LEFT_Y -> LinuxInputConstants.ABS_Y
        StandardAxisCode.RIGHT_TRIGGER -> LinuxInputConstants.ABS_Z
        StandardAxisCode.RIGHT_X -> LinuxInputConstants.ABS_RX
        StandardAxisCode.RIGHT_Y -> LinuxInputConstants.ABS_RY
        StandardAxisCode.LEFT_TRIGGER -> LinuxInputConstants.ABS_RZ
    }

    fun axisNeutralValue(axis: StandardAxisCode, invert: Boolean): Int {
        if (!axis.isSignedFullAxis) {
            return if (invert) PEDAL_MAX else PEDAL_MIN
        }
        return STEERING_NEUTRAL
    }

    fun mapSteering(normalizedSteering: Float): Int {
        if (normalizedSteering.isNaN() || normalizedSteering.isInfinite()) return STEERING_NEUTRAL
        val clamped = normalizedSteering.coerceIn(-1.0f, 1.0f)
        if (clamped == 0.0f) return STEERING_NEUTRAL
        return if (clamped > 0.0f) {
            (clamped * STEERING_MAX.toFloat()).roundToInt().coerceIn(0, STEERING_MAX)
        } else {
            (clamped * (-STEERING_MIN).toFloat()).roundToInt().coerceIn(STEERING_MIN, 0)
        }
    }

    /** Strictly digital mapping: only `0` (released) or `255` (pressed), never intermediate values. */
    fun mapDigitalPedal(normalizedPedal: Float): Int {
        if (normalizedPedal.isNaN() || normalizedPedal.isInfinite()) return PEDAL_MIN
        return if (normalizedPedal >= 1.0f) PEDAL_MAX else PEDAL_MIN
    }

    fun mapDigitalPedal(active: Boolean): Int = if (active) PEDAL_MAX else PEDAL_MIN

    fun mapAxes(state: ControllerState): IntArray = intArrayOf(
        mapSteering(state.steering),
        mapDigitalPedal(state.throttle),
        mapDigitalPedal(state.brake)
    )
}
