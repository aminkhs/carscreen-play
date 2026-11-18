package com.cartvlab.browser

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object BrowserUrlPolicy {
    const val YOUTUBE = "https://m.youtube.com/"
    const val YOUTUBE_SIGN_IN = "https://accounts.google.com/ServiceLogin?service=youtube&passive=true&continue=https%3A%2F%2Fm.youtube.com%2F"
    const val HELP = "https://appassets.androidplatform.net/assets/browser-help.html"
    fun normalize(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty() && value.length <= 8192) { "Enter a website address or search." }
        val hasScheme = Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(value)
        val candidate = when {
            hasScheme -> value
            value.contains(' ') || (!value.contains('.') && !value.startsWith("localhost")) ->
                "https://www.google.com/search?q=" + URLEncoder.encode(value, "UTF-8")
            else -> "https://$value"
        }
        require(isWebUrl(candidate)) { "Only HTTP and HTTPS websites are supported. Local files and script URLs are blocked." }
        return URI(candidate).toASCIIString()
    }
    fun isWebUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)
    fun requiresAccountBrowser(value: String): Boolean = runCatching {
        URI(value).host?.lowercase() in setOf("accounts.google.com", "accounts.youtube.com")
    }.getOrDefault(false)
}
