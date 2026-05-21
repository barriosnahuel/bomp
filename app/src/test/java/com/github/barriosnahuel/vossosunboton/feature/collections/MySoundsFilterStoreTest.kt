/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test

internal class MySoundsFilterStoreTest : AbstractRobolectricTest() {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val store = MySoundsFilterStore(context)

    @After
    fun tearDown() =
        runTest {
            store.clearForTest()
        }

    @Test
    fun `fresh install reads back the ALL sentinel`() =
        runTest {
            assertThat(store.get()).isEqualTo(MySoundsFilterStore.ALL_SENTINEL)
        }

    @Test
    fun `set persists across reads`() =
        runTest {
            store.set("collection-42")
            assertThat(store.get()).isEqualTo("collection-42")
        }

    @Test
    fun `set ALL_SENTINEL is honored on read`() =
        runTest {
            store.set("collection-42")
            store.set(MySoundsFilterStore.ALL_SENTINEL)
            assertThat(store.get()).isEqualTo(MySoundsFilterStore.ALL_SENTINEL)
        }
}
