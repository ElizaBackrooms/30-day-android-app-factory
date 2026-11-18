package com.example.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.SentryUiState

@Composable
fun HelpPrivacyScreen(
    uiState: SentryUiState,
    onSaveHistoryChanged: (Boolean) -> Unit,
    onWipeAllLocalData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val privacyPolicyUrl = stringResource(R.string.privacy_policy_url)
    var showWipeConfirm by remember { mutableStateOf(false) }
    var browserError by remember { mutableStateOf<String?>(null) }

    if (showWipeConfirm) {
        AlertDialog(
            onDismissRequest = { showWipeConfirm = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteForever,
                    contentDescription = "Confirm wipe",
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Wipe All Local Data?") },
            text = {
                Text(
                    "This clears saved settings, deletes encrypted alert files on this phone, removes the encryption key, and resets all live readings and alert links in memory."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeConfirm = false
                        onWipeAllLocalData()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    modifier = Modifier.testTag("confirm_wipe_dialog_button")
                ) {
                    Text("Wipe Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWipeConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
            // 1. Privacy & Storage Controls
            item(key = "privacy_controls") {
                SectionCard(
                    title = stringResource(R.string.section_privacy_title),
                    subtitle = stringResource(R.string.section_privacy_subtitle),
                    icon = Icons.Rounded.PrivacyTip
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.toggle_save_history),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = stringResource(R.string.toggle_save_history_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = uiState.saveHistoryOnPhone,
                            onCheckedChange = onSaveHistoryChanged,
                            modifier = Modifier.testTag("save_history_opt_in_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { showWipeConfirm = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("wipe_all_data_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteForever,
                            contentDescription = "Wipe all local data",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_wipe_all_data))
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(privacyPolicyUrl))
                                context.startActivity(intent)
                                browserError = null
                            } catch (_: Exception) {
                                browserError = "No web browser app found to open the Privacy Policy link."
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_privacy_policy_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = "Open Privacy Policy in browser",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.action_open_privacy_policy))
                    }

                    browserError?.let { err ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // 2. Plain-English Beginner Guide
            item(key = "beginner_guide") {
                SectionCard(
                    title = "How Tripwire Pulse Works (Plain English)",
                    subtitle = "No jargon required — here is what every tool inside the app does.",
                    icon = Icons.Rounded.HelpOutline
                ) {
                    GuideItem(
                        heading = "1. Shake Sensor (3-Axis Accelerometer Geophone)",
                        body = "Place your phone flat on a door frame, table, luggage, or floor. The built-in motion chip feels tiny vibrations in 3 directions (left/right, forward/back, up/down) and plots them on the live seismograph."
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    GuideItem(
                        heading = "2. Sound Gate (Optional Mic Decibel Meter)",
                        body = "If you allow the microphone, Tripwire Pulse measures room loudness in decibels (dB) only in temporary memory. Audio is never saved or recorded. Using Shake + Sound (Dual Check) helps ignore soft traffic rumbles."
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    GuideItem(
                        heading = "3. Test Mode (Demo Signal)",
                        body = "Want to test without shaking your phone? Turn on Test Mode on the Watch tab and tap Simulate Footstep or Door Knock to watch the graph spike and trigger an alert."
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    GuideItem(
                        heading = "4. Online Push Alerts (Optional Ntfy.sh / Discord / HTTPS)",
                        body = "Completely off by default. If you opt in on the Online tab, Tripwire Pulse can generate a random private Ntfy.sh topic or post to your Discord/HTTPS link whenever an alert fires."
                    )
                }
            }

            // 3. De-Googled & Security Hardening Summary
            item(key = "security_guarantees") {
                SectionCard(
                    title = "Built for Offline & De-Googled Phones (AOSP)",
                    subtitle = "Zero Google Play Services, zero Firebase, zero ads, and zero hidden tracking.",
                    icon = Icons.Rounded.Lock
                ) {
                    GuideItem(
                        heading = "Runs 100% Natively on LineageOS & Stock Android",
                        body = "Uses only standard Android (AOSP) sensor, audio, encryption, and HTTPS libraries. Works with airplane mode on."
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    GuideItem(
                        heading = "Android Keystore Encryption & No Cloud Backup",
                        body = "If you turn on Save History on Phone, logs and alert URLs are locked with AES-GCM encryption inside private app storage and excluded from Android backups."
                    )
                }
            }

            // 4. Hardware Detection Snapshot
            item(key = "device_sensor_status") {
                SectionCard(
                    title = "Detected Hardware on This Phone",
                    subtitle = "Tripwire Pulse never reads device IDs, serial numbers, or hardware fingerprints.",
                    icon = Icons.Rounded.Sensors
                ) {
                    Text(
                        text = "Active Shake Sensor: ${uiState.hardwareSensorName}\n" +
                            "Mic Sound Gate: ${if (uiState.micPermissionGranted) "Allowed" else "Off (optional)"}\n" +
                            "Local Notifications: ${if (uiState.notificationPermissionGranted) "Allowed" else "Off (optional)"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun GuideItem(
    heading: String,
    body: String
) {
    Column {
        Text(
            text = heading,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
