/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.util

import java.util.Locale

private const val HL_PARAM = "hl"

private const val ES_AR = "es-AR"
private const val ES_ES = "es-ES"
private const val ES_419 = "es-419"
private const val EN = "en"
private const val PT_BR = "pt-BR"

internal fun toSupportedHl(locale: Locale): String =
    when (locale.language) {
        "es" ->
            when (locale.country) {
                "AR" -> ES_AR
                "ES" -> ES_ES
                else -> ES_419
            }
        "en" -> EN
        "pt" -> PT_BR
        else -> EN
    }

internal fun appendHl(
    url: String,
    hl: String,
): String {
    val separator = if (url.contains('?')) '&' else '?'
    return "$url$separator$HL_PARAM=$hl"
}

fun String.withDeviceHl(): String = appendHl(this, toSupportedHl(Locale.getDefault()))
