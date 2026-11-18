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

    // Event types (<linux/input-event-codes.h>)
    const val EV_SYN: Int = 0x00
    const val EV_KEY: Int = 0x01
    const val EV_ABS: Int = 0x03

    // Synchronization codes
    const val SYN_REPORT: Int = 0x00

    // Bus type (<linux/input.h>)
    const val BUS_VIRTUAL: Int = 0x06

    // Absolute axes (<linux/input-event-codes.h>)
    const val ABS_X: Int = 0x00
    const val ABS_Y: Int = 0x01
    const val ABS_Z: Int = 0x02
    const val ABS_RX: Int = 0x03
    const val ABS_RY: Int = 0x04
    const val ABS_RZ: Int = 0x05

    // Standard Linux gamepad button codes (<linux/input-event-codes.h>)
    const val BTN_SOUTH: Int = 0x130       // Gamepad A / Cross
    const val BTN_EAST: Int = 0x131        // Gamepad B / Circle
    const val BTN_NORTH: Int = 0x133       // Gamepad X / Square
    const val BTN_WEST: Int = 0x134        // Gamepad Y / Triangle
    const val BTN_TL: Int = 0x136          // Left Bumper (L1)
    const val BTN_TR: Int = 0x137          // Right Bumper (R1)
    const val BTN_SELECT: Int = 0x13a      // Back / View
    const val BTN_START: Int = 0x13b       // Start / Menu
    const val BTN_MODE: Int = 0x13c        // System / Guide
    const val BTN_THUMBL: Int = 0x13d      // Left Stick Click (L3)
    const val BTN_THUMBR: Int = 0x13e      // Right Stick Click (R3)
    const val BTN_DPAD_UP: Int = 0x220     // D-Pad Up
    const val BTN_DPAD_DOWN: Int = 0x221   // D-Pad Down
    const val BTN_DPAD_LEFT: Int = 0x222   // D-Pad Left
    const val BTN_DPAD_RIGHT: Int = 0x223  // D-Pad Right

    // uinput ioctl numbers (<linux/uinput.h>, UINPUT_IOCTL_BASE = 'U' = 0x55)
    const val UINPUT_MAX_NAME_SIZE: Int = 80
    const val UI_DEV_CREATE: Long = 0x00005501L   // _IO('U', 1)
    const val UI_DEV_DESTROY: Long = 0x00005502L  // _IO('U', 2)
    const val UI_DEV_SETUP: Long = 0x405c5503L    // _IOW('U', 3, struct uinput_setup [92B])
    const val UI_ABS_SETUP: Long = 0x401c5504L    // _IOW('U', 4, struct uinput_abs_setup [28B])
    const val UI_SET_EVBIT: Long = 0x40045564L    // _IOW('U', 100, int [4B])
    const val UI_SET_KEYBIT: Long = 0x40045565L   // _IOW('U', 101, int [4B])
    const val UI_SET_ABSBIT: Long = 0x40045567L   // _IOW('U', 103, int [4B])

    // Verified LP64 ABI struct sizes (bytes)
    const val INPUT_EVENT_SIZE_BYTES: Int = 24
    const val UINPUT_SETUP_SIZE_BYTES: Int = 92
    const val UINPUT_ABS_SETUP_SIZE_BYTES: Int = 28

    /**
     * Serializes a Linux 64-bit `struct input_event` (24 bytes):
     * - 0..7   : `__kernel_long_t tv_sec` (0 on write; kernel timestamps injected events)
     * - 8..15  : `__kernel_long_t tv_usec` (0)
     * - 16..17 : `__u16 type`
     * - 18..19 : `__u16 code`
     * - 20..23 : `__s32 value`
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
        val bb = ByteBuffer.wrap(out, offset, INPUT_EVENT_SIZE_BYTES).order(byteOrder)
        bb.putLong(0, 0L)
        bb.putLong(8, 0L)
        bb.putShort(16, (type and 0xFFFF).toShort())
        bb.putShort(18, (code and 0xFFFF).toShort())
        bb.putInt(20, value)
    }

    /**
     * Serializes `struct uinput_setup` (92 bytes):
     * - 0..7   : `struct input_id { bustype(u16), vendor(u16), product(u16), version(u16) }`
    
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
 * - 8..87  : `char name[80]` (NUL-terminated UTF-8, zero-padded)
     * - 88..91 : `__u32 ff_effects_max` (0)
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
     * Serializes `struct uinput_abs_setup` (28 bytes):
     * - 0..1   : `__u16 code`
     * - 2..3   : natural alignment padding (0)
     * - 4..27  : `struct input_absinfo { value(s32), minimum(s32), maximum(s32), fuzz(s32), flat(s32), resolution(s32) }`
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
