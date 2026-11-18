package com.cartvlab.player

import com.cartvlab.core.common.readUpTo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.media3.common.*
import java.io.File
import java.nio.charset.Charset

class SubtitleManager(private val context: Context) {
    fun load(uri: Uri, encoding: String, language: String): MediaItem.SubtitleConfiguration {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else ""
        }.orEmpty()
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readUpTo(2_000_001) }
            ?: error("Subtitle document is unavailable")
        require(bytes.size <= 2_000_000) { "Subtitle exceeds 2 MB" }
        val text = bytes.toString(Charset.forName(encoding)).removePrefix("\uFEFF")
        val webvtt = name.endsWith(".vtt", true) || text.trimStart().startsWith("WEBVTT")
        require(webvtt || text.contains("-->")) { "Choose a valid SRT or WebVTT subtitle" }
        val directory = File(context.cacheDir, "subtitles").apply { mkdirs() }
        val file = File.createTempFile("subtitle-", if (webvtt) ".vtt" else ".srt", directory)
        file.writeText(text, Charsets.UTF_8)
        return MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(file))
            .setMimeType(if (webvtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP)
            .setLanguage(language).setLabel("External subtitle")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
    }
}
