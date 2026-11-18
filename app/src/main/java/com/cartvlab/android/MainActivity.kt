package com.cartvlab.android

import android.os.Bundle
import android.view.Display
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.Text
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartvlab.core.designsystem.LabTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint class MainActivity : ComponentActivity() {
    private val model: LabViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        @Suppress("DEPRECATION")
        val currentDisplayId = windowManager.defaultDisplay.displayId
        // Fail closed: this build has no verified vehicle execution or motion-state contract.
        if (currentDisplayId != Display.DEFAULT_DISPLAY) {
            setContent { LabTheme { Text("External-display playback is disabled: no verified parked-only integration.") } }
            return
        }
        model.displayId = currentDisplayId
        setContent {
            val settings = model.settings.collectAsStateWithLifecycle().value
            LabTheme(settings.theme) { Navigation(model, intent.getStringExtra("screen") ?: "Home") }
        }
    }
    override fun onStart() { super.onStart(); model.foreground() }
    override fun onStop() { model.background(); super.onStop() }
}
