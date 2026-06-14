/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
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
 * Smoke-level regressions for the Vault tab.
 *
 * The instrumented suite is the right place for these because the Vault renders against the real
 * `AppTheme` color roles (the round-3 fix removed a forced-dark subtree override) and because the
 * biometric gate's "no protection" branch — taken whenever the emulator has no fingerprint or
 * screen lock configured — produces a visually different UnlockGate than the prompt path. Both
 * branches are reachable here without configuring a biometric on the device:
 *
 * - `vaultTabAlwaysVisibleInBottomNav`: ensures the Vault item is reachable even on a brand-new
 *   install (no bundled audios, no custom audios). This invariant regressed once during round 2.
 * - `tappingVaultRevealsUnlockGate`: covers the unlocked CTA path and the inspirational ZRP that
 *   the user lands on after the per-session unlock — exercises the new "no dark theme subtree"
 *   rendering so a future hex-color regression surfaces here.
 * - `unlockingVaultRevealsInspirationalZeroResultState`: same flow as above but pre-seeds the
 *   session state so we land directly on the ZRP body. Belt + suspenders for the empty-state
 *   message; failing it means either the chip row, the FAB, or the ZRP text changed unexpectedly.
 * - `unprotectedDeviceGateOffersScreenLockShortcut`: on the (unprotected) AVD the gate must surface
 *   the secondary "set up screen lock" shortcut, since the system Settings resolves the deep link.
 */
@RunWith(AndroidJUnit4::class)
internal class VaultTabFlowTest : AbstractUiTest() {
    @Test
    fun vaultTabAlwaysVisibleInBottomNav() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun tappingVaultRevealsUnlockGate() {
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(unlockCta()).assertIsDisplayed()
        }
    }

    @Test
    fun unprotectedDeviceGateOffersScreenLockShortcut() {
        // The CI AVD cold-boots with no screen lock, so the gate takes its unprotected branch and the
        // system Settings resolves ACTION_SET_NEW_PASSWORD — both conditions for the secondary
        // "set up screen lock" shortcut. Asserts the real device-resolution path the Robolectric test
        // can only simulate.
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(setupScreenLockCta()).assertIsDisplayed()
        }
    }

    @Test
    fun unlockingVaultRevealsInspirationalZeroResultState() {
        // Pre-grant the per-session unlock so the test does not depend on the emulator having a
        // biometric configured — the gate falls through to "open directly" on unprotected devices
        // (spec § 6) and the user lands on the ZRP body the same way.
        TestData.markVaultOpen()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(zrpHeadlineLead()).assertIsDisplayed()
            composeRule.onNodeWithText(zrpEmphasis()).assertIsDisplayed()
        }
    }

    @Test
    fun unlockedVaultShowsTheSearchAction() {
        // Search lives in the top app bar on every tab. markVaultOpen lands us on the unlocked Vault
        // body directly, where the search action shows like on every other tab.
        TestData.seedCustomSounds(context, count = SEEDED_SOUNDS)
        TestData.markVaultOpen()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(searchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun lockedVaultStillShowsTheSearchAction() {
        // Search moved to the top app bar (persistent chrome), so it stays present even while the
        // Vault is locked — the unlock gate fills the body, but the bar keeps the search action.
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(unlockCta()).assertIsDisplayed()
            composeRule.waitForIdle()
            composeRule.awaitNodeWithContentDescription(searchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun searchOpensFromTheLockedVaultTab() {
        // The search action is newly reachable on the locked Vault tab; tapping it opens the same
        // full-screen overlay as on any other tab (the overlay keeps its own biometric gate for
        // vault content, so this entry point exposes nothing private before unlock).
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(unlockCta()).assertIsDisplayed()
            composeRule.awaitNodeWithContentDescription(searchLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(closeSearchLabel()).assertIsDisplayed()
        }
    }

    private fun vaultLabel() = context.getString(R.string.app_navigation_menu_item_vault)

    private fun searchLabel() = context.getString(R.string.app_search)

    private fun closeSearchLabel() = context.getString(R.string.app_search_close)

    private fun unlockCta() = context.getString(R.string.app_vault_unlock_cta)

    private fun setupScreenLockCta() = context.getString(R.string.app_vault_unprotected_setup_screenlock_cta)

    private fun zrpHeadlineLead() = context.getString(R.string.app_vault_zrp_headline_lead)

    private fun zrpEmphasis() = context.getString(R.string.app_vault_zrp_headline_emphasis)

    private companion object {
        // A few searchable sounds so the unlocked Vault body has content — the search action itself
        // is always present regardless of count.
        const val SEEDED_SOUNDS = 7
    }
}
