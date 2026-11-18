package com.cartvlab.player

import java.net.URI

data class PlaylistEntry(val uri: String, val title: String)
object PlaylistParser {
    /** SAF grants cannot be inferred for relative file paths. Remote relative paths need a base URL. */
    fun parse(text: String, baseUrl: String? = null): List<PlaylistEntry> {
        require(text.length <= 2_000_000) { "Playlist exceeds 2 MB" }
        require(!text.contains("#EXT-X-")) { "This is HLS: open its URL as a video instead of importing it as a playlist." }
        var title: String? = null
        return buildList {
            text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
                val line = raw.trim()
                if (line.startsWith("#EXTINF:")) title = line.substringAfter(',', "").trim().take(160)
                else if (line.isNotEmpty() && !line.startsWith("#")) {
                    val uri = runCatching { URI(line) }.getOrNull()
                    val resolved = if (uri?.isAbsolute == true) uri
                        else if (baseUrl != null) runCatching { URI(baseUrl).resolve(line) }.getOrNull() else null
                    if (resolved?.scheme?.lowercase() in setOf("http", "https", "content")) {
                        add(PlaylistEntry(resolved.toString(), title?.takeIf { it.isNotBlank() } ?: "Video ${size + 1}"))
                    }
                    title = null
                    require(size <= 1000) { "Playlist exceeds 1000 entries" }
                }
            }
        }
    }
}
