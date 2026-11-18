package com.drivepad.app.feature.racing.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val CrimsonAccent = Color(0xFFE51414)
private val CrimsonGlow = Color(0xFFFF2424)

@Composable
fun SteeringWheel(
    angleDegrees: Float,
    normalizedValue: Float,
    isInteracting: Boolean,
    onAngleChange: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
    isTouchEnabled: Boolean = true
) {
    val animatedAngle by animateFloatAsState(
        targetValue = angleDegrees,
        animationSpec = spring(dampingRatio = 0.78f, stiffness = 420f),
        label = "wheelRotation"
    )

    BoxWithConstraints(
        modifier = modifier.semantics {
            contentDescription = "Interactive racing steering wheel"
        },
        contentAlignment = Alignment.Center
    ) {
        val wheelSize = minOf(maxWidth, maxHeight)
        Box(
            modifier = Modifier
                .size(wheelSize)
                .testTag("steering_wheel_container"),
            contentAlignment = Alignment.Center
        ) {
            WheelCanvas(
                rotationProvider = { animatedAngle },
                isTouchEnabled = isTouchEnabled,
                onAngleChange = onAngleChange,
                onRelease = onRelease,
                modifier = Modifier
                    .fillMaxSize(0.95f)
                    .testTag("steering_wheel_canvas")
            )
        }
    }
}

@Composable
private fun WheelCanvas(
    rotationProvider: () -> Float,
    isTouchEnabled: Boolean,
    onAngleChange: (Float) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier
) {
    var previousTouchAngle by remember { mutableFloatStateOf(0f) }
    val currentOnAngleChange by rememberUpdatedState(onAngleChange)
    val currentOnRelease by rememberUpdatedState(onRelease)

    val touchModifier = if (isTouchEnabled) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragStart = { offset ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    previousTouchAngle = atan2(
                        offset.y - center.y,
                        offset.x - center.x
                    ) * (180f / PI.toFloat())
                },
                onDrag = { change, _ ->
                    change.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val currentTouchAngle = atan2(
                        change.position.y - center.y,
                        change.position.x - center.x
                    ) * (180f / PI.toFloat())

                    var delta = currentTouchAngle - previousTouchAngle
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f

                    currentOnAngleChange(delta)
                    previousTouchAngle = currentTouchAngle
                },
                onDragEnd = { currentOnRelease() },
                onDragCancel = { currentOnRelease() }
            )
        }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .then(touchModifier)
            .graphicsLayer {
                rotationZ = rotationProvider()
            }
            .drawWithCache {
                onDrawBehind {
                    if (size.width <= 0f || size.height <= 0f) return@onDrawBehind
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val outerRadius = minOf(size.width, size.height) * 0.485f

                    drawWheelRim(center, outerRadius)
                    drawInnerCrimsonPiping(center, outerRadius)
                    drawBottomYSpoke(center, outerRadius)
                    drawHorizontalSpokePods(center, outerRadius)
                    drawCenterHub(center, outerRadius)
                    drawCenterEmblem(center, outerRadius)
                    drawTopCenteringStripe(center, outerRadius)
                }
            }
    )
}

