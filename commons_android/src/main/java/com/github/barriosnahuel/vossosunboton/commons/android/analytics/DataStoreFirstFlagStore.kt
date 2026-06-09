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
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap

/**
 * [FirstFlagStore] backed by Jetpack DataStore Preferences with an in-memory cache. Reads
 * (`isFirstTime`) are pure in-memory `getOrDefault` — sync, lock-free; writes (`markFired`) update
 * the [ConcurrentHashMap] atomically (`putIfAbsent`) and dispatch the disk write fire-and-forget on
 * the process-lived [scope]. The constructor primes the map once via `runBlocking(Dispatchers.IO)`
 * inside `AnalyticsTrackerProvider`'s `StrictMode.allowThreadDiskReads` block.
 *
 * Why a sync API + cache (not suspend reads), why the `runBlocking` boot-time prime, and the
 * process-kill durability trade-off: docs/adr/0004-datastore-sync-api-cache-prime.md.
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
            // Surface as a non-fatal so we can detect silent state loss in the field. `Timber.e`
            // alone goes through `ErrorTrackerTree` which drops the throwable — Crashlytics never
            // sees it. Wrap to give the dashboard entry a searchable title.
            Tracker.track(RuntimeException("Analytics flags DataStore corruption recovered", exception))
            emptyPreferences()
        },
)
