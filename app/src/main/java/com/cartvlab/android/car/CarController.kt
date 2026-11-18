package com.cartvlab.android.car

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import com.cartvlab.android.BuildConfig
import com.cartvlab.core.datastore.SettingsStore
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray

const val MAPS_URL = "https://www.google.com/maps"

enum class CarMode { HOME, VIDEO, BROWSER }

/** Owns everything shown on the car screen for one Android Auto session. */
class CarController(val carContext: CarContext, private val store: SettingsStore) {
    val renderer = CarSurfaceRenderer(carContext)
    var mode = CarMode.HOME
        private set
    private val listeners = mutableSetOf<() -> Unit>()
    private var videoPlayer: CarVideoPlayer? = null
    private var carBrowser: CarBrowser? = null
    private val guard = SpeedGuard(carContext) { state -> onLockState(state) }
    private val scope = MainScope()

    /** Car screen display settings (position, panel size, crop / full size, buttons hidden, safety lock). */
    var display = CarDisplay()
        private set

    /** GitHub build: always on. Personal build: optional setting (off by default). */
    val lockEnforced: Boolean get() = BuildConfig.DRIVING_LOCK || display.safetyLock
    val lockState: LockState get() = guard.state

    init {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(renderer)
        renderer.onScale = { factor -> if (mode == CarMode.BROWSER || display.browserBeside) carBrowser?.zoom(factor) }
        if (BuildConfig.DRIVING_LOCK) guard.start()
        scope.launch {
            CarDisplayPrefs.load(store)
            CarDisplayPrefs.state.collect { next -> applyDisplay(next) }
        }
    }

    fun addListener(l: () -> Unit) { listeners += l }
    fun removeListener(l: () -> Unit) { listeners -= l }
    private fun notifyChanged() { listeners.toList().forEach { it() } }

    fun updateDisplay(change: (CarDisplay) -> CarDisplay) { scope.launch { CarDisplayPrefs.update(store, change) } }

    private fun applyDisplay(next: CarDisplay) {
        val old = display
        display = next
        videoPlayer?.setResizeMode(next.fit.resizeMode)
        if (lockEnforced) guard.start() else guard.stop()
        val layoutChanged = old.layout != next.layout || old.panelPercent != next.panelPercent || old.browserBeside != next.browserBeside
        if (layoutChanged && mode == CarMode.VIDEO) renderer.setContent { videoContent(it) }
        notifyChanged()
    }

    /** Above the speed limit the picture is covered by an audio icon; sound keeps playing. */
    private fun onLockState(state: LockState) {
        val message = when {
            !lockEnforced || state == LockState.CLEAR -> null
            state == LockState.MOVING -> "Audio only\nVideo is hidden above ${SpeedLockPolicy.LIMIT_KMH.toInt()} km/h.\nIt returns about 10 seconds after you slow down."
            state == LockState.NEED_PERMISSION -> "Audio only\nAllow location access on your phone.\nThe speed check needs GPS."
            else -> "Audio only\nTurn on location (GPS) on your phone."
        }
        renderer.setOverlay(message, audioIcon = message != null)
        notifyChanged()
    }

    val video: CarVideoPlayer
        get() = videoPlayer ?: CarVideoPlayer(carContext) { notifyChanged() }.also {
            it.setResizeMode(display.fit.resizeMode)
            videoPlayer = it
        }

    /** Video full screen, or in a side panel with empty space / the browser in the rest of the screen. */
    private fun videoContent(ctx: Context): View {
        val settings = display // note: inside View.apply{}, `display` would mean View.getDisplay()
        val playerView = video.view(ctx).also { detach(it) }
        if (settings.layout == CarLayout.FULL) return playerView
        val other: View = if (settings.browserBeside) {
            browser.also { b -> if (b.web.url == null) b.open(MAPS_URL) }.web.also { detach(it) }
        } else View(ctx).apply { setBackgroundColor(Color.BLACK) }
        val panel = settings.panelPercent.toFloat()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            val videoLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, panel)
            val otherLp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 100f - panel)
            if (settings.layout == CarLayout.RIGHT) { addView(other, otherLp); addView(playerView, videoLp) }
            else { addView(playerView, videoLp); addView(other, otherLp) }
        }
    }

    private fun detach(view: View) { (view.parent as? ViewGroup)?.removeView(view) }

    val browser: CarBrowser
        get() = carBrowser ?: run {
            val ctx = renderer.viewContext ?: carContext
            CarBrowser(ctx, { renderer.showFullscreen(it) }, { notifyChanged() }).also { carBrowser = it }
        }

    fun showVideo() {
        carBrowser?.pauseMedia()
        mode = CarMode.VIDEO
        renderer.setContent { ctx -> videoContent(ctx) }
        notifyChanged()
    }

    fun playVideos(items: List<CarVideo>, index: Int = 0) {
        showVideo()
        video.play(items, index)
    }

    fun showBrowser(url: String? = null) {
        videoPlayer?.player?.pause()
        mode = CarMode.BROWSER
        val b = browser
        renderer.setContent { b.web }
        if (url != null) b.open(url) else if (b.web.url == null) b.open(com.cartvlab.browser.BrowserUrlPolicy.YOUTUBE)
        notifyChanged()
    }

    fun showHome() {
        videoPlayer?.player?.pause()
        carBrowser?.pauseMedia()
        mode = CarMode.HOME
        renderer.setContent(null)
        notifyChanged()
    }

    /** Playlist the user opened in the phone app's Watch Videos screen. */
    suspend fun phonePlaylist(): List<CarVideo> = runCatching {
        val a = JSONArray(store.read("playlist") ?: "[]")
        (0 until a.length()).map { a.getJSONObject(it).let { o -> CarVideo(o.getString("uri"), o.getString("title")) } }
    }.getOrDefault(emptyList())

    fun hasVideoPermission(): Boolean = hasVideoPermission(carContext)
    val videoPermission: String get() = videoPermission()
    fun phoneVideos(limit: Int): List<CarVideo> = queryPhoneVideos(carContext, limit)

    fun release() {
        scope.cancel()
        guard.stop()
        renderer.release()
        carBrowser?.release(); carBrowser = null
        videoPlayer?.player?.pause() // disconnected from the car: stop sound
        videoPlayer?.release(); videoPlayer = null
        listeners.clear()
    }
}
