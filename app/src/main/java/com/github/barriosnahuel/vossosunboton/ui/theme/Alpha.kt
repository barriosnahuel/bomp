/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.theme

/*
 * Named alpha constants shared across the design system.
 *
 * A bare `.copy(alpha = 0.NN)` in a Composable is a magic number — invented in the moment, not
 * greppable, and its contrast never reviewed. Reach for a semantic role from AppTheme.kt first;
 * when a translucent overlay or emphasis is genuinely needed (a tint composited over a variable
 * background, which a solid role can't express), name the alpha rather than inline the literal.
 *
 * Enforced by scripts/check-adr-invariants.sh (CI job adr-invariants): a raw numeric
 * .copy(alpha = N) under feature/ or ui/ fails the build. See CLAUDE.md § Design system.
 * Single-use alphas may instead live as a private const val next to their Composable (the
 * pattern already used by VaultScreen and MySoundsEmptyState).
 */

/** Tint behind the play control while a sound or preview is playing (over `primaryContainer`). */
internal const val PLAYING_TINT_ALPHA = 0.18f

/** Material 3 disabled-state emphasis for a slider's inactive track and thumb (over `onSurfaceVariant`). */
internal const val DISABLED_TRACK_ALPHA = 0.24f

/** De-emphasized secondary text and input placeholders on a surface. */
internal const val MUTED_TEXT_ALPHA = 0.6f
