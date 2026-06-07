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

// --- Seeded scroll benchmark (controlled list size, hypothesis H2: does scroll jank scale with N?) ---

/**
 * Launch-intent extra carrying how many synthetic sounds to seed. Must match `SEED_COUNT_EXTRA` in
 * the app's benchmark-variant `CustomBuildTypeApplication`.
 */
internal const val SEED_COUNT_EXTRA = "benchmark_seed_count"

/**
 * Display-name prefix of seeded sounds; the benchmark waits for one to render before scrolling. Must
 * match `SYNTHETIC_NAME_PREFIX` in the app's benchmark-variant `CustomBuildTypeApplication`.
 */
internal const val SYNTHETIC_NAME_PREFIX = "Benchmark sound"

/** Max wait for the seeded corpus to render before scrolling. Seeding is async + persisted. */
internal const val SEED_RENDER_TIMEOUT_MS = 15_000L

/** Three list sizes — few / medium / many — to expose whether scroll jank scales with item count. */
internal const val SMALL_LIST = 5
internal const val MEDIUM_LIST = 50
internal const val LARGE_LIST = 200
