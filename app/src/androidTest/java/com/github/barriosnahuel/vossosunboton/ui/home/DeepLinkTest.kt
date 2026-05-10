/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
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
            // Custom sound is on Home; bundled sound is on Explore.
            composeRule.awaitNodeWithText("custom_1").assertIsDisplayed()
            // Bundled sound should not be visible on Home.
            composeRule.onAllNodes(hasText(bundled.first().name)).assertCountEquals(0)
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
            composeRule.awaitNodeWithText(bundled.first().name).assertIsDisplayed()
            // Custom sound should not be visible on Explore.
            composeRule.onAllNodes(hasText("custom_1")).assertCountEquals(0)
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
            // Custom sound (Home content) is visible.
            composeRule.awaitNodeWithText("custom_1").assertIsDisplayed()
            // BottomBar is hidden since hasBundledSounds == false.
            composeRule.onAllNodesWithContentDescription(exploreTabLabel()).assertCountEquals(0)
        }
    }

    @Test
    fun unknownPathDeeplinkFallsBackToHome() {
        val bundled = PackagedAudios.get(context)
        assumeTrue(
            "Need bundled sounds so MY_SOUNDS (custom) is distinguishable from EXPLORE_SOUNDS (bundled).",
            bundled.isNotEmpty(),
        )
        TestData.seedCustomSounds(context, count = 1)

        ActivityScenario.launch<LandingActivity>(deeplink("/anything-unknown")).use {
            // Per the closed deep-link allowlist in LandingActivity.handleDeeplink,
            // any unknown path must safely fall back to MY_SOUNDS — never silently route to Explore.
            composeRule.awaitNodeWithText("custom_1").assertIsDisplayed()
            // Bundled sound should not be visible since unknown paths fall back to MY_SOUNDS.
            composeRule.onAllNodes(hasText(bundled.first().name)).assertCountEquals(0)
        }
    }

    private fun deeplink(path: String): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("push-me://open$path")).apply {
            setClassName(context, LandingActivity::class.java.name)
        }

    private fun exploreTabLabel() = context.getString(R.string.app_navigation_menu_item_explore_sounds)
}
