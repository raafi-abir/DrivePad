package com.drivepad.app.domain.model

import androidx.compose.runtime.Immutable
import com.drivepad.app.feature.racing.AutoGear
import com.drivepad.app.feature.racing.RacingUiState
import java.util.Locale
import kotlin.math.roundToInt

enum class TelemetrySourceStatus(val label: String) {
    LIVE("LIVE"),
    DISCONNECTED("NO LINK"),
    STALE("STALE"),
    PAUSED("PAUSED"),
    UNAVAILABLE("N/A")
}

enum class WarningIndicatorType(
    val label: String,
    val shortCode: String,
    val isWarningCategory: Boolean,
    val requiresExternalTruckTelemetry: Boolean
) {
    ENGINE_WARNING("Engine Warning", "ENG", isWarningCategory = true, requiresExternalTruckTelemetry = true),
    HIGH_ENGINE_TEMP("High Coolant Temp", "TEMP", isWarningCategory = true, requiresExternalTruckTelemetry = true),
    LOW_OIL_PRESSURE("Low Oil Pressure", "OIL", isWarningCategory = true, requiresExternalTruckTelemetry = true),
    LOW_AIR_PRESSURE("Low Air Pressure", "AIR", isWarningCategory = true, requiresExternalTruckTelemetry = true),
    LOW_FUEL("Low Fuel", "FUEL", isWarningCategory = true, requiresExternalTruckTelemetry = true),
    PARKING_BRAKE("Parking Brake", "PARK", isWarningCategory = true, requiresExternalTruckTelemetry = false),
    RPM_REDLINE("RPM Redline", "REDLINE", isWarningCategory = true, requiresExternalTruckTelemetry = false),
    OVERSPEED("Overspeed", "OVERSPD", isWarningCategory = true, requiresExternalTruckTelemetry = false),
    LEFT_INDICATOR("Left Signal", "LEFT", isWarningCategory = false, requiresExternalTruckTelemetry = false),
    RIGHT_INDICATOR("Right Signal", "RIGHT", isWarningCategory = false, requiresExternalTruckTelemetry = false),
    HAZARD_LIGHTS("Hazard Lights", "HAZARD", isWarningCategory = false, requiresExternalTruckTelemetry = false),
    HIGH_BEAM("Headlights", "LIGHTS", isWarningCategory = false, requiresExternalTruckTelemetry = false),
    WIPERS("Wipers", "WIPER", isWarningCategory = false, requiresExternalTruckTelemetry = false),
    CRUISE_CONTROL("Cruise Control", "CRUISE", isWarningCategory = false, requiresExternalTruckTelemetry = false)
}

enum class IndicatorActivationState {
    ACTIVE,
    INACTIVE,
    UNAVAILABLE
}

enum class IndicatorSeverity {
    WARNING_CRITICAL,
    WARNING_CAUTION,
    INFO,
    UNAVAILABLE
}

@Immutable
data class TelemetryAvailability(
    val sourceName: String,
    val supportsExternalTruckTelemetry: Boolean,
    val availableGauges: Set<GaugeType>,
    val availableIndicators: Set<WarningIndicatorType>
) {
    fun isGaugeAvailable(type: GaugeType): Boolean = type in availableGauges
    fun isIndicatorAvailable(type: WarningIndicatorType): Boolean = type in availableIndicators

    companion object {
        /**
         * Reflects the genuine fields available in the current Phase 8/10 DrivePad repository:
         * Speed, RPM, Gear, and local vehicle control switches exist via cockpit simulation + controller state,
         * while ETS2 scs-sdk-plugin fields (fuel, water temp, oil/air pressure, battery, AdBlue, speed limit,
         * and truck fault lights) are unavailable until a live telemetry plugin channel is added.
         */
        val CurrentDrivePadSource = TelemetryAvailability(
            sourceName = "DrivePad Cockpit State & Simulation",
            supportsExternalTruckTelemetry = false,
            availableGauges = setOf(
                GaugeType.SPEEDOMETER,
                GaugeType.RPM_METER,
                GaugeType.GEAR_INDICATOR
            ),
            availableIndicators = setOf(
                WarningIndicatorType.PARKING_BRAKE,
                WarningIndicatorType.RPM_REDLINE,
                WarningIndicatorType.OVERSPEED,
                WarningIndicatorType.LEFT_INDICATOR,
                WarningIndicatorType.RIGHT_INDICATOR,
                WarningIndicatorType.HAZARD_LIGHTS,
                WarningIndicatorType.HIGH_BEAM,
                WarningIndicatorType.WIPERS,
                WarningIndicatorType.CRUISE_CONTROL
            )
        )

        val FullTruckTelemetrySource = TelemetryAvailability(
            sourceName = "Live ETS2 Telemetry",
            supportsExternalTruckTelemetry = true,
            availableGauges = GaugeType.entries.toSet(),
            availableIndicators = WarningIndicatorType.entries.toSet()
        )
    }
}

