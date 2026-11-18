package com.drivepad.companion.config

import com.drivepad.companion.PlatformBackendFactory
import com.drivepad.companion.backend.uinput.UInputGamepadBackend
import com.drivepad.companion.backend.windows.ViGEmX360GamepadBackend
import com.drivepad.companion.diagnostics.TerminalDashboard
import com.drivepad.companion.domain.BackendDiagnosticsSnapshot
import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.domain.HostPlatform
import com.drivepad.companion.domain.NoOpGamepadBackend
import com.drivepad.companion.monitoring.CompanionConnectionStatus
import com.drivepad.companion.monitoring.ConnectionStatusSnapshot
import com.drivepad.companion.monitoring.LatencySnapshot
import com.drivepad.companion.monitoring.PacketStatsSnapshot
import com.drivepad.companion.monitoring.WatchdogStatus
import com.drivepad.companion.parseCliArgs
import com.drivepad.companion.protocol.TransmissionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConfigAndCliTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    @Test
    fun `ConfigManager creates default properties file with noop backend and persists custom overrides`() {
        val file = tempDir.newFolder("cfg").toPath().resolve("config.properties")
        val manager = ConfigManager(file)

        val initial = manager.loadOrCreateDefault()
        assertEquals(CompanionConfig.DEFAULT_UDP_PORT, initial.udpPort)
        assertEquals("0.0.0.0", initial.networkInterface)
        assertEquals(1500L, initial.watchdogTimeoutMs)
        assertEquals(GamepadBackendType.NOOP, initial.backendType)
        assertEquals("DrivePad Virtual Gamepad", initial.virtualDeviceName)
        assertTrue(file.toFile().exists())

        val updated = CompanionConfig(
            udpPort = 43210,
            networkInterface = "usb0",
            watchdogTimeoutMs = 900L,
            logLevel = LogLevel.DEBUG,
            diagnosticsMode = DiagnosticsMode.LOGGING,
            backendType = GamepadBackendType.VIGEM_X360,
            virtualDeviceName = "Custom DrivePad",
            vendorId = 0x1209,
            productId = 0x4451,
            versionId = 0x0002,
            activeProfileId = "ets2"
        )
        manager.save(updated)

        val reloaded = manager.loadOrCreateDefault()
        assertEquals(updated, reloaded)
    }

    @Test
    fun `parseCliArgs parses cross-platform options including auto vigem uinput and list-interfaces`() {
        val parsed = parseCliArgs(
            arrayOf(
                "--port", "45000",
                "-i", "usb0",
                "--watchdog-ms", "1200",
                "--backend", "vigem",
                "--profile", "ets2",
                "--device-name", "My DrivePad",
                "--log-level", "DEBUG",
                "--diagnostics", "SILENT",
                "--config", "/tmp/custom.properties",
                "--save-config"
            )
        )
        assertEquals(45000, parsed.portOverride)
        assertEquals("usb0", parsed.interfaceOverride)
        assertEquals(1200L, parsed.watchdogMsOverride)
        assertEquals(GamepadBackendType.VIGEM_X360, parsed.backendOverride)
        assertEquals("ets2", parsed.profileOverride)
        assertEquals("My DrivePad", parsed.deviceNameOverride)
        assertEquals(LogLevel.DEBUG, parsed.logLevelOverride)
        assertEquals(DiagnosticsMode.SILENT, parsed.diagnosticsModeOverride)
        assertEquals("/tmp/custom.properties", parsed.configPathOverride)
        assertTrue(parsed.saveUpdatedConfig)

        assertTrue(parseCliArgs(arrayOf("--list-profiles")).listProfiles)
        assertTrue(parseCliArgs(arrayOf("--list-interfaces")).listInterfaces)
        assertEquals(GamepadBackendType.AUTO, parseCliArgs(arrayOf("-B", "auto")).backendOverride)
        assertEquals(GamepadBackendType.UINPUT, parseCliArgs(arrayOf("-B", "uinput")).backendOverride)
    }

    @Test
    fun `PlatformBackendFactory resolves AUTO backend for Linux Windows and fallback platforms`() {
        val autoCfg = CompanionConfig(backendType = GamepadBackendType.AUTO)

        val linuxBackend = PlatformBackendFactory.createBackend(autoCfg, hostPlatform = HostPlatform.LINUX)
        assertTrue(linuxBackend is UInputGamepadBackend)

        val winBackend = PlatformBackendFactory.createBackend(autoCfg, hostPlatform = HostPlatform.WINDOWS)
        assertTrue(winBackend is ViGEmX360GamepadBackend)

        val macBackend = PlatformBackendFactory.createBackend(autoCfg, hostPlatform = HostPlatform.MACOS)
        assertTrue(macBackend is NoOpGamepadBackend)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCliArgs rejects unknown options`() {
        parseCliArgs(arrayOf("--does-not-exist"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `parseCliArgs rejects invalid backend option`() {
        parseCliArgs(arrayOf("--backend", "invalid-backend"))
    }

    @Test
    fun `TerminalDashboard formats host platform connection controller telemetry watchdog and backend state`() {
        val frame = TerminalDashboard.formatDashboardFrame(
            listeningEndpoint = "192.168.42.10:42100",
            conn = ConnectionStatusSnapshot(
                status = CompanionConnectionStatus.CONNECTED,
                clientIp = "192.168.42.129",
                clientPort = 54321,
                protocolVersion = 1,
                watchdogStatus = WatchdogStatus.ARMED,
                failsafeActivations = 0L
            ),
            ctrl = ControllerState(
                steering = 0.5f,
                throttle = 1.0f,
                brake = 0.0f,
                handbrake = true,
                currentGear = -1,
                transmissionMode = TransmissionMode.MANUAL,
                lights = true,
                engine = true,
                isSafeNeutral = false
            ),
            stats = PacketStatsSnapshot(
                totalPacketsReceived = 1000L,
                validPackets = 998L,
                invalidPackets = 2L,
                sequenceGaps = 1L,
                duplicatePackets = 0L,
                outOfOrderPackets = 1L,
                estimatedPacketLossPercent = 0.10
            ),
            latency = LatencySnapshot(
                averageIntervalMs = 4.0,
                actualUpdateHz = 250.0,
                interArrivalJitterMs = 0.12,
                lastPacketAgeMs = 2L
            ),
            backend = BackendDiagnosticsSnapshot(
                backendType = GamepadBackendType.VIGEM_X360,
                backendName = "ViGEmX360GamepadBackend",
                hostPlatform = HostPlatform.WINDOWS,
                hostSupported = true,
                driverOrDeviceReady = true,
                availabilitySummary = "Windows detected (ViGEmClient.dll found)",
                isInitialized = true,
                isVirtualDeviceActive = true,
                deviceName = "Controller (Xbox 360 For Windows) [DrivePad Virtual Gamepad]",
                statusLabel = "Virtual Xbox 360 Device Active (Windows 11)",
                lastError = null,
                activeProfileId = "ets2",
                activeProfileSummary = "ets2 (Euro Truck Simulator 2)"
            )
        )

        assertTrue(frame.contains("DRIVEPAD COMPANION"))
        assertTrue(frame.contains("Host Platform    : Windows"))
        assertTrue(frame.contains("192.168.42.129"))
        assertTrue(frame.contains("1.00 (100%)"))
        assertTrue(frame.contains("R (Manual Sequential)"))
        assertTrue(frame.contains("LIGHTS, ENGINE"))
        assertTrue(frame.contains("250.0 Hz"))
        assertTrue(frame.contains("ARMED"))
        assertTrue(frame.contains("Virtual Gamepad Backend:"))
        assertTrue(frame.contains("Active Profile   : ets2 (Euro Truck Simulator 2)"))
        assertTrue(frame.contains("Xbox 360 For Windows"))
        assertTrue(frame.contains("ACTIVE"))
    }
}
