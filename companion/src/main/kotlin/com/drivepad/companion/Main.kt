package com.drivepad.companion

import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.config.ConfigManager
import com.drivepad.companion.config.DiagnosticsMode
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.MonotonicClock
import com.drivepad.companion.diagnostics.ReleaseMetadata
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.diagnostics.SystemMonotonicClock
import com.drivepad.companion.diagnostics.TerminalDashboard
import com.drivepad.companion.domain.BackendDiagnosticsSnapshot
import com.drivepad.companion.domain.ControllerOutput
import com.drivepad.companion.domain.ControllerStateProcessor
import com.drivepad.companion.domain.GamepadBackend
import com.drivepad.companion.domain.HostPlatform
import com.drivepad.companion.monitoring.ConnectionMonitor
import com.drivepad.companion.monitoring.LatencyMonitor
import com.drivepad.companion.monitoring.PacketStatistics
import com.drivepad.companion.network.ConnectionManager
import com.drivepad.companion.network.ProtocolHandler
import com.drivepad.companion.network.UdpServer
import com.drivepad.companion.profile.ControllerProfile
import com.drivepad.companion.profile.ProfileManager
import com.drivepad.companion.profile.ProfilePresets
import com.drivepad.companion.profile.ProfileValidationException
import com.drivepad.companion.safety.InputWatchdog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

/**
 * Coordinates the full lifecycle of the headless cross-platform DrivePad Companion runtime
 * across Linux (`uinput`), Windows (`ViGEm` X360), and safe `noop` testing mode.
 */
class DrivePadCompanionApp(
    val config: CompanionConfig,
    val profile: ControllerProfile = ProfilePresets.builtInById(config.activeProfileId) ?: ProfilePresets.generic(),
    outputs: List<ControllerOutput> = emptyList(),
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val logger: StructuredLogger = StructuredLogger(minLevel = config.logLevel),
    backendOverride: GamepadBackend? = null
) : AutoCloseable {
    val gamepadBackend: GamepadBackend = backendOverride ?: createBackend(config, profile, clock, logger)
    val connectionMonitor = ConnectionMonitor()
    val packetStatistics = PacketStatistics()
    val latencyMonitor = LatencyMonitor()
    val stateProcessor = ControllerStateProcessor(outputs = listOf(gamepadBackend) + outputs)
    val connectionManager = ConnectionManager(
        connectionMonitor = connectionMonitor,
        activeSessionGuardWindowNs = config.watchdogTimeoutMs * 1_000_000L
    )
    val watchdog = InputWatchdog(
        timeoutMs = config.watchdogTimeoutMs,
        stateProcessor = stateProcessor,
        connectionMonitor = connectionMonitor,
        onSessionTimedOut = { connectionManager.onWatchdogTimeout() },
        clock = clock,
        logger = logger
    )
    val protocolHandler = ProtocolHandler(
        connectionManager = connectionManager,
        connectionMonitor = connectionMonitor,
        stateProcessor = stateProcessor,
        packetStatistics = packetStatistics,
        latencyMonitor = latencyMonitor,
        watchdog = watchdog,
        clock = clock,
        logger = logger
    )
    val udpServer = UdpServer(
        port = config.udpPort,
        bindTarget = config.networkInterface,
        protocolHandler = protocolHandler,
        logger = logger
    )

    private var appScope: CoroutineScope? = null
    private var dashboard: TerminalDashboard? = null

    fun start() {
        if (appScope != null) return
        gamepadBackend.initialize()
        val backendDiag = gamepadBackend.diagnostics()

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        appScope = scope

        udpServer.start(scope)
        watchdog.start(scope)

        val endpoint = "${udpServer.boundAddress}:${udpServer.boundPort}"
        val termDash = TerminalDashboard(
            mode = config.diagnosticsMode,
            listeningEndpoint = endpoint,
            stateProvider = stateProcessor,
            connectionMonitor = connectionMonitor,
            packetStatistics = packetStatistics,
            latencyMonitor = latencyMonitor,
            backendDiagnosticsProvider = { gamepadBackend.diagnostics() },
            logger = logger
        )
        dashboard = termDash
        termDash.start(scope)

        logger.info(
            "DrivePadCompanion",
            "Companion started",
            "drivepad_version" to ReleaseMetadata.drivepadVersion,
            "companion_version" to ReleaseMetadata.companionVersion,
            "protocol_version" to ReleaseMetadata.protocolVersion,
            "host" to HostPlatform.current().displayName,
            "endpoint" to endpoint,
            "watchdog_ms" to config.watchdogTimeoutMs,
            "mode" to config.diagnosticsMode.name,
            "backend" to backendDiag.backendType.cliName,
            "backend_ready" to backendDiag.driverOrDeviceReady,
            "availability" to backendDiag.availabilitySummary,
            "profile" to profile.id,
            "virtual_device_active" to backendDiag.isVirtualDeviceActive
        )

        if (isRunningAsRoot()) {
            logger.warn(
                "DrivePadCompanion",
                "Running as root is strongly discouraged; configure udev rule 99-drivepad-uinput.rules and run in group 'uinput'"
            )
        }

        if (!backendDiag.isVirtualDeviceActive && backendDiag.backendType != GamepadBackendType.NOOP) {
            logger.warn(
                "DrivePadCompanion",
                "Virtual gamepad backend did not activate; UDP telemetry will still run safely",
                "backend" to backendDiag.backendType.cliName,
                "availability" to backendDiag.availabilitySummary,
                "error" to (backendDiag.lastError ?: "unknown")
            )
        }
    }

    fun stop() {
        dashboard?.stop()
        dashboard = null
        watchdog.stop()
        udpServer.stop()
        stateProcessor.resetToFailsafe(clock.nanoTime())
        runCatching { gamepadBackend.close() }
        appScope?.cancel()
        appScope = null
        logger.info("DrivePadCompanion", "Companion shutdown complete")
    }

    override fun close() {
        stop()
    }

    companion object {
        fun createBackend(
            config: CompanionConfig,
            profile: ControllerProfile = ProfilePresets.builtInById(config.activeProfileId) ?: ProfilePresets.generic(),
            clock: MonotonicClock = SystemMonotonicClock,
            logger: StructuredLogger = StructuredLogger(minLevel = config.logLevel)
        ): GamepadBackend = PlatformBackendFactory.createBackend(
            config = config,
            profile = profile,
            clock = clock,
            logger = logger
        )
    }
}