private fun DrawScope.drawWheelRim(center: Offset, r: Float) {
    val rimThickness = r * 0.235f
    val midRadius = r - rimThickness / 2f

    // Subtle dark drop shadow behind wheel
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
            center = center,
            radius = r * 1.07f
        ),
        radius = r * 1.05f,
        center = center
    )

    // Base leather rim with sculpted 3D shading
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF2B2F37),
                Color(0xFF14161B),
                Color(0xFF0B0C0F)
            ),
            startY = center.y - r,
            endY = center.y + r
        ),
        radius = midRadius,
        center = center,
        style = Stroke(width = rimThickness)
    )

    // Outer specular bezel sheen
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF545B68).copy(alpha = 0.72f),
                Color(0xFF1C2026).copy(alpha = 0.45f),
                Color(0xFF393F4A).copy(alpha = 0.55f)
            ),
            start = Offset(center.x - r, center.y - r),
            end = Offset(center.x + r, center.y + r)
        ),
        radius = r - 1.dp.toPx(),
        center = center,
        style = Stroke(width = 2.dp.toPx())
    )

    // Inner contoured rim highlight bevel
    drawCircle(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF3E444F).copy(alpha = 0.8f),
                Color(0xFF111317),
                Color(0xFF2A2F38).copy(alpha = 0.7f)
            ),
            startY = center.y - midRadius,
            endY = center.y + midRadius
        ),
        radius = midRadius,
        center = center,
        style = Stroke(width = rimThickness * 0.56f)
    )

    // Perforated grip texture along left (135°..225°) and right (-45°..+45°) lateral grips
    val gripDotRadius = (r * 0.011f).coerceAtLeast(1.1f)
    val rowOffsets = listOf(-0.22f, -0.07f, 0.08f, 0.23f)
    val lateralZones = listOf(
        136f..224f,
        -44f..44f
    )
    for (zone in lateralZones) {
        var deg = zone.start
        var stepIdx = 0
        while (deg <= zone.endInclusive) {
            val stagger = if (stepIdx % 2 == 0) 0f else 1.8f
            for ((rowIdx, radialFrac) in rowOffsets.withIndex()) {
                val dotAngleRad = (deg + (if (rowIdx % 2 == 0) 0f else stagger)) * (PI.toFloat() / 180f)
                val dotRadius = midRadius + rimThickness * radialFrac
                val dx = center.x + dotRadius * cos(dotAngleRad)
                val dy = center.y + dotRadius * sin(dotAngleRad)
                drawCircle(
                    color = Color(0xFF07080A).copy(alpha = 0.78f),
                    radius = gripDotRadius,
                    center = Offset(dx, dy)
                )
            }
            deg += 4.2f
            stepIdx++
        }
    }

    // Inner rim shadow ring
    drawCircle(
        color = Color(0xFF07080B),
        radius = r - rimThickness + 1.dp.toPx(),
        center = center,
        style = Stroke(width = 2.5.dp.toPx())
    )
}

private fun DrawScope.drawInnerCrimsonPiping(center: Offset, r: Float) {
    val rimThickness = r * 0.235f
    val innerRimRadius = r - rimThickness + 1.5.dp.toPx()
    val arcTopLeft = Offset(center.x - innerRimRadius, center.y - innerRimRadius)
    val arcSize = Size(innerRimRadius * 2f, innerRimRadius * 2f)
    val pipingStroke = (r * 0.020f).coerceAtLeast(2.dp.toPx())

    // 4 inner piping arcs peeking cleanly along upper/lower quadrants like reference
    val pipingArcs = listOf(
        186f to 36f,   // Upper-left inner quadrant
        318f to 36f,   // Upper-right inner quadrant
        118f to 42f,   // Lower-left inner quadrant
        20f to 42f     // Lower-right inner quadrant
    )
    for ((startAngle, sweepAngle) in pipingArcs) {
        drawArc(
            color = CrimsonAccent.copy(alpha = 0.92f),
            startAngle = startAngle,
            sweepAngle = sweepAngle,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = pipingStroke, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawTopCenteringStripe(center: Offset, r: Float) {
    val rimThickness = r * 0.235f
    val stripeWidth = (r * 0.105f).coerceAtLeast(10.dp.toPx())
    val stripeTop = center.y - r + 0.5.dp.toPx()

    // Glowing red 12 o'clock centering stripe
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFF2B2B),
                CrimsonAccent,
                Color(0xFFB30E0E)
            ),
            startY = stripeTop,
            endY = stripeTop + rimThickness
        ),
        topLeft = Offset(center.x - stripeWidth / 2f, stripeTop),
        size = Size(stripeWidth, rimThickness - 1.dp.toPx()),
        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
    )

    // Seam framing lines on left/right of stripe
    drawLine(
        color = Color(0xFF0A0B0D),
        start = Offset(center.x - stripeWidth / 2f, stripeTop),
        end = Offset(center.x - stripeWidth / 2f, stripeTop + rimThickness - 1.dp.toPx()),
        strokeWidth = 1.2.dp.toPx()
    )
    drawLine(
        color = Color(0xFF0A0B0D),
        start = Offset(center.x + stripeWidth / 2f, stripeTop),
        end = Offset(center.x + stripeWidth / 2f, stripeTop + rimThickness - 1.dp.toPx()),
        strokeWidth = 1.2.dp.toPx()
    )
}

