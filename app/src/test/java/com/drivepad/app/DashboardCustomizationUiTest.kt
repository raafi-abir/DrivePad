package com.drivepad.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardCustomizationUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun resetDashboardBeforeTest() {
        val appContainer = (composeRule.activity.application as DrivePadApplication).container
        runBlocking {
            appContainer.dashboardPreferencesRepository.resetToDefaults()
        }
        composeRule.waitForIdle()
    }

    @After
    fun resetDashboardAfterTest() {
        val appContainer = (composeRule.activity.application as DrivePadApplication).container
        runBlocking {
            appContainer.dashboardPreferencesRepository.resetToDefaults()
        }
    }

    @Test
    @Config(qualifiers = "w800dp-h480dp-land")
    fun dashboardStudio_switchesLayoutsUnitsThemeAndHidesUnavailableGauges() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("home_customize_dashboard_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Preview shows Racing layout initially
        composeRule.onNodeWithTag("dashboard_preview_container").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_layout_racing").assertIsDisplayed()

        // Switch through Truck, Minimal, Compact layouts and verify preview updates immediately
        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("layout_option_truck"))
        composeRule.onNodeWithTag("layout_option_truck").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag("dashboard_layout_truck")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_layout_truck").assertIsDisplayed()

        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("layout_option_minimal"))
        composeRule.onNodeWithTag("layout_option_minimal").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag("dashboard_layout_minimal")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_layout_minimal").assertIsDisplayed()

        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("layout_option_compact"))
        composeRule.onNodeWithTag("layout_option_compact").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag("dashboard_layout_compact")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_layout_compact").assertIsDisplayed()

        // Test scenario switcher (Stale / Disconnected) renders safety banner cleanly
        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("preview_scenario_stale"))
        composeRule.onNodeWithTag("preview_scenario_stale").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag("dashboard_status_banner")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_status_banner").assertIsDisplayed()

        composeRule.onNodeWithTag("preview_scenario_live_cruising").performClick()
        composeRule.waitForIdle()

        // Test theme & unit changes
        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("theme_option_light"))
        composeRule.onNodeWithTag("theme_option_light").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("unit_option_mph"))
        composeRule.onNodeWithTag("unit_option_mph").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("mph"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("mph", useUnmergedTree = true).assertIsDisplayed()

        // Unavailable external truck channels must NOT expose fake customizable toggles
        composeRule.onNodeWithTag("toggle_gauge_fuel").assertDoesNotExist()
        composeRule.onNodeWithTag("toggle_gauge_oil_pressure").assertDoesNotExist()
        composeRule.onNodeWithTag("toggle_gauge_water_temperature").assertDoesNotExist()
        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("unavailable_gauges_notice"))
        composeRule.onNodeWithTag("unavailable_gauges_notice").assertIsDisplayed()

        // Restore defaults resets back to Racing layout + km/h
        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("btn_restore_dashboard_defaults"))
        composeRule.onNodeWithTag("btn_restore_dashboard_defaults").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag("dashboard_layout_racing")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("dashboard_layout_racing").assertIsDisplayed()
        composeRule.onNodeWithText("km/h", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w640dp-h360dp-land")
    fun customizedLayoutAndUnit_applyInsideLiveCockpitWithoutScrolling() {
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_customize_dashboard_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Pick Truck layout and MPH unit
        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("layout_option_truck"))
        composeRule.onNodeWithTag("layout_option_truck").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag("dashboard_layout_truck")).fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithTag("dashboard_customization_list")
            .performScrollToNode(hasTestTag("unit_option_mph"))
        composeRule.onNodeWithTag("unit_option_mph").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(androidx.compose.ui.test.hasText("mph"), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }

        // Return to Home and launch Cockpit
        composeRule.onNodeWithTag("dashboard_custom_back_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag("launch_controller_button")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("launch_controller_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000L) {
            composeRule.onAllNodes(hasTestTag("dashboard_layout_truck")).fetchSemanticsNodes().isNotEmpty()
        }

        // Verify Cockpit renders Truck layout with MPH speed, RPM gauge, gear indicator, and controller inputs visible
        composeRule.onNodeWithTag("dashboard_layout_truck").assertIsDisplayed()
        composeRule.onNodeWithText("mph", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("rpm_meter_card").assertIsDisplayed()
        composeRule.onNodeWithTag("manual_gear_display").assertIsDisplayed()
        composeRule.onNodeWithTag("steering_wheel_container").assertIsDisplayed()
        composeRule.onNodeWithTag("pedal_accelerator").assertIsDisplayed()
        composeRule.onNodeWithTag("cockpit_customize_dashboard_button").assertIsDisplayed()
    }
}
