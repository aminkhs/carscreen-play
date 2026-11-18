package com.cartvlab.android.car

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer

/**
 * Renders ordinary Android views on the Android Auto car screen.
 *
 * Android Auto hands navigation-category apps a Surface. We back a private VirtualDisplay with
 * that Surface and show a [Presentation] on it, so the video player and WebView draw directly
 * on the car display. Car touchscreen taps / pan gestures are converted into touch events.
 */
class CarSurfaceRenderer(private val carContext: CarContext) : SurfaceCallback {
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var root: FrameLayout? = null
    private var contentHolder: FrameLayout? = null
    private var overlay: TextView? = null
    private var width = 0
    private var height = 0
    private var dpi = 160
    private var visible = Rect()
    private var contentFactory: ((Context) -> View)? = null
    private var overlayText: String? = null
    private var released = false
    private val handler = Handler(Looper.getMainLooper())

    /** Context bound to the car display; views shown on the car must be created with it. */
    var viewContext: Context? = null
        private set
    var onScale: ((Float) -> Unit)? = null

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val surface = container.surface ?: return
        if (released || container.width <= 0 || container.height <= 0) return
        val sizeChanged = container.width != width || container.height != height || container.dpi != dpi
        width = container.width; height = container.height; dpi = container.dpi.coerceAtLeast(80)
        if (visible.isEmpty) visible = Rect(0, 0, width, height)
        val existing = display
        if (existing == null) {
            val dm = carContext.getSystemService(DisplayManager::class.java)
            display = dm.createVirtualDisplay("CarScreenPlay", width, height, dpi, surface,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
            showPresentation()
        } else {
            existing.surface = surface
            if (sizeChanged) { existing.resize(width, height, dpi); showPresentation() }
        }
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) { display?.surface = null }

    override fun onVisibleAreaChanged(visibleArea: Rect) { visible = Rect(visibleArea); applyPadding() }

    override fun onStableAreaChanged(stableArea: Rect) {}

    override fun onClick(x: Float, y: Float) { endDrag(); tap(x, y) }

    override fun onScroll(distanceX: Float, distanceY: Float) { drag(distanceX, distanceY) }

    override fun onFling(velocityX: Float, velocityY: Float) { endDrag() }

    override fun onScale(focusX: Float, focusY: Float, scaleFactor: Float) { onScale?.invoke(scaleFactor) }

    /** Replace what is shown on the car screen. The factory receives the car-display context. */
    fun setContent(factory: ((Context) -> View)?) {
        contentFactory = factory
        attachContent()
    }

    /**
     * Opaque full-screen cover over the content (null hides it). The content keeps running underneath,
     * so audio continues; with [audioIcon] a large speaker icon is shown above the message.
     */
    fun setOverlay(text: String?, audioIcon: Boolean = false) {
        overlayText = text
        overlayIcon = audioIcon
        overlay?.let { styleOverlay(it) }
    }

    private var overlayIcon = false

    private fun styleOverlay(view: TextView) {
        view.text = overlayText.orEmpty()
        view.visibility = if (overlayText == null) View.GONE else View.VISIBLE
        view.setCompoundDrawablesWithIntrinsicBounds(0, if (overlayIcon) com.cartvlab.android.R.drawable.ic_car_audio_large else 0, 0, 0)
    }

    /** Temporarily add a view (e.g. WebView HTML5 fullscreen video) above the content. */
    fun showFullscreen(view: View?) {
        val holder = root ?: return
        holder.findViewWithTag<View>(FULLSCREEN_TAG)?.let { holder.removeView(it) }
        if (view != null) {
            (view.parent as? ViewGroup)?.removeView(view)
            view.tag = FULLSCREEN_TAG
            holder.addView(view, holder.indexOfChild(overlay), FrameLayout.LayoutParams(-1, -1))
        }
    }

    /** Saves what is currently on the car screen to the phone gallery. */
    suspend fun screenshot(): android.net.Uri? {
        val window = presentation?.window ?: return null
        return com.cartvlab.core.common.Screenshots.captureAndSave(carContext, window, "CarScreen")
    }

    fun release() {
        released = true
        endDrag()
        handler.removeCallbacksAndMessages(null)
        contentHolder?.removeAllViews()
        val old = presentation
        presentation = null
        runCatching { old?.dismiss() }
        display?.release()
        display = null
    }

