package com.cartvlab.browser

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Shared WebView configuration for the phone browser and the Android Auto car-screen browser.
 * Both run in the same process, so cookies (YouTube / Google sign-in) are shared between them:
 * sign in once on the phone and the car screen is signed in too.
 */
object WebSetup {
    private val VERSION_TOKEN = Regex("""Version/\d+(\.\d+)* """)
    private val CHROME_TOKEN = Regex("""Chrome/[\d.]+""")

    /** Default WebView UA without the "; wv" WebView marker, i.e. a normal mobile Chrome UA. */
    fun mobileUserAgent(default: String): String = default.replace("; wv", "").replace(VERSION_TOKEN, "")

    fun desktopUserAgent(default: String): String {
        val chrome = CHROME_TOKEN.find(default)?.value ?: "Chrome/140.0.0.0"
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chrome Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun apply(view: WebView, desktop: Boolean = false, autoplay: Boolean = false) {
        val s = view.settings
        val default = WebSettings.getDefaultUserAgent(view.context)
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.mediaPlaybackRequiresUserGesture = !autoplay
        s.userAgentString = if (desktop) desktopUserAgent(default) else mobileUserAgent(default)
        s.useWideViewPort = true
        s.loadWithOverviewMode = desktop
        if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST))
            WebSettingsCompat.setRequestedWithHeaderOriginAllowList(s, emptySet())
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, true)
    }
}
