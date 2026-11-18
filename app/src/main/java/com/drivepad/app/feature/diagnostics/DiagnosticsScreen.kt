package com.drivepad.app.feature.diagnostics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.NetworkPing
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
import com.drivepad.app.BuildConfig
import com.drivepad.app.core.network.DrivePadPacket
import com.drivepad.app.domain.model.ConnectionStatus
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val diag = uiState.diagnostics
    val route = diag.networkRoute
    val ctrl = uiState.controllerState

    val isConnectedOrConnecting =
        diag.status == ConnectionStatus.CONNECTED || diag.status == ConnectionStatus.CONNECTING

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DeepGraphiteBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Telemetry & Diagnostics",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("diagnostics_back_button")
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
                .padding(innerPadding)
                .testTag("diagnostics_list"),
            contentPadding = PaddingValues(Dimens.spacing16),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacing16)
        ) {
            // 1. Live USB Tether & UDP Connection Card
            item {
                DiagnosticCard(title = "USB Tether & UDP Pipeline", icon = Icons.Default.Cable) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${diag.targetHostIp}:${diag.targetPort}",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        StatusPill(status = diag.status)
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacing8))
                    Text(
                        text = route.routeSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )

                    if (!route.warningMessage.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(Dimens.spacing8))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = BrakeRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacing6))
                            Text(
                                text = route.warningMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = BrakeRed
                            )
                        }
                    }

                    if (!diag.lastError.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(Dimens.spacing6))
                        Text(
                            text = "Alert: ${diag.lastError}",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrakeRed,
                            modifier = Modifier.testTag("diag_last_error")
                        )
                    }

                    Spacer(modifier = Modifier.height(Dimens.spacing12))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.spacing8)
                    ) {
                        Button(
                            onClick = { viewModel.onToggleConnection() },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("diag_connect_button"),
                            shape = Dimens.shapeMedium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isConnectedOrConnecting) BrakeRed else NeonGreenPrimary,
                                contentColor = if (isConnectedOrConnecting) TextPrimary else DeepGraphiteBackground
                            )
                        ) {
                            Text(
                                text = when (diag.status) {
                                    ConnectionStatus.CONNECTED -> "Disconnect"
                                    ConnectionStatus.CONNECTING -> "Cancel"
                                    ConnectionStatus.WATCHDOG_TIMEOUT -> "Reconnect"
                                    else -> "Connect"
                                },
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { viewModel.onSendPing() },
                            enabled = diag.status == ConnectionStatus.CONNECTED,
                            modifier = Modifier.testTag("diag_ping_button"),
                            shape = Dimens.shapeMedium
                        ) {
                            Icon(
                                imageVector = Icons.Default.NetworkPing,
                                contentDescription = "Send Ping",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(Dimens.spacing4))
                            Text("Ping")
                        }

                        IconButton(
                            onClick = { viewModel.onRefreshRoute() },
                            modifier = Modifier.testTag("diag_refresh_route_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh Network Route",
                                tint = TextSecondary
                            )
                        }
                    }

                    if (route.activeInterfaces.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Dimens.spacing12))
                        Text(
                            text = "Detected Non-Loopback IPv4 Interfaces",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(Dimens.spacing4))
                        route.activeInterfaces.forEach { netIf ->
                            val tag = if (netIf.isUsbTetherCandidate) " [USB/LAN Candidate]" else ""
                            DiagnosticRow(
                                label = "${netIf.name}$tag",
                                value = netIf.ipv4Address
                            )
                        }
                    }
                }
            }

            // 2. UDP Rate, Packet Loss, RTT & Jitter Metrics
            item {
                DiagnosticCard(title = "UDP Telemetry & Timing", icon = Icons.Default.Speed) {
                    DiagnosticRow(
                        label = "Target / Actual Rate",
                        value = "${diag.targetUpdateHz} Hz / ${String.format(Locale.US, "%.1f Hz", diag.actualUpdateHz)}"
                    )
                    DiagnosticRow(
                        label = "Packets Sent (TX)",
                        value = diag.packetsSent.toString()
                    )
                    DiagnosticRow(
                        label = "Packets Received (RX)",
                        value = diag.packetsReceived.toString()
                    )
                    DiagnosticRow(
                        label = "Sequence Gaps / Out-of-Order",
                        value = "${diag.sequenceGapsDetected} / ${diag.outOfOrderPackets}"
                    )
                    DiagnosticRow(
                        label = "Estimated RX Loss",
                        value = String.format(Locale.US, "%.2f%%", diag.estimatedPacketLossPercent)
                    )
                    DiagnosticRow(
                        label = "Round-Trip Time (RTT)",
                        value = diag.rttMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "—"
                    )
                    DiagnosticRow(
                        label = "Smoothed RTT (EMA)",
                        value = diag.smoothedRttMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "—"
                    )
                    DiagnosticRow(
                        label = "Jitter (RTT variance)",
                        value = diag.jitterMs?.let { String.format(Locale.US, "%.2f ms", it) } ?: "—"
                    )
                    DiagnosticRow(
                        label = "Last Successful RX",
                        value = diag.lastSuccessfulCommElapsedMs?.let { "${it} ms ago" } ?: "—"
                    )
                    DiagnosticRow(
                        label = "Watchdog Timeouts",
                        value = diag.watchdogTriggerCount.toString()
                    )
                }
            }

            // 3. Sensor & Controller Input Snapshot
            item {
                DiagnosticCard(title = "Sensor & Input Engine Snapshot", icon = Icons.Default.Sensors) {
                    DiagnosticRow(
                        label = "Gyroscope / Rotation Sensor",
                        value = if (uiState.isSensorAvailable) "Available" else "Unavailable (Touch Mode)"
                    )
                    DiagnosticRow(
                        label = "Motion Steering",
                        value = if (ctrl.isMotionSteeringActive) "Enabled" else "Disabled"
                    )
                    DiagnosticRow(
                        label = "Normalized Steering",
                        value = String.format(Locale.US, "%+.3f", ctrl.steering)
                    )
                    DiagnosticRow(
                        label = "Digital Throttle / Brake",
                        value = "${if (ctrl.throttle > 0.5f) "100%" else "0%"} / ${if (ctrl.brake > 0.5f) "100%" else "0%"}"
                    )
                    DiagnosticRow(
                        label = "Gear / Mode",
                        value = "${ctrl.currentGear} (${ctrl.transmissionMode.displayName})"
                    )
                }
            }

            // 4. Binary Packet Protocol & Release Version Reference
            item {
                DiagnosticCard(title = "Binary Protocol & Release Info", icon = Icons.Default.DeveloperMode) {
                    DiagnosticRow(label = "DrivePad Version", value = "v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
                    DiagnosticRow(label = "Protocol Version", value = "v${BuildConfig.PROTOCOL_VERSION}")
                    DiagnosticRow(label = "Frame Layout", value = "${DrivePadPacket.PACKET_SIZE_BYTES} bytes, Little-Endian")
                    DiagnosticRow(label = "Scheduler", value = "Monotonic (Non-RTOS, No Burst)")
                    DiagnosticRow(label = "Handshake & Heartbeat", value = "HELLO / HELLO_ACK + PING / PONG")
                    DiagnosticRow(label = "Architecture", value = "MVVM + StateFlow + DatagramSocket")
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = Dimens.shapeMedium,
        colors = CardDefaults.cardColors(containerColor = CockpitSurfaceElevated),
        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(CockpitBorder))
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
            Spacer(modifier = Modifier.height(Dimens.spacing12))
            content()
        }
    }
}

@Composable
private fun StatusPill(status: ConnectionStatus) {
    val accent = when (status) {
        ConnectionStatus.CONNECTED -> NeonGreenPrimary
        ConnectionStatus.TETHER_DETECTED -> Color(0xFF1E88E5)
        ConnectionStatus.CONNECTING -> Color(0xFFFFB300)
        ConnectionStatus.WATCHDOG_TIMEOUT -> BrakeRed
        else -> TextMuted
    }
    Surface(
        shape = Dimens.shapePill,
        color = accent.copy(alpha = 0.18f),
        border = BorderStroke(1.dp, accent)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Dimens.spacing12, vertical = Dimens.spacing4),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(Dimens.shapePill)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(Dimens.spacing6))
            Text(
                text = status.label,
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag("diag_status_label")
            )
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacing4),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Text(text = value, style = MaterialTheme.typography.bodySmall, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}
