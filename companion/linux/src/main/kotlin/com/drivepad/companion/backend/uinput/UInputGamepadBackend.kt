package com.drivepad.companion.backend.uinput

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
import com.drivepad.companion.profile.NormalizedGamepadOutput
import com.drivepad.companion.profile.ProfilePresets

/**
 * Production Linux `/dev/uinput` virtual gamepad backend implementing [GamepadBackend].
 *
 * Delegates profile curves, toggle-to-pulse transitions, and gear stepping to the shared
 * [AsyncProfileWriterLoop] and translates normalized outputs into Linux `evdev` events.
 */
class UInputGamepadBackend(
    private val deviceConfig: UInputDeviceConfig = UInputDeviceConfig(),
    private val writerFactory: () -> InputEventWriter = { NativeUInputEventWriter() },
    private val availabilityProbe: () -> UInputAvailability = {
        UInputAvailabilityProbe.probe(deviceConfig.devicePath)
    },
    shiftPulseDurationMs: Long? = null,
    profile: ControllerProfile = ProfilePresets.generic(),
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val logger: StructuredLogger = StructuredLogger()
) : GamepadBackend {

    override val backendName: String = "UInputGamepadBackend"

    private val effectiveProfile: ControllerProfile = if (shiftPulseDurationMs != null) {
        profile.copy(pulseDurationMs = shiftPulseDurationMs.coerceIn(10L, 500L))
    } else {
        profile
    }

    @Volatile
    private var device: UInputDevice? = null

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
        val createdDevice = UInputDevice(deviceConfig, writerFactory())
        try {
            createdDevice.create()
        } catch (t: Throwable) {
            val errMsg = t.message ?: t.javaClass.simpleName
            initialized = false
            virtualDeviceActive = false
            lastError = errMsg
            statusLabel = "Initialization Failed"
            logger.error(
                "UInputBackend",
                "Failed to initialize virtual gamepad",
                "device" to deviceConfig.deviceName,
                "path" to deviceConfig.devicePath,
                "profile" to effectiveProfile.id,
                "error" to errMsg
            )
            return
        }

        device = createdDevice
        val loop = AsyncProfileWriterLoop(
            profile = effectiveProfile,
            clock = clock,
            threadName = "drivepad-uinput-writer",
            onApplyNormalized = { mapped -> applyNormalizedToDevice(createdDevice, mapped) },
            onResetToNeutral = { createdDevice.resetToNeutral(forceAll = false) },
            onError = { t ->
                val errMsg = t.message ?: t.javaClass.simpleName
                lastError = errMsg
                statusLabel = "Backend I/O Error"
                virtualDeviceActive = false
                logger.error("UInputBackend", "Error writing to uinput device", "error" to errMsg)
            }
        )
        writerLoop = loop
        loop.start()

        initialized = true
        virtualDeviceActive = true
        lastError = null
        statusLabel = "Virtual Device Active (${availability.osDescription})"

        logger.info(
            "UInputBackend",
            "Virtual gamepad created successfully",
            "name" to deviceConfig.deviceName,
            "path" to deviceConfig.devicePath,
            "vid" to "0x${deviceConfig.vendorId.toString(16)}",
            "pid" to "0x${deviceConfig.productId.toString(16)}",
            "profile" to effectiveProfile.id
        )
    }

    private fun applyNormalizedToDevice(dev: UInputDevice, mapped: NormalizedGamepadOutput) {
        val axes = intArrayOf(
            GamepadAxisMapping.mapSteering(mapped.steeringNormalized),
            GamepadAxisMapping.mapDigitalPedal(mapped.throttleActive),
            GamepadAxisMapping.mapDigitalPedal(mapped.brakeActive)
        )
        dev.applyState(axes, mapped.buttons)
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
        logger.info("UInputBackend", "Virtual gamepad closed cleanly")
    }

    override fun diagnostics(): BackendDiagnosticsSnapshot {
        val probe = availabilityProbe()
        return BackendDiagnosticsSnapshot(
            backendType = GamepadBackendType.UINPUT,
            backendName = backendName,
            hostPlatform = HostPlatform.current(),
            hostSupported = probe.supportedOs,
            driverOrDeviceReady = probe.deviceExists && probe.deviceWritable,
            availabilitySummary = probe.summary,
            isInitialized = initialized,
            isVirtualDeviceActive = virtualDeviceActive,
            deviceName = deviceConfig.deviceName,
            statusLabel = statusLabel,
            lastError = lastError,
            activeProfileId = effectiveProfile.id,
            activeProfileSummary = effectiveProfile.summaryString()
        )
    }
}
