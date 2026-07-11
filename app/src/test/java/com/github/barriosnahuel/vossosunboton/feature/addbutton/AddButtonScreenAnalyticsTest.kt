/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraft
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraftStore
import com.github.barriosnahuel.vossosunboton.feature.recorder.RecorderDraftStoreProvider
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.SoundSource
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config
import java.io.File

@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class AddButtonScreenAnalyticsTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private lateinit var fake: FakeAnalyticsTracker
    private lateinit var feature: FakeAddButtonFeature
    private lateinit var draftStore: FakeRecorderDraftStore

    private val screenMounted = mutableStateOf(true)

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        feature = FakeAddButtonFeature()
        AddButtonFeatureProvider.setForTest(feature)
        draftStore = FakeRecorderDraftStore()
        RecorderDraftStoreProvider.setForTest(draftStore)
    }

    @After
    fun tearDown() {
        disposeAddButtonScreen(composeTestRule, screenMounted)
        AnalyticsTrackerProvider.setForTest(null)
        AddButtonFeatureProvider.setForTest(null)
        RecorderDraftStoreProvider.setForTest(null)
    }

    @Test
    fun `the name field is auto focused on entry so the keyboard opens without an extra tap`() {
        setCreateScreen()

        composeTestRule.onNode(hasSetTextAction()).assertIsFocused()
    }

    @Test
    fun `Edit mode places the cursor at the end of the existing name so the user can append immediately`() {
        setEditScreen()

        val node = composeTestRule.onNode(hasSetTextAction()).fetchSemanticsNode()
        val selection = node.config[SemanticsProperties.TextSelectionRange]
        assertThat(selection.start).isEqualTo(EXISTING_NAME.length)
        assertThat(selection.end).isEqualTo(EXISTING_NAME.length)
    }

    @Test
    fun `Create mode emits screen_view add_sound on first composition`() {
        setCreateScreen()

        val screen = fake.assertScreenView(CanonicalScreenName.ADD_SOUND)
        assertThat(screen.extras["source"]).isEqualTo(AddSoundSource.SHARE)
    }

    @Test
    fun `Edit mode emits screen_view edit_sound on first composition`() {
        setEditScreen()

        fake.assertScreenView(CanonicalScreenName.EDIT_SOUND)
    }

    @Test
    fun `saving a new button emits sound_add and does not emit sound_edit`() {
        setCreateScreen()
        typeNameAndSave()

        assertThat(feature.saveNewCalls).isEqualTo(1)
        val event = fake.assertEmitted("sound_add")
        assertThat(event.params["source"]).isEqualTo(AddSoundSource.SHARE)
        assertThat(event.params["name_length"]).isEqualTo(NEW_NAME.length)
        fake.assertNotEmitted("sound_edit")
    }

    @Test
    fun `saving a Bomp brought in from the import Hub tags sound_add with the import source`() {
        // Create opened from the in-app import Hub must thread the import source all the way to the
        // sound_add event, not fall back to the share-sheet default.
        setCreateScreen(source = AddSoundSource.IMPORT)
        typeNameAndSave()

        val event = fake.assertEmitted("sound_add")
        assertThat(event.params["source"]).isEqualTo(AddSoundSource.IMPORT)
    }

    @Test
    fun `saving a recorded Bomp clears its recovery draft so the banner won't re-offer it`() {
        setCreateScreen(source = AddSoundSource.RECORD)
        typeNameAndSave()

        assertThat(draftStore.clearCount).isAtLeast(1)
    }

    @Test
    fun `saving an imported Bomp leaves the recorder draft untouched`() {
        setCreateScreen(source = AddSoundSource.IMPORT)
        typeNameAndSave()

        assertThat(draftStore.clearCount).isEqualTo(0)
    }

    @Test
    fun `renaming an existing button emits sound_edit and does not emit sound_add`() {
        setEditScreen()

        composeTestRule
            .onNodeWithText(context.getString(R.string.app_addbutton_save_changes))
            .performClick()
        composeTestRule.waitForIdle()

        val event = fake.assertEmitted("sound_edit")
        assertThat(event.params["name_changed"]).isEqualTo(false)
        assertThat(event.params["name_length"]).isEqualTo(EXISTING_NAME.length)
        fake.assertNotEmitted("sound_add")
    }

    @Test
    fun `Create mode invokes preview path for incoming URI without crashing the tree`() {
        // Symmetry contract with Edit mode: AudioPreview's LaunchedEffect must run for the incoming URI.
        // We can't assert the play/pause Card here: SAMPLE_URI is unresolvable so MediaPlayer.prepare fails and
        // the Card stays hidden by the `if (isReady)` guard. Asserting the form below remains reachable proves
        // the LaunchedEffect didn't throw. Happy-path interactivity would need a playable test URI.
        setCreateScreen()

        composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).assertIsDisplayed()
    }

    @Test
    fun `Create flow does not emit sound_add when saveNewButtonAsync signals the generic error`() {
        feature.saveNewFeedback = R.string.app_feedback_generic_error_contact_support

        setCreateScreen()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
        composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
        composeTestRule.waitForIdle()

        assertThat(feature.saveNewCalls).isEqualTo(1)
        fake.assertNotEmitted("sound_add")
    }

    @Test
    fun `save success shows the confirmation overlay with the bomp name and subtitle`() {
        setCreateScreen()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
        composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()

        val expectedAnnouncement = context.getString(R.string.app_feedback_button_saved, NEW_NAME)
        awaitContentDescription(expectedAnnouncement)
        composeTestRule.onNodeWithContentDescription(expectedAnnouncement).assertIsDisplayed()
        // Brand subtitle is the visible "ahora es tuyo" / "is now yours" below the name.
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_addbutton_overlay_subtitle_saved))
            .assertIsDisplayed()
    }

    @Test
    fun `rename success shows the confirmation overlay with the renamed subtitle`() {
        setEditScreen()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_addbutton_save_changes))
            .performClick()

        val expectedAnnouncement = context.getString(R.string.app_feedback_button_renamed, EXISTING_NAME)
        awaitContentDescription(expectedAnnouncement)
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_addbutton_overlay_subtitle_renamed))
            .assertIsDisplayed()
    }

    @Test
    fun `name input survives Activity recreate so the user does not lose what they typed`() {
        // The OutlinedTextField is the screen's primary action — losing the user's draft on
        // rotation is a worse UX bug than the (already covered) Success overlay disappearing.
        // Edit mode pre-populates with EXISTING_NAME and places the cursor at the end, so a
        // performTextInput(TYPED_DRAFT) appends to it: post-recreate the field must still
        // contain TYPED_DRAFT, otherwise rememberSaveable on `name` regressed.
        setEditScreen()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(TYPED_DRAFT)

        recreateHost()
        setEditScreen()

        composeTestRule.onNode(hasSetTextAction()).assertTextContains(TYPED_DRAFT, substring = true)
    }

    @Test
    fun `save success overlay survives Activity recreate and still renders the saved name`() {
        // Pause the clock BEFORE clicking save so the overlay's hold cannot elapse between Success
        // appearing and the recreate; the assertion then observes the restored SaveOutcome rather
        // than a post-hold state.
        setEditScreen()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_addbutton_save_changes))
            .performClick()
        // Advance just enough for save() to flip saveOutcome to Success and for the overlay to
        // enter composition; well under the 600 ms hold.
        composeTestRule.mainClock.advanceTimeBy(CLOCK_STEP_MS)

        recreateHost()
        setEditScreen()
        composeTestRule.mainClock.advanceTimeBy(CLOCK_STEP_MS)

        val expectedAnnouncement = context.getString(R.string.app_feedback_button_renamed, EXISTING_NAME)
        composeTestRule.onNodeWithContentDescription(expectedAnnouncement).assertIsDisplayed()
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_addbutton_overlay_subtitle_renamed))
            .assertIsDisplayed()

        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun `save success shows the confirmation overlay under reduce motion when ANIMATOR_DURATION_SCALE is zero`() {
        // Verifies the overlay's content reaches the user even with Remove Animations ON. The full
        // entry+hold+exit timing is covered by the instrumented test on device — Robolectric's paused
        // looper makes the host lifecycle transition flaky here.
        Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            setCreateScreen()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()

            val expectedAnnouncement = context.getString(R.string.app_feedback_button_saved, NEW_NAME)
            awaitContentDescription(expectedAnnouncement)
            composeTestRule.onNodeWithContentDescription(expectedAnnouncement).assertIsDisplayed()
        } finally {
            Settings.Global.putFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }
    }

    @Test
    fun `save failure shows snackbar with retry action and keeps user on form`() {
        feature.saveNewFeedback = R.string.app_addbutton_feedback_save_failed

        setCreateScreen()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
        composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()

        val errorMessage = context.getString(R.string.app_addbutton_feedback_save_failed)
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            composeTestRule.onAllNodesWithText(errorMessage).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule
            .onNodeWithText(context.getString(R.string.app_snackbar_action_retry))
            .assertIsDisplayed()
        // The form stays up (no navigation away) so the user can retry without re-typing.
        composeTestRule.onNode(hasSetTextAction()).assertTextContains(NEW_NAME, substring = true)
    }

    @Test
    fun `tapping retry on the error snackbar runs save again`() {
        feature.saveNewFeedback = R.string.app_addbutton_feedback_save_failed

        setCreateScreen()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
        composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
        awaitRetryAction()

        // Flip the feature to succeed before retrying so this test does not loop on errors.
        feature.saveNewFeedback = R.string.app_addbutton_feedback_saved_ok
        composeTestRule.onNodeWithText(context.getString(R.string.app_snackbar_action_retry)).performClick()
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { feature.saveNewCalls >= 2 }

        assertThat(feature.saveNewCalls).isEqualTo(2)
        fake.assertNotEmitted("sound_add_abandoned_after_error")
    }

    @Test
    fun `stopping the host with an unresolved error tracks abandonment once`() {
        feature.saveNewFeedback = R.string.app_addbutton_feedback_save_failed

        setCreateScreen()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
        composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
        awaitRetryAction()

        composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)

        val abandoned = fake.assertEmitted("sound_add_abandoned_after_error")
        assertThat(abandoned.params["reason"]).isEqualTo("save_failed")
    }

    @Test
    fun `backing out of the naming destination with an unresolved error still tracks abandonment`() {
        feature.saveNewFeedback = R.string.app_addbutton_feedback_save_failed

        setCreateScreen()
        composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
        composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
        awaitRetryAction()

        // As a graph destination this screen leaves by being popped: it drops out of composition while the
        // hosting Activity stays RESUMED, so no ON_STOP ever arrives (Nav3 has no per-destination
        // lifecycle). Only the disposal marks the exit — without it the funnel's abandon event is lost for
        // every internal entry point (import, record, edit) and survives only on the share trampoline.
        screenMounted.value = false
        composeTestRule.waitForIdle()

        val abandoned = fake.assertEmitted("sound_add_abandoned_after_error")
        assertThat(abandoned.params["reason"]).isEqualTo("save_failed")
    }

    private fun setCreateScreen(source: String = AddSoundSource.SHARE) {
        setScreen(AddButtonMode.Create(SAMPLE_URI), source)
    }

    private fun setEditScreen() {
        // Custom sound (file != null → id = "custom:<name>") matches the real edit-flow shape,
        // which only fires for user-created Bomps. Mirrors `AddButtonScreenStrictModeTest`.
        setScreen(AddButtonMode.Edit(testSound(EXISTING_NAME, file = "existing.mp3")), AddSoundSource.SHARE)
    }

    private fun setScreen(
        mode: AddButtonMode,
        source: String,
    ) {
        val host = composeTestRule.activity
        composeTestRule.setContent {
            AppTheme {
                if (screenMounted.value) {
                    AddButtonScreen(
                        context = host,
                        mode = mode,
                        source = source,
                        onSaved = {},
                        onNavigateUp = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    /**
     * Destroy and rebuild the host Activity. The caller re-sets the content afterwards, which is what
     * the real hosts do from `onCreate` — `rememberSaveable` then restores from the Activity's
     * SavedStateRegistry, so the assertion after it observes genuinely-restored state.
     */
    private fun recreateHost() {
        composeTestRule.activityRule.scenario.recreate()
    }

    private fun typeNameAndSave() {
        composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
        composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) { fake.events.isNotEmpty() }
    }

    private fun awaitRetryAction() {
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithText(context.getString(R.string.app_snackbar_action_retry))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
    }

    private fun awaitContentDescription(description: String) {
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MS) {
            composeTestRule
                .onAllNodesWithContentDescription(description)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
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
            source: SoundSource,
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

    /** Records whether the save pipeline cleared the recorder draft (ADR 0019 § Draft recovery). */
    private class FakeRecorderDraftStore : RecorderDraftStore {
        var clearCount = 0
        override val draft: Flow<RecorderDraft?> = MutableStateFlow(null)

        override suspend fun current(): RecorderDraft? = null

        override fun save(
            file: File,
            durationMs: Long,
        ) = Unit

        override fun clear() {
            clearCount += 1
        }
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
