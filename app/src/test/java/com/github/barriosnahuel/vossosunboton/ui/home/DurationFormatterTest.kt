package com.github.barriosnahuel.vossosunboton.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test

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
}
