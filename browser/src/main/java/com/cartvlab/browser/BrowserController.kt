package com.cartvlab.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.webkit.WebViewAssetLoader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class BrowserState(
    val url: String = "", val title: String = "Browser & YouTube", val progress: Int = 100,
    val canBack: Boolean = false, val canForward: Boolean = false, val hasPage: Boolean = false,
    val error: String? = null, val notice: String? = null, val alive: Boolean = true
)
interface BrowserEngine {
    val view: WebView
    val state: kotlinx.coroutines.flow.StateFlow<BrowserState>
    val fullscreen: kotlinx.coroutines.flow.StateFlow<View?>
    fun open(input: String)
    fun back()
    fun forward()
    fun reload()
    fun exitFullscreen()
    fun pause()
    fun resume()
    fun clearData()
    fun save(): Bundle?
    fun close()
}
@SuppressLint("SetJavaScriptEnabled")
class BrowserController(context: Context, saved: Bundle? = null) : BrowserEngine {
    private val mutable = MutableStateFlow(BrowserState())
    override val state = mutable.asStateFlow()
    private val custom = MutableStateFlow<View?>(null)
    override val fullscreen = custom.asStateFlow()
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var destroyed = false
    private var clearing = false
    private val assets = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
    override val view = WebView(context).apply {
        contentDescription = "Embedded browser"
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.safeBrowsingEnabled = true
        settings.mediaPlaybackRequiresUserGesture = true
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        WebSetup.apply(this)
        webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                assets.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (!BrowserUrlPolicy.isWebUrl(url)) {
                    if (request.isForMainFrame) mutable.update { it.copy(notice = "This link needs another app. Open this page in the secure browser tab if needed.") }
                    return true
                }
                return false
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                mutable.update { it.copy(url = url.orEmpty(), progress = 0, hasPage = url != "about:blank", error = null) }
            }
            override fun onPageFinished(view: WebView, url: String?) {
                if (clearing && url == "about:blank") { view.clearHistory(); clearing = false }
                mutable.update { it.copy(url = url.orEmpty(), title = view.title.orEmpty().take(120),
                    canBack = view.canGoBack(), canForward = view.canGoForward(), progress = 100) }
                CookieManager.getInstance().flush()
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) mutable.update { it.copy(progress = 100, error = "Page could not load. Check your internet connection and retry, or use the secure browser tab.") }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) mutable.update { it.copy(error = "Website returned HTTP ${response.statusCode}. You can retry or use the secure browser tab.") }
            }
            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                mutable.update { it.copy(error = "The site's security certificate could not be verified. Connection blocked.") }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                exitFullscreen()
                (view.parent as? ViewGroup)?.removeView(view)
                view.destroy()
                destroyed = true
                mutable.value = BrowserState(alive = false, error = "The web renderer stopped. Reopen the browser to continue.")
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, progress: Int) { mutable.update { it.copy(progress = progress) } }
            override fun onReceivedTitle(view: WebView, title: String?) { mutable.update { it.copy(title = title.orEmpty().take(120)) } }
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (custom.value != null) { callback.onCustomViewHidden(); return }
                customCallback = callback
                custom.value = view
            }
            override fun onHideCustomView() { exitFullscreen() }
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
        }
        setDownloadListener { _, _, _, _, _ ->
            mutable.update { it.copy(notice = "Downloads are not handled here. Use Open in browser for this page.") }
        }
    }
    init {
        if (saved != null) view.restoreState(saved)?.let { history ->
            val item = history.currentItem
            if (item != null) mutable.value = BrowserState(url = item.url.orEmpty(), title = item.title.orEmpty(),
                hasPage = item.url != "about:blank", canBack = view.canGoBack(), canForward = view.canGoForward())
        }
    }
    override fun open(input: String) {
        if (destroyed) return
        runCatching { BrowserUrlPolicy.normalize(input) }.onSuccess { url ->
            mutable.update { it.copy(error = null, notice = null) }; view.loadUrl(url)
        }.onFailure { mutable.update { it.copy(error = "Enter a valid HTTP/HTTPS address or search. Script and file URLs are blocked.") } }
    }
    override fun back() { if (!destroyed && view.canGoBack()) view.goBack() }
    override fun forward() { if (!destroyed && view.canGoForward()) view.goForward() }
    override fun reload() { if (!destroyed) { mutable.update { it.copy(error = null) }; view.reload() } }
    override fun exitFullscreen() {
        custom.value?.let { (it.parent as? ViewGroup)?.removeView(it) }
        custom.value = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }
    override fun pause() {
        if (destroyed) return
        exitFullscreen()
        view.evaluateJavascript("document.querySelectorAll('video,audio').forEach(function(m){m.pause();});", null)
        view.onPause()
        CookieManager.getInstance().flush()
    }
    override fun resume() { if (!destroyed) view.onResume() }
    override fun save(): Bundle? = if (destroyed) null else Bundle().also { view.saveState(it) }
    override fun clearData() {
        if (destroyed) return
        clearing = true
        view.stopLoading()
        view.loadUrl("about:blank")
        view.clearHistory()
        view.clearCache(true)
        WebStorage.getInstance().deleteAllData()
        CookieManager.getInstance().removeAllCookies { CookieManager.getInstance().flush() }
        mutable.value = BrowserState(notice = "Embedded browser cookies and storage cleared. Your secure browser tab account is managed separately by your browser.")
    }
    override fun close() {
        if (destroyed) return
        pause()
        view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
        destroyed = true
    }
    companion object {
        const val ACCOUNT_NOTICE = "Google sign-in uses the secure browser tab. Tap YouTube account to sign in and watch there. Its account is separate from this embedded browser."
    }
}
