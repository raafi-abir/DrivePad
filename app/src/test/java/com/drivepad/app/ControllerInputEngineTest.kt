package com.drivepad.app

import com.drivepad.app.core.input.SteeringCalibrationManager
import com.drivepad.app.core.input.SteeringMath
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.core.sensor.MotionSteeringManager
import com.drivepad.app.data.repository.ControllerInputRepositoryImpl
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.NormalizedControllerState
import com.drivepad.app.domain.model.SteeringResponseCurve
import com.drivepad.app.domain.model.TransmissionMode
import com.drivepad.app.feature.racing.AutoGear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class ControllerInputEngineTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakePreferencesRepository = object : UserPreferencesRepository {
        override val controllerConfigFlow: Flow<ControllerConfig> = flowOf(ControllerConfig())
        override val selectedProfileIdFlow: Flow<String> = flowOf("ets2_truck")

        override suspend fun updateSteeringSensitivity(sensitivity: Float) {}
        override suspend fun updateSteeringDeadzone(deadzone: Float) {}
        override suspend fun updateMaxSteeringAngle(angle: Float) {}
        override suspend fun updateSteeringResponseCurve(curve: SteeringResponseCurve) {}
        override suspend fun updateInvertSteering(invert: Boolean) {}
        override suspend fun updateMotionSteeringEnabled(enabled: Boolean) {}
        override suspend fun updateMotionSmoothing(factor: Float) {}
        override suspend fun updateHapticFeedback(enabled: Boolean) {}
        override suspend fun updateReturnToCenter(enabled: Boolean) {}
        override suspend fun updateDefaultTransmission(mode: TransmissionMode) {}
        override suspend fun updateNetworkSettings(port: Int, hostIp: String) {}
        override suspend fun updateTargetUpdateFrequency(frequencyHz: Int) {}
        override suspend fun updateSelectedProfileId(profileId: String) {}
        override suspend fun resetToDefaults() {}
    }

    private lateinit var repository: ControllerInputRepositoryImpl

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        repository = ControllerInputRepositoryImpl(
            context = context,
            preferencesRepository = fakePreferencesRepository,
            scope = CoroutineScope(testDispatcher)
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // 1. Steering Normalization Test
    @Test
    fun testSteeringNormalization() {
        val center = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 0.0f,
            maxAngleDegrees = 40.0f,
            deadzone = 0.0f
        )
        assertEquals(0.0f, center, 0.001f)

        val halfRight = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 20.0f,
            maxAngleDegrees = 40.0f,
            deadzone = 0.0f
        )
        assertEquals(0.5f, halfRight, 0.001f)

        val halfLeft = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = -20.0f,
            maxAngleDegrees = 40.0f,
            deadzone = 0.0f
        )
        assertEquals(-0.5f, halfLeft, 0.001f)
    }

    // 2. Steering Sensitivity & Response Curves Test
    @Test
    fun testSteeringSensitivityAndCurves() {
        val scaled = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 10.0f,
            maxAngleDegrees = 40.0f,
            deadzone = 0.0f,
            sensitivity = 1.5f
        )
        assertEquals(0.375f, scaled, 0.001f)

        val exponential = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 20.0f,
            maxAngleDegrees = 40.0f,
            deadzone = 0.0f,
            sensitivity = 1.0f,
            curve = SteeringResponseCurve.EXPONENTIAL
        )
        // 0.5^1.6 < 0.5 for finer center precision
        assertTrue(exponential > 0.0f && exponential < 0.5f)
    }

    // 3. Dead-zone Calculations
    @Test
    fun testDeadzoneCalculations() {
        val insideDeadzone = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 2.0f,
            maxAngleDegrees = 40.0f,
            deadzone = 0.06f
        )
        assertEquals(0.0f, insideDeadzone, 0.0001f)

        val outsideDeadzone = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 8.0f,
            maxAngleDegrees = 40.0f,
            deadzone = 0.06f
        )
        assertTrue("Output should be greater than 0 once past deadzone", outsideDeadzone > 0.0f)
    }

    // 4. Steering Limits (Clamping at -1.0 and +1.0 and NaN safety)
    @Test
    fun testSteeringLimitsAndNaNGuards() {
        val pastMaxRight = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 120.0f,
            maxAngleDegrees = 40.0f
        )
        assertEquals(1.0f, pastMaxRight, 0.0001f)

        val pastMaxLeft = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = -120.0f,
            maxAngleDegrees = 40.0f
        )
        assertEquals(-1.0f, pastMaxLeft, 0.0001f)

        assertEquals(0.0f, SteeringMath.calculateNormalizedSteering(Float.NaN), 0.0f)
        assertEquals(0.0f, SteeringMath.calculateNormalizedSteering(Float.POSITIVE_INFINITY), 0.0f)
    }

    // 5. Calibration Behaviour (Neutral Zero Reference & Non-Horizontal Rejection)
    @Test
    fun testCalibrationBehavior() {
        val calib = SteeringCalibrationManager()
        assertFalse(calib.isCalibrated)

        // Phone held at +15 degrees naturally
        val success = calib.calibrate(15.0f)
        assertTrue(success)
        assertTrue(calib.isCalibrated)
        assertEquals(15.0f, calib.currentNeutralOffset, 0.001f)

        // Tilting to +15 degrees should be recognized as neutral (0.00)
        val atNeutral = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 15.0f,
            neutralOffsetDegrees = calib.currentNeutralOffset,
            maxAngleDegrees = 40.0f,
            deadzone = 0.0f
        )
        assertEquals(0.0f, atNeutral, 0.001f)

        // Tilting to +35 degrees is a +20 degree delta (0.50 normalized)
        val rightTilt = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 35.0f,
            neutralOffsetDegrees = calib.currentNeutralOffset,
            maxAngleDegrees = 40.0f,
            deadzone = 0.0f
        )
        assertEquals(0.5f, rightTilt, 0.001f)

        // Reject invalid / extreme angle
        assertFalse(calib.calibrate(Float.NaN))
        assertFalse(calib.calibrate(120.0f))

        // Reset calibration
        calib.resetCalibration()
        assertFalse(calib.isCalibrated)
        assertEquals(0.0f, calib.currentNeutralOffset, 0.001f)
    }

    // 6. Motion Steering Manager Roll Processing
    @Test
    fun testMotionSteeringRollProcessing() {
        val context = RuntimeEnvironment.getApplication()
        val calib = SteeringCalibrationManager()
        val manager = MotionSteeringManager(context, calib)
        manager.activeConfig = ControllerConfig(
            maxSteeringAngleDegrees = 40.0f,
            steeringDeadzone = 0.0f,
            motionSmoothingFactor = 0.0f // Direct without lag for deterministic step check
        )

        manager.processRollDegrees(20.0f)
        assertEquals(20.0f, manager.rawTiltDegrees.value, 0.01f)
        assertEquals(0.5f, manager.normalizedSteering.value, 0.01f)

        // Calibrate at 20 degrees -> normalized steering drops to 0.0
        assertTrue(calib.calibrate(20.0f))
        manager.recalculateWithCurrentConfig()
        assertEquals(0.0f, manager.normalizedSteering.value, 0.01f)

        // Invalid reading ignored
        manager.processRollDegrees(Float.NaN)
        assertFalse(manager.normalizedSteering.value.isNaN())
    }

    // 7. Steering Inversion
    @Test
    fun testSteeringInversion() {
        val normal = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 20.0f,
            maxAngleDegrees = 40.0f,
            deadzone = 0.0f,
            invert = false
        )
        assertEquals(0.5f, normal, 0.001f)

        val inverted = SteeringMath.calculateNormalizedSteering(
            rawAngleDegrees = 20.0f,
            maxAngleDegrees = 40.0f,
            deadzone = 0.0f,
            invert = true
        )
        assertEquals(-0.5f, inverted, 0.001f)
    }

    // 8. Digital Accelerator Press and Release (STRICTLY 0.0 OR 1.0)
    @Test
    fun testDigitalAcceleratorPressAndRelease() = runTest {
        advanceUntilIdle()

        assertEquals(0.0f, repository.controllerState.value.throttle, 0.0f)

        repository.setAcceleratorPressed(true)
        assertEquals(1.0f, repository.controllerState.value.throttle, 0.0f)

        repository.setAcceleratorPressed(false)
        assertEquals(0.0f, repository.controllerState.value.throttle, 0.0f)
    }

    // 9. Digital Brake Press and Release (STRICTLY 0.0 OR 1.0)
    @Test
    fun testDigitalBrakePressAndRelease() = runTest {
        advanceUntilIdle()

        assertEquals(0.0f, repository.controllerState.value.brake, 0.0f)

        repository.setBrakePressed(true)
        assertEquals(1.0f, repository.controllerState.value.brake, 0.0f)

        repository.setBrakePressed(false)
        assertEquals(0.0f, repository.controllerState.value.brake, 0.0f)
    }

    // 10. Touch Cancellation & Lifecycle Safeguard Test
    @Test
    fun testTouchCancellationSafeguard() = runTest {
        advanceUntilIdle()

        repository.setAcceleratorPressed(true)
        repository.setBrakePressed(true)
        repository.setHorn(true)

        // App backgrounding or gesture cancel triggers safety reset
        repository.stopSensors()

        assertEquals(0.0f, repository.controllerState.value.throttle, 0.0f)
        assertEquals(0.0f, repository.controllerState.value.brake, 0.0f)
        assertFalse(repository.controllerState.value.horn)
    }

    // 11. Digital Pedals Invariant & Auto Gear Preservation
    @Test
    fun testDigitalPedalsNeverProduceIntermediateValues() {
        val sanitizedHigh = NormalizedControllerState.sanitize(
            steering = Float.NaN,
            throttle = 0.45f,
            brake = 0.12f,
            currentGear = -2 // Park
        )
        assertEquals(0.0f, sanitizedHigh.steering, 0.0f)
        assertEquals(1.0f, sanitizedHigh.throttle, 0.0f)
        assertEquals(1.0f, sanitizedHigh.brake, 0.0f)
        assertEquals(-2, sanitizedHigh.currentGear)

        val sanitizedZero = NormalizedControllerState.sanitize(
            steering = 0.0f,
            throttle = 0.0f,
            brake = 0.0f
        )
        assertEquals(0.0f, sanitizedZero.throttle, 0.0f)
        assertEquals(0.0f, sanitizedZero.brake, 0.0f)
    }

    // 12. Gear Shifting & Vehicle Functions Consistency
    @Test
    fun testGearShiftingAndVehicleFunctions() = runTest {
        advanceUntilIdle()

        repository.setTransmissionMode(TransmissionMode.AUTO)
        repository.selectAutoGear(AutoGear.R)
        assertEquals(-1, repository.controllerState.value.currentGear)

        repository.selectAutoGear(AutoGear.P)
        assertEquals(-2, repository.controllerState.value.currentGear)

        // Switching from Auto Park (-2) to Manual enters Neutral (0)
        repository.setTransmissionMode(TransmissionMode.MANUAL)
        assertEquals(0, repository.controllerState.value.currentGear)

        repository.shiftUp(maxGear = 6)
        assertEquals(1, repository.controllerState.value.currentGear)
        repository.shiftUp(maxGear = 6)
        assertEquals(2, repository.controllerState.value.currentGear)

        // Downshift: 2 -> 1 -> 0 (Neutral) -> -1 (Reverse) -> stays at -1
        repository.shiftDown()
        assertEquals(1, repository.controllerState.value.currentGear)
        repository.shiftDown()
        assertEquals(0, repository.controllerState.value.currentGear)
        repository.shiftDown()
        assertEquals(-1, repository.controllerState.value.currentGear)
        repository.shiftDown()
        assertEquals(-1, repository.controllerState.value.currentGear)

        // Upshift from Reverse: -1 -> 0 (Neutral) -> 1
        repository.shiftUp(maxGear = 6)
        assertEquals(0, repository.controllerState.value.currentGear)
        repository.shiftUp(maxGear = 6)
        assertEquals(1, repository.controllerState.value.currentGear)

        repository.toggleLeftIndicator()
        assertTrue(repository.controllerState.value.leftIndicator)
        assertFalse(repository.controllerState.value.rightIndicator)

        repository.toggleRightIndicator()
        assertFalse(repository.controllerState.value.leftIndicator)
        assertTrue(repository.controllerState.value.rightIndicator)

        repository.toggleHazard()
        assertTrue(repository.controllerState.value.hazardLights)
        assertFalse(repository.controllerState.value.leftIndicator)
        assertFalse(repository.controllerState.value.rightIndicator)
    }
}