data class CliParseResult(
    val showHelp: Boolean = false,
    val showVersion: Boolean = false,
    val runDoctorCheck: Boolean = false,
    val listProfiles: Boolean = false,
    val listInterfaces: Boolean = false,
    val configPathOverride: String? = null,
    val saveUpdatedConfig: Boolean = false,
    val portOverride: Int? = null,
    val interfaceOverride: String? = null,
    val watchdogMsOverride: Long? = null,
    val logLevelOverride: LogLevel? = null,
    val diagnosticsModeOverride: DiagnosticsMode? = null,
    val backendOverride: GamepadBackendType? = null,
    val deviceNameOverride: String? = null,
    val profileOverride: String? = null
)

fun parseCliArgs(args: Array<String>): CliParseResult {
    var result = CliParseResult()
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--help", "-h" -> result = result.copy(showHelp = true)
            "--version", "-V" -> result = result.copy(showVersion = true)
            "--check", "--doctor" -> result = result.copy(runDoctorCheck = true)
            "--list-profiles" -> result = result.copy(listProfiles = true)
            "--list-interfaces" -> result = result.copy(listInterfaces = true)
            "--config", "-c" -> {
                val path = args.getOrNull(++i)
                    ?: throw IllegalArgumentException("Missing value for --config")
                result = result.copy(configPathOverride = path)
            }
            "--save-config" -> result = result.copy(saveUpdatedConfig = true)
            "--port", "-p" -> {
                val p = args.getOrNull(++i)?.toIntOrNull()
                    ?: throw IllegalArgumentException("Invalid value for --port")
                result = result.copy(portOverride = p)
            }
            "--interface", "-i", "--bind", "-b" -> {
                val iface = args.getOrNull(++i)
                    ?: throw IllegalArgumentException("Missing value for --interface")
                result = result.copy(interfaceOverride = iface)
            }
            "--watchdog-ms", "-w" -> {
                val w = args.getOrNull(++i)?.toLongOrNull()
                    ?: throw IllegalArgumentException("Invalid value for --watchdog-ms")
                result = result.copy(watchdogMsOverride = w)
            }
            "--log-level", "-l" -> {
                val lvl = LogLevel.parse(args.getOrNull(++i))
                    ?: throw IllegalArgumentException("Invalid --log-level (use ERROR, WARN, INFO, DEBUG)")
                result = result.copy(logLevelOverride = lvl)
            }
            "--diagnostics", "-d" -> {
                val mode = DiagnosticsMode.parse(args.getOrNull(++i))
                    ?: throw IllegalArgumentException("Invalid --diagnostics (use DASHBOARD, LOGGING, SILENT)")
                result = result.copy(diagnosticsModeOverride = mode)
            }
            "--backend", "-B" -> {
                val backend = GamepadBackendType.parse(args.getOrNull(++i))
                    ?: throw IllegalArgumentException("Invalid --backend (use auto, noop, uinput, or vigem)")
                result = result.copy(backendOverride = backend)
            }
            "--device-name" -> {
                val name = args.getOrNull(++i)
                    ?: throw IllegalArgumentException("Missing value for --device-name")
                result = result.copy(deviceNameOverride = name)
            }
            "--profile", "-P" -> {
                val profile = args.getOrNull(++i)?.trim()?.takeIf { it.isNotEmpty() }
                    ?: throw IllegalArgumentException("Missing value for --profile (e.g., generic, ets2)")
                result = result.copy(profileOverride = profile)
            }
            else -> throw IllegalArgumentException("Unknown CLI option: ${args[i]}")
        }
        i++
    }
    return result
}

