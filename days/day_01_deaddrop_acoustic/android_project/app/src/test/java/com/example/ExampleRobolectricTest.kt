package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.LogDirection
import com.example.data.PacketAuditRepository
import kotlinx.coroutines.runBlocking
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
  fun `read app name from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("DeadDrop Acoustic", appName)
  }

  @Test
  fun `privacy by default keeps logs in memory only until opted in`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = PacketAuditRepository(context)
    repo.wipeAllData()
    assertFalse(repo.isPersistenceEnabled())

    repo.append(
      direction = LogDirection.TX,
      packetType = "TEXT",
      callsign = "NODE-1",
      payload = "EPHEMERAL",
      crcHex = "0x1234",
      crcValid = true,
      hexFrame = "55 55",
    )
    assertEquals(1, repo.entries.value.size)

    // New repository instance without opt-in must NOT load prior session logs from disk
    val freshRepo = PacketAuditRepository(context)
    freshRepo.loadInitial()
    assertFalse(freshRepo.isPersistenceEnabled())
    assertTrue(freshRepo.entries.value.isEmpty())
  }

  @Test
  fun `audit repository persists only after explicit opt in and wipes cleanly`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val repo = PacketAuditRepository(context)
    repo.wipeAllData()
    repo.setPersistenceEnabled(true)

    repo.append(
      direction = LogDirection.TX,
      packetType = "SOS_LOC",
      callsign = "BRAVO-7",
      payload = "BRAVO-7 @37.77490,-122.41940 EVAC",
      crcHex = "0xABCD",
      crcValid = true,
      hexFrame = "55 55 55 55 AA 55",
    )

    val repoReloaded = PacketAuditRepository(context)
    repoReloaded.loadInitial()
    assertTrue(repoReloaded.isPersistenceEnabled())
    val entries = repoReloaded.entries.value
    assertEquals(1, entries.size)
    assertEquals("SOS_LOC", entries[0].packetType)
    assertEquals("0xABCD", entries[0].crcHex)
    assertTrue(entries[0].crcValid)

    repoReloaded.wipeAllData()
    assertFalse(repoReloaded.isPersistenceEnabled())
    assertTrue(repoReloaded.entries.value.isEmpty())
  }
}
