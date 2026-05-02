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
import org.junit.Before
import org.junit.Test

internal class WelcomeStickerStoreTest : AbstractRobolectricTest() {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Each test starts with a clean prefs file so the default state is observable.
        context
            .getSharedPreferences(WelcomeStickerStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun `default state is active`() {
        val store = WelcomeStickerStore(context)

        assertThat(store.isActive()).isTrue()
    }

    @Test
    fun `consume disables`() {
        val store = WelcomeStickerStore(context)

        store.consume()

        assertThat(store.isActive()).isFalse()
    }

    @Test
    fun `restore re-enables after consume`() {
        val store = WelcomeStickerStore(context)
        store.consume()

        store.restore()

        assertThat(store.isActive()).isTrue()
    }

    @Test
    fun `consume is idempotent`() {
        val store = WelcomeStickerStore(context)

        store.consume()
        store.consume()

        assertThat(store.isActive()).isFalse()
    }
}
