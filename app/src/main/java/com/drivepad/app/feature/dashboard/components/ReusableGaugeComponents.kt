package com.drivepad.app.feature.dashboard.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepad.app.core.designsystem.DashboardPalette
import com.drivepad.app.domain.model.GaugeBackgroundStyle
import com.drivepad.app.domain.model.GaugeConfiguration
import com.drivepad.app.domain.model.GaugeSize
import com.drivepad.app.domain.model.GaugeStyle
import com.drivepad.app.domain.model.GaugeValueState
import com.drivepad.app.domain.model.SpeedUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val TelemetryCardShape = RoundedCornerShape(14.dp)
private val GearBadgeShape = RoundedCornerShape(10.dp)
private val LimitChipShape = RoundedCornerShape(6.dp)

@Composable
fun SpeedometerGauge(
    state: GaugeValueState,
    config: GaugeConfiguration,
    speedUnit: SpeedUnit,
    maxScaleKmh: Int,
    overspeedThresholdKmh: Int,
    speedLimitState: GaugeValueState,
    showSpeedLimit: Boolean,
    palette: DashboardPalette,
    backgroundStyle: GaugeBackgroundStyle,
    animationsEnabled: Boolean,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    if (!config.visible) return

    val sizeScale = when (config.size) {
        GaugeSize.SMALL -> 0.86f
        GaugeSize.MEDIUM -> 1.0f
        GaugeSize.LARGE -> 1.12f
    }

    val isWarning = (state as? GaugeValueState.Live)?.isWarning == true
    val speedDisplayText = when (state) {
        is GaugeValueState.Live -> state.formattedValue
        is GaugeValueState.EngineOff -> "0.00"
        is GaugeValueState.Stale -> "--"
        GaugeValueState.Disconnected -> "--"
        GaugeValueState.Paused -> "--"
        GaugeValueState.Unavailable -> "--"
    }
    val subStatusText = when (state) {
        is GaugeValueState.Live -> if (state.isWarning && config.showWarningThreshold) state.thresholdNote else null
        is GaugeValueState.EngineOff -> null
        is GaugeValueState.Stale -> "STALE"
        GaugeValueState.Disconnected -> "NO LINK"
        GaugeValueState.Paused -> "PAUSED"
        GaugeValueState.Unavailable -> "N/A"
    }

    if (config.presentation == GaugeStyle.CIRCULAR) {
        CircularSpeedometerCard(
            state = state,
            speedDisplayText = speedDisplayText,
            subStatusText = subStatusText,
            speedUnit = speedUnit,
            maxScaleKmh = maxScaleKmh,
            overspeedThresholdKmh = overspeedThresholdKmh,
            showLabel = config.showLabel,
            showUnit = config.showUnit,
            showThreshold = config.showWarningThreshold,
            isWarning = isWarning,
            palette = palette,
            backgroundStyle = backgroundStyle,
            animationsEnabled = animationsEnabled,
            isCompact = isCompact,
            modifier = modifier.testTag("speedometer_gauge")
        )
    } else {
        DigitalSpeedometerBlock(
            speedDisplayText = speedDisplayText,
            subStatusText = subStatusText,
            speedUnit = speedUnit,
            speedLimitState = speedLimitState,
            showSpeedLimit = showSpeedLimit,
            showLabel = config.showLabel,
            showUnit = config.showUnit,
            showThreshold = config.showWarningThreshold,
            isWarning = isWarning,
            sizeScale = sizeScale,
            palette = palette,
            isCompact = isCompact,
            modifier = modifier.testTag("speedometer_gauge")
        )
    }
}

