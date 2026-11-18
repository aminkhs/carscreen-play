package com.cartvlab.android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cartvlab.android.car.CarDisplay
import com.cartvlab.android.car.CarDisplayPrefs
import com.cartvlab.android.car.CarEntryPoint
import com.cartvlab.android.car.CarFit
import com.cartvlab.android.car.CarLayout
import com.cartvlab.android.car.SpeedGuard
import com.cartvlab.android.car.SpeedLockPolicy
import com.cartvlab.core.designsystem.InfoCard
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

private const val GEARHEAD = "com.google.android.projection.gearhead"
private const val PLAY_STORE = "com.android.vending"
private val SYSTEM_INSTALLERS = setOf("com.google.android.packageinstaller", "com.android.packageinstaller", PLAY_STORE)

/** How this APK was installed. Android Auto 17+ checks this before showing a Car App Library app. */
data class InstallSource(val installing: String?, val initiating: String?) {
    val looksLikePlay: Boolean get() = installing == PLAY_STORE && (initiating == null || initiating in SYSTEM_INSTALLERS)
}

fun installSource(context: Context): InstallSource = runCatching {
    val pm = context.packageManager
    if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(context.packageName).let { InstallSource(it.installingPackageName, it.initiatingPackageName) }
    else @Suppress("DEPRECATION") InstallSource(pm.getInstallerPackageName(context.packageName), null)
}.getOrDefault(InstallSource(null, null))

