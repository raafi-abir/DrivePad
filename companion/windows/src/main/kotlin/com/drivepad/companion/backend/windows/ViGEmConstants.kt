package com.drivepad.companion.backend.windows

import com.sun.jna.Structure
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Verified Win32 `ViGEmClient` C ABI constants, error codes, button bitmasks, and
 * 12-byte `XUSB_REPORT` structure matching Nefarius ViGEmClient v1.22.0 (`ViGEm/Common.h`).
 *
 * Notice on driver lifecycle: Nefarius ViGEmBus reached official EOL/archive at v1.22.0 (BSD 3-Clause,
 * Microsoft-signed driver for Windows 10/11). DrivePad targets ViGEmClient as an optional external
 * Windows kernel driver and cleanly reports unavailability when not installed.
 */
object ViGEmConstants {
    // VIGEM_ERROR values (unsigned 32-bit values represented as signed Kotlin Int)
    const val VIGEM_ERROR_NONE: Int = 0x20000000
    const val VIGEM_ERROR_BUS_NOT_FOUND: Int = 0xE0000001.toInt()
    const val VIGEM_ERROR_NO_FREE_SLOT: Int = 0xE0000002.toInt()
    const val VIGEM_ERROR_INVALID_TARGET: Int = 0xE0000003.toInt()
    const val VIGEM_ERROR_REMOVAL_FAILED: Int = 0xE0000004.toInt()
    const val VIGEM_ERROR_ALREADY_CONNECTED: Int = 0xE0000005.toInt()
    const val VIGEM_ERROR_TARGET_UNINITIALIZED: Int = 0xE0000006.toInt()
    const val VIGEM_ERROR_TARGET_NOT_PLUGGED_IN: Int = 0xE0000007.toInt()
    const val VIGEM_ERROR_BUS_VERSION_MISMATCH: Int = 0xE0000008.toInt()
    const val VIGEM_ERROR_BUS_ACCESS_FAILED: Int = 0xE0000009.toInt()
    const val VIGEM_ERROR_CALLBACK_ALREADY_REGISTERED: Int = 0xE0000010.toInt()
    const val VIGEM_ERROR_INVALID_PARAMETER: Int = 0xE000000D.toInt()

    // Official Microsoft XUSB_GAMEPAD_* bitmask flags (<ViGEm/Common.h>)
    const val XUSB_GAMEPAD_DPAD_UP: Int = 0x0001
    const val XUSB_GAMEPAD_DPAD_DOWN: Int = 0x0002
    const val XUSB_GAMEPAD_DPAD_LEFT: Int = 0x0004
    const val XUSB_GAMEPAD_DPAD_RIGHT: Int = 0x0008
    const val XUSB_GAMEPAD_START: Int = 0x0010
    const val XUSB_GAMEPAD_BACK: Int = 0x0020
    const val XUSB_GAMEPAD_LEFT_THUMB: Int = 0x0040
    const val XUSB_GAMEPAD_RIGHT_THUMB: Int = 0x0080
    const val XUSB_GAMEPAD_LEFT_SHOULDER: Int = 0x0100
    const val XUSB_GAMEPAD_RIGHT_SHOULDER: Int = 0x0200
    const val XUSB_GAMEPAD_GUIDE: Int = 0x0400
    const val XUSB_GAMEPAD_A: Int = 0x1000
    const val XUSB_GAMEPAD_B: Int = 0x2000
    const val XUSB_GAMEPAD_X: Int = 0x4000
    const val XUSB_GAMEPAD_Y: Int = 0x8000

    const val XUSB_REPORT_SIZE_BYTES: Int = 12
    const val THUMB_MIN: Int = -32768
    const val THUMB_MAX: Int = 32767
    const val TRIGGER_MIN: Int = 0
    const val TRIGGER_MAX: Int = 255

    fun isSuccess(errorCode: Int): Boolean = errorCode == VIGEM_ERROR_NONE

