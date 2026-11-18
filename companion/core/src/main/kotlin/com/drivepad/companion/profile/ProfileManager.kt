package com.drivepad.companion.profile

import com.drivepad.companion.config.ConfigManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties

/**
 * Persists and loads validated [ControllerProfile] `.properties` files cleanly across OSes:
 * - Linux/Unix: `$XDG_CONFIG_HOME/drivepad-companion/profiles/` (`~/.config/drivepad-companion/profiles/`)
 * - Windows: `%APPDATA%\drivepad-companion\profiles\`
 */
class ProfileManager(
    private val profilesDir: Path = defaultProfilesDir()
) {
    fun ensureBuiltInProfilesSeeded() {
        if (!Files.exists(profilesDir)) {
            Files.createDirectories(profilesDir)
        }
        applyRestrictivePermissions(profilesDir, isDirectory = true)
        for (preset in ProfilePresets.allBuiltIns()) {
            val file = profilePath(preset.id)
            if (!Files.exists(file)) {
                save(preset)
            }
        }
    }

    fun resolveProfile(profileId: String): ControllerProfile {
        val normalizedId = normalizeProfileId(profileId)
        ensureBuiltInProfilesSeeded()

        val customFile = profilePath(normalizedId)
        if (Files.exists(customFile)) {
            return loadFromFile(customFile, expectedId = normalizedId)
        }

        return ProfilePresets.builtInById(normalizedId)
            ?: throw ProfileValidationException(
                "Unknown profile '$profileId'. Available profiles: ${listProfileIds().joinToString(", ")}"
            )
    }

    fun listProfileIds(): List<String> {
        ensureBuiltInProfilesSeeded()
        val ids = linkedSetOf<String>()
        ProfilePresets.allBuiltIns().forEach { ids.add(it.id) }
        Files.list(profilesDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".properties") }
                .forEach { path ->
                    val name = path.fileName.toString().removeSuffix(".properties")
                    if (isValidProfileId(name)) ids.add(name)
                }
        }
        return ids.sorted()
    }

    fun listProfiles(): List<ControllerProfile> =
        listProfileIds().mapNotNull { id ->
            runCatching { resolveProfile(id) }.getOrNull()
        }

    fun save(profile: ControllerProfile) {
        validateProfileId(profile.id)
        ProfileValidator.validate(profile)
        if (!Files.exists(profilesDir)) {
            Files.createDirectories(profilesDir)
        }
        applyRestrictivePermissions(profilesDir, isDirectory = true)
        val props = toProperties(profile)
        val target = profilePath(profile.id)
        Files.newOutputStream(target).use { out ->
            props.store(out, "DrivePad Companion Controller Profile (${profile.displayName})")
        }
        applyRestrictivePermissions(target, isDirectory = false)
    }

    fun loadFromFile(file: Path, expectedId: String? = null): ControllerProfile {
        if (!Files.exists(file) || !Files.isRegularFile(file)) {
            throw ProfileValidationException("Profile file does not exist or is not a regular file: $file")
        }
        val fileSize = Files.size(file)
        if (fileSize > MAX_PROFILE_FILE_BYTES) {
            throw ProfileValidationException(
                "Profile file exceeds maximum safe size ($MAX_PROFILE_FILE_BYTES bytes): $file"
            )
        }
        val props = Properties()
        try {
            Files.newInputStream(file).use { input ->
                props.load(input)
            }
        } catch (e: Exception) {
            throw ProfileValidationException("Malformed profile properties in $file: ${e.message}")
        }
        val fallbackId = expectedId ?: file.fileName.toString().removeSuffix(".properties")
        return fromProperties(props, fallbackId)
    }

    fun profilePath(profileId: String): Path {
        val normalizedId = normalizeProfileId(profileId)
        val normalizedDir = profilesDir.toAbsolutePath().normalize()
        val resolved = normalizedDir.resolve("$normalizedId.properties").normalize()
        if (resolved.parent != normalizedDir || !resolved.startsWith(normalizedDir)) {
            throw ProfileValidationException("Profile path escapes profiles directory: $profileId")
        }
        return resolved
    }

    fun profilesDirectory(): Path = profilesDir

    companion object {
        private const val KEY_ID = "profile.id"
        private const val KEY_DISPLAY_NAME = "profile.display_name"
        private const val KEY_DESCRIPTION = "profile.description"
        private const val KEY_STEERING_DEADZONE = "steering.deadzone"
        private const val KEY_STEERING_SENSITIVITY = "steering.sensitivity"
        private const val KEY_STEERING_INVERT = "steering.invert"
        private const val KEY_STEERING_CURVE = "steering.curve"
        private const val KEY_STEERING_GAMMA = "steering.curve_gamma"
        private const val KEY_AXIS_STEERING = "axis.steering"
        private const val KEY_AXIS_THROTTLE = "axis.throttle"
        private const val KEY_AXIS_BRAKE = "axis.brake"
        private const val KEY_THROTTLE_INVERT = "pedal.throttle_invert"
        private const val KEY_BRAKE_INVERT = "pedal.brake_invert"
        private const val KEY_PULSE_DURATION_MS = "timing.pulse_duration_ms"
        private const val KEY_PULSE_GAP_MS = "timing.pulse_gap_ms"
        private const val KEY_CLAMP_PARK = "transmission.clamp_park_to_neutral_in_auto"
        private const val KEY_MULTI_STEP_AUTO = "transmission.multi_step_auto_shift"

        const val MAX_PROFILE_FILE_BYTES: Long = 65_536L
        private val SAFE_PROFILE_ID_REGEX = Regex("^[a-z0-9][a-z0-9_-]{0,63}$")

        fun isValidProfileId(rawId: String): Boolean {
            val trimmed = rawId.trim().lowercase()
            return SAFE_PROFILE_ID_REGEX.matches(trimmed)
        }

        fun validateProfileId(rawId: String): String {
            val trimmed = rawId.trim().lowercase()
            if (!SAFE_PROFILE_ID_REGEX.matches(trimmed)) {
                throw ProfileValidationException(
                    "Invalid profile id '$rawId'. Only [a-z0-9_-] up to 64 characters is allowed."
                )
            }
            return trimmed
        }

        fun normalizeProfileId(rawId: String): String {
            val trimmed = rawId.trim().lowercase()
            val mapped = if (trimmed == ProfilePresets.DEFAULT_ALIAS) ProfilePresets.GENERIC_ID else trimmed
            return validateProfileId(mapped)
        }

        internal fun applyRestrictivePermissions(path: Path, isDirectory: Boolean) {
            runCatching {
                val perms = PosixFilePermissions.fromString(if (isDirectory) "rwx------" else "rw-------")
                Files.setPosixFilePermissions(path, perms)
            }
        }

        fun toProperties(profile: ControllerProfile): Properties = Properties().apply {
            setProperty(KEY_ID, profile.id)
            setProperty(KEY_DISPLAY_NAME, profile.displayName)
            setProperty(KEY_DESCRIPTION, profile.description)
            setProperty(KEY_STEERING_DEADZONE, profile.steeringDeadzone.toString())
            setProperty(KEY_STEERING_SENSITIVITY, profile.steeringSensitivity.toString())
            setProperty(KEY_STEERING_INVERT, profile.steeringInvert.toString())
            setProperty(KEY_STEERING_CURVE, profile.steeringCurve.name)
            setProperty(KEY_STEERING_GAMMA, profile.steeringCurveGamma.toString())
            setProperty(KEY_AXIS_STEERING, profile.steeringAxis.specName)
            setProperty(KEY_AXIS_THROTTLE, profile.throttleAxis.specName)
            setProperty(KEY_AXIS_BRAKE, profile.brakeAxis.specName)
            setProperty(KEY_THROTTLE_INVERT, profile.throttleInvert.toString())
            setProperty(KEY_BRAKE_INVERT, profile.brakeInvert.toString())
            setProperty(KEY_PULSE_DURATION_MS, profile.pulseDurationMs.toString())
            setProperty(KEY_PULSE_GAP_MS, profile.pulseGapMs.toString())
            setProperty(KEY_CLAMP_PARK, profile.clampParkToNeutralInAuto.toString())
            setProperty(KEY_MULTI_STEP_AUTO, profile.multiStepAutoShift.toString())

            for (logical in LogicalButton.entries) {
                val binding = profile.buttonBindings.getValue(logical)
                setProperty("button.${logical.propertyKey}.code", binding.button.name)
                setProperty("button.${logical.propertyKey}.behavior", binding.behavior.name)
            }
        }

        fun fromProperties(props: Properties, fallbackId: String): ControllerProfile {
            val base = ProfilePresets.builtInById(fallbackId) ?: ProfilePresets.generic()

            val id = normalizeProfileId(props.getProperty(KEY_ID) ?: fallbackId)
            val displayName = props.getProperty(KEY_DISPLAY_NAME)?.trim() ?: base.displayName
            val description = props.getProperty(KEY_DESCRIPTION)?.trim() ?: base.description

            val deadzone = parseFloat(props, KEY_STEERING_DEADZONE, base.steeringDeadzone)
            val sensitivity = parseFloat(props, KEY_STEERING_SENSITIVITY, base.steeringSensitivity)
            val invert = parseBoolean(props, KEY_STEERING_INVERT, base.steeringInvert)

            val curveRaw = props.getProperty(KEY_STEERING_CURVE)
            val curve = if (curveRaw == null) {
                base.steeringCurve
            } else {
                SteeringCurve.parse(curveRaw)
                    ?: throw ProfileValidationException("Invalid $KEY_STEERING_CURVE: '$curveRaw'")
            }

            val gamma = parseFloat(props, KEY_STEERING_GAMMA, base.steeringCurveGamma)

            val steeringAxis = parseAxis(props, KEY_AXIS_STEERING, base.steeringAxis)
            val throttleAxis = parseAxis(props, KEY_AXIS_THROTTLE, base.throttleAxis)
            val brakeAxis = parseAxis(props, KEY_AXIS_BRAKE, base.brakeAxis)

            val throttleInvert = parseBoolean(props, KEY_THROTTLE_INVERT, base.throttleInvert)
            val brakeInvert = parseBoolean(props, KEY_BRAKE_INVERT, base.brakeInvert)
            val pulseDurationMs = parseLong(props, KEY_PULSE_DURATION_MS, base.pulseDurationMs)
            val pulseGapMs = parseLong(props, KEY_PULSE_GAP_MS, base.pulseGapMs)
            val clampPark = parseBoolean(props, KEY_CLAMP_PARK, base.clampParkToNeutralInAuto)
            val multiStepAuto = parseBoolean(props, KEY_MULTI_STEP_AUTO, base.multiStepAutoShift)

            val bindings = linkedMapOf<LogicalButton, ButtonBinding>()
            for (logical in LogicalButton.entries) {
                val baseBinding = base.buttonBindings.getValue(logical)
                val codeKey = "button.${logical.propertyKey}.code"
                val behaviorKey = "button.${logical.propertyKey}.behavior"

                val codeRaw = props.getProperty(codeKey)
                val code = if (codeRaw == null) {
                    baseBinding.button
                } else {
                    StandardButtonCode.parse(codeRaw)
                        ?: throw ProfileValidationException("Invalid $codeKey: '$codeRaw'")
                }

                val behaviorRaw = props.getProperty(behaviorKey)
                val behavior = if (behaviorRaw == null) {
                    baseBinding.behavior
                } else {
                    ButtonBehavior.parse(behaviorRaw)
                        ?: throw ProfileValidationException("Invalid $behaviorKey: '$behaviorRaw'")
                }

                bindings[logical] = ButtonBinding(code, behavior)
            }

            return ControllerProfile(
                id = id,
                displayName = displayName,
                description = description,
                steeringDeadzone = deadzone,
                steeringSensitivity = sensitivity,
                steeringInvert = invert,
                steeringCurve = curve,
                steeringCurveGamma = gamma,
                steeringAxis = steeringAxis,
                throttleAxis = throttleAxis,
                brakeAxis = brakeAxis,
                throttleInvert = throttleInvert,
                brakeInvert = brakeInvert,
                buttonBindings = bindings,
                pulseDurationMs = pulseDurationMs,
                pulseGapMs = pulseGapMs,
                clampParkToNeutralInAuto = clampPark,
                multiStepAutoShift = multiStepAuto
            )
        }

        private fun parseAxis(props: Properties, key: String, defaultValue: StandardAxisCode): StandardAxisCode {
            val raw = props.getProperty(key) ?: return defaultValue
            return StandardAxisCode.parse(raw)
                ?: throw ProfileValidationException("Invalid $key axis code: '$raw'")
        }

        private fun parseFloat(props: Properties, key: String, defaultValue: Float): Float {
            val raw = props.getProperty(key) ?: return defaultValue
            return raw.trim().toFloatOrNull()
                ?: throw ProfileValidationException("Invalid float for $key: '$raw'")
        }

        private fun parseLong(props: Properties, key: String, defaultValue: Long): Long {
            val raw = props.getProperty(key) ?: return defaultValue
            return raw.trim().toLongOrNull()
                ?: throw ProfileValidationException("Invalid integer for $key: '$raw'")
        }

        private fun parseBoolean(props: Properties, key: String, defaultValue: Boolean): Boolean {
            val raw = props.getProperty(key)?.trim()?.lowercase() ?: return defaultValue
            return when (raw) {
                "true" -> true
                "false" -> false
                else -> throw ProfileValidationException("Invalid boolean for $key: '$raw' (use true or false)")
            }
        }

        fun defaultProfilesDir(
            osName: String? = System.getProperty("os.name"),
            appData: String? = System.getenv("APPDATA"),
            xdgConfigHome: String? = System.getenv("XDG_CONFIG_HOME"),
            userHome: String = System.getProperty("user.home")
        ): Path = ConfigManager.defaultConfigDir(osName, appData, xdgConfigHome, userHome).resolve("profiles")
    }
}
