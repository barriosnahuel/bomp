/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

internal class RecordingActivitySmokeTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var engine: FakeEngine
    private lateinit var fake: FakeAnalyticsTracker

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.playbackState } returns MutableStateFlow<PlaybackState?>(null)
        every { PlayerControllerFactory.instance.stopPlayingSound() } answers { nothing }
        engine = FakeEngine()
        RecorderEngineProvider.setForTest(engine)
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
    }

    @After
    fun tearDown() {
        RecorderEngineProvider.setForTest(null)
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Test
    fun emitsTheRecordSoundScreenView() {
        ActivityScenario.launch(RecordingActivity::class.java).use {
            fake.assertScreenView(CanonicalScreenName.RECORD_SOUND)
        }
    }

    @Test
    fun reachesResumedWithoutCrashing() {
        ActivityScenario.launch(RecordingActivity::class.java).use { scenario ->
            assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun releasesTheEngineWhenDestroyed() {
        ActivityScenario.launch(RecordingActivity::class.java).use { /* closing destroys the Activity */ }

        assertThat(engine.releaseCount).isAtLeast(1)
    }

    private class FakeEngine : RecorderEngine {
        override var onMaxDurationReached: (() -> Unit)? = null
        override var onInterrupted: (() -> Unit)? = null
        var releaseCount = 0

        override fun start(outputFile: File) = Unit

        override fun stop(): Boolean = true

        override fun maxAmplitude(): Float = 0f

        override fun release() {
            releaseCount++
        }
    }
}
