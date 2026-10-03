/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.junit.Assert.assertThrows
import org.junit.Test

/** Drives [awaitInitialLoad] with a controllable gate, so no real ViewModel or DataStore is involved. */
internal class SoundsViewModelTestBuilderTest {
    @Test
    fun `awaitInitialLoad returns immediately when the gate is already open`() {
        awaitInitialLoad(MutableStateFlow(true), timeoutMs = SHORT_TIMEOUT_MS)
    }

    @Test
    fun `awaitInitialLoad returns once the gate opens`() {
        val gate = MutableStateFlow(false)
        val opener = CoroutineScope(Dispatchers.Default)
        opener.launch {
            delay(OPEN_AFTER_MS)
            gate.value = true
        }
        try {
            awaitInitialLoad(gate)
        } finally {
            opener.cancel()
        }

        assertThat(gate.value).isTrue()
    }

    @Test
    fun `awaitInitialLoad fails naming the gate when it never opens`() {
        val error =
            assertThrows(AssertionError::class.java) {
                awaitInitialLoad(MutableStateFlow(false), timeoutMs = SHORT_TIMEOUT_MS)
            }

        assertThat(error).hasMessageThat().contains("SoundsViewModel.isInitialLoadComplete")
        assertThat(error).hasMessageThat().contains("$SHORT_TIMEOUT_MS ms")
        assertThat(error).hasCauseThat().isInstanceOf(TimeoutCancellationException::class.java)
    }

    private companion object {
        const val SHORT_TIMEOUT_MS = 50L
        const val OPEN_AFTER_MS = 50L
    }
}
