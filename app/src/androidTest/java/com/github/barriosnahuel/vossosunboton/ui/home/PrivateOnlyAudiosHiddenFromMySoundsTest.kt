/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for the "private-only audios stay in the Vault" rule (spec § 1).
 *
 * The first iteration leaked private-only audios into My Sounds because `loadSounds` joined the
 * full library against the welcome flag but not against the private collections set. The fix
 * lives in `SoundsViewModel.privateOnlyAudioIds()`. The cross-tagged case (audio belongs to BOTH
 * a public and a private collection) is intentionally NOT hidden — spec § 3.1's "cross-preset"
 * restriction says public visibility wins.
 *
 * Two tests here lock both ends of the contract:
 * - `audioOnlyInAPrivateCollectionIsHiddenFromMySounds`: the bug we paid for.
 * - `audioCrossTaggedInPublicAndPrivateStaysVisibleOnMySounds`: the explicit exemption.
 */
@RunWith(AndroidJUnit4::class)
internal class PrivateOnlyAudiosHiddenFromMySoundsTest : AbstractUiTest() {
    @Test
    fun audioOnlyInAPrivateCollectionIsHiddenFromMySounds() {
        val (publicAudio, privateAudio) = TestData.seedCustomSounds(context, count = 2)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(publicAudio.id))
        TestData.seedPrivateCollection(context, name = "Caro", audioIds = listOf(privateAudio.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(publicAudio.name).assertIsDisplayed()
            // The private-only audio must NOT appear on My Sounds — even though it exists in the
            // library, the projection step filters it out.
            composeRule.waitUntil(LOAD_TIMEOUT_MS) {
                composeRule.onAllNodes(hasText(publicAudio.name)).fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onAllNodesWithText(privateAudio.name).assertCountEquals(0)
        }
    }

    @Test
    fun audioCrossTaggedInPublicAndPrivateStaysVisibleOnMySounds() {
        val (crossTagged) = TestData.seedCustomSounds(context, count = 1)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(crossTagged.id))
        TestData.seedPrivateCollection(context, name = "Caro", audioIds = listOf(crossTagged.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            // Visible on My Sounds: cross-tagged audios surface from their public side.
            composeRule.awaitNodeWithText(crossTagged.name).assertIsDisplayed()
        }
    }

    companion object {
        private const val LOAD_TIMEOUT_MS = 5_000L
    }
}
