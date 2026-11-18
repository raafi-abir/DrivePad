package com.drivepad.app.core.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import com.drivepad.app.core.input.SteeringCalibrationManager
import com.drivepad.app.core.input.SteeringMath
import com.drivepad.app.domain.model.ControllerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

class MotionSteeringManager(
    context: Context,
    private val calibrationManager: SteeringCalibrationManager
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val displayManager = appContext.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager

    // Pre-allocated array to guarantee zero GC allocation in sensor callbacks
    private val rotationMatrix = FloatArray(9)

    // Sensor availability detection: prefer fused Game Rotation Vector, fallback to Rotation Vector or Gyroscope
    val targetSensor: Sensor? = sensorManager?.let { sm ->
        sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    }

    val isSensorAvailable: Boolean = targetSensor != null

    @Volatile
    private var isListening = false
    private var previousFilteredAngle = 0.0f
    private var lastGyroTimestampNanos = 0L

    private val _rawTiltDegrees = MutableStateFlow(0.0f)
    val rawTiltDegrees: StateFlow<Float> = _rawTiltDegrees.asStateFlow()

    private val _normalizedSteering = MutableStateFlow(0.0f)
    val normalizedSteering: StateFlow<Float> = _normalizedSteering.asStateFlow()

    @Volatile
    var activeConfig: ControllerConfig = ControllerConfig()

    fun startListening() {
        if (!isSensorAvailable || isListening) return
        lastGyroTimestampNanos = 0L
        targetSensor?.let { sensor ->
            val registered = sensorManager?.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_GAME
            ) ?: false
            isListening = registered
        }
    }

    fun stopListening() {
        if (!isListening) return
        sensorManager?.unregisterListener(this)
        isListening = false
        lastGyroTimestampNanos = 0L
        previousFilteredAngle = 0.0f
        _rawTiltDegrees.value = 0.0f
        _normalizedSteering.value = 0.0f
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isListening) return
        val values = event.values
        if (values.size < 3) return
        for (i in 0 until minOf(values.size, 4)) {
            val v = values[i]
            if (v.isNaN() || v.isInfinite()) return
        }

        val sensorType = event.sensor.type
        val displayRotation = getCurrentDisplayRotation()
        val rawRollDegrees: Float = when (sensorType) {
            Sensor.TYPE_GAME_ROTATION_VECTOR, Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
                // World Up (0,0,1) expressed in Device coords is (rotationMatrix[6], rotationMatrix[7], rotationMatrix[8]).
                // Measuring atan2(R[7], hypot(R[6], R[8])) gives gimbal-lock-free steering wheel roll
                // across flat-to-upright driving angles.
                val gx = rotationMatrix[6]
                val gy = rotationMatrix[7]
                val gz = rotationMatrix[8]
                val baseRollRad = atan2(gy, hypot(gx, gz))
                val baseRollDeg = baseRollRad * (180.0f / PI.toFloat())
                when (displayRotation) {
                    Surface.ROTATION_270 -> -baseRollDeg
                    Surface.ROTATION_0 -> atan2(gx, hypot(gy, gz)) * (180.0f / PI.toFloat())
                    Surface.ROTATION_180 -> -atan2(gx, hypot(gy, gz)) * (180.0f / PI.toFloat())
                    else -> baseRollDeg // Surface.ROTATION_90 (standard landscape)
                }
            }

            Sensor.TYPE_GYROSCOPE -> {
                val gyroZ = values[2] // rad/s around screen-normal axis
                val prevTs = lastGyroTimestampNanos
                lastGyroTimestampNanos = event.timestamp
                if (prevTs == 0L) {
                    previousFilteredAngle
                } else {
                    val dt = ((event.timestamp - prevTs) * 1e-9f).coerceIn(0.0f, 0.1f)
                    val deltaDeg = -gyroZ * (180.0f / PI.toFloat()) * dt
                    (previousFilteredAngle + deltaDeg).coerceIn(-90.0f, 90.0f)
                }
            }

            else -> 0.0f
        }

        processRollDegrees(rawRollDegrees)
    }

    /**
     * Filters and normalizes a raw roll angle in degrees. Exposed internally for deterministic testing.
     */
    internal fun processRollDegrees(rawRollDegrees: Float) {
        if (rawRollDegrees.isNaN() || rawRollDegrees.isInfinite()) return

        val config = activeConfig
        val smoothedAngle = SteeringMath.applyEmaFilter(
            currentValue = rawRollDegrees,
            previousFilteredValue = previousFilteredAngle,
            smoothingFactor = config.motionSmoothingFactor
        )
        previousFilteredAngle = smoothedAngle
        _rawTiltDegrees.value = smoothedAngle

        val normalized = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = smoothedAngle,
            neutralOffsetDegrees = calibrationManager.currentNeutralOffset,
            maxAngleDegrees = config.maxSteeringAngleDegrees,
            deadzone = config.steeringDeadzone,
            sensitivity = config.steeringSensitivity,
            invert = config.invertSteering,
            curve = config.steeringResponseCurve
        )

        _normalizedSteering.value = normalized
    }

    fun recalculateWithCurrentConfig() {
        processRollDegrees(previousFilteredAngle)
    }

    private fun getCurrentDisplayRotation(): Int {
        val display = displayManager?.getDisplay(Display.DEFAULT_DISPLAY)
        return display?.rotation ?: Surface.ROTATION_90
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}
