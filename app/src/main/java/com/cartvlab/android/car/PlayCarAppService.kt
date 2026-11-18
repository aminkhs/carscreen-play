package com.cartvlab.android.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.cartvlab.core.datastore.SettingsStore
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint @InstallIn(SingletonComponent::class)
interface CarEntryPoint { fun store(): SettingsStore }

/** Entry point Android Auto binds to. Declared in the manifest with the NAVIGATION category. */
class PlayCarAppService : CarAppService() {
    // Release: only the official Android Auto / Automotive hosts (Car App Library allowlist).
    // Debug: any host, so the Desktop Head Unit and test hosts can connect.
    override fun createHostValidator(): HostValidator =
        if (com.cartvlab.android.BuildConfig.DEBUG) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(applicationContext).addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample).build()

    override fun onCreateSession(sessionInfo: SessionInfo): Session = PlaySession()
}

class PlaySession : Session() {
    override fun onCreateScreen(intent: Intent): Screen {
        val store = EntryPointAccessors.fromApplication(carContext.applicationContext, CarEntryPoint::class.java).store()
        val controller = CarController(carContext, store)
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) { controller.release() }
        })
        return MainCarScreen(carContext, controller)
    }
}
