package com.drivepad.companion.backend.uinput

import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.profile.ControllerProfile
import com.drivepad.companion.profile.LogicalButton

data class UInputAxisSpec(
    val code: Int,
    val name: String,
    val minimum: Int,
    val maximum: Int,
    val initialValue: Int = 0,
    val fuzz: Int = 0,
    val flat: Int = 0,
    val resolution: Int = 0
) {
    init {
        require(minimum <= maximum) { "Axis $name minimum ($minimum) cannot exceed maximum ($maximum)" }
        require(initialValue in minimum..maximum) {
            "Axis $name initialValue ($initialValue) outside [$minimum..$maximum]"
        }
    }
}

/**
 * Identity and axis specification for the Linux uinput virtual gamepad device.
 * Uses `BUS_VIRTUAL` and an open-hardware test VID/PID so it never impersonates
 * Sony DualSense or Microsoft Xbox commercial USB hardware.
 */
data class UInputDeviceConfig(
    val devicePath: String = DEFAULT_DEVICE_PATH,
    val deviceName: String = CompanionConfig.DEFAULT_VIRTUAL_DEVICE_NAME,
    val busType: Int = LinuxInputConstants.BUS_VIRTUAL,
    val vendorId: Int = CompanionConfig.DEFAULT_VENDOR_ID,
    val productId: Int = CompanionConfig.DEFAULT_PRODUCT_ID,
    val versionId: Int = CompanionConfig.DEFAULT_VERSION_ID,
    val axes: List<UInputAxisSpec> = GamepadAxisMapping.axisSpecs,
    val buttonCodes: List<Int> = GamepadButtonMapping.allLinuxButtonCodes
) {
    init {
        require(devicePath.isNotBlank()) { "uinput device path cannot be blank" }
        require(deviceName.isNotBlank()) { "Virtual device name cannot be blank" }
        require(deviceName.toByteArray(Charsets.UTF_8).size < LinuxInputConstants.UINPUT_MAX_NAME_SIZE) {
            "Device name exceeds ${LinuxInputConstants.UINPUT_MAX_NAME_SIZE - 1} UTF-8 bytes"
        }
        require(vendorId in 0..0xFFFF) { "Vendor ID out of 16-bit range: $vendorId" }
        require(productId in 0..0xFFFF) { "Product ID out of 16-bit range: $productId" }
        require(versionId in 0..0xFFFF) { "Version ID out of 16-bit range: $versionId" }
    }

    companion object {
        const val DEFAULT_DEVICE_PATH = "/dev/uinput"

        fun fromCompanionConfig(config: CompanionConfig): UInputDeviceConfig = UInputDeviceConfig(
            deviceName = config.virtualDeviceName,
            vendorId = config.vendorId,
            productId = config.productId,
            versionId = config.versionId
        )

        fun fromProfile(config: CompanionConfig, profile: ControllerProfile): UInputDeviceConfig {
            val steeringSpec = UInputAxisSpec(
                code = GamepadAxisMapping.toLinuxAbsCode(profile.steeringAxis),
                name = "${profile.steeringAxis.legacyLinuxName} (Steering)",
                minimum = GamepadAxisMapping.STEERING_MIN,
                maximum = GamepadAxisMapping.STEERING_MAX,
                initialValue = GamepadAxisMapping.STEERING_NEUTRAL
            )
            val throttleSpec = UInputAxisSpec(
                code = GamepadAxisMapping.toLinuxAbsCode(profile.throttleAxis),
                name = "${profile.throttleAxis.legacyLinuxName} (Throttle)",
                minimum = GamepadAxisMapping.PEDAL_MIN,
                maximum = GamepadAxisMapping.PEDAL_MAX,
                initialValue = GamepadAxisMapping.axisNeutralValue(profile.throttleAxis, profile.throttleInvert)
            )
            val brakeSpec = UInputAxisSpec(
                code = GamepadAxisMapping.toLinuxAbsCode(profile.brakeAxis),
                name = "${profile.brakeAxis.legacyLinuxName} (Brake)",
                minimum = GamepadAxisMapping.PEDAL_MIN,
                maximum = GamepadAxisMapping.PEDAL_MAX,
                initialValue = GamepadAxisMapping.axisNeutralValue(profile.brakeAxis, profile.brakeInvert)
            )
            val buttonCodes = LogicalButton.entries.map { logical ->
                GamepadButtonMapping.toLinuxKeyCode(profile.buttonBindings.getValue(logical).button)
            }
            return UInputDeviceConfig(
                deviceName = config.virtualDeviceName,
                vendorId = config.vendorId,
                productId = config.productId,
                versionId = config.versionId,
                axes = listOf(steeringSpec, throttleSpec, brakeSpec),
                buttonCodes = buttonCodes
            )
        }
    }
}
