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
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsEvent
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.AnalyticsTrackerProvider
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.CanonicalScreenName
import com.github.barriosnahuel.vossosunboton.feature.addbutton.findFragmentActivity
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.playback.seekTargetMs
import com.github.barriosnahuel.vossosunboton.feature.vault.WaveformExtractor
import com.github.barriosnahuel.vossosunboton.ui.theme.ImmersiveListenTheme

private const val AUDIO_MIME = "audio/*"

/**
 * Full-screen recorder destination (ADR 0019 + ADR 0024 D4). Owns the `RECORD_AUDIO` permission flow
 * (priming → request → denied/settings/import-escape), preview playback and the discard prompt; the
 * capture state machine lives in [RecorderViewModel].
 *
 * Was `RecordingActivity`. As a destination it can't `finish()`, so the three ways out are callbacks:
 * [onExit] (closed, discarded, or backed out with nothing to lose), [onKeepClip] (the recorded clip
 * goes on to naming) and [onImportInstead] (the mic-denied escape hatch hands a picked audio to the
 * same naming destination).
 *
 * The ViewModel is scoped to this NavEntry (ADR 0024 D3, via `rememberViewModelStoreNavEntryDecorator`),
 * so leaving the destination clears the capture state and a later visit starts fresh — the lifecycle the
 * Activity used to get from `finish()`. A pending draft outlives it deliberately: it is persisted in
 * [RecorderDraftStore], not held in the ViewModel.
 */
