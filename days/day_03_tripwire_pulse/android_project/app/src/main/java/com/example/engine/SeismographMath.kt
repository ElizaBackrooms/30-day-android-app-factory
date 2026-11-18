package com.example.engine

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

enum class SensorGateMode(val displayName: String, val plainEnglishSummary: String) {
    VIBRATION_ONLY(
        displayName = "Shake Only",
        plainEnglishSummary = "Triggers on vibration alone (microphone not needed)."
    ),
    VIBRATION_OR_SOUND(
        displayName = "Shake OR Sound",
        plainEnglishSummary = "Triggers if either vibration or loudness crosses its limit."
    ),
    VIBRATION_AND_SOUND(
        displayName = "Shake + Sound (Dual Check)",
        plainEnglishSummary = "Requires both vibration and noise to cut down false alarms."
    )
}

data class SeismographSample(
    val timestampMs: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitudeMs2: Float,
    val deltaMs2: Float,
    val soundDb: Float,
    val isTriggered: Boolean
)

data class AudioLevelReading(
    val rms: Double,
    val dbfs: Double,
    val dbSplApprox: Float
)

data class TriggerEvaluation(
    val triggered: Boolean,
    val shakeExceeded: Boolean,
    val soundExceeded: Boolean,
    val reason: String
)

object SeismographMath {
    const val DEFAULT_EMA_ALPHA = 0.08f
    const val SILENCE_DBFS = -96.0
    const val DB_SPL_OFFSET = 96.0
    const val MAX_PCM_AMPLITUDE = 32767.0
    const val MAX_HISTORY_SAMPLES = 140

    /**
     * Computes 3-axis Euclidean vector magnitude in m/s² from linear acceleration.
     */
    fun computeMagnitude(x: Float, y: Float, z: Float): Float {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return 0f
        return sqrt((x * x + y * y + z * z).toDouble()).toFloat()
    }

    /**
     * Exponential Moving Average (EMA) baseline noise floor tracker.
     */
    fun updateBaselineEma(
        currentBaseline: Float,
        magnitude: Float,
        alpha: Float = DEFAULT_EMA_ALPHA
    ): Float {
        if (!magnitude.isFinite()) return currentBaseline.coerceAtLeast(0f)
        val safeAlpha = alpha.coerceIn(0.001f, 1.0f)
        val safeCurrent = if (currentBaseline.isFinite()) currentBaseline.coerceAtLeast(0f) else 0f
        return safeCurrent + safeAlpha * (magnitude.coerceAtLeast(0f) - safeCurrent)
    }

    /**
     * Returns positive shake excursion above the ambient baseline noise floor.
     */
    fun computeShakeDelta(magnitude: Float, baseline: Float): Float {
        if (!magnitude.isFinite() || !baseline.isFinite()) return 0f
        return max(0f, magnitude - baseline)
    }

    /**
     * Converts a 16-bit signed PCM audio buffer to RMS, dBFS, and an approximate dB SPL meter reading.
     * Runs strictly in memory — no audio data is retained.
     */
    fun computeDecibelsFromPcm(samples: ShortArray, count: Int): AudioLevelReading {
        val validCount = count.coerceIn(0, samples.size)
        if (validCount <= 0) {
            return AudioLevelReading(
                rms = 0.0,
                dbfs = SILENCE_DBFS,
                dbSplApprox = 0f
            )
        }

        var sumSquares = 0.0
        for (i in 0 until validCount) {
            val s = samples[i].toDouble()
            sumSquares += s * s
        }
        val rms = sqrt(sumSquares / validCount)
        if (rms <= 0.0) {
            return AudioLevelReading(
                rms = 0.0,
                dbfs = SILENCE_DBFS,
                dbSplApprox = 0f
            )
        }

        val ratio = (rms / MAX_PCM_AMPLITUDE).coerceIn(1e-6, 1.0)
        val dbfs = (20.0 * log10(ratio)).coerceIn(SILENCE_DBFS, 0.0)
        val dbSpl = (dbfs + DB_SPL_OFFSET).toFloat().coerceIn(0f, 120f)
        return AudioLevelReading(
            rms = rms,
            dbfs = dbfs,
            dbSplApprox = dbSpl
        )
    }

