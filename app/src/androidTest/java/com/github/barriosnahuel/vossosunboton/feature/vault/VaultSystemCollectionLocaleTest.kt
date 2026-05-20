/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.core.os.LocaleListCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Locale-fallback regression for the system "Baúl" collection.
 *
 * Why this lives in the instrumented suite: the bug was that `MySoundsFilterChipsRow` (and the
 * other rendering sites that took `collection.name` directly) showed the Spanish-seeded literal
 * "Baúl" in any locale, because the system collection is persisted to DataStore with a fixed
 * canonical name. The round-3 fix routes `collection.isSystem` through `stringResource(...)`
 * so the localized resource wins. The check is a live-tree assertion against a configuration
 * forced to English — Robolectric can do the same but its semantics-tree handling diverges from
 * the real Compose runtime in subtle ways for `stringResource` + `LocaleListCompat` overrides,
 * and the bug was specifically a user-on-device regression, so we validate it against the real
 * resource lookup path.
 *
 * @see com.github.barriosnahuel.vossosunboton.feature.collections.MySoundsFilterChipsRow
 */
@RunWith(AndroidJUnit4::class)
internal class VaultSystemCollectionLocaleTest : AbstractUiTest() {
    private var previousLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()

    @Before
    override fun setUp() {
        super.setUp()
        previousLocales = AppCompatDelegate.getApplicationLocales()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.create(Locale.ENGLISH))
    }

    @After
    override fun tearDown() {
        AppCompatDelegate.setApplicationLocales(previousLocales)
        super.tearDown()
    }

    @Test
    fun systemBaulChipRendersLocalizedLabelInsteadOfStoredSpanishLiteral() {
        // Skip the biometric gate so the chip row actually renders.
        TestData.markVaultOpen()
        // The repo seeds the system Baúl on first observation. Triggering it explicitly avoids
        // racing the first `collectionsRepo.collections.collect { ... }` emission in the VM.
        TestData.touchPrivateCollections(context)

        val expectedEnglishLabel = englishStringFor(R.string.app_vault_baul_name)
        // Defensive assertion: if the EN resource ever drifts from "My Vault" we want to know
        // here rather than chasing a `assertIsDisplayed` false-negative.
        assertThat(expectedEnglishLabel).isEqualTo("My Vault")

        ActivityScenario.launch(LandingActivity::class.java).use {
            val vaultTab = englishStringFor(R.string.app_navigation_menu_item_vault)
            composeRule.awaitNodeWithText(vaultTab).performClick()

            composeRule.awaitNodeWithText(expectedEnglishLabel).assertIsDisplayed()
            // And the Spanish-seeded literal must not be on screen.
            composeRule
                .onAllNodes(hasText("Baúl"))
                .fetchSemanticsNodes()
                .also {
                    require(it.isEmpty()) {
                        "Found ${it.size} node(s) showing the Spanish literal 'Baúl' under an EN locale"
                    }
                }
        }
    }

    /** Resolves [stringId] against an EN configuration explicitly, sidestepping any caching the
     *  test process might have picked up before the locale override landed. */
    private fun englishStringFor(stringId: Int): String {
        val baseConfig = context.resources.configuration
        val englishConfig =
            Configuration(baseConfig).apply { setLocale(Locale.ENGLISH) }
        return context.createConfigurationContext(englishConfig).getString(stringId)
    }
}
