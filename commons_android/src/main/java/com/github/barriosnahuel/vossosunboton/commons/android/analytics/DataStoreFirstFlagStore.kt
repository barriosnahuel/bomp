/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * [FirstFlagStore] backed by Jetpack DataStore Preferences with an in-memory cache.
 *
 * Why the cache: the [FirstFlagStore] / [AnalyticsTracker] API is sync-from-caller because Firebase's
 * own `logEvent` is sync — the SDK queues events synchronously and persists them to disk on its own
 * worker thread, so the call returns before the caller can navigate away. Several of our analytics
 * call sites fire right before a context switch out of the app (share-sheet chooser, browser
 * intent, etc.); a `coroutineScope.launch { tracker.log(...) }` could lose the event because the
 * launch may not run before the OS suspends our process. Mirroring Firebase's design — sync API +
 * async-internal-buffer — preserves durability.
 *
 * Implementation:
 * - Constructor primes the in-memory map from DataStore once via `runBlocking(Dispatchers.IO)`.
 *   This is a one-time cost on first `AnalyticsTrackerProvider.get(context)` and lives inside the
 *   same `StrictMode.allowThreadDiskReads` block that already wraps Firebase init. The
 *   `MainApplication.onCreate` warm-up dispatches that init onto a background thread so the prime
 *   rarely blocks main in practice.
 * - Reads (`isFirstTime`) are pure in-memory `getOrDefault` — sync, lock-free.
 * - Writes update the [ConcurrentHashMap] atomically (`putIfAbsent`) and dispatch the disk write
 *   fire-and-forget on a process-lived [scope].
 *
 * Trade-off: a process kill between an in-memory write and the DataStore commit would lose the
 * `markFired` and re-emit the `first_*` variant on next launch. Acceptable for telemetry.
 */
class DataStoreFirstFlagStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : FirstFlagStore {
    private val store: DataStore<Preferences> = context.applicationContext.analyticsFlagsStore
    private val flags = ConcurrentHashMap<String, Boolean>()

    init {
        // One-time prime from disk. Subsequent reads are pure in-memory.
        runBlocking(Dispatchers.IO) {
            store.data.first().asMap().forEach { (key, value) ->
                if (value is Boolean) flags[key.name] = value
            }
        }
    }

    // Avoid ConcurrentHashMap.getOrDefault — it requires API 24 and minSdk is 23.
    override fun isFirstTime(event: String): Boolean = !(flags[event] ?: false)

    override fun markFired(event: String) {
        flags[event] = true
        scope.launch { store.edit { it[booleanPreferencesKey(event)] = true } }
    }

    override fun consumeFirstTime(event: String): Boolean {
        // putIfAbsent is the atomic gate that satisfies the FirstFlagStore KDoc contract:
        // exactly one concurrent caller sees `true` per install for each event.
        val previous = flags.putIfAbsent(event, true)
        if (previous != null) return false
        scope.launch { store.edit { it[booleanPreferencesKey(event)] = true } }
        return true
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun clearForTest() {
        store.edit { it.clear() }
        flags.clear()
    }

    companion object {
        const val DATASTORE_NAME = "analytics-flags"
    }
}

private val Context.analyticsFlagsStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreFirstFlagStore.DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
            Timber.e(exception, "Analytics flags DataStore corruption recovered with empty prefs")
            emptyPreferences()
        },
)
