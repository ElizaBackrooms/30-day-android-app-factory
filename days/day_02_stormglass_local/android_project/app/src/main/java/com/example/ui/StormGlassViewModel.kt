package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SquallTonePlayer
import com.example.data.StormGlassRepository
import com.example.domain.ZambrettiEngine
import com.example.model.AppTab
import com.example.model.PressureReading
import com.example.model.PressureUnit
import com.example.model.ReadinessTask
import com.example.model.TimeWindow
import com.example.model.WindDirection
import com.example.model.ZambrettiForecast
import com.example.network.OnlineWeatherClient
import com.example.network.OnlineWeatherSnapshot
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StormGlassUiState(
  val activeTab: AppTab = AppTab.COCKPIT,
  val hasHardwareSensor: Boolean = false,
  val liveSensorPressureHpa: Float? = null,
  val manualInputPressureHpa: Float = 1013.2f,
  val readings: List<PressureReading> = emptyList(),
  val selectedWindow: TimeWindow = TimeWindow.TWENTY_FOUR_HOURS,
  val unit: PressureUnit = PressureUnit.HPA,
  val elevationMeters: Float = 0f,
  val windDirection: WindDirection = WindDirection.NW,
  val isSummer: Boolean = true,
  val squallThresholdHpa: Float = StormGlassRepository.DEFAULT_SQUALL_THRESHOLD,
  val forecast: ZambrettiForecast? = null,
  val isSquallWarning: Boolean = false,
  val checklist: List<ReadinessTask> = emptyList(),
  val persistLocalData: Boolean = false,
  val onlineWeatherEnabled: Boolean = false,
  val latitudeInput: String = "",
  val longitudeInput: String = "",
  val isFetchingOnline: Boolean = false,
  val onlineStatusNote: String? = null,
  val onlineWeather: OnlineWeatherSnapshot? = null,
  val snackbarMessage: String? = null,
) {
  val currentStationPressureHpa: Float
    get() =
      if (hasHardwareSensor && liveSensorPressureHpa != null) {
        liveSensorPressureHpa
      } else {
        readings.lastOrNull()?.pressureHpa ?: manualInputPressureHpa
      }

  val currentMslpHpa: Float
    get() = ZambrettiEngine.calculateMslp(currentStationPressureHpa, elevationMeters)

  companion object {
    const val PRIVACY_POLICY_URL =
      "https://github.com/ElizaBackrooms/30-day-android-app-factory/blob/main/PRIVACY_POLICY.md"
  }
}

class StormGlassViewModel(application: Application) : AndroidViewModel(application) {

  private val repository = StormGlassRepository(application.applicationContext)
  private var lastSquallAlertState = false

  private val _uiState = MutableStateFlow(loadInitialState())
  val uiState: StateFlow<StormGlassUiState> = _uiState.asStateFlow()

  private fun loadInitialState(): StormGlassUiState {
    val persistEnabled = repository.isPersistenceEnabled()
    val readings = repository.loadReadings()
    val elevation = repository.loadElevationMeters()
    val wind = repository.loadWindDirection()
    val isSummer = repository.loadIsSummer()
    val threshold = repository.loadSquallThreshold()
    val unit = repository.loadPressureUnit()
    val checkedIds = repository.loadCheckedChecklistIds()
    val initialManual = readings.lastOrNull()?.pressureHpa ?: 1013.2f

    val base =
      StormGlassUiState(
        readings = readings,
        manualInputPressureHpa = initialManual,
        elevationMeters = elevation,
        windDirection = wind,
        isSummer = isSummer,
        squallThresholdHpa = threshold,
        unit = unit,
        checklist = buildChecklist(checkedIds),
        persistLocalData = persistEnabled,
      )
    return recalculateForecast(base, playAudioOnNewSquall = false)
  }

