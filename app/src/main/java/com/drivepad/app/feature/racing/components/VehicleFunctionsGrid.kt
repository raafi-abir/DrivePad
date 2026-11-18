package com.drivepad.app.feature.racing.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepad.app.feature.racing.VehicleFunctionsState

private val CapsuleShape = RoundedCornerShape(50)
private val ControlsCardShape = RoundedCornerShape(16.dp)
private val TileShape = RoundedCornerShape(10.dp)
private val CrimsonAccent = Color(0xFFE51414)
private val CrimsonGlow = Color(0xFFFF2424)
private val EmeraldGlow = Color(0xFF00E676)

/**
 * Top-right triple indicator capsule: [ Left Arrow | Hazard | Right Arrow ].
 */
@Composable
fun IndicatorCapsuleRow(
    state: VehicleFunctionsState,
    blinkOn: Boolean,
    onToggleLeftIndicator: () -> Unit,
    onToggleHazard: () -> Unit,
    onToggleRightIndicator: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val pillHeight = if (isCompact) 38.dp else 44.dp
    val leftActive = (state.leftIndicatorActive || state.hazardActive) && blinkOn
    val rightActive = (state.rightIndicatorActive || state.hazardActive) && blinkOn
    val hazardGlowActive = state.hazardActive && blinkOn

    Row(
        modifier = modifier
            .clip(CapsuleShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0C0E12), Color(0xFF07080B))
                )
            )
            .border(1.dp, Color(0xFF282C35), CapsuleShape)
            .padding(horizontal = if (isCompact) 8.dp else 10.dp, vertical = if (isCompact) 6.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Turn Indicator Pill
        val leftBorder by animateColorAsState(
            targetValue = if (leftActive) EmeraldGlow else Color(0xFF2E333E),
            label = "leftIndBorder"
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(pillHeight)
                .clip(CapsuleShape)
                .background(if (leftActive) Color(0xFF0B2416) else Color(0xFF101319))
                .border(1.dp, leftBorder, CapsuleShape)
                .clickable { onToggleLeftIndicator() }
                .semantics { contentDescription = "Left indicator" }
                .testTag("btn_function_indicator_left"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)) {
                drawThickArrow(
                    pointingLeft = true,
                    color = if (leftActive) EmeraldGlow else Color.White
                )
            }
        }

        // Center Hazard Triangle Pill (signature crimson glow matching reference)
        val hazardBorder by animateColorAsState(
            targetValue = when {
                hazardGlowActive -> CrimsonGlow
                state.hazardActive -> Color(0xFF6B0C0C)
                else -> CrimsonAccent
            },
            label = "hazardBorder"
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(pillHeight)
                .drawBehind {
                    val alpha = when {
                        hazardGlowActive -> 0.42f
                        state.hazardActive -> 0.10f
                        else -> 0.25f
                    }
                    drawRoundRect(
                        brush = Brush.radialGradient(
                            colors = listOf(CrimsonGlow.copy(alpha = alpha), Color.Transparent),
                            center = center,
                            radius = size.maxDimension * 0.72f
                        ),
                        topLeft = Offset(-3.dp.toPx(), -3.dp.toPx()),
                        size = Size(size.width + 6.dp.toPx(), size.height + 6.dp.toPx()),
                        cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                    )
                }
                .clip(CapsuleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            if (hazardGlowActive) Color(0xFF5A090B) else Color(0xFF340507),
                            Color(0xFF140305)
                        )
                    )
                )
                .border(if (hazardGlowActive) 1.6.dp else 1.2.dp, hazardBorder, CapsuleShape)
                .clickable { onToggleHazard() }
                .semantics { contentDescription = "Hazard lights" }
                .testTag("btn_function_hazard"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(if (isCompact) 19.dp else 21.dp)) {
                drawDoubleHazardTriangle(color = Color.White)
            }
        }

        // Right Turn Indicator Pill
        val rightBorder by animateColorAsState(
            targetValue = if (rightActive) EmeraldGlow else Color(0xFF2E333E),
            label = "rightIndBorder"
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(pillHeight)
                .clip(CapsuleShape)
                .background(if (rightActive) Color(0xFF0B2416) else Color(0xFF101319))
                .border(1.dp, rightBorder, CapsuleShape)
                .clickable { onToggleRightIndicator() }
                .semantics { contentDescription = "Right indicator" }
                .testTag("btn_function_indicator_right"),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(if (isCompact) 18.dp else 20.dp)) {
                drawThickArrow(
                    pointingLeft = false,
                    color = if (rightActive) EmeraldGlow else Color.White
                )
            }
        }
    }
}

