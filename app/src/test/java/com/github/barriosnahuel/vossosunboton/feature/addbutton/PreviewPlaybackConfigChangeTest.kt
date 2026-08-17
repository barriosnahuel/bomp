/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performTextInput
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerController
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.SoundSource
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Guards that rotating the device (or any other Activity recreate) does not cut the audio the user
 * is auditioning on the add screen — for both preview surfaces, which route their stop through
 * [StopPreviewOnDispose].
 *
 * The negative assertions are only meaningful next to the positive one: `recreate()` and a real
 * unmount both dispose the composition, and the unmount case proves the disposal genuinely reaches
 * `stopPlayingSound`, so a passing config-change test cannot be a composition that never disposed.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class PreviewPlaybackConfigChangeTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val playbackStateFlow = MutableStateFlow<PlaybackState?>(null)
    private lateinit var fakeController: PlayerController
    private lateinit var originalController: PlayerController

    private val screenMounted = mutableStateOf(true)

    @Before
    fun setUp() {
        AnalyticsTrackerProvider.setForTest(FakeAnalyticsTracker())
        AddButtonFeatureProvider.setForTest(NoOpFeature())
        originalController = PlayerControllerFactory.instance
        fakeController =
            mockk(relaxed = true) {
                every { playbackState } returns playbackStateFlow
            }
        PlayerControllerFactory.instance = fakeController
        runBlocking { SoundsRepository(context).clearForTest() }
    }

    @After
    fun tearDown() {
        disposeAddButtonScreen(composeTestRule, screenMounted)
        PlayerControllerFactory.instance = originalController
        AnalyticsTrackerProvider.setForTest(null)
        AddButtonFeatureProvider.setForTest(null)
        runBlocking { SoundsRepository(context).clearForTest() }
    }

    @Test
    fun `preview keeps playing when the host Activity is recreated for a configuration change`() {
        playbackStateFlow.value =
            PlaybackState(uri = SAMPLE_URI, positionMs = 1_200, durationMs = 30_000, isPlaying = true)

        setScreen()
        recreateHost()
        setScreen()

        verify(exactly = 0) { fakeController.stopPlayingSound() }
    }

    @Test
    fun `preview stops when the screen leaves the composition for good`() {
        playbackStateFlow.value =
            PlaybackState(uri = SAMPLE_URI, positionMs = 1_200, durationMs = 30_000, isPlaying = true)

        setScreen()
        disposeAddButtonScreen(composeTestRule, screenMounted)

        verify(exactly = 1) { fakeController.stopPlayingSound() }
    }

    @Test
    fun `duplicate name hint keeps its match playing when the host Activity is recreated`() {
        val match = testSound(name = EXISTING_NAME, file = "existing.mp3")
        runBlocking { SoundsRepository(context).save(match) }
        val matchUri = getFile(context, match.file!!).toUri()

        setScreen()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
        awaitHint()
        // The hint owns the controller now: its inline probe is the audio the user is listening to.
        playbackStateFlow.value =
            PlaybackState(uri = matchUri, positionMs = 500, durationMs = 3_000, isPlaying = true)
        composeTestRule.waitForIdle()

        recreateHost()

        verify(exactly = 0) { fakeController.stopPlayingSound() }
    }

    /** Destroy and rebuild the host Activity, which is what the system does on rotation. */
    private fun recreateHost() {
        composeTestRule.activityRule.scenario.recreate()
        composeTestRule.waitForIdle()
    }

    private fun setScreen() {
        val host = composeTestRule.activity
        composeTestRule.setContent {
            AppTheme {
                if (screenMounted.value) {
                    AddButtonScreen(
                        context = host,
                        mode = AddButtonMode.Create(SAMPLE_URI),
                        source = AddSoundSource.SHARE,
                        onSaved = {},
                        onNavigateUp = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun awaitHint() {
        val playLabel = context.getString(R.string.app_addbutton_duplicate_name_hint_play_description)
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithContentDescription(playLabel).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private class NoOpFeature : AddButtonFeature {
        override fun saveNewButtonAsync(
            context: Context,
            name: String,
            uri: String,
            publicCollectionIds: Set<String>,
            privateCollectionIds: Set<String>,
            source: SoundSource,
        ): Deferred<Int> = CompletableDeferred(R.string.app_addbutton_feedback_saved_ok)

        override fun renameButtonAsync(
            context: Context,
            sound: Sound,
            newName: String,
        ): Deferred<Unit> = CompletableDeferred(Unit)
    }

    private companion object {
        val SAMPLE_URI: Uri = Uri.parse("content://test/audio.mp3")
        const val EXISTING_NAME = "Existing sound"
        const val WAIT_TIMEOUT_MS = 5_000L
    }
}
