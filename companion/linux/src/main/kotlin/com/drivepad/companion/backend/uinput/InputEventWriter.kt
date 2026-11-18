package com.drivepad.companion.backend.uinput

import com.sun.jna.LastErrorException
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths

data class InputEventRecord(
    val type: Int,
    val code: Int,
    val value: Int
)

data class UInputAvailability(
    val supportedOs: Boolean,
    val osDescription: String,
    val deviceExists: Boolean,
    val deviceWritable: Boolean,
    val summary: String
)

object UInputAvailabilityProbe {
    fun probe(devicePath: String = UInputDeviceConfig.DEFAULT_DEVICE_PATH): UInputAvailability {
        val osName = System.getProperty("os.name") ?: "Unknown"
        val osArch = System.getProperty("os.arch") ?: "Unknown"
        val isLinux = osName.contains("linux", ignoreCase = true)
        if (!isLinux) {
            return UInputAvailability(
                supportedOs = false,
                osDescription = "$osName ($osArch)",
                deviceExists = false,
                deviceWritable = false,
                summary = "Unsupported OS ($osName) — Linux /dev/uinput required"
            )
        }

        val path = Paths.get(devicePath)
        val exists = Files.exists(path)
        if (!exists) {
            return UInputAvailability(
                supportedOs = true,
                osDescription = "$osName ($osArch)",
                deviceExists = false,
                deviceWritable = false,
                summary = "$devicePath missing (run: sudo modprobe uinput)"
            )
        }

        val writable = Files.isWritable(path)
        val summary = if (writable) {
            "Available ($devicePath writable)"
        } else {
            "Permission denied on $devicePath (add udev rule / uinput group)"
        }
        return UInputAvailability(
            supportedOs = true,
            osDescription = "$osName ($osArch)",
            deviceExists = true,
            deviceWritable = writable,
            summary = summary
        )
    }
}

open class UInputException(message: String, cause: Throwable? = null) : IOException(message, cause)

class UInputPermissionException(message: String, cause: Throwable? = null) : UInputException(message, cause)

class UInputUnavailableException(message: String, cause: Throwable? = null) : UInputException(message, cause)

/**
 * Abstraction over Linux `/dev/uinput` device opening, ioctl setup, batch event writing, and teardown.
 * Allows complete unit testing of mapping, batching, error handling, and failsafe resets without kernel access.
 */
interface InputEventWriter : AutoCloseable {
    fun open(devicePath: String)
    fun enableEventType(eventType: Int)
    fun enableKeyBit(keyCode: Int)
    fun enableAbsBit(absCode: Int)
    fun setupAbsAxis(axis: UInputAxisSpec)
    fun setupDevice(config: UInputDeviceConfig)
    fun createDevice()
    fun writeEvents(events: List<InputEventRecord>)
    fun destroyDevice()
}

interface LinuxLibC : Library {
    @Throws(LastErrorException::class)
    fun open(pathname: String, flags: Int): Int

    @Throws(LastErrorException::class)
    fun ioctl(fd: Int, request: NativeLong): Int

    @Throws(LastErrorException::class)
    fun ioctl(fd: Int, request: NativeLong, value: Int): Int

    @Throws(LastErrorException::class)
    fun ioctl(fd: Int, request: NativeLong, data: Pointer): Int

    @Throws(LastErrorException::class)
    fun write(fd: Int, buf: Pointer, count: NativeLong): NativeLong

    @Throws(LastErrorException::class)
    fun close(fd: Int): Int

    fun strerror(errnum: Int): String
}

/**
 * Native Linux `/dev/uinput` writer using JNA `LibC` bindings with verified 64-bit LP64 struct
 * layouts, explicit short-write looping, and safe errno diagnostics.
 */
