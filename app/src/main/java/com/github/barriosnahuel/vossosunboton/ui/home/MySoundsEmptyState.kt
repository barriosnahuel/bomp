/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
 * the welcome audio: keep close the voices that matter, hear them whenever you want. Owns the one
 * import imperative for this state — [onImportClick] opens the same add-a-Bomp Hub as the FAB,
 * which the host suppresses here so there is exactly one call to action. [onShowOnboarding] is the
 * soft secondary exit below it — a low-commitment "see how it works" that opens the tour.
 */
@Composable
internal fun MySoundsEmptyState(
    onImportClick: () -> Unit,
    onShowOnboarding: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rippleColor = MaterialTheme.colorScheme.primary

    // heightIn(min = maxHeight) keeps the group vertically centered while it fits the viewport (the
    // common case — "the copy paints the room"), and lets the Column grow past it so verticalScroll
    // makes the CTA reachable when it doesn't fit (short screen / large font scale). Plain
    // fillMaxSize + verticalScroll would defeat Arrangement.Center (scroll measures content with
    // unbounded height, leaving no free space to distribute) and top-align the group.
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
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
            Spacer(Modifier.height(Spacing.XL))
            // Filled-primary tier (ADR 0010): the single main action of an otherwise empty screen.
            // Opens the import Hub — the same destination the FAB carries when the list is non-empty.
            Button(
                onClick = onImportClick,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text(text = stringResource(R.string.app_my_sounds_empty_cta))
            }
            Spacer(Modifier.height(Spacing.SM))
            // Text tier (ADR 0010): a low-commitment secondary below the single Import imperative —
            // inherits `primary`, no container, so it never competes with the filled CTA above.
            SeeHowItWorksButton(onClick = onShowOnboarding)
        }
    }
}

/**
 * Text-tier (ADR 0010) "see how it works" CTA that opens the onboarding tour. Shared by this empty-state
 * secondary and the welcome-audio footer (SoundsList) so the two never drift in copy or button typology.
 */
@Composable
internal fun SeeHowItWorksButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text = stringResource(R.string.app_my_sounds_empty_secondary))
    }
}

private val RIPPLE_SIZE = 144.dp
private val RIPPLE_STROKE = 1.5.dp
private val INNER_RIPPLE_RADIUS = 24.dp
private val MID_RIPPLE_RADIUS = 48.dp
private val OUTER_RIPPLE_RADIUS = 72.dp
private const val INNER_RIPPLE_ALPHA = 0.85f
private const val MID_RIPPLE_ALPHA = 0.55f
private const val OUTER_RIPPLE_ALPHA = 0.30f
