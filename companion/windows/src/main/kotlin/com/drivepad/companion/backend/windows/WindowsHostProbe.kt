package com.drivepad.companion.backend.windows

import com.drivepad.companion.domain.HostPlatform
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

data class WindowsAvailability(
    val supportedOs: Boolean,
    val osDescription: String,
    val clientLibraryFound: Boolean,
    val clientLibraryPath: String?,
    val summary: String
)

/**
 * Probes the Windows host environment for OS support and `ViGEmClient.dll` presence without
 * loading native binaries on non-Windows hosts or throwing during diagnostics queries.
 */
object WindowsHostProbe {
    const val VIGEM_DLL_NAME = "ViGEmClient.dll"

    fun probe(
        osName: String = System.getProperty("os.name") ?: "Unknown",
        osArch: String = System.getProperty("os.arch") ?: "Unknown",
        searchPaths: List<Path> = defaultDllSearchPaths()
    ): WindowsAvailability {
        val platform = HostPlatform.detect(osName)
        val osDesc = "$osName ($osArch)"
        if (platform != HostPlatform.WINDOWS) {
            return WindowsAvailability(
                supportedOs = false,
                osDescription = osDesc,
                clientLibraryFound = false,
                clientLibraryPath = null,
                summary = "Unsupported OS ($osName) — Windows 10/11 + ViGEmBus required"
            )
        }

        val found = searchPaths.firstOrNull { path ->
            runCatching {
                path.fileName?.toString().equals(VIGEM_DLL_NAME, ignoreCase = true) &&
                    Files.isRegularFile(path)
            }.getOrDefault(false)
        }
        return if (found != null) {
            val normalized = found.toAbsolutePath().normalize().toString()
            WindowsAvailability(
                supportedOs = true,
                osDescription = osDesc,
                clientLibraryFound = true,
                clientLibraryPath = normalized,
                summary = "Windows detected ($VIGEM_DLL_NAME found at $normalized)"
            )
        } else {
            WindowsAvailability(
                supportedOs = true,
                osDescription = osDesc,
                clientLibraryFound = false,
                clientLibraryPath = null,
                summary = "Windows detected ($VIGEM_DLL_NAME not in explicit trusted install dirs; " +
                    "requires ViGEmBus v1.22.0 + 64-bit ViGEmClient.dll in native\\windows, %APPDATA%\\drivepad-companion, or Program Files)"
            )
        }
    }

    fun detectAppHome(): Path? {
        val uri = runCatching {
            WindowsHostProbe::class.java.protectionDomain?.codeSource?.location?.toURI()
        }.getOrNull() ?: return null
        val codePath = runCatching { Paths.get(uri) }.getOrNull() ?: return null
        val jarOrClassesDir = if (Files.isRegularFile(codePath)) codePath.parent else codePath
        return if (jarOrClassesDir != null && jarOrClassesDir.fileName?.toString().equals("lib", ignoreCase = true)) {
            jarOrClassesDir.parent
        } else {
            jarOrClassesDir
        }
    }

    /**
     * Explicit trusted locations for `ViGEmClient.dll`. Never includes arbitrary current working
     * directories (`user.dir`) so invoking DrivePad from Downloads or %TEMP% cannot plant a Trojan DLL.
     */
    @Suppress("UNUSED_PARAMETER")
    fun defaultDllSearchPaths(
        workingDir: String = System.getProperty("user.dir") ?: ".",
        appHome: Path? = detectAppHome(),
        appData: String? = System.getenv("APPDATA"),
        programFiles: String? = System.getenv("ProgramFiles"),
        systemRoot: String? = System.getenv("SystemRoot")
    ): List<Path> {
        val candidates = linkedSetOf<Path>()
        if (appHome != null) {
            val cleanHome = appHome.toAbsolutePath().normalize()
            candidates.add(cleanHome.resolve("native").resolve("windows").resolve(VIGEM_DLL_NAME))
            candidates.add(cleanHome.resolve("lib").resolve(VIGEM_DLL_NAME))
            candidates.add(cleanHome.resolve(VIGEM_DLL_NAME))
        }
        if (!appData.isNullOrBlank()) {
            candidates.add(Paths.get(appData, "drivepad-companion", VIGEM_DLL_NAME).toAbsolutePath().normalize())
        }
        if (!programFiles.isNullOrBlank()) {
            candidates.add(
                Paths.get(programFiles, "Nefarius Software Solutions", "ViGEmClient", VIGEM_DLL_NAME)
                    .toAbsolutePath().normalize()
            )
        }
        if (!systemRoot.isNullOrBlank()) {
            candidates.add(Paths.get(systemRoot, "System32", VIGEM_DLL_NAME).toAbsolutePath().normalize())
        }
        return candidates.toList()
    }
}
