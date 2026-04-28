/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import androidx.annotation.VisibleForTesting

/**
 * Factory for the [AddButtonFeature] singleton. Call sites obtain the feature via [get]; tests inject a fake via
 * [setForTest]. Mirrors `AnalyticsTrackerProvider` so the AddButton flow is substitutable from Robolectric Compose
 * tests without mocking the Firebase wrapper or reaching into the underlying file I/O.
 */
object AddButtonFeatureProvider {
    @Volatile
    private var override: AddButtonFeature? = null

    fun get(): AddButtonFeature = override ?: AddButtonFeature.instance

    /**
     * Replace the production feature for tests. Call from `@Before`; reset to `null` from `@After` if isolation is
     * needed.
     */
    @VisibleForTesting
    fun setForTest(feature: AddButtonFeature?) {
        override = feature
    }
}
