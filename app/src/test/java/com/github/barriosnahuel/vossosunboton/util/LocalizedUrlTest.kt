/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.util

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import java.util.Locale

internal class LocalizedUrlTest {
    private val originalDefault = Locale.getDefault()

    @After
    fun restoreDefault() {
        Locale.setDefault(originalDefault)
    }

    @Test
    fun `Argentine Spanish maps to es-AR`() {
        assertThat(toSupportedHl(Locale("es", "AR"))).isEqualTo("es-AR")
    }

    @Test
    fun `Spain Spanish maps to es-ES`() {
        assertThat(toSupportedHl(Locale("es", "ES"))).isEqualTo("es-ES")
    }

    @Test
    fun `Latin American Spanish countries fall back to es-419`() {
        listOf("MX", "CL", "UY", "CO", "PE", "BO", "419").forEach { country ->
            assertThat(toSupportedHl(Locale("es", country))).isEqualTo("es-419")
        }
    }

    @Test
    fun `Spanish without country falls back to es-419`() {
        assertThat(toSupportedHl(Locale("es"))).isEqualTo("es-419")
    }

    @Test
    fun `English of any country maps to en`() {
        listOf("US", "GB", "AU", "IN", "CA").forEach { country ->
            assertThat(toSupportedHl(Locale("en", country))).isEqualTo("en")
        }
        assertThat(toSupportedHl(Locale("en"))).isEqualTo("en")
    }

    @Test
    fun `Portuguese of any country maps to pt-BR`() {
        listOf("BR", "PT", "AO", "MZ").forEach { country ->
            assertThat(toSupportedHl(Locale("pt", country))).isEqualTo("pt-BR")
        }
    }

    @Test
    fun `unsupported languages fall back to en`() {
        listOf(
            Locale("ja", "JP"),
            Locale("fr", "FR"),
            Locale("de", "DE"),
            Locale("zh", "CN"),
            Locale("it", "IT"),
            Locale.ROOT,
        ).forEach { locale ->
            assertThat(toSupportedHl(locale)).isEqualTo("en")
        }
    }

    @Test
    fun `appendHl uses question mark when URL has no query string`() {
        assertThat(appendHl("https://example.com/page", "es-AR"))
            .isEqualTo("https://example.com/page?hl=es-AR")
    }

    @Test
    fun `appendHl uses ampersand when URL already has a query string`() {
        assertThat(appendHl("https://example.com/page?foo=bar", "en"))
            .isEqualTo("https://example.com/page?foo=bar&hl=en")
    }

    @Test
    fun `withDeviceHl uses the JVM default locale`() {
        Locale.setDefault(Locale("pt", "BR"))
        assertThat("https://example.com/p".withDeviceHl()).isEqualTo("https://example.com/p?hl=pt-BR")

        Locale.setDefault(Locale("ja", "JP"))
        assertThat("https://example.com/p".withDeviceHl()).isEqualTo("https://example.com/p?hl=en")
    }
}
