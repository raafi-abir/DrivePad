package com.drivepad.app.feature.racing

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepad.app.core.designsystem.DrivePadThemeExtras
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.TransmissionMode
import com.drivepad.app.feature.dashboard.components.DashboardLayoutRenderer
import com.drivepad.app.feature.racing.components.AcceleratorPedal
import com.drivepad.app.feature.racing.components.BrakePedal
import com.drivepad.app.feature.racing.components.GearboxDropdownButton
import com.drivepad.app.feature.racing.components.GearboxMenuCard
import com.drivepad.app.feature.racing.components.IndicatorCapsuleRow
import com.drivepad.app.feature.racing.components.ManualShiftButton
import com.drivepad.app.feature.racing.components.MountainBackdrop
import com.drivepad.app.feature.racing.components.SteeringWheel
import com.drivepad.app.feature.racing.components.VehicleFunctionsGrid
import kotlinx.coroutines.isActive

private val PillShape = RoundedCornerShape(50)
private val CrimsonAccent = Color(0xFFE51414)
private val CrimsonGlow = Color(0xFFFF2424)
private val ConnectedGreen = Color(0xFF00FF3B)

@Composable
fun RacingControllerScreen(
    viewModel: RacingViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToDashboardCustomization: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dashboardUiState by viewModel.dashboardUiState.collectAsStateWithLifecycle()
    val palette = DrivePadThemeExtras.dashboardPalette
    val calibrationFeedback by viewModel.calibrationFeedback.collectAsStateWithLifecycle()
    var gearboxExpanded by remember { mutableStateOf(false) }

    BackHandler {
        if (gearboxExpanded) {
            gearboxExpanded = false
        } else {
            onNavigateBack()
        }
    }

    // Lock to landscape while cockpit is active; system bar hiding is sequenced after rotation completes
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val previousOrientation =
            activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (activity?.requestedOrientation != ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }

        onDispose {
            activity?.requestedOrientation = previousOrientation
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> viewModel.onPause()
                Lifecycle.Event.ON_RESUME -> viewModel.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onPause()
        }
    }

    // Drive smooth live RPM and Speed updates only while engine/pedals/motion are active
    val isPhysicsActive = uiState.vehicleFunctions.engineRunning ||
        uiState.pedalsState.throttle > 0.01f ||
        uiState.speedKmh > 0.01f ||
        uiState.rpm > 0.5f
    LaunchedEffect(isPhysicsActive) {
        if (!isPhysicsActive) return@LaunchedEffect
        var lastNanos = withFrameNanos { it }
        while (isActive) {
            val nowNanos = withFrameNanos { it }
            val dt = (nowNanos - lastNanos) / 1_000_000_000f
            lastNanos = nowNanos
            viewModel.stepSimulation(dt)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(if (palette.isDark) Color(0xFF040507) else palette.canvasBackground)
    ) {
        if (palette.isDark) {
            MountainBackdrop(modifier = Modifier.fillMaxSize())
        }

        val isCompactWidth = maxWidth < 720.dp
        val isCompactHeight = maxHeight < 420.dp
        val outerHorizontal = if (isCompactWidth) 12.dp else 22.dp
        val outerVertical = if (isCompactHeight) 8.dp else 14.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .padding(horizontal = outerHorizontal, vertical = outerVertical)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // =========================================================
                // TOP BAR: [DrivePad Pill] | [Connected + Settings] | [Gearbox v]
                // =========================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: DrivePad brand + Motion Steering toggle pill (+ Calibrate when active)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DrivePadMotionPill(
                            motionSteeringEnabled = uiState.motionSteeringEnabled,
                            onClick = {
                                viewModel.onMotionSteeringToggled(!uiState.motionSteeringEnabled)
                            }
                        )

                        AnimatedVisibility(
                            visible = uiState.motionSteeringEnabled,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(38.dp)
                                    .clip(PillShape)
                                    .background(Color(0xFF140709))
                                    .border(1.dp, CrimsonAccent, PillShape)
                                    .clickable { viewModel.calibrateSteering() }
                                    .padding(horizontal = 12.dp)
                                    .testTag("cockpit_calibrate_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Adjust,
                                    contentDescription = "Calibrate Neutral",
                                    tint = CrimsonGlow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Calibrate",
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    // Center: Connection Status Pill + Customize Dashboard + Circular Settings Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ConnectionStatusPill(status = uiState.connectionStatus)

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF15181F), Color(0xFF0A0C10))
                                    )
                                )
                                .border(1.dp, palette.accent.copy(alpha = 0.7f), CircleShape)
                                .clickable { onNavigateToDashboardCustomization() }
                                .semantics { contentDescription = "Customize Dashboard" }
                                .testTag("cockpit_customize_dashboard_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DashboardCustomize,
                                contentDescription = null,
                                tint = Color(0xFFE5E7EB),
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF15181F), Color(0xFF0A0C10))
                                    )
                                )
                                .border(1.dp, Color(0xFF2B303A), CircleShape)
                                .clickable { onNavigateToSettings() }
                                .semantics { contentDescription = "Settings" }
                                .testTag("cockpit_settings_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Settings,
                                contentDescription = null,
                                tint = Color(0xFFD5DAE3),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Right: Gearbox Dropdown Trigger Pill (Auto/Manual + R/N/D)
                    GearboxDropdownButton(
                        expanded = gearboxExpanded,
                        onToggleExpanded = { gearboxExpanded = !gearboxExpanded }
                    )
                }

                Spacer(modifier = Modifier.height(if (isCompactHeight) 6.dp else 10.dp))

                // =========================================================
                // 3-COLUMN COCKPIT BODY
                // =========================================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompactWidth) 10.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // LEFT COLUMN: Obsidian-Crimson Steering Wheel
                    Box(
                        modifier = Modifier
                            .weight(if (isCompactWidth) 1.05f else 1.15f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        SteeringWheel(
                            angleDegrees = uiState.steeringState.angleDegrees,
                            normalizedValue = uiState.steeringState.normalizedValue,
                            isInteracting = uiState.steeringState.isInteracting,
                            isTouchEnabled = !uiState.motionSteeringEnabled,
                            onAngleChange = { delta -> viewModel.onSteeringDrag(delta) },
                            onRelease = { viewModel.onSteeringReleased() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // CENTER COLUMN: Customizable Live Dashboard Renderer
                    Box(
                        modifier = Modifier
                            .weight(if (isCompactWidth) 0.95f else 0.96f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        DashboardLayoutRenderer(
                            uiState = dashboardUiState,
                            palette = palette,
                            isCompactHeight = isCompactHeight,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = if (isCompactHeight) 4.dp else 8.dp)
                        )
                    }

                    // RIGHT COLUMN: Indicators + Controls + (Manual Shifts) + Brake & Accelerator Pedals
                    Column(
                        modifier = Modifier
                            .weight(if (isCompactWidth) 1.32f else 1.28f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 6.dp else 10.dp)
                        ) {
                            IndicatorCapsuleRow(
                                state = uiState.vehicleFunctions,
                                blinkOn = uiState.indicatorBlinkOn,
                                onToggleLeftIndicator = { viewModel.toggleLeftIndicator() },
                                onToggleHazard = { viewModel.toggleHazard() },
                                onToggleRightIndicator = { viewModel.toggleRightIndicator() },
                                isCompact = isCompactHeight,
                                modifier = Modifier.fillMaxWidth()
                            )

                            VehicleFunctionsGrid(
                                state = uiState.vehicleFunctions,
                                handbrakeEngaged = uiState.pedalsState.handbrakeEngaged,
                                onToggleLights = { viewModel.toggleLights() },
                                onHornPressChanged = { viewModel.setHornActive(it) },
                                onToggleWipers = { viewModel.toggleWipers() },
                                onToggleEngine = { viewModel.toggleEngine() },
                                onToggleHandbrake = { viewModel.onHandbrakeToggled() },
                                isCompact = isCompactHeight,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Lower Zone: Manual Shift Down/Up aligned cleanly directly above Brake / Accelerator
                        val isManualMode = uiState.transmissionMode == TransmissionMode.MANUAL
                        val shiftBtnHeight = if (isCompactHeight) 38.dp else 44.dp
                        val shiftGap = if (isCompactHeight) 10.dp else 14.dp
                        val pedalHeight = if (isManualMode) {
                            if (isCompactHeight) 86.dp else 106.dp
                        } else {
                            if (isCompactHeight) 108.dp else 130.dp
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .padding(
                                    horizontal = if (isCompactWidth) 4.dp else 10.dp,
                                    vertical = 2.dp
                                ),
                            horizontalArrangement = Arrangement.spacedBy(if (isCompactWidth) 12.dp else 20.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            // Left Column: Shift Down (Manual) + Brake Pedal
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (isManualMode) {
                                    ManualShiftButton(
                                        label = "Shift Down",
                                        isShiftUp = false,
                                        onClick = { viewModel.onShiftDown() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(shiftBtnHeight)
                                            .testTag("manual_shift_down")
                                    )
                                    Spacer(modifier = Modifier.height(shiftGap))
                                }

                                BrakePedal(
                                    brakePressure = uiState.pedalsState.brake,
                                    onBrakePressed = { viewModel.onBrakePressed() },
                                    onBrakeReleased = { viewModel.onBrakeReleased() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 74.dp, max = pedalHeight)
                                )
                            }

                            // Right Column: Shift Up (Manual) + Accelerator Pedal
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (isManualMode) {
                                    ManualShiftButton(
                                        label = "Shift Up",
                                        isShiftUp = true,
                                        onClick = { viewModel.onShiftUp() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(shiftBtnHeight)
                                            .testTag("manual_shift_up")
                                    )
                                    Spacer(modifier = Modifier.height(shiftGap))
                                }

                                AcceleratorPedal(
                                    throttlePressure = uiState.pedalsState.throttle,
                                    onThrottlePressed = { viewModel.onThrottlePressed() },
                                    onThrottleReleased = { viewModel.onThrottleReleased() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 74.dp, max = pedalHeight)
                                )
                            }
                        }
                    }
                }
            }

            // In-window dismiss scrim & Gearbox menu overlay (prevents Android PopupWindow surface transactions)
            if (gearboxExpanded) {
                val scrimInteraction = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = scrimInteraction,
                            indication = null,
                            onClick = { gearboxExpanded = false }
                        )
                )
            }

            AnimatedVisibility(
                visible = gearboxExpanded,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 44.dp),
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
            ) {
                GearboxMenuCard(
                    transmissionMode = uiState.transmissionMode,
                    selectedAutoGear = uiState.selectedAutoGear,
                    currentManualGear = uiState.currentManualGear,
                    onTransmissionModeChanged = { viewModel.onTransmissionModeChanged(it) },
                    onSelectAutoGear = { viewModel.onSelectAutoGear(it) },
                    onDismiss = { gearboxExpanded = false }
                )
            }

            // Calibration / Sensor Feedback Toast Popup
            AnimatedVisibility(
                visible = calibrationFeedback != null,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = PillShape,
                    color = Color(0xFF140709),
                    border = BorderStroke(1.dp, CrimsonGlow)
                ) {
                    Text(
                        text = calibrationFeedback ?: "",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrivePadMotionPill(
    motionSteeringEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(38.dp)
            .clip(PillShape)
            .background(
                Brush.verticalGradient(
                    colors = if (motionSteeringEnabled) {
                        listOf(Color(0xFF2B080A), Color(0xFF120506))
                    } else {
                        listOf(Color(0xFF161920), Color(0xFF0A0C10))
                    }
                )
            )
            .border(
                width = 1.dp,
                color = if (motionSteeringEnabled) CrimsonGlow else Color(0xFF2B303A),
                shape = PillShape
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = "Toggle motion steering" }
            .testTag("cockpit_motion_steering_switch"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Phone icon + tiny red tilt accent matching top-left pill in reference
        Canvas(modifier = Modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            drawRoundRect(
                color = Color(0xFFE5E7EB),
                topLeft = Offset(w * 0.14f, h * 0.12f),
                size = Size(w * 0.48f, h * 0.76f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(width = 1.3.dp.toPx())
            )
            drawCircle(
                color = Color(0xFFE5E7EB),
                radius = 1.1.dp.toPx(),
                center = Offset(w * 0.38f, h * 0.74f)
            )
            drawLine(
                color = CrimsonGlow,
                start = Offset(w * 0.74f, h * 0.68f),
                end = Offset(w * 0.90f, h * 0.28f),
                strokeWidth = 1.5.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        Text(
            text = "DrivePad",
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic
            ),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun ConnectionStatusPill(
    status: ConnectionStatus,
    modifier: Modifier = Modifier
) {
    val (dotColor, label) = when (status) {
        ConnectionStatus.CONNECTED -> ConnectedGreen to "Connected"
        ConnectionStatus.TETHER_DETECTED -> Color(0xFF38BDF8) to "USB Ready"
        ConnectionStatus.CONNECTING -> Color(0xFFFFAB00) to "Connecting"
        ConnectionStatus.WATCHDOG_TIMEOUT -> CrimsonGlow to "Timeout"
        ConnectionStatus.DISCONNECTED -> Color(0xFF6B7280) to "Disconnected"
    }

    Row(
        modifier = modifier
            .height(38.dp)
            .clip(PillShape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF15181F), Color(0xFF0A0C10))
                )
            )
            .border(1.dp, Color(0xFF282C36), PillShape)
            .padding(horizontal = 14.dp)
            .testTag("cockpit_connection_pill"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            color = Color(0xFFE5E7EB),
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
