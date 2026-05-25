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
internal class DataStoreCounterStoreTest {
    private lateinit var context: Context
    private lateinit var store: DataStoreCounterStore
    private lateinit var writebackScope: CoroutineScope

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        writebackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = DataStoreCounterStore(context, writebackScope)
        runBlocking { store.clearForTest() }
    }

    @After
    fun tearDown() {
        writebackScope.cancel()
    }

    @Test
    fun `default state is zero for any key`() {
        assertThat(store.get("foo")).isEqualTo(0L)
        assertThat(store.get("bar")).isEqualTo(0L)
    }

    @Test
    fun `increment returns the new value monotonically`() {
        assertThat(store.increment("foo")).isEqualTo(1L)
        assertThat(store.increment("foo")).isEqualTo(2L)
        assertThat(store.increment("foo")).isEqualTo(3L)
        assertThat(store.get("foo")).isEqualTo(3L)
    }

    @Test
    fun `independent keys do not interfere`() {
        store.increment("foo")
        store.increment("foo")
        store.increment("bar")

        assertThat(store.get("foo")).isEqualTo(2L)
        assertThat(store.get("bar")).isEqualTo(1L)
    }

    @Test
    fun `increment is atomic under concurrent callers`() =
        runBlocking {
            coroutineScope {
                repeat(100) {
                    launch { store.increment("race") }
                }
            }

            assertThat(store.get("race")).isEqualTo(100L)
        }

    /**
     * Regression net for the "write the latest cache value INSIDE `edit`, not the captured
     * `newValue`" rule. Two concurrent increments dispatched on `Dispatchers.IO` (a thread pool)
     * could land in inverted order if we captured `newValue` outside the `edit` block; the disk
     * would end at the lower value while the cache is at the higher. Reading the cache inside
     * `edit` ensures the latest wins under DataStore's internal Mutex. This test asserts cache
     * and disk converge after 100 concurrent increments.
     */
    @Test
    fun `increment write-back — cache and disk converge under concurrency`() =
        runBlocking {
            coroutineScope {
                repeat(100) {
                    launch { store.increment("converge") }
                }
            }
            drainWritebacks()

            val freshStore = DataStoreCounterStore(context)
            assertThat(store.get("converge")).isEqualTo(100L)
            assertThat(freshStore.get("converge")).isEqualTo(100L)
        }

    @Test
    fun `state survives instantiating a new store backed by the same DataStore file`() =
        runBlocking {
            repeat(7) { store.increment("persist") }
            drainWritebacks()

            val freshStore = DataStoreCounterStore(context)

            assertThat(freshStore.get("persist")).isEqualTo(7L)
        }

    @Test
    fun `clearForTest wipes both in-memory cache and disk state`() =
        runBlocking {
            store.increment("foo")
            drainWritebacks()

            store.clearForTest()

            assertThat(store.get("foo")).isEqualTo(0L)
            val freshStore = DataStoreCounterStore(context)
            assertThat(freshStore.get("foo")).isEqualTo(0L)
        }

    /**
     * Joins every fire-and-forget `scope.launch { store.edit { ... } }` registered by [DataStoreCounterStore.increment]
     * (and friends). Replaces a `Thread.sleep` grace period so the assertions that read disk state run after every
     * pending write has actually committed — the previous fixed-time wait raced under the bumped DataStore 1.2.x.
     */
    private suspend fun drainWritebacks() {
        writebackScope.coroutineContext.job.children
            .toList()
            .joinAll()
    }
}