/**
 * Rounded "Controls" card holding 5 function tiles:
 * Lights | Horn (press-and-hold) | Wipers | Engine | Handbrake
 */
@Composable
fun VehicleFunctionsGrid(
    state: VehicleFunctionsState,
    handbrakeEngaged: Boolean,
    onToggleLights: () -> Unit,
    onHornPressChanged: (Boolean) -> Unit,
    onToggleWipers: () -> Unit,
    onToggleEngine: () -> Unit,
    onToggleHandbrake: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val tileHeight = if (isCompact) 42.dp else 48.dp
    val tileSpacing = if (isCompact) 5.dp else 8.dp
    val iconSize = if (isCompact) 18.dp else 21.dp

    Column(
        modifier = modifier
            .clip(ControlsCardShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0E1015), Color(0xFF080A0D))
                )
            )
            .border(1.dp, Color(0xFF262A33), ControlsCardShape)
            .padding(
                horizontal = if (isCompact) 10.dp else 14.dp,
                vertical = if (isCompact) 8.dp else 12.dp
            )
    ) {
        Text(
            text = "Controls",
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = if (isCompact) 13.sp else 15.sp
            ),
            color = Color(0xFFF3F4F6),
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(if (isCompact) 7.dp else 10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(tileSpacing)
        ) {
            // 1. Lights
            ControlIconTile(
                isActive = state.lightsOn,
                activeAccent = CrimsonGlow,
                onClick = onToggleLights,
                description = "Headlights",
                testTag = "btn_function_lights",
                modifier = Modifier
                    .weight(1f)
                    .height(tileHeight)
            ) { tint ->
                Canvas(modifier = Modifier.size(iconSize)) { drawHeadlightIcon(tint) }
            }

            // 2. Momentary Horn
            MomentaryControlIconTile(
                onPressChange = onHornPressChanged,
                description = "Horn",
                testTag = "btn_function_horn",
                modifier = Modifier
                    .weight(1f)
                    .height(tileHeight)
            ) { tint ->
                Canvas(modifier = Modifier.size(iconSize)) { drawBugleHornIcon(tint) }
            }

            // 3. Wipers
            ControlIconTile(
                isActive = state.wipersActive,
                activeAccent = CrimsonGlow,
                onClick = onToggleWipers,
                description = "Windshield wipers",
                testTag = "btn_function_wipers",
                modifier = Modifier
                    .weight(1f)
                    .height(tileHeight)
            ) { tint ->
                Canvas(modifier = Modifier.size(iconSize)) { drawWiperIcon(tint) }
            }

            // 4. Engine start/stop
            ControlIconTile(
                isActive = state.engineRunning,
                activeAccent = CrimsonGlow,
                onClick = onToggleEngine,
                description = "Engine toggle",
                testTag = "btn_function_engine",
                modifier = Modifier
                    .weight(1f)
                    .height(tileHeight)
            ) { tint ->
                Canvas(modifier = Modifier.size(iconSize)) { drawEngineIcon(tint) }
            }

            // 5. Handbrake tile (white when inactive, glowing crimson when engaged)
            val hbBorder by animateColorAsState(
                targetValue = if (handbrakeEngaged) CrimsonGlow else Color(0xFF2B303A),
                label = "hbTileBorder"
            )
            val hbBg by animateColorAsState(
                targetValue = if (handbrakeEngaged) Color(0xFF420709) else Color(0xFF101319),
                label = "hbTileBg"
            )
            val hbTint by animateColorAsState(
                targetValue = if (handbrakeEngaged) CrimsonGlow else Color(0xFFE5E7EB),
                label = "hbTileTint"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(tileHeight)
                    .clip(TileShape)
                    .background(hbBg)
                    .border(if (handbrakeEngaged) 1.5.dp else 1.dp, hbBorder, TileShape)
                    .clickable { onToggleHandbrake() }
                    .semantics { contentDescription = "Handbrake" }
                    .testTag("btn_handbrake"),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(iconSize)) {
                    drawHandbrakeLeverIcon(hbTint)
                }
            }
        }
    }
}

