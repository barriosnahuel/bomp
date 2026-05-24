/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.ui.home

import com.github.barriosnahuel.vossosunboton.model.Collection
import com.github.barriosnahuel.vossosunboton.model.CollectionProfile
import com.github.barriosnahuel.vossosunboton.testSound
import com.google.common.truth.Truth.assertThat
import org.junit.Test

internal class SearchResultLabelsTest {
    private val explore = "Explore"
    private val vault = "My Vault"

    private val work = Collection(id = "c-work", name = "Work", profile = CollectionProfile.GENERIC_PUBLIC)
    private val family = Collection(id = "c-family", name = "Family", profile = CollectionProfile.GENERIC_PUBLIC)
    private val baul = Collection(id = "c-baul", name = "stored-literal", profile = CollectionProfile.VAULT, isSystem = true)
    private val secrets = Collection(id = "c-secrets", name = "Secrets", profile = CollectionProfile.VAULT)
    private val byId = listOf(work, family, baul, secrets).associateBy { it.id }

    @Test
    fun `bundled audio is tagged Explore`() {
        val bundled = testSound(name = "ambulancia", file = null)

        val labels = labelsFor(bundled, index = emptyMap(), vaultOpen = false)

        assertThat(labels).containsExactly(explore)
    }

    @Test
    fun `bundled audio is tagged Explore even if it appears in the audio-collections index`() {
        // Bundled audios cannot be added to collections, but the Explore branch must win regardless
        // so a stale index entry never makes a bundled result show a collection tag instead.
        val bundled = testSound(name = "ambulancia", file = null)

        val labels = labelsFor(bundled, index = mapOf(bundled.id to listOf("c-work")), vaultOpen = false)

        assertThat(labels).containsExactly(explore)
    }

    @Test
    fun `custom audio in a public collection is tagged with the collection name`() {
        val custom = testSound(name = "che", file = "/audio/che.mp3")

        val labels = labelsFor(custom, index = mapOf(custom.id to listOf("c-work")), vaultOpen = false)

        assertThat(labels).containsExactly("Work")
    }

    @Test
    fun `system Vault collection shows its localized name when the Vault is unlocked`() {
        val custom = testSound(name = "secreto", file = "/audio/secreto.mp3")

        val labels = labelsFor(custom, index = mapOf(custom.id to listOf("c-baul")), vaultOpen = true)

        assertThat(labels).containsExactly(vault)
    }

    @Test
    fun `private collection tag is hidden while the Vault is locked`() {
        // The private collection's name is content behind the Vault gate — a locked session must
        // not surface it. (Private-only audios are filtered out of search upstream anyway; this is
        // the resolver's own guard.)
        val custom = testSound(name = "secreto", file = "/audio/secreto.mp3")

        val labels = labelsFor(custom, index = mapOf(custom.id to listOf("c-secrets")), vaultOpen = false)

        assertThat(labels).isEmpty()
    }

    @Test
    fun `custom audio in several public collections keeps the index order`() {
        val custom = testSound(name = "multi", file = "/audio/multi.mp3")

        val labels = labelsFor(custom, index = mapOf(custom.id to listOf("c-work", "c-family")), vaultOpen = false)

        assertThat(labels).containsExactly("Work", "Family").inOrder()
    }

    @Test
    fun `cross-tagged audio shows both its public and private tags when the Vault is unlocked`() {
        val custom = testSound(name = "cross", file = "/audio/cross.mp3")

        val labels = labelsFor(custom, index = mapOf(custom.id to listOf("c-work", "c-secrets")), vaultOpen = true)

        assertThat(labels).containsExactly("Work", "Secrets").inOrder()
    }

    @Test
    fun `cross-tagged audio hides its private collection tag while the Vault is locked`() {
        // The leak guard: a public+private audio stays searchable while locked (spec § 3.1), but its
        // tag must show only the public collection — never the private collection's name.
        val custom = testSound(name = "cross", file = "/audio/cross.mp3")

        val labels = labelsFor(custom, index = mapOf(custom.id to listOf("c-work", "c-secrets")), vaultOpen = false)

        assertThat(labels).containsExactly("Work")
    }

    @Test
    fun `custom audio in no collection has no tag`() {
        val custom = testSound(name = "orphan", file = "/audio/orphan.mp3")

        val labels = labelsFor(custom, index = emptyMap(), vaultOpen = false)

        assertThat(labels).isEmpty()
    }

    @Test
    fun `custom audio referencing an unknown collection id drops that label`() {
        // A reverse-index entry can outlive a deleted collection; resolving against an absent id
        // must skip it rather than crash or surface a blank tag.
        val custom = testSound(name = "stale", file = "/audio/stale.mp3")

        val labels = labelsFor(custom, index = mapOf(custom.id to listOf("c-work", "c-deleted")), vaultOpen = false)

        assertThat(labels).containsExactly("Work")
    }

    private fun labelsFor(
        sound: com.github.barriosnahuel.vossosunboton.model.Sound,
        index: Map<String, List<String>>,
        vaultOpen: Boolean,
    ): List<String> =
        searchResultCollectionLabels(
            sound = sound,
            collectionsByAudio = index,
            collectionsById = byId,
            systemCollectionLabel = vault,
            exploreLabel = explore,
            vaultOpen = vaultOpen,
        )
}
