/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
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
    fun unlockedVaultShowsTheSearchFab() {
        // The Vault tab now offers the global Search FAB (it replaced the dedicated "new private
        // collection" FAB — creating one still lives on the chip row's "+ Nueva"). markVaultOpen
        // lands us on the body directly, where the FAB shows like on every other tab.
        // Seed past SEARCH_FAB_MIN_SOUNDS explicitly so the FAB gate is satisfied by the seeded
        // library alone, not the (uncommitted, best-effort) bundled catalog.
        TestData.seedCustomSounds(context, count = SOUNDS_FOR_VISIBLE_FAB)
        TestData.markVaultOpen()

        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithContentDescription(searchLabel()).assertIsDisplayed()
        }
    }

    @Test
    fun lockedVaultHidesTheSearchFab() {
        // While the Vault is locked the unlock gate is the whole screen — the Search FAB stays
        // hidden until unlock, matching the create FAB it replaced (also visible only when unlocked).
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(vaultLabel()).performClick()
            composeRule.awaitNodeWithText(unlockCta()).assertIsDisplayed()
            composeRule.waitForIdle()
            composeRule.onNodeWithContentDescription(searchLabel()).assertDoesNotExist()
        }
    }

    private fun vaultLabel() = context.getString(R.string.app_navigation_menu_item_vault)

    private fun searchLabel() = context.getString(R.string.app_search)

    private fun unlockCta() = context.getString(R.string.app_vault_unlock_cta)

    private fun setupScreenLockCta() = context.getString(R.string.app_vault_unprotected_setup_screenlock_cta)

    private fun zrpHeadlineLead() = context.getString(R.string.app_vault_zrp_headline_lead)

    private fun zrpEmphasis() = context.getString(R.string.app_vault_zrp_headline_emphasis)

    private companion object {
        // Mirrors SEARCH_FAB_MIN_SOUNDS in LandingScreen.kt — seeding this many guarantees the
        // global library crosses the Search FAB threshold without relying on the bundled catalog.
        const val SOUNDS_FOR_VISIBLE_FAB = 7
    }
}
