package com.drivepad.app.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepad.app.R
import com.drivepad.app.core.designsystem.BrakeRed
import com.drivepad.app.core.designsystem.CockpitBorder
import com.drivepad.app.core.designsystem.CockpitSurfaceElevated
import com.drivepad.app.core.designsystem.DeepGraphiteBackground
import com.drivepad.app.core.designsystem.Dimens
import com.drivepad.app.core.designsystem.NeonGreenGlow
import com.drivepad.app.core.designsystem.NeonGreenPrimary
import com.drivepad.app.core.designsystem.TextMuted
import com.drivepad.app.core.designsystem.TextPrimary
import com.drivepad.app.core.designsystem.TextSecondary
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.ControllerProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToRacing: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfiles: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToDashboardCustomization: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepGraphiteBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(Dimens.shapeSmall)
                                .background(NeonGreenPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsMotorsports,
                                contentDescription = null,
                                tint = DeepGraphiteBackground,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(Dimens.spacing12))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "PC Racing & Simulation Controller",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToDashboardCustomization,
                        modifier = Modifier.testTag("nav_customize_dashboard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DashboardCustomize,
                            contentDescription = "Dashboard Studio",
                            tint = NeonGreenPrimary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToDiagnostics,
                        modifier = Modifier.testTag("nav_diagnostics_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = "Diagnostics",
                            tint = TextSecondary
                        )
                    }
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("nav_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextSecondary
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
            // Connection & Readiness Status Banner
            item {
                ConnectionStatusCard(
                    status = uiState.connectionStatus,
                    selectedProfile = uiState.selectedProfile,
                    endpointLabel = "${uiState.config.targetHostIp}:${uiState.config.udpPort} @ ${uiState.config.targetUpdateFrequencyHz} Hz",
                    routeSummary = uiState.diagnostics.networkRoute.routeSummary,
                    lastError = uiState.diagnostics.lastError,
                    rttMs = uiState.diagnostics.smoothedRttMs ?: uiState.diagnostics.rttMs,
                    actualHz = uiState.diagnostics.actualUpdateHz,
                    onToggleConnection = { viewModel.onToggleConnection() },
                    onOpenDiagnostics = onNavigateToDiagnostics
                )
            }

            // Primary Launch Controller CTA (Transitions to Controller View)
            item {
                LaunchControllerCard(
                    onLaunch = onNavigateToRacing,
                    onCustomizeDashboard = onNavigateToDashboardCustomization,
                    selectedProfile = uiState.selectedProfile
                )
            }

            // Quick Controller Mode Toggles
            item {
                QuickTogglesCard(
                    motionSteeringEnabled = uiState.config.motionSteeringEnabled,
                    onToggleMotionSteering = { viewModel.onQuickToggleMotionSteering(it) },
                    transmissionMode = uiState.config.defaultTransmissionMode.displayName
                )
            }

            // Vehicle Profiles Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Vehicle Profiles",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Manage",
                        style = MaterialTheme.typography.labelMedium,
                        color = NeonGreenPrimary,
                        modifier = Modifier
                            .clip(Dimens.shapeSmall)
                            .clickable { onNavigateToProfiles() }
                            .padding(horizontal = Dimens.spacing8, vertical = Dimens.spacing4)
                    )
                }
            }

            // List of Profiles
            items(uiState.availableProfiles) { profile ->
                ProfileSelectionCard(
                    profile = profile,
                    isSelected = profile.id == uiState.selectedProfile.id,
                    onSelect = { viewModel.onSelectProfile(profile.id) }
                )
            }

            // USB Tethering & UDP Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = Dimens.shapeMedium,
                    colors = CardDefaults.cardColors(
                        containerColor = CockpitSurfaceElevated.copy(alpha = 0.6f)
                    ),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CockpitBorder))
                ) {
                    Column(modifier = Modifier.padding(Dimens.spacing16)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = null,
                                tint = NeonGreenPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacing8))
                            Text(
                                text = "USB Tethering & UDP Telemetry Active",
                                style = MaterialTheme.typography.titleSmall,
                                color = NeonGreenPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(Dimens.spacing8))
                        Text(
                            text = "Streams 33-byte little-endian binary controller packets up to 250 Hz over local USB tethering IPv4 with HELLO/ACK handshake, RTT/jitter monitoring, and watchdog fail-safe.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusCard(
    status: ConnectionStatus,
    selectedProfile: ControllerProfile,
    endpointLabel: String,
    routeSummary: String,
    lastError: String?,
    rttMs: Double?,
    actualHz: Double,
    onToggleConnection: () -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    val isConnectedOrConnecting = status == ConnectionStatus.CONNECTED || status == ConnectionStatus.CONNECTING

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Dimens.shapeLarge,
        colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CockpitBorder))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Dimens.spacing16, vertical = Dimens.spacing12)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${selectedProfile.name}  •  $endpointLabel",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacing2))
                    Text(
                        text = when {
                            !lastError.isNullOrBlank() -> lastError
                            status == ConnectionStatus.CONNECTED -> {
                                val rttStr = rttMs?.let { String.format(java.util.Locale.US, "%.1f ms RTT", it) } ?: "RTT pending"
                                val hzStr = String.format(java.util.Locale.US, "%.0f Hz actual", actualHz)
                                "$hzStr  •  $rttStr"
                            }
                            else -> routeSummary
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!lastError.isNullOrBlank()) BrakeRed else TextMuted,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(Dimens.spacing8))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacing6),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = Dimens.shapePill,
                        color = when (status) {
                            ConnectionStatus.CONNECTED -> NeonGreenPrimary.copy(alpha = 0.2f)
                            ConnectionStatus.TETHER_DETECTED -> Color(0xFF1E88E5).copy(alpha = 0.2f)
                            ConnectionStatus.CONNECTING -> Color(0xFFFFB300).copy(alpha = 0.2f)
                            ConnectionStatus.WATCHDOG_TIMEOUT -> BrakeRed.copy(alpha = 0.2f)
                            else -> CockpitBorder
                        },
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = when (status) {
                                ConnectionStatus.CONNECTED -> NeonGreenPrimary
                                ConnectionStatus.TETHER_DETECTED -> Color(0xFF1E88E5)
                                ConnectionStatus.CONNECTING -> Color(0xFFFFB300)
                                ConnectionStatus.WATCHDOG_TIMEOUT -> BrakeRed
                                else -> TextMuted
                            }
                        ),
                        modifier = Modifier
                            .clip(Dimens.shapePill)
                            .clickable { onOpenDiagnostics() }
                            .testTag("home_diagnostics_chip_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Dimens.spacing8, vertical = Dimens.spacing4),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(Dimens.shapePill)
                                    .background(
                                        when (status) {
                                            ConnectionStatus.CONNECTED -> NeonGreenPrimary
                                            ConnectionStatus.TETHER_DETECTED -> Color(0xFF1E88E5)
                                            ConnectionStatus.CONNECTING -> Color(0xFFFFB300)
                                            ConnectionStatus.WATCHDOG_TIMEOUT -> BrakeRed
                                            else -> TextMuted
                                        }
                                    )
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacing6))
                            Text(
                                text = status.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        shape = Dimens.shapeSmall,
                        color = if (isConnectedOrConnecting) BrakeRed else Color(0xFF1E88E5),
                        modifier = Modifier
                            .clip(Dimens.shapeSmall)
                            .clickable { onToggleConnection() }
                            .testTag("home_connect_button")
                    ) {
                        Text(
                            text = when (status) {
                                ConnectionStatus.CONNECTED -> "Disconnect"
                                ConnectionStatus.CONNECTING -> "Cancel"
                                ConnectionStatus.WATCHDOG_TIMEOUT -> "Reconnect"
                                else -> "Connect"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = Dimens.spacing12, vertical = Dimens.spacing4)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LaunchControllerCard(
    onLaunch: () -> Unit,
    onCustomizeDashboard: () -> Unit,
    selectedProfile: ControllerProfile
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(Dimens.borderWidthThin, NeonGreenGlow, Dimens.shapeLarge),
        shape = Dimens.shapeLarge,
        colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated)
    ) {
        Column(modifier = Modifier.padding(Dimens.spacing20)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Gamepad,
                    contentDescription = null,
                    tint = NeonGreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.spacing8))
                Text(
                    text = "Ready to Drive",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacing8))
            Text(
                text = "Landscape racing cockpit with steering, pedals, gearbox, and customizable telemetry dashboard.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(Dimens.spacing16))
            Button(
                onClick = onLaunch,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("launch_controller_button"),
                shape = Dimens.shapeMedium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = NeonGreenPrimary,
                    contentColor = DeepGraphiteBackground
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(Dimens.spacing8))
                Text(
                    text = "Launch Controller Cockpit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacing8))
            OutlinedButton(
                onClick = onCustomizeDashboard,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_customize_dashboard_button"),
                shape = Dimens.shapeMedium,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = TextPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = NeonGreenPrimary
                )
                Spacer(modifier = Modifier.width(Dimens.spacing8))
                Text(
                    text = "Customize Dashboard Layout & Gauges",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun QuickTogglesCard(
    motionSteeringEnabled: Boolean,
    onToggleMotionSteering: (Boolean) -> Unit,
    transmissionMode: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Dimens.shapeMedium,
        colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CockpitBorder))
    ) {
        Column(modifier = Modifier.padding(Dimens.spacing16)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = NeonGreenPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacing12))
                    Column {
                        Text(
                            text = "Motion Steering (Gyroscope)",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (motionSteeringEnabled) "Active (Tilt to steer)" else "Touch Wheel mode active",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                    }
                }
                Switch(
                    checked = motionSteeringEnabled,
                    onCheckedChange = onToggleMotionSteering,
                    modifier = Modifier.testTag("motion_steering_switch"),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DeepGraphiteBackground,
                        checkedTrackColor = NeonGreenPrimary,
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DeepGraphiteBackground
                    )
                )
            }
            Spacer(modifier = Modifier.height(Dimens.spacing12))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacing12))
                    Text(
                        text = "Default Transmission",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = transmissionMode,
                    style = MaterialTheme.typography.labelMedium,
                    color = NeonGreenPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ProfileSelectionCard(
    profile: ControllerProfile,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(Dimens.shapeMedium)
            .clickable { onSelect() }
            .testTag("profile_card_${profile.id}"),
        shape = Dimens.shapeMedium,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) CockpitSurfaceElevated else DeepGraphiteBackground
        ),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) Dimens.borderWidthMedium else Dimens.borderWidthThin,
            color = if (isSelected) NeonGreenPrimary else CockpitBorder
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.spacing16),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (profile.isDefault) {
                        Spacer(modifier = Modifier.width(Dimens.spacing8))
                        Surface(
                            shape = Dimens.shapeSmall,
                            color = NeonGreenPrimary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = "DEFAULT",
                                style = MaterialTheme.typography.labelSmall,
                                color = NeonGreenPrimary,
                                modifier = Modifier.padding(horizontal = Dimens.spacing4, vertical = Dimens.spacing2)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(Dimens.spacing4))
                Text(
                    text = profile.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = NeonGreenPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
