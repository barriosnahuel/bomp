/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.waveform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress

/**
 * Shared amplitude-envelope waveform: [barCount] vertical bars drawn from a real `peaks` envelope
 * (0..1, normalized by `WaveformExtractor` / `buildRecorderEnvelope`), whose left portion fills with
 * `primary` as playback advances and the rest dims. While `peaks` is still decoding (or decoding
 * failed) it is `null` and a neutral placeholder baseline shows — never a fake-looking wave.
 *
 * Per-surface differences are parameters: bar width ([barFill]); [onSeek], which when non-null wires
 * drag-to-scrub plus the `SetProgress` semantics action (a `null` renders a read-only wave); and
 * [barCount], which sizes the placeholder grid only — once `peaks` arrives the bar count follows
 * `peaks.size`, so a caller must decode at the same resolution it passes here.
 *
 * The one shared component, the unified left-edge played boundary, and the per-surface params:
 * docs/adr/0020-shared-envelope-waveform.md.
 */
@Composable
internal fun EnvelopeWaveform(
    progress: Float,
    peaks: FloatArray?,
    contentDescription: String,
    barCount: Int,
    barFill: Float,
    modifier: Modifier = Modifier,
    onSeek: ((Float) -> Unit)? = null,
) {
    val playedColor = MaterialTheme.colorScheme.primary
    val unplayedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = WAVEFORM_UNPLAYED_ALPHA)
    val label = contentDescription
    val effectiveBars = peaks?.size ?: barCount
    var scrub by remember { mutableStateOf<Float?>(null) }
    val displayFraction = (scrub ?: progress).coerceIn(0f, 1f)

    val semanticsModifier =
        modifier.semantics {
            this.contentDescription = label
            progressBarRangeInfo = ProgressBarRangeInfo(displayFraction, 0f..1f)
            if (onSeek != null) {
                setProgress { target ->
                    onSeek(target.coerceIn(0f, 1f))
                    true
                }
            }
        }
    val canvasModifier =
        if (onSeek != null) {
            semanticsModifier.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> scrub = (offset.x / size.width.toFloat()).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        scrub = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        scrub?.let(onSeek)
                        scrub = null
                    },
                    onDragCancel = { scrub = null },
                )
            }
        } else {
            semanticsModifier
        }

    Canvas(modifier = canvasModifier) {
        val slot = size.width / effectiveBars
        val barWidth = slot * barFill
        val centerY = size.height / 2f
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
        for (i in 0 until effectiveBars) {
            val amplitude = peaks?.getOrNull(i) ?: WAVEFORM_PLACEHOLDER_FRACTION
            val barHeight = amplitude.coerceIn(WAVEFORM_MIN_BAR, 1f) * size.height
            val played = i.toFloat() / effectiveBars < displayFraction
            val x = i * slot + (slot - barWidth) / 2f
            drawRoundRect(
                color = if (played) playedColor else unplayedColor,
                topLeft = Offset(x, centerY - barHeight / 2f),
                size = Size(barWidth, barHeight),
                cornerRadius = corner,
            )
        }
    }
}

// Neutral baseline drawn for every bar while the real envelope is still decoding (peaks == null).
private const val WAVEFORM_PLACEHOLDER_FRACTION = 0.12f
private const val WAVEFORM_UNPLAYED_ALPHA = 0.30f