fun buildVersionReport(
    selectedBackend: GamepadBackendType = GamepadBackendType.AUTO,
    hostPlatform: HostPlatform = HostPlatform.current(),
    probe: Triple<Boolean, Boolean, String> = PlatformBackendFactory.probeHostAvailability(hostPlatform)
): String {
    val snapshot = ReleaseMetadata.snapshot(
        selectedBackend = selectedBackend.resolveForHost(hostPlatform),
        backendAvailable = probe.second,
        backendAvailabilitySummary = probe.third,
        hostPlatform = hostPlatform
    )
    return ReleaseMetadata.formatVersionSummary(snapshot).trimEnd()
}

fun buildDoctorReport(
    config: CompanionConfig,
    configPath: String,
    profilesDir: String,
    hostPlatform: HostPlatform = HostPlatform.current(),
    probe: Triple<Boolean, Boolean, String> = PlatformBackendFactory.probeHostAvailability(hostPlatform),
    interfaces: List<UdpServer.DiscoveredInterface> = UdpServer.listUpIpv4Interfaces()
): String = buildString {
    val effectiveBackend = config.backendType.resolveForHost(hostPlatform)
    appendLine("================================================================")
    appendLine("  DrivePad Companion Readiness & Environment Check")
    appendLine("================================================================")
    appendLine(buildVersionReport(selectedBackend = config.backendType, hostPlatform = hostPlatform, probe = probe))
    appendLine("Configured Backend  : ${config.backendType.cliName} (resolved: ${effectiveBackend.cliName})")
    appendLine("Config File         : $configPath")
    appendLine("Profiles Directory  : $profilesDir")
    appendLine("Active Profile      : ${config.activeProfileId}")
    appendLine("UDP Listen Target   : ${config.networkInterface}:${config.udpPort} (Watchdog: ${config.watchdogTimeoutMs} ms)")
    appendLine()
    appendLine("Detected Non-Loopback IPv4 Interfaces:")
    if (interfaces.isEmpty()) {
        appendLine("  (none detected — connect USB tethering or Wi-Fi LAN)")
    } else {
        for (iface in interfaces) {
            appendLine("  - ${iface.ipv4Address}  |  name=${iface.name}  |  display=${iface.displayName}")
        }
    }
    appendLine()
    appendLine("Platform Guidance:")
    when (hostPlatform) {
        HostPlatform.LINUX -> {
            if (isRunningAsRoot()) {
                appendLine("  [WARN] Running as root (UID 0) is discouraged. Use non-root group 'uinput' + udev rules.")
            }
            if (!probe.second) {
                appendLine("  [!] Linux /dev/uinput is not ready: ${probe.third}")
                appendLine("      1) sudo modprobe uinput")
                appendLine("      2) Install udev rule for group 'uinput' (MODE=\"0660\") and add user to 'uinput'")
                appendLine("      3) Allow inbound UDP port ${config.udpPort} in ufw/firewalld")
            } else {
                appendLine("  [OK] /dev/uinput ready for unprivileged virtual gamepad creation.")
                appendLine("       Ensure inbound UDP port ${config.udpPort} is allowed in your firewall.")
            }
        }
        HostPlatform.WINDOWS -> {
            if (!probe.second) {
                appendLine("  [!] Windows ViGEmClient.dll not found in probed paths.")
            } else {
                appendLine("  [OK] ViGEmClient.dll located (${probe.third}).")
            }
            appendLine("  [INFO] ViGEmBus v1.22.0 is archived/EOL (Nov 2023) and must be installed manually.")
            appendLine("         Do NOT disable HVCI/Core Isolation or Secure Boot; verify Windows firewall allows UDP ${config.udpPort}.")
        }
        else -> {
            appendLine("  [INFO] No kernel virtual gamepad driver for ${hostPlatform.displayName}; use --backend noop for UDP diagnostics.")
        }
    }
    append("================================================================")
}

