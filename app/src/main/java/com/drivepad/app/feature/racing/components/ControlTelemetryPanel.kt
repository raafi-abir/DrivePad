package com.drivepad.app.feature.racing.components

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
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val TelemetryCardShape = RoundedCornerShape(14.dp)
private val GearBadgeShape = RoundedCornerShape(10.dp)
private val CrimsonSlash = Color(0xFFE51414)
private val CrimsonGlow = Color(0xFFFF2424)
private val RedlineCrimson = Color(0xFFF51818)
private val GaugeWhite = Color(0xFFE5E7EB)

@Composable
fun ControlTelemetryPanel(
    gearLabel: String,
    speedKmh: Float,
    rpm: Float,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 10.dp else 14.dp)
    ) {
        // Speed + Crimson Slash Header + Glowing Red Gear Badge
        SpeedAndGearHeader(
            gearLabel = gearLabel,
            speedText = String.format(Locale.US, "%.2f", speedKmh.coerceAtLeast(0f)),
            isCompact = isCompact,
            modifier = Modifier.fillMaxWidth()
        )

        // Semicircular RPM Tachometer Card matching visual reference
        RpmGaugeCard(
            rpm = rpm,
            isCompact = isCompact,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (isCompact) 152.dp else 188.dp)
        )
    }
}

@Composable
private fun SpeedAndGearHeader(
    gearLabel: String,
    speedText: String,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeBorder by animateColorAsState(
        targetValue = if (gearLabel == "R") CrimsonGlow else CrimsonAccentBorder,
        label = "gearBorder"
    )

    Row(
        modifier = modifier.padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slanted bright crimson accent slash '/'
            Canvas(
                modifier = Modifier
                    .width(if (isCompact) 14.dp else 18.dp)
                    .height(if (isCompact) 44.dp else 54.dp)
            ) {
                drawLine(
                    brush = Brush.verticalGradient(
                        colors = listOf(CrimsonGlow, CrimsonSlash)
                    ),
                    start = Offset(size.width * 0.85f, size.height * 0.08f),
                    end = Offset(size.width * 0.15f, size.height * 0.92f),
                    strokeWidth = 2.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            Spacer(modifier = Modifier.width(if (isCompact) 8.dp else 10.dp))

            Column(verticalArrangement = Arrangement.Center) {
                Text(
                    text = "SPEED",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isCompact) 10.sp else 11.sp,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = 0.8.sp
                    ),
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.SemiBold
                )

                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = speedText,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = if (isCompact) 28.sp else 36.sp,
                            fontStyle = FontStyle.Italic,
                            lineHeight = if (isCompact) 30.sp else 38.sp
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = if (isCompact) 11.sp else 13.sp,
                            fontStyle = FontStyle.Italic
                        ),
                        color = Color(0xFFB0B7C3),
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = if (isCompact) 3.dp else 5.dp),
                        maxLines = 1
                    )
                }
            }
        }

        // Glowing Crimson Square Gear Badge (displays D/R/N/P or 1..6/N/R)
        Box(
            modifier = Modifier
                .size(if (isCompact) 42.dp else 48.dp)
                .drawBehind {
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                CrimsonGlow.copy(alpha = 0.34f),
                                Color.Transparent
                            ),
                            center = center,
                            radius = size.maxDimension * 0.88f
                        ),
                        topLeft = Offset(-4.dp.toPx(), -4.dp.toPx()),
                        size = Size(size.width + 8.dp.toPx(), size.height + 8.dp.toPx()),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                    )
                }
                .clip(GearBadgeShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF280608), Color(0xFF100304))
                    )
                )
                .border(1.6.dp, badgeBorder, GearBadgeShape)
                .testTag("manual_gear_display"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = gearLabel,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = if (isCompact) 20.sp else 24.sp
                ),
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private val CrimsonAccentBorder = Color(0xFFFF2424)

