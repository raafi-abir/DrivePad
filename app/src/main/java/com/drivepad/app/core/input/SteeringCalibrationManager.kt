package com.drivepad.app.core.input

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

data class CalibrationState(
    val neutralOffsetAngle: Float = 0.0f,
    val isCalibrated: Boolean = false,
    val lastCalibratedTimestamp: Long = 0L
)

class SteeringCalibrationManager {

    private val _calibrationState = MutableStateFlow(CalibrationState())
    val calibrationState: StateFlow<CalibrationState> = _calibrationState.asStateFlow()

    /**
     * Calibrates current raw angle as the new neutral (0.00) reference.
     * Rejects extreme anomalous angles (> 90 degrees roll in neutral driving posture).
     */
    fun calibrate(currentRawAngle: Float): Boolean {
        if (currentRawAngle.isNaN() || currentRawAngle.isInfinite()) return false
        if (abs(currentRawAngle) > 90.0f) return false // Safety: don't calibrate inverted phone

        _calibrationState.value = CalibrationState(
            neutralOffsetAngle = currentRawAngle,
            isCalibrated = true,
            lastCalibratedTimestamp = System.currentTimeMillis()
        )
        return true
    }

    /**
     * Resets calibration back to absolute 0.0 degrees.
     */
    fun resetCalibration() {
        _calibrationState.value = CalibrationState(
            neutralOffsetAngle = 0.0f,
            isCalibrated = false,
            lastCalibratedTimestamp = 0L
        )
    }

    val currentNeutralOffset: Float
        get() = _calibrationState.value.neutralOffsetAngle

    val isCalibrated: Boolean
        get() = _calibrationState.value.isCalibrated
}
