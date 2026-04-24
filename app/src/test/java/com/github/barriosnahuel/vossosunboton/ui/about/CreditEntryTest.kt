/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.about

import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class CreditEntryTest : AbstractRobolectricTest() {
    @Test
    fun `parseCreditEntries returns 9 entries for the bundled notices file`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val text =
            context.resources
                .openRawResource(R.raw.app_third_party_notices)
                .bufferedReader()
                .use { it.readText() }
        assertThat(parseCreditEntries(text)).hasSize(9)
    }

    @Test
    fun `parseCreditEntries correctly parses name copyright license and url`() {
        val input =
            """
            |--------------------------------------------------------------------------------
            |Timber
            |Copyright (C) 2013 Jake Wharton
            |Apache License, Version 2.0
            |https://github.com/JakeWharton/timber
            |--------------------------------------------------------------------------------
            """.trimMargin()
        val entries = parseCreditEntries(input)
        assertThat(entries).hasSize(1)
        val entry = entries[0]
        assertThat(entry.name).isEqualTo("Timber")
        assertThat(entry.copyright).isEqualTo("Copyright (C) 2013 Jake Wharton")
        assertThat(entry.license).isEqualTo("Apache License, Version 2.0")
        assertThat(entry.url).isEqualTo("https://github.com/JakeWharton/timber")
    }

    @Test
    fun `parseCreditEntries skips entries without a license line`() {
        val input =
            """
            |--------------------------------------------------------------------------------
            |Apache License, Version 2.0
            |Full text: https://www.apache.org/licenses/LICENSE-2.0
            |--------------------------------------------------------------------------------
            """.trimMargin()
        assertThat(parseCreditEntries(input)).isEmpty()
    }

    @Test
    fun `parseCreditEntries handles entry without copyright`() {
        val input =
            """
            |--------------------------------------------------------------------------------
            |SomeLibrary
            |MIT License
            |https://example.com/somelibrary
            |--------------------------------------------------------------------------------
            """.trimMargin()
        val entries = parseCreditEntries(input)
        assertThat(entries).hasSize(1)
        assertThat(entries[0].copyright).isNull()
        assertThat(entries[0].license).isEqualTo("MIT License")
    }
}
