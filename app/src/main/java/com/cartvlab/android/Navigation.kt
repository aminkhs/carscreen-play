package com.cartvlab.android

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.cartvlab.diagnostics.DiagnosticsScreen
import com.cartvlab.mirroring.MirrorPreviewScreen
import com.cartvlab.player.PlaybackScreen
import com.cartvlab.settings.SettingsScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Navigation(model: LabViewModel, initial: String) {
    var screen by rememberSaveable { mutableStateOf(initial) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val settings by model.settings.collectAsStateWithLifecycle()
    val facts by model.facts.collectAsStateWithLifecycle()
    val capabilities by model.capabilities.collectAsStateWithLifecycle()
    val results by model.journal.results.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    val activity = context as Activity
    val dark = settings.theme == "Dark" || (settings.theme == "System" && isSystemInDarkTheme())
    DisposableEffect(fullscreen, dark) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
        activity.window.isNavigationBarContrastEnforced = false
        if (fullscreen) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
    fun go(destination: String) {
        if (screen == "Watch Videos" && destination != screen) { model.player.pause() }
        screen = destination
    }
    BackHandler(screen != "Home" || fullscreen) { if (fullscreen) fullscreen = false else go("Home") }
    val patternLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result -> if (result.resultCode == Activity.RESULT_OK) go("Watch Videos") }
    fun pattern() { patternLauncher.launch(Intent(context, TestDisplayActivity::class.java)) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(screen) { if (screen == "Diagnostics" || screen == "Vehicle Connection Status") model.refresh() }
    Scaffold(
        topBar = { if (!fullscreen) TopAppBar(title = { Text(androidx.compose.ui.res.stringResource(R.string.app_name)) },
            navigationIcon = { if (screen != "Home") TextButton(onClick = { go("Home") }) { Text("Back") } },
            actions = { TextButton(onClick = {
                scope.launch {
                    val uri = com.cartvlab.core.common.Screenshots.captureAndSave(context, activity.window, "CarScreenPlay")
                    android.widget.Toast.makeText(context, if (uri != null) "Screenshot saved to gallery (Pictures/CarScreen Play)" else "Screenshot failed",
                        android.widget.Toast.LENGTH_SHORT).show()
                }
            }) { Text("Screenshot") } }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).background(MaterialTheme.colorScheme.background)) {
            when (screen) {
                "Home" -> Dashboard { if (it == "Test Display") pattern() else if (it == "YouTube & Browser") context.startActivity(Intent(context, com.cartvlab.browser.BrowserActivity::class.java)) else go(it) }
                "Watch Videos" -> PlaybackScreen(model.player, settings, fullscreen, { fullscreen = it }, model::open, model::url, model::playlist, model::subtitle)
                "Screen Mirroring" -> MirrorPreviewScreen(model.projection, settings)
                "Diagnostics", "Vehicle Connection Status" -> DiagnosticsScreen(facts, capabilities, results.size, model::refresh, model::export, model::record, message)
                "Android Auto Setup" -> CarSetupScreen { context.startActivity(Intent(context, com.cartvlab.browser.BrowserActivity::class.java)) }
                "Settings" -> SettingsScreen(settings, model::save, model::reset, { go("Diagnostics") })
            }
        }
    }
}
@Composable private fun Dashboard(open: (String) -> Unit) {
    LazyVerticalGrid(columns = GridCells.Adaptive(260.dp), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("VIDEO & YOUTUBE\nON YOUR CAR SCREEN", style = MaterialTheme.typography.displaySmall)
                Text("Phone player + Android Auto car screen", color = MaterialTheme.colorScheme.primary)
                Text("Open \"Android Auto Setup\" once to make the app appear in Android Auto.")
            }
        }
        val cards = listOf(
            Triple("00", "Android Auto Setup", "Show this app on the car screen"),
            Triple("01", "Watch Videos", "Local files, streams and subtitles"),
            Triple("02", "YouTube & Browser", "Web video and secure account sign-in"),
            Triple("03", "Screen Mirroring", "Consent-based capture and preview"),
            Triple("04", "Diagnostics", "Android Auto versions and evidence"),
            Triple("05", "Vehicle Connection Status", "Observable facts and unknowns"),
            Triple("06", "Test Display", "Phone rendering and frame counter"),
            Triple("08", "Settings", "Playback, capture and reports")

        )
        items(cards) { (number, title, subtitle) ->
            Card(onClick = { open(title) }, modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(number, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Text(if (title == "Diagnostics") "Android Auto Diagnostics" else title, style = MaterialTheme.typography.titleLarge)
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
