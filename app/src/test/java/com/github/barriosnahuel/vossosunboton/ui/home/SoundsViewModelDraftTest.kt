/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraft
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraftStore
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
internal class SoundsViewModelDraftTest : AbstractRobolectricTest() {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        runBlocking { SoundsRepository(app).clearForTest() }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `discardDraft deletes the clip file and clears the draft store`() {
        val clip =
            File(app.cacheDir, "recordings")
                .apply { mkdirs() }
                .let { File(it, "draft.m4a") }
                .apply { createNewFile() }
        val draftStore = FakeDraftStore(RecorderDraft(clip, durationMs = 3_000))
        val viewModel = SoundsViewModel(app, ioDispatcher = UnconfinedTestDispatcher(), draftStore = draftStore)

        viewModel.discardDraft()

        assertThat(clip.exists()).isFalse()
        assertThat(draftStore.cleared).isTrue()
    }

    private class FakeDraftStore(
        private val draftValue: RecorderDraft?,
    ) : RecorderDraftStore {
        var cleared = false
        override val draft: Flow<RecorderDraft?> = MutableStateFlow(draftValue)

        override suspend fun current(): RecorderDraft? = draftValue

        override fun save(
            file: File,
            durationMs: Long,
        ) = Unit

        override fun clear() {
            cleared = true
        }
    }
}
