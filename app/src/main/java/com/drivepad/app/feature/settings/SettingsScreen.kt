package com.drivepad.app.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepad.app.core.designsystem.BrakeRed
import com.drivepad.app.core.designsystem.CockpitBorder
import com.drivepad.app.core.designsystem.CockpitSurfaceElevated
import com.drivepad.app.core.designsystem.DeepGraphiteBackground
import com.drivepad.app.core.designsystem.Dimens
import com.drivepad.app.core.designsystem.NeonGreenPrimary
import com.drivepad.app.core.designsystem.TextMuted
import com.drivepad.app.core.designsystem.TextPrimary
import com.drivepad.app.core.designsystem.TextSecondary
import com.drivepad.app.domain.model.SteeringResponseCurve
import com.drivepad.app.domain.model.TransmissionMode
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDashboardCustomization: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val config by viewModel.configState.collectAsStateWithLifecycle()
    val calibrationMessage by viewModel.calibrationMessage.collectAsStateWithLifecycle()
    val networkFeedback by viewModel.networkFeedback.collectAsStateWithLifecycle()
    val networkError by viewModel.networkError.collectAsStateWithLifecycle()

    var hostIpInput by remember { mutableStateOf(config.targetHostIp) }
    var udpPortInput by remember { mutableStateOf(config.udpPort.toString()) }
    var targetFrequencyInput by remember { mutableIntStateOf(config.targetUpdateFrequencyHz) }

    LaunchedEffect(config.targetHostIp, config.udpPort, config.targetUpdateFrequencyHz) {
        hostIpInput = config.targetHostIp
        udpPortInput = config.udpPort.toString()
        targetFrequencyInput = config.targetUpdateFrequencyHz
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepGraphiteBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Controller Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepGraphiteBackground
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(Dimens.spacing16),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacing16)
        ) {
            // Dashboard Customization Studio Entry
            item {
                SettingsSectionCard(title = "Dashboard Personalization", icon = Icons.Default.DashboardCustomize) {
                    Text(
                        text = "Customize cockpit layouts (Racing, Truck, Minimal, Compact), circular or digital gauges, units (km/h or mph), redline thresholds, and theme colors.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacing12))
                    Button(
                        onClick = onNavigateToDashboardCustomization,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_dashboard_customization"),
                        shape = Dimens.shapeMedium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreenPrimary,
                            contentColor = DeepGraphiteBackground
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(Dimens.spacing8))
                        Text("Open Dashboard Studio", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Steering Calibration Card
            item {
                SettingsSectionCard(title = "Steering Calibration", icon = Icons.Default.Adjust) {
                    Text(
                        text = "Hold your phone in your natural driving posture and tap Calibrate to lock in the neutral zero-steering reference angle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacing12))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacing12)
                    ) {
                        Button(
                            onClick = { viewModel.calibrateNeutral() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_calibrate_neutral"),
                            shape = Dimens.shapeMedium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeonGreenPrimary,
                                contentColor = DeepGraphiteBackground
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Adjust,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacing8))
                            Text("Calibrate Zero", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { viewModel.resetCalibration() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("btn_reset_calibration"),
                            shape = Dimens.shapeMedium,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                        ) {
                            Icon(
                                imageVector = Icons.Default.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacing8))
                            Text("Reset 0°")
                        }
                    }

                    if (calibrationMessage != null) {
                        Spacer(modifier = Modifier.height(Dimens.spacing8))
                        Text(
                            text = calibrationMessage ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonGreenPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Steering & Response Configuration
            item {
                SettingsSectionCard(title = "Steering Sensitivity & Limits", icon = Icons.Default.Tune) {
                    // Steering Sensitivity
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Steering Sensitivity",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = String.format(Locale.US, "%.2fx", config.steeringSensitivity),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonGreenPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = config.steeringSensitivity,
                            onValueChange = { viewModel.onSensitivityChanged(it) },
                            valueRange = 0.2f..3.0f,
                            steps = 27,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonGreenPrimary,
                                activeTrackColor = NeonGreenPrimary,
                                inactiveTrackColor = CockpitBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacing8))

                    // Steering Deadzone
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Steering Deadzone",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = String.format(Locale.US, "%.0f%%", config.steeringDeadzone * 100),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonGreenPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = config.steeringDeadzone,
                            onValueChange = { viewModel.onDeadzoneChanged(it) },
                            valueRange = 0.0f..0.25f,
                            steps = 24,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonGreenPrimary,
                                activeTrackColor = NeonGreenPrimary,
                                inactiveTrackColor = CockpitBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacing8))

                    // Maximum Steering Angle Limit
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Maximum Steering Angle",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = String.format(Locale.US, "%.0f°", config.maxSteeringAngleDegrees),
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeonGreenPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = config.maxSteeringAngleDegrees,
                            onValueChange = { viewModel.onMaxAngleChanged(it) },
                            valueRange = 15.0f..90.0f,
                            steps = 14,
                            colors = SliderDefaults.colors(
                                thumbColor = NeonGreenPrimary,
                                activeTrackColor = NeonGreenPrimary,
                                inactiveTrackColor = CockpitBorder
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacing8))

                    // Steering Response Curve Selector
                    Column {
                        Text(
                            text = "Response Curve",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(Dimens.spacing8))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.spacing8)
                        ) {
                            SteeringResponseCurve.entries.forEach { curve ->
                                val isSelected = config.steeringResponseCurve == curve
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onResponseCurveChanged(curve) },
                                    label = { Text(curve.displayName, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = NeonGreenPrimary,
                                        selectedLabelColor = DeepGraphiteBackground,
                                        containerColor = DeepGraphiteBackground,
                                        labelColor = TextPrimary
                                    )
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacing12))

                    // Invert Steering Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Invert Steering Axis",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Reverses left and right steering direction",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = config.invertSteering,
                            onCheckedChange = { viewModel.onInvertSteeringToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepGraphiteBackground,
                                checkedTrackColor = NeonGreenPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DeepGraphiteBackground
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacing12))

                    // Return to Center Toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Return-to-Center Spring",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Automatically spring wheel back to 0.00 when touch is released",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = config.returnToCenterEnabled,
                            onCheckedChange = { viewModel.onReturnToCenterToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepGraphiteBackground,
                                checkedTrackColor = NeonGreenPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DeepGraphiteBackground
                            )
                        )
                    }
                }
            }

            // Motion Steering (Gyroscope) Section
            item {
                SettingsSectionCard(title = "Motion Steering (Gyroscope)", icon = Icons.Default.Sensors) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Enable Gyroscope Steering",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                            Text(
                                text = "Tilt smartphone like a real steering wheel in landscape",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMuted
                            )
                        }
                        Switch(
                            checked = config.motionSteeringEnabled,
                            onCheckedChange = { viewModel.onMotionSteeringToggled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = DeepGraphiteBackground,
                                checkedTrackColor = NeonGreenPrimary,
                                uncheckedThumbColor = TextMuted,
                                uncheckedTrackColor = DeepGraphiteBackground
                            )
                        )
                    }

                    if (config.motionSteeringEnabled) {
                        Spacer(modifier = Modifier.height(Dimens.spacing12))
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Motion Smoothing (EMA Jitter Filter)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = String.format(Locale.US, "%.0f%%", config.motionSmoothingFactor * 100),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NeonGreenPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = config.motionSmoothingFactor,
                                onValueChange = { viewModel.onMotionSmoothingChanged(it) },
                                valueRange = 0.05f..0.95f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonGreenPrimary,
                                    activeTrackColor = NeonGreenPrimary,
                                    inactiveTrackColor = CockpitBorder
                                )
                            )
                        }
                    }
                }
            }

            // Transmission & Gearbox Section
            item {
                SettingsSectionCard(title = "Transmission Default", icon = Icons.Default.Speed) {
                    Text(
                        text = "Preferred Gearbox Mode on Launch",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacing12))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacing12)
                    ) {
                        TransmissionMode.entries.forEach { mode ->
                            val isSelected = config.defaultTransmissionMode == mode
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.onTransmissionChanged(mode) },
                                label = {
                                    Text(
                                        text = mode.displayName,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonGreenPrimary,
                                    selectedLabelColor = DeepGraphiteBackground,
                                    containerColor = DeepGraphiteBackground,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }
                }
            }

            // USB Tethering & UDP Connection Section
            item {
                SettingsSectionCard(title = "USB Tethering & UDP Receiver", icon = Icons.Default.Wifi) {
                    Text(
                        text = "Configure the Arch Linux receiver IPv4 address on your USB tether subnet, UDP port, and target transmit frequency.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacing12))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacing12)
                    ) {
                        OutlinedTextField(
                            value = hostIpInput,
                            onValueChange = { hostIpInput = it },
                            label = { Text("Receiver IPv4") },
                            placeholder = { Text("192.168.42.1") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("input_receiver_ip"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreenPrimary,
                                unfocusedBorderColor = CockpitBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = NeonGreenPrimary,
                                unfocusedLabelColor = TextSecondary
                            )
                        )

                        OutlinedTextField(
                            value = udpPortInput,
                            onValueChange = { udpPortInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("UDP Port") },
                            placeholder = { Text("42100") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("input_udp_port"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonGreenPrimary,
                                unfocusedBorderColor = CockpitBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedLabelColor = NeonGreenPrimary,
                                unfocusedLabelColor = TextSecondary
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacing12))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Target Update Rate",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = "$targetFrequencyInput Hz (${String.format(Locale.US, "%.1f", 1000.0 / targetFrequencyInput)} ms)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeonGreenPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(Dimens.spacing6))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacing8)
                    ) {
                        listOf(60, 120, 250, 500).forEach { presetHz ->
                            val isSelected = targetFrequencyInput == presetHz
                            FilterChip(
                                selected = isSelected,
                                onClick = { targetFrequencyInput = presetHz },
                                label = { Text("${presetHz}Hz", fontSize = 11.sp) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("freq_chip_$presetHz"),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonGreenPrimary,
                                    selectedLabelColor = DeepGraphiteBackground,
                                    containerColor = DeepGraphiteBackground,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }

                    Slider(
                        value = targetFrequencyInput.toFloat(),
                        onValueChange = { targetFrequencyInput = it.roundToInt().coerceIn(20, 500) },
                        valueRange = 20f..500f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonGreenPrimary,
                            activeTrackColor = NeonGreenPrimary,
                            inactiveTrackColor = CockpitBorder
                        )
                    )

                    Text(
                        text = "Note: Android is non-RTOS; 250 Hz (4 ms) is a best-effort target cadence. Missed intervals are skipped without bursting stale packets.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextMuted
                    )

                    if (networkError != null) {
                        Spacer(modifier = Modifier.height(Dimens.spacing8))
                        Text(
                            text = networkError ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = BrakeRed,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("network_error_text")
                        )
                    } else if (networkFeedback != null) {
                        Spacer(modifier = Modifier.height(Dimens.spacing8))
                        Text(
                            text = networkFeedback ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = NeonGreenPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.testTag("network_feedback_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacing12))
                    Button(
                        onClick = {
                            viewModel.saveNetworkSettings(
                                hostIpRaw = hostIpInput,
                                portRaw = udpPortInput,
                                frequencyHz = targetFrequencyInput
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("btn_save_network_settings"),
                        shape = Dimens.shapeMedium,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonGreenPrimary,
                            contentColor = DeepGraphiteBackground
                        )
                    ) {
                        Text("Save Network Settings", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Reset Preferences Button
            item {
                OutlinedButton(
                    onClick = { viewModel.onResetDefaults() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = Dimens.shapeMedium,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = BrakeRed
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacing8))
                    Text("Reset All Settings to Defaults")
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Dimens.shapeMedium,
        colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CockpitBorder))
    ) {
        Column(modifier = Modifier.padding(Dimens.spacing16)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = NeonGreenPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.spacing8))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacing16))
            content()
        }
    }
}
