package com.flick.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "flick_settings")

class SettingsStore(private val context: Context) {
    private val viewKey = stringPreferencesKey("view_mode")
    private val sortKey = stringPreferencesKey("sort_mode")
    private val hapticsKey = booleanPreferencesKey("haptics")

    val viewMode: Flow<ViewMode> = context.dataStore.data.map { p ->
        ViewMode.entries.firstOrNull { it.name == p[viewKey] } ?: ViewMode.GRID
    }
    val sortMode: Flow<SortMode> = context.dataStore.data.map { p ->
        SortMode.entries.firstOrNull { it.name == p[sortKey] } ?: SortMode.RECENT
    }
    val haptics: Flow<Boolean> = context.dataStore.data.map { it[hapticsKey] ?: true }

    suspend fun setViewMode(mode: ViewMode) {
        context.dataStore.edit { it[viewKey] = mode.name }
    }

    suspend fun setSortMode(mode: SortMode) {
        context.dataStore.edit { it[sortKey] = mode.name }
    }

    suspend fun setHaptics(on: Boolean) {
        context.dataStore.edit { it[hapticsKey] = on }
    }
}
