package com.drivepad.app.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.drivepad.app.domain.model.DashboardAccentColor
import com.drivepad.app.domain.model.DashboardConfiguration
import com.drivepad.app.domain.model.DashboardDensity
import com.drivepad.app.domain.model.DashboardLayout
import com.drivepad.app.domain.model.DashboardThemeMode
import com.drivepad.app.domain.model.GaugeBackgroundStyle
import com.drivepad.app.domain.model.GaugeConfiguration
import com.drivepad.app.domain.model.GaugeRegion
import com.drivepad.app.domain.model.GaugeSize
import com.drivepad.app.domain.model.GaugeStyle
import com.drivepad.app.domain.model.GaugeType
import com.drivepad.app.domain.model.SpeedUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

interface DashboardPreferencesRepository {
    val dashboardConfigFlow: Flow<DashboardConfiguration>

    suspend fun selectLayout(layout: DashboardLayout, applyLayoutPreset: Boolean = true)
    suspend fun updateThemeMode(themeMode: DashboardThemeMode)
    suspend fun updateAccentColor(accentColor: DashboardAccentColor)
    suspend fun updateGaugeBackgroundStyle(style: GaugeBackgroundStyle)
    suspend fun updateDensity(density: DashboardDensity)
    suspend fun updateAnimationsEnabled(enabled: Boolean)
    suspend fun updateSpeedUnit(unit: SpeedUnit)
    suspend fun updateSpeedometerMaxScale(maxScaleKmh: Int)
    suspend fun updateShowSpeedLimit(show: Boolean)
    suspend fun updateOverspeedWarning(enabled: Boolean, thresholdKmh: Int)
    suspend fun updateRpmRedlineThreshold(rpm: Int)
    suspend fun updateWarningsEnabled(enabled: Boolean)
    suspend fun updateShowUnavailablePlaceholders(show: Boolean)
    suspend fun updateGaugeConfiguration(gaugeConfig: GaugeConfiguration)
    suspend fun resetToDefaults()
}

/**
 * Shares the existing DataStore instance (`drivepad_user_preferences`) to guarantee
 * a single unified persistence layer across controller and dashboard settings.
 */
class DashboardPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : DashboardPreferencesRepository {

    internal object Keys {
        val SCHEMA_VERSION = intPreferencesKey("dashboard_schema_version")
        val LAYOUT = stringPreferencesKey("dashboard_layout")
        val THEME_MODE = stringPreferencesKey("dashboard_theme_mode")
        val ACCENT_COLOR = stringPreferencesKey("dashboard_accent_color")
        val GAUGE_BG_STYLE = stringPreferencesKey("dashboard_gauge_bg_style")
        val DENSITY = stringPreferencesKey("dashboard_density")
        val ANIMATIONS_ENABLED = booleanPreferencesKey("dashboard_animations_enabled")
        val SPEED_UNIT = stringPreferencesKey("dashboard_speed_unit")
        val SPEED_MAX_SCALE_KMH = intPreferencesKey("dashboard_speed_max_scale_kmh")
        val SHOW_SPEED_LIMIT = booleanPreferencesKey("dashboard_show_speed_limit")
        val OVERSPEED_WARNING_ENABLED = booleanPreferencesKey("dashboard_overspeed_warning_enabled")
        val OVERSPEED_THRESHOLD_KMH = intPreferencesKey("dashboard_overspeed_threshold_kmh")
        val RPM_REDLINE_THRESHOLD = intPreferencesKey("dashboard_rpm_redline_threshold")
        val WARNINGS_ENABLED = booleanPreferencesKey("dashboard_warnings_enabled")
        val SHOW_UNAVAILABLE_PLACEHOLDERS = booleanPreferencesKey("dashboard_show_unavailable_placeholders")
        val GAUGE_CONFIGS_SERIALIZED = stringPreferencesKey("dashboard_gauge_configs_v1")
    }

