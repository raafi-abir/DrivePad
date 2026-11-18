package com.drivepad.app

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drivepad.app.core.preferences.DashboardPreferencesRepositoryImpl
import com.drivepad.app.domain.model.DashboardAccentColor
import com.drivepad.app.domain.model.DashboardConfiguration
import com.drivepad.app.domain.model.DashboardDensity
import com.drivepad.app.domain.model.DashboardLayout
import com.drivepad.app.domain.model.DashboardStateFactory
import com.drivepad.app.domain.model.DashboardTelemetrySnapshot
import com.drivepad.app.domain.model.DashboardThemeMode
import com.drivepad.app.domain.model.GaugeBackgroundStyle
import com.drivepad.app.domain.model.GaugeConfiguration
import com.drivepad.app.domain.model.GaugeRegion
import com.drivepad.app.domain.model.GaugeSize
import com.drivepad.app.domain.model.GaugeStyle
import com.drivepad.app.domain.model.GaugeType
import com.drivepad.app.domain.model.GaugeValueState
import com.drivepad.app.domain.model.IndicatorActivationState
import com.drivepad.app.domain.model.SpeedUnit
import com.drivepad.app.domain.model.TelemetryAvailability
import com.drivepad.app.domain.model.TelemetrySourceStatus
import com.drivepad.app.domain.model.WarningIndicatorType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardCustomizationUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun dataStorePersistence_survivesRepositoryRestartAndRestoreDefaults() = runBlocking {
        val backingFile = File(tempFolder.root, "drivepad_dashboard_test.preferences_pb")

        // Session 1: persist customized dashboard configuration
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store1 = PreferenceDataStoreFactory.create(scope = scope1) { backingFile }
        val repo1 = DashboardPreferencesRepositoryImpl(store1)

        repo1.selectLayout(DashboardLayout.TRUCK, applyLayoutPreset = true)
        repo1.updateThemeMode(DashboardThemeMode.LIGHT)
        repo1.updateAccentColor(DashboardAccentColor.ELECTRIC_CYAN)
        repo1.updateGaugeBackgroundStyle(GaugeBackgroundStyle.TRANSLUCENT_GLASS)
        repo1.updateDensity(DashboardDensity.COMPACT)
        repo1.updateAnimationsEnabled(false)
        repo1.updateSpeedUnit(SpeedUnit.MPH)
        repo1.updateSpeedometerMaxScale(320)
        repo1.updateOverspeedWarning(enabled = true, thresholdKmh = 110)
        repo1.updateRpmRedlineThreshold(6400)
        repo1.updateGaugeConfiguration(
            GaugeConfiguration(
                type = GaugeType.SPEEDOMETER,
                visible = true,
                size = GaugeSize.LARGE,
                region = GaugeRegion.PRIMARY_MIDDLE,
                presentation = GaugeStyle.CIRCULAR,
                showLabel = false,
                showUnit = true,
                showWarningThreshold = false
            )
        )
        val saved = repo1.dashboardConfigFlow.first()
        scope1.cancel()

        // Session 2: new DataStore + Repository instance reads back identical configuration
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store2 = PreferenceDataStoreFactory.create(scope = scope2) { backingFile }
        val repo2 = DashboardPreferencesRepositoryImpl(store2)

        val restored = repo2.dashboardConfigFlow.first()
        assertEquals(DashboardLayout.TRUCK, restored.layout)
        assertEquals(DashboardThemeMode.LIGHT, restored.themeMode)
        assertEquals(DashboardAccentColor.ELECTRIC_CYAN, restored.accentColor)
        assertEquals(GaugeBackgroundStyle.TRANSLUCENT_GLASS, restored.gaugeBackgroundStyle)
        assertEquals(DashboardDensity.COMPACT, restored.density)
        assertFalse(restored.animationsEnabled)
        assertEquals(SpeedUnit.MPH, restored.speedUnit)
        assertEquals(320, restored.speedometerMaxScaleKmh)
        assertEquals(110, restored.overspeedThresholdKmh)
        assertEquals(6400, restored.rpmRedlineThreshold)

        val restoredSpeedo = restored.effectiveGaugeConfig(GaugeType.SPEEDOMETER)
        assertEquals(GaugeStyle.CIRCULAR, restoredSpeedo.presentation)
        assertEquals(GaugeSize.LARGE, restoredSpeedo.size)
        assertEquals(GaugeRegion.PRIMARY_MIDDLE, restoredSpeedo.region)
        assertFalse(restoredSpeedo.showLabel)
        assertFalse(restoredSpeedo.showWarningThreshold)
        assertEquals(saved, restored)

        // Reset to defaults clears dashboard preferences cleanly
        repo2.resetToDefaults()
        val afterReset = repo2.dashboardConfigFlow.first()
        assertEquals(DashboardConfiguration(), afterReset)
        scope2.cancel()
    }

    @Test
    fun legacyAndInvalidPreferences_migrateAndClampSafely() = runBlocking {
        val backingFile = File(tempFolder.root, "drivepad_legacy_migration.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope) { backingFile }

        store.edit { prefs ->
            prefs[intPreferencesKey("dashboard_schema_version")] = 0
            prefs[stringPreferencesKey("dashboard_layout")] = "INVALID_LAYOUT"
            prefs[stringPreferencesKey("dashboard_theme_mode")] = "LIGHT"
            prefs[stringPreferencesKey("dashboard_speed_unit")] = "KNOTS"
            prefs[intPreferencesKey("dashboard_speed_max_scale_kmh")] = 140
            prefs[intPreferencesKey("dashboard_overspeed_threshold_kmh")] = 999
            prefs[intPreferencesKey("dashboard_rpm_redline_threshold")] = 1200
            // Partial v0 gauge entry missing trailing threshold boolean
            prefs[stringPreferencesKey("dashboard_gauge_configs_v1")] =
                "SPEEDOMETER,true,LARGE,AUXILIARY_STRIP,CIRCULAR,true,true;UNKNOWN_GAUGE,true"
        }

        val repo = DashboardPreferencesRepositoryImpl(store)
        val migrated = repo.dashboardConfigFlow.first()

        assertEquals(DashboardConfiguration.CURRENT_SCHEMA_VERSION, migrated.schemaVersion)
        assertEquals(DashboardLayout.RACING, migrated.layout)
        assertEquals(DashboardThemeMode.LIGHT, migrated.themeMode)
        assertEquals(SpeedUnit.KMH, migrated.speedUnit)
        assertEquals(140, migrated.speedometerMaxScaleKmh)
        // Clamped to speedometerMaxScaleKmh
        assertEquals(140, migrated.overspeedThresholdKmh)
        // Clamped to minimum redline 3000 RPM
        assertEquals(3000, migrated.rpmRedlineThreshold)

        // SPEEDOMETER region sanitized out of AUXILIARY_STRIP to PRIMARY_TOP
        val speedo = migrated.effectiveGaugeConfig(GaugeType.SPEEDOMETER)
        assertEquals(GaugeRegion.PRIMARY_TOP, speedo.region)
        assertEquals(GaugeStyle.CIRCULAR, speedo.presentation)
        assertTrue(speedo.showWarningThreshold)

        // Direct decodeAndMigrate with empty prefs stays valid
        val emptyMigrated = DashboardPreferencesRepositoryImpl.decodeAndMigrate(preferencesOf())
        assertEquals(DashboardConfiguration(), emptyMigrated)
        scope.cancel()
    }

    @Test
    fun speedUnitConversion_neverMutatesSourceSnapshotAndPreservesWarningLogic() {
        val rawSpeedKmh = 100f
        val snapshot = DashboardTelemetrySnapshot(
            sourceStatus = TelemetrySourceStatus.LIVE,
            timestampMs = 5_000L,
            isLocalSimulation = true,
            engineRunning = true,
            speedKmh = rawSpeedKmh,
            rpm = 2400f,
            maxRpm = 8000f,
            gearLabel = "5"
        )

        val kmhConfig = DashboardConfiguration(
            speedUnit = SpeedUnit.KMH,
            overspeedWarningEnabled = true,
            overspeedThresholdKmh = 95
        )
        val mphConfig = kmhConfig.copy(speedUnit = SpeedUnit.MPH)

        val kmhState = DashboardStateFactory.buildDashboardUiState(snapshot, kmhConfig)
        val mphState = DashboardStateFactory.buildDashboardUiState(snapshot, mphConfig)

        val kmhSpeed = kmhState.speedState as GaugeValueState.Live
        val mphSpeed = mphState.speedState as GaugeValueState.Live

        assertEquals(100.0f, kmhSpeed.numericValue, 0.01f)
        assertEquals("km/h", kmhSpeed.unitLabel)
        assertTrue(kmhSpeed.isWarning)

        assertEquals(62.1371f, mphSpeed.numericValue, 0.05f)
        assertEquals("mph", mphSpeed.unitLabel)
        assertTrue(mphSpeed.isWarning)
        // Underlying snapshot value untouched
        assertEquals(rawSpeedKmh, snapshot.speedKmh ?: -1f, 0.0f)
    }

    @Test
    fun rpmGauge_handlesEngineOffAndConfigurableRedlineThreshold() {
        val engineOffSnapshot = DashboardTelemetrySnapshot(
            sourceStatus = TelemetrySourceStatus.LIVE,
            timestampMs = 1_000L,
            engineRunning = false,
            speedKmh = 0f,
            rpm = 0f
        )
        val offUi = DashboardStateFactory.buildDashboardUiState(
            engineOffSnapshot,
            DashboardConfiguration()
        )
        assertTrue(offUi.rpmState is GaugeValueState.EngineOff)

        val highRpmSnapshot = DashboardTelemetrySnapshot(
            sourceStatus = TelemetrySourceStatus.LIVE,
            timestampMs = 2_000L,
            engineRunning = true,
            speedKmh = 90f,
            rpm = 5600f,
            maxRpm = 7000f
        )

        val strictThresholdUi = DashboardStateFactory.buildDashboardUiState(
            highRpmSnapshot,
            DashboardConfiguration(rpmRedlineThreshold = 5500, warningsEnabled = true)
        )
        val relaxedThresholdUi = DashboardStateFactory.buildDashboardUiState(
            highRpmSnapshot,
            DashboardConfiguration(rpmRedlineThreshold = 6200, warningsEnabled = true)
        )

        val strictRpm = strictThresholdUi.rpmState as GaugeValueState.Live
        val relaxedRpm = relaxedThresholdUi.rpmState as GaugeValueState.Live
        assertTrue(strictRpm.isWarning)
        assertFalse(relaxedRpm.isWarning)
        assertEquals("5600", strictRpm.formattedValue)
        assertEquals(7000f, strictRpm.maxValue ?: 0f, 0.01f)
    }

    @Test
    fun staleDisconnectedPausedAndMissingFields_neverRenderZeroOrFakeLiveValues() {
        val config = DashboardConfiguration(layout = DashboardLayout.TRUCK)

        // 1. Stale telemetry (age > 2000ms) -> Stale state, not Live
        val staleSnapshot = DashboardTelemetrySnapshot(
            sourceStatus = TelemetrySourceStatus.LIVE,
            timestampMs = 1_000L,
            isLocalSimulation = false,
            engineRunning = true,
            speedKmh = 88f,
            rpm = 2200f,
            gearLabel = "4"
        )
        val staleUi = DashboardStateFactory.buildDashboardUiState(
            snapshot = staleSnapshot,
            configuration = config,
            nowMs = 5_000L,
            staleTimeoutMs = 2_000L
        )
        assertEquals(TelemetrySourceStatus.STALE, staleUi.effectiveStatus)
        assertTrue(staleUi.speedState is GaugeValueState.Stale)
        assertTrue(staleUi.rpmState is GaugeValueState.Stale)
        assertEquals("--", staleUi.gearLabel)

        // 2. Disconnected telemetry -> Disconnected state
        val disconnectedUi = DashboardStateFactory.buildDashboardUiState(
            snapshot = DashboardTelemetrySnapshot(sourceStatus = TelemetrySourceStatus.DISCONNECTED),
            configuration = config
        )
        assertTrue(disconnectedUi.speedState is GaugeValueState.Disconnected)
        assertTrue(disconnectedUi.rpmState is GaugeValueState.Disconnected)
        assertEquals("--", disconnectedUi.gearLabel)

        // 3. Paused telemetry -> Paused state
        val pausedUi = DashboardStateFactory.buildDashboardUiState(
            snapshot = DashboardTelemetrySnapshot(sourceStatus = TelemetrySourceStatus.PAUSED, gearLabel = "3"),
            configuration = config
        )
        assertTrue(pausedUi.speedState is GaugeValueState.Paused)
        assertTrue(pausedUi.rpmState is GaugeValueState.Paused)

        // 4. Null ETS2 truck fields stay Unavailable (never coerced to 0) even with FullTruckTelemetrySource
        val partialLiveSnapshot = DashboardTelemetrySnapshot(
            sourceStatus = TelemetrySourceStatus.LIVE,
            timestampMs = 10_000L,
            isLocalSimulation = false,
            engineRunning = true,
            speedKmh = 60f,
            rpm = 1500f,
            gearLabel = "3",
            fuelRatio = null,
            waterTempCelsius = null,
            oilPressureBar = null,
            airPressureBar = null,
            batteryVolts = null,
            adBlueRatio = null,
            speedLimitKmh = null,
            parkingBrake = true,
            engineWarning = null
        )
        val partialUi = DashboardStateFactory.buildDashboardUiState(
            snapshot = partialLiveSnapshot,
            configuration = config,
            availability = TelemetryAvailability.FullTruckTelemetrySource
        )
        assertEquals(GaugeValueState.Unavailable, partialUi.auxiliaryGauges[GaugeType.FUEL])
        assertEquals(GaugeValueState.Unavailable, partialUi.auxiliaryGauges[GaugeType.WATER_TEMPERATURE])
        assertEquals(GaugeValueState.Unavailable, partialUi.auxiliaryGauges[GaugeType.OIL_PRESSURE])
        assertEquals(GaugeValueState.Unavailable, partialUi.auxiliaryGauges[GaugeType.AIR_PRESSURE])
        assertEquals(GaugeValueState.Unavailable, partialUi.auxiliaryGauges[GaugeType.BATTERY_VOLTAGE])
        assertEquals(GaugeValueState.Unavailable, partialUi.auxiliaryGauges[GaugeType.ADBLUE])
        assertEquals(GaugeValueState.Unavailable, partialUi.speedLimitState)

        // Indicators clearly distinguish ACTIVE vs UNAVAILABLE
        val parkItem = partialUi.indicators.first { it.type == WarningIndicatorType.PARKING_BRAKE }
        val engineFaultItem = partialUi.indicators.first { it.type == WarningIndicatorType.ENGINE_WARNING }
        assertEquals(IndicatorActivationState.ACTIVE, parkItem.activationState)
        assertEquals(IndicatorActivationState.UNAVAILABLE, engineFaultItem.activationState)
    }
}
