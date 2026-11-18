package com.cartvlab.browser

import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.cartvlab.core.datastore.AppStore
import com.cartvlab.core.designsystem.LabTheme
import com.cartvlab.core.model.Settings

class BrowserActivity : ComponentActivity() {
    lateinit var engine: BrowserEngine
        private set
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        if (windowManager.defaultDisplay.displayId != Display.DEFAULT_DISPLAY) { finish(); return }
        enableEdgeToEdge()
        engine = BrowserController(this, savedInstanceState?.getBundle("browser"))
        val account = AndroidAccountBrowser(this)
        val preferences = AppStore(applicationContext)
        setContent {
            val settings by preferences.settings.collectAsStateWithLifecycle(Settings())
            val state by engine.state.collectAsStateWithLifecycle()
            val fullscreen by engine.fullscreen.collectAsStateWithLifecycle()
            val dark = settings.theme == "Dark" || (settings.theme == "System" && androidx.compose.foundation.isSystemInDarkTheme())
            DisposableEffect(fullscreen, dark) {
                val bars = WindowCompat.getInsetsController(window, window.decorView)
                bars.isAppearanceLightStatusBars = !dark
                bars.isAppearanceLightNavigationBars = !dark
                window.isNavigationBarContrastEnforced = false
                if (fullscreen != null) {
                    bars.hide(WindowInsetsCompat.Type.systemBars())
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else bars.show(WindowInsetsCompat.Type.systemBars())
                onDispose { bars.show(WindowInsetsCompat.Type.systemBars()); window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            LabTheme(settings.theme) {
                BrowserScreen(engine, state, fullscreen, account, { finish() }, { recreate() })
            }
        }
    }
    override fun onResume() { super.onResume(); if (::engine.isInitialized) engine.resume() }
    override fun onPause() { if (::engine.isInitialized) engine.pause(); super.onPause() }
    override fun onSaveInstanceState(outState: Bundle) {
        if (::engine.isInitialized) engine.save()?.let { outState.putBundle("browser", it) }
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() { if (::engine.isInitialized) engine.close(); super.onDestroy() }
}
