/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.android.analytics

import android.os.Bundle

/**
 * No-op tracker used in tests that only need the wrapper to not crash. Tests that need to verify emission should use
 * the `FakeAnalyticsTracker` fixture instead.
 */
class NoOpAnalyticsTracker : AnalyticsTracker {
    override fun log(event: AnalyticsEvent) = Unit

    override fun setUserProperty(
        name: String,
        value: String,
    ) = Unit

    override fun logScreen(
        screenName: String,
        extras: Bundle?,
    ) = Unit
}
