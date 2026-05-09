/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.commons.file

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream

class FileUtilsTest {
    private lateinit var targetFile: File

    @Before
    fun setUp() {
        targetFile = File.createTempFile("file-utils-test", ".tmp")
    }

    @After
    fun tearDown() {
        targetFile.delete()
    }

    @Test
    fun `copy closes both streams on success and writes the input to the file`() {
        val input = TrackingInputStream(ByteArrayInputStream("hello".toByteArray()))
        val output = TrackingFileOutputStream(targetFile)

        copy(input, output)

        assertThat(input.closed).isTrue()
        assertThat(output.closed).isTrue()
        assertThat(targetFile.readText()).isEqualTo("hello")
    }

    @Test
    fun `copy closes both streams when input read throws IOException and rethrows`() {
        val input = TrackingInputStream(ThrowingOnReadInputStream())
        val output = TrackingFileOutputStream(targetFile)

        // The .use { } wrappers in copy() must close both streams before the exception escapes —
        // assertThrows confirms it propagates, and the closed assertions confirm cleanup happened.
        assertThrows(IOException::class.java) { copy(input, output) }

        assertThat(input.closed).isTrue()
        assertThat(output.closed).isTrue()
    }

    private class TrackingInputStream(
        private val delegate: InputStream,
    ) : InputStream() {
        var closed = false
            private set

        override fun read(): Int = delegate.read()

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = delegate.read(b, off, len)

        override fun close() {
            closed = true
            delegate.close()
        }
    }

    private class TrackingFileOutputStream(
        file: File,
    ) : FileOutputStream(file) {
        var closed = false
            private set

        override fun close() {
            closed = true
            super.close()
        }
    }

    private class ThrowingOnReadInputStream : InputStream() {
        override fun read(): Int = throw IOException("simulated read failure")

        override fun read(
            b: ByteArray,
            off: Int,
            len: Int,
        ): Int = throw IOException("simulated read failure")
    }
}
