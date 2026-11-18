package com.drivepad.app.data.repository

import android.content.Context
import com.drivepad.app.core.input.SteeringCalibrationManager
import com.drivepad.app.core.input.TouchSteeringProcessor
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.core.sensor.MotionSteeringManager
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.NormalizedControllerState
import com.drivepad.app.domain.model.TransmissionMode
import com.drivepad.app.feature.racing.AutoGear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface ControllerInputRepository {
    val controllerState: StateFlow<NormalizedControllerState>
    val isSensorAvailable: Boolean
    val isMotionSteeringActive: Boolean
    val isTouchInteracting: StateFlow<Boolean>

    fun onSteeringDrag(deltaAngleDegrees: Float)
    fun onSteeringReleased()
    fun setAcceleratorPressed(isPressed: Boolean)
    fun setBrakePressed(isPressed: Boolean)
    fun toggleHandbrake()
    fun setTransmissionMode(mode: TransmissionMode)
    fun selectAutoGear(gear: AutoGear)
    fun shiftUp(maxGear: Int = 6)
    fun shiftDown(minGear: Int = -1)
    fun toggleLights()
    fun setHorn(isPressed: Boolean)
    fun toggleWipers()
    fun toggleHazard()
    fun toggleCruise()
    fun toggleEngine()
    fun toggleLeftIndicator()
    fun toggleRightIndicator()
    fun toggleMotionSteering(enabled: Boolean)
    fun calibrateSteering(): Boolean
    fun resetSteeringCalibration()
    fun safetyResetAllInputs()
    fun startSensors()
    fun stopSensors()
}

