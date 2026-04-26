/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class SharedPrefsAnalyticsStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Application>()
        context
            .getSharedPreferences(SharedPrefsAnalyticsStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `isFirstTime returns true before markFired and false after`() {
        val store = SharedPrefsAnalyticsStore(context)

        assertThat(store.isFirstTime("first_sound_add")).isTrue()
        store.markFired("first_sound_add")
        assertThat(store.isFirstTime("first_sound_add")).isFalse()
    }

    @Test
    fun `markFired persists across store instances`() {
        SharedPrefsAnalyticsStore(context).markFired("first_share")

        val freshStore = SharedPrefsAnalyticsStore(context)

        assertThat(freshStore.isFirstTime("first_share")).isFalse()
    }

    @Test
    fun `increment counts up monotonically`() {
        val store = SharedPrefsAnalyticsStore(context)

        assertThat(store.get("lifetime_plays")).isEqualTo(0L)
        assertThat(store.increment("lifetime_plays")).isEqualTo(1L)
        assertThat(store.increment("lifetime_plays")).isEqualTo(2L)
        assertThat(store.get("lifetime_plays")).isEqualTo(2L)
    }

    @Test
    fun `flag and counter namespaces are isolated`() {
        val store = SharedPrefsAnalyticsStore(context)

        store.markFired("first_sound_play")
        store.increment("first_sound_play")

        assertThat(store.isFirstTime("first_sound_play")).isFalse()
        assertThat(store.get("first_sound_play")).isEqualTo(1L)
    }
}
