package com.drivepad.companion.protocol

sealed class ValidationOutcome {
    data object Valid : ValidationOutcome()
    data class Invalid(val reason: String) : ValidationOutcome()
}

object PacketValidator {
    fun validateRawLength(bytes: ByteArray, length: Int): ValidationOutcome {
        if (length != ProtocolConstants.PACKET_SIZE_BYTES || bytes.size < ProtocolConstants.PACKET_SIZE_BYTES) {
            return ValidationOutcome.Invalid(
                "Invalid packet size: expected ${ProtocolConstants.PACKET_SIZE_BYTES} bytes, got $length"
            )
        }
        return ValidationOutcome.Valid
    }

    fun validateFields(
        protocolVersion: Int,
        rawMessageType: Int,
        steering: Float,
        throttle: Float,
        brake: Float,
        handbrakeByte: Int,
        gear: Int,
        transmissionByte: Int,
        buttonFlags: UInt
    ): ValidationOutcome {
        if (protocolVersion != ProtocolConstants.PROTOCOL_VERSION_V1) {
            return ValidationOutcome.Invalid("Unsupported protocol version: $protocolVersion")
        }
        if (PacketMessageType.fromWireValue(rawMessageType) == null) {
            return ValidationOutcome.Invalid("Unknown message type: $rawMessageType")
        }
        if (steering.isNaN() || steering.isInfinite() || steering !in -1.0f..1.0f) {
            return ValidationOutcome.Invalid("Steering out of bounds [-1.0, 1.0]: $steering")
        }
        if (throttle.isNaN() || throttle.isInfinite() || (throttle != 0.0f && throttle != 1.0f)) {
            return ValidationOutcome.Invalid("Throttle must be strictly 0.0 or 1.0, got $throttle")
        }
        if (brake.isNaN() || brake.isInfinite() || (brake != 0.0f && brake != 1.0f)) {
            return ValidationOutcome.Invalid("Brake must be strictly 0.0 or 1.0, got $brake")
        }
        if (handbrakeByte != 0 && handbrakeByte != 1) {
            return ValidationOutcome.Invalid("Invalid handbrake byte: $handbrakeByte")
        }
        if (gear < ProtocolConstants.MIN_GEAR || gear > ProtocolConstants.MAX_GEAR) {
            return ValidationOutcome.Invalid(
                "Gear out of range [${ProtocolConstants.MIN_GEAR}..${ProtocolConstants.MAX_GEAR}]: $gear"
            )
        }
        if (TransmissionMode.fromWireValue(transmissionByte) == null) {
            return ValidationOutcome.Invalid("Invalid transmission mode byte: $transmissionByte")
        }
        if ((buttonFlags and ProtocolConstants.KNOWN_FLAGS_MASK.inv()) != 0u) {
            return ValidationOutcome.Invalid("Unknown reserved vehicleButtonFlags bits: 0x${buttonFlags.toString(16)}")
        }
        return ValidationOutcome.Valid
    }
}