@Composable
private fun DigitalSpeedometerBlock(
    speedDisplayText: String,
    subStatusText: String?,
    speedUnit: SpeedUnit,
    speedLimitState: GaugeValueState,
    showSpeedLimit: Boolean,
    showLabel: Boolean,
    showUnit: Boolean,
    showThreshold: Boolean,
    isWarning: Boolean,
    sizeScale: Float,
    palette: DashboardPalette,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val slashColor = if (isWarning) palette.warningCritical else palette.accent
    val valueColor = if (isWarning) palette.warningCritical else palette.textPrimary
    val baseFontSp = (if (isCompact) 26f else 34f) * sizeScale

    Row(
        modifier = modifier.semantics {
            contentDescription = "Speedometer $speedDisplayText ${speedUnit.symbol}"
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(
            modifier = Modifier
                .width(if (isCompact) 12.dp else 16.dp)
                .height(if (isCompact) 40.dp else 50.dp)
        ) {
            drawLine(
                brush = Brush.verticalGradient(colors = listOf(palette.accentGlow, slashColor)),
                start = Offset(size.width * 0.85f, size.height * 0.08f),
                end = Offset(size.width * 0.15f, size.height * 0.92f),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Spacer(modifier = Modifier.width(if (isCompact) 6.dp else 9.dp))

        Column(verticalArrangement = Arrangement.Center) {
            if (showLabel) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "SPEED",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (isCompact) 9.5.sp else 11.sp,
                            fontStyle = FontStyle.Italic,
                            letterSpacing = 0.8.sp
                        ),
                        color = palette.textSecondary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    if (showSpeedLimit) {
                        val limitLabel = when (speedLimitState) {
                            is GaugeValueState.Live -> "LIM ${speedLimitState.formattedValue}"
                            else -> "LIM --"
                        }
                        Text(
                            text = limitLabel,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = palette.textSecondary,
                            modifier = Modifier
                                .clip(LimitChipShape)
                                .border(1.dp, palette.cardBorder, LimitChipShape)
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                            maxLines = 1
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = speedDisplayText,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = baseFontSp.sp,
                        fontStyle = FontStyle.Italic,
                        lineHeight = (baseFontSp + 2f).sp
                    ),
                    color = valueColor,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    modifier = Modifier.testTag("speed_value_display")
                )
                if (showUnit) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = speedUnit.symbol,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = if (isCompact) 10.5.sp else 12.5.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = palette.textSecondary,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = if (isCompact) 3.dp else 4.dp),
                        maxLines = 1
                    )
                }
            }

            if (subStatusText != null && (showThreshold || !isWarning)) {
                Text(
                    text = subStatusText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (isWarning) palette.warningCritical else palette.warningCaution,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CircularSpeedometerCard(
    state: GaugeValueState,
    speedDisplayText: String,
    subStatusText: String?,
    speedUnit: SpeedUnit,
    maxScaleKmh: Int,
    overspeedThresholdKmh: Int,
    showLabel: Boolean,
    showUnit: Boolean,
    showThreshold: Boolean,
    isWarning: Boolean,
    palette: DashboardPalette,
    backgroundStyle: GaugeBackgroundStyle,
    animationsEnabled: Boolean,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val maxDisplay = speedUnit.convertFromKmh(maxScaleKmh.toFloat()).coerceAtLeast(60f)
    val targetSpeed = when (state) {
        is GaugeValueState.Live -> state.numericValue.coerceIn(0f, maxDisplay)
        else -> 0f
    }
    val animatedSpeed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 480f),
        label = "speedNeedle"
    )
    val effectiveSpeed = if (animationsEnabled) animatedSpeed else targetSpeed
    val overspeedFraction = (overspeedThresholdKmh.toFloat() / maxScaleKmh.toFloat()).coerceIn(0.25f, 0.98f)
    val (bgTop, bgBottom) = palette.cardColorsFor(backgroundStyle)
    val borderColor = if (isWarning) palette.warningCritical else palette.cardBorder

    BoxWithConstraints(
        modifier = modifier
            .clip(TelemetryCardShape)
            .background(Brush.verticalGradient(colors = listOf(bgTop, bgBottom)))
            .border(if (isWarning) 1.5.dp else 1.dp, borderColor, TelemetryCardShape)
            .semantics { contentDescription = "Speedometer $speedDisplayText ${speedUnit.symbol}" }
    ) {
        val arcRadiusDp = minOf(maxWidth * 0.42f, maxHeight * 0.68f)
        val centerBottomDp = maxHeight * 0.16f
        val isTight = arcRadiusDp < 60.dp

        key(palette.isDark, overspeedFraction, palette.accent) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawBehind {
                            val w = size.width
                            val h = size.height
                            if (w <= 0f || h <= 0f) return@onDrawBehind
                            val arcRadius = arcRadiusDp.toPx()
                            val center = Offset(w / 2f, h - centerBottomDp.toPx())
                            val topLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                            val arcSize = Size(arcRadius * 2f, arcRadius * 2f)
                            val stroke = (arcRadius * 0.04f).coerceIn(1.5.dp.toPx(), 2.8.dp.toPx())
                            val safeSweep = 180f * overspeedFraction
                            drawArc(
                                color = palette.gaugeTrack,
                                startAngle = 180f,
                                sweepAngle = safeSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Butt)
                            )
                            drawArc(
                                color = palette.warningCritical,
                                startAngle = 180f + safeSweep,
                                sweepAngle = 180f - safeSweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Butt)
                            )
                            for (step in 0..16) {
                                val frac = step / 16f
                                val angleRad = (180f + frac * 180f) * (PI.toFloat() / 180f)
                                val major = step % 4 == 0
                                val len = if (major) 7.dp.toPx() else 3.5.dp.toPx()
                                drawLine(
                                    color = if (frac >= overspeedFraction) palette.warningCritical else palette.gaugeTrack,
                                    start = Offset(center.x + arcRadius * cos(angleRad), center.y + arcRadius * sin(angleRad)),
                                    end = Offset(center.x + (arcRadius - len) * cos(angleRad), center.y + (arcRadius - len) * sin(angleRad)),
                                    strokeWidth = if (major) 1.6.dp.toPx() else 1.dp.toPx()
                                )
                            }
                        }
                    }
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas
            val arcRadius = arcRadiusDp.toPx()
            val center = Offset(w / 2f, h - centerBottomDp.toPx())
            val norm = (effectiveSpeed / maxDisplay).coerceIn(0f, 1f)
            drawGaugeNeedle(
                center = center,
                arcRadius = arcRadius,
                hubRadiusPx = (arcRadius * 0.11f).coerceIn(4.5.dp.toPx(), 8.dp.toPx()),
                normalizedFraction = norm,
                needleColor = if (isWarning) palette.warningCritical else palette.accent,
                glowColor = palette.accentGlow
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = centerBottomDp + 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showLabel) {
                Text(
                    text = "SPEED",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (isTight) 7.5.sp else 9.sp),
                    color = palette.textSecondary,
                    maxLines = 1
                )
            }
            Text(
                text = speedDisplayText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = if (isTight || isCompact) 16.sp else 20.sp
                ),
                color = if (isWarning) palette.warningCritical else palette.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.testTag("speed_value_display")
            )
            if (showUnit) {
                Text(
                    text = subStatusText?.takeIf { showThreshold || !isWarning } ?: speedUnit.symbol,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                    color = if (isWarning) palette.warningCritical else palette.textSecondary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun RpmTachometerGauge(
    state: GaugeValueState,
    config: GaugeConfiguration,
    redlineThreshold: Int,
    palette: DashboardPalette,
    backgroundStyle: GaugeBackgroundStyle,
    animationsEnabled: Boolean,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    if (!config.visible) return

    val reportedMaxRpm = when (state) {
        is GaugeValueState.Live -> (state.maxValue ?: 8000f).coerceIn(4000f, 12000f)
        else -> 8000f
    }
    val targetRpm = when (state) {
        is GaugeValueState.Live -> state.numericValue.coerceIn(0f, reportedMaxRpm)
        else -> 0f
    }
    val animatedRpm by animateFloatAsState(
        targetValue = targetRpm,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "rpmNeedle"
    )
    val effectiveRpm = if (animationsEnabled) animatedRpm else targetRpm
    val isRedline = (state as? GaugeValueState.Live)?.isWarning == true
    val centerReadout = when (state) {
        is GaugeValueState.Live -> state.formattedValue
        is GaugeValueState.EngineOff -> "0"
        is GaugeValueState.Stale -> "--"
        GaugeValueState.Disconnected -> "--"
        GaugeValueState.Paused -> "--"
        GaugeValueState.Unavailable -> "--"
    }
    val statusBadge = when (state) {
        is GaugeValueState.Live -> if (state.isWarning && config.showWarningThreshold) "REDLINE" else null
        is GaugeValueState.EngineOff -> "ENGINE OFF"
        is GaugeValueState.Stale -> "STALE"
        GaugeValueState.Disconnected -> "NO LINK"
        GaugeValueState.Paused -> "PAUSED"
        GaugeValueState.Unavailable -> "N/A"
    }

    val (bgTop, bgBottom) = palette.cardColorsFor(backgroundStyle)
    val borderColor = if (isRedline) palette.warningCritical else palette.cardBorder

    if (config.presentation == GaugeStyle.DIGITAL) {
        DigitalRpmCard(
            rpmReadout = centerReadout,
            statusBadge = statusBadge,
            currentRpm = effectiveRpm,
            maxRpm = reportedMaxRpm,
            redlineThreshold = redlineThreshold,
            showLabel = config.showLabel,
            showUnit = config.showUnit,
            showThreshold = config.showWarningThreshold,
            isRedline = isRedline,
            palette = palette,
            bgTop = bgTop,
            bgBottom = bgBottom,
            borderColor = borderColor,
            isCompact = isCompact,
            modifier = modifier
        )
    } else {
        CircularRpmCard(
            rpmReadout = centerReadout,
            statusBadge = statusBadge,
            currentRpm = effectiveRpm,
            maxRpm = reportedMaxRpm,
            redlineThreshold = redlineThreshold,
            showLabel = config.showLabel,
            showUnit = config.showUnit,
            showThreshold = config.showWarningThreshold,
            isRedline = isRedline,
            palette = palette,
            bgTop = bgTop,
            bgBottom = bgBottom,
            borderColor = borderColor,
            isCompact = isCompact,
            modifier = modifier
        )
    }
}

@Composable
private fun DigitalRpmCard(
    rpmReadout: String,
    statusBadge: String?,
    currentRpm: Float,
    maxRpm: Float,
    redlineThreshold: Int,
    showLabel: Boolean,
    showUnit: Boolean,
    showThreshold: Boolean,
    isRedline: Boolean,
    palette: DashboardPalette,
    bgTop: Color,
    bgBottom: Color,
    borderColor: Color,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val fillFrac = (currentRpm / maxRpm).coerceIn(0f, 1f)
    val redFrac = (redlineThreshold.toFloat() / maxRpm).coerceIn(0.3f, 0.98f)

    Column(
        modifier = modifier
            .clip(TelemetryCardShape)
            .background(Brush.verticalGradient(colors = listOf(bgTop, bgBottom)))
            .border(if (isRedline) 1.6.dp else 1.dp, borderColor, TelemetryCardShape)
            .padding(horizontal = 14.dp, vertical = if (isCompact) 10.dp else 14.dp)
            .semantics { contentDescription = "RPM tachometer $rpmReadout" }
            .testTag("rpm_meter_card"),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showLabel) {
                Text(
                    text = "RPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (statusBadge != null) {
                Text(
                    text = statusBadge,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = if (isRedline) palette.warningCritical else palette.warningCaution,
                    fontWeight = FontWeight.Bold
                )
            } else if (showThreshold) {
                Text(
                    text = "REDLINE ${redlineThreshold}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = palette.textSecondary
                )
            }
        }

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = rpmReadout,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (isCompact) 24.sp else 32.sp
                ),
                color = if (isRedline) palette.warningCritical else palette.textPrimary,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.testTag("rpm_value_display")
            )
            if (showUnit) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "/ ${maxRpm.roundToInt()} RPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.textSecondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        // Segmented RPM bar with configured redline marker
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 14.dp else 18.dp)
        ) {
            val segments = 20
            val gap = 3.dp.toPx()
            val segW = ((size.width - gap * (segments - 1)) / segments).coerceAtLeast(2f)
            for (idx in 0 until segments) {
                val segFrac = (idx + 1).toFloat() / segments
                val isLit = fillFrac >= (idx.toFloat() / segments) && currentRpm > 5f
                val isRedZone = segFrac >= redFrac
                val color = when {
                    isLit && isRedZone -> palette.redlineColor
                    isLit -> palette.accent
                    isRedZone -> palette.redlineColor.copy(alpha = 0.22f)
                    else -> palette.cardBorder.copy(alpha = 0.7f)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(idx * (segW + gap), 0f),
                    size = Size(segW, size.height),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
    }
}

@Composable
private fun CircularRpmCard(
    rpmReadout: String,
    statusBadge: String?,
    currentRpm: Float,
    maxRpm: Float,
    redlineThreshold: Int,
    showLabel: Boolean,
    showUnit: Boolean,
    showThreshold: Boolean,
    isRedline: Boolean,
    palette: DashboardPalette,
    bgTop: Color,
    bgBottom: Color,
    borderColor: Color,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val maxDigit = (maxRpm / 1000f).roundToInt().coerceIn(4, 12)
    val redlineFraction = (redlineThreshold.toFloat() / maxRpm).coerceIn(0.3f, 0.98f)

    BoxWithConstraints(
        modifier = modifier
            .clip(TelemetryCardShape)
            .background(Brush.verticalGradient(colors = listOf(bgTop, bgBottom)))
            .border(if (isRedline) 1.6.dp else 1.dp, borderColor, TelemetryCardShape)
            .semantics { contentDescription = "RPM tachometer $rpmReadout" }
            .testTag("rpm_meter_card")
    ) {
        val cardW = maxWidth
        val cardH = maxHeight
        val arcRadiusDp = minOf(cardW * 0.42f, cardH * 0.74f)
        val hubRadiusDp = (arcRadiusDp * 0.11f).coerceIn(5.5.dp, 9.5.dp)
        val centerFromBottomDp = cardH * 0.15f
        val readoutBottomPadding = centerFromBottomDp + hubRadiusDp + 2.dp
        val isTightGauge = arcRadiusDp < 78.dp

        val labelTextStyle = remember(isTightGauge, palette.textPrimary) {
            TextStyle(
                color = palette.textPrimary,
                fontSize = if (isTightGauge) 10.sp else 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        val measuredDigits = remember(textMeasurer, labelTextStyle, maxDigit) {
            (0..maxDigit).map { digit ->
                textMeasurer.measure(digit.toString(), style = labelTextStyle)
            }
        }

        key(isTightGauge, palette.isDark, redlineFraction, maxDigit) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        onDrawBehind {
                            val w = size.width
                            val h = size.height
                            if (w <= 0f || h <= 0f) return@onDrawBehind

                            val arcRadius = arcRadiusDp.toPx()
                            val center = Offset(w / 2f, h - centerFromBottomDp.toPx())
                            val arcTopLeft = Offset(center.x - arcRadius, center.y - arcRadius)
                            val arcSize = Size(arcRadius * 2f, arcRadius * 2f)

                            val normalSweepDeg = 180f * redlineFraction
                            val redSweepDeg = 180f - normalSweepDeg
                            val arcStroke = (arcRadius * 0.026f).coerceIn(1.6.dp.toPx(), 2.8.dp.toPx())

                            drawArc(
                                color = palette.gaugeTrack,
                                startAngle = 180f,
                                sweepAngle = normalSweepDeg,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(width = arcStroke, cap = StrokeCap.Butt)
                            )
                            drawArc(
                                color = palette.redlineColor,
                                startAngle = 180f + normalSweepDeg,
                                sweepAngle = redSweepDeg,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = Stroke(width = arcStroke, cap = StrokeCap.Butt)
                            )

                            val totalSteps = maxDigit * 4
                            val majorLen = (arcRadius * 0.105f).coerceIn(5.5.dp.toPx(), 10.5.dp.toPx())
                            val minorLen = (arcRadius * 0.056f).coerceIn(3.dp.toPx(), 6.dp.toPx())
                            val majorWidth = (arcRadius * 0.022f).coerceIn(1.4.dp.toPx(), 2.4.dp.toPx())
                            val minorWidth = (arcRadius * 0.011f).coerceIn(0.8.dp.toPx(), 1.3.dp.toPx())

                            for (step in 0..totalSteps) {
                                val frac = step.toFloat() / totalSteps
                                val angleRad = (180f + frac * 180f) * (PI.toFloat() / 180f)
                                val isMajor = step % 4 == 0
                                val isRedZone = frac >= redlineFraction
                                val tickLen = if (isMajor) majorLen else minorLen
                                val innerR = arcRadius - tickLen
                                drawLine(
                                    color = if (isRedZone) palette.redlineColor else palette.gaugeTrack,
                                    start = Offset(center.x + arcRadius * cos(angleRad), center.y + arcRadius * sin(angleRad)),
                                    end = Offset(center.x + innerR * cos(angleRad), center.y + innerR * sin(angleRad)),
                                    strokeWidth = if (isMajor) majorWidth else minorWidth,
                                    cap = StrokeCap.Butt
                                )
                            }

                            val labelRadius = arcRadius - majorLen - (arcRadius * 0.12f)
                            for (digit in 0..maxDigit) {
                                val frac = digit.toFloat() / maxDigit.toFloat()
                                val angleRad = (180f + frac * 180f) * (PI.toFloat() / 180f)
                                val lx = center.x + labelRadius * cos(angleRad)
                                val ly = center.y + labelRadius * sin(angleRad)
                                val measured = measuredDigits[digit]
                                drawText(
                                    textLayoutResult = measured,
                                    topLeft = Offset(
                                        x = lx - measured.size.width / 2f,
                                        y = ly - measured.size.height / 2f
                                    )
                                )
                            }
                        }
                    }
            )
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val glowAlpha = if (isRedline) 0.34f else 0.14f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        (if (isRedline) palette.warningCritical else palette.accentGlow).copy(alpha = glowAlpha),
                        Color.Transparent
                    ),
                    center = Offset(w / 2f, h * 1.04f),
                    radius = w * 0.46f
                ),
                radius = w * 0.46f,
                center = Offset(w / 2f, h * 1.04f)
            )

            val arcRadius = arcRadiusDp.toPx()
            val center = Offset(w / 2f, h - centerFromBottomDp.toPx())
            val norm = (currentRpm / maxRpm).coerceIn(0f, 1f)
            drawGaugeNeedle(
                center = center,
                arcRadius = arcRadius,
                hubRadiusPx = hubRadiusDp.toPx(),
                normalizedFraction = norm,
                needleColor = if (isRedline) palette.warningCritical else palette.accent,
                glowColor = if (isRedline) palette.warningCritical else palette.accentGlow
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = readoutBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showLabel) {
                Text(
                    text = statusBadge ?: "RPM",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isTightGauge || isCompact) 8.sp else 9.5.sp,
                        letterSpacing = 0.6.sp
                    ),
                    color = when {
                        isRedline -> palette.warningCritical
                        statusBadge != null -> palette.warningCaution
                        else -> palette.textSecondary
                    },
                    fontWeight = if (statusBadge != null) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
            Text(
                text = rpmReadout,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (isTightGauge) 18.sp else if (isCompact) 22.sp else 27.sp,
                    lineHeight = if (isTightGauge) 19.sp else 28.sp
                ),
                color = if (isRedline) palette.warningCritical else palette.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.testTag("rpm_value_display")
            )
            if (showUnit) {
                Text(
                    text = if (showThreshold && isRedline) "MAX ${maxRpm.roundToInt()}" else "x1000",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isTightGauge) 7.5.sp else 9.sp
                    ),
                    color = palette.textSecondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun GearIndicatorGauge(
    gearLabel: String,
    config: GaugeConfiguration,
    palette: DashboardPalette,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    if (!config.visible) return

    val sizeDp = when (config.size) {
        GaugeSize.SMALL -> if (isCompact) 36.dp else 40.dp
        GaugeSize.MEDIUM -> if (isCompact) 42.dp else 46.dp
        GaugeSize.LARGE -> if (isCompact) 44.dp else 50.dp
    }
    val borderColor by animateColorAsState(
        targetValue = if (gearLabel == "R") palette.warningCritical else palette.accentGlow,
        label = "gearBadgeBorder"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(sizeDp)
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(palette.accentGlow.copy(alpha = 0.30f), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.86f
                        ),
                        topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                        size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                }
                .clip(GearBadgeShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(palette.cardTop, palette.cardBottom)
                    )
                )
                .border(1.6.dp, borderColor, GearBadgeShape)
                .testTag("manual_gear_display"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = gearLabel,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = when (config.size) {
                        GaugeSize.SMALL -> 17.sp
                        GaugeSize.MEDIUM -> 21.sp
                        GaugeSize.LARGE -> 23.sp
                    }
                ),
                color = palette.textPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        if (config.showLabel) {
            Text(
                text = "GEAR",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = palette.textSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun AuxiliaryTelemetryTile(
    title: String,
    valueState: GaugeValueState,
    palette: DashboardPalette,
    backgroundStyle: GaugeBackgroundStyle,
    modifier: Modifier = Modifier
) {
    val (bgTop, bgBottom) = palette.cardColorsFor(backgroundStyle)
    val valueText = when (valueState) {
        is GaugeValueState.Live -> "${valueState.formattedValue} ${valueState.unitLabel}"
        is GaugeValueState.EngineOff -> "OFF"
        is GaugeValueState.Stale -> "STALE"
        GaugeValueState.Disconnected -> "--"
        GaugeValueState.Paused -> "PAUSED"
        GaugeValueState.Unavailable -> "N/A"
    }
    val isWarning = (valueState as? GaugeValueState.Live)?.isWarning == true
    val border = if (isWarning) palette.warningCritical else palette.cardBorder

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(colors = listOf(bgTop, bgBottom)))
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
            color = palette.textSecondary,
            maxLines = 1
        )
        Text(
            text = valueText,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            color = when {
                isWarning -> palette.warningCritical
                valueState is GaugeValueState.Live -> palette.textPrimary
                else -> palette.unavailableTint
            },
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun DrawScope.drawGaugeNeedle(
    center: Offset,
    arcRadius: Float,
    hubRadiusPx: Float,
    normalizedFraction: Float,
    needleColor: Color,
    glowColor: Color
) {
    val norm = normalizedFraction.coerceIn(0f, 1f)
    val angleDeg = 180f + norm * 180f
    val angleRad = angleDeg * (PI.toFloat() / 180f)
    val perpRad = angleRad + (PI.toFloat() / 2f)

    val tipR = arcRadius * 0.82f
    val baseHalfW = (arcRadius * 0.045f).coerceIn(2.6.dp.toPx(), 4.6.dp.toPx())
    val tipHalfW = 0.85.dp.toPx()

    val tipCenter = Offset(
        x = center.x + tipR * cos(angleRad),
        y = center.y + tipR * sin(angleRad)
    )

    val needlePath = Path().apply {
        moveTo(center.x + baseHalfW * cos(perpRad), center.y + baseHalfW * sin(perpRad))
        lineTo(tipCenter.x + tipHalfW * cos(perpRad), tipCenter.y + tipHalfW * sin(perpRad))
        lineTo(tipCenter.x - tipHalfW * cos(perpRad), tipCenter.y - tipHalfW * sin(perpRad))
        lineTo(center.x - baseHalfW * cos(perpRad), center.y - baseHalfW * sin(perpRad))
        close()
    }

    drawLine(
        color = glowColor.copy(alpha = 0.28f),
        start = center,
        end = tipCenter,
        strokeWidth = 5.5.dp.toPx(),
        cap = StrokeCap.Round
    )
    drawPath(
        path = needlePath,
        brush = Brush.linearGradient(
            colors = listOf(needleColor.copy(alpha = 0.88f), needleColor, glowColor),
            start = center,
            end = tipCenter
        )
    )
    drawCircle(
        color = Color(0xFF14171E),
        radius = hubRadiusPx,
        center = center
    )
    drawCircle(
        color = Color(0xFF7B8290),
        radius = hubRadiusPx,
        center = center,
        style = Stroke(width = 1.5.dp.toPx())
    )
}
