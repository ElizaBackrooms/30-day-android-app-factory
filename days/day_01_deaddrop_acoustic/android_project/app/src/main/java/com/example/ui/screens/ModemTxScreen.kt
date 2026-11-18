package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EditLocationAlt
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.dsp.PacketProtocol
import com.example.dsp.PacketType
import com.example.ui.DeadDropUiState
import com.example.ui.LoopbackTestResult
import com.example.ui.components.SectionHeader
import com.example.ui.components.TacticalBadge
import com.example.ui.components.TacticalCard
import com.example.ui.theme.AmberSync
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.PhosphorGreenDim
import com.example.ui.theme.PhosphorGreenOn
import com.example.ui.theme.SosRed
import com.example.ui.theme.SosRedDim
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalBorderBright
import com.example.ui.theme.TacticalCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun ModemTxScreen(
  state: DeadDropUiState,
  onCallsignChange: (String) -> Unit,
  onPacketTypeChange: (PacketType) -> Unit,
  onMessageChange: (String) -> Unit,
  onCoordinatesChange: (String) -> Unit,
  onToggleManualCoords: () -> Unit,
  onFetchGps: () -> Unit,
  onLocationPermissionResult: (Boolean) -> Unit,
  onTransmitClick: () -> Unit,
  onLoopbackClick: () -> Unit,
  onOnlineRelayToggle: (Boolean) -> Unit,
  onOnlineRelayUrlChange: (String) -> Unit,
  onGenerateRandomTopic: () -> Unit,
  onSendOnlineCopy: () -> Unit,
  onCheckOnlineInbox: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showGpsRationale by remember { mutableStateOf(false) }
  val locationLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = onLocationPermissionResult,
    )

  val scrollState = rememberScrollState()
  val frame = state.encodedFrame

  Column(
    modifier =
      modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    TacticalCard {
      SectionHeader(title = "1. CREATE MESSAGE", trailing = "NO INTERNET NEEDED")

      Text(
        text = "Turns your text into radio-like audio beeps so another phone running DeadDrop can hear and decode it offline.",
        style = MaterialTheme.typography.bodySmall,
        color = TextSecondary,
      )

      OutlinedTextField(
        value = state.callsign,
        onValueChange = onCallsignChange,
        label = { Text(stringResource(R.string.tx_callsign_label), fontFamily = FontFamily.Monospace) },
        placeholder = {
          Text(stringResource(R.string.tx_callsign_placeholder), fontFamily = FontFamily.Monospace)
        },
        singleLine = true,
        textStyle =
          MaterialTheme.typography.bodyLarge.copy(
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = PhosphorGreen,
          ),
        colors = tacticalTextFieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("callsign_input"),
      )

      Text(
        text = stringResource(R.string.tx_type_label),
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondary,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        PacketType.entries.forEach { type ->
          val selected = state.packetType == type
          val accent = if (type == PacketType.SOS_LOC) SosRed else PhosphorGreen
          val bg =
            when {
              selected && type == PacketType.SOS_LOC -> SosRedDim
              selected -> PhosphorGreenDim
              else -> OledBlack
            }
          Box(
            modifier =
              Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(bg)
                .border(
                  width = if (selected) 1.5.dp else 1.dp,
                  color = if (selected) accent else TacticalBorder,
                  shape = RoundedCornerShape(8.dp),
                )
                .clickable { onPacketTypeChange(type) }
                .testTag("type_chip_${type.label.lowercase()}"),
            contentAlignment = Alignment.Center,
          ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              val friendlyTitle =
                when (type) {
                  PacketType.TEXT -> "NOTE"
                  PacketType.SOS_LOC -> "SOS"
                  PacketType.PING -> "PING"
                }
              val friendlySub =
                when (type) {
                  PacketType.TEXT -> "Normal"
                  PacketType.SOS_LOC -> "Urgent"
                  PacketType.PING -> "Quick"
                }
              Text(
                text = friendlyTitle,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (selected) accent else TextSecondary,
              )
              Text(
                text = friendlySub,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = if (selected) accent.copy(alpha = 0.85f) else TextMuted,
              )
            }
          }
        }
      }

      OutlinedTextField(
        value = state.message,
        onValueChange = onMessageChange,
        label = { Text(stringResource(R.string.tx_message_label), fontFamily = FontFamily.Monospace) },
        placeholder = { Text(stringResource(R.string.tx_message_placeholder)) },
        minLines = 2,
        maxLines = 4,
        supportingText = {
          val used = frame.payloadBytes.size
          val limit = PacketProtocol.MAX_PAYLOAD_BYTES
          Text(
            text = "PAYLOAD UTF-8: $used / $limit BYTES",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (used >= limit) AmberSync else TextSecondary,
          )
        },
        colors = tacticalTextFieldColors(),
        modifier = Modifier.fillMaxWidth().testTag("message_input"),
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedButton(
          onClick = {
            val fine =
              ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            val coarse =
              ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
              ) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
              onFetchGps()
            } else {
              showGpsRationale = true
            }
          },
          enabled = !state.isFetchingGps,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, PhosphorGreen.copy(alpha = 0.6f)),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = PhosphorGreen),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("attach_gps_button"),
        ) {
          if (state.isFetchingGps) {
            CircularProgressIndicator(
              modifier = Modifier.size(16.dp),
              strokeWidth = 2.dp,
              color = PhosphorGreen,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.tx_fetching_gps),
              fontFamily = FontFamily.Monospace,
              fontSize = 12.sp,
            )
          } else {
            Icon(
              imageVector = Icons.Rounded.MyLocation,
              contentDescription = "Attach GPS coordinates",
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.tx_attach_gps),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            )
          }
        }

        OutlinedButton(
          onClick = onToggleManualCoords,
          shape = RoundedCornerShape(8.dp),
          border = BorderStroke(1.dp, TacticalBorderBright),
          colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
          modifier = Modifier.heightIn(min = 48.dp).testTag("manual_coords_toggle"),
        ) {
          Icon(
            imageVector = Icons.Rounded.EditLocationAlt,
            contentDescription = "Manual Grid or Coordinates",
            modifier = Modifier.size(18.dp),
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "GRID",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
          )
        }
      }

      AnimatedVisibility(visible = state.showManualCoords || state.packetType == PacketType.SOS_LOC) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          OutlinedTextField(
            value = state.coordinates,
            onValueChange = onCoordinatesChange,
            label = {
              Text(stringResource(R.string.tx_coords_label), fontFamily = FontFamily.Monospace)
            },
            placeholder = {
              Text(
                stringResource(R.string.tx_coords_placeholder),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
              )
            },
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                color = AmberSync,
              ),
            colors = tacticalTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("coordinates_input"),
          )
          if (state.gpsStatusNote != null) {
            Text(
              text = state.gpsStatusNote,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = AmberSync,
            )
          }
        }
      }
    }

    // Live Hex / Duration / CRC Preview Card
    TacticalCard {
      SectionHeader(title = "2. AUDIO PREVIEW", trailing = "OFFLINE READY")

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TelemetryStatBox(
          label = "SOUND TIME",
          value = "${frame.durationMs} ms",
          accent = PhosphorGreen,
          modifier = Modifier.weight(1f),
        )
        TelemetryStatBox(
          label = "PACKET SIZE",
          value = "${frame.frameBytes.size} B",
          accent = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.weight(1f),
        )
        TelemetryStatBox(
          label = "SAFETY CODE",
          value = frame.crcHex,
          accent = AmberSync,
          modifier = Modifier.weight(1f),
        )
      }

      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(OledBlack)
            .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
            .padding(10.dp),
      ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = "WIRE PAYLOAD: ${frame.payloadText}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = PhosphorGreen,
          )
          Text(
            text = frame.hexPreview,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = TextSecondary,
          )
        }
      }
    }

    // TX & Loopback Controls
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
      val txButtonColor =
        when {
          state.isTransmitting -> SosRed
          state.packetType == PacketType.SOS_LOC -> SosRed
          else -> PhosphorGreen
        }
      val txTextColor =
        if (state.isTransmitting || state.packetType == PacketType.SOS_LOC) Color.White
        else PhosphorGreenOn

      Button(
        onClick = onTransmitClick,
        shape = RoundedCornerShape(10.dp),
        colors =
          ButtonDefaults.buttonColors(
            containerColor = txButtonColor,
            contentColor = txTextColor,
          ),
        modifier = Modifier.fillMaxWidth().height(54.dp).testTag("transmit_fsk_button"),
      ) {
        Icon(
          imageVector = if (state.isTransmitting) Icons.Rounded.Stop else Icons.Rounded.GraphicEq,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text =
            if (state.isTransmitting) stringResource(R.string.tx_stop_button)
            else stringResource(R.string.tx_transmit_button),
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          letterSpacing = 0.8.sp,
        )
      }

      if (state.isTransmitting) {
        LinearProgressIndicator(
          progress = { state.txProgress },
          modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
          color = txButtonColor,
          trackColor = TacticalBorder,
        )
      }

      OutlinedButton(
        onClick = onLoopbackClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.5.dp, AmberSync),
        colors =
          ButtonDefaults.outlinedButtonColors(
            containerColor = TacticalCardElevated,
            contentColor = AmberSync,
          ),
        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("loopback_self_test_button"),
      ) {
        Icon(
          imageVector = Icons.Rounded.Loop,
          contentDescription = "Run 1-Tap Loopback Self-Test",
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = stringResource(R.string.tx_loopback_button),
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          letterSpacing = 0.7.sp,
        )
      }
    }

    if (state.loopbackResult != null) {
      LoopbackResultCard(result = state.loopbackResult)
    }

    // Optional Opt-In Online Relay Card
    TacticalCard(borderColor = if (state.onlineRelayEnabled) AmberSync.copy(alpha = 0.65f) else TacticalBorder) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          SectionHeader(title = "3. ONLINE RELAY (OPT-IN)")
          Text(
            text = "Off by default. Turn ON only to also send/check messages via an HTTPS relay (e.g. private ntfy.sh room).",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
          checked = state.onlineRelayEnabled,
          onCheckedChange = onOnlineRelayToggle,
          colors =
            SwitchDefaults.colors(
              checkedThumbColor = PhosphorGreenOn,
              checkedTrackColor = PhosphorGreen,
              uncheckedThumbColor = TextSecondary,
              uncheckedTrackColor = OledBlack,
              uncheckedBorderColor = TacticalBorderBright,
            ),
          modifier = Modifier.testTag("online_relay_switch"),
        )
      }

      AnimatedVisibility(visible = state.onlineRelayEnabled) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = state.onlineRelayUrl,
            onValueChange = onOnlineRelayUrlChange,
            label = { Text("HTTPS Relay URL", fontFamily = FontFamily.Monospace) },
            placeholder = { Text("https://ntfy.sh/your-private-room", fontSize = 12.sp) },
            singleLine = true,
            textStyle =
              MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                color = AmberSync,
              ),
            colors = tacticalTextFieldColors(),
            modifier = Modifier.fillMaxWidth().testTag("online_relay_url_input"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = onGenerateRandomTopic,
              border = BorderStroke(1.dp, TacticalBorderBright),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(1f).heightIn(min = 44.dp),
            ) {
              Text("New Private Room", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            OutlinedButton(
              onClick = onCheckOnlineInbox,
              enabled = !state.isOnlineBusy,
              border = BorderStroke(1.dp, PhosphorGreen.copy(alpha = 0.6f)),
              shape = RoundedCornerShape(8.dp),
              modifier = Modifier.weight(1f).heightIn(min = 44.dp),
            ) {
              Text("Check Inbox", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = PhosphorGreen)
            }
          }

          Button(
            onClick = onSendOnlineCopy,
            enabled = !state.isOnlineBusy,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmberSync, contentColor = Color.Black),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("send_online_button"),
          ) {
            Text(
              text = if (state.isOnlineBusy) "Connecting via HTTPS…" else "Send Copy Online Now",
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 12.sp,
            )
          }

          if (state.onlineStatusNote != null) {
            Text(
              text = state.onlineStatusNote,
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = TextSecondary,
            )
          }

          if (state.onlineInboxMessages.isNotEmpty()) {
            Column(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(6.dp))
                  .background(OledBlack)
                  .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
                  .padding(8.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Text(
                text = "RECENT ONLINE MESSAGES",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                color = PhosphorGreen,
              )
              state.onlineInboxMessages.take(5).forEach { msg ->
                Text(
                  text = "• $msg",
                  fontFamily = FontFamily.Monospace,
                  fontSize = 11.sp,
                  color = TextSecondary,
                )
              }
            }
          }
        }
      }
    }

    Spacer(modifier = Modifier.height(8.dp))
  }

  if (showGpsRationale) {
    AlertDialog(
      onDismissRequest = { showGpsRationale = false },
      containerColor = TacticalCardElevated,
      title = { Text("Share GPS Coordinates?", color = PhosphorGreen) },
      text = {
        Text(
          text =
            "DeadDrop reads your phone's GPS only once when you tap 'Add My GPS' so you can attach your coordinates to this message. Nothing is tracked in the background or shared automatically.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showGpsRationale = false
            locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
          }
        ) {
          Text("Continue", color = PhosphorGreen)
        }
      },
      dismissButton = {
        TextButton(onClick = { showGpsRationale = false }) {
          Text("Not Now", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun LoopbackResultCard(result: LoopbackTestResult) {
  val accent = if (result.passed) PhosphorGreen else SosRed
  TacticalCard(
    borderColor = accent.copy(alpha = 0.75f),
    modifier = Modifier.testTag("loopback_result_card"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Icon(
          imageVector =
            if (result.passed) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(20.dp),
        )
        Text(
          text =
            if (result.passed) "LOOPBACK SELF-TEST: CRC-16 PASS"
            else "LOOPBACK SELF-TEST: FAILED",
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 12.sp,
          color = accent,
        )
      }
      TacticalBadge(
        text = "${result.elapsedMs}ms",
        color = accent,
        filled = true,
      )
    }

    Text(
      text =
        "TX CRC ${result.expectedCrcHex} == RX CRC ${result.decodedCrcHex} · PHASE #${result.matchedPhase} · ${result.sampleCount} SAMPLES",
      fontFamily = FontFamily.Monospace,
      fontSize = 11.sp,
      color = TextSecondary,
    )

    Text(
      text = "DECODED: ${result.decodedPayload}",
      fontFamily = FontFamily.Monospace,
      fontSize = 12.sp,
      color = MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun TelemetryStatBox(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier =
      modifier
        .clip(RoundedCornerShape(6.dp))
        .background(OledBlack)
        .border(1.dp, TacticalBorder, RoundedCornerShape(6.dp))
        .padding(horizontal = 10.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = label,
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      color = TextMuted,
    )
    Text(
      text = value,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.Bold,
      fontSize = 13.sp,
      color = accent,
    )
  }
}

@Composable
internal fun tacticalTextFieldColors() =
  OutlinedTextFieldDefaults.colors(
    focusedBorderColor = PhosphorGreen,
    unfocusedBorderColor = TacticalBorderBright,
    focusedLabelColor = PhosphorGreen,
    unfocusedLabelColor = TextSecondary,
    cursorColor = PhosphorGreen,
    focusedContainerColor = OledBlack,
    unfocusedContainerColor = OledBlack,
  )