@Immutable
data class DashboardTelemetrySnapshot(
    val sourceStatus: TelemetrySourceStatus = TelemetrySourceStatus.LIVE,
    val timestampMs: Long = 0L,
    val isLocalSimulation: Boolean = true,
    val engineRunning: Boolean? = false,
    val speedKmh: Float? = 0f,
    val rpm: Float? = 0f,
    val maxRpm: Float? = 8000f,
    val gearLabel: String? = "D",
    // Nullable ETS2 truck telemetry fields — null strictly represents "not reported / unavailable"
    val fuelRatio: Float? = null,
    val waterTempCelsius: Float? = null,
    val oilPressureBar: Float? = null,
    val airPressureBar: Float? = null,
    val batteryVolts: Float? = null,
    val adBlueRatio: Float? = null,
    val speedLimitKmh: Float? = null,
    // Available switch/indicator states
    val parkingBrake: Boolean? = false,
    val leftIndicator: Boolean? = false,
    val rightIndicator: Boolean? = false,
    val hazardLights: Boolean? = false,
    val lightsOn: Boolean? = false,
    val wipers: Boolean? = false,
    val cruiseControl: Boolean? = false,
    // Nullable fault indicators
    val engineWarning: Boolean? = null,
    val highEngineTempWarning: Boolean? = null,
    val lowOilPressureWarning: Boolean? = null,
    val lowAirPressureWarning: Boolean? = null,
    val lowFuelWarning: Boolean? = null
) {
    companion object {
        fun fromRacingUiState(
            racingState: RacingUiState,
            timestampMs: Long,
            sourceStatus: TelemetrySourceStatus = TelemetrySourceStatus.LIVE
        ): DashboardTelemetrySnapshot {
            val gearLabel = when (racingState.transmissionMode) {
                TransmissionMode.AUTO -> {
                    if (racingState.selectedAutoGear == AutoGear.D && racingState.autoActiveGearNumber != null) {
                        "${racingState.autoActiveGearNumber}"
                    } else {
                        racingState.selectedAutoGear.label
                    }
                }
                TransmissionMode.MANUAL -> when {
                    racingState.currentManualGear <= -1 -> "R"
                    racingState.currentManualGear == 0 -> "N"
                    else -> "${racingState.currentManualGear}"
                }
            }
            return DashboardTelemetrySnapshot(
                sourceStatus = sourceStatus,
                timestampMs = timestampMs,
                isLocalSimulation = true,
                engineRunning = racingState.vehicleFunctions.engineRunning,
                speedKmh = racingState.speedKmh,
                rpm = racingState.rpm,
                maxRpm = 8000f,
                gearLabel = gearLabel,
                parkingBrake = racingState.pedalsState.handbrakeEngaged,
                leftIndicator = racingState.vehicleFunctions.leftIndicatorActive,
                rightIndicator = racingState.vehicleFunctions.rightIndicatorActive,
                hazardLights = racingState.vehicleFunctions.hazardActive,
                lightsOn = racingState.vehicleFunctions.lightsOn,
                wipers = racingState.vehicleFunctions.wipersActive,
                cruiseControl = racingState.vehicleFunctions.cruiseActive
            )
        }
    }
}

@Immutable
sealed interface GaugeValueState {
    @Immutable
    data class Live(
        val numericValue: Float,
        val maxValue: Float?,
        val formattedValue: String,
        val unitLabel: String,
        val isWarning: Boolean,
        val thresholdNote: String? = null
    ) : GaugeValueState

    @Immutable
    data class EngineOff(val statusText: String = "OFF") : GaugeValueState

    @Immutable
    data class Stale(val ageMs: Long) : GaugeValueState

    data object Disconnected : GaugeValueState

    data object Paused : GaugeValueState

    data object Unavailable : GaugeValueState
}

