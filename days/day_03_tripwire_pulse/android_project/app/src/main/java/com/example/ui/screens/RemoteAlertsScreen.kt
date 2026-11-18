package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.SentryUiState
import com.example.model.WebhookProvider
import com.example.ui.theme.MonoReadoutSmall

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RemoteAlertsScreen(
    uiState: SentryUiState,
    onOptInChanged: (Boolean) -> Unit,
    onProviderSelected: (WebhookProvider) -> Unit,
    onUrlChanged: (String) -> Unit,
    onLabelChanged: (String) -> Unit,
    onGeneratePrivateNtfy: () -> Unit,
    onSendTestAlert: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var copiedFeedback by remember { mutableStateOf(false) }

    // Hardening: Set FLAG_SECURE while this screen displays private webhook URLs / tokens
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
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
            // FLAG_SECURE privacy notice banner
            item(key = "flag_secure_banner") {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("flag_secure_notice")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Screen protection active",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.remote_screen_protected),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            // Main Opt-In Card
            item(key = "opt_in_card") {
                SectionCard(
                    title = stringResource(R.string.section_remote_title),
                    subtitle = stringResource(R.string.section_remote_subtitle),
                    icon = Icons.Rounded.CloudUpload
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Send Online Alerts (Opt-In)",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (uiState.webhookConfig.enabledOptIn) {
                                    "Enabled — alerts will POST over HTTPS when armed."
                                } else {
                                    "Off — Tripwire Pulse makes zero internet connections."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.webhookConfig.enabledOptIn,
                            onCheckedChange = onOptInChanged,
                            modifier = Modifier.testTag("online_alerts_opt_in_switch")
                        )
                    }
                }
            }

            // Destination Configuration Card
            item(key = "destination_config") {
                SectionCard(
                    title = "Alert Destination (HTTPS Webhook)",
                    subtitle = "Choose Ntfy.sh, Discord, or your own HTTPS server. Private network IPs are blocked.",
                    icon = Icons.Rounded.Shield
                ) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        WebhookProvider.entries.forEach { provider ->
                            FilterChip(
                                selected = uiState.webhookConfig.provider == provider,
                                onClick = { onProviderSelected(provider) },
                                label = { Text(provider.displayName) },
                                modifier = Modifier.testTag("provider_chip_${provider.name.lowercase()}")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.webhookConfig.provider.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (uiState.webhookConfig.provider == WebhookProvider.NTFY) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                copiedFeedback = false
                                onGeneratePrivateNtfy()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("generate_ntfy_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoFixHigh,
                                contentDescription = "Generate random private topic",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_generate_ntfy))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.webhookConfig.webhookUrl,
                        onValueChange = {
                            copiedFeedback = false
                            onUrlChanged(it)
                        },
                        label = { Text("Alert Web Address (https:// only)") },
                        placeholder = {
                            Text(
                                when (uiState.webhookConfig.provider) {
                                    WebhookProvider.NTFY -> "https://ntfy.sh/tripwire-..."
                                    WebhookProvider.DISCORD -> "https://discord.com/api/webhooks/..."
                                    WebhookProvider.CUSTOM_HTTPS -> "https://your-server.example/hook"
                                }
                            )
                        },
                        singleLine = true,
                        trailingIcon = {
                            if (uiState.webhookConfig.webhookUrl.isNotBlank()) {
                                IconButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(uiState.webhookConfig.webhookUrl))
                                        copiedFeedback = true
                                    },
                                    modifier = Modifier.testTag("copy_webhook_url_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "Copy private alert address"
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("webhook_url_input")
                    )

                    if (copiedFeedback) {
                        Text(
                            text = "Copied alert address to clipboard. Subscribe to this topic on your other device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    uiState.urlValidationMessage?.let { msg ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (uiState.urlIsSafe) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                contentDescription = "URL safety check",
                                tint = if (uiState.urlIsSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (uiState.urlIsSafe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                modifier = Modifier.testTag("url_validation_message")
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = uiState.webhookConfig.customLabel,
                        onValueChange = onLabelChanged,
                        label = { Text("Sentry Name (Shown in Alert)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("webhook_label_input")
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onSendTestAlert,
                        enabled = uiState.webhookConfig.enabledOptIn && uiState.urlIsSafe && !uiState.isSendingTestAlert,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("send_test_alert_button")
                    ) {
                        if (uiState.isSendingTestAlert) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sending...")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Send,
                                contentDescription = "Send test alert now",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.action_send_test_alert))
                        }
                    }

                    uiState.lastTestAlertMessage?.let { resultMsg ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = resultMsg,
                                style = MonoReadoutSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Network Safety Rules Explainer
            item(key = "ssrf_rules_card") {
                SectionCard(
                    title = "Built-in Network Safety Filter (SSRF Guard)",
                    subtitle = "Protects your home router and local Wi-Fi devices from unintended connections.",
                    icon = Icons.Rounded.Shield
                ) {
                    Text(
                        text = "• HTTPS only (strict platform TLS verification, no redirects, 10s timeout, 64 KB response cap).\n" +
                            "• Blocks localhost, .local, .internal, private LAN ranges (10/8, 172.16/12, 192.168/16), link-local, CGNAT, ULA, IPv4-mapped IPv6, and multicast IPs.\n" +
                            "• Never opens listening ports and never logs your private webhook URL.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}
