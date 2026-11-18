package com.example.model

import com.example.engine.SeismographSample
import com.example.engine.SensorGateMode

enum class AppTab(val route: String) {
    WATCH("watch"),
    ALERTS("alerts"),
    REMOTE("remote"),
    HELP_PRIVACY("help_privacy")
}

enum class DemoImpulseType(val label: String, val peakAccelMs2: Float, val peakDb: Float) {
    FOOTSTEP("Footstep", 1.85f, 58f),
    DOOR_KNOCK("Door Knock", 3.40f, 76f),
    LOUD_IMPACT("Door Open / Impact", 5.80f, 88f)
}

enum class WebhookProvider(val displayName: String, val subtitle: String) {
    NTFY("Ntfy.sh Push", "Free instant phone alerts using a random private topic."),
    DISCORD("Discord Webhook", "Posts a brief alert card into your private Discord channel."),
    CUSTOM_HTTPS("Custom HTTPS Link", "Sends a compact JSON alert to your own HTTPS server.")
}

enum class WebhookDeliveryStatus {
    OFFLINE_ONLY,
    PENDING,
    SENT,
    BLOCKED_INVALID_URL,
    FAILED
}

data class AlertEvent(
    val id: String,
    val timestampMs: Long,
    val reason: String,
    val shakeDeltaMs2: Float,
    val soundDb: Float,
    val isSimulated: Boolean,
    val webhookStatus: WebhookDeliveryStatus,
    val webhookStatusNote: String,
    val encryptedStored: Boolean,
    val payloadJson: String
)

data class WebhookConfig(
    val enabledOptIn: Boolean = false,
    val provider: WebhookProvider = WebhookProvider.NTFY,
    val webhookUrl: String = "",
    val customLabel: String = "Front Door Sentry"
)

data class SentryUiState(
    val selectedTab: AppTab = AppTab.WATCH,
    val isArmed: Boolean = false,
    val isAlarmActive: Boolean = false,
    val isDemoMode: Boolean = false,
    val hardwareSensorName: String = "Checking phone shake sensor...",
    val hasLinearAccelSensor: Boolean = false,
    val showAxisTraces: Boolean = true,

    // Live readings
    val currentX: Float = 0f,
    val currentY: Float = 0f,
    val currentZ: Float = 0f,
    val currentMagnitudeMs2: Float = 0f,
    val baselineNoiseMs2: Float = 0f,
    val currentShakeDeltaMs2: Float = 0f,
    val peakShakeDeltaMs2: Float = 0f,
    val currentSoundDb: Float = 32f,
    val peakSoundDb: Float = 32f,
    val samples: List<SeismographSample> = emptyList(),

    // Trigger settings
    val shakeThresholdMs2: Float = 1.25f,
    val soundThresholdDb: Float = 68f,
    val gateMode: SensorGateMode = SensorGateMode.VIBRATION_ONLY,
    val cooldownSeconds: Int = 5,

    // Optional Mic Gate & Local Notification permissions / states
    val micGateEnabled: Boolean = false,
    val micPermissionGranted: Boolean = false,
    val micPermissionDeniedMessage: String? = null,
    val notificationsEnabled: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    val vibrateOnTrigger: Boolean = true,

    // Alerts log (100% in-memory by default)
    val alerts: List<AlertEvent> = emptyList(),

    // Privacy by default (OFF by default)
    val saveHistoryOnPhone: Boolean = false,
    val encryptedRecordCountOnDisk: Int = 0,

    // Online Push Alerts (Opt-in only, OFF by default)
    val webhookConfig: WebhookConfig = WebhookConfig(),
    val urlValidationMessage: String? = null,
    val urlIsSafe: Boolean = false,
    val isSendingTestAlert: Boolean = false,
    val lastTestAlertMessage: String? = null,

    // Transient UI feedback
    val statusBannerMessage: String? = null
)
