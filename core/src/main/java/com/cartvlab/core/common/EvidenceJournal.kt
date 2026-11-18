package com.cartvlab.core.common

import com.cartvlab.core.datastore.SettingsStore
import com.cartvlab.core.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Only structured, allowlisted events are stored. Never pass URLs, file names or exception messages. */
class EvidenceJournal(private val store: SettingsStore) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow<List<TestResult>>(emptyList())
    val results = mutable.asStateFlow()
    val renderStats = MutableStateFlow(RenderStats())
    suspend fun load() = mutex.withLock { mutable.value = decode(store.read("tests") ?: "[]") }
    suspend fun record(test: String, outcome: String, evidence: String, origin: String = "APP_OBSERVATION") = mutex.withLock {
        val result = TestResult(Instant.now().toString(), test, outcome, evidence, origin)
        mutable.value = (mutable.value + result).takeLast(100)
        store.write("tests", encode(mutable.value).toString())
    }
    fun encode(list: List<TestResult>): JSONArray = JSONArray().apply {
        list.forEach { put(JSONObject().put("timestamp", it.timestamp).put("test", it.test)
            .put("outcome", it.outcome).put("evidence", it.evidence).put("origin", it.origin)) }
    }
    private fun decode(raw: String): List<TestResult> = runCatching {
        val a = JSONArray(raw)
        (0 until a.length()).map { i -> a.getJSONObject(i).let {
            TestResult(it.getString("timestamp"), it.getString("test"), it.getString("outcome"), it.getString("evidence"), it.getString("origin"))
        } }
    }.getOrDefault(emptyList())
}
