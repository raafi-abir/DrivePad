package com.drivepad.app

import android.app.Application
import com.drivepad.app.di.AppContainer
import com.drivepad.app.di.DefaultAppContainer

class DrivePadApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
