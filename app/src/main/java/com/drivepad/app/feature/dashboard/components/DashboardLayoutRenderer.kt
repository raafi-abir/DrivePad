package com.drivepad.app.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepad.app.core.designsystem.DashboardPalette
import com.drivepad.app.domain.model.DashboardDensity
import com.drivepad.app.domain.model.DashboardLayout
import com.drivepad.app.domain.model.DashboardUiState
import com.drivepad.app.domain.model.GaugeRegion
import com.drivepad.app.domain.model.GaugeStyle
import com.drivepad.app.domain.model.GaugeType
import com.drivepad.app.domain.model.GaugeValueState
import com.drivepad.app.domain.model.IndicatorActivationState

/**
 * Renders the center driving telemetry dashboard across all 4 built-in layouts
 * (`RACING`, `TRUCK`, `MINIMAL`, `COMPACT`) inside bounded viewport constraints
 * with zero overlapping gauges and zero scrolling required while driving.
 */
@Composable
fun DashboardLayoutRenderer(
    uiState: DashboardUiState,
    palette: DashboardPalette,
    isCompactHeight: Boolean,
    modifier: Modifier = Modifier
) {
    val config = uiState.configuration
    val isCompactDensity = config.density == DashboardDensity.COMPACT || isCompactHeight
    val speedConfig = config.effectiveGaugeConfig(GaugeType.SPEEDOMETER)
    val rpmConfig = config.effectiveGaugeConfig(GaugeType.RPM_METER)
    val gearConfig = config.effectiveGaugeConfig(GaugeType.GEAR_INDICATOR)

    val swapPrimaryTopAndMiddle = speedConfig.region == GaugeRegion.PRIMARY_MIDDLE &&
        rpmConfig.region == GaugeRegion.PRIMARY_TOP

    BoxWithConstraints(
        modifier = modifier.testTag("dashboard_layout_${config.layout.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        val availableH = maxHeight
        val isTightHeight = availableH < 305.dp
        val verticalGap = if (isCompactDensity || isTightHeight) 5.dp else 11.dp

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(verticalGap, Alignment.CenterVertically)
        ) {
            if (uiState.statusBannerText != null) {
                Text(
                    text = uiState.statusBannerText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = palette.warningCaution,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(palette.warningCaution.copy(alpha = 0.14f))
                        .border(1.dp, palette.warningCaution.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .testTag("dashboard_status_banner"),
                    maxLines = 1
                )
            }

            when (config.layout) {
                DashboardLayout.RACING -> {
                    val rpmHeight = when {
                        rpmConfig.presentation == GaugeStyle.DIGITAL && (isCompactDensity || isTightHeight) -> 104.dp
                        isCompactDensity || isTightHeight -> 130.dp
                        else -> 174.dp
                    }
                    val primaryHeader: @Composable () -> Unit = {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SpeedometerGauge(
                                state = uiState.speedState,
                                config = speedConfig,
                                speedUnit = config.speedUnit,
                                maxScaleKmh = config.speedometerMaxScaleKmh,
                                overspeedThresholdKmh = config.overspeedThresholdKmh,
                                speedLimitState = uiState.speedLimitState,
                                showSpeedLimit = config.showSpeedLimit,
                                palette = palette,
                                backgroundStyle = config.gaugeBackgroundStyle,
                                animationsEnabled = config.animationsEnabled,
                                isCompact = isCompactDensity,
                                modifier = if (speedConfig.presentation == GaugeStyle.CIRCULAR) {
                                    Modifier.weight(1f).height(94.dp).padding(end = 8.dp)
                                } else {
                                    Modifier.weight(1f)
                                }
                            )
                            GearIndicatorGauge(
                                gearLabel = uiState.gearLabel,
                                config = gearConfig,
                                palette = palette,
                                isCompact = isCompactDensity
                            )
                        }
                    }
                    val rpmBlock: @Composable () -> Unit = {
                        RpmTachometerGauge(
                            state = uiState.rpmState,
                            config = rpmConfig,
                            redlineThreshold = config.rpmRedlineThreshold,
                            palette = palette,
                            backgroundStyle = config.gaugeBackgroundStyle,
                            animationsEnabled = config.animationsEnabled,
                            isCompact = isCompactDensity,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rpmHeight)
                        )
                    }

                    if (swapPrimaryTopAndMiddle) {
                        rpmBlock()
                        primaryHeader()
                    } else {
                        primaryHeader()
                        rpmBlock()
                    }

                    // Compact active driving alerts strip (excludes cockpit control button echoes)
                    val racingAlerts = uiState.indicators.filter {
                        it.activationState == IndicatorActivationState.ACTIVE &&
                            it.type.isWarningCategory &&
                            it.type != com.drivepad.app.domain.model.WarningIndicatorType.PARKING_BRAKE
                    }.take(4)
                    if (racingAlerts.isNotEmpty()) {
                        WarningIndicatorStrip(
                            indicators = racingAlerts,
                            palette = palette,
                            isCompact = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                DashboardLayout.TRUCK -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpeedometerGauge(
                            state = uiState.speedState,
                            config = speedConfig,
                            speedUnit = config.speedUnit,
                            maxScaleKmh = config.speedometerMaxScaleKmh,
                            overspeedThresholdKmh = config.overspeedThresholdKmh,
                            speedLimitState = uiState.speedLimitState,
                            showSpeedLimit = config.showSpeedLimit,
                            palette = palette,
                            backgroundStyle = config.gaugeBackgroundStyle,
                            animationsEnabled = config.animationsEnabled,
                            isCompact = true,
                            modifier = if (speedConfig.presentation == GaugeStyle.CIRCULAR) {
                                Modifier.weight(1f).height(82.dp).padding(end = 6.dp)
                            } else {
                                Modifier.weight(1f)
                            }
                        )
                        GearIndicatorGauge(
                            gearLabel = uiState.gearLabel,
                            config = gearConfig,
                            palette = palette,
                            isCompact = true
                        )
                    }

                    RpmTachometerGauge(
                        state = uiState.rpmState,
                        config = rpmConfig,
                        redlineThreshold = config.rpmRedlineThreshold,
                        palette = palette,
                        backgroundStyle = config.gaugeBackgroundStyle,
                        animationsEnabled = config.animationsEnabled,
                        isCompact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isTightHeight) 104.dp else 128.dp)
                    )

                    // Optional truck telemetry placeholders (only shows real values or N/A)
                    if (config.showUnavailableTelemetryPlaceholders ||
                        uiState.availability.supportsExternalTruckTelemetry
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AuxiliaryTelemetryTile(
                                title = "FUEL",
                                valueState = uiState.auxiliaryGauges[GaugeType.FUEL] ?: GaugeValueState.Unavailable,
                                palette = palette,
                                backgroundStyle = config.gaugeBackgroundStyle,
                                modifier = Modifier.weight(1f)
                            )
                            AuxiliaryTelemetryTile(
                                title = "TEMP",
                                valueState = uiState.auxiliaryGauges[GaugeType.WATER_TEMPERATURE] ?: GaugeValueState.Unavailable,
                                palette = palette,
                                backgroundStyle = config.gaugeBackgroundStyle,
                                modifier = Modifier.weight(1f)
                            )
                            AuxiliaryTelemetryTile(
                                title = "OIL / AIR",
                                valueState = uiState.auxiliaryGauges[GaugeType.AIR_PRESSURE] ?: GaugeValueState.Unavailable,
                                palette = palette,
                                backgroundStyle = config.gaugeBackgroundStyle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    WarningIndicatorStrip(
                        indicators = uiState.indicators.take(8),
                        palette = palette,
                        isCompact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                DashboardLayout.MINIMAL -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SpeedometerGauge(
                            state = uiState.speedState,
                            config = speedConfig,
                            speedUnit = config.speedUnit,
                            maxScaleKmh = config.speedometerMaxScaleKmh,
                            overspeedThresholdKmh = config.overspeedThresholdKmh,
                            speedLimitState = uiState.speedLimitState,
                            showSpeedLimit = config.showSpeedLimit,
                            palette = palette,
                            backgroundStyle = config.gaugeBackgroundStyle,
                            animationsEnabled = config.animationsEnabled,
                            isCompact = isCompactDensity,
                            modifier = if (speedConfig.presentation == GaugeStyle.CIRCULAR) {
                                Modifier.weight(1f).height(94.dp).padding(end = 8.dp)
                            } else {
                                Modifier.weight(1f)
                            }
                        )
                        GearIndicatorGauge(
                            gearLabel = uiState.gearLabel,
                            config = gearConfig,
                            palette = palette,
                            isCompact = isCompactDensity
                        )
                    }

                    RpmTachometerGauge(
                        state = uiState.rpmState,
                        config = rpmConfig,
                        redlineThreshold = config.rpmRedlineThreshold,
                        palette = palette,
                        backgroundStyle = config.gaugeBackgroundStyle,
                        animationsEnabled = config.animationsEnabled,
                        isCompact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (rpmConfig.presentation == GaugeStyle.DIGITAL) 92.dp else 138.dp)
                    )

                    val essentialWarnings = uiState.indicators.filter {
                        it.activationState == IndicatorActivationState.ACTIVE &&
                            it.type.isWarningCategory &&
                            it.type != com.drivepad.app.domain.model.WarningIndicatorType.PARKING_BRAKE
                    }.take(3)
                    if (essentialWarnings.isNotEmpty()) {
                        WarningIndicatorStrip(
                            indicators = essentialWarnings,
                            palette = palette,
                            isCompact = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                DashboardLayout.COMPACT -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SpeedometerGauge(
                            state = uiState.speedState,
                            config = speedConfig.copy(presentation = speedConfig.presentation),
                            speedUnit = config.speedUnit,
                            maxScaleKmh = config.speedometerMaxScaleKmh,
                            overspeedThresholdKmh = config.overspeedThresholdKmh,
                            speedLimitState = uiState.speedLimitState,
                            showSpeedLimit = config.showSpeedLimit,
                            palette = palette,
                            backgroundStyle = config.gaugeBackgroundStyle,
                            animationsEnabled = config.animationsEnabled,
                            isCompact = true,
                            modifier = Modifier.weight(1f)
                        )
                        GearIndicatorGauge(
                            gearLabel = uiState.gearLabel,
                            config = gearConfig,
                            palette = palette,
                            isCompact = true
                        )
                    }

                    RpmTachometerGauge(
                        state = uiState.rpmState,
                        config = rpmConfig,
                        redlineThreshold = config.rpmRedlineThreshold,
                        palette = palette,
                        backgroundStyle = config.gaugeBackgroundStyle,
                        animationsEnabled = config.animationsEnabled,
                        isCompact = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (rpmConfig.presentation == GaugeStyle.DIGITAL) 88.dp else 128.dp)
                    )

                    WarningIndicatorStrip(
                        indicators = uiState.indicators.take(9),
                        palette = palette,
                        isCompact = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