@Immutable
data class WarningIndicatorItem(
    val type: WarningIndicatorType,
    val activationState: IndicatorActivationState,
    val severity: IndicatorSeverity
)

@Immutable
data class DashboardUiState(
    val configuration: DashboardConfiguration = DashboardConfiguration(),
    val availability: TelemetryAvailability = TelemetryAvailability.CurrentDrivePadSource,
    val effectiveStatus: TelemetrySourceStatus = TelemetrySourceStatus.LIVE,
    val statusBannerText: String? = null,
    val speedState: GaugeValueState = GaugeValueState.Unavailable,
    val rpmState: GaugeValueState = GaugeValueState.EngineOff(),
    val gearLabel: String = "--",
    val speedLimitState: GaugeValueState = GaugeValueState.Unavailable,
    val auxiliaryGauges: Map<GaugeType, GaugeValueState> = emptyMap(),
    val indicators: List<WarningIndicatorItem> = emptyList(),
    val engineRunning: Boolean = false
)

object DashboardStateFactory {
    const val DEFAULT_STALE_TIMEOUT_MS: Long = 2_000L

    fun buildDashboardUiState(
        snapshot: DashboardTelemetrySnapshot,
        configuration: DashboardConfiguration,
        availability: TelemetryAvailability = TelemetryAvailability.CurrentDrivePadSource,
        nowMs: Long = snapshot.timestampMs,
        staleTimeoutMs: Long = DEFAULT_STALE_TIMEOUT_MS
    ): DashboardUiState {
        val safeConfig = configuration.sanitized()
        val ageMs = if (snapshot.timestampMs > 0L && nowMs > snapshot.timestampMs) {
            nowMs - snapshot.timestampMs
        } else {
            0L
        }

        val effectiveStatus = when {
            snapshot.sourceStatus == TelemetrySourceStatus.DISCONNECTED -> TelemetrySourceStatus.DISCONNECTED
            snapshot.sourceStatus == TelemetrySourceStatus.PAUSED -> TelemetrySourceStatus.PAUSED
            snapshot.sourceStatus == TelemetrySourceStatus.UNAVAILABLE -> TelemetrySourceStatus.UNAVAILABLE
            snapshot.sourceStatus == TelemetrySourceStatus.STALE -> TelemetrySourceStatus.STALE
            !snapshot.isLocalSimulation && snapshot.timestampMs > 0L && ageMs > staleTimeoutMs ->
                TelemetrySourceStatus.STALE
            snapshot.sourceStatus == TelemetrySourceStatus.LIVE && snapshot.timestampMs > 0L && ageMs > staleTimeoutMs ->
                TelemetrySourceStatus.STALE
            else -> TelemetrySourceStatus.LIVE
        }

        val statusBannerText = when (effectiveStatus) {
            TelemetrySourceStatus.DISCONNECTED -> "Telemetry disconnected — values paused"
            TelemetrySourceStatus.STALE -> "Telemetry stale (${ageMs} ms old)"
            TelemetrySourceStatus.PAUSED -> "Simulator paused"
            TelemetrySourceStatus.UNAVAILABLE -> "Telemetry unavailable"
            TelemetrySourceStatus.LIVE -> null
        }

        val engineRunning = snapshot.engineRunning == true

        // 1. Speedometer state (display unit conversion NEVER alters snapshot.speedKmh)
        val speedState = evaluateSpeedGauge(
            snapshot = snapshot,
            effectiveStatus = effectiveStatus,
            ageMs = ageMs,
            config = safeConfig,
            availability = availability
        )

        // 2. RPM state
        val rpmState = evaluateRpmGauge(
            snapshot = snapshot,
            effectiveStatus = effectiveStatus,
            ageMs = ageMs,
            config = safeConfig,
            availability = availability
        )

        // 3. Gear label
        val gearLabel = when (effectiveStatus) {
            TelemetrySourceStatus.LIVE -> snapshot.gearLabel?.takeIf { it.isNotBlank() } ?: "--"
            TelemetrySourceStatus.PAUSED -> snapshot.gearLabel?.takeIf { it.isNotBlank() } ?: "--"
            TelemetrySourceStatus.DISCONNECTED,
            TelemetrySourceStatus.STALE,
            TelemetrySourceStatus.UNAVAILABLE -> "--"
        }

        // 4. Optional Speed Limit
        val speedLimitState = evaluateOptionalFloatGauge(
            rawValue = snapshot.speedLimitKmh?.let { safeConfig.speedUnit.convertFromKmh(it) },
            maxValue = null,
            unit = safeConfig.speedUnit.symbol,
            decimals = 0,
            isWarning = false,
            gaugeType = GaugeType.SPEED_LIMIT,
            effectiveStatus = effectiveStatus,
            ageMs = ageMs,
            availability = availability
        )

        // 5. Optional auxiliary truck telemetry gauges
        val auxTypes = listOf(
            GaugeType.FUEL,
            GaugeType.WATER_TEMPERATURE,
            GaugeType.OIL_PRESSURE,
            GaugeType.AIR_PRESSURE,
            GaugeType.BATTERY_VOLTAGE,
            GaugeType.ADBLUE
        )
        val auxiliaryMap = auxTypes.associateWith { gaugeType ->
            when (gaugeType) {
                GaugeType.FUEL -> evaluateOptionalFloatGauge(
                    rawValue = snapshot.fuelRatio?.times(100f),
                    maxValue = 100f,
                    unit = "%",
                    decimals = 0,
                    isWarning = snapshot.lowFuelWarning == true,
                    gaugeType = gaugeType,
                    effectiveStatus = effectiveStatus,
                    ageMs = ageMs,
                    availability = availability
                )
                GaugeType.WATER_TEMPERATURE -> evaluateOptionalFloatGauge(
                    rawValue = snapshot.waterTempCelsius,
                    maxValue = 130f,
                    unit = "°C",
                    decimals = 0,
                    isWarning = snapshot.highEngineTempWarning == true,
                    gaugeType = gaugeType,
                    effectiveStatus = effectiveStatus,
                    ageMs = ageMs,
                    availability = availability
                )
                GaugeType.OIL_PRESSURE -> evaluateOptionalFloatGauge(
                    rawValue = snapshot.oilPressureBar,
                    maxValue = 10f,
                    unit = "bar",
                    decimals = 1,
                    isWarning = snapshot.lowOilPressureWarning == true,
                    gaugeType = gaugeType,
                    effectiveStatus = effectiveStatus,
                    ageMs = ageMs,
                    availability = availability
                )
                GaugeType.AIR_PRESSURE -> evaluateOptionalFloatGauge(
                    rawValue = snapshot.airPressureBar,
                    maxValue = 12f,
                    unit = "bar",
                    decimals = 1,
                    isWarning = snapshot.lowAirPressureWarning == true,
                    gaugeType = gaugeType,
                    effectiveStatus = effectiveStatus,
                    ageMs = ageMs,
                    availability = availability
                )
                GaugeType.BATTERY_VOLTAGE -> evaluateOptionalFloatGauge(
                    rawValue = snapshot.batteryVolts,
                    maxValue = 30f,
                    unit = "V",
                    decimals = 1,
                    isWarning = false,
                    gaugeType = gaugeType,
                    effectiveStatus = effectiveStatus,
                    ageMs = ageMs,
                    availability = availability
                )
                GaugeType.ADBLUE -> evaluateOptionalFloatGauge(
                    rawValue = snapshot.adBlueRatio?.times(100f),
                    maxValue = 100f,
                    unit = "%",
                    decimals = 0,
                    isWarning = false,
                    gaugeType = gaugeType,
                    effectiveStatus = effectiveStatus,
                    ageMs = ageMs,
                    availability = availability
                )
                else -> GaugeValueState.Unavailable
            }
        }

        // 6. Warning & Indicator evaluation
        val indicators = buildIndicators(
            snapshot = snapshot,
            effectiveStatus = effectiveStatus,
            config = safeConfig,
            availability = availability
        )

        return DashboardUiState(
            configuration = safeConfig,
            availability = availability,
            effectiveStatus = effectiveStatus,
            statusBannerText = statusBannerText,
            speedState = speedState,
            rpmState = rpmState,
            gearLabel = gearLabel,
            speedLimitState = speedLimitState,
            auxiliaryGauges = auxiliaryMap,
            indicators = indicators,
            engineRunning = engineRunning
        )
    }

