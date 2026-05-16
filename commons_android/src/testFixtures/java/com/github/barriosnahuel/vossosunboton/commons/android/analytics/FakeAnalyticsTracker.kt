/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.os.Bundle
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Test double that records every emission so call-site tests can assert what the wrapper would have sent to Firebase.
 *
 * Distributed via the `java-test-fixtures` configuration of `:commons_android` so any module can consume it via
 * `testImplementation testFixtures(project(":commons_android"))`.
 *
 * Thread-safety: tests in this codebase create `SoundsViewModel` instances that launch coroutines on
 * `Dispatchers.IO` (the production default); a single test can also have multiple ViewModels alive at
 * once. [firedFlags] therefore mirrors production's [DataStoreFirstFlagStore] semantics — backed by a
 * [ConcurrentHashMap] so `add` is atomic, leaving exactly one concurrent caller observing "not yet
 * fired" per flag and preventing a double-emit of `first_*` / `milestone_*` events.
 * `Collections.newSetFromMap` (API 9+) wraps the map without requiring `ConcurrentHashMap.newKeySet`
 * (API 24+). The other recorders stay plain — they are written from the same coroutines but only
 * read by the test thread after the ViewModel has reached a quiescent state, so the JMM visibility
 * cost of synchronized collections is not worth paying.
 */
class FakeAnalyticsTracker : AnalyticsTracker {
    val events: MutableList<RecordedEvent> = mutableListOf()
    val screens: MutableList<RecordedScreen> = mutableListOf()
    val userProperties: MutableMap<String, String> = mutableMapOf()
    val counters: MutableMap<String, Long> = mutableMapOf()
    val firedFlags: MutableSet<String> = Collections.newSetFromMap(ConcurrentHashMap())

    override fun log(event: AnalyticsEvent) {
        events += RecordedEvent(event.name, event.params().toReadableMap())
    }

    override fun logScreen(
        screenName: String,
        extras: Bundle?,
    ) {
        screens += RecordedScreen(screenName, extras.toReadableMap())
    }

    override fun setUserProperty(
        name: String,
        value: String,
    ) {
        userProperties[name] = value
    }

    override fun incrementCounter(name: String): Long {
        val newValue = (counters[name] ?: 0L) + 1L
        counters[name] = newValue
        return newValue
    }

    override fun markFiredOnce(flagName: String): Boolean = firedFlags.add(flagName)

    /** Return the first emission with [eventName] or fail with the list of recorded names. */
    fun assertEmitted(eventName: String): RecordedEvent =
        events.firstOrNull { it.name == eventName }
            ?: error("Expected event '$eventName' was not emitted. Recorded: ${events.map { it.name }}")

    /** Return the first `screen_view` with [screenName] or fail with the list of recorded names. */
    fun assertScreenView(screenName: String): RecordedScreen =
        screens.firstOrNull { it.name == screenName }
            ?: error("Expected screen_view '$screenName' was not emitted. Recorded: ${screens.map { it.name }}")

    /** Fail if any event with [eventName] was emitted. */
    fun assertNotEmitted(eventName: String) {
        val match = events.firstOrNull { it.name == eventName }
        if (match != null) error("Expected '$eventName' to NOT be emitted but was: $match")
    }

    /** Reset state between tests. */
    fun reset() {
        events.clear()
        screens.clear()
        userProperties.clear()
        counters.clear()
        firedFlags.clear()
    }

    data class RecordedEvent(
        val name: String,
        val params: Map<String, Any?>,
    )

    data class RecordedScreen(
        val name: String,
        val extras: Map<String, Any?>,
    )

    private fun Bundle?.toReadableMap(): Map<String, Any?> {
        if (this == null) return emptyMap()
        return keySet().associateWith {
            @Suppress("DEPRECATION")
            get(it)
        }
    }
}
