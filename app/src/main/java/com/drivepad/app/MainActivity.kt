package com.drivepad.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.drivepad.app.core.designsystem.DrivePadTheme
import com.drivepad.app.core.navigation.DrivePadNavHost
import com.drivepad.app.domain.model.DashboardConfiguration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )

        val appContainer = (application as DrivePadApplication).container

        setContent {
            val dashboardConfig by appContainer.dashboardPreferencesRepository
                .dashboardConfigFlow
                .collectAsStateWithLifecycle(initialValue = DashboardConfiguration())

            DrivePadTheme(
                themeMode = dashboardConfig.themeMode,
                accentColor = dashboardConfig.accentColor
            ) {
                DrivePadNavHost(
                    appContainer = appContainer,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        (application as DrivePadApplication).container.controllerInputRepository.safetyResetAllInputs()
    }
}
