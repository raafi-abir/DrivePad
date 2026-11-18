package com.drivepad.app

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RacingCockpitRotationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun launchControllerCockpit_rotatesToLandscapeAndFitsScreen() {
        composeRule.waitForIdle()
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            composeRule.activity.requestedOrientation
        )

        composeRule.onNodeWithTag("launch_controller_button").assertIsDisplayed()
        composeRule.onNodeWithTag("launch_controller_button").performClick()
        composeRule.waitForIdle()

        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            composeRule.activity.requestedOrientation
        )

        composeRule.onNodeWithTag("steering_wheel_container").assertIsDisplayed()
        composeRule.onNodeWithTag("rpm_meter_card").assertIsDisplayed()
        composeRule.onNodeWithTag("rpm_value_display").assertIsDisplayed()
        composeRule.onNodeWithText("Steering").assertDoesNotExist()
        composeRule.onNodeWithText("Throttle").assertDoesNotExist()
        composeRule.onNodeWithTag("pedal_brake").assertIsDisplayed()
        composeRule.onNodeWithTag("pedal_accelerator").assertIsDisplayed()
        composeRule.onNodeWithTag("btn_handbrake").assertIsDisplayed()

        // Navigate back to Home and verify orientation is restored
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()

        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED,
            composeRule.activity.requestedOrientation
        )
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun gearboxDropdown_switchesAutoGearsRND() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("launch_controller_button").performClick()
        composeRule.waitForIdle()

        // Default Auto gear is D
        composeRule.onNodeWithTag("manual_gear_display").assertIsDisplayed()
        composeRule.onNodeWithText("D", useUnmergedTree = true).assertIsDisplayed()

        // Open Gearbox dropdown and switch to R -> N -> D
        composeRule.onNodeWithTag("gearbox_dropdown_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auto_gear_R").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("R", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("gearbox_dropdown_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auto_gear_N").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("N", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag("gearbox_dropdown_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("auto_gear_D").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("D", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun manualMode_placesShiftButtonsAbovePedalsAndShiftsThroughNeutralAndReverse() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("launch_controller_button").performClick()
        composeRule.waitForIdle()

        // Open Gearbox dropdown and switch to Manual transmission
        composeRule.onNodeWithTag("gearbox_dropdown_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("transmission_selector_manual").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        val shiftUpNode = composeRule.onNodeWithTag("manual_shift_up").assertIsDisplayed()
        val shiftDownNode = composeRule.onNodeWithTag("manual_shift_down").assertIsDisplayed()
        val brakeNode = composeRule.onNodeWithTag("pedal_brake").assertIsDisplayed()
        val accelNode = composeRule.onNodeWithTag("pedal_accelerator").assertIsDisplayed()
        composeRule.onNodeWithTag("manual_gear_display").assertIsDisplayed()

        // Verify Shift Up is directly above Accelerator and Shift Down is directly above Brake
        val shiftUpBounds = shiftUpNode.getBoundsInRoot()
        val shiftDownBounds = shiftDownNode.getBoundsInRoot()
        val brakeBounds = brakeNode.getBoundsInRoot()
        val accelBounds = accelNode.getBoundsInRoot()

        assertTrue(
            "Shift Up (${shiftUpBounds.bottom}) must sit above Accelerator (${accelBounds.top})",
            shiftUpBounds.bottom <= accelBounds.top
        )
        assertTrue(
            "Shift Down (${shiftDownBounds.bottom}) must sit above Brake (${brakeBounds.top})",
            shiftDownBounds.bottom <= brakeBounds.top
        )
        val shiftUpCenterX = (shiftUpBounds.left.value + shiftUpBounds.right.value) / 2f
        val accelCenterX = (accelBounds.left.value + accelBounds.right.value) / 2f
        val shiftDownCenterX = (shiftDownBounds.left.value + shiftDownBounds.right.value) / 2f
        val brakeCenterX = (brakeBounds.left.value + brakeBounds.right.value) / 2f
        assertTrue("Shift Up should align with Accelerator horizontally", abs(shiftUpCenterX - accelCenterX) < 12f)
        assertTrue("Shift Down should align with Brake horizontally", abs(shiftDownCenterX - brakeCenterX) < 12f)

        // Default gear is 1 -> Shift Down -> N -> Shift Down -> R -> Shift Up -> N -> Shift Up -> 1
        composeRule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()

        shiftDownNode.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("N", useUnmergedTree = true).assertIsDisplayed()

        shiftDownNode.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("R", useUnmergedTree = true).assertIsDisplayed()

        shiftUpNode.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("N", useUnmergedTree = true).assertIsDisplayed()

        shiftUpNode.performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun cockpitControls_doNotRenderWiperOrToggleTextBadgesWhenEnabled() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("launch_controller_button").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("btn_function_lights").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("btn_function_wipers").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("btn_handbrake").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("WIPER", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("LIGHTS", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("PARK", useUnmergedTree = true).assertDoesNotExist()

        // Engine toggle runs continuous physics frame loop; advance Compose clock manually
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithTag("btn_function_engine").assertIsDisplayed().performClick()
        composeRule.mainClock.advanceTimeBy(150L)

        composeRule.onNodeWithText("WIPER", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("LIGHTS", useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText("PARK", useUnmergedTree = true).assertDoesNotExist()

        composeRule.onNodeWithTag("btn_function_engine").performClick()
        composeRule.mainClock.autoAdvance = true
    }
}
