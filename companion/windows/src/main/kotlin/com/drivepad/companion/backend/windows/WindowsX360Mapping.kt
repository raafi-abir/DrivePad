package com.drivepad.companion.backend.windows

import com.drivepad.companion.profile.ControllerProfile
import com.drivepad.companion.profile.LogicalButton
import com.drivepad.companion.profile.NormalizedGamepadOutput
import com.drivepad.companion.profile.StandardAxisCode
import com.drivepad.companion.profile.StandardButtonCode
import kotlin.math.roundToInt

/**
 * Translates platform-independent [NormalizedGamepadOutput] and [ControllerProfile] bindings
 * into Xbox 360 [XusbReportData] snapshots for ViGEmBus (`XUSB`).
 */
object WindowsX360Mapping {

    fun toXusbButtonBit(button: StandardButtonCode): Int = when (button) {
        StandardButtonCode.SOUTH -> ViGEmConstants.XUSB_GAMEPAD_A
        StandardButtonCode.EAST -> ViGEmConstants.XUSB_GAMEPAD_B
        StandardButtonCode.NORTH -> ViGEmConstants.XUSB_GAMEPAD_X
        StandardButtonCode.WEST -> ViGEmConstants.XUSB_GAMEPAD_Y
        StandardButtonCode.LEFT_SHOULDER -> ViGEmConstants.XUSB_GAMEPAD_LEFT_SHOULDER
        StandardButtonCode.RIGHT_SHOULDER -> ViGEmConstants.XUSB_GAMEPAD_RIGHT_SHOULDER
        StandardButtonCode.BACK -> ViGEmConstants.XUSB_GAMEPAD_BACK
        StandardButtonCode.START -> ViGEmConstants.XUSB_GAMEPAD_START
        StandardButtonCode.GUIDE -> ViGEmConstants.XUSB_GAMEPAD_GUIDE
        StandardButtonCode.LEFT_THUMB -> ViGEmConstants.XUSB_GAMEPAD_LEFT_THUMB
        StandardButtonCode.RIGHT_THUMB -> ViGEmConstants.XUSB_GAMEPAD_RIGHT_THUMB
        StandardButtonCode.DPAD_UP -> ViGEmConstants.XUSB_GAMEPAD_DPAD_UP
        StandardButtonCode.DPAD_DOWN -> ViGEmConstants.XUSB_GAMEPAD_DPAD_DOWN
        StandardButtonCode.DPAD_LEFT -> ViGEmConstants.XUSB_GAMEPAD_DPAD_LEFT
        StandardButtonCode.DPAD_RIGHT -> ViGEmConstants.XUSB_GAMEPAD_DPAD_RIGHT
    }

    fun mapSteeringSigned16(normalizedSteering: Float): Int {
        if (normalizedSteering.isNaN() || normalizedSteering.isInfinite()) return 0
        val clamped = normalizedSteering.coerceIn(-1.0f, 1.0f)
        if (clamped == 0.0f) return 0
        return if (clamped > 0.0f) {
            (clamped * ViGEmConstants.THUMB_MAX.toFloat()).roundToInt().coerceIn(0, ViGEmConstants.THUMB_MAX)
        } else {
            (clamped * (-ViGEmConstants.THUMB_MIN).toFloat()).roundToInt().coerceIn(ViGEmConstants.THUMB_MIN, 0)
        }
    }

    fun mapDigitalTrigger(active: Boolean): Int =
        if (active) ViGEmConstants.TRIGGER_MAX else ViGEmConstants.TRIGGER_MIN

    fun mapOutput(output: NormalizedGamepadOutput, profile: ControllerProfile): XusbReportData {
        var buttonsMask = 0
        val orderedButtons = LogicalButton.entries
        for (i in orderedButtons.indices) {
            if (i < output.buttons.size && output.buttons[i]) {
                val standardBtn = profile.buttonBindings.getValue(orderedButtons[i]).button
                buttonsMask = buttonsMask or toXusbButtonBit(standardBtn)
            }
        }

        var leftTrigger = 0
        var rightTrigger = 0
        var thumbLX = 0
        var thumbLY = 0
        var thumbRX = 0
        var thumbRY = 0

        fun assignAxis(axis: StandardAxisCode, signedValue: Int, triggerValue: Int) {
            when (axis) {
                StandardAxisCode.LEFT_X -> thumbLX = signedValue
                StandardAxisCode.LEFT_Y -> thumbLY = signedValue
                StandardAxisCode.RIGHT_X -> thumbRX = signedValue
                StandardAxisCode.RIGHT_Y -> thumbRY = signedValue
                StandardAxisCode.RIGHT_TRIGGER -> rightTrigger = triggerValue
                StandardAxisCode.LEFT_TRIGGER -> leftTrigger = triggerValue
            }
        }

        val steer16 = mapSteeringSigned16(output.steeringNormalized)
        val steerTrigger = if (output.steeringNormalized > 0.5f) ViGEmConstants.TRIGGER_MAX else 0
        assignAxis(profile.steeringAxis, signedValue = steer16, triggerValue = steerTrigger)

        val throttleSigned = if (output.throttleActive) ViGEmConstants.THUMB_MAX else 0
        val throttleTrig = mapDigitalTrigger(output.throttleActive)
        assignAxis(profile.throttleAxis, signedValue = throttleSigned, triggerValue = throttleTrig)

        val brakeSigned = if (output.brakeActive) ViGEmConstants.THUMB_MAX else 0
        val brakeTrig = mapDigitalTrigger(output.brakeActive)
        assignAxis(profile.brakeAxis, signedValue = brakeSigned, triggerValue = brakeTrig)

        return XusbReportData(
            buttonsMask = buttonsMask,
            leftTrigger = leftTrigger,
            rightTrigger = rightTrigger,
            thumbLX = thumbLX,
            thumbLY = thumbLY,
            thumbRX = thumbRX,
            thumbRY = thumbRY
        )
    }

    fun neutralReport(profile: ControllerProfile): XusbReportData {
        val neutralNormalized = NormalizedGamepadOutput(
            steeringNormalized = 0.0f,
            throttleActive = !profile.throttleAxis.isSignedFullAxis && profile.throttleInvert,
            brakeActive = !profile.brakeAxis.isSignedFullAxis && profile.brakeInvert,
            buttons = BooleanArray(LogicalButton.entries.size)
        )
        return mapOutput(neutralNormalized, profile)
    }
}
