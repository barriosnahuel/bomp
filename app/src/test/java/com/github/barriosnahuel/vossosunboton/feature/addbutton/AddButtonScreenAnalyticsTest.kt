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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.TIRAMISU])
internal class AddButtonScreenAnalyticsTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var fake: FakeAnalyticsTracker
    private lateinit var feature: FakeAddButtonFeature

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        feature = FakeAddButtonFeature()
        AddButtonFeatureProvider.setForTest(feature)
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
        AddButtonFeatureProvider.setForTest(null)
    }

    @Test
    fun `Create mode emits screen_view add_sound on first composition`() {
        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()

            val screen = fake.assertScreenView(CanonicalScreenName.ADD_SOUND)
            assertThat(screen.extras["source"]).isEqualTo(AddButtonActivity.SOURCE_SHARE)
        }
    }

    @Test
    fun `Edit mode emits screen_view edit_sound on first composition`() {
        ActivityScenario.launch<AddButtonActivity>(editIntent()).use {
            composeTestRule.waitForIdle()

            fake.assertScreenView(CanonicalScreenName.EDIT_SOUND)
        }
    }

    @Test
    fun `saving a new button emits sound_add and does not emit sound_edit`() {
        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { fake.events.isNotEmpty() }

            assertThat(feature.saveNewCalls).isEqualTo(1)
            val event = fake.assertEmitted("sound_add")
            assertThat(event.params["source"]).isEqualTo(AddButtonActivity.SOURCE_SHARE)
            assertThat(event.params["name_length"]).isEqualTo(NEW_NAME.length)
            fake.assertNotEmitted("sound_edit")
        }
    }

    @Test
    fun `renaming an existing button emits sound_edit and does not emit sound_add`() {
        ActivityScenario.launch<AddButtonActivity>(editIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule
                .onNodeWithText(context.getString(R.string.app_addbutton_save_changes))
                .performClick()
            composeTestRule.waitForIdle()

            val event = fake.assertEmitted("sound_edit")
            assertThat(event.params["name_changed"]).isEqualTo(false)
            assertThat(event.params["name_length"]).isEqualTo(EXISTING_NAME.length)
            fake.assertNotEmitted("sound_add")
        }
    }

    @Test
    fun `Create mode invokes preview path for incoming URI without crashing the tree`() {
        // Symmetry contract with Edit mode: AudioPreview's LaunchedEffect must run for the share-incoming URI.
        // We can't assert the play/pause Card here: SAMPLE_URI is unresolvable so MediaPlayer.prepare fails and
        // the Card stays hidden by the `if (isReady)` guard. Asserting the form below remains reachable proves
        // the LaunchedEffect didn't throw. Happy-path interactivity needs a playable test URI — see PR notes.
        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()

            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).assertIsDisplayed()
        }
    }

    @Test
    fun `Create flow does not emit sound_add when saveNewButtonAsync signals the generic error`() {
        feature.saveNewFeedback = R.string.app_feedback_generic_error_contact_support

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.waitForIdle()

            assertThat(feature.saveNewCalls).isEqualTo(1)
            fake.assertNotEmitted("sound_add")
        }
    }

    private fun createIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, SAMPLE_URI)
        }

    private fun editIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            putExtra(LandingActivity.EXTRA_EDIT_SOUND_NAME, EXISTING_NAME)
        }

    /**
     * Test double for [AddButtonFeature]. Returns already-completed deferreds so the Compose `coroutineScope.launch`
     * inside `save()` does not race with `composeTestRule.waitForIdle()`.
     */
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
        const val NEW_NAME = "Test sound"
        const val EXISTING_NAME = "Existing sound"
        const val WAIT_TIMEOUT_MS = 5_000L
    }
}
