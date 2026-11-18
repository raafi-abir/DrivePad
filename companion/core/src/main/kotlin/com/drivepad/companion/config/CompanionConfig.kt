package com.drivepad.companion.config

import com.drivepad.companion.domain.HostPlatform

enum class LogLevel(val priority: Int) {
    ERROR(0),
    WARN(1),
    INFO(2),
    DEBUG(3);

    companion object {
        fun parse(raw: String?): LogLevel? = entries.find { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class DiagnosticsMode {
    DASHBOARD,
    LOGGING,
    SILENT;

    companion object {
        fun parse(raw: String?): DiagnosticsMode? = entries.find { it.name.equals(raw?.trim(), ignoreCase = true) }
    }
}

enum class GamepadBackendType(val cliName: String, val aliases: Set<String> = emptySet()) {
    AUTO("auto"),
    NOOP("noop"),
    UINPUT("uinput", setOf("linux")),
    VIGEM_X360("vigem", setOf("vigem-x360", "vigem_x360", "x360", "windows"));

    fun resolveForHost(platform: HostPlatform = HostPlatform.current()): GamepadBackendType = when (this) {
        AUTO -> when (platform) {
            HostPlatform.LINUX -> UINPUT
            HostPlatform.WINDOWS -> VIGEM_X360
            HostPlatform.MACOS, HostPlatform.OTHER -> NOOP
        }
        else -> this
    }

    companion object {
        fun parse(raw: String?): GamepadBackendType? {
            val normalized = raw?.trim()?.lowercase() ?: return null
            return entries.find {
                it.cliName == normalized ||
                    it.name.equals(normalized, ignoreCase = true) ||
                    normalized in it.aliases
            }
        }
    }
}

data class CompanionConfig(
    val udpPort: Int = DEFAULT_UDP_PORT,
    val networkInterface: String = DEFAULT_BIND_TARGET,
    val watchdogTimeoutMs: Long = DEFAULT_WATCHDOG_TIMEOUT_MS,
    val logLevel: LogLevel = LogLevel.INFO,
    val diagnosticsMode: DiagnosticsMode = DiagnosticsMode.DASHBOARD,
    val backendType: GamepadBackendType = GamepadBackendType.NOOP,
    val virtualDeviceName: String = DEFAULT_VIRTUAL_DEVICE_NAME,
    val vendorId: Int = DEFAULT_VENDOR_ID,
    val productId: Int = DEFAULT_PRODUCT_ID,
    val versionId: Int = DEFAULT_VERSION_ID,
    val activeProfileId: String = DEFAULT_PROFILE_ID
) {
    init {
        require(udpPort in 1..65535) { "UDP port must be in 1..65535, was $udpPort" }
        require(networkInterface.isNotBlank()) { "Network interface / bind address cannot be blank" }
        require(watchdogTimeoutMs in 100L..60_000L) {
            "Watchdog timeout must be in 100..60000 ms, was $watchdogTimeoutMs"
        }
        require(virtualDeviceName.isNotBlank()) { "Virtual device name cannot be blank" }
        require(virtualDeviceName.toByteArray(Charsets.UTF_8).size <= 79) {
            "Virtual device name exceeds Linux UINPUT_MAX_NAME_SIZE (79 UTF-8 bytes + NUL)"
        }
        require(vendorId in 0..0xFFFF) { "Vendor ID must be 16-bit unsigned (0..65535), was $vendorId" }
        require(productId in 0..0xFFFF) { "Product ID must be 16-bit unsigned (0..65535), was $productId" }
        require(versionId in 0..0xFFFF) { "Version ID must be 16-bit unsigned (0..65535), was $versionId" }
        require(activeProfileId.isNotBlank()) { "Active profile ID cannot be blank" }
    }

    companion object {
        const val DEFAULT_UDP_PORT = 42100
        const val DEFAULT_BIND_TARGET = "0.0.0.0"
        const val DEFAULT_WATCHDOG_TIMEOUT_MS = 1_500L
        const val DEFAULT_VIRTUAL_DEVICE_NAME = "DrivePad Virtual Gamepad"
        const val DEFAULT_VENDOR_ID = 0x1209 // pid.codes open-source hardware test VID
        const val DEFAULT_PRODUCT_ID = 0x4450 // "DP" for DrivePad
        const val DEFAULT_VERSION_ID = 0x0001
        const val DEFAULT_PROFILE_ID = "generic"
    }
}
