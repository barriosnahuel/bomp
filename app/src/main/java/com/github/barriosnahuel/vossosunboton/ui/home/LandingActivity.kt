/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.CustomBuildTypeApplication
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios
import com.github.barriosnahuel.vossosunboton.ui.home.navigation.resolveDeepLinkTab
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme

// FragmentActivity (vs the smaller ComponentActivity) is required so androidx.biometric's
// BiometricPrompt can manage its hosting fragment when the user opens a Vault collection.
class LandingActivity : FragmentActivity() {
    private val viewModel: SoundsViewModel by viewModels { SoundsViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        // The TopAppBar is always a dark violet in both light and dark modes, so status
        // bar icons must always be light (white). SystemBarStyle.dark forces this.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                LandingScreen(viewModel = viewModel)
            }
        }
        // Only on a fresh start: the launching Intent survives in `getIntent()` across recreates, so
        // replaying it on every rotation would re-navigate — and a deep link now resets the tab it
        // names, which would silently discard whatever the user had opened since (an unsaved take,
        // a half-typed name). A restored Activity already has its back stack; it needs no link.
        if (savedInstanceState == null) {
            handleDeeplink(intent)
        }
        maybeSeedDebugSounds(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Keep getIntent() pointing at the newest Intent, so a later recreate restores from the one
        // the user actually arrived on rather than the stale launcher Intent.
        setIntent(intent)
        handleDeeplink(intent)
        maybeSeedDebugSounds(intent)
    }

    // Debug-only dev convenience (seed sample My Sounds on a clean install). The release
    // CustomBuildTypeApplication seam is a no-op, and Robolectric's TestApplication is not a
    // CustomBuildTypeApplication, so this is inert in release builds and unit tests alike.
    private fun maybeSeedDebugSounds(intent: Intent) {
        (application as? CustomBuildTypeApplication)?.seedDebugSoundsIfRequested(intent)
    }

    private fun handleDeeplink(intent: Intent) {
        val uri = intent.data ?: return
        val resolved = resolveDeepLinkTab(uri.path, hasBundledAudios = PackagedAudios.get(this).isNotEmpty())
        // Handed to the graph as a one-shot event: the link resets the destination tab to its root,
        // so it lands the user on the tab it names rather than under whatever was left open.
        viewModel.onDeepLink(resolved)
    }
}
