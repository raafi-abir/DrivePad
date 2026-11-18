package com.drivepad.app.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepad.app.core.designsystem.DashboardPalette
import com.drivepad.app.core.designsystem.Dimens
import com.drivepad.app.core.designsystem.DrivePadThemeExtras
import com.drivepad.app.domain.model.DashboardAccentColor
import com.drivepad.app.domain.model.DashboardConfiguration
import com.drivepad.app.domain.model.DashboardDensity
import com.drivepad.app.domain.model.DashboardLayout
import com.drivepad.app.domain.model.DashboardThemeMode
import com.drivepad.app.domain.model.GaugeBackgroundStyle
import com.drivepad.app.domain.model.GaugeRegion
import com.drivepad.app.domain.model.GaugeSize
import com.drivepad.app.domain.model.GaugeStyle
import com.drivepad.app.domain.model.GaugeType
import com.drivepad.app.domain.model.SpeedUnit
import com.drivepad.app.feature.dashboard.components.DashboardLayoutRenderer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardCustomizationScreen(
    viewModel: DashboardCustomizationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = DrivePadThemeExtras.dashboardPalette
    val config = uiState.configuration

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Dashboard Studio",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Customize layouts, gauges, warnings & theme",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("dashboard_custom_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val isWideScreen = maxWidth >= 560.dp
            val previewHeight = if (isWideScreen) {
                (maxHeight - 56.dp).coerceIn(160.dp, 258.dp)
            } else {
                (maxHeight * 0.40f).coerceIn(140.dp, 240.dp)
            }

            if (isWideScreen) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimens.spacing16, vertical = Dimens.spacing12),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacing16)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(0.45f)
                            .fillMaxHeight()
                    ) {
                        DashboardPreviewPane(
                            uiState = uiState,
                            palette = palette,
                            previewHeight = previewHeight
                        )
                    }
                    CustomizationSettingsList(
                        uiState = uiState,
                        palette = palette,
                        viewModel = viewModel,
                        modifier = Modifier
                            .weight(0.55f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Dimens.spacing12)
                ) {
                    DashboardPreviewPane(
                        uiState = uiState,
                        palette = palette,
                        previewHeight = previewHeight,
                        modifier = Modifier.padding(top = Dimens.spacing8, bottom = Dimens.spacing8)
                    )
                    CustomizationSettingsList(
                        uiState = uiState,
                        palette = palette,
                        viewModel = viewModel,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardPreviewPane(
    uiState: DashboardCustomizationUiState,
    palette: DashboardPalette,
    previewHeight: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScenarioHeaderBadge(
            activeLabel = uiState.previewScenario.label,
            layoutName = uiState.configuration.layout.displayName,
            palette = palette
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(previewHeight)
                .clip(RoundedCornerShape(16.dp))
                .background(palette.canvasBackground)
                .border(1.dp, palette.cardBorder, RoundedCornerShape(16.dp))
                .padding(12.dp)
                .testTag("dashboard_preview_container")
        ) {
            DashboardLayoutRenderer(
                uiState = uiState.previewUiState,
                palette = palette,
                isCompactHeight = true,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun ScenarioHeaderBadge(
    activeLabel: String,
    layoutName: String,
    palette: DashboardPalette
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "LIVE PREVIEW",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
            color = palette.accentGlow,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "$layoutName • $activeLabel",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomizationSettingsList(
    uiState: DashboardCustomizationUiState,
    palette: DashboardPalette,
    viewModel: DashboardCustomizationViewModel,
    modifier: Modifier = Modifier
) {
    val config = uiState.configuration
    val availableGauges = GaugeType.entries.filter { uiState.availability.isGaugeAvailable(it) }
    val unavailableGauges = GaugeType.entries.filterNot { uiState.availability.isGaugeAvailable(it) }

    LazyColumn(
        modifier = modifier.testTag("dashboard_customization_list"),
        contentPadding = PaddingValues(bottom = Dimens.spacing24, top = Dimens.spacing4),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacing12)
    ) {
        if (uiState.feedbackMessage != null) {
            item {
                Surface(
                    shape = Dimens.shapeMedium,
                    color = palette.accent.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, palette.accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = uiState.feedbackMessage,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        // 0. Preview Scenario Tester (so user can verify Missing/Stale/EngineOff/Redline states)
        item {
            CustomSectionCard(title = "Preview Telemetry State", icon = Icons.Default.Tune, palette = palette) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PreviewTelemetryScenario.entries.forEach { scenario ->
                        val selected = uiState.previewScenario == scenario
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onPreviewScenarioSelected(scenario) },
                            label = { Text(scenario.label, fontSize = 11.sp) },
                            modifier = Modifier.testTag("preview_scenario_${scenario.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        }

        // 1. Dashboard Layouts
        item {
            CustomSectionCard(title = "Dashboard Layout", icon = Icons.Default.DashboardCustomize, palette = palette) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DashboardLayout.entries.forEach { layout ->
                        val selected = config.layout == layout
                        Surface(
                            shape = Dimens.shapeSmall,
                            color = if (selected) palette.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) palette.accent else MaterialTheme.colorScheme.outline
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Dimens.shapeSmall)
                                .clickable { viewModel.onLayoutSelected(layout) }
                                .testTag("layout_option_${layout.name.lowercase()}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = layout.displayName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = layout.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected layout",
                                        tint = palette.accent
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Theme, Accent Palette & Visual Style
        item {
            CustomSectionCard(title = "Theme & Appearance", icon = Icons.Default.Palette, palette = palette) {
                Text(
                    text = "Color Theme",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardThemeMode.entries.forEach { mode ->
                        val selected = config.themeMode == mode
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onThemeModeSelected(mode) },
                            label = { Text(mode.displayName, fontSize = 11.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("theme_option_${mode.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Accent Palette",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DashboardAccentColor.entries.forEach { accent ->
                        val selected = config.accentColor == accent
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(Color(accent.primaryHex).copy(alpha = if (selected) 0.25f else 0.1f))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) Color(accent.glowHex) else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { viewModel.onAccentColorSelected(accent) }
                                .semantics { contentDescription = accent.displayName }
                                .testTag("accent_option_${accent.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(accent.primaryHex))
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Gauge Background Style",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    GaugeBackgroundStyle.entries.forEach { style ->
                        val selected = config.gaugeBackgroundStyle == style
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onGaugeBackgroundStyleSelected(style) },
                            label = { Text(style.displayName, fontSize = 11.sp) },
                            modifier = Modifier.testTag("bg_style_${style.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Visual Density",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DashboardDensity.entries.forEach { density ->
                        val selected = config.density == density
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onDensitySelected(density) },
                            label = { Text(density.displayName) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("density_option_${density.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                ToggleRow(
                    title = "Gauge Animations",
                    subtitle = "Smooth needle & state transitions (disable for ultra-low latency)",
                    checked = config.animationsEnabled,
                    onCheckedChange = { viewModel.onAnimationsToggled(it) },
                    testTag = "switch_gauge_animations"
                )
            }
        }

        // 3. Speedometer, Units & Warning Thresholds
        item {
            CustomSectionCard(title = "Units & Warning Thresholds", icon = Icons.Default.Speed, palette = palette) {
                Text(
                    text = "Speed Display Unit",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SpeedUnit.entries.forEach { unit ->
                        val selected = config.speedUnit == unit
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onSpeedUnitSelected(unit) },
                            label = { Text(unit.displayName, fontSize = 11.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("unit_option_${unit.name.lowercase()}"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Max Speedometer Gauge Scale",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DashboardConfiguration.VALID_SPEED_SCALES_KMH.forEach { scaleKmh ->
                        val selected = config.speedometerMaxScaleKmh == scaleKmh
                        val labelVal = config.speedUnit.convertRoundedIntFromKmh(scaleKmh)
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onSpeedometerMaxScaleSelected(scaleKmh) },
                            label = { Text("$labelVal ${config.speedUnit.symbol}", fontSize = 10.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("speed_scale_$scaleKmh"),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = palette.accent,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "RPM Redline Warning",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${config.rpmRedlineThreshold} RPM",
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.accent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Slider(
                    value = config.rpmRedlineThreshold.toFloat(),
                    onValueChange = { viewModel.onRpmRedlineThresholdChanged((it / 200f).roundToInt() * 200) },
                    valueRange = 3000f..7800f,
                    modifier = Modifier.testTag("slider_rpm_redline"),
                    colors = SliderDefaults.colors(
                        thumbColor = palette.accent,
                        activeTrackColor = palette.accent
                    )
                )

                ToggleRow(
                    title = "Warning Indicators",
                    subtitle = "Highlight redline, overspeed, and vehicle alerts",
                    checked = config.warningsEnabled,
                    onCheckedChange = { viewModel.onWarningsToggled(it) },
                    testTag = "switch_warnings_enabled"
                )

                ToggleRow(
                    title = "Overspeed Warning",
                    subtitle = "Threshold: ${config.speedUnit.convertRoundedIntFromKmh(config.overspeedThresholdKmh)} ${config.speedUnit.symbol}",
                    checked = config.overspeedWarningEnabled,
                    onCheckedChange = { viewModel.onOverspeedWarningChanged(it, config.overspeedThresholdKmh) },
                    testTag = "switch_overspeed_warning"
                )
                if (config.overspeedWarningEnabled) {
                    Slider(
                        value = config.overspeedThresholdKmh.toFloat(),
                        onValueChange = {
                            viewModel.onOverspeedWarningChanged(true, (it / 5f).roundToInt() * 5)
                        },
                        valueRange = 40f..config.speedometerMaxScaleKmh.toFloat(),
                        modifier = Modifier.testTag("slider_overspeed_threshold"),
                        colors = SliderDefaults.colors(
                            thumbColor = palette.accent,
                            activeTrackColor = palette.accent
                        )
                    )
                }

                ToggleRow(
                    title = "Unavailable Telemetry Placeholders",
                    subtitle = "Show N/A status tiles for truck sensors awaiting ETS2 plugin telemetry",
                    checked = config.showUnavailableTelemetryPlaceholders,
                    onCheckedChange = { viewModel.onUnavailablePlaceholdersToggled(it) },
                    testTag = "switch_unavailable_placeholders"
                )
            }
        }

        // 4. Supported Gauge Customization (strictly filters out unavailable Phase 9 fields)
        item {
            CustomSectionCard(title = "Supported Gauge Controls", icon = Icons.Default.WarningAmber, palette = palette) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    availableGauges.forEach { gaugeType ->
                        val gConfig = config.effectiveGaugeConfig(gaugeType)
                        Surface(
                            shape = Dimens.shapeSmall,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gauge_config_card_${gaugeType.name.lowercase()}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = gaugeType.displayName,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = gaugeType.requiredTelemetryFieldDesc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = gConfig.visible,
                                        onCheckedChange = { viewModel.onToggleGaugeVisibility(gaugeType, it) },
                                        modifier = Modifier.testTag("toggle_gauge_${gaugeType.name.lowercase()}")
                                    )
                                }

                                if (gConfig.visible) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (gaugeType.supportsPresentationToggle) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            GaugeStyle.entries.forEach { style ->
                                                FilterChip(
                                                    selected = gConfig.presentation == style,
                                                    onClick = { viewModel.onChangeGaugePresentation(gaugeType, style) },
                                                    label = { Text(style.displayName, fontSize = 11.sp) },
                                                    modifier = Modifier.testTag("style_${gaugeType.name.lowercase()}_${style.name.lowercase()}")
                                                )
                                            }
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        GaugeSize.entries.forEach { sz ->
                                            FilterChip(
                                                selected = gConfig.size == sz,
                                                onClick = { viewModel.onChangeGaugeSize(gaugeType, sz) },
                                                label = { Text(sz.displayName, fontSize = 11.sp) },
                                                modifier = Modifier.testTag("size_${gaugeType.name.lowercase()}_${sz.name.lowercase()}")
                                            )
                                        }
                                    }
                                    if (gaugeType == GaugeType.SPEEDOMETER || gaugeType == GaugeType.RPM_METER) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(GaugeRegion.PRIMARY_TOP, GaugeRegion.PRIMARY_MIDDLE).forEach { reg ->
                                                FilterChip(
                                                    selected = gConfig.region == reg,
                                                    onClick = { viewModel.onChangeGaugeRegion(gaugeType, reg) },
                                                    label = { Text(reg.displayName, fontSize = 11.sp) }
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        FilterChip(
                                            selected = gConfig.showLabel,
                                            onClick = { viewModel.onToggleGaugeLabel(gaugeType, !gConfig.showLabel) },
                                            label = { Text("Label", fontSize = 11.sp) },
                                            modifier = Modifier.testTag("label_toggle_${gaugeType.name.lowercase()}")
                                        )
                                        FilterChip(
                                            selected = gConfig.showUnit,
                                            onClick = { viewModel.onToggleGaugeUnit(gaugeType, !gConfig.showUnit) },
                                            label = { Text("Unit", fontSize = 11.sp) },
                                            modifier = Modifier.testTag("unit_toggle_${gaugeType.name.lowercase()}")
                                        )
                                        FilterChip(
                                            selected = gConfig.showWarningThreshold,
                                            onClick = { viewModel.onToggleGaugeThreshold(gaugeType, !gConfig.showWarningThreshold) },
                                            label = { Text("Limit Marker", fontSize = 11.sp) },
                                            modifier = Modifier.testTag("threshold_toggle_${gaugeType.name.lowercase()}")
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. Telemetry Source Capability Notice (explicitly documents unavailable Phase 9 fields)
        if (unavailableGauges.isNotEmpty()) {
            item {
                CustomSectionCard(title = "Unavailable ETS2 Sensor Channels", icon = Icons.Default.Info, palette = palette) {
                    Text(
                        text = "The current telemetry source (${uiState.availability.sourceName}) does not receive external ETS2 scs-sdk-plugin truck sensor packets. Customization is withheld for unavailable channels:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = unavailableGauges.joinToString(" • ") { it.displayName },
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.unavailableTint,
                        modifier = Modifier.testTag("unavailable_gauges_notice")
                    )
                }
            }
        }

        // 6. Restore Defaults
        item {
            OutlinedButton(
                onClick = { viewModel.onRestoreDefaults() },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("btn_restore_dashboard_defaults"),
                shape = Dimens.shapeMedium
            ) {
                Icon(
                    imageVector = Icons.Default.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Restore Default Dashboard Settings")
            }
        }
    }
}

@Composable
private fun CustomSectionCard(
    title: String,
    icon: ImageVector,
    palette: DashboardPalette,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Dimens.shapeMedium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(Dimens.spacing16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
