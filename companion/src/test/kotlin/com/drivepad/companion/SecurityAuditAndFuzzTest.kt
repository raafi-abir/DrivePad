package com.drivepad.companion

import com.drivepad.companion.backend.uinput.InputEventRecord
import com.drivepad.companion.backend.uinput.InputEventWriter
import com.drivepad.companion.backend.uinput.UInputAvailability
import com.drivepad.companion.backend.uinput.UInputAxisSpec
import com.drivepad.companion.backend.uinput.UInputDeviceConfig
import com.drivepad.companion.backend.uinput.UInputGamepadBackend
import com.drivepad.companion.backend.windows.NativeViGEmGamepadDriver
import com.drivepad.companion.backend.windows.ViGEmUnavailableException
import com.drivepad.companion.backend.windows.WindowsAvailability
import com.drivepad.companion.backend.windows.WindowsHostProbe
import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.config.ConfigManager
import com.drivepad.companion.config.DiagnosticsMode
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.MonotonicClock
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.domain.ControllerStateProcessor
import com.drivepad.companion.domain.GamepadBackend
import com.drivepad.companion.domain.HostPlatform
import com.drivepad.companion.monitoring.CompanionConnectionStatus
import com.drivepad.companion.monitoring.ConnectionMonitor
import com.drivepad.companion.monitoring.LatencyMonitor
import com.drivepad.companion.monitoring.PacketStatistics
import com.drivepad.companion.network.ConnectionManager
import com.drivepad.companion.network.ProtocolHandler
import com.drivepad.companion.network.UdpServer
import com.drivepad.companion.profile.ProfileManager
import com.drivepad.companion.profile.ProfileValidationException
import com.drivepad.companion.protocol.PacketDecodeResult
import com.drivepad.companion.protocol.PacketDecoder
import com.drivepad.companion.protocol.PacketMessageType
import com.drivepad.companion.protocol.ProtocolConstants
import com.drivepad.companion.protocol.TransmissionMode
import com.drivepad.companion.safety.InputWatchdog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class SecurityAuditAndFuzzTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private class FakeClock(initialNs: Long = 10_000_000_000L) : MonotonicClock {
        val now = AtomicLong(initialNs)
        override fun nanoTime(): Long = now.get()
        fun advanceMs(ms: Long) {
            now.addAndGet(ms * 1_000_000L)
        }
    }

    private val silentLogger = StructuredLogger(
        minLevel = LogLevel.ERROR,
        out = PrintStream(ByteArrayOutputStream())
    )

    private fun buildWirePacket(
        type: PacketMessageType = PacketMessageType.CONTROLLER_STATE,
        seq: Long = 1L,
        timestampNanos: Long = 1000L,
        steering: Float = 0.0f,
        throttle: Float = 0.0f,
        brake: Float = 0.0f,
        handbrake: Boolean = false,
        gear: Int = 0,
        mode: TransmissionMode = TransmissionMode.AUTO,
        flags: UInt = 0u
    ): ByteArray {
        val bytes = ByteArray(ProtocolConstants.PACKET_SIZE_BYTES)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0, ProtocolConstants.PROTOCOL_VERSION_V1.toByte())
        bb.put(1, type.wireValue.toByte())
        bb.putInt(2, seq.toInt())
        bb.putLong(6, timestampNanos)
        bb.putFloat(14, steering)
        bb.putFloat(18, throttle)
        bb.putFloat(22, brake)
        bb.put(26, if (handbrake) 1 else 0)
        bb.put(27, gear.toByte())
        bb.put(28, mode.wireValue.toByte())
        bb.putInt(29, flags.toInt())
        return bytes
    }

    @Test
    fun `seeded fuzzing of 5000 malformed truncated oversized and edge packets never crashes and preserves neutrality`() {
        val rng = Random(20261118L)
        val clock = FakeClock()
        val monitor = ConnectionMonitor()
        val stats = PacketStatistics()
        val latency = LatencyMonitor()
        val stateProcessor = ControllerStateProcessor()
        val connMgr = ConnectionManager(monitor, activeSessionGuardWindowNs = 1_500_000_000L)
        val watchdog = InputWatchdog(
            timeoutMs = 1500L,
            stateProcessor = stateProcessor,
            connectionMonitor = monitor,
            onSessionTimedOut = { connMgr.onWatchdogTimeout() },
            clock = clock,
            logger = silentLogger
        )
        val handler = ProtocolHandler(connMgr, monitor, stateProcessor, stats, latency, watchdog, clock, silentLogger)
        val client = InetSocketAddress("192.168.42.129", 51234)

        // Purely random packets of sizes 0..256 (0 valid handshakes expected)
        for (i in 0 until 2500) {
            val size = rng.nextInt(0, 257)
            val payload = ByteArray(size).also { rng.nextBytes(it) }
            val result = PacketDecoder.decode(payload, size)
            if (size != ProtocolConstants.PACKET_SIZE_BYTES) {
                assertTrue("Expected Invalid for size $size", result is PacketDecodeResult.Invalid)
            }
            handler.handleIncomingBytes(payload, size, client)
        }

        // Targeted mutations of structurally near-valid 33-byte packets
        val baseline = buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 10L, steering = 0.25f, throttle = 1.0f)
        for (i in 0 until 2500) {
            val mutated = baseline.clone()
            when (i % 10) {
                0 -> mutated[0] = rng.nextInt(2, 255).toByte() // bad protocol version
                1 -> mutated[1] = rng.nextInt(7, 255).toByte() // unknown message type
                2 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putFloat(14, Float.NaN)
                3 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putFloat(14, Float.POSITIVE_INFINITY)
                4 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putFloat(14, 1.001f)
                5 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putFloat(18, 0.42f) // non-digital throttle
                6 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putFloat(22, -0.5f) // invalid brake
                7 -> mutated[26] = rng.nextInt(2, 255).toByte() // invalid handbrake
                8 -> mutated[27] = rng.nextInt(13, 127).toByte() // invalid gear
                9 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putInt(29, 0x00010000) // reserved button bits
            }
            assertTrue("Mutation kind ${i % 10} must decode Invalid", PacketDecoder.decode(mutated) is PacketDecodeResult.Invalid)
            assertNull(handler.handleIncomingBytes(mutated, mutated.size, client))
        }

        assertFalse(connMgr.isConnected())
        assertTrue(stateProcessor.controllerState.value.isSafeNeutral)
        assertTrue(stats.snapshot().invalidPackets >= 4900L)
    }

    @Test
    fun `anti-replay sequence filter blocks duplicate and stale packets while handling UInt32 wrap-around`() {
        val clock = FakeClock()
        val monitor = ConnectionMonitor()
        val stats = PacketStatistics()
        val latency = LatencyMonitor()
        val stateProcessor = ControllerStateProcessor()
        val connMgr = ConnectionManager(monitor, activeSessionGuardWindowNs = 1_500_000_000L)
        val watchdog = InputWatchdog(
            timeoutMs = 1500L,
            stateProcessor = stateProcessor,
            connectionMonitor = monitor,
            onSessionTimedOut = { connMgr.onWatchdogTimeout() },
            clock = clock,
            logger = silentLogger
        )
        val handler = ProtocolHandler(connMgr, monitor, stateProcessor, stats, latency, watchdog, clock, silentLogger)
        val client = InetSocketAddress("192.168.42.129", 51000)

        assertNotNull(handler.handleIncomingBytes(buildWirePacket(PacketMessageType.HELLO, seq = 1L), 33, client))

        // Initial valid state at seq = 0xFFFFFFFEL (near 32-bit wrap)
        handler.handleIncomingBytes(
            buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 0xFFFFFFFEL, steering = 0.5f, throttle = 1.0f),
            33,
            client
        )
        assertEquals(0.5f, stateProcessor.controllerState.value.steering, 0.001f)
        assertEquals(1.0f, stateProcessor.controllerState.value.throttle, 0.001f)

        // Advance to 0xFFFFFFFFL and then wrap across 0L -> 1L -> 5L
        handler.handleIncomingBytes(buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 0xFFFFFFFFL, steering = 0.6f, throttle = 1.0f), 33, client)
        handler.handleIncomingBytes(buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 0L, steering = 0.7f, throttle = 1.0f), 33, client)
        handler.handleIncomingBytes(buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 5L, steering = 0.8f, throttle = 0.0f, brake = 1.0f), 33, client)
        assertEquals(0.8f, stateProcessor.controllerState.value.steering, 0.001f)
        assertEquals(1.0f, stateProcessor.controllerState.value.brake, 0.001f)

        val validBeforeReplay = stats.snapshot().validPackets

        // Replay of duplicate seq = 5 and stale seq = 4 / seq = 0xFFFFFFFFL attempting to re-engage full throttle!
        handler.handleIncomingBytes(buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 5L, steering = -1.0f, throttle = 1.0f), 33, client)
        handler.handleIncomingBytes(buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 4L, steering = -1.0f, throttle = 1.0f), 33, client)
        handler.handleIncomingBytes(buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 0xFFFFFFFFL, steering = -1.0f, throttle = 1.0f), 33, client)

        // State must NOT change to replayed full throttle; invalid counter increments
        assertEquals(0.8f, stateProcessor.controllerState.value.steering, 0.001f)
        assertEquals(0.0f, stateProcessor.controllerState.value.throttle, 0.001f)
        assertEquals(1.0f, stateProcessor.controllerState.value.brake, 0.001f)
        assertEquals(validBeforeReplay, stats.snapshot().validPackets)
        assertTrue(stats.snapshot().invalidPackets >= 3L)

        // Replayed PING must also be ignored
        assertNotNull(handler.handleIncomingBytes(buildWirePacket(PacketMessageType.PING, seq = 10L), 33, client))
        assertNull(handler.handleIncomingBytes(buildWirePacket(PacketMessageType.PING, seq = 10L), 33, client))
        assertNull(handler.handleIncomingBytes(buildWirePacket(PacketMessageType.PING, seq = 9L), 33, client))
    }

    @Test
    fun `cross-IP HELLO hijack and spoofed sender packets are rejected while legitimate timeout handover works`() {
        val clock = FakeClock()
        val monitor = ConnectionMonitor()
        val stats = PacketStatistics()
        val latency = LatencyMonitor()
        val stateProcessor = ControllerStateProcessor()
        val connMgr = ConnectionManager(monitor, activeSessionGuardWindowNs = 1_500_000_000L)
        val watchdog = InputWatchdog(
            timeoutMs = 1500L,
            stateProcessor = stateProcessor,
            connectionMonitor = monitor,
            onSessionTimedOut = { connMgr.onWatchdogTimeout() },
            clock = clock,
            logger = silentLogger
        )
        val handler = ProtocolHandler(connMgr, monitor, stateProcessor, stats, latency, watchdog, clock, silentLogger)

        val ownerPort1 = InetSocketAddress("192.168.42.129", 50001)
        val ownerPort2 = InetSocketAddress("192.168.42.129", 50002)
        val attacker = InetSocketAddress("192.168.42.250", 60001)

        // Owner establishes session and steers
        assertNotNull(handler.handleIncomingBytes(buildWirePacket(PacketMessageType.HELLO, seq = 1L), 33, ownerPort1))
        handler.handleIncomingBytes(buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 2L, steering = 0.45f, throttle = 1.0f), 33, ownerPort1)

        // Attacker tries spoofed CONTROLLER_STATE, PING, DISCONNECT, and HELLO hijack within active window
        clock.advanceMs(200)
        handler.handleIncomingBytes(buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 3L, steering = -1.0f, brake = 1.0f), 33, attacker)
        assertNull(handler.handleIncomingBytes(buildWirePacket(PacketMessageType.PING, seq = 4L), 33, attacker))
        handler.handleIncomingBytes(buildWirePacket(PacketMessageType.DISCONNECT, seq = 5L), 33, attacker)
        assertNull("Competing IP HELLO must be rejected while owner session is live", handler.handleIncomingBytes(buildWirePacket(PacketMessageType.HELLO, seq = 1L), 33, attacker))

        assertTrue(connMgr.isConnectedTo(ownerPort1))
        assertEquals(0.45f, stateProcessor.controllerState.value.steering, 0.001f)
        assertEquals(1.0f, stateProcessor.controllerState.value.throttle, 0.001f)

        // Same-IP crash reconnect (new ephemeral port) IS allowed and resets prior controls safely
        assertNotNull(handler.handleIncomingBytes(buildWirePacket(PacketMessageType.HELLO, seq = 1L), 33, ownerPort2))
        assertTrue(connMgr.isConnectedTo(ownerPort2))
        assertTrue(stateProcessor.controllerState.value.isSafeNeutral)

        // Once active session goes silent past guard window (1500 ms), another client can take over cleanly
        clock.advanceMs(1600)
        assertNotNull("After guard window expiry, new client handover must succeed", handler.handleIncomingBytes(buildWirePacket(PacketMessageType.HELLO, seq = 1L), 33, attacker))
        assertTrue(connMgr.isConnectedTo(attacker))
    }

    @Test
    fun `bounded real UDP socket flood of malformed and replayed datagrams leaves companion responsive and SafeNeutral`() {
        val freePort = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val app = DrivePadCompanionApp(
            config = CompanionConfig(
                udpPort = freePort,
                networkInterface = "127.0.0.1",
                watchdogTimeoutMs = 1500L,
                logLevel = LogLevel.ERROR,
                diagnosticsMode = DiagnosticsMode.SILENT,
                backendType = GamepadBackendType.NOOP
            ),
            logger = silentLogger
        )
        app.start()
        val target = InetSocketAddress("127.0.0.1", app.udpServer.boundPort)

        try {
            val rng = Random(99L)
            // Unauthenticated attacker socket sends 1500 garbage + spoofed datagrams
            DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { floodSocket ->
                val stateSpoof = buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 99L, steering = -1.0f, throttle = 1.0f)
                repeat(1500) { i ->
                    if (i % 3 == 0) {
                        floodSocket.send(DatagramPacket(stateSpoof, stateSpoof.size, target))
                    } else {
                        val size = rng.nextInt(1, 200)
                        val noise = ByteArray(size).also { rng.nextBytes(it) }
                        // Avoid accidental valid HELLO byte combination
                        noise[0] = 99
                        floodSocket.send(DatagramPacket(noise, noise.size, target))
                    }
                }
            }

            // Wait briefly for flood queue drain and confirm state remained SafeNeutral
            Thread.sleep(150)
            assertTrue(app.stateProcessor.controllerState.value.isSafeNeutral)

            // Legitimate client must still connect, steer, and cleanly reset to neutral on DISCONNECT
            DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { legit ->
                legit.soTimeout = 1000
                val hello = buildWirePacket(PacketMessageType.HELLO, seq = 1L, timestampNanos = 555_000L)
                legit.send(DatagramPacket(hello, hello.size, target))
                val ackBuf = ByteArray(64)
                val ackPkt = DatagramPacket(ackBuf, ackBuf.size)
                legit.receive(ackPkt)
                val decoded = PacketDecoder.decode(ackBuf, ackPkt.length)
                assertTrue(decoded is PacketDecodeResult.Valid)
                assertEquals(PacketMessageType.HELLO_ACK, (decoded as PacketDecodeResult.Valid).packet.messageType)

                val drive = buildWirePacket(PacketMessageType.CONTROLLER_STATE, seq = 2L, steering = 0.65f, throttle = 1.0f, gear = 3)
                legit.send(DatagramPacket(drive, drive.size, target))
                val deadline = System.currentTimeMillis() + 1500L
                while (System.currentTimeMillis() < deadline && app.stateProcessor.controllerState.value.currentGear != 3) {
                    Thread.sleep(10)
                }
                assertEquals(3, app.stateProcessor.controllerState.value.currentGear)
                assertEquals(0.65f, app.stateProcessor.controllerState.value.steering, 0.001f)

                val disc = buildWirePacket(PacketMessageType.DISCONNECT, seq = 3L)
                legit.send(DatagramPacket(disc, disc.size, target))
                val resetDeadline = System.currentTimeMillis() + 1500L
                while (System.currentTimeMillis() < resetDeadline && !app.stateProcessor.controllerState.value.isSafeNeutral) {
                    Thread.sleep(10)
                }
                assertTrue(app.stateProcessor.controllerState.value.isSafeNeutral)
                assertEquals(CompanionConnectionStatus.DISCONNECTED, app.connectionMonitor.snapshot.value.status)
            }
        } finally {
            app.stop()
            assertTrue(app.stateProcessor.controllerState.value.isSafeNeutral)
        }
    }

    @Test
    fun `ProfileManager blocks path traversal oversized files and malformed properties and sets restrictive POSIX permissions`() {
        val dir = tempFolder.newFolder("profiles").toPath()
        val pm = ProfileManager(dir)
        pm.ensureBuiltInProfilesSeeded()

        val traversalInputs = listOf(
            "../../etc/passwd",
            "..\\..\\Windows\\win.ini",
            "/etc/shadow",
            "ets2/../evil",
            "bad id with spaces",
            "",
            "a".repeat(65)
        )
        for (badId in traversalInputs) {
            assertFalse("Must reject invalid profile id '$badId'", ProfileManager.isValidProfileId(badId))
            var rejected = false
            try {
                pm.resolveProfile(badId)
            } catch (_: ProfileValidationException) {
                rejected = true
            }
            assertTrue("Expected ProfileValidationException for '$badId'", rejected)
        }

        // Oversized > 64KB profile file rejected
        val hugeFile = dir.resolve("huge.properties")
        Files.writeString(hugeFile, "profile.id=huge\n#" + "X".repeat(70_000))
        var hugeRejected = false
        try {
            pm.loadFromFile(hugeFile, expectedId = "huge")
        } catch (_: ProfileValidationException) {
            hugeRejected = true
        }
        assertTrue("Oversized profile file must be rejected", hugeRejected)

        // Malformed Unicode escape in profile throws clean ProfileValidationException
        val corruptFile = dir.resolve("corrupt.properties")
        Files.writeString(corruptFile, "profile.id=corrupt\nprofile.display_name=\\uZZZZ")
        var corruptRejected = false
        try {
            pm.loadFromFile(corruptFile, expectedId = "corrupt")
        } catch (_: ProfileValidationException) {
            corruptRejected = true
        }
        assertTrue("Malformed escape sequence must throw ProfileValidationException", corruptRejected)

        // POSIX permissions (on POSIX-capable Linux/Unix filesystems)
        val builtInPath = pm.profilePath("ets2")
        val posixDir = runCatching { Files.getPosixFilePermissions(dir) }.getOrNull()
        val posixFile = runCatching { Files.getPosixFilePermissions(builtInPath) }.getOrNull()
        if (posixDir != null && posixFile != null) {
            assertFalse("Profiles dir must not be group/world accessible", posixDir.contains(PosixFilePermission.OTHERS_READ))
            assertFalse("Profile file must not be world readable", posixFile.contains(PosixFilePermission.OTHERS_READ))
            assertFalse("Profile file must not be group readable", posixFile.contains(PosixFilePermission.GROUP_READ))
        }
    }

    @Test
    fun `ConfigManager safely recovers from malformed escapes oversized files and poisoned active profile ids`() {
        val cfgDir = tempFolder.newFolder("cfg").toPath()
        val cfgPath = cfgDir.resolve("config.properties")
        val cm = ConfigManager(cfgPath)

        // Poisoned Unicode escape & oversized file
        Files.writeString(cfgPath, "udp.port=42100\nbackend.device_name=\\uZZZZ")
        val fromBadEscape = cm.loadOrCreateDefault()
        assertEquals(CompanionConfig.DEFAULT_UDP_PORT, fromBadEscape.udpPort)

        Files.writeString(cfgPath, "udp.port=55555\n#" + "A".repeat(70_000))
        val fromOversized = cm.loadOrCreateDefault()
        assertEquals(CompanionConfig.DEFAULT_UDP_PORT, fromOversized.udpPort)

        // Poisoned profile.active traversal is sanitized back to default "generic"
        Files.writeString(
            cfgPath,
            """
            udp.port=42100
            profile.active=../../../etc/passwd
            """.trimIndent()
        )
        val fromTraversal = cm.loadOrCreateDefault()
        assertEquals(CompanionConfig.DEFAULT_PROFILE_ID, fromTraversal.activeProfileId)
    }

    @Test
    fun `WindowsHostProbe ignores untrusted CWD DLL and NativeViGEmGamepadDriver refuses unqualified load`() {
        val untrustedCwd = tempFolder.newFolder("downloads_untrusted").toPath()
        val trojanDll = untrustedCwd.resolve("ViGEmClient.dll")
        Files.writeString(trojanDll, "not-a-real-dll")

        val trustedHome = tempFolder.newFolder("program_home").toPath()
        val candidates = WindowsHostProbe.defaultDllSearchPaths(
            workingDir = untrustedCwd.toAbsolutePath().toString(),
            appHome = trustedHome,
            appData = null,
            programFiles = null,
            systemRoot = null
        )
        assertFalse(
            "Untrusted working directory DLL must NOT appear in DLL search paths",
            candidates.any { it.toAbsolutePath().normalize() == trojanDll.toAbsolutePath().normalize() }
        )

        val probeResult = WindowsHostProbe.probe(
            osName = "Windows 11",
            osArch = "amd64",
            searchPaths = candidates
        )
        assertFalse("Presence of CWD ViGEmClient.dll must NOT be treated as found", probeResult.clientLibraryFound)

        // NativeViGEmGamepadDriver on simulated Windows host without explicit DLL must NOT call libraryLoader("ViGEmClient")
        val loaderInvoked = AtomicInteger(0)
        val driver = NativeViGEmGamepadDriver(
            availabilityProbe = {
                WindowsAvailability(
                    supportedOs = true,
                    osDescription = "Windows 11 (amd64)",
                    clientLibraryFound = false,
                    clientLibraryPath = null,
                    summary = "Missing"
                )
            },
            libraryLoader = { _ ->
                loaderInvoked.incrementAndGet()
                error("Should never be invoked without explicit DLL path")
            }
        )

        if (HostPlatform.current() == HostPlatform.WINDOWS) {
            var threw = false
            try {
                driver.open()
            } catch (_: ViGEmUnavailableException) {
                threw = true
            }
            assertTrue(threw)
        } else {
            // Even simulating the check directly validates driver refuses on non-Windows too
            var threw = false
            try {
                driver.open()
            } catch (_: ViGEmUnavailableException) {
                threw = true
            }
            assertTrue(threw)
        }
        assertEquals("Unqualified Native.load(\"ViGEmClient\") must NEVER run", 0, loaderInvoked.get())
    }

    @Test
    fun `root execution detection UInput permission failure and empty bind address validation work cleanly`() {
        assertTrue(isRunningAsRoot(HostPlatform.LINUX, "root", "Uid:\t0\t0\t0\t0\n"))
        assertTrue(isRunningAsRoot(HostPlatform.LINUX, "archuser", "Uid:\t1000\t0\t0\t0\n")) // effective UID 0 (setuid)
        assertFalse(isRunningAsRoot(HostPlatform.LINUX, "archuser", "Uid:\t1000\t1000\t1000\t1000\n"))
        assertFalse(isRunningAsRoot(HostPlatform.WINDOWS, "Administrator", null))

        var blankRejected = false
        try {
            UdpServer.resolveBindAddress("   ")
        } catch (_: IllegalArgumentException) {
            blankRejected = true
        }
        assertTrue(blankRejected)

        // Missing or unreadable /dev/uinput must degrade gracefully without uncaught crash
        val deniedBackend = UInputGamepadBackend(
            deviceConfig = UInputDeviceConfig(devicePath = "/dev/uinput"),
            writerFactory = {
                object : InputEventWriter {
                    override fun open(devicePath: String) {
                        throw IOException("EACCES: Permission denied opening $devicePath")
                    }
                    override fun enableEventType(eventType: Int) {}
                    override fun enableKeyBit(keyCode: Int) {}
                    override fun enableAbsBit(absCode: Int) {}
                    override fun setupAbsAxis(axis: UInputAxisSpec) {}
                    override fun setupDevice(config: UInputDeviceConfig) {}
                    override fun createDevice() {}
                    override fun writeEvents(events: List<InputEventRecord>) {}
                    override fun destroyDevice() {}
                    override fun close() {}
                }
            },
            availabilityProbe = {
                UInputAvailability(true, "Arch Linux", true, false, "/dev/uinput not writable")
            },
            logger = silentLogger
        )

        deniedBackend.initialize()
        assertFalse(deniedBackend.isReady)
        assertFalse(deniedBackend.diagnostics().isVirtualDeviceActive)
        assertTrue(deniedBackend.diagnostics().lastError?.contains("Permission denied") == true)
        // Publishing or closing in failed state must be safe no-op
        deniedBackend.publishState(ControllerState(throttle = 1.0f))
        deniedBackend.resetToSafeState(ControllerState.safeNeutral())
        deniedBackend.close()
    }
}