  fun setPersistLocalData(enabled: Boolean) {
    repository.setPersistenceEnabled(enabled)
    val state = _uiState.value
    if (enabled) {
      repository.saveReadings(state.readings)
      repository.saveElevationMeters(state.elevationMeters)
      repository.saveWindDirection(state.windDirection)
      repository.saveIsSummer(state.isSummer)
      repository.saveSquallThreshold(state.squallThresholdHpa)
      repository.savePressureUnit(state.unit)
      repository.saveCheckedChecklistIds(
        state.checklist.filter { it.isChecked }.map { it.id }.toSet()
      )
    }
    _uiState.update {
      it.copy(
        persistLocalData = enabled,
        snackbarMessage =
          if (enabled) "Saving logs & settings ON (this phone only)"
          else "Saving OFF (in-memory only until closed)",
      )
    }
  }

  fun setOnlineWeatherEnabled(enabled: Boolean) {
    _uiState.update {
      it.copy(
        onlineWeatherEnabled = enabled,
        isFetchingOnline = false,
        onlineStatusNote =
          if (enabled) "Ready. Enter latitude/longitude and tap Check Live Station."
          else "Offline mode — zero internet traffic allowed.",
      )
    }
  }

  fun updateLatitudeInput(value: String) {
    _uiState.update { it.copy(latitudeInput = value.take(16)) }
  }

  fun updateLongitudeInput(value: String) {
    _uiState.update { it.copy(longitudeInput = value.take(16)) }
  }

  fun fetchLiveStationWeather() {
    val snapshot = _uiState.value
    if (!snapshot.onlineWeatherEnabled || snapshot.isFetchingOnline) {
      if (!snapshot.onlineWeatherEnabled) {
        _uiState.update { it.copy(snackbarMessage = "Turn ON Optional Weather Check first") }
      }
      return
    }
    val lat = snapshot.latitudeInput.trim().toDoubleOrNull()
    val lon = snapshot.longitudeInput.trim().toDoubleOrNull()
    if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
      _uiState.update {
        it.copy(onlineStatusNote = "Enter valid coordinates (Lat -90..90, Lon -180..180), e.g. 41.88 and -87.63")
      }
      return
    }

