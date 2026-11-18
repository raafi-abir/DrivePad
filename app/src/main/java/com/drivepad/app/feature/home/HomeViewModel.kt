package com.drivepad.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivepad.app.core.network.ControllerConnectionManager
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.domain.model.ConnectionDiagnosticsState
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.ControllerProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val selectedProfile: ControllerProfile = ControllerProfile.DefaultProfiles.first(),
    val availableProfiles: List<ControllerProfile> = ControllerProfile.DefaultProfiles,
    val config: ControllerConfig = ControllerConfig(),
    val connectionStatus: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val diagnostics: ConnectionDiagnosticsState = ConnectionDiagnosticsState(),
    val isLoading: Boolean = false
)

class HomeViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val connectionManager: ControllerConnectionManager? = null
) : ViewModel() {

    private val fallbackDiagnostics = MutableStateFlow(ConnectionDiagnosticsState())

    val uiState: StateFlow<HomeUiState> = combine(
        preferencesRepository.controllerConfigFlow,
        preferencesRepository.selectedProfileIdFlow,
        connectionManager?.diagnosticsState ?: fallbackDiagnostics
    ) { config, profileId, diag ->
        val selected = ControllerProfile.DefaultProfiles.find { it.id == profileId }
            ?: ControllerProfile.DefaultProfiles.first()
        HomeUiState(
            selectedProfile = selected,
            availableProfiles = ControllerProfile.DefaultProfiles,
            config = config,
            connectionStatus = diag.status,
            diagnostics = diag,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = true)
    )

    fun onSelectProfile(profileId: String) {
        viewModelScope.launch {
            preferencesRepository.updateSelectedProfileId(profileId)
        }
    }

    fun onQuickToggleMotionSteering(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.updateMotionSteeringEnabled(enabled)
        }
    }

    fun onToggleConnection() {
        val manager = connectionManager ?: return
        when (uiState.value.connectionStatus) {
            ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING -> manager.disconnect()
            else -> manager.connect()
        }
    }

    fun onRefreshRoute() {
        connectionManager?.refreshNetworkRoute()
    }
}
