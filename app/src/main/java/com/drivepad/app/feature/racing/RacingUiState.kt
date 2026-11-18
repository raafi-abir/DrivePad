package com.drivepad.app.feature.racing

import androidx.compose.runtime.Immutable
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.TransmissionMode

enum class AutoGear(val label: String) {
    P("P"),
    R("R"),
    N("N"),
    D("D")
}

@Immutable
data class VehicleFunctionsState(
    val lightsOn: Boolean = false,
    val hornActive: Boolean = false,
    val wipersActive: Boolean = false,
    val hazardActive: Boolean = false,
    val cruiseActive: Boolean = false,
    val engineRunning: Boolean = false,
    val leftIndicatorActive: Boolean = false,
    val rightIndicatorActive: Boolean = false
)

@Immutable
data class SteeringState(
    val angleDegrees: Float = 0.0f,
    val normalizedValue: Float = 0.0f, // -1.0 (full left) to +1.0 (full right)
    val isInteracting: Boolean = false
)

@Immutable
data class PedalsState(
    val throttle: Float = 0.0f, // 0.0f to 1.0f
    val brake: Float = 0.0f,    // 0.0f to 1.0f
    val handbrakeEngaged: Boolean = false
)

@Immutable
data class RacingUiState(
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val selectedAutoGear: AutoGear = AutoGear.D,
    val currentManualGear: Int = 1, // -1 (R), 0 (N), 1..6
    val steeringState: SteeringState = SteeringState(),
    val pedalsState: PedalsState = PedalsState(),
    val vehicleFunctions: VehicleFunctionsState = VehicleFunctionsState(),
    val motionSteeringEnabled: Boolean = false,
    val isSensorAvailable: Boolean = true,
    val isCalibrated: Boolean = false,
    val indicatorBlinkOn: Boolean = true,
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val rpm: Float = 0.0f,
    val speedKmh: Float = 0.0f,
    val autoActiveGearNumber: Int? = null
)
