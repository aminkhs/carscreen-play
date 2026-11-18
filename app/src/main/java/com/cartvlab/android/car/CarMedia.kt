@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.cartvlab.android.car

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.cartvlab.browser.BrowserUrlPolicy
import com.cartvlab.browser.WebSetup
import org.json.JSONObject

data class CarVideo(val uri: String, val title: String) {
    /** Playable item; mediaId = URI so the media service can resolve items Android Auto sends back by id. */
    fun toMediaItem(): MediaItem = MediaItem.Builder().setMediaId(uri).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setIsPlayable(true).setIsBrowsable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO).build()).build()
}

/** PlayerView on the car screen, driving the process-wide [SharedPlayer] (also used by the media card). */
class CarVideoPlayer(context: Context, private val changed: () -> Unit) {
    val player: ExoPlayer = SharedPlayer.get(context)
    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) { changed() }
        override fun onPlayerError(error: PlaybackException) { lastError = error.errorCodeName; changed() }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { lastError = null; changed() }
    }
    init { player.addListener(listener) }
    var lastError: String? = null
        private set
    private var view: PlayerView? = null
    var resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
        private set

    fun view(ctx: Context): PlayerView = view ?: PlayerView(ctx).also {
        it.player = player
        it.useController = true
        it.controllerShowTimeoutMs = 3500
        it.controllerHideOnTouch = true
        it.setShowNextButton(true)
        it.setShowPreviousButton(true)
        it.setShowSubtitleButton(true)
        it.keepScreenOn = true
        it.resizeMode = resizeMode
        view = it
    }

    fun play(items: List<CarVideo>, index: Int = 0, startMs: Long = 0) {
        if (items.isEmpty()) return
        lastError = null
        player.setMediaItems(items.map { it.toMediaItem() }, index.coerceIn(items.indices), startMs)
        player.prepare()
        player.play()
    }

    fun toggle() { if (player.isPlaying) player.pause() else { if (player.playbackState == Player.STATE_IDLE) player.prepare(); player.play() } }
    fun seekBy(ms: Long) { player.seekTo((player.currentPosition + ms).coerceAtLeast(0)) }
    fun setResizeMode(mode: Int) {
        resizeMode = mode
        view?.resizeMode = mode
    }
    val title: String get() = player.mediaMetadata.title?.toString().orEmpty()
    /** Car session ended: detach from the screen. The shared player stays alive for the media card. */
    fun release() {
        player.removeListener(listener)
        view?.player = null
        view = null
    }
}

/** WebView for the car screen. Shares cookies (YouTube / Google sign-in) with the phone browser. */
@SuppressLint("SetJavaScriptEnabled")
class CarBrowser(context: Context, private val fullscreen: (View?) -> Unit, private val changed: () -> Unit) {
    var desktop = false
        private set
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    val web: WebView = WebView(context).apply {
        WebSetup.apply(this, desktop = false, autoplay = true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.safeBrowsingEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        keepScreenOn = true
        isFocusable = true
        isFocusableInTouchMode = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !BrowserUrlPolicy.isWebUrl(request.url.toString())
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { changed() }
            override fun onPageFinished(view: WebView, url: String?) { CookieManager.getInstance().flush(); changed() }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) { handler.cancel() }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                crashed = true; changed(); return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customCallback?.onCustomViewHidden()
                customCallback = callback
                fullscreen(view)
            }
            override fun onHideCustomView() { exitFullscreen() }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
        }
    }
    var crashed = false
        private set

    fun open(input: String) {
        val url = runCatching { BrowserUrlPolicy.normalize(input) }.getOrNull() ?: return
        exitFullscreen()
        web.loadUrl(url)
    }
    fun youtubeSearch(query: String) =
        open("https://m.youtube.com/results?search_query=" + java.net.URLEncoder.encode(query, "UTF-8"))
    fun back() { if (customCallback != null) exitFullscreen() else if (web.canGoBack()) web.goBack() }
    fun reload() = web.reload()
    fun zoom(factor: Float) { web.zoomBy(factor.coerceIn(0.5f, 2f)) }
    fun exitFullscreen() {
        val cb = customCallback ?: return
        customCallback = null
        fullscreen(null)
        cb.onCustomViewHidden()
    }
    fun setDesktopMode(on: Boolean) {
        desktop = on
        WebSetup.apply(web, desktop = on, autoplay = true)
        web.reload()
    }
    fun pauseMedia() { web.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null) }

    /** Types text into the focused field of the page (works for Persian text too), optionally pressing Enter. */
    fun typeText(text: String, enter: Boolean) {
        val js = """
            (function(t){
              var e=document.activeElement;
              if(!e||e===document.body){return false;}
              if(e.isContentEditable){document.execCommand('insertText',false,t);return true;}
              var proto=Object.getPrototypeOf(e);var d=Object.getOwnPropertyDescriptor(proto,'value');
              var v=(e.value||'')+t; if(d&&d.set){d.set.call(e,v);}else{e.value=v;}
              e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}));
              return true;
            })(${JSONObject.quote(text)})
        """.trimIndent()
        web.evaluateJavascript(js) { if (enter) pressEnter() }
    }
    fun clearField() {
        web.evaluateJavascript("""(function(){var e=document.activeElement;if(!e)return;var d=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(e),'value');if(d&&d.set){d.set.call(e,'');}else{e.value='';}e.dispatchEvent(new Event('input',{bubbles:true}));})()""", null)
    }
    fun pressEnter() {
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
        web.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
    }
    val title: String get() = web.title.orEmpty()
    fun release() {
        exitFullscreen()
        pauseMedia()
        (web.parent as? ViewGroup)?.removeView(web)
        web.destroy()
    }
}
