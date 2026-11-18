package com.drivepad.companion.domain

import com.drivepad.companion.protocol.ProtocolConstants
import com.drivepad.companion.protocol.TransmissionMode

data class ControllerState(
    val steering: Float = 0.0f,
    val throttle: Float = 0.0f,
    val brake: Float = 0.0f,
    val handbrake: Boolean = false,
    val currentGear: Int = 0,
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val lights: Boolean = false,
    val horn: Boolean = false,
    val wipers: Boolean = false,
    val hazardLights: Boolean = false,
    val leftIndicator: Boolean = false,
    val rightIndicator: Boolean = false,
    val cruiseControl: Boolean = false,
    val engine: Boolean = false,
    val isSafeNeutral: Boolean = true,
    val lastUpdatedNanos: Long = 0L
) {
    init {
        require(!steering.isNaN() && !steering.isInfinite() && steering in -1.0f..1.0f) {
            "Steering must remain within [-1.0, +1.0], was $steering"
        }
        require(!throttle.isNaN() && !throttle.isInfinite() && (throttle == 0.0f || throttle == 1.0f)) {
            "Digital throttle must be strictly 0.0 or 1.0, was $throttle"
        }
        require(!brake.isNaN() && !brake.isInfinite() && (brake == 0.0f || brake == 1.0f)) {
            "Digital brake must be strictly 0.0 or 1.0, was $brake"
        }
        require(currentGear in ProtocolConstants.MIN_GEAR..ProtocolConstants.MAX_GEAR) {
            "Gear must be in [${ProtocolConstants.MIN_GEAR}..${ProtocolConstants.MAX_GEAR}], was $currentGear"
        }
    }

    companion object {
        fun safeNeutral(updatedAtNanos: Long = 0L): ControllerState = ControllerState(
            steering = 0.0f,
            throttle = 0.0f,
            brake = 0.0f,
            handbrake = false,
            currentGear = 0, // Safe Neutral
            transmissionMode = TransmissionMode.AUTO,
            lights = false,
            horn = false,
            wipers = false,
            hazardLights = false,
            leftIndicator = false,
            rightIndicator = false,
            cruiseControl = false,
            engine = false,
            isSafeNeutral = true,
            lastUpdatedNanos = updatedAtNanos
        )
    }
}
