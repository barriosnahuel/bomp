/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.share

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure unit tests for [resolveAudioMimeType]. No Robolectric: the resolution is plain string logic, and
 * the SDK-dependent gap it guards (`ContentResolver.getType` returning null/octet-stream for `.opus` on
 * older API levels) is exercised here by feeding those exact inputs — deterministic at any SDK.
 */
internal class ShareMimeTypeTest {
    @Test
    fun `resolveAudioMimeType keeps the resolver type when it is a concrete audio type`() {
        assertThat(resolveAudioMimeType("audio/mpeg", "any.mp3")).isEqualTo("audio/mpeg")
    }

    @Test
    fun `resolveAudioMimeType falls back to the extension when the resolver returns null`() {
        assertThat(resolveAudioMimeType(null, "voice-note.opus")).isEqualTo("audio/opus")
    }

    @Test
    fun `resolveAudioMimeType ignores a non-audio resolver type and uses the extension`() {
        assertThat(resolveAudioMimeType("application/octet-stream", "voice-note.opus")).isEqualTo("audio/opus")
    }

    @Test
    fun `resolveAudioMimeType maps every supported audio extension`() {
        assertThat(resolveAudioMimeType(null, "a.mp3")).isEqualTo("audio/mpeg")
        assertThat(resolveAudioMimeType(null, "a.ogg")).isEqualTo("audio/ogg")
        assertThat(resolveAudioMimeType(null, "a.m4a")).isEqualTo("audio/mp4")
        assertThat(resolveAudioMimeType(null, "a.aac")).isEqualTo("audio/aac")
        assertThat(resolveAudioMimeType(null, "a.wav")).isEqualTo("audio/wav")
    }

    @Test
    fun `resolveAudioMimeType is case-insensitive on the extension`() {
        assertThat(resolveAudioMimeType(null, "RECORDING.MP3")).isEqualTo("audio/mpeg")
    }

    @Test
    fun `resolveAudioMimeType returns the wildcard for an unknown extension`() {
        assertThat(resolveAudioMimeType(null, "mystery.xyz")).isEqualTo("audio/*")
    }

    @Test
    fun `resolveAudioMimeType returns the wildcard when the name has no extension`() {
        assertThat(resolveAudioMimeType(null, "noextension")).isEqualTo("audio/*")
    }

    @Test
    fun `resolveAudioMimeType returns the wildcard when both inputs are null`() {
        assertThat(resolveAudioMimeType(null, null)).isEqualTo("audio/*")
    }
}
