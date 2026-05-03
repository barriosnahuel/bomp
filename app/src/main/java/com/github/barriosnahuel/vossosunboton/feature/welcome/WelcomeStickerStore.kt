/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.welcome

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Persists the visibility of the home-grid welcome sticker (Sticker Cero, fresh-install variant).
 *
 * Two booleans:
 * - `consumed`: flips to `true` when the user lets the dismiss snackbar time out (or, after a
 *   restored welcome, when they manually dismiss again). Once `true`, the welcome never reappears
 *   on this install.
 * - `was_restored`: sticky `true` after the user taps Undo at least once. The next render demotes
 *   the welcome from row 0 to the END of MY_SOUNDS so the prime spot belongs to the user.
 *
 * Both reads are race-protected by an in-memory cache populated lazily on first read and updated
 * synchronously by [consume] / [restore]. Without the cache, an Undo immediately followed by a tab
 * switch could re-prepend the welcome at row 0 because the next [wasRestored] would observe stale
 * disk state (the async `store.edit` may not have committed yet). Same shape as the analytics
 * stores in `commons_android`.
 *
 * Lives in its own DataStore Preferences file (`datastore/welcome-sticker.preferences_pb`) on
 * purpose:
 * - The audio metadata DataStore (`bomps.preferences_pb`) holds user-created Bomps and is also
 *   backed up but the data shape is different (JSON-encoded list).
 * - The analytics flags / counters live in their own DataStores with different backup postures.
 *
 * Backup posture: included in `app_backup_rules.xml` and `app_data_extraction_rules.xml` — the
 * user's "I dismissed this" gesture is meaningful state that should survive a restore.
 * `BackupRulesTest` enforces the include rules.
 */
class WelcomeStickerStore(
    context: Context,
) {
    private val store: DataStore<Preferences> = context.applicationContext.welcomeStickerStore

    @Volatile private var consumedCache: Boolean? = null

    @Volatile private var wasRestoredCache: Boolean? = null

    /**
     * `true` until the user has consumed the welcome sticker. Returns `false` when the bundled
     * resource is missing — matches the spec's "no storage for `resource_update` → silently skip"
     * stance.
     */
    suspend fun isActive(): Boolean {
        if (R.raw.app_welcome_sticker == 0) return false
        return !readConsumed()
    }

    /**
     * `true` after the user has tapped Undo on the dismiss snackbar at least once. Used by
     * `SoundsViewModel.loadSounds()` to demote the welcome from row 0 to the end of MY_SOUNDS:
     * the prime spot belongs to the user once they've shown they want this sticker back rather
     * than letting it consume.
     */
    suspend fun wasRestored(): Boolean = readWasRestored()

    suspend fun consume() {
        // Update the in-memory cache before dispatching the disk write so a follow-up read
        // observes the new value immediately, regardless of when the `store.edit` commits.
        consumedCache = true
        withContext(Dispatchers.IO) {
            store.edit { it[KEY_CONSUMED] = true }
        }
    }

    /**
     * Re-enables the welcome AND records the restore atomically. The two effects are coupled: an
     * Undo by definition means "user was restored at least once", and from that moment on the
     * welcome is no longer eligible for row 0.
     */
    suspend fun restore() {
        consumedCache = false
        wasRestoredCache = true
        withContext(Dispatchers.IO) {
            store.edit {
                it[KEY_CONSUMED] = false
                it[KEY_WAS_RESTORED] = true
            }
        }
    }

    /**
     * Test-only escape hatch to reset state. Same `@VisibleForTesting(otherwise = NONE)`
     * protection as `SoundsRepository.clearForTest`.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun clearForTest() {
        consumedCache = null
        wasRestoredCache = null
        withContext(Dispatchers.IO) {
            store.edit { it.clear() }
        }
    }

    private suspend fun readConsumed(): Boolean {
        consumedCache?.let { return it }
        val value = readFlag(KEY_CONSUMED)
        consumedCache = value
        return value
    }

    private suspend fun readWasRestored(): Boolean {
        wasRestoredCache?.let { return it }
        val value = readFlag(KEY_WAS_RESTORED)
        wasRestoredCache = value
        return value
    }

    private suspend fun readFlag(key: Preferences.Key<Boolean>): Boolean =
        withContext(Dispatchers.IO) {
            store.data.first()[key] ?: false
        }

    companion object {
        const val DATASTORE_NAME = "welcome-sticker"
        private val KEY_CONSUMED = booleanPreferencesKey("consumed")
        private val KEY_WAS_RESTORED = booleanPreferencesKey("was_restored")
    }
}

private val Context.welcomeStickerStore: DataStore<Preferences> by preferencesDataStore(
    name = WelcomeStickerStore.DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
            // Surface as a non-fatal so we can detect silent state loss in the field. `Timber.e`
            // alone goes through `ErrorTrackerTree` which drops the throwable — Crashlytics never
            // sees it. Wrap to give the dashboard entry a searchable title.
            Tracker.track(RuntimeException("Welcome sticker DataStore corruption recovered", exception))
            emptyPreferences()
        },
)
