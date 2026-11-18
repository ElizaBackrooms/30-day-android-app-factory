package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.AuditLogEntry
import com.example.data.LogDirection
import com.example.hardware.MorseAlphabet
import com.example.ui.DeadDropUiState
import com.example.ui.components.SectionHeader
import com.example.ui.components.TacticalBadge
import com.example.ui.components.TacticalCard
import com.example.ui.theme.AmberSync
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.PhosphorGreenDim
import com.example.ui.theme.PhosphorGreenOn
import com.example.ui.theme.SosRed
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalBorderBright
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BeaconLogScreen(
  state: DeadDropUiState,
  auditEntries: List<AuditLogEntry>,
  persistHistoryOptIn: Boolean,
  onPersistOptInChange: (Boolean) -> Unit,
  onBeaconMessageChange: (String) -> Unit,
  onBeaconWpmChange: (Int) -> Unit,
  onBeaconLoopChange: (Boolean) -> Unit,
  onToggleBeacon: () -> Unit,
  onExportLog: () -> String,
  onWipeAllData: () -> Unit,
  onShowMessage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val clipboardManager = LocalClipboardManager.current
  val morsePreview = remember(state.beaconMessage) {
    MorseAlphabet.toMorsePreview(state.beaconMessage.ifBlank { "SOS" })
  }

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      TacticalCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionHeader(
            title = stringResource(R.string.beacon_title),
            modifier = Modifier.weight(1f),
          )
          TacticalBadge(
            text = if (state.hasFlashHardware) "FLASH READY" else "SCREEN STROBE",
            color = if (state.hasFlashHardware) PhosphorGreen else AmberSync,
            filled = state.isStrobeOn,
          )
        }

        // Strobe Circle Mirror + Active Telemetry
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(8.dp))
              .background(OledBlack)
              .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
              .padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
          StrobeIndicatorCircle(
            isOn = state.isStrobeOn,
            modifier = Modifier.size(60.dp).testTag("beacon_strobe_indicator"),
          )

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Text(
              text =
                when {
                  state.isStrobeOn -> "STROBE PULSE: ON"
                  state.isBeaconRunning -> "STROBE PULSE: OFF (KEYING)"
                  else -> "STROBE IDLE"
                },
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              color = if (state.isStrobeOn) PhosphorGreen else TextSecondary,
            )
            Text(
              text = morsePreview.ifEmpty { "· · · — — — · · ·" },
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              lineHeight = 16.sp,
              color = AmberSync,
              maxLines = 3,
            )
          }
        }

        // Quick Preset Chips
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          listOf("SOS", "CQ DEADDROP", state.callsign.ifBlank { "BRAVO-7" }).forEach { preset ->
            val selected = state.beaconMessage.equals(preset, ignoreCase = true)
            Box(
              modifier =
                Modifier
                  .weight(1f)
                  .heightIn(min = 40.dp)
                  .clip(RoundedCornerShape(6.dp))
                  .background(if (selected) PhosphorGreenDim else OledBlack)
                  .border(
                    1.dp,
                    if (selected) PhosphorGreen else TacticalBorder,
                    RoundedCornerShape(6.dp),
                  )
                  .clickable { onBeaconMessageChange(preset) }
                  .padding(horizontal = 6.dp),
              contentAlignment = Alignment.Center,
            ) {
              Text(
                text = preset,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (selected) PhosphorGreen else TextSecondary,
                maxLines = 1,
              )
            }
          }
        }

        OutlinedTextField(
          value = state.beaconMessage,
          onValueChange = onBeaconMessageChange,
          label = {
            Text(stringResource(R.string.beacon_message_label), fontFamily = FontFamily.Monospace)
          },
          singleLine = true,
          textStyle =
            MaterialTheme.typography.bodyLarge.copy(
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              color = PhosphorGreen,
            ),
          colors = tacticalTextFieldColors(),
          modifier = Modifier.fillMaxWidth().testTag("beacon_message_input"),
        )

        // WPM Slider (5..20)
        Column {
          val dotMs = 1200 / state.beaconWpm.coerceIn(5, 20)
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = "KEYING SPEED: ${state.beaconWpm} WPM",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text = "1 DOT = ${dotMs}ms",
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
              color = AmberSync,
            )
          }
          Slider(
            value = state.beaconWpm.toFloat(),
            onValueChange = { onBeaconWpmChange(it.roundToInt()) },
            valueRange = 5f..20f,
            steps = 14,
            colors =
              SliderDefaults.colors(
                thumbColor = PhosphorGreen,
                activeTrackColor = PhosphorGreen,
                inactiveTrackColor = TacticalBorderBright,
              ),
            modifier = Modifier.testTag("beacon_wpm_slider"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = stringResource(R.string.beacon_loop_label),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = TextSecondary,
          )
          Switch(
            checked = state.beaconLoop,
            onCheckedChange = onBeaconLoopChange,
            colors =
              SwitchDefaults.colors(
                checkedThumbColor = PhosphorGreenOn,
                checkedTrackColor = PhosphorGreen,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = OledBlack,
                uncheckedBorderColor = TacticalBorderBright,
              ),
            modifier = Modifier.testTag("beacon_loop_switch"),
          )
        }

        Button(
          onClick = onToggleBeacon,
          shape = RoundedCornerShape(10.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = if (state.isBeaconRunning) SosRed else PhosphorGreen,
              contentColor = if (state.isBeaconRunning) Color.White else PhosphorGreenOn,
            ),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("beacon_toggle_button"),
        ) {
          Icon(
            imageVector =
              if (state.isBeaconRunning) Icons.Rounded.FlashlightOff
              else Icons.Rounded.FlashlightOn,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              if (state.isBeaconRunning) stringResource(R.string.beacon_stop)
              else stringResource(R.string.beacon_start),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.8.sp,
          )
        }
      }
    }

    // Privacy & Storage Controls (Stateless by Default)
    item {
      TacticalCard(borderColor = if (persistHistoryOptIn) AmberSync.copy(alpha = 0.6f) else TacticalBorder) {
        SectionHeader(
          title = "PRIVACY & STORAGE (OPT-IN ONLY)",
          trailing = if (persistHistoryOptIn) "SAVING LOCALLY" else "IN-MEMORY ONLY",
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Save Message Log on Phone (Opt-In)",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
              text =
                if (persistHistoryOptIn) "Saved privately on this device only. Zero cloud sync."
                else "OFF by default. Everything disappears when you close the app.",
              style = MaterialTheme.typography.bodySmall,
              color = TextSecondary,
            )
          }
          Spacer(modifier = Modifier.width(8.dp))
          Switch(
            checked = persistHistoryOptIn,
            onCheckedChange = onPersistOptInChange,
            colors =
              SwitchDefaults.colors(
                checkedThumbColor = PhosphorGreenOn,
                checkedTrackColor = PhosphorGreen,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = OledBlack,
                uncheckedBorderColor = TacticalBorderBright,
              ),
            modifier = Modifier.testTag("persist_opt_in_switch"),
          )
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          OutlinedButton(
            onClick = onWipeAllData,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, SosRed.copy(alpha = 0.7f)),
            modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("wipe_all_data_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteSweep,
              contentDescription = "Wipe All Local Data",
              tint = SosRed,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = stringResource(R.string.log_clear),
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = SosRed,
            )
          }

          OutlinedButton(
            onClick = {
              try {
                context.startActivity(
                  Intent(Intent.ACTION_VIEW, Uri.parse(DeadDropUiState.PRIVACY_POLICY_URL))
                )
              } catch (_: Throwable) {
                onShowMessage("Open: ${DeadDropUiState.PRIVACY_POLICY_URL}")
              }
            },
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, TacticalBorderBright),
            modifier = Modifier.heightIn(min = 44.dp).testTag("privacy_policy_button"),
          ) {
            Text(
              text = "Privacy Policy",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = PhosphorGreen,
            )
          }
        }
      }
    }

    // Audit Log Header
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "${stringResource(R.string.log_title)} (${auditEntries.size})",
          style = MaterialTheme.typography.labelMedium,
          color = TextSecondary,
        )
        OutlinedButton(
          onClick = {
            val exported = onExportLog()
            if (exported.isEmpty()) {
              onShowMessage("Activity log is empty")
            } else {
              clipboardManager.setText(AnnotatedString(exported))
              onShowMessage("Copied ${auditEntries.size} log entries")
            }
          },
          shape = RoundedCornerShape(6.dp),
          border = BorderStroke(1.dp, TacticalBorderBright),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          modifier = Modifier.heightIn(min = 40.dp).testTag("log_copy_button"),
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = "Copy activity log",
            tint = PhosphorGreen,
            modifier = Modifier.size(14.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = stringResource(R.string.log_copy),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = PhosphorGreen,
          )
        }
      }
    }

    if (auditEntries.isEmpty()) {
      item {
        TacticalCard {
          Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Icon(
              imageVector = Icons.Rounded.History,
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.log_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = TextMuted,
            )
          }
        }
      }
    } else {
      items(auditEntries, key = { it.id }) { entry ->
        AuditLogItemCard(entry = entry)
      }
    }
  }
}

