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
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * One-shot "seen" flag for the dual-home coach mark (ADR 0012).
 *
 * The coach snackbar — "Lo vas a encontrar en los dos lados" — fires the **first** time a user
 * closes the assign sheet with an audio that is both visible in My Sounds AND tagged to a private
 * collection. It teaches the additive model (a Bomp can live in the botonera *and* the Vault) once;
 * after that this flag suppresses it forever.
 *
 * Single boolean, race-protected by an in-memory cache populated on first read and updated
 * synchronously by [markSeen] — same shape as [WelcomeStickerStore]. Backup posture: included in
 * `app_backup_rules.xml` + `app_data_extraction_rules.xml` (asserted by `BackupRulesTest`), so a
 * restored device does not re-teach a coach the user already dismissed.
 */
class DualHomeCoachStore(
    context: Context,
) {
    private val store: DataStore<Preferences> = context.applicationContext.dualHomeCoachStore

    @Volatile private var seenCache: Boolean? = null

    suspend fun hasSeen(): Boolean {
        seenCache?.let { return it }
        val value = withContext(Dispatchers.IO) { store.data.first()[KEY_SEEN] ?: false }
        seenCache = value
        return value
    }

    suspend fun markSeen() {
        // Cache first, so a follow-up read observes the new value before the disk write commits.
        seenCache = true
        withContext(Dispatchers.IO) {
            store.edit { it[KEY_SEEN] = true }
        }
    }

    /** Test-only reset. Same `@VisibleForTesting(otherwise = NONE)` protection as the sibling stores. */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun clearForTest() {
        seenCache = null
        withContext(Dispatchers.IO) {
            store.edit { it.clear() }
        }
    }

    companion object {
        const val DATASTORE_NAME = "dual-home-coach"
        private val KEY_SEEN = booleanPreferencesKey("seen")
    }
}

private val Context.dualHomeCoachStore: DataStore<Preferences> by preferencesDataStore(
    name = DualHomeCoachStore.DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
            Tracker.track(RuntimeException("Dual home coach DataStore corruption recovered", exception))
            emptyPreferences()
        },
)
