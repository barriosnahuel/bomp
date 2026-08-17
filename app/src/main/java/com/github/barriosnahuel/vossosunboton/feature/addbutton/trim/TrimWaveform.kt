/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton.trim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.feature.waveform.WAVEFORM_MIN_BAR

/**
 * Range editor over an amplitude envelope: the bars inside the kept range render in `primary`, the
 * discarded head and tail dim out, and a thin playhead tracks the range preview.
 *
 * The two handles are a Material 3 [RangeSlider] laid over the wave rather than hand-rolled drag
 * gestures on the Canvas. That buys correct touch-target sizing and the TalkBack semantics a custom
 * two-thumb Canvas gesture would have to reinvent (each thumb is focusable and adjustable with the
 * accessibility gestures), and it keeps the control reading like the sliders already in this flow —
 * `AudioPreview` sits right above it. The Canvas underneath is decorative and takes no input.
 *
 * A separate renderer from the shared `EnvelopeWaveform` on purpose — a two-handle range selector is
 * ADR 0020's "materially different envelope" revisit criterion firing; the shared floor and
 * normalization are still single-sourced: docs/adr/0028-add-flow-audio-trim.md.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TrimWaveform(
    selection: TrimSelection,
    peaks: FloatArray?,
    durationMs: Int,
    playheadFraction: Float?,
    handlesContentDescription: String,
    onSelectionChange: (TrimSelection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val keptColor = MaterialTheme.colorScheme.primary
    val discardedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = TRIM_DISCARDED_ALPHA)
    // onSurfaceVariant, not an accent: the playhead crosses bars drawn in `primary`, and an accent on
    // top of them would vanish exactly where it matters most.
    val playheadColor = MaterialTheme.colorScheme.onSurfaceVariant
    val effectiveBars = peaks?.size ?: TRIM_BAR_COUNT

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    // Inset by the slider's thumb radius so bar 0 lines up with the leftmost handle
                    // position instead of sitting half a thumb outside the range the handles can reach.
                    .padding(horizontal = THUMB_RADIUS)
                    .height(WAVE_HEIGHT)
                    // The wave is a picture of the audio; the RangeSlider on top carries the value and
                    // its own label, so leaving both would make TalkBack announce the control twice.
                    .clearAndSetSemantics { },
        ) {
            val slot = size.width / effectiveBars
            val barWidth = slot * TRIM_BAR_FILL
            val centerY = size.height / 2f
            val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
            for (i in 0 until effectiveBars) {
                val amplitude = peaks?.getOrNull(i) ?: TRIM_PLACEHOLDER_FRACTION
                val barHeight = amplitude.coerceIn(WAVEFORM_MIN_BAR, 1f) * size.height
                val barFraction = i.toFloat() / effectiveBars
                val kept = barFraction >= selection.startFraction && barFraction < selection.endFraction
                drawRoundRect(
                    color = if (kept) keptColor else discardedColor,
                    topLeft = Offset(i * slot + (slot - barWidth) / 2f, centerY - barHeight / 2f),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
            playheadFraction?.let { fraction ->
                val x = fraction.coerceIn(0f, 1f) * size.width
                drawRect(
                    color = playheadColor,
                    topLeft = Offset(x - PLAYHEAD_WIDTH.toPx() / 2f, 0f),
                    size = Size(PLAYHEAD_WIDTH.toPx(), size.height),
                )
            }
        }
        RangeSlider(
            value = selection.startFraction..selection.endFraction,
            onValueChange = { range ->
                onSelectionChange(resolveHandleDrag(selection, range.start, range.endInclusive, durationMs))
            },
            colors =
                SliderDefaults.colors(
                    // thumbColor is deliberately NOT overridden: it inherits `primary`, the role
                    // AppThemeContrastTest sanctions on `surfaceVariant` and the one the sibling slider
                    // in AudioPreview already uses. `primaryContainer` here measures ~1.01:1 against
                    // this card in light mode — an interactive control the user cannot see.
                    //
                    // The wave underneath already shows kept-vs-discarded; a painted track on top of it
                    // would be a second, redundant encoding of the same range.
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
            modifier =
                Modifier.fillMaxWidth().semantics {
                    // Names the range control for screen readers; the visible label sits above it (WCAG 2.5.3).
                    contentDescription = handlesContentDescription
                },
        )
    }
}

private const val TRIM_BAR_COUNT = 56
private const val TRIM_BAR_FILL = 0.55f

/** Neutral baseline drawn for every bar while the real envelope is still decoding. */
private const val TRIM_PLACEHOLDER_FRACTION = 0.12f

/** Emphasis of the head/tail the user is throwing away — dim, but still legibly a waveform. */
private const val TRIM_DISCARDED_ALPHA = 0.30f

private val WAVE_HEIGHT = 72.dp
private val THUMB_RADIUS = 10.dp
private val PLAYHEAD_WIDTH = 2.dp
