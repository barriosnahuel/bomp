/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import android.content.Context
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
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.model.data.local.defaultaudios.PackagedAudios
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
        handleDeeplink(intent)
        maybeSeedDebugSounds(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
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
        // Closed allowlist of known deep-link destinations. Unknown paths fall back to MY_SOUNDS
        // (the safe default) so we never silently route to a tab the link did not name.
        val requested =
            when (uri.path) {
                "/home" -> AppTab.MY_SOUNDS
                "/explore" -> AppTab.EXPLORE_SOUNDS
                else -> AppTab.MY_SOUNDS
            }
        // Explore is empty in release builds (no bundled audios) and in any debug build that
        // hasn't populated model/src/debug/res/raw/. Routing there would land on a blank tab,
        // so fall back to Home when there's nothing to explore.
        val resolved =
            if (requested == AppTab.EXPLORE_SOUNDS && PackagedAudios.get(this).isEmpty()) {
                AppTab.MY_SOUNDS
            } else {
                requested
            }
        viewModel.selectTab(resolved)
    }

    companion object {
        /** Single Parcelable [Sound] extra for the edit flow — carries the full entity, [Sound.id] included. */
        const val EXTRA_EDIT_SOUND = "extra_edit_sound"

        private const val ADD_BUTTON_ACTIVITY_CLASS =
            "com.github.barriosnahuel.vossosunboton.feature.addbutton.AddButtonActivity"

        fun editIntent(
            context: Context,
            sound: Sound,
        ): Intent =
            Intent().apply {
                setClassName(context, ADD_BUTTON_ACTIVITY_CLASS)
                putExtra(EXTRA_EDIT_SOUND, sound)
            }
    }
}
