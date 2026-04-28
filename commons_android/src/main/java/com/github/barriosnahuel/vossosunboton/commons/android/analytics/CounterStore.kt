/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

/**
 * Persistent monotonic counters for lifetime aggregates surfaced as Firebase user properties (`lifetime_shares`,
 * `lifetime_plays`, etc.). Counters reset only on uninstall.
 */
interface CounterStore {
    /** Atomically increment the counter at [key] and return the new value. */
    fun increment(key: String): Long

    /** Read the current value at [key]; returns `0` if it has never been incremented. */
    fun get(key: String): Long
}
