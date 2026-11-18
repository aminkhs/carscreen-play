package com.cartvlab.mirroring

import android.graphics.Bitmap
import com.cartvlab.core.model.CaptureState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object MirrorSessionManager {
    internal val mutable = MutableStateFlow(CaptureState())
    val state = mutable.asStateFlow()
    internal val image = MutableStateFlow<Bitmap?>(null)
    val preview = image.asStateFlow()
}
data class CaptureOptions(val height: Int = 720, val fps: Int = 30, val hardware: Boolean = true,
    val host: String = "", val token: String = "")
