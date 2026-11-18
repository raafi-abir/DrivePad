package com.drivepad.companion.backend.uinput

import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.protocol.TransmissionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class GamepadMappingAndAbiTest {

    @Test
    fun `steering normalization maps exactly to -32768, 0, and 32767`() {
        assertEquals(0, GamepadAxisMapping.mapSteering(0.0f))
        assertEquals(0, GamepadAxisMapping.mapSteering(-0.0f))
        assertEquals(-32768, GamepadAxisMapping.mapSteering(-1.0f))
        assertEquals(32767, GamepadAxisMapping.mapSteering(1.0f))
        assertEquals(-16384, GamepadAxisMapping.mapSteering(-0.5f))
        assertEquals(16384, GamepadAxisMapping.mapSteering(0.5f))
        // Clamping & NaN safety
        assertEquals(-32768, GamepadAxisMapping.mapSteering(-1.5f))
        assertEquals(32767, GamepadAxisMapping.mapSteering(1.5f))
        assertEquals(0, GamepadAxisMapping.mapSteering(Float.NaN))
        assertEquals(0, GamepadAxisMapping.mapSteering(Float.POSITIVE_INFINITY))
    }

    @Test
    fun `throttle and brake remain strictly digital 0 or 255`() {
        assertEquals(0, GamepadAxisMapping.mapDigitalPedal(0.0f))
        assertEquals(255, GamepadAxisMapping.mapDigitalPedal(1.0f))
        // Never emit intermediate values even if called with fractional floats
        assertEquals(0, GamepadAxisMapping.mapDigitalPedal(0.5f))
        assertEquals(0, GamepadAxisMapping.mapDigitalPedal(0.99f))
        assertEquals(0, GamepadAxisMapping.mapDigitalPedal(Float.NaN))
    }

    @Test
    fun `button extraction maps all buttons cleanly and guarantees all released on SafeNeutral`() {
        val allPressed = ControllerState(
            steering = -0.25f,
            throttle = 1.0f,
            brake = 0.0f,
            handbrake = true,
            currentGear = 4,
            transmissionMode = TransmissionMode.MANUAL,
            lights = true,
            horn = true,
            wipers = true,
            hazardLights = true,
            leftIndicator = true,
            rightIndicator = true,
            cruiseControl = true,
            engine = true,
            isSafeNeutral = false
        )
        val pressed = GamepadButtonMapping.extractButtonStates(
            state = allPressed,
            shiftUpPressed = true,
            shiftDownPressed = false
        )
        assertTrue(pressed[GamepadButton.HANDBRAKE.ordinal])
        assertTrue(pressed[GamepadButton.HORN.ordinal])
        assertTrue(pressed[GamepadButton.LIGHTS.ordinal])
        assertTrue(pressed[GamepadButton.WIPERS.ordinal])
        assertTrue(pressed[GamepadButton.LEFT_INDICATOR.ordinal])
        assertTrue(pressed[GamepadButton.RIGHT_INDICATOR.ordinal])
        assertTrue(pressed[GamepadButton.HAZARD_LIGHTS.ordinal])
        assertTrue(pressed[GamepadButton.CRUISE_CONTROL.ordinal])
        assertTrue(pressed[GamepadButton.ENGINE.ordinal])
        assertTrue(pressed[GamepadButton.SHIFT_UP.ordinal])
        assertFalse(pressed[GamepadButton.SHIFT_DOWN.ordinal])

        val safeButtons = GamepadButtonMapping.extractButtonStates(
            state = ControllerState.safeNeutral(),
            shiftUpPressed = true,
            shiftDownPressed = true
        )
        assertTrue(safeButtons.all { !it })
    }

    @Test
    fun `Linux LP64 ABI struct encoders produce exact verified sizes and offsets`() {
        val evBytes = ByteArray(LinuxInputConstants.INPUT_EVENT_SIZE_BYTES)
        assertEquals(24, evBytes.size)
        LinuxInputConstants.encodeInputEventInto(
            type = LinuxInputConstants.EV_ABS,
            code = LinuxInputConstants.ABS_X,
            value = -32768,
            out = evBytes,
            offset = 0,
            byteOrder = ByteOrder.LITTLE_ENDIAN
        )
        val evBb = ByteBuffer.wrap(evBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(0L, evBb.getLong(0))
        assertEquals(0L, evBb.getLong(8))
        assertEquals(LinuxInputConstants.EV_ABS.toShort(), evBb.getShort(16))
        assertEquals(LinuxInputConstants.ABS_X.toShort(), evBb.getShort(18))
        assertEquals(-32768, evBb.getInt(20))

        val setupBytes = LinuxInputConstants.encodeUInputSetup(
            busType = LinuxInputConstants.BUS_VIRTUAL,
            vendorId = 0x1209,
            productId = 0x4450,
            versionId = 0x0001,
            name = "DrivePad Virtual Gamepad",
            byteOrder = ByteOrder.LITTLE_ENDIAN
        )
        assertEquals(92, setupBytes.size)
        val setupBb = ByteBuffer.wrap(setupBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(LinuxInputConstants.BUS_VIRTUAL.toShort(), setupBb.getShort(0))
        assertEquals(0x1209.toShort(), setupBb.getShort(2))
        assertEquals(0x4450.toShort(), setupBb.getShort(4))
        assertEquals(0x0001.toShort(), setupBb.getShort(6))
        val nameSlice = setupBytes.copyOfRange(8, 8 + "DrivePad Virtual Gamepad".length)
        assertEquals("DrivePad Virtual Gamepad", String(nameSlice, Charsets.UTF_8))
        assertEquals(0.toByte(), setupBytes[8 + "DrivePad Virtual Gamepad".length])
        assertEquals(0, setupBb.getInt(88))

        val absBytes = LinuxInputConstants.encodeUInputAbsSetup(
            axisCode = LinuxInputConstants.ABS_X,
            initialValue = 0,
            minimum = -32768,
            maximum = 32767,
            byteOrder = ByteOrder.LITTLE_ENDIAN
        )
        assertEquals(28, absBytes.size)
        val absBb = ByteBuffer.wrap(absBytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(LinuxInputConstants.ABS_X.toShort(), absBb.getShort(0))
        assertEquals(0.toShort(), absBb.getShort(2))
        assertEquals(0, absBb.getInt(4))
        assertEquals(-32768, absBb.getInt(8))
        assertEquals(32767, absBb.getInt(12))
    }
}
