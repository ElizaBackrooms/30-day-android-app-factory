package com.example.network

import com.example.model.WindDirection
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.net.ssl.HttpsURLConnection
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class OnlineWeatherSnapshot(
  val latitude: Double,
  val longitude: Double,
  val mslpHpa: Float,
  val surfacePressureHpa: Float?,
  val temperatureC: Float?,
  val windSpeedKmh: Float?,
  val windDegrees: Int?,
  val suggestedWind: WindDirection?,
  val conditionSummary: String,
  val fetchedAtMs: Long = System.currentTimeMillis(),
)

object OnlineWeatherClient {
  private const val CONNECT_TIMEOUT_MS = 10_000
  private const val READ_TIMEOUT_MS = 10_000
  private const val MAX_RESPONSE_BYTES = 64 * 1024
  private const val OPEN_METEO_HOST = "api.open-meteo.com"

  suspend fun fetchCurrentConditions(lat: Double, lon: Double): Result<OnlineWeatherSnapshot> =
    withContext(Dispatchers.IO) {
      if (!lat.isFinite() || lat !in -90.0..90.0) {
        return@withContext Result.failure(
          IllegalArgumentException("Latitude must be between -90.0 and 90.0")
        )
      }
      if (!lon.isFinite() || lon !in -180.0..180.0) {
        return@withContext Result.failure(
          IllegalArgumentException("Longitude must be between -180.0 and 180.0")
        )
      }

      val urlStr =
        String.format(
          Locale.US,
          "https://%s/v1/forecast?latitude=%.4f&longitude=%.4f&current=surface_pressure,pressure_msl,temperature_2m,wind_speed_10m,wind_direction_10m,weather_code",
          OPEN_METEO_HOST,
          lat,
          lon,
        )

      val resolved =
        try {
          InetAddress.getAllByName(OPEN_METEO_HOST)
        } catch (_: Throwable) {
          return@withContext Result.failure(IllegalStateException("Unable to reach $OPEN_METEO_HOST (offline?)"))
        }
      if (resolved.isEmpty() || resolved.any { isBlockedIp(it) }) {
        return@withContext Result.failure(IllegalStateException("Blocked unsafe or private resolved IP target"))
      }

      var conn: HttpsURLConnection? = null
      try {
        conn =
          (URL(urlStr).openConnection() as? HttpsURLConnection)
            ?: return@withContext Result.failure(IllegalStateException("HTTPS connection required"))

        conn.requestMethod = "GET"
        conn.connectTimeout = CONNECT_TIMEOUT_MS
        conn.readTimeout = READ_TIMEOUT_MS
        conn.instanceFollowRedirects = false
        conn.useCaches = false
        conn.setRequestProperty("Accept", "application/json")

        val code = conn.responseCode
        if (code !in 200..299) {
          return@withContext Result.failure(IllegalStateException("Weather server HTTP $code"))
        }

        val jsonText = readCappedUtf8(conn)
        val root = JSONObject(jsonText)
        val current = root.optJSONObject("current")
          ?: return@withContext Result.failure(IllegalStateException("Missing current weather object"))

        val mslpRaw = current.optDouble("pressure_msl", Double.NaN)
        if (!mslpRaw.isFinite() || mslpRaw !in 800.0..1120.0) {
          return@withContext Result.failure(IllegalStateException("Invalid station sea-level pressure"))
        }
        val surfaceRaw = current.optDouble("surface_pressure", Double.NaN)
        val surface = if (surfaceRaw.isFinite() && surfaceRaw in 300.0..1150.0) surfaceRaw.toFloat() else null
        val tempRaw = current.optDouble("temperature_2m", Double.NaN)
        val temp = if (tempRaw.isFinite()) tempRaw.toFloat() else null
        val windSpeedRaw = current.optDouble("wind_speed_10m", Double.NaN)
        val windSpeed = if (windSpeedRaw.isFinite()) windSpeedRaw.toFloat() else null
        val windDegRaw = if (current.has("wind_direction_10m")) current.optDouble("wind_direction_10m", Double.NaN) else Double.NaN
        val windDeg = if (windDegRaw.isFinite()) ((windDegRaw.roundToInt() % 360) + 360) % 360 else null
        val weatherCode = current.optInt("weather_code", -1)

        Result.success(
          OnlineWeatherSnapshot(
            latitude = lat,
            longitude = lon,
            mslpHpa = mslpRaw.toFloat(),
            surfacePressureHpa = surface,
            temperatureC = temp,
            windSpeedKmh = windSpeed,
            windDegrees = windDeg,
            suggestedWind = windDeg?.let { degreesToWindDirection(it) },
            conditionSummary = describeWmoCode(weatherCode),
          )
        )
      } catch (t: Throwable) {
        Result.failure(IllegalStateException(t.message ?: "Weather check failed"))
      } finally {
        conn?.disconnect()
      }
    }

  internal fun degreesToWindDirection(deg: Int): WindDirection {
    val norm = ((deg % 360) + 360) % 360
    return when {
      norm >= 338 || norm < 23 -> WindDirection.N
      norm < 68 -> WindDirection.NE
      norm < 113 -> WindDirection.E
      norm < 158 -> WindDirection.SE
      norm < 203 -> WindDirection.S
      norm < 248 -> WindDirection.SW
      norm < 293 -> WindDirection.W
      else -> WindDirection.NW
    }
  }

  private fun describeWmoCode(code: Int): String =
    when (code) {
      0 -> "Clear sky"
      1 -> "Mostly clear"
      2 -> "Partly cloudy"
      3 -> "Overcast"
      45, 48 -> "Fog / mist"
      51, 53, 55 -> "Light drizzle"
      61, 63, 65 -> "Rain"
      71, 73, 75 -> "Snow"
      80, 81, 82 -> "Rain showers"
      95, 96, 99 -> "Thunderstorm risk"
      else -> "Outdoor conditions ready"
    }

  private fun isBlockedIp(addr: InetAddress): Boolean {
    if (
      addr.isAnyLocalAddress ||
        addr.isLoopbackAddress ||
        addr.isLinkLocalAddress ||
        addr.isSiteLocalAddress ||
        addr.isMulticastAddress
    ) {
      return true
    }
    return when (addr) {
      is Inet4Address -> {
        val b0 = addr.address[0].toInt() and 0xFF
        val b1 = addr.address[1].toInt() and 0xFF
        b0 == 0 || b0 == 10 || (b0 == 100 && b1 in 64..127) || b0 == 127 ||
          (b0 == 169 && b1 == 254) || (b0 == 172 && b1 in 16..31) ||
          (b0 == 192 && b1 == 168) || b0 >= 224
      }
      is Inet6Address -> {
        val b = addr.address
        val b0 = b[0].toInt() and 0xFF
        val b1 = b[1].toInt() and 0xFF
        (b0 and 0xFE) == 0xFC || (b0 == 0xFE && (b1 and 0xC0) == 0x80) || b0 == 0xFF
      }
      else -> true
    }
  }

  private fun readCappedUtf8(conn: HttpsURLConnection): String {
    val input = BufferedInputStream(conn.inputStream)
    val out = ByteArrayOutputStream()
    val buf = ByteArray(4096)
    var total = 0
    input.use { stream ->
      while (total < MAX_RESPONSE_BYTES) {
        val toRead = (MAX_RESPONSE_BYTES - total).coerceAtMost(buf.size)
        val read = stream.read(buf, 0, toRead)
        if (read <= 0) break
        out.write(buf, 0, read)
        total += read
      }
    }
    return String(out.toByteArray(), StandardCharsets.UTF_8)
  }
}
