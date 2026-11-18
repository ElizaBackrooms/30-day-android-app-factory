package com.example.hardware

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

object MorseAlphabet {
  val CODE_MAP: Map<Char, String> =
    mapOf(
      'A' to ".-",
      'B' to "-...",
      'C' to "-.-.",
      'D' to "-..",
      'E' to ".",
      'F' to "..-.",
      'G' to "--.",
      'H' to "....",
      'I' to "..",
      'J' to ".---",
      'K' to "-.-",
      'L' to ".-..",
      'M' to "--",
      'N' to "-.",
      'O' to "---",
      'P' to ".--.",
      'Q' to "--.-",
      'R' to ".-.",
      'S' to "...",
      'T' to "-",
      'U' to "..-",
      'V' to "...-",
      'W' to ".--",
      'X' to "-..-",
      'Y' to "-.--",
      'Z' to "--..",
      '0' to "-----",
      '1' to ".----",
      '2' to "..---",
      '3' to "...--",
      '4' to "....-",
      '5' to ".....",
      '6' to "-....",
      '7' to "--...",
      '8' to "---..",
      '9' to "----.",
      '.' to ".-.-.-",
      ',' to "--..--",
      '?' to "..--..",
      '/' to "-..-.",
      '-' to "-....-",
      '@' to ".--.-.",
    )

  fun toMorsePreview(text: String): String {
    val words = text.uppercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return ""
    return words.joinToString("  /  ") { word ->
      word.mapNotNull { CODE_MAP[it] }.joinToString(" ")
    }
  }
}

class TorchMorseController(context: Context) {
  private val cameraManager: CameraManager? =
    try {
      context.applicationContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    } catch (_: Throwable) {
      null
    }

  private val flashCameraId: String? = resolveFlashCameraId()

  val hasFlashHardware: Boolean
    get() = flashCameraId != null

  private fun resolveFlashCameraId(): String? {
    val manager = cameraManager ?: return null
    return try {
      var fallbackId: String? = null
      for (id in manager.cameraIdList) {
        val chars = manager.getCameraCharacteristics(id)
        val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        if (hasFlash) {
          val facing = chars.get(CameraCharacteristics.LENS_FACING)
          if (facing == CameraCharacteristics.LENS_FACING_BACK) {
            return id
          }
          if (fallbackId == null) fallbackId = id
        }
      }
      fallbackId
    } catch (t: Throwable) {
      Log.w(TAG, "Unable to inspect camera flash characteristics", t)
      null
    }
  }

  fun setTorch(enabled: Boolean, onStrobeChanged: (Boolean) -> Unit = {}) {
    onStrobeChanged(enabled)
    val id = flashCameraId ?: return
    val manager = cameraManager ?: return
    try {
      manager.setTorchMode(id, enabled)
    } catch (t: Throwable) {
      Log.w(TAG, "setTorchMode($enabled) failed", t)
    }
  }

  fun forceOff(onStrobeChanged: (Boolean) -> Unit = {}) {
    setTorch(false, onStrobeChanged)
  }

  suspend fun transmitMorse(
    text: String,
    wpm: Int,
    loop: Boolean,
    onStrobeChanged: (Boolean) -> Unit,
    onCharIndexChanged: (Int) -> Unit = {},
  ) =
    withContext(Dispatchers.Default) {
      val clampedWpm = wpm.coerceIn(5, 20)
      val dotMs = 1200L / clampedWpm
      val sanitized = text.uppercase().trim().ifEmpty { "SOS" }

      try {
        do {
          var i = 0
          while (i < sanitized.length) {
            val ch = sanitized[i]
            onCharIndexChanged(i)

            if (ch.isWhitespace()) {
              setTorch(false, onStrobeChanged)
              // Skip consecutive whitespace and wait word gap (7 units total)
              while (i < sanitized.length && sanitized[i].isWhitespace()) {
                i++
              }
              delay(dotMs * 4) // 3 units from previous letter + 4 = 7 units
              continue
            }

            val pattern = MorseAlphabet.CODE_MAP[ch]
            if (pattern != null) {
              for (elemIdx in pattern.indices) {
                val symbol = pattern[elemIdx]
                val durationUnits = if (symbol == '-') 3L else 1L
                setTorch(true, onStrobeChanged)
                delay(dotMs * durationUnits)
                setTorch(false, onStrobeChanged)

                if (elemIdx < pattern.lastIndex) {
                  delay(dotMs) // Intra-character gap: 1 unit
                }
              }
              // Inter-character gap: 3 units
              delay(dotMs * 3)
            }
            i++
          }

          if (loop) {
            setTorch(false, onStrobeChanged)
            delay(dotMs * 7) // Word gap before repeating beacon
          }
        } while (loop)
      } finally {
        forceOff(onStrobeChanged)
        onCharIndexChanged(-1)
      }
    }

  companion object {
    private const val TAG = "TorchMorseController"
  }
}