fun main(args: Array<String>) {
    val cli = try {
        parseCliArgs(args)
    } catch (e: IllegalArgumentException) {
        System.err.println("Argument error: ${e.message}")
        printUsage()
        return
    }

    if (cli.showHelp) {
        printUsage()
        return
    }

    if (cli.showVersion) {
        println(buildVersionReport(selectedBackend = cli.backendOverride ?: GamepadBackendType.AUTO))
        return
    }

    if (cli.listInterfaces) {
        println("Active non-loopback IPv4 interfaces (${HostPlatform.current().displayName}):")
        val ifaces = UdpServer.listUpIpv4Interfaces()
        if (ifaces.isEmpty()) {
            println("  (none detected)")
        } else {
            for (iface in ifaces) {
                println("  - ${iface.ipv4Address}  |  name=${iface.name}  |  display=${iface.displayName}")
            }
        }
        return
    }

    val configPath = cli.configPathOverride?.let { Paths.get(it) } ?: ConfigManager.defaultConfigPath()
    val profileDir = configPath.parent?.resolve("profiles") ?: ProfileManager.defaultProfilesDir()
    val profileManager = ProfileManager(profileDir)

    if (cli.listProfiles) {
        profileManager.ensureBuiltInProfilesSeeded()
        println("Available DrivePad Companion controller profiles (dir: ${profileManager.profilesDirectory()}):")
        for (id in profileManager.listProfileIds()) {
            val loaded = runCatching { profileManager.resolveProfile(id) }
            loaded.onSuccess { p ->
                println("  - ${p.id}: ${p.displayName} — ${p.description}")
            }.onFailure { err ->
                println("  - $id: [INVALID] ${err.message}")
            }
        }
        return
    }

    val configManager = ConfigManager(configPath)
    val baseConfig = configManager.loadOrCreateDefault()

    val requestedProfileId = ProfileManager.normalizeProfileId(
        cli.profileOverride ?: baseConfig.activeProfileId
    )
    val activeProfile = try {
        profileManager.resolveProfile(requestedProfileId)
    } catch (e: ProfileValidationException) {
        System.err.println("Profile error: ${e.message}")
        return
    }

    val effectiveConfig = try {
        baseConfig.copy(
            udpPort = cli.portOverride ?: baseConfig.udpPort,
            networkInterface = cli.interfaceOverride ?: baseConfig.networkInterface,
            watchdogTimeoutMs = cli.watchdogMsOverride ?: baseConfig.watchdogTimeoutMs,
            logLevel = cli.logLevelOverride ?: baseConfig.logLevel,
            diagnosticsMode = cli.diagnosticsModeOverride ?: baseConfig.diagnosticsMode,
            backendType = cli.backendOverride ?: baseConfig.backendType,
            virtualDeviceName = cli.deviceNameOverride ?: baseConfig.virtualDeviceName,
            activeProfileId = activeProfile.id
        )
    } catch (e: IllegalArgumentException) {
        System.err.println("Configuration error: ${e.message}")
        printUsage()
        return
    }

    if (cli.saveUpdatedConfig) {
        configManager.save(effectiveConfig)
    }

    if (cli.runDoctorCheck) {
        println(
            buildDoctorReport(
                config = effectiveConfig,
                configPath = configPath.toAbsolutePath().toString(),
                profilesDir = profileDir.toAbsolutePath().toString()
            )
        )
        return
    }

    val companion = DrivePadCompanionApp(effectiveConfig, profile = activeProfile)
    val stopSignal = CompletableDeferred<Unit>()

    Runtime.getRuntime().addShutdownHook(
        Thread {
            companion.stop()
            stopSignal.complete(Unit)
        }
    )

    companion.start()
    runBlocking {
        stopSignal.await()
    }
}

