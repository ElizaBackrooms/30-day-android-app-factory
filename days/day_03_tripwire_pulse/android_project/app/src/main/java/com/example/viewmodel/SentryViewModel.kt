package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.engine.DemoSignalGenerator
import com.example.engine.GeophoneSensorEngine
import com.example.engine.LocalAlertNotifier
import com.example.engine.MicDecibelEngine
import com.example.engine.SeismographMath
import com.example.engine.SeismographSample
import com.example.engine.SensorGateMode
import com.example.model.AlertEvent
import com.example.model.AppTab
import com.example.model.DemoImpulseType
import com.example.model.SentryUiState
import com.example.model.WebhookConfig
import com.example.model.WebhookDeliveryStatus
import com.example.model.WebhookProvider
import com.example.security.EncryptedVault
import com.example.security.UrlValidationResult
import com.example.security.WebhookDispatcher
import com.example.security.WebhookUrlValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class SentryViewModel(
    application: Application,
    private val geophoneEngine: GeophoneSensorEngine = GeophoneSensorEngine(application),
    private val micEngine: MicDecibelEngine = MicDecibelEngine(application),
    private val demoGenerator: DemoSignalGenerator = DemoSignalGenerator(),
    private val notifier: LocalAlertNotifier = LocalAlertNotifier(application),
    private val vault: EncryptedVault = EncryptedVault(application),
    private val webhookDispatcher: WebhookDispatcher = WebhookDispatcher()
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(SentryUiState())
    val uiState: StateFlow<SentryUiState> = _uiState.asStateFlow()

    private var sensorStreamJob: Job? = null
    private var micStreamJob: Job? = null
    private var baselineEma: Float = 0f
    private var lastTriggerMs: Long = 0L
    private var latestMicDb: Float = 32f

    init {
        val hwInfo = geophoneEngine.hardwareInfo()
        val snapshot = vault.loadIfOptedIn()
        val initialValidation = if (snapshot.webhookConfig.webhookUrl.isNotBlank()) {
            WebhookUrlValidator.validateSyntax(snapshot.webhookConfig.webhookUrl)
        } else {
            null
        }

        _uiState.update {
            it.copy(
                hardwareSensorName = hwInfo.sensorDisplayName,
                hasLinearAccelSensor = hwInfo.usingLinearAcceleration,
                micPermissionGranted = micEngine.hasRecordAudioPermission(),
                notificationPermissionGranted = notifier.hasNotificationPermission(),
                saveHistoryOnPhone = snapshot.saveHistoryOnPhone,
                alerts = snapshot.alerts,
                encryptedRecordCountOnDisk = if (snapshot.saveHistoryOnPhone) snapshot.alerts.size else 0,
                webhookConfig = snapshot.webhookConfig,
                urlIsSafe = initialValidation is UrlValidationResult.Valid,
                urlValidationMessage = when (initialValidation) {
                    is UrlValidationResult.Valid -> "HTTPS address looks safe (verified again via DNS before sending)."
                    is UrlValidationResult.Invalid -> initialValidation.reason
                    null -> null
                }
            )
        }

        // Start passive local sensor stream (100% offline, zero network calls)
        restartMotionStream(demoMode = false)
    }

    fun selectTab(tab: AppTab) {
        _uiState.update { it.copy(selectedTab = tab, statusBannerMessage = null) }
    }

    fun toggleArmed() {
        _uiState.update { current ->
            val nextArmed = !current.isArmed
            if (nextArmed) {
                lastTriggerMs = 0L
            }
            current.copy(
                isArmed = nextArmed,
                isAlarmActive = if (!nextArmed) false else current.isAlarmActive,
                peakShakeDeltaMs2 = if (nextArmed) 0f else current.peakShakeDeltaMs2,
                peakSoundDb = if (nextArmed) current.currentSoundDb else current.peakSoundDb,
                statusBannerMessage = if (nextArmed) {
                    "Sentry armed. Place phone flat on a door, bag, or floor."
                } else {
                    "Sentry disarmed."
                }
            )
        }
    }

    fun clearActiveAlarm() {
        notifier.clearNotifications()
        _uiState.update {
            it.copy(
                isAlarmActive = false,
                statusBannerMessage = "Alarm silenced. Sentry status unchanged."
            )
        }
    }

    fun toggleDemoMode(enabled: Boolean) {
        baselineEma = 0f
        _uiState.update {
            it.copy(
                isDemoMode = enabled,
                samples = emptyList(),
                peakShakeDeltaMs2 = 0f,
                statusBannerMessage = if (enabled) {
                    "Test Mode active. Tap a button below to simulate footsteps or knocks."
                } else {
                    "Switched back to live phone hardware sensors."
                }
            )
        }
        restartMotionStream(demoMode = enabled)
        syncMicStream()
    }

    fun triggerDemoImpulse(type: DemoImpulseType) {
        if (!_uiState.value.isDemoMode) {
            toggleDemoMode(true)
        }
        if (!_uiState.value.isArmed) {
            _uiState.update { it.copy(isArmed = true) }
        }
        demoGenerator.injectImpulse(type)
        _uiState.update {
            it.copy(statusBannerMessage = "Simulated ${type.label.lowercase()} sent into live seismograph.")
        }
    }

    fun toggleShowAxisTraces(show: Boolean) {
        _uiState.update { it.copy(showAxisTraces = show) }
    }

    fun updateShakeThreshold(value: Float) {
        _uiState.update { it.copy(shakeThresholdMs2 = value.coerceIn(0.25f, 6.0f)) }
    }

    fun updateSoundThreshold(value: Float) {
        _uiState.update { it.copy(soundThresholdDb = value.coerceIn(40f, 95f)) }
    }

    fun updateGateMode(mode: SensorGateMode) {
        _uiState.update { it.copy(gateMode = mode) }
    }

    fun updateVibrateOnTrigger(enabled: Boolean) {
        _uiState.update { it.copy(vibrateOnTrigger = enabled) }
    }

    fun onMicPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                micPermissionGranted = granted,
                micGateEnabled = granted,
                micPermissionDeniedMessage = if (granted) {
                    null
                } else {
                    "Microphone permission declined — running safely in Shake-Only mode."
                },
                gateMode = if (!granted && it.gateMode != SensorGateMode.VIBRATION_ONLY) {
                    SensorGateMode.VIBRATION_ONLY
                } else if (granted && it.gateMode == SensorGateMode.VIBRATION_ONLY) {
                    SensorGateMode.VIBRATION_AND_SOUND
                } else {
                    it.gateMode
                }
            )
        }
        syncMicStream()
    }

    fun disableMicGate() {
        _uiState.update {
            it.copy(
                micGateEnabled = false,
                gateMode = SensorGateMode.VIBRATION_ONLY
            )
        }
        syncMicStream()
    }

    fun enableMicGateIfPermitted() {
        val granted = micEngine.hasRecordAudioPermission()
        if (granted) {
            _uiState.update {
                it.copy(
                    micPermissionGranted = true,
                    micGateEnabled = true,
                    micPermissionDeniedMessage = null,
                    gateMode = if (it.gateMode == SensorGateMode.VIBRATION_ONLY) {
                        SensorGateMode.VIBRATION_AND_SOUND
                    } else {
                        it.gateMode
                    }
                )
            }
            syncMicStream()
        }
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (granted) {
            notifier.ensureChannel()
        }
        _uiState.update {
            it.copy(
                notificationPermissionGranted = granted,
                notificationsEnabled = granted,
                statusBannerMessage = if (granted) {
                    "Local alert notifications turned on."
                } else {
                    "Notifications declined — alerts will still appear live inside the app."
                }
            )
        }
    }

    fun disableNotifications() {
        _uiState.update { it.copy(notificationsEnabled = false) }
    }

    fun enableNotificationsIfPermitted() {
        if (notifier.hasNotificationPermission()) {
            notifier.ensureChannel()
            _uiState.update {
                it.copy(notificationPermissionGranted = true, notificationsEnabled = true)
            }
        }
    }

    // --- Online Push Alerts (Opt-In Only, OFF by default) ---

    fun setOnlineAlertsOptIn(enabled: Boolean) {
        _uiState.update { state ->
            val updatedConfig = state.webhookConfig.copy(enabledOptIn = enabled)
            state.copy(
                webhookConfig = updatedConfig,
                lastTestAlertMessage = null,
                statusBannerMessage = if (enabled) {
                    "Online alerts opted in. Set an HTTPS destination and tap Send Test Alert Now."
                } else {
                    "Online alerts turned off. App is strictly offline."
                }
            )
        }
        persistIfOptedIn()
    }

    fun selectWebhookProvider(provider: WebhookProvider) {
        _uiState.update { state ->
            val updatedConfig = state.webhookConfig.copy(provider = provider)
            state.copy(webhookConfig = updatedConfig, lastTestAlertMessage = null)
        }
        persistIfOptedIn()
    }

    fun updateWebhookUrl(rawUrl: String) {
        val syntax = WebhookUrlValidator.validateSyntax(rawUrl)
        _uiState.update { state ->
            state.copy(
                webhookConfig = state.webhookConfig.copy(webhookUrl = rawUrl),
                urlIsSafe = syntax is UrlValidationResult.Valid,
                urlValidationMessage = when (syntax) {
                    is UrlValidationResult.Valid -> "HTTPS format passed. DNS & SSRF checks also run right before sending."
                    is UrlValidationResult.Invalid -> syntax.reason
                }
            )
        }
        persistIfOptedIn()
    }

    fun updateWebhookLabel(label: String) {
        val safe = WebhookUrlValidator.sanitizePayloadText(label, 60)
        _uiState.update { state ->
            state.copy(webhookConfig = state.webhookConfig.copy(customLabel = safe))
        }
        persistIfOptedIn()
    }

    fun generatePrivateNtfyTopic() {
        val freshUrl = WebhookUrlValidator.generatePrivateNtfyUrl()
        _uiState.update { state ->
            state.copy(
                webhookConfig = state.webhookConfig.copy(
                    provider = WebhookProvider.NTFY,
                    webhookUrl = freshUrl
                ),
                urlIsSafe = true,
                urlValidationMessage = "Generated a unique 160-bit random private Ntfy.sh topic.",
                lastTestAlertMessage = null
            )
        }
        persistIfOptedIn()
    }

    /**
     * Explicit user-initiated online test alert button. Never called automatically at startup.
     */
    fun sendTestWebhookAlert() {
        val state = _uiState.value
        if (!state.webhookConfig.enabledOptIn) {
            _uiState.update {
                it.copy(lastTestAlertMessage = "Turn on 'Send Online Alerts (Opt-In)' first.")
            }
            return
        }
        if (state.isSendingTestAlert) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSendingTestAlert = true, lastTestAlertMessage = "Checking address safety and sending...") }
            val nowMs = System.currentTimeMillis()
            val result = webhookDispatcher.sendAlert(
                rawUrl = state.webhookConfig.webhookUrl,
                provider = state.webhookConfig.provider,
                label = state.webhookConfig.customLabel,
                reason = "Manual test alert from Tripwire Pulse",
                shakeDeltaMs2 = state.currentShakeDeltaMs2,
                soundDb = state.currentSoundDb,
                isSimulated = true,
                timestampMs = nowMs
            )

            val event = AlertEvent(
                id = UUID.randomUUID().toString(),
                timestampMs = nowMs,
                reason = "Manual test alert",
                shakeDeltaMs2 = state.currentShakeDeltaMs2,
                soundDb = state.currentSoundDb,
                isSimulated = true,
                webhookStatus = result.status,
                webhookStatusNote = result.message,
                encryptedStored = _uiState.value.saveHistoryOnPhone,
                payloadJson = result.payloadJson
            )

            _uiState.update { current ->
                val updatedAlerts = (listOf(event) + current.alerts).take(100)
                current.copy(
                    isSendingTestAlert = false,
                    lastTestAlertMessage = result.message,
                    alerts = updatedAlerts,
                    encryptedRecordCountOnDisk = if (current.saveHistoryOnPhone) updatedAlerts.size else 0
                )
            }
            persistIfOptedIn()
        }
    }

    // --- Privacy by Default & 1-Tap Wipe ---

    fun setSaveHistoryOnPhone(optIn: Boolean) {
        _uiState.update { current ->
            val taggedAlerts = current.alerts.map { it.copy(encryptedStored = optIn) }
            current.copy(
                saveHistoryOnPhone = optIn,
                alerts = taggedAlerts,
                encryptedRecordCountOnDisk = if (optIn) taggedAlerts.size else 0,
                statusBannerMessage = if (optIn) {
                    "Encrypted phone storage enabled (AES-GCM Keystore)."
                } else {
                    "Switched back to memory-only mode. Local vault cleared."
                }
            )
        }
        val state = _uiState.value
        vault.saveState(
            optIn = optIn,
            alerts = state.alerts,
            webhookConfig = state.webhookConfig
        )
    }

    fun wipeAllLocalData() {
        vault.wipeAll()
        notifier.clearNotifications()
        baselineEma = 0f
        lastTriggerMs = 0L
        latestMicDb = 32f

        _uiState.update { current ->
            current.copy(
                isArmed = false,
                isAlarmActive = false,
                isDemoMode = false,
                samples = emptyList(),
                currentX = 0f,
                currentY = 0f,
                currentZ = 0f,
                currentMagnitudeMs2 = 0f,
                baselineNoiseMs2 = 0f,
                currentShakeDeltaMs2 = 0f,
                peakShakeDeltaMs2 = 0f,
                currentSoundDb = 32f,
                peakSoundDb = 32f,
                alerts = emptyList(),
                saveHistoryOnPhone = false,
                encryptedRecordCountOnDisk = 0,
                webhookConfig = WebhookConfig(),
                urlValidationMessage = null,
                urlIsSafe = false,
                lastTestAlertMessage = null,
                statusBannerMessage = "All local data, encrypted logs, and memory wiped clean."
            )
        }
        restartMotionStream(demoMode = false)
        syncMicStream()
    }

    fun clearStatusBanner() {
        _uiState.update { it.copy(statusBannerMessage = null) }
    }

    // --- Internal Signal Processing Loop ---

    private fun restartMotionStream(demoMode: Boolean) {
        sensorStreamJob?.cancel()
        sensorStreamJob = viewModelScope.launch {
            if (demoMode) {
                demoGenerator.observeSimulatedFrames().collect { frame ->
                    processSample(
                        x = frame.x,
                        y = frame.y,
                        z = frame.z,
                        soundDb = frame.soundDb,
                        timestampMs = frame.timestampMs,
                        isSimulated = true
                    )
                }
            } else {
                geophoneEngine.observeAcceleration().collect { reading ->
                    processSample(
                        x = reading.x,
                        y = reading.y,
                        z = reading.z,
                        soundDb = latestMicDb,
                        timestampMs = reading.timestampMs,
                        isSimulated = false
                    )
                }
            }
        }
    }

    private fun syncMicStream() {
        micStreamJob?.cancel()
        val state = _uiState.value
        if (state.micGateEnabled && state.micPermissionGranted && !state.isDemoMode) {
            micStreamJob = viewModelScope.launch {
                micEngine.observeDecibels().collect { reading ->
                    latestMicDb = reading.dbSplApprox
                }
            }
        } else if (!state.isDemoMode) {
            latestMicDb = 32f
        }
    }

    /**
     * Exposed internal for deterministic testing of sample processing & trigger gating.
     */
    internal fun processSample(
        x: Float,
        y: Float,
        z: Float,
        soundDb: Float,
        timestampMs: Long,
        isSimulated: Boolean
    ) {
        val state = _uiState.value
        val magnitude = SeismographMath.computeMagnitude(x, y, z)
        baselineEma = SeismographMath.updateBaselineEma(baselineEma, magnitude)
        val delta = SeismographMath.computeShakeDelta(magnitude, baselineEma)

        val micActive = isSimulated || (state.micGateEnabled && state.micPermissionGranted)
        val eval = SeismographMath.evaluateTrigger(
            shakeDeltaMs2 = delta,
            shakeThresholdMs2 = state.shakeThresholdMs2,
            soundDb = soundDb,
            soundThresholdDb = state.soundThresholdDb,
            gateMode = state.gateMode,
            micActive = micActive
        )

        val cooldownElapsed = (timestampMs - lastTriggerMs) >= (state.cooldownSeconds * 1000L)
        val shouldFireAlert = state.isArmed && eval.triggered && cooldownElapsed

        val sample = SeismographSample(
            timestampMs = timestampMs,
            x = x,
            y = y,
            z = z,
            magnitudeMs2 = magnitude,
            deltaMs2 = delta,
            soundDb = soundDb,
            isTriggered = shouldFireAlert
        )

        val nextSamples = if (state.samples.size >= SeismographMath.MAX_HISTORY_SAMPLES) {
            state.samples.drop(1) + sample
        } else {
            state.samples + sample
        }

        if (shouldFireAlert) {
            lastTriggerMs = timestampMs
            handleIntrusionTrigger(
                timestampMs = timestampMs,
                reason = eval.reason,
                shakeDeltaMs2 = delta,
                soundDb = soundDb,
                isSimulated = isSimulated,
                nextSamples = nextSamples,
                x = x,
                y = y,
                z = z,
                magnitude = magnitude
            )
        } else {
            _uiState.update { current ->
                current.copy(
                    currentX = x,
                    currentY = y,
                    currentZ = z,
                    currentMagnitudeMs2 = magnitude,
                    baselineNoiseMs2 = baselineEma,
                    currentShakeDeltaMs2 = delta,
                    peakShakeDeltaMs2 = maxOf(current.peakShakeDeltaMs2, delta),
                    currentSoundDb = soundDb,
                    peakSoundDb = maxOf(current.peakSoundDb, soundDb),
                    samples = nextSamples
                )
            }
        }
    }

    private fun handleIntrusionTrigger(
        timestampMs: Long,
        reason: String,
        shakeDeltaMs2: Float,
        soundDb: Float,
        isSimulated: Boolean,
        nextSamples: List<SeismographSample>,
        x: Float,
        y: Float,
        z: Float,
        magnitude: Float
    ) {
        val state = _uiState.value
        val eventId = UUID.randomUUID().toString()
        val payloadJson = webhookDispatcher.buildAlertPayloadJson(
            provider = state.webhookConfig.provider,
            label = state.webhookConfig.customLabel,
            reason = reason,
            shakeDeltaMs2 = shakeDeltaMs2,
            soundDb = soundDb,
            isSimulated = isSimulated,
            timestampMs = timestampMs
        )

        val onlineEnabled = state.webhookConfig.enabledOptIn && state.webhookConfig.webhookUrl.isNotBlank()
        val initialEvent = AlertEvent(
            id = eventId,
            timestampMs = timestampMs,
            reason = reason,
            shakeDeltaMs2 = shakeDeltaMs2,
            soundDb = soundDb,
            isSimulated = isSimulated,
            webhookStatus = if (onlineEnabled) WebhookDeliveryStatus.PENDING else WebhookDeliveryStatus.OFFLINE_ONLY,
            webhookStatusNote = if (onlineEnabled) "Sending HTTPS alert..." else "Offline mode — kept on phone only",
            encryptedStored = state.saveHistoryOnPhone,
            payloadJson = payloadJson
        )

        _uiState.update { current ->
            val updatedAlerts = (listOf(initialEvent) + current.alerts).take(100)
            current.copy(
                isAlarmActive = true,
                currentX = x,
                currentY = y,
                currentZ = z,
                currentMagnitudeMs2 = magnitude,
                baselineNoiseMs2 = baselineEma,
                currentShakeDeltaMs2 = shakeDeltaMs2,
                peakShakeDeltaMs2 = maxOf(current.peakShakeDeltaMs2, shakeDeltaMs2),
                currentSoundDb = soundDb,
                peakSoundDb = maxOf(current.peakSoundDb, soundDb),
                samples = nextSamples,
                alerts = updatedAlerts,
                encryptedRecordCountOnDisk = if (current.saveHistoryOnPhone) updatedAlerts.size else 0
            )
        }

        if (state.vibrateOnTrigger) {
            notifier.vibrateAlert()
        }
        if (state.notificationsEnabled && state.notificationPermissionGranted) {
            val title = if (isSimulated) "Tripwire Pulse (Test Alert)" else "Tripwire Pulse Alert"
            notifier.postTriggerNotification(title = title, body = reason)
        }

        persistIfOptedIn()

        if (onlineEnabled) {
            val cfg = state.webhookConfig
            viewModelScope.launch {
                val result = webhookDispatcher.sendAlert(
                    rawUrl = cfg.webhookUrl,
                    provider = cfg.provider,
                    label = cfg.customLabel,
                    reason = reason,
                    shakeDeltaMs2 = shakeDeltaMs2,
                    soundDb = soundDb,
                    isSimulated = isSimulated,
                    timestampMs = timestampMs
                )
                _uiState.update { current ->
                    val updated = current.alerts.map { ev ->
                        if (ev.id == eventId) {
                            ev.copy(
                                webhookStatus = result.status,
                                webhookStatusNote = result.message,
                                payloadJson = result.payloadJson
                            )
                        } else {
                            ev
                        }
                    }
                    current.copy(alerts = updated)
                }
                persistIfOptedIn()
            }
        }
    }

    private fun persistIfOptedIn() {
        val state = _uiState.value
        if (state.saveHistoryOnPhone) {
            vault.saveState(
                optIn = true,
                alerts = state.alerts,
                webhookConfig = state.webhookConfig
            )
        }
    }

    override fun onCleared() {
        sensorStreamJob?.cancel()
        micStreamJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SentryViewModel(application = application) as T
                }
            }
        }
    }
}
