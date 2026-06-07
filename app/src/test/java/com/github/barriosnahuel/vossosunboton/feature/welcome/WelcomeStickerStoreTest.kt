/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.welcome

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

internal class WelcomeStickerStoreTest : AbstractRobolectricTest() {
    private lateinit var context: Context
    private lateinit var store: WelcomeStickerStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        store = WelcomeStickerStore(context)
        // DataStore is a process-singleton via the top-level delegate; reset between tests so
        // the default state is observable. Mirrors `SoundsRepository.clearForTest()` usage.
        runBlocking { store.clearForTest() }
    }

    @Test
    fun `default state is active`() {
        runBlocking {
            assertThat(store.isActive()).isTrue()
        }
    }

    @Test
    fun `consume disables`() {
        runBlocking {
            store.consume()

            assertThat(store.isActive()).isFalse()
        }
    }

    @Test
    fun `restore re-enables after consume`() {
        runBlocking {
            store.consume()

            store.restore()

            assertThat(store.isActive()).isTrue()
        }
    }

    @Test
    fun `consume is idempotent`() {
        runBlocking {
            store.consume()
            store.consume()

            assertThat(store.isActive()).isFalse()
        }
    }

    @Test
    fun `acknowledged defaults to false`() {
        runBlocking {
            assertThat(store.isAcknowledged()).isFalse()
        }
    }

    @Test
    fun `markAcknowledged flips it true and survives a fresh instance`() {
        runBlocking {
            store.markAcknowledged()

            assertThat(store.isAcknowledged()).isTrue()
            // A fresh instance reads the persisted disk value, not the in-memory cache.
            assertThat(WelcomeStickerStore(context).isAcknowledged()).isTrue()
        }
    }

    @Test
    fun `acknowledged is independent of consumed`() {
        runBlocking {
            // Playing to completion (acknowledged) must NOT remove the welcome (consumed) — the card
            // stays in the list until the user manually deletes it.
            store.markAcknowledged()

            assertThat(store.isAcknowledged()).isTrue()
            assertThat(store.isActive()).isTrue()
        }
    }

    @Test
    fun `hintShown defaults to false and flips true once marked`() {
        runBlocking {
            assertThat(store.isHintShown()).isFalse()

            store.markHintShown()

            assertThat(store.isHintShown()).isTrue()
        }
    }

    @Test
    fun `installTimestamp is captured once and stable across reads`() {
        runBlocking {
            var clock = 1_000L
            val timed = WelcomeStickerStore(context, now = { clock })

            val first = timed.installTimestamp()
            clock = 9_999L
            val second = timed.installTimestamp()
            // The timestamp is captured on first read and persisted, so it never moves even though
            // the clock advanced — the welcome keeps a stable position in the date-sorted list.
            assertThat(first).isEqualTo(1_000L)
            assertThat(second).isEqualTo(1_000L)
        }
    }

    @Test
    fun `migrateToPersistentIfNeeded resurfaces a welcome consumed under the old model`() {
        runBlocking {
            // Old model: letting it auto-destruct set consumed=true and it vanished forever.
            store.consume()
            assertThat(store.isActive()).isFalse()

            store.migrateToPersistentIfNeeded()

            assertThat(store.isActive()).isTrue()
        }
    }

    @Test
    fun `migrateToPersistentIfNeeded is idempotent so a later manual delete sticks`() {
        runBlocking {
            store.consume()
            store.migrateToPersistentIfNeeded() // resurfaced once
            // Under the new model the user deletes it on purpose.
            store.consume()

            store.migrateToPersistentIfNeeded() // must NOT resurrect it again

            assertThat(store.isActive()).isFalse()
        }
    }
}
