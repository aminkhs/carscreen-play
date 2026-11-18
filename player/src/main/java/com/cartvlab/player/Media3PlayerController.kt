@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.cartvlab.player

import com.cartvlab.core.common.readUpTo

import android.content.Context
import android.net.Uri
import androidx.media3.common.*
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.cartvlab.core.common.EvidenceJournal
import com.cartvlab.core.datastore.SettingsStore
import com.cartvlab.core.model.Settings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

class Media3PlayerController(
    private val context: Context, private val store: SettingsStore, private val journal: EvidenceJournal
) : PlayerRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    override val player = ExoPlayer.Builder(context, androidx.media3.exoplayer.DefaultRenderersFactory(context).setEnableDecoderFallback(true)).build().apply {
        setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
        setHandleAudioBecomingNoisy(true)
    }
    private val session = MediaSession.Builder(context, player).setId(java.util.UUID.randomUUID().toString()).build()
    private val mutable = MutableStateFlow(PlaybackState())
    override val state = mutable.asStateFlow()
    private var settings = Settings()
    private var opened = false
    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                mutable.update { it.copy(ready = playbackState == Player.STATE_READY, error = if (playbackState == Player.STATE_READY) null else it.error) }
            }
            override fun onRenderedFirstFrame() {
                scope.launch { journal.record("VIDEO_FIRST_FRAME", "PASS", "Media3 rendered a frame on the phone player surface.") }
            }
            override fun onPlayerError(error: PlaybackException) {
                mutable.update { it.copy(error = "Playback failed (${error.errorCodeName}). Check network access, file permissions and device codec support; then Retry.") }
                scope.launch { journal.record("MEDIA_PLAYBACK", "FAIL", error.errorCodeName) }
            }
        })
    }
    private fun key(uri: String) = MessageDigest.getInstance("SHA-256").digest(uri.toByteArray()).joinToString("") { "%02x".format(it) }
    override suspend fun save() {
        player.currentMediaItem?.localConfiguration?.uri?.toString()?.let {
            store.savePosition(key(it), player.currentPosition)
            store.write("currentIndex", player.currentMediaItemIndex.toString())
        }
    }
    private suspend fun install(entries: List<PlaylistEntry>, autoPlay: Boolean, index: Int = 0) {
        save()
        require(entries.isNotEmpty()) { "No supported entries. Local playlist paths require individually selected SAF documents." }
        opened = true
        val start = index.coerceIn(entries.indices)
        val position = store.position(key(entries[start].uri))
        player.setMediaItems(entries.map { MediaItem.Builder().setUri(it.uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(it.title).build()).build() }, start, position)
        mutable.value = PlaybackState(entries)
        player.prepare()
        player.playWhenReady = autoPlay
        store.write("playlist", JSONArray().apply { entries.forEach { put(JSONObject().put("uri", it.uri).put("title", it.title)) } }.toString())
    }
    override suspend fun restore() {
        if (opened) return
        runCatching {
            val a = JSONArray(store.read("playlist") ?: "[]")
            val entries = (0 until a.length()).map { a.getJSONObject(it).let { PlaylistEntry(it.getString("uri"), it.getString("title")) } }
            if (entries.isNotEmpty()) install(entries, false, store.read("currentIndex")?.toIntOrNull() ?: 0)
        }.onFailure { mutable.update { s -> s.copy(error = "Saved playlist unavailable. Select a file again.") } }
    }
    private suspend fun guarded(block: suspend () -> Unit) {
        try { block() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { mutable.update { it.copy(error = when (e) {
            is SecurityException -> "Document access expired. Select the file again."
            is IllegalArgumentException -> e.message ?: "Unsupported input."
            else -> "Unable to open the selected source. Check its format and access."
        }) } }
    }
    override suspend fun open(uris: List<Uri>) = guarded {
        install(uris.mapIndexed { i, uri -> PlaylistEntry(uri.toString(), "Local video ${i + 1}") }, true)
    }
    override suspend fun openUrl(url: String) = guarded {
        require(Uri.parse(url.trim()).scheme?.lowercase() in setOf("https", "http")) { "Enter an HTTP or HTTPS video/HLS URL." }
        install(listOf(PlaylistEntry(url.trim(), "Network video")), true)
    }
    override suspend fun importPlaylist(uri: Uri) = guarded {
        val entries = withContext(Dispatchers.IO) {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readUpTo(2_000_001) } ?: error("Unavailable")
            require(bytes.size <= 2_000_000) { "Playlist exceeds 2 MB" }
            PlaylistParser.parse(bytes.toString(Charsets.UTF_8))
        }
        install(entries, true)
    }
    override suspend fun subtitle(uri: Uri) = guarded {
        val current = player.currentMediaItem
        require(current != null) { "Open a video before adding subtitles." }
        val config = withContext(Dispatchers.IO) { SubtitleManager(context).load(uri, settings.subtitleEncoding, settings.subtitleLanguage) }
        val position = player.currentPosition
        val playing = player.playWhenReady
        player.replaceMediaItem(player.currentMediaItemIndex, current.buildUpon().setSubtitleConfigurations(listOf(config)).build())
        player.seekTo(position)
        player.playWhenReady = playing
    }
    override fun configure(settings: Settings) {
        this.settings = settings
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, settings.quality)
            .setPreferredTextLanguage(settings.subtitleLanguage).build()
    }
    override fun pause() { player.pause() }
    override fun release() { session.release(); player.release(); scope.cancel() }
}
