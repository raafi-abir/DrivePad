package com.drivepad.companion.backend.windows

import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.diagnostics.MonotonicClock
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.diagnostics.SystemMonotonicClock
import com.drivepad.companion.domain.BackendDiagnosticsSnapshot
import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.domain.GamepadBackend
import com.drivepad.companion.domain.HostPlatform
import com.drivepad.companion.profile.AsyncProfileWriterLoop
import com.drivepad.companion.profile.ControllerProfile
import com.drivepad.companion.profile.ProfilePresets

/**
 * Windows virtual Xbox 360 (`XUSB`) gamepad backend using Nefarius `ViGEmBus` (`ViGEmClient.dll`).
 *
 * Uses the shared [AsyncProfileWriterLoop] for ETS2 progressive steering, toggle-to-pulse transitions,
 * and automatic/sequential shift pulses, while translating normalized outputs into Xbox 360 reports.
 * Never simulates or fakes an active device when `ViGEmBus` or Windows host support is unavailable.
 */
class ViGEmX360GamepadBackend(
    private val deviceName: String = CompanionConfig.DEFAULT_VIRTUAL_DEVICE_NAME,
    private val vendorId: Int? = null,
    private val productId: Int? = null,
    private val driverFactory: () -> WindowsVirtualGamepadDriver = { NativeViGEmGamepadDriver() },
    private val availabilityProbe: () -> WindowsAvailability = { WindowsHostProbe.probe() },
    shiftPulseDurationMs: Long? = null,
    profile: ControllerProfile = ProfilePresets.generic(),
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val logger: StructuredLogger = StructuredLogger()
) : GamepadBackend {

    override val backendName: String = "ViGEmX360GamepadBackend"

    private val effectiveProfile: ControllerProfile = if (shiftPulseDurationMs != null) {
        profile.copy(pulseDurationMs = shiftPulseDurationMs.coerceIn(10L, 500L))
    } else {
        profile
    }
    private val neutralReport: XusbReportData = WindowsX360Mapping.neutralReport(effectiveProfile)

    @Volatile
    private var device: ViGEmX360Device? = null

    private var writerLoop: AsyncProfileWriterLoop? = null

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var virtualDeviceActive: Boolean = false

    @Volatile
    private var statusLabel: String = "Uninitialized"

    @Volatile
    private var lastError: String? = null

    override val isReady: Boolean
        get() = initialized && virtualDeviceActive && lastError == null

    override fun initialize() {
        if (initialized && virtualDeviceActive) return

        val availability = availabilityProbe()
        val createdDevice = ViGEmX360Device(
            driver = driverFactory(),
            vendorId = vendorId,
            productId = productId,
            initialReport = neutralReport
        )

        try {
            createdDevice.create()
        } catch (t: Throwable) {
            val errMsg = t.message ?: t.javaClass.simpleName
            initialized = false
            virtualDeviceActive = false
            lastError = errMsg
            statusLabel = "Initialization Failed"
            logger.error(
                "ViGEmX360Backend",
                "Failed to initialize Windows virtual Xbox 360 gamepad",
                "device" to deviceName,
                "profile" to effectiveProfile.id,
                "error" to errMsg
            )
            return
        }

        device = createdDevice
        val loop = AsyncProfileWriterLoop(
            profile = effectiveProfile,
            clock = clock,
            threadName = "drivepad-vigem-writer",
            onApplyNormalized = { mapped ->
                val report = WindowsX360Mapping.mapOutput(mapped, effectiveProfile)
                createdDevice.applyReport(report)
            },
            onResetToNeutral = { createdDevice.resetToNeutral(force = false) },
            onError = { t ->
                val errMsg = t.message ?: t.javaClass.simpleName
                lastError = errMsg
                statusLabel = "Driver I/O Error"
                virtualDeviceActive = false
                logger.error("ViGEmX360Backend", "Error submitting ViGEm X360 report", "error" to errMsg)
            }
        )
        writerLoop = loop
        loop.start()

        initialized = true
        virtualDeviceActive = true
        lastError = null
        statusLabel = "Virtual Xbox 360 Device Active (${availability.osDescription})"

        logger.info(
            "ViGEmX360Backend",
            "Windows ViGEm Xbox 360 virtual gamepad created successfully",
            "profile" to effectiveProfile.id,
            "os" to availability.osDescription
        )
    }

    override fun publishState(state: ControllerState) {
        if (!virtualDeviceActive) return
        writerLoop?.enqueueState(state)
    }

    override fun resetToSafeState(state: ControllerState) {
        if (!virtualDeviceActive) return
        writerLoop?.enqueueSafeReset(state)
    }

    fun awaitIdle(timeoutMs: Long = 2_000L): Boolean = writerLoop?.awaitIdle(timeoutMs) ?: true

    override fun close() {
        val loop = writerLoop
        writerLoop = null
        loop?.stopAndReset()

        val dev = device
        device = null
        dev?.close()

        virtualDeviceActive = false
        initialized = false
        if (lastError == null) {
            statusLabel = "Stopped cleanly"
        }
        logger.info("ViGEmX360Backend", "Windows virtual Xbox 360 gamepad closed cleanly")
    }

    override fun diagnostics(): BackendDiagnosticsSnapshot {
        val probe = availabilityProbe()
        return BackendDiagnosticsSnapshot(
            backendType = GamepadBackendType.VIGEM_X360,
            backendName = backendName,
            hostPlatform = HostPlatform.current(),
            hostSupported = probe.supportedOs,
            driverOrDeviceReady = probe.clientLibraryFound,
            availabilitySummary = probe.summary,
            isInitialized = initialized,
            isVirtualDeviceActive = virtualDeviceActive,
            deviceName = if (virtualDeviceActive) "Controller (Xbox 360 For Windows) [$deviceName]" else deviceName,
            statusLabel = statusLabel,
            lastError = lastError,
            activeProfileId = effectiveProfile.id,
            activeProfileSummary = effectiveProfile.summaryString()
        )
    }
}
