/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Flow guard for Vault listen mode (`CollectionPlaybackUI.IMMERSIVE`).
 *
 * The Vault list stays identical to My Sounds, but tapping play opens the full-screen, listen-only
 * player instead of the inline card transport. The Robolectric layer covers the rendering and the
 * label/omission contract; this instrumented variant exercises the real navigation wiring
 * (list → overlay → back) and the durable-state survival across an Activity recreate, neither of
 * which is reachable from the Robolectric stateless-screen tests.
 *
 * Vault is force-opened via [TestData.markVaultOpen] so the test does not depend on a biometric
 * being configured on the emulator (the gate falls through to "open directly" on unprotected
 * devices, spec § 6).
 */
@RunWith(AndroidJUnit4::class)
internal class VaultImmersiveListenTest : AbstractUiTest() {
    @Test
    fun tappingPlayOnAVaultAudioOpensImmersiveListenMode() {
        seedVaultAudio()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(AUDIO_NAME).assertIsDisplayed()
            composeRule.awaitNodeWithContentDescription(playLabel()).performClick()

            composeRule.awaitNodeWithText(listenModeLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun backFromImmersiveListenModeReturnsToTheVaultList() {
        seedVaultAudio()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(playLabel()).performClick()
            composeRule.awaitNodeWithText(listenModeLabel()).assertIsDisplayed()

            composeRule.awaitNodeWithContentDescription(backLabel()).performClick()

            // Back closes listen mode and reveals the Vault list again.
            composeRule.awaitNodeWithText(AUDIO_NAME).assertIsDisplayed()
        }
    }

    @Test
    fun immersiveListenModeSurvivesActivityRecreate() {
        seedVaultAudio()

        ActivityScenario.launch(LandingActivity::class.java).use { scenario ->
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(playLabel()).performClick()
            composeRule.awaitNodeWithText(listenModeLabel()).assertIsDisplayed()

            scenario.recreate()

            // The rememberSaveable overlay id restores; the host re-resolves the audio from library.
            composeRule.awaitNodeWithText(listenModeLabel()).assertIsDisplayed()
        }
    }

    private fun seedVaultAudio() {
        val (audio) = TestData.seedCustomSounds(context, count = 1)
        TestData.seedPrivateCollection(context, name = "Caro", audioIds = listOf(audio.id))
        TestData.markVaultOpen()
    }

    private fun vaultLabel() = context.getString(R.string.app_navigation_menu_item_vault)

    private fun playLabel() = context.getString(R.string.app_play)

    private fun backLabel() = context.getString(R.string.app_vault_immersive_back)

    private fun listenModeLabel() = context.getString(R.string.app_vault_immersive_mode_label).uppercase()

    private companion object {
        // seedCustomSounds names sounds custom_1, custom_2, ... — count = 1 yields custom_1.
        const val AUDIO_NAME = "custom_1"
    }
}
