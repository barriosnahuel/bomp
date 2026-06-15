/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

internal class DurationFormatterTest {
    // Noon (not midnight) so the calendar day cannot flip across the host timezone offset.
    private fun epochOf(
        year: Int,
        month: Int,
        day: Int,
    ): Long =
        Calendar
            .getInstance()
            .apply {
                clear()
                set(year, month, day, 12, 0, 0)
            }.timeInMillis

    @Test
    fun `fullDate en-US formats day-month-year uppercased`() {
        val epoch = epochOf(2024, Calendar.DECEMBER, 24)
        assertThat(fullDate(epoch, Locale.US)).isEqualTo("24 DEC 2024")
    }

    @Test
    fun `fullDate is uppercase and carries day and year regardless of locale`() {
        val epoch = epochOf(2024, Calendar.DECEMBER, 24)
        val formatted = fullDate(epoch, Locale.of("es", "AR"))
        // Month abbreviation text varies by JVM locale data, so assert structure, not exact MMM.
        assertThat(formatted).isEqualTo(formatted.uppercase(Locale.of("es", "AR")))
        assertThat(formatted).contains("24")
        assertThat(formatted).contains("2024")
    }

    @Test
    fun `fullDate single-digit day has no leading zero`() {
        val epoch = epochOf(2024, Calendar.JANUARY, 5)
        assertThat(fullDate(epoch, Locale.US)).isEqualTo("5 JAN 2024")
    }

    @Test
    fun `formatDuration zero milliseconds returns 0 colon 00`() {
        assertThat(formatDuration(0)).isEqualTo("0:00")
    }

    @Test
    fun `formatDuration 59999 ms returns 0 colon 59`() {
        assertThat(formatDuration(59_999)).isEqualTo("0:59")
    }

    @Test
    fun `formatDuration 60000 ms returns 1 colon 00`() {
        assertThat(formatDuration(60_000)).isEqualTo("1:00")
    }

    @Test
    fun `formatDuration 3723000 ms returns 62 colon 03`() {
        assertThat(formatDuration(3_723_000)).isEqualTo("62:03")
    }

    @Test
    fun `relativeDateDays returns 0 for same day`() {
        val now = System.currentTimeMillis()
        assertThat(relativeDateDays(now, nowMs = now)).isEqualTo(0L)
    }

    @Test
    fun `relativeDateDays returns 1 for yesterday`() {
        val now = System.currentTimeMillis()
        val yesterday = now - TimeUnit.DAYS.toMillis(1)
        assertThat(relativeDateDays(yesterday, nowMs = now)).isEqualTo(1L)
    }

    @Test
    fun `relativeDateDays returns 3 for three days ago`() {
        val now = System.currentTimeMillis()
        val threeDaysAgo = now - TimeUnit.DAYS.toMillis(3)
        assertThat(relativeDateDays(threeDaysAgo, nowMs = now)).isEqualTo(3L)
    }

    @Test
    fun `relativeDateDays returns 10 for ten days ago`() {
        val now = System.currentTimeMillis()
        val tenDaysAgo = now - TimeUnit.DAYS.toMillis(10)
        assertThat(relativeDateDays(tenDaysAgo, nowMs = now)).isEqualTo(10L)
    }
}
