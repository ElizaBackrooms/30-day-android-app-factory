package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AuditLogEntry
import com.example.data.LogDirection
import com.example.data.PacketAuditRepository
import com.example.dsp.AudioRxController
import com.example.dsp.BfskDemodulator
import com.example.dsp.BfskModulator
import com.example.dsp.DecodedPacket
import com.example.dsp.DemodTelemetry
import com.example.dsp.EncodedFrame
import com.example.dsp.PacketProtocol
import com.example.dsp.PacketType
import com.example.hardware.LocationFetchResult
import com.example.hardware.LocationHelper
import com.example.hardware.TorchMorseController
import com.example.network.OnlineRelayClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DeadDropTab {
  MODEM_TX,
  DECODER_RX,
  BEACON_LOG,
}

enum class RxSyncStatus(val label: String) {
  STANDBY("READY"),
  SCANNING("LISTENING"),
  SYNC_LOCK("SIGNAL LOCKED"),
  CRC_PASS("VERIFIED OK"),
  CRC_FAIL("CHECK FAILED"),
}

data class LoopbackTestResult(
  val passed: Boolean,
  val expectedCrcHex: String,
  val decodedCrcHex: String,
  val decodedPayload: String,
  val sampleCount: Int,
  val matchedPhase: Int,
  val elapsedMs: Long,
  val timestampMs: Long = System.currentTimeMillis(),
)

