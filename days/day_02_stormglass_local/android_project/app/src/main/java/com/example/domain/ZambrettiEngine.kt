package com.example.domain

import com.example.model.PressureReading
import com.example.model.PressureTrend
import com.example.model.ThreatLevel
import com.example.model.WindDirection
import com.example.model.ZambrettiForecast
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Implements the 1915 Negretti & Zambra Zambretti barometric forecasting algorithm,
 * hypsometric Mean Sea Level Pressure (MSLP) reduction, 3-hour tendency analysis,
 * and rapid-drop squall detection.
 */
object ZambrettiEngine {

  private const val THREE_HOURS_MS = 3L * 60L * 60L * 1000L
  private const val ONE_HOUR_MS = 60L * 60L * 1000L
  private const val FIVE_HOURS_MS = 5L * 60L * 60L * 1000L

  fun calculateMslp(stationPressureHpa: Float, elevationMeters: Float): Float {
    val clampedElevation = elevationMeters.coerceIn(0f, 3000f)
    if (clampedElevation <= 0.5f) return stationPressureHpa
    val factor = (1.0f - clampedElevation / 44330.0f).pow(-5.255f)
    return stationPressureHpa * factor
  }

  /**
   * Computes deltaP = currentPressure - pressure3HoursAgo.
   * Prefers the reading closest to 3 hours before the latest reading (between 1h and 5h old).
   * If history spans less than 1 hour (e.g., rapid manual logging tests), falls back to the
   * earliest reading in the last 3 hours so the user gets immediate feedback, marking
   * [isThreeHourComplete] = false.
   */
  fun calculateDeltaP(readings: List<PressureReading>): Pair<Float?, Boolean> {
    if (readings.size < 2) return null to false
    val sorted = readings.sortedBy { it.timestamp }
    val latest = sorted.last()
    val targetTime = latest.timestamp - THREE_HOURS_MS

    val historicalCandidates =
      sorted.dropLast(1).filter {
        val age = latest.timestamp - it.timestamp
        age in ONE_HOUR_MS..FIVE_HOURS_MS
      }

    if (historicalCandidates.isNotEmpty()) {
      val reference = historicalCandidates.minBy { abs(it.timestamp - targetTime) }
      return (latest.pressureHpa - reference.pressureHpa) to true
    }

    val recentCandidates =
      sorted.dropLast(1).filter {
        val age = latest.timestamp - it.timestamp
        age in 1L..THREE_HOURS_MS
      }
    if (recentCandidates.isNotEmpty()) {
      val earliestRecent = recentCandidates.first()
      return (latest.pressureHpa - earliestRecent.pressureHpa) to false
    }

    val previous = sorted[sorted.size - 2]
    return (latest.pressureHpa - previous.pressureHpa) to false
  }

  fun classifyTrend(deltaP: Float?): PressureTrend {
    if (deltaP == null) return PressureTrend.STEADY
    return when {
      deltaP <= -1.6f -> PressureTrend.FALLING
      deltaP >= 1.6f -> PressureTrend.RISING
      else -> PressureTrend.STEADY
    }
  }

  fun isSquallWarning(deltaP: Float?, thresholdHpa: Float): Boolean {
    if (deltaP == null) return false
    return deltaP <= thresholdHpa
  }

