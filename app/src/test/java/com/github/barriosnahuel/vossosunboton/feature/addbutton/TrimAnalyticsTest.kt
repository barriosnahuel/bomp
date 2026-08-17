/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.commons.android.analytics.FakeAnalyticsTracker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Wire contract for `sound_trim`. The literals are hardcoded on purpose (CLAUDE.md § Analytics
 * events): reading them from the constants would let a rename slip through to the dashboard and
 * BigQuery unnoticed.
 */
internal class TrimAnalyticsTest : AbstractRobolectricTest() {
    private val fake = FakeAnalyticsTracker()

    @Test
    fun `a landed cut reports how much was kept out of how long the original was`() {
        trackSoundTrim(keptMs = 4_000, sourceMs = 180_000, fellBack = false, tracker = fake)

        val event = fake.assertEmitted("sound_trim")
        assertThat(event.params["kept_ms"]).isEqualTo(4_000)
        assertThat(event.params["source_ms"]).isEqualTo(180_000)
        assertThat(event.params["outcome"]).isEqualTo("applied")
    }

    @Test
    fun `a cut that could not be produced reports the fallback, so the per-codec failure rate is visible`() {
        trackSoundTrim(keptMs = 4_000, sourceMs = 180_000, fellBack = true, tracker = fake)

        assertThat(fake.assertEmitted("sound_trim").params["outcome"]).isEqualTo("fallback")
    }
}
