package com.drivepad.companion

import com.drivepad.companion.backend.uinput.UInputAvailabilityProbe
import com.drivepad.companion.backend.uinput.UInputDeviceConfig
import com.drivepad.companion.backend.uinput.UInputGamepadBackend
import com.drivepad.companion.backend.windows.ViGEmX360GamepadBackend
import com.drivepad.companion.backend.windows.WindowsHostProbe
import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.diagnostics.MonotonicClock
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.diagnostics.SystemMonotonicClock
import com.drivepad.companion.domain.GamepadBackend
import com.drivepad.companion.domain.HostPlatform
import com.drivepad.companion.domain.NoOpGamepadBackend
import com.drivepad.companion.profile.ControllerProfile
import com.drivepad.companion.profile.ProfilePresets

/**
 * Resolves configured or auto-detected virtual gamepad backends across Linux (`uinput`)
 * and Windows (`ViGEm` Xbox 360), defaulting safely to [NoOpGamepadBackend] on unsupported hosts
 * or when `noop` is requested.
 */
object PlatformBackendFactory {

    fun createBackend(
        config: CompanionConfig,
        profile: ControllerProfile = ProfilePresets.builtInById(config.activeProfileId) ?: ProfilePresets.generic(),
        clock: MonotonicClock = SystemMonotonicClock,
        logger: StructuredLogger = StructuredLogger(minLevel = config.logLevel),
        hostPlatform: HostPlatform = HostPlatform.current()
    ): GamepadBackend {
        val resolvedType = config.backendType.resolveForHost(hostPlatform)
        return when (resolvedType) {
            GamepadBackendType.AUTO,
            GamepadBackendType.NOOP -> NoOpGamepadBackend(
                probeAvailability = { probeHostAvailability(hostPlatform) },
                activeProfileId = profile.id,
                activeProfileSummary = profile.summaryString(),
                hostPlatform = hostPlatform
            )
            GamepadBackendType.UINPUT -> UInputGamepadBackend(
                deviceConfig = UInputDeviceConfig.fromProfile(config, profile),
                profile = profile,
                clock = clock,
                logger = logger
            )
            GamepadBackendType.VIGEM_X360 -> ViGEmX360GamepadBackend(
                deviceName = config.virtualDeviceName,
                vendorId = config.vendorId,
                productId = config.productId,
                profile = profile,
                clock = clock,
                logger = logger
            )
        }
    }

    fun probeHostAvailability(platform: HostPlatform = HostPlatform.current()): Triple<Boolean, Boolean, String> =
        when (platform) {
            HostPlatform.LINUX -> {
                val avail = UInputAvailabilityProbe.probe()
                Triple(avail.supportedOs, avail.deviceExists && avail.deviceWritable, avail.summary)
            }
            HostPlatform.WINDOWS -> {
                val avail = WindowsHostProbe.probe()
                Triple(avail.supportedOs, avail.clientLibraryFound, avail.summary)
            }
            HostPlatform.MACOS, HostPlatform.OTHER -> {
                Triple(false, false, "No virtual gamepad driver registered for ${platform.displayName}; using NoOp")
            }
        }
}
