package com.example.engine

import com.example.model.DemoImpulseType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

data class SimulatedSensorFrame(
    val x: Float,
    val y: Float,
    val z: Float,
    val soundDb: Float,
    val timestampMs: Long
)

private data class ActiveImpulse(
    val type: DemoImpulseType,
    val startedAtMs: Long
)

/**
 * Built-in 1-tap Demo / Simulation signal engine so every feature (live seismograph,
 * mic dB gate, trigger alarm, encrypted local log, and opt-in webhook test) works
 * immediately even on a stationary desk or emulator.
 */
class DemoSignalGenerator {
    private val pendingImpulse = AtomicReference<ActiveImpulse?>(null)
    private val rng = Random(42_137L)

    fun injectImpulse(type: DemoImpulseType) {
        pendingImpulse.set(ActiveImpulse(type = type, startedAtMs = System.currentTimeMillis()))
    }

    fun observeSimulatedFrames(): Flow<SimulatedSensorFrame> = flow {
        var tick = 0L
        while (true) {
            val nowMs = System.currentTimeMillis()
            val phase = tick * 0.22

            // Subtle realistic ambient micro-tremor (desk / floor hum ~0.04–0.11 m/s²)
            var x = (sin(phase) * 0.045 + (rng.nextDouble() - 0.5) * 0.05).toFloat()
            var y = (cos(phase * 1.3) * 0.035 + (rng.nextDouble() - 0.5) * 0.05).toFloat()
            var z = (sin(phase * 0.7) * 0.055 + (rng.nextDouble() - 0.5) * 0.06).toFloat()
            var soundDb = (34.0 + sin(phase * 0.35) * 2.5 + (rng.nextDouble() - 0.5) * 2.0).toFloat()

            val impulse = pendingImpulse.get()
            if (impulse != null) {
                val elapsedSec = (nowMs - impulse.startedAtMs) / 1000.0
                if (elapsedSec > 1.8) {
                    pendingImpulse.compareAndSet(impulse, null)
                } else if (elapsedSec >= 0.0) {
                    // Damped seismic ring-down wave
                    val envelope = exp(-elapsedSec * 3.4)
                    val carrier = sin(elapsedSec * 2.0 * PI * 6.5)
                    val secondary = cos(elapsedSec * 2.0 * PI * 4.2)
                    val peak = impulse.type.peakAccelMs2.toDouble()
                    x += (peak * 0.65 * envelope * carrier).toFloat()
                    y += (peak * 0.48 * envelope * secondary).toFloat()
                    z += (peak * 0.82 * envelope * sin(elapsedSec * 2.0 * PI * 8.0)).toFloat()

                    val dbLift = (impulse.type.peakDb - 34f) * exp(-elapsedSec * 2.8)
                    soundDb = (34.0 + dbLift).toFloat().coerceIn(30f, 110f)
                }
            }

            emit(
                SimulatedSensorFrame(
                    x = x,
                    y = y,
                    z = z,
                    soundDb = soundDb,
                    timestampMs = nowMs
                )
            )
            tick++
            delay(50L)
        }
    }
}
