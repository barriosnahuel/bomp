/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton.trim

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.feature.playback.PlayerControllerFactory
import com.github.barriosnahuel.vossosunboton.feature.vault.WaveformExtractor
import com.github.barriosnahuel.vossosunboton.ui.AppIcons
import com.github.barriosnahuel.vossosunboton.ui.home.formatDuration

/**
 * The "keep just this part" editor for an audio being added — a collapsed text button that reveals
 * the waveform range selector in place.
 *
 * Create flow only, and only for clips long enough to be worth cutting ([TrimSelection.isTrimmable]);
 * the host decides that, this composable renders whatever it is given. Nothing here mutates the
 * audio: it produces a [TrimSelection] the save path turns into a real cut, so backing out of the
 * screen leaves the source file exactly as it was.
 *
 * Range preview reuses the add flow's existing `MediaPlayer` engine through `PlayerController` —
 * no second engine, no changes inside `PlayerControllerImpl`: docs/adr/0028-add-flow-audio-trim.md.
 */
@Composable
internal fun TrimSection(
    context: Context,
    source: Uri,
    durationMs: Int,
    expanded: Boolean,
    selection: TrimSelection,
    peaks: FloatArray?,
    onExpandedChange: (Boolean) -> Unit,
    onSelectionChange: (TrimSelection) -> Unit,
) {
    if (!expanded) {
        TextButton(onClick = { onExpandedChange(true) }) {
            Icon(
                painter = painterResource(R.drawable.app_ic_content_cut),
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(text = stringResource(R.string.app_addbutton_trim_cta))
        }
        return
    }

    val controller = remember { PlayerControllerFactory.instance }
    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    val startMs = selection.startMs(durationMs)
    val endMs = selection.endMs(durationMs)

    // Whether the CURRENT playback is the one this editor started. The `AudioPreview` card above plays
    // the very same URI through the very same controller, so matching on the URI alone would make this
    // editor's range stop also cut the card's full-clip playback short.
    //
    // Tearing playback down on the way out is NOT this composable's job: the card above covers the same
    // URI through `StopPreviewOnDispose`, which also tells a real exit from an Activity recreate. A
    // second disposal here would double-stop on exit and cut the audio on every rotation.
    val previewingRange = remember(source) { mutableStateOf(false) }

    val isLoadedHere = playbackState?.uri == source
    val isPlaying = isLoadedHere && playbackState?.isPlaying == true

    // Ownership is released when the controller moves on to something else — another surface took the
    // player, or the preview was stopped outright. From an effect, not the composable body: writing
    // state while composing re-runs on every recomposition and can schedule extra ones.
    LaunchedEffect(isLoadedHere) {
        if (!isLoadedHere) previewingRange.value = false
    }

    val positionMs = if (isLoadedHere) playbackState?.positionMs ?: 0 else 0
    // The range preview's end stop. MediaPlayer has no play-range, so the controller's existing
    // ~100 ms progress ticks are what pause it at the selection's tail; the ≤ 100 ms overshoot is a
    // preview artifact only — the exported cut is frame-exact (ADR 0028 D3).
    //
    // Keyed on the crossing, not on the position: keying on positionMs would tear down and relaunch
    // the effect ten times a second, and keying only on (isPlaying, endMs) would never re-run as the
    // clip advances — the preview would run past the range to the end of the audio.
    val reachedRangeEnd = positionMs >= endMs
    LaunchedEffect(isPlaying, reachedRangeEnd) {
        if (previewingRange.value && isPlaying && reachedRangeEnd) {
            previewingRange.value = false
            controller.pause()
        }
    }

    Card(
        border = BorderStroke(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.app_addbutton_trim_title),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            TrimWaveform(
                selection = selection,
                peaks = peaks,
                durationMs = durationMs,
                // Derived from the already-guarded positionMs rather than re-reading playbackState: the
                // editor can own a preview whose first progress tick has not landed yet, and a bang
                // operator on that window is a crash, not an invariant.
                playheadFraction =
                    if (previewingRange.value && isLoadedHere && durationMs > 0) {
                        positionMs.toFloat() / durationMs
                    } else {
                        null
                    },
                handlesContentDescription = stringResource(R.string.app_addbutton_trim_handles_description),
                onSelectionChange = { next ->
                    // A moved handle invalidates whatever the user was hearing: restarting from the new
                    // start on the next tap beats resuming mid-range from a position now out of bounds.
                    if (previewingRange.value && isPlaying) {
                        previewingRange.value = false
                        controller.pause()
                    }
                    onSelectionChange(next)
                },
            )
            TrimRangeRow(
                startMs = startMs,
                keptMs = selection.keptMs(durationMs),
                isPlaying = isPlaying,
                onTogglePreview = {
                    when {
                        isPlaying -> {
                            previewingRange.value = false
                            controller.pause()
                        }
                        // Already loaded and paused — exactly where the range stop leaves it.
                        // `startPlayingUri` short-circuits that case into a resume in place and ignores
                        // the offset, so replaying would pause instantly at the tail it stopped on.
                        // Seek first, then resume.
                        isLoadedHere -> {
                            controller.seekTo(startMs)
                            previewingRange.value = true
                            controller.resume()
                        }
                        else -> {
                            previewingRange.value = true
                            controller.startPlayingUri(context, source, startPositionMs = startMs)
                        }
                    }
                },
            )
            TextButton(
                onClick = {
                    if (previewingRange.value && isPlaying) {
                        previewingRange.value = false
                        controller.pause()
                    }
                    onSelectionChange(TrimSelection.WHOLE)
                    onExpandedChange(false)
                },
            ) {
                Text(text = stringResource(R.string.app_addbutton_trim_keep_whole))
            }
        }
    }
}