    private fun evaluateSpeedGauge(
        snapshot: DashboardTelemetrySnapshot,
        effectiveStatus: TelemetrySourceStatus,
        ageMs: Long,
        config: DashboardConfiguration,
        availability: TelemetryAvailability
    ): GaugeValueState {
        if (!availability.isGaugeAvailable(GaugeType.SPEEDOMETER)) return GaugeValueState.Unavailable
        return when (effectiveStatus) {
            TelemetrySourceStatus.DISCONNECTED -> GaugeValueState.Disconnected
            TelemetrySourceStatus.STALE -> GaugeValueState.Stale(ageMs)
            TelemetrySourceStatus.PAUSED -> GaugeValueState.Paused
            TelemetrySourceStatus.UNAVAILABLE -> GaugeValueState.Unavailable
            TelemetrySourceStatus.LIVE -> {
                val rawKmh = snapshot.speedKmh ?: return GaugeValueState.Unavailable
                val clampedKmh = rawKmh.coerceAtLeast(0f)
                val displayValue = config.speedUnit.convertFromKmh(clampedKmh)
                val displayMax = config.speedUnit.convertFromKmh(config.speedometerMaxScaleKmh.toFloat())
                val isOverspeed = config.warningsEnabled &&
                    config.overspeedWarningEnabled &&
                    clampedKmh >= config.overspeedThresholdKmh.toFloat()
                val thresholdDisplay = config.speedUnit.convertRoundedIntFromKmh(config.overspeedThresholdKmh)
                GaugeValueState.Live(
                    numericValue = displayValue,
                    maxValue = displayMax,
                    formattedValue = String.format(Locale.US, "%.2f", displayValue),
                    unitLabel = config.speedUnit.symbol,
                    isWarning = isOverspeed,
                    thresholdNote = if (config.overspeedWarningEnabled) "Limit $thresholdDisplay ${config.speedUnit.symbol}" else null
                )
            }
        }
    }

