package com.drivepad.app.feature.racing.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PillShape = RoundedCornerShape(50)
private val CrimsonGlow = Color(0xFFFF2424)
private val CrimsonOutline = Color(0xFFE51414)

/**
 * Sleek obsidian-and-crimson capsule pill button for Shift Down / Shift Up in Manual mode.
 */
@Composable
fun ManualShiftButton(
    label: String,
    isShiftUp: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(PillShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0E1015), Color(0xFF07080B))
                )
            )
            .border(
                width = 1.15.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF2D313B), CrimsonOutline)
                ),
                shape = PillShape
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Canvas(modifier = Modifier.size(13.dp)) {
                val w = size.width
                val h = size.height
                val chevron = Path().apply {
                    if (isShiftUp) {
                        moveTo(w * 0.14f, h * 0.68f)
                        lineTo(w * 0.50f, h * 0.30f)
                        lineTo(w * 0.86f, h * 0.68f)
                    } else {
                        moveTo(w * 0.14f, h * 0.32f)
                        lineTo(w * 0.50f, h * 0.70f)
                        lineTo(w * 0.86f, h * 0.32f)
                    }
                }
                drawPath(
                    path = chevron,
                    color = CrimsonGlow,
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = Color(0xFFF3F4F6),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

/**
 * Digital Press-and-Hold Brake Pedal (Strictly 0.0f or 1.0f).
 * Slanted silver-rimmed parallelogram matching reference image; glows crimson when pressed.
 */
@Composable
fun BrakePedal(
    brakePressure: Float,
    onBrakePressed: () -> Unit,
    onBrakeReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnPressed by rememberUpdatedState(onBrakePressed)
    val currentOnReleased by rememberUpdatedState(onBrakeReleased)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    try {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        currentOnPressed()
                        waitForUpOrCancellation()
                    } finally {
                        currentOnReleased()
                    }
                }
            }
            .semantics { contentDescription = "Brake pedal" }
            .testTag("pedal_brake"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawSlantedPedalPlate(
                        isPressed = brakePressure > 0.5f,
                        isAccelerator = false
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Brake",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = if (brakePressure > 0.5f) CrimsonGlow else Color(0xFFE5E7EB),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Digital Press-and-Hold Accelerator Pedal (Strictly 0.0f or 1.0f).
 * Slanted crimson-glowing parallelogram matching reference image.
 */
@Composable
fun AcceleratorPedal(
    throttlePressure: Float,
    onThrottlePressed: () -> Unit,
    onThrottleReleased: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnPressed by rememberUpdatedState(onThrottlePressed)
    val currentOnReleased by rememberUpdatedState(onThrottleReleased)

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    try {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                        currentOnPressed()
                        waitForUpOrCancellation()
                    } finally {
                        currentOnReleased()
                    }
                }
            }
            .semantics { contentDescription = "Accelerator pedal" }
            .testTag("pedal_accelerator"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawSlantedPedalPlate(
                        isPressed = throttlePressure > 0.5f,
                        isAccelerator = true
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Accelerator",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                color = if (throttlePressure > 0.5f) CrimsonGlow else Color(0xFFE5E7EB),
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

private fun DrawScope.drawSlantedPedalPlate(
    isPressed: Boolean,
    isAccelerator: Boolean
) {
    val pad = 4.dp.toPx()
    val w = (size.width - pad * 2f).coerceAtLeast(10f)
    val h = (size.height - pad * 2f).coerceAtLeast(10f)
    val ox = pad
    val oy = pad

    val slant = w * 0.16f
    val cornerR = (minOf(w, h) * 0.16f).coerceIn(8.dp.toPx(), 16.dp.toPx())

    // Build slanted parallelogram with rounded corners
    val platePath = Path().apply {
        moveTo(ox + slant + cornerR, oy)
        lineTo(ox + w - cornerR, oy)
        cubicTo(
            ox + w, oy,
            ox + w + slant * 0.02f, oy + cornerR,
            ox + w - slant * 0.08f, oy + cornerR
        )
        lineTo(ox + w - slant, oy + h - cornerR)
        cubicTo(
            ox + w - slant - slant * 0.02f, oy + h,
            ox + w - slant - cornerR, oy + h,
            ox + w - slant - cornerR, oy + h
        )
        lineTo(ox + cornerR, oy + h)
        cubicTo(
            ox, oy + h,
            ox - slant * 0.02f, oy + h - cornerR,
            ox + slant * 0.08f, oy + h - cornerR
        )
        lineTo(ox + slant, oy + cornerR)
        cubicTo(
            ox + slant + slant * 0.02f, oy,
            ox + slant + cornerR, oy,
            ox + slant + cornerR, oy
        )
        close()
    }

    // Outer crimson glow halo (always on Accelerator matching reference, active on Brake when pressed)
    if (isAccelerator || isPressed) {
        drawPath(
            path = platePath,
            color = CrimsonGlow.copy(alpha = if (isPressed) 0.38f else 0.22f),
            style = Stroke(width = if (isPressed) 9.dp.toPx() else 6.dp.toPx())
        )
    }

    // Dark obsidian / red-tinted body gradient
    val bodyColors = when {
        isPressed -> listOf(Color(0xFF32080A), Color(0xFF170406))
        isAccelerator -> listOf(Color(0xFF1A1215), Color(0xFF0C090B))
        else -> listOf(Color(0xFF1A1D22), Color(0xFF0D0F13))
    }
    drawPath(
        path = platePath,
        brush = Brush.verticalGradient(colors = bodyColors, startY = oy, endY = oy + h)
    )

    // 5 slanted recessed traction slots parallel to pedal sides
    val slatCount = 5
    val slatWidth = (w * 0.042f).coerceIn(3.dp.toPx(), 5.5.dp.toPx())
    val slotTopY = oy + h * 0.20f
    val slotBotY = oy + h * 0.80f
    val dyFrac = (slotBotY - slotTopY) / h
    val slotSlantDx = slant * dyFrac
    val slotSpanX = w * 0.56f
    val slotStartX = ox + w * 0.22f

    for (i in 0 until slatCount) {
        val topCenterX = slotStartX + slant * 0.35f + (slotSpanX * i / (slatCount - 1))
        val botCenterX = topCenterX - slotSlantDx
        drawLine(
            color = when {
                isPressed -> Color(0xFF5E1B1F)
                isAccelerator -> Color(0xFF282024)
                else -> Color(0xFF272B33)
            },
            start = Offset(topCenterX, slotTopY),
            end = Offset(botCenterX, slotBotY),
            strokeWidth = slatWidth,
            cap = StrokeCap.Round
        )
    }

    // Outer bevel stroke
    when {
        isAccelerator || isPressed -> {
            drawPath(
                path = platePath,
                brush = Brush.verticalGradient(
                    colors = listOf(CrimsonGlow, CrimsonOutline, Color(0xFFB30E0E)),
                    startY = oy,
                    endY = oy + h
                ),
                style = Stroke(width = if (isPressed) 2.5.dp.toPx() else 1.8.dp.toPx())
            )
        }
        else -> {
            drawPath(
                path = platePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF9EA5B0),
                        Color(0xFF4F5560),
                        Color(0xFF2D313A)
                    ),
                    startY = oy,
                    endY = oy + h
                ),
                style = Stroke(width = 1.4.dp.toPx())
            )
        }
    }
}
