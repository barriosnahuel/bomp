package com.github.barriosnahuel.vossosunboton.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.concurrent.TimeUnit

internal class DurationFormatterTest {
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
