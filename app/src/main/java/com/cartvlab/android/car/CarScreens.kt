package com.cartvlab.android.car

import androidx.annotation.DrawableRes
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.*
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.cartvlab.android.R
import com.cartvlab.browser.BrowserUrlPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun CarContext.icon(@DrawableRes id: Int) = CarIcon.Builder(IconCompat.createWithResource(this, id)).build()
private fun action(title: String, onClick: () -> Unit) = Action.Builder().setTitle(title).setOnClickListener(onClick).build()
/** Takes a car-screen screenshot after [delayMs] (lets a list screen close first) and reports the result. */
private fun Screen.carScreenshot(c: CarController, delayMs: Long = 0) {
    val app = carContext
    lifecycleScope.launch {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        val uri = c.renderer.screenshot()
        CarToast.makeText(app, if (uri != null) "Screenshot saved to gallery" else "Screenshot failed – open a video or page first",
            CarToast.LENGTH_SHORT).show()
    }
}
private fun Screen.listenWhileAlive(controller: CarController) {
    val l: () -> Unit = { invalidate() }
    lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onCreate(owner: LifecycleOwner) { controller.addListener(l) }
        override fun onDestroy(owner: LifecycleOwner) { controller.removeListener(l) }
    })
}

/** Root screen: the car display shows the video player / browser, with controls in the action strips. */
class MainCarScreen(carContext: CarContext, private val c: CarController) : Screen(carContext) {
    init { listenWhileAlive(c) }

    private fun iconAction(@DrawableRes id: Int, onClick: () -> Unit) =
        Action.Builder().setIcon(carContext.icon(id)).setOnClickListener(onClick).build()

    override fun onGetTemplate(): Template {
        // Collapsed: only a small arrow remains, freeing the right side of the screen. Tap it to bring the buttons back.
        if (c.display.buttonsHidden) {
            return NavigationTemplate.Builder()
                .setActionStrip(ActionStrip.Builder()
                    .addAction(iconAction(R.drawable.ic_car_chevron_left) { c.updateDisplay { it.copy(buttonsHidden = false) } })
                    .build())
                .build()
        }
        // Navigation template allows at most 4 buttons in each strip.
        val strip = ActionStrip.Builder()
            .addAction(iconAction(R.drawable.ic_car_chevron_right) { c.updateDisplay { it.copy(buttonsHidden = true) } })
            .addAction(action("Menu") { screenManager.push(MenuScreen(carContext, c)) })
        val map = ActionStrip.Builder().addAction(Action.PAN)
        when (c.mode) {
            CarMode.HOME -> {
                strip.addAction(action("Videos") { screenManager.push(VideoLibraryScreen(carContext, c)) })
                strip.addAction(action("YouTube") { c.showBrowser(BrowserUrlPolicy.YOUTUBE) })
            }
            CarMode.VIDEO -> {
                val p = c.video
                strip.addAction(action(if (p.player.isPlaying) "Pause" else "Play") {
                    p.toggle() // audio stays available; the speed lock only hides the picture
                })
                // Shows the current picture mode; tap to switch Full size → Cropped → Stretched.
                strip.addAction(action(c.display.fit.label) { c.updateDisplay { it.copy(fit = CarFit.entries.after(it.fit)) } })
                map.addAction(iconAction(R.drawable.ic_car_rewind) { p.seekBy(-10_000) })
                map.addAction(iconAction(R.drawable.ic_car_forward) { p.seekBy(10_000) })
                map.addAction(iconAction(R.drawable.ic_car_camera) { carScreenshot(c) })
            }
            CarMode.BROWSER -> {
                val b = c.browser
                strip.addAction(action("Back") { b.back() })
                strip.addAction(action("Type") { screenManager.push(InputScreen(carContext, c, InputKind.TYPE)) })
                map.addAction(iconAction(R.drawable.ic_car_zoom_in) { b.zoom(1.25f) })
                map.addAction(iconAction(R.drawable.ic_car_zoom_out) { b.zoom(0.8f) })
                map.addAction(iconAction(R.drawable.ic_car_reload) { b.reload() })
            }
        }
        return NavigationTemplate.Builder()
            .setActionStrip(strip.build())
            .setMapActionStrip(map.build())
            .setPanModeListener { }
            .build()
    }
}

