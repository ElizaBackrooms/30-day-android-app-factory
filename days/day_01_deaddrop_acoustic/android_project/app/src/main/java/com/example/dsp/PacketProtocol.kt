package com.example.dsp

import java.nio.charset.StandardCharsets

enum class PacketType(val code: Byte, val label: String) {
  TEXT(0x01, "TEXT"),
  SOS_LOC(0x02, "SOS_LOC"),
  PING(0x03, "PING");

  companion object {
    fun fromCode(code: Int): PacketType? = entries.firstOrNull { (it.code.toInt() and 0xFF) == code }
  }
}

data class EncodedFrame(
  val type: PacketType,
  val payloadText: String,
  val payloadBytes: ByteArray,
  val crc16: Int,
  val frameBytes: ByteArray,
  val durationMs: Int,
  val hexPreview: String,
) {
  val crcHex: String
    get() = "0x%04X".format(crc16)
}

data class DecodedPacket(
  val type: PacketType,
  val payloadText: String,
  val payloadBytes: ByteArray,
  val crc16Expected: Int,
  val crc16Actual: Int,
  val crcValid: Boolean,
  val frameBytes: ByteArray,
  val hexFrame: String,
  val matchedPhase: Int,
  val timestampMs: Long = System.currentTimeMillis(),
) {
  val crcHex: String
    get() = "0x%04X".format(crc16Actual)
}

object PacketProtocol {
  const val SAMPLE_RATE = 44100
  const val BAUD_RATE = 300
  const val SAMPLES_PER_BIT = SAMPLE_RATE / BAUD_RATE // 147 samples/bit
  const val FREQ_MARK = 1200.0
  const val FREQ_SPACE = 2200.0

  const val PREAMBLE_BYTE: Byte = 0x55
  const val PREAMBLE_COUNT = 4
  const val SYNC_HI: Byte = 0xAA.toByte()
  const val SYNC_LO: Byte = 0x55
  const val SYNC_WORD = 0xAA55
  const val POSTAMBLE_BYTE: Byte = 0xFF.toByte()
  const val MAX_PAYLOAD_BYTES = 120

  fun crc16Ccitt(data: ByteArray, offset: Int = 0, length: Int = data.size - offset): Int {
    var crc = 0xFFFF
    val end = (offset + length).coerceAtMost(data.size)
    for (i in offset until end) {
      crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
      for (bit in 0 until 8) {
        crc =
          if ((crc and 0x8000) != 0) {
            ((crc shl 1) xor 0x1021) and 0xFFFF
          } else {
            (crc shl 1) and 0xFFFF
          }
      }
    }
    return crc and 0xFFFF
  }

  fun sanitizeText(input: String, maxChars: Int = 240): String {
    val sb = StringBuilder(input.length.coerceAtMost(maxChars))
    for (ch in input) {
      if (sb.length >= maxChars) break
      val isBreak = ch == '\r' || ch == '\n' || ch == '\t'
      val c = if (isBreak) ' ' else ch
      if (c.code >= 0x20 && c.code != 0x7F) {
        if (c == ' ' && (sb.isEmpty() || sb.last() == ' ')) continue
        sb.append(c)
      }
    }
    return sb.toString().trim()
  }

  fun buildPayloadString(
    callsign: String,
    type: PacketType,
    message: String,
    coordinates: String,
  ): String {
    val cleanCall = sanitizeText(callsign, 12).ifEmpty { "NODE" }
    val cleanMsg = sanitizeText(message, 200)
    val cleanCoords = sanitizeText(coordinates, 48)

    val raw =
      when (type) {
        PacketType.SOS_LOC -> {
          val locPart = if (cleanCoords.isNotEmpty()) "@$cleanCoords" else "@NO_FIX"
          val msgPart = if (cleanMsg.isNotEmpty()) " $cleanMsg" else " SOS"
          "$cleanCall $locPart$msgPart"
        }
        PacketType.PING -> {
          val locPart = if (cleanCoords.isNotEmpty()) " @$cleanCoords" else ""
          val msgPart = if (cleanMsg.isNotEmpty()) ": $cleanMsg" else " PING"
          "$cleanCall$locPart$msgPart"
        }
        PacketType.TEXT -> {
          val locPart = if (cleanCoords.isNotEmpty()) " [$cleanCoords]" else ""
          val msgPart = if (cleanMsg.isNotEmpty()) ": $cleanMsg" else ": READY"
          "$cleanCall$locPart$msgPart"
        }
      }
    return truncateUtf8String(raw, MAX_PAYLOAD_BYTES)
  }

  fun truncateUtf8String(text: String, maxBytes: Int = MAX_PAYLOAD_BYTES): String {
    if (maxBytes <= 0 || text.isEmpty()) return ""
    var bytesUsed = 0
    var i = 0
    while (i < text.length) {
      val cp = text.codePointAt(i)
      val cpBytes =
        when {
          cp <= 0x7F -> 1
          cp <= 0x7FF -> 2
          cp <= 0xFFFF -> 3
          else -> 4
        }
      if (bytesUsed + cpBytes > maxBytes) break
      bytesUsed += cpBytes
      i += Character.charCount(cp)
    }
    return text.substring(0, i)
  }

  fun encodeFrame(
    callsign: String,
    type: PacketType,
    message: String,
    coordinates: String = "",
  ): EncodedFrame {
    val payloadText = buildPayloadString(callsign, type, message, coordinates)
    var payloadBytes = payloadText.toByteArray(StandardCharsets.UTF_8)
    if (payloadBytes.isEmpty()) {
      payloadBytes = "PING".toByteArray(StandardCharsets.UTF_8)
    }
    if (payloadBytes.size > MAX_PAYLOAD_BYTES) {
      payloadBytes = payloadBytes.copyOf(MAX_PAYLOAD_BYTES)
    }

    val payloadLen = payloadBytes.size
    // CRC-16-CCITT computed over Type (1) + Length (1) + Payload (N)
    val crcInput = ByteArray(2 + payloadLen)
    crcInput[0] = type.code
    crcInput[1] = payloadLen.toByte()
    System.arraycopy(payloadBytes, 0, crcInput, 2, payloadLen)
    val crc16 = crc16Ccitt(crcInput)

    // Total frame = 4 preamble + 2 sync + 1 type + 1 len + N payload + 2 CRC + 1 postamble
    val totalLen = PREAMBLE_COUNT + 2 + 1 + 1 + payloadLen + 2 + 1
    val frame = ByteArray(totalLen)
    var pos = 0
    repeat(PREAMBLE_COUNT) { frame[pos++] = PREAMBLE_BYTE }
    frame[pos++] = SYNC_HI
    frame[pos++] = SYNC_LO
    frame[pos++] = type.code
    frame[pos++] = payloadLen.toByte()
    System.arraycopy(payloadBytes, 0, frame, pos, payloadLen)
    pos += payloadLen
    frame[pos++] = ((crc16 ushr 8) and 0xFF).toByte()
    frame[pos++] = (crc16 and 0xFF).toByte()
    frame[pos] = POSTAMBLE_BYTE

    val totalBits = totalLen * 8
    val durationMs = ((totalBits * 1000.0) / BAUD_RATE).toInt()

    return EncodedFrame(
      type = type,
      payloadText = String(payloadBytes, StandardCharsets.UTF_8),
      payloadBytes = payloadBytes,
      crc16 = crc16,
      frameBytes = frame,
      durationMs = durationMs,
      hexPreview = bytesToHex(frame),
    )
  }

  fun bytesToHex(bytes: ByteArray): String =
    bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}
