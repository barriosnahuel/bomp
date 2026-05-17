/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Persists the last public-collection filter chip the user selected on My Sounds. Spec § 7
 * "Comportamiento del FilterChip activo en cold start" — defaulted to "persist" because it favors
 * users of a single context (e.g. someone who lives in Recetas).
 *
 * The value is the collection id, or [ALL_SENTINEL] for the unfiltered "All" chip. A null read
 * collapses to [ALL_SENTINEL] so a fresh install lands on the unfiltered list.
 *
 * Implementation mirrors [com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore]:
 * own DataStore Preferences file, in-memory cache + async write-back. Backup-excluded per spec § 5.
 */
class MySoundsFilterStore(
    context: Context,
) {
    private val store: DataStore<Preferences> = context.applicationContext.mySoundsFilterStore

    @Volatile private var cache: String? = null

    /** Returns the persisted filter id, or [ALL_SENTINEL] when no preference is stored. */
    suspend fun get(): String {
        cache?.let { return it }
        val value =
            withContext(Dispatchers.IO) {
                store.data.first()[KEY_LAST_FILTER] ?: ALL_SENTINEL
            }
        cache = value
        return value
    }

    /**
     * Persists [filterId]. [ALL_SENTINEL] is stored as a literal so a later read sees the same
     * thing the writer set (vs writing null and reading back the sentinel default — same effect
     * but harder to debug in a DataStore inspector dump).
     */
    suspend fun set(filterId: String) {
        cache = filterId
        withContext(Dispatchers.IO) {
            store.edit { it[KEY_LAST_FILTER] = filterId }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun clearForTest() {
        cache = null
        withContext(Dispatchers.IO) {
            store.edit { it.clear() }
        }
    }

    companion object {
        const val DATASTORE_NAME = "my-sounds-filter"

        /** Stable sentinel for "no specific collection — show all my sounds." */
        const val ALL_SENTINEL = "__all__"

        private val KEY_LAST_FILTER = stringPreferencesKey("last_filter_id")
    }
}

private val Context.mySoundsFilterStore: DataStore<Preferences> by preferencesDataStore(
    name = MySoundsFilterStore.DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
            Tracker.track(RuntimeException("MySounds filter DataStore corruption recovered", exception))
            emptyPreferences()
        },
)
