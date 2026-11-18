package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.dsp.DecodedPacket
import com.example.dsp.PacketType
import com.example.ui.DeadDropUiState
import com.example.ui.RxSyncStatus
import com.example.ui.components.SectionHeader
import com.example.ui.components.TacticalBadge
import com.example.ui.components.TacticalCard
import com.example.ui.theme.AmberSync
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.PhosphorGreenOn
import com.example.ui.theme.SosRed
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DecoderRxScreen(
  state: DeadDropUiState,
  onToggleRx: (Boolean) -> Unit,
  onClearFeed: () -> Unit,
  onCopyPacket: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val clipboardManager = LocalClipboardManager.current

  var hasMicPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    )
  }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        hasMicPermission =
          ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val micPermissionLauncher =
    rememberLauncherForActivityResult(
      contract = ActivityResultContracts.RequestPermission(),
      onResult = { granted ->
        hasMicPermission = granted
        if (granted) {
          onToggleRx(true)
        }
      },
    )

  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (!hasMicPermission) {
      item {
        TacticalCard(borderColor = AmberSync.copy(alpha = 0.7f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Mic,
              contentDescription = null,
              tint = AmberSync,
              modifier = Modifier.size(24.dp),
            )
            Text(
              text = stringResource(R.string.rx_permission_title),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = AmberSync,
            )
          }
          Text(
            text = stringResource(R.string.rx_permission_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Button(
            onClick = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
            shape = RoundedCornerShape(8.dp),
            colors =
              ButtonDefaults.buttonColors(
                containerColor = AmberSync,
                contentColor = Color.Black,
              ),
            modifier = Modifier.fillMaxWidth().height(48.dp).testTag("grant_mic_button"),
          ) {
            Text(
              text = stringResource(R.string.rx_permission_grant),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
            )
          }
        }
      }
    }

    item {
      TacticalCard {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          SectionHeader(
            title = "SOUND-TO-TEXT LISTENER",
            modifier = Modifier.weight(1f),
          )
          val badgeColor =
            when (state.rxSyncStatus) {
              RxSyncStatus.STANDBY -> TextMuted
              RxSyncStatus.SCANNING -> PhosphorGreen
              RxSyncStatus.SYNC_LOCK -> AmberSync
              RxSyncStatus.CRC_PASS -> PhosphorGreen
              RxSyncStatus.CRC_FAIL -> SosRed
            }
          TacticalBadge(
            text = state.rxSyncStatus.label,
            color = badgeColor,
            filled = state.isRxListening,
            dotColor = if (state.isRxListening) badgeColor else null,
            modifier = Modifier.testTag("rx_sync_badge"),
          )
        }

        Button(
          onClick = {
            if (!hasMicPermission) {
              micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
              onToggleRx(true)
            }
          },
          shape = RoundedCornerShape(10.dp),
          colors =
            ButtonDefaults.buttonColors(
              containerColor = if (state.isRxListening) SosRed else PhosphorGreen,
              contentColor = if (state.isRxListening) Color.White else PhosphorGreenOn,
            ),
          modifier = Modifier.fillMaxWidth().height(50.dp).testTag("rx_toggle_button"),
        ) {
          Icon(
            imageVector = if (state.isRxListening) Icons.Rounded.Stop else Icons.Rounded.Hearing,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text =
              if (state.isRxListening) stringResource(R.string.rx_disarm_listener)
              else stringResource(R.string.rx_arm_listener),
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            letterSpacing = 0.8.sp,
          )
        }

        if (state.rxError != null) {
          Text(
            text = "RX ERROR: ${state.rxError}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = SosRed,
          )
        }

        // 1200 Hz (MARK) & 2200 Hz (SPACE) Goertzel Energy Bars
        ToneEnergyBars(
          markLevel = state.rxTelemetry.markLevel,
          spaceLevel = state.rxTelemetry.spaceLevel,
        )

        // 48-Bar Real-Time Waveform Canvas
        WaveformCanvas(
          bars = state.rxTelemetry.waveformBars,
          syncLocked = state.rxSyncStatus == RxSyncStatus.SYNC_LOCK,
          active = state.isRxListening,
        )

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = "VERIFIED OK: ${state.rxValidCount}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = PhosphorGreen,
          )
          Text(
            text = "BAD SIGNAL: ${state.rxCrcFailCount}",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = if (state.rxCrcFailCount > 0) SosRed else TextMuted,
          )
          Text(
            text = "44.1kHz · 147 SMP/BIT",
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = TextMuted,
          )
        }
      }
    }

    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          text = "DECODED PACKETS (${state.decodedPackets.size})",
          style = MaterialTheme.typography.labelMedium,
          color = TextSecondary,
        )
        if (state.decodedPackets.isNotEmpty()) {
          TextButton(
            onClick = onClearFeed,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.testTag("rx_clear_button"),
          ) {
            Icon(
              imageVector = Icons.Rounded.DeleteSweep,
              contentDescription = "Clear decoded feed",
              tint = TextSecondary,
              modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "CLEAR",
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              color = TextSecondary,
            )
          }
        }
      }
    }

    if (state.decodedPackets.isEmpty()) {
      item {
        TacticalCard {
          Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              imageVector = Icons.Rounded.Sensors,
              contentDescription = null,
              tint = TextMuted,
              modifier = Modifier.size(34.dp),
            )
            Text(
              text = stringResource(R.string.rx_empty_title),
              fontFamily = FontFamily.Monospace,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = TextSecondary,
            )
            Text(
              text = stringResource(R.string.rx_empty_hint),
              style = MaterialTheme.typography.bodyMedium,
              color = TextMuted,
              textAlign = TextAlign.Center,
            )
          }
        }
      }
    } else {
      items(state.decodedPackets, key = { "${it.timestampMs}_${it.crc16Actual}_${it.matchedPhase}" }) {
        packet ->
        DecodedPacketCard(
          packet = packet,
          onCopy = {
            clipboardManager.setText(AnnotatedString(packet.payloadText))
            onCopyPacket("Copied payload to clipboard")
          },
        )
      }
    }
  }
}

