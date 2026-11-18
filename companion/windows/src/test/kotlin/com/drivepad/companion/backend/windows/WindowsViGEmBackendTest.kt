package com.drivepad.companion.backend.windows

import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.profile.ProfileInputMapper
import com.drivepad.companion.profile.ProfilePresets
import com.drivepad.companion.protocol.TransmissionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class WindowsViGEmBackendTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))

    class RecordingWindowsDriver(
        var failOnOpen: Throwable? = null,
        var failOnSubmit: Throwable? = null
    ) : WindowsVirtualGamepadDriver {
        var opened = false
        var openedVid: Int? = null
        var openedPid: Int? = null
        var closed = false
        val submittedReports = mutableListOf<XusbReportData>()

        override fun open(vendorId: Int?, productId: Int?) {
            failOnOpen?.let { throw it }
            opened = true
            openedVid = vendorId
            openedPid = productId
        }

        override fun submitReport(report: XusbReportData) {
            failOnSubmit?.let { throw it }
            submittedReports.add(report)
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `XUSB_REPORT ABI serializer encodes 12-byte little-endian Microsoft struct accurately`() {
        val data = XusbReportData(
            buttonsMask = ViGEmConstants.XUSB_GAMEPAD_A or ViGEmConstants.XUSB_GAMEPAD_DPAD_UP,
            leftTrigger = 255,
            rightTrigger = 255,
            thumbLX = -32768,
            thumbLY = 32767,
            thumbRX = 0,
            thumbRY = -1234
        )
        val bytes = ViGEmConstants.encodeXusbReport(data, ByteOrder.LITTLE_ENDIAN)
        assertEquals(12, bytes.size)

        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals((0x1000 or 0x0001).toShort(), bb.getShort(0))
        assertEquals(255, bb.get(2).toInt() and 0xFF)
        assertEquals(255, bb.get(3).toInt() and 0xFF)
        assertEquals((-32768).toShort(), bb.getShort(4))
        assertEquals(32767.toShort(), bb.getShort(6))
        assertEquals(0.toShort(), bb.getShort(8))
        assertEquals((-1234).toShort(), bb.getShort(10))
    }

    @Test
    fun `WindowsHostProbe detects non-Windows OS safely and finds ViGEmClient dll when present`() {
        val linuxProbe = WindowsHostProbe.probe(osName = "Linux", osArch = "amd64", searchPaths = emptyList())
        assertFalse(linuxProbe.supportedOs)
        assertFalse(linuxProbe.clientLibraryFound)
        assertTrue(linuxProbe.summary.contains("Unsupported OS"))

        val fakeWinDir = tempDir.newFolder("win").toPath()
        val fakeDll = fakeWinDir.resolve("ViGEmClient.dll")
        Files.writeString(fakeDll, "stub")

        val winWithDll = WindowsHostProbe.probe(
            osName = "Windows 11",
            osArch = "amd64",
            searchPaths = listOf(fakeDll)
        )
        assertTrue(winWithDll.supportedOs)
        assertTrue(winWithDll.clientLibraryFound)
        assertEquals(fakeDll.toAbsolutePath().toString(), winWithDll.clientLibraryPath)

        val winWithoutDll = WindowsHostProbe.probe(
            osName = "Windows 11",
            osArch = "amd64",
            searchPaths = listOf(fakeWinDir.resolve("missing.dll"))
        )
        assertTrue(winWithoutDll.supportedOs)
        assertFalse(winWithoutDll.clientLibraryFound)
        assertTrue(winWithoutDll.summary.contains("ViGEmBus"))
    }

    @Test
    fun `WindowsX360Mapping maps ETS2 progressive steering digital triggers and toggle pulses cleanly`() {
        val ets2 = ProfilePresets.ets2()
        val mapper = ProfileInputMapper(ets2)

        val out1 = mapper.processState(
            ControllerState(
                steering = 1.0f,
                throttle = 1.0f,
                brake = 1.0f,
                horn = true,
                lights = true,
                isSafeNeutral = false
            ),
            nowNanos = 10_000_000L
        )
        val report = WindowsX360Mapping.mapOutput(out1, ets2)
        assertEquals(32767, report.thumbLX)
        assertEquals(255, report.rightTrigger)
        assertEquals(255, report.leftTrigger)
        assertTrue((report.buttonsMask and ViGEmConstants.XUSB_GAMEPAD_A) != 0) // Horn
        assertTrue((report.buttonsMask and ViGEmConstants.XUSB_GAMEPAD_X) != 0) // Lights

        // After 75ms pulse expires, lights pulse releases while Horn (HOLD) stays active
        val out2 = mapper.onTimerTick(
            ControllerState(
                steering = 1.0f,
                throttle = 1.0f,
                brake = 1.0f,
                horn = true,
                lights = true,
                isSafeNeutral = false
            ),
            nowNanos = 90_000_000L
        )
        val report2 = WindowsX360Mapping.mapOutput(out2, ets2)
        assertTrue((report2.buttonsMask and ViGEmConstants.XUSB_GAMEPAD_A) != 0)
        assertFalse((report2.buttonsMask and ViGEmConstants.XUSB_GAMEPAD_X) != 0)
    }

    @Test
    fun `ViGEmX360Device suppresses duplicate reports and forces neutral on shutdown`() {
        val driver = RecordingWindowsDriver()
        val device = ViGEmX360Device(driver)
        device.create()
        assertEquals(1, driver.submittedReports.size) // Initial neutral

        // Same neutral report suppressed
        assertFalse(device.applyReport(XusbReportData.NEUTRAL))
        assertEquals(1, driver.submittedReports.size)

        val active = XusbReportData(
            buttonsMask = ViGEmConstants.XUSB_GAMEPAD_B,
            rightTrigger = 255,
            thumbLX = -16384
        )
        assertTrue(device.applyReport(active))
        assertEquals(2, driver.submittedReports.size)
        assertFalse(device.applyReport(active)) // duplicate suppressed

        device.close()
        assertFalse(device.isCreated())
        assertTrue(driver.closed)
        assertEquals(XusbReportData.NEUTRAL, driver.submittedReports.last())
    }

    @Test
    fun `ViGEmX360GamepadBackend handles ETS2 shift pulses SafeNeutral reset and missing driver gracefully`() {
        val driver = RecordingWindowsDriver()
        val backend = ViGEmX360GamepadBackend(
            driverFactory = { driver },
            availabilityProbe = {
                WindowsAvailability(
                    supportedOs = true,
                    osDescription = "Windows 11 (amd64)",
                    clientLibraryFound = true,
                    clientLibraryPath = "C:\\Program Files\\ViGEmClient.dll",
                    summary = "Windows detected (ViGEmClient.dll found)"
                )
            },
            shiftPulseDurationMs = 20L,
            profile = ProfilePresets.ets2(),
            logger = silentLogger
        )

        backend.initialize()
        assertTrue(backend.isReady)
        val diag = backend.diagnostics()
        assertEquals(GamepadBackendType.VIGEM_X360, diag.backendType)
        assertTrue(diag.isVirtualDeviceActive)
        assertNull(diag.lastError)

        val s1 = ControllerState(
            steering = -1.0f,
            throttle = 1.0f,
            brake = 0.0f,
            currentGear = 1,
            transmissionMode = TransmissionMode.MANUAL,
            horn = true,
            isSafeNeutral = false
        )
        backend.publishState(s1)
        assertTrue(backend.awaitIdle())

        val s2 = s1.copy(currentGear = 2)
        backend.publishState(s2)
        assertTrue(backend.awaitIdle())

        val dpadUpPressedSequence = driver.submittedReports.map {
            (it.buttonsMask and ViGEmConstants.XUSB_GAMEPAD_DPAD_UP) != 0
        }
        assertTrue(dpadUpPressedSequence.contains(true))
        assertFalse(driver.submittedReports.last().let { (it.buttonsMask and ViGEmConstants.XUSB_GAMEPAD_DPAD_UP) != 0 })

        backend.resetToSafeState(ControllerState.safeNeutral())
        assertTrue(backend.awaitIdle())
        assertEquals(XusbReportData.NEUTRAL, driver.submittedReports.last())

        backend.close()
        assertFalse(backend.isReady)
        assertTrue(driver.closed)

        // Missing ViGEmBus driver on initialization must fail cleanly without faking active state
        val failingBackend = ViGEmX360GamepadBackend(
            driverFactory = {
                RecordingWindowsDriver(
                    failOnOpen = ViGEmBusNotFoundException("VIGEM_ERROR_BUS_NOT_FOUND (ViGEmBus driver missing)")
                )
            },
            availabilityProbe = { WindowsHostProbe.probe("Linux", "amd64", emptyList()) },
            logger = silentLogger
        )
        failingBackend.initialize()
        assertFalse(failingBackend.isReady)
        val failDiag = failingBackend.diagnostics()
        assertFalse(failDiag.isVirtualDeviceActive)
        assertFalse(failDiag.isInitialized)
        assertEquals("Initialization Failed", failDiag.statusLabel)
        assertNotNull(failDiag.lastError)
        assertTrue(failDiag.lastError!!.contains("BUS_NOT_FOUND"))
        failingBackend.publishState(s1)
        failingBackend.close()
    }
}
