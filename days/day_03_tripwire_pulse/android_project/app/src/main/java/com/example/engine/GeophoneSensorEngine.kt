package com.example.engine

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class AccelReading(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampMs: Long
)

data class GeophoneHardwareInfo(
    val available: Boolean,
    val usingLinearAcceleration: Boolean,
    val sensorDisplayName: String
)

/**
 * Pure AOSP 3-axis accelerometer geophone driver.
 * Prefers Sensor.TYPE_LINEAR_ACCELERATION (gravity-free motion) and falls back cleanly
 * to high-pass filtered Sensor.TYPE_ACCELEROMETER if the virtual linear sensor is absent.
 */
class GeophoneSensorEngine(context: Context) {
    private val sensorManager: SensorManager? =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

    private val linearSensor: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

    private val rawAccelerometer: Sensor? =
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    fun hardwareInfo(): GeophoneHardwareInfo {
        return when {
            linearSensor != null -> GeophoneHardwareInfo(
                available = true,
                usingLinearAcceleration = true,
                sensorDisplayName = "3-Axis Linear Geophone (TYPE_LINEAR_ACCELERATION)"
            )
            rawAccelerometer != null -> GeophoneHardwareInfo(
                available = true,
                usingLinearAcceleration = false,
                sensorDisplayName = "3-Axis Accelerometer (Gravity-Filtered)"
            )
            else -> GeophoneHardwareInfo(
                available = false,
                usingLinearAcceleration = false,
                sensorDisplayName = "No shake sensor detected — use Test Mode"
            )
        }
    }

    fun observeAcceleration(): Flow<AccelReading> = callbackFlow {
        val manager = sensorManager
        val activeSensor = linearSensor ?: rawAccelerometer
        if (manager == null || activeSensor == null) {
            close()
            return@callbackFlow
        }

        val isLinear = activeSensor.type == Sensor.TYPE_LINEAR_ACCELERATION
        val gravityState = floatArrayOf(0f, 0f, 9.81f)
        var initialized = false
        var lastEmitMs = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val nowMs = System.currentTimeMillis()
                // Throttle UI stream to ~25 Hz (40ms) for smooth battery-friendly Compose seismograph
                if (nowMs - lastEmitMs < 40L) return
                lastEmitMs = nowMs

                val rawX = event.values.getOrElse(0) { 0f }
                val rawY = event.values.getOrElse(1) { 0f }
                val rawZ = event.values.getOrElse(2) { 0f }

                val (lx, ly, lz) = if (isLinear) {
                    Triple(rawX, rawY, rawZ)
                } else {
                    if (!initialized) {
                        gravityState[0] = rawX
                        gravityState[1] = rawY
                        gravityState[2] = rawZ
                        initialized = true
                        Triple(0f, 0f, 0f)
                    } else {
                        SeismographMath.applyGravityHighPass(rawX, rawY, rawZ, gravityState)
                    }
                }

                trySend(AccelReading(x = lx, y = ly, z = lz, timestampMs = nowMs))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                // No-op
            }
        }

        val registered = manager.registerListener(
            listener,
            activeSensor,
            SensorManager.SENSOR_DELAY_GAME
        )

        if (!registered) {
            close()
            return@callbackFlow
        }

        awaitClose {
            manager.unregisterListener(listener)
        }
    }
}
