package com.elendheim.notes.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class SortMode { EDITED, CREATED, ALPHA }

private val Context.dataStore by preferencesDataStore(name = "settings")

class Prefs(private val context: Context) {

    private val appLockKey = booleanPreferencesKey("app_lock")

    val appLock: Flow<Boolean> = context.dataStore.data.map { it[appLockKey] ?: false }

    suspend fun setAppLock(enabled: Boolean) {
        context.dataStore.edit { it[appLockKey] = enabled }
    }

    fun sort(listKey: String): Flow<SortMode> =
        context.dataStore.data.map { prefs ->
            prefs[stringPreferencesKey("sort_$listKey")]
                ?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
                ?: SortMode.EDITED
        }

    suspend fun setSort(listKey: String, mode: SortMode) {
        context.dataStore.edit { it[stringPreferencesKey("sort_$listKey")] = mode.name }
    }
}

fun sortNotes(notes: List<Note>, mode: SortMode): List<Note> {
    val comparator = when (mode) {
        SortMode.EDITED -> compareByDescending<Note> { it.updatedAt }
        SortMode.CREATED -> compareByDescending<Note> { it.createdAt }
        SortMode.ALPHA -> compareBy<Note> { it.title.isBlank() }
            .thenBy { it.title.lowercase() }
    }
    return notes.sortedWith(compareByDescending<Note> { it.pinned }.then(comparator))
}
