package com.drivepad.companion.profile

/**
 * Built-in cross-platform controller profiles shipped with DrivePad Companion.
 * Works identically on Linux (`uinput`) and Windows (`ViGEm` Xbox 360).
 */
object ProfilePresets {
    const val GENERIC_ID = "generic"
    const val DEFAULT_ALIAS = "default"
    const val ETS2_ID = "ets2"

    fun generic(): ControllerProfile = ControllerProfile(
        id = GENERIC_ID,
        displayName = "Generic Cross-Platform Gamepad",
        description = "Direct pass-through controller profile compatible with Linux evdev/SDL and Windows XInput/Steam.",
        steeringDeadzone = 0.0f,
        steeringSensitivity = 1.0f,
        steeringInvert = false,
        steeringCurve = SteeringCurve.LINEAR,
        steeringCurveGamma = 1.0f,
        steeringAxis = StandardAxisCode.LEFT_X,
        throttleAxis = StandardAxisCode.RIGHT_TRIGGER,
        brakeAxis = StandardAxisCode.LEFT_TRIGGER,
        throttleInvert = false,
        brakeInvert = false,
        buttonBindings = linkedMapOf(
            LogicalButton.HORN to ButtonBinding(StandardButtonCode.SOUTH, ButtonBehavior.HOLD),
            LogicalButton.HANDBRAKE to ButtonBinding(StandardButtonCode.EAST, ButtonBehavior.HOLD),
            LogicalButton.LIGHTS to ButtonBinding(StandardButtonCode.NORTH, ButtonBehavior.HOLD),
            LogicalButton.WIPERS to ButtonBinding(StandardButtonCode.WEST, ButtonBehavior.HOLD),
            LogicalButton.LEFT_INDICATOR to ButtonBinding(StandardButtonCode.LEFT_SHOULDER, ButtonBehavior.HOLD),
            LogicalButton.RIGHT_INDICATOR to ButtonBinding(StandardButtonCode.RIGHT_SHOULDER, ButtonBehavior.HOLD),
            LogicalButton.HAZARD_LIGHTS to ButtonBinding(StandardButtonCode.BACK, ButtonBehavior.HOLD),
            LogicalButton.CRUISE_CONTROL to ButtonBinding(StandardButtonCode.START, ButtonBehavior.HOLD),
            LogicalButton.ENGINE to ButtonBinding(StandardButtonCode.GUIDE, ButtonBehavior.HOLD),
            LogicalButton.SHIFT_UP to ButtonBinding(StandardButtonCode.DPAD_UP, ButtonBehavior.PULSE_ON_PRESS),
            LogicalButton.SHIFT_DOWN to ButtonBinding(StandardButtonCode.DPAD_DOWN, ButtonBehavior.PULSE_ON_PRESS)
        ),
        pulseDurationMs = 35L,
        pulseGapMs = 35L,
        clampParkToNeutralInAuto = false,
        multiStepAutoShift = false
    )

    fun ets2(): ControllerProfile = ControllerProfile(
        id = ETS2_ID,
        displayName = "Euro Truck Simulator 2",
        description = "Practical cross-platform ETS2 profile: progressive highway steering, 3% center deadzone, " +
            "digital pedals, toggle-to-pulse truck switches, and Real Automatic / Sequential shifting.",
        steeringDeadzone = 0.03f,
        steeringSensitivity = 1.0f,
        steeringInvert = false,
        steeringCurve = SteeringCurve.PROGRESSIVE,
        steeringCurveGamma = 1.5f,
        steeringAxis = StandardAxisCode.LEFT_X,
        throttleAxis = StandardAxisCode.RIGHT_TRIGGER,
        brakeAxis = StandardAxisCode.LEFT_TRIGGER,
        throttleInvert = false,
        brakeInvert = false,
        buttonBindings = linkedMapOf(
            LogicalButton.HORN to ButtonBinding(StandardButtonCode.SOUTH, ButtonBehavior.HOLD),
            LogicalButton.HANDBRAKE to ButtonBinding(StandardButtonCode.EAST, ButtonBehavior.PULSE_ON_CHANGE),
            LogicalButton.LIGHTS to ButtonBinding(StandardButtonCode.NORTH, ButtonBehavior.PULSE_ON_CHANGE),
            LogicalButton.WIPERS to ButtonBinding(StandardButtonCode.WEST, ButtonBehavior.PULSE_ON_CHANGE),
            LogicalButton.LEFT_INDICATOR to ButtonBinding(StandardButtonCode.LEFT_SHOULDER, ButtonBehavior.PULSE_ON_CHANGE),
            LogicalButton.RIGHT_INDICATOR to ButtonBinding(StandardButtonCode.RIGHT_SHOULDER, ButtonBehavior.PULSE_ON_CHANGE),
            LogicalButton.HAZARD_LIGHTS to ButtonBinding(StandardButtonCode.BACK, ButtonBehavior.PULSE_ON_CHANGE),
            LogicalButton.CRUISE_CONTROL to ButtonBinding(StandardButtonCode.START, ButtonBehavior.PULSE_ON_CHANGE),
            LogicalButton.ENGINE to ButtonBinding(StandardButtonCode.GUIDE, ButtonBehavior.PULSE_ON_CHANGE),
            LogicalButton.SHIFT_UP to ButtonBinding(StandardButtonCode.DPAD_UP, ButtonBehavior.PULSE_ON_PRESS),
            LogicalButton.SHIFT_DOWN to ButtonBinding(StandardButtonCode.DPAD_DOWN, ButtonBehavior.PULSE_ON_PRESS)
        ),
        pulseDurationMs = 75L,
        pulseGapMs = 45L,
        clampParkToNeutralInAuto = true,
        multiStepAutoShift = true
    )

    fun builtInById(rawId: String): ControllerProfile? = when (rawId.trim().lowercase()) {
        GENERIC_ID, DEFAULT_ALIAS -> generic()
        ETS2_ID -> ets2()
        else -> null
    }

    fun allBuiltIns(): List<ControllerProfile> = listOf(generic(), ets2())
}
