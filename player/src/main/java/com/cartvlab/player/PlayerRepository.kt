package com.cartvlab.player

import android.net.Uri
import androidx.media3.common.Player
import com.cartvlab.core.model.Settings
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(val entries: List<PlaylistEntry> = emptyList(), val error: String? = null, val ready: Boolean = false)
interface PlayerRepository {
    val player: Player
    val state: StateFlow<PlaybackState>
    suspend fun restore()
    suspend fun open(uris: List<Uri>)
    suspend fun openUrl(url: String)
    suspend fun importPlaylist(uri: Uri)
    suspend fun subtitle(uri: Uri)
    fun configure(settings: Settings)
    suspend fun save()
    fun pause()
    fun release()
}
