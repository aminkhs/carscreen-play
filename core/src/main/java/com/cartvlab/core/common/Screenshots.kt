package com.cartvlab.core.common

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/** Captures a window (including video SurfaceViews, which a normal window copy shows as black) and saves it to the gallery. */
object Screenshots {
    private val main = Handler(Looper.getMainLooper())

    /** Must be called on the main thread. Returns null if the window has nothing to capture yet. */
    suspend fun capture(window: Window): Bitmap? {
        val decor = window.peekDecorView() ?: return null
        if (decor.width <= 0 || decor.height <= 0) return null
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        if (!copy { PixelCopy.request(window, bitmap, it, main) }) {
            // Fallback: software draw of the view tree (no video layers).
            bitmap.eraseColor(Color.BLACK)
            runCatching { decor.draw(Canvas(bitmap)) }.onFailure { return null }
        }
        val canvas = Canvas(bitmap)
        val windowPos = IntArray(2).also { decor.getLocationInWindow(it) }
        surfaceViews(decor).forEach { sv ->
            val frame = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
            if (copy { PixelCopy.request(sv, frame, it, main) }) {
                val pos = IntArray(2).also { sv.getLocationInWindow(it) }
                canvas.drawBitmap(frame, (pos[0] - windowPos[0]).toFloat(), (pos[1] - windowPos[1]).toFloat(), null)
            }
            frame.recycle()
        }
        return bitmap
    }

    /** Saves to Pictures/CarScreen Play. Returns the gallery Uri, or null on failure. No permission needed on Android 10+. */
    suspend fun save(context: Context, bitmap: Bitmap, prefix: String): Uri? = withContext(Dispatchers.IO) {
        val name = "${prefix}_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.png"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CarScreen Play")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@withContext null
        val written = runCatching {
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } ?: false
        }.getOrDefault(false)
        if (!written) { resolver.delete(uri, null, null); return@withContext null }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        uri
    }

    suspend fun captureAndSave(context: Context, window: Window, prefix: String): Uri? {
        val bitmap = capture(window) ?: return null
        return try { save(context, bitmap, prefix) } finally { bitmap.recycle() }
    }

    private suspend fun copy(request: (PixelCopy.OnPixelCopyFinishedListener) -> Unit): Boolean =
        suspendCancellableCoroutine { cont ->
            runCatching { request { result -> if (cont.isActive) cont.resume(result == PixelCopy.SUCCESS) } }
                .onFailure { if (cont.isActive) cont.resume(false) }
        }

    private fun surfaceViews(view: View): List<SurfaceView> = when {
        view.visibility != View.VISIBLE || view.width == 0 || view.height == 0 -> emptyList()
        view is SurfaceView -> listOf(view)
        view is ViewGroup -> (0 until view.childCount).flatMap { surfaceViews(view.getChildAt(it)) }
        else -> emptyList()
    }
}
