package com.drivepad.app.domain.model

import androidx.compose.runtime.Immutable

enum class DashboardLayout(
    val displayName: String,
    val subtitle: String
) {
    RACING(
        displayName = "Racing Dashboard",
        subtitle = "Large RPM & speed focus, prominent gear badge, compact alerts"
    ),
    TRUCK(
        displayName = "Truck Dashboard",
        subtitle = "Balanced gauges, cruise control & full vehicle warning strip"
    ),
    MINIMAL(
        displayName = "Minimal Dashboard",
        subtitle = "Distraction-free speed, RPM, gear, and critical alerts only"
    ),
    COMPACT(
        displayName = "Compact Dashboard",
        subtitle = "High-density dual gauges with simultaneous telemetry & status badges"
    )
}

enum class DashboardThemeMode(val displayName: String) {
    DARK("Dark"),
    LIGHT("Light"),
    SYSTEM("Follow System")
}

enum class DashboardAccentColor(
    val displayName: String,
    val primaryHex: Long,
    val glowHex: Long
) {
    CRIMSON("Racing Crimson", 0xFFE51414, 0xFFFF2424),
    NEON_GREEN("DrivePad Green", 0xFF00E676, 0xFF69F0AE),
    ELECTRIC_CYAN("Telemetry Cyan", 0xFF00B8D4, 0xFF18FFFF),
    AMBER_GOLD("Amber Gold", 0xFFFFAB00, 0xFFFFD740),
    COBALT_BLUE("Cobalt Blue", 0xFF2563EB, 0xFF60A5FA)
}

enum class GaugeBackgroundStyle(val displayName: String) {
    CARBON_DARK("Carbon Gradient"),
    TRANSLUCENT_GLASS("Frosted Glass"),
    SOLID_CONTRAST("High Contrast")
}

enum class DashboardDensity(val displayName: String) {
    COMPACT("Compact"),
    SPACIOUS("Spacious")
}

enum class SpeedUnit(val symbol: String, val displayName: String) {
    KMH("km/h", "Kilometers (km/h)"),
    MPH("mph", "Miles (mph)");

    fun convertFromKmh(kmh: Float): Float = when (this) {
        KMH -> kmh
        MPH -> kmh * 0.621371f
    }

    fun convertRoundedIntFromKmh(kmh: Int): Int = when (this) {
        KMH -> kmh
        MPH -> (kmh * 0.621371f).toInt()
    }
}

enum class GaugeStyle(val displayName: String) {
    CIRCULAR("Circular"),
    DIGITAL("Digital")
}

enum class GaugeSize(val displayName: String, val weightMultiplier: Float) {
    SMALL("Small", 0.85f),
    MEDIUM("Medium", 1.0f),
    LARGE("Large", 1.18f)
}

enum class GaugeRegion(val displayName: String) {
    PRIMARY_TOP("Top Header"),
    PRIMARY_MIDDLE("Main Stage"),
    AUXILIARY_STRIP("Lower Strip")
}

enum class GaugeType(
    val displayName: String,
    val supportsPresentationToggle: Boolean,
    val requiresExternalTruckTelemetry: Boolean,
    val requiredTelemetryFieldDesc: String
) {
    SPEEDOMETER(
        displayName = "Speedometer",
        supportsPresentationToggle = true,
        requiresExternalTruckTelemetry = false,
        requiredTelemetryFieldDesc = "Vehicle speed (local cockpit simulation or ETS2 truck speed)"
    ),
    RPM_METER(
        displayName = "RPM Meter",
        supportsPresentationToggle = true,
        requiresExternalTruckTelemetry = false,
        requiredTelemetryFieldDesc = "Engine RPM & max RPM (local simulation or ETS2 engine RPM)"
    ),
    GEAR_INDICATOR(
        displayName = "Gear Indicator",
        supportsPresentationToggle = false,
        requiresExternalTruckTelemetry = false,
        requiredTelemetryFieldDesc = "Selected gearbox state (Auto P/R/N/D/1..6 or Manual R/N/1..6)"
    ),
    FUEL(
        displayName = "Fuel Level",
        supportsPresentationToggle = true,
        requiresExternalTruckTelemetry = true,
        requiredTelemetryFieldDesc = "Requires ETS2 telemetry fuel & capacity fields"
    ),
    WATER_TEMPERATURE(
        displayName = "Water Temperature",
        supportsPresentationToggle = true,
        requiresExternalTruckTelemetry = true,
        requiredTelemetryFieldDesc = "Requires ETS2 telemetry water temperature (°C)"
    ),
    OIL_PRESSURE(
        displayName = "Oil Pressure",
        supportsPresentationToggle = true,
        requiresExternalTruckTelemetry = true,
        requiredTelemetryFieldDesc = "Requires ETS2 telemetry oil pressure (bar/psi)"
    ),
    AIR_PRESSURE(
        displayName = "Air Pressure",
        supportsPresentationToggle = true,
        requiresExternalTruckTelemetry = true,
        requiredTelemetryFieldDesc = "Requires ETS2 telemetry air brake pressure (bar/psi)"
    ),
    BATTERY_VOLTAGE(
        displayName = "Battery Voltage",
        supportsPresentationToggle = true,
        requiresExternalTruckTelemetry = true,
        requiredTelemetryFieldDesc = "Requires ETS2 telemetry battery voltage (V)"
    ),
    ADBLUE(
        displayName = "AdBlue",
        supportsPresentationToggle = true,
        requiresExternalTruckTelemetry = true,
        requiredTelemetryFieldDesc = "Requires ETS2 telemetry AdBlue level & capacity"
    ),
    SPEED_LIMIT(
        displayName = "Speed Limit",
        supportsPresentationToggle = false,
        requiresExternalTruckTelemetry = true,
        requiredTelemetryFieldDesc = "Requires ETS2 navigation speed limit telemetry"
    )
}

