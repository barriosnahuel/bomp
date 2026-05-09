/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.addbutton

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.github.barriosnahuel.vossosunboton.AbstractRobolectricTest
import com.github.barriosnahuel.vossosunboton.R
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

internal class AddButtonFeatureTest : AbstractRobolectricTest() {
    private val realContext: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `saveNewButtonAsync saves a valid audio URI and returns saved_ok`() {
        val uri = Uri.parse("content://test/valid-audio")
        val context = contextWith(uri, mime = "audio/mpeg", sizeBytes = 1024L)

        val result =
            runBlocking {
                AddButtonFeature.instance.saveNewButtonAsync(context, "valid", uri.toString()).await()
            }

        assertThat(result).isEqualTo(R.string.app_addbutton_feedback_saved_ok)
    }

    @Test
    fun `saveNewButtonAsync rejects URIs with non-audio MIME types`() {
        val uri = Uri.parse("content://test/zip-file")
        val context = contextWith(uri, mime = "application/zip", sizeBytes = 1024L)

        val result =
            runBlocking {
                AddButtonFeature.instance.saveNewButtonAsync(context, "zip", uri.toString()).await()
            }

        assertThat(result).isEqualTo(R.string.app_feedback_generic_error_contact_support)
    }

    @Test
    fun `saveNewButtonAsync rejects URIs that exceed the maximum allowed size`() {
        val uri = Uri.parse("content://test/huge-audio")
        val tooLarge = 50L * 1024 * 1024 + 1
        val context = contextWith(uri, mime = "audio/mpeg", sizeBytes = tooLarge)

        val result =
            runBlocking {
                AddButtonFeature.instance.saveNewButtonAsync(context, "huge", uri.toString()).await()
            }

        assertThat(result).isEqualTo(R.string.app_feedback_generic_error_contact_support)
    }

    @Test
    fun `saveNewButtonAsync rejects URIs with unsupported schemes`() {
        val uri = Uri.parse("http://example.com/foo.mp3")

        val result =
            runBlocking {
                AddButtonFeature.instance.saveNewButtonAsync(realContext, "http", uri.toString()).await()
            }

        assertThat(result).isEqualTo(R.string.app_feedback_generic_error_contact_support)
    }

    @Test
    fun `saveNewButtonAsync does not call ContentResolver getType when scheme is rejected`() {
        // Locks in the scheme-first ordering: a future refactor that re-runs getType before checking
        // the scheme would mis-route the failure into the Unreadable bucket and waste a resolver call.
        val uri = Uri.parse("http://example.com/foo.mp3")
        val resolver = spyk(realContext.contentResolver)
        val context = spyk(realContext)
        every { context.contentResolver } returns resolver

        runBlocking {
            AddButtonFeature.instance.saveNewButtonAsync(context, "http", uri.toString()).await()
        }

        verify(exactly = 0) { resolver.getType(uri) }
    }

    @Test
    fun `saveNewButtonAsync rejects URIs whose MIME type is unknown`() {
        val uri = Uri.parse("content://test/no-mime")
        val context = contextWith(uri, mime = null, sizeBytes = 1024L)

        val result =
            runBlocking {
                AddButtonFeature.instance.saveNewButtonAsync(context, "nomime", uri.toString()).await()
            }

        assertThat(result).isEqualTo(R.string.app_feedback_generic_error_contact_support)
    }

    @Test
    fun `saveNewButtonAsync returns uri-unreadable feedback when ContentResolver getType throws SecurityException`() {
        val uri = Uri.parse("content://test/revoked-grant")
        val baseResolver = realContext.contentResolver
        shadowOf(baseResolver).registerInputStream(uri, ByteArrayInputStream(ByteArray(0)))
        val resolver = spyk(baseResolver)
        every { resolver.getType(uri) } throws SecurityException("URI permission revoked")
        val context = spyk(realContext)
        every { context.contentResolver } returns resolver

        mockkObject(Tracker)
        every { Tracker.track(any()) } answers { nothing }

        val result =
            runBlocking {
                AddButtonFeature.instance.saveNewButtonAsync(context, "revoked", uri.toString()).await()
            }

        assertThat(result).isEqualTo(R.string.app_addbutton_feedback_uri_unreadable)
        verify(exactly = 1) { Tracker.track(any()) }
    }