    /**
     * Evaluates whether a sample crosses the configured vibration and/or sound gate thresholds.
     */
    fun evaluateTrigger(
        shakeDeltaMs2: Float,
        shakeThresholdMs2: Float,
        soundDb: Float,
        soundThresholdDb: Float,
        gateMode: SensorGateMode,
        micActive: Boolean
    ): TriggerEvaluation {
        val shakeExceeded = shakeDeltaMs2 >= shakeThresholdMs2
        val soundExceeded = micActive && (soundDb >= soundThresholdDb)

        return when (gateMode) {
            SensorGateMode.VIBRATION_ONLY -> TriggerEvaluation(
                triggered = shakeExceeded,
                shakeExceeded = shakeExceeded,
                soundExceeded = false,
                reason = if (shakeExceeded) {
                    "Shake spike (${formatTwoDecimals(shakeDeltaMs2)} m/s²)"
                } else {
                    "Below limit"
                }
            )

            SensorGateMode.VIBRATION_OR_SOUND -> {
                val triggered = shakeExceeded || soundExceeded
                val reason = when {
                    shakeExceeded && soundExceeded ->
                        "Shake (${formatTwoDecimals(shakeDeltaMs2)} m/s²) + Sound (${soundDb.toInt()} dB)"
                    shakeExceeded ->
                        "Shake spike (${formatTwoDecimals(shakeDeltaMs2)} m/s²)"
                    soundExceeded ->
                        "Loud sound (${soundDb.toInt()} dB)"
                    else -> "Below limit"
                }
                TriggerEvaluation(
                    triggered = triggered,
                    shakeExceeded = shakeExceeded,
                    soundExceeded = soundExceeded,
                    reason = reason
                )
            }

            SensorGateMode.VIBRATION_AND_SOUND -> {
                // If mic gate is not active (e.g. user declined permission), gracefully fall back to shake-only
                val triggered = if (micActive) {
                    shakeExceeded && soundExceeded
                } else {
                    shakeExceeded
                }
                val reason = when {
                    triggered && micActive ->
                        "Confirmed shake (${formatTwoDecimals(shakeDeltaMs2)} m/s²) + sound (${soundDb.toInt()} dB)"
                    triggered ->
                        "Shake spike (${formatTwoDecimals(shakeDeltaMs2)} m/s²) [Mic off]"
                    else -> "Below dual-check limit"
                }
                TriggerEvaluation(
                    triggered = triggered,
                    shakeExceeded = shakeExceeded,
                    soundExceeded = soundExceeded,
                    reason = reason
                )
            }
        }
    }

    /**
     * Computes a simple high-pass filtered linear acceleration estimate when a device
     * only exposes raw TYPE_ACCELEROMETER (gravity + motion).
     */
    fun applyGravityHighPass(
        rawX: Float,
        rawY: Float,
        rawZ: Float,
        gravityState: FloatArray,
        alpha: Float = 0.8f
    ): Triple<Float, Float, Float> {
        gravityState[0] = alpha * gravityState[0] + (1f - alpha) * rawX
        gravityState[1] = alpha * gravityState[1] + (1f - alpha) * rawY
        gravityState[2] = alpha * gravityState[2] + (1f - alpha) * rawZ
        val lx = rawX - gravityState[0]
        val ly = rawY - gravityState[1]
        val lz = rawZ - gravityState[2]
        // Suppress tiny initial convergence glitch
        if (abs(lx) > 50f || abs(ly) > 50f || abs(lz) > 50f) {
            return Triple(0f, 0f, 0f)
        }
        return Triple(lx, ly, lz)
    }

    fun formatTwoDecimals(value: Float): String {
        val safe = if (value.isFinite()) value else 0f
        return String.format(java.util.Locale.US, "%.2f", safe)
    }
}
