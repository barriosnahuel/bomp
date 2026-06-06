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
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Persists the state of the home-grid welcome sticker (Sticker Cero, fresh-install variant).
 *
 * The welcome audio is **persistent** — it is treated as just-another-audio that the Bomper deletes
 * manually whenever they want. It no longer self-destructs on playback completion (feedback
 * v2.1.0 #1). Four pieces of state:
 * - `consumed`: flips to `true` **only** when the user manually deletes the welcome and lets the
 *   Undo snackbar time out. Once `true`, the welcome never reappears on this install.
 * - `acknowledged`: flips to `true` the first time the welcome plays to completion. Drives the
 *   one-shot informative snackbar AND is the hook a future "What's New" overwrite should gate on
 *   ("the Bomper already experienced the welcome"), decoupled from `consumed` (card removal).
 * - `hint_shown`: flips to `true` after the one-time swipe-hint nudge runs, so the card peeks the
 *   delete affordance only once per install.
 * - `install_ts`: the welcome's `dateAdded`, captured lazily on first read. Lets the welcome sort
 *   by date alongside the user's own audios (newest-first) instead of being force-pinned to row 0.
 *
 * Reads are race-protected by in-memory caches populated lazily on first read and updated
 * synchronously by the mutators. Without the cache, a mutate immediately followed by a reload could
 * observe stale disk state because the async `store.edit` may not have committed yet. Same shape as
 * the analytics stores in `commons_android`.
 *
 * Lives in its own DataStore Preferences file (`datastore/welcome-sticker.preferences_pb`) on
 * purpose, separate from the audio-metadata store. Backup posture: included in
 * `app_backup_rules.xml` and `app_data_extraction_rules.xml` — the user's "I dismissed this" gesture
 * is meaningful state that should survive a restore. `BackupRulesTest` enforces the include rules.
 */
class WelcomeStickerStore(
    context: Context,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val store: DataStore<Preferences> = context.applicationContext.welcomeStickerStore

    @Volatile private var consumedCache: Boolean? = null

    @Volatile private var acknowledgedCache: Boolean? = null

    @Volatile private var hintShownCache: Boolean? = null

    @Volatile private var installTsCache: Long? = null

    /**
     * `true` until the user has manually deleted the welcome sticker. Returns `false` when the
     * bundled resource is missing — matches the spec's "no storage for `resource_update` → silently
     * skip" stance.
     */
    suspend fun isActive(): Boolean {
        if (R.raw.app_welcome_sticker == 0) return false
        return !readConsumed()
    }

    /**
     * `true` once the welcome has played to completion at least once. The informative snackbar is
     * shown only on the transition to `true`; a future "What's New" overwrite gates on this rather
     * than on [isActive] so a persistent (never-deleted) welcome doesn't block update stickers.
     */
    suspend fun isAcknowledged(): Boolean {
        acknowledgedCache?.let { return it }
        val value = readFlag(KEY_ACKNOWLEDGED)
        acknowledgedCache = value
        return value
    }

    /** `true` once the one-time swipe-hint nudge has run. */
    suspend fun isHintShown(): Boolean {
        hintShownCache?.let { return it }
        val value = readFlag(KEY_HINT_SHOWN)
        hintShownCache = value
        return value
    }

    /** The welcome's stable `dateAdded`, captured on first read so ordering is deterministic. */
    suspend fun installTimestamp(): Long {
        installTsCache?.let { return it }
        val existing = withContext(Dispatchers.IO) { store.data.first()[KEY_INSTALL_TS] }
        val value =
            existing ?: now().also { ts ->
                withContext(Dispatchers.IO) { store.edit { it[KEY_INSTALL_TS] = ts } }
            }
        installTsCache = value
        return value
    }

    suspend fun consume() {
        // Update the in-memory cache before dispatching the disk write so a follow-up read observes
        // the new value immediately, regardless of when the `store.edit` commits.
        consumedCache = true
        withContext(Dispatchers.IO) {
            store.edit { it[KEY_CONSUMED] = true }
        }
    }

    /** Re-enables the welcome after a manual delete was undone within the snackbar window. */
    suspend fun restore() {
        consumedCache = false
        withContext(Dispatchers.IO) {
            store.edit { it[KEY_CONSUMED] = false }
        }
    }

    /**
     * One-time migration from the old "ephemeral, self-destruct on completion" model to the new
     * persistent one (feedback v2.1.0 #1). Under the old model, letting the welcome play to the end
     * set `consumed=true` and it vanished forever — many Bompers lost it without understanding why.
     * Since the old `consumed` is ambiguous (auto-destruct vs intentional delete, indistinguishable
     * on disk), we resurface it for everyone once: clear `consumed`, drop the orphaned `was_restored`
     * key, and set the guard so this never runs again — a deliberate manual delete afterwards sticks.
     * Precedent: `SoundsRepository.migrateVisibilityIfNeeded` (ADR 0012).
     */
    suspend fun migrateToPersistentIfNeeded() {
        if (readFlag(KEY_MIGRATED_PERSISTENT)) return
        consumedCache = false
        withContext(Dispatchers.IO) {
            store.edit {
                it[KEY_CONSUMED] = false
                it[KEY_MIGRATED_PERSISTENT] = true
                it.remove(LEGACY_KEY_WAS_RESTORED)
            }
        }
    }

    suspend fun markAcknowledged() {
        acknowledgedCache = true
        withContext(Dispatchers.IO) {
            store.edit { it[KEY_ACKNOWLEDGED] = true }
        }
    }

    suspend fun markHintShown() {
        hintShownCache = true
        withContext(Dispatchers.IO) {
            store.edit { it[KEY_HINT_SHOWN] = true }
        }
    }

    /**
     * Test-only escape hatch to reset state. Same `@VisibleForTesting(otherwise = NONE)` protection
     * as `SoundsRepository.clearForTest`.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun clearForTest() {
        consumedCache = null
        acknowledgedCache = null
        hintShownCache = null
        installTsCache = null
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

    private suspend fun readFlag(key: Preferences.Key<Boolean>): Boolean =
        withContext(Dispatchers.IO) {
            store.data.first()[key] ?: false
        }

    companion object {
        const val DATASTORE_NAME = "welcome-sticker"
        private val KEY_CONSUMED = booleanPreferencesKey("consumed")
        private val KEY_ACKNOWLEDGED = booleanPreferencesKey("acknowledged")
        private val KEY_HINT_SHOWN = booleanPreferencesKey("hint_shown")
        private val KEY_INSTALL_TS = longPreferencesKey("install_ts")
        private val KEY_MIGRATED_PERSISTENT = booleanPreferencesKey("migrated_persistent")

        // Legacy key from the old ephemeral model — only referenced to drop it during migration.
        private val LEGACY_KEY_WAS_RESTORED = booleanPreferencesKey("was_restored")
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
