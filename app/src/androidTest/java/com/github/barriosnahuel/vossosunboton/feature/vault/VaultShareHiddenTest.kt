/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard for the Vault's listen-only contract.
 *
 * Spec § 3.1 defines [com.github.barriosnahuel.vossosunboton.model.CollectionShareability.LISTEN_ONLY]
 * as a property of the private profile. Round-3 added the visible consequence: the per-card share
 * icon is hidden whenever a Vault audio renders. The decision is taken at the render context
 * (Vault passes `shareEnabled = false` to `SoundsList`), not per-audio — a cross-tagged audio
 * (also in a public collection) still surfaces the share icon on the My Sounds side, which is
 * exactly the behaviour the spec wants.
 *
 * Failing this test should be read as either "Vault gained a share affordance" (regression) or
 * "the contract changed" (then update both this test and the spec). The Robolectric layer covers
 * the wiring; this instrumented variant catches the case where a future copy/icon change reuses
 * the same content description on a different button, in a different surface.
 */
@RunWith(AndroidJUnit4::class)
internal class VaultShareHiddenTest : AbstractUiTest() {
    @Test
    fun audioInsideVaultDoesNotShowShareIcon() {
        val (audio) = TestData.seedCustomSounds(context, count = 1)
        TestData.seedPrivateCollection(context, name = "Caro", audioIds = listOf(audio.id))
        TestData.markVaultOpen()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(audio.name).assertIsDisplayed()

            // No share button anywhere in the Vault subtree — the only candidate node would be a
            // SoundItem's per-card share. Asserting count == 0 (vs. assertDoesNotExist) is the
            // documented way to assert "no such node" when other nodes with the same description
            // could in theory appear in the future (top bar, FAB).
            composeRule
                .onAllNodes(hasContentDescription(shareLabel()))
                .assertCountEquals(0)
        }
    }

    @Test
    fun audioCrossTaggedInPublicAndPrivateStillShowsShareOnMySounds() {
        val (audio) = TestData.seedCustomSounds(context, count = 1)
        TestData.seedPublicCollection(context, name = "Familia", audioIds = listOf(audio.id))
        TestData.seedPrivateCollection(context, name = "Caro", audioIds = listOf(audio.id))

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(audio.name).assertIsDisplayed()
            // My Sounds keeps share visible — the audio is reachable through a public collection.
            composeRule
                .onAllNodes(hasContentDescription(shareLabel()))
                .assertCountEquals(1)
        }
    }

    private fun vaultLabel() = context.getString(R.string.app_navigation_menu_item_vault)

    private fun shareLabel() = context.getString(R.string.app_share_chooser_title)
}
