package com.drivepad.app.core.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.drivepad.app.domain.model.DashboardAccentColor
import com.drivepad.app.domain.model.DashboardThemeMode
import com.drivepad.app.domain.model.GaugeBackgroundStyle

@Immutable
data class ExtendedCockpitColors(
    val neonGreen: Color = NeonGreenPrimary,
    val neonGreenGlow: Color = NeonGreenGlow,
    val brakeRed: Color = BrakeRed,
    val brakeRedGlow: Color = BrakeRedGlow,
    val warningAmber: Color = WarningAmber,
    val cockpitSurfaceElevated: Color = CockpitSurfaceElevated,
    val cockpitBorder: Color = CockpitBorder,
    val pedalMetalBase: Color = PedalMetalBase,
    val pedalMetalSlats: Color = PedalMetalSlats,
    val controlUnselected: Color = ControlUnselected,
    val textMuted: Color = TextMuted
)

@Immutable
data class DashboardPalette(
    val isDark: Boolean = true,
    val accent: Color = Color(0xFFE51414),
    val accentGlow: Color = Color(0xFFFF2424),
    val canvasBackground: Color = Color(0xFF040507),
    val cardTop: Color = Color(0xFF0E1015),
    val cardBottom: Color = Color(0xFF07080B),
    val cardBorder: Color = Color(0xFF262A33),
    val textPrimary: Color = Color.White,
    val textSecondary: Color = Color(0xFF9CA3AF),
    val gaugeTrack: Color = Color(0xFFE5E7EB),
    val redlineColor: Color = Color(0xFFF51818),
    val warningCritical: Color = Color(0xFFFF2424),
    val warningCaution: Color = Color(0xFFFFAB00),
    val infoActive: Color = Color(0xFF00E676),
    val unavailableTint: Color = Color(0xFF4B5563)
) {
    fun cardColorsFor(style: GaugeBackgroundStyle): Pair<Color, Color> = when (style) {
        GaugeBackgroundStyle.CARBON_DARK -> cardTop to cardBottom
        GaugeBackgroundStyle.TRANSLUCENT_GLASS -> if (isDark) {
            Color(0xCC12161E) to Color(0xB3090B0F)
        } else {
            Color(0xE6FFFFFF) to Color(0xD9EEF2F6)
        }
        GaugeBackgroundStyle.SOLID_CONTRAST -> if (isDark) {
            Color(0xFF0A0B0E) to Color(0xFF0A0B0E)
        } else {
            Color(0xFFFFFFFF) to Color(0xFFFFFFFF)
        }
    }
}

val LocalCockpitColors = staticCompositionLocalOf { ExtendedCockpitColors() }
val LocalDashboardPalette = staticCompositionLocalOf { DashboardPalette() }

private val DrivePadDarkColorScheme = darkColorScheme(
    primary = NeonGreenPrimary,
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF005327),
    onPrimaryContainer = Color(0xFF6AFF9B),
    secondary = TextSecondary,
    onSecondary = DeepGraphiteBackground,
    secondaryContainer = CockpitSurfaceElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = BrakeRed,
    onTertiary = Color.White,
    background = DeepGraphiteBackground,
    onBackground = TextPrimary,
    surface = CockpitSurface,
    onSurface = TextPrimary,
    surfaceVariant = CockpitSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = CockpitBorder,
    outlineVariant = CockpitBorderSubtle,
    error = BrakeRed,
    onError = Color.White
)

private val DrivePadLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5C9),
    onPrimaryContainer = Color(0xFF00210B),
    secondary = LightTextSecondary,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceElevated,
    onSecondaryContainer = LightTextPrimary,
    tertiary = BrakeRed,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorder,
    error = BrakeRed,
    onError = Color.White
)

fun buildDashboardPalette(
    isDark: Boolean,
    accentColor: DashboardAccentColor
): DashboardPalette {
    val accent = Color(accentColor.primaryHex)
    val glow = Color(accentColor.glowHex)
    return if (isDark) {
        DashboardPalette(
            isDark = true,
            accent = accent,
            accentGlow = glow,
            canvasBackground = Color(0xFF040507),
            cardTop = Color(0xFF0E1015),
            cardBottom = Color(0xFF07080B),
            cardBorder = Color(0xFF262A33),
            textPrimary = Color.White,
            textSecondary = Color(0xFF9CA3AF),
            gaugeTrack = Color(0xFFE5E7EB),
            redlineColor = Color(0xFFF51818),
            warningCritical = Color(0xFFFF2424),
            warningCaution = Color(0xFFFFAB00),
            infoActive = Color(0xFF00E676),
            unavailableTint = Color(0xFF4B5563)
        )
    } else {
        DashboardPalette(
            isDark = false,
            accent = accent,
            accentGlow = glow,
            canvasBackground = Color(0xFFF1F5F9),
            cardTop = Color(0xFFFFFFFF),
            cardBottom = Color(0xFFE9EEF5),
            cardBorder = Color(0xFFCBD5E1),
            textPrimary = Color(0xFF0F172A),
            textSecondary = Color(0xFF475569),
            gaugeTrack = Color(0xFF334155),
            redlineColor = Color(0xFFDC2626),
            warningCritical = Color(0xFFDC2626),
            warningCaution = Color(0xFFD97706),
            infoActive = Color(0xFF059669),
            unavailableTint = Color(0xFF94A3B8)
        )
    }
}

@Composable
fun DrivePadTheme(
    darkTheme: Boolean = true,
    themeMode: DashboardThemeMode? = null,
    accentColor: DashboardAccentColor = DashboardAccentColor.CRIMSON,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val resolvedDark = when (themeMode) {
        DashboardThemeMode.DARK -> true
        DashboardThemeMode.LIGHT -> false
        DashboardThemeMode.SYSTEM -> isSystemInDarkTheme()
        null -> darkTheme
    }

    val accentPrimary = Color(accentColor.primaryHex)
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (resolvedDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        resolvedDark -> DrivePadDarkColorScheme.copy(
            primary = if (accentColor == DashboardAccentColor.CRIMSON) NeonGreenPrimary else accentPrimary
        )
        else -> DrivePadLightColorScheme.copy(
            primary = if (accentColor == DashboardAccentColor.CRIMSON) LightPrimary else accentPrimary
        )
    }

    val extendedColors = ExtendedCockpitColors()
    val dashboardPalette = buildDashboardPalette(
        isDark = resolvedDark,
        accentColor = accentColor
    )

    CompositionLocalProvider(
        LocalCockpitColors provides extendedColors,
        LocalDashboardPalette provides dashboardPalette
    ) {
        MaterialTheme(
            colorScheme = baseScheme,
            typography = DrivePadTypography,
            content = content
        )
    }
}

object DrivePadThemeExtras {
    val cockpitColors: ExtendedCockpitColors
        @Composable
        get() = LocalCockpitColors.current

    val dashboardPalette: DashboardPalette
        @Composable
        get() = LocalDashboardPalette.current
}