  fun computeForecast(
    stationPressureHpa: Float,
    elevationMeters: Float,
    deltaP3h: Float?,
    isThreeHourComplete: Boolean,
    windDirection: WindDirection,
    isSummer: Boolean,
    squallThresholdHpa: Float,
  ): ZambrettiForecast {
    val mslp = calculateMslp(stationPressureHpa, elevationMeters)
    val clampedP0 = mslp.coerceIn(950f, 1050f)
    val trend = classifyTrend(deltaP3h)

    val rawBase =
      when (trend) {
        PressureTrend.FALLING -> {
          val pressureComponent = ((clampedP0 - 950f) / 100f) * 14f + 2f
          val dropPenalty = if ((deltaP3h ?: 0f) <= -3.0f) -2f else 0f
          pressureComponent + dropPenalty
        }
        PressureTrend.STEADY -> {
          ((clampedP0 - 950f) / 100f) * 17f + 6f
        }
        PressureTrend.RISING -> {
          ((clampedP0 - 950f) / 100f) * 15f + 10f
        }
      }

    val baseIndex = rawBase.roundToInt().coerceIn(1, 26)
    val seasonModifier = if (isSummer) +1 else -1
    val windModifier = windDirection.quadrantModifier
    val finalIndex = (baseIndex + seasonModifier + windModifier).coerceIn(1, 26)

    val squallActive = isSquallWarning(deltaP3h, squallThresholdHpa)
    val descriptor = FORECAST_TABLE[finalIndex - 1]
    val threatLevel =
      when {
        squallActive || finalIndex <= 4 -> ThreatLevel.SEVERE
        finalIndex <= 10 -> ThreatLevel.HIGH
        finalIndex <= 17 -> ThreatLevel.MODERATE
        else -> ThreatLevel.LOW
      }

    val confidence = computeConfidence(mslp, deltaP3h, isThreeHourComplete)
    val explanation =
      buildExplanation(
        mslp = mslp,
        elevationMeters = elevationMeters,
        deltaP3h = deltaP3h,
        isThreeHourComplete = isThreeHourComplete,
        trend = trend,
        seasonModifier = seasonModifier,
        windDirection = windDirection,
        finalIndex = finalIndex,
        squallActive = squallActive,
      )

    return ZambrettiForecast(
      index = finalIndex,
      title = descriptor.title,
      maritimeSummary = descriptor.summary,
      confidencePercent = confidence,
      mslpHpa = mslp,
      deltaP3h = deltaP3h,
      isThreeHourWindowComplete = isThreeHourComplete,
      trend = trend,
      threatLevel = threatLevel,
      baseIndex = baseIndex,
      seasonModifier = seasonModifier,
      windModifier = windModifier,
      tendencyExplanation = explanation,
    )
  }

  private fun computeConfidence(
    mslp: Float,
    deltaP3h: Float?,
    isThreeHourComplete: Boolean,
  ): Int {
    var score = if (isThreeHourComplete) 78 else 58
    if (deltaP3h != null && abs(deltaP3h) >= 2.0f) score += 10
    if (mslp <= 990f || mslp >= 1026f) score += 7
    if (deltaP3h == null) score = 45
    return score.coerceIn(40, 96)
  }

  private fun buildExplanation(
    mslp: Float,
    elevationMeters: Float,
    deltaP3h: Float?,
    isThreeHourComplete: Boolean,
    trend: PressureTrend,
    seasonModifier: Int,
    windDirection: WindDirection,
    finalIndex: Int,
    squallActive: Boolean,
  ): String {
    val mslpStr = String.format(Locale.US, "%.1f hPa", mslp)
    val elevStr = String.format(Locale.US, "%.0f m", elevationMeters)
    val deltaStr =
      if (deltaP3h == null) {
        "insufficient history (log 2+ readings)"
      } else {
        val sign = if (deltaP3h > 0f) "+" else ""
        val windowLabel = if (isThreeHourComplete) "3h" else "recent"
        "$sign${String.format(Locale.US, "%.1f", deltaP3h)} hPa ($windowLabel)"
      }
    val seasonStr = if (seasonModifier > 0) "Summer (+1)" else "Winter (-1)"
    val windSign = if (windDirection.quadrantModifier > 0) "+" else ""
    val windStr = "${windDirection.code} ($windSign${windDirection.quadrantModifier})"
    val squallNote =
      if (squallActive) " Rapid drop triggers immediate squall alert." else ""

    return "MSLP $mslpStr (corrected from $elevStr) with $trend tendency [$deltaStr]. " +
      "Adjusted for $seasonStr and $windStr wind quadrant -> Zambretti Index $finalIndex/26.$squallNote"
  }

  private data class ForecastDescriptor(val title: String, val summary: String)

