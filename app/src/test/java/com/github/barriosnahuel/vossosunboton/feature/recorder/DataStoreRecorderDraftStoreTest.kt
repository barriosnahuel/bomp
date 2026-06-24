/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

internal class DataStoreRecorderDraftStoreTest : AbstractRobolectricTest() {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private lateinit var writebackScope: CoroutineScope
    private lateinit var store: DataStoreRecorderDraftStore

    @Before
    fun setUp() {
        writebackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = DataStoreRecorderDraftStore(app, writebackScope)
        runBlocking {
            store.clearForTest()
            RecorderTempFiles.purge(app)
        }
    }

    @After
    fun tearDown() {
        runBlocking { store.clearForTest() }
        writebackScope.cancel()
    }

    @Test
    fun `save then current returns the clip while its file exists`() =
        runBlocking {
            val file = RecorderTempFiles.newTempFile(app).apply { createNewFile() }

            store.save(file, durationMs = 4_000)
            drainWritebacks()

            val draft = store.current()!!
            assertThat(draft.file.name).isEqualTo(file.name)
            assertThat(draft.durationMs).isEqualTo(4_000)
        }

    @Test
    fun `current returns null and self-heals once the file is gone`() =
        runBlocking {
            val file = RecorderTempFiles.newTempFile(app).apply { createNewFile() }
            store.save(file, durationMs = 4_000)
            drainWritebacks()

            file.delete()

            assertThat(store.current()).isNull()
        }

    @Test
    fun `clear forgets the draft`() =
        runBlocking {
            val file = RecorderTempFiles.newTempFile(app).apply { createNewFile() }
            store.save(file, durationMs = 4_000)
            drainWritebacks()

            store.clear()
            drainWritebacks()

            assertThat(store.current()).isNull()
        }

    /** Joins every fire-and-forget `scope.launch { store.edit { ... } }` so disk reads run post-commit. */
    private suspend fun drainWritebacks() {
        writebackScope.coroutineContext.job.children
            .toList()
            .joinAll()
    }
}
