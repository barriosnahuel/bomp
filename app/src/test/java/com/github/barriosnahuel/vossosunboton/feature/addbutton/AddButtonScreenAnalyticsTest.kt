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
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
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

    @Test
    fun `save success shows the confirmation overlay with the bomp name and subtitle`() {
        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()

            val expectedAnnouncement = context.getString(R.string.app_feedback_button_saved, NEW_NAME)
            composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeTestRule
                    .onAllNodesWithContentDescription(expectedAnnouncement)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule.onNodeWithContentDescription(expectedAnnouncement).assertIsDisplayed()
            // Brand subtitle is the visible "ahora es tuyo" / "is now yours" below the name.
            composeTestRule
                .onNodeWithText(context.getString(R.string.app_addbutton_overlay_subtitle_saved))
                .assertIsDisplayed()
        }
    }

    @Test
    fun `rename success shows the confirmation overlay with the renamed subtitle`() {
        ActivityScenario.launch<AddButtonActivity>(editIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule
                .onNodeWithText(context.getString(R.string.app_addbutton_save_changes))
                .performClick()

            val expectedAnnouncement = context.getString(R.string.app_feedback_button_renamed, EXISTING_NAME)
            composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeTestRule
                    .onAllNodesWithContentDescription(expectedAnnouncement)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            composeTestRule
                .onNodeWithText(context.getString(R.string.app_addbutton_overlay_subtitle_renamed))
                .assertIsDisplayed()
        }
    }

    @Test
    fun `save success shows the confirmation overlay under reduce motion when ANIMATOR_DURATION_SCALE is zero`() {
        // Verifies the overlay's content reaches the user even with Remove Animations ON. The full
        // entry+hold+exit timing and the subsequent finish() are covered by the instrumented test on
        // device — Robolectric's paused looper makes the Activity lifecycle transition flaky here.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
                composeTestRule.waitForIdle()
                composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
                composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()

                val expectedAnnouncement = context.getString(R.string.app_feedback_button_saved, NEW_NAME)
                composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                    composeTestRule
                        .onAllNodesWithContentDescription(expectedAnnouncement)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
                }
                composeTestRule.onNodeWithContentDescription(expectedAnnouncement).assertIsDisplayed()
            }
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `save failure shows snackbar with retry action and keeps user on form`() {
        feature.saveNewFeedback = R.string.app_addbutton_feedback_save_failed

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()

            val errorMessage = context.getString(R.string.app_addbutton_feedback_save_failed)
            composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(errorMessage).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule
                .onNodeWithText(context.getString(R.string.app_snackbar_action_retry))
                .assertIsDisplayed()
            // Activity must remain RESUMED so the user can retry without re-typing.
            assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun `tapping retry on the error snackbar runs save again`() {
        feature.saveNewFeedback = R.string.app_addbutton_feedback_save_failed

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeTestRule
                    .onAllNodesWithText(context.getString(R.string.app_snackbar_action_retry))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            // Flip the feature to succeed before retrying so this test does not loop on errors.
            feature.saveNewFeedback = R.string.app_addbutton_feedback_saved_ok
            composeTestRule.onNodeWithText(context.getString(R.string.app_snackbar_action_retry)).performClick()
            composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { feature.saveNewCalls >= 2 }

            assertThat(feature.saveNewCalls).isEqualTo(2)
            fake.assertNotEmitted("sound_add_abandoned_after_error")
        }
    }

    @Test
    fun `stopping the activity with an unresolved error tracks abandonment once`() {
        feature.saveNewFeedback = R.string.app_addbutton_feedback_save_failed

        ActivityScenario.launch<AddButtonActivity>(createIntent()).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
                composeTestRule
                    .onAllNodesWithText(context.getString(R.string.app_snackbar_action_retry))
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            scenario.moveToState(Lifecycle.State.CREATED)

            val abandoned = fake.assertEmitted("sound_add_abandoned_after_error")
            assertThat(abandoned.params["reason"]).isEqualTo("save_failed")
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
