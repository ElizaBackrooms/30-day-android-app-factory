package com.example

import com.example.dsp.BfskDemodulator
import com.example.dsp.BfskModulator
import com.example.dsp.DecodedPacket
import com.example.dsp.PacketProtocol
import com.example.dsp.PacketType
import com.example.hardware.MorseAlphabet
import com.example.network.SsrfGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeadDropUnitTest {

  @Test
  fun `crc16 ccitt matches standard test vector`() {
    val input = "123456789".toByteArray(Charsets.US_ASCII)
    assertEquals(0x29B1, PacketProtocol.crc16Ccitt(input))
  }

  @Test
  fun `bfsk modulator and 7x goertzel demodulator roundtrip all packet types`() {
    val modulator = BfskModulator()
    val cases =
      listOf(
        Triple("ALPHA-1", PacketType.TEXT, "ALL CLEAR AT RIDGE"),
        Triple("BRAVO-7", PacketType.SOS_LOC, "NEED EVAC"),
        Triple("CHARLIE", PacketType.PING, ""),
      )

    for ((callsign, type, msg) in cases) {
      val coords = if (type == PacketType.SOS_LOC) "37.77490,-122.41940" else ""
      val frame = PacketProtocol.encodeFrame(callsign, type, msg, coords)
      val pcm = modulator.modulateToPcm16(frame.frameBytes)

      val decodedList = mutableListOf<DecodedPacket>()
      val demodulator = BfskDemodulator(onPacketDecoded = { decodedList.add(it) })

      // Feed with an odd leading offset to exercise 7x oversampled phase selection
      val leadJitter = ShortArray(37) { 0 }
      demodulator.feedPcmShorts(leadJitter)
      demodulator.feedPcmShorts(pcm)
      val tailZeros = ShortArray(300) { 0 }
      demodulator.feedPcmShorts(tailZeros)

      assertEquals("Expected exactly 1 decoded packet for $type", 1, decodedList.size)
      val decoded = decodedList.first()
      assertNotNull(decoded)
      assertTrue(decoded.crcValid)
      assertEquals(type, decoded.type)
      assertEquals(frame.crc16, decoded.crc16Actual)
      assertEquals(frame.payloadText, decoded.payloadText)
    }
  }

  @Test
  fun `utf8 payload truncates safely to max 120 bytes`() {
    val longMsg = "ALERT ".repeat(40)
    val frame = PacketProtocol.encodeFrame("NODE-9", PacketType.TEXT, longMsg, "")
    assertTrue(frame.payloadBytes.size <= PacketProtocol.MAX_PAYLOAD_BYTES)
    assertTrue(frame.payloadBytes.isNotEmpty())
  }

  @Test
  fun `morse alphabet covers A-Z and 0-9`() {
    assertEquals("... --- ...", MorseAlphabet.toMorsePreview("SOS"))
    for (c in 'A'..'Z') {
      assertTrue("Missing Morse for $c", MorseAlphabet.CODE_MAP.containsKey(c))
    }
    for (d in '0'..'9') {
      assertTrue("Missing Morse for $d", MorseAlphabet.CODE_MAP.containsKey(d))
    }
  }

  @Test
  fun `ssrf guard blocks insecure schemes, loopback, private ranges, and internal hosts`() {
    val insecureHttpUrl = "http" + "://1.1.1.1/hook"
    val blockedTargets =
      listOf(
        insecureHttpUrl,
        "https://localhost/hook",
        "https://relay.local/hook",
        "https://metadata.internal/latest",
        "https://127.0.0.1/hook",
        "https://10.0.0.5/hook",
        "https://172.16.10.1/hook",
        "https://192.168.1.50/hook",
        "https://100.64.0.1/hook",
        "https://169.254.169.254/latest",
        "https://[::1]/hook",
        "https://[fe80::1]/hook",
        "https://[fc00::1]/hook",
        "https://[::ffff:127.0.0.1]/hook",
        "https://user:pass@1.1.1.1/hook",
      )
    for (url in blockedTargets) {
      assertFalse("Expected SSRF block for $url", SsrfGuard.validateHttpsUrl(url).isSuccess)
    }
    assertTrue(SsrfGuard.validateHttpsUrl("https://1.1.1.1/relay").isSuccess)
  }

  @Test
  fun `sanitizeText strips control characters and newlines`() {
    val dirty = "OK\n[FAKE] TEST\r\t\u0007MSG"
    assertEquals("OK [FAKE] TEST MSG", PacketProtocol.sanitizeText(dirty, 100))
  }
}
