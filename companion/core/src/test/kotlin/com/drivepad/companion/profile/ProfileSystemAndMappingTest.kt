package com.drivepad.companion.profile

import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.protocol.TransmissionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.util.Properties

class ProfileSystemAndMappingTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private fun msToNs(ms: Long): Long = ms * 1_000_000L

    @Test
    fun `ets2 built-in profile loads with expected axes buttons curve and transmission safety`() {
        val ets2 = ProfilePresets.ets2()
        assertEquals("ets2", ets2.id)
        assertEquals(StandardAxisCode.LEFT_X, ets2.steeringAxis)
        assertEquals(StandardAxisCode.RIGHT_TRIGGER, ets2.throttleAxis)
        assertEquals(StandardAxisCode.LEFT_TRIGGER, ets2.brakeAxis)
        assertEquals(SteeringCurve.PROGRESSIVE, ets2.steeringCurve)
        assertEquals(0.03f, ets2.steeringDeadzone, 0.0001f)
        assertEquals(75L, ets2.pulseDurationMs)
        assertTrue(ets2.clampParkToNeutralInAuto)
        assertTrue(ets2.multiStepAutoShift)

        assertEquals(ButtonBehavior.HOLD, ets2.buttonBindings.getValue(LogicalButton.HORN).behavior)
        assertEquals(ButtonBehavior.PULSE_ON_CHANGE, ets2.buttonBindings.getValue(LogicalButton.LIGHTS).behavior)
        assertEquals(ButtonBehavior.PULSE_ON_CHANGE, ets2.buttonBindings.getValue(LogicalButton.HANDBRAKE).behavior)
        assertEquals(ButtonBehavior.PULSE_ON_CHANGE, ets2.buttonBindings.getValue(LogicalButton.LEFT_INDICATOR).behavior)
        assertEquals(ButtonBehavior.PULSE_ON_CHANGE, ets2.buttonBindings.getValue(LogicalButton.RIGHT_INDICATOR).behavior)
        assertEquals(ButtonBehavior.PULSE_ON_CHANGE, ets2.buttonBindings.getValue(LogicalButton.HAZARD_LIGHTS).behavior)
        assertEquals(ButtonBehavior.PULSE_ON_CHANGE, ets2.buttonBindings.getValue(LogicalButton.WIPERS).behavior)
        assertEquals(ButtonBehavior.PULSE_ON_CHANGE, ets2.buttonBindings.getValue(LogicalButton.CRUISE_CONTROL).behavior)
        assertEquals(ButtonBehavior.PULSE_ON_CHANGE, ets2.buttonBindings.getValue(LogicalButton.ENGINE).behavior)
        assertEquals(StandardButtonCode.DPAD_UP, ets2.buttonBindings.getValue(LogicalButton.SHIFT_UP).button)
        assertEquals(StandardButtonCode.DPAD_DOWN, ets2.buttonBindings.getValue(LogicalButton.SHIFT_DOWN).button)
    }

    @Test
    fun `ProfileManager seeds generic and ets2 profiles persists custom profiles and parses legacy Linux codes`() {
        val dir = tempDir.newFolder("profiles").toPath()
        val manager = ProfileManager(dir)

        val ids = manager.listProfileIds()
        assertTrue(ids.contains("generic"))
        assertTrue(ids.contains("ets2"))
        assertTrue(Files.exists(dir.resolve("generic.properties")))
        assertTrue(Files.exists(dir.resolve("ets2.properties")))

        assertEquals("generic", manager.resolveProfile("default").id)
        val loadedEts2 = manager.resolveProfile("ets2")
        assertEquals(ProfilePresets.ets2(), loadedEts2)

        val customEts2 = loadedEts2.copy(
            id = "ets2_heavy",
            displayName = "ETS2 Heavy Cargo",
            steeringDeadzone = 0.05f,
            steeringSensitivity = 1.25f,
            steeringInvert = true,
            steeringCurveGamma = 1.8f,
            pulseDurationMs = 90L
        )
        manager.save(customEts2)

        val reloaded = manager.resolveProfile("ets2_heavy")
        assertEquals(customEts2, reloaded)

        // Legacy Linux .properties aliases (ABS_X / ABS_Z / ABS_RZ / BTN_SOUTH / BTN_THUMBL) remain compatible
        val legacyProps = Properties().apply {
            setProperty("profile.id", "legacy_ets2")
            setProperty("axis.steering", "ABS_X")
            setProperty("axis.throttle", "ABS_Z")
            setProperty("axis.brake", "ABS_RZ")
            setProperty("button.engine.code", "BTN_THUMBL")
            setProperty("button.horn.code", "BTN_SOUTH")
        }
        val parsedLegacy = ProfileManager.fromProperties(legacyProps, "ets2")
        assertEquals(StandardAxisCode.LEFT_X, parsedLegacy.steeringAxis)
        assertEquals(StandardAxisCode.RIGHT_TRIGGER, parsedLegacy.throttleAxis)
        assertEquals(StandardAxisCode.LEFT_TRIGGER, parsedLegacy.brakeAxis)
        assertEquals(StandardButtonCode.LEFT_THUMB, parsedLegacy.buttonBindings.getValue(LogicalButton.ENGINE).button)
        assertEquals(StandardButtonCode.SOUTH, parsedLegacy.buttonBindings.getValue(LogicalButton.HORN).button)
    }

    @Test
    fun `ProfileValidator rejects invalid deadzone sensitivity duplicate axes duplicate buttons and malformed files`() {
        val base = ProfilePresets.ets2()

        assertThrowsValidation { base.copy(steeringDeadzone = 0.6f) }
        assertThrowsValidation { base.copy(steeringDeadzone = Float.NaN) }
        assertThrowsValidation { base.copy(steeringSensitivity = 0.05f) }
        assertThrowsValidation { base.copy(pulseDurationMs = 2L) }
        assertThrowsValidation { base.copy(throttleAxis = StandardAxisCode.LEFT_X) }

        val dupButtons = base.buttonBindings.toMutableMap()
        dupButtons[LogicalButton.LIGHTS] = ButtonBinding(StandardButtonCode.SOUTH, ButtonBehavior.PULSE_ON_CHANGE)
        assertThrowsValidation { base.copy(buttonBindings = dupButtons) }

        val dir = tempDir.newFolder("bad_profiles").toPath()
        val badFile = dir.resolve("broken.properties")
        Files.writeString(
            badFile,
            """
            profile.id=broken
            axis.steering=ABS_DOES_NOT_EXIST
            """.trimIndent()
        )
        val manager = ProfileManager(dir)
        assertThrowsValidation { manager.resolveProfile("broken") }
    }

    @Test
    fun `SteeringTransform applies deadzone progressive curve sensitivity inversion and NaN clamping`() {
        val ets2 = ProfilePresets.ets2().copy(
            steeringDeadzone = 0.10f,
            steeringSensitivity = 1.0f,
            steeringCurve = SteeringCurve.PROGRESSIVE,
            steeringCurveGamma = 2.0f,
            steeringInvert = false
        )

        assertEquals(0.0f, SteeringTransform.apply(0.0f, ets2), 0.0001f)
        assertEquals(0.0f, SteeringTransform.apply(0.09f, ets2), 0.0001f)
        assertEquals(0.0f, SteeringTransform.apply(-0.10f, ets2), 0.0001f)

        assertEquals(1.0f, SteeringTransform.apply(1.0f, ets2), 0.0001f)
        assertEquals(-1.0f, SteeringTransform.apply(-1.0f, ets2), 0.0001f)

        assertEquals(0.25f, SteeringTransform.apply(0.55f, ets2), 0.001f)
        assertEquals(-0.25f, SteeringTransform.apply(-0.55f, ets2), 0.001f)

        val inverted = ets2.copy(steeringInvert = true)
        assertEquals(-0.25f, SteeringTransform.apply(0.55f, inverted), 0.001f)
        assertEquals(0.25f, SteeringTransform.apply(-0.55f, inverted), 0.001f)

        assertEquals(0.0f, SteeringTransform.apply(Float.NaN, ets2), 0.0001f)
        assertEquals(0.0f, SteeringTransform.apply(Float.POSITIVE_INFINITY, ets2), 0.0001f)
    }

    @Test
    fun `ETS2 toggle buttons emit momentary pulses on transition only while Horn remains HOLD`() {
        val mapper = ProfileInputMapper(ProfilePresets.ets2())

        val s0 = ControllerState(isSafeNeutral = false)
        val out0 = mapper.processState(s0, msToNs(0))
        assertTrue(out0.buttons.all { !it })

        val s1 = ControllerState(
            steering = -0.6f,
            throttle = 1.0f,
            brake = 1.0f,
            leftIndicator = true,
            lights = true,
            horn = true,
            isSafeNeutral = false
        )
        val out1 = mapper.processState(s1, msToNs(10))
        assertTrue(out1.throttleActive)
        assertTrue(out1.brakeActive)
        assertTrue(out1.buttons[LogicalButton.LEFT_INDICATOR.ordinal])
        assertTrue(out1.buttons[LogicalButton.LIGHTS.ordinal])
        assertTrue(out1.buttons[LogicalButton.HORN.ordinal])

        val outDup = mapper.processState(s1, msToNs(30))
        assertTrue(outDup.buttons[LogicalButton.LEFT_INDICATOR.ordinal])
        assertTrue(outDup.buttons[LogicalButton.HORN.ordinal])

        val outExpired = mapper.processState(s1, msToNs(86))
        assertFalse(outExpired.buttons[LogicalButton.LEFT_INDICATOR.ordinal])
        assertFalse(outExpired.buttons[LogicalButton.LIGHTS.ordinal])
        assertTrue(outExpired.buttons[LogicalButton.HORN.ordinal])

        val s2 = s1.copy(leftIndicator = false, horn = false)
        val outOffToggle = mapper.processState(s2, msToNs(120))
        assertTrue("Toggle OFF must trigger another ETS2 pulse", outOffToggle.buttons[LogicalButton.LEFT_INDICATOR.ordinal])
        assertFalse(outOffToggle.buttons[LogicalButton.LIGHTS.ordinal])
        assertFalse(outOffToggle.buttons[LogicalButton.HORN.ordinal])

        val deadline = mapper.nextTimerDeadlineNanos()
        assertNotNull(deadline)
        val outAfterTick = mapper.onTimerTick(s2, msToNs(196))
        assertFalse(outAfterTick.buttons[LogicalButton.LEFT_INDICATOR.ordinal])
    }

    @Test
    fun `ETS2 Park clamps cleanly to Neutral in Auto mode and R to D emits two sequential shift pulses`() {
        val mapper = ProfileInputMapper(ProfilePresets.ets2())

        mapper.processState(ControllerState(currentGear = -2, transmissionMode = TransmissionMode.AUTO, isSafeNeutral = false), msToNs(0))
        val parkToN = mapper.processState(
            ControllerState(currentGear = 0, transmissionMode = TransmissionMode.AUTO, isSafeNeutral = false),
            msToNs(10)
        )
        assertFalse(parkToN.buttons[LogicalButton.SHIFT_UP.ordinal])
        assertFalse(parkToN.buttons[LogicalButton.SHIFT_DOWN.ordinal])

        val nToR = mapper.processState(
            ControllerState(currentGear = -1, transmissionMode = TransmissionMode.AUTO, isSafeNeutral = false),
            msToNs(20)
        )
        assertTrue(nToR.buttons[LogicalButton.SHIFT_DOWN.ordinal])
        val endDown = mapper.onTimerTick(
            ControllerState(currentGear = -1, transmissionMode = TransmissionMode.AUTO, isSafeNeutral = false),
            msToNs(100)
        )
        assertFalse(endDown.buttons[LogicalButton.SHIFT_DOWN.ordinal])

        val jumpState = ControllerState(currentGear = 1, transmissionMode = TransmissionMode.AUTO, isSafeNeutral = false)
        val step1Start = mapper.processState(jumpState, msToNs(200))
        assertTrue(step1Start.buttons[LogicalButton.SHIFT_UP.ordinal])

        val gapStart = mapper.onTimerTick(jumpState, msToNs(276))
        assertFalse(gapStart.buttons[LogicalButton.SHIFT_UP.ordinal])

        val step2Start = mapper.onTimerTick(jumpState, msToNs(322))
        assertTrue(step2Start.buttons[LogicalButton.SHIFT_UP.ordinal])

        val step2End = mapper.onTimerTick(jumpState, msToNs(398))
        assertFalse(step2End.buttons[LogicalButton.SHIFT_UP.ordinal])
        assertFalse(mapper.hasPendingTimers())
    }

    @Test
    fun `failsafe reset clears all active pulses shift queue steering and pedals`() {
        val mapper = ProfileInputMapper(ProfilePresets.ets2())
        mapper.processState(
            ControllerState(
                steering = 0.9f,
                throttle = 1.0f,
                brake = 1.0f,
                horn = true,
                lights = true,
                currentGear = 1,
                transmissionMode = TransmissionMode.MANUAL,
                isSafeNeutral = false
            ),
            msToNs(10)
        )
        val queuedShift = mapper.processState(
            ControllerState(
                steering = 0.9f,
                throttle = 1.0f,
                horn = true,
                currentGear = 2,
                transmissionMode = TransmissionMode.MANUAL,
                isSafeNeutral = false
            ),
            msToNs(20)
        )
        assertTrue(queuedShift.buttons[LogicalButton.SHIFT_UP.ordinal])

        val safe = mapper.reset()
        assertEquals(0.0f, safe.steeringNormalized, 0.0001f)
        assertFalse(safe.throttleActive)
        assertFalse(safe.brakeActive)
        assertTrue(safe.buttons.all { !it })
        assertFalse(mapper.hasPendingTimers())
    }

    private inline fun assertThrowsValidation(block: () -> Unit) {
        var threw = false
        try {
            block()
        } catch (_: ProfileValidationException) {
            threw = true
        }
        assertTrue("Expected ProfileValidationException", threw)
    }
}
