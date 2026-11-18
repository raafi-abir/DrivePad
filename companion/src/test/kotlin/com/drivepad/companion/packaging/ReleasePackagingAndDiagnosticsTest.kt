package com.drivepad.companion.packaging

import com.drivepad.companion.backend.uinput.InputEventRecord
import com.drivepad.companion.backend.uinput.InputEventWriter
import com.drivepad.companion.backend.uinput.UInputAvailability
import com.drivepad.companion.backend.uinput.UInputAvailabilityProbe
import com.drivepad.companion.backend.uinput.UInputAxisSpec
import com.drivepad.companion.backend.uinput.UInputDeviceConfig
import com.drivepad.companion.backend.uinput.UInputGamepadBackend
import com.drivepad.companion.backend.uinput.UInputUnavailableException
import com.drivepad.companion.backend.windows.ViGEmBusNotFoundException
import com.drivepad.companion.backend.windows.ViGEmConstants
import com.drivepad.companion.backend.windows.ViGEmX360GamepadBackend
import com.drivepad.companion.backend.windows.WindowsAvailability
import com.drivepad.companion.backend.windows.WindowsHostProbe
import com.drivepad.companion.backend.windows.WindowsVirtualGamepadDriver
import com.drivepad.companion.backend.windows.XusbReportData
import com.drivepad.companion.buildDoctorReport
import com.drivepad.companion.buildVersionReport
import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.config.ConfigManager
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.ReleaseMetadata
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.domain.HostPlatform
import com.drivepad.companion.network.UdpServer
import com.drivepad.companion.parseCliArgs
import com.drivepad.companion.profile.ProfileManager
import com.drivepad.companion.protocol.ProtocolConstants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ReleasePackagingAndDiagnosticsTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))

    @Test
    fun `release metadata preserves independent protocol v1 and surfaces OS backend diagnostics`() {
        assertEquals("1.0.0", ReleaseMetadata.drivepadVersion)
        assertEquals("1.0.0", ReleaseMetadata.companionVersion)
        assertEquals(ProtocolConstants.PROTOCOL_VERSION_V1, ReleaseMetadata.protocolVersion)
        assertEquals(1, ReleaseMetadata.protocolVersion)

        val versionText = buildVersionReport(
            selectedBackend = GamepadBackendType.AUTO,
            hostPlatform = HostPlatform.LINUX,
            probe = Triple(true, false, "/dev/uinput missing")
        )
        assertTrue(versionText.contains("DrivePad Version    : 1.0.0"))
        assertTrue(versionText.contains("Companion Version   : 1.0.0"))
        assertTrue(versionText.contains("Protocol Version    : v1 (33-byte Little-Endian UDP)"))
        assertTrue(versionText.contains("Selected Backend    : uinput"))
        assertTrue(versionText.contains("Backend Available   : NO"))

        val cliVersion = parseCliArgs(arrayOf("--version"))
        assertTrue(cliVersion.showVersion)
        val cliDoctor = parseCliArgs(arrayOf("--check"))
        assertTrue(cliDoctor.runDoctorCheck)
        assertTrue(parseCliArgs(arrayOf("--doctor")).runDoctorCheck)

        val doctorText = buildDoctorReport(
            config = CompanionConfig(backendType = GamepadBackendType.AUTO, activeProfileId = "ets2"),
            configPath = "/home/user/.config/drivepad-companion/config.properties",
            profilesDir = "/home/user/.config/drivepad-companion/profiles",
            hostPlatform = HostPlatform.WINDOWS,
            probe = Triple(true, false, "ViGEmClient.dll not found"),
            interfaces = listOf(UdpServer.DiscoveredInterface("eth3", "Remote NDIS Compatible Device", "192.168.42.10"))
        )
        assertTrue(doctorText.contains("DrivePad Companion Readiness & Environment Check"))
        assertTrue(doctorText.contains("192.168.42.10"))
        assertTrue(doctorText.contains("ViGEmBus v1.22.0 is archived/EOL"))
        assertTrue(doctorText.contains("Do NOT disable HVCI"))
    }

    @Test
    fun `cross-platform config and profile directories resolve accurately for Linux XDG and Windows AppData`() {
        val xdgPath = ConfigManager.defaultConfigPath(
            osName = "Linux",
            appData = null,
            xdgConfigHome = "/custom/xdg",
            userHome = "/home/alice"
        )
        assertEquals(Paths.get("/custom/xdg", "drivepad-companion", "config.properties"), xdgPath)

        val linuxDefaultProfiles = ProfileManager.defaultProfilesDir(
            osName = "Linux",
            appData = null,
            xdgConfigHome = null,
            userHome = "/home/alice"
        )
        assertEquals(Paths.get("/home/alice", ".config", "drivepad-companion", "profiles"), linuxDefaultProfiles)

        val windowsConfig = ConfigManager.defaultConfigPath(
            osName = "Windows 11",
            appData = "C:/Users/Alice/AppData/Roaming",
            xdgConfigHome = "/ignored",
            userHome = "C:/Users/Alice"
        )
        assertEquals(Paths.get("C:/Users/Alice/AppData/Roaming", "drivepad-companion", "config.properties"), windowsConfig)
    }

    @Test
    fun `missing dev uinput reports clean availability probe and non-crashing backend diagnostics`() {
        val missingPath = tempDir.root.toPath().resolve("missing-uinput").toString()
        val probe = UInputAvailabilityProbe.probe(missingPath)
        assertFalse(probe.deviceExists)
        assertFalse(probe.deviceWritable)

        val failingWriter = object : InputEventWriter {
            override fun open(devicePath: String) {
                throw UInputUnavailableException("$devicePath unavailable. Load module with: sudo modprobe uinput")
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

        val backend = UInputGamepadBackend(
            deviceConfig = UInputDeviceConfig(devicePath = missingPath),
            writerFactory = { failingWriter },
            availabilityProbe = { probe },
            logger = silentLogger
        )
        backend.initialize()
        val diag = backend.diagnostics()
        assertFalse(diag.isVirtualDeviceActive)
        assertFalse(diag.driverOrDeviceReady)
        assertNotNull(diag.lastError)
        assertTrue(diag.lastError!!.contains("modprobe uinput"))
        backend.close()
    }

    @Test
    fun `missing ViGEmClient dll and missing ViGEmBus driver surface actionable diagnostics without crashing`() {
        val missingDllProbe = WindowsHostProbe.probe(
            osName = "Windows 11",
            osArch = "amd64",
            searchPaths = listOf(tempDir.root.toPath().resolve("nonexistent-ViGEmClient.dll"))
        )
        assertTrue(missingDllProbe.supportedOs)
        assertFalse(missingDllProbe.clientLibraryFound)
        assertTrue(missingDllProbe.summary.contains("ViGEmBus v1.22.0"))

        val busMissingDriver = object : WindowsVirtualGamepadDriver {
            override fun open(vendorId: Int?, productId: Int?) {
                throw ViGEmBusNotFoundException(
                    "vigem_connect failed: ${ViGEmConstants.describeError(ViGEmConstants.VIGEM_ERROR_BUS_NOT_FOUND)} " +
                        "— ViGEmBus v1.22.0 is archived/EOL and must be installed manually"
                )
            }
            override fun submitReport(report: XusbReportData) {}
            override fun close() {}
        }

        val backend = ViGEmX360GamepadBackend(
            driverFactory = { busMissingDriver },
            availabilityProbe = {
                WindowsAvailability(
                    supportedOs = true,
                    osDescription = "Windows 11 (amd64)",
                    clientLibraryFound = false,
                    clientLibraryPath = null,
                    summary = missingDllProbe.summary
                )
            },
            logger = silentLogger
        )
        backend.initialize()
        val diag = backend.diagnostics()
        assertFalse(diag.isVirtualDeviceActive)
        assertFalse(diag.driverOrDeviceReady)
        assertNotNull(diag.lastError)
        assertTrue(diag.lastError!!.contains("VIGEM_ERROR_BUS_NOT_FOUND"))
        assertTrue(diag.lastError!!.contains("archived/EOL"))
        backend.close()
    }

    @Test
    fun `packaging layouts contain no third-party ViGEm binaries and Linux install-uninstall scripts are reversible`() {
        val projectRoot = locateCompanionRoot()
        val packagingDir = projectRoot.resolve("packaging")
        assertTrue("Expected packaging dir at $packagingDir", Files.isDirectory(packagingDir))

        // Verify no unverified ViGEm binaries are bundled
        Files.walk(packagingDir).use { stream ->
            val forbidden = stream
                .filter { Files.isRegularFile(it) }
                .map { it.fileName.toString().lowercase() }
                .filter { it.endsWith(".dll") || it.endsWith(".sys") || it.endsWith(".exe") }
                .toList()
            assertEquals("Third-party binaries must not be bundled in packaging: $forbidden", emptyList<String>(), forbidden)
        }

        // Verify Arch PKGBUILD & Windows scripts exist
        assertTrue(Files.isRegularFile(packagingDir.resolve("linux/arch/PKGBUILD")))
        assertTrue(Files.isRegularFile(packagingDir.resolve("linux/arch/drivepad-companion.install")))
        assertTrue(Files.isRegularFile(packagingDir.resolve("linux/udev/99-drivepad-uinput.rules")))
        assertTrue(Files.isRegularFile(packagingDir.resolve("windows/bin/drivepad-companion.bat")))
        assertTrue(Files.isRegularFile(packagingDir.resolve("windows/bin/drivepad-companion.ps1")))
        assertTrue(Files.isRegularFile(packagingDir.resolve("windows/scripts/install-windows.ps1")))
        assertTrue(Files.isRegularFile(packagingDir.resolve("windows/scripts/uninstall-windows.ps1")))

        // Exercise Linux install/uninstall scripts cleanly inside TemporaryFolder
        val stagingDist = tempDir.newFolder("dist").toPath()
        Files.createDirectories(stagingDist.resolve("bin"))
        Files.createDirectories(stagingDist.resolve("lib"))
        Files.createDirectories(stagingDist.resolve("udev"))
        Files.createDirectories(stagingDist.resolve("scripts"))

        Files.writeString(stagingDist.resolve("lib/drivepad-companion-1.0.0-all.jar"), "dummy-jar-for-script-test")
        Files.copy(
            packagingDir.resolve("linux/bin/drivepad-companion"),
            stagingDist.resolve("bin/drivepad-companion")
        )
        Files.copy(
            packagingDir.resolve("linux/udev/99-drivepad-uinput.rules"),
            stagingDist.resolve("udev/99-drivepad-uinput.rules")
        )
        Files.copy(
            packagingDir.resolve("linux/README.txt"),
            stagingDist.resolve("README.txt")
        )
        val installScript = stagingDist.resolve("scripts/install-linux.sh")
        val uninstallScript = stagingDist.resolve("scripts/uninstall-linux.sh")
        Files.copy(packagingDir.resolve("linux/scripts/install-linux.sh"), installScript)
        Files.copy(packagingDir.resolve("linux/scripts/uninstall-linux.sh"), uninstallScript)
        installScript.toFile().setExecutable(true)
        uninstallScript.toFile().setExecutable(true)

        val prefixDir = tempDir.newFolder("prefix").toPath()
        val fakeHome = tempDir.newFolder("home").toPath()
        val fakeXdg = fakeHome.resolve(".xdg-config")

        val installProc = ProcessBuilder(
            "sh",
            installScript.toString(),
            "--prefix",
            prefixDir.toString()
        ).apply {
            environment()["HOME"] = fakeHome.toString()
            environment()["XDG_CONFIG_HOME"] = fakeXdg.toString()
            redirectErrorStream(true)
        }.start()
        val installOutput = installProc.inputStream.bufferedReader().readText()
        assertEquals("install-linux.sh failed: $installOutput", 0, installProc.waitFor())
        assertTrue(Files.isExecutable(prefixDir.resolve("bin/drivepad-companion")))
        assertTrue(Files.isRegularFile(prefixDir.resolve("share/drivepad-companion/lib/drivepad-companion-1.0.0-all.jar")))
        assertTrue(Files.isRegularFile(prefixDir.resolve("share/drivepad-companion/udev/99-drivepad-uinput.rules")))
        assertTrue(Files.isDirectory(fakeXdg.resolve("drivepad-companion/profiles")))

        // Default uninstall removes app binaries while preserving user config
        val uninstallProc = ProcessBuilder(
            "sh",
            uninstallScript.toString(),
            "--prefix",
            prefixDir.toString()
        ).apply {
            environment()["HOME"] = fakeHome.toString()
            environment()["XDG_CONFIG_HOME"] = fakeXdg.toString()
            redirectErrorStream(true)
        }.start()
        val uninstallOutput = uninstallProc.inputStream.bufferedReader().readText()
        assertEquals("uninstall-linux.sh failed: $uninstallOutput", 0, uninstallProc.waitFor())
        assertFalse(Files.exists(prefixDir.resolve("bin/drivepad-companion")))
        assertFalse(Files.exists(prefixDir.resolve("share/drivepad-companion")))
        assertTrue("Config directory should survive default uninstall", Files.isDirectory(fakeXdg.resolve("drivepad-companion")))
    }

    @Test
    fun `windows host probe resolves ViGEmClient dll inside appHome regardless of working directory`() {
        val appHome = tempDir.newFolder("win-install").toPath()
        val otherWorkingDir = tempDir.newFolder("other-cwd").toPath()
        val nativeDll = appHome.resolve("native/windows/ViGEmClient.dll")
        Files.createDirectories(nativeDll.parent)
        Files.writeString(nativeDll, "placeholder-for-path-resolution-test")

        val searchPaths = WindowsHostProbe.defaultDllSearchPaths(
            workingDir = otherWorkingDir.toString(),
            appHome = appHome,
            appData = null,
            programFiles = null,
            systemRoot = null
        )
        assertTrue(searchPaths.contains(nativeDll))
        assertTrue(searchPaths.contains(appHome.resolve("lib/ViGEmClient.dll")))
        assertTrue(searchPaths.contains(appHome.resolve("ViGEmClient.dll")))

        val probe = WindowsHostProbe.probe(
            osName = "Windows 11",
            osArch = "amd64",
            searchPaths = searchPaths
        )
        assertTrue(probe.supportedOs)
        assertTrue(probe.clientLibraryFound)
        assertEquals(nativeDll.toAbsolutePath().toString(), probe.clientLibraryPath)
    }

    @Test
    fun `windows launcher and install-uninstall scripts satisfy release safety contracts`() {
        val packagingDir = locateCompanionRoot().resolve("packaging/windows")
        val batScript = Files.readString(packagingDir.resolve("bin/drivepad-companion.bat"))
        val psLauncher = Files.readString(packagingDir.resolve("bin/drivepad-companion.ps1"))
        val installPs = Files.readString(packagingDir.resolve("scripts/install-windows.ps1"))
        val uninstallPs = Files.readString(packagingDir.resolve("scripts/uninstall-windows.ps1"))

        // Launcher scripts require Java 21+ and point to APP_HOME\lib jar
        assertTrue(batScript.contains("drivepad-companion-*-all.jar"))
        assertTrue(batScript.contains("Java 21+"))
        assertTrue(psLauncher.contains("drivepad-companion-*-all.jar"))
        assertTrue(psLauncher.contains("Java 21+"))

        // Installer discloses archived/EOL ViGEmBus, forbids silent driver installs & HVCI tampering
        assertTrue(installPs.contains("ViGEmBus is NOT silently installed"))
        assertTrue(installPs.contains("archived/EOL"))
        assertTrue(installPs.contains("Do NOT disable HVCI/Core Isolation or Secure Boot"))
        assertTrue(installPs.contains("42100"))

        // Uninstaller removes binaries & PATH entry while preserving config unless -Purge is supplied
        assertTrue(uninstallPs.contains("[switch]\$Purge"))
        assertTrue(uninstallPs.contains("if (\$Purge -and (Test-Path \$ConfigDir))"))
        assertTrue(uninstallPs.contains("User config preserved"))
    }

    @Test
    fun `linux installer supports dry-run upgrade reinstall purge and reports missing java cleanly`() {
        val packagingDir = locateCompanionRoot().resolve("packaging")
        val stagingDist = tempDir.newFolder("dist-lifecycle").toPath()
        Files.createDirectories(stagingDist.resolve("bin"))
        Files.createDirectories(stagingDist.resolve("lib"))
        Files.createDirectories(stagingDist.resolve("udev"))
        Files.createDirectories(stagingDist.resolve("scripts"))

        Files.writeString(stagingDist.resolve("lib/drivepad-companion-1.0.0-all.jar"), "v1-jar")
        Files.copy(packagingDir.resolve("linux/bin/drivepad-companion"), stagingDist.resolve("bin/drivepad-companion"))
        Files.copy(packagingDir.resolve("linux/udev/99-drivepad-uinput.rules"), stagingDist.resolve("udev/99-drivepad-uinput.rules"))
        val installScript = stagingDist.resolve("scripts/install-linux.sh")
        val uninstallScript = stagingDist.resolve("scripts/uninstall-linux.sh")
        Files.copy(packagingDir.resolve("linux/scripts/install-linux.sh"), installScript)
        Files.copy(packagingDir.resolve("linux/scripts/uninstall-linux.sh"), uninstallScript)

        val prefixDir = tempDir.newFolder("prefix-lifecycle").toPath()
        val fakeHome = tempDir.newFolder("home-lifecycle").toPath()
        val fakeXdg = fakeHome.resolve(".xdg")

        // 1. Dry-run makes zero filesystem mutations
        val dryRunProc = ProcessBuilder("sh", installScript.toString(), "--prefix", prefixDir.toString(), "--dry-run").apply {
            environment()["HOME"] = fakeHome.toString()
            environment()["XDG_CONFIG_HOME"] = fakeXdg.toString()
            redirectErrorStream(true)
        }.start()
        val dryRunOut = dryRunProc.inputStream.bufferedReader().readText()
        assertEquals("dry-run failed: $dryRunOut", 0, dryRunProc.waitFor())
        assertTrue(dryRunOut.contains("[dry-run]"))
        assertFalse(Files.exists(prefixDir.resolve("bin/drivepad-companion")))
        assertFalse(Files.exists(fakeXdg.resolve("drivepad-companion")))

        // 2. Fresh install + custom user config/profile creation
        fun runScript(script: Path, vararg args: String): Pair<Int, String> {
            val proc = ProcessBuilder(listOf("sh", script.toString()) + args).apply {
                environment()["HOME"] = fakeHome.toString()
                environment()["XDG_CONFIG_HOME"] = fakeXdg.toString()
                redirectErrorStream(true)
            }.start()
            val out = proc.inputStream.bufferedReader().readText()
            return proc.waitFor() to out
        }

        val (rc1, out1) = runScript(installScript, "--prefix", prefixDir.toString())
        assertEquals(out1, 0, rc1)
        val userConfig = fakeXdg.resolve("drivepad-companion/config.properties")
        val userProfile = fakeXdg.resolve("drivepad-companion/profiles/custom_rig.properties")
        Files.writeString(userConfig, "udpPort=42100\nactiveProfileId=custom_rig\n")
        Files.writeString(userProfile, "id=custom_rig\nname=Custom Rig\n")

        // 3. Upgrade preserves user config and custom profile byte-for-byte
        Files.writeString(stagingDist.resolve("lib/drivepad-companion-1.0.0-all.jar"), "v1-upgraded-jar")
        val (rc2, out2) = runScript(installScript, "--prefix", prefixDir.toString())
        assertEquals(out2, 0, rc2)
        assertEquals("v1-upgraded-jar", Files.readString(prefixDir.resolve("share/drivepad-companion/lib/drivepad-companion-1.0.0-all.jar")))
        assertEquals("udpPort=42100\nactiveProfileId=custom_rig\n", Files.readString(userConfig))
        assertEquals("id=custom_rig\nname=Custom Rig\n", Files.readString(userProfile))

        // 4. Uninstall preserves config/profile, then reinstall restores launcher
        val (rc3, out3) = runScript(uninstallScript, "--prefix", prefixDir.toString())
        assertEquals(out3, 0, rc3)
        assertFalse(Files.exists(prefixDir.resolve("bin/drivepad-companion")))
        assertTrue(Files.isRegularFile(userConfig))
        assertTrue(Files.isRegularFile(userProfile))

        val (rc4, out4) = runScript(installScript, "--prefix", prefixDir.toString())
        assertEquals(out4, 0, rc4)
        assertTrue(Files.isExecutable(prefixDir.resolve("bin/drivepad-companion")))
        assertEquals("udpPort=42100\nactiveProfileId=custom_rig\n", Files.readString(userConfig))

        // 5. Launchers without java on PATH print actionable diagnostic and exit non-zero
        val noJavaBinDir = tempDir.newFolder("no-java-path").toPath()
        for (tool in listOf("dirname", "readlink", "head", "sed", "basename")) {
            val sysTool = listOf(Paths.get("/usr/bin", tool), Paths.get("/bin", tool)).firstOrNull { Files.isExecutable(it) }
            if (sysTool != null) {
                Files.createSymbolicLink(noJavaBinDir.resolve(tool), sysTool)
            }
        }
        val noJavaProc = ProcessBuilder("sh", stagingDist.resolve("bin/drivepad-companion").toString(), "--version").apply {
            environment()["PATH"] = noJavaBinDir.toString()
            environment().remove("JAVA_HOME")
            redirectErrorStream(true)
        }.start()
        val noJavaOut = noJavaProc.inputStream.bufferedReader().readText()
        assertTrue("Expected non-zero exit when java missing", noJavaProc.waitFor() != 0)
        assertTrue("Expected clear Java 21+ error: $noJavaOut", noJavaOut.contains("Java 21+"))

        val installedNoJavaProc = ProcessBuilder("sh", prefixDir.resolve("bin/drivepad-companion").toString(), "--version").apply {
            environment()["PATH"] = noJavaBinDir.toString()
            environment().remove("JAVA_HOME")
            redirectErrorStream(true)
        }.start()
        val installedNoJavaOut = installedNoJavaProc.inputStream.bufferedReader().readText()
        assertTrue("Expected non-zero exit when installed launcher missing java", installedNoJavaProc.waitFor() != 0)
        assertTrue("Expected clear Java 21+ error: $installedNoJavaOut", installedNoJavaOut.contains("Java 21+"))

        // 6. Uninstall with --purge removes config directory completely
        val (rc5, out5) = runScript(uninstallScript, "--prefix", prefixDir.toString(), "--purge")
        assertEquals(out5, 0, rc5)
        assertFalse(Files.exists(fakeXdg.resolve("drivepad-companion")))
    }

    @Test
    fun `third party notices and release manifest configuration cover JNA ViGEmBus and com drivepad app`() {
        val companionRoot = locateCompanionRoot()
        val repoRoot = companionRoot.parent
        val noticesPath = repoRoot.resolve("docs/THIRD_PARTY_NOTICES.md")
        assertTrue("Expected notices at $noticesPath", Files.isRegularFile(noticesPath))

        val notices = Files.readString(noticesPath)
        assertTrue(notices.contains("com.drivepad.app"))
        assertTrue(notices.contains("Apache-2.0"))
        assertTrue(notices.contains("net.java.dev.jna:jna"))
        assertTrue(notices.contains("LGPL-2.1-or-later"))
        assertTrue(notices.contains("BSD-3-Clause"))
        assertTrue(notices.contains("ViGEmBus"))

        val companionGradle = Files.readString(companionRoot.resolve("build.gradle.kts"))
        assertTrue(companionGradle.contains("THIRD_PARTY_NOTICES.md"))
        assertTrue(companionGradle.contains("\"androidApplicationId\": \"com.drivepad.app\""))
    }

    private fun locateCompanionRoot(): Path {
        val cwd = Paths.get("").toAbsolutePath()
        return if (Files.isDirectory(cwd.resolve("packaging"))) {
            cwd
        } else {
            cwd.resolve("companion")
        }
    }
}
