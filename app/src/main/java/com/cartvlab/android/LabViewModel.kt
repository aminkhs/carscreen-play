package com.cartvlab.android

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cartvlab.core.common.EvidenceJournal
import com.cartvlab.core.model.*
import com.cartvlab.diagnostics.*
import com.cartvlab.mirroring.*
import com.cartvlab.player.PlayerRepository
import com.cartvlab.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@HiltViewModel class LabViewModel @Inject constructor(
    val player: PlayerRepository, val projection: MediaProjectionController,
    private val repository: SettingsRepository, val journal: EvidenceJournal,
    private val exporter: ReportExporter
) : ViewModel() {
    val settings = repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    val facts = MutableStateFlow("Reading device facts…")
    val capabilities = MutableStateFlow<List<Capability>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    var displayId = 0
    init {
        viewModelScope.launch {
            journal.load()
            journal.record("APP_STARTUP", "PASS", "Application ViewModel initialized; this does not verify physical Android 17 or vehicle operation.")
            player.restore()
            refresh()
        }
        viewModelScope.launch { settings.collect { player.configure(it) } }
        viewModelScope.launch {
            var reported = false
            var lastError: String? = null
            var previousActive = false
            MirrorSessionManager.state.collect { state ->
                if (state.active && state.frames > 0 && !reported) {
                    reported = true
                    journal.record("CAPTURE_FRAMES", "PASS", "MediaProjection delivered capture output on this phone. Receiver visibility and protected content are not verified.")
                }
                if (!state.active) reported = false
                if (state.error != null && state.error != lastError) {
                    journal.record("CAPTURE_ERROR", "FAIL", requireNotNull(state.error))
                    lastError = state.error
                }
                if (previousActive && !state.active) journal.record("CAPTURE_STOP", "PASS", "Capture worker completed teardown.")
                previousActive = state.active
            }
        }
        viewModelScope.launch {
            while (isActive) { delay(5000); player.save() }
        }
    }
    fun refresh() {
        facts.value = exporter.json(settings.value, displayId).getJSONObject("confirmedFacts").toString(2)
        capabilities.value = exporter.capabilities()
    }
    fun open(uris: List<Uri>) { viewModelScope.launch { player.open(uris) } }
    fun url(value: String) { viewModelScope.launch { player.openUrl(value) } }
    fun playlist(uri: Uri) { viewModelScope.launch { player.importPlaylist(uri) } }
    fun subtitle(uri: Uri) { viewModelScope.launch { player.subtitle(uri) } }
    fun save(value: Settings) { viewModelScope.launch {
        repository.update(value)
        DiagnosticLogger(journal).event(value.debug, DiagnosticLogger.Event.SETTINGS_SAVED)
    } }
    fun reset() { viewModelScope.launch { repository.reset() } }
    fun export(uri: Uri, json: Boolean) { viewModelScope.launch {
        runCatching { exporter.export(uri, json, settings.value, displayId) }
            .onSuccess { message.value = "Diagnostic report exported." }
            .onFailure { message.value = "Could not write report. Choose a writable document location." }
    } }
    fun record(test: String, outcome: String) { viewModelScope.launch {
        journal.record(test, outcome, "Manually reported by the operator; not independently verified by the application.", "USER_OBSERVATION")
        refresh()
        message.value = "Saved manual observation."
    } }
    fun foreground() { viewModelScope.launch { DiagnosticLogger(journal).event(settings.value.debug, DiagnosticLogger.Event.APP_FOREGROUND) } }
    fun background() {
        player.pause()
        viewModelScope.launch {
            player.save()
            DiagnosticLogger(journal).event(settings.value.debug, DiagnosticLogger.Event.APP_BACKGROUND)
        }
    }
    override fun onCleared() { player.release() }
}
