package com.cartvlab.mirroring

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import androidx.core.content.ContextCompat

interface MediaProjectionController {
    fun consentIntent(): Intent
    fun start(resultCode: Int, data: Intent, options: CaptureOptions)
    fun stop()
}
class AndroidMediaProjectionController(private val context: Context) : MediaProjectionController {
    override fun consentIntent(): Intent =
        context.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent()
    override fun start(resultCode: Int, data: Intent, options: CaptureOptions) {
        val intent = Intent(context, ScreenCaptureService::class.java)
            .putExtra("code", resultCode).putExtra("consent", data)
            .putExtra("height", options.height).putExtra("fps", options.fps)
            .putExtra("hardware", options.hardware).putExtra("host", options.host).putExtra("token", options.token)
        ContextCompat.startForegroundService(context, intent)
    }
    override fun stop() { context.stopService(Intent(context, ScreenCaptureService::class.java)) }
}
