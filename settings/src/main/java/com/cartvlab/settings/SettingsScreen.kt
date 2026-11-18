package com.cartvlab.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cartvlab.core.model.Settings

@Composable fun SettingsScreen(value: Settings, save: (Settings) -> Unit, reset: () -> Unit, diagnostics: () -> Unit) {
    var car by remember(value.car) { mutableStateOf(value.car) }
    var source by remember(value.testSource) { mutableStateOf(value.testSource) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Choice("Theme", listOf("Dark", "Light", "System"), value.theme) { save(value.copy(theme = it)) }
        Choice("Playback quality ceiling", listOf("480", "720", "1080", "2160"), value.quality.toString()) { save(value.copy(quality = it.toInt())) }
        Choice("Mirroring short edge", listOf("360", "480", "720", "1080"), value.mirrorHeight.toString()) { save(value.copy(mirrorHeight = it.toInt())) }
        Choice("Mirroring target FPS", listOf("10", "15", "24", "30"), value.mirrorFps.toString()) { save(value.copy(mirrorFps = it.toInt())) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Prefer hardware capture encoder", modifier = Modifier.weight(1f))
            Switch(value.hardware, { save(value.copy(hardware = it)) })
        }
        Text("Encoder settings apply to the next receiver session. Local preview uses an RGBA surface; Media3 uses device decoders automatically.")
        Choice("Subtitle language", listOf("fa", "en", "ar"), value.subtitleLanguage) { save(value.copy(subtitleLanguage = it)) }
        Choice("Subtitle encoding", listOf("UTF-8", "windows-1256"), value.subtitleEncoding) { save(value.copy(subtitleEncoding = it)) }
        Text("Subtitle size")
        Slider(value.subtitleSize, { save(value.copy(subtitleSize = it)) }, valueRange = 0.7f..1.6f)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Structured debug events"); Switch(value.debug, { save(value.copy(debug = it)) })
        }
        OutlinedTextField(car, { car = it.take(100) }, label = { Text("Vehicle model (user provided)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(source, { source = it }, label = { Text("Configurable test video URL") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { save(value.copy(car = car, testSource = source)) }) { Text("Save vehicle and source") }
        OutlinedButton(onClick = diagnostics) { Text("Export diagnostic report") }
        TextButton(onClick = reset) { Text("Reset settings") }
        Text("HTTP sources are supported and are unencrypted. Use HTTPS where available. No personal filenames, stream URLs, pairing codes or captured frames are exported.")
    }
}
@Composable private fun Choice(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { FilterChip(selected == it, onClick = { onSelect(it) }, label = { Text(it) }) }
    }
}
