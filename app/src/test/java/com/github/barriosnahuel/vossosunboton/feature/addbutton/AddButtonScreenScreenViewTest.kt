/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.github.barriosnahuel.vossosunboton.testSound
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.robolectric.annotation.Config

/**
 * Pins that `screen_view {add_sound|edit_sound}` is emitted by [AddButtonScreen] itself, composing it
 * directly — no host Activity. A manual GA4 `screen_view` logged from `Activity.onCreate` (before the
 * Activity is in focus) is silently dropped by the SDK, so Activity-level logging never reaches the
 * export; the Activity-launching tests can't catch that regression because the fake records the call
 * either way.
 */
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class AddButtonScreenScreenViewTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val fake = FakeAnalyticsTracker()

    @Before
    fun setUp() {
        AnalyticsTrackerProvider.setForTest(fake)
    }

    @After
    fun tearDown() {
        AnalyticsTrackerProvider.setForTest(null)
    }

    @Test
    fun `Create mode logs screen_view add_sound from the composable, tagged with its source`() {
        composeTestRule.setContent {
            AppTheme {
                AddButtonScreen(
                    context = context,
                    mode = AddButtonMode.Create(Uri.parse(SAMPLE_URI)),
                    source = AddButtonActivity.SOURCE_IMPORT,
                    onSaved = {},
                    onNavigateUp = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        val screen = fake.assertScreenView(CanonicalScreenName.ADD_SOUND)
        assertThat(screen.extras["source"]).isEqualTo(AddButtonActivity.SOURCE_IMPORT)
    }

    @Test
    fun `Edit mode logs screen_view edit_sound from the composable`() {
        composeTestRule.setContent {
            AppTheme {
                AddButtonScreen(
                    context = context,
                    mode = AddButtonMode.Edit(testSound(EXISTING_NAME, file = "existing.mp3")),
                    onSaved = {},
                    onNavigateUp = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        fake.assertScreenView(CanonicalScreenName.EDIT_SOUND)
    }

    private companion object {
        const val SAMPLE_URI = "content://media/external/audio/1"
        const val EXISTING_NAME = "Existing sound"
    }
}
