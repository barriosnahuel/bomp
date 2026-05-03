/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.ui.theme.Spacing

/**
 * Heart-first empty state for MY_SOUNDS. Renders only when the user has no custom sounds AND the
 * welcome sticker has been consumed (so the list is genuinely empty). Continues the voice from
 * the welcome audio: keep close the voices that matter, hear them whenever you want. Absent on
 * purpose: any imperative CTA — the FAB does the asking, the copy paints the room.
 */
@Composable
internal fun MySoundsEmptyState(modifier: Modifier = Modifier) {
    val rippleColor = MaterialTheme.colorScheme.primaryContainer

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Concentric ripples — a voice resonating into a quiet room. Alphas descend from inner
        // to outer so the eye anchors on the pulse and reads outward as a fade.
        Canvas(modifier = Modifier.size(RIPPLE_SIZE)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokePx = RIPPLE_STROKE.toPx()
            drawCircle(
                color = rippleColor.copy(alpha = INNER_RIPPLE_ALPHA),
                radius = INNER_RIPPLE_RADIUS.toPx(),
                center = center,
                style = Stroke(strokePx),
            )
            drawCircle(
                color = rippleColor.copy(alpha = MID_RIPPLE_ALPHA),
                radius = MID_RIPPLE_RADIUS.toPx(),
                center = center,
                style = Stroke(strokePx),
            )
            drawCircle(
                color = rippleColor.copy(alpha = OUTER_RIPPLE_ALPHA),
                radius = OUTER_RIPPLE_RADIUS.toPx(),
                center = center,
                style = Stroke(strokePx),
            )
        }
        Spacer(Modifier.height(Spacing.XL))
        Text(
            text = stringResource(R.string.app_my_sounds_empty_headline),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Spacing.MD))
        Text(
            text = stringResource(R.string.app_my_sounds_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            // `onSurfaceVariant` is the M3 role for secondary body text and is validated by
            // `AppThemeContrastTest` to meet WCAG AA. An alpha-modified `onSurface` would push
            // light-mode contrast below 4.5:1 for normal-size text — outside the audit's reach.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val RIPPLE_SIZE = 144.dp
private val RIPPLE_STROKE = 1.5.dp
private val INNER_RIPPLE_RADIUS = 24.dp
private val MID_RIPPLE_RADIUS = 48.dp
private val OUTER_RIPPLE_RADIUS = 72.dp
private const val INNER_RIPPLE_ALPHA = 0.55f
private const val MID_RIPPLE_ALPHA = 0.30f
private const val OUTER_RIPPLE_ALPHA = 0.15f
