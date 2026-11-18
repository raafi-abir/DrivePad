package com.drivepad.companion.profile

import com.drivepad.companion.domain.ControllerState

class ProfileValidationException(message: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

enum class SteeringCurve {
    LINEAR,
    PROGRESSIVE;

    companion object {
        fun parse(raw: String?): SteeringCurve? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.find { it.name == normalized }
        }
    }
}

enum class ButtonBehavior {
    /** Keeps virtual button pressed while the DrivePad state bit is `true`, released when `false`. */
    HOLD,

    /** Emits a timed momentary `press -> release` pulse whenever the DrivePad toggle flag transitions. */
    PULSE_ON_CHANGE,

    /** Emits a timed momentary `press -> release` pulse on `false -> true` transitions or discrete gear steps. */
    PULSE_ON_PRESS;

    companion object {
        fun parse(raw: String?): ButtonBehavior? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.find { it.name == normalized }
        }
    }
}

enum class LogicalButton(val propertyKey: String, val displayName: String) {
    HORN("horn", "Horn"),
    HANDBRAKE("handbrake", "Handbrake"),
    LIGHTS("lights", "Lights"),
    WIPERS("wipers", "Wipers"),
    LEFT_INDICATOR("left_indicator", "Left Indicator"),
    RIGHT_INDICATOR("right_indicator", "Right Indicator"),
    HAZARD_LIGHTS("hazard_lights", "Hazard Lights"),
    CRUISE_CONTROL("cruise_control", "Cruise Control"),
    ENGINE("engine", "Engine Start/Stop"),
    SHIFT_UP("shift_up", "Shift Up"),
    SHIFT_DOWN("shift_down", "Shift Down");

    fun readState(state: ControllerState): Boolean = when (this) {
        HORN -> state.horn
        HANDBRAKE -> state.handbrake
        LIGHTS -> state.lights
        WIPERS -> state.wipers
        LEFT_INDICATOR -> state.leftIndicator
        RIGHT_INDICATOR -> state.rightIndicator
        HAZARD_LIGHTS -> state.hazardLights
        CRUISE_CONTROL -> state.cruiseControl
        ENGINE -> state.engine
        SHIFT_UP, SHIFT_DOWN -> false // Triggered by gear delta, not static flags
    }
}

/**
 * Platform-independent canonical gamepad axes mapped individually by Linux (`/dev/uinput`)
 * and Windows (`ViGEm` X360) platform adapters. Also accepts legacy Linux `ABS_*` names in `.properties`.
 */
enum class StandardAxisCode(
    val specName: String,
    val legacyLinuxName: String,
    val isSignedFullAxis: Boolean
) {
    LEFT_X("LEFT_X", "ABS_X", true),
    LEFT_Y("LEFT_Y", "ABS_Y", true),
    RIGHT_TRIGGER("RIGHT_TRIGGER", "ABS_Z", false),
    RIGHT_X("RIGHT_X", "ABS_RX", true),
    RIGHT_Y("RIGHT_Y", "ABS_RY", true),
    LEFT_TRIGGER("LEFT_TRIGGER", "ABS_RZ", false);

    companion object {
        fun parse(raw: String?): StandardAxisCode? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.find {
                it.name == normalized ||
                    it.specName == normalized ||
                    it.legacyLinuxName == normalized
            }
        }
    }
}

/**
 * Platform-independent canonical gamepad digital buttons mapped by Linux (`BTN_*`)
 * and Windows (`XUSB_GAMEPAD_*`) backends. Accepts canonical SDL/XInput names as well
 * as legacy Linux `BTN_*` names in saved `.properties` files.
 */
enum class StandardButtonCode(
    val legacyLinuxName: String,
    val sdlHint: String
) {
    SOUTH("BTN_SOUTH", "A / Cross"),
    EAST("BTN_EAST", "B / Circle"),
    NORTH("BTN_NORTH", "X / Square"),
    WEST("BTN_WEST", "Y / Triangle"),
    LEFT_SHOULDER("BTN_TL", "LB / L1"),
    RIGHT_SHOULDER("BTN_TR", "RB / R1"),
    BACK("BTN_SELECT", "Back / View"),
    START("BTN_START", "Start / Menu"),
    GUIDE("BTN_MODE", "Guide / Mode"),
    LEFT_THUMB("BTN_THUMBL", "L3 / Left Stick Click"),
    RIGHT_THUMB("BTN_THUMBR", "R3 / Right Stick Click"),
    DPAD_UP("BTN_DPAD_UP", "D-Pad Up"),
    DPAD_DOWN("BTN_DPAD_DOWN", "D-Pad Down"),
    DPAD_LEFT("BTN_DPAD_LEFT", "D-Pad Left"),
    DPAD_RIGHT("BTN_DPAD_RIGHT", "D-Pad Right");

    companion object {
        private val extraAliases: Map<String, StandardButtonCode> = mapOf(
            "A" to SOUTH,
            "B" to EAST,
            "X" to NORTH,
            "Y" to WEST,
            "LB" to LEFT_SHOULDER,
            "RB" to RIGHT_SHOULDER,
            "SELECT" to BACK,
            "VIEW" to BACK,
            "MENU" to START,
            "MODE" to GUIDE,
            "L3" to LEFT_THUMB,
            "R3" to RIGHT_THUMB
        )

        fun parse(raw: String?): StandardButtonCode? {
            val normalized = raw?.trim()?.uppercase() ?: return null
            return entries.find {
                it.name == normalized || it.legacyLinuxName == normalized
            } ?: extraAliases[normalized]
        }
    }
}

