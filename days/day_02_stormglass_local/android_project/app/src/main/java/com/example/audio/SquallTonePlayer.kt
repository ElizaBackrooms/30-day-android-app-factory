package com.example.audio

import android.media.AudioManager
import android.media.ToneGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays audible squall warning tones offline using Android's ToneGenerator.
 */
object SquallTonePlayer {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  fun playSquallAlertTone() {
    scope.launch {
      var generator: ToneGenerator? = null
      try {
        generator = ToneGenerator(AudioManager.STREAM_ALARM, 80)
        generator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 400)
        delay(500L)
      } catch (_: Throwable) {
        // Safely ignore on headless or audio-restricted devices
      } finally {
        try {
          generator?.release()
        } catch (_: Throwable) {
        }
      }
    }
  }
}
