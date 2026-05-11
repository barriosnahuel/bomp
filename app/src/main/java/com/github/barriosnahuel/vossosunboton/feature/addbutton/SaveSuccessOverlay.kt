/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Brand "voice bubble" confirmation rendered after a successful save. Inflates from the centre with a spring
 * overshoot, holds the name for ~350 ms, then slides up and fades out — metaphor for "your Bomp is on its way".
 *
 * Honours the system "Remove animations" setting (Accessibility): when `ANIMATOR_DURATION_SCALE = 0`, the same
 * content is shown statically for [STATIC_HOLD_MS] and dismissed instantly, preserving the equivalent
 * experience without motion.
 */
@Composable
internal fun SaveSuccessOverlay(
    name: String,
    @StringRes announcementTemplate: Int,
    @StringRes subtitleId: Int,
    onFinished: () -> Unit,
) {
    val reduceMotion = rememberReduceMotionEnabled()
    val announcement = stringResource(id = announcementTemplate, name)
    val subtitle = stringResource(id = subtitleId)

    LaunchedEffect(Unit) {
        delay(if (reduceMotion) STATIC_HOLD_MS else ENTRY_PLUS_HOLD_MS)
        onFinished()
    }

    BackHandler { /* swallow back while the overlay is visible — save already persisted */ }

    AnimatedVisibility(
        visible = true,
        enter =
            if (reduceMotion) {
                fadeIn(snap())
            } else {
                fadeIn() +
                    scaleIn(
                        initialScale = INITIAL_SCALE,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                    )
            },
        exit = ExitTransition.None,
        label = "save_success_overlay",
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .pointerInput(Unit) {
                        awaitPointerEventScope { while (true) awaitPointerEvent() }
                    }.semantics {
                        liveRegion = LiveRegionMode.Polite
                        contentDescription = announcement
                    },
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(BUBBLE_CORNER_DP.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier =
                    Modifier
                        .widthIn(min = BUBBLE_MIN_WIDTH_DP.dp)
                        .padding(horizontal = 32.dp)
                        .rotate(BUBBLE_TILT_DEG),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(CHECK_ICON_DP.dp),
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = name,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberReduceMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

private const val STATIC_HOLD_MS = 600L
private const val ENTRY_PLUS_HOLD_MS = 600L
private const val INITIAL_SCALE = 0.3f
private const val BUBBLE_TILT_DEG = -2f
private const val BUBBLE_CORNER_DP = 28
private const val BUBBLE_MIN_WIDTH_DP = 220
private const val CHECK_ICON_DP = 48
