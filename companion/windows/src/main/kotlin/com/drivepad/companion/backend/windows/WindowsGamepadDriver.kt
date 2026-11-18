package com.drivepad.companion.backend.windows

import com.drivepad.companion.domain.HostPlatform
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import java.io.IOException

open class ViGEmException(message: String, cause: Throwable? = null) : IOException(message, cause)

class ViGEmUnavailableException(message: String, cause: Throwable? = null) : ViGEmException(message, cause)

class ViGEmBusNotFoundException(message: String, cause: Throwable? = null) : ViGEmException(message, cause)

/**
 * Abstraction over Windows virtual Xbox 360 controller driver lifecycle (`ViGEmClient`).
 * Enables deterministic JVM unit testing of delta suppression, failsafe neutral resets,
 * and lifecycle cleanup on Linux/CI build hosts without loading Windows `.dll` binaries.
 */
interface WindowsVirtualGamepadDriver : AutoCloseable {
    fun open(vendorId: Int? = null, productId: Int? = null)
    fun submitReport(report: XusbReportData)
}

/**
 * Official Nefarius `ViGEmClient.dll` Win32 C ABI declarations via JNA [StdCallLibrary].
 */
interface ViGEmClientLibrary : StdCallLibrary {
    fun vigem_alloc(): Pointer?
    fun vigem_free(vigem: Pointer)
    fun vigem_connect(vigem: Pointer): Int
    fun vigem_disconnect(vigem: Pointer)

    fun vigem_target_x360_alloc(): Pointer?
    fun vigem_target_free(target: Pointer)
    fun vigem_target_set_vid(target: Pointer, vid: Short)
    fun vigem_target_set_pid(target: Pointer, pid: Short)
    fun vigem_target_add(vigem: Pointer, target: Pointer): Int
    fun vigem_target_remove(vigem: Pointer, target: Pointer): Int
    fun vigem_target_x360_update(vigem: Pointer, target: Pointer, report: XusbReportStruct.ByValue): Int
}

/**
 * Production native ViGEmBus Xbox 360 driver implementation loading `ViGEmClient.dll` via JNA.
 */