    private fun evaluateRpmGauge(
        snapshot: DashboardTelemetrySnapshot,
        effectiveStatus: TelemetrySourceStatus,
        ageMs: Long,
        config: DashboardConfiguration,
        availability: TelemetryAvailability
    ): GaugeValueState {
        if (!availability.isGaugeAvailable(GaugeType.RPM_METER)) return GaugeValueState.Unavailable
        return when (effectiveStatus) {
            TelemetrySourceStatus.DISCONNECTED -> GaugeValueState.Disconnected
            TelemetrySourceStatus.STALE -> GaugeValueState.Stale(ageMs)
            TelemetrySourceStatus.PAUSED -> GaugeValueState.Paused
            TelemetrySourceStatus.UNAVAILABLE -> GaugeValueState.Unavailable
            TelemetrySourceStatus.LIVE -> {
                val engineRunning = snapshot.engineRunning
                if (engineRunning == null) return GaugeValueState.Unavailable
                if (!engineRunning) {
                    return GaugeValueState.EngineOff("OFF")
                }
                val rawRpm = snapshot.rpm ?: return GaugeValueState.Unavailable
                val reportedMax = (snapshot.maxRpm ?: 8000f).coerceIn(3000f, 12000f)
                val clampedRpm = rawRpm.coerceIn(0f, reportedMax)
                val isRedline = config.warningsEnabled && clampedRpm >= config.rpmRedlineThreshold.toFloat()
                GaugeValueState.Live(
                    numericValue = clampedRpm,
                    maxValue = reportedMax,
                    formattedValue = "${clampedRpm.roundToInt()}",
                    unitLabel = "RPM",
                    isWarning = isRedline,
                    thresholdNote = "Redline ${config.rpmRedlineThreshold}"
                )
            }
        }
    }

    private fun evaluateOptionalFloatGauge(
        rawValue: Float?,
        maxValue: Float?,
        unit: String,
        decimals: Int,
        isWarning: Boolean,
        gaugeType: GaugeType,
        effectiveStatus: TelemetrySourceStatus,
        ageMs: Long,
        availability: TelemetryAvailability
    ): GaugeValueState {
        if (!availability.isGaugeAvailable(gaugeType)) return GaugeValueState.Unavailable
        return when (effectiveStatus) {
            TelemetrySourceStatus.DISCONNECTED -> GaugeValueState.Disconnected
            TelemetrySourceStatus.STALE -> GaugeValueState.Stale(ageMs)
            TelemetrySourceStatus.PAUSED -> GaugeValueState.Paused
            TelemetrySourceStatus.UNAVAILABLE -> GaugeValueState.Unavailable
            TelemetrySourceStatus.LIVE -> {
                val value = rawValue ?: return GaugeValueState.Unavailable
                val formatted = if (decimals <= 0) {
                    "${value.roundToInt()}"
                } else {
                    String.format(Locale.US, "%.${decimals}f", value)
                }
                GaugeValueState.Live(
                    numericValue = value,
                    maxValue = maxValue,
                    formattedValue = formatted,
                    unitLabel = unit,
                    isWarning = isWarning
                )
            }
        }
    }