    @Test
    fun `saveNewButtonAsync tracks non-fatal when ContentResolver returns null inputStream`() {
        val uri = Uri.parse("content://test/null-stream")
        val baseResolver = realContext.contentResolver
        val resolver = spyk(baseResolver)
        every { resolver.getType(uri) } returns "audio/mpeg"
        val afd = mockk<AssetFileDescriptor>(relaxed = true)
        every { afd.length } returns 1024L
        every { resolver.openAssetFileDescriptor(uri, "r") } returns afd
        every { resolver.openInputStream(uri) } returns null
        val context = spyk(realContext)
        every { context.contentResolver } returns resolver

        mockkObject(Tracker)
        every { Tracker.track(any()) } answers { nothing }

        val result =
            runBlocking {
                AddButtonFeature.instance.saveNewButtonAsync(context, "nullstream", uri.toString()).await()
            }

        assertThat(result).isEqualTo(R.string.app_addbutton_feedback_uri_unreadable)
        verify(exactly = 1) { Tracker.track(any()) }
    }

    @Test
    fun `saveNewButtonAsync tracks non-fatal when copy throws IOException`() {
        val uri = Uri.parse("content://test/throwing-stream")
        val baseResolver = realContext.contentResolver
        val throwingStream =
            object : InputStream() {
                override fun read(): Int = throw IOException("simulated copy failure")

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int = throw IOException("simulated copy failure")
            }
        shadowOf(baseResolver).registerInputStream(uri, throwingStream)
        val resolver = spyk(baseResolver)
        every { resolver.getType(uri) } returns "audio/mpeg"
        val afd = mockk<AssetFileDescriptor>(relaxed = true)
        every { afd.length } returns 1024L
        every { resolver.openAssetFileDescriptor(uri, "r") } returns afd
        val context = spyk(realContext)
        every { context.contentResolver } returns resolver

        mockkObject(Tracker)
        every { Tracker.track(any()) } answers { nothing }

        val result =
            runBlocking {
                AddButtonFeature.instance.saveNewButtonAsync(context, "ioerror", uri.toString()).await()
            }

        assertThat(result).isEqualTo(R.string.app_addbutton_feedback_save_failed)
        verify(exactly = 1) { Tracker.track(any()) }
    }

    @Test
    fun `saveNewButtonAsync tracks non-fatal when MediaMetadataRetriever throws but still saves`() {
        val uri = Uri.parse("content://test/retriever-fail")
        val context = contextWith(uri, mime = "audio/mpeg", sizeBytes = 1024L)

        mockkObject(Tracker)
        every { Tracker.track(any()) } answers { nothing }
        mockkConstructor(MediaMetadataRetriever::class)
        every { anyConstructed<MediaMetadataRetriever>().setDataSource(any<String>()) } throws
            RuntimeException("Retriever blew up")
        every { anyConstructed<MediaMetadataRetriever>().release() } answers { nothing }

        val result =
            runBlocking {
                AddButtonFeature.instance.saveNewButtonAsync(context, "retriever", uri.toString()).await()
            }

        assertThat(result).isEqualTo(R.string.app_addbutton_feedback_saved_ok)
        verify(exactly = 1) { Tracker.track(any()) }
    }

    /**
     * Wraps the Robolectric application in a spy so we can stub the [ContentResolver] surfaces
     * the validator depends on (`getType`, `openAssetFileDescriptor`) without touching the real
     * MediaStore. The shadow resolver still serves `openInputStream` for the happy-path copy.
     */
    private fun contextWith(
        uri: Uri,
        mime: String?,
        sizeBytes: Long,
    ): Context {
        val baseResolver = realContext.contentResolver
        shadowOf(baseResolver).registerInputStream(uri, ByteArrayInputStream(ByteArray(0)))
        val resolver = spyk(baseResolver)
        every { resolver.getType(uri) } returns mime
        val afd = mockk<AssetFileDescriptor>(relaxed = true)
        every { afd.length } returns sizeBytes
        every { resolver.openAssetFileDescriptor(uri, "r") } returns afd

        val context = spyk(realContext)
        every { context.contentResolver } returns resolver
        return context
    }
}
