/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterStore
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.welcome.WelcomeStickerStore
import com.github.barriosnahuel.vossosunboton.model.data.manager.CollectionsRepository
import com.github.barriosnahuel.vossosunboton.model.data.manager.SoundsRepository
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Smoke test for [VaultScreen] reached via the Vault bottom-nav tab.
 *
 * Round-2 redesigned the Vault from a collection grid (one card per private collection) into a
 * flat audio list gated by a per-session biometric unlock. The grid no longer exists, so the
 * smoke contract is now:
 *
 * 1. Tapping the Vault tab from a fresh launch lands on the [UnlockGate], not the body.
 * 2. The UnlockGate exposes the unlock CTA so the user has a path forward.
 *
 * OWASP MASVS-AUTH-1 (UI consequence of biometric policy): on a Robolectric device with no
 * configured biometric, [VaultScreen] must show the UnlockGate before it shows the body —
 * granting access silently would skip the "this is the private surface" affordance even if the
 * underlying gate is configured as `UNAVAILABLE` (spec § 6 allows open-on-tap, but the user
 * must still cross the gate UI).
 *
 * End-to-end flows (assign sheet, system Baúl localization, filter chips, ZRP) live in
 * `app/src/androidTest/.../feature/vault/` because they need the real Compose runtime + the
 * production `AppTheme` color resolution, which Robolectric handles less reliably for
 * locale-aware string resources.
 */
internal class VaultScreenTest : AbstractRobolectricTest() {
    @get:Rule
    val composeRule = createAndroidComposeRule<LandingActivity>()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        runBlocking {
            SoundsRepository(context).clearForTest()
            CollectionsRepository(context).clearForTest()
            WelcomeStickerStore(context).clearForTest()
            MySoundsFilterStore(context).clearForTest()
        }
        mockkObject(PlayerControllerFactory)
        every { PlayerControllerFactory.instance.setOnStartStopListener(any()) } answers { nothing }
        every { PlayerControllerFactory.instance.removeOnStartStopListener(any()) } answers { nothing }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `selecting the Vault tab lands on the UnlockGate, not the body`() {
        val vaultLabel = context.getString(R.string.app_navigation_menu_item_vault)
        val unlockCta = context.getString(R.string.app_vault_unlock_cta)

        composeRule.onNodeWithText(vaultLabel).performClick()
        composeRule.onNodeWithText(unlockCta).assertIsDisplayed()
    }
}
