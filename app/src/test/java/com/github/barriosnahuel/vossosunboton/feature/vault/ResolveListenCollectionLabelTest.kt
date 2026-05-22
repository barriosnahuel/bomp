/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionProfile
import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class ResolveListenCollectionLabelTest {
    private fun privateCollection(
        id: String,
        name: String,
        system: Boolean = false,
    ) = Collection(id = id, name = name, profile = CollectionProfile.VAULT, isSystem = system)

    private fun publicCollection(
        id: String,
        name: String,
    ) = Collection(id = id, name = name, profile = CollectionProfile.GENERIC_PUBLIC)

    @Test
    fun `prefers the active filter collection when the audio belongs to it`() {
        val collections = listOf(privateCollection("a", "Caro"), privateCollection("b", "Trabajo"))
        val byAudio = mapOf("s1" to listOf("a", "b"))

        val label = resolveListenCollectionLabel("s1", collections, byAudio, activeFilter = "b", systemLabel = "Baúl")

        assertThat(label).isEqualTo("Trabajo")
    }

    @Test
    fun `falls back to the first private membership when no filter is active`() {
        val collections = listOf(privateCollection("a", "Caro"), privateCollection("b", "Trabajo"))
        val byAudio = mapOf("s1" to listOf("a", "b"))

        val label = resolveListenCollectionLabel("s1", collections, byAudio, activeFilter = null, systemLabel = "Baúl")

        assertThat(label).isEqualTo("Caro")
    }

    @Test
    fun `resolves the system collection to the locale-aware label`() {
        val collections = listOf(privateCollection("sys", "internal-name", system = true))
        val byAudio = mapOf("s1" to listOf("sys"))

        val label = resolveListenCollectionLabel("s1", collections, byAudio, activeFilter = null, systemLabel = "My Vault")

        assertThat(label).isEqualTo("My Vault")
    }

    @Test
    fun `returns empty when the audio has no private membership`() {
        val collections = listOf(publicCollection("p", "Familia"))
        val byAudio = mapOf("s1" to listOf("p"))

        val label = resolveListenCollectionLabel("s1", collections, byAudio, activeFilter = null, systemLabel = "Baúl")

        assertThat(label).isEmpty()
    }
}
