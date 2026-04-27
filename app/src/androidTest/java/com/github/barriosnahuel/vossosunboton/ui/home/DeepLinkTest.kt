/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class DeepLinkTest : AbstractUiTest() {
    @Test
    fun homeDeeplinkLandsOnHomeTab() {
        val bundled = PackagedAudios.get(context)
        assumeTrue(
            "Need bundled sounds so the BottomBar (and an Explore tab to differ from) is visible.",
            bundled.isNotEmpty(),
        )
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch<LandingActivity>(deeplink("/home")).use {
            composeRule.waitForIdle()
            // Custom sound is on Home; bundled sound is on Explore.
            composeRule.onNodeWithText("custom_1").assertIsDisplayed()
            composeRule.onAllNodes(hasText(bundled.first().name)).fetchSemanticsNodes().let {
                assert(it.isEmpty()) { "Bundled sound should not be visible on Home." }
            }
        }
    }

    @Test
    fun exploreDeeplinkWithBundledLandsOnExploreTab() {
        val bundled = PackagedAudios.get(context)
        assumeTrue(
            "Need bundled sounds for the Explore tab to be reachable.",
            bundled.isNotEmpty(),
        )
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch<LandingActivity>(deeplink("/explore")).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(bundled.first().name).assertIsDisplayed()
            composeRule.onAllNodes(hasText("custom_1")).fetchSemanticsNodes().let {
                assert(it.isEmpty()) { "Custom sound should not be visible on Explore." }
            }
        }
    }

    @Test
    fun exploreDeeplinkWithoutBundledFallsBackToHome() {
        assumeFalse(
            "This test verifies the no-bundled fallback. Skip when local raw/ is populated.",
            PackagedAudios.get(context).isNotEmpty(),
        )
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch<LandingActivity>(deeplink("/explore")).use {
            composeRule.waitForIdle()
            // Custom sound (Home content) is visible.
            composeRule.onNodeWithText("custom_1").assertIsDisplayed()
            // BottomBar is hidden since hasBundledSounds == false.
            composeRule.onAllNodesWithContentDescription(exploreTabLabel()).fetchSemanticsNodes().let {
                assert(it.isEmpty()) { "BottomBar should be hidden when there are no bundled sounds." }
            }
        }
    }

    @Test
    fun unknownPathDeeplinkRoutesLikeExplore() {
        val bundled = PackagedAudios.get(context)
        assumeTrue(
            "Need bundled sounds so the unknown-path fallback to Explore is observable.",
            bundled.isNotEmpty(),
        )

        ActivityScenario.launch<LandingActivity>(deeplink("/anything-unknown")).use {
            composeRule.waitForIdle()
            composeRule.onNodeWithText(bundled.first().name).assertIsDisplayed()
        }
    }

    private fun deeplink(path: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open$path")).apply {
            setClassName(context, LandingActivity::class.java.name)
        }

    private fun exploreTabLabel() = context.getString(R.string.app_navigation_menu_item_explore_sounds)
}
