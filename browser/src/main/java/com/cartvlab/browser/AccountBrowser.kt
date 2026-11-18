package com.cartvlab.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabColorSchemeParams

interface AccountBrowser { fun openYouTube(): Boolean; fun openWebsite(url: String): Boolean }
class AndroidAccountBrowser(private val context: Context) : AccountBrowser {
    override fun openYouTube() = openWebsite(BrowserUrlPolicy.YOUTUBE)
    override fun openWebsite(url: String): Boolean {
        if (!BrowserUrlPolicy.isWebUrl(url)) return false
        return runCatching {
            val provider = CustomTabsClient.getPackageName(context, null)
            val tab = CustomTabsIntent.Builder().setShowTitle(true)
                .setDefaultColorSchemeParams(CustomTabColorSchemeParams.Builder().setToolbarColor(0xFF191F25.toInt()).build())
                .setColorScheme(CustomTabsIntent.COLOR_SCHEME_DARK).build()
            if (provider != null) tab.intent.setPackage(provider)
            else {
                // Resolve a generic web address to a browser, not the YouTube app.
                val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)
                val info = context.packageManager.resolveActivity(browser, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
                if (info != null && info.activityInfo.packageName != "android") tab.intent.setPackage(info.activityInfo.packageName) else return false
            }
            tab.launchUrl(context, Uri.parse(url))
            true
        }.getOrDefault(false)
    }
}
