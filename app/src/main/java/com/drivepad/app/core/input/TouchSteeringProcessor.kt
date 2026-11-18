package com.drivepad.app.core.input

import com.drivepad.app.domain.model.ControllerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TouchSteeringProcessor(
    private val scope: CoroutineScope
) {
    private val _wheelAngleDegrees = MutableStateFlow(0.0f)
    val wheelAngleDegrees: StateFlow<Float> = _wheelAngleDegrees.asStateFlow()

    private val _normalizedSteering = MutableStateFlow(0.0f)
    val normalizedSteering: StateFlow<Float> = _normalizedSteering.asStateFlow()

    private val _isInteracting = MutableStateFlow(false)
    val isInteracting: StateFlow<Boolean> = _isInteracting.asStateFlow()

    @Volatile
    var activeConfig: ControllerConfig = ControllerConfig()
    private var returnSpringJob: Job? = null

    fun onDrag(deltaAngleDegrees: Float, maxAngleDegrees: Float = 135.0f) {
        if (deltaAngleDegrees.isNaN() || deltaAngleDegrees.isInfinite()) return
        returnSpringJob?.cancel()
        _isInteracting.value = true

        val newAngle = (_wheelAngleDegrees.value + deltaAngleDegrees)
            .coerceIn(-maxAngleDegrees, maxAngleDegrees)
        _wheelAngleDegrees.value = newAngle

        recalculateNormalization(newAngle, maxAngleDegrees)
    }

    fun onDirectAngle(angleDegrees: Float, maxAngleDegrees: Float = 135.0f) {
        if (angleDegrees.isNaN() || angleDegrees.isInfinite()) return
        returnSpringJob?.cancel()
        _isInteracting.value = true

        val clamped = angleDegrees.coerceIn(-maxAngleDegrees, maxAngleDegrees)
        _wheelAngleDegrees.value = clamped

        recalculateNormalization(clamped, maxAngleDegrees)
    }

    fun onRelease(maxAngleDegrees: Float = 135.0f) {
        _isInteracting.value = false

        if (!activeConfig.returnToCenterEnabled) return

        returnSpringJob?.cancel()
        returnSpringJob = scope.launch {
            val startAngle = _wheelAngleDegrees.value
            if (startAngle == 0.0f) return@launch

            val steps = 10
            for (i in 1..steps) {
                delay(12)
                val factor = 1.0f - (i.toFloat() / steps.toFloat())
                val interpolated = startAngle * factor
                _wheelAngleDegrees.value = interpolated
                recalculateNormalization(interpolated, maxAngleDegrees)
            }
            _wheelAngleDegrees.value = 0.0f
            _normalizedSteering.value = 0.0f
        }
    }

    fun recalculateWithCurrentConfig(maxAngleDegrees: Float = 135.0f) {
        recalculateNormalization(_wheelAngleDegrees.value, maxAngleDegrees)
    }

    fun reset() {
        returnSpringJob?.cancel()
        _wheelAngleDegrees.value = 0.0f
        _normalizedSteering.value = 0.0f
        _isInteracting.value = false
    }

    private fun recalculateNormalization(angle: Float, maxAngleDegrees: Float) {
        _normalizedSteering.value = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = angle,
            neutralOffsetDegrees = 0.0f,
            maxAngleDegrees = maxAngleDegrees,
            deadzone = activeConfig.steeringDeadzone,
            sensitivity = activeConfig.steeringSensitivity,
            invert = activeConfig.invertSteering,
            curve = activeConfig.steeringResponseCurve
        )
    }
}
