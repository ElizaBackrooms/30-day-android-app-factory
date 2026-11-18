package com.example.dsp

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AudioRxController(
  private val onTelemetry: (DemodTelemetry) -> Unit,
  private val onPacketDecoded: (DecodedPacket) -> Unit,
  private val onCrcFail: (expected: Int, actual: Int) -> Unit,
  private val onError: (String) -> Unit,
) {
  private var rxJob: Job? = null
  private val demodulator =
    BfskDemodulator(
      onPacketDecoded = onPacketDecoded,
      onCrcFail = onCrcFail,
    )

  val isRunning: Boolean
    get() = rxJob?.isActive == true

  @SuppressLint("MissingPermission")
  fun start(scope: CoroutineScope) {
    if (isRunning) return
    demodulator.reset()

    rxJob =
      scope.launch(Dispatchers.Default) {
        val minBufBytes =
          AudioRecord.getMinBufferSize(
            PacketProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
          )
        if (minBufBytes <= 0) {
          onError("AudioRecord unavailable on this hardware (code $minBufBytes)")
          return@launch
        }

        val bufferSizeBytes = (minBufBytes * 2).coerceAtLeast(PacketProtocol.SAMPLE_RATE / 5 * 2)
        val record =
          createAudioRecord(bufferSizeBytes)
            ?: run {
              onError("Failed to initialize AudioRecord at 44100 Hz")
              return@launch
            }

        val readShorts = ShortArray(1024)
        var lastUiEmitMs = 0L

        try {
          record.startRecording()
          if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            onError("Microphone capture could not enter recording state")
            return@launch
          }

          while (isActive) {
            val readCount = record.read(readShorts, 0, readShorts.size)
            if (readCount > 0) {
              demodulator.feedPcmShorts(readShorts, readCount)
              val now = System.currentTimeMillis()
              if (now - lastUiEmitMs >= 50L) { // ~20 FPS throttle for UI
                lastUiEmitMs = now
                onTelemetry(demodulator.snapshotTelemetry())
              }
            } else if (readCount < 0) {
              onError("AudioRecord read error: $readCount")
              break
            }
          }
        } catch (se: SecurityException) {
          onError("RECORD_AUDIO permission denied: ${se.message}")
        } catch (t: Throwable) {
          onError("RX audio stream error: ${t.message ?: "unknown"}")
        } finally {
          try {
            if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
              record.stop()
            }
          } catch (_: IllegalStateException) {}
          record.release()
          demodulator.reset()
          onTelemetry(DemodTelemetry())
        }
      }
  }

  fun stop() {
    rxJob?.cancel()
    rxJob = null
    demodulator.reset()
  }

  @SuppressLint("MissingPermission")
  private fun createAudioRecord(bufferSizeBytes: Int): AudioRecord? {
    val sources =
      listOf(
        MediaRecorder.AudioSource.UNPROCESSED,
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        MediaRecorder.AudioSource.MIC,
      )
    for (source in sources) {
      try {
        val candidate =
          AudioRecord(
            source,
            PacketProtocol.SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSizeBytes,
          )
        if (candidate.state == AudioRecord.STATE_INITIALIZED) {
          return candidate
        }
        candidate.release()
      } catch (_: Throwable) {
        // Fallback to next audio source
      }
    }
    return null
  }
}
