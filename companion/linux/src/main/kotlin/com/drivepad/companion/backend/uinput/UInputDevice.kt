package com.drivepad.companion.backend.uinput

/**
 * Manages the lifecycle and delta state tracking of a virtual Linux `uinput` gamepad device.
 *
 * Emits ONLY changed axes and changed button transitions followed by an explicit
 * `EV_SYN / SYN_REPORT` event, avoiding duplicate kernel writes while preserving
 * deterministic ordering and full button release on failsafe reset or shutdown.
 */
class UInputDevice(
    private val config: UInputDeviceConfig,
    private val writer: InputEventWriter
) : AutoCloseable {

    private var created: Boolean = false
    private val lastAxisValues = IntArray(config.axes.size)
    private val lastButtonStates = BooleanArray(config.buttonCodes.size)

    fun isCreated(): Boolean = created

    fun create() {
        if (created) return
        try {
            writer.open(config.devicePath)
            writer.enableEventType(LinuxInputConstants.EV_SYN)
            writer.enableEventType(LinuxInputConstants.EV_KEY)
            writer.enableEventType(LinuxInputConstants.EV_ABS)

            for (code in config.buttonCodes) {
                writer.enableKeyBit(code)
            }
            for (axis in config.axes) {
                writer.enableAbsBit(axis.code)
                writer.setupAbsAxis(axis)
            }

            writer.setupDevice(config)
            writer.createDevice()

            for (i in config.axes.indices) {
                lastAxisValues[i] = config.axes[i].initialValue
            }
            lastButtonStates.fill(false)
            created = true
        } catch (t: Throwable) {
            runCatching { writer.close() }
            created = false
            throw t
        }
    }

    /**
     * Emits only changed axes and button transitions followed by `EV_SYN / SYN_REPORT`.
     * Returns `true` if a batch was written, `false` if state was completely unchanged.
     */
    fun applyState(axes: IntArray, buttons: BooleanArray): Boolean {
        check(created) { "UInputDevice is not created" }
        require(axes.size == config.axes.size) { "Expected ${config.axes.size} axes, got ${axes.size}" }
        require(buttons.size == config.buttonCodes.size) {
            "Expected ${config.buttonCodes.size} button states, got ${buttons.size}"
        }

        val batch = ArrayList<InputEventRecord>(axes.size + buttons.size + 1)

        for (i in axes.indices) {
            val target = axes[i].coerceIn(config.axes[i].minimum, config.axes[i].maximum)
            if (target != lastAxisValues[i]) {
                batch.add(InputEventRecord(LinuxInputConstants.EV_ABS, config.axes[i].code, target))
            }
        }

        for (i in buttons.indices) {
            val pressed = buttons[i]
            if (pressed != lastButtonStates[i]) {
                batch.add(
                    InputEventRecord(
                        LinuxInputConstants.EV_KEY,
                        config.buttonCodes[i],
                        if (pressed) 1 else 0
                    )
                )
            }
        }

        if (batch.isEmpty()) {
            return false
        }

        batch.add(InputEventRecord(LinuxInputConstants.EV_SYN, LinuxInputConstants.SYN_REPORT, 0))
        writer.writeEvents(batch)

        for (i in axes.indices) {
            lastAxisValues[i] = axes[i].coerceIn(config.axes[i].minimum, config.axes[i].maximum)
        }
        for (i in buttons.indices) {
            lastButtonStates[i] = buttons[i]
        }
        return true
    }

    /**
     * Releases every button and resets every axis to neutral, ending with `SYN_REPORT`.
     * When [forceAll] is true (e.g., on shutdown), explicitly emits neutral axis values and
     * release `0` events for every button regardless of cached delta state before finalizing.
     */
    fun resetToNeutral(forceAll: Boolean = false): Boolean {
        if (!created) return false
        val batch = ArrayList<InputEventRecord>(config.axes.size + config.buttonCodes.size + 1)

        for (i in config.axes.indices) {
            val neutral = config.axes[i].initialValue
            if (forceAll || lastAxisValues[i] != neutral) {
                batch.add(InputEventRecord(LinuxInputConstants.EV_ABS, config.axes[i].code, neutral))
            }
        }

        for (i in config.buttonCodes.indices) {
            if (forceAll || lastButtonStates[i]) {
                batch.add(InputEventRecord(LinuxInputConstants.EV_KEY, config.buttonCodes[i], 0))
            }
        }

        if (batch.isEmpty()) {
            return false
        }

        batch.add(InputEventRecord(LinuxInputConstants.EV_SYN, LinuxInputConstants.SYN_REPORT, 0))
        writer.writeEvents(batch)

        for (i in config.axes.indices) {
            lastAxisValues[i] = config.axes[i].initialValue
        }
        lastButtonStates.fill(false)
        return true
    }

    override fun close() {
        val wasCreated = created
        try {
            if (wasCreated) {
                // Guaranteed shutdown sequence: neutral axes + release every button + SYN_REPORT -> destroy -> close
                runCatching { resetToNeutral(forceAll = true) }
                runCatching { writer.destroyDevice() }
            }
        } finally {
            created = false
            runCatching { writer.close() }
        }
    }
}
