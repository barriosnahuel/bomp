/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/*
 * Bomp's spring vocabulary — the project's motion specs while Material 3's MotionScheme is still
 * internal in material3 1.4.0 (not consumable from app code; migration is a follow-up). Two flavours,
 * following the same spatial-vs-effects split the scheme uses internally:
 *  - spatial: things that move (enter/exit, size, position) — a touch of bounce is Bomp's character.
 *  - effects: things that change colour/alpha — no bounce; a bouncing colour reads as a glitch.
 * Centralised so "the Bomp spring" lives in one place: the future migration to the public
 * MotionScheme becomes a one-file change. Rationale: ../push-me-backlog/backlog/004-physics-based-motion.md.
 */

/** Spatial spring for entrances, exits and layout — carries a subtle bounce (Bomp character). */
internal fun <T> bompSpatialSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

/** Effects spring for colour/alpha changes — no bounce. */
internal fun <T> bompEffectsSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
