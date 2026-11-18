package com.drivepad.companion.diagnostics

import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.domain.HostPlatform
import com.drivepad.companion.protocol.ProtocolConstants
import java.util.Properties

data class ReleaseDiagnosticSnapshot(
    val drivepadVersion: String,
    val companionVersion: String,
    val protocolVersion: Int,
    val hostPlatform: HostPlatform,
    val osDescription: String,
    val selectedBackend: GamepadBackendType,
    val backendAvailable: Boolean,
    val backendAvailabilitySummary: String
)

/**
 * Exposes consistent release versioning across DrivePad Companion modules while keeping
 * Protocol v1 (`PROTOCOL_VERSION_V1 = 1`) completely independent of app release versions.
 */
object ReleaseMetadata {
    private const val RESOURCE_NAME = "drivepad-release.properties"

    val drivepadVersion: String
    val companionVersion: String
    val protocolVersion: Int = ProtocolConstants.PROTOCOL_VERSION_V1

    init {
        val props = Properties()
        ReleaseMetadata::class.java.classLoader
            ?.getResourceAsStream(RESOURCE_NAME)
            ?.use { props.load(it) }
        drivepadVersion = props.getProperty("drivepad.version")?.trim()?.takeIf { it.isNotEmpty() } ?: "1.0.0"
        companionVersion = props.getProperty("companion.version")?.trim()?.takeIf { it.isNotEmpty() } ?: "1.0.0"
    }

    fun snapshot(
        selectedBackend: GamepadBackendType,
        backendAvailable: Boolean,
        backendAvailabilitySummary: String,
        hostPlatform: HostPlatform = HostPlatform.current(),
        osDescription: String = "${System.getProperty("os.name") ?: "Unknown"} (${System.getProperty("os.arch") ?: "Unknown"})"
    ): ReleaseDiagnosticSnapshot = ReleaseDiagnosticSnapshot(
        drivepadVersion = drivepadVersion,
        companionVersion = companionVersion,
        protocolVersion = protocolVersion,
        hostPlatform = hostPlatform,
        osDescription = osDescription,
        selectedBackend = selectedBackend,
        backendAvailable = backendAvailable,
        backendAvailabilitySummary = backendAvailabilitySummary
    )

    fun formatVersionSummary(snapshot: ReleaseDiagnosticSnapshot): String = buildString {
        appendLine("DrivePad Version    : ${snapshot.drivepadVersion}")
        appendLine("Companion Version   : ${snapshot.companionVersion}")
        appendLine("Protocol Version    : v${snapshot.protocolVersion} (${ProtocolConstants.PACKET_SIZE_BYTES}-byte Little-Endian UDP)")
        appendLine("Operating System    : ${snapshot.hostPlatform.displayName} [${snapshot.osDescription}]")
        appendLine("Selected Backend    : ${snapshot.selectedBackend.cliName}")
        appendLine(
            "Backend Available   : ${if (snapshot.backendAvailable) "YES" else "NO"} (${snapshot.backendAvailabilitySummary})"
        )
    }
}
