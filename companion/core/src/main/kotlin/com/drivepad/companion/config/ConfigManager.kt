package com.drivepad.companion.config

import com.drivepad.companion.domain.HostPlatform
import com.drivepad.companion.profile.ProfileManager
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Properties

/**
 * Loads and persists [CompanionConfig] cleanly without elevated privileges:
 * - Linux/Unix: `$XDG_CONFIG_HOME/drivepad-companion/config.properties` (default `~/.config/drivepad-companion/config.properties`)
 * - Windows: `%APPDATA%\drivepad-companion\config.properties` (fallback `~/AppData/Roaming/drivepad-companion/config.properties`)
 */
class ConfigManager(
    private val configFile: Path = defaultConfigPath()
) {
    fun loadOrCreateDefault(): CompanionConfig {
        if (!Files.exists(configFile)) {
            val defaultConfig = CompanionConfig()
            save(defaultConfig)
            return defaultConfig
        }

        if (!Files.isRegularFile(configFile) || runCatching { Files.size(configFile) }.getOrDefault(Long.MAX_VALUE) > MAX_CONFIG_FILE_BYTES) {
            return CompanionConfig()
        }

        val props = Properties()
        val loadedOk = runCatching {
            Files.newInputStream(configFile).use { input ->
                props.load(input)
            }
        }.isSuccess
        if (!loadedOk) {
            return CompanionConfig()
        }

        val port = props.getProperty(KEY_UDP_PORT)?.toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: CompanionConfig.DEFAULT_UDP_PORT

        val iface = props.getProperty(KEY_NETWORK_INTERFACE)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: CompanionConfig.DEFAULT_BIND_TARGET

        val watchdogMs = props.getProperty(KEY_WATCHDOG_TIMEOUT_MS)?.toLongOrNull()
            ?.takeIf { it in 100L..60_000L }
            ?: CompanionConfig.DEFAULT_WATCHDOG_TIMEOUT_MS

        val logLevel = LogLevel.parse(props.getProperty(KEY_LOG_LEVEL)) ?: LogLevel.INFO
        val diagMode = DiagnosticsMode.parse(props.getProperty(KEY_DIAGNOSTICS_MODE)) ?: DiagnosticsMode.DASHBOARD
        val backendType = GamepadBackendType.parse(props.getProperty(KEY_BACKEND_TYPE)) ?: GamepadBackendType.NOOP

        val deviceName = props.getProperty(KEY_VIRTUAL_DEVICE_NAME)
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it.toByteArray(Charsets.UTF_8).size <= 79 }
            ?: CompanionConfig.DEFAULT_VIRTUAL_DEVICE_NAME

        val vendorId = parseUInt16(props.getProperty(KEY_VENDOR_ID)) ?: CompanionConfig.DEFAULT_VENDOR_ID
        val productId = parseUInt16(props.getProperty(KEY_PRODUCT_ID)) ?: CompanionConfig.DEFAULT_PRODUCT_ID
        val versionId = parseUInt16(props.getProperty(KEY_VERSION_ID)) ?: CompanionConfig.DEFAULT_VERSION_ID
        val activeProfileId = props.getProperty(KEY_ACTIVE_PROFILE)
            ?.trim()
            ?.lowercase()
            ?.takeIf { ProfileManager.isValidProfileId(it) }
            ?: CompanionConfig.DEFAULT_PROFILE_ID

        return CompanionConfig(
            udpPort = port,
            networkInterface = iface,
            watchdogTimeoutMs = watchdogMs,
            logLevel = logLevel,
            diagnosticsMode = diagMode,
            backendType = backendType,
            virtualDeviceName = deviceName,
            vendorId = vendorId,
            productId = productId,
            versionId = versionId,
            activeProfileId = activeProfileId
        )
    }

    fun save(config: CompanionConfig) {
        val parent = configFile.parent
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent)
        }
        if (parent != null) {
            ProfileManager.applyRestrictivePermissions(parent, isDirectory = true)
        }
        val props = Properties().apply {
            setProperty(KEY_UDP_PORT, config.udpPort.toString())
            setProperty(KEY_NETWORK_INTERFACE, config.networkInterface)
            setProperty(KEY_WATCHDOG_TIMEOUT_MS, config.watchdogTimeoutMs.toString())
            setProperty(KEY_LOG_LEVEL, config.logLevel.name)
            setProperty(KEY_DIAGNOSTICS_MODE, config.diagnosticsMode.name)
            setProperty(KEY_BACKEND_TYPE, config.backendType.cliName)
            setProperty(KEY_VIRTUAL_DEVICE_NAME, config.virtualDeviceName)
            setProperty(KEY_VENDOR_ID, formatHex16(config.vendorId))
            setProperty(KEY_PRODUCT_ID, formatHex16(config.productId))
            setProperty(KEY_VERSION_ID, formatHex16(config.versionId))
            setProperty(KEY_ACTIVE_PROFILE, config.activeProfileId)
        }
        Files.newOutputStream(configFile).use { out ->
            props.store(out, "DrivePad Cross-Platform Companion Configuration")
        }
        ProfileManager.applyRestrictivePermissions(configFile, isDirectory = false)
    }

    fun configFilePath(): Path = configFile

    companion object {
        const val MAX_CONFIG_FILE_BYTES: Long = 65_536L
        private const val KEY_UDP_PORT = "udp.port"
        private const val KEY_NETWORK_INTERFACE = "network.interface"
        private const val KEY_WATCHDOG_TIMEOUT_MS = "watchdog.timeout_ms"
        private const val KEY_LOG_LEVEL = "logging.level"
        private const val KEY_DIAGNOSTICS_MODE = "diagnostics.mode"
        private const val KEY_BACKEND_TYPE = "backend.type"
        private const val KEY_VIRTUAL_DEVICE_NAME = "backend.device_name"
        private const val KEY_VENDOR_ID = "backend.vendor_id"
        private const val KEY_PRODUCT_ID = "backend.product_id"
        private const val KEY_VERSION_ID = "backend.version_id"
        private const val KEY_ACTIVE_PROFILE = "profile.active"

        private fun parseUInt16(raw: String?): Int? {
            val trimmed = raw?.trim() ?: return null
            val parsed = if (trimmed.startsWith("0x", ignoreCase = true)) {
                trimmed.substring(2).toIntOrNull(16)
            } else {
                trimmed.toIntOrNull()
            }
            return parsed?.takeIf { it in 0..0xFFFF }
        }

        private fun formatHex16(value: Int): String = "0x" + value.toString(16).padStart(4, '0')

        fun defaultConfigDir(
            osName: String? = System.getProperty("os.name"),
            appData: String? = System.getenv("APPDATA"),
            xdgConfigHome: String? = System.getenv("XDG_CONFIG_HOME"),
            userHome: String = System.getProperty("user.home")
        ): Path {
            val baseDir = when (HostPlatform.detect(osName)) {
                HostPlatform.WINDOWS -> {
                    if (!appData.isNullOrBlank()) {
                        Paths.get(appData)
                    } else {
                        Paths.get(userHome, "AppData", "Roaming")
                    }
                }
                else -> {
                    if (!xdgConfigHome.isNullOrBlank()) {
                        Paths.get(xdgConfigHome)
                    } else {
                        Paths.get(userHome, ".config")
                    }
                }
            }
            return baseDir.resolve("drivepad-companion")
        }

        fun defaultConfigPath(
            osName: String? = System.getProperty("os.name"),
            appData: String? = System.getenv("APPDATA"),
            xdgConfigHome: String? = System.getenv("XDG_CONFIG_HOME"),
            userHome: String = System.getProperty("user.home")
        ): Path = defaultConfigDir(osName, appData, xdgConfigHome, userHome).resolve("config.properties")
    }
}