    private fun showPresentation() {
        val vd = display ?: return
        val old = presentation
        presentation = null
        runCatching { old?.dismiss() }
        contentHolder?.removeAllViews()
        val ctx = viewContext ?: ContextThemeWrapper(carContext.createDisplayContext(vd.display),
            android.R.style.Theme_DeviceDefault_NoActionBar).also { viewContext = it }
        val holder = FrameLayout(ctx)
        val text = TextView(ctx).apply {
            setBackgroundColor(Color.rgb(10, 12, 16)); setTextColor(Color.WHITE); textSize = 22f
            gravity = Gravity.CENTER; setPadding(48, 48, 48, 48); compoundDrawablePadding = 24
            isClickable = true // blocks touches to the hidden video / page
        }.also { styleOverlay(it) }
        val frame = FrameLayout(ctx).apply {
            setBackgroundColor(Color.BLACK)
            addView(holder, FrameLayout.LayoutParams(-1, -1))
            addView(text, FrameLayout.LayoutParams(-1, -1))
        }
        val p = Presentation(ctx, vd.display)
        p.window?.setFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED, WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        p.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        p.setContentView(frame)
        p.setCancelable(false)
        // Android dismisses a Presentation when its display changes; rebuild it if that happens.
        p.setOnDismissListener {
            if (presentation === p && !released) handler.post { if (presentation === p) { presentation = null; showPresentation() } }
        }
        try {
            p.show()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to show car presentation", e)
            return
        }
        presentation = p; root = frame; contentHolder = holder; overlay = text
        applyPadding()
        attachContent()
    }

    private fun attachContent() {
        val holder = contentHolder ?: return
        val ctx = viewContext ?: return
        holder.removeAllViews()
        val view = contentFactory?.invoke(ctx) ?: homeView(ctx)
        (view.parent as? ViewGroup)?.removeView(view)
        holder.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    private fun homeView(ctx: Context) = TextView(ctx).apply {
        text = ctx.applicationInfo.loadLabel(ctx.packageManager).toString() + "\n\nMenu → Videos, YouTube or Browser\nTap the screen to press buttons · tap ✥ (pan) to scroll"
        setTextColor(Color.WHITE); textSize = 20f; gravity = Gravity.CENTER
    }

    private fun applyPadding() {
        val r = root ?: return
        if (visible.isEmpty || width == 0) return
        r.setPadding(visible.left.coerceAtLeast(0), visible.top.coerceAtLeast(0),
            (width - visible.right).coerceAtLeast(0), (height - visible.bottom).coerceAtLeast(0))
    }

    // ---- Touch injection -------------------------------------------------------------------

    private fun send(action: Int, downTime: Long, x: Float, y: Float) {
        val p = presentation ?: return
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        runCatching { p.dispatchTouchEvent(event) }
        event.recycle()
    }

    private fun tap(x: Float, y: Float) {
        val down = SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN, down, x, y)
        handler.postDelayed({ send(MotionEvent.ACTION_UP, down, x, y) }, 40)
    }

    private var dragDown = 0L
    private var dragX = 0f
    private var dragY = 0f
    private val endDragRunnable = Runnable { endDrag() }

    /** Pan gestures arrive as scroll distances; replay them as a finger drag on the content. */
    private fun drag(dx: Float, dy: Float) {
        val area = if (visible.isEmpty) Rect(0, 0, width, height) else visible
        if (dragDown == 0L) {
            dragDown = SystemClock.uptimeMillis()
            dragX = area.exactCenterX(); dragY = area.exactCenterY()
            send(MotionEvent.ACTION_DOWN, dragDown, dragX, dragY)
        }
        dragX -= dx; dragY -= dy
        if (!area.contains(dragX.toInt(), dragY.toInt())) {
            dragX = dragX.coerceIn(area.left.toFloat(), area.right - 1f)
            dragY = dragY.coerceIn(area.top.toFloat(), area.bottom - 1f)
            send(MotionEvent.ACTION_MOVE, dragDown, dragX, dragY)
            endDrag()
            return
        }
        send(MotionEvent.ACTION_MOVE, dragDown, dragX, dragY)
        handler.removeCallbacks(endDragRunnable)
        handler.postDelayed(endDragRunnable, 150)
    }

    private fun endDrag() {
        handler.removeCallbacks(endDragRunnable)
        if (dragDown != 0L) send(MotionEvent.ACTION_UP, dragDown, dragX, dragY)
        dragDown = 0L
    }

    private companion object {
        const val TAG = "CarSurfaceRenderer"
        const val FULLSCREEN_TAG = "car-fullscreen"
    }
}
