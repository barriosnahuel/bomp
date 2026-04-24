/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

internal data class CreditEntry(
    val name: String,
    val copyright: String?,
    val license: String,
    val url: String?,
)

internal fun parseCreditEntries(text: String): List<CreditEntry> {
    val separatorRegex = Regex("^-{40,}$")
    val entries = mutableListOf<CreditEntry>()
    var currentLines = mutableListOf<String>()
    var inEntry = false

    for (line in text.lines()) {
        val trimmed = line.trim()
        if (!separatorRegex.matches(trimmed)) {
            if (inEntry && trimmed.isNotBlank()) currentLines.add(trimmed)
            continue
        }
        if (inEntry) entries.addAll(listOfNotNull(parseEntry(currentLines)))
        currentLines = mutableListOf()
        inEntry = !inEntry
    }
    return entries
}

private fun parseEntry(lines: List<String>): CreditEntry? {
    if (lines.isEmpty()) return null
    val name = lines[0]
    var copyright: String? = null
    var license: String? = null
    var url: String? = null
    lines.drop(1).forEach { line ->
        when {
            line.startsWith("Copyright") -> copyright = line
            line.startsWith("http") -> url = line
            line.startsWith("Full text:") -> url = line
            license == null -> license = line
        }
    }
    return license?.let { CreditEntry(name = name, copyright = copyright, license = it, url = url) }
}
