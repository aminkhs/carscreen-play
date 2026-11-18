package com.cartvlab.diagnostics

import android.content.Context
import android.net.Uri
import com.cartvlab.automotive.*
import com.cartvlab.core.common.EvidenceJournal
import com.cartvlab.core.model.*
import com.cartvlab.mirroring.MirrorSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.*
import java.time.Instant

class ReportExporter(
    private val context: Context, private val device: DeviceInfoRepository, private val permissions: PermissionInspector,
    private val automotive: AndroidAutoCapabilityRepository, private val connection: CarConnectionDiagnostics,
    private val journal: EvidenceJournal
) {
    fun capabilities(): List<Capability> = automotive.capabilities().map { capability ->
        val test = when (capability.name) {
            "Phone video playback" -> journal.results.value.lastOrNull { it.test == "VIDEO_FIRST_FRAME" || it.test == "MEDIA_PLAYBACK" }
            "MediaProjection capture" -> journal.results.value.lastOrNull { it.test == "CAPTURE_FRAMES" || it.test == "CAPTURE_ERROR" }
            else -> null
        }
        if (test == null || test.origin != "APP_OBSERVATION") capability
        else capability.copy(status = if (test.outcome == "PASS") CapabilityStatus.SUPPORTED_AND_TESTED else CapabilityStatus.TEST_FAILED,
            evidence = "${test.timestamp}: ${test.evidence} Limited to this observed phone session; does not verify all formats or Android Auto.")
    }
    fun json(settings: Settings, displayId: Int): JSONObject {
        val capture = MirrorSessionManager.state.value
        val render = journal.renderStats.value
        return JSONObject()
            .put("schemaVersion", 1).put("timestamp", Instant.now().toString())
            .put("confirmedFacts", device.facts(displayId).put("permissions", permissions.inspect()))
            .put("userProvided", JSONObject().put("carModel", settings.car))
            .put("observedConnectionState", connection.observableConnection())
            .put("actualTestResults", journal.encode(journal.results.value))
            .put("capabilityMatrix", JSONArray().apply { capabilities().forEach { put(JSONObject()
                .put("capability", it.name).put("status", it.status.name).put("evidence", it.evidence)) } })
            .put("mediaProjection", JSONObject().put("active", capture.active).put("frames", capture.frames)
                .put("width", capture.width).put("height", capture.height).put("observedFps", capture.fps)
                .put("encoder", capture.encoder).put("bitrate", capture.bitrate).put("transport", capture.transport)
                .put("error", capture.error ?: JSONObject.NULL))
            .put("renderingPerformance", JSONObject().put("scope", "PHONE_TEST_PATTERN")
                .put("frames", render.frames).put("fps", render.fps).put("width", render.width).put("height", render.height))
            .put("inferences", JSONArray(listOf("A visible package does not establish an active connection.",
                "An external display does not establish Android Auto eligibility.")))
            .put("unknownInformation", JSONArray(listOf("Head-unit wired/wireless capability", "Vehicle motion",
                "Successful car-display launch", "End-to-end receiver latency", "Private partner eligibility")))
            .put("recommendations", JSONArray(listOf("Test phone playback and projection separately.",
                "Record wired and wireless observations while safely parked.", "Recheck Google category documentation before enabling any car entry.")))
            .put("documentationReviewed", "2026-11-16")
    }
    fun text(settings: Settings, displayId: Int): String {
        val report = json(settings, displayId)
        return buildString {
            appendLine("ANDROID AUTO PLAY — DIAGNOSTIC REPORT")
            appendLine("Timestamp: ${report.getString("timestamp")}")
            appendLine("Vehicle (user entered): ${settings.car}")
            appendLine("\nCONFIRMED FACTS")
            appendLine(report.getJSONObject("confirmedFacts").toString(2))
            appendLine("\nCAPABILITIES")
            capabilities().forEach { appendLine("${it.name}: ${it.status}\n  ${it.evidence}") }
            appendLine("\nOBSERVED TEST RESULTS (origin identifies manual reports)")
            appendLine(report.getJSONArray("actualTestResults").toString(2))
            for (section in listOf("mediaProjection", "renderingPerformance", "inferences", "unknownInformation", "recommendations"))
                appendLine("\n$section\n${report.get(section)}")
        }
    }
    suspend fun export(uri: Uri, asJson: Boolean, settings: Settings, displayId: Int) = withContext(Dispatchers.IO) {
        val report = if (asJson) json(settings, displayId).toString(2) else text(settings, displayId)
        context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { it.write(report) }
            ?: error("Unable to write diagnostic report")
    }
}
