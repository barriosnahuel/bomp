/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.Manifest
import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerController
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraft
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraftStore
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraftStoreProvider
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderTempFiles
import com.github.barriosnahuel.vossosunboton.feature.vault.WaveformExtractor
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * "+" → Record with an unsaved draft pending asks first, so a fresh take can never silently overwrite or
 * drop it (ADR 0019 § Draft recovery). One test per transition of the guard, plus a recreate;
 * dismissing it (back / tap outside) is covered by the instrumented [RecorderDraftGuardFlowTest].
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class LandingDraftGuardTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val createdViewModels = mutableListOf<SoundsViewModel>()
    private val playbackStateFlow = MutableStateFlow<PlaybackState?>(null)
    private lateinit var originalController: PlayerController
    private lateinit var draftStore: FakeDraftStore
    private lateinit var analytics: FakeAnalyticsTracker

    @Before
    fun setUp() {
        analytics = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(analytics)
        // Granted so the recorder renders its real Ready/Review states, which is what tells a fresh
        // entry apart from a resumed one.
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.RECORD_AUDIO)
        // A resumed take decodes its wave in the background; stub it so no work outlives the test
        // (CONTRIBUTING.md § Work that outlives a test).
        mockkObject(WaveformExtractor)
        coEvery { WaveformExtractor.extract(any(), any<Uri>(), any(), any()) } returns null
        originalController = PlayerControllerFactory.instance
        PlayerControllerFactory.instance =
            mockk(relaxed = true) {
                every { playbackState } returns playbackStateFlow
            }
        draftStore = FakeDraftStore()
        RecorderDraftStoreProvider.setForTest(draftStore)
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        RecorderDraftStoreProvider.setForTest(null)
        RecorderTempFiles.purge(app)
        PlayerControllerFactory.instance = originalController
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Test
    fun `recording with no pending draft opens the recorder straight away`() {
        givenLanding()

        tapRecordInTheHub()

        composeTestRule.onAllNodesWithText(GUARD_KEEP).assertCountEquals(0)
        composeTestRule.onNodeWithText(READY_HINT).assertIsDisplayed()
    }

    @Test
    fun `recording opens the recorder straight away when the pending draft's clip is gone`() {
        givenAPendingDraft().delete()
        givenLanding()

        tapRecordInTheHub()

        composeTestRule.onAllNodesWithText(GUARD_KEEP).assertCountEquals(0)
        composeTestRule.onNodeWithText(READY_HINT).assertIsDisplayed()
    }

    @Test
    fun `recording with a pending draft asks first instead of opening the recorder`() {
        givenAPendingDraft()
        givenLanding()

        tapRecordInTheHub()

        composeTestRule.onNodeWithText(GUARD_MESSAGE).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(READY_HINT).assertCountEquals(0)
    }

    @Test
    fun `keeping the pending draft resumes it in review and keeps it`() {
        val clip = givenAPendingDraft()
        givenLanding()
        tapRecordInTheHub()

        composeTestRule.onNodeWithText(GUARD_KEEP).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(USE_CLIP).assertIsDisplayed()
        analytics.assertEmitted("recording_draft_resumed")
        assertThat(draftStore.cleared).isFalse()
        assertThat(clip.exists()).isTrue()
    }

    @Test
    fun `recording a new one drops the pending draft and opens a fresh recorder`() {
        val clip = givenAPendingDraft()
        givenLanding()
        tapRecordInTheHub()

        composeTestRule.onNodeWithText(GUARD_RECORD_NEW).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(READY_HINT).assertIsDisplayed()
        analytics.assertEmitted("recording_draft_discarded")
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { draftStore.cleared }
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { !clip.exists() }
    }

    @Test
    fun `a recreate keeps the question open`() {
        givenAPendingDraft()
        val viewModel = buildLoadedSoundsViewModel(createdViewModels, draftStore = draftStore)
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
        tapRecordInTheHub()
        composeTestRule.onNodeWithText(GUARD_MESSAGE).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(GUARD_MESSAGE).assertIsDisplayed()
    }

    private fun givenAPendingDraft(): File {
        val clip = RecorderTempFiles.newTempFile(app).apply { writeBytes(byteArrayOf(1, 2, 3)) }
        draftStore.set(RecorderDraft(clip, durationMs = 3_000))
        return clip
    }

    private fun givenLanding() {
        val viewModel = buildLoadedSoundsViewModel(createdViewModels, draftStore = draftStore)
        composeTestRule.setContent { AppTheme { LandingScreen(viewModel) } }
        composeTestRule.waitForIdle()
    }

    private fun tapRecordInTheHub() {
        composeTestRule.onNodeWithContentDescription(app.getString(R.string.app_hub_fab_description)).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(app.getString(R.string.app_hub_record)).performClick()
        composeTestRule.waitForIdle()
    }

    /** One store shared by Landing and the recorder (as the production provider is), file-validated like the real one. */
    private class FakeDraftStore : RecorderDraftStore {
        private val state = MutableStateFlow<RecorderDraft?>(null)
        var cleared = false

        fun set(value: RecorderDraft) {
            state.value = value
        }

        override val draft: Flow<RecorderDraft?> = state

        override suspend fun current(): RecorderDraft? = state.value?.takeIf { it.file.exists() }

        override fun save(
            file: File,
            durationMs: Long,
        ) {
            state.value = RecorderDraft(file, durationMs)
        }

        override fun clear() {
            cleared = true
            state.value = null
        }
    }

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L
        val GUARD_MESSAGE: String get() = string(R.string.app_recorder_draft_guard_message)
        val GUARD_KEEP: String get() = string(R.string.app_recorder_draft_guard_keep)
        val GUARD_RECORD_NEW: String get() = string(R.string.app_recorder_draft_guard_record_new)
        val READY_HINT: String get() = string(R.string.app_recorder_ready_hint)
        val USE_CLIP: String get() = string(R.string.app_recorder_use)

        fun string(id: Int): String = ApplicationProvider.getApplicationContext<Application>().getString(id)
    }
}