@Composable
private fun RpmGaugeCard(
    rpm: Float,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    val clampedRpm = rpm.coerceIn(0f, 8000f)
    val animatedRpm by animateFloatAsState(
        targetValue = clampedRpm,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "rpmNeedle"
    )
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier
            .clip(TelemetryCardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0E1015), Color(0xFF07080B))
                )
            )
            .border(1.dp, Color(0xFF262A33), TelemetryCardShape)
            .semantics { contentDescription = "RPM tachometer ${clampedRpm.roundToInt()} RPM" }
            .testTag("rpm_meter_card")
    ) {
        val cardW = maxWidth
        val cardH = maxHeight
        val arcRadiusDp = minOf(cardW * 0.42f, cardH * 0.74f)
        val hubRadiusDp = (arcRadiusDp * 0.11f).coerceIn(6.5.dp, 10.dp)
        val centerFromBottomDp = cardH * 0.15f
        val readoutBottomPadding = centerFromBottomDp + hubRadiusDp + 2.dp
        val isTightGauge = arcRadiusDp < 84.dp

        val labelTextStyle = remember(isTightGauge) {
            TextStyle(
                color = Color(0xFFF3F4F6),
                fontSize = if (isTightGauge) 10.5.sp else 13.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        val measuredDigits = remember(textMeasurer, labelTextStyle) {
            (0..8).map { digit ->
                textMeasurer.measure(digit.toString(), style = labelTextStyle)
            }
        }

        // Static cached dial layer: arcs, 33 radial ticks, and pre-measured 0..8 labels
        key(isTightGauge) {
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

                        val whiteSweepDeg = 129.4f
                        val redSweepDeg = 180f - whiteSweepDeg
                        val arcStroke = (arcRadius * 0.025f).coerceIn(1.8.dp.toPx(), 2.8.dp.toPx())

                        drawArc(
                            color = GaugeWhite,
                            startAngle = 180f,
                            sweepAngle = whiteSweepDeg,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = arcStroke, cap = StrokeCap.Butt)
                        )
                        drawArc(
                            color = RedlineCrimson,
                            startAngle = 180f + whiteSweepDeg,
                            sweepAngle = redSweepDeg,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = arcStroke, cap = StrokeCap.Butt)
                        )

                        val totalSteps = 32
                        val majorLen = (arcRadius * 0.105f).coerceIn(6.5.dp.toPx(), 11.dp.toPx())
                        val minorLen = (arcRadius * 0.056f).coerceIn(3.5.dp.toPx(), 6.5.dp.toPx())
                        val majorWidth = (arcRadius * 0.022f).coerceIn(1.5.dp.toPx(), 2.4.dp.toPx())
                        val minorWidth = (arcRadius * 0.011f).coerceIn(0.9.dp.toPx(), 1.3.dp.toPx())

                        for (step in 0..totalSteps) {
                            val frac = step.toFloat() / totalSteps
                            val angleRad = (180f + frac * 180f) * (PI.toFloat() / 180f)
                            val isMajor = step % 4 == 0
                            val isRedZone = frac >= (5.75f / 8.0f)

                            val tickLen = if (isMajor) majorLen else minorLen
                            val innerR = arcRadius - tickLen
                            drawLine(
                                color = if (isRedZone) RedlineCrimson else GaugeWhite,
                                start = Offset(center.x + arcRadius * cos(angleRad), center.y + arcRadius * sin(angleRad)),
                                end = Offset(center.x + innerR * cos(angleRad), center.y + innerR * sin(angleRad)),
                                strokeWidth = if (isMajor) majorWidth else minorWidth,
                                cap = StrokeCap.Butt
                            )
                        }

                        val labelRadius = arcRadius - majorLen - (arcRadius * 0.12f)
                        for (digit in 0..8) {
                            val frac = digit.toFloat() / 8f
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

        // Lightweight animated layer: ambient redline glow + rotating needle + hub only
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        CrimsonGlow.copy(alpha = if (animatedRpm > 5750f) 0.30f else 0.16f),
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
            drawTachometerNeedle(
                center = center,
                arcRadius = arcRadius,
                hubRadiusPx = hubRadiusDp.toPx(),
                rpm = animatedRpm
            )
        }

        // Center digital RPM readout stack ("RPM" / value / "x1000")
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = readoutBottomPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "RPM",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (isTightGauge) 8.5.sp else 10.sp,
                    letterSpacing = 0.7.sp
                ),
                color = Color(0xFFD1D5DB),
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${clampedRpm.roundToInt()}",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontSize = if (isTightGauge) 20.sp else 29.sp,
                    lineHeight = if (isTightGauge) 21.sp else 31.sp
                ),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("rpm_value_display")
            )
            Text(
                text = "x1000",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = if (isTightGauge) 8.sp else 9.5.sp
                ),
                color = Color(0xFF9CA3AF),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun DrawScope.drawTachometerNeedle(
    center: Offset,
    arcRadius: Float,
    hubRadiusPx: Float,
    rpm: Float
) {
    val norm = (rpm / 8000f).coerceIn(0f, 1f)
    val angleDeg = 180f + norm * 180f
    val angleRad = angleDeg * (PI.toFloat() / 180f)
    val perpRad = angleRad + (PI.toFloat() / 2f)

    val tipR = arcRadius * 0.82f
    val baseHalfW = (arcRadius * 0.046f).coerceIn(3.dp.toPx(), 4.8.dp.toPx())
    val tipHalfW = 0.9.dp.toPx()

    val tipCenter = Offset(
        x = center.x + tipR * cos(angleRad),
        y = center.y + tipR * sin(angleRad)
    )

    val needlePath = Path().apply {
        moveTo(
            center.x + baseHalfW * cos(perpRad),
            center.y + baseHalfW * sin(perpRad)
        )
        lineTo(
            tipCenter.x + tipHalfW * cos(perpRad),
            tipCenter.y + tipHalfW * sin(perpRad)
        )
        lineTo(
            tipCenter.x - tipHalfW * cos(perpRad),
            tipCenter.y - tipHalfW * sin(perpRad)
        )
        lineTo(
            center.x - baseHalfW * cos(perpRad),
            center.y - baseHalfW * sin(perpRad)
        )
        close()
    }

    // Soft crimson glow around needle
    drawLine(
        color = CrimsonGlow.copy(alpha = 0.28f),
        start = center,
        end = tipCenter,
        strokeWidth = 6.dp.toPx(),
        cap = StrokeCap.Round
    )

    // Crisp crimson tapered pointer
    drawPath(
        path = needlePath,
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFC81010), RedlineCrimson, Color(0xFFFF3A3A)),
            start = center,
            end = tipCenter
        )
    )

    // Dark center hub with sculpted silver-gray bezel ring
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1A1D24), Color(0xFF090A0D)),
            center = center,
            radius = hubRadiusPx
        ),
        radius = hubRadiusPx,
        center = center
    )
    drawCircle(
        color = Color(0xFF7B8290),
        radius = hubRadiusPx,
        center = center,
        style = Stroke(width = 1.6.dp.toPx())
    )
}