private fun printUsage() {
    println(
        """
        DrivePad Companion v${ReleaseMetadata.companionVersion} (Protocol v${ReleaseMetadata.protocolVersion} | Linux uinput & Windows ViGEm)
        Usage: drivepad-companion [options]
          --port, -p <1..65535>               UDP listening port (default: 42100)
          --interface, -i <if|display|ipv4>   Linux iface (usb0), Windows adapter/display name, or IPv4 (default: 0.0.0.0)
          --list-interfaces                   List active non-loopback IPv4 interfaces and exit
          --watchdog-ms, -w <100..60000>      Failsafe watchdog timeout in ms (default: 1500)
          --backend, -B <auto|noop|uinput|vigem>
                                              Gamepad backend: auto (OS default), noop (dry-run), uinput (Linux), vigem (Windows X360)
          --profile, -P <id>                  Controller profile (generic, ets2, or custom id; default: generic)
          --list-profiles                     List available controller profiles and exit
          --device-name <name>                Virtual device name label (default: DrivePad Virtual Gamepad)
          --log-level, -l <LEVEL>             ERROR | WARN | INFO | DEBUG (default: INFO)
          --diagnostics, -d <MODE>            DASHBOARD | LOGGING | SILENT (default: DASHBOARD)
          --config, -c <path>                 Custom config.properties path (Linux: ~/.config/..., Windows: %APPDATA%\...)
          --save-config                       Persist CLI overrides into config.properties
          --check, --doctor                   Print version, backend availability, interfaces & config diagnostics and exit
          --version, -V                       Show DrivePad/Companion/Protocol version and backend availability
          --help, -h                          Show this help message
        """.trimIndent()
    )
}

internal fun isRunningAsRoot(
    hostPlatform: HostPlatform = HostPlatform.current(),
    userName: String? = System.getProperty("user.name"),
    procStatusText: String? = runCatching {
        java.nio.file.Paths.get("/proc/self/status").toFile().takeIf { it.isFile }?.readText()
    }.getOrNull()
): Boolean {
    if (hostPlatform != HostPlatform.LINUX) return false
    val uidLine = procStatusText
        ?.lineSequence()
        ?.firstOrNull { it.startsWith("Uid:") }
    if (uidLine != null) {
        val parts = uidLine.removePrefix("Uid:").trim().split(Regex("\\s+"))
        val effectiveUid = parts.getOrNull(1)?.toIntOrNull() ?: parts.getOrNull(0)?.toIntOrNull()
        if (effectiveUid != null) return effectiveUid == 0
    }
    return userName == "root"
}