@Composable
internal fun RecorderHost(
    resumeDraft: Boolean,
    onExit: () -> Unit,
    onKeepClip: (Uri) -> Unit,
    onImportInstead: (Uri) -> Unit,
    // Defaulted, not injected by the graph: the NavEntry-scoped ViewModel is what production wants, but
    // the capture state machine is only reachable through Dispatchers.IO, so tests supply one already in
    // the state under test instead of driving a real capture.
    viewModel: RecorderViewModel = viewModel(factory = RecorderViewModel.Factory),
) {
    val context = LocalContext.current

    // Restore a pending draft when opened from the Landing banner; otherwise start fresh. Guarded
    // inside the VM so a recomposition (or a config-change recreate, which keeps the VM) can't re-run it.
    LaunchedEffect(Unit) { viewModel.onEnter(resumeDraft = resumeDraft) }

    // screen_view fires from composition, not from a host callback: a manual GA4 screen_view logged
    // before the Activity is in focus is silently dropped by the SDK.
    LaunchedEffect(Unit) {
        AnalyticsTrackerProvider.get(context.applicationContext).logScreen(CanonicalScreenName.RECORD_SOUND)
    }

    // Backgrounding frees the mic and stops the preview, as the Activity's onStop did. A config-change
    // recreate (locale/theme — rotation is handled in place) also routes through ON_STOP; treating it as
    // a background would auto-stop the recording on every such change, so it is filtered out the same way.
    val activity = remember(context) { context.findFragmentActivity() }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (activity?.isChangingConfigurations != true) {
            viewModel.onHostStopped()
            PlayerControllerFactory.instance.stopPlayingSound()
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var granted by remember { mutableStateOf(hasMicPermission(context)) }
    // Saveable: durable progress (the denied/settings screen, an open discard dialog) must survive an
    // Activity recreate — locale/theme switch, system kill (CLAUDE.md § Stateful Composables).
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            AnalyticsTrackerProvider.get(context).log(AnalyticsEvent.RecordPermissionResult(granted = ok))
            if (!ok) {
                permanentlyDenied =
                    activity?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == false
            }
        }
    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let(onImportInstead)
        }

    val state by viewModel.state.collectAsStateWithLifecycle()
    val playback by PlayerControllerFactory.instance.playbackState.collectAsStateWithLifecycle()
    val reviewUri = (state as? RecorderState.Review)?.uri
    val preview = playback?.takeIf { it.uri == reviewUri }
    val isPreviewPlaying = preview?.isPlaying == true
    // A scrub the user made BEFORE playing this clip (no player loaded yet). Held so the wave shows it,
    // and consumed as the resume offset when play begins (so a later completion rewinds to 0 like the
    // listen screen). Reset per clip. While the clip is loaded the live player head wins (seekTo publishes
    // it even when paused), so a pending scrub only matters before first play.
    var pendingScrubMs by rememberSaveable(reviewUri) { mutableStateOf<Long?>(null) }
    val displayPositionMs = reviewWavePositionMs(preview?.positionMs?.toLong(), pendingScrubMs)

    // Real amplitude envelope of the recorded clip. A fresh recording carries the live-captured envelope
    // (instant — no placeholder gap); only a restored draft (no live samples) falls back to decoding the
    // file off the main thread. Re-keyed per clip so a re-record swaps the wave.
    var peaks by remember(reviewUri) { mutableStateOf(viewModel.capturedEnvelope) }
    LaunchedEffect(reviewUri) {
        if (reviewUri != null && peaks == null) {
            val decoded = WaveformExtractor.extract(context, reviewUri, RECORDER_WAVEFORM_BARS)
            peaks = decoded
            // Cache it on the VM so a config recreate reuses it instead of re-decoding (restored drafts).
            viewModel.cacheDecodedEnvelope(decoded)
        }
    }

    val resources = LocalResources.current
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecorderEvent.Message -> snackbarHostState.showSnackbar(resources.getString(event.messageRes))
                is RecorderEvent.Handoff -> {
                    // Stop the review preview before handing off. As an Activity this came for free —
                    // finish() routed through onStop. As a destination the recorder stays on the back
                    // stack (so back can return to the take), the Activity never stops, and without this
                    // the clip would keep playing over the naming screen.
                    PlayerControllerFactory.instance.stopPlayingSound()
                    onKeepClip(event.uri)
                }
            }
        }
    }

    // Only intercept back when there is something to lose. With nothing to discard, back falls through to
    // NavDisplay and pops this destination — which is what keeps predictive back continuous on the way out.
    BackHandler(enabled = viewModel.hasUnsavedClip()) { showDiscard = true }

    ImmersiveListenTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                granted ->
                    RecorderScreen(
                        state = state,
                        isPreviewPlaying = isPreviewPlaying,
                        previewPositionMs = displayPositionMs,
                        peaks = peaks,
                        onRecordTap = viewModel::onRecordTapped,
                        onStopTap = viewModel::onStopTapped,
                        onPreviewToggle = {
                            togglePreview(context, reviewUri, (pendingScrubMs ?: 0L).toInt())
                            // The pending pre-play scrub is now the resume offset — consume it so a later
                            // completion rewinds the wave to the start (like the listen screen).
                            pendingScrubMs = null
                        },
                        onUseClip = viewModel::onUseClip,
                        onReRecord = {
                            PlayerControllerFactory.instance.stopPlayingSound()
                            viewModel.onReRecord()
                        },
                        onClose = { if (viewModel.hasUnsavedClip()) showDiscard = true else onExit() },
                        // Scrub like the Vault listen wave. While the clip is loaded, seek the player live;
                        // before first play there is no player, so hold the target as a pending scrub that
                        // the wave shows and that `play` resumes from.
                        onSeek = { fraction ->
                            val review = state as? RecorderState.Review
                            if (review != null) {
                                seekTargetMs(review.durationMs, fraction)?.let { target ->
                                    if (preview != null) {
                                        PlayerControllerFactory.instance.seekTo(target)
                                    } else {
                                        pendingScrubMs = target.toLong()
                                    }
                                }
                            }
                        },
                    )
                permanentlyDenied ->
                    MicPermissionDenied(
                        onOpenSettings = { openAppSettings(context) },
                        onImportInstead = { importLauncher.launch(arrayOf(AUDIO_MIME)) },
                        onClose = onExit,
                    )
                else ->
                    MicPermissionPriming(
                        onAllow = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                        onNotNow = onExit,
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
                        // Durably drop the clip + its persisted draft before leaving — onCleared no longer
                        // cleans up (a Review clip is now a recoverable draft).
                        viewModel.onDiscard()
                        PlayerControllerFactory.instance.stopPlayingSound()
                        onExit()
                    },
                    onDismiss = { showDiscard = false },
                )
            }
        }
    }
}

private fun togglePreview(
    context: Context,
    reviewUri: Uri?,
    startPositionMs: Int,
) {
    val uri = reviewUri ?: return
    val controller = PlayerControllerFactory.instance
    val current = controller.playbackState.value
    when {
        current?.isPlaying == true -> controller.pause()
        // Paused on this clip: the player head already holds the scrubbed position — resume from it.
        current != null && current.uri == uri -> controller.resume()
        // Fresh start (before first play / after completion): resume from the remembered scrub offset.
        // Long-form engine (ADR 0022): the review is a listening session, not a quick tap.
        else -> controller.startUriListenSession(context, uri, startPositionMs)
    }
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
    runCatching { context.startActivity(intent) }
}
