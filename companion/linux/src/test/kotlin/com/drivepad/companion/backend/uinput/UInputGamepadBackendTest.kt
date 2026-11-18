package com.drivepad.companion.backend.uinput

import com.drivepad.companion.backend.uinput.UInputDeviceTest.RecordingInputEventWriter
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.domain.NoOpGamepadBackend
import com.drivepad.companion.protocol.TransmissionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class UInputGamepadBackendTest {

    private val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))

    private fun availableAvailability() = UInputAvailability(
        supportedOs = true,
        osDescription = "Linux (amd64)",
        deviceExists = true,
        deviceWritable = true,
        summary = "Available (/dev/uinput writable)"
    )

    @Test
    fun `publishes state changes asynchronously handles gear shift pulses and resets on failsafe`() {
        val writer = RecordingInputEventWriter()
        val backend = UInputGamepadBackend(
            deviceConfig = UInputDeviceConfig(),
            writerFactory = { writer },
            availabilityProbe = { availableAvailability() },
            shiftPulseDurationMs = 25L,
            logger = silentLogger
        )

        backend.initialize()
        assertTrue(backend.isReady)
        val diag = backend.diagnostics()
        assertTrue(diag.isVirtualDeviceActive)
        assertNull(diag.lastError)
        assertEquals("DrivePad Virtual Gamepad", diag.deviceName)

        val s1 = ControllerState(
            steering = 1.0f,
            throttle = 1.0f,
            brake = 0.0f,
            handbrake = false,
            currentGear = 1,
            transmissionMode = TransmissionMode.MANUAL,
            lights = true,
            horn = true,
            isSafeNeutral = false
        )
        backend.publishState(s1)
        assertTrue(backend.awaitIdle())

        val s2 = s1.copy(currentGear = 2)
        backend.publishState(s2)
        assertTrue(backend.awaitIdle())

        val shiftUpPresses = writer.writtenBatches.flatten()
            .filter { it.type == LinuxInputConstants.EV_KEY && it.code == LinuxInputConstants.BTN_DPAD_UP }
            .map { it.value }
        assertEquals(listOf(1, 0), shiftUpPresses)

        backend.resetToSafeState(ControllerState.safeNeutral())
        assertTrue(backend.awaitIdle())

        val afterReset = writer.writtenBatches.last()
        assertEquals(InputEventRecord(LinuxInputConstants.EV_SYN, LinuxInputConstants.SYN_REPORT, 0), afterReset.last())
        assertTrue(afterReset.any { it == InputEventRecord(LinuxInputConstants.EV_ABS, LinuxInputConstants.ABS_X, 0) })
        assertTrue(afterReset.any { it == InputEventRecord(LinuxInputConstants.EV_ABS, LinuxInputConstants.ABS_Z, 0) })
        assertTrue(afterReset.any { it == InputEventRecord(LinuxInputConstants.EV_KEY, LinuxInputConstants.BTN_NORTH, 0) })
        assertTrue(afterReset.any { it == InputEventRecord(LinuxInputConstants.EV_KEY, LinuxInputConstants.BTN_SOUTH, 0) })

        backend.close()
        assertFalse(backend.isReady)
        assertTrue(writer.deviceDestroyed)
        assertTrue(writer.closed)
    }

    @Test
    fun `handles initialization failure gracefully without claiming virtual device is active`() {
        val failingWriter = RecordingInputEventWriter(
            failOnOpen = UInputPermissionException("Permission denied on /dev/uinput")
        )
        val backend = UInputGamepadBackend(
            deviceConfig = UInputDeviceConfig(),
            writerFactory = { failingWriter },
            availabilityProbe = {
                UInputAvailability(
                    supportedOs = true,
                    osDescription = "Linux (amd64)",
                    deviceExists = true,
                    deviceWritable = false,
                    summary = "Permission denied on /dev/uinput"
                )
            },
            logger = silentLogger
        )

        backend.initialize()

        assertFalse(backend.isReady)
        val diag = backend.diagnostics()
        assertFalse(diag.isInitialized)
        assertFalse(diag.isVirtualDeviceActive)
        assertEquals("Initialization Failed", diag.statusLabel)
        assertNotNull(diag.lastError)
        assertTrue(diag.lastError!!.contains("Permission denied"))

        backend.publishState(ControllerState(steering = 0.5f, isSafeNeutral = false))
        backend.resetToSafeState(ControllerState.safeNeutral())
        backend.close()
    }

    @Test
    fun `NoOp backend stays ready without touching uinput`() {
        val noop = NoOpGamepadBackend()
        noop.initialize()
        assertTrue(noop.isReady)
        val diag = noop.diagnostics()
        assertEquals(GamepadBackendType.NOOP, diag.backendType)
        assertFalse(diag.isVirtualDeviceActive)
        assertNull(diag.lastError)
        noop.publishState(ControllerState(throttle = 1.0f, isSafeNeutral = false))
        noop.resetToSafeState(ControllerState.safeNeutral())
        noop.close()
    }
}
