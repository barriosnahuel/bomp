/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.macrobenchmark

/**
 * Shared constants for the LandingActivity performance benchmarks.
 *
 * The `benchmark` build type carries `applicationIdSuffix ".debug"` (reuses the debug app identity +
 * the real debug Firebase config), so the target package has the `.debug` suffix.
 */
internal const val TARGET_PACKAGE = "com.github.barriosnahuel.vossosunboton.debug"

/**
 * Iterations per benchmark. `StartupMode.COLD` makes the **first** iteration a warmup outlier — a
 * 30-iteration TapLatency run measured 263 ms on iteration 1 and 35–64 ms on the other 29 — so the
 * count has to be high enough that one such sample doesn't swing the median the gates are read from.
 * At 5 the median moved 13% run-to-run (56.5 vs 49.9 ms); 15 keeps it stable while a full local run
 * still takes a few minutes.
 */
internal const val DEFAULT_ITERATIONS = 15

/** Number of fling gestures per scroll-benchmark iteration. */
internal const val SCROLL_GESTURES = 3

/** Max backward swipes to reset the list to the top between iterations (overshoots [SCROLL_GESTURES]). */
internal const val SCROLL_RESET_SWIPES = 12

/** Swipe dead-zone (fraction per edge) so UiScrollable flings clear the system back-gesture areas. */
internal const val SWIPE_DEAD_ZONE = 0.2

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

/**
 * Three list sizes — few / medium / many — to expose whether scroll jank scales with item count.
 * "Few" is 20 (not a handful): a list that fits on screen has no scrollable node, so the smallest
 * size must still overflow one screen to be scrollable.
 */
internal const val SMALL_LIST = 20
internal const val MEDIUM_LIST = 50
internal const val LARGE_LIST = 200

// --- Tap-to-sound latency guardrail (the ≤100 ms tap budget, pre/post playback-engine migration) ---

/**
 * Async trace section spanning tap → playback started. Must match `TAP_TO_SOUND_TRACE` in
 * `SoundsViewModel` — the span is emitted at the ViewModel boundary (not inside the engine) so it
 * stays comparable across playback-engine swaps.
 */
internal const val TAP_TO_SOUND_SECTION = "BompTapToSound"

/**
 * Display name of the one seeded sound backed by real audio bytes (synthetic index 0 — see the
 * benchmark variant's `CustomBuildTypeApplication.seedPlayableAudioFile`). The tap benchmark taps it.
 */
internal const val PLAYABLE_SOUND_NAME = "$SYNTHETIC_NAME_PREFIX 0"

/** Corpus size for the tap benchmark: one playable row is all it needs (no scrolling involved). */
internal const val TAP_SEED_COUNT = 1

/** Post-tap wait so prepare+start complete and the async section closes inside the measured window. */
internal const val TAP_SETTLE_MS = 2_000L

/**
 * Content description of the per-row Play icon button (`R.string.app_play`). UiAutomator reads the
 * localized string, so the benchmark assumes an English-locale device — it fails loudly otherwise.
 */
internal const val PLAY_BUTTON_DESC = "Play"