data class DeadDropUiState(
  val selectedTab: DeadDropTab = DeadDropTab.MODEM_TX,
  val callsign: String = "NODE-1",
  val packetType: PacketType = PacketType.TEXT,
  val message: String = "HELLO TEST",
  val coordinates: String = "",
  val showManualCoords: Boolean = false,
  val isFetchingGps: Boolean = false,
  val gpsStatusNote: String? = null,
  val encodedFrame: EncodedFrame =
    PacketProtocol.encodeFrame("NODE-1", PacketType.TEXT, "HELLO TEST", ""),
  val isTransmitting: Boolean = false,
  val txProgress: Float = 0f,
  val loopbackResult: LoopbackTestResult? = null,
  val isRxListening: Boolean = false,
  val rxSyncStatus: RxSyncStatus = RxSyncStatus.STANDBY,
  val rxTelemetry: DemodTelemetry = DemodTelemetry(),
  val decodedPackets: List<DecodedPacket> = emptyList(),
  val rxValidCount: Int = 0,
  val rxCrcFailCount: Int = 0,
  val rxError: String? = null,
  val beaconMessage: String = "SOS",
  val beaconWpm: Int = 12,
  val beaconLoop: Boolean = false,
  val isBeaconRunning: Boolean = false,
  val isStrobeOn: Boolean = false,
  val hasFlashHardware: Boolean = false,
  val activeBeaconCharIndex: Int = -1,
  val onlineRelayEnabled: Boolean = false,
  val onlineRelayUrl: String = "",
  val isOnlineBusy: Boolean = false,
  val onlineStatusNote: String? = null,
  val onlineInboxMessages: List<String> = emptyList(),
  val snackbarMessage: String? = null,
) {
  companion object {
    const val PRIVACY_POLICY_URL =
      "https://github.com/ElizaBackrooms/30-day-android-app-factory/blob/main/PRIVACY_POLICY.md"
  }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
  private val repository = PacketAuditRepository(application)
  private val modulator = BfskModulator()
  private val torchController = TorchMorseController(application)
  private val locationHelper = LocationHelper(application)

  private val _uiState =
    MutableStateFlow(
      DeadDropUiState(
        hasFlashHardware = torchController.hasFlashHardware,
      )
    )
  val uiState: StateFlow<DeadDropUiState> = _uiState.asStateFlow()

  val auditEntries: StateFlow<List<AuditLogEntry>> = repository.entries
  val persistHistoryOptIn: StateFlow<Boolean> = repository.persistenceEnabled

  private var txJob: Job? = null
  private var beaconJob: Job? = null
  private var statusResetJob: Job? = null

  private val rxController =
    AudioRxController(
      onTelemetry = { telemetry ->
        _uiState.update { state ->
          val nextStatus =
            when {
              !state.isRxListening -> RxSyncStatus.STANDBY
              telemetry.syncLocked -> RxSyncStatus.SYNC_LOCK
              state.rxSyncStatus == RxSyncStatus.CRC_PASS ||
                state.rxSyncStatus == RxSyncStatus.CRC_FAIL -> state.rxSyncStatus
              else -> RxSyncStatus.SCANNING
            }
          state.copy(rxTelemetry = telemetry, rxSyncStatus = nextStatus)
        }
      },
      onPacketDecoded = { packet ->
        handleDecodedRxPacket(packet)
      },
      onCrcFail = { _, _ ->
        _uiState.update { state ->
          state.copy(
            rxSyncStatus = RxSyncStatus.CRC_FAIL,
            rxCrcFailCount = state.rxCrcFailCount + 1,
          )
        }
        scheduleSyncBadgeReset()
      },
      onError = { err ->
        _uiState.update { state ->
          state.copy(
            isRxListening = false,
            rxSyncStatus = RxSyncStatus.STANDBY,
            rxError = err,
          )
        }
      },
    )

  init {
    viewModelScope.launch { repository.loadInitial() }
  }

  fun selectTab(tab: DeadDropTab) {
    _uiState.update { it.copy(selectedTab = tab) }
  }

  fun updateCallsign(value: String) {
    val sanitized = value.take(12).uppercase()
    _uiState.update { state ->
      state.copy(
        callsign = sanitized,
        encodedFrame =
          PacketProtocol.encodeFrame(sanitized, state.packetType, state.message, state.coordinates),
      )
    }
  }

  fun updatePacketType(type: PacketType) {
    _uiState.update { state ->
      val shouldShowCoords =
        state.showManualCoords || type == PacketType.SOS_LOC || state.coordinates.isNotEmpty()
      state.copy(
        packetType = type,
        showManualCoords = shouldShowCoords,
        encodedFrame =
          PacketProtocol.encodeFrame(state.callsign, type, state.message, state.coordinates),
      )
    }
  }

  fun updateMessage(value: String) {
    val bounded = value.take(240)
    _uiState.update { state ->
      state.copy(
        message = bounded,
        encodedFrame =
          PacketProtocol.encodeFrame(state.callsign, state.packetType, bounded, state.coordinates),
      )
    }
  }

  fun updateCoordinates(value: String) {
    val bounded = value.take(48)
    _uiState.update { state ->
      state.copy(
        coordinates = bounded,
        showManualCoords = true,
        encodedFrame =
          PacketProtocol.encodeFrame(state.callsign, state.packetType, state.message, bounded),
      )
    }
  }

  fun setPersistHistoryOptIn(enabled: Boolean) {
    viewModelScope.launch {
      repository.setPersistenceEnabled(enabled)
      showSnackbar(
        if (enabled) "History saving ON (stored only on this phone)"
        else "History saving OFF (in-memory only until closed)"
      )
    }
  }

  fun setOnlineRelayEnabled(enabled: Boolean) {
    _uiState.update { state ->
      state.copy(
        onlineRelayEnabled = enabled,
        isOnlineBusy = false,
        onlineStatusNote =
          if (enabled) "Ready. Uses HTTPS only when you tap Send or Check."
          else "Offline mode — zero internet traffic allowed.",
      )
    }
  }

  fun updateOnlineRelayUrl(url: String) {
    _uiState.update { it.copy(onlineRelayUrl = url.take(512)) }
  }

  fun generateRandomOnlineTopic() {
    val url = OnlineRelayClient.generateRandomTopicUrl()
    _uiState.update {
      it.copy(
        onlineRelayUrl = url,
        onlineStatusNote = "Created private random topic URL. Share only with trusted recipients.",
      )
    }
  }

  fun sendOnlineCopy() {
    val snapshot = _uiState.value
    if (!snapshot.onlineRelayEnabled || snapshot.isOnlineBusy) {
      if (!snapshot.onlineRelayEnabled) {
        showSnackbar("Turn ON Optional Online Relay first")
      }
      return
    }
    val frame = snapshot.encodedFrame
    val targetUrl = snapshot.onlineRelayUrl
    viewModelScope.launch {
      _uiState.update { it.copy(isOnlineBusy = true, onlineStatusNote = "Sending via HTTPS…") }
      val res =
        OnlineRelayClient.sendPacketOnline(
          rawUrl = targetUrl,
          callsign = snapshot.callsign.ifEmpty { "NODE" },
          packetType = frame.type.label,
          payloadText = frame.payloadText,
          crcHex = frame.crcHex,
        )
      res.fold(
        onSuccess = { msg ->
          repository.append(
            direction = LogDirection.TX,
            packetType = "WEB_${frame.type.label}",
            callsign = snapshot.callsign.ifEmpty { "NODE" },
            payload = frame.payloadText,
            crcHex = frame.crcHex,
            crcValid = true,
            hexFrame = "",
          )
          _uiState.update { it.copy(isOnlineBusy = false, onlineStatusNote = msg) }
          showSnackbar(msg)
        },
        onFailure = { err ->
          val reason = err.message ?: "Online send blocked"
          _uiState.update { it.copy(isOnlineBusy = false, onlineStatusNote = "Blocked/Failed: $reason") }
          showSnackbar(reason)
        },
      )
    }
  }

  fun checkOnlineInbox() {
    val snapshot = _uiState.value
    if (!snapshot.onlineRelayEnabled || snapshot.isOnlineBusy) {
      if (!snapshot.onlineRelayEnabled) {
        showSnackbar("Turn ON Optional Online Relay first")
      }
      return
    }
    val targetUrl = snapshot.onlineRelayUrl
    viewModelScope.launch {
      _uiState.update { it.copy(isOnlineBusy = true, onlineStatusNote = "Checking HTTPS inbox…") }
      val res = OnlineRelayClient.fetchOnlineMessages(targetUrl)
      res.fold(
        onSuccess = { msgs ->
          _uiState.update {
            it.copy(
              isOnlineBusy = false,
              onlineInboxMessages = msgs,
              onlineStatusNote =
                if (msgs.isEmpty()) "No online messages yet."
                else "Loaded ${msgs.size} recent message(s).",
            )
          }
        },
        onFailure = { err ->
          val reason = err.message ?: "Inbox check blocked"
          _uiState.update { it.copy(isOnlineBusy = false, onlineStatusNote = "Blocked/Failed: $reason") }
          showSnackbar(reason)
        },
      )
    }
  }

  fun wipeAllLocalData() {
    viewModelScope.launch {
      repository.wipeAllData()
      _uiState.update { state ->
        state.copy(
          callsign = "NODE-1",
          packetType = PacketType.TEXT,
          message = "",
          coordinates = "",
          showManualCoords = false,
          gpsStatusNote = null,
          encodedFrame = PacketProtocol.encodeFrame("NODE-1", PacketType.TEXT, "", ""),
          loopbackResult = null,
          decodedPackets = emptyList(),
          rxValidCount = 0,
          rxCrcFailCount = 0,
          rxError = null,
          beaconMessage = "SOS",
          beaconLoop = false,
          onlineRelayEnabled = false,
          onlineRelayUrl = "",
          isOnlineBusy = false,
          onlineStatusNote = null,
          onlineInboxMessages = emptyList(),
          snackbarMessage = "Wiped all local logs, coordinates, and online links",
        )
      }
    }
  }

  fun toggleManualCoords() {
    _uiState.update { it.copy(showManualCoords = !it.showManualCoords) }
  }

  fun fetchGpsCoordinates() {
    if (_uiState.value.isFetchingGps) return
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          isFetchingGps = true,
          showManualCoords = true,
          gpsStatusNote = "Querying LocationManager…",
        )
      }
      when (val result = locationHelper.fetchCurrentCoordinates()) {
        is LocationFetchResult.Success -> {
          _uiState.update { state ->
            state.copy(
              isFetchingGps = false,
              coordinates = result.coordinates,
              gpsStatusNote = "FIX: ${result.provider.uppercase()}",
              encodedFrame =
                PacketProtocol.encodeFrame(
                  state.callsign,
                  state.packetType,
                  state.message,
                  result.coordinates,
                ),
            )
          }
        }
        is LocationFetchResult.PermissionDenied -> {
          _uiState.update {
            it.copy(
              isFetchingGps = false,
              showManualCoords = true,
              gpsStatusNote = "Location denied — enter Grid/Coords manually",
            )
          }
        }
        is LocationFetchResult.Unavailable -> {
          _uiState.update {
            it.copy(
              isFetchingGps = false,
              showManualCoords = true,
              gpsStatusNote = result.reason,
            )
          }
        }
      }
    }
  }

  fun onLocationPermissionResult(granted: Boolean) {
    if (granted) {
      fetchGpsCoordinates()
    } else {
      _uiState.update {
        it.copy(
          showManualCoords = true,
          gpsStatusNote = "Location permission denied — manual Grid/Coords enabled",
        )
      }
    }
  }

  fun transmitAudio() {
    if (_uiState.value.isTransmitting) {
      stopTransmission()
      return
    }
    val snapshot = _uiState.value
    val frame = snapshot.encodedFrame

    txJob =
      viewModelScope.launch {
        _uiState.update { it.copy(isTransmitting = true, txProgress = 0f) }
        try {
          val pcm = withContext(Dispatchers.Default) { modulator.modulateToPcm16(frame.frameBytes) }
          modulator.playPcmStatic(pcm) { progress ->
            _uiState.update { it.copy(txProgress = progress) }
          }
          repository.append(
            direction = LogDirection.TX,
            packetType = frame.type.label,
            callsign = snapshot.callsign.ifEmpty { "NODE" },
            payload = frame.payloadText,
            crcHex = frame.crcHex,
            crcValid = true,
            hexFrame = frame.hexPreview,
          )
          showSnackbar("TX COMPLETE · ${frame.frameBytes.size}B · ${frame.crcHex}")
        } catch (t: Throwable) {
          if (t !is kotlinx.coroutines.CancellationException) {
            showSnackbar("TX ERROR: ${t.message ?: "AudioTrack failure"}")
          }
        } finally {
          _uiState.update { it.copy(isTransmitting = false, txProgress = 0f) }
        }
      }
  }

  fun stopTransmission() {
    txJob?.cancel()
    txJob = null
    _uiState.update { it.copy(isTransmitting = false, txProgress = 0f) }
  }

  fun runLoopbackSelfTest() {
    val snapshot = _uiState.value
    val frame = snapshot.encodedFrame

    viewModelScope.launch {
      val result =
        withContext(Dispatchers.Default) {
          val startNs = System.nanoTime()
          val pcm = modulator.modulateToPcm16(frame.frameBytes)
          var decodedPacket: DecodedPacket? = null
          val demod =
            BfskDemodulator(
              onPacketDecoded = { pkt ->
                if (decodedPacket == null) decodedPacket = pkt
              },
            )
          val chunkSize = 512
          var offset = 0
          while (offset < pcm.size) {
            val len = (pcm.size - offset).coerceAtMost(chunkSize)
            val slice = pcm.copyOfRange(offset, offset + len)
            demod.feedPcmShorts(slice, len)
            offset += len
          }
          val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
          val pkt = decodedPacket
          val passed =
            pkt != null &&
              pkt.crcValid &&
              pkt.crc16Actual == frame.crc16 &&
              pkt.payloadText == frame.payloadText &&
              pkt.type == frame.type

          LoopbackTestResult(
            passed = passed,
            expectedCrcHex = frame.crcHex,
            decodedCrcHex = pkt?.crcHex ?: "NONE",
            decodedPayload = pkt?.payloadText ?: "NO SYNC LOCK",
            sampleCount = pcm.size,
            matchedPhase = pkt?.matchedPhase ?: -1,
            elapsedMs = elapsedMs,
          )
        }

      _uiState.update { it.copy(loopbackResult = result) }

      repository.append(
        direction = LogDirection.LOOPBACK,
        packetType = frame.type.label,
        callsign = snapshot.callsign.ifEmpty { "NODE" },
        payload = result.decodedPayload,
        crcHex = result.decodedCrcHex,
        crcValid = result.passed,
        hexFrame = frame.hexPreview,
      )
    }
  }

  fun toggleRxListening(hasMicPermission: Boolean) {
    if (!hasMicPermission) {
      _uiState.update { it.copy(rxError = "RECORD_AUDIO permission required") }
      return
    }
    if (_uiState.value.isRxListening) {
      stopRxListening()
    } else {
      startRxListening()
    }
  }

  fun startRxListening() {
    if (_uiState.value.isRxListening) return
    _uiState.update {
      it.copy(
        isRxListening = true,
        rxSyncStatus = RxSyncStatus.SCANNING,
        rxError = null,
      )
    }
    rxController.start(viewModelScope)
  }

  fun stopRxListening() {
    rxController.stop()
    _uiState.update {
      it.copy(
        isRxListening = false,
        rxSyncStatus = RxSyncStatus.STANDBY,
        rxTelemetry = DemodTelemetry(),
      )
    }
  }

  private fun handleDecodedRxPacket(packet: DecodedPacket) {
    _uiState.update { state ->
      val updatedList = (listOf(packet) + state.decodedPackets).take(50)
      state.copy(
        decodedPackets = updatedList,
        rxValidCount = state.rxValidCount + 1,
        rxSyncStatus = RxSyncStatus.CRC_PASS,
      )
    }
    scheduleSyncBadgeReset()
    viewModelScope.launch {
      repository.append(
        direction = LogDirection.RX,
        packetType = packet.type.label,
        callsign = "RX",
        payload = packet.payloadText,
        crcHex = packet.crcHex,
        crcValid = packet.crcValid,
        hexFrame = packet.hexFrame,
        timestampMs = packet.timestampMs,
      )
    }
  }

  private fun scheduleSyncBadgeReset() {
    statusResetJob?.cancel()
    statusResetJob =
      viewModelScope.launch {
        delay(1400L)
        _uiState.update { state ->
          if (state.isRxListening && !state.rxTelemetry.syncLocked) {
            state.copy(rxSyncStatus = RxSyncStatus.SCANNING)
          } else {
            state
          }
        }
      }
  }

  fun clearRxFeed() {
    _uiState.update { it.copy(decodedPackets = emptyList(), rxValidCount = 0, rxCrcFailCount = 0) }
  }

  fun updateBeaconMessage(value: String) {
    _uiState.update { it.copy(beaconMessage = value.take(64)) }
  }

  fun updateBeaconWpm(wpm: Int) {
    _uiState.update { it.copy(beaconWpm = wpm.coerceIn(5, 20)) }
  }

  fun setBeaconLoop(loop: Boolean) {
    _uiState.update { it.copy(beaconLoop = loop) }
  }

  fun toggleBeacon() {
    if (_uiState.value.isBeaconRunning) {
      stopBeacon()
      return
    }
    val state = _uiState.value
    val msg = state.beaconMessage.trim().ifEmpty { "SOS" }
    val wpm = state.beaconWpm
    val loop = state.beaconLoop

    beaconJob =
      viewModelScope.launch {
        _uiState.update { it.copy(isBeaconRunning = true, activeBeaconCharIndex = 0) }
        try {
          repository.append(
            direction = LogDirection.BEACON,
            packetType = "MORSE",
            callsign = state.callsign.ifEmpty { "NODE" },
            payload = "$msg (${wpm} WPM${if (loop) " LOOP" else ""})",
            crcHex = "N/A",
            crcValid = true,
            hexFrame = "",
          )
          torchController.transmitMorse(
            text = msg,
            wpm = wpm,
            loop = loop,
            onStrobeChanged = { on -> _uiState.update { it.copy(isStrobeOn = on) } },
            onCharIndexChanged = { idx ->
              _uiState.update { it.copy(activeBeaconCharIndex = idx) }
            },
          )
        } finally {
          torchController.forceOff { on -> _uiState.update { it.copy(isStrobeOn = on) } }
          _uiState.update {
            it.copy(
              isBeaconRunning = false,
              isStrobeOn = false,
              activeBeaconCharIndex = -1,
            )
          }
        }
      }
  }

  fun stopBeacon() {
    beaconJob?.cancel()
    beaconJob = null
    torchController.forceOff { on -> _uiState.update { it.copy(isStrobeOn = on) } }
    _uiState.update {
      it.copy(
        isBeaconRunning = false,
        isStrobeOn = false,
        activeBeaconCharIndex = -1,
      )
    }
  }

  fun exportAuditLogText(): String = repository.exportFormattedLog()

  fun clearAuditLog() {
    viewModelScope.launch {
      repository.clear()
      showSnackbar("Audit log cleared")
    }
  }

  fun showSnackbar(message: String) {
    _uiState.update { it.copy(snackbarMessage = message) }
  }

  fun consumeSnackbar() {
    _uiState.update { it.copy(snackbarMessage = null) }
  }

  override fun onCleared() {
    super.onCleared()
    txJob?.cancel()
    beaconJob?.cancel()
    rxController.stop()
    torchController.forceOff()
  }
}
