package com.drivepad.app

import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.data.repository.ControllerInputRepositoryImpl
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.SteeringResponseCurve
import com.drivepad.app.domain.model.TransmissionMode
import com.drivepad.app.feature.racing.AutoGear
import com.drivepad.app.feature.racing.RacingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class RacingViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val configFlow = MutableStateFlow(ControllerConfig(steeringDeadzone = 0.0f))

    private val fakePreferencesRepository = object : UserPreferencesRepository {
        override val controllerConfigFlow: Flow<ControllerConfig> = configFlow
        override val selectedProfileIdFlow: Flow<String> = MutableStateFlow("ets2_truck")

        override suspend fun updateSteeringSensitivity(sensitivity: Float) {}
        override suspend fun updateSteeringDeadzone(deadzone: Float) {}
        override suspend fun updateMaxSteeringAngle(angle: Float) {}
        override suspend fun updateSteeringResponseCurve(curve: SteeringResponseCurve) {}
        override suspend fun updateInvertSteering(invert: Boolean) {}
        override suspend fun updateMotionSteeringEnabled(enabled: Boolean) {
            configFlow.value = configFlow.value.copy(motionSteeringEnabled = enabled)
        }
        override suspend fun updateMotionSmoothing(factor: Float) {}
        override suspend fun updateHapticFeedback(enabled: Boolean) {}
        override suspend fun updateReturnToCenter(enabled: Boolean) {}
        override suspend fun updateDefaultTransmission(mode: TransmissionMode) {}
        override suspend fun updateNetworkSettings(port: Int, hostIp: String) {}
        override suspend fun updateTargetUpdateFrequency(frequencyHz: Int) {}
        override suspend fun updateSelectedProfileId(profileId: String) {}
        override suspend fun resetToDefaults() {}
    }

    private lateinit var inputRepository: ControllerInputRepositoryImpl
    private lateinit var viewModel: RacingViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        configFlow.value = ControllerConfig(steeringDeadzone = 0.0f)
        val context = RuntimeEnvironment.getApplication()
        inputRepository = ControllerInputRepositoryImpl(
            context = context,
            preferencesRepository = fakePreferencesRepository,
            scope = CoroutineScope(testDispatcher)
        )
        viewModel = RacingViewModel(fakePreferencesRepository, inputRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun steeringDrag_normalizesValueAccurately() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        // Drag 67.5 degrees right (half of 135 max)
        viewModel.onSteeringDrag(67.5f)
        advanceUntilIdle()
        assertEquals(67.5f, viewModel.uiState.value.steeringState.angleDegrees, 0.01f)
        assertEquals(0.5f, viewModel.uiState.value.steeringState.normalizedValue, 0.01f)
        assertTrue(viewModel.uiState.value.steeringState.isInteracting)

        // Drag full left (beyond -135 degrees)
        viewModel.onSteeringDrag(-300f)
        advanceUntilIdle()
        assertEquals(-135f, viewModel.uiState.value.steeringState.angleDegrees, 0.01f)
        assertEquals(-1.0f, viewModel.uiState.value.steeringState.normalizedValue, 0.01f)

        collectJob.cancel()
    }

    @Test
    fun throttleAndBrake_operateAsDigitalPressAndRelease() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.onThrottlePressed()
        advanceUntilIdle()
        assertEquals(1.0f, viewModel.uiState.value.pedalsState.throttle, 0.0f)

        viewModel.onThrottleReleased()
        advanceUntilIdle()
        assertEquals(0.0f, viewModel.uiState.value.pedalsState.throttle, 0.0f)

        viewModel.onBrakePressed()
        advanceUntilIdle()
        assertEquals(1.0f, viewModel.uiState.value.pedalsState.brake, 0.0f)

        viewModel.onBrakeReleased()
        advanceUntilIdle()
        assertEquals(0.0f, viewModel.uiState.value.pedalsState.brake, 0.0f)

        viewModel.onHandbrakeToggled()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.pedalsState.handbrakeEngaged)

        collectJob.cancel()
    }

    @Test
    fun gearbox_autoAndManualTransitions() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        // Test Auto Gears
        viewModel.onTransmissionModeChanged(TransmissionMode.AUTO)
        viewModel.onSelectAutoGear(AutoGear.R)
        advanceUntilIdle()
        assertEquals(AutoGear.R, viewModel.uiState.value.selectedAutoGear)

        viewModel.onSelectAutoGear(AutoGear.D)
        advanceUntilIdle()
        assertEquals(AutoGear.D, viewModel.uiState.value.selectedAutoGear)

        // Test Manual Sequential Shifting (1 -> 2 -> 3 -> 2 -> 1 -> 0 [N] -> -1 [R])
        viewModel.onTransmissionModeChanged(TransmissionMode.MANUAL)
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentManualGear)

        viewModel.onShiftUp(maxGear = 6)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.currentManualGear)

        viewModel.onShiftUp(maxGear = 6)
        advanceUntilIdle()
        assertEquals(3, viewModel.uiState.value.currentManualGear)

        viewModel.onShiftDown()
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.currentManualGear)

        viewModel.onShiftDown()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentManualGear)

        viewModel.onShiftDown()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentManualGear)

        viewModel.onShiftDown()
        advanceUntilIdle()
        assertEquals(-1, viewModel.uiState.value.currentManualGear)

        // Clamps at Reverse (-1)
        viewModel.onShiftDown()
        advanceUntilIdle()
        assertEquals(-1, viewModel.uiState.value.currentManualGear)

        // Upshift back through Neutral to 1
        viewModel.onShiftUp()
        advanceUntilIdle()
        assertEquals(0, viewModel.uiState.value.currentManualGear)

        viewModel.onShiftUp()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.currentManualGear)

        collectJob.cancel()
    }

    @Test
    fun vehicleFunctions_toggleCorrectly() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.vehicleFunctions.lightsOn)
        viewModel.toggleLights()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.vehicleFunctions.lightsOn)

        assertFalse(viewModel.uiState.value.vehicleFunctions.cruiseActive)
        viewModel.toggleCruise()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.vehicleFunctions.cruiseActive)

        assertFalse(viewModel.uiState.value.vehicleFunctions.engineRunning)
        viewModel.toggleEngine()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.vehicleFunctions.engineRunning)

        // Turn Indicators
        viewModel.toggleLeftIndicator()
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.vehicleFunctions.leftIndicatorActive)
        assertFalse(viewModel.uiState.value.vehicleFunctions.rightIndicatorActive)

        // Toggling Right turns off Left
        viewModel.toggleRightIndicator()
        testScheduler.runCurrent()
        assertFalse(viewModel.uiState.value.vehicleFunctions.leftIndicatorActive)
        assertTrue(viewModel.uiState.value.vehicleFunctions.rightIndicatorActive)

        viewModel.onPause()
        collectJob.cancel()
    }

    @Test
    fun rpmAndSpeed_reactToEngineThrottleGearShiftsAndBrake() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        // Resting state with engine off: 0 RPM, 0 km/h
        assertEquals(0f, viewModel.uiState.value.rpm, 0.01f)
        assertEquals(0f, viewModel.uiState.value.speedKmh, 0.01f)

        // Toggle Engine ON -> idles around 850 RPM
        viewModel.toggleEngine()
        advanceUntilIdle()
        repeat(40) { viewModel.stepSimulation(0.02f) }
        advanceUntilIdle()
        assertEquals(850f, viewModel.uiState.value.rpm, 15f)

        // Switch to Manual Gear 1 and press Accelerator
        viewModel.onTransmissionModeChanged(TransmissionMode.MANUAL)
        viewModel.onThrottlePressed()
        advanceUntilIdle()
        repeat(65) { viewModel.stepSimulation(0.02f) }
        advanceUntilIdle()

        val rpmInGear1 = viewModel.uiState.value.rpm
        val speedInGear1 = viewModel.uiState.value.speedKmh
        assertTrue("Expected speed > 30 km/h in Gear 1, was $speedInGear1", speedInGear1 > 30f)
        assertTrue("Expected high RPM (> 4000) in Gear 1, was $rpmInGear1", rpmInGear1 > 4000f)

        // Upshift to Gear 2 without throttle -> RPM drops smoothly for taller gear ratio
        viewModel.onThrottleReleased()
        viewModel.onShiftUp()
        advanceUntilIdle()
        repeat(20) { viewModel.stepSimulation(0.02f) }
        advanceUntilIdle()
        val rpmInGear2 = viewModel.uiState.value.rpm
        assertTrue("Expected RPM to drop on upshift ($rpmInGear2 < $rpmInGear1)", rpmInGear2 < rpmInGear1)

        // Brake to stop -> speed hits 0 and RPM settles back toward idle (850)
        viewModel.onBrakePressed()
        advanceUntilIdle()
        repeat(100) { viewModel.stepSimulation(0.02f) }
        advanceUntilIdle()
        assertEquals(0f, viewModel.uiState.value.speedKmh, 0.01f)
        assertEquals(850f, viewModel.uiState.value.rpm, 25f)

        viewModel.onBrakeReleased()
        viewModel.onPause()
        collectJob.cancel()
    }

    @Test
    fun engineOff_acceleratorDoesNotIncreaseSpeedOrRpm() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.vehicleFunctions.engineRunning)

        // Pressing accelerator in Auto D with engine OFF must keep speed and RPM at 0
        viewModel.onTransmissionModeChanged(TransmissionMode.AUTO)
        viewModel.onSelectAutoGear(AutoGear.D)
        viewModel.onThrottlePressed()
        advanceUntilIdle()
        repeat(60) { viewModel.stepSimulation(0.02f) }
        advanceUntilIdle()

        assertEquals(0f, viewModel.uiState.value.speedKmh, 0.01f)
        assertEquals(0f, viewModel.uiState.value.rpm, 0.01f)
        assertEquals(null, viewModel.uiState.value.autoActiveGearNumber)

        // Switching to Manual Gear 1 with engine OFF must also keep speed and RPM at 0
        viewModel.onTransmissionModeChanged(TransmissionMode.MANUAL)
        advanceUntilIdle()
        repeat(60) { viewModel.stepSimulation(0.02f) }
        advanceUntilIdle()

        assertEquals(0f, viewModel.uiState.value.speedKmh, 0.01f)
        assertEquals(0f, viewModel.uiState.value.rpm, 0.01f)

        viewModel.onThrottleReleased()
        viewModel.onPause()
        collectJob.cancel()
    }

    @Test
    fun autoMode_gearNumberIndicatorAdvancesThroughGearsAndReturnsToD() = runTest {
        val collectJob = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect()
        }
        advanceUntilIdle()

        viewModel.toggleEngine()
        viewModel.onTransmissionModeChanged(TransmissionMode.AUTO)
        viewModel.onSelectAutoGear(AutoGear.D)
        advanceUntilIdle()

        // Stationary in Auto D -> null (displays "D")
        assertEquals(null, viewModel.uiState.value.autoActiveGearNumber)

        // Pressing Accelerator immediately engages Gear 1 and steps up as speed rises
        viewModel.onThrottlePressed()
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.autoActiveGearNumber)

        // Accelerate past 42 km/h -> auto-shifts to Gear 2
        repeat(80) { viewModel.stepSimulation(0.03f) }
        advanceUntilIdle()
        assertTrue(
            "Expected auto gear >= 2 at speed ${viewModel.uiState.value.speedKmh}",
            (viewModel.uiState.value.autoActiveGearNumber ?: 0) >= 2
        )

        // Accelerate past 82 km/h -> auto-shifts to Gear 3
        repeat(100) { viewModel.stepSimulation(0.03f) }
        advanceUntilIdle()
        assertTrue(
            "Expected auto gear >= 3 at speed ${viewModel.uiState.value.speedKmh}",
            (viewModel.uiState.value.autoActiveGearNumber ?: 0) >= 3
        )

        // Brake to complete stop -> indicator returns to "D" (null)
        viewModel.onThrottleReleased()
        viewModel.onBrakePressed()
        advanceUntilIdle()
        repeat(120) { viewModel.stepSimulation(0.03f) }
        advanceUntilIdle()
        assertEquals(0f, viewModel.uiState.value.speedKmh, 0.01f)
        assertEquals(null, viewModel.uiState.value.autoActiveGearNumber)

        viewModel.onBrakeReleased()
        viewModel.onPause()
        collectJob.cancel()
    }
}