@Composable
private fun StrobeIndicatorCircle(isOn: Boolean, modifier: Modifier = Modifier) {
  Canvas(modifier = modifier) {
    val radius = size.minDimension / 2f
    if (isOn) {
      drawCircle(
        brush =
          Brush.radialGradient(
            colors = listOf(Color.White, PhosphorGreen, PhosphorGreen.copy(alpha = 0.08f)),
            center = center,
            radius = radius,
          ),
        radius = radius,
      )
    } else {
      drawCircle(
        color = Color(0xFF0A140F),
        radius = radius * 0.85f,
      )
      drawCircle(
        color = TacticalBorderBright,
        radius = radius * 0.85f,
        style = Stroke(width = 2.dp.toPx()),
      )
    }
  }
}

@Composable
private fun AuditLogItemCard(entry: AuditLogEntry) {
  val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }
  val dirColor =
    when (entry.direction) {
      LogDirection.TX -> PhosphorGreen
      LogDirection.RX -> PhosphorGreen
      LogDirection.LOOPBACK -> AmberSync
      LogDirection.BEACON -> AmberSync
    }

  TacticalCard(
    borderColor = if (!entry.crcValid) SosRed.copy(alpha = 0.7f) else TacticalBorder,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TacticalBadge(
          text = entry.direction.label,
          color = dirColor,
          filled = true,
        )
        TacticalBadge(
          text = entry.packetType,
          color = if (entry.packetType == "SOS_LOC") SosRed else TextSecondary,
        )
      }
      Text(
        text = fmt.format(Date(entry.timestampMs)),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = TextMuted,
      )
    }

    Text(
      text = entry.payload,
      fontFamily = FontFamily.Monospace,
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      val crcLabel =
        if (entry.crcHex == "N/A") "TORCH KEYING"
        else if (entry.crcValid) "CRC-16 ${entry.crcHex} PASS"
        else "CRC-16 ${entry.crcHex} FAIL"
      Text(
        text = crcLabel,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color =
          if (entry.crcHex == "N/A") AmberSync
          else if (entry.crcValid) PhosphorGreen
          else SosRed,
      )
      Text(
        text = "NODE: ${entry.callsign}",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = TextMuted,
      )
    }
  }
}
