/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.app.Application
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
internal class RecorderViewModelTest : AbstractRobolectricTest() {
    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val dispatcher = StandardTestDispatcher()
    private lateinit var engine: FakeRecorderEngine
    private lateinit var draftStore: FakeRecorderDraftStore
    private lateinit var analytics: FakeAnalyticsTracker

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        engine = FakeRecorderEngine()
        draftStore = FakeRecorderDraftStore()
        analytics = FakeAnalyticsTracker()
        File(application.cacheDir, "recordings").deleteRecursively()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        RecorderViewModel(
            application,
            engine,
            dispatcher,
            draftStore,
            analytics,
            uriProvider = { Uri.parse("content://test/${it.name}") },
        )

    @Test
    fun `tapping record moves to Recording and starts the engine`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.onRecordTapped()
            // Bounded advance only: the amplitude poll is an unbounded delay loop, so advanceUntilIdle
            // would spin forever. Cancel the loop (clearForTest → onCleared) before the test ends.
            advanceTimeBy(200)

            assertThat(vm.state.value).isInstanceOf(RecorderState.Recording::class.java)
            assertThat(engine.startCount).isEqualTo(1)
            vm.clearForTest()
        }

    @Test
    fun `a mic-in-use failure keeps Ready and emits a message`() =
        runTest(dispatcher) {
            engine.failOnStart = true
            val vm = viewModel()

            vm.onRecordTapped()
            advanceUntilIdle()

            assertThat(vm.state.value).isEqualTo(RecorderState.Ready)
            assertThat(vm.events.first()).isInstanceOf(RecorderEvent.Message::class.java)
        }

    @Test
    fun `stopping after at least a second moves to Review`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(1_200)

            vm.onStopTapped()
            advanceUntilIdle()

            assertThat(vm.state.value).isInstanceOf(RecorderState.Review::class.java)
            assertThat(engine.stopCount).isEqualTo(1)
        }

    @Test
    fun `stopping under a second discards and nudges`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(400)

            vm.onStopTapped()
            advanceUntilIdle()

            assertThat(vm.state.value).isEqualTo(RecorderState.Ready)
            assertThat(vm.events.first()).isInstanceOf(RecorderEvent.Message::class.java)
        }

    @Test
    fun `an interruption preserves a long-enough clip into Review`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(2_000)

            engine.onInterrupted?.invoke()
            advanceUntilIdle()

            assertThat(vm.state.value).isInstanceOf(RecorderState.Review::class.java)
        }

    @Test
    fun `re-record from Review returns to Ready`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(1_200)
            vm.onStopTapped()
            advanceUntilIdle()

            vm.onReRecord()

            assertThat(vm.state.value).isEqualTo(RecorderState.Ready)
        }

    @Test
    fun `using the clip emits a handoff with the clip uri`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(1_200)
            vm.onStopTapped()
            advanceUntilIdle()

            vm.onUseClip()

            assertThat(vm.events.first()).isInstanceOf(RecorderEvent.Handoff::class.java)
        }

    @Test
    fun `clearing the ViewModel releases the engine`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(200)

            vm.clearForTest()

            assertThat(engine.releaseCount).isAtLeast(1)
        }

    @Test
    fun `reaching Review persists the clip as a recoverable draft`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(1_200)

            vm.onStopTapped()
            advanceUntilIdle()

            assertThat(draftStore.saved).isNotNull()
            assertThat(draftStore.saved!!.durationMs).isAtLeast(1_000)
        }

    @Test
    fun `re-record clears the persisted draft`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(1_200)
            vm.onStopTapped()
            advanceUntilIdle()

            vm.onReRecord()

            assertThat(draftStore.cleared).isTrue()
        }

    @Test
    fun `using the clip keeps the draft until the save pipeline persists it`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(1_200)
            vm.onStopTapped()
            advanceUntilIdle()

            vm.onUseClip()

            // The draft survives handoff — backing out of the save screen must still be recoverable.
            assertThat(draftStore.cleared).isFalse()
            assertThat(vm.events.first()).isInstanceOf(RecorderEvent.Handoff::class.java)
        }

    @Test
    fun `a too-short clip clears any persisted draft`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(400)

            vm.onStopTapped()
            advanceUntilIdle()

            assertThat(draftStore.cleared).isTrue()
        }

    @Test
    fun `onEnter with resumeDraft restores the persisted clip into Review`() =
        runTest(dispatcher) {
            draftStore.currentDraft = RecorderDraft(File(application.cacheDir, "recordings/clip.m4a"), durationMs = 4_000)
            val vm = viewModel()

            vm.onEnter(resumeDraft = true)
            advanceUntilIdle()

            val state = vm.state.value
            assertThat(state).isInstanceOf(RecorderState.Review::class.java)
            assertThat((state as RecorderState.Review).durationMs).isEqualTo(4_000)
        }

    @Test
    fun `onEnter without resumeDraft stays Ready and purges leftovers`() =
        runTest(dispatcher) {
            val stray = File(application.cacheDir, "recordings").apply { mkdirs() }.let { File(it, "stray.m4a") }
            stray.createNewFile()
            val vm = viewModel()

            vm.onEnter(resumeDraft = false)
            advanceUntilIdle()

            assertThat(vm.state.value).isEqualTo(RecorderState.Ready)
            assertThat(stray.exists()).isFalse()
        }

    @Test
    fun `onEnter without resumeDraft preserves an existing draft's file`() =
        runTest(dispatcher) {
            val draftFile = File(application.cacheDir, "recordings").apply { mkdirs() }.let { File(it, "draft.m4a") }
            draftFile.createNewFile()
            draftStore.currentDraft = RecorderDraft(draftFile, durationMs = 3_000)
            val vm = viewModel()

            vm.onEnter(resumeDraft = false)
            advanceUntilIdle()

            // A fresh entry must NOT delete an unsaved draft's bytes — the Landing banner still needs it.
            assertThat(draftFile.exists()).isTrue()
            assertThat(vm.state.value).isEqualTo(RecorderState.Ready)
        }

    @Test
    fun `double-tapping record starts the engine only once`() =
        runTest(dispatcher) {
            val vm = viewModel()

            vm.onRecordTapped()
            vm.onRecordTapped()
            advanceTimeBy(200)

            assertThat(engine.startCount).isEqualTo(1)
            vm.clearForTest()
        }

    @Test
    fun `double-tapping stop reaches Review once without discarding the clip`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(1_200)

            vm.onStopTapped()
            vm.onStopTapped()
            advanceUntilIdle()

            assertThat(vm.state.value).isInstanceOf(RecorderState.Review::class.java)
            assertThat(engine.stopCount).isEqualTo(1)
        }

    @Test
    fun `reaching Review emits recording_completed`() =
        runTest(dispatcher) {
            val vm = viewModel()
            vm.onRecordTapped()
            advanceTimeBy(1_200)

            vm.onStopTapped()
            advanceUntilIdle()

            analytics.assertEmitted("recording_completed")
        }

    @Test
    fun `restoring a draft does not emit recording_completed`() =
        runTest(dispatcher) {
            draftStore.currentDraft = RecorderDraft(File(application.cacheDir, "recordings/clip.m4a"), durationMs = 4_000)
            val vm = viewModel()

            vm.onEnter(resumeDraft = true)
            advanceUntilIdle()

            // A restored draft is a recovered prior completion, not a new one — the funnel must not double-count.
            analytics.assertNotEmitted("recording_completed")
        }
}