@Composable
private fun ControlIconTile(
    isActive: Boolean,
    activeAccent: Color,
    onClick: () -> Unit,
    description: String,
    testTag: String,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) activeAccent.copy(alpha = 0.16f) else Color(0xFF101319),
        label = "ctrlBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) activeAccent else Color(0xFF2B303A),
        label = "ctrlBorder"
    )
    val tintColor by animateColorAsState(
        targetValue = if (isActive) activeAccent else Color(0xFFE5E7EB),
        label = "ctrlTint"
    )

    Box(
        modifier = modifier
            .clip(TileShape)
            .background(bgColor)
            .border(if (isActive) 1.5.dp else 1.dp, borderColor, TileShape)
            .clickable { onClick() }
            .semantics { contentDescription = description }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        icon(tintColor)
    }
}

@Composable
private fun MomentaryControlIconTile(
    onPressChange: (Boolean) -> Unit,
    description: String,
    testTag: String,
    modifier: Modifier = Modifier,
    icon: @Composable (Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    LaunchedEffect(isPressed) {
        onPressChange(isPressed)
    }

    val bgColor by animateColorAsState(
        targetValue = if (isPressed) CrimsonAccent.copy(alpha = 0.25f) else Color(0xFF101319),
        label = "hornBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isPressed) CrimsonGlow else Color(0xFF2B303A),
        label = "hornBorder"
    )
    val tintColor by animateColorAsState(
        targetValue = if (isPressed) CrimsonGlow else Color(0xFFE5E7EB),
        label = "hornTint"
    )

    Box(
        modifier = modifier
            .clip(TileShape)
            .background(bgColor)
            .border(if (isPressed) 1.5.dp else 1.dp, borderColor, TileShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {}
            )
            .semantics { contentDescription = description }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        icon(tintColor)
    }
}

// --- Custom Crisp Vector Icons matching visual references ---

private fun DrawScope.drawThickArrow(pointingLeft: Boolean, color: Color) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        if (pointingLeft) {
            moveTo(w * 0.84f, h * 0.38f)
            lineTo(w * 0.44f, h * 0.38f)
            lineTo(w * 0.44f, h * 0.20f)
            lineTo(w * 0.14f, h * 0.50f)
            lineTo(w * 0.44f, h * 0.80f)
            lineTo(w * 0.44f, h * 0.62f)
            lineTo(w * 0.84f, h * 0.62f)
            close()
        } else {
            moveTo(w * 0.16f, h * 0.38f)
            lineTo(w * 0.56f, h * 0.38f)
            lineTo(w * 0.56f, h * 0.20f)
            lineTo(w * 0.86f, h * 0.50f)
            lineTo(w * 0.56f, h * 0.80f)
            lineTo(w * 0.56f, h * 0.62f)
            lineTo(w * 0.16f, h * 0.62f)
            close()
        }
    }
    drawPath(path, color)
}

