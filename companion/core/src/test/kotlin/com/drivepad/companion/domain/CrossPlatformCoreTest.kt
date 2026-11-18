package com.drivepad.companion.domain

import com.drivepad.companion.config.ConfigManager
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.profile.ProfileManager
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Paths

class CrossPlatformCoreTest {

    @Test
    fun `HostPlatform detects Linux Windows macOS and Unknown reliably`() {
        assertEquals(HostPlatform.LINUX, HostPlatform.detect("Linux"))
        assertEquals(HostPlatform.WINDOWS, HostPlatform.detect("Windows 11"))
        assertEquals(HostPlatform.WINDOWS, HostPlatform.detect("Windows 10"))
        assertEquals(HostPlatform.MACOS, HostPlatform.detect("Mac OS X"))
        assertEquals(HostPlatform.OTHER, HostPlatform.detect("FreeBSD"))
        assertEquals(HostPlatform.OTHER, HostPlatform.detect(null))
    }

    @Test
    fun `GamepadBackendType AUTO resolves to UINPUT on Linux VIGEM on Windows and NOOP elsewhere`() {
        assertEquals(GamepadBackendType.UINPUT, GamepadBackendType.AUTO.resolveForHost(HostPlatform.LINUX))
        assertEquals(GamepadBackendType.VIGEM_X360, GamepadBackendType.AUTO.resolveForHost(HostPlatform.WINDOWS))
        assertEquals(GamepadBackendType.NOOP, GamepadBackendType.AUTO.resolveForHost(HostPlatform.MACOS))
        assertEquals(GamepadBackendType.NOOP, GamepadBackendType.AUTO.resolveForHost(HostPlatform.OTHER))

        assertEquals(GamepadBackendType.VIGEM_X360, GamepadBackendType.parse("vigem"))
        assertEquals(GamepadBackendType.VIGEM_X360, GamepadBackendType.parse("windows"))
        assertEquals(GamepadBackendType.AUTO, GamepadBackendType.parse("auto"))
        assertEquals(GamepadBackendType.UINPUT, GamepadBackendType.parse("uinput"))
    }

    @Test
    fun `ConfigManager and ProfileManager resolve XDG paths on Linux and AppData paths on Windows`() {
        val linuxCfg = ConfigManager.defaultConfigPath(
            osName = "Linux",
            appData = null,
            xdgConfigHome = "/home/driver/.config",
            userHome = "/home/driver"
        )
        assertEquals(
            Paths.get("/home/driver/.config", "drivepad-companion", "config.properties"),
            linuxCfg
        )

        val winCfg = ConfigManager.defaultConfigPath(
            osName = "Windows 11",
            appData = "C:\\Users\\Driver\\AppData\\Roaming",
            xdgConfigHome = null,
            userHome = "C:\\Users\\Driver"
        )
        assertEquals(
            Paths.get("C:\\Users\\Driver\\AppData\\Roaming", "drivepad-companion", "config.properties"),
            winCfg
        )

        val winProfiles = ProfileManager.defaultProfilesDir(
            osName = "Windows 10",
            appData = null,
            xdgConfigHome = null,
            userHome = "/users/driver"
        )
        assertEquals(
            Paths.get("/users/driver", "AppData", "Roaming", "drivepad-companion", "profiles"),
            winProfiles
        )
    }
}
