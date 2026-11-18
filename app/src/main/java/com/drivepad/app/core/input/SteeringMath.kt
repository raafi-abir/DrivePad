package com.drivepad.app.core.input

import com.drivepad.app.domain.model.SteeringResponseCurve
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sign

object SteeringMath {

    /**
     * Converts a raw steering angle (in degrees) into a normalized [-1.0..1.0] steering value.
     *
     * @param rawAngleDegrees The current measured or dragged angle in degrees.
     * @param neutralOffsetDegrees The calibrated neutral zero angle in degrees.
     * @param maxAngleDegrees The maximum angle representing full lock (e.g. 35° for tilt, 135° for wheel).
     * @param deadzone Deadzone threshold [0.0..0.5] where output remains strictly 0.0.
     * @param sensitivity Multiplier scaling the steering response [0.2..3.0].
     * @param invert When true, reverses the steering direction.
     * @param curve Response curve type (Linear, Exponential, or Smooth S-Curve).
     * @return Normalized steering value in [-1.0f..1.0f].
     */
    fun calculateNormalizedSteering(
        rawAngleDegrees: Float,
        neutralOffsetDegrees: Float = 0.0f,
        maxAngleDegrees: Float = 35.0f,
        deadzone: Float = 0.04f,
        sensitivity: Float = 1.0f,
        invert: Boolean = false,
        curve: SteeringResponseCurve = SteeringResponseCurve.LINEAR
    ): Float {
        if (rawAngleDegrees.isNaN() || rawAngleDegrees.isInfinite()) return 0.0f
        if (maxAngleDegrees <= 0.0f) return 0.0f

        // 1. Subtract neutral calibration offset
        val relativeAngle = rawAngleDegrees - neutralOffsetDegrees

        // 2. Inversion
        val directedAngle = if (invert) -relativeAngle else relativeAngle

        // 3. Raw normalization to [-1.0..1.0]
        val rawFraction = (directedAngle / maxAngleDegrees).coerceIn(-1.0f, 1.0f)
        val absVal = abs(rawFraction)
        val clampedDeadzone = deadzone.coerceIn(0.0f, 0.49f)

        // 4. Deadzone filter
        if (absVal <= clampedDeadzone) {
            return 0.0f
        }

        // Rescale so values outside deadzone start smoothly from 0.0 to 1.0
        val postDeadzoneFraction = (absVal - clampedDeadzone) / (1.0f - clampedDeadzone)

        // 5. Response curve shaping
        val curvedFraction = when (curve) {
            SteeringResponseCurve.LINEAR -> postDeadzoneFraction
            SteeringResponseCurve.EXPONENTIAL -> postDeadzoneFraction.pow(1.6f)
            SteeringResponseCurve.SMOOTH -> {
                // Cubic Hermite smoothstep (3x^2 - 2x^3)
                (3.0f * postDeadzoneFraction.pow(2) - 2.0f * postDeadzoneFraction.pow(3))
            }
        }

        // 6. Apply sensitivity multiplier and clamp strictly to [-1.0..1.0]
        val scaled = (curvedFraction * sensitivity).coerceIn(0.0f, 1.0f)
        val result = sign(rawFraction) * scaled

        return if (result.isNaN() || result.isInfinite()) 0.0f else result.coerceIn(-1.0f, 1.0f)
    }

    /**
     * Applies an Exponential Moving Average (EMA) low-pass filter to smooth sensor jitter.
     */
    fun applyEmaFilter(currentValue: Float, previousFilteredValue: Float, smoothingFactor: Float): Float {
        if (currentValue.isNaN() || currentValue.isInfinite()) return previousFilteredValue
        val alpha = (1.0f - smoothingFactor).coerceIn(0.05f, 1.0f)
        return (alpha * currentValue) + ((1.0f - alpha) * previousFilteredValue)
    }
}
