package com.example.dsp

import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sqrt

data class DemodTelemetry(
  val markLevel: Float = 0f,
  val spaceLevel: Float = 0f,
  val syncLocked: Boolean = false,
  val waveformBars: List<Float> = List(48) { 0f },
)

class BfskDemodulator(
  private val onPacketDecoded: (DecodedPacket) -> Unit = {},
  private val onCrcFail: (expected: Int, actual: Int) -> Unit = { _, _ -> },
) {
  companion object {
    const val WINDOW_SIZE = PacketProtocol.SAMPLES_PER_BIT // 147
    const val OVERSAMPLE = 7
    const val STEP_SIZE = WINDOW_SIZE / OVERSAMPLE // 21
    const val WAVEFORM_BARS = 48
    private const val MIN_SIGNAL_ENERGY = 1e-6f
    private const val DUPLICATE_COOLDOWN_SAMPLES = PacketProtocol.SAMPLE_RATE / 4
  }

  private enum class PhaseStage {
    SEARCH_SYNC,
    READ_TYPE,
    READ_LENGTH,
    READ_PAYLOAD,
    READ_CRC_HI,
    READ_CRC_LO,
  }

  private class PhaseDecoder(val phaseIndex: Int) {
    var stage: PhaseStage = PhaseStage.SEARCH_SYNC
    var shiftReg: Int = 0
    var bitCount: Int = 0
    var currentByte: Int = 0
    var typeCode: Int = 0
    var payloadLength: Int = 0
    var payloadIndex: Int = 0
    val payloadBuffer: ByteArray = ByteArray(PacketProtocol.MAX_PAYLOAD_BYTES)
    var crcHi: Int = 0

    fun reset() {
      stage = PhaseStage.SEARCH_SYNC
      shiftReg = 0
      bitCount = 0
      currentByte = 0
      typeCode = 0
      payloadLength = 0
      payloadIndex = 0
      crcHi = 0
    }
  }

  private val coeffMark: Float =
    (2.0 * cos(2.0 * Math.PI * PacketProtocol.FREQ_MARK / PacketProtocol.SAMPLE_RATE)).toFloat()
  private val coeffSpace: Float =
    (2.0 * cos(2.0 * Math.PI * PacketProtocol.FREQ_SPACE / PacketProtocol.SAMPLE_RATE)).toFloat()

  private val ringBuffer = FloatArray(WINDOW_SIZE)
  private var writePos = 0
  private var samplesFilled = 0
  private var samplesInStep = 0
  private var stepPeak = 0f
  private var stepCounter = 0L
  private var totalSamplesProcessed = 0L
  private var lastValidSample = -DUPLICATE_COOLDOWN_SAMPLES.toLong()

  private val phases = Array(OVERSAMPLE) { PhaseDecoder(it) }

  private val waveformRing = FloatArray(WAVEFORM_BARS)
  private var waveformWritePos = 0

  @Volatile private var lastMarkLevel: Float = 0f
  @Volatile private var lastSpaceLevel: Float = 0f

  @Synchronized
  fun reset() {
    ringBuffer.fill(0f)
    writePos = 0
    samplesFilled = 0
    samplesInStep = 0
    stepPeak = 0f
    stepCounter = 0L
    totalSamplesProcessed = 0L
    lastValidSample = -DUPLICATE_COOLDOWN_SAMPLES.toLong()
    phases.forEach { it.reset() }
    waveformRing.fill(0f)
    waveformWritePos = 0
    lastMarkLevel = 0f
    lastSpaceLevel = 0f
  }

  @Synchronized
  fun feedPcmShorts(samples: ShortArray, count: Int = samples.size) {
    val limit = count.coerceAtMost(samples.size)
    for (i in 0 until limit) {
      val normalized = samples[i] / 32768f
      pushSample(normalized)
    }
  }

  private fun pushSample(sample: Float) {
    ringBuffer[writePos] = sample
    writePos++
    if (writePos == WINDOW_SIZE) {
      writePos = 0
    }
    if (samplesFilled < WINDOW_SIZE) {
      samplesFilled++
    }
    totalSamplesProcessed++

    val absSample = abs(sample)
    if (absSample > stepPeak) {
      stepPeak = absSample
    }

    samplesInStep++
    if (samplesInStep >= STEP_SIZE) {
      samplesInStep = 0
      waveformRing[waveformWritePos] = sqrt(stepPeak.coerceIn(0f, 1f))
      waveformWritePos = (waveformWritePos + 1) % WAVEFORM_BARS
      stepPeak = 0f

      if (samplesFilled == WINDOW_SIZE) {
        processGoertzelStep()
      }
      stepCounter++
    }
  }

  private fun processGoertzelStep() {
    val markPower = computeGoertzelPower(coeffMark)
    val spacePower = computeGoertzelPower(coeffSpace)

    // Normalize Goertzel power for UI bars (max theoretical power for unit sine is ~(147/2)^2 = 5402)
    val normFactor = 5402.25f
    lastMarkLevel = sqrt((markPower / normFactor).coerceIn(0f, 1f))
    lastSpaceLevel = sqrt((spacePower / normFactor).coerceIn(0f, 1f))

    val totalPower = markPower + spacePower
    val phaseIdx = (stepCounter % OVERSAMPLE).toInt()
    val decoder = phases[phaseIdx]

    if (totalPower < MIN_SIGNAL_ENERGY) {
      if (decoder.stage != PhaseStage.SEARCH_SYNC) {
        decoder.reset()
      }
      return
    }

    val bit = if (markPower >= spacePower) 1 else 0
    stepPhaseDecoder(decoder, bit)
  }

  private fun computeGoertzelPower(coeff: Float): Float {
    var s0: Float
    var s1 = 0f
    var s2 = 0f
    var idx = writePos // Oldest sample in circular buffer
    for (i in 0 until WINDOW_SIZE) {
      s0 = ringBuffer[idx] + coeff * s1 - s2
      s2 = s1
      s1 = s0
      idx++
      if (idx == WINDOW_SIZE) {
        idx = 0
      }
    }
    val power = s1 * s1 + s2 * s2 - coeff * s1 * s2
    return if (power > 0f) power else 0f
  }

  private fun stepPhaseDecoder(decoder: PhaseDecoder, bit: Int) {
    when (decoder.stage) {
      PhaseStage.SEARCH_SYNC -> {
        decoder.shiftReg = ((decoder.shiftReg shl 1) or bit) and 0xFFFF
        if (decoder.shiftReg == PacketProtocol.SYNC_WORD) {
          decoder.stage = PhaseStage.READ_TYPE
          decoder.bitCount = 0
          decoder.currentByte = 0
        }
      }

      PhaseStage.READ_TYPE -> {
        decoder.currentByte = (decoder.currentByte shl 1) or bit
        decoder.bitCount++
        if (decoder.bitCount == 8) {
          val type = PacketType.fromCode(decoder.currentByte)
          if (type == null) {
            decoder.reset()
          } else {
            decoder.typeCode = decoder.currentByte
            decoder.stage = PhaseStage.READ_LENGTH
            decoder.bitCount = 0
            decoder.currentByte = 0
          }
        }
      }

      PhaseStage.READ_LENGTH -> {
        decoder.currentByte = (decoder.currentByte shl 1) or bit
        decoder.bitCount++
        if (decoder.bitCount == 8) {
          val len = decoder.currentByte
          if (len !in 1..PacketProtocol.MAX_PAYLOAD_BYTES) {
            decoder.reset()
          } else {
            decoder.payloadLength = len
            decoder.payloadIndex = 0
            decoder.stage = PhaseStage.READ_PAYLOAD
            decoder.bitCount = 0
            decoder.currentByte = 0
          }
        }
      }

      PhaseStage.READ_PAYLOAD -> {
        decoder.currentByte = (decoder.currentByte shl 1) or bit
        decoder.bitCount++
        if (decoder.bitCount == 8) {
          decoder.payloadBuffer[decoder.payloadIndex++] = decoder.currentByte.toByte()
          decoder.bitCount = 0
          decoder.currentByte = 0
          if (decoder.payloadIndex >= decoder.payloadLength) {
            decoder.stage = PhaseStage.READ_CRC_HI
          }
        }
      }

      PhaseStage.READ_CRC_HI -> {
        decoder.currentByte = (decoder.currentByte shl 1) or bit
        decoder.bitCount++
        if (decoder.bitCount == 8) {
          decoder.crcHi = decoder.currentByte
          decoder.stage = PhaseStage.READ_CRC_LO
          decoder.bitCount = 0
          decoder.currentByte = 0
        }
      }

      PhaseStage.READ_CRC_LO -> {
        decoder.currentByte = (decoder.currentByte shl 1) or bit
        decoder.bitCount++
        if (decoder.bitCount == 8) {
          val crcActual = (decoder.crcHi shl 8) or decoder.currentByte
          val len = decoder.payloadLength
          val payloadBytes = decoder.payloadBuffer.copyOf(len)

          val crcInput = ByteArray(2 + len)
          crcInput[0] = decoder.typeCode.toByte()
          crcInput[1] = len.toByte()
          System.arraycopy(payloadBytes, 0, crcInput, 2, len)
          val crcExpected = PacketProtocol.crc16Ccitt(crcInput)

          val packetType = PacketType.fromCode(decoder.typeCode) ?: PacketType.TEXT

          if (crcActual == crcExpected) {
            val isDuplicate =
              (totalSamplesProcessed - lastValidSample) < DUPLICATE_COOLDOWN_SAMPLES
            if (!isDuplicate) {
              lastValidSample = totalSamplesProcessed
              val frameBytes = reconstructFrame(decoder.typeCode, payloadBytes, crcActual)
              val packet =
                DecodedPacket(
                  type = packetType,
                  payloadText = PacketProtocol.sanitizeText(String(payloadBytes, StandardCharsets.UTF_8), 200),
                  payloadBytes = payloadBytes,
                  crc16Expected = crcExpected,
                  crc16Actual = crcActual,
                  crcValid = true,
                  frameBytes = frameBytes,
                  hexFrame = PacketProtocol.bytesToHex(frameBytes),
                  matchedPhase = decoder.phaseIndex,
                )
              phases.forEach { it.reset() }
              onPacketDecoded(packet)
            } else {
              decoder.reset()
            }
          } else {
            val recentlySucceeded =
              (totalSamplesProcessed - lastValidSample) < DUPLICATE_COOLDOWN_SAMPLES
            decoder.reset()
            if (!recentlySucceeded) {
              onCrcFail(crcExpected, crcActual)
            }
          }
        }
      }
    }
  }

  private fun reconstructFrame(typeCode: Int, payloadBytes: ByteArray, crc16: Int): ByteArray {
    val totalLen = PacketProtocol.PREAMBLE_COUNT + 2 + 1 + 1 + payloadBytes.size + 2 + 1
    val frame = ByteArray(totalLen)
    var pos = 0
    repeat(PacketProtocol.PREAMBLE_COUNT) { frame[pos++] = PacketProtocol.PREAMBLE_BYTE }
    frame[pos++] = PacketProtocol.SYNC_HI
    frame[pos++] = PacketProtocol.SYNC_LO
    frame[pos++] = typeCode.toByte()
    frame[pos++] = payloadBytes.size.toByte()
    System.arraycopy(payloadBytes, 0, frame, pos, payloadBytes.size)
    pos += payloadBytes.size
    frame[pos++] = ((crc16 ushr 8) and 0xFF).toByte()
    frame[pos++] = (crc16 and 0xFF).toByte()
    frame[pos] = PacketProtocol.POSTAMBLE_BYTE
    return frame
  }

  @Synchronized
  fun snapshotTelemetry(): DemodTelemetry {
    val bars = ArrayList<Float>(WAVEFORM_BARS)
    var idx = waveformWritePos
    for (i in 0 until WAVEFORM_BARS) {
      bars.add(waveformRing[idx])
      idx = (idx + 1) % WAVEFORM_BARS
    }
    val locked = phases.any { it.stage != PhaseStage.SEARCH_SYNC }
    return DemodTelemetry(
      markLevel = lastMarkLevel,
      spaceLevel = lastSpaceLevel,
      syncLocked = locked,
      waveformBars = bars,
    )
  }
}
