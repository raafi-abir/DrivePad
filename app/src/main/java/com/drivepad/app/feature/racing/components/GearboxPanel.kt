package com.drivepad.app.feature.racing.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepad.app.domain.model.TransmissionMode
import com.drivepad.app.feature.racing.AutoGear

private val PillShape = RoundedCornerShape(50)
private val MenuShape = RoundedCornerShape(14.dp)
private val TileShape = RoundedCornerShape(10.dp)
private val CrimsonHighlight = Color(0xFFE51414)
private val CrimsonBorderGlow = Color(0xFFFF2424)

/**
 * Top-right "Gearbox v" trigger pill button.
 */
@Composable
fun GearboxDropdownButton(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(PillShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF171A21), Color(0xFF0A0C10))
                )
            )
            .border(
                width = 1.dp,
                color = if (expanded) CrimsonHighlight else Color(0xFF2D313B),
                shape = PillShape
            )
            .clickable { onToggleExpanded() }
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = "Gearbox mode and gear selector" }
            .testTag("gearbox_dropdown_button"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        HShifterIcon(modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Gearbox",
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            color = Color(0xFFF3F4F6),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = Color(0xFFD1D5DB),
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * In-window Gearbox panel containing [ Auto | Manual ] mode switch and R / N / D gears.
 * Rendered as an in-window Compose overlay (avoiding separate Android PopupWindow transactions).
 */
@Composable
fun GearboxMenuCard(
    transmissionMode: TransmissionMode,
    selectedAutoGear: AutoGear,
    currentManualGear: Int,
    onTransmissionModeChanged: (TransmissionMode) -> Unit,
    onSelectAutoGear: (AutoGear) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val consumeClickInteraction = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .width(228.dp)
            .clickable(
                interactionSource = consumeClickInteraction,
                indication = null,
                onClick = {}
            ),
        shape = MenuShape,
        color = Color(0xFF0D0F14),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2C303A)),
        shadowElevation = 12.dp
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            // Header row inside menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TRANSMISSION",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        letterSpacing = 0.8.sp
                    ),
                    color = Color(0xFF9CA3AF),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (transmissionMode == TransmissionMode.MANUAL) {
                        val label = when {
                            currentManualGear <= -1 -> "R"
                            currentManualGear == 0 -> "N"
                            else -> "$currentManualGear"
                        }
                        "Manual ($label)"
                    } else {
                        "Auto (${selectedAutoGear.label})"
                    },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = CrimsonBorderGlow,
                    fontWeight = FontWeight.Bold
                )
            }

            // [ Auto | Manual ] mode segmented pill
            TransmissionModeSegmentedPill(
                currentMode = transmissionMode,
                onModeSelected = { mode ->
                    onTransmissionModeChanged(mode)
                    if (mode == TransmissionMode.MANUAL) {
                        onDismiss()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            // R | N | D gear selection row inside Gearbox menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(AutoGear.R, AutoGear.N, AutoGear.D).forEach { gear ->
                    val isSelected =
                        transmissionMode == TransmissionMode.AUTO && selectedAutoGear == gear

                    val bgColor by animateColorAsState(
                        targetValue = if (isSelected) Color(0xFF330608) else Color(0xFF14171E),
                        label = "gearBg"
                    )
                    val borderColor by animateColorAsState(
                        targetValue = if (isSelected) CrimsonBorderGlow else Color(0xFF2A2E38),
                        label = "gearBorder"
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(TileShape)
                            .background(bgColor)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = borderColor,
                                shape = TileShape
                            )
                            .clickable {
                                if (transmissionMode != TransmissionMode.AUTO) {
                                    onTransmissionModeChanged(TransmissionMode.AUTO)
                                }
                                onSelectAutoGear(gear)
                                onDismiss()
                            }
                            .testTag("auto_gear_${gear.name}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = gear.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                            color = if (isSelected) Color.White else Color(0xFFD1D5DB),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransmissionModeSegmentedPill(
    currentMode: TransmissionMode,
    onModeSelected: (TransmissionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(Color(0xFF13161D))
            .border(1.dp, Color(0xFF292D36), PillShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        val autoSelected = currentMode == TransmissionMode.AUTO
        val manualSelected = currentMode == TransmissionMode.MANUAL

        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(PillShape)
                .background(
                    if (autoSelected) {
                        Brush.horizontalGradient(listOf(CrimsonHighlight, Color(0xFFB30E0E)))
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .clickable { onModeSelected(TransmissionMode.AUTO) }
                .testTag("transmission_selector_auto"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Auto",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = if (autoSelected) Color.White else Color(0xFF9CA3AF),
                fontWeight = if (autoSelected) FontWeight.Bold else FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .clip(PillShape)
                .background(
                    if (manualSelected) {
                        Brush.horizontalGradient(listOf(CrimsonHighlight, Color(0xFFB30E0E)))
                    } else {
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    }
                )
                .clickable { onModeSelected(TransmissionMode.MANUAL) }
                .testTag("transmission_selector_manual"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Manual",
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                color = if (manualSelected) Color.White else Color(0xFF9CA3AF),
                fontWeight = if (manualSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun HShifterIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 1.35.dp.toPx()
        val ink = Color(0xFFD5DAE3)

        val xLeft = w * 0.20f
        val xMid = w * 0.50f
        val xRight = w * 0.80f
        val yTop = h * 0.16f
        val yMid = h * 0.52f
        val yBottom = h * 0.84f

        // 3 vertical H-gate legs + center crossbar
        drawLine(ink, Offset(xLeft, yTop), Offset(xLeft, yBottom), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(ink, Offset(xMid, yTop), Offset(xMid, yBottom), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(ink, Offset(xRight, yTop), Offset(xRight, yMid), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(ink, Offset(xLeft, yMid), Offset(xRight, yMid), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
