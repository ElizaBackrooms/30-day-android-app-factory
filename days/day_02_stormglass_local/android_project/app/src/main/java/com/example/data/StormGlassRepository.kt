package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.domain.ZambrettiEngine
import com.example.model.PressureReading
import com.example.model.PressureUnit
import com.example.model.WindDirection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class StormGlassRepository(context: Context) {

  private val prefs: SharedPreferences =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun isPersistenceEnabled(): Boolean = prefs.getBoolean(KEY_PERSIST_OPT_IN, false)

  fun setPersistenceEnabled(enabled: Boolean) {
    if (enabled) {
      prefs.edit().putBoolean(KEY_PERSIST_OPT_IN, true).apply()
    } else {
      prefs.edit().clear().apply()
    }
  }

  fun wipeAllData() {
    prefs.edit().clear().apply()
  }

  fun loadReadings(): List<PressureReading> {
    if (!isPersistenceEnabled()) return emptyList()
    val raw = prefs.getString(KEY_READINGS, null) ?: return emptyList()
    return try {
      val array = JSONArray(raw)
      val list = ArrayList<PressureReading>(array.length().coerceAtMost(MAX_READINGS))
      for (i in 0 until array.length().coerceAtMost(MAX_READINGS * 2)) {
        val obj = array.optJSONObject(i) ?: continue
        val ts = obj.optLong("timestamp", -1L)
        val p = obj.optDouble("pressureHpa", Double.NaN)
        if (ts <= 0L || !p.isFinite() || p !in 300.0..1150.0) continue
        list.add(
          PressureReading(
            timestamp = ts,
            pressureHpa = p.toFloat(),
            isManual = obj.optBoolean("isManual", false),
            note = sanitizeSingleLine(obj.optString("note", ""), 120),
          )
        )
      }
      list.sortedBy { it.timestamp }.takeLast(MAX_READINGS)
    } catch (_: Throwable) {
      emptyList()
    }
  }

  fun saveReadings(readings: List<PressureReading>): List<PressureReading> {
    val trimmed =
      readings
        .filter { it.pressureHpa.isFinite() && it.pressureHpa in 300f..1150f && it.timestamp > 0L }
        .sortedBy { it.timestamp }
        .takeLast(MAX_READINGS)

    if (!isPersistenceEnabled()) {
      return trimmed
    }

    val array = JSONArray()
    trimmed.forEach { reading ->
      val obj =
        JSONObject().apply {
          put("timestamp", reading.timestamp)
          put("pressureHpa", reading.pressureHpa.toDouble())
          put("isManual", reading.isManual)
          put("note", sanitizeSingleLine(reading.note, 120))
        }
      array.put(obj)
    }
    prefs.edit().putString(KEY_READINGS, array.toString()).apply()
    return trimmed
  }

  fun appendReading(existing: List<PressureReading>, reading: PressureReading): List<PressureReading> {
    return saveReadings(existing + reading)
  }

  fun deleteReading(existing: List<PressureReading>, timestamp: Long): List<PressureReading> {
    return saveReadings(existing.filterNot { it.timestamp == timestamp })
  }

  fun clearReadings() {
    prefs.edit().remove(KEY_READINGS).apply()
  }

  fun loadElevationMeters(): Float =
    if (!isPersistenceEnabled()) 0f else prefs.getFloat(KEY_ELEVATION, 0f).coerceIn(0f, 3000f)

  fun saveElevationMeters(meters: Float) {
    if (!isPersistenceEnabled()) return
    prefs.edit().putFloat(KEY_ELEVATION, meters.coerceIn(0f, 3000f)).apply()
  }

  fun loadWindDirection(): WindDirection {
    if (!isPersistenceEnabled()) return WindDirection.NW
    val name = prefs.getString(KEY_WIND, WindDirection.NW.name) ?: WindDirection.NW.name
    return WindDirection.entries.firstOrNull { it.name == name } ?: WindDirection.NW
  }

  fun saveWindDirection(direction: WindDirection) {
    if (!isPersistenceEnabled()) return
    prefs.edit().putString(KEY_WIND, direction.name).apply()
  }

  fun loadIsSummer(): Boolean =
    if (!isPersistenceEnabled()) true else prefs.getBoolean(KEY_IS_SUMMER, true)

  fun saveIsSummer(isSummer: Boolean) {
    if (!isPersistenceEnabled()) return
    prefs.edit().putBoolean(KEY_IS_SUMMER, isSummer).apply()
  }

  fun loadSquallThreshold(): Float =
    if (!isPersistenceEnabled()) DEFAULT_SQUALL_THRESHOLD
    else prefs.getFloat(KEY_SQUALL_THRESHOLD, DEFAULT_SQUALL_THRESHOLD).coerceIn(-4.0f, -1.0f)

  fun saveSquallThreshold(threshold: Float) {
    if (!isPersistenceEnabled()) return
    prefs.edit().putFloat(KEY_SQUALL_THRESHOLD, threshold.coerceIn(-4.0f, -1.0f)).apply()
  }

  fun loadPressureUnit(): PressureUnit {
    if (!isPersistenceEnabled()) return PressureUnit.HPA
    val name = prefs.getString(KEY_UNIT, PressureUnit.HPA.name) ?: PressureUnit.HPA.name
    return PressureUnit.entries.firstOrNull { it.name == name } ?: PressureUnit.HPA
  }

  fun savePressureUnit(unit: PressureUnit) {
    if (!isPersistenceEnabled()) return
    prefs.edit().putString(KEY_UNIT, unit.name).apply()
  }

  fun loadCheckedChecklistIds(): Set<String> =
    if (!isPersistenceEnabled()) emptySet()
    else prefs.getStringSet(KEY_CHECKLIST, emptySet())?.toSet() ?: emptySet()

  fun saveCheckedChecklistIds(ids: Set<String>) {
    if (!isPersistenceEnabled()) return
    prefs.edit().putStringSet(KEY_CHECKLIST, ids).apply()
  }

  fun generate24hSquallDemoData(nowMs: Long = System.currentTimeMillis()): List<PressureReading> {
    val hourMs = 3_600_000L
    val curvePoints =
      listOf(
        24.0f to 1018.8f,
        22.5f to 1019.0f,
        21.0f to 1018.9f,
        19.5f to 1018.6f,
        18.0f to 1018.2f,
        16.5f to 1017.7f,
        15.0f to 1017.0f,
        13.5f to 1016.2f,
        12.0f to 1015.4f,
        10.5f to 1014.5f,
        9.0f to 1013.6f,
        7.5f to 1012.5f,
        6.0f to 1011.2f,
        5.0f to 1010.0f,
        4.0f to 1008.4f,
        3.0f to 1005.1f,
        2.5f to 1004.5f,
        2.0f to 1003.9f,
        1.5f to 1003.2f,
        1.0f to 1002.6f,
        0.5f to 1001.9f,
        0.0f to 1001.3f,
      )

    val readings =
      curvePoints.mapIndexed { idx, (hoursAgo, pressure) ->
        val ts = nowMs - (hoursAgo * hourMs).toLong()
        val note =
          when {
            idx == 0 -> "Demo ridge baseline"
            hoursAgo == 3.0f -> "Front approaching"
            hoursAgo == 0.0f -> "Squall line onset"
            else -> ""
          }
        PressureReading(
          timestamp = ts,
          pressureHpa = pressure,
          isManual = idx % 4 == 0,
          note = note,
        )
      }
    return saveReadings(readings)
  }

  fun buildCsvExport(readings: List<PressureReading>, elevationMeters: Float): String {
    val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    val builder = StringBuilder()
    builder.appendLine("timestamp_local,station_hpa,mslp_hpa,mode,note")
    readings
      .sortedByDescending { it.timestamp }
      .forEach { r ->
        val ts = isoFormat.format(Date(r.timestamp))
        val mslp = ZambrettiEngine.calculateMslp(r.pressureHpa, elevationMeters)
        val mode = if (r.isManual) "MANUAL" else "AUTO"
        val cleanNote = sanitizeCsvField(r.note)
        builder.appendLine(
          String.format(
            Locale.US,
            "%s,%.1f,%.1f,%s,%s",
            ts,
            r.pressureHpa,
            mslp,
            mode,
            cleanNote,
          )
        )
      }
    return builder.toString()
  }

  internal fun sanitizeCsvField(raw: String): String {
    val flat = sanitizeSingleLine(raw, 120).replace(",", ";")
    return if (flat.isNotEmpty() && flat[0] in charArrayOf('=', '+', '-', '@', '\t')) {
      "'$flat"
    } else {
      flat
    }
  }

  private fun sanitizeSingleLine(raw: String, maxChars: Int): String {
    val sb = StringBuilder(raw.length.coerceAtMost(maxChars))
    for (ch in raw) {
      if (sb.length >= maxChars) break
      val norm = if (ch == '\r' || ch == '\n' || ch == '\t') ' ' else ch
      if (norm.code >= 0x20 && norm.code != 0x7F) {
        sb.append(norm)
      }
    }
    return sb.toString().trim()
  }

  companion object {
    private const val PREFS_NAME = "stormglass_local_prefs"
    private const val KEY_PERSIST_OPT_IN = "persist_history_opt_in_v1"
    private const val KEY_READINGS = "readings_json"
    private const val KEY_ELEVATION = "elevation_meters"
    private const val KEY_WIND = "wind_direction"
    private const val KEY_IS_SUMMER = "is_summer"
    private const val KEY_SQUALL_THRESHOLD = "squall_threshold_hpa"
    private const val KEY_UNIT = "pressure_unit"
    private const val KEY_CHECKLIST = "checked_checklist_ids"

    const val MAX_READINGS = 100
    const val DEFAULT_SQUALL_THRESHOLD = -2.0f
  }
}
