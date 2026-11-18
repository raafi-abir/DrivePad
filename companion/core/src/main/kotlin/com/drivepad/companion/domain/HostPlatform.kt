package com.drivepad.companion.domain

/**
 * Host operating system detection helper for backend selection, diagnostics,
 * and cross-platform configuration/profile directory paths.
 */
enum class HostPlatform(val displayName: String) {
    LINUX("Linux"),
    WINDOWS("Windows"),
    MACOS("macOS"),
    OTHER("Other");

    companion object {
        fun detect(osName: String? = System.getProperty("os.name")): HostPlatform {
            val normalized = osName?.trim()?.lowercase().orEmpty()
            return when {
                normalized.contains("win") -> WINDOWS
                normalized.contains("linux") -> LINUX
                normalized.contains("mac") || normalized.contains("darwin") -> MACOS
                else -> OTHER
            }
        }

        fun current(): HostPlatform = detect()
    }
}