data class ButtonBinding(
    val button: StandardButtonCode,
    val behavior: ButtonBehavior
)

data class ControllerProfile(
    val id: String,
    val displayName: String,
    val description: String,
    val steeringDeadzone: Float = 0.0f,
    val steeringSensitivity: Float = 1.0f,
    val steeringInvert: Boolean = false,
    val steeringCurve: SteeringCurve = SteeringCurve.LINEAR,
    val steeringCurveGamma: Float = 1.0f,
    val steeringAxis: StandardAxisCode = StandardAxisCode.LEFT_X,
    val throttleAxis: StandardAxisCode = StandardAxisCode.RIGHT_TRIGGER,
    val brakeAxis: StandardAxisCode = StandardAxisCode.LEFT_TRIGGER,
    val throttleInvert: Boolean = false,
    val brakeInvert: Boolean = false,
    val buttonBindings: Map<LogicalButton, ButtonBinding>,
    val pulseDurationMs: Long = 35L,
    val pulseGapMs: Long = 35L,
    val clampParkToNeutralInAuto: Boolean = false,
    val multiStepAutoShift: Boolean = false
) {
    init {
        ProfileValidator.validate(this)
    }

    fun summaryString(): String =
        "$id ($displayName | curve=${steeringCurve.name}, gamma=$steeringCurveGamma, " +
            "dz=$steeringDeadzone, sens=$steeringSensitivity, invert=$steeringInvert, " +
            "pulse=${pulseDurationMs}ms)"
}

object ProfileValidator {
    private val validIdRegex = Regex("^[a-z0-9][a-z0-9_-]{0,47}$")

    fun validate(profile: ControllerProfile) {
        if (!validIdRegex.matches(profile.id)) {
            throw ProfileValidationException(
                "Invalid profile id '${profile.id}': must match [a-z0-9][a-z0-9_-]{0,47}"
            )
        }
        if (profile.displayName.isBlank()) {
            throw ProfileValidationException("Profile '${profile.id}' displayName cannot be blank")
        }
        if (profile.steeringDeadzone.isNaN() || profile.steeringDeadzone.isInfinite() ||
            profile.steeringDeadzone !in 0.0f..0.45f
        ) {
            throw ProfileValidationException(
                "Profile '${profile.id}' steeringDeadzone must be in [0.0..0.45], was ${profile.steeringDeadzone}"
            )
        }
        if (profile.steeringSensitivity.isNaN() || profile.steeringSensitivity.isInfinite() ||
            profile.steeringSensitivity !in 0.2f..3.0f
        ) {
            throw ProfileValidationException(
                "Profile '${profile.id}' steeringSensitivity must be in [0.2..3.0], was ${profile.steeringSensitivity}"
            )
        }
        if (profile.steeringCurveGamma.isNaN() || profile.steeringCurveGamma.isInfinite() ||
            profile.steeringCurveGamma !in 0.5f..3.5f
        ) {
            throw ProfileValidationException(
                "Profile '${profile.id}' steeringCurveGamma must be in [0.5..3.5], was ${profile.steeringCurveGamma}"
            )
        }
        if (profile.pulseDurationMs !in 10L..500L) {
            throw ProfileValidationException(
                "Profile '${profile.id}' pulseDurationMs must be in [10..500], was ${profile.pulseDurationMs}"
            )
        }
        if (profile.pulseGapMs !in 10L..250L) {
            throw ProfileValidationException(
                "Profile '${profile.id}' pulseGapMs must be in [10..250], was ${profile.pulseGapMs}"
            )
        }

        val axes = listOf(profile.steeringAxis, profile.throttleAxis, profile.brakeAxis)
        if (axes.distinct().size != axes.size) {
            throw ProfileValidationException(
                "Profile '${profile.id}' has duplicate axis mappings: $axes"
            )
        }

        for (logical in LogicalButton.entries) {
            if (!profile.buttonBindings.containsKey(logical)) {
                throw ProfileValidationException(
                    "Profile '${profile.id}' is missing required button binding for ${logical.name}"
                )
            }
        }

        val seenButtons = HashMap<StandardButtonCode, LogicalButton>()
        for (logical in LogicalButton.entries) {
            val binding = profile.buttonBindings.getValue(logical)
            val existing = seenButtons.put(binding.button, logical)
            if (existing != null) {
                throw ProfileValidationException(
                    "Profile '${profile.id}' assigns duplicate button ${binding.button.name} " +
                        "to both ${existing.name} and ${logical.name}"
                )
            }
        }
    }
}