class MenuScreen(carContext: CarContext, private val c: CarController) : Screen(carContext) {
    init { listenWhileAlive(c) }
    private fun row(title: String, text: String? = null, onClick: () -> Unit) =
        Row.Builder().setTitle(title).apply { if (text != null) addText(text) }.setOnClickListener(onClick).build()

    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
            .addItem(row("Phone videos", "Gallery, Downloads, camera") { screenManager.push(VideoLibraryScreen(carContext, c)) })
            .addItem(row("YouTube", "Uses your sign-in from the phone app") { c.showBrowser(BrowserUrlPolicy.YOUTUBE); screenManager.popToRoot() })
            .addItem(row("Display settings", "${c.display.layout.label} · ${c.display.fit.label}") { screenManager.push(DisplaySettingsScreen(carContext, c)) })
            .addItem(row("Search YouTube") { screenManager.push(InputScreen(carContext, c, InputKind.YOUTUBE)) })
            .addItem(row("Web browser", "Open a website or search") { screenManager.push(InputScreen(carContext, c, InputKind.WEB)) })
            .addItem(row("Play video URL", "MP4, MKV or HLS (.m3u8) link") { screenManager.push(InputScreen(carContext, c, InputKind.VIDEO_URL)) })
            .addItem(row("App playlist", "Videos opened in Watch Videos on the phone") {
                lifecycleScope.launch {
                    val items = c.phonePlaylist()
                    if (items.isEmpty()) CarToast.makeText(carContext, "Open videos in the phone app first", CarToast.LENGTH_LONG).show()
                    else { c.playVideos(items); screenManager.popToRoot() }
                }
            })
            .addItem(row(if (c.mode == CarMode.BROWSER && c.browser.desktop) "Mobile site" else "Desktop site", "Switch the browser layout") {
                c.showBrowser(); c.browser.setDesktopMode(!c.browser.desktop); screenManager.popToRoot()
            })
            .addItem(row("Test video", "Bundled clip to check sound and picture") {
                c.playVideos(listOf(CarVideo("asset:///test-pattern.mp4", "Test pattern"))); screenManager.popToRoot()
            })
            .addItem(row("Screenshot", "Save the car screen to the phone gallery") {
                // Run on the main screen (it stays alive) after the menu closes and the car surface is visible again.
                screenManager.popToRoot()
                screenManager.top.carScreenshot(c, 900)
            })
            .addItem(row("Close video / browser") { c.showHome(); screenManager.popToRoot() })
            .build()
        return ListTemplate.Builder().setTitle(carContext.applicationInfo.loadLabel(carContext.packageManager).toString()).setHeaderAction(Action.BACK).setSingleList(list).build()
    }
}

/** Car-side display settings. The same settings are on the phone (Android Auto Setup page). */
class DisplaySettingsScreen(carContext: CarContext, private val c: CarController) : Screen(carContext) {
    init { listenWhileAlive(c) }
    private fun row(title: String, text: String, onClick: () -> Unit) =
        Row.Builder().setTitle(title).addText(text).setOnClickListener(onClick).build()
    private fun toggle(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) =
        Row.Builder().setTitle(title).addText(text).setToggle(Toggle.Builder { onChange(it) }.setChecked(checked).build()).build()

    override fun onGetTemplate(): Template {
        val d = c.display
        val side = d.layout != CarLayout.FULL
        val list = ItemList.Builder()
            .addItem(row("Video position: ${d.layout.label}", "Tap to switch: Full screen → Right side → Left side") {
                c.updateDisplay { it.copy(layout = CarLayout.entries.after(it.layout)) }
            })
            .addItem(row("Video width: ${d.panelPercent}%", if (side) "Share of the screen used by the video" else "Used when the video is on the right or left side") {
                c.updateDisplay { it.copy(panelPercent = CarDisplay.PANEL_SIZES.after(it.panelPercent.takeIf { p -> p in CarDisplay.PANEL_SIZES } ?: 50)) }
            })
            .addItem(row("Picture: ${d.fit.label}", d.fit.hint) {
                c.updateDisplay { it.copy(fit = CarFit.entries.after(it.fit)) }
            })
            .addItem(toggle("Browser on the other side", "Show a website (e.g. Google Maps) next to the video", d.browserBeside) { on ->
                c.updateDisplay { it.copy(browserBeside = on) }
            })
            .addItem(toggle("Hide top buttons", "Frees the right side; tap the ‹ arrow to bring them back", d.buttonsHidden) { on ->
                c.updateDisplay { it.copy(buttonsHidden = on) }
            })
            .addItem(if (com.cartvlab.android.BuildConfig.DRIVING_LOCK)
                Row.Builder().setTitle("Driving lock: always on").addText("Video becomes audio-only above 30 km/h (GPS)").build()
                else toggle("Driving lock", "Audio only above 30 km/h (GPS)", d.safetyLock) { on -> c.updateDisplay { it.copy(safetyLock = on) } })
            .build()
        return ListTemplate.Builder().setTitle("Car screen display").setHeaderAction(Action.BACK).setSingleList(list).build()
    }
}

