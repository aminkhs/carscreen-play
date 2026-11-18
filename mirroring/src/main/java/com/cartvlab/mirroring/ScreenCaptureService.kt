package com.cartvlab.mirroring

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.*
import android.os.*
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.cartvlab.core.model.CaptureState
import kotlinx.coroutines.flow.update
import kotlin.math.min
import kotlin.math.roundToInt

/** All projection resources belong to one serial worker. Consent is consumed once per service session. */
class ScreenCaptureService : Service() {
    private val thread = HandlerThread("screen-capture")
    private lateinit var handler: Handler
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var encoder: VideoEncoder? = null
    private var transport: ReceiverTransport? = null
    private var options = CaptureOptions()
    private var consumed = false
    private var stopping = false
    private var frames = 0L
    private var began = 0L
    private var lastFrame = 0L
    private var captureWidth = 0
    private var captureHeight = 0
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { cleanup(); stopSelf() }
        override fun onCapturedContentResize(width: Int, height: Int) {
            if (width > 0 && height > 0 && !stopping) runCatching { resize(width, height) }.onFailure { fail("Capture could not resize. Stop and request a new session.") }
        }
    }
    override fun onCreate() { super.onCreate(); thread.start(); handler = Handler(thread.looper) }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (consumed) return START_NOT_STICKY
        consumed = true
        createNotification()
        val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra("consent", Intent::class.java)
            else @Suppress("DEPRECATION") (intent?.getParcelableExtra("consent") as? Intent)
        val code = intent?.getIntExtra("code", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        if (intent == null || data == null || code != Activity.RESULT_OK) { stopSelf(); return START_NOT_STICKY }
        options = CaptureOptions(intent.getIntExtra("height", 720).coerceIn(360, 1080),
            intent.getIntExtra("fps", 30).coerceIn(10, 30), intent.getBooleanExtra("hardware", true),
            intent.getStringExtra("host").orEmpty(), intent.getStringExtra("token").orEmpty())
        handler.post {
            try {
                began = SystemClock.elapsedRealtime()
                MirrorSessionManager.mutable.value = CaptureState(active = true)
                val p = requireNotNull(getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data))
                projection = p
                p.registerCallback(callback, handler)
                val size = captureSize()
                resize(size.first, size.second)
            } catch (_: Exception) { fail("Screen capture could not start. Unlock the phone and grant fresh capture consent.") }
        }
        return START_NOT_STICKY
    }
    private fun captureSize(): Pair<Int, Int> {
        val manager = getSystemService(WindowManager::class.java)
        return if (Build.VERSION.SDK_INT >= 30) manager.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        else {
            val metrics = android.util.DisplayMetrics()
            @Suppress("DEPRECATION")
            manager.defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }
    override fun onConfigurationChanged(configuration: android.content.res.Configuration) {
        super.onConfigurationChanged(configuration)
        if (Build.VERSION.SDK_INT < 34) handler.post {
            if (projection != null && !stopping) runCatching {
                val size = captureSize(); resize(size.first, size.second)
            }.onFailure { fail("Capture could not resize. Start a new session.") }
        }
    }
    private fun createNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("capture", "Screen capture", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 2, Intent(this, javaClass).setAction("STOP"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val notification = NotificationCompat.Builder(this, "capture")
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("CarScreen Play · screen capture")
            .setContentText("Sharing only with your selected preview or receiver. Tap Stop to end.")
            .setOngoing(true).setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop", stop)
            .apply { if (launch != null) setContentIntent(PendingIntent.getActivity(this@ScreenCaptureService, 3, launch, PendingIntent.FLAG_IMMUTABLE)) }
            .build()
        startForeground(42, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }
    private fun resize(sourceWidth: Int, sourceHeight: Int) {
        val scale = min(1.0, options.height.toDouble() / min(sourceWidth, sourceHeight))
        val width = ((sourceWidth * scale).roundToInt() / 2 * 2).coerceAtLeast(2)
        val height = ((sourceHeight * scale).roundToInt() / 2 * 2).coerceAtLeast(2)
        if (width == captureWidth && height == captureHeight && display != null) return
        display?.surface = null
        reader?.close(); reader = null
        encoder?.close(); encoder = null
        transport?.close(); transport = null
        captureWidth = width; captureHeight = height
        MirrorSessionManager.image.value = null
        val surface: Surface
        if (options.host.isNotBlank()) {
            require(options.token.matches(Regex("[0-9]{8}")))
            val receiver = TcpReceiverTransport(options.host, options.token) { message -> handler.post { fail(message) } }
            transport = receiver
            encoder = VideoEncoder(width, height, options.fps, options.hardware, handler, receiver,
                { bitrate, name -> frame(bitrate, name) },
                { handler.post { fail("Encoder or receiver cannot keep up. Try a lower resolution or framerate.") } })
            surface = encoder!!.surface
        } else {
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            reader = imageReader
            imageReader.setOnImageAvailableListener({ source ->
                runCatching {
                    source.acquireLatestImage()?.use { image ->
                        val now = SystemClock.elapsedRealtime()
                        if (now - lastFrame < 1000 / options.fps || stopping) return@use
                        lastFrame = now
                        val plane = image.planes[0]
                        val paddedWidth = plane.rowStride / plane.pixelStride
                        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                        padded.copyPixelsFromBuffer(plane.buffer)
                        val bitmap = if (paddedWidth == image.width) padded
                            else Bitmap.createBitmap(padded, 0, 0, image.width, image.height).also { padded.recycle() }
                        MirrorSessionManager.image.value = bitmap
                        frame(0, "RGBA preview (not encoded)")
                    }
                }.onFailure { fail("Capture surface became unavailable. Start a new session.") }
            }, handler)
            surface = imageReader.surface
        }
        val current = display
        if (current == null) {
            display = projection!!.createVirtualDisplay("CarScreen Play capture", width, height,
                resources.configuration.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, handler)
        } else {
            current.resize(width, height, resources.configuration.densityDpi)
            current.surface = surface
        }
        MirrorSessionManager.mutable.update { it.copy(active = true, width = width, height = height,
            transport = if (options.host.isBlank()) "Local preview" else "Experimental paired TCP · video only") }
    }
    private fun frame(bitrate: Int, name: String) {
        frames++
        val elapsed = (SystemClock.elapsedRealtime() - began).coerceAtLeast(1)
        MirrorSessionManager.mutable.update { it.copy(frames = frames, fps = frames * 1000.0 / elapsed, bitrate = bitrate, encoder = name) }
    }
    private fun fail(message: String) {
        if (stopping) return
        MirrorSessionManager.mutable.update { it.copy(error = message) }
        cleanup(); stopSelf()
    }
    private fun cleanup() {
        if (stopping) return
        stopping = true
        runCatching { display?.release() }; display = null
        runCatching { reader?.close() }; reader = null
        runCatching { encoder?.close() }; encoder = null
        runCatching { transport?.close() }; transport = null
        runCatching { projection?.unregisterCallback(callback); projection?.stop() }; projection = null
        MirrorSessionManager.image.value = null
        MirrorSessionManager.mutable.update { it.copy(active = false) }
    }
    override fun onDestroy() {
        handler.post { cleanup(); thread.quitSafely() }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
