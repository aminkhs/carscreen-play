@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.cartvlab.android.car

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * One ExoPlayer for the whole process, shared by the car screen (video) and the media service
 * (Android Auto media card next to the map). Switching between the two never interrupts playback.
 */
object SharedPlayer {
    private var instance: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val thumbnailed = mutableSetOf<String>()

    fun get(context: Context): ExoPlayer = instance ?: build(context.applicationContext).also { instance = it }

    private fun build(context: Context) =
        ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true)).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.let { addThumbnail(context, this@apply, it) }
                }
            })
        }

    /** Adds a video frame as cover art, so the Android Auto media card shows a picture of the current video. */
    private fun addThumbnail(context: Context, player: ExoPlayer, item: MediaItem) {
        val uri = item.localConfiguration?.uri ?: return
        if (item.mediaMetadata.artworkData != null || !thumbnailed.add(item.mediaId + uri)) return
        scope.launch {
            val jpeg = withContext(Dispatchers.IO) { frame(context, uri) } ?: return@launch
            val index = (0 until player.mediaItemCount).firstOrNull { player.getMediaItemAt(it).localConfiguration?.uri == uri } ?: return@launch
            val current = player.getMediaItemAt(index)
            // Same URI: Media3 updates the metadata without re-preparing, so playback continues.
            player.replaceMediaItem(index, current.buildUpon().setMediaMetadata(current.mediaMetadata.buildUpon()
                .setArtworkData(jpeg, MediaMetadata.PICTURE_TYPE_FRONT_COVER).build()).build())
        }
    }

    private fun frame(context: Context, uri: Uri): ByteArray? = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            when (uri.scheme) {
                "asset" -> context.assets.openFd(uri.path.orEmpty().trimStart('/')).use {
                    retriever.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                "http", "https" -> retriever.setDataSource(uri.toString(), emptyMap())
                else -> retriever.setDataSource(context, uri)
            }
            val bitmap = retriever.getScaledFrameAtTime(3_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, 480, 270)
                ?: return@runCatching null
            ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                bitmap.recycle()
                out.toByteArray()
            }
        } finally {
            retriever.release()
        }
    }.getOrNull()
}
