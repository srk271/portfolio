package dev.bidu.app

import android.app.Application
import android.content.Context
import dev.bidu.app.notifications.NotificationChannels

class BiduApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        container = AppContainer(this)
    }
}

/** Reaches the dependency graph from any Android component. */
val Context.biduContainer: AppContainer
    get() = (applicationContext as BiduApplication).container
