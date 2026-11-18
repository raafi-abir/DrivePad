package com.drivepad.app.core.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.SteeringResponseCurve
import com.drivepad.app.domain.model.TransmissionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "drivepad_user_preferences")

interface UserPreferencesRepository {
    val controllerConfigFlow: Flow<ControllerConfig>
    val selectedProfileIdFlow: Flow<String>

    suspend fun updateSteeringSensitivity(sensitivity: Float)
    suspend fun updateSteeringDeadzone(deadzone: Float)
    suspend fun updateMaxSteeringAngle(angle: Float)
    suspend fun updateSteeringResponseCurve(curve: SteeringResponseCurve)
    suspend fun updateInvertSteering(invert: Boolean)
    suspend fun updateMotionSteeringEnabled(enabled: Boolean)
    suspend fun updateMotionSmoothing(factor: Float)
    suspend fun updateHapticFeedback(enabled: Boolean)
    suspend fun updateReturnToCenter(enabled: Boolean)
    suspend fun updateDefaultTransmission(mode: TransmissionMode)
    suspend fun updateNetworkSettings(port: Int, hostIp: String)
    suspend fun updateTargetUpdateFrequency(frequencyHz: Int)
    suspend fun updateSelectedProfileId(profileId: String)
    suspend fun resetToDefaults()
}

class UserPreferencesRepositoryImpl(
    private val dataStore: DataStore<Preferences>
) : UserPreferencesRepository {

    private object PreferencesKeys {
        val STEERING_SENSITIVITY = floatPreferencesKey("steering_sensitivity")
        val STEERING_DEADZONE = floatPreferencesKey("steering_deadzone")
        val MAX_STEERING_ANGLE = floatPreferencesKey("max_steering_angle")
        val STEERING_RESPONSE_CURVE = stringPreferencesKey("steering_response_curve")
        val INVERT_STEERING = booleanPreferencesKey("invert_steering")
        val MOTION_STEERING_ENABLED = booleanPreferencesKey("motion_steering_enabled")
        val MOTION_SMOOTHING = floatPreferencesKey("motion_smoothing")
        val HAPTIC_FEEDBACK_ENABLED = booleanPreferencesKey("haptic_feedback_enabled")
        val RETURN_TO_CENTER_ENABLED = booleanPreferencesKey("return_to_center_enabled")
        val DEFAULT_TRANSMISSION_MODE = stringPreferencesKey("default_transmission_mode")
        val UDP_PORT = intPreferencesKey("udp_port")
        val TARGET_HOST_IP = stringPreferencesKey("target_host_ip")
        val TARGET_UPDATE_FREQUENCY_HZ = intPreferencesKey("target_update_frequency_hz")
        val SELECTED_PROFILE_ID = stringPreferencesKey("selected_profile_id")
    }

    override val controllerConfigFlow: Flow<ControllerConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            val sensitivity = preferences[PreferencesKeys.STEERING_SENSITIVITY] ?: 1.0f
            val deadzone = preferences[PreferencesKeys.STEERING_DEADZONE] ?: 0.04f
            val maxAngle = preferences[PreferencesKeys.MAX_STEERING_ANGLE] ?: 35.0f
            val curveStr = preferences[PreferencesKeys.STEERING_RESPONSE_CURVE] ?: SteeringResponseCurve.LINEAR.name
            val curve = try {
                SteeringResponseCurve.valueOf(curveStr)
            } catch (e: Exception) {
                SteeringResponseCurve.LINEAR
            }
            val invert = preferences[PreferencesKeys.INVERT_STEERING] ?: false
            val motionEnabled = preferences[PreferencesKeys.MOTION_STEERING_ENABLED] ?: false
            val motionSmoothing = preferences[PreferencesKeys.MOTION_SMOOTHING] ?: 0.35f
            val haptic = preferences[PreferencesKeys.HAPTIC_FEEDBACK_ENABLED] ?: true
            val returnCenter = preferences[PreferencesKeys.RETURN_TO_CENTER_ENABLED] ?: true
            val transModeStr = preferences[PreferencesKeys.DEFAULT_TRANSMISSION_MODE] ?: TransmissionMode.AUTO.name
            val transMode = try {
                TransmissionMode.valueOf(transModeStr)
            } catch (e: Exception) {
                TransmissionMode.AUTO
            }
            val port = (preferences[PreferencesKeys.UDP_PORT] ?: 42100).coerceIn(1, 65535)
            val hostIp = preferences[PreferencesKeys.TARGET_HOST_IP] ?: "192.168.42.1"
            val updateHz = (preferences[PreferencesKeys.TARGET_UPDATE_FREQUENCY_HZ] ?: 250).coerceIn(20, 500)

            ControllerConfig(
                steeringSensitivity = sensitivity,
                steeringDeadzone = deadzone,
                maxSteeringAngleDegrees = maxAngle,
                steeringResponseCurve = curve,
                invertSteering = invert,
                motionSteeringEnabled = motionEnabled,
                motionSmoothingFactor = motionSmoothing,
                hapticFeedbackEnabled = haptic,
                returnToCenterEnabled = returnCenter,
                defaultTransmissionMode = transMode,
                udpPort = port,
                targetHostIp = hostIp,
                targetUpdateFrequencyHz = updateHz
            )
        }

    override val selectedProfileIdFlow: Flow<String> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.SELECTED_PROFILE_ID] ?: "ets2_truck"
        }

    override suspend fun updateSteeringSensitivity(sensitivity: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STEERING_SENSITIVITY] = sensitivity.coerceIn(0.2f, 3.0f)
        }
    }

    override suspend fun updateSteeringDeadzone(deadzone: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STEERING_DEADZONE] = deadzone.coerceIn(0.0f, 0.25f)
        }
    }

    override suspend fun updateMaxSteeringAngle(angle: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MAX_STEERING_ANGLE] = angle.coerceIn(15.0f, 90.0f)
        }
    }

    override suspend fun updateSteeringResponseCurve(curve: SteeringResponseCurve) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STEERING_RESPONSE_CURVE] = curve.name
        }
    }

    override suspend fun updateInvertSteering(invert: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.INVERT_STEERING] = invert
        }
    }

    override suspend fun updateMotionSteeringEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MOTION_STEERING_ENABLED] = enabled
        }
    }

    override suspend fun updateMotionSmoothing(factor: Float) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.MOTION_SMOOTHING] = factor.coerceIn(0.05f, 0.95f)
        }
    }

    override suspend fun updateHapticFeedback(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.HAPTIC_FEEDBACK_ENABLED] = enabled
        }
    }

    override suspend fun updateReturnToCenter(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.RETURN_TO_CENTER_ENABLED] = enabled
        }
    }

    override suspend fun updateDefaultTransmission(mode: TransmissionMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DEFAULT_TRANSMISSION_MODE] = mode.name
        }
    }

    override suspend fun updateNetworkSettings(port: Int, hostIp: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.UDP_PORT] = port.coerceIn(1, 65535)
            preferences[PreferencesKeys.TARGET_HOST_IP] = hostIp.trim()
        }
    }

    override suspend fun updateTargetUpdateFrequency(frequencyHz: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TARGET_UPDATE_FREQUENCY_HZ] = frequencyHz.coerceIn(20, 500)
        }
    }

    override suspend fun updateSelectedProfileId(profileId: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SELECTED_PROFILE_ID] = profileId
        }
    }

    override suspend fun resetToDefaults() {
        dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