@Composable
private fun ToneEnergyBars(markLevel: Float, spaceLevel: Float) {
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(8.dp))
        .background(OledBlack)
        .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
        .padding(10.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ToneMeterRow(
      label = "LOW TONE · 1200 Hz (BIT 1)",
      level = markLevel,
      barColor = PhosphorGreen,
    )
    ToneMeterRow(
      label = "HIGH TONE · 2200 Hz (BIT 0)",
      level = spaceLevel,
      barColor = AmberSync,
    )
  }
}

@Composable
private fun ToneMeterRow(label: String, level: Float, barColor: Color) {
  val clamped = level.coerceIn(0f, 1f)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(
        text = label,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = TextSecondary,
      )
      Text(
        text = "${(clamped * 100).toInt()}%",
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = barColor,
      )
    }
    Canvas(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(10.dp)
          .clip(RoundedCornerShape(3.dp))
          .background(Color(0xFF0B1410)),
    ) {
      val fillW = size.width * clamped
      if (fillW > 0f) {
        drawRoundRect(
          color = barColor,
          topLeft = Offset.Zero,
          size = Size(fillW, size.height),
          cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
        )
      }
    }
  }
}

@Composable
private fun WaveformCanvas(bars: List<Float>, syncLocked: Boolean, active: Boolean) {
  val barColor =
    when {
      !active -> TextMuted.copy(alpha = 0.35f)
      syncLocked -> AmberSync
      else -> PhosphorGreen
    }

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .height(92.dp)
        .clip(RoundedCornerShape(8.dp))
        .background(OledBlack)
        .border(1.dp, TacticalBorder, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 8.dp),
  ) {
    Canvas(modifier = Modifier.fillMaxSize().testTag("rx_waveform_canvas")) {
      val count = 48
      val slotW = size.width / count
      val barW = (slotW * 0.65f).coerceAtLeast(2f)
      val midY = size.height / 2f

      drawLine(
        color = TacticalBorder,
        start = Offset(0f, midY),
        end = Offset(size.width, midY),
        strokeWidth = 1.dp.toPx(),
      )

      for (i in 0 until count) {
        val amp = (bars.getOrNull(i) ?: 0f).coerceIn(0.04f, 1f)
        val halfH = (size.height * 0.46f) * amp
        val x = i * slotW + (slotW - barW) / 2f
        drawRoundRect(
          color = barColor,
          topLeft = Offset(x, midY - halfH),
          size = Size(barW, halfH * 2f),
          cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
        )
      }
    }

    Row(
      modifier = Modifier.align(Alignment.TopEnd),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Icon(
        imageVector = Icons.Rounded.GraphicEq,
        contentDescription = null,
        tint = barColor,
        modifier = Modifier.size(12.dp),
      )
      Text(
        text = "48-CH SCOPE",
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        color = TextMuted,
      )
    }
  }
}

@Composable
private fun DecodedPacketCard(packet: DecodedPacket, onCopy: () -> Unit) {
  val fmt = remember { SimpleDateFormat("HH:mm:ss", Locale.US) }
  val isSos = packet.type == PacketType.SOS_LOC
  val border = if (isSos) SosRed.copy(alpha = 0.8f) else TacticalBorder

  TacticalCard(borderColor = border) {
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
          text = packet.type.label,
          color = if (isSos) SosRed else PhosphorGreen,
          filled = true,
        )
        TacticalBadge(
          text = "CRC ${packet.crcHex} OK",
          color = PhosphorGreen,
        )
      }

      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = "${fmt.format(Date(packet.timestampMs))} · P${packet.matchedPhase}",
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = TextMuted,
        )
        IconButton(
          onClick = onCopy,
          modifier = Modifier.size(36.dp),
        ) {
          Icon(
            imageVector = Icons.Rounded.ContentCopy,
            contentDescription = "Copy decoded packet",
            tint = TextSecondary,
            modifier = Modifier.size(16.dp),
          )
        }
      }
    }

    Text(
      text = packet.payloadText,
      fontFamily = FontFamily.Monospace,
      fontWeight = FontWeight.SemiBold,
      fontSize = 14.sp,
      color = MaterialTheme.colorScheme.onSurface,
    )

    Text(
      text = packet.hexFrame,
      fontFamily = FontFamily.Monospace,
      fontSize = 10.sp,
      lineHeight = 14.sp,
      color = TextMuted,
      maxLines = 2,
    )
  }
}