  // Mapped 1..26 (1 = Severe Squall/Storm, 26 = Settled Fine)
  private val FORECAST_TABLE: List<ForecastDescriptor> =
    listOf(
      ForecastDescriptor(
        "Stormy, Severe Squalls",
        "Violent wind shifts, heavy precipitation, and dangerous sea or ridge gusts imminent.",
      ),
      ForecastDescriptor(
        "Gale & Squall Warning",
        "Rapidly deepening low pressure. Expect strong gusts, driving rain, and poor visibility.",
      ),
      ForecastDescriptor(
        "Stormy, Much Rain",
        "Active frontal passage likely within 3–6 hours. Secure camp and avoid exposed terrain.",
      ),
      ForecastDescriptor(
        "Very Unsettled, Squalls Likely",
        "Falling glass signals approaching squall line with gusty winds and heavy showers.",
      ),
      ForecastDescriptor(
        "Very Unsettled, Rain",
        "Persistent low pressure with frequent rain bands and freshening winds.",
      ),
      ForecastDescriptor(
        "Rain at Frequent Intervals",
        "Overcast skies with steady precipitation and shifting winds.",
      ),
      ForecastDescriptor(
        "Unsettled, Rain at Times",
        "Deteriorating conditions. Expect intermittent showers and rising gusts.",
      ),
      ForecastDescriptor(
        "Changeable, Deteriorating",
        "Barometer sliding downward; weather turning wetter and windier over the next 6 hours.",
      ),
      ForecastDescriptor(
        "Showery, Gusty Later",
        "Increasing cloud cover with scattered squally showers developing.",
      ),
      ForecastDescriptor(
        "Stormy, May Improve",
        "Low pressure bottoming out and starting to recover; lingering gusts and showers.",
      ),
      ForecastDescriptor(
        "Unsettled, Clearing Slowly",
        "Post-frontal trough; expect gusty showers mixed with brief bright breaks.",
      ),
      ForecastDescriptor(
        "Changeable, Some Rain",
        "Mixed maritime outlook with passing cloud decks and light rain risk.",
      ),
      ForecastDescriptor(
        "Showery, Bright Intervals",
        "Passing showers interspersed with clearer skies as pressure stabilizes.",
      ),
      ForecastDescriptor(
        "Fairly Fine, Showers Likely",
        "Generally workable visibility with isolated afternoon or coastal showers.",
      ),
      ForecastDescriptor(
        "Fairly Fine, Possible Showers",
        "Moderate conditions; monitor 3h tendency for any renewed drop.",
      ),
      ForecastDescriptor(
        "Changeable, Mending",
        "Pressure recovering from trough; cloud base lifting and wind moderating.",
      ),
      ForecastDescriptor(
        "Fair, Steady Glass",
        "Balanced atmospheric column with light winds and low precipitation probability.",
      ),
      ForecastDescriptor(
        "Fairly Fine, Improving",
        "High pressure ridge building; clearing skies and calmer conditions ahead.",
      ),
      ForecastDescriptor(
        "Becoming Fine",
        "Steady upward barometric climb signals dry air mass moving in.",
      ),
      ForecastDescriptor(
        "Fine, Possibly Light Showers",
        "Predominantly clear weather with only minor residual moisture.",
      ),
      ForecastDescriptor(
        "Fine Weather",
        "Stable high pressure, good visibility, and predictable surface winds.",
      ),
      ForecastDescriptor(
        "Fine, Clearing Skies",
        "Favorable ridge holding firm with crisp visibility.",
      ),
      ForecastDescriptor(
        "Becoming Settled",
        "Strong barometric support; minimal squall risk over the next 12 hours.",
      ),
      ForecastDescriptor(
        "Settled & Fair",
        "Anticyclonic conditions prevailing. Ideal window for exposed travel.",
      ),
      ForecastDescriptor(
        "Settled Fine",
        "High, stable atmospheric ridge with calm to light favorable winds.",
      ),
      ForecastDescriptor(
        "Settled Fine, Very Dry",
        "Peak high-pressure dome. Clear skies, calm air, and high confidence fair weather.",
      ),
    )
}
