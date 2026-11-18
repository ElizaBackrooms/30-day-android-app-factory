package com.example.dsp

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.coroutines.coroutineContext
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class BfskModulator {

  fun modulateToPcm16(
    frameBytes: ByteArray,
    amplitude: Double = 0.85,
    leadSilenceSamples: Int = 441,
    tailSilenceSamples: Int = 441,
  ): ShortArray {
    val totalBits = frameBytes.size * 8
    val signalSamples = totalBits * PacketProtocol.SAMPLES_PER_BIT
    val totalSamples = leadSilenceSamples + signalSamples + tailSilenceSamples
    val pcm = ShortArray(totalSamples)

    var outIdx = leadSilenceSamples
    var phase = 0.0
    val twoPi = 2.0 * Math.PI
    val twoPiOverSr = twoPi / PacketProtocol.SAMPLE_RATE
    val maxAmp = 32767.0 * amplitude.coerceIn(0.05, 1.0)

    val rampSamples = 32
    var sampleCounter = 0

    for (byteVal in frameBytes) {
      val unsigned = byteVal.toInt() and 0xFF
      for (bitIdx in 7 downTo 0) {
        val bit = (unsigned ushr bitIdx) and 1
        val freq = if (bit == 1) PacketProtocol.FREQ_MARK else PacketProtocol.FREQ_SPACE
        val phaseInc = twoPiOverSr * freq

        for (s in 0 until PacketProtocol.SAMPLES_PER_BIT) {
          val env =
            when {
              sampleCounter < rampSamples -> sampleCounter.toDouble() / rampSamples
              sampleCounter >= signalSamples - rampSamples ->
                (signalSamples - 1 - sampleCounter).coerceAtLeast(0).toDouble() / rampSamples
              else -> 1.0
            }
          val sample = (sin(phase) * maxAmp * env).toInt().coerceIn(-32767, 32767).toShort()
          pcm[outIdx++] = sample

          phase += phaseInc
          if (phase >= twoPi) {
            phase -= twoPi
          }
          sampleCounter++
        }
      }
    }
    return pcm
  }

  suspend fun playPcmStatic(
    pcm: ShortArray,
    onProgress: (Float) -> Unit = {},
  ) =
    withContext(Dispatchers.IO) {
      if (pcm.isEmpty()) return@withContext

      val bufferBytes = pcm.size * 2
      val track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_MEDIA)
              .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(PacketProtocol.SAMPLE_RATE)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(bufferBytes)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()

      try {
        val written = track.write(pcm, 0, pcm.size)
        if (written <= 0) {
          throw IllegalStateException("AudioTrack.write failed with code $written")
        }
        track.play()

        val expectedDurationMs = (pcm.size * 1000L) / PacketProtocol.SAMPLE_RATE
        val startMs = System.currentTimeMillis()
        while (coroutineContext.isActive) {
          val headPos =
            try {
              track.playbackHeadPosition
            } catch (_: IllegalStateException) {
              pcm.size
            }
          val elapsedMs = System.currentTimeMillis() - startMs
          val progress =
            if (pcm.isNotEmpty()) {
              (headPos.toFloat() / pcm.size.toFloat()).coerceIn(0f, 1f)
            } else {
              1f
            }
          onProgress(progress)

          if (headPos >= pcm.size || elapsedMs >= expectedDurationMs + 350L) {
            onProgress(1f)
            break
          }
          delay(25L)
        }
      } finally {
        try {
          if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            track.pause()
            track.flush()
          }
          track.stop()
        } catch (_: IllegalStateException) {
          // Ignore if already stopped
        }
        track.release()
      }
    }
}
