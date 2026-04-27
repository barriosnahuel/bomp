/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Production [AnalyticsTracker]. The single allowed call site for `FirebaseAnalytics.logEvent` — the CI lint check
 * fails any other file that bypasses this wrapper.
 */
internal class FirebaseAnalyticsTracker(
    private val firebase: FirebaseAnalytics,
    private val store: AnalyticsStore,
) : AnalyticsTracker {
    override fun log(event: AnalyticsEvent) {
        val params = event.params()
        firebase.logEvent(event.name, params)

        if (event.hasFirstVariant) {
            val firstName = "first_${event.name}"
            if (store.isFirstTime(firstName)) {
                firebase.logEvent(firstName, params)
                store.markFired(firstName)
            }
        }
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        firebase.setUserProperty(name, value)
    }

    override fun logScreen(
        screenName: String,
        extras: Bundle?,
    ) {
        val params =
            Bundle().apply {
                putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
                extras?.let { putAll(it) }
            }
        firebase.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, params)
    }

    override fun incrementCounter(name: String): Long = store.increment(name)
}
