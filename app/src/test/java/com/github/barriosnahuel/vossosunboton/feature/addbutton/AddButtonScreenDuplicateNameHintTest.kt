/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
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
 * Covers the non-blocking duplicate-name hint introduced in PR #2 of the stable-`Sound.id` series.
 * The hint surfaces when the user types a name that matches an existing Bomp; the save button
 * stays enabled because [SoundsRepository.save] upserts by id (ADR 0008), so two same-named
 * Bomps can legitimately coexist.
 */
@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class AddButtonScreenDuplicateNameHintTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val hintText
        get() = context.getString(R.string.app_addbutton_duplicate_name_hint)
    private val playLabel
        get() = context.getString(R.string.app_addbutton_duplicate_name_hint_play_description)

    private lateinit var fake: FakeAnalyticsTracker
    private lateinit var feature: FakeAddButtonFeature

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        feature = FakeAddButtonFeature()
        AddButtonFeatureProvider.setForTest(feature)
        // Mocked so the inline-play test never actually loads a MediaPlayer; the hint only needs to
        // verify that the click routes through the unified controller (ADR 0005). `playbackState`
        // also needs an answer because `AddButtonScreen` renders `AudioPreview`, which reads it on
        // every composition pass.
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.playbackState } returns MutableStateFlow(null)
        every { PlayerControllerFactory.instance.startPlayingSound(any(), any()) } answers { nothing }
        every { PlayerControllerFactory.instance.startPlayingUri(any(), any()) } answers { nothing }
        every { PlayerControllerFactory.instance.stopPlayingSound() } answers { nothing }
        every { PlayerControllerFactory.instance.pause() } answers { nothing }
        every { PlayerControllerFactory.instance.resume() } answers { nothing }
        every { PlayerControllerFactory.instance.seekTo(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        runBlocking { SoundsRepository(context).clearForTest() }
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        AddButtonFeatureProvider.setForTest(null)
        runBlocking { SoundsRepository(context).clearForTest() }
        unmockkAll()
    }

    @Test
    fun `hint appears in Create mode when the typed name matches an existing custom Bomp`() {
        seedSound(EXISTING_NAME, "existing.mp3")

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)

            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(hintText).assertIsDisplayed()
            fake.assertEmitted("duplicate_name_hint_shown")
        }
    }

    @Test
    fun `hint matches case-insensitively and trims whitespace`() {
        seedSound("Bell", "bell.mp3")

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput("  BELL  ")

            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(hintText).assertIsDisplayed()
        }
    }

    @Test
    fun `hint stays hidden in Create mode when the typed name has no match`() {
        seedSound(EXISTING_NAME, "existing.mp3")

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput("brand new")

            composeTestRule.waitForIdle()
            composeTestRule.onAllNodesWithText(hintText).assertCountEquals(0)
            fake.assertNotEmitted("duplicate_name_hint_shown")
        }
    }

    @Test
    fun `hint stays hidden in Edit mode when the name still matches only the sound being edited`() {
        val self = seedSound(EXISTING_NAME, "existing.mp3")

        ActivityScenario.launch<AddButtonActivity>(editIntent(self)).use {
            composeTestRule.waitForIdle()

            // Field is pre-populated with the sound's own name; the only match is the sound
            // itself, which is excluded by id (ADR 0008) — the hint must not fire.
            composeTestRule.onAllNodesWithText(hintText).assertCountEquals(0)
            fake.assertNotEmitted("duplicate_name_hint_shown")
        }
    }

    @Test
    fun `hint appears in Edit mode when the typed name matches a different existing Bomp`() {
        val self = seedSound(EXISTING_NAME, "existing.mp3")
        seedSound(OTHER_NAME, "other.mp3")

        ActivityScenario.launch<AddButtonActivity>(editIntent(self)).use {
            composeTestRule.waitForIdle()
            // Replace the pre-populated name with the other existing sound's name.
            composeTestRule.onNode(hasSetTextAction()).performTextReplacement(OTHER_NAME)

            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(hintText).assertIsDisplayed()
        }
    }

    @Test
    fun `save proceeds while the duplicate-name hint is showing (non-blocking)`() {
        seedSound(EXISTING_NAME, "existing.mp3")

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }

            // The save action must fire even with the hint visible — the hint is purely informational.
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.waitForIdle()

            assertThat(feature.saveNewCalls).isEqualTo(1)
        }
    }

    @Test
    fun `tapping the inline play button fires analytics and routes through PlayerController`() {
        val match = seedSound(EXISTING_NAME, "existing.mp3")

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(EXISTING_NAME)
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(hintText).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithContentDescription(playLabel).performClick()
            composeTestRule.waitForIdle()

            fake.assertEmitted("duplicate_name_hint_play")
            verify { PlayerControllerFactory.instance.startPlayingSound(any(), match) }
        }
    }

    private fun seedSound(
        name: String,
        file: String,
    ): Sound {
        val sound = testSound(name = name, file = file)
        runBlocking { SoundsRepository(context).save(sound) }
        return sound
    }

    private fun createIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, SAMPLE_URI)
        }

    private fun editIntent(sound: Sound): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            putExtra(LandingActivity.EXTRA_EDIT_SOUND, sound)
        }

    private class FakeAddButtonFeature : AddButtonFeature {
        var saveNewFeedback: Int = R.string.app_addbutton_feedback_saved_ok
        var saveNewCalls: Int = 0

        override fun saveNewButtonAsync(
            context: Context,
            name: String,
            uri: String,
        ): Deferred<Int> {
            saveNewCalls += 1
            return CompletableDeferred(saveNewFeedback)
        }

        override fun renameButtonAsync(
            context: Context,
            sound: Sound,
            newName: String,
        ): Deferred<Unit> = CompletableDeferred(Unit)
    }

    private companion object {
        val SAMPLE_URI: Uri = Uri.parse("content://test/audio.mp3")
        const val EXISTING_NAME = "Existing Bomp"
        const val OTHER_NAME = "Other Bomp"
        const val WAIT_TIMEOUT_MS = 5_000L
    }
}
