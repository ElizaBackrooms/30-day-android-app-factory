package com.example.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.abs

/**
 * Dual-mode barometer sensor controller:
 * - Detects Sensor.TYPE_PRESSURE on startup.
 * - Low-pass filters incoming hardware samples (alpha = 0.2f).
 * - Reports live filtered hPa and throttled auto-log candidates.
 * - Gracefully reports unavailable hardware on devices like Moto G Stylus 4G.
 */
class BarometerSensorController(
  context: Context,
  private val onFilteredPressure: (Float) -> Unit,
  private val onAutoLogSample: (Float) -> Unit,
) : SensorEventListener {

  private val sensorManager: SensorManager? =
    context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager

  private val pressureSensor: Sensor? =
    sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

  val hasHardwareSensor: Boolean
    get() = pressureSensor != null

  private var filteredPressure: Float? = null
  private var lastAutoLoggedPressure: Float? = null
  private var lastAutoLoggedTimeMs: Long = 0L
  private var isRegistered: Boolean = false

  fun start() {
    val sensor = pressureSensor ?: return
    if (isRegistered) return
    sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_UI)
    isRegistered = true
  }

  fun stop() {
    if (!isRegistered) return
    sensorManager?.unregisterListener(this)
    isRegistered = false
  }

  override fun onSensorChanged(event: SensorEvent?) {
    if (event == null || event.sensor.type != Sensor.TYPE_PRESSURE) return
    val raw = event.values.firstOrNull() ?: return
    if (!raw.isFinite() || raw < 300f || raw > 1150f) return

    val prev = filteredPressure
    val smoothed =
      if (prev == null) {
        raw
      } else {
        LOW_PASS_ALPHA * raw + (1f - LOW_PASS_ALPHA) * prev
      }
    filteredPressure = smoothed
    onFilteredPressure(smoothed)

    val now = System.currentTimeMillis()
    val prevLogged = lastAutoLoggedPressure
    val elapsed = now - lastAutoLoggedTimeMs
    val shouldLog =
      prevLogged == null ||
        elapsed >= AUTO_LOG_INTERVAL_MS ||
        (elapsed >= MIN_CHANGE_INTERVAL_MS && abs(smoothed - prevLogged) >= SIGNIFICANT_SHIFT_HPA)

    if (shouldLog) {
      lastAutoLoggedPressure = smoothed
      lastAutoLoggedTimeMs = now
      onAutoLogSample(smoothed)
    }
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  companion object {
    private const val LOW_PASS_ALPHA = 0.2f
    private const val AUTO_LOG_INTERVAL_MS = 5L * 60L * 1000L
    private const val MIN_CHANGE_INTERVAL_MS = 60L * 1000L
    private const val SIGNIFICANT_SHIFT_HPA = 0.2f
  }
}
