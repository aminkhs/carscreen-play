package com.cartvlab.settings

import com.cartvlab.core.datastore.SettingsStore
import com.cartvlab.core.model.Settings

class SettingsRepository(private val store: SettingsStore) {
    val settings = store.settings
    suspend fun update(value: Settings) = store.update(value)
    suspend fun reset() = store.reset()
}
