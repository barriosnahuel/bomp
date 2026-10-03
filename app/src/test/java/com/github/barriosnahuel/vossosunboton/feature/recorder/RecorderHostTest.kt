/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.declareAudioFileBrowser
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerController
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.vault.WaveformExtractor
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File

/**
 * The recorder as a graph destination (ADR 0024 D4) rather than an Activity: what used to happen for free
 * on `finish()` → `onStop()` now has to be done explicitly, because the destination stays on the back
 * stack (so back can return to the take) and the Activity never stops.
 *
 * The ViewModel is supplied already in Review: a live capture would only be reachable through
 * `Dispatchers.IO`, which the Compose test clock does not join.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class RecorderHostTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var originalController: PlayerController
    private lateinit var viewModel: RecorderViewModel
    private val playbackStateFlow = MutableStateFlow<PlaybackState?>(null)
    private val draftStore = StubDraftStore()
    private val fakeTracker = FakeAnalyticsTracker()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        // The host logs its screen view through the provider, which without a fake builds the real
        // Firebase tracker TestApplication never initialised, and caches it globally for the rest of
        // the fork.
        AnalyticsTrackerProvider.setForTest(fakeTracker)

        // The seeded take is an empty temp file behind an unresolvable content:// URI, so the review
        // wave's decode fails in a background coroutine that OUTLIVES this test — and its failure path
        // calls Tracker, whose global mock the teardown has already undone by then. The escaping
        // Firebase error then fails whichever Compose test drains it next. Stub the extractor so no
        // such work starts: CONTRIBUTING.md § Work that outlives a test.
        mockkObject(WaveformExtractor)
        coEvery { WaveformExtractor.extract(any(), any<Uri>(), any(), any()) } returns null
        Shadows
            .shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .grantPermissions(Manifest.permission.RECORD_AUDIO)
        originalController = PlayerControllerFactory.instance
        PlayerControllerFactory.instance =
            mockk(relaxed = true) {
                every { playbackState } returns playbackStateFlow
            }
        viewModel =
            RecorderViewModel(
                ApplicationProvider.getApplicationContext(),
                engine = StubEngine(),
                ioDispatcher = UnconfinedTestDispatcher(),
                draftStore = draftStore,
                // The real provider goes through FileProvider, which rejects a temp file outside the
                // authority's declared paths.
                uriProvider = { Uri.parse("content://test/${it.name}") },
            )
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        PlayerControllerFactory.instance = originalController
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `keeping the take stops its preview before handing off to naming`() {
        var handedOff: Uri? = null
        givenAHostOnAReviewedTake(onKeepClip = { handedOff = it })
        composeTestRule.onNodeWithText(USE_CLIP).performClick()
        composeTestRule.waitForIdle()

        // Without this the take keeps playing over the naming screen: nothing else stops it now that the
        // recorder survives on the back stack instead of being finish()ed.
        verify(exactly = 1) { PlayerControllerFactory.instance.stopPlayingSound() }
        assertThat(handedOff).isNotNull()
    }

    @Test
    fun `on a device without a microphone a fresh visit explains why and offers only the import escape`() {
        givenNoMicrophone()
        declareAudioFileBrowser()
        var exited = false
        givenAHost(resumeDraft = false, onExit = { exited = true })

        composeTestRule.onNodeWithText(NO_MIC_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithText(IMPORT_INSTEAD).assertIsDisplayed()
        // Neither the capture button nor the permission priming: there is no mic to grant.
        composeTestRule.onAllNodesWithContentDescription(START_RECORDING).assertCountEquals(0)
        composeTestRule.onAllNodesWithText(PERMISSION_TITLE).assertCountEquals(0)

        // Seen once per appearance, however many frames the screen draws; the option was not hidden.
        assertThat(fakeTracker.events.count { it.name == "record_mic_unavailable" }).isEqualTo(1)
        fakeTracker.assertNotEmitted("import_option_hidden")

        composeTestRule.onNodeWithContentDescription(CLOSE).performClick()
        composeTestRule.waitForIdle()
        assertThat(exited).isTrue()
    }

    @Test
    fun `on a device without a microphone or a file browser the import escape is not offered`() {
        givenNoMicrophone()
        givenAHost(resumeDraft = false)

        composeTestRule.onNodeWithText(NO_MIC_MESSAGE).assertIsDisplayed()
        // It would launch nothing: hide it rather than offer a button that fails.
        composeTestRule.onAllNodesWithText(IMPORT_INSTEAD).assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription(CLOSE).assertIsDisplayed()
        assertThat(fakeTracker.events.count { it.name == "record_mic_unavailable" }).isEqualTo(1)
        val hidden = fakeTracker.events.filter { it.name == "import_option_hidden" }
        assertThat(hidden).hasSize(1)
        assertThat(hidden.single().params["surface"]).isEqualTo("record_sound")
    }

    @Test
    fun `on a device without a microphone a restored draft can still be kept but not re-recorded`() {
        givenNoMicrophone()
        // Not granted either: a review only plays back, so it must not sit behind the permission priming.
        Shadows
            .shadowOf(ApplicationProvider.getApplicationContext<Application>())
            .denyPermissions(Manifest.permission.RECORD_AUDIO)
        var handedOff: Uri? = null
        givenAHostOnAReviewedTake(onKeepClip = { handedOff = it })

        composeTestRule.onAllNodesWithText(RE_RECORD).assertCountEquals(0)
        composeTestRule.onNodeWithText(USE_CLIP).performClick()
        composeTestRule.waitForIdle()
        assertThat(handedOff).isNotNull()
    }

    @Test
    fun `on a device without a microphone a draft that vanished falls back to the no-microphone message`() {
        givenNoMicrophone()
        // The banner offered a draft whose clip the OS has since evicted: nothing restores into Review.
        draftStore.pending = null
        givenAHost(resumeDraft = true)

        composeTestRule.onNodeWithText(NO_MIC_MESSAGE).assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription(START_RECORDING).assertCountEquals(0)
    }

    private fun givenNoMicrophone() {
        Shadows
            .shadowOf(ApplicationProvider.getApplicationContext<Application>().packageManager)
            .setSystemFeature(PackageManager.FEATURE_MICROPHONE, false)
    }

    private fun givenAHostOnAReviewedTake(onKeepClip: (Uri) -> Unit = {}) {
        val clip = File.createTempFile("take", ".m4a")
        draftStore.pending = RecorderDraft(clip, TAKE_MS)
        givenAHost(resumeDraft = true, onKeepClip = onKeepClip)
    }

    private fun givenAHost(
        resumeDraft: Boolean,
        onExit: () -> Unit = {},
        onKeepClip: (Uri) -> Unit = {},
    ) {
        composeTestRule.setContent {
            AppTheme {
                RecorderHost(
                    resumeDraft = resumeDraft,
                    onExit = onExit,
                    onKeepClip = onKeepClip,
                    onImportInstead = {},
                    viewModel = viewModel,
                )
            }
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val USE_CLIP = "Use this"
        const val RE_RECORD = "Re-record"
        const val IMPORT_INSTEAD = "Import instead"
        const val START_RECORDING = "Start recording"
        const val CLOSE = "Close"
        const val PERMISSION_TITLE = "Bomp needs your mic"
        const val NO_MIC_MESSAGE = "This device has no microphone to record with."
        const val TAKE_MS = 1_500L
    }
}

private class StubEngine : RecorderEngine {
    override var onMaxDurationReached: (() -> Unit)? = null
    override var onInterrupted: (() -> Unit)? = null

    override fun start(outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.createNewFile()
    }

    override fun stop(): Boolean = true

    override fun maxAmplitude(): Float = 0.5f

    override fun release() = Unit
}

private class StubDraftStore : RecorderDraftStore {
    var pending: RecorderDraft? = null
    var cleared = false

    override val draft: Flow<RecorderDraft?> = MutableStateFlow(null)

    override suspend fun current(): RecorderDraft? = pending

    override fun save(
        file: File,
        durationMs: Long,
    ) = Unit

    override fun clear() {
        cleared = true
    }
}
