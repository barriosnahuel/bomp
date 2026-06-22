/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.feature.addbutton.AddButtonActivity
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.ui.theme.ImmersiveListenTheme

/**
 * Full-screen host for the in-app recorder (ADR 0019). Owns the `RECORD_AUDIO` permission flow (priming
 * → request → denied/settings/import-escape), the [RecorderViewModel], preview playback, and the handoff
 * to [AddButtonActivity] (Create mode, `SOURCE_RECORD`) once the user keeps a clip. Not exported.
 */
class RecordingActivity : FragmentActivity() {
    private val viewModel: RecorderViewModel by viewModels { RecorderViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        AnalyticsTrackerProvider.get(this).logScreen(CanonicalScreenName.RECORD_SOUND)
        setContent { RecorderHost() }
    }

    override fun onStop() {
        super.onStop()
        // Recording is foreground-only: free the mic if backgrounded mid-capture, and stop any preview.
        viewModel.onHostStopped()
        PlayerControllerFactory.instance.stopPlayingSound()
    }

    @Composable
    private fun RecorderHost() {
        val context = LocalContext.current
        val snackbarHostState = remember { SnackbarHostState() }
        var granted by remember { mutableStateOf(hasMicPermission()) }
        var permanentlyDenied by remember { mutableStateOf(false) }
        var showDiscard by remember { mutableStateOf(false) }

        val permissionLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
                granted = ok
                if (!ok) permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
            }
        val importLauncher =
            rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    startActivity(AddButtonActivity.createIntent(this, uri))
                    finish()
                }
            }

        val state by viewModel.state.collectAsStateWithLifecycle()
        val playback by PlayerControllerFactory.instance.playbackState.collectAsStateWithLifecycle()
        val reviewFile = (state as? RecorderState.Review)?.file
        val reviewUri = remember(reviewFile) { reviewFile?.let { RecorderTempFiles.contentUriFor(this, it) } }
        val isPreviewPlaying = playback?.let { it.isPlaying && it.uri == reviewUri } == true

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    is RecorderEvent.Message -> snackbarHostState.showSnackbar(getString(event.messageRes))
                    is RecorderEvent.Handoff -> {
                        val uri = RecorderTempFiles.contentUriFor(this@RecordingActivity, event.file)
                        startActivity(AddButtonActivity.createIntent(this@RecordingActivity, uri, AddButtonActivity.SOURCE_RECORD))
                        finish()
                    }
                }
            }
        }

        BackHandler { if (viewModel.hasUnsavedClip()) showDiscard = true else finish() }

        ImmersiveListenTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    granted ->
                        RecorderScreen(
                            state = state,
                            isPreviewPlaying = isPreviewPlaying,
                            onRecordTap = viewModel::onRecordTapped,
                            onStopTap = viewModel::onStopTapped,
                            onPreviewToggle = { togglePreview(reviewUri) },
                            onUseClip = viewModel::onUseClip,
                            onReRecord = {
                                PlayerControllerFactory.instance.stopPlayingSound()
                                viewModel.onReRecord()
                            },
                            onClose = { if (viewModel.hasUnsavedClip()) showDiscard = true else finish() },
                        )
                    permanentlyDenied ->
                        MicPermissionDenied(
                            onOpenSettings = { openAppSettings(context) },
                            onImportInstead = { importLauncher.launch(arrayOf(AUDIO_MIME)) },
                            onClose = { finish() },
                        )
                    else ->
                        MicPermissionPriming(
                            onAllow = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                            onNotNow = { finish() },
                        )
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
                )
                if (showDiscard) {
                    RecorderDiscardDialog(
                        onConfirm = {
                            showDiscard = false
                            PlayerControllerFactory.instance.stopPlayingSound()
                            finish()
                        },
                        onDismiss = { showDiscard = false },
                    )
                }
            }
        }
    }

    private fun togglePreview(reviewUri: Uri?) {
        val uri = reviewUri ?: return
        val controller = PlayerControllerFactory.instance
        val current = controller.playbackState.value
        when {
            current?.isPlaying == true -> controller.pause()
            current != null && current.uri == uri -> controller.resume()
            else -> controller.startPlayingUri(this, uri)
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun openAppSettings(context: Context) {
        val intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        runCatching { context.startActivity(intent) }
    }

    companion object {
        private const val AUDIO_MIME = "audio/*"

        fun createIntent(context: Context): Intent = Intent(context, RecordingActivity::class.java)
    }
}
