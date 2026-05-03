/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

/**
 * Composes a [FirstFlagStore] and a [CounterStore] into the combined [AnalyticsStore] interface.
 * Production wires [DataStoreFirstFlagStore] + [DataStoreCounterStore]; tests can swap either
 * side independently.
 */
internal class DefaultAnalyticsStore(
    flags: FirstFlagStore,
    counters: CounterStore,
) : AnalyticsStore,
    FirstFlagStore by flags,
    CounterStore by counters
