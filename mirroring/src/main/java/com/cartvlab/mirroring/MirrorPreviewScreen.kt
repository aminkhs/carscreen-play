package com.cartvlab.mirroring

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartvlab.core.designsystem.InfoCard
import com.cartvlab.core.model.Settings

@Composable fun MirrorPreviewScreen(controller: MediaProjectionController, settings: Settings) {
    val state by MirrorSessionManager.state.collectAsStateWithLifecycle()
    val bitmap by MirrorSessionManager.preview.collectAsStateWithLifecycle()
    var host by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var denied by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var networkDenied by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pending = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            denied = false
            runCatching { controller.start(result.resultCode, result.data!!,
                CaptureOptions(settings.mirrorHeight, settings.mirrorFps, settings.hardware, host.trim(), token)) }
                .onFailure { denied = true }
        } else denied = true
    }
    val notify = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        consent.launch(controller.consentIntent())
    }
    val network = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { networkDenied = false; notify.launch(Manifest.permission.POST_NOTIFICATIONS) }
        else { pending = false; networkDenied = true }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Screen mirroring", style = MaterialTheme.typography.headlineMedium)
        InfoCard("Independent capture", "Android Auto does not expose a verified generic mirroring receiver. This captures your phone for a local preview or the separate Lab Receiver app.")
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = "Live screen capture preview",
            modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) }
        if (state.active && host.isNotBlank()) Text("Streaming to receiver. Local preview is disabled while the encoder owns the capture surface.")
        Text(if (state.active) "LIVE · ${state.width} × ${state.height} · %.1f fps · ${state.frames} frames".format(state.fps) else "Capture stopped")
        if (state.encoder.isNotBlank()) Text("${state.encoder}\n${state.transport} · ${state.bitrate / 1000} kbps")
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (networkDenied) Text("Local-network permission is required for the selected receiver. Grant it and retry.", color = MaterialTheme.colorScheme.error)
        if (denied) Text("Capture consent was cancelled or capture could not start. No new session is running.", color = MaterialTheme.colorScheme.error)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !state.active && !pending && (host.isBlank() || token.length == 8), onClick = {
                pending = true
                if (host.isNotBlank() && Build.VERSION.SDK_INT >= 37 && context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED)
                    network.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
                else if (Build.VERSION.SDK_INT >= 33) notify.launch(Manifest.permission.POST_NOTIFICATIONS)
                else consent.launch(controller.consentIntent())
            }) { Text("Start capture") }
            OutlinedButton(enabled = state.active, onClick = { controller.stop() }) { Text("Stop") }
        }
        Text("For preview, select another app in the system consent picker. Capturing this app or the whole screen can create a recursive mirror.")
        InfoCard("Protected content", "DRM video and FLAG_SECURE windows may appear black. Android does not reliably report the cause of black frames. Protected content cannot be captured by this app.")
        Text("Optional Lab Receiver", style = MaterialTheme.typography.titleLarge)
        Text("Install the separate receiver APK on another Android device. Keep its receiver screen open and enter its private LAN IP and pairing code. Experimental TCP is video-only and unencrypted; use a trusted private network. Not a car integration.")
        OutlinedTextField(host, { host = it }, enabled = !state.active, label = { Text("Receiver IP · blank = local preview") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(token, { token = it.filter(Char::isDigit).take(8) }, enabled = !state.active, label = { Text("8-digit receiver code") }, modifier = Modifier.fillMaxWidth())
        Text("Target: ${settings.mirrorHeight}p short edge / ${settings.mirrorFps} fps. Actual rate is measured above; no latency guarantee.")
    }
}