/** Car screen display settings, shared live with the running Android Auto session. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun CarDisplaySettings() {
    val context = LocalContext.current
    val store = remember { EntryPointAccessors.fromApplication(context.applicationContext, CarEntryPoint::class.java).store() }
    val display by CarDisplayPrefs.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { CarDisplayPrefs.load(store) }
    fun update(change: (CarDisplay) -> CarDisplay) { scope.launch { CarDisplayPrefs.update(store, change) } }

    @Composable fun <T> choices(title: String, options: List<T>, selected: T, label: (T) -> String, pick: (T) -> Unit) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option -> FilterChip(selected = option == selected, onClick = { pick(option) }, label = { Text(label(option)) }) }
        }
    }
    @Composable fun switch(title: String, text: String, checked: Boolean, change: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = change)
        }
    }

    Card {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Car screen display", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Changes apply on the car screen right away.", style = MaterialTheme.typography.bodySmall)
            choices("Video position", CarLayout.entries, display.layout, { it.label }) { v -> update { it.copy(layout = v) } }
            if (display.layout != CarLayout.FULL)
                choices("Video width", CarDisplay.PANEL_SIZES, display.panelPercent, { "$it%" }) { v -> update { it.copy(panelPercent = v) } }
            choices("Picture", CarFit.entries, display.fit, { it.label }) { v -> update { it.copy(fit = v) } }
            Text(display.fit.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (display.layout != CarLayout.FULL)
                switch("Browser on the other side", "Show a website (e.g. Google Maps) next to the video", display.browserBeside) { on -> update { it.copy(browserBeside = on) } }
            switch("Hide top buttons", "Frees the right side of the car screen; tap the ‹ arrow to bring them back", display.buttonsHidden) { on -> update { it.copy(buttonsHidden = on) } }
            val limit = SpeedLockPolicy.LIMIT_KMH.toInt()
            if (BuildConfig.DRIVING_LOCK) {
                Text("Driving lock: always on", style = MaterialTheme.typography.titleSmall)
                Text("Above $limit km/h (measured with GPS) the car screen shows only an audio icon; sound keeps playing. Needs location permission.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else switch("Driving lock", "Audio only above $limit km/h (GPS). Off by default in this personal build.", display.safetyLock) { on -> update { it.copy(safetyLock = on) } }
        }
    }
}

@Composable fun CarSetupScreen(openBrowser: () -> Unit) {
    val context = LocalContext.current
    val videoPermission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    var refresh by remember { mutableIntStateOf(0) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh++ }
    val autoVersion = remember { runCatching { context.packageManager.getPackageInfo(GEARHEAD, 0).versionName }.getOrNull() }
    val source = remember(refresh) { installSource(context) }
    val appName = remember { context.applicationInfo.loadLabel(context.packageManager).toString() }
    fun open(url: String) = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Android Auto setup", style = MaterialTheme.typography.headlineMedium)
        Text(if (autoVersion != null) "Android Auto $autoVersion is installed." else "Android Auto app was not found on this phone.",
            color = MaterialTheme.colorScheme.primary)

        // --- The check that decides whether Android Auto shows the app -----------------------------
        val ok = source.looksLikePlay
        Card(colors = CardDefaults.cardColors(containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (ok) "✓ Installed as a Google Play app" else "✗ Android Auto will hide this app",
                    style = MaterialTheme.typography.titleMedium)
                Text("Installer: ${source.installing ?: "unknown (sideloaded)"}\nStarted by: ${source.initiating ?: "unknown"}",
                    style = MaterialTheme.typography.bodySmall)
                Text(if (ok) "Android Auto accepts apps recorded as installed by Google Play. Enable Unknown sources too, then reconnect to the car."
                    else "The audio entry (media card next to the map) works with Unknown sources. The video screen does not: Android Auto 17 and newer hide sideloaded Car App Library apps even when \"Unknown sources\" is on. " +
                        "\"adb install -i com.android.vending\" no longer works either. Reinstall this APK with KingInstaller + Shizuku (steps below).")
                TextButton(onClick = { refresh++ }) { Text("Check again") }
            }
        }

        InfoCard("1. Install Shizuku",
            "Install \"Shizuku\" from Google Play and start it with Wireless debugging (follow the steps inside Shizuku; no root needed).")
        OutlinedButton(onClick = { open("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api") }, modifier = Modifier.fillMaxWidth()) {
            Text("Shizuku on Google Play")
        }
        InfoCard("2. Reinstall this app with KingInstaller",
            "Download KingInstaller from its official GitHub page. Open it, allow Shizuku, choose this app's APK file and install with the Shizuku method. " +
                "Your data is kept. Repeat this for every future update. Afterwards, this card should turn green.")
        OutlinedButton(onClick = { open("https://github.com/fcaronte/KingInstaller/releases") }, modifier = Modifier.fillMaxWidth()) {
            Text("KingInstaller on GitHub")
        }
        InfoCard("3. Android Auto settings",
            "In Android Auto settings tap \"Version\" 10 times to unlock developer mode. In ⋮ → Developer settings turn on \"Unknown sources\". " +
                "Then Settings → Apps → Android Auto → Force stop and Storage → Clear cache (Android Auto remembers rejected apps). Reconnect to the car.")
        Button(onClick = {
            runCatching {
                context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$GEARHEAD"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Android Auto app info (force stop / cache)") }
        InfoCard("4. Show it in the car launcher",
            "Android Auto settings → Customize launcher: tick \"$appName\". You may see it twice: the video screen and the media entry (audio card next to the map).")
        key(refresh) {
            val needed = listOf(videoPermission, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION, SpeedGuard.CAR_SPEED).filterNot(::granted)
            OutlinedButton(onClick = { request.launch(needed.toTypedArray()) }, enabled = needed.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(if (needed.isEmpty()) "Permissions granted ✓" else "Allow video library, location & car speed")
            }
        }
        InfoCard("5. Sign in to YouTube on the phone",
            "Sign in once in this app's browser. The car screen browser shares the same login.")
        OutlinedButton(onClick = openBrowser, modifier = Modifier.fillMaxWidth()) { Text("Open browser & sign in") }
        CarDisplaySettings()
        InfoCard("Next to the map (split screen)",
            "Android Auto draws the small panel beside the map itself and only allows music-style cards there, so no app can show moving video in it. Play a video, then open Google Maps: the card shows the video's sound, a still frame as the cover and play / pause / next.")
        InfoCard("Using it in the car",
            "Open $appName from the car launcher. Menu → Phone videos / YouTube / Browser. Tap the car screen to press buttons on the page; tap the pan (✥) button to scroll by dragging. Menu → Screenshot saves the car screen to your gallery.")
        InfoCard("Safety", if (BuildConfig.DRIVING_LOCK) "Above 30 km/h the video is hidden and only audio plays. Watch only while parked."
            else "This personal build has no driving lock unless you turn it on above. Never watch video while driving.")
    }
}
