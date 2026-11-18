package com.cartvlab.browser

import android.Manifest
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.cartvlab.core.designsystem.InfoCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun BrowserScreen(engine: BrowserEngine, state: BrowserState, fullscreen: View?,
    account: AccountBrowser, close: () -> Unit, restart: () -> Unit) {
    var address by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var clear by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val network = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        message = if (it) "Local-network access granted." else "Local-network access denied."
    }
    LaunchedEffect(state.url) { if (state.url != "about:blank") address = state.url }
    fun browse() { engine.open(address); keyboard?.hide() }
    fun youtube() {
        engine.pause()
        if (!account.openYouTube()) message = "No browser is available. Install or enable Chrome or another compatible browser."
    }
    BackHandler {
        when {
            fullscreen != null -> engine.exitFullscreen()
            state.canBack -> engine.back()
            else -> close()
        }
    }
    if (fullscreen != null) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { context -> FrameLayout(context).apply {
                (fullscreen.parent as? ViewGroup)?.removeView(fullscreen)
                addView(fullscreen, FrameLayout.LayoutParams(-1, -1))
            } }, onRelease = { it.removeAllViews() }, modifier = Modifier.fillMaxSize())
            TextButton(onClick = engine::exitFullscreen, modifier = Modifier.statusBarsPadding()) { Text("Exit fullscreen", color = Color.White) }
        }
    } else Scaffold(topBar = {
        TopAppBar(title = { Text("Browser & YouTube") }, navigationIcon = { TextButton(onClick = close) { Text("Close") } },
            actions = {
                TextButton(onClick = { menu = true }) { Text("More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Open in browser") }, onClick = {
                        menu = false; engine.pause()
                        if (!account.openWebsite(state.url.takeIf { BrowserUrlPolicy.isWebUrl(it) } ?: BrowserUrlPolicy.YOUTUBE))
                            message = "No browser is available on this device."
                    })
                    DropdownMenuItem(text = { Text("Screenshot") }, onClick = {
                        menu = false
                        val window = (context as? android.app.Activity)?.window
                        if (window != null) scope.launch {
                            kotlinx.coroutines.delay(300) // let the menu close first
                            val uri = com.cartvlab.core.common.Screenshots.captureAndSave(context, window, "Browser")
                            message = if (uri != null) "Screenshot saved to gallery (Pictures/CarScreen Play)." else "Screenshot failed."
                        }
                    })
                    DropdownMenuItem(text = { Text("Offline browser test") }, onClick = { menu = false; engine.open(BrowserUrlPolicy.HELP) })
                    if (Build.VERSION.SDK_INT >= 37) DropdownMenuItem(text = { Text("Allow local-network sites") },
                        onClick = { menu = false; network.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) })
                    DropdownMenuItem(text = { Text("Clear embedded browsing data") }, onClick = { menu = false; clear = true })
                }
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(address, { address = it }, modifier = Modifier.weight(1f), singleLine = true,
                    label = { Text("Website or search") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { browse() }))
                Button(onClick = ::browse, modifier = Modifier.padding(top = 8.dp), enabled = state.alive) { Text("Go") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = engine::back, enabled = state.canBack) { Text("Back") }
                TextButton(onClick = engine::forward, enabled = state.canForward) { Text("Next") }
                TextButton(onClick = engine::reload, enabled = state.hasPage) { Text("Reload") }
                TextButton(onClick = { engine.open(BrowserUrlPolicy.YOUTUBE_SIGN_IN) }) { Text("Sign in") }
            }
            if (state.progress < 100 && state.hasPage) LinearProgressIndicator(progress = { state.progress / 100f }, modifier = Modifier.fillMaxWidth())
            val notice = message ?: state.error ?: state.notice
            if (notice != null) Text(notice, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            if (!state.alive) {
                Button(onClick = restart, modifier = Modifier.padding(16.dp)) { Text("Restart browser") }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (state.hasPage) AndroidView(factory = { engine.view.apply { (parent as? ViewGroup)?.removeView(this) } },
                        modifier = Modifier.fillMaxSize().clipToBounds(), onRelease = { (it.parent as? ViewGroup)?.removeView(it) })
                    if (!state.hasPage) Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Watch. Browse. Sign in.", style = MaterialTheme.typography.headlineMedium)
                        Button(onClick = { engine.open(BrowserUrlPolicy.YOUTUBE_SIGN_IN) }, modifier = Modifier.fillMaxWidth()) { Text("Sign in to YouTube (shared with car screen)") }
                        OutlinedButton(onClick = { engine.open(BrowserUrlPolicy.YOUTUBE) }, modifier = Modifier.fillMaxWidth()) { Text("Open YouTube") }
                        OutlinedButton(onClick = ::youtube, modifier = Modifier.fillMaxWidth()) { Text("YouTube in Chrome tab (phone only)") }
                        InfoCard("One sign-in for phone and car", "Sign in to Google / YouTube here. This browser and the Android Auto car-screen browser share the same cookies, so the car is signed in too. Use Clear embedded browsing data to sign out.")
                        InfoCard("Android Auto", "On the car screen open CarScreen Play, tap Menu, then YouTube or Browser.")
                        TextButton(onClick = { engine.open(BrowserUrlPolicy.HELP) }) { Text("Offline browser test") }
                    }
                }
            }
        }
    }
    if (clear) AlertDialog(onDismissRequest = { clear = false }, title = { Text("Clear embedded browsing data?") },
        text = { Text("This removes this app's web cookies, cache and site storage. It does not sign you out of Chrome or your secure browser tab.") },
        confirmButton = { TextButton(onClick = { engine.clearData(); clear = false; message = null; address = "" }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clear = false }) { Text("Cancel") } })
}
