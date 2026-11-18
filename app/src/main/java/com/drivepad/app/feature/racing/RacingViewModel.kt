package com.drivepad.app.feature.racing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivepad.app.core.network.ControllerConnectionManager
import com.drivepad.app.core.preferences.DashboardPreferencesRepository
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.data.repository.ControllerInputRepository
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.DashboardConfiguration
import com.drivepad.app.domain.model.DashboardStateFactory
import com.drivepad.app.domain.model.DashboardTelemetrySnapshot
import com.drivepad.app.domain.model.DashboardUiState
import com.drivepad.app.domain.model.TelemetryAvailability
import com.drivepad.app.domain.model.TelemetrySourceStatus
import com.drivepad.app.domain.model.TransmissionMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs

private data class TelemetrySimulationState(
    val speedKmh: Float = 0.0f,
    val rpm: Float = 0.0f,
    val revLimiterPhase: Float = 0.0f
)

class RacingViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val inputRepository: ControllerInputRepository,
    private val connectionManager: ControllerConnectionManager? = null,
    private val dashboardPreferencesRepository: DashboardPreferencesRepository? = null
) : ViewModel() {

    private val _indicatorBlinkOn = MutableStateFlow(true)
    val indicatorBlinkOn: StateFlow<Boolean> = _indicatorBlinkOn.asStateFlow()

    private val _calibrationFeedback = MutableStateFlow<String?>(null)
    val calibrationFeedback: StateFlow<String?> = _calibrationFeedback.asStateFlow()

    private val _telemetrySimulation = MutableStateFlow(TelemetrySimulationState())
    private val _isDashboardPaused = MutableStateFlow(false)

    private var blinkerJob: Job? = null
    private var feedbackJob: Job? = null

    private val dashboardConfigFlow = dashboardPreferencesRepository?.dashboardConfigFlow
        ?: flowOf(DashboardConfiguration())

    private val connectionStatusFlow = connectionManager?.diagnosticsState
        ?.map { it.status }
        ?.distinctUntilChanged()
        ?: flowOf(ConnectionStatus.DISCONNECTED)

    // Unified Reactive UI State connected directly to the Controller Input Engine
    val uiState: StateFlow<RacingUiState> = combine(
        inputRepository.controllerState,
        inputRepository.isTouchInteracting,
        _indicatorBlinkOn,
        connectionStatusFlow,
        _telemetrySimulation
    ) { controllerState, isTouchInteracting, blinkOn, connStatus, simState ->
        val autoGear = when (controllerState.currentGear) {
            -2 -> AutoGear.P
            -1 -> AutoGear.R
            0 -> AutoGear.N
            else -> AutoGear.D
        }

        val autoActiveGearNumber = if (
            controllerState.transmissionMode == TransmissionMode.AUTO &&
            autoGear == AutoGear.D &&
            (simState.speedKmh >= 0.5f || (controllerState.engine && controllerState.throttle > 0.05f))
        ) {
            autoVirtualGear(simState.speedKmh)
        } else {
            null
        }

        RacingUiState(
            transmissionMode = controllerState.transmissionMode,
            selectedAutoGear = autoGear,
            currentManualGear = controllerState.currentGear.coerceAtLeast(-1),
            steeringState = SteeringState(
                // Always reflect the calculated normalized steering angle across both Touch and Motion modes
                angleDegrees = controllerState.steering * 135.0f,
                normalizedValue = controllerState.steering,
                isInteracting = isTouchInteracting
            ),
            pedalsState = PedalsState(
                throttle = controllerState.throttle,
                brake = controllerState.brake,
                handbrakeEngaged = controllerState.handbrake
            ),
            vehicleFunctions = VehicleFunctionsState(
                lightsOn = controllerState.lights,
                hornActive = controllerState.horn,
                wipersActive = controllerState.wipers,
                hazardActive = controllerState.hazardLights,
                cruiseActive = controllerState.cruiseControl,
                engineRunning = controllerState.engine,
                leftIndicatorActive = controllerState.leftIndicator,
                rightIndicatorActive = controllerState.rightIndicator
            ),
            motionSteeringEnabled = controllerState.isMotionSteeringActive,
            isSensorAvailable = inputRepository.isSensorAvailable,
            isCalibrated = controllerState.isCalibrated,
            indicatorBlinkOn = blinkOn,
            connectionStatus = connStatus,
            rpm = simState.rpm,
            speedKmh = simState.speedKmh,
            autoActiveGearNumber = autoActiveGearNumber
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RacingUiState(isSensorAvailable = inputRepository.isSensorAvailable)
    )

    val dashboardUiState: StateFlow<DashboardUiState> = combine(
        uiState,
        dashboardConfigFlow,
        _isDashboardPaused
    ) { racingState, dashboardConfig, isPaused ->
        val snapshot = DashboardTelemetrySnapshot.fromRacingUiState(
            racingState = racingState,
            timestampMs = System.currentTimeMillis(),
            sourceStatus = if (isPaused) TelemetrySourceStatus.PAUSED else TelemetrySourceStatus.LIVE
        )
        DashboardStateFactory.buildDashboardUiState(
            snapshot = snapshot,
            configuration = dashboardConfig,
            availability = TelemetryAvailability.CurrentDrivePadSource,
            nowMs = snapshot.timestampMs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardStateFactory.buildDashboardUiState(
            snapshot = DashboardTelemetrySnapshot.fromRacingUiState(
                racingState = RacingUiState(isSensorAvailable = inputRepository.isSensorAvailable),
                timestampMs = 0L
            ),
            configuration = DashboardConfiguration(),
            availability = TelemetryAvailability.CurrentDrivePadSource,
            nowMs = 0L
        )
    )

    init {
        // Only run the blinker ticker while an indicator or hazard is active to prevent unnecessary recompositions
        viewModelScope.launch {
            inputRepository.controllerState
                .map { it.leftIndicator || it.rightIndicator || it.hazardLights }
                .distinctUntilChanged()
                .collect { shouldBlink ->
                    if (shouldBlink) {
                        startBlinkerLoop()
                    } else {
                        stopBlinkerLoop()
                    }
                }
        }
    }

    private fun startBlinkerLoop() {
        if (blinkerJob?.isActive == true) return
        _indicatorBlinkOn.value = true
        blinkerJob = viewModelScope.launch {
            while (true) {
                delay(380)
                _indicatorBlinkOn.update { !it }
            }
        }
    }

    private fun stopBlinkerLoop() {
        blinkerJob?.cancel()
        blinkerJob = null
        _indicatorBlinkOn.value = true
    }

    private fun showFeedback(message: String) {
        feedbackJob?.cancel()
        feedbackJob = viewModelScope.launch {
            _calibrationFeedback.value = message
            delay(2000)
            _calibrationFeedback.value = null
        }
    }

    // --- Steering Controls ---
    fun onSteeringDrag(deltaAngleDegrees: Float) {
        inputRepository.onSteeringDrag(deltaAngleDegrees)
    }

    fun onSteeringReleased() {
        inputRepository.onSteeringReleased()
    }

    // --- Digital Press-and-Hold Pedals ---
    fun onThrottlePressed() {
        inputRepository.setAcceleratorPressed(true)
    }

    fun onThrottleReleased() {
        inputRepository.setAcceleratorPressed(false)
    }

    fun onBrakePressed() {
        inputRepository.setBrakePressed(true)
    }

    fun onBrakeReleased() {
        inputRepository.setBrakePressed(false)
    }

    fun onHandbrakeToggled() {
        inputRepository.toggleHandbrake()
    }

    // --- Transmission & Gearbox Controls ---
    fun onTransmissionModeChanged(mode: TransmissionMode) {
        inputRepository.setTransmissionMode(mode)
    }

    fun onSelectAutoGear(gear: AutoGear) {
        inputRepository.selectAutoGear(gear)
    }

    fun onShiftUp(maxGear: Int = 6) {
        inputRepository.shiftUp(maxGear)
    }

    fun onShiftDown(minGear: Int = -1) {
        inputRepository.shiftDown(minGear)
    }

    // --- Motion Steering & Calibration ---
    fun onMotionSteeringToggled(enabled: Boolean) {
        if (enabled && !inputRepository.isSensorAvailable) {
            showFeedback("Gyroscope / motion sensor unavailable on this device")
            inputRepository.toggleMotionSteering(false)
            return
        }
        inputRepository.toggleMotionSteering(enabled)
    }

    fun calibrateSteering() {
        if (!inputRepository.isSensorAvailable) {
            showFeedback("Motion sensor unavailable")
            return
        }
        val success = inputRepository.calibrateSteering()
        showFeedback(
            if (success) "Calibrated to current angle"
            else "Calibration failed: Hold phone level"
        )
    }

    fun resetCalibration() {
        inputRepository.resetSteeringCalibration()
        showFeedback("Calibration reset to 0°")
    }

    // --- Vehicle Functions ---
    fun toggleLights() = inputRepository.toggleLights()
    fun setHornActive(active: Boolean) = inputRepository.setHorn(active)
    fun toggleWipers() = inputRepository.toggleWipers()
    fun toggleHazard() = inputRepository.toggleHazard()
    fun toggleCruise() = inputRepository.toggleCruise()
    fun toggleEngine() = inputRepository.toggleEngine()
    fun toggleLeftIndicator() = inputRepository.toggleLeftIndicator()
    fun toggleRightIndicator() = inputRepository.toggleRightIndicator()

    // --- Live Telemetry (RPM & Speed) Physics Simulation ---
    fun stepSimulation(dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0f, 0.05f)
        if (dt <= 0f) return

        val ctrl = inputRepository.controllerState.value
        val idleFloor = if (ctrl.engine) 850f else 0f
        val isNeutralOrPark = ctrl.currentGear == 0 || ctrl.currentGear <= -2
        val isParked = ctrl.currentGear <= -2
        val hasThrottle = ctrl.throttle > 0.05f
        val hasBrake = ctrl.brake > 0.05f
        val hasHandbrake = ctrl.handbrake

        _telemetrySimulation.update { prev ->
            var speed = prev.speedKmh
            var rpm = prev.rpm
            var limiterPhase = prev.revLimiterPhase

            // 1. Speed evolution
            when {
                isParked -> {
                    speed = 0f
                }
                hasBrake || hasHandbrake -> {
                    val decel = (if (hasBrake) 88f else 0f) + (if (hasHandbrake) 68f else 0f)
                    speed = (speed - decel * dt).coerceAtLeast(0f)
                }
                isNeutralOrPark -> {
                    // Coast down in neutral
                    speed = (speed - 11f * dt).coerceAtLeast(0f)
                }
                else -> {
                    // In gear: Reverse (-1) or Forward (1..6 / Auto D)
                    val gearMax = if (ctrl.transmissionMode == TransmissionMode.AUTO && ctrl.currentGear >= 1) {
                        260f
                    } else {
                        gearTopSpeedKmh(ctrl.currentGear)
                    }
                    val activeGearForTorque = if (ctrl.transmissionMode == TransmissionMode.AUTO && ctrl.currentGear >= 1) {
                        autoVirtualGear(speed)
                    } else {
                        ctrl.currentGear
                    }

                    if (ctrl.engine && hasThrottle) {
                        val accelRate = when (abs(activeGearForTorque)) {
                            1 -> 34f
                            2 -> 28f
                            3 -> 22f
                            4 -> 17f
                            5 -> 13f
                            else -> 10f
                        } * ctrl.throttle
                        speed = (speed + accelRate * dt).coerceAtMost(gearMax)
                    } else if (ctrl.engine && ctrl.cruiseControl && ctrl.currentGear >= 1 && speed > 5f) {
                        // Maintain current speed under cruise control while engine is running
                        speed = speed.coerceIn(0f, gearMax)
                    } else {
                        // Rolling + engine braking drag (no acceleration when engine is off)
                        speed = (speed - 12f * dt).coerceAtLeast(0f)
                    }
                }
            }

            // 2. RPM evolution
            val targetRpm: Float
            if (!ctrl.engine) {
                limiterPhase = 0f
                rpm = approachValue(rpm, 0f, upRate = 0f, downRate = 5500f * dt)
            } else if (isNeutralOrPark) {
                if (hasThrottle) {
                    if (rpm >= 7700f) {
                        limiterPhase += dt * 28f
                        targetRpm = if ((limiterPhase.toInt() % 2) == 0) 7880f else 7520f
                    } else {
                        limiterPhase = 0f
                        targetRpm = 7850f
                    }
                    rpm = approachValue(rpm, targetRpm, upRate = 7200f * dt, downRate = 4800f * dt)
                } else {
                    limiterPhase = 0f
                    rpm = approachValue(rpm, idleFloor, upRate = 3000f * dt, downRate = 4500f * dt)
                }
            } else {
                val activeRatioGear = if (ctrl.transmissionMode == TransmissionMode.AUTO && ctrl.currentGear >= 1) {
                    autoVirtualGear(speed)
                } else {
                    ctrl.currentGear
                }
                val ratioTopSpeed = gearTopSpeedKmh(activeRatioGear)
                val normalizedBand = (speed / ratioTopSpeed).coerceIn(0f, 1.05f)

                val wheelRpm = (normalizedBand * 7250f).coerceIn(0f, 7950f)
                val launchSlipFloor = if (hasThrottle && !hasBrake && !hasHandbrake) {
                    maxOf(idleFloor, 1150f + ctrl.throttle * 1850f)
                } else {
                    idleFloor
                }

                var rawTarget = maxOf(wheelRpm, launchSlipFloor)
                if (hasThrottle && rawTarget >= 7750f) {
                    limiterPhase += dt * 26f
                    rawTarget = if ((limiterPhase.toInt() % 2) == 0) 7880f else 7560f
                } else {
                    limiterPhase = 0f
                }

                val snapUp = if (hasThrottle) 5800f * dt else 4500f * dt
                val snapDown = if (hasBrake || hasHandbrake) 6200f * dt else 4200f * dt
                rpm = approachValue(rpm, rawTarget, upRate = snapUp, downRate = snapDown)
            }

            TelemetrySimulationState(
                speedKmh = speed.coerceIn(0f, 320f),
                rpm = rpm.coerceIn(0f, 8000f),
                revLimiterPhase = limiterPhase
            )
        }
    }

    private fun autoVirtualGear(speedKmh: Float): Int = when {
        speedKmh < 42f -> 1
        speedKmh < 82f -> 2
        speedKmh < 126f -> 3
        speedKmh < 172f -> 4
        speedKmh < 218f -> 5
        else -> 6
    }

    private fun gearTopSpeedKmh(gear: Int): Float = when (gear) {
        -1 -> 45f
        1 -> 55f
        2 -> 95f
        3 -> 140f
        4 -> 185f
        5 -> 235f
        else -> 290f
    }

    private fun gearMinSpeedKmh(gear: Int): Float = when (gear) {
        2 -> 32f
        3 -> 65f
        4 -> 105f
        5 -> 145f
        6 -> 190f
        else -> 0f
    }

    private fun approachValue(current: Float, target: Float, upRate: Float, downRate: Float): Float {
        return when {
            current < target -> (current + upRate).coerceAtMost(target)
            current > target -> (current - downRate).coerceAtLeast(target)
            else -> target
        }
    }

    // --- Lifecycle Safeguards ---
    fun onPause() {
        _isDashboardPaused.value = true
        stopBlinkerLoop()
        inputRepository.stopSensors()
        inputRepository.safetyResetAllInputs()
    }

    fun onResume() {
        _isDashboardPaused.value = false
        inputRepository.startSensors()
        val state = inputRepository.controllerState.value
        if (state.leftIndicator || state.rightIndicator || state.hazardLights) {
            startBlinkerLoop()
        }
    }
}
