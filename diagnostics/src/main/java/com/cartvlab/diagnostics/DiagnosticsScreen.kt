package com.cartvlab.diagnostics

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cartvlab.core.designsystem.InfoCard
import com.cartvlab.core.model.Capability

@Composable fun DiagnosticsScreen(facts: String, capabilities: List<Capability>, resultCount: Int,
    refresh: () -> Unit, export: (Uri, Boolean) -> Unit, record: (String, String) -> Unit, message: String?) {
    val json = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let { export(it, true) } }
    val txt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { it?.let { export(it, false) } }
    var selected by remember { mutableStateOf("Wired Android Auto connection") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Android Auto diagnostics", style = MaterialTheme.typography.headlineMedium)
        Text("Evidence before assumptions", color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = refresh) { Text("Refresh") }
            OutlinedButton(onClick = { json.launch("carscreen-play.json") }) { Text("Export JSON") }
            OutlinedButton(onClick = { txt.launch("carscreen-play.txt") }) { Text("TXT") }
        }
        message?.let { Text(it) }
        capabilities.forEach { InfoCard("${it.name}\n${it.status}", it.evidence) }
        InfoCard("Confirmed device facts", facts)
        Text("Physical test journal · $resultCount observations", style = MaterialTheme.typography.titleLarge)
        Text("Manual reports are labeled USER_OBSERVATION and never promote Android Auto compatibility to SUPPORTED_AND_TESTED. Do not enter secrets or personal file names.")
        val tests = listOf("Wired Android Auto connection", "Wireless Android Auto connection", "App present in car launcher",
            "Phone playback controls", "Persian subtitles", "Rotation and lifecycle", "Receiver video visible", "Capture teardown")
        tests.forEach { test ->
            Row {
                RadioButton(selected == test, onClick = { selected = test })
                Text(test, modifier = Modifier.padding(top = 12.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { record(selected, "PASS") }) { Text("Observed pass") }
            OutlinedButton(onClick = { record(selected, "FAIL") }) { Text("Observed fail") }
            TextButton(onClick = { record(selected, "UNKNOWN") }) { Text("Unknown") }
        }
    }
}
