/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

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
 * Persists the last Vault filter chip the user selected. Same shape as `MySoundsFilterStore` —
 * an in-memory cache plus a DataStore write-back. The persisted value is either the canonical
 * collection id of a private collection or [ALL_SENTINEL] for the unfiltered view.
 *
 * Backed up (see `app_backup_rules.xml`) so a restored device lands on the same Vault filter chip,
 * matching the rest of the user's archive.
 */
class VaultFilterStore(
    context: Context,
) {
    private val store: DataStore<Preferences> = context.applicationContext.vaultFilterStore

    @Volatile private var cache: String? = null

    suspend fun get(): String {
        cache?.let { return it }
        val value =
            withContext(Dispatchers.IO) {
                store.data.first()[KEY_LAST_FILTER] ?: ALL_SENTINEL
            }
        cache = value
        return value
    }

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
        const val DATASTORE_NAME = "vault-filter"
        const val ALL_SENTINEL = "__all__"
        private val KEY_LAST_FILTER = stringPreferencesKey("last_filter_id")
    }
}

private val Context.vaultFilterStore: DataStore<Preferences> by preferencesDataStore(
    name = VaultFilterStore.DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
            Tracker.track(RuntimeException("Vault filter DataStore corruption recovered", exception))
            emptyPreferences()
        },
)
