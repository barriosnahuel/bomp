/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        engine = FakeRecorderEngine()
        File(application.cacheDir, "recordings").deleteRecursively()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = RecorderViewModel(application, engine, dispatcher)

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
