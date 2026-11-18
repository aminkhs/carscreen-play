@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
package com.cartvlab.player

import android.content.Intent
import android.Manifest
import android.os.Build
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.*
import androidx.media3.ui.*
import com.cartvlab.core.model.Settings

@Composable fun PlaybackScreen(
    repository: PlayerRepository, settings: Settings, fullscreen: Boolean, onFullscreen: (Boolean) -> Unit,
    open: (List<Uri>) -> Unit, url: (String) -> Unit, playlist: (Uri) -> Unit, subtitle: (Uri) -> Unit
) {
    val context = LocalContext.current
    val state by repository.state.collectAsStateWithLifecycle()
    var lanMessage by remember { mutableStateOf<String?>(null) }
    val networkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> lanMessage = if (granted) "Local-network streams allowed." else "Local-network access denied; public internet streams remain available." }
    var input by rememberSaveable { mutableStateOf("") }
    var aspect by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var showTracks by remember { mutableStateOf(false) }
    fun keep(uri: Uri) { runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) { uris.forEach { keep(it) }; open(uris) }
    }
    val m3u = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> keep(uri); playlist(uri) } }
    val subs = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> subtitle(uri) } }
    Column(Modifier.fillMaxSize().then(if (!fullscreen) Modifier.verticalScroll(rememberScrollState()) else Modifier)) {
        AndroidView(
            factory = { PlayerView(it).apply { player = repository.player; keepScreenOn = true; useController = true } },
            update = { it.player = repository.player; it.resizeMode = aspect; it.subtitleView?.setFractionalTextSize(0.0533f * settings.subtitleSize) },
            onRelease = { it.player = null },
            modifier = if (fullscreen) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().aspectRatio(16f / 9)
        )
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onFullscreen(!fullscreen) }) { Text(if (fullscreen) "Exit fullscreen" else "Fullscreen") }
            TextButton(onClick = { aspect = when (aspect) {
                AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            } }) { Text(when (aspect) { 4 -> "Crop"; 3 -> "Stretch"; else -> "Fit" }) }
            TextButton(onClick = { showTracks = true }) { Text("Audio") }
        }
        state.error?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            TextButton(onClick = { repository.player.prepare(); repository.player.play() }) { Text("Retry") }
        }
        if (!fullscreen) Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("PHONE PLAYER", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            Text("MP4 • MKV • HLS", style = MaterialTheme.typography.headlineSmall)
            Text("Codec support depends on this device. This screen is not projected to Android Auto.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { files.launch(arrayOf("video/*", "application/octet-stream")) }) { Text("Open videos") }
                OutlinedButton(onClick = { m3u.launch(arrayOf("*/*")) }) { Text("Import M3U") }
            }
            OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("HTTP / HTTPS video or HLS URL") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { url(input) }, enabled = input.isNotBlank()) { Text("Play URL") }
                OutlinedButton(onClick = { url(settings.testSource) }) { Text("Test URL") }
            }
            if (Build.VERSION.SDK_INT >= 37) TextButton(onClick = { networkPermission.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) }) { Text("Allow local-network streams") }
            lanMessage?.let { Text(it) }
            OutlinedButton(onClick = { open(listOf(Uri.parse("asset:///test-pattern.mp4"))) }) { Text("Bundled test") }
            OutlinedButton(onClick = { subs.launch(arrayOf("*/*")) }) { Text("Add SRT / WebVTT") }
            Text("External subtitles use ${settings.subtitleEncoding}. Persian language and size are configurable in Settings.")
            Text("Playlist · ${state.entries.size}", style = MaterialTheme.typography.titleMedium)
            state.entries.forEachIndexed { i, entry ->
                TextButton(onClick = { repository.player.seekToDefaultPosition(i); repository.player.play() }, modifier = Modifier.fillMaxWidth()) {
                    Text("${i + 1}. ${entry.title}")
                }
            }
        }
    }
    if (showTracks) AlertDialog(onDismissRequest = { showTracks = false }, title = { Text("Audio tracks") }, text = {
        Column {
            TextButton(onClick = {
                repository.player.trackSelectionParameters = repository.player.trackSelectionParameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
                showTracks = false
            }) { Text("Automatic") }
            repository.player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.forEach { group ->
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    TextButton(enabled = group.isTrackSupported(i), onClick = {
                        repository.player.trackSelectionParameters = repository.player.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i)).build()
                        showTracks = false
                    }) { Text("${format.label ?: format.language ?: "Audio"} · ${format.channelCount} channels") }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { showTracks = false }) { Text("Close") } })
}
