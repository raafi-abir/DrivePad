package com.drivepad.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToIndex
import com.drivepad.app.core.designsystem.DrivePadTheme
import com.drivepad.app.core.network.ControllerConnectionManager
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.data.repository.ControllerInputRepository
import com.drivepad.app.domain.model.ConnectionDiagnosticsState
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.LocalNetworkInterface
import com.drivepad.app.domain.model.NetworkRouteStatus
import com.drivepad.app.domain.model.NormalizedControllerState
import com.drivepad.app.domain.model.SteeringResponseCurve
import com.drivepad.app.domain.model.TransmissionMode
import com.drivepad.app.feature.diagnostics.DiagnosticsScreen
import com.drivepad.app.feature.diagnostics.DiagnosticsViewModel
import com.drivepad.app.feature.racing.AutoGear
import com.drivepad.app.feature.settings.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@OptIn(ExperimentalCoroutinesApi::class)
class Phase4UiIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testDispatcher = StandardTestDispatcher()

    private val configState = MutableStateFlow(ControllerConfig())

    private val fakePreferences = object : UserPreferencesRepository {
        override val controllerConfigFlow: Flow<ControllerConfig> = configState.asStateFlow()
        override val selectedProfileIdFlow: Flow<String> = MutableStateFlow("ets2_truck")
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
        override suspend fun updateNetworkSettings(port: Int, hostIp: String) {
            configState.value = configState.value.copy(udpPort = port, targetHostIp = hostIp)
        }
        override suspend fun updateTargetUpdateFrequency(frequencyHz: Int) {
            configState.value = configState.value.copy(targetUpdateFrequencyHz = frequencyHz)
        }
        override suspend fun updateSelectedProfileId(profileId: String) {}
        override suspend fun resetToDefaults() {
            configState.value = ControllerConfig()
        }
    }

    private val fakeInputRepo = object : ControllerInputRepository {
        override val controllerState: StateFlow<NormalizedControllerState> =
            MutableStateFlow(NormalizedControllerState(steering = 0.25f, throttle = 1.0f, currentGear = 3))
        override val isSensorAvailable: Boolean = true
        override val isMotionSteeringActive: Boolean = false
        override val isTouchInteracting: StateFlow<Boolean> = MutableStateFlow(false)
        override fun onSteeringDrag(deltaAngleDegrees: Float) {}
        override fun onSteeringReleased() {}
        override fun setAcceleratorPressed(isPressed: Boolean) {}
        override fun setBrakePressed(isPressed: Boolean) {}
        override fun toggleHandbrake() {}
        override fun setTransmissionMode(mode: TransmissionMode) {}
        override fun selectAutoGear(gear: AutoGear) {}
        override fun shiftUp(maxGear: Int) {}
        override fun shiftDown(minGear: Int) {}
        override fun toggleLights() {}
        override fun setHorn(isPressed: Boolean) {}
        override fun toggleWipers() {}
        override fun toggleHazard() {}
        override fun toggleCruise() {}
        override fun toggleEngine() {}
        override fun toggleLeftIndicator() {}
        override fun toggleRightIndicator() {}
        override fun toggleMotionSteering(enabled: Boolean) {}
        override fun calibrateSteering(): Boolean = true
        override fun resetSteeringCalibration() {}
        override fun safetyResetAllInputs() {}
        override fun startSensors() {}
        override fun stopSensors() {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        configState.value = ControllerConfig()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun settingsViewModel_validatesAndPersistsNetworkConfig() = runTest {
        val vm = SettingsViewModel(fakePreferences, fakeInputRepo)

        // Invalid IP rejected
        assertFalse(vm.saveNetworkSettings("999.999.0.1", "42100", 250))
        assertNotNull(vm.networkError.value)

        // Invalid port rejected
        assertFalse(vm.saveNetworkSettings("192.168.42.10", "70000", 250))
        assertNotNull(vm.networkError.value)

        // Valid config accepted and saved
        assertTrue(vm.saveNetworkSettings("192.168.42.55", "42155", 120))
        advanceUntilIdle()
        assertNull(vm.networkError.value)
        assertEquals("192.168.42.55", configState.value.targetHostIp)
        assertEquals(42155, configState.value.udpPort)
        assertEquals(120, configState.value.targetUpdateFrequencyHz)
    }

    @Test
    @Config(qualifiers = "w411dp-h915dp")
    fun diagnosticsScreen_displaysLiveConnectionTelemetryAndRoute() {
        val diagState = MutableStateFlow(
            ConnectionDiagnosticsState(
                status = ConnectionStatus.CONNECTED,
                targetHostIp = "192.168.42.1",
                targetPort = 42100,
                targetUpdateHz = 250,
                actualUpdateHz = 248.4,
                packetsSent = 1250,
                packetsReceived = 12,
                sequenceGapsDetected = 0,
                estimatedPacketLossPercent = 0.0,
                rttMs = 1.42,
                smoothedRttMs = 1.50,
                jitterMs = 0.18,
                lastSuccessfulCommElapsedMs = 35L,
                networkRoute = NetworkRouteStatus(
                    hasLocalRoute = true,
                    isLikelyUsbTether = true,
                    activeInterfaces = listOf(LocalNetworkInterface("rndis0", "192.168.42.129", true)),
                    routeSummary = "USB tether route candidate: rndis0 (192.168.42.129)"
                )
            )
        )
        val fakeManager = object : ControllerConnectionManager {
            override val diagnosticsState: StateFlow<ConnectionDiagnosticsState> = diagState
            override fun connect() {}
            override fun disconnect() {}
            override fun sendPingNow() {}
            override fun refreshNetworkRoute() {}
        }
        val diagVm = DiagnosticsViewModel(fakeManager, fakeInputRepo, fakePreferences)

        composeRule.setContent {
            DrivePadTheme(darkTheme = true) {
                DiagnosticsScreen(viewModel = diagVm, onNavigateBack = {})
            }
        }
        testDispatcher.scheduler.advanceUntilIdle()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("diag_status_label").assertIsDisplayed()
        composeRule.onNodeWithText("Connected (Active)").assertIsDisplayed()
        composeRule.onNodeWithText("192.168.42.1:42100").assertIsDisplayed()
        composeRule.onNodeWithTag("diagnostics_list").performScrollToIndex(1)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("250 Hz / 248.4 Hz").assertIsDisplayed()
        composeRule.onNodeWithText("1.42 ms").assertIsDisplayed()
        composeRule.onNodeWithText("0.18 ms").assertIsDisplayed()
    }
}
