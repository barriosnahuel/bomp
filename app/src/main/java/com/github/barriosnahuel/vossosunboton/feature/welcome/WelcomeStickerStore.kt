/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.welcome

import android.content.Context
import android.content.SharedPreferences
import android.os.StrictMode
import com.github.barriosnahuel.vossosunboton.R

/**
 * Persists whether the home-grid welcome sticker (Sticker Cero, fresh-install variant) is still
 * eligible to appear. Single boolean per install, default `false` (sticker active until consumed).
 *
 * Lives in its own SharedPreferences file (`welcome-sticker.xml`) on purpose:
 * - The audio metadata DataStore (`bomps.preferences_pb`) holds user-created Bomps and IS backed up.
 *   The welcome flag must NOT be backed up — a restored device should replay the welcome.
 * - The analytics-flags prefs (`analytics-flags.xml`) is for first-event gating; mixing in product
 *   state would muddy that contract.
 *
 * Backup rules in `app_backup_rules.xml` and `app_data_extraction_rules.xml` use `<include>` only,
 * so this file is excluded by default. `BackupRulesTest` enforces the contract with a negative
 * assertion.
 *
 * Writes use `apply()` (async) to keep the main thread off disk and pass the StrictMode audit.
 */
class WelcomeStickerStore(
    context: Context,
) {
    // SharedPreferences.getSharedPreferences() reads the prefs file on first access; wrap the
    // initial load in allowThreadDiskReads to keep StrictMode quiet — same pattern as
    // AnalyticsTrackerProvider.createTracker.
    private val prefs: SharedPreferences =
        run {
            val previous = StrictMode.allowThreadDiskReads()
            try {
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            } finally {
                StrictMode.setThreadPolicy(previous)
            }
        }

    /**
     * `true` until the user has consumed the welcome sticker (audio finished + snackbar timed out
     * without an Undo). Returns `false` when the bundled resource is missing — matches the spec's
     * "no storage for `resource_update` → silently skip" stance.
     */
    fun isActive(): Boolean {
        if (R.raw.app_welcome_sticker == 0) return false
        val previous = StrictMode.allowThreadDiskReads()
        return try {
            !prefs.getBoolean(KEY_CONSUMED, false)
        } finally {
            StrictMode.setThreadPolicy(previous)
        }
    }

    /**
     * `true` after the user has tapped Undo on the dismiss snackbar at least once. Used by
     * `SoundsViewModel.loadSounds()` to demote the welcome from row 0 to the end of MY_SOUNDS:
     * the prime spot belongs to the user once they've shown they want this sticker back rather
     * than letting it consume.
     */
    fun wasRestored(): Boolean {
        val previous = StrictMode.allowThreadDiskReads()
        return try {
            prefs.getBoolean(KEY_WAS_RESTORED, false)
        } finally {
            StrictMode.setThreadPolicy(previous)
        }
    }

    fun consume() {
        prefs.edit().putBoolean(KEY_CONSUMED, true).apply()
    }

    /**
     * Re-enables the welcome AND records the restore so the next render demotes it to the bottom
     * of MY_SOUNDS. Both effects are coupled: an Undo by definition means "user was restored at
     * least once", and from that moment on the welcome is no longer eligible for row 0.
     */
    fun restore() {
        prefs
            .edit()
            .putBoolean(KEY_CONSUMED, false)
            .putBoolean(KEY_WAS_RESTORED, true)
            .apply()
    }

    companion object {
        const val PREFS_NAME = "welcome-sticker"
        private const val KEY_CONSUMED = "consumed"
        private const val KEY_WAS_RESTORED = "was_restored"
    }
}
