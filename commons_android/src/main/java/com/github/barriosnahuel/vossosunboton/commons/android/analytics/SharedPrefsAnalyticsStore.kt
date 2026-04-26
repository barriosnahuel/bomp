/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.content.Context
import android.content.SharedPreferences

/**
 * Combined [FirstFlagStore] + [CounterStore] backed by a dedicated `SharedPreferences` file. Kept separate from the
 * production audio-metadata prefs to isolate analytics state from the DataStore migration in flight.
 *
 * Always uses [SharedPreferences.Editor.apply] (async) to avoid main-thread disk writes that would trip the
 * Strict Mode audit.
 */
internal class SharedPrefsAnalyticsStore(
    context: Context,
) : FirstFlagStore,
    CounterStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun isFirstTime(event: String): Boolean = !prefs.getBoolean(flagKey(event), false)

    override fun markFired(event: String) {
        prefs.edit().putBoolean(flagKey(event), true).apply()
    }

    @Synchronized
    override fun increment(key: String): Long {
        val newValue = prefs.getLong(counterKey(key), 0L) + 1L
        prefs.edit().putLong(counterKey(key), newValue).apply()
        return newValue
    }

    override fun get(key: String): Long = prefs.getLong(counterKey(key), 0L)

    private fun flagKey(event: String) = "fired.$event"

    private fun counterKey(key: String) = "counter.$key"

    companion object {
        const val PREFS_NAME = "analytics-flags"
    }
}
