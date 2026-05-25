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
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
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
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class AddButtonScreenHapticTest : AbstractRobolectricTest() {
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
    fun `tapping Save with a blank name triggers reject haptic and keeps the user on the form`() {
        ActivityScenario.launch<AddButtonActivity>(createIntent()).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.waitForIdle()

            var lastHaptic = HAPTIC_NEVER_PERFORMED
            scenario.onActivity { activity ->
                lastHaptic = activity.window.decorView.findHapticPerformed()
            }

            assertThat(lastHaptic).isEqualTo(HapticFeedbackConstants.REJECT)
            assertThat(feature.saveNewCalls).isEqualTo(0)
            composeTestRule
                .onNodeWithText(context.getString(R.string.app_addbutton_name_is_required_error))
                .assertIsDisplayed()
        }
    }

    @Test
    fun `tapping Save with a valid name does not trigger reject haptic`() {
        ActivityScenario.launch<AddButtonActivity>(createIntent()).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.onNode(hasSetTextAction()).performTextInput(NEW_NAME)
            composeTestRule.onNodeWithText(context.getString(R.string.app_addbutton_save)).performClick()
            composeTestRule.waitForIdle()

            var lastHaptic = HAPTIC_NEVER_PERFORMED
            scenario.onActivity { activity ->
                lastHaptic = activity.window.decorView.findHapticPerformed()
            }

            // The contract guarded here is "REJECT is reserved for failed validation".
            // Other haptic constants (CONFIRM from the success overlay, etc.) are out of scope for this test.
            assertThat(lastHaptic).isNotEqualTo(HapticFeedbackConstants.REJECT)
            assertThat(feature.saveNewCalls).isEqualTo(1)
        }
    }

    // Walks the view tree depth-first and returns the first non-default haptic constant recorded by any
    // ShadowView, since `performHapticFeedback` is called on `LocalView.current` (the AndroidComposeView)
    // rather than on the decorView. Default value `-1` means "no haptic performed yet" on that view.
    private fun View.findHapticPerformed(): Int {
        val ownHaptic = Shadows.shadowOf(this).lastHapticFeedbackPerformed()
        return when {
            ownHaptic != HAPTIC_NEVER_PERFORMED -> ownHaptic
            this is ViewGroup ->
                (0 until childCount)
                    .asSequence()
                    .map { getChildAt(it).findHapticPerformed() }
                    .firstOrNull { it != HAPTIC_NEVER_PERFORMED }
                    ?: HAPTIC_NEVER_PERFORMED
            else -> HAPTIC_NEVER_PERFORMED
        }
    }

    private fun createIntent(): Intent =
        Intent(context, AddButtonActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, SAMPLE_URI)
        }

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
        const val HAPTIC_NEVER_PERFORMED = -1
        val SAMPLE_URI: Uri = Uri.parse("content://test/audio.mp3")
        const val NEW_NAME = "Test sound"
    }
}
