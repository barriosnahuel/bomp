/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

internal class LandingActivityTest : AbstractRobolectricTest() {
    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `LandingActivity reaches RESUMED without crashing`() {
        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            assertThat(scenario.state).isEqualTo(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun `deep link with home path opens MY_SOUNDS tab`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open/home"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
            }
        }
    }

    @Test
    fun `deep link with explore path opens EXPLORE_SOUNDS tab when bundled audios exist`() {
        // Skip when this checkout has no bundled audios — the empty-Explore UX fallback would
        // mask the allowlist behaviour we want to assert. Run only on debug builds with raw/.
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        if (PackagedAudios.get(ctx).isEmpty()) {
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open/explore"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.EXPLORE_SOUNDS)
            }
        }
    }

    @Test
    fun `deep link with unknown path falls back to MY_SOUNDS tab`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open/unknown"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
            }
        }
    }

    @Test
    fun `deep link with no path falls back to MY_SOUNDS tab`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val viewModel = ViewModelProvider(activity, SoundsViewModel.Factory)[SoundsViewModel::class.java]
                assertThat(viewModel.selectedTab.value).isEqualTo(AppTab.MY_SOUNDS)
            }
        }
    }

    @Test
    fun `About overlay survives Activity recreate so rotation does not bounce the user back to MY_SOUNDS`() {
        // The About overlay is a full-screen early-return inside LandingScreen — when
        // `isAboutVisible` flips back to false on recreate, the user silently lands on MY_SOUNDS
        // without ever asking for it. Recreate has to preserve the flag so the screen stays where
        // the user left it. The back-arrow content description is unique to the About TopAppBar.
        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule
                .onNodeWithContentDescription(context.getString(R.string.app_overflow_menu))
                .performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(context.getString(R.string.app_about)).performClick()
            composeTestRule.waitForIdle()
            // Sanity check: About is open before the rotation.
            composeTestRule
                .onNodeWithContentDescription(context.getString(R.string.app_about_back))
                .assertIsDisplayed()

            scenario.recreate()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithContentDescription(context.getString(R.string.app_about_back))
                .assertIsDisplayed()
        }
    }
}
