/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.app.ActivityOptionsCompat
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.feature.playback.PlaybackState
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerController
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * The import Hub's two-path flow on the real Landing graph: Hub → bring guide → the guide's file-browser
 * footer → the picker result. The SAF picker is swapped for an in-process [ActivityResultRegistry] that
 * records what was launched and answers synchronously, so the result lands on the same composition.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class LandingImportHubFlowTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var fake: FakeAnalyticsTracker
    private val picker = FakePickerRegistry()
    private val createdViewModels = mutableListOf<SoundsViewModel>()
    private val playbackStateFlow = MutableStateFlow<PlaybackState?>(null)
    private lateinit var originalController: PlayerController
    private var backDispatcherOwner: OnBackPressedDispatcherOwner? = null

    @Before
    fun setUp() {
        fake = FakeAnalyticsTracker()
        AnalyticsTrackerProvider.setForTest(fake)
        originalController = PlayerControllerFactory.instance
        PlayerControllerFactory.instance =
            mockk(relaxed = true) {
                every { playbackState } returns playbackStateFlow
            }
        // The guide reuses the onboarding IMPORT demo, whose looping animation would otherwise keep the
        // Compose clock busy past waitForIdle.
        Settings.Global.putFloat(
            ApplicationProvider.getApplicationContext<Context>().contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
    }

    @After
    fun tearDown() {
        createdViewModels.cancelAndJoinAll()
        createdViewModels.clear()
        PlayerControllerFactory.instance = originalController
        AnalyticsTrackerProvider.setForTest(null)
        unmockkAll()
    }

    @Test
    fun `Find it on your phone launches the system picker filtered to audio`() {
        givenLanding()
        openGuide()

        composeTestRule.onNodeWithText(FILES_CTA).performClick()
        composeTestRule.waitForIdle()

        assertThat(picker.launchedInputs).hasSize(1)
        assertThat(picker.launchedInputs.single() as Array<*>).asList().containsExactly("audio/*")
        // The old Hub row's event is not re-pointed here: that would silently redefine its history.
        fake.assertNotEmitted("import_hub_import_selected")
    }

    @Test
    fun `double-tapping Find it on your phone launches a single picker until it answers`() {
        givenLanding()
        openGuide()
        picker.answerImmediately = false

        composeTestRule.onNodeWithText(FILES_CTA).performClick()
        composeTestRule.onNodeWithText(FILES_CTA).performClick()
        composeTestRule.waitForIdle()
        assertThat(picker.launchedInputs).hasSize(1)

        // Once the picker answers, the footer launches again.
        composeTestRule.runOnUiThread { picker.answer(null) }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(FILES_CTA).performClick()
        composeTestRule.waitForIdle()
        assertThat(picker.launchedInputs).hasSize(2)
    }

    @Test
    fun `an empty picker result keeps the guide up and shows the notice`() {
        givenLanding()
        openGuide()

        pickNothing()

        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithText(GUIDE_CTA).assertIsDisplayed()
    }

    @Test
    fun `a recreate keeps the guide but does not replay an expired notice`() {
        val viewModel = givenAViewModel()
        val restorationTester = StateRestorationTester(composeTestRule)
        restorationTester.setContent { LandingUnderTest(viewModel) }
        composeTestRule.waitForIdle()
        viewModel.withOneSound()
        openGuide()
        pickNothing()
        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()

        restorationTester.emulateSavedInstanceStateRestore()
        composeTestRule.waitForIdle()

        // Rotation doesn't recreate Landing (configChanges), so a restore here means a process death or a
        // theme/locale change — by then the 10 s notice is stale, and replaying it would contradict what the
        // user just did. The guide itself is durable progress and stays.
        composeTestRule.onAllNodesWithText(EMPTY_RESULT_MESSAGE).assertCountEquals(0)
        composeTestRule.onNodeWithText(GUIDE_CTA).assertIsDisplayed()
    }

    @Test
    fun `a second empty result while the notice is up shows it afresh`() {
        givenLanding()
        openGuide()
        pickNothing()
        composeTestRule.mainClock.advanceTimeBy(HALF_A_NOTICE_MS)

        pickNothing()
        composeTestRule.mainClock.advanceTimeBy(HALF_A_NOTICE_MS + 2_000L)

        // Past the first notice's own 10 s: the second empty result got its own, rather than being swallowed.
        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()
    }

    @Test
    fun `a device with no file browser keeps the app alive and points at sharing`() {
        givenLanding()
        openGuide()
        picker.throwOnLaunch = true

        composeTestRule.onNodeWithText(FILES_CTA).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()
        verify { Tracker.track(any()) }
        // The in-flight guard was released, so the footer is not left dead for the rest of the visit.
        picker.throwOnLaunch = false
        composeTestRule.onNodeWithText(FILES_CTA).performClick()
        composeTestRule.waitForIdle()
        assertThat(picker.launchedInputs).hasSize(2)
    }

    @Test
    fun `tapping the footer right after Got it does not open the picker from the closing guide`() {
        givenLanding()
        openGuide()

        // A paused clock keeps the closing guide on screen and tappable, like its exit crossfade does.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText(GUIDE_CTA).performClick()
        composeTestRule.onNodeWithText(FILES_CTA).performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertThat(picker.launchedInputs).isEmpty()
    }

    @Test
    fun `back from the guide returns to My Bomps`() {
        givenLanding()
        openGuide()

        pressBack()

        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(GUIDE_CTA).assertCountEquals(0)
    }

    @Test
    fun `a notice left pending does not resurface on the next visit to the guide`() {
        givenLanding()
        openGuide()
        pickNothing()
        composeTestRule.onNodeWithText(EMPTY_RESULT_MESSAGE).assertIsDisplayed()

        // Leave while the notice is still up, then come back through the Hub.
        pressBack()
        openGuide()

        composeTestRule.onAllNodesWithText(EMPTY_RESULT_MESSAGE).assertCountEquals(0)
    }

    @Test
    fun `double-tapping the FAB opens the Hub once`() {
        givenLanding()

        // A paused clock keeps both taps inside one frame, before the open recomposes the sheet in.
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).performClick()
        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertThat(fake.events.count { it.name == "import_hub_opened" }).isEqualTo(1)
    }

    @Test
    fun `double-tapping the bring row opens the guide once`() {
        givenLanding()
        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithText(BRING_ROW).performClick()
        composeTestRule.onNodeWithText(BRING_ROW).performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertThat(fake.events.count { it.name == "import_hub_bring_selected" }).isEqualTo(1)
        // One guide on the stack: a single back lands on the tab, not on a second copy of the guide.
        pressBack()
        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).assertIsDisplayed()
        composeTestRule.onAllNodesWithText(GUIDE_CTA).assertCountEquals(0)
    }

    @Composable
    private fun LandingUnderTest(viewModel: SoundsViewModel) {
        backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
        val registryOwner =
            object : ActivityResultRegistryOwner {
                override val activityResultRegistry: ActivityResultRegistry = picker
            }
        CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
            AppTheme { LandingScreen(viewModel) }
        }
    }

    private fun givenLanding() {
        val viewModel = givenAViewModel()
        composeTestRule.setContent { LandingUnderTest(viewModel) }
        composeTestRule.waitForIdle()
        viewModel.withOneSound()
    }

    // The + FAB only renders over a non-empty list. Don't rely on the welcome audio for that: another class in
    // the same JVM may already have consumed it, which leaves My Bomps on its empty state with no FAB.
    private fun SoundsViewModel.withOneSound() {
        injectSounds(listOf(testSound(SOUND_NAME, file = "$SOUND_NAME.mp3")))
        composeTestRule.waitForIdle()
    }

    // Drives the rendered list. Injected after the first composition so init's loadSounds cascade (which
    // repopulates it) has already settled.
    @Suppress("UNCHECKED_CAST")
    private fun SoundsViewModel.injectSounds(value: List<Sound>) {
        SoundsViewModel::class.java
            .getDeclaredField("_sounds")
            .also { it.isAccessible = true }
            .let { (it.get(this) as MutableStateFlow<List<Sound>>).value = value }
    }

    /** + FAB → Hub → "bring in an audio you already have" → the guide. A fresh install seeds the welcome audio, so the FAB shows. */
    private fun openGuide() {
        composeTestRule.onNodeWithContentDescription(FAB_DESCRIPTION).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(BRING_ROW).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(GUIDE_CTA).assertIsDisplayed()
    }

    private fun pickNothing() {
        picker.nextResult = null
        composeTestRule.onNodeWithText(FILES_CTA).performClick()
        composeTestRule.waitForIdle()
    }

    /** Drives the real system back dispatcher, so `NavDisplay`'s onBack (not a test shortcut) decides. */
    private fun pressBack() {
        composeTestRule.runOnUiThread {
            backDispatcherOwner?.onBackPressedDispatcher?.onBackPressed()
        }
        composeTestRule.waitForIdle()
    }

    private fun givenAViewModel(): SoundsViewModel {
        val vm =
            SoundsViewModel(
                ApplicationProvider.getApplicationContext(),
                ioDispatcher = UnconfinedTestDispatcher(),
            )
        createdViewModels += vm
        runBlocking { withTimeout(LOAD_TIMEOUT_MS) { vm.isInitialLoadComplete.first { it } } }
        return vm
    }

    /**
     * Stands in for the SAF picker: records each launch input and answers [nextResult] synchronously, or,
     * with [answerImmediately] off, holds the request open until [answer] — a picker still on screen.
     */
    private class FakePickerRegistry : ActivityResultRegistry() {
        val launchedInputs = mutableListOf<Any?>()
        var nextResult: Uri? = null
        var answerImmediately = true
        var throwOnLaunch = false
        private var pendingRequestCode: Int? = null

        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            launchedInputs += input
            // What ComponentActivity's registry does when nothing resolves ACTION_OPEN_DOCUMENT.
            if (throwOnLaunch) throw ActivityNotFoundException("no SAF handler")
            if (answerImmediately) dispatchResult(requestCode, nextResult) else pendingRequestCode = requestCode
        }

        fun answer(result: Uri?) {
            val requestCode = checkNotNull(pendingRequestCode) { "no picker is open" }
            pendingRequestCode = null
            dispatchResult(requestCode, result)
        }
    }

    private companion object {
        const val LOAD_TIMEOUT_MS = 5_000L
        const val SOUND_NAME = "Existing Bomp"
        const val FAB_DESCRIPTION = "Add a Bomp"
        const val BRING_ROW = "Bring in an audio you already have"
        const val GUIDE_CTA = "Got it"
        const val FILES_CTA = "Find it on your phone"
        const val EMPTY_RESULT_MESSAGE = "Wasn't it there? In WhatsApp, press and hold the note, tap Share and pick Bomp."
        const val HALF_A_NOTICE_MS = 5_000L
    }
}
