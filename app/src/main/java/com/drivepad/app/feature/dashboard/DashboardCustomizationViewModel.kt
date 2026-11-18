package com.drivepad.app.feature.dashboard

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivepad.app.core.preferences.DashboardPreferencesRepository
import com.drivepad.app.domain.model.DashboardAccentColor
import com.drivepad.app.domain.model.DashboardConfiguration
import com.drivepad.app.domain.model.DashboardDensity
import com.drivepad.app.domain.model.DashboardLayout
import com.drivepad.app.domain.model.DashboardStateFactory
import com.drivepad.app.domain.model.DashboardTelemetrySnapshot
import com.drivepad.app.domain.model.DashboardThemeMode
import com.drivepad.app.domain.model.DashboardUiState
import com.drivepad.app.domain.model.GaugeBackgroundStyle
import com.drivepad.app.domain.model.GaugeConfiguration
import com.drivepad.app.domain.model.GaugeRegion
import com.drivepad.app.domain.model.GaugeSize
import com.drivepad.app.domain.model.GaugeStyle
import com.drivepad.app.domain.model.GaugeType
import com.drivepad.app.domain.model.SpeedUnit
import com.drivepad.app.domain.model.TelemetryAvailability
import com.drivepad.app.domain.model.TelemetrySourceStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PreviewTelemetryScenario(val label: String) {
    LIVE_CRUISING("Live Cruise"),
    WARNING_PEAK("Redline & Overspeed"),
    ENGINE_OFF("Engine Off"),
    DISCONNECTED("Disconnected"),
    STALE("Stale"),
    PAUSED("Paused")
}

@Immutable
data class DashboardCustomizationUiState(
    val configuration: DashboardConfiguration = DashboardConfiguration(),
    val availability: TelemetryAvailability = TelemetryAvailability.CurrentDrivePadSource,
    val previewScenario: PreviewTelemetryScenario = PreviewTelemetryScenario.LIVE_CRUISING,
    val previewUiState: DashboardUiState = DashboardUiState(),
    val feedbackMessage: String? = null
)

