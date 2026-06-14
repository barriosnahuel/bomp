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
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
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
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
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
    fun `the name field is auto focused on entry so the keyboard opens without an extra tap`() {
        ActivityScenario.launch<AddButtonActivity>(createIntent()).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).assertIsFocused()
        }
    }

    @Test
    fun `Edit mode places the cursor at the end of the existing name so the user can append immediately`() {
        ActivityScenario.launch<AddButtonActivity>(editIntent()).use {
            composeTestRule.waitForIdle()
            val node = composeTestRule.onNode(hasSetTextAction()).fetchSemanticsNode()
            val selection = node.config[SemanticsProperties.TextSelectionRange]
            assertThat(selection.start).isEqualTo(EXISTING_NAME.length)
            assertThat(selection.end).isEqualTo(EXISTING_NAME.length)
        }
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
    fun `saving a Bomp imported from the Hub tags sound_add with the import source`() {
        // Create launched via AddButtonActivity.createIntent (the import Hub) must thread
        // SOURCE_IMPORT all the way to the sound_add event, not fall back to the share default.
        ActivityScenario.launch<AddButtonActivity>(AddButtonActivity.createIntent(context, SAMPLE_URI)).use {
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { fake.events.isNotEmpty() }

            val event = fake.assertEmitted("sound_add")
            assertThat(event.params["source"]).isEqualTo(AddButtonActivity.SOURCE_IMPORT)
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
        // the LaunchedEffect didn't throw. Happy-path interactivity would need a playable test URI.
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
    fun `name input survives Activity recreate so the user does not lose what they typed`() {
        // The OutlinedTextField is the screen's primary action — losing the user's draft on
        // rotation is a worse UX bug than the (already covered) Success overlay disappearing.
        // Edit mode pre-populates with EXISTING_NAME and places the cursor at the end, so a
        // performTextInput(TYPED_DRAFT) appends to it: post-recreate the field must still
        // contain TYPED_DRAFT, otherwise rememberSaveable on `name` regressed.
        ActivityScenario.launch<AddButtonActivity>(editIntent()).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(TYPED_DRAFT)

            scenario.recreate()
            composeTestRule.waitForIdle()

            composeTestRule.onNode(hasSetTextAction()).assertTextContains(TYPED_DRAFT, substring = true)
        }
    }

    @Test
    fun `save success overlay survives Activity recreate and still renders the saved name`() {
        // Pause the clock BEFORE clicking save so the overlay's delay(600ms) auto-finish cannot
        // resolve between Success appearing and scenario.recreate(). Without the pause, the
        // post-recreate composition's LaunchedEffect could finish() the Activity before the
        // assertion runs.
        ActivityScenario.launch<AddButtonActivity>(editIntent()).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.mainClock.autoAdvance = false
            composeTestRule
                .onNodeWithText(context.getString(R.string.app_addbutton_save_changes))
                .performClick()
            // Advance just enough for save() to flip saveOutcome to Success and for the overlay to
            // enter composition; well under the 600 ms auto-finish window.
            composeTestRule.mainClock.advanceTimeBy(CLOCK_STEP_MS)

            scenario.recreate()
            composeTestRule.mainClock.advanceTimeBy(CLOCK_STEP_MS)

            val expectedAnnouncement = context.getString(R.string.app_feedback_button_renamed, EXISTING_NAME)
            composeTestRule.onNodeWithContentDescription(expectedAnnouncement).assertIsDisplayed()
            composeTestRule
                .onNodeWithText(context.getString(R.string.app_addbutton_overlay_subtitle_renamed))
                .assertIsDisplayed()

            composeTestRule.mainClock.autoAdvance = true
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
            // Custom sound (file != null → id = "custom:<name>") matches the real edit-flow shape,
            // which only fires for user-created Bomps. Mirrors `AddButtonScreenStrictModeTest`.
            putExtra(LandingActivity.EXTRA_EDIT_SOUND, testSound(EXISTING_NAME, file = "existing.mp3"))
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
            publicCollectionIds: Set<String>,
            privateCollectionIds: Set<String>,
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
        const val TYPED_DRAFT = "_mid_edit_typed"
        const val WAIT_TIMEOUT_MS = 5_000L
        const val CLOCK_STEP_MS = 100L
    }
}
