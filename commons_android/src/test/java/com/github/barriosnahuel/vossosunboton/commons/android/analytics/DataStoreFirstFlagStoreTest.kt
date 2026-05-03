/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.M, Build.VERSION_CODES.TIRAMISU, Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class DataStoreFirstFlagStoreTest {
    private lateinit var context: Context
    private lateinit var store: DataStoreFirstFlagStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = DataStoreFirstFlagStore(context)
        runBlocking { store.clearForTest() }
    }

    @Test
    fun `default state is first-time for any event`() {
        assertThat(store.isFirstTime("foo")).isTrue()
        assertThat(store.isFirstTime("bar")).isTrue()
    }

    @Test
    fun `markFired flips the flag synchronously in memory`() {
        store.markFired("foo")

        assertThat(store.isFirstTime("foo")).isFalse()
        assertThat(store.isFirstTime("bar")).isTrue()
    }

    @Test
    fun `consumeFirstTime returns true exactly once`() {
        assertThat(store.consumeFirstTime("foo")).isTrue()
        assertThat(store.consumeFirstTime("foo")).isFalse()
        assertThat(store.consumeFirstTime("foo")).isFalse()
    }

    @Test
    fun `consumeFirstTime is atomic under concurrent callers`() =
        runBlocking {
            // 100 coroutines race on the same key. Exactly one must win.
            val winners =
                java.util.concurrent.atomic
                    .AtomicInteger(0)
            coroutineScope {
                repeat(100) {
                    launch {
                        if (store.consumeFirstTime("race")) winners.incrementAndGet()
                    }
                }
            }

            assertThat(winners.get()).isEqualTo(1)
        }

    @Test
    fun `state survives instantiating a new store backed by the same DataStore file`() =
        runBlocking {
            store.markFired("persist-me")
            // Give the fire-and-forget disk write a moment to commit before instantiating the
            // second store (which primes its cache from disk).
            Thread.sleep(WRITE_BACK_GRACE_MS)

            val freshStore = DataStoreFirstFlagStore(context)

            assertThat(freshStore.isFirstTime("persist-me")).isFalse()
        }

    @Test
    fun `clearForTest wipes both in-memory cache and disk state`() =
        runBlocking {
            store.markFired("foo")
            Thread.sleep(WRITE_BACK_GRACE_MS)

            store.clearForTest()

            assertThat(store.isFirstTime("foo")).isTrue()
            val freshStore = DataStoreFirstFlagStore(context)
            assertThat(freshStore.isFirstTime("foo")).isTrue()
        }

    companion object {
        // Async write-back via SupervisorJob + Dispatchers.IO; tiny disk write completes within
        // tens of ms even under Robolectric. 250 ms is generous enough to be reliable without
        // padding total test time noticeably.
        private const val WRITE_BACK_GRACE_MS = 250L
    }
}