private fun DrawScope.drawHorizontalSpokePods(center: Offset, r: Float) {
    val hubRadius = r * 0.39f
    val innerR = r * 0.775f

    // Left spoke pod housing
    val leftPod = Path().apply {
        moveTo(center.x - hubRadius * 0.85f, center.y - r * 0.18f)
        lineTo(center.x - innerR, center.y - r * 0.14f)
        quadraticTo(
            center.x - innerR - r * 0.04f, center.y,
            center.x - innerR, center.y + r * 0.14f
        )
        lineTo(center.x - hubRadius * 0.85f, center.y + r * 0.18f)
        close()
    }
    drawPath(
        path = leftPod,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF232730), Color(0xFF14161B), Color(0xFF0D0F13)),
            startY = center.y - r * 0.18f,
            endY = center.y + r * 0.18f
        )
    )
    drawPath(leftPod, Color(0xFF343A46), style = Stroke(width = 1.2.dp.toPx()))

    // Right spoke pod housing
    val rightPod = Path().apply {
        moveTo(center.x + hubRadius * 0.85f, center.y - r * 0.18f)
        lineTo(center.x + innerR, center.y - r * 0.14f)
        quadraticTo(
            center.x + innerR + r * 0.04f, center.y,
            center.x + innerR, center.y + r * 0.14f
        )
        lineTo(center.x + hubRadius * 0.85f, center.y + r * 0.18f)
        close()
    }
    drawPath(
        path = rightPod,
        brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF232730), Color(0xFF14161B), Color(0xFF0D0F13)),
            startY = center.y - r * 0.18f,
            endY = center.y + r * 0.18f
        )
    )
    drawPath(rightPod, Color(0xFF343A46), style = Stroke(width = 1.2.dp.toPx()))

    // Crisp white '<' chevron on Left spoke pod
    val chevronCenterXOffset = r * 0.57f
    val chevH = r * 0.068f
    val chevW = r * 0.045f
    val chevStroke = (r * 0.018f).coerceAtLeast(1.8.dp.toPx())

    val leftChev = Path().apply {
        val cx = center.x - chevronCenterXOffset
        moveTo(cx + chevW * 0.5f, center.y - chevH)
        lineTo(cx - chevW * 0.5f, center.y)
        lineTo(cx + chevW * 0.5f, center.y + chevH)
    }
    drawPath(
        path = leftChev,
        color = Color(0xFFF3F4F6),
        style = Stroke(width = chevStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    // Crisp white '>' chevron on Right spoke pod
    val rightChev = Path().apply {
        val cx = center.x + chevronCenterXOffset
        moveTo(cx - chevW * 0.5f, center.y - chevH)
        lineTo(cx + chevW * 0.5f, center.y)
        lineTo(cx - chevW * 0.5f, center.y + chevH)
    }
    drawPath(
        path = rightChev,
        color = Color(0xFFF3F4F6),
        style = Stroke(width = chevStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawBottomYSpoke(center: Offset, r: Float) {
    val topY = center.y + r * 0.30f
    val bottomY = center.y + r * 0.785f

    // Brushed metallic trapezoid Y-strut
    val outerStrut = Path().apply {
        moveTo(center.x - r * 0.255f, topY)
        lineTo(center.x - r * 0.135f, bottomY)
        lineTo(center.x + r * 0.135f, bottomY)
        lineTo(center.x + r * 0.255f, topY)
        close()
    }
    drawPath(
        path = outerStrut,
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF1A1D24),
                Color(0xFF39404D),
                Color(0xFF282D36)
            ),
            startY = topY,
            endY = bottomY
        )
    )
    drawPath(outerStrut, Color(0xFF4C5464).copy(alpha = 0.7f), style = Stroke(width = 1.2.dp.toPx()))

    // Hollow center cutout inside bottom strut
    val innerCutout = Path().apply {
        val cTopY = topY + r * 0.11f
        val cBottomY = bottomY - r * 0.08f
        moveTo(center.x - r * 0.11f, cTopY)
        lineTo(center.x - r * 0.068f, cBottomY)
        lineTo(center.x + r * 0.068f, cBottomY)
        lineTo(center.x + r * 0.11f, cTopY)
        close()
    }
    drawPath(
        path = innerCutout,
        color = Color(0xFF07080A)
    )
    drawPath(innerCutout, Color(0xFF566071).copy(alpha = 0.5f), style = Stroke(width = 1.dp.toPx()))
}

private fun DrawScope.drawCenterHub(center: Offset, r: Float) {
    val hubRadius = r * 0.405f

    // Subtle outer ambient shadow ring
    drawCircle(
        color = Color.Black.copy(alpha = 0.7f),
        radius = hubRadius + 3.dp.toPx(),
        center = center
    )

    // Metallic sculpted outer bezel
    drawCircle(
        brush = Brush.linearGradient(
            colors = listOf(
                Color(0xFF474F5E),
                Color(0xFF1F242C),
                Color(0xFF111318),
                Color(0xFF323844)
            ),
            start = Offset(center.x - hubRadius, center.y - hubRadius),
            end = Offset(center.x + hubRadius, center.y + hubRadius)
        ),
        radius = hubRadius,
        center = center
    )
    drawCircle(
        color = Color(0xFF586173).copy(alpha = 0.6f),
        radius = hubRadius,
        center = center,
        style = Stroke(width = 1.5.dp.toPx())
    )

    // Matte obsidian inner airbag cap
    val innerHubRadius = hubRadius * 0.84f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF13151A), Color(0xFF08090C)),
            center = Offset(center.x - innerHubRadius * 0.15f, center.y - innerHubRadius * 0.15f),
            radius = innerHubRadius * 1.1f
        ),
        radius = innerHubRadius,
        center = center
    )
    drawCircle(
        color = Color(0xFF272C36),
        radius = innerHubRadius,
        center = center,
        style = Stroke(width = 1.2.dp.toPx())
    )
}

