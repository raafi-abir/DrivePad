package com.drivepad.app.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.drivepad.app.core.network.ControllerConnectionManager
import com.drivepad.app.core.network.NetworkConnectivityMonitor
import com.drivepad.app.core.network.UdpControllerConnectionManager
import com.drivepad.app.core.preferences.DashboardPreferencesRepository
import com.drivepad.app.core.preferences.DashboardPreferencesRepositoryImpl
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.core.preferences.UserPreferencesRepositoryImpl
import com.drivepad.app.core.preferences.dataStore
import com.drivepad.app.data.repository.ControllerInputRepository
import com.drivepad.app.data.repository.ControllerInputRepositoryImpl
import com.drivepad.app.feature.dashboard.DashboardCustomizationViewModel
import com.drivepad.app.feature.diagnostics.DiagnosticsViewModel
import com.drivepad.app.feature.home.HomeViewModel
import com.drivepad.app.feature.racing.RacingViewModel
import com.drivepad.app.feature.settings.SettingsViewModel

interface AppContainer {
    val userPreferencesRepository: UserPreferencesRepository
    val dashboardPreferencesRepository: DashboardPreferencesRepository
    val controllerInputRepository: ControllerInputRepository
    val networkConnectivityMonitor: NetworkConnectivityMonitor
    val controllerConnectionManager: ControllerConnectionManager
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepositoryImpl(context.dataStore)
    }

    override val dashboardPreferencesRepository: DashboardPreferencesRepository by lazy {
        DashboardPreferencesRepositoryImpl(context.dataStore)
    }

    override val controllerInputRepository: ControllerInputRepository by lazy {
        ControllerInputRepositoryImpl(context, userPreferencesRepository)
    }

    override val networkConnectivityMonitor: NetworkConnectivityMonitor by lazy {
        NetworkConnectivityMonitor(context)
    }

    override val controllerConnectionManager: ControllerConnectionManager by lazy {
        UdpControllerConnectionManager(
            preferencesRepository = userPreferencesRepository,
            inputRepository = controllerInputRepository,
            routeInspector = networkConnectivityMonitor
        )
    }
}

object ViewModelFactoryProvider {
    fun provideHomeViewModelFactory(appContainer: AppContainer): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                    return HomeViewModel(
                        preferencesRepository = appContainer.userPreferencesRepository,
                        connectionManager = appContainer.controllerConnectionManager
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    fun provideSettingsViewModelFactory(appContainer: AppContainer): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
                    return SettingsViewModel(
                        preferencesRepository = appContainer.userPreferencesRepository,
                        inputRepository = appContainer.controllerInputRepository
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    fun provideRacingViewModelFactory(appContainer: AppContainer): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(RacingViewModel::class.java)) {
                    return RacingViewModel(
                        preferencesRepository = appContainer.userPreferencesRepository,
                        inputRepository = appContainer.controllerInputRepository,
                        connectionManager = appContainer.controllerConnectionManager,
                        dashboardPreferencesRepository = appContainer.dashboardPreferencesRepository
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    fun provideDiagnosticsViewModelFactory(appContainer: AppContainer): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(DiagnosticsViewModel::class.java)) {
                    return DiagnosticsViewModel(
                        connectionManager = appContainer.controllerConnectionManager,
                        inputRepository = appContainer.controllerInputRepository,
                        preferencesRepository = appContainer.userPreferencesRepository
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }

    fun provideDashboardCustomizationViewModelFactory(appContainer: AppContainer): ViewModelProvider.Factory =
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                if (modelClass.isAssignableFrom(DashboardCustomizationViewModel::class.java)) {
                    return DashboardCustomizationViewModel(
                        dashboardPreferencesRepository = appContainer.dashboardPreferencesRepository
                    ) as T
                }
                throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
            }
        }
}
