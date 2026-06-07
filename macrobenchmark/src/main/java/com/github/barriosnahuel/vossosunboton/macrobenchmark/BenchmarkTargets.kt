/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.macrobenchmark

/**
 * Shared constants for the LandingActivity performance benchmarks.
 *
 * The `benchmark` build type inherits the release applicationId (no `.debug` suffix), so the target
 * package is the release one.
 */
internal const val TARGET_PACKAGE = "com.github.barriosnahuel.vossosunboton"

/** Iterations per benchmark. Kept modest so a full local run stays under a few minutes. */
internal const val DEFAULT_ITERATIONS = 5

/** Number of fling gestures per scroll-benchmark iteration. */
internal const val SCROLL_GESTURES = 3

/**
 * Fraction of the screen width reserved as a gesture margin so flings start inside the list and not
 * on the system back-gesture edges. `displayWidth / 5` ≈ 20% per side.
 */
internal const val GESTURE_MARGIN_DIVISOR = 5
