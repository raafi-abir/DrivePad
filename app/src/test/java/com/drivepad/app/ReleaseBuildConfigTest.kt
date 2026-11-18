package com.drivepad.app

import com.drivepad.app.core.network.DrivePadPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReleaseBuildConfigTest {

    @Test
    fun `applicationId release version metadata and protocol version are consistent`() {
        assertEquals("com.drivepad.app", BuildConfig.APPLICATION_ID)
        assertEquals("com.drivepad.app", MainActivity::class.java.`package`?.name)
        assertEquals("1.0.0", BuildConfig.VERSION_NAME)
        assertEquals(1, BuildConfig.VERSION_CODE)
        assertEquals(DrivePadPacket.PROTOCOL_VERSION_V1.toInt(), BuildConfig.PROTOCOL_VERSION)
    }

    @Test
    fun `release signing config uses environment variables without hardcoded secrets and excludes debug appcheck`() {
        val appGradle = locateAppBuildGradle()
        val text = appGradle.readText()

        assertTrue(text.contains("System.getenv(\"KEYSTORE_PATH\")"))
        assertTrue(text.contains("System.getenv(\"STORE_PASSWORD\")"))
        assertTrue(text.contains("System.getenv(\"KEY_PASSWORD\")"))
        assertTrue(text.contains("signingConfig = signingConfigs.getByName(\"release\")"))

        // Ensure debug AppCheck library is not active on classpath
        val debugAppCheckPresent = try {
            Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
            true
        } catch (_: ClassNotFoundException) {
            false
        }
        assertFalse("Debug AppCheck provider must not be linked into release builds", debugAppCheckPresent)
    }

    private fun locateAppBuildGradle(): File {
        val direct = File("build.gradle.kts")
        if (direct.exists() && direct.readText().contains("signingConfigs")) {
            return direct
        }
        val fromRoot = File("app/build.gradle.kts")
        assertTrue("Could not find app/build.gradle.kts from ${File("").absolutePath}", fromRoot.exists())
        return fromRoot
    }
}