class NativeUInputEventWriter(
    private val libcProvider: () -> LinuxLibC = { Native.load("c", LinuxLibC::class.java) }
) : InputEventWriter {

    private val libc: LinuxLibC by lazy(libcProvider)
    private var fd: Int = -1
    private var devicePath: String = UInputDeviceConfig.DEFAULT_DEVICE_PATH

    // Preallocated native buffer for up to 64 input_event structs (zero hot-path allocations)
    private val batchByteBuffer = ByteArray(64 * LinuxInputConstants.INPUT_EVENT_SIZE_BYTES)
    private val batchNativeMemory = Memory(batchByteBuffer.size.toLong())

    override fun open(devicePath: String) {
        val osName = System.getProperty("os.name") ?: "Unknown"
        if (!osName.contains("linux", ignoreCase = true)) {
            throw UInputUnavailableException(
                "Linux uinput backend requires a Linux host (current OS: $osName). Use --backend noop here."
            )
        }
        if (Native.POINTER_SIZE != 8) {
            throw UInputUnavailableException(
                "Unsupported pointer width (${Native.POINTER_SIZE * 8}-bit); verified ABI targets 64-bit Linux (x86_64/aarch64)."
            )
        }

        this.devicePath = devicePath
        try {
            fd = libc.open(devicePath, LinuxInputConstants.O_WRONLY)
        } catch (e: LastErrorException) {
            val errno = e.errorCode
            val errDesc = runCatching { libc.strerror(errno) }.getOrDefault("errno $errno")
            when (errno) {
                LinuxInputConstants.ENOENT, LinuxInputConstants.ENODEV -> {
                    throw UInputUnavailableException(
                        "$devicePath unavailable ($errDesc). Load the module on Arch Linux with: sudo modprobe uinput",
                        e
                    )
                }
                LinuxInputConstants.EACCES, LinuxInputConstants.EPERM -> {
                    throw UInputPermissionException(
                        "Permission denied opening $devicePath ($errDesc). " +
                            "Configure a safe udev rule for group 'uinput' and add your user to it (do NOT run the companion as root).",
                        e
                    )
                }
                else -> throw UInputException("Failed to open $devicePath: $errDesc (errno=$errno)", e)
            }
        }
    }

    override fun enableEventType(eventType: Int) {
        requireFd()
        invokeIoctlInt(LinuxInputConstants.UI_SET_EVBIT, eventType, "UI_SET_EVBIT($eventType)")
    }

    override fun enableKeyBit(keyCode: Int) {
        requireFd()
        invokeIoctlInt(LinuxInputConstants.UI_SET_KEYBIT, keyCode, "UI_SET_KEYBIT(0x${keyCode.toString(16)})")
    }

    override fun enableAbsBit(absCode: Int) {
        requireFd()
        invokeIoctlInt(LinuxInputConstants.UI_SET_ABSBIT, absCode, "UI_SET_ABSBIT(0x${absCode.toString(16)})")
    }

    override fun setupAbsAxis(axis: UInputAxisSpec) {
        requireFd()
        val encoded = LinuxInputConstants.encodeUInputAbsSetup(
            axisCode = axis.code,
            initialValue = axis.initialValue,
            minimum = axis.minimum,
            maximum = axis.maximum,
            fuzz = axis.fuzz,
            flat = axis.flat,
            resolution = axis.resolution
        )
        val mem = Memory(encoded.size.toLong())
        mem.write(0, encoded, 0, encoded.size)
        invokeIoctlPtr(LinuxInputConstants.UI_ABS_SETUP, mem, "UI_ABS_SETUP(${axis.name})")
    }

    override fun setupDevice(config: UInputDeviceConfig) {
        requireFd()
        val encoded = LinuxInputConstants.encodeUInputSetup(
            busType = config.busType,
            vendorId = config.vendorId,
            productId = config.productId,
            versionId = config.versionId,
            name = config.deviceName
        )
        val mem = Memory(encoded.size.toLong())
        mem.write(0, encoded, 0, encoded.size)
        invokeIoctlPtr(LinuxInputConstants.UI_DEV_SETUP, mem, "UI_DEV_SETUP(${config.deviceName})")
    }

    override fun createDevice() {
        requireFd()
        try {
            libc.ioctl(fd, NativeLong(LinuxInputConstants.UI_DEV_CREATE))
        } catch (e: LastErrorException) {
            throw translateIoctlError("UI_DEV_CREATE", e)
        }
    }

    override fun writeEvents(events: List<InputEventRecord>) {
        if (events.isEmpty()) return
        val activeFd = requireFd()

        val totalBytes = events.size * LinuxInputConstants.INPUT_EVENT_SIZE_BYTES
        val byteBuf = if (totalBytes <= batchByteBuffer.size) batchByteBuffer else ByteArray(totalBytes)
        val nativeMem = if (totalBytes <= batchByteBuffer.size) batchNativeMemory else Memory(totalBytes.toLong())

        for (i in events.indices) {
            val ev = events[i]
            LinuxInputConstants.encodeInputEventInto(
                type = ev.type,
                code = ev.code,
                value = ev.value,
                out = byteBuf,
                offset = i * LinuxInputConstants.INPUT_EVENT_SIZE_BYTES
            )
        }
        nativeMem.write(0, byteBuf, 0, totalBytes)

        var offset = 0
        while (offset < totalBytes) {
            val remaining = totalBytes - offset
            val ptr = if (offset == 0) nativeMem else nativeMem.share(offset.toLong())
            try {
                val written = libc.write(activeFd, ptr, NativeLong(remaining.toLong())).toLong()
                if (written <= 0L) {
                    throw UInputException("Unexpected short/zero write to $devicePath ($written bytes)")
                }
                offset += written.toInt()
            } catch (e: LastErrorException) {
                if (e.errorCode == LinuxInputConstants.EINTR || e.errorCode == LinuxInputConstants.EAGAIN) {
                    continue
                }
                val errDesc = runCatching { libc.strerror(e.errorCode) }.getOrDefault("errno ${e.errorCode}")
                throw UInputException("Failed writing input events to $devicePath: $errDesc (errno=${e.errorCode})", e)
            }
        }
    }

    override fun destroyDevice() {
        val activeFd = fd
        if (activeFd >= 0) {
            runCatching { libc.ioctl(activeFd, NativeLong(LinuxInputConstants.UI_DEV_DESTROY)) }
        }
    }

    override fun close() {
        val activeFd = fd
        fd = -1
        if (activeFd >= 0) {
            runCatching { libc.close(activeFd) }
        }
    }

    private fun requireFd(): Int {
        val activeFd = fd
        if (activeFd < 0) {
            throw UInputException("$devicePath is not open")
        }
        return activeFd
    }

    private fun invokeIoctlInt(request: Long, value: Int, label: String) {
        try {
            libc.ioctl(fd, NativeLong(request), value)
        } catch (e: LastErrorException) {
            throw translateIoctlError(label, e)
        }
    }

    private fun invokeIoctlPtr(request: Long, data: Pointer, label: String) {
        try {
            libc.ioctl(fd, NativeLong(request), data)
        } catch (e: LastErrorException) {
            throw translateIoctlError(label, e)
        }
    }

    private fun translateIoctlError(op: String, e: LastErrorException): UInputException {
        val errno = e.errorCode
        val errDesc = runCatching { libc.strerror(errno) }.getOrDefault("errno $errno")
        return UInputException("uinput ioctl $op failed on $devicePath: $errDesc (errno=$errno)", e)
    }
}
