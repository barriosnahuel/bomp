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
    private val byId = listOf(work, family, baul).associateBy { it.id }

    @Test
    fun `bundled audio is tagged Explore`() {
        val bundled = testSound(name = "ambulancia", file = null)

        val labels =
            searchResultCollectionLabels(
                bundled,
                collectionsByAudio = emptyMap(),
                collectionsById = byId,
                systemCollectionLabel = vault,
                exploreLabel = explore,
            )

        assertThat(labels).containsExactly(explore)
    }

    @Test
    fun `bundled audio is tagged Explore even if it appears in the audio-collections index`() {
        // Bundled audios cannot be added to collections, but the Explore branch must win regardless
        // so a stale index entry never makes a bundled result show a collection tag instead.
        val bundled = testSound(name = "ambulancia", file = null)
        val index = mapOf(bundled.id to listOf("c-work"))

        val labels =
            searchResultCollectionLabels(
                bundled,
                collectionsByAudio = index,
                collectionsById = byId,
                systemCollectionLabel = vault,
                exploreLabel = explore,
            )

        assertThat(labels).containsExactly(explore)
    }

    @Test
    fun `custom audio in a public collection is tagged with the collection name`() {
        val custom = testSound(name = "che", file = "/audio/che.mp3")
        val index = mapOf(custom.id to listOf("c-work"))

        val labels =
            searchResultCollectionLabels(
                custom,
                collectionsByAudio = index,
                collectionsById = byId,
                systemCollectionLabel = vault,
                exploreLabel = explore,
            )

        assertThat(labels).containsExactly("Work")
    }

    @Test
    fun `custom audio in the system Vault collection is tagged with the localized name, not the stored literal`() {
        val custom = testSound(name = "secreto", file = "/audio/secreto.mp3")
        val index = mapOf(custom.id to listOf("c-baul"))

        val labels =
            searchResultCollectionLabels(
                custom,
                collectionsByAudio = index,
                collectionsById = byId,
                systemCollectionLabel = vault,
                exploreLabel = explore,
            )

        assertThat(labels).containsExactly(vault)
    }

    @Test
    fun `custom audio in several collections keeps the index order`() {
        val custom = testSound(name = "multi", file = "/audio/multi.mp3")
        val index = mapOf(custom.id to listOf("c-work", "c-family"))

        val labels =
            searchResultCollectionLabels(
                custom,
                collectionsByAudio = index,
                collectionsById = byId,
                systemCollectionLabel = vault,
                exploreLabel = explore,
            )

        assertThat(labels).containsExactly("Work", "Family").inOrder()
    }

    @Test
    fun `cross-tagged audio shows both its public collection and the Vault, mirroring My Sounds`() {
        // A public+private audio stays searchable even while the Vault is locked (spec § 3.1), so
        // its tag carries both memberships exactly as My Sounds renders them — the Vault substitutes
        // its localized name. The lock-state filtering of private-only audios happens upstream in the
        // ViewModel, never in this pure resolver, so the same labels apply regardless of lock state.
        val custom = testSound(name = "cross", file = "/audio/cross.mp3")
        val index = mapOf(custom.id to listOf("c-work", "c-baul"))

        val labels =
            searchResultCollectionLabels(
                custom,
                collectionsByAudio = index,
                collectionsById = byId,
                systemCollectionLabel = vault,
                exploreLabel = explore,
            )

        assertThat(labels).containsExactly("Work", vault).inOrder()
    }

    @Test
    fun `custom audio in no collection has no tag`() {
        val custom = testSound(name = "orphan", file = "/audio/orphan.mp3")

        val labels =
            searchResultCollectionLabels(
                custom,
                collectionsByAudio = emptyMap(),
                collectionsById = byId,
                systemCollectionLabel = vault,
                exploreLabel = explore,
            )

        assertThat(labels).isEmpty()
    }

    @Test
    fun `custom audio referencing an unknown collection id drops that label`() {
        // A reverse-index entry can outlive a deleted collection; resolving against an absent id
        // must skip it rather than crash or surface a blank tag.
        val custom = testSound(name = "stale", file = "/audio/stale.mp3")
        val index = mapOf(custom.id to listOf("c-work", "c-deleted"))

        val labels =
            searchResultCollectionLabels(
                custom,
                collectionsByAudio = index,
                collectionsById = byId,
                systemCollectionLabel = vault,
                exploreLabel = explore,
            )

        assertThat(labels).containsExactly("Work")
    }
}
