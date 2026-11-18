package com.cartvlab.diagnostics

import com.cartvlab.core.common.EvidenceJournal
/** Only allowlisted labels; intentionally does not accept arbitrary logcat or exception content. */
class DiagnosticLogger(private val journal: EvidenceJournal) {
    suspend fun event(debug: Boolean, event: Event) {
        if (debug) journal.record("DEBUG", "INFO", event.name)
    }
    enum class Event { APP_FOREGROUND, APP_BACKGROUND, DISPLAY_CHANGED, SETTINGS_SAVED }
}
