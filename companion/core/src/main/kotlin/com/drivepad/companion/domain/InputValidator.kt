package com.drivepad.companion.domain

import com.drivepad.companion.protocol.DecodedPacket
import com.drivepad.companion.protocol.ProtocolConstants
import com.drivepad.companion.protocol.VehicleButtonFlag

sealed class InputValidationResult {
    data class Valid(val state: ControllerState) : InputValidationResult()
    data class Rejected(val reason: String) : InputValidationResult()
}

object InputValidator {
    fun validateAndBuildState(
        packet: DecodedPacket,
        receivedAtNanos: Long
    ): InputValidationResult {
        if (packet.steering.isNaN() || packet.steering.isInfinite() || packet.steering !in -1.0f..1.0f) {
            return InputValidationResult.Rejected("Invalid steering value: ${packet.steering}")
        }
        if (packet.throttle != 0.0f && packet.throttle != 1.0f) {
            return InputValidationResult.Rejected("Non-digital throttle rejected: ${packet.throttle}")
        }
        if (packet.brake != 0.0f && packet.brake != 1.0f) {
            return InputValidationResult.Rejected("Non-digital brake rejected: ${packet.brake}")
        }
        if (packet.currentGear !in ProtocolConstants.MIN_GEAR..ProtocolConstants.MAX_GEAR) {
            return InputValidationResult.Rejected("Invalid gear value: ${packet.currentGear}")
        }

        val flags = packet.vehicleButtonFlags
        val state = ControllerState(
            steering = packet.steering.coerceIn(-1.0f, 1.0f),
            throttle = packet.throttle,
            brake = packet.brake,
            handbrake = packet.handbrake,
            currentGear = packet.currentGear,
            transmissionMode = packet.transmissionMode,
            lights = (flags and VehicleButtonFlag.LIGHTS) != 0u,
            horn = (flags and VehicleButtonFlag.HORN) != 0u,
            wipers = (flags and VehicleButtonFlag.WIPERS) != 0u,
            hazardLights = (flags and VehicleButtonFlag.HAZARD_LIGHTS) != 0u,
            leftIndicator = (flags and VehicleButtonFlag.LEFT_INDICATOR) != 0u,
            rightIndicator = (flags and VehicleButtonFlag.RIGHT_INDICATOR) != 0u,
            cruiseControl = (flags and VehicleButtonFlag.CRUISE_CONTROL) != 0u,
            engine = (flags and VehicleButtonFlag.ENGINE) != 0u,
            isSafeNeutral = false,
            lastUpdatedNanos = receivedAtNanos
        )
        return InputValidationResult.Valid(state)
    }
}