    viewModelScope.launch {
      _uiState.update { it.copy(isFetchingOnline = true, onlineStatusNote = "Checking Open-Meteo station via HTTPS…") }
      val result = OnlineWeatherClient.fetchCurrentConditions(lat, lon)
      result.fold(
        onSuccess = { wx ->
          _uiState.update {
            it.copy(
              isFetchingOnline = false,
              onlineWeather = wx,
              onlineStatusNote = "Live station checked via Open-Meteo HTTPS.",
            )
          }
        },
        onFailure = { err ->
          val reason = err.message ?: "Could not fetch weather station"
          _uiState.update {
            it.copy(
              isFetchingOnline = false,
              onlineStatusNote = "Failed: $reason",
              snackbarMessage = reason,
            )
          }
        },
      )
    }
  }

  fun applyOnlineWindDirection() {
    val suggested = _uiState.value.onlineWeather?.suggestedWind ?: return
    setWindDirection(suggested)
    _uiState.update { it.copy(snackbarMessage = "Updated wind direction to ${suggested.fullName}") }
  }

  fun wipeAllLocalData() {
    repository.wipeAllData()
    lastSquallAlertState = false
    _uiState.update {
      recalculateForecast(
        StormGlassUiState(
          activeTab = it.activeTab,
          hasHardwareSensor = it.hasHardwareSensor,
          liveSensorPressureHpa = it.liveSensorPressureHpa,
          checklist = buildChecklist(emptySet()),
          snackbarMessage = "All local logs, coordinates, and saved preferences wiped",
        ),
        playAudioOnNewSquall = false,
      )
    }
  }

  fun setHasHardwareSensor(available: Boolean) {
    _uiState.update { it.copy(hasHardwareSensor = available) }
  }

  fun onLiveHardwarePressure(hpa: Float) {
    if (!hpa.isFinite()) return
    val rounded = (hpa * 10f).roundToInt() / 10f
    _uiState.update { state ->
      val updated = state.copy(liveSensorPressureHpa = rounded)
      recalculateForecast(updated, playAudioOnNewSquall = true)
    }
  }

  fun onAutoSensorSample(hpa: Float) {
    if (!hpa.isFinite()) return
    val rounded = (hpa * 10f).roundToInt() / 10f
    val reading =
      PressureReading(
        timestamp = System.currentTimeMillis(),
        pressureHpa = rounded,
        isManual = false,
        note = "Auto sensor sample",
      )
    _uiState.update { state ->
      val saved = repository.appendReading(state.readings, reading)
      val updated = state.copy(readings = saved, liveSensorPressureHpa = rounded)
      recalculateForecast(updated, playAudioOnNewSquall = true)
    }
  }

  fun selectTab(tab: AppTab) {
    _uiState.update { it.copy(activeTab = tab) }
  }

  fun toggleUnit() {
    _uiState.update { state ->
      val nextUnit =
        if (state.unit == PressureUnit.HPA) PressureUnit.INHG else PressureUnit.HPA
      repository.savePressureUnit(nextUnit)
      state.copy(unit = nextUnit)
    }
  }

  fun selectTimeWindow(window: TimeWindow) {
    _uiState.update { it.copy(selectedWindow = window) }
  }

  fun nudgeManualPressure(deltaHpa: Float) {
    _uiState.update { state ->
      val next = ((state.manualInputPressureHpa + deltaHpa) * 10f).roundToInt() / 10f
      state.copy(manualInputPressureHpa = next.coerceIn(850f, 1085f))
    }
  }

  fun setManualPressureExact(hpa: Float) {
    if (!hpa.isFinite()) return
    val clamped = ((hpa.coerceIn(850f, 1085f)) * 10f).roundToInt() / 10f
    _uiState.update { it.copy(manualInputPressureHpa = clamped) }
  }

  fun logManualReading(note: String = "") {
    val currentState = _uiState.value
    val now = System.currentTimeMillis()
    val reading =
      PressureReading(
        timestamp = now,
        pressureHpa = currentState.manualInputPressureHpa,
        isManual = true,
        note = note.take(120).ifBlank { "Manual entry" },
      )
    _uiState.update { state ->
      val saved = repository.appendReading(state.readings, reading)
      val updated =
        state.copy(
          readings = saved,
          snackbarMessage =
            "Logged ${state.unit.formatWithUnit(reading.pressureHpa)} [MANUAL]",
        )
      recalculateForecast(updated, playAudioOnNewSquall = true)
    }
  }

  fun setElevation(meters: Float) {
    val clamped = (meters / 10f).roundToInt() * 10f
    val valid = clamped.coerceIn(0f, 3000f)
    repository.saveElevationMeters(valid)
    _uiState.update { state ->
      recalculateForecast(state.copy(elevationMeters = valid), playAudioOnNewSquall = false)
    }
  }

  fun setWindDirection(direction: WindDirection) {
    repository.saveWindDirection(direction)
    _uiState.update { state ->
      recalculateForecast(state.copy(windDirection = direction), playAudioOnNewSquall = false)
    }
  }

  fun setSeason(isSummer: Boolean) {
    repository.saveIsSummer(isSummer)
    _uiState.update { state ->
      recalculateForecast(state.copy(isSummer = isSummer), playAudioOnNewSquall = false)
    }
  }

  fun setSquallThreshold(thresholdHpa: Float) {
    val rounded = (thresholdHpa * 10f).roundToInt() / 10f
    val valid = rounded.coerceIn(-4.0f, -1.0f)
    repository.saveSquallThreshold(valid)
    _uiState.update { state ->
      recalculateForecast(
        state.copy(squallThresholdHpa = valid),
        playAudioOnNewSquall = true,
      )
    }
  }

  fun testSoundAlert() {
    SquallTonePlayer.playSquallAlertTone()
    _uiState.update {
      it.copy(snackbarMessage = "Played test storm alarm tone")
    }
  }

  fun loadDemoSquallData(navigateToCockpit: Boolean = false) {
    val demo = repository.generate24hSquallDemoData()
    val latestPressure = demo.lastOrNull()?.pressureHpa ?: 1001.3f
    lastSquallAlertState = false
    _uiState.update { state ->
      val updated =
        state.copy(
          readings = demo,
          manualInputPressureHpa = latestPressure,
          activeTab = if (navigateToCockpit) AppTab.COCKPIT else state.activeTab,
          snackbarMessage = "Loaded 24h sample storm drop (-3.8 hPa / 3h)",
        )
      recalculateForecast(updated, playAudioOnNewSquall = true)
    }
  }

  fun deleteReading(timestamp: Long) {
    _uiState.update { state ->
      val updatedReadings = repository.deleteReading(state.readings, timestamp)
      recalculateForecast(
        state.copy(readings = updatedReadings),
        playAudioOnNewSquall = false,
      )
    }
  }

  fun clearHistory() {
    wipeAllLocalData()
  }

  fun buildCsvForClipboard(): String {
    val state = _uiState.value
    return repository.buildCsvExport(state.readings, state.elevationMeters)
  }

  fun notifyCsvCopied() {
    val count = _uiState.value.readings.size
    _uiState.update {
      it.copy(snackbarMessage = "Copied $count readings as CSV")
    }
  }

  fun toggleChecklistItem(id: String) {
    _uiState.update { state ->
      val updatedList =
        state.checklist.map { task ->
          if (task.id == id) task.copy(isChecked = !task.isChecked) else task
        }
      val checkedIds = updatedList.filter { it.isChecked }.map { it.id }.toSet()
      repository.saveCheckedChecklistIds(checkedIds)
      state.copy(checklist = updatedList)
    }
  }

  fun consumeSnackbar() {
    _uiState.update { it.copy(snackbarMessage = null) }
  }

  private fun recalculateForecast(
    state: StormGlassUiState,
    playAudioOnNewSquall: Boolean,
  ): StormGlassUiState {
    val (deltaP, isComplete) = ZambrettiEngine.calculateDeltaP(state.readings)
    val forecast =
      ZambrettiEngine.computeForecast(
        stationPressureHpa = state.currentStationPressureHpa,
        elevationMeters = state.elevationMeters,
        deltaP3h = deltaP,
        isThreeHourComplete = isComplete,
        windDirection = state.windDirection,
        isSummer = state.isSummer,
        squallThresholdHpa = state.squallThresholdHpa,
      )
    val squallActive = ZambrettiEngine.isSquallWarning(deltaP, state.squallThresholdHpa)

    if (playAudioOnNewSquall && squallActive && !lastSquallAlertState) {
      SquallTonePlayer.playSquallAlertTone()
    }
    lastSquallAlertState = squallActive

    return state.copy(forecast = forecast, isSquallWarning = squallActive)
  }

  private fun buildChecklist(checkedIds: Set<String>): List<ReadinessTask> =
    listOf(
      ReadinessTask(
        id = "anchor_guylines",
        title = "Secure outdoor gear & tent stakes",
        detail = "Check tarps, patio items, or guy lines before strong gusts arrive.",
        isChecked = "anchor_guylines" in checkedIds,
        isCritical = true,
      ),
      ReadinessTask(
        id = "comms_watch",
        title = "Check weather radio or alerts",
        detail = "Confirm phone/battery charge and local storm advisories.",
        isChecked = "comms_watch" in checkedIds,
        isCritical = true,
      ),
      ReadinessTask(
        id = "waterproof_kit",
        title = "Keep electronics & essentials dry",
        detail = "Seal battery packs, flashlights, and paper maps inside dry bags.",
        isChecked = "waterproof_kit" in checkedIds,
        isCritical = false,
      ),
      ReadinessTask(
        id = "terrain_egress",
        title = "Move away from high ridges & lone trees",
        detail = "Head to lower grounded shelter and avoid flash-flood gullies.",
        isChecked = "terrain_egress" in checkedIds,
        isCritical = true,
      ),
      ReadinessTask(
        id = "hourly_log",
        title = "Check pressure every 30–60 minutes",
        detail = "Watch for pressure leveling off and rising once the storm passes.",
        isChecked = "hourly_log" in checkedIds,
        isCritical = false,
      ),
    )
}