    fun describeError(errorCode: Int): String = when (errorCode) {
        VIGEM_ERROR_NONE -> "VIGEM_ERROR_NONE (Success)"
        VIGEM_ERROR_BUS_NOT_FOUND ->
            "VIGEM_ERROR_BUS_NOT_FOUND (ViGEmBus v1.22+ kernel driver is not installed or stopped)"
        VIGEM_ERROR_NO_FREE_SLOT ->
            "VIGEM_ERROR_NO_FREE_SLOT (All 4 XInput virtual slots are occupied)"
        VIGEM_ERROR_INVALID_TARGET -> "VIGEM_ERROR_INVALID_TARGET"
        VIGEM_ERROR_REMOVAL_FAILED -> "VIGEM_ERROR_REMOVAL_FAILED"
        VIGEM_ERROR_ALREADY_CONNECTED -> "VIGEM_ERROR_ALREADY_CONNECTED"
        VIGEM_ERROR_TARGET_UNINITIALIZED -> "VIGEM_ERROR_TARGET_UNINITIALIZED"
        VIGEM_ERROR_TARGET_NOT_PLUGGED_IN -> "VIGEM_ERROR_TARGET_NOT_PLUGGED_IN"
        VIGEM_ERROR_BUS_VERSION_MISMATCH ->
            "VIGEM_ERROR_BUS_VERSION_MISMATCH (ViGEmClient.dll version does not match installed ViGEmBus driver)"
        VIGEM_ERROR_BUS_ACCESS_FAILED -> "VIGEM_ERROR_BUS_ACCESS_FAILED"
        VIGEM_ERROR_INVALID_PARAMETER -> "VIGEM_ERROR_INVALID_PARAMETER"
        else -> "Unknown ViGEm error (0x${errorCode.toUInt().toString(16)})"
    }

    /**
     * Serializes a 12-byte Win32 `XUSB_REPORT`:
     * - 0..1  : USHORT wButtons
     * - 2     : BYTE bLeftTrigger (0..255)
     * - 3     : BYTE bRightTrigger (0..255)
     * - 4..5  : SHORT sThumbLX (-32768..32767)
     * - 6..7  : SHORT sThumbLY (-32768..32767)
     * - 8..9  : SHORT sThumbRX (-32768..32767)
     * - 10..11: SHORT sThumbRY (-32768..32767)
     */
    fun encodeXusbReport(report: XusbReportData, byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN): ByteArray {
        val out = ByteArray(XUSB_REPORT_SIZE_BYTES)
        val bb = ByteBuffer.wrap(out).order(byteOrder)
        bb.putShort(0, (report.buttonsMask and 0xFFFF).toShort())
        bb.put(2, (report.leftTrigger and 0xFF).toByte())
        bb.put(3, (report.rightTrigger and 0xFF).toByte())
        bb.putShort(4, report.thumbLX.toShort())
        bb.putShort(6, report.thumbLY.toShort())
        bb.putShort(8, report.thumbRX.toShort())
        bb.putShort(10, report.thumbRY.toShort())
        return out
    }
}

/**
 * Immutable JVM value representation of an Xbox 360 `XUSB_REPORT`.
 */
data class XusbReportData(
    val buttonsMask: Int = 0,
    val leftTrigger: Int = 0,
    val rightTrigger: Int = 0,
    val thumbLX: Int = 0,
    val thumbLY: Int = 0,
    val thumbRX: Int = 0,
    val thumbRY: Int = 0
) {
    init {
        require(buttonsMask in 0..0xFFFF) { "buttonsMask out of uint16 range: $buttonsMask" }
        require(leftTrigger in 0..255) { "leftTrigger out of uint8 range: $leftTrigger" }
        require(rightTrigger in 0..255) { "rightTrigger out of uint8 range: $rightTrigger" }
        require(thumbLX in -32768..32767) { "thumbLX out of int16 range: $thumbLX" }
        require(thumbLY in -32768..32767) { "thumbLY out of int16 range: $thumbLY" }
        require(thumbRX in -32768..32767) { "thumbRX out of int16 range: $thumbRX" }
        require(thumbRY in -32768..32767) { "thumbRY out of int16 range: $thumbRY" }
    }

    fun toNativeStruct(): XusbReportStruct.ByValue = XusbReportStruct.ByValue().apply {
        wButtons = (buttonsMask and 0xFFFF).toShort()
        bLeftTrigger = (leftTrigger and 0xFF).toByte()
        bRightTrigger = (rightTrigger and 0xFF).toByte()
        sThumbLX = thumbLX.toShort()
        sThumbLY = thumbLY.toShort()
        sThumbRX = thumbRX.toShort()
        sThumbRY = thumbRY.toShort()
    }

    companion object {
        val NEUTRAL = XusbReportData()
    }
}

/**
 * JNA native structure matching C `XUSB_REPORT` passed by value to `vigem_target_x360_update`.
 */
@Structure.FieldOrder("wButtons", "bLeftTrigger", "bRightTrigger", "sThumbLX", "sThumbLY", "sThumbRX", "sThumbRY")
open class XusbReportStruct : Structure() {
    @JvmField var wButtons: Short = 0
    @JvmField var bLeftTrigger: Byte = 0
    @JvmField var bRightTrigger: Byte = 0
    @JvmField var sThumbLX: Short = 0
    @JvmField var sThumbLY: Short = 0
    @JvmField var sThumbRX: Short = 0
    @JvmField var sThumbRY: Short = 0

    class ByValue : XusbReportStruct(), Structure.ByValue
}
