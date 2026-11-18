@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.cartvlab.android.car

import androidx.media3.ui.AspectRatioFrameLayout
import com.cartvlab.core.datastore.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the video sits on the car screen. */
enum class CarLayout(val label: String) { FULL("Full screen"), RIGHT("Right side"), LEFT("Left side") }

/** How the picture fills its area. */
enum class CarFit(val label: String, val hint: String, val resizeMode: Int) {
    FIT("Full size", "Whole picture, black bars if needed", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    CROP("Cropped", "Fills the area, edges cut off", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    STRETCH("Stretched", "Fills the area, picture distorted", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

data class CarDisplay(
    val layout: CarLayout = CarLayout.FULL,
    val panelPercent: Int = 50,
    val fit: CarFit = CarFit.FIT,
    /** Show the browser (e.g. Google Maps website) in the other part of the screen when the video is on one side. */
    val browserBeside: Boolean = false,
    /** Car action-strip buttons collapsed to a single arrow. */
    val buttonsHidden: Boolean = false,
    /** Personal build only: hide video above 30 km/h (always on in the GitHub build). */
    val safetyLock: Boolean = false
) {
    fun encode() = "layout=${layout.name};panel=$panelPercent;fit=${fit.name};browser=${if (browserBeside) 1 else 0};hidden=${if (buttonsHidden) 1 else 0};lock=${if (safetyLock) 1 else 0}"

    companion object {
        val PANEL_SIZES = listOf(40, 50, 60, 70)
        fun decode(value: String?): CarDisplay {
            if (value.isNullOrBlank()) return CarDisplay()
            val map = value.split(';').mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return CarDisplay(
                layout = CarLayout.entries.firstOrNull { it.name == map["layout"] } ?: CarLayout.FULL,
                panelPercent = map["panel"]?.toIntOrNull()?.takeIf { it in 20..80 } ?: 50,
                fit = CarFit.entries.firstOrNull { it.name == map["fit"] } ?: CarFit.FIT,
                browserBeside = map["browser"] == "1",
                buttonsHidden = map["hidden"] == "1",
                safetyLock = map["lock"] == "1"
            )
        }
    }
}

/** Next value in the list, wrapping around (used by car rows that cycle through options). */
fun <T> List<T>.after(item: T): T = this[(indexOf(item) + 1).mod(size)]

/** Shared between the phone UI and the car session (same process); persisted in the app store. */
object CarDisplayPrefs {
    private const val KEY = "car_display"
    private val mutable = MutableStateFlow(CarDisplay())
    val state = mutable.asStateFlow()
    @Volatile private var loaded = false

    suspend fun load(store: SettingsStore) {
        if (loaded) return
        mutable.value = CarDisplay.decode(store.read(KEY))
        loaded = true
    }

    suspend fun update(store: SettingsStore, change: (CarDisplay) -> CarDisplay) {
        load(store)
        val next = change(mutable.value)
        mutable.value = next
        store.write(KEY, next.encode())
    }
}
