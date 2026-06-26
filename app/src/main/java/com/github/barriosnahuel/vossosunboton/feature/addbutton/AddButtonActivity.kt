/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.Context
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
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.model.Sound
import com.github.barriosnahuel.vossosunboton.ui.home.LandingActivity
import com.github.barriosnahuel.vossosunboton.ui.theme.AppTheme

// FragmentActivity (vs ComponentActivity) is required so the Add/Edit tagging UI can fire
// BiometricPrompt when the user requests to assign the new audio to a private collection.
class AddButtonActivity : FragmentActivity() {
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
        // Default to share: the manifest ACTION_SEND filter carries no source extra. The in-app
        // import Hub sets SOURCE_IMPORT via createIntent so the funnel is attributed honestly.
        val source = intent.getStringExtra(EXTRA_SOURCE) ?: SOURCE_SHARE
        launchCreateAddButtonMode(uri, source)
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

    private fun launchCreateAddButtonMode(
        uri: Uri,
        source: String,
    ) {
        setContent {
            AppTheme {
                AddButtonScreen(
                    context = this,
                    mode = AddButtonMode.Create(uri),
                    source = source,
                    onSaved = { finish() },
                    onNavigateUp = { finish() },
                )
            }
        }
        val extras = Bundle().apply { putString("source", source) }
        AnalyticsTrackerProvider.get(this).logScreen(CanonicalScreenName.ADD_SOUND, extras)
    }

    companion object {
        /** Source param for `screen_view {add_sound}` and `sound_add`: the surface that opened Create. */
        const val SOURCE_SHARE = "share"
        const val SOURCE_IMPORT = "import"
        const val SOURCE_RECORD = "record"

        private const val EXTRA_SOURCE = "com.github.barriosnahuel.vossosunboton.extra.SOURCE"

        /**
         * Builds an explicit intent to start the Create flow from an audio [uri] the user produced
         * in-app — the import Hub picker ([SOURCE_IMPORT], default) or the recorder ([SOURCE_RECORD]).
         * [Intent.FLAG_GRANT_READ_URI_PERMISSION] + [Intent.setData] forward the read grant to this
         * Activity so the copy at save time can read the stream even if the launching screen is killed
         * first. The URI still flows through the same inbound validator (`AddButtonFeature`) as the
         * share-sheet path — see CLAUDE.md § Security boundaries. [source] also tags `sound_add`'s
         * analytics source and, for [SOURCE_RECORD], the persisted `SoundSource.RECORDED` provenance.
         */
        fun createIntent(
            context: Context,
            uri: Uri,
            source: String = SOURCE_IMPORT,
        ): Intent =
            Intent(context, AddButtonActivity::class.java).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(EXTRA_SOURCE, source)
            }
    }
}