/** Preview control plus the two numbers that matter: where the cut starts and how long it keeps. */
@Composable
private fun TrimRangeRow(
    startMs: Int,
    keptMs: Int,
    isPlaying: Boolean,
    onTogglePreview: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledIconButton(
            onClick = onTogglePreview,
            modifier = Modifier.size(44.dp),
            colors =
                IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
        ) {
            Icon(
                painter =
                    if (isPlaying) {
                        rememberVectorPainter(AppIcons.Pause)
                    } else {
                        painterResource(R.drawable.app_ic_play_arrow)
                    },
                contentDescription = stringResource(R.string.app_addbutton_trim_preview_description),
            )
        }
        Text(
            text = stringResource(R.string.app_addbutton_trim_range, formatDuration(startMs), formatDuration(keptMs)),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

/**
 * The clip's amplitude envelope, decoded off the main thread and re-decoded only when the source
 * changes. `null` until it lands (or if it failed), which the renderer draws as a neutral baseline —
 * the handles work meanwhile, being driven by the duration from metadata, not by the envelope.
 *
 * Gated on [enabled] — the editor being OPEN, not merely offered. The decode copies the whole file to
 * cache and runs it through MediaCodec, which for an inbound share near the 50 MB cap is seconds of
 * work and a 50 MB cache write; doing that behind a collapsed call to action the user may never tap
 * would put it on the add flow's critical path for nothing.
 */
@Composable
internal fun rememberTrimEnvelope(
    context: Context,
    source: Uri,
    enabled: Boolean,
): FloatArray? {
    val peaks by produceState<FloatArray?>(initialValue = null, source, enabled) {
        if (!enabled) return@produceState
        value = WaveformExtractor.extract(context, source, TRIM_ENVELOPE_BARS)
    }
    return peaks
}

/**
 * Envelope resolution for the trim wave. Denser than the listen surface's 56 bars would buy little:
 * the bars are a shape to aim the handles at, and the range readout below is what states the cut.
 */
private const val TRIM_ENVELOPE_BARS = 56
private val ICON_SIZE = 18.dp
