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
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme

/**
 * Share-sheet trampoline: the only entry point left on this Activity is the external `ACTION_SEND`
 * intent carrying an audio MIME type (ADR 0024 D4). Every internal creation/edit flow is a destination
 * in Landing's graph now.
 *
 * It stays an Activity because the share contract is a task-level guarantee that no destination inside
 * Landing's task can honor: `excludeFromRecents` (no ghost entry in Recents) and `finish()` returning
 * the user to the app they shared from — not into a Bomp tab. It delegates to the same
 * [AddButtonScreen] the graph's naming destination hosts, so naming, validation and save keep one
 * implementation, not two.
 *
 * FragmentActivity (vs ComponentActivity) is required so the tagging UI can fire BiometricPrompt when
 * the user assigns the shared audio to a private collection.
 */
class AddButtonActivity : FragmentActivity() {
    // Monotonic per-instance intent sequence. Each handleIntent call is a distinct user intent and
    // must re-emit screen_view even when the payload is structurally identical to the live screen's
    // (e.g. re-sharing the same audio) — AddButtonScreen keys its logging effect on this.
    private var intentSequence = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    // singleTask + a second ACTION_SEND from the share sheet arriving while a previous share is still
    // being named: the most recent user intent wins, and the in-progress state is discarded by reading
    // the new intent and recomposing setContent.
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        intentSequence++
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
        setContent {
            AppTheme {
                AddButtonScreen(
                    context = this,
                    mode = AddButtonMode.Create(uri),
                    source = AddSoundSource.SHARE,
                    intentKey = intentSequence,
                    // finish() rather than a graph pop is the whole point of the trampoline: it returns
                    // the user to the app they shared from, leaving Bomp where it was.
                    onSaved = { finish() },
                    onNavigateUp = { finish() },
                )
            }
        }
    }
}