private fun DrawScope.drawDoubleHazardTriangle(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = 1.4.dp.toPx()

    val outer = Path().apply {
        moveTo(w * 0.50f, h * 0.14f)
        lineTo(w * 0.88f, h * 0.82f)
        lineTo(w * 0.12f, h * 0.82f)
        close()
    }
    val inner = Path().apply {
        moveTo(w * 0.50f, h * 0.34f)
        lineTo(w * 0.72f, h * 0.73f)
        lineTo(w * 0.28f, h * 0.73f)
        close()
    }
    drawPath(outer, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
    drawPath(inner, color, style = Stroke(width = stroke * 0.85f, join = StrokeJoin.Round))
}

private fun DrawScope.drawHeadlightIcon(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = 1.35.dp.toPx()

    // 3 beam rays on left
    drawLine(color, Offset(w * 0.10f, h * 0.30f), Offset(w * 0.36f, h * 0.30f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.10f, h * 0.50f), Offset(w * 0.36f, h * 0.50f), stroke, StrokeCap.Round)
    drawLine(color, Offset(w * 0.10f, h * 0.70f), Offset(w * 0.36f, h * 0.70f), stroke, StrokeCap.Round)

    // Lamp dome on right
    val dome = Path().apply {
        moveTo(w * 0.50f, h * 0.24f)
        lineTo(w * 0.50f, h * 0.76f)
        cubicTo(
            w * 0.92f, h * 0.76f,
            w * 0.92f, h * 0.24f,
            w * 0.50f, h * 0.24f
        )
        close()
    }
    drawPath(dome, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
}

private fun DrawScope.drawBugleHornIcon(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = 1.35.dp.toPx()

    val bugle = Path().apply {
        moveTo(w * 0.14f, h * 0.42f)
        lineTo(w * 0.54f, h * 0.42f)
        lineTo(w * 0.84f, h * 0.22f)
        lineTo(w * 0.84f, h * 0.72f)
        lineTo(w * 0.54f, h * 0.52f)
        lineTo(w * 0.14f, h * 0.52f)
        close()
    }
    drawPath(bugle, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
    drawArc(
        color = color,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.30f, h * 0.48f),
        size = Size(w * 0.24f, h * 0.26f),
        style = Stroke(width = stroke, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawWiperIcon(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = 1.35.dp.toPx()

    val fan = Path().apply {
        moveTo(w * 0.12f, h * 0.34f)
        quadraticTo(w * 0.50f, h * 0.12f, w * 0.88f, h * 0.34f)
        lineTo(w * 0.68f, h * 0.76f)
        quadraticTo(w * 0.50f, h * 0.66f, w * 0.32f, h * 0.76f)
        close()
    }
    drawPath(fan, color, style = Stroke(width = stroke, join = StrokeJoin.Round))

    // Wiper blade arm
    drawLine(
        color = color,
        start = Offset(w * 0.50f, h * 0.74f),
        end = Offset(w * 0.66f, h * 0.30f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawCircle(color = color, radius = 1.8.dp.toPx(), center = Offset(w * 0.50f, h * 0.74f))
}

private fun DrawScope.drawEngineIcon(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = 1.35.dp.toPx()

    // Top intake cap (T-bar)
    drawLine(
        color = color,
        start = Offset(w * 0.34f, h * 0.22f),
        end = Offset(w * 0.62f, h * 0.22f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.48f, h * 0.22f),
        end = Offset(w * 0.48f, h * 0.34f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )

    // Left front pulley / fan bar
    drawLine(
        color = color,
        start = Offset(w * 0.12f, h * 0.40f),
        end = Offset(w * 0.12f, h * 0.72f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = color,
        start = Offset(w * 0.12f, h * 0.56f),
        end = Offset(w * 0.22f, h * 0.56f),
        strokeWidth = stroke,
        cap = StrokeCap.Round
    )

    // Engine block housing and rear flange
    val block = Path().apply {
        moveTo(w * 0.22f, h * 0.38f)
        lineTo(w * 0.30f, h * 0.34f)
        lineTo(w * 0.64f, h * 0.34f)
        lineTo(w * 0.70f, h * 0.44f)
        lineTo(w * 0.80f, h * 0.44f)
        lineTo(w * 0.80f, h * 0.38f)
        lineTo(w * 0.88f, h * 0.38f)
        lineTo(w * 0.88f, h * 0.76f)
        lineTo(w * 0.80f, h * 0.76f)
        lineTo(w * 0.80f, h * 0.70f)
        lineTo(w * 0.68f, h * 0.70f)
        lineTo(w * 0.62f, h * 0.78f)
        lineTo(w * 0.34f, h * 0.78f)
        lineTo(w * 0.28f, h * 0.68f)
        lineTo(w * 0.22f, h * 0.68f)
        close()
    }
    drawPath(block, color, style = Stroke(width = stroke, join = StrokeJoin.Round))
}

private fun DrawScope.drawHandbrakeLeverIcon(color: Color) {
    val w = size.width
    val h = size.height
    val stroke = 1.8.dp.toPx()

    // Base line
    drawLine(
        color = color,
        start = Offset(w * 0.16f, h * 0.80f),
        end = Offset(w * 0.82f, h * 0.80f),
        strokeWidth = 1.3.dp.toPx(),
        cap = StrokeCap.Round
    )
    // Slanted lever grip
    drawLine(
        color = color,
        start = Offset(w * 0.34f, h * 0.74f),
        end = Offset(w * 0.76f, h * 0.24f),
        strokeWidth = stroke * 1.35f,
        cap = StrokeCap.Round
    )
    drawCircle(
        color = color,
        radius = 2.dp.toPx(),
        center = Offset(w * 0.34f, h * 0.74f)
    )
}
