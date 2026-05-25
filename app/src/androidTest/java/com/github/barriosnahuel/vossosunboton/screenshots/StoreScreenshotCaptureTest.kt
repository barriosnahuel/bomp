/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.screenshots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.github.barriosnahuel.vossosunboton.AbstractUiTest
import com.github.barriosnahuel.vossosunboton.DebugSeedCorpus
import com.github.barriosnahuel.vossosunboton.DebugSoundSeeder
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.SeedSource
import com.github.barriosnahuel.vossosunboton.TestData
import com.github.barriosnahuel.vossosunboton.awaitNodeWithContentDescription
import com.github.barriosnahuel.vossosunboton.awaitNodeWithText
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Locale

/**
 * NOT a behaviour test — a capture utility that drives the real app through the store-listing
 * scenes and writes full-screen 1080×2400 PNGs to the app's external files dir
 * (`…/files/screenshots/<scene>-<locale>.png`) for the marketing pipeline to pull.
 *
 * Locale: this app reads the **system** locale (no AppCompat per-app override), so the language of
 * the captured UI is whatever the device is set to. The capture is therefore locale-agnostic — it
 * reads [Locale.getDefault] to pick the matching [DebugSeedCorpus] and to tag the files. The driver
 * (`scripts/capture-store-screenshots.sh`) flips the emulator's system locale and reboots between
 * the two runs, so es-AR and en-US each render their own UI chrome + content.
 *
 * It lives in `androidTest` (not a pure adb script) because the Vault + immersive scenes need the
 * in-process unlock bypass ([TestData.markVaultOpen], which a `BiometricPrompt` script can't fake)
 * and deterministic Compose navigation. Seeding reuses [DebugSeedCorpus] + [DebugSoundSeeder.seed]
 * so the captured names match a real debug board; the payload is the 5s silent test asset.
 *
 * Drive it with `scripts/capture-store-screenshots.sh` (installs without the auto-uninstall of
 * `connectedDebugAndroidTest`, flips locale + reboots, runs via `am instrument`, then pulls).
 */
@RunWith(AndroidJUnit4::class)
internal class StoreScreenshotCaptureTest : AbstractUiTest() {
    private val device: UiDevice
        get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun captureStoreScenes() {
        val locale = Locale.getDefault()
        val tag = if (locale.language == "es") "es-AR" else "en-US"
        seedCorpus(locale)
        // Process-global unlock so the Vault + immersive scenes skip the biometric gate.
        TestData.markVaultOpen()

        // 01 — My Sounds Home: realistic board + the public collection filter chips.
        ActivityScenario.launch(LandingActivity::class.java).use {
            // The active filter chip survives cold starts and TestData.clearAll does not reset it, so
            // a filter tapped in a prior locale run would leave Home empty here. Force "All" first.
            composeRule.awaitNodeWithText(string(R.string.app_my_sounds_filter_all)).performClick()
            awaitContentLoaded()
            capture("01-home", tag)

            // 03 — Collections: filter the list to the first public collection.
            composeRule.awaitNodeWithText(firstPublicCollection(locale)).performClick()
            awaitContentLoaded()
            capture("03-collections", tag)
        }

        // 04 — Search overlay (empty state with the warm hint).
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithContentDescription(string(R.string.app_search)).performClick()
            composeRule.waitForIdle()
            capture("04-search", tag)
        }

        // 06 — Vault: unlocked, showing the private collections (Baúl + the seeded one).
        ActivityScenario.launch(LandingActivity::class.java).use {
            composeRule.awaitNodeWithText(string(R.string.app_navigation_menu_item_vault)).performClick()
            awaitContentLoaded()
            capture("06-vault", tag)

            // 05 — Immersive listen: tap play on the first Vault audio.
            composeRule
                .onAllNodesWithContentDescription(string(R.string.app_play))
                .onFirst()
                .performClick()
            composeRule.awaitNodeWithText(immersiveModeLabel()).assertIsDisplayed()
            composeRule.waitForIdle()
            capture("05-immersive", tag)
        }
    }

    /**
     * Blocks until the seeded list has actually rendered (≥1 play button on screen). A bare
     * [androidx.compose.ui.test.junit4.ComposeTestRule.waitForIdle] only flushes recompositions, not
     * the `DataStore → StateFlow → render` chain (CLAUDE.md § Synchronization), so capturing right
     * after launch races the empty state.
     */
    private fun awaitContentLoaded() {
        composeRule.waitUntil(timeoutMillis = CONTENT_TIMEOUT_MS) {
            composeRule
                .onAllNodesWithContentDescription(string(R.string.app_play))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.waitForIdle()
    }

    /** Seeds the locale corpus through the production debug seeder, with the silent test asset as
     *  the audio payload for every item. */
    private fun seedCorpus(locale: Locale) {
        val corpus = DebugSeedCorpus.forLocale(locale)
        val sources =
            corpus.items.map { item ->
                SeedSource(
                    key = item.key,
                    name = item.name,
                    publicCollections = item.publicCollections,
                    privateCollections = item.privateCollections,
                    inSystemVault = item.inSystemVault,
                    visibleInMySounds = item.visibleInMySounds,
                ) { InstrumentationRegistry.getInstrumentation().context.assets.open(TEST_ASSET) }
            }
        runBlocking {
            DebugSoundSeeder.seed(
                context = context,
                sources = sources,
                limit = sources.size,
                publicCollections = corpus.publicCollections,
                privateCollections = corpus.privateCollections,
            )
        }
    }

    private fun firstPublicCollection(locale: Locale) = DebugSeedCorpus.forLocale(locale).publicCollections.first()

    private fun immersiveModeLabel() = string(R.string.app_vault_immersive_mode_label).uppercase()

    private fun string(resId: Int) = context.getString(resId)

    private fun capture(
        scene: String,
        tag: String,
    ) {
        composeRule.waitForIdle()
        val dir = File(context.getExternalFilesDir(null), SCREENSHOT_DIR).apply { mkdirs() }
        val ok = device.takeScreenshot(File(dir, "$scene-$tag.png"))
        require(ok) { "takeScreenshot failed for $scene-$tag" }
    }

    private companion object {
        const val TEST_ASSET = "test_sound.mp3"
        const val SCREENSHOT_DIR = "screenshots"
        const val CONTENT_TIMEOUT_MS = 5_000L
    }
}
