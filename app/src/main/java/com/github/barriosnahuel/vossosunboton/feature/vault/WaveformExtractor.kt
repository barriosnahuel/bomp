/*
 * Copyright (c) 2016-2026 Nahuel Barrios. All rights reserved.
 * SPDX-License-Identifier: AGPL-3.0-only
 * See LICENSE in the project root for full license information.
 */
package com.github.barriosnahuel.vossosunboton.feature.vault

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.github.barriosnahuel.vossosunboton.commons.android.error.Tracker
import com.github.barriosnahuel.vossosunboton.commons.file.getFile
import com.github.barriosnahuel.vossosunboton.model.Sound
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Real amplitude-envelope waveform for a [Sound] — decodes the audio to PCM and folds it into
 * [barCount] normalized peaks (0..1). This is genuine audio data, not a synthetic shape; the
 * mock's waveform is the only synthetic part we deliberately do NOT reproduce.
 *
 * A live [android.media.audiofx.Visualizer] would give a reactive waveform but requires
 * `RECORD_AUDIO` — a non-starter for a privacy-first Vault — so we extract a static envelope of the
 * whole clip instead (which is also what the design shows: a fixed shape with a progress fill).
 *
 * Extraction is off the main thread, cached per [Sound.id] for the process lifetime (each result is
 * a tiny `FloatArray`), and returns `null` on any failure so the UI falls back to a neutral
 * placeholder rather than a fake-looking wave.
 */
internal object WaveformExtractor {
    private val cache = ConcurrentHashMap<String, FloatArray>()

    suspend fun extract(
        context: Context,
        sound: Sound,
        barCount: Int,
        dispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): FloatArray? {
        cache[sound.id]?.let { return it }
        val peaks =
            withContext(dispatcher) {
                runCatching { decodeEnvelope(context, sound, barCount) }
                    .onFailure { error ->
                        if (error is CancellationException) throw error
                        Tracker.log("immersive.waveform_fail=${error.javaClass.simpleName}")
                        Tracker.track(RuntimeException("Vault waveform extraction failed", error))
                    }.getOrNull()
            }
        return peaks?.also { cache[sound.id] = it }
    }

    /** Visible for the cold-start / no-data fallback path and tests. */
    fun cached(soundId: String): FloatArray? = cache[soundId]

    private suspend fun decodeEnvelope(
        context: Context,
        sound: Sound,
        barCount: Int,
    ): FloatArray {
        val extractor = MediaExtractor()
        try {
            if (sound.file != null) {
                extractor.setDataSource(getFile(context, sound.file!!).path)
            } else {
                context.resources.openRawResourceFd(sound.rawRes).use { afd ->
                    extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.declaredLength)
                }
            }
            val trackIndex = audioTrackIndex(extractor)
            require(trackIndex >= 0) { "no audio track" }
            extractor.selectTrack(trackIndex)
            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("no mime")

            val sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inputFormat.runCatching { getInteger(MediaFormat.KEY_CHANNEL_COUNT) }.getOrDefault(1)
            val durationUs = inputFormat.runCatching { getLong(MediaFormat.KEY_DURATION) }.getOrDefault(0L)
            val estimatedSamples = (durationUs / MICROS_PER_SECOND.toDouble() * sampleRate * channels).toLong()
            val accumulator = PeakAccumulator(barCount, estimatedSamples)

            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(inputFormat, null, null, 0)
                codec.start()
                decodeLoop(codec, extractor, accumulator)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
            return accumulator.result()
        } finally {
            extractor.release()
        }
    }

    private suspend fun decodeLoop(
        codec: MediaCodec,
        extractor: MediaExtractor,
        accumulator: PeakAccumulator,
    ) {
        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        while (!outputDone) {
            coroutineContext.ensureActive()
            if (!inputDone) inputDone = feedInput(codec, extractor)
            outputDone = drainOutput(codec, bufferInfo, accumulator)
        }
    }

    /** Queues one input sample (or end-of-stream). Returns true once the stream is exhausted. */
    private fun feedInput(
        codec: MediaCodec,
        extractor: MediaExtractor,
    ): Boolean {
        val inIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (inIndex < 0) return false
        val size = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
        val endOfStream = size < 0
        if (endOfStream) {
            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        } else {
            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
        return endOfStream
    }

    /** Folds one decoded PCM buffer into [accumulator]. Returns true on the end-of-stream buffer. */
    private fun drainOutput(
        codec: MediaCodec,
        bufferInfo: MediaCodec.BufferInfo,
        accumulator: PeakAccumulator,
    ): Boolean {
        val outIndex = codec.dequeueOutputBuffer(bufferInfo, DEQUEUE_TIMEOUT_US)
        if (outIndex < 0) return false
        if (bufferInfo.size > 0) {
            val outBuffer =
                codec.getOutputBuffer(outIndex)!!.apply {
                    position(bufferInfo.offset)
                    limit(bufferInfo.offset + bufferInfo.size)
                }
            val shorts = outBuffer.order(ByteOrder.nativeOrder()).asShortBuffer()
            while (shorts.hasRemaining()) accumulator.add(shorts.get())
        }
        codec.releaseOutputBuffer(outIndex, false)
        return bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
    }

    private fun audioTrackIndex(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith("audio/")) return i
        }
        return -1
    }

    private const val DEQUEUE_TIMEOUT_US = 10_000L
    private const val MICROS_PER_SECOND = 1_000_000L
}

/**
 * Folds an interleaved 16-bit PCM stream into [barCount] peak buckets, then normalizes so the
 * loudest bucket is 1.0. Pure (no Android types) so the bucketing + normalization is unit-tested
 * without decoding real audio. Each sample lands in a bucket by its position in the estimated
 * total; a wrong estimate only distorts the tail slightly (extra samples clamp into the last bucket).
 */
internal class PeakAccumulator(
    private val barCount: Int,
    estimatedTotalSamples: Long,
) {
    init {
        require(barCount > 0) { "barCount must be positive" }
    }

    private val total = max(1L, estimatedTotalSamples)
    private val peaks = FloatArray(barCount)
    private var index = 0L

    fun add(sample: Short) {
        val bucket = min(barCount - 1L, index * barCount / total).toInt()
        val magnitude = abs(sample.toInt()) / SHORT_FULL_SCALE
        if (magnitude > peaks[bucket]) peaks[bucket] = magnitude
        index++
    }

    /** Normalized peaks in [MIN_BAR, 1]. Silent / empty input yields a flat [MIN_BAR] baseline. */
    fun result(): FloatArray {
        val loudest = peaks.maxOrNull() ?: 0f
        if (loudest <= 0f) return FloatArray(barCount) { MIN_BAR }
        return FloatArray(barCount) { i -> max(MIN_BAR, peaks[i] / loudest) }
    }

    private companion object {
        const val SHORT_FULL_SCALE = 32_768f
        const val MIN_BAR = 0.06f
    }
}