class VideoLibraryScreen(carContext: CarContext, private val c: CarController) : Screen(carContext) {
    private var videos: List<CarVideo>? = null

    override fun onGetTemplate(): Template {
        if (!c.hasVideoPermission()) {
            return MessageTemplate.Builder("Allow this app to read the videos on your phone.")
                .setTitle("Phone videos").setHeaderAction(Action.BACK)
                .addAction(Action.Builder().setTitle("Allow").setFlags(Action.FLAG_PRIMARY).setOnClickListener {
                    carContext.requestPermissions(listOf(c.videoPermission)) { _, _ -> videos = null; invalidate() }
                }.build())
                .build()
        }
        val loaded = videos
        val builder = ListTemplate.Builder().setTitle("Phone videos").setHeaderAction(Action.BACK)
        if (loaded == null) {
            val limit = runCatching {
                carContext.getCarService(ConstraintManager::class.java).getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
            }.getOrDefault(25)
            lifecycleScope.launch {
                videos = withContext(Dispatchers.IO) { c.phoneVideos(limit) }
                invalidate()
            }
            return builder.setLoading(true).build()
        }
        val list = ItemList.Builder().setNoItemsMessage("No videos found on this phone")
        loaded.forEachIndexed { i, v ->
            list.addItem(Row.Builder().setTitle(v.title).setOnClickListener { c.playVideos(loaded, i); screenManager.popToRoot() }.build())
        }
        return builder.setSingleList(list.build()).build()
    }
}

enum class InputKind { WEB, YOUTUBE, VIDEO_URL, TYPE }

/** Car keyboard (available while parked) for URLs, searches and typing into web pages. */
class InputScreen(carContext: CarContext, private val c: CarController, private val kind: InputKind) : Screen(carContext) {
    private fun submit(text: String) {
        val value = text.trim()
        when (kind) {
            InputKind.WEB -> if (value.isNotEmpty()) c.showBrowser(value)
            InputKind.YOUTUBE -> if (value.isNotEmpty()) { c.showBrowser(); c.browser.youtubeSearch(value) }
            InputKind.VIDEO_URL -> if (BrowserUrlPolicy.isWebUrl(value)) c.playVideos(listOf(CarVideo(value, "Network video")))
                else { CarToast.makeText(carContext, "Enter a full http(s):// video link", CarToast.LENGTH_LONG).show(); return }
            InputKind.TYPE -> c.browser.typeText(text, enter = false)
        }
        screenManager.popToRoot()
    }

    override fun onGetTemplate(): Template {
        val hint = when (kind) {
            InputKind.WEB -> "Website or search"
            InputKind.YOUTUBE -> "Search YouTube"
            InputKind.VIDEO_URL -> "https://…/video.mp4"
            InputKind.TYPE -> "Text to type into the page"
        }
        val items = ItemList.Builder()
        fun suggestion(title: String, onClick: () -> Unit) { items.addItem(Row.Builder().setTitle(title).setOnClickListener(onClick).build()) }
        when (kind) {
            InputKind.WEB -> {
                suggestion("youtube.com") { c.showBrowser(BrowserUrlPolicy.YOUTUBE); screenManager.popToRoot() }
                suggestion("google.com") { c.showBrowser("https://www.google.com/"); screenManager.popToRoot() }
                suggestion("Sign in to Google / YouTube") { c.showBrowser(BrowserUrlPolicy.YOUTUBE_SIGN_IN); screenManager.popToRoot() }
            }
            InputKind.TYPE -> {
                suggestion("Press Enter") { c.browser.pressEnter(); screenManager.popToRoot() }
                suggestion("Clear the field") { c.browser.clearField(); screenManager.popToRoot() }
            }
            else -> {}
        }
        return SearchTemplate.Builder(object : SearchTemplate.SearchCallback {
            override fun onSearchSubmitted(searchText: String) = submit(searchText)
        }).setHeaderAction(Action.BACK).setSearchHint(hint).setShowKeyboardByDefault(true).setItemList(items.build()).build()
    }
}
