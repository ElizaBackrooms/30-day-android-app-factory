package com.example.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * Pure AOSP in-memory microphone loudness (dB) meter using AudioRecord.
 * Never writes, caches, or transmits audio buffers — only emits scalar dB levels.
 */
class MicDecibelEngine(private val context: Context) {

    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun observeDecibels(): Flow<AudioLevelReading> = flow {
        if (!hasRecordAudioPermission()) {
            return@flow
        }

        val sampleRate = 16_000
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        val minBufBytes = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (minBufBytes <= 0) {
            return@flow
        }

        val bufferSizeBytes = maxOf(minBufBytes, 2048)
        val pcmBuffer = ShortArray(bufferSizeBytes / 2)

        var recorder: AudioRecord? = null
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSizeBytes
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                return@flow
            }
            recorder.startRecording()

            while (currentCoroutineContext().isActive) {
                val readCount = recorder.read(pcmBuffer, 0, pcmBuffer.size)
                if (readCount > 0) {
                    val reading = SeismographMath.computeDecibelsFromPcm(pcmBuffer, readCount)
                    emit(reading)
                }
                delay(75L)
            }
        } catch (_: SecurityException) {
            // Permission revoked mid-stream; terminate cleanly
        } catch (_: IllegalStateException) {
            // Audio hardware unavailable or busy; terminate cleanly
        } finally {
            // Zero out transient PCM samples in memory before release
            pcmBuffer.fill(0)
            try {
                recorder?.stop()
            } catch (_: IllegalStateException) {
                // Ignore if not started
            }
            recorder?.release()
        }
    }.flowOn(Dispatchers.IO)
}
