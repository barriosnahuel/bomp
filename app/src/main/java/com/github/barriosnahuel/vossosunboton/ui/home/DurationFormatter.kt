package com.github.barriosnahuel.vossosunboton.ui.home

import java.util.Calendar
import java.util.concurrent.TimeUnit

internal fun formatDuration(ms: Int): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.toLong())
    val minutes = totalSeconds / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

@Suppress("MagicNumber")
internal fun startOfDay(epochMs: Long): Long =
    Calendar.getInstance().run {
        timeInMillis = epochMs
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

internal const val RELATIVE_DATE_MAX_DAYS = 7L
private const val SECONDS_PER_MINUTE = 60L
