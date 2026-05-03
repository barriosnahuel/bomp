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
    fun `wasRestored defaults to false`() {
        runBlocking {
            assertThat(store.wasRestored()).isFalse()
        }
    }

    @Test
    fun `restore sets both consumed=false AND wasRestored=true atomically`() {
        runBlocking {
            store.consume()

            store.restore()

            assertThat(store.isActive()).isTrue()
            assertThat(store.wasRestored()).isTrue()
        }
    }

    @Test
    fun `wasRestored is sticky once set`() {
        runBlocking {
            store.consume()
            store.restore()
            // A subsequent consume must NOT clear wasRestored — the demotion is permanent until
            // the user manually dismisses again, which marks consumed = true and the flag becomes
            // moot.
            store.consume()

            assertThat(store.wasRestored()).isTrue()
        }
    }
}
