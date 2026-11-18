package com.example

import com.example.engine.SeismographMath
import com.example.engine.SensorGateMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeismographMathTest {

    @Test
    fun `computeMagnitude computes 3D Euclidean norm accurately`() {
        val mag = SeismographMath.computeMagnitude(3f, 4f, 0f)
        assertEquals(5.0f, mag, 0.0001f)

        val mag3d = SeismographMath.computeMagnitude(1f, 2f, 2f)
        assertEquals(3.0f, mag3d, 0.0001f)
    }

    @Test
    fun `computeMagnitude guards against NaN and Infinity`() {
        assertEquals(0f, SeismographMath.computeMagnitude(Float.NaN, 1f, 1f), 0.0001f)
        assertEquals(0f, SeismographMath.computeMagnitude(Float.POSITIVE_INFINITY, 0f, 0f), 0.0001f)
    }

    @Test
    fun `EMA baseline tracks steady noise floor and delta clamps at zero`() {
        var baseline = 0f
        repeat(60) {
            baseline = SeismographMath.updateBaselineEma(baseline, 0.20f, alpha = 0.1f)
        }
        assertEquals(0.20f, baseline, 0.01f)

        val deltaBelow = SeismographMath.computeShakeDelta(magnitude = 0.10f, baseline = baseline)
        assertEquals(0f, deltaBelow, 0.0001f)

        val deltaSpike = SeismographMath.computeShakeDelta(magnitude = 2.20f, baseline = baseline)
        assertEquals(2.0f, deltaSpike, 0.02f)
    }

    @Test
    fun `computeDecibelsFromPcm handles silence, full-scale, and empty buffers`() {
        val empty = SeismographMath.computeDecibelsFromPcm(ShortArray(0), 0)
        assertEquals(0f, empty.dbSplApprox, 0.001f)
        assertEquals(SeismographMath.SILENCE_DBFS, empty.dbfs, 0.001)

        val silent = SeismographMath.computeDecibelsFromPcm(ShortArray(256) { 0 }, 256)
        assertEquals(0f, silent.dbSplApprox, 0.001f)

        val fullScale = SeismographMath.computeDecibelsFromPcm(ShortArray(256) { Short.MAX_VALUE }, 256)
        assertEquals(0.0, fullScale.dbfs, 0.05)
        assertEquals(96.0f, fullScale.dbSplApprox, 0.2f)

        val halfScale = SeismographMath.computeDecibelsFromPcm(ShortArray(256) { 16384 }, 256)
        assertEquals(-6.02, halfScale.dbfs, 0.15)
        assertEquals(90.0f, halfScale.dbSplApprox, 0.3f)
    }

    @Test
    fun `evaluateTrigger enforces vibration-only, OR, and dual-check AND gate modes`() {
        // Vibration-only ignores loud sound if shake is low
        val vibOnlyLow = SeismographMath.evaluateTrigger(
            shakeDeltaMs2 = 0.4f,
            shakeThresholdMs2 = 1.2f,
            soundDb = 90f,
            soundThresholdDb = 65f,
            gateMode = SensorGateMode.VIBRATION_ONLY,
            micActive = true
        )
        assertFalse(vibOnlyLow.triggered)

        // Vibration OR Sound fires on loud sound even when shake is low
        val orSound = SeismographMath.evaluateTrigger(
            shakeDeltaMs2 = 0.4f,
            shakeThresholdMs2 = 1.2f,
            soundDb = 80f,
            soundThresholdDb = 65f,
            gateMode = SensorGateMode.VIBRATION_OR_SOUND,
            micActive = true
        )
        assertTrue(orSound.triggered)
        assertTrue(orSound.soundExceeded)

        // Vibration AND Sound requires both when mic is active
        val andOnlyShake = SeismographMath.evaluateTrigger(
            shakeDeltaMs2 = 2.0f,
            shakeThresholdMs2 = 1.2f,
            soundDb = 45f,
            soundThresholdDb = 65f,
            gateMode = SensorGateMode.VIBRATION_AND_SOUND,
            micActive = true
        )
        assertFalse(andOnlyShake.triggered)

        val andBoth = SeismographMath.evaluateTrigger(
            shakeDeltaMs2 = 2.0f,
            shakeThresholdMs2 = 1.2f,
            soundDb = 78f,
            soundThresholdDb = 65f,
            gateMode = SensorGateMode.VIBRATION_AND_SOUND,
            micActive = true
        )
        assertTrue(andBoth.triggered)

        // Graceful fallback when mic is off/denied: AND mode falls back to shake-only
        val andMicDeclinedFallback = SeismographMath.evaluateTrigger(
            shakeDeltaMs2 = 2.0f,
            shakeThresholdMs2 = 1.2f,
            soundDb = 30f,
            soundThresholdDb = 65f,
            gateMode = SensorGateMode.VIBRATION_AND_SOUND,
            micActive = false
        )
        assertTrue(andMicDeclinedFallback.triggered)
    }
}
