package com.drivepad.app.domain.model

data class NormalizedControllerState(
    val steering: Float = 0.0f,               // -1.0f (full left) to +1.0f (full right)
    val throttle: Float = 0.0f,               // Digital: strictly 0.0f or 1.0f
    val brake: Float = 0.0f,                  // Digital: strictly 0.0f or 1.0f
    val handbrake: Boolean = false,
    val currentGear: Int = 1,                 // 1 to 6 (or 1 to 12)
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val lights: Boolean = false,
    val horn: Boolean = false,
    val wipers: Boolean = false,
    val hazardLights: Boolean = false,
    val leftIndicator: Boolean = false,
    val rightIndicator: Boolean = false,
    val cruiseControl: Boolean = false,
    val engine: Boolean = false,
    val isMotionSteeringActive: Boolean = false,
    val isCalibrated: Boolean = false,
    val rawTiltDegrees: Float = 0.0f          // Raw un-normalized tilt angle for telemetry
) {
    init {
        // Enforce boundary invariants
        require(!steering.isNaN() && !steering.isInfinite()) { "Steering cannot be NaN or Infinite" }
        require(steering in -1.0f..1.0f) { "Steering must be normalized between -1.0 and 1.0, was $steering" }
        require(throttle == 0.0f || throttle == 1.0f) { "Throttle must be strictly digital (0.0 or 1.0), was $throttle" }
        require(brake == 0.0f || brake == 1.0f) { "Brake must be strictly digital (0.0 or 1.0), was $brake" }
    }

    companion object {
        val Neutral = NormalizedControllerState()

        /**
         * Sanitizes arbitrary float inputs to strictly conform to controller invariants.
         */
        fun sanitize(
            steering: Float,
            throttle: Float,
            brake: Float,
            handbrake: Boolean = false,
            currentGear: Int = 1,
            transmissionMode: TransmissionMode = TransmissionMode.AUTO,
            lights: Boolean = false,
            horn: Boolean = false,
            wipers: Boolean = false,
            hazardLights: Boolean = false,
            leftIndicator: Boolean = false,
            rightIndicator: Boolean = false,
            cruiseControl: Boolean = false,
            engine: Boolean = false,
            isMotionSteeringActive: Boolean = false,
            isCalibrated: Boolean = false,
            rawTiltDegrees: Float = 0.0f
        ): NormalizedControllerState {
            val safeSteering = if (steering.isNaN() || steering.isInfinite()) 0.0f else steering.coerceIn(-1.0f, 1.0f)
            val digitalThrottle = if (throttle > 0.0f) 1.0f else 0.0f
            val digitalBrake = if (brake > 0.0f) 1.0f else 0.0f

            return NormalizedControllerState(
                steering = safeSteering,
                throttle = digitalThrottle,
                brake = digitalBrake,
                handbrake = handbrake,
                currentGear = currentGear.coerceIn(-2, 12),
                transmissionMode = transmissionMode,
                lights = lights,
                horn = horn,
                wipers = wipers,
                hazardLights = hazardLights,
                leftIndicator = leftIndicator,
                rightIndicator = rightIndicator,
                cruiseControl = cruiseControl,
                engine = engine,
                isMotionSteeringActive = isMotionSteeringActive,
                isCalibrated = isCalibrated,
                rawTiltDegrees = if (rawTiltDegrees.isNaN() || rawTiltDegrees.isInfinite()) 0.0f else rawTiltDegrees
            )
        }
    }
}