@Immutable
data class GaugeConfiguration(
    val type: GaugeType,
    val visible: Boolean = true,
    val size: GaugeSize = GaugeSize.MEDIUM,
    val region: GaugeRegion = defaultRegionFor(type),
    val presentation: GaugeStyle = defaultStyleFor(type),
    val showLabel: Boolean = true,
    val showUnit: Boolean = true,
    val showWarningThreshold: Boolean = true
) {
    fun sanitized(): GaugeConfiguration {
        val safePresentation = if (type.supportsPresentationToggle) presentation else GaugeStyle.DIGITAL
        val safeRegion = when (type) {
            GaugeType.SPEEDOMETER, GaugeType.GEAR_INDICATOR ->
                if (region == GaugeRegion.AUXILIARY_STRIP) GaugeRegion.PRIMARY_TOP else region
            GaugeType.RPM_METER ->
                if (region == GaugeRegion.AUXILIARY_STRIP) GaugeRegion.PRIMARY_MIDDLE else region
            else -> GaugeRegion.AUXILIARY_STRIP
        }
        return copy(presentation = safePresentation, region = safeRegion)
    }

    companion object {
        fun defaultRegionFor(type: GaugeType): GaugeRegion = when (type) {
            GaugeType.SPEEDOMETER, GaugeType.GEAR_INDICATOR -> GaugeRegion.PRIMARY_TOP
            GaugeType.RPM_METER -> GaugeRegion.PRIMARY_MIDDLE
            else -> GaugeRegion.AUXILIARY_STRIP
        }

        fun defaultStyleFor(type: GaugeType): GaugeStyle = when (type) {
            GaugeType.RPM_METER -> GaugeStyle.CIRCULAR
            else -> GaugeStyle.DIGITAL
        }

        fun defaultFor(type: GaugeType): GaugeConfiguration = GaugeConfiguration(
            type = type,
            visible = !type.requiresExternalTruckTelemetry,
            size = if (type == GaugeType.RPM_METER) GaugeSize.LARGE else GaugeSize.MEDIUM,
            region = defaultRegionFor(type),
            presentation = defaultStyleFor(type),
            showLabel = true,
            showUnit = true,
            showWarningThreshold = true
        )
    }
}

