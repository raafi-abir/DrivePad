package com.drivepad.app

import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.ControllerProfile
import com.drivepad.app.domain.model.TransmissionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivePadArchitectureTest {

    @Test
    fun defaultProfiles_containEts2TruckProfile() {
        val profiles = ControllerProfile.DefaultProfiles
        val ets2Profile = profiles.find { it.id == "ets2_truck" }

        assertNotNull("ETS2 profile must exist by default", ets2Profile)
        assertEquals("Euro Truck Simulator 2", ets2Profile?.name)
        assertTrue("ETS2 profile should be default", ets2Profile?.isDefault == true)
        assertEquals(TransmissionMode.AUTO, ets2Profile?.config?.defaultTransmissionMode)
    }

    @Test
    fun controllerConfig_defaultValuesAreWithinSafeRanges() {
        val config = ControllerConfig()
        assertTrue("Sensitivity must be positive", config.steeringSensitivity > 0f)
        assertTrue("Deadzone must be non-negative and <= 0.25", config.steeringDeadzone in 0.0f..0.25f)
        assertFalse("Motion steering should be disabled by default until calibrated", config.motionSteeringEnabled)
        assertTrue("Return to center should be enabled by default", config.returnToCenterEnabled)
        assertEquals(42100, config.udpPort)
    }
}
