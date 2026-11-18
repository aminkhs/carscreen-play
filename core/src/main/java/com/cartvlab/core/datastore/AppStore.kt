package com.cartvlab.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.cartvlab.core.model.Settings
import kotlinx.coroutines.flow.*
import java.io.IOException

private val Context.store by preferencesDataStore("android_auto_play")
interface SettingsStore {
    val settings: Flow<Settings>
    suspend fun update(settings: Settings)
    suspend fun reset()
    suspend fun position(key: String): Long
    suspend fun savePosition(key: String, position: Long)
    suspend fun read(key: String): String?
    suspend fun write(key: String, value: String)
}
class AppStore(private val context: Context) : SettingsStore {
    private val data = context.store.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }
    override val settings = data.map { p ->
        Settings(
            theme = p[stringPreferencesKey("theme")] ?: "Dark",
            quality = p[intPreferencesKey("quality")] ?: 1080,
            mirrorHeight = p[intPreferencesKey("mirrorHeight")] ?: 720,
            mirrorFps = p[intPreferencesKey("mirrorFps")] ?: 30,
            hardware = p[booleanPreferencesKey("hardware")] ?: true,
            subtitleLanguage = p[stringPreferencesKey("subtitleLanguage")] ?: "fa",
            subtitleSize = p[floatPreferencesKey("subtitleSize")] ?: 1f,
            subtitleEncoding = p[stringPreferencesKey("subtitleEncoding")] ?: "UTF-8",
            debug = p[booleanPreferencesKey("debug")] ?: false,
            car = p[stringPreferencesKey("car")] ?: "Not set",
            testSource = p[stringPreferencesKey("testSource")] ?: Settings().testSource
        )
    }
    override suspend fun update(settings: Settings) {
        context.store.edit { p ->
            p[stringPreferencesKey("theme")] = settings.theme
            p[intPreferencesKey("quality")] = settings.quality
            p[intPreferencesKey("mirrorHeight")] = settings.mirrorHeight
            p[intPreferencesKey("mirrorFps")] = settings.mirrorFps
            p[booleanPreferencesKey("hardware")] = settings.hardware
            p[stringPreferencesKey("subtitleLanguage")] = settings.subtitleLanguage
            p[floatPreferencesKey("subtitleSize")] = settings.subtitleSize
            p[stringPreferencesKey("subtitleEncoding")] = settings.subtitleEncoding
            p[booleanPreferencesKey("debug")] = settings.debug
            p[stringPreferencesKey("car")] = settings.car.take(100)
            p[stringPreferencesKey("testSource")] = settings.testSource
        }
    }
    override suspend fun reset() { update(Settings()) }
    override suspend fun position(key: String) = data.first()[longPreferencesKey("position_$key")] ?: 0L
    override suspend fun savePosition(key: String, position: Long) {
        context.store.edit { it[longPreferencesKey("position_$key")] = position.coerceAtLeast(0) }
    }
    override suspend fun read(key: String) = data.first()[stringPreferencesKey(key)]
    override suspend fun write(key: String, value: String) { context.store.edit { it[stringPreferencesKey(key)] = value } }
}
