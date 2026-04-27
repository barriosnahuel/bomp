/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.os.Bundle

/**
 * Single entry point for emitting Firebase Analytics. Call-sites must use this wrapper instead of going to the
 * Firebase SDK directly — the analytics-wrapper-guard CI check fails any build that bypasses it.
 *
 * The wrapper centralises three concerns: the consecutive-vs-first-variant toggle, the `screen_view` recommended
 * event with canonical names, and user property writes.
 */
interface AnalyticsTracker {
    /**
     * Emit a custom event. The implementation also emits the matching `first_*` variant the first time the event
     * fires on this install (when [AnalyticsEvent.hasFirstVariant] is `true`).
     */
    fun log(event: AnalyticsEvent)

    /**
     * Set a Firebase Analytics user property. Persistent across sessions until overwritten or the app is uninstalled.
     */
    fun setUserProperty(
        name: String,
        value: String,
    )

    /**
     * Emit Firebase's recommended `screen_view` event with a canonical [screenName]. Auto-tracking is disabled at the
     * manifest level, so every screen must call this manually. Re-emits on every visit (no first-variant).
     */
    fun logScreen(
        screenName: String,
        extras: Bundle? = null,
    )

    /**
     * Increment a persistent lifetime counter (e.g. `lifetime_shares`, `lifetime_plays`) and return the new value.
     * Pair the result with [setUserProperty] to surface it on Firebase.
     */
    fun incrementCounter(name: String): Long

    /**
     * Atomically mark [flagName] as fired. Returns `true` only the first time on this install. Used by call-sites that
     * gate one-shot custom events such as milestones (the wrapper itself manages first-variants for events declared
     * with `hasFirstVariant = true`).
     */
    fun markFiredOnce(flagName: String): Boolean
}
