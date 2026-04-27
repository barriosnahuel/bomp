/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.os.Build
import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import com.google.firebase.analytics.FirebaseAnalytics
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class FirebaseAnalyticsTrackerTest {
    private lateinit var firebase: FirebaseAnalytics
    private lateinit var store: AnalyticsStore
    private lateinit var tracker: FirebaseAnalyticsTracker

    @Before
    fun setUp() {
        firebase = mockk(relaxed = true)
        store = mockk(relaxed = true)
        every { store.isFirstTime(any()) } returns false
        tracker = FirebaseAnalyticsTracker(firebase, store)
    }

    @Test
    fun `log emits the base event with its params`() {
        val captured = slot<Bundle>()
        every { firebase.logEvent(any(), capture(captured)) } answers { Unit }

        tracker.log(AnalyticsEvent.SoundPlay(surface = "my_sounds"))

        verify { firebase.logEvent("sound_play", any()) }
        assertThat(captured.captured.getString("surface")).isEqualTo("my_sounds")
    }

    @Test
    fun `log emits the first variant on first invocation when hasFirstVariant is true`() {
        every { store.isFirstTime("first_sound_delete") } returns true

        tracker.log(AnalyticsEvent.SoundDelete)

        verify { firebase.logEvent("sound_delete", null) }
        verify { firebase.logEvent("first_sound_delete", null) }
        verify { store.markFired("first_sound_delete") }
    }

    @Test
    fun `log does not re-emit the first variant once marked fired`() {
        every { store.isFirstTime("first_sound_delete") } returns false

        tracker.log(AnalyticsEvent.SoundDelete)

        verify { firebase.logEvent("sound_delete", null) }
        verify(exactly = 0) { firebase.logEvent("first_sound_delete", any()) }
        verify(exactly = 0) { store.markFired(any()) }
    }

    @Test
    fun `log keeps first-variant flags independent across events`() {
        every { store.isFirstTime("first_sound_delete") } returns false
        every { store.isFirstTime("first_pin_toggle") } returns true

        tracker.log(AnalyticsEvent.SoundDelete)
        tracker.log(AnalyticsEvent.PinToggle(pinned = true))

        verify(exactly = 0) { firebase.logEvent("first_sound_delete", any()) }
        verify { firebase.logEvent("first_pin_toggle", any()) }
    }

    @Test
    fun `log never emits a first variant for events declared without one`() {
        every { store.isFirstTime(any()) } returns true

        tracker.log(AnalyticsEvent.SoundDeleteUndone)

        verify { firebase.logEvent("sound_delete_undone", null) }
        verify(exactly = 0) { firebase.logEvent("first_sound_delete_undone", any()) }
    }

    @Test
    fun `log propagates SoundAdd params verbatim into the bundle`() {
        val captured = slot<Bundle>()
        every { firebase.logEvent("sound_add", capture(captured)) } answers { Unit }

        tracker.log(
            AnalyticsEvent.SoundAdd(
                source = "share",
                nameLength = 7,
                nameWordCount = 2,
                nameHitLimit = false,
                currentSounds = 5,
            ),
        )

        with(captured.captured) {
            assertThat(getString("source")).isEqualTo("share")
            assertThat(getInt("name_length")).isEqualTo(7)
            assertThat(getInt("name_word_count")).isEqualTo(2)
            assertThat(getBoolean("name_hit_limit")).isFalse()
            assertThat(getInt("current_sounds")).isEqualTo(5)
        }
    }

    @Test
    fun `logScreen emits SCREEN_VIEW with the canonical screen name`() {
        val captured = slot<Bundle>()
        every { firebase.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, capture(captured)) } answers { Unit }

        tracker.logScreen("my_sounds")

        assertThat(captured.captured.getString(FirebaseAnalytics.Param.SCREEN_NAME)).isEqualTo("my_sounds")
    }

    @Test
    fun `logScreen merges extras into the screen view bundle`() {
        val captured = slot<Bundle>()
        every { firebase.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, capture(captured)) } answers { Unit }

        val extras = Bundle().apply { putString("source", "share") }
        tracker.logScreen("add_sound", extras)

        with(captured.captured) {
            assertThat(getString(FirebaseAnalytics.Param.SCREEN_NAME)).isEqualTo("add_sound")
            assertThat(getString("source")).isEqualTo("share")
        }
    }

    @Test
    fun `setUserProperty delegates to FirebaseAnalytics`() {
        tracker.setUserProperty("current_sounds", "3")

        verify { firebase.setUserProperty("current_sounds", "3") }
    }

    @Test
    fun `incrementCounter delegates to the analytics store and returns the new value`() {
        every { store.increment("lifetime_shares") } returns 7L

        val result = tracker.incrementCounter("lifetime_shares")

        assertThat(result).isEqualTo(7L)
        verify { store.increment("lifetime_shares") }
    }

    @Test
    fun `markFiredOnce returns true and persists when never fired`() {
        every { store.isFirstTime("milestone_sounds_3") } returns true

        val result = tracker.markFiredOnce("milestone_sounds_3")

        assertThat(result).isTrue()
        verify { store.markFired("milestone_sounds_3") }
    }

    @Test
    fun `markFiredOnce returns false and skips persistence when already fired`() {
        every { store.isFirstTime("milestone_sounds_3") } returns false

        val result = tracker.markFiredOnce("milestone_sounds_3")

        assertThat(result).isFalse()
        verify(exactly = 0) { store.markFired(any()) }
    }
}
