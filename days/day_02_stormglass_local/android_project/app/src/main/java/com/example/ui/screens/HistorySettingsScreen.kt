package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ZambrettiEngine
import com.example.ui.StormGlassUiState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CyanData
import com.example.ui.theme.CyanDim
import com.example.ui.theme.SensorGreen
import com.example.ui.theme.SquallCrimson
import com.example.ui.theme.TacticalBackground
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceBorder
import com.example.ui.theme.TacticalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistorySettingsScreen(
  state: StormGlassUiState,
  onThresholdChange: (Float) -> Unit,
  onPersistOptInChange: (Boolean) -> Unit,
  onTestSoundAlert: () -> Unit,
  onExportCsv: () -> Unit,
  onGenerateDemoData: () -> Unit,
  onClearHistory: () -> Unit,
  onDeleteReading: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var showClearDialog by remember { mutableStateOf(false) }
  val reversedReadings = remember(state.readings) { state.readings.sortedByDescending { it.timestamp } }
  val clockFormat = remember { SimpleDateFormat("MMM d, HH:mm", Locale.US) }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.widthIn(max = 680.dp).fillMaxSize(),
      contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      item {
        Surface(
          color = TacticalSurface,
          shape = RoundedCornerShape(16.dp),
          border =
            BorderStroke(
              1.dp,
              if (state.persistLocalData) AmberAccent.copy(alpha = 0.6f) else TacticalSurfaceBorder,
            ),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = CyanData,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "PRIVACY & STORAGE (OPT-IN ONLY)",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Save History & Settings on Phone",
                  style = MaterialTheme.typography.bodyMedium,
                  fontWeight = FontWeight.SemiBold,
                  color = TextPrimary,
                )
                Text(
                  text =
                    if (state.persistLocalData) "Stored only on this device. Zero cloud sync."
                    else "OFF by default. Readings clear automatically when you close the app.",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
              }
              Spacer(modifier = Modifier.width(8.dp))
              Switch(
                checked = state.persistLocalData,
                onCheckedChange = onPersistOptInChange,
                colors =
                  SwitchDefaults.colors(
                    checkedThumbColor = TacticalBackground,
                    checkedTrackColor = SensorGreen,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = TacticalBackground,
                  ),
                modifier = Modifier.testTag("persist_opt_in_switch"),
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = {
                try {
                  context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(StormGlassUiState.PRIVACY_POLICY_URL))
                  )
                } catch (_: Throwable) {}
              },
              border = BorderStroke(1.dp, TacticalSurfaceBorder),
              shape = RoundedCornerShape(10.dp),
              modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("privacy_policy_button"),
            ) {
              Text("Open Privacy Policy", color = CyanData, style = MaterialTheme.typography.labelLarge)
            }
          }
        }
      }

      item {
        Surface(
          color = TacticalSurface,
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, TacticalSurfaceBorder),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = SquallCrimson,
                modifier = Modifier.size(20.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "SQUALL ALARM THRESHOLD",
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
              )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = "Trigger on 3h drop <=",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
              Text(
                text = "${String.format(Locale.US, "%.1f", state.squallThresholdHpa)} hPa / 3h",
                style = MaterialTheme.typography.titleMedium,
                color = SquallCrimson,
                fontWeight = FontWeight.Bold,
              )
            }

            // Slider goes from -4.0 (strict/severe only) to -1.0 (sensitive)
            Slider(
              value = state.squallThresholdHpa,
              onValueChange = onThresholdChange,
              valueRange = -4.0f..-1.0f,
              steps = 5,
              colors =
                SliderDefaults.colors(
                  thumbColor = SquallCrimson,
                  activeTrackColor = SquallCrimson,
                  inactiveTrackColor = TacticalSurfaceElevated,
                ),
              modifier = Modifier.fillMaxWidth().testTag("squall_threshold_slider"),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Button(
              onClick = onTestSoundAlert,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = TacticalSurfaceElevated,
                  contentColor = AmberAccent,
                ),
              border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.6f)),
              shape = RoundedCornerShape(10.dp),
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("test_sound_alert_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.test_sound_alert),
                style = MaterialTheme.typography.labelLarge,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
              onClick = onExportCsv,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = TacticalSurfaceElevated,
                  contentColor = CyanData,
                ),
              border = BorderStroke(1.dp, CyanData.copy(alpha = 0.55f)),
              shape = RoundedCornerShape(10.dp),
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("export_csv_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.export_csv_clipboard),
                style = MaterialTheme.typography.labelLarge,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Button(
              onClick = onGenerateDemoData,
              colors =
                ButtonDefaults.buttonColors(
                  containerColor = AmberAccent,
                  contentColor = TacticalBackground,
                ),
              shape = RoundedCornerShape(10.dp),
              modifier =
                Modifier.fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("generate_demo_data_button"),
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.generate_demo_data),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
              )
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
              onClick = { showClearDialog = true },
              border = BorderStroke(1.dp, SquallCrimson.copy(alpha = 0.65f)),
              shape = RoundedCornerShape(10.dp),
              modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("clear_history_button"),
            ) {
              Icon(
                imageVector = Icons.Filled.DeleteSweep,
                contentDescription = null,
                tint = SquallCrimson,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = stringResource(R.string.clear_history),
                style = MaterialTheme.typography.labelLarge,
                color = SquallCrimson,
              )
            }
          }
        }
      }

      item {
        Row(
          modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            text = "HISTORICAL LOG (${reversedReadings.size} / 100)",
            style = MaterialTheme.typography.labelLarge,
            color = TextSecondary,
          )
          Text(
            text = "LOCAL JSON",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
          )
        }
      }

      if (reversedReadings.isEmpty()) {
        item {
          Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, TacticalSurfaceBorder),
            modifier = Modifier.fillMaxWidth(),
          ) {
            Column(
              modifier = Modifier.padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
            ) {
              Text(
                text = "No pressure readings recorded yet.",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Log a reading on the Cockpit tab or tap Generate Demo Data above.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
              )
            }
          }
        }
      } else {
        itemsIndexed(
          items = reversedReadings,
          key = { index, item -> "${item.timestamp}_$index" },
        ) { index, reading ->
          val olderReading = reversedReadings.getOrNull(index + 1)
          val stepDelta = olderReading?.let { reading.pressureHpa - it.pressureHpa }
          val relative =
            DateUtils.getRelativeTimeSpanString(
              reading.timestamp,
              System.currentTimeMillis(),
              DateUtils.MINUTE_IN_MILLIS,
            ).toString()
          val mslp = ZambrettiEngine.calculateMslp(reading.pressureHpa, state.elevationMeters)

          Surface(
            color = TacticalSurface,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, TacticalSurfaceBorder),
            modifier = Modifier.fillMaxWidth().testTag("history_row_$index"),
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Surface(
                    color = if (reading.isManual) AmberDim else CyanDim,
                    shape = RoundedCornerShape(6.dp),
                  ) {
                    Text(
                      text = if (reading.isManual) "[MANUAL]" else "[AUTO]",
                      style = MaterialTheme.typography.labelSmall,
                      color = if (reading.isManual) AmberAccent else CyanData,
                      fontWeight = FontWeight.Bold,
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                  }
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = relative,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "• ${clockFormat.format(Date(reading.timestamp))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                  )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                  Text(
                    text = state.unit.formatWithUnit(reading.pressureHpa),
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = "MSLP ${state.unit.formatWithUnit(mslp)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextMuted,
                  )
                }

                if (reading.note.isNotBlank()) {
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = reading.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                  )
                }
              }

              Column(horizontalAlignment = Alignment.End) {
                if (stepDelta != null) {
                  val deltaColor =
                    when {
                      stepDelta <= -0.8f -> SquallCrimson
                      stepDelta < 0f -> AmberAccent
                      stepDelta > 0.2f -> SensorGreen
                      else -> CyanData
                    }
                  Text(
                    text = "Δ ${state.unit.formatDelta(stepDelta)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = deltaColor,
                    fontWeight = FontWeight.Bold,
                  )
                } else {
                  Text(
                    text = "BASE",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                  )
                }

                IconButton(
                  onClick = { onDeleteReading(reading.timestamp) },
                  modifier = Modifier.size(48.dp),
                ) {
                  Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete reading",
                    tint = TextMuted,
                    modifier = Modifier.size(18.dp),
                  )
                }
              }
            }
          }
        }
      }

      item { Spacer(modifier = Modifier.height(8.dp)) }
    }
  }

  if (showClearDialog) {
    AlertDialog(
      onDismissRequest = { showClearDialog = false },
      containerColor = TacticalSurface,
      title = {
        Text(
          text = "Wipe All Local Data & Settings?",
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
        )
      },
      text = {
        Text(
          text =
            "This deletes all saved readings, altitude/wind settings, typed coordinates, and turns off persistence. This cannot be undone.",
          style = MaterialTheme.typography.bodyMedium,
          color = TextSecondary,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onClearHistory()
            showClearDialog = false
          }
        ) {
          Text("Wipe All", color = SquallCrimson)
        }
      },
      dismissButton = {
        TextButton(onClick = { showClearDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
    )
  }
}
