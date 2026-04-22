/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.github.barriosnahuel.vossosunboton.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

fun formatDuration(ms: Int): String {
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

fun relativeDateDays(
    epochMs: Long,
    nowMs: Long = System.currentTimeMillis(),
): Long {
    val today = startOfDay(nowMs)
    val dateDay = startOfDay(epochMs)
    return TimeUnit.MILLISECONDS.toDays(today - dateDay)
}

@Composable
fun formatRelativeDate(epochMs: Long): String {
    val diffDays = relativeDateDays(epochMs)
    val resources = LocalResources.current
    return when {
        diffDays == 0L -> stringResource(R.string.app_date_today)
        diffDays == 1L -> stringResource(R.string.app_date_yesterday)
        diffDays < RELATIVE_DATE_MAX_DAYS -> resources.getQuantityString(R.plurals.app_date_days_ago, diffDays.toInt(), diffDays.toInt())
        else -> SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(epochMs))
    }
}

internal const val RELATIVE_DATE_MAX_DAYS = 7L
private const val SECONDS_PER_MINUTE = 60L
