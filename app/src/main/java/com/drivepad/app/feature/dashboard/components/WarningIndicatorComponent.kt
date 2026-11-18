package com.drivepad.app.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.drivepad.app.core.designsystem.DashboardPalette
import com.drivepad.app.domain.model.IndicatorActivationState
import com.drivepad.app.domain.model.IndicatorSeverity
import com.drivepad.app.domain.model.WarningIndicatorItem

private val IndicatorPillShape = RoundedCornerShape(7.dp)

/**
 * Renders warning and informational vehicle indicators cleanly without unnecessary flashing.
 * Clearly distinguishes ACTIVE, INACTIVE, and UNAVAILABLE states and separates warning
 * severity styling from informational status badges.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WarningIndicatorStrip(
    indicators: List<WarningIndicatorItem>,
    palette: DashboardPalette,
    isCompact: Boolean,
    modifier: Modifier = Modifier
) {
    if (indicators.isEmpty()) return

    FlowRow(
        modifier = modifier.testTag("dashboard_warning_strip"),
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 5.dp)
    ) {
        indicators.forEach { item ->
            WarningIndicatorChip(
                item = item,
                palette = palette,
                isCompact = isCompact
            )
        }
    }
}

@Composable
private fun WarningIndicatorChip(
    item: WarningIndicatorItem,
    palette: DashboardPalette,
    isCompact: Boolean
) {
    val activeAccent = when (item.severity) {
        IndicatorSeverity.WARNING_CRITICAL -> palette.warningCritical
        IndicatorSeverity.WARNING_CAUTION -> palette.warningCaution
        IndicatorSeverity.INFO -> palette.infoActive
        IndicatorSeverity.UNAVAILABLE -> palette.unavailableTint
    }

    val (bgColor, borderColor, textColor, alpha) = when (item.activationState) {
        IndicatorActivationState.ACTIVE -> Quadruple(
            activeAccent.copy(alpha = if (palette.isDark) 0.22f else 0.14f),
            activeAccent,
            if (palette.isDark) Color.White else activeAccent,
            1.0f
        )
        IndicatorActivationState.INACTIVE -> Quadruple(
            palette.cardBottom.copy(alpha = 0.7f),
            palette.cardBorder,
            palette.textSecondary,
            0.78f
        )
        IndicatorActivationState.UNAVAILABLE -> Quadruple(
            Color.Transparent,
            palette.cardBorder.copy(alpha = 0.5f),
            palette.unavailableTint,
            0.52f
        )
    }

    val stateSuffix = when (item.activationState) {
        IndicatorActivationState.ACTIVE -> "active"
        IndicatorActivationState.INACTIVE -> "inactive"
        IndicatorActivationState.UNAVAILABLE -> "unavailable"
    }

    Row(
        modifier = Modifier
            .alpha(alpha)
            .clip(IndicatorPillShape)
            .background(bgColor)
            .border(
                width = if (item.activationState == IndicatorActivationState.ACTIVE) 1.2.dp else 0.8.dp,
                color = borderColor,
                shape = IndicatorPillShape
            )
            .padding(
                horizontal = if (isCompact) 6.dp else 7.dp,
                vertical = if (isCompact) 2.dp else 3.dp
            )
            .semantics { contentDescription = "${item.type.label} $stateSuffix" }
            .testTag("indicator_${item.type.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (item.activationState == IndicatorActivationState.UNAVAILABLE) {
                "${item.type.shortCode} N/A"
            } else {
                item.type.shortCode
            },
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = if (isCompact) 8.5.sp else 9.5.sp,
                letterSpacing = 0.4.sp
            ),
            color = textColor,
            fontWeight = if (item.activationState == IndicatorActivationState.ACTIVE) {
                FontWeight.Bold
            } else {
                FontWeight.Medium
            },
            maxLines = 1
        )
    }
}

private data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
