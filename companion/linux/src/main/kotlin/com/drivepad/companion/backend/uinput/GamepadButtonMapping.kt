package com.drivepad.companion.backend.uinput

import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.profile.StandardButtonCode

/**
 * Documented mapping between DrivePad controls and standard Linux `EV_KEY` gamepad buttons.
 */
enum class GamepadButton(
    val displayName: String,
    val linuxCode: Int,
    val linuxName: String
) {
    HORN("Horn", LinuxInputConstants.BTN_SOUTH, "BTN_SOUTH"),
    HANDBRAKE("Handbrake", LinuxInputConstants.BTN_EAST, "BTN_EAST"),
    LIGHTS("Lights", LinuxInputConstants.BTN_NORTH, "BTN_NORTH"),
    WIPERS("Wipers", LinuxInputConstants.BTN_WEST, "BTN_WEST"),
    LEFT_INDICATOR("Left Indicator", LinuxInputConstants.BTN_TL, "BTN_TL"),
    RIGHT_INDICATOR("Right Indicator", LinuxInputConstants.BTN_TR, "BTN_TR"),
    HAZARD_LIGHTS("Hazard Lights", LinuxInputConstants.BTN_SELECT, "BTN_SELECT"),
    CRUISE_CONTROL("Cruise Control", LinuxInputConstants.BTN_START, "BTN_START"),
    ENGINE("Engine", LinuxInputConstants.BTN_MODE, "BTN_MODE"),
    SHIFT_UP("Shift Up", LinuxInputConstants.BTN_DPAD_UP, "BTN_DPAD_UP"),
    SHIFT_DOWN("Shift Down", LinuxInputConstants.BTN_DPAD_DOWN, "BTN_DPAD_DOWN")
}

object GamepadButtonMapping {
    val allButtons: List<GamepadButton> = GamepadButton.entries
    val allLinuxButtonCodes: List<Int> = allButtons.map { it.linuxCode }

    fun toLinuxKeyCode(button: StandardButtonCode): Int = when (button) {
        StandardButtonCode.SOUTH -> LinuxInputConstants.BTN_SOUTH
        StandardButtonCode.EAST -> LinuxInputConstants.BTN_EAST
        StandardButtonCode.NORTH -> LinuxInputConstants.BTN_NORTH
        StandardButtonCode.WEST -> LinuxInputConstants.BTN_WEST
        StandardButtonCode.LEFT_SHOULDER -> LinuxInputConstants.BTN_TL
        StandardButtonCode.RIGHT_SHOULDER -> LinuxInputConstants.BTN_TR
        StandardButtonCode.BACK -> LinuxInputConstants.BTN_SELECT
        StandardButtonCode.START -> LinuxInputConstants.BTN_START
        StandardButtonCode.GUIDE -> LinuxInputConstants.BTN_MODE
        StandardButtonCode.LEFT_THUMB -> LinuxInputConstants.BTN_THUMBL
        StandardButtonCode.RIGHT_THUMB -> LinuxInputConstants.BTN_THUMBR
        StandardButtonCode.DPAD_UP -> LinuxInputConstants.BTN_DPAD_UP
        StandardButtonCode.DPAD_DOWN -> LinuxInputConstants.BTN_DPAD_DOWN
        StandardButtonCode.DPAD_LEFT -> LinuxInputConstants.BTN_DPAD_LEFT
        StandardButtonCode.DPAD_RIGHT -> LinuxInputConstants.BTN_DPAD_RIGHT
    }

    /**
     * Extracts deterministic button states for a [ControllerState]. If [shiftUpPressed] or
     * [shiftDownPressed] is active (from a momentary gear step) and `!state.isSafeNeutral`,
     * those momentary shift buttons are asserted; on SafeNeutral all buttons are always `false`.
     */
    fun extractButtonStates(
        state: ControllerState,
        shiftUpPressed: Boolean = false,
        shiftDownPressed: Boolean = false
    ): BooleanArray {
        val out = BooleanArray(allButtons.size)
        if (state.isSafeNeutral) {
            return out
        }
        out[GamepadButton.HORN.ordinal] = state.horn
        out[GamepadButton.HANDBRAKE.ordinal] = state.handbrake
        out[GamepadButton.LIGHTS.ordinal] = state.lights
        out[GamepadButton.WIPERS.ordinal] = state.wipers
        out[GamepadButton.LEFT_INDICATOR.ordinal] = state.leftIndicator
        out[GamepadButton.RIGHT_INDICATOR.ordinal] = state.rightIndicator
        out[GamepadButton.HAZARD_LIGHTS.ordinal] = state.hazardLights
        out[GamepadButton.CRUISE_CONTROL.ordinal] = state.cruiseControl
        out[GamepadButton.ENGINE.ordinal] = state.engine
        out[GamepadButton.SHIFT_UP.ordinal] = shiftUpPressed
        out[GamepadButton.SHIFT_DOWN.ordinal] = shiftDownPressed
        return out
    }
}
