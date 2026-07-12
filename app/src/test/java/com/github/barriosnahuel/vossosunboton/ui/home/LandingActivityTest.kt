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
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
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

    private fun assertOnTab(tab: AppTab) = composeTestRule.assertOnTab(tab)

    @Before
    fun setUp() {
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
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
        ActivityScenario.launch<LandingActivity>(intent).use {
            composeTestRule.waitForIdle()
            assertOnTab(AppTab.MY_SOUNDS)
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
        ActivityScenario.launch<LandingActivity>(intent).use {
            composeTestRule.waitForIdle()
            assertOnTab(AppTab.EXPLORE_SOUNDS)
        }
    }

    /** OWASP MASVS-PLATFORM-1 / CWE-939 (deep-link allowlist: unknown path never routes off Home). */
    @Test
    fun `deep link with unknown path falls back to MY_SOUNDS tab`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open/unknown"))
        ActivityScenario.launch<LandingActivity>(intent).use {
            composeTestRule.waitForIdle()
            assertOnTab(AppTab.MY_SOUNDS)
        }
    }

    @Test
    fun `deep link with no path falls back to MY_SOUNDS tab`() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open"))
        ActivityScenario.launch<LandingActivity>(intent).use {
            composeTestRule.waitForIdle()
            assertOnTab(AppTab.MY_SOUNDS)
        }
    }

    /** OWASP MASVS-PLATFORM-1 / CWE-939 (deep-link allowlist fallback survives state restoration). */
    @Test
    fun `deep link with unknown path still lands on MY_SOUNDS after Activity recreate`() {
        // Restoration guard: the saveable NavBackStack must not let a restored graph override the
        // deep-link allowlist fallback — an unknown path lands (and stays) on Home.
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open/unknown"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            composeTestRule.waitForIdle()
            scenario.recreate()
            composeTestRule.waitForIdle()
            assertOnTab(AppTab.MY_SOUNDS)
        }
    }

    @Test
    fun `rotating after arriving on a deep link does not replay it over what the user opened since`() {
        // The launching Intent survives in getIntent() across recreates. Replaying it would re-run
        // the link — which resets the tab to its root — so a rotation would silently throw away
        // whatever the user opened after arriving (here About; in the wild, an unsaved recording).
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open/home"))
        ActivityScenario.launch<LandingActivity>(intent).use { scenario ->
            composeTestRule.waitForIdle()
            openAboutFromOverflow()

            scenario.recreate()
            composeTestRule.waitForIdle()

            composeTestRule
                .onNodeWithContentDescription(context.getString(R.string.app_about_back))
                .assertIsDisplayed()
        }
    }

    @Test
    fun `back from a non-home tab returns to My Bomps before exiting`() {
        // Multi-back-stack "exit through home" (ADR 0024): from the Vault base, back crosses to the
        // Home tab instead of leaving the app; only the Home base exits.
        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.selectTab(AppTab.VAULT)

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()

            assertOnTab(AppTab.MY_SOUNDS)
            scenario.onActivity { activity -> assertThat(activity.isFinishing).isFalse() }
        }
    }

    @Test
    fun `back sequence from About over the Vault walks the Vault then My Bomps then exits`() {
        // The epic's acceptance walk: My Bomps → Vault → About → back closes About, back returns
        // to My Bomps (exit through home), back leaves the app. Driven through the Vault tab so it
        // runs everywhere — Explore needs bundled audios that CI checkouts lack (the earlier
        // Explore-based variant silently skipped there, leaving this behavior unguarded).
        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            composeTestRule.waitForIdle()
            composeTestRule.selectTab(AppTab.VAULT)
            openAboutFromOverflow()
            composeTestRule
                .onNodeWithContentDescription(context.getString(R.string.app_about_back))
                .assertIsDisplayed()

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            assertOnTab(AppTab.VAULT)

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            assertOnTab(AppTab.MY_SOUNDS)
            scenario.onActivity { activity -> assertThat(activity.isFinishing).isFalse() }

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            composeTestRule.waitForIdle()
            scenario.onActivity { activity ->
                assertThat(activity.isFinishing).isTrue()
            }
        }
    }

    private fun openAboutFromOverflow() {
        composeTestRule
            .onNodeWithContentDescription(context.getString(R.string.app_overflow_menu))
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText(context.getString(R.string.app_about)).performClick()
        composeTestRule.waitForIdle()
    }

    @Test
    fun `About overlay survives Activity recreate so rotation does not bounce the user back to MY_SOUNDS`() {
        // About is a destination on the saveable NavBackStack — if the restored stack dropped it,
        // the user would silently land on MY_SOUNDS without ever asking for it. Recreate has to
        // preserve the stack so the screen stays where the user left it. The back-arrow content
        // description is unique to the About TopAppBar.
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
