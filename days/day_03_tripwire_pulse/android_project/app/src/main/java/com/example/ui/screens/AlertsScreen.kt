package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.engine.SeismographMath
import com.example.model.AlertEvent
import com.example.model.SentryUiState
import com.example.model.WebhookDeliveryStatus
import com.example.ui.theme.MonoReadoutSmall
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AlertsScreen(
    uiState: SentryUiState,
    onWipeAllData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm:ss • MMM d", Locale.getDefault()) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 760.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "alerts_header") {
                SectionCard(
                    title = stringResource(R.string.section_alerts_title),
                    subtitle = stringResource(R.string.section_alerts_subtitle),
                    icon = Icons.Rounded.History
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = if (uiState.saveHistoryOnPhone) Icons.Rounded.Lock else Icons.Rounded.Memory,
                                contentDescription = "Storage mode",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (uiState.saveHistoryOnPhone) {
                                    "Encrypted Phone Storage ON (${uiState.encryptedRecordCountOnDisk} saved)"
                                } else {
                                    "Memory Only — cleared when you close the app"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (uiState.alerts.isNotEmpty() || uiState.saveHistoryOnPhone) {
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = onWipeAllData,
                                modifier = Modifier.testTag("alerts_wipe_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.DeleteForever,
                                    contentDescription = "Wipe all data",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Wipe")
                            }
                        }
                    }
                }
            }

            if (uiState.alerts.isEmpty()) {
                item(key = "alerts_empty") {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("alerts_empty_card"),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Quiet perimeter",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.alerts_empty_title),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.alerts_empty_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(items = uiState.alerts, key = { it.id }) { event ->
                    AlertEventCard(
                        event = event,
                        formattedTime = timeFormatter.format(Date(event.timestampMs))
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun AlertEventCard(
    event: AlertEvent,
    formattedTime: String
) {
    var expanded by remember { mutableStateOf(false) }
    val colorScheme = MaterialTheme.colorScheme

    val (statusIcon, statusTint, statusLabel) = when (event.webhookStatus) {
        WebhookDeliveryStatus.OFFLINE_ONLY -> Triple(Icons.Rounded.CloudOff, colorScheme.onSurfaceVariant, "Offline Only")
        WebhookDeliveryStatus.PENDING -> Triple(Icons.Rounded.History, colorScheme.secondary, "Sending...")
        WebhookDeliveryStatus.SENT -> Triple(Icons.Rounded.CheckCircle, colorScheme.primary, "Online Alert Sent")
        WebhookDeliveryStatus.BLOCKED_INVALID_URL -> Triple(Icons.Rounded.ErrorOutline, colorScheme.error, "Blocked Unsafe URL")
        WebhookDeliveryStatus.FAILED -> Triple(Icons.Rounded.ErrorOutline, colorScheme.error, "Online Send Failed")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .testTag("alert_event_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedTime,
                    style = MonoReadoutSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (event.isSimulated) {
                        Surface(
                            color = colorScheme.secondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "TEST",
                                style = MonoReadoutSmall,
                                color = colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (event.encryptedStored) {
                        Surface(
                            color = colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "AES-GCM",
                                style = MonoReadoutSmall,
                                color = colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = event.reason,
                style = MaterialTheme.typography.titleSmall,
                color = colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shake: ${SeismographMath.formatTwoDecimals(event.shakeDeltaMs2)} m/s²",
                    style = MonoReadoutSmall,
                    color = colorScheme.primary
                )
                Text(
                    text = "Sound: ${event.soundDb.toInt()} dB",
                    style = MonoReadoutSmall,
                    color = colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusLabel,
                    tint = statusTint,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$statusLabel — ${event.webhookStatusNote}",
                    style = MaterialTheme.typography.bodySmall,
                    color = statusTint
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Text(
                        text = "Alert Payload (JSON):",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        color = colorScheme.surfaceContainerLowest,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, colorScheme.outlineVariant, MaterialTheme.shapes.small)
                    ) {
                        Text(
                            text = event.payloadJson,
                            style = MonoReadoutSmall,
                            color = colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (expanded) "Tap to hide details" else "Tap to inspect alert payload",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}
