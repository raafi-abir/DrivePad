package com.drivepad.companion.domain

import com.drivepad.companion.config.GamepadBackendType
import kotlinx.coroutines.flow.StateFlow

/**
 * Platform-independent abstractions decoupling UDP networking and domain processing
 * from OS-specific virtual gamepad backends (Linux `/dev/uinput` or Windows ViGEmBus X360).
 */
interface ControllerStateProvider {
    val controllerState: StateFlow<ControllerState>
}

interface ControllerOutput {
    fun publishState(state: ControllerState)
    fun resetToSafeState(state: ControllerState)
}

data class BackendDiagnosticsSnapshot(
    val backendType: GamepadBackendType,
    val backendName: String,
    val hostPlatform: HostPlatform = HostPlatform.current(),
    val hostSupported: Boolean,
    val driverOrDeviceReady: Boolean,
    val availabilitySummary: String,
    val isInitialized: Boolean,
    val isVirtualDeviceActive: Boolean,
    val deviceName: String?,
    val statusLabel: String,
    val lastError: String? = null,
    val activeProfileId: String? = null,
    val activeProfileSummary: String? = null
)

interface GamepadBackend : ControllerOutput, AutoCloseable {
    val backendName: String
    val isReady: Boolean
    fun initialize()
    fun diagnostics(): BackendDiagnosticsSnapshot = BackendDiagnosticsSnapshot(
        backendType = GamepadBackendType.NOOP,
        backendName = backendName,
        hostPlatform = HostPlatform.current(),
        hostSupported = false,
        driverOrDeviceReady = false,
        availabilitySummary = "Not applicable",
        isInitialized = isReady,
        isVirtualDeviceActive = false,
        deviceName = null,
        statusLabel = if (isReady) "Ready" else "Unavailable",
        lastError = null
    )
}

class NoOpGamepadBackend(
    private val probeAvailability: () -> Triple<Boolean, Boolean, String> = {
        Triple(false, false, "Disabled (NoOp mode)")
    },
    private val activeProfileId: String? = null,
    private val activeProfileSummary: String? = null,
    private val hostPlatform: HostPlatform = HostPlatform.current()
) : GamepadBackend {
    override val backendName: String = "NoOpGamepadBackend"
    override val isReady: Boolean = true

    override fun initialize() {}
    override fun publishState(state: ControllerState) {}
    override fun resetToSafeState(state: ControllerState) {}
    override fun close() {}

    override fun diagnostics(): BackendDiagnosticsSnapshot {
        val (supportedOs, driverReady, summary) = probeAvailability()
        return BackendDiagnosticsSnapshot(
            backendType = GamepadBackendType.NOOP,
            backendName = backendName,
            hostPlatform = hostPlatform,
            hostSupported = supportedOs,
            driverOrDeviceReady = driverReady,
            availabilitySummary = summary,
            isInitialized = true,
            isVirtualDeviceActive = false,
            deviceName = null,
            statusLabel = "NoOp Active (No virtual gamepad created)",
            lastError = null,
            activeProfileId = activeProfileId,
            activeProfileSummary = activeProfileSummary
        )
    }
}
