package com.example

import com.example.domain.ZambrettiEngine
import com.example.model.PressureReading
import com.example.model.PressureTrend
import com.example.model.ThreatLevel
import com.example.model.WindDirection
import com.example.network.OnlineWeatherClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZambrettiEngineTest {

  @Test
  fun `mslp calculation normalizes elevation accurately`() {
    val seaLevel = ZambrettiEngine.calculateMslp(1013.25f, 0f)
    assertEquals(1013.25f, seaLevel, 0.01f)

    // At 1000m, ~898.7 hPa corresponds to ~1013.25 hPa MSLP
    val at1000m = ZambrettiEngine.calculateMslp(898.75f, 1000f)
    assertEquals(1013.25f, at1000m, 0.5f)
  }

  @Test
  fun `trend classification matches 1_6 hPa thresholds`() {
    assertEquals(PressureTrend.FALLING, ZambrettiEngine.classifyTrend(-1.6f))
    assertEquals(PressureTrend.FALLING, ZambrettiEngine.classifyTrend(-3.2f))
    assertEquals(PressureTrend.STEADY, ZambrettiEngine.classifyTrend(-1.5f))
    assertEquals(PressureTrend.STEADY, ZambrettiEngine.classifyTrend(1.5f))
    assertEquals(PressureTrend.RISING, ZambrettiEngine.classifyTrend(1.6f))
  }

  @Test
  fun `zambretti index stays bounded 1 to 26 and responds to season and wind`() {
    val squallForecast =
      ZambrettiEngine.computeForecast(
        stationPressureHpa = 984.0f,
        elevationMeters = 0f,
        deltaP3h = -3.5f,
        isThreeHourComplete = true,
        windDirection = WindDirection.SW,
        isSummer = false,
        squallThresholdHpa = -2.0f,
      )
    assertTrue(squallForecast.index in 1..26)
    assertEquals(ThreatLevel.SEVERE, squallForecast.threatLevel)

    val fairForecast =
      ZambrettiEngine.computeForecast(
        stationPressureHpa = 1032.0f,
        elevationMeters = 0f,
        deltaP3h = 2.5f,
        isThreeHourComplete = true,
        windDirection = WindDirection.NE,
        isSummer = true,
        squallThresholdHpa = -2.0f,
      )
    assertTrue(fairForecast.index in 1..26)
    assertEquals(ThreatLevel.LOW, fairForecast.threatLevel)
    assertTrue(fairForecast.index > squallForecast.index)
  }

  @Test
  fun `squall warning respects custom threshold`() {
    val now = 1_700_000_000_000L
    val readings =
      listOf(
        PressureReading(timestamp = now - 3 * 3_600_000L, pressureHpa = 1012.0f),
        PressureReading(timestamp = now, pressureHpa = 1009.5f),
      )
    val (deltaP, complete) = ZambrettiEngine.calculateDeltaP(readings)
    assertTrue(complete)
    assertEquals(-2.5f, deltaP ?: 0f, 0.01f)
    assertTrue(ZambrettiEngine.isSquallWarning(deltaP, -2.0f))
    assertFalse(ZambrettiEngine.isSquallWarning(deltaP, -3.0f))
  }

  @Test
  fun `degreesToWindDirection maps compass azimuth accurately`() {
    assertEquals(WindDirection.N, OnlineWeatherClient.degreesToWindDirection(0))
    assertEquals(WindDirection.E, OnlineWeatherClient.degreesToWindDirection(90))
    assertEquals(WindDirection.S, OnlineWeatherClient.degreesToWindDirection(180))
    assertEquals(WindDirection.W, OnlineWeatherClient.degreesToWindDirection(270))
    assertEquals(WindDirection.NW, OnlineWeatherClient.degreesToWindDirection(315))
  }
}