    override val dashboardConfigFlow: Flow<DashboardConfiguration> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> decodeAndMigrate(prefs) }

    override suspend fun selectLayout(layout: DashboardLayout, applyLayoutPreset: Boolean) {
        dataStore.edit { prefs ->
            val current = decodeAndMigrate(prefs)
            val next = if (applyLayoutPreset) {
                DashboardConfiguration.presetForLayout(layout, current)
            } else {
                current.copy(layout = layout).sanitized()
            }
            writeAll(prefs, next)
        }
    }

    override suspend fun updateThemeMode(themeMode: DashboardThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.THEME_MODE] = themeMode.name
        }
    }

    override suspend fun updateAccentColor(accentColor: DashboardAccentColor) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.ACCENT_COLOR] = accentColor.name
        }
    }

    override suspend fun updateGaugeBackgroundStyle(style: GaugeBackgroundStyle) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.GAUGE_BG_STYLE] = style.name
        }
    }

    override suspend fun updateDensity(density: DashboardDensity) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.DENSITY] = density.name
        }
    }

    override suspend fun updateAnimationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.ANIMATIONS_ENABLED] = enabled
        }
    }

    override suspend fun updateSpeedUnit(unit: SpeedUnit) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.SPEED_UNIT] = unit.name
        }
    }

    override suspend fun updateSpeedometerMaxScale(maxScaleKmh: Int) {
        dataStore.edit { prefs ->
            val current = decodeAndMigrate(prefs)
            val safeScale = maxScaleKmh.coerceIn(120, 360)
            val safeOverspeed = current.overspeedThresholdKmh.coerceIn(30, safeScale)
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.SPEED_MAX_SCALE_KMH] = safeScale
            prefs[Keys.OVERSPEED_THRESHOLD_KMH] = safeOverspeed
        }
    }

    override suspend fun updateShowSpeedLimit(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.SHOW_SPEED_LIMIT] = show
        }
    }

    override suspend fun updateOverspeedWarning(enabled: Boolean, thresholdKmh: Int) {
        dataStore.edit { prefs ->
            val current = decodeAndMigrate(prefs)
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.OVERSPEED_WARNING_ENABLED] = enabled
            prefs[Keys.OVERSPEED_THRESHOLD_KMH] =
                thresholdKmh.coerceIn(30, current.speedometerMaxScaleKmh)
        }
    }

    override suspend fun updateRpmRedlineThreshold(rpm: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.RPM_REDLINE_THRESHOLD] = rpm.coerceIn(3000, 7800)
        }
    }

    override suspend fun updateWarningsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.WARNINGS_ENABLED] = enabled
        }
    }

    override suspend fun updateShowUnavailablePlaceholders(show: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.SHOW_UNAVAILABLE_PLACEHOLDERS] = show
        }
    }

    override suspend fun updateGaugeConfiguration(gaugeConfig: GaugeConfiguration) {
        dataStore.edit { prefs ->
            val current = decodeAndMigrate(prefs)
            val updated = current.gaugeConfigs.toMutableMap().apply {
                put(gaugeConfig.type, gaugeConfig.sanitized())
            }
            prefs[Keys.SCHEMA_VERSION] = DashboardConfiguration.CURRENT_SCHEMA_VERSION
            prefs[Keys.GAUGE_CONFIGS_SERIALIZED] = serializeGaugeConfigs(updated)
        }
    }

    override suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.SCHEMA_VERSION)
            prefs.remove(Keys.LAYOUT)
            prefs.remove(Keys.THEME_MODE)
            prefs.remove(Keys.ACCENT_COLOR)
            prefs.remove(Keys.GAUGE_BG_STYLE)
            prefs.remove(Keys.DENSITY)
            prefs.remove(Keys.ANIMATIONS_ENABLED)
            prefs.remove(Keys.SPEED_UNIT)
            prefs.remove(Keys.SPEED_MAX_SCALE_KMH)
            prefs.remove(Keys.SHOW_SPEED_LIMIT)
            prefs.remove(Keys.OVERSPEED_WARNING_ENABLED)
            prefs.remove(Keys.OVERSPEED_THRESHOLD_KMH)
            prefs.remove(Keys.RPM_REDLINE_THRESHOLD)
            prefs.remove(Keys.WARNINGS_ENABLED)
            prefs.remove(Keys.SHOW_UNAVAILABLE_PLACEHOLDERS)
            prefs.remove(Keys.GAUGE_CONFIGS_SERIALIZED)
        }
    }

    private fun writeAll(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        config: DashboardConfiguration
    ) {
        val safe = config.sanitized()
        prefs[Keys.SCHEMA_VERSION] = safe.schemaVersion
        prefs[Keys.LAYOUT] = safe.layout.name
        prefs[Keys.THEME_MODE] = safe.themeMode.name
        prefs[Keys.ACCENT_COLOR] = safe.accentColor.name
        prefs[Keys.GAUGE_BG_STYLE] = safe.gaugeBackgroundStyle.name
        prefs[Keys.DENSITY] = safe.density.name
        prefs[Keys.ANIMATIONS_ENABLED] = safe.animationsEnabled
        prefs[Keys.SPEED_UNIT] = safe.speedUnit.name
        prefs[Keys.SPEED_MAX_SCALE_KMH] = safe.speedometerMaxScaleKmh
        prefs[Keys.SHOW_SPEED_LIMIT] = safe.showSpeedLimit
        prefs[Keys.OVERSPEED_WARNING_ENABLED] = safe.overspeedWarningEnabled
        prefs[Keys.OVERSPEED_THRESHOLD_KMH] = safe.overspeedThresholdKmh
        prefs[Keys.RPM_REDLINE_THRESHOLD] = safe.rpmRedlineThreshold
        prefs[Keys.WARNINGS_ENABLED] = safe.warningsEnabled
        prefs[Keys.SHOW_UNAVAILABLE_PLACEHOLDERS] = safe.showUnavailableTelemetryPlaceholders
        prefs[Keys.GAUGE_CONFIGS_SERIALIZED] = serializeGaugeConfigs(safe.gaugeConfigs)
    }

    companion object {
        internal fun decodeAndMigrate(prefs: Preferences): DashboardConfiguration {
            val storedVersion = prefs[Keys.SCHEMA_VERSION] ?: 0
            val defaults = DashboardConfiguration()

            val layout = safeEnum(prefs[Keys.LAYOUT], defaults.layout)
            val theme = safeEnum(prefs[Keys.THEME_MODE], defaults.themeMode)
            val accent = safeEnum(prefs[Keys.ACCENT_COLOR], defaults.accentColor)
            val bgStyle = safeEnum(prefs[Keys.GAUGE_BG_STYLE], defaults.gaugeBackgroundStyle)
            val density = safeEnum(prefs[Keys.DENSITY], defaults.density)
            val animations = prefs[Keys.ANIMATIONS_ENABLED] ?: defaults.animationsEnabled
            val speedUnit = safeEnum(prefs[Keys.SPEED_UNIT], defaults.speedUnit)
            val maxSpeed = (prefs[Keys.SPEED_MAX_SCALE_KMH] ?: defaults.speedometerMaxScaleKmh)
                .coerceIn(120, 360)
            val showSpeedLimit = prefs[Keys.SHOW_SPEED_LIMIT] ?: defaults.showSpeedLimit
            val overspeedEnabled = prefs[Keys.OVERSPEED_WARNING_ENABLED] ?: defaults.overspeedWarningEnabled
            val overspeedThreshold = (prefs[Keys.OVERSPEED_THRESHOLD_KMH] ?: defaults.overspeedThresholdKmh)
                .coerceIn(30, maxSpeed)
            val redline = (prefs[Keys.RPM_REDLINE_THRESHOLD] ?: defaults.rpmRedlineThreshold)
                .coerceIn(3000, 7800)
            val warningsEnabled = prefs[Keys.WARNINGS_ENABLED] ?: defaults.warningsEnabled
            val showPlaceholders = prefs[Keys.SHOW_UNAVAILABLE_PLACEHOLDERS]
                ?: defaults.showUnavailableTelemetryPlaceholders

            val gaugeMap = deserializeGaugeConfigs(
                raw = prefs[Keys.GAUGE_CONFIGS_SERIALIZED],
                storedVersion = storedVersion
            )

            return DashboardConfiguration(
                schemaVersion = DashboardConfiguration.CURRENT_SCHEMA_VERSION,
                layout = layout,
                themeMode = theme,
                accentColor = accent,
                gaugeBackgroundStyle = bgStyle,
                density = density,
                animationsEnabled = animations,
                speedUnit = speedUnit,
                speedometerMaxScaleKmh = maxSpeed,
                showSpeedLimit = showSpeedLimit,
                overspeedWarningEnabled = overspeedEnabled,
                overspeedThresholdKmh = overspeedThreshold,
                rpmRedlineThreshold = redline,
                warningsEnabled = warningsEnabled,
                showUnavailableTelemetryPlaceholders = showPlaceholders,
                gaugeConfigs = gaugeMap
            ).sanitized()
        }

        internal fun serializeGaugeConfigs(map: Map<GaugeType, GaugeConfiguration>): String {
            return GaugeType.entries.joinToString(separator = ";") { type ->
                val cfg = (map[type] ?: GaugeConfiguration.defaultFor(type)).sanitized()
                listOf(
                    cfg.type.name,
                    cfg.visible.toString(),
                    cfg.size.name,
                    cfg.region.name,
                    cfg.presentation.name,
                    cfg.showLabel.toString(),
                    cfg.showUnit.toString(),
                    cfg.showWarningThreshold.toString()
                ).joinToString(separator = ",")
            }
        }

        internal fun deserializeGaugeConfigs(
            raw: String?,
            storedVersion: Int
        ): Map<GaugeType, GaugeConfiguration> {
            val result = DashboardConfiguration.defaultGaugeConfigs().toMutableMap()
            if (raw.isNullOrBlank()) return result

            // Support migrating entries even if storedVersion == 0 or partial fields exist
            raw.split(";").forEach { entry ->
                val parts = entry.split(",")
                if (parts.isEmpty()) return@forEach
                val type = runCatching { GaugeType.valueOf(parts[0].trim()) }.getOrNull()
                    ?: return@forEach
                val fallback = GaugeConfiguration.defaultFor(type)
                val visible = parts.getOrNull(1)?.toBooleanStrictOrNull() ?: fallback.visible
                val size = safeEnum(parts.getOrNull(2), fallback.size)
                val region = safeEnum(parts.getOrNull(3), fallback.region)
                val presentation = safeEnum(parts.getOrNull(4), fallback.presentation)
                val showLabel = parts.getOrNull(5)?.toBooleanStrictOrNull() ?: fallback.showLabel
                val showUnit = parts.getOrNull(6)?.toBooleanStrictOrNull() ?: fallback.showUnit
                val showThreshold = parts.getOrNull(7)?.toBooleanStrictOrNull()
                    ?: if (storedVersion <= 0) true else fallback.showWarningThreshold

                result[type] = GaugeConfiguration(
                    type = type,
                    visible = visible,
                    size = size,
                    region = region,
                    presentation = presentation,
                    showLabel = showLabel,
                    showUnit = showUnit,
                    showWarningThreshold = showThreshold
                ).sanitized()
            }
            return result
        }

        private inline fun <reified E : Enum<E>> safeEnum(raw: String?, default: E): E {
            if (raw.isNullOrBlank()) return default
            return runCatching { enumValueOf<E>(raw.trim().uppercase()) }.getOrDefault(default)
        }
    }
}
