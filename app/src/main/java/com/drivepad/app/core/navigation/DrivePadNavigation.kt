package com.drivepad.app.core.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.drivepad.app.di.AppContainer
import com.drivepad.app.di.ViewModelFactoryProvider
import com.drivepad.app.feature.dashboard.DashboardCustomizationScreen
import com.drivepad.app.feature.dashboard.DashboardCustomizationViewModel
import com.drivepad.app.feature.diagnostics.DiagnosticsScreen
import com.drivepad.app.feature.diagnostics.DiagnosticsViewModel
import com.drivepad.app.feature.home.HomeScreen
import com.drivepad.app.feature.home.HomeViewModel
import com.drivepad.app.feature.profiles.ProfilesScreen
import com.drivepad.app.feature.racing.RacingControllerScreen
import com.drivepad.app.feature.racing.RacingViewModel
import com.drivepad.app.feature.settings.SettingsScreen
import com.drivepad.app.feature.settings.SettingsViewModel
import kotlinx.coroutines.delay

sealed class DrivePadDestination(val route: String) {
    data object Home : DrivePadDestination("home")
    data object Settings : DrivePadDestination("settings")
    data object Profiles : DrivePadDestination("profiles")
    data object Diagnostics : DrivePadDestination("diagnostics")
    data object Racing : DrivePadDestination("racing")
    data object DashboardCustomization : DrivePadDestination("dashboard_customization")
}

@Composable
fun DrivePadNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var systemBarsHidden by remember { mutableStateOf(false) }

    // Wait for WindowManager orientation rotation sync to finish before toggling system bars,
    // preventing competing SurfaceSyncGroup(wmsSync-VRI) and SurfaceSyncGroup(VRI) deadlocks.
    LaunchedEffect(currentRoute, configuration.orientation) {
        val window = context.findActivity()?.window ?: return@LaunchedEffect
        if (currentRoute == DrivePadDestination.Racing.route) {
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && !systemBarsHidden) {
                withFrameNanos { }
                delay(400L)
                WindowCompat.getInsetsController(window, view).apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
                systemBarsHidden = true
            }
        } else if (systemBarsHidden) {
            withFrameNanos { }
            delay(400L)
            WindowCompat.getInsetsController(window, view)
                .show(WindowInsetsCompat.Type.systemBars())
            systemBarsHidden = false
        }
    }

    NavHost(
        navController = navController,
        startDestination = DrivePadDestination.Home.route,
        modifier = modifier
    ) {
        composable(DrivePadDestination.Home.route) {
            val homeViewModel: HomeViewModel = viewModel(
                factory = ViewModelFactoryProvider.provideHomeViewModelFactory(appContainer)
            )
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToRacing = {
                    navController.navigate(DrivePadDestination.Racing.route)
                },
                onNavigateToSettings = {
                    navController.navigate(DrivePadDestination.Settings.route)
                },
                onNavigateToProfiles = {
                    navController.navigate(DrivePadDestination.Profiles.route)
                },
                onNavigateToDiagnostics = {
                    navController.navigate(DrivePadDestination.Diagnostics.route)
                },
                onNavigateToDashboardCustomization = {
                    navController.navigate(DrivePadDestination.DashboardCustomization.route)
                }
            )
        }

        composable(
            route = DrivePadDestination.Racing.route,
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            val racingViewModel: RacingViewModel = viewModel(
                factory = ViewModelFactoryProvider.provideRacingViewModelFactory(appContainer)
            )
            RacingControllerScreen(
                viewModel = racingViewModel,
                onNavigateToSettings = {
                    navController.navigate(DrivePadDestination.Settings.route)
                },
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDashboardCustomization = {
                    navController.navigate(DrivePadDestination.DashboardCustomization.route)
                }
            )
        }

        composable(DrivePadDestination.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = ViewModelFactoryProvider.provideSettingsViewModelFactory(appContainer)
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                },
                onNavigateToDashboardCustomization = {
                    navController.navigate(DrivePadDestination.DashboardCustomization.route)
                }
            )
        }

        composable(DrivePadDestination.DashboardCustomization.route) {
            val customizationViewModel: DashboardCustomizationViewModel = viewModel(
                factory = ViewModelFactoryProvider.provideDashboardCustomizationViewModelFactory(appContainer)
            )
            DashboardCustomizationScreen(
                viewModel = customizationViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(DrivePadDestination.Profiles.route) {
            ProfilesScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(DrivePadDestination.Diagnostics.route) {
            val diagnosticsViewModel: DiagnosticsViewModel = viewModel(
                factory = ViewModelFactoryProvider.provideDiagnosticsViewModelFactory(appContainer)
            )
            DiagnosticsScreen(
                viewModel = diagnosticsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