class ControllerInputRepositoryImpl(
    context: Context,
    private val preferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : ControllerInputRepository {

    val calibrationManager = SteeringCalibrationManager()
    val motionSteeringManager = MotionSteeringManager(context, calibrationManager)
    val touchSteeringProcessor = TouchSteeringProcessor(scope)

    override val isSensorAvailable: Boolean
        get() = motionSteeringManager.isSensorAvailable

    override val isTouchInteracting: StateFlow<Boolean>
        get() = touchSteeringProcessor.isInteracting

    private val _controllerState = MutableStateFlow(NormalizedControllerState())
    override val controllerState: StateFlow<NormalizedControllerState> = _controllerState.asStateFlow()

    @Volatile
    private var currentConfig = ControllerConfig()

    @Volatile
    private var isSensorSessionActive = false

    init {
        // Collect preferences and configure processors
        scope.launch {
            preferencesRepository.controllerConfigFlow.collect { config ->
                currentConfig = config
                motionSteeringManager.activeConfig = config
                touchSteeringProcessor.activeConfig = config
                touchSteeringProcessor.recalculateWithCurrentConfig()

                _controllerState.update { current ->
                    current.copy(
                        transmissionMode = config.defaultTransmissionMode,
                        isMotionSteeringActive = config.motionSteeringEnabled
                    )
                }

                if (config.motionSteeringEnabled && isSensorSessionActive) {
                    motionSteeringManager.recalculateWithCurrentConfig()
                    motionSteeringManager.startListening()
                } else {
                    motionSteeringManager.stopListening()
                }
            }
        }

        // Combine motion and touch steering streams without letting them fight each other
        scope.launch {
            combine(
                motionSteeringManager.normalizedSteering,
                touchSteeringProcessor.normalizedSteering,
                motionSteeringManager.rawTiltDegrees,
                calibrationManager.calibrationState,
                preferencesRepository.controllerConfigFlow
            ) { motionNorm, touchNorm, rawTilt, calib, config ->
                val activeSteering = if (config.motionSteeringEnabled) motionNorm else touchNorm
                val safeSteering = if (activeSteering.isNaN() || activeSteering.isInfinite()) {
                    0.0f
                } else {
                    activeSteering.coerceIn(-1.0f, 1.0f)
                }
                val safeRawTilt = if (rawTilt.isNaN() || rawTilt.isInfinite()) 0.0f else rawTilt

                _controllerState.update { current ->
                    current.copy(
                        steering = safeSteering,
                        rawTiltDegrees = safeRawTilt,
                        isCalibrated = calib.isCalibrated,
                        isMotionSteeringActive = config.motionSteeringEnabled
                    )
                }
            }.collect {}
        }
    }

    override val isMotionSteeringActive: Boolean
        get() = currentConfig.motionSteeringEnabled

    override fun onSteeringDrag(deltaAngleDegrees: Float) {
        if (!currentConfig.motionSteeringEnabled) {
            touchSteeringProcessor.onDrag(deltaAngleDegrees)
        }
    }

    override fun onSteeringReleased() {
        if (!currentConfig.motionSteeringEnabled) {
            touchSteeringProcessor.onRelease()
        }
    }

    // MANDATORY DIGITAL PEDALS IMPLEMENTATION: STRICTLY 0.0 OR 1.0
    override fun setAcceleratorPressed(isPressed: Boolean) {
        val digitalVal = if (isPressed) 1.0f else 0.0f
        _controllerState.update { it.copy(throttle = digitalVal) }
    }

    override fun setBrakePressed(isPressed: Boolean) {
        val digitalVal = if (isPressed) 1.0f else 0.0f
        _controllerState.update { it.copy(brake = digitalVal) }
    }

    override fun toggleHandbrake() {
        _controllerState.update { it.copy(handbrake = !it.handbrake) }
    }

    override fun setTransmissionMode(mode: TransmissionMode) {
        _controllerState.update { current ->
            val adjustedGear = if (mode == TransmissionMode.MANUAL && current.currentGear < -1) {
                0 // Park (-2) is Auto-only; start Manual in Neutral (0)
            } else {
                current.currentGear
            }
            current.copy(transmissionMode = mode, currentGear = adjustedGear)
        }
    }

    override fun selectAutoGear(gear: AutoGear) {
        // Map AutoGear to internal representation (1=D, 0=N, -1=R, -2=P)
        val gearInt = when (gear) {
            AutoGear.D -> 1
            AutoGear.N -> 0
            AutoGear.R -> -1
            AutoGear.P -> -2
        }
        _controllerState.update { it.copy(currentGear = gearInt) }
    }

    override fun shiftUp(maxGear: Int) {
        _controllerState.update { current ->
            val next = (current.currentGear + 1).coerceAtMost(maxGear)
            current.copy(currentGear = next)
        }
    }

    override fun shiftDown(minGear: Int) {
        _controllerState.update { current ->
            val prev = (current.currentGear - 1).coerceAtLeast(minGear)
            current.copy(currentGear = prev)
        }
    }

    override fun toggleLights() {
        _controllerState.update { it.copy(lights = !it.lights) }
    }

    override fun setHorn(isPressed: Boolean) {
        _controllerState.update { it.copy(horn = isPressed) }
    }

    override fun toggleWipers() {
        _controllerState.update { it.copy(wipers = !it.wipers) }
    }

    override fun toggleHazard() {
        _controllerState.update { current ->
            val newHazard = !current.hazardLights
            current.copy(
                hazardLights = newHazard,
                leftIndicator = if (newHazard) false else current.leftIndicator,
                rightIndicator = if (newHazard) false else current.rightIndicator
            )
        }
    }

    override fun toggleCruise() {
        _controllerState.update { it.copy(cruiseControl = !it.cruiseControl) }
    }

    override fun toggleEngine() {
        _controllerState.update { it.copy(engine = !it.engine) }
    }

    override fun toggleLeftIndicator() {
        _controllerState.update { current ->
            val newLeft = !current.leftIndicator
            current.copy(
                leftIndicator = newLeft,
                rightIndicator = false, // Mutually exclusive
                hazardLights = false
            )
        }
    }

    override fun toggleRightIndicator() {
        _controllerState.update { current ->
            val newRight = !current.rightIndicator
            current.copy(
                rightIndicator = newRight,
                leftIndicator = false, // Mutually exclusive
                hazardLights = false
            )
        }
    }

    override fun toggleMotionSteering(enabled: Boolean) {
        scope.launch {
            preferencesRepository.updateMotionSteeringEnabled(enabled)
        }
        if (enabled) {
            touchSteeringProcessor.reset()
            if (isSensorSessionActive) {
                motionSteeringManager.startListening()
            }
        } else {
            motionSteeringManager.stopListening()
        }
    }

    override fun calibrateSteering(): Boolean {
        if (!motionSteeringManager.isSensorAvailable) return false
        val rawAngle = motionSteeringManager.rawTiltDegrees.value
        val calibrated = calibrationManager.calibrate(rawAngle)
        if (calibrated) {
            motionSteeringManager.recalculateWithCurrentConfig()
        }
        return calibrated
    }

    override fun resetSteeringCalibration() {
        calibrationManager.resetCalibration()
        motionSteeringManager.recalculateWithCurrentConfig()
    }

    override fun safetyResetAllInputs() {
        touchSteeringProcessor.reset()
        _controllerState.update { current ->
            current.copy(
                throttle = 0.0f,
                brake = 0.0f,
                horn = false
            )
        }
    }

    override fun startSensors() {
        isSensorSessionActive = true
        if (currentConfig.motionSteeringEnabled) {
            motionSteeringManager.startListening()
        }
    }

    override fun stopSensors() {
        isSensorSessionActive = false
        motionSteeringManager.stopListening()
        safetyResetAllInputs()
    }
}