@Immutable
data class DashboardConfiguration(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val layout: DashboardLayout = DashboardLayout.RACING,
    val themeMode: DashboardThemeMode = DashboardThemeMode.DARK,
    val accentColor: DashboardAccentColor = DashboardAccentColor.CRIMSON,
    val gaugeBackgroundStyle: GaugeBackgroundStyle = GaugeBackgroundStyle.CARBON_DARK,
    val density: DashboardDensity = DashboardDensity.SPACIOUS,
    val animationsEnabled: Boolean = true,
    val speedUnit: SpeedUnit = SpeedUnit.KMH,
    val speedometerMaxScaleKmh: Int = 240,
    val showSpeedLimit: Boolean = false,
    val overspeedWarningEnabled: Boolean = true,
    val overspeedThresholdKmh: Int = 120,
    val rpmRedlineThreshold: Int = 6000,
    val warningsEnabled: Boolean = true,
    val showUnavailableTelemetryPlaceholders: Boolean = true,
    val gaugeConfigs: Map<GaugeType, GaugeConfiguration> = defaultGaugeConfigs()
) {
    fun effectiveGaugeConfig(type: GaugeType): GaugeConfiguration {
        return (gaugeConfigs[type] ?: GaugeConfiguration.defaultFor(type)).sanitized()
    }

    fun sanitized(): DashboardConfiguration {
        val safeMaxScale = speedometerMaxScaleKmh.coerceIn(120, 360)
        val safeOverspeed = overspeedThresholdKmh.coerceIn(30, safeMaxScale)
        val safeRedline = rpmRedlineThreshold.coerceIn(3000, 7800)
        val normalizedMap = GaugeType.entries.associateWith { type ->
            (gaugeConfigs[type] ?: GaugeConfiguration.defaultFor(type)).sanitized()
        }
        return copy(
            schemaVersion = CURRENT_SCHEMA_VERSION,
            speedometerMaxScaleKmh = safeMaxScale,
            overspeedThresholdKmh = safeOverspeed,
            rpmRedlineThreshold = safeRedline,
            gaugeConfigs = normalizedMap
        )
    }

    companion object {
        const val CURRENT_SCHEMA_VERSION: Int = 1
        val VALID_SPEED_SCALES_KMH = listOf(160, 200, 240, 320)

        fun defaultGaugeConfigs(): Map<GaugeType, GaugeConfiguration> =
            GaugeType.entries.associateWith { GaugeConfiguration.defaultFor(it) }

        fun presetForLayout(layout: DashboardLayout, base: DashboardConfiguration): DashboardConfiguration {
            val updatedMap = base.gaugeConfigs.toMutableMap()
            when (layout) {
                DashboardLayout.RACING -> {
                    updatedMap[GaugeType.SPEEDOMETER] = base.effectiveGaugeConfig(GaugeType.SPEEDOMETER).copy(
                        visible = true,
                        size = GaugeSize.LARGE,
                        region = GaugeRegion.PRIMARY_TOP
                    )
                    updatedMap[GaugeType.RPM_METER] = base.effectiveGaugeConfig(GaugeType.RPM_METER).copy(
                        visible = true,
                        size = GaugeSize.LARGE,
                        presentation = GaugeStyle.CIRCULAR,
                        region = GaugeRegion.PRIMARY_MIDDLE
                    )
                    updatedMap[GaugeType.GEAR_INDICATOR] = base.effectiveGaugeConfig(GaugeType.GEAR_INDICATOR).copy(
                        visible = true,
                        size = GaugeSize.LARGE,
                        region = GaugeRegion.PRIMARY_TOP
                    )
                }
                DashboardLayout.TRUCK -> {
                    updatedMap[GaugeType.SPEEDOMETER] = base.effectiveGaugeConfig(GaugeType.SPEEDOMETER).copy(
                        visible = true,
                        size = GaugeSize.MEDIUM,
                        region = GaugeRegion.PRIMARY_TOP
                    )
                    updatedMap[GaugeType.RPM_METER] = base.effectiveGaugeConfig(GaugeType.RPM_METER).copy(
                        visible = true,
                        size = GaugeSize.MEDIUM,
                        region = GaugeRegion.PRIMARY_MIDDLE
                    )
                    updatedMap[GaugeType.GEAR_INDICATOR] = base.effectiveGaugeConfig(GaugeType.GEAR_INDICATOR).copy(
                        visible = true,
                        size = GaugeSize.MEDIUM,
                        region = GaugeRegion.PRIMARY_TOP
                    )
                }
                DashboardLayout.MINIMAL -> {
                    updatedMap[GaugeType.SPEEDOMETER] = base.effectiveGaugeConfig(GaugeType.SPEEDOMETER).copy(
                        visible = true,
                        size = GaugeSize.LARGE,
                        presentation = GaugeStyle.DIGITAL,
                        region = GaugeRegion.PRIMARY_TOP
                    )
                    updatedMap[GaugeType.RPM_METER] = base.effectiveGaugeConfig(GaugeType.RPM_METER).copy(
                        visible = true,
                        size = GaugeSize.MEDIUM,
                        presentation = GaugeStyle.DIGITAL,
                        region = GaugeRegion.PRIMARY_MIDDLE
                    )
                    updatedMap[GaugeType.GEAR_INDICATOR] = base.effectiveGaugeConfig(GaugeType.GEAR_INDICATOR).copy(
                        visible = true,
                        size = GaugeSize.LARGE,
                        region = GaugeRegion.PRIMARY_TOP
                    )
                }
                DashboardLayout.COMPACT -> {
                    updatedMap[GaugeType.SPEEDOMETER] = base.effectiveGaugeConfig(GaugeType.SPEEDOMETER).copy(
                        visible = true,
                        size = GaugeSize.SMALL,
                        region = GaugeRegion.PRIMARY_TOP
                    )
                    updatedMap[GaugeType.RPM_METER] = base.effectiveGaugeConfig(GaugeType.RPM_METER).copy(
                        visible = true,
                        size = GaugeSize.SMALL,
                        region = GaugeRegion.PRIMARY_MIDDLE
                    )
                    updatedMap[GaugeType.GEAR_INDICATOR] = base.effectiveGaugeConfig(GaugeType.GEAR_INDICATOR).copy(
                        visible = true,
                        size = GaugeSize.SMALL,
                        region = GaugeRegion.PRIMARY_TOP
                    )
                }
            }
            val suggestedDensity = if (layout == DashboardLayout.COMPACT) DashboardDensity.COMPACT else base.density
            return base.copy(
                layout = layout,
                density = suggestedDensity,
                gaugeConfigs = updatedMap
            ).sanitized()
        }
    }
}
