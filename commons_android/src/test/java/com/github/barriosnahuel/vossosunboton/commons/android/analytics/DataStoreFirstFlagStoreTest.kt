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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class DataStoreFirstFlagStoreTest {
    private lateinit var context: Context
    private lateinit var store: DataStoreFirstFlagStore
    private lateinit var writebackScope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        writebackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = DataStoreFirstFlagStore(context, writebackScope)
        runBlocking { store.clearForTest() }
    }

    @After
    fun tearDown() {
        writebackScope.cancel()
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
            drainWritebacks()

            val freshStore = DataStoreFirstFlagStore(context)

            assertThat(freshStore.isFirstTime("persist-me")).isFalse()
        }

    @Test
    fun `clearForTest wipes both in-memory cache and disk state`() =
        runBlocking {
            store.markFired("foo")
            drainWritebacks()

            store.clearForTest()

            assertThat(store.isFirstTime("foo")).isTrue()
            val freshStore = DataStoreFirstFlagStore(context)
            assertThat(freshStore.isFirstTime("foo")).isTrue()
        }

    /**
     * Joins every fire-and-forget `scope.launch { store.edit { ... } }` registered by
     * [DataStoreFirstFlagStore.markFired] / [DataStoreFirstFlagStore.consumeFirstTime]. Replaces a fixed-time
     * `Thread.sleep` so disk-state assertions run after every pending write has actually committed.
     */
    private suspend fun drainWritebacks() {
        writebackScope.coroutineContext.job.children
            .toList()
            .joinAll()
    }
}
