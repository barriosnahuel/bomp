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
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
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
 * [CounterStore] backed by Jetpack DataStore Preferences with an in-memory cache. Same design as
 * [DataStoreFirstFlagStore]; rationale in docs/adr/0004-datastore-sync-api-cache-prime.md.
 *
 * One critical detail in [increment]: the disk write reads the latest cache value INSIDE the
 * `store.edit` block instead of capturing the `newValue` returned by `merge`. DataStore serialises
 * `edit` calls via an internal Mutex but does NOT guarantee FIFO ordering between concurrent
 * launches dispatched on `Dispatchers.IO` (a thread pool). If we captured `newValue = 1` outside
 * and another concurrent increment captured `newValue = 2`, the launches could land in inverted
 * order and write `2` then `1`, leaving the disk at `1` while the cache is at `2`. Reading the
 * cache inside `edit` ensures the latest value always wins under DataStore's serialisation
 * guarantee.
 */
class DataStoreCounterStore(
    context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : CounterStore {
    private val store: DataStore<Preferences> = context.applicationContext.analyticsCountersStore
    private val counters = ConcurrentHashMap<String, Long>()

    // ConcurrentHashMap.merge requires API 24 (minSdk = 23), so atomicity comes from a coarse
    // synchronized block on this lock. Increment frequency is low (one per play / share); the
    // contention cost is negligible for analytics.
    private val incrementLock = Any()

    init {
        runBlocking(Dispatchers.IO) {
            store.data.first().asMap().forEach { (key, value) ->
                if (value is Long) counters[key.name] = value
            }
        }
    }

    // Avoid ConcurrentHashMap.getOrDefault — it requires API 24 and minSdk is 23.
    override fun get(key: String): Long = counters[key] ?: 0L

    override fun increment(key: String): Long {
        val newValue =
            synchronized(incrementLock) {
                val next = (counters[key] ?: 0L) + 1L
                counters[key] = next
                next
            }
        scope.launch {
            // Write the latest cache value, NOT the captured `newValue`. See KDoc on this class.
            store.edit { it[longPreferencesKey(key)] = counters[key] ?: 0L }
        }
        return newValue
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    suspend fun clearForTest() {
        store.edit { it.clear() }
        counters.clear()
    }

    companion object {
        const val DATASTORE_NAME = "analytics-counters"
    }
}

private val Context.analyticsCountersStore: DataStore<Preferences> by preferencesDataStore(
    name = DataStoreCounterStore.DATASTORE_NAME,
    corruptionHandler =
        ReplaceFileCorruptionHandler { exception ->
            // Surface as a non-fatal so we can detect silent state loss in the field. `Timber.e`
            // alone goes through `ErrorTrackerTree` which drops the throwable — Crashlytics never
            // sees it. Wrap to give the dashboard entry a searchable title.
            Tracker.track(RuntimeException("Analytics counters DataStore corruption recovered", exception))
            emptyPreferences()
        },
)
