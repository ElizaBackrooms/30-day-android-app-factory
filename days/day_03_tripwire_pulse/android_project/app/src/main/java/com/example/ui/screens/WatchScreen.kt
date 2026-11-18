package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.SeismographMath
import com.example.engine.SensorGateMode
import com.example.model.DemoImpulseType
import com.example.model.SentryUiState
import com.example.ui.components.LiveSeismographChart
import com.example.ui.components.SoundLevelMeterBar
import com.example.ui.theme.MonoReadoutLarge
import com.example.ui.theme.MonoReadoutMedium
import com.example.ui.theme.MonoReadoutSmall

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WatchScreen(
    uiState: SentryUiState,
    onToggleArmed: () -> Unit,
    onClearAlarm: () -> Unit,
    onToggleDemoMode: (Boolean) -> Unit,
    onTriggerDemoImpulse: (DemoImpulseType) -> Unit,
    onToggleAxisTraces: (Boolean) -> Unit,
    onShakeThresholdChange: (Float) -> Unit,
    onSoundThresholdChange: (Float) -> Unit,
    onGateModeSelected: (SensorGateMode) -> Unit,
    onRequestMicGateToggle: (Boolean) -> Unit,
    onRequestNotificationsToggle: (Boolean) -> Unit,
    onVibrateToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Hero Sentry Status Card
            item(key = "hero_status") {
                SentryHeroCard(
                    uiState = uiState,
                    onToggleArmed = onToggleArmed,
                    onClearAlarm = onClearAlarm
                )
            }

            // 2. Live Seismograph + Telemetry Readouts
            item(key = "seismograph_section") {
                SectionCard(
                    title = stringResource(R.string.section_seismograph_title),
                    subtitle = stringResource(R.string.section_seismograph_subtitle),
                    icon = Icons.Rounded.GraphicEq
                ) {
                    // Numeric Telemetry Strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TelemetryTile(
                            label = "Live Shake",
                            value = SeismographMath.formatTwoDecimals(uiState.currentShakeDeltaMs2),
                            unit = "m/s²",
                            highlight = uiState.currentShakeDeltaMs2 >= uiState.shakeThresholdMs2,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            label = "Peak Shake",
                            value = SeismographMath.formatTwoDecimals(uiState.peakShakeDeltaMs2),
                            unit = "m/s²",
                            highlight = uiState.peakShakeDeltaMs2 >= uiState.shakeThresholdMs2,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryTile(
                            label = "Sound Level",
                            value = if (uiState.isDemoMode || (uiState.micGateEnabled && uiState.micPermissionGranted)) {
                                "${uiState.currentSoundDb.toInt()}"
                            } else {
                                "OFF"
                            },
                            unit = if (uiState.isDemoMode || (uiState.micGateEnabled && uiState.micPermissionGranted)) "dB" else "",
                            highlight = (uiState.isDemoMode || uiState.micGateEnabled) &&
                                uiState.currentSoundDb >= uiState.soundThresholdDb,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LiveSeismographChart(
                        samples = uiState.samples,
                        shakeThresholdMs2 = uiState.shakeThresholdMs2,
                        isArmed = uiState.isArmed,
                        isAlarmActive = uiState.isAlarmActive,
                        showAxisTraces = uiState.showAxisTraces
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    SoundLevelMeterBar(
                        currentDb = uiState.currentSoundDb,
                        thresholdDb = uiState.soundThresholdDb,
                        active = uiState.isDemoMode || (uiState.micGateEnabled && uiState.micPermissionGranted)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.hardwareSensorName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = uiState.showAxisTraces,
                            onClick = { onToggleAxisTraces(!uiState.showAxisTraces) },
                            label = { Text("X/Y/Z Lines") },
                            modifier = Modifier.testTag("toggle_axis_traces_chip")
                        )
                    }
                }
            }

            // 3. Built-in 1-Tap Demo / Simulation Mode
            item(key = "demo_mode_section") {
                SectionCard(
                    title = stringResource(R.string.section_demo_title),
                    subtitle = stringResource(R.string.section_demo_subtitle),
                    icon = Icons.Rounded.Science
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (uiState.isDemoMode) "Test Mode is ON" else "Use Simulated Signal",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Generates realistic room hum plus tap-to-trigger bumps.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.isDemoMode,
                            onCheckedChange = onToggleDemoMode,
                            modifier = Modifier.testTag("demo_mode_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DemoImpulseType.entries.forEach { impulse ->
                            OutlinedButton(
                                onClick = { onTriggerDemoImpulse(impulse) },
                                modifier = Modifier.testTag("demo_impulse_${impulse.name.lowercase()}")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = "Simulate ${impulse.label}",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Simulate ${impulse.label}")
                            }
                        }
                    }
                }
            }

            // 4. Trigger Sensitivity & Mic Gate Controls
            item(key = "controls_section") {
                SectionCard(
                    title = stringResource(R.string.section_controls_title),
                    subtitle = stringResource(R.string.section_controls_subtitle),
                    icon = Icons.Rounded.Sensors
                ) {
                    // Shake sensitivity slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Shake Limit (m/s²)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${SeismographMath.formatTwoDecimals(uiState.shakeThresholdMs2)} m/s²",
                            style = MonoReadoutMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Text(
                        text = "Lower numbers catch lighter footsteps; higher numbers ignore gentle bumps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = uiState.shakeThresholdMs2,
                        onValueChange = onShakeThresholdChange,
                        valueRange = 0.25f..5.0f,
                        modifier = Modifier.testTag("shake_threshold_slider")
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                    // Optional Microphone Sound Gate toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (uiState.micGateEnabled) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                contentDescription = "Microphone sound gate",
                                tint = if (uiState.micGateEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Sound Gate (Optional Mic dB)",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Measures room loudness in memory only. Never records audio.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.micGateEnabled,
                            onCheckedChange = onRequestMicGateToggle,
                            modifier = Modifier.testTag("mic_gate_switch")
                        )
                    }

                    AnimatedVisibility(visible = uiState.micPermissionDeniedMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Text(
                                text = uiState.micPermissionDeniedMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Sound threshold slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Sound Limit (Decibels)",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${uiState.soundThresholdDb.toInt()} dB",
                            style = MonoReadoutMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Slider(
                        value = uiState.soundThresholdDb,
                        onValueChange = onSoundThresholdChange,
                        valueRange = 40f..95f,
                        enabled = uiState.micGateEnabled || uiState.isDemoMode,
                        modifier = Modifier.testTag("sound_threshold_slider")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "How Should It Trigger?",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SensorGateMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.gateMode == mode,
                                onClick = { onGateModeSelected(mode) },
                                label = { Text(mode.displayName) },
                                modifier = Modifier.testTag("gate_mode_${mode.name.lowercase()}")
                            )
                        }
                    }
                    Text(
                        text = uiState.gateMode.plainEnglishSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Local Notification & Vibrate toggles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (uiState.notificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                                contentDescription = "Alert notifications",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Phone Alert Banner (Optional)", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Shows a local Android notification on trigger.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.notificationsEnabled,
                            onCheckedChange = onRequestNotificationsToggle,
                            modifier = Modifier.testTag("notifications_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Vibration,
                                contentDescription = "Vibrate on alert",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Vibrate on Trigger", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "Gives a short tactile buzz when movement is detected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.vibrateOnTrigger,
                            onCheckedChange = onVibrateToggle,
                            modifier = Modifier.testTag("vibrate_switch")
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun SentryHeroCard(
    uiState: SentryUiState,
    onToggleArmed: () -> Unit,
    onClearAlarm: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val accentColor = when {
        uiState.isAlarmActive -> colorScheme.error
        uiState.isArmed -> colorScheme.primary
        else -> colorScheme.onSurfaceVariant
    }
    val statusTitle = when {
        uiState.isAlarmActive -> stringResource(R.string.sentry_status_triggered)
        uiState.isArmed -> stringResource(R.string.sentry_status_armed)
        else -> stringResource(R.string.sentry_status_standby)
    }
    val statusSub = when {
        uiState.isAlarmActive -> "Movement crossed your limit. Check the Alerts tab or silence below."
        uiState.isArmed -> "Watching quietly for vibration spikes. Leave the phone resting on a surface."
        else -> "100% offline sentry ready. Tap Arm Sentry or try Test Mode below."
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.22f),
                        colorScheme.surfaceContainer
                    )
                )
            )
            .border(
                width = if (uiState.isAlarmActive) 2.dp else 1.dp,
                color = accentColor.copy(alpha = if (uiState.isAlarmActive) 0.9f else 0.45f),
                shape = MaterialTheme.shapes.large
            )
            .padding(18.dp)
            .testTag("sentry_hero_card")
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (uiState.webhookConfig.enabledOptIn) "ONLINE ALERTS OPTED IN" else "100% OFFLINE MODE",
                        style = MonoReadoutSmall,
                        color = accentColor
                    )
                }

                if (uiState.isDemoMode) {
                    Surface(
                        color = colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "DEMO SIGNAL",
                            style = MonoReadoutSmall,
                            color = colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (uiState.isAlarmActive) Icons.Rounded.WarningAmber else Icons.Rounded.Shield,
                    contentDescription = statusTitle,
                    tint = accentColor,
                    modifier = Modifier.size(30.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = statusTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = statusSub,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onToggleArmed,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isArmed) colorScheme.surfaceContainerHigh else colorScheme.primary,
                        contentColor = if (uiState.isArmed) colorScheme.onSurface else colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("arm_disarm_button")
                ) {
                    Text(
                        text = if (uiState.isArmed) stringResource(R.string.action_disarm_sentry) else stringResource(R.string.action_arm_sentry),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                if (uiState.isAlarmActive) {
                    Button(
                        onClick = onClearAlarm,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colorScheme.error,
                            contentColor = colorScheme.onError
                        ),
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("silence_alarm_button")
                    ) {
                        Text(stringResource(R.string.action_clear_alarm))
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryTile(
    label: String,
    value: String,
    unit: String,
    highlight: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        color = if (highlight) colorScheme.errorContainer else colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.small,
        modifier = modifier.border(
            width = 1.dp,
            color = if (highlight) colorScheme.error else colorScheme.outlineVariant,
            shape = MaterialTheme.shapes.small
        )
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (highlight) colorScheme.onErrorContainer else colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = value,
                    style = MonoReadoutLarge,
                    color = if (highlight) colorScheme.error else colorScheme.onSurface
                )
                if (unit.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = unit,
                        style = MonoReadoutSmall,
                        color = colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() }
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}
