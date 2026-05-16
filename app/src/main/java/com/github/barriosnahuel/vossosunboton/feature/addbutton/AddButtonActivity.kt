/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme

class AddButtonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    // singleTask + a new ACTION_SEND from a share sheet arrives here while an Edit
    // flow is still alive: the most recent user intent wins, the previous in-progress
    // state is discarded by reading the new intent and recomposing setContent.
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val editSound: Sound? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(LandingActivity.EXTRA_EDIT_SOUND, Sound::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(LandingActivity.EXTRA_EDIT_SOUND)
            }
        if (editSound != null) {
            launchEditAddButtonMode(editSound)
            return
        }

        val uri: Uri? =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
        if (uri == null) {
            Toast.makeText(this, R.string.app_addbutton_missing_parameter_error, Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        launchCreateAddButtonMode(uri)
    }

    private fun launchEditAddButtonMode(sound: Sound) {
        setContent {
            AppTheme {
                AddButtonScreen(
                    context = this,
                    mode = AddButtonMode.Edit(sound),
                    onSaved = { finish() },
                    onNavigateUp = { finish() },
                )
            }
        }
        AnalyticsTrackerProvider.get(this).logScreen(CanonicalScreenName.EDIT_SOUND)
    }

    private fun launchCreateAddButtonMode(uri: Uri) {
        setContent {
            AppTheme {
                AddButtonScreen(
                    context = this,
                    mode = AddButtonMode.Create(uri),
                    onSaved = { finish() },
                    onNavigateUp = { finish() },
                )
            }
        }
        val extras = Bundle().apply { putString("source", SOURCE_SHARE) }
        AnalyticsTrackerProvider.get(this).logScreen(CanonicalScreenName.ADD_SOUND, extras)
    }

    companion object {
        /** Source param for `screen_view {add_sound}` and `sound_add`. Today every Create flow comes from share. */
        const val SOURCE_SHARE = "share"
    }
}
