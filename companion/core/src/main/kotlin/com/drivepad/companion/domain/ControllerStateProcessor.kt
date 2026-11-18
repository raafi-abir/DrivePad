package com.drivepad.companion.domain

import com.drivepad.companion.protocol.DecodedPacket
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Networking-independent controller state processor. Validates incoming decoded packets,
 * publishes immutable [ControllerState] snapshots via [StateFlow], and supports immediate
 * failsafe neutral resets without any dependency on UDP sockets or Linux uinput.
 */
class ControllerStateProcessor(
    private val outputs: List<ControllerOutput> = emptyList()
) : ControllerStateProvider {

    private val _controllerState = MutableStateFlow(ControllerState.safeNeutral())
    override val controllerState: StateFlow<ControllerState> = _controllerState.asStateFlow()

    fun applyDecodedPacket(packet: DecodedPacket, receivedAtNanos: Long): InputValidationResult {
        val result = InputValidator.validateAndBuildState(packet, receivedAtNanos)
        if (result is InputValidationResult.Valid) {
            _controllerState.value = result.state
            for (i in outputs.indices) {
                outputs[i].publishState(result.state)
            }
        }
        return result
    }

    fun resetToFailsafe(nowNanos: Long) {
        val safeState = ControllerState.safeNeutral(updatedAtNanos = nowNanos)
        _controllerState.value = safeState
        for (i in outputs.indices) {
            outputs[i].resetToSafeState(safeState)
        }
    }
}
