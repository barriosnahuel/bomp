/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.coroutines.cancellation.CancellationException

/**
 * Material 3-style predictive back for a visually discrete surface — an overlay or a full-screen
 * Composable. Drop-in replacement for a plain `BackHandler { onBack() }` so the back gesture previews
 * where it leads: as the finger drags, the surface translates aside, scales down and fades (driven by
 * the gesture `progress`, 0f→1f), revealing what sits behind it. Releasing mid-gesture cancels and the
 * surface springs back to rest; completing the gesture runs [onBack] exactly once.
 *
 * Honours the system "Remove animations" setting ([rememberReduceMotionEnabled]): the visual transform
 * is skipped and [onBack] still fires on completion, matching the OS's own instant collapse. On API < 33
 * the platform falls back to a binary back — identical to today's behaviour, so the animation is a pure
 * progressive enhancement with no regression on old devices.
 *
 * Keep a plain `BackHandler` for *back-stoppers* that must swallow the gesture (e.g. SaveSuccessOverlay
 * while its success animation runs): those intentionally do not animate out.
 *
 * @param enabled mirrors `BackHandler(enabled = …)` — when false the gesture passes through to the next
 *   handler registered up the dispatcher.
 * @param onBack invoked once when the gesture completes (never on cancel, never per progress frame).
 */
@Composable
fun Modifier.predictiveBackTransition(
    enabled: Boolean = true,
    onBack: () -> Unit,
): Modifier {
    val reduceMotion = rememberReduceMotionEnabled()
    // Held outside the graphicsLayer block and read only inside it, so each gesture frame invalidates
    // the layer in the draw phase without recomposing this modifier — the performant predictive path.
    val progress = remember { mutableFloatStateOf(0f) }

    PredictiveBackHandler(enabled = enabled) { events ->
        try {
            events.collect { event -> progress.floatValue = event.progress }
            // Reached only when the flow completes without cancellation: the gesture went through.
            onBack()
            progress.floatValue = 0f
        } catch (cancellation: CancellationException) {
            // Gesture released before completion, or the surface left composition mid-drag: reset to
            // rest, then rethrow so structured cancellation is honoured (idiomatic predictive-back).
            progress.floatValue = 0f
            throw cancellation
        }
    }

    return if (reduceMotion) {
        this
    } else {
        graphicsLayer {
            val fraction = progress.floatValue
            // Material 3 caps the horizontal drift so the surface stays partly on-screen all gesture.
            translationX = size.width * MAX_TRANSLATION_FRACTION * fraction
            val scale = 1f - fraction * SCALE_REDUCTION
            scaleX = scale
            scaleY = scale
            alpha = 1f - fraction * ALPHA_REDUCTION
        }
    }
}

private const val MAX_TRANSLATION_FRACTION = 0.35f
private const val SCALE_REDUCTION = 0.05f
private const val ALPHA_REDUCTION = 0.5f
