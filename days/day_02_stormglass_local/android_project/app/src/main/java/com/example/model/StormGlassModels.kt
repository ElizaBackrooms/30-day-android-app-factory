package com.example.model

import java.util.Locale

data class PressureReading(
  val timestamp: Long,
  val pressureHpa: Float,
  val isManual: Boolean = false,
  val note: String = "",
)

enum class PressureUnit(val label: String) {
  HPA("hPa"),
  INHG("inHg");

  fun formatValue(hpa: Float): String =
    when (this) {
      HPA -> String.format(Locale.US, "%.1f", hpa)
      INHG -> String.format(Locale.US, "%.2f", hpa * HPA_TO_INHG)
    }

  fun formatWithUnit(hpa: Float): String = "${formatValue(hpa)} $label"

  fun formatDelta(deltaHpa: Float): String {
    val value =
      when (this) {
        HPA -> deltaHpa
        INHG -> deltaHpa * HPA_TO_INHG
      }
    val sign = if (value > 0f) "+" else ""
    val pattern = if (this == HPA) "%.1f" else "%.2f"
    return "$sign${String.format(Locale.US, pattern, value)} $label"
  }

  companion object {
    const val HPA_TO_INHG = 0.029529983f
  }
}

enum class TimeWindow(val label: String, val hours: Int) {
  THREE_HOURS("3H", 3),
  TWELVE_HOURS("12H", 12),
  TWENTY_FOUR_HOURS("24H", 24),
}

enum class WindDirection(
  val code: String,
  val fullName: String,
  val quadrantModifier: Int,
  val azimuthDeg: Float,
) {
  N("N", "North", +1, 0f),
  NE("NE", "Northeast", +1, 45f),
  E("E", "East", 0, 90f),
  SE("SE", "Southeast", -1, 135f),
  S("S", "South", -2, 180f),
  SW("SW", "Southwest", -2, 225f),
  W("W", "West", -1, 270f),
  NW("NW", "Northwest", 0, 315f),
}

enum class PressureTrend(val label: String) {
  FALLING("FALLING"),
  STEADY("STEADY"),
  RISING("RISING"),
}

enum class ThreatLevel(val label: String) {
  LOW("CALM / SETTLED"),
  MODERATE("WATCH / CHANGEABLE"),
  HIGH("UNSETTLED / WEATHER"),
  SEVERE("SQUALL / GALE ALERT"),
}

data class ZambrettiForecast(
  val index: Int,
  val title: String,
  val maritimeSummary: String,
  val confidencePercent: Int,
  val mslpHpa: Float,
  val deltaP3h: Float?,
  val isThreeHourWindowComplete: Boolean,
  val trend: PressureTrend,
  val threatLevel: ThreatLevel,
  val baseIndex: Int,
  val seasonModifier: Int,
  val windModifier: Int,
  val tendencyExplanation: String,
)

data class ReadinessTask(
  val id: String,
  val title: String,
  val detail: String,
  val isChecked: Boolean = false,
  val isCritical: Boolean = false,
)

enum class AppTab(val label: String) {
  COCKPIT("Now"),
  FORECASTER("Forecast"),
  LOG("Settings"),
  GUIDE("Help"),
}