    private fun buildIndicators(
        snapshot: DashboardTelemetrySnapshot,
        effectiveStatus: TelemetrySourceStatus,
        config: DashboardConfiguration,
        availability: TelemetryAvailability
    ): List<WarningIndicatorItem> {
        val isDataUsable = effectiveStatus == TelemetrySourceStatus.LIVE

        fun resolveBoolIndicator(
            type: WarningIndicatorType,
            flag: Boolean?,
            warningSeverity: IndicatorSeverity
        ): WarningIndicatorItem {
            if (!isDataUsable || !availability.isIndicatorAvailable(type) || flag == null) {
                return WarningIndicatorItem(
                    type = type,
                    activationState = IndicatorActivationState.UNAVAILABLE,
                    severity = IndicatorSeverity.UNAVAILABLE
                )
            }
            return WarningIndicatorItem(
                type = type,
                activationState = if (flag) IndicatorActivationState.ACTIVE else IndicatorActivationState.INACTIVE,
                severity = warningSeverity
            )
        }

        val speedWarningActive = if (
            isDataUsable &&
            config.warningsEnabled &&
            config.overspeedWarningEnabled &&
            snapshot.speedKmh != null
        ) {
            snapshot.speedKmh >= config.overspeedThresholdKmh.toFloat()
        } else {
            null
        }

        val redlineWarningActive = if (
            isDataUsable &&
            config.warningsEnabled &&
            snapshot.engineRunning == true &&
            snapshot.rpm != null
        ) {
            snapshot.rpm >= config.rpmRedlineThreshold.toFloat()
        } else {
            null
        }

        val rawItems = listOf(
            resolveBoolIndicator(WarningIndicatorType.PARKING_BRAKE, snapshot.parkingBrake, IndicatorSeverity.WARNING_CRITICAL),
            resolveBoolIndicator(WarningIndicatorType.RPM_REDLINE, redlineWarningActive, IndicatorSeverity.WARNING_CRITICAL),
            resolveBoolIndicator(WarningIndicatorType.OVERSPEED, speedWarningActive, IndicatorSeverity.WARNING_CAUTION),
            // External ETS2 fault warnings — strictly UNAVAILABLE unless genuine telemetry exists
            resolveBoolIndicator(WarningIndicatorType.ENGINE_WARNING, snapshot.engineWarning, IndicatorSeverity.WARNING_CRITICAL),
            resolveBoolIndicator(WarningIndicatorType.HIGH_ENGINE_TEMP, snapshot.highEngineTempWarning, IndicatorSeverity.WARNING_CRITICAL),
            resolveBoolIndicator(WarningIndicatorType.LOW_OIL_PRESSURE, snapshot.lowOilPressureWarning, IndicatorSeverity.WARNING_CRITICAL),
            resolveBoolIndicator(WarningIndicatorType.LOW_AIR_PRESSURE, snapshot.lowAirPressureWarning, IndicatorSeverity.WARNING_CRITICAL),
            resolveBoolIndicator(WarningIndicatorType.LOW_FUEL, snapshot.lowFuelWarning, IndicatorSeverity.WARNING_CAUTION)
        )

        return when (config.layout) {
            DashboardLayout.MINIMAL -> rawItems.filter {
                it.type == WarningIndicatorType.PARKING_BRAKE ||
                    it.type == WarningIndicatorType.RPM_REDLINE ||
                    it.type == WarningIndicatorType.OVERSPEED ||
                    it.type == WarningIndicatorType.HAZARD_LIGHTS ||
                    (it.activationState == IndicatorActivationState.ACTIVE && it.type.isWarningCategory)
            }
            DashboardLayout.RACING -> rawItems.filter {
                !it.type.requiresExternalTruckTelemetry || it.activationState != IndicatorActivationState.UNAVAILABLE
            }
            DashboardLayout.TRUCK, DashboardLayout.COMPACT -> rawItems.filter {
                config.showUnavailableTelemetryPlaceholders || it.activationState != IndicatorActivationState.UNAVAILABLE
            }
        }
    }
}
