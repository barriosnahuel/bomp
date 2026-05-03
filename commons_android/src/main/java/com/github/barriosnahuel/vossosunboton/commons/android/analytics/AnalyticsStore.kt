/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

/**
 * Combined persistence contract for the analytics wrapper. Production wires a [DefaultAnalyticsStore]
 * composing [DataStoreFirstFlagStore] (for first-flag bookkeeping) and [DataStoreCounterStore]
 * (for lifetime counters) — they live in separate DataStore files so each can have its own backup
 * posture. Tests can implement [AnalyticsStore] directly to control either side.
 */
internal interface AnalyticsStore :
    FirstFlagStore,
    CounterStore
