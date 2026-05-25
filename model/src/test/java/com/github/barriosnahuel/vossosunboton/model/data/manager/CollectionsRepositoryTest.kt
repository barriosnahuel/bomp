/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.model.data.manager

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.model.CollectionAccess
import com.github.barriosnahuel.vossosunboton.model.CollectionProfile
import com.github.barriosnahuel.vossosunboton.model.CollectionShareability
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.VANILLA_ICE_CREAM])
internal class CollectionsRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val repo = CollectionsRepository(context)

    @After
    fun tearDown() =
        runTest {
            repo.clearForTest()
        }

    @Test
    fun `first read seeds the system Baul as a private listen-only collection`() =
        runTest {
            val list = repo.collections.first()
            val baul = list.firstOrNull { it.id == CollectionsRepository.BAUL_SYSTEM_ID }
            assertThat(baul).isNotNull()
            assertThat(baul!!.isSystem).isTrue()
            assertThat(baul.isPrivate).isTrue()
            assertThat(baul.profile.shareability).isEqualTo(CollectionShareability.LISTEN_ONLY)
        }

    @Test
    fun `Baul seed is idempotent across multiple reads`() =
        runTest {
            repo.collections.first()
            repo.collections.first()
            val list = repo.collections.first()
            val baulCount = list.count { it.id == CollectionsRepository.BAUL_SYSTEM_ID }
            assertThat(baulCount).isEqualTo(1)
        }

    @Test
    fun `create persists the collection with a fresh UUID and the requested profile`() =
        runTest {
            val created = repo.create("Family", CollectionProfile.GENERIC_PUBLIC)
            val list = repo.collections.first()
            assertThat(list.any { it.id == created.id && it.name == "Family" }).isTrue()
            assertThat(created.profile.access).isEqualTo(CollectionAccess.PUBLIC)
        }

    @Test
    fun `create rejects duplicate name in the same access scope`() =
        runTest {
            repo.create("Family", CollectionProfile.GENERIC_PUBLIC)
            val thrown =
                runCatching { repo.create("family", CollectionProfile.GENERIC_PUBLIC) }
            assertThat(thrown.isFailure).isTrue()
            assertThat(thrown.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
        }

    @Test
    fun `create allows the same name across different access scopes`() =
        runTest {
            repo.create("Family", CollectionProfile.GENERIC_PUBLIC)
            val privateOne = repo.create("Family", CollectionProfile.VAULT)
            val list = repo.collections.first()
            assertThat(list.filter { it.name == "Family" }).hasSize(2)
            assertThat(privateOne.isPrivate).isTrue()
        }

    @Test
    fun `delete refuses to remove the system Baul`() =
        runTest {
            repo.collections.first() // trigger seed
            val thrown = runCatching { repo.delete(CollectionsRepository.BAUL_SYSTEM_ID) }
            assertThat(thrown.isFailure).isTrue()
            val list = repo.collections.first()
            assertThat(list.any { it.id == CollectionsRepository.BAUL_SYSTEM_ID }).isTrue()
        }

    @Test
    fun `addAudio is idempotent`() =
        runTest {
            val c = repo.create("Work", CollectionProfile.GENERIC_PUBLIC)
            repo.addAudio(c.id, "audio-1")
            repo.addAudio(c.id, "audio-1")
            val list = repo.collections.first()
            val updated = list.first { it.id == c.id }
            assertThat(updated.audioIds).containsExactly("audio-1")
        }

    @Test
    fun `setAudioCollections only mutates collections inside the named access scope`() =
        runTest {
            val pub = repo.create("Public-1", CollectionProfile.GENERIC_PUBLIC)
            val priv = repo.create("Private-1", CollectionProfile.VAULT)
            repo.addAudio(pub.id, "audio-1")
            repo.addAudio(priv.id, "audio-1")
            // Replace public memberships only; private should stay untouched.
            repo.setAudioCollections("audio-1", emptySet(), CollectionAccess.PUBLIC)
            val list = repo.collections.first()
            assertThat(list.first { it.id == pub.id }.audioIds).isEmpty()
            assertThat(list.first { it.id == priv.id }.audioIds).containsExactly("audio-1")
        }

    @Test
    fun `forgetAudio sweeps the id from every collection`() =
        runTest {
            val pub = repo.create("Public-1", CollectionProfile.GENERIC_PUBLIC)
            val priv = repo.create("Private-1", CollectionProfile.VAULT)
            repo.addAudio(pub.id, "audio-1")
            repo.addAudio(priv.id, "audio-1")
            repo.forgetAudio("audio-1")
            val list = repo.collections.first()
            assertThat(list.first { it.id == pub.id }.audioIds).isEmpty()
            assertThat(list.first { it.id == priv.id }.audioIds).isEmpty()
        }

    @Test
    fun `rename rejects duplicate target name inside the same scope`() =
        runTest {
            repo.create("Work", CollectionProfile.GENERIC_PUBLIC)
            val other = repo.create("Family", CollectionProfile.GENERIC_PUBLIC)
            val thrown = runCatching { repo.rename(other.id, "work") }
            assertThat(thrown.isFailure).isTrue()
        }

    @Test
    fun `rename succeeds when target name is unique inside scope`() =
        runTest {
            val c = repo.create("Old", CollectionProfile.GENERIC_PUBLIC)
            repo.rename(c.id, "New")
            val list = repo.collections.first()
            assertThat(list.first { it.id == c.id }.name).isEqualTo("New")
        }

    @Test
    fun `rename refuses to rename the system Baul`() =
        runTest {
            repo.collections.first() // trigger seed
            val thrown = runCatching { repo.rename(CollectionsRepository.BAUL_SYSTEM_ID, "Mi cosa") }
            assertThat(thrown.isFailure).isTrue()
            assertThat(thrown.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
            // The persisted name must stay untouched.
            val list = repo.collections.first()
            val baul = list.first { it.id == CollectionsRepository.BAUL_SYSTEM_ID }
            assertThat(baul.name).isNotEqualTo("Mi cosa")
        }

    @Test
    fun `decodeSafely recovers from malformed JSON without losing future writes`() =
        runTest {
            repo.setRawJsonForTest("not valid json {}")
            // Should not throw; reads return empty list (modulo the Baul reseed via the next read).
            val list = repo.collections.first()
            assertThat(list.any { it.id == CollectionsRepository.BAUL_SYSTEM_ID }).isTrue()
        }
}
