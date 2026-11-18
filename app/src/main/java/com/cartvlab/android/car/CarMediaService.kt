@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.cartvlab.android.car

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.cartvlab.android.R
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking

// ---- Phone video library (shared by the car screen and the media service) ------------------------

fun videoPermission(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE

fun hasVideoPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, videoPermission()) == PackageManager.PERMISSION_GRANTED

/** Videos on the phone (camera, downloads, etc.), newest first. Empty without permission. */
fun queryPhoneVideos(context: Context, limit: Int): List<CarVideo> {
    if (!hasVideoPermission(context)) return emptyList()
    val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME)
    val result = mutableListOf<CarVideo>()
    runCatching {
        context.contentResolver.query(uri, projection, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val name = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            while (c.moveToNext() && result.size < limit) {
                result += CarVideo(ContentUris.withAppendedId(uri, c.getLong(id)).toString(), c.getString(name) ?: "Video")
            }
        }
    }
    return result
}

const val TEST_VIDEO_URI = "asset:///test-pattern.mp4"

/**
 * Android Auto media app. Its "now playing" card appears in the small panel next to the map,
 * like music apps, with the current video's audio, a frame of the video as cover art and controls.
 * Android Auto draws that card itself, so it cannot show moving video.
 */
class CarMediaService : MediaLibraryService() {
    private var session: MediaLibrarySession? = null

    override fun onCreate() {
        super.onCreate()
        session = MediaLibrarySession.Builder(this, SharedPlayer.get(this), LibraryCallback(this)).setId("carscreen-play").build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = session

    override fun onDestroy() {
        // The player is shared with the car screen; only the session is released here.
        session?.release()
        session = null
        super.onDestroy()
    }

    private class LibraryCallback(private val context: Context) : MediaLibrarySession.Callback {
        private val icon: Uri = Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
            .authority(context.packageName).appendPath(context.resources.getResourceTypeName(R.drawable.ic_car_folder))
            .appendPath(context.resources.getResourceEntryName(R.drawable.ic_car_folder)).build()

        private fun folder(id: String, title: String, subtitle: String) = MediaItem.Builder().setMediaId(id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setSubtitle(subtitle).setArtworkUri(icon)
                .setIsBrowsable(true).setIsPlayable(false).setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_VIDEOS).build()).build()

        private fun root() = folder(ROOT, context.applicationInfo.loadLabel(context.packageManager).toString(), "Videos")

        private fun children(parentId: String): List<MediaItem> = when (parentId) {
            ROOT -> listOf(
                folder(VIDEOS, "Phone videos", "Gallery, Downloads, camera"),
                folder(PLAYLIST, "App playlist", "Videos opened in the phone app"),
                CarVideo(TEST_VIDEO_URI, "Test video").toMediaItem()
            )
            VIDEOS -> if (hasVideoPermission(context)) queryPhoneVideos(context, 200).map { it.toMediaItem() }
                else listOf(MediaItem.Builder().setMediaId(NEED_PERMISSION).setMediaMetadata(MediaMetadata.Builder()
                    .setTitle("Allow video access in the phone app").setSubtitle("Android Auto Setup → Allow video library")
                    .setIsBrowsable(false).setIsPlayable(false).build()).build())
            PLAYLIST -> playlist().map { it.toMediaItem() }
            else -> emptyList()
        }

        private fun playlist(): List<CarVideo> = runCatching {
            val store = EntryPointAccessors.fromApplication(context.applicationContext, CarEntryPoint::class.java).store()
            val json = runBlocking { store.read("playlist") } ?: "[]"
            val a = org.json.JSONArray(json)
            (0 until a.length()).map { a.getJSONObject(it).let { o -> CarVideo(o.getString("uri"), o.getString("title")) } }
        }.getOrDefault(emptyList())

        override fun onGetLibraryRoot(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            params: LibraryParams?): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(LibraryResult.ofItem(root(), params))

        override fun onGetChildren(session: MediaLibrarySession, browser: MediaSession.ControllerInfo, parentId: String,
            page: Int, pageSize: Int, params: LibraryParams?): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val all = children(parentId)
            if (parentId != ROOT) lastFolder = all.filter { it.mediaMetadata.isPlayable == true }
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(all.subList(from, to)), params))
        }

        override fun onGetItem(session: MediaLibrarySession, browser: MediaSession.ControllerInfo,
            mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
            val item = when (mediaId) {
                ROOT -> root()
                VIDEOS, PLAYLIST -> children(ROOT).first { it.mediaId == mediaId }
                else -> CarVideo(mediaId, "Video").toMediaItem()
            }
            return Futures.immediateFuture(LibraryResult.ofItem(item, null))
        }

        /** Last listed folder, so picking one video queues the rest of that folder after it. */
        private var lastFolder: List<MediaItem> = emptyList()

        private fun resolve(item: MediaItem): MediaItem =
            lastFolder.firstOrNull { it.mediaId == item.mediaId } ?: CarVideo(item.mediaId,
                item.mediaMetadata.title?.toString() ?: Uri.parse(item.mediaId).lastPathSegment ?: "Video").toMediaItem()

        /** Android Auto sends items back by id only; the id is the video URI. Folders and hints are not playable. */
        override fun onAddMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>): ListenableFuture<MutableList<MediaItem>> =
            Futures.immediateFuture(mediaItems.filter { it.mediaId !in NOT_PLAYABLE }.map(::resolve).toMutableList())

        override fun onSetMediaItems(mediaSession: MediaSession, controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val single = mediaItems.singleOrNull()
            val folderIndex = single?.let { s -> lastFolder.indexOfFirst { it.mediaId == s.mediaId } } ?: -1
            val result = if (folderIndex >= 0) MediaSession.MediaItemsWithStartPosition(lastFolder, folderIndex, startPositionMs)
                else MediaSession.MediaItemsWithStartPosition(mediaItems.filter { it.mediaId !in NOT_PLAYABLE }.map(::resolve),
                    startIndex.coerceAtLeast(0), startPositionMs)
            return Futures.immediateFuture(result)
        }
    }

    private companion object {
        const val ROOT = "root"
        const val VIDEOS = "videos"
        const val PLAYLIST = "playlist"
        const val NEED_PERMISSION = "need-permission"
        val NOT_PLAYABLE = setOf(ROOT, VIDEOS, PLAYLIST, NEED_PERMISSION)
    }
}
