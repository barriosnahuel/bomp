/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.recorder

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import androidx.annotation.VisibleForTesting
import java.io.File

/**
 * Captures microphone audio to a single AAC/MP4 (`.m4a`) file — the in-app recorder's only platform
 * surface (ADR 0019). Synchronous and blocking on purpose: [RecorderViewModel] owns the dispatcher and
 * calls [start]/[stop]/[release] off the main thread; [maxAmplitude] is an in-memory read safe on main.
 *
 * Lifecycle contract (the ADR's main care point): the caller MUST [release] in `onCleared` *and* the
 * Activity's `onStop` — an unreleased recorder can hold the mic globally until reboot on some OEMs.
 * [release] is idempotent. Audio focus (`AUDIOFOCUS_GAIN_TRANSIENT`) is requested on [start] and
 * abandoned on [release]; losing it (an incoming call, another app grabbing the mic) fires
 * [onInterrupted] so the VM can auto-stop and preserve what was captured.
 */
interface RecorderEngine {
    /** Auto-stop fired by the platform when [MAX_DURATION_MS] is reached. Set by the VM. */
    var onMaxDurationReached: (() -> Unit)?

    /** A fatal recorder error or an audio-focus loss. Set by the VM to auto-stop + preserve. */
    var onInterrupted: (() -> Unit)?

    /** Starts capturing into [outputFile]. Throws if the mic is unavailable (in use by another app). */
    fun start(outputFile: File)

    /** Stops capturing and releases the recorder. Returns false if no valid clip was produced. */
    fun stop(): Boolean

    /** Peak amplitude since the previous call, normalized to `0f..1f`; `0f` when not recording. */
    fun maxAmplitude(): Float

    /** Releases the recorder and abandons audio focus. Idempotent — safe to call repeatedly. */
    fun release()

    companion object {
        const val MAX_DURATION_MS = 60_000
        private const val SAMPLE_RATE_HZ = 44_100
        private const val BIT_RATE_BPS = 64_000
        private const val MAX_AMPLITUDE = 32_767f

        fun create(context: Context): RecorderEngine = MediaRecorderEngine(context.applicationContext)

        @VisibleForTesting
        internal val SAMPLE_RATE: Int = SAMPLE_RATE_HZ

        @VisibleForTesting
        internal val BIT_RATE: Int = BIT_RATE_BPS

        internal const val NORMALIZER: Float = MAX_AMPLITUDE
    }
}

private class MediaRecorderEngine(
    private val context: Context,
) : RecorderEngine {
    override var onMaxDurationReached: (() -> Unit)? = null
    override var onInterrupted: (() -> Unit)? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var recorder: MediaRecorder? = null
    private var focusRequest: AudioFocusRequest? = null
    private var recording = false

    private val focusListener =
        AudioManager.OnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                onInterrupted?.invoke()
            }
        }

    override fun start(outputFile: File) {
        requestFocus()
        val mediaRecorder = newRecorder()
        recorder = mediaRecorder
        mediaRecorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(RecorderEngine.SAMPLE_RATE)
            setAudioEncodingBitRate(RecorderEngine.BIT_RATE)
            setMaxDuration(RecorderEngine.MAX_DURATION_MS)
            setOutputFile(outputFile.absolutePath)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxDurationReached?.invoke()
            }
            setOnErrorListener { _, _, _ -> onInterrupted?.invoke() }
            prepare()
            start()
        }
        recording = true
    }

    override fun stop(): Boolean {
        val mediaRecorder = recorder ?: return false
        val produced =
            try {
                mediaRecorder.stop()
                true
            } catch (ignored: RuntimeException) {
                // MediaRecorder.stop() throws if stopped before any frame was written (a sub-second
                // tap); the partial file is unusable. Treat as "no valid clip".
                false
            }
        release()
        return produced
    }

    override fun maxAmplitude(): Float =
        if (recording) {
            (recorder?.maxAmplitude ?: 0) / RecorderEngine.NORMALIZER
        } else {
            0f
        }

    override fun release() {
        recording = false
        recorder?.let { mediaRecorder ->
            runCatching {
                mediaRecorder.reset()
                mediaRecorder.release()
            }
        }
        recorder = null
        abandonFocus()
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

    private fun requestFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request =
                AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    ).setOnAudioFocusChangeListener(focusListener)
                    .build()
            focusRequest = request
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            )
        }
    }

    private fun abandonFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            focusRequest = null
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(focusListener)
        }
    }
}
