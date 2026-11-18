package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.StormGlassRepository
import com.example.domain.ZambrettiEngine
import com.example.model.PressureReading
import com.example.model.PressureTrend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @Test
  fun `app name matches StormGlass Local`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("StormGlass Local", appName)
  }

  @Test
  fun `privacy by default stores nothing on disk until opted in`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = StormGlassRepository(context)
    repo.wipeAllData()
    assertFalse(repo.isPersistenceEnabled())

    val now = 1_700_000_000_000L
    val savedInMem = repo.saveReadings(listOf(PressureReading(timestamp = now, pressureHpa = 1012.0f)))
    assertEquals(1, savedInMem.size)

    // Without opt-in, nothing is saved to SharedPreferences
    val reloaded = StormGlassRepository(context)
    assertFalse(reloaded.isPersistenceEnabled())
    assertTrue(reloaded.loadReadings().isEmpty())
  }

  @Test
  fun `repository persists up to 100 readings when opted in and wipes cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = StormGlassRepository(context)
    repo.wipeAllData()
    repo.setPersistenceEnabled(true)

    val now = 1_700_000_000_000L
    val many =
      (0 until 120).map { i ->
        PressureReading(
          timestamp = now + i * 60_000L,
          pressureHpa = 1013.0f,
          isManual = i % 2 == 0,
        )
      }
    val saved = repo.saveReadings(many)
    assertEquals(100, saved.size)
    assertEquals(100, repo.loadReadings().size)

    val demo = repo.generate24hSquallDemoData(nowMs = now)
    assertFalse(demo.isEmpty())
    val (deltaP, complete) = ZambrettiEngine.calculateDeltaP(demo)
    assertTrue(complete)
    assertTrue((deltaP ?: 0f) <= -2.0f)
    assertTrue(ZambrettiEngine.isSquallWarning(deltaP, -2.0f))
    assertEquals(PressureTrend.FALLING, ZambrettiEngine.classifyTrend(deltaP))

    repo.wipeAllData()
    assertFalse(repo.isPersistenceEnabled())
    assertTrue(repo.loadReadings().isEmpty())
  }

  @Test
  fun `csv export neutralizes formula injection prefixes`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = StormGlassRepository(context)
    val csv =
      repo.buildCsvExport(
        readings = listOf(PressureReading(timestamp = 1_700_000_000_000L, pressureHpa = 1010.0f, note = "=CMD()")),
        elevationMeters = 0f,
      )
    assertTrue(csv.contains("'=CMD()"))
  }
}