// Crimson slanted double-ribbon aerodynamic emblem centered on the hub
private fun DrawScope.drawCenterEmblem(center: Offset, r: Float) {
    val s = r * 0.17f

    // Soft crimson ambient glow behind logo
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(CrimsonGlow.copy(alpha = 0.25f), Color.Transparent),
            center = center,
            radius = s * 1.45f
        ),
        radius = s * 1.45f,
        center = center
    )

    // Upper sweeping crimson wing ribbon
    val upperRibbon = Path().apply {
        moveTo(center.x - s * 0.65f, center.y + s * 0.12f)
        cubicTo(
            center.x - s * 0.42f, center.y - s * 0.72f,
            center.x + s * 0.28f, center.y - s * 0.64f,
            center.x + s * 0.92f, center.y - s * 0.72f
        )
        cubicTo(
            center.x + s * 0.56f, center.y - s * 0.22f,
            center.x + s * 0.02f, center.y - s * 0.26f,
            center.x - s * 0.34f, center.y + s * 0.16f
        )
        close()
    }

    // Lower matching crimson wing ribbon
    val lowerRibbon = Path().apply {
        moveTo(center.x - s * 0.85f, center.y + s * 0.82f)
        cubicTo(
            center.x - s * 0.52f, center.y + s * 0.04f,
            center.x + s * 0.10f, center.y + s * 0.02f,
            center.x + s * 0.62f, center.y - s * 0.04f
        )
        cubicTo(
            center.x + s * 0.34f, center.y + s * 0.40f,
            center.x - s * 0.12f, center.y + s * 0.34f,
            center.x - s * 0.52f, center.y + s * 0.86f
        )
        close()
    }

    val logoBrush = Brush.linearGradient(
        colors = listOf(CrimsonGlow, CrimsonAccent, Color(0xFFB30E0E)),
        start = Offset(center.x - s, center.y - s),
        end = Offset(center.x + s, center.y + s)
    )
    drawPath(upperRibbon, logoBrush)
    drawPath(lowerRibbon, logoBrush)
}
