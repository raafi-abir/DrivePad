package com.drivepad.app.domain.model

enum class TransmissionMode(val displayName: String) {
    AUTO("Automatic"),
    MANUAL("Manual Sequential")
}

enum class ConnectionStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    TETHER_DETECTED("USB Tether Ready"),
    CONNECTING("Connecting to PC..."),
    CONNECTED("Connected (Active)"),
    WATCHDOG_TIMEOUT("Watchdog Fail-Safe")
}

enum class SteeringResponseCurve(val displayName: String) {
    LINEAR("Linear"),
    EXPONENTIAL("Exponential (High precision center)"),
    SMOOTH("Smooth S-Curve")
}

data class ControllerConfig(
    val steeringSensitivity: Float = 1.0f,
    val steeringDeadzone: Float = 0.04f,
    val maxSteeringAngleDegrees: Float = 35.0f,
    val steeringResponseCurve: SteeringResponseCurve = SteeringResponseCurve.LINEAR,
    val invertSteering: Boolean = false,
    val motionSteeringEnabled: Boolean = false,
    val motionSmoothingFactor: Float = 0.35f,
    val hapticFeedbackEnabled: Boolean = true,
    val returnToCenterEnabled: Boolean = true,
    val defaultTransmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val udpPort: Int = 42100,
    val targetHostIp: String = "192.168.42.1",
    val targetUpdateFrequencyHz: Int = 250
)

data class ControllerProfile(
    val id: String,
    val name: String,
    val description: String,
    val targetGame: String,
    val config: ControllerConfig,
    val isDefault: Boolean = false
) {
    companion object {
        val DefaultProfiles = listOf(
            ControllerProfile(
                id = "ets2_truck",
                name = "Euro Truck Simulator 2",
                description = "900° truck steering scale, calibrated neutral deadzone, and automatic gearbox",
                targetGame = "Euro Truck Simulator 2",
                config = ControllerConfig(
                    steeringSensitivity = 0.9f,
                    steeringDeadzone = 0.04f,
                    maxSteeringAngleDegrees = 35.0f,
                    motionSteeringEnabled = true,
                    defaultTransmissionMode = TransmissionMode.AUTO
                ),
                isDefault = true
            ),
            ControllerProfile(
                id = "gt_racing",
                name = "GT & Track Racing",
                description = "Direct 1:1 steering ratio, sequential manual paddle shifting",
                targetGame = "Assetto Corsa / Project CARS",
                config = ControllerConfig(
                    steeringSensitivity = 1.25f,
                    steeringDeadzone = 0.02f,
                    maxSteeringAngleDegrees = 30.0f,
                    motionSteeringEnabled = false,
                    defaultTransmissionMode = TransmissionMode.MANUAL
                ),
                isDefault = false
            ),
            ControllerProfile(
                id = "casual_arcade",
                name = "Casual Driving",
                description = "High return-to-center spring and wide deadzone for relaxed cruising",
                targetGame = "Arcade & Open World",
                config = ControllerConfig(
                    steeringSensitivity = 1.0f,
                    steeringDeadzone = 0.06f,
                    maxSteeringAngleDegrees = 40.0f,
                    motionSteeringEnabled = false,
                    defaultTransmissionMode = TransmissionMode.AUTO
                ),
                isDefault = false
            )
        )
    }
}
