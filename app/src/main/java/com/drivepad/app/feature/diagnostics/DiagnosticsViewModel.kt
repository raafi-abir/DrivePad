package com.drivepad.app.feature.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.drivepad.app.core.network.ControllerConnectionManager
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.data.repository.ControllerInputRepository
import com.drivepad.app.domain.model.ConnectionDiagnosticsState
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.NormalizedControllerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DiagnosticsUiState(
    val diagnostics: ConnectionDiagnosticsState = ConnectionDiagnosticsState(),
    val controllerState: NormalizedControllerState = NormalizedControllerState(),
    val config: ControllerConfig = ControllerConfig(),
    val isSensorAvailable: Boolean = false
)

class DiagnosticsViewModel(
    private val connectionManager: ControllerConnectionManager,
    private val inputRepository: ControllerInputRepository,
    preferencesRepository: UserPreferencesRepository
) : ViewModel() {

    val uiState: StateFlow<DiagnosticsUiState> = combine(
        connectionManager.diagnosticsState,
        inputRepository.controllerState,
        preferencesRepository.controllerConfigFlow
    ) { diagnostics, controller, config ->
        DiagnosticsUiState(
            diagnostics = diagnostics,
            controllerState = controller,
            config = config,
            isSensorAvailable = inputRepository.isSensorAvailable
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiagnosticsUiState(isSensorAvailable = inputRepository.isSensorAvailable)
    )

    fun onToggleConnection() {
        when (uiState.value.diagnostics.status) {
            ConnectionStatus.CONNECTED, ConnectionStatus.CONNECTING -> connectionManager.disconnect()
            else -> connectionManager.connect()
        }
    }

    fun onSendPing() {
        connectionManager.sendPingNow()
    }

    fun onRefreshRoute() {
        connectionManager.refreshNetworkRoute()
    }
}
