package com.drivepad.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivepad.app.core.network.NetworkAddressValidator
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.data.repository.ControllerInputRepository
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.SteeringResponseCurve
import com.drivepad.app.domain.model.TransmissionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val config: ControllerConfig = ControllerConfig(),
    val isSavedBannerVisible: Boolean = false,
    val calibrationMessage: String? = null
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val inputRepository: ControllerInputRepository? = null
) : ViewModel() {

    private val _calibrationMessage = MutableStateFlow<String?>(null)
    val calibrationMessage: StateFlow<String?> = _calibrationMessage.asStateFlow()

    private val _networkFeedback = MutableStateFlow<String?>(null)
    val networkFeedback: StateFlow<String?> = _networkFeedback.asStateFlow()

    private val _networkError = MutableStateFlow<String?>(null)
    val networkError: StateFlow<String?> = _networkError.asStateFlow()

    val configState: StateFlow<ControllerConfig> = preferencesRepository.controllerConfigFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ControllerConfig()
    )

    fun onSensitivityChanged(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updateSteeringSensitivity(value)
        }
    }

    fun onDeadzoneChanged(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updateSteeringDeadzone(value)
        }
    }

    fun onMaxAngleChanged(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updateMaxSteeringAngle(value)
        }
    }

    fun onResponseCurveChanged(curve: SteeringResponseCurve) {
        viewModelScope.launch {
            preferencesRepository.updateSteeringResponseCurve(curve)
        }
    }

    fun onInvertSteeringToggled(inverted: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateInvertSteering(inverted)
        }
    }

    fun onMotionSteeringToggled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateMotionSteeringEnabled(enabled)
            inputRepository?.toggleMotionSteering(enabled)
        }
    }

    fun onMotionSmoothingChanged(value: Float) {
        viewModelScope.launch {
            preferencesRepository.updateMotionSmoothing(value)
        }
    }

    fun onHapticToggled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateHapticFeedback(enabled)
        }
    }

    fun onReturnToCenterToggled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateReturnToCenter(enabled)
        }
    }

    fun onTransmissionChanged(mode: TransmissionMode) {
        viewModelScope.launch {
            preferencesRepository.updateDefaultTransmission(mode)
        }
    }

    fun onNetworkConfigChanged(port: Int, hostIp: String) {
        saveNetworkSettings(
            hostIpRaw = hostIp,
            portRaw = port.toString(),
            frequencyHz = configState.value.targetUpdateFrequencyHz
        )
    }

    fun onTargetFrequencyChanged(frequencyHz: Int) {
        val coerced = frequencyHz.coerceIn(20, 500)
        viewModelScope.launch {
            preferencesRepository.updateTargetUpdateFrequency(coerced)
            _networkError.value = null
            _networkFeedback.value = "Target rate updated to $coerced Hz"
        }
    }

    fun saveNetworkSettings(
        hostIpRaw: String,
        portRaw: String,
        frequencyHz: Int
    ): Boolean {
        val trimmedIp = hostIpRaw.trim()
        if (!NetworkAddressValidator.isValidTargetIpv4(trimmedIp)) {
            _networkFeedback.value = null
            _networkError.value = "Enter a valid target IPv4 address (e.g., 192.168.42.1)"
            return false
        }

        val parsedPort = portRaw.trim().toIntOrNull()
        if (parsedPort == null || !NetworkAddressValidator.isValidPort(parsedPort)) {
            _networkFeedback.value = null
            _networkError.value = "UDP port must be an integer between 1 and 65535"
            return false
        }

        val safeHz = frequencyHz.coerceIn(20, 500)
        _networkError.value = null
        _networkFeedback.value = "Saved UDP endpoint $trimmedIp:$parsedPort @ $safeHz Hz"
        viewModelScope.launch {
            preferencesRepository.updateNetworkSettings(parsedPort, trimmedIp)
            preferencesRepository.updateTargetUpdateFrequency(safeHz)
        }
        return true
    }

    fun calibrateNeutral() {
        val success = inputRepository?.calibrateSteering() ?: true
        _calibrationMessage.value = if (success) "Calibrated neutral position" else "Calibration failed: keep level"
    }

    fun resetCalibration() {
        inputRepository?.resetSteeringCalibration()
        _calibrationMessage.value = "Calibration reset to 0°"
    }

    fun onResetDefaults() {
        viewModelScope.launch {
            preferencesRepository.resetToDefaults()
            inputRepository?.resetSteeringCalibration()
            _networkError.value = null
            _networkFeedback.value = null
            _calibrationMessage.value = "Settings reset to defaults"
        }
    }
}
