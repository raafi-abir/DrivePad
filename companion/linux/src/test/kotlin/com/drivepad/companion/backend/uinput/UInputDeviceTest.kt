package com.drivepad.companion.backend.uinput

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UInputDeviceTest {

    class RecordingInputEventWriter(
        var failOnOpen: Throwable? = null,
        var failOnWrite: Throwable? = null
    ) : InputEventWriter {
        var openedPath: String? = null
        val enabledEventTypes = mutableListOf<Int>()
        val enabledKeyBits = mutableListOf<Int>()
        val enabledAbsBits = mutableListOf<Int>()
        val configuredAxes = mutableListOf<UInputAxisSpec>()
        var setupDeviceConfig: UInputDeviceConfig? = null
        var deviceCreated = false
        var deviceDestroyed = false
        var closed = false
        val writtenBatches = mutableListOf<List<InputEventRecord>>()

        override fun open(devicePath: String) {
            failOnOpen?.let { throw it }
            openedPath = devicePath
        }

        override fun enableEventType(eventType: Int) {
            enabledEventTypes.add(eventType)
        }

        override fun enableKeyBit(keyCode: Int) {
            enabledKeyBits.add(keyCode)
        }

        override fun enableAbsBit(absCode: Int) {
            enabledAbsBits.add(absCode)
        }

        override fun setupAbsAxis(axis: UInputAxisSpec) {
            configuredAxes.add(axis)
        }

        override fun setupDevice(config: UInputDeviceConfig) {
            setupDeviceConfig = config
        }

        override fun createDevice() {
            deviceCreated = true
        }

        override fun writeEvents(events: List<InputEventRecord>) {
            failOnWrite?.let { throw it }
            writtenBatches.add(events.toList())
        }

        override fun destroyDevice() {
            deviceDestroyed = true
        }

        override fun close() {
            closed = true
        }
    }

    @Test
    fun `registers EV_SYN EV_KEY EV_ABS all buttons and axes then creates device`() {
        val writer = RecordingInputEventWriter()
        val config = UInputDeviceConfig()
        val device = UInputDevice(config, writer)

        device.create()

        assertTrue(device.isCreated())
        assertEquals("/dev/uinput", writer.openedPath)
        assertEquals(
            listOf(LinuxInputConstants.EV_SYN, LinuxInputConstants.EV_KEY, LinuxInputConstants.EV_ABS),
            writer.enabledEventTypes
        )
        assertEquals(GamepadButtonMapping.allLinuxButtonCodes, writer.enabledKeyBits)
        assertEquals(
            listOf(LinuxInputConstants.ABS_X, LinuxInputConstants.ABS_Z, LinuxInputConstants.ABS_RZ),
            writer.enabledAbsBits
        )
        assertEquals(3, writer.configuredAxes.size)
        assertEquals("DrivePad Virtual Gamepad", writer.setupDeviceConfig?.deviceName)
        assertTrue(writer.deviceCreated)
    }

    @Test
    fun `suppresses duplicate states and batches changed events with SYN_REPORT`() {
        val writer = RecordingInputEventWriter()
        val device = UInputDevice(UInputDeviceConfig(), writer)
        device.create()

        val initialAxes = intArrayOf(0, 0, 0)
        val initialButtons = BooleanArray(GamepadButton.entries.size) { false }
        // Exact initial neutral -> no-op write suppressed
        assertFalse(device.applyState(initialAxes, initialButtons))
        assertEquals(0, writer.writtenBatches.size)

        // Move steering + engage throttle + press HORN & HANDBRAKE simultaneously
        val axes1 = intArrayOf(16384, 255, 0)
        val buttons1 = BooleanArray(GamepadButton.entries.size) { false }
        buttons1[GamepadButton.HORN.ordinal] = true
        buttons1[GamepadButton.HANDBRAKE.ordinal] = true

        assertTrue(device.applyState(axes1, buttons1))
        assertEquals(1, writer.writtenBatches.size)
        val batch1 = writer.writtenBatches[0]
        assertEquals(
            listOf(
                InputEventRecord(LinuxInputConstants.EV_ABS, LinuxInputConstants.ABS_X, 16384),
                InputEventRecord(LinuxInputConstants.EV_ABS, LinuxInputConstants.ABS_Z, 255),
                InputEventRecord(LinuxInputConstants.EV_KEY, LinuxInputConstants.BTN_SOUTH, 1),
                InputEventRecord(LinuxInputConstants.EV_KEY, LinuxInputConstants.BTN_EAST, 1),
                InputEventRecord(LinuxInputConstants.EV_SYN, LinuxInputConstants.SYN_REPORT, 0)
            ),
            batch1
        )

        // Identical duplicate state -> suppressed cleanly
        assertFalse(device.applyState(axes1.copyOf(), buttons1.copyOf()))
        assertEquals(1, writer.writtenBatches.size)

        // Release HORN while keeping HANDBRAKE & steering -> only HORN release emitted + SYN_REPORT
        val buttons2 = buttons1.copyOf()
        buttons2[GamepadButton.HORN.ordinal] = false
        assertTrue(device.applyState(axes1, buttons2))
        assertEquals(2, writer.writtenBatches.size)
        assertEquals(
            listOf(
                InputEventRecord(LinuxInputConstants.EV_KEY, LinuxInputConstants.BTN_SOUTH, 0),
                InputEventRecord(LinuxInputConstants.EV_SYN, LinuxInputConstants.SYN_REPORT, 0)
            ),
            writer.writtenBatches[1]
        )
    }

    @Test
    fun `close sends forced neutral and releases all buttons then destroys and closes device`() {
        val writer = RecordingInputEventWriter()
        val device = UInputDevice(UInputDeviceConfig(), writer)
        device.create()

        val activeButtons = BooleanArray(GamepadButton.entries.size) { false }
        activeButtons[GamepadButton.LIGHTS.ordinal] = true
        device.applyState(intArrayOf(-32768, 255, 0), activeButtons)

        device.close()

        assertFalse(device.isCreated())
        assertTrue(writer.deviceDestroyed)
        assertTrue(writer.closed)

        val shutdownBatch = writer.writtenBatches.last()
        // Neutral axes for all 3 axes + 0 release for all 11 buttons + SYN_REPORT
        assertEquals(3 + GamepadButton.entries.size + 1, shutdownBatch.size)
        assertEquals(InputEventRecord(LinuxInputConstants.EV_SYN, LinuxInputConstants.SYN_REPORT, 0), shutdownBatch.last())
        assertTrue(shutdownBatch.filter { it.type == LinuxInputConstants.EV_ABS }.all { it.value == 0 })
        assertTrue(shutdownBatch.filter { it.type == LinuxInputConstants.EV_KEY }.all { it.value == 0 })
    }

    @Test
    fun `closes writer cleanly when open fails`() {
        val writer = RecordingInputEventWriter(failOnOpen = UInputPermissionException("Permission denied"))
        val device = UInputDevice(UInputDeviceConfig(), writer)

        var threw = false
        try {
            device.create()
        } catch (_: UInputPermissionException) {
            threw = true
        }
        assertTrue(threw)
        assertFalse(device.isCreated())
        assertTrue(writer.closed)
    }
}
