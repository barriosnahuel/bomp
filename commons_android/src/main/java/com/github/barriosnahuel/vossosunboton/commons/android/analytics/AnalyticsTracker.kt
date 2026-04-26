/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.os.Bundle

/**
 * Single entry point for emitting Firebase Analytics. Call-sites must use this wrapper instead of
 * `FirebaseAnalytics.getInstance(...).logEvent(...)` directly — the lint check in CI fails the build otherwise.
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
    fun setUserProperty(name: String, value: String)

    /**
     * Emit Firebase's recommended `screen_view` event with a canonical [screenName]. Auto-tracking is disabled at the
     * manifest level, so every screen must call this manually. Re-emits on every visit (no first-variant).
     */
    fun logScreen(screenName: String, extras: Bundle? = null)
}
