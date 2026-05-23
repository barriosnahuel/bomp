/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.collections

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
 * End-to-end coverage for the long-press → "Asignar a colecciones" sheet (ADR 0012).
 *
 * The sheet is **transactional**: chip toggles and the "Visible en Mis Bomps" switch stage locally
 * and apply on "Listo" (backing out discards). The flows under test:
 *
 * - Menu entry shows; the sheet opens with the chip UI + Vault group.
 * - **Dual-home:** tagging an audio into the Baúl while keeping it visible leaves it in My Bomps
 *   (the headline fix — tagging private no longer silently removes it).
 * - **Move out:** turning the visibility switch OFF (only allowed once it's in the Vault) removes it
 *   from My Bomps on "Listo".
 */
@RunWith(AndroidJUnit4::class)
internal class AssignCollectionSheetFlowTest : AbstractUiTest() {
    @Test
    fun longPressMenuExposesAddToCollectionEntry() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia")

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun tappingAddToCollectionOpensTheSheetWithChips() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.seedPublicCollection(context, name = "Familia")
        TestData.markVaultOpen()
        TestData.touchPrivateCollections(context)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()

            // Subtitle proves the sheet rendered; the Baúl chip proves the chip UI (and the unlocked
            // Vault group) is showing. The Baúl label is unique to the sheet.
            composeRule.awaitNodeWithText(sheetSubtitle()).assertIsDisplayed()
            composeRule.awaitNodeWithText(systemBaulLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun taggingToBaulWhileVisibleKeepsTheAudioInMySounds() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.markVaultOpen()
        TestData.touchPrivateCollections(context)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).assertIsDisplayed()

            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()
            // Stage into the Baúl, keep visibility ON, confirm with "Listo".
            composeRule.awaitNodeWithText(systemBaulLabel()).performClick()
            composeRule.awaitNodeWithText(doneLabel()).performClick()

            // Dual-home: it lives in the Vault AND stays in My Bomps.
            composeRule.awaitNodeWithText(sound.name).assertIsDisplayed()
        }
    }

    @Test
    fun turningOffVisibilityMovesTheAudioOutOfMySounds() {
        val sound = TestData.seedCustomSounds(context, count = 1).single()
        TestData.markVaultOpen()
        TestData.touchPrivateCollections(context)

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(sound.name).assertIsDisplayed()

            composeRule.awaitNodeWithText(sound.name).performTouchInput { longClick() }
            composeRule.awaitNodeWithText(addToCollectionLabel()).performClick()
            // Stage into the Baúl first (so the switch can be turned off), then turn it off + confirm.
            composeRule.awaitNodeWithText(systemBaulLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(visibleLabel()).performClick()
            composeRule.awaitNodeWithText(doneLabel()).performClick()

            // Hidden from My Bomps now (lives only in the Vault).
            composeRule.waitUntil(SHEET_DISMISS_TIMEOUT_MS) {
                composeRule.onAllNodesWithText(sound.name).fetchSemanticsNodes().isEmpty()
            }
        }
    }

    private fun addToCollectionLabel() = context.getString(R.string.app_audio_menu_add_to_collection)

    private fun sheetSubtitle() = context.getString(R.string.app_assign_collection_sheet_subtitle)

    private fun visibleLabel() = context.getString(R.string.app_assign_visible_label)

    private fun systemBaulLabel() = context.getString(R.string.app_vault_baul_name)

    private fun doneLabel() = context.getString(R.string.app_assign_collection_sheet_done)

    companion object {
        private const val SHEET_DISMISS_TIMEOUT_MS = 5_000L
    }
}
