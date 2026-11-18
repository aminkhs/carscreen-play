package com.cartvlab.automotive

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display

interface VehicleDisplayManager { fun displays(): List<DisplayEvidence> }
data class DisplayEvidence(val id: Int, val width: Int, val height: Int, val refresh: Float,
    val state: Int, val modes: List<String>, val external: Boolean)
class AndroidVehicleDisplayManager(private val context: Context) : VehicleDisplayManager {
    override fun displays() = context.getSystemService(DisplayManager::class.java).displays.map { display ->
        val mode = display.mode
        DisplayEvidence(display.displayId, mode.physicalWidth, mode.physicalHeight, mode.refreshRate, display.state,
            display.supportedModes.map { "${it.physicalWidth}x${it.physicalHeight}@${it.refreshRate}" },
            display.displayId != Display.DEFAULT_DISPLAY)
    }
}
interface CarConnectionDiagnostics { fun observableConnection(): String }
class UnknownCarConnectionDiagnostics : CarConnectionDiagnostics {
    override fun observableConnection() = "UNKNOWN"
}
data class CompatibilityReport(val vehicle: String, val observedConnection: String, val capabilities: List<com.cartvlab.core.model.Capability>)