class DashboardCustomizationViewModel(
    private val dashboardPreferencesRepository: DashboardPreferencesRepository,
    private val availability: TelemetryAvailability = TelemetryAvailability.CurrentDrivePadSource
) : ViewModel() {

    private val _previewScenario = MutableStateFlow(PreviewTelemetryScenario.LIVE_CRUISING)
    private val _feedbackMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<DashboardCustomizationUiState> = combine(
        dashboardPreferencesRepository.dashboardConfigFlow,
        _previewScenario,
        _feedbackMessage
    ) { config, scenario, feedback ->
        val (snapshot, nowMs) = buildPreviewSnapshot(scenario)
        val preview = DashboardStateFactory.buildDashboardUiState(
            snapshot = snapshot,
            configuration = config,
            availability = availability,
            nowMs = nowMs
        )
        DashboardCustomizationUiState(
            configuration = config,
            availability = availability,
            previewScenario = scenario,
            previewUiState = preview,
            feedbackMessage = feedback
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardCustomizationUiState(
            availability = availability,
            previewUiState = DashboardStateFactory.buildDashboardUiState(
                snapshot = buildPreviewSnapshot(PreviewTelemetryScenario.LIVE_CRUISING).first,
                configuration = DashboardConfiguration(),
                availability = availability,
                nowMs = buildPreviewSnapshot(PreviewTelemetryScenario.LIVE_CRUISING).second
            )
        )
    )

    fun onPreviewScenarioSelected(scenario: PreviewTelemetryScenario) {
        _previewScenario.value = scenario
    }

    fun onLayoutSelected(layout: DashboardLayout) {
        viewModelScope.launch {
            dashboardPreferencesRepository.selectLayout(layout, applyLayoutPreset = true)
            _feedbackMessage.value = "Layout set to ${layout.displayName}"
        }
    }

    fun onThemeModeSelected(themeMode: DashboardThemeMode) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateThemeMode(themeMode)
        }
    }

    fun onAccentColorSelected(accentColor: DashboardAccentColor) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateAccentColor(accentColor)
        }
    }

    fun onGaugeBackgroundStyleSelected(style: GaugeBackgroundStyle) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateGaugeBackgroundStyle(style)
        }
    }

    fun onDensitySelected(density: DashboardDensity) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateDensity(density)
        }
    }

    fun onAnimationsToggled(enabled: Boolean) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateAnimationsEnabled(enabled)
        }
    }

    fun onSpeedUnitSelected(unit: SpeedUnit) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateSpeedUnit(unit)
        }
    }

    fun onSpeedometerMaxScaleSelected(maxScaleKmh: Int) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateSpeedometerMaxScale(maxScaleKmh)
        }
    }

    fun onShowSpeedLimitToggled(show: Boolean) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateShowSpeedLimit(show)
        }
    }

    fun onOverspeedWarningChanged(enabled: Boolean, thresholdKmh: Int) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateOverspeedWarning(enabled, thresholdKmh)
        }
    }

    fun onRpmRedlineThresholdChanged(rpm: Int) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateRpmRedlineThreshold(rpm)
        }
    }

    fun onWarningsToggled(enabled: Boolean) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateWarningsEnabled(enabled)
        }
    }

    fun onUnavailablePlaceholdersToggled(show: Boolean) {
        viewModelScope.launch {
            dashboardPreferencesRepository.updateShowUnavailablePlaceholders(show)
        }
    }

    fun updateGaugeConfig(
        type: GaugeType,
        transform: (GaugeConfiguration) -> GaugeConfiguration
    ) {
        // Strictly ignore unavailable external truck gauges
        if (!availability.isGaugeAvailable(type)) return
        viewModelScope.launch {
            val current = uiState.value.configuration.effectiveGaugeConfig(type)
            dashboardPreferencesRepository.updateGaugeConfiguration(transform(current).sanitized())
        }
    }

    fun onToggleGaugeVisibility(type: GaugeType, visible: Boolean) {
        updateGaugeConfig(type) { it.copy(visible = visible) }
    }

    fun onChangeGaugeSize(type: GaugeType, size: GaugeSize) {
        updateGaugeConfig(type) { it.copy(size = size) }
    }

    fun onChangeGaugePresentation(type: GaugeType, style: GaugeStyle) {
        updateGaugeConfig(type) { it.copy(presentation = style) }
    }

    fun onChangeGaugeRegion(type: GaugeType, region: GaugeRegion) {
        updateGaugeConfig(type) { it.copy(region = region) }
    }

    fun onToggleGaugeLabel(type: GaugeType, show: Boolean) {
        updateGaugeConfig(type) { it.copy(showLabel = show) }
    }

    fun onToggleGaugeUnit(type: GaugeType, show: Boolean) {
        updateGaugeConfig(type) { it.copy(showUnit = show) }
    }

    fun onToggleGaugeThreshold(type: GaugeType, show: Boolean) {
        updateGaugeConfig(type) { it.copy(showWarningThreshold = show) }
    }

    fun onRestoreDefaults() {
        viewModelScope.launch {
            dashboardPreferencesRepository.resetToDefaults()
            _feedbackMessage.value = "Dashboard preferences restored to defaults"
        }
    }

    fun clearFeedback() {
        _feedbackMessage.value = null
    }

    companion object {
        internal fun buildPreviewSnapshot(scenario: PreviewTelemetryScenario): Pair<DashboardTelemetrySnapshot, Long> {
            val baseTime = 100_000L
            return when (scenario) {
                PreviewTelemetryScenario.LIVE_CRUISING -> DashboardTelemetrySnapshot(
                    sourceStatus = TelemetrySourceStatus.LIVE,
                    timestampMs = baseTime,
                    isLocalSimulation = true,
                    engineRunning = true,
                    speedKmh = 86.5f,
                    rpm = 2150f,
                    maxRpm = 8000f,
                    gearLabel = "5",
                    parkingBrake = false,
                    leftIndicator = false,
                    rightIndicator = false,
                    hazardLights = false,
                    lightsOn = true,
                    wipers = false,
                    cruiseControl = true
                ) to baseTime

                PreviewTelemetryScenario.WARNING_PEAK -> DashboardTelemetrySnapshot(
                    sourceStatus = TelemetrySourceStatus.LIVE,
                    timestampMs = baseTime,
                    isLocalSimulation = true,
                    engineRunning = true,
                    speedKmh = 142.0f,
                    rpm = 7100f,
                    maxRpm = 8000f,
                    gearLabel = "4",
                    parkingBrake = true,
                    leftIndicator = true,
                    rightIndicator = false,
                    hazardLights = false,
                    lightsOn = true,
                    wipers = true,
                    cruiseControl = false
                ) to baseTime

                PreviewTelemetryScenario.ENGINE_OFF -> DashboardTelemetrySnapshot(
                    sourceStatus = TelemetrySourceStatus.LIVE,
                    timestampMs = baseTime,
                    isLocalSimulation = true,
                    engineRunning = false,
                    speedKmh = 0f,
                    rpm = 0f,
                    maxRpm = 8000f,
                    gearLabel = "N",
                    parkingBrake = true
                ) to baseTime

                PreviewTelemetryScenario.DISCONNECTED -> DashboardTelemetrySnapshot(
                    sourceStatus = TelemetrySourceStatus.DISCONNECTED,
                    timestampMs = baseTime,
                    isLocalSimulation = false,
                    engineRunning = null,
                    speedKmh = null,
                    rpm = null,
                    maxRpm = null,
                    gearLabel = null
                ) to baseTime

                PreviewTelemetryScenario.STALE -> DashboardTelemetrySnapshot(
                    sourceStatus = TelemetrySourceStatus.LIVE,
                    timestampMs = 1_000L,
                    isLocalSimulation = false,
                    engineRunning = true,
                    speedKmh = 75f,
                    rpm = 1800f,
                    maxRpm = 8000f,
                    gearLabel = "4"
                ) to 10_000L

                PreviewTelemetryScenario.PAUSED -> DashboardTelemetrySnapshot(
                    sourceStatus = TelemetrySourceStatus.PAUSED,
                    timestampMs = baseTime,
                    isLocalSimulation = false,
                    engineRunning = true,
                    speedKmh = 64f,
                    rpm = 1650f,
                    maxRpm = 8000f,
                    gearLabel = "3"
                ) to baseTime
            }
        }
    }
}