class NativeViGEmGamepadDriver(
    private val availabilityProbe: () -> WindowsAvailability = { WindowsHostProbe.probe() },
    private val libraryLoader: (String) -> ViGEmClientLibrary = { name ->
        Native.load(name, ViGEmClientLibrary::class.java)
    }
) : WindowsVirtualGamepadDriver {

    private var lib: ViGEmClientLibrary? = null
    private var clientHandle: Pointer? = null
    private var targetHandle: Pointer? = null

    override fun open(vendorId: Int?, productId: Int?) {
        if (HostPlatform.current() != HostPlatform.WINDOWS) {
            throw ViGEmUnavailableException(
                "Windows ViGEm backend requires a Windows host (current OS: ${System.getProperty("os.name")}). " +
                    "Use --backend uinput on Linux or --backend noop."
            )
        }

        val probe = availabilityProbe()
        val libraryNameOrPath = probe.clientLibraryPath?.takeIf { probe.clientLibraryFound && it.isNotBlank() }
            ?: throw ViGEmUnavailableException(
                "ViGEmClient.dll not found in explicit trusted install paths (refusing unqualified CWD/PATH DLL search). " +
                    "Place 64-bit ViGEmClient.dll in native\\windows\\ or %APPDATA%\\drivepad-companion\\ " +
                    "and install signed Nefarius ViGEmBus v1.22.0."
            )
        val loadedLib = try {
            libraryLoader(libraryNameOrPath)
        } catch (t: UnsatisfiedLinkError) {
            throw ViGEmUnavailableException(
                "Could not load ViGEmClient.dll (${t.message}). Place x64 ViGEmClient.dll next to the JAR " +
                    "or on PATH and install signed Nefarius ViGEmBus v1.22.0.",
                t
            )
        } catch (t: Throwable) {
            throw ViGEmUnavailableException("Failed loading ViGEmClient library: ${t.message}", t)
        }

        val client = loadedLib.vigem_alloc()
            ?: throw ViGEmException("vigem_alloc() returned null")
        try {
            val connectStatus = loadedLib.vigem_connect(client)
            if (!ViGEmConstants.isSuccess(connectStatus)) {
                val msg = "vigem_connect failed: ${ViGEmConstants.describeError(connectStatus)}"
                if (connectStatus == ViGEmConstants.VIGEM_ERROR_BUS_NOT_FOUND) {
                    throw ViGEmBusNotFoundException(
                        "$msg — ViGEmBus kernel driver is not installed or running. " +
                            "Note: ViGEmBus v1.22.0 is archived/EOL and must be installed manually " +
                            "from official signed releases without disabling HVCI or Secure Boot."
                    )
                }
                throw ViGEmUnavailableException(msg)
            }

            val target = loadedLib.vigem_target_x360_alloc()
                ?: throw ViGEmException("vigem_target_x360_alloc() returned null")
            try {
                if (vendorId != null && productId != null) {
                    loadedLib.vigem_target_set_vid(target, (vendorId and 0xFFFF).toShort())
                    loadedLib.vigem_target_set_pid(target, (productId and 0xFFFF).toShort())
                }
                val addStatus = loadedLib.vigem_target_add(client, target)
                if (!ViGEmConstants.isSuccess(addStatus)) {
                    throw ViGEmException("vigem_target_add(x360) failed: ${ViGEmConstants.describeError(addStatus)}")
                }
                lib = loadedLib
                clientHandle = client
                targetHandle = target
            } catch (t: Throwable) {
                runCatching { loadedLib.vigem_target_free(target) }
                throw t
            }
        } catch (t: Throwable) {
            runCatching { loadedLib.vigem_disconnect(client) }
            runCatching { loadedLib.vigem_free(client) }
            throw t
        }
    }

    override fun submitReport(report: XusbReportData) {
        val loaded = lib ?: throw ViGEmException("ViGEm driver is not open")
        val client = clientHandle ?: throw ViGEmException("ViGEm client handle is null")
        val target = targetHandle ?: throw ViGEmException("ViGEm target handle is null")

        val status = loaded.vigem_target_x360_update(client, target, report.toNativeStruct())
        if (!ViGEmConstants.isSuccess(status)) {
            throw ViGEmException("vigem_target_x360_update failed: ${ViGEmConstants.describeError(status)}")
        }
    }

    override fun close() {
        val loaded = lib
        val client = clientHandle
        val target = targetHandle
        lib = null
        clientHandle = null
        targetHandle = null

        if (loaded != null && client != null) {
            if (target != null) {
                runCatching {
                    loaded.vigem_target_x360_update(client, target, XusbReportData.NEUTRAL.toNativeStruct())
                }
                runCatching { loaded.vigem_target_remove(client, target) }
                runCatching { loaded.vigem_target_free(target) }
            }
            runCatching { loaded.vigem_disconnect(client) }
            runCatching { loaded.vigem_free(client) }
        }
    }
}

/**
 * Manages virtual Xbox 360 controller connection state and delta suppression
 * so duplicate reports are never submitted unnecessarily.
 */
class ViGEmX360Device(
    private val driver: WindowsVirtualGamepadDriver,
    private val vendorId: Int? = null,
    private val productId: Int? = null,
    private val initialReport: XusbReportData = XusbReportData.NEUTRAL
) : AutoCloseable {

    private var created: Boolean = false
    private var lastReport: XusbReportData = initialReport

    fun isCreated(): Boolean = created

    fun create() {
        if (created) return
        try {
            driver.open(vendorId, productId)
            driver.submitReport(initialReport)
            lastReport = initialReport
            created = true
        } catch (t: Throwable) {
            runCatching { driver.close() }
            created = false
            throw t
        }
    }

    fun applyReport(report: XusbReportData): Boolean {
        check(created) { "ViGEmX360Device is not created" }
        if (report == lastReport) return false
        driver.submitReport(report)
        lastReport = report
        return true
    }

    fun resetToNeutral(force: Boolean = false): Boolean {
        if (!created) return false
        if (!force && lastReport == initialReport) return false
        driver.submitReport(initialReport)
        lastReport = initialReport
        return true
    }

    override fun close() {
        val wasCreated = created
        try {
            if (wasCreated) {
                runCatching { resetToNeutral(force = true) }
            }
        } finally {
            created = false
            runCatching { driver.close() }
        }
    }
}
