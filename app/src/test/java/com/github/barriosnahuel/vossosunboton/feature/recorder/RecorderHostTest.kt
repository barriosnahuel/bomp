/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.Manifest
import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerController
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
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

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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

    private fun givenAHostOnAReviewedTake(onKeepClip: (Uri) -> Unit = {}) {
        val clip = File.createTempFile("take", ".m4a")
        draftStore.pending = RecorderDraft(clip, TAKE_MS)
        composeTestRule.setContent {
            AppTheme {
                RecorderHost(
                    resumeDraft = true,
                    onExit = {},
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