private class FakeRecorderEngine : RecorderEngine {
    override var onMaxDurationReached: (() -> Unit)? = null
    override var onInterrupted: (() -> Unit)? = null

    var startCount = 0
    var stopCount = 0
    var releaseCount = 0
    var failOnStart = false
    var produced = true

    override fun start(outputFile: File) {
        if (failOnStart) error("mic busy")
        startCount++
        outputFile.parentFile?.mkdirs()
        outputFile.createNewFile()
    }

    override fun stop(): Boolean {
        stopCount++
        return produced
    }

    override fun maxAmplitude(): Float = 0.5f

    override fun release() {
        releaseCount++
    }
}

private class FakeRecorderDraftStore : RecorderDraftStore {
    private val _draft = MutableStateFlow<RecorderDraft?>(null)
    override val draft: Flow<RecorderDraft?> = _draft

    var saved: RecorderDraft? = null
    var cleared = false
    var currentDraft: RecorderDraft? = null

    override suspend fun current(): RecorderDraft? = currentDraft

    override fun save(
        file: File,
        durationMs: Long,
    ) {
        saved = RecorderDraft(file, durationMs)
        _draft.value = saved
    }

    override fun clear() {
        cleared = true
        saved = null
        currentDraft = null
        _draft.value = null
    }
}
