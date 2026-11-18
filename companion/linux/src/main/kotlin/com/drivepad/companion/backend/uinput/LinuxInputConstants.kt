
package com.drivepad.companion.backend.uinput

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Verified Linux kernel input and uinput ABI constants and binary serializers for 64-bit Linux (LP64,
 * including x86_64 and aarch64 Arch Linux targets).
 *
 * Derived directly from `<linux/input.h>`, `<linux/input-event-codes.h>`, `<linux/uinput.h>`,
 * `<asm-generic/ioctl.h>`, and `<asm-generic/fcntl.h>`.
 */
object LinuxInputConstants {
    // POSIX open(2) & common errno values
    const val O_WRONLY: Int = 0x0001
    const val EINTR: Int = 4
    const val EPERM: Int = 1
    const val ENOENT: Int = 2
    const val EAGAIN: Int = 11
    const val EACCES: Int = 13
    const val ENODEV: Int = 19
    const val EINVAL: Int = 22

    // Event types
    const val EV_SYN: Int = 0x00
    const val EV_KEY: Int = 0x01
    const val EV_ABS: Int = 0x03

    // Synchronization codes
    const val SYN_REPORT: Int = 0x00

    // Bus type
    const val BUS_VIRTUAL: Int = 0x06

    // Absolute axes
    const val ABS_X: Int = 0x00
    const val ABS_Y: Int = 0x01
    const val ABS_Z: Int = 0x02
    const val ABS_RX: Int = 0x03
    const val ABS_RY: Int = 0x04
    const val ABS_RZ: Int = 0x05

    // Standard Linux gamepad button codes
    const val BTN_SOUTH: Int = 0x130
    const val BTN_EAST: Int = 0x131
    const val BTN_NORTH: Int = 0x133
    const val BTN_WEST: Int = 0x134
    const val BTN_TL: Int = 0x136
    const val BTN_TR: Int = 0x137
    const val BTN_SELECT: Int = 0x13a
    const val BTN_START: Int = 0x13b
    const val BTN_MODE: Int = 0x13c
    const val BTN_THUMBL: Int = 0x13d
    const val BTN_THUMBR: Int = 0x13e
    const val BTN_DPAD_UP: Int = 0x220
    const val BTN_DPAD_DOWN: Int = 0x221
    const val BTN_DPAD_LEFT: Int = 0x222
    const val BTN_DPAD_RIGHT: Int = 0x223

    // uinput ioctl numbers
    const val UINPUT_MAX_NAME_SIZE: Int = 80
    const val UI_DEV_CREATE: Long = 0x00005501L
    const val UI_DEV_DESTROY: Long = 0x00005502L
    const val UI_DEV_SETUP: Long = 0x405c5503L
    const val UI_ABS_SETUP: Long = 0x401c5504L
    const val UI_SET_EVBIT: Long = 0x40045564L
    const val UI_SET_KEYBIT: Long = 0x40045565L
    const val UI_SET_ABSBIT: Long = 0x40045567L

    // Verified LP64 ABI struct sizes
    const val INPUT_EVENT_SIZE_BYTES: Int = 24
    const val UINPUT_SETUP_SIZE_BYTES: Int = 92
    const val UINPUT_ABS_SETUP_SIZE_BYTES: Int = 28

    /**
     * Serializes a Linux 64-bit struct input_event (24 bytes).
     *
     * IMPORTANT:
     * Absolute ByteBuffer writes must use the supplied offset.
     * Each event occupies its own 24-byte region in the batch buffer.
     */
    fun encodeInputEventInto(
        type: Int,
        code: Int,
        value: Int,
        out: ByteArray,
        offset: Int = 0,
        byteOrder: ByteOrder = ByteOrder.nativeOrder()
    ) {
        require(offset >= 0 && offset + INPUT_EVENT_SIZE_BYTES <= out.size) {
            "Buffer too small for struct input_event at offset $offset"
        }

        val bb = ByteBuffer.wrap(out).order(byteOrder)

        bb.putLong(offset, 0L)
        bb.putLong(offset + 8, 0L)
        bb.putShort(offset + 16, (type and 0xFFFF).toShort())
        bb.putShort(offset + 18, (code and 0xFFFF).toShort())
        bb.putInt(offset + 20, value)
    }

    /**
     * Serializes struct uinput_setup (92 bytes).
     */
    fun encodeUInputSetup(
        busType: Int,
        vendorId: Int,
        productId: Int,
        versionId: Int,
        name: String,
        ffEffectsMax: Int = 0,
        byteOrder: ByteOrder = ByteOrder.nativeOrder()
    ): ByteArray {
        val out = ByteArray(UINPUT_SETUP_SIZE_BYTES)
        val bb = ByteBuffer.wrap(out).order(byteOrder)

        bb.putShort(0, (busType and 0xFFFF).toShort())
        bb.putShort(2, (vendorId and 0xFFFF).toShort())
        bb.putShort(4, (productId and 0xFFFF).toShort())
        bb.putShort(6, (versionId and 0xFFFF).toShort())

        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val copyLen = nameBytes.size.coerceAtMost(UINPUT_MAX_NAME_SIZE - 1)
        System.arraycopy(nameBytes, 0, out, 8, copyLen)
        out[8 + copyLen] = 0

        bb.putInt(88, ffEffectsMax)
        return out
    }

    /**
     * Serializes struct uinput_abs_setup (28 bytes).
     */
    fun encodeUInputAbsSetup(
        axisCode: Int,
        initialValue: Int,
        minimum: Int,
        maximum: Int,
        fuzz: Int = 0,
        flat: Int = 0,
        resolution: Int = 0,
        byteOrder: ByteOrder = ByteOrder.nativeOrder()
    ): ByteArray {
        val out = ByteArray(UINPUT_ABS_SETUP_SIZE_BYTES)
        val bb = ByteBuffer.wrap(out).order(byteOrder)

        bb.putShort(0, (axisCode and 0xFFFF).toShort())
        bb.putShort(2, 0)
        bb.putInt(4, initialValue)
        bb.putInt(8, minimum)
        bb.putInt(12, maximum)
        bb.putInt(16, fuzz)
        bb.putInt(20, flat)
        bb.putInt(24, resolution)

        return out
    }
}
