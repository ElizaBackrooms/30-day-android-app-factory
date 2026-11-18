package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.ThreatLevel
import com.example.model.WindDirection
import com.example.ui.StormGlassUiState
import com.example.ui.components.TrendIconBadge
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanData
import com.example.ui.theme.SensorGreen
import com.example.ui.theme.SquallCrimson
import com.example.ui.theme.TacticalBackground
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceBorder
import com.example.ui.theme.TacticalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ForecasterScreen(
  state: StormGlassUiState,
  onElevationChange: (Float) -> Unit,
  onWindChange: (WindDirection) -> Unit,
  onSeasonChange: (Boolean) -> Unit,
  onToggleChecklist: (String) -> Unit,
  onOnlineWeatherToggle: (Boolean) -> Unit,
  onLatitudeChange: (String) -> Unit,
  onLongitudeChange: (String) -> Unit,
  onFetchLiveStation: () -> Unit,
  onApplyOnlineWind: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val forecast = state.forecast
  val threatColor =
    when (forecast?.threatLevel) {
      ThreatLevel.SEVERE -> SquallCrimson
      ThreatLevel.HIGH -> AmberAccent
      ThreatLevel.MODERATE -> CyanData
      ThreatLevel.LOW, null -> SensorGreen
    }

  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier =
        Modifier.widthIn(max = 680.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      // Zambretti Forecast Card
      Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, threatColor.copy(alpha = 0.55f)),
        modifier = Modifier.fillMaxWidth().testTag("zambretti_forecast_card"),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Surface(
              color = threatColor.copy(alpha = 0.16f),
              shape = RoundedCornerShape(8.dp),
              border = BorderStroke(1.dp, threatColor.copy(alpha = 0.5f)),
            ) {
              Text(
                text = "ZAMBRETTI INDEX ${forecast?.index ?: "--"} / 26",
                style = MaterialTheme.typography.labelLarge,
                color = threatColor,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
              )
            }
            if (forecast != null) {
              TrendIconBadge(trend = forecast.trend)
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Text(
            text = forecast?.title ?: "Awaiting Barometric Samples",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              forecast?.maritimeSummary
                ?: "Log at least two readings to activate Negretti & Zambra 1915 tendency forecasting.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
          )

          if (forecast != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "CONFIDENCE",
                style = MaterialTheme.typography.labelSmall,
                color = TextMuted,
              )
              Text(
                text = "${forecast.confidencePercent}% • ${forecast.threatLevel.label}",
                style = MaterialTheme.typography.labelMedium,
                color = threatColor,
                fontWeight = FontWeight.Bold,
              )
            }
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
              progress = { forecast.confidencePercent / 100f },
              color = threatColor,
              trackColor = TacticalSurfaceElevated,
              modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )

            Spacer(modifier = Modifier.height(12.dp))
            Surface(
              color = TacticalBackground,
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, TacticalSurfaceBorder),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Column(modifier = Modifier.padding(12.dp)) {
                Text(
                  text = "TENDENCY BREAKDOWN",
                  style = MaterialTheme.typography.labelSmall,
                  color = CyanData,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                  text = forecast.tendencyExplanation,
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondary,
                )
              }
            }
          }
        }
      }

      // Calibration Card: Elevation, Wind Direction, Season
      Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TacticalSurfaceBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.Terrain,
              contentDescription = null,
              tint = CyanData,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "YOUR ALTITUDE (ABOVE SEA LEVEL)",
              style = MaterialTheme.typography.labelLarge,
              color = TextPrimary,
            )
          }

          Spacer(modifier = Modifier.height(6.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "${state.elevationMeters.roundToInt()} m",
              style = MaterialTheme.typography.displaySmall,
              color = CyanData,
            )
            Text(
              text = "MSLP: ${state.unit.formatWithUnit(state.currentMslpHpa)}",
              style = MaterialTheme.typography.labelLarge,
              color = AmberAccent,
            )
          }

          Slider(
            value = state.elevationMeters,
            onValueChange = onElevationChange,
            valueRange = 0f..3000f,
            colors =
              SliderDefaults.colors(
                thumbColor = CyanData,
                activeTrackColor = CyanData,
                inactiveTrackColor = TacticalSurfaceElevated,
              ),
            modifier = Modifier.fillMaxWidth().testTag("elevation_slider"),
          )

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            OutlinedButton(
              onClick = { onElevationChange((state.elevationMeters - 100f).coerceAtLeast(0f)) },
              border = BorderStroke(1.dp, TacticalSurfaceBorder),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
              Text("-100 m", color = TextSecondary)
            }
            OutlinedButton(
              onClick = { onElevationChange(0f) },
              border = BorderStroke(1.dp, TacticalSurfaceBorder),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
              Text("Sea Level", color = CyanData)
            }
            OutlinedButton(
              onClick = { onElevationChange((state.elevationMeters + 100f).coerceAtMost(3000f)) },
              border = BorderStroke(1.dp, TacticalSurfaceBorder),
              modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
              Text("+100 m", color = TextSecondary)
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.Explore,
              contentDescription = null,
              tint = AmberAccent,
              modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "WIND DIRECTION (BLOWING FROM)",
              style = MaterialTheme.typography.labelLarge,
              color = TextPrimary,
            )
          }
          Spacer(modifier = Modifier.height(8.dp))

          FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            WindDirection.entries.forEach { dir ->
              val selected = dir == state.windDirection
              val modLabel =
                when {
                  dir.quadrantModifier > 0 -> "+${dir.quadrantModifier}"
                  else -> "${dir.quadrantModifier}"
                }
              Surface(
                onClick = { onWindChange(dir) },
                color =
                  if (selected) AmberAccent.copy(alpha = 0.2f) else TacticalSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border =
                  BorderStroke(
                    1.dp,
                    if (selected) AmberAccent else TacticalSurfaceBorder,
                  ),
                modifier =
                  Modifier.heightIn(min = 48.dp)
                    .testTag("wind_${dir.code.lowercase(Locale.US)}"),
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                ) {
                  Text(
                    text = dir.code,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) AmberAccent else TextPrimary,
                    fontWeight = FontWeight.Bold,
                  )
                  Spacer(modifier = Modifier.width(4.dp))
                  Text(
                    text = "($modLabel)",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) AmberAccent else TextMuted,
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(16.dp))

          Text(
            text = "SEASONAL BIAS",
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(8.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            SeasonToggleChip(
              label = "Summer (+1)",
              icon = Icons.Filled.WbSunny,
              selected = state.isSummer,
              onClick = { onSeasonChange(true) },
              modifier = Modifier.weight(1f).testTag("season_summer_button"),
            )
            SeasonToggleChip(
              label = "Winter (-1)",
              icon = Icons.Filled.AcUnit,
              selected = !state.isSummer,
              onClick = { onSeasonChange(false) },
              modifier = Modifier.weight(1f).testTag("season_winter_button"),
            )
          }
        }
      }

      // Optional Live Station Check (Opt-In Only, Open-Meteo HTTPS)
      Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(16.dp),
        border =
          BorderStroke(
            1.dp,
            if (state.onlineWeatherEnabled) CyanData.copy(alpha = 0.55f) else TacticalSurfaceBorder,
          ),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.CloudSync,
                contentDescription = null,
                tint = CyanData,
                modifier = Modifier.size(18.dp),
              )
              Spacer(modifier = Modifier.width(8.dp))
              Column {
                Text(
                  text = "COMPARE WITH LIVE WEATHER STATION (OPT-IN)",
                  style = MaterialTheme.typography.labelLarge,
                  color = TextPrimary,
                )
                Text(
                  text = "Off by default. Uses Open-Meteo HTTPS only when you tap Check.",
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
              checked = state.onlineWeatherEnabled,
              onCheckedChange = onOnlineWeatherToggle,
              colors =
                SwitchDefaults.colors(
                  checkedThumbColor = TacticalBackground,
                  checkedTrackColor = CyanData,
                  uncheckedThumbColor = TextSecondary,
                  uncheckedTrackColor = TacticalBackground,
                ),
              modifier = Modifier.testTag("online_weather_switch"),
            )
          }

          AnimatedVisibility(visible = state.onlineWeatherEnabled) {
            Column(modifier = Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                OutlinedTextField(
                  value = state.latitudeInput,
                  onValueChange = onLatitudeChange,
                  label = { Text("Latitude") },
                  placeholder = { Text("41.88") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.weight(1f).testTag("online_lat_input"),
                )
                OutlinedTextField(
                  value = state.longitudeInput,
                  onValueChange = onLongitudeChange,
                  label = { Text("Longitude") },
                  placeholder = { Text("-87.63") },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                  modifier = Modifier.weight(1f).testTag("online_lon_input"),
                )
              }

              Button(
                onClick = onFetchLiveStation,
                enabled = !state.isFetchingOnline,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = CyanData,
                    contentColor = TacticalBackground,
                  ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("fetch_live_station_button"),
              ) {
                Text(
                  text =
                    if (state.isFetchingOnline) "Checking Open-Meteo…"
                    else "Compare With Live Weather Station",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                )
              }

              if (state.onlineStatusNote != null) {
                Text(
                  text = state.onlineStatusNote,
                  style = MaterialTheme.typography.bodySmall,
                  color = TextSecondary,
                )
              }

              val wx = state.onlineWeather
              if (wx != null) {
                Surface(
                  color = TacticalBackground,
                  shape = RoundedCornerShape(10.dp),
                  border = BorderStroke(1.dp, CyanData.copy(alpha = 0.4f)),
                  modifier = Modifier.fillMaxWidth(),
                ) {
                  Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                      text = "${wx.conditionSummary}${wx.temperatureC?.let { " · ${String.format(Locale.US, "%.1f", it)}°C" } ?: ""}",
                      style = MaterialTheme.typography.titleMedium,
                      color = CyanData,
                      fontWeight = FontWeight.Bold,
                    )
                    Text(
                      text = "Nearest Station Sea-Level Pressure: ${state.unit.formatWithUnit(wx.mslpHpa)}",
                      style = MaterialTheme.typography.bodyMedium,
                      color = TextPrimary,
                    )
                    if (wx.windDegrees != null && wx.suggestedWind != null) {
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        Text(
                          text = "Wind: ${wx.suggestedWind.code} (${wx.windDegrees}° · ${wx.windSpeedKmh ?: "--"} km/h)",
                          style = MaterialTheme.typography.bodySmall,
                          color = TextSecondary,
                        )
                        OutlinedButton(
                          onClick = onApplyOnlineWind,
                          border = BorderStroke(1.dp, AmberAccent),
                          shape = RoundedCornerShape(8.dp),
                        ) {
                          Text("Use Wind", color = AmberAccent, style = MaterialTheme.typography.labelSmall)
                        }
                      }
                    }
                  }
                }
              }
            }
          }
        }
      }

      // Tactical Storm-Readiness Checklist
      Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TacticalSurfaceBorder),
        modifier = Modifier.fillMaxWidth().testTag("readiness_checklist_card"),
      ) {
        val completed = state.checklist.count { it.isChecked }
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = "TACTICAL STORM-READINESS",
              style = MaterialTheme.typography.labelLarge,
              color = AmberAccent,
            )
            Text(
              text = "$completed / ${state.checklist.size} READY",
              style = MaterialTheme.typography.labelMedium,
              color = if (completed == state.checklist.size) SensorGreen else TextSecondary,
            )
          }

          Spacer(modifier = Modifier.height(10.dp))

          state.checklist.forEach { task ->
            Surface(
              color =
                if (task.isChecked) TacticalBackground.copy(alpha = 0.7f)
                else TacticalSurfaceElevated,
              shape = RoundedCornerShape(10.dp),
              border =
                BorderStroke(
                  1.dp,
                  when {
                    task.isChecked -> SensorGreen.copy(alpha = 0.4f)
                    task.isCritical && state.isSquallWarning -> SquallCrimson.copy(alpha = 0.5f)
                    else -> TacticalSurfaceBorder
                  },
                ),
              modifier =
                Modifier.fillMaxWidth()
                  .padding(vertical = 4.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .clickable { onToggleChecklist(task.id) }
                  .testTag("checklist_${task.id}"),
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.Top,
              ) {
                Checkbox(
                  checked = task.isChecked,
                  onCheckedChange = { onToggleChecklist(task.id) },
                  colors =
                    CheckboxDefaults.colors(
                      checkedColor = SensorGreen,
                      uncheckedColor = TextMuted,
                      checkmarkColor = TacticalBackground,
                    ),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                      imageVector =
                        if (task.isChecked) Icons.Filled.CheckCircle
                        else Icons.Filled.RadioButtonUnchecked,
                      contentDescription = null,
                      tint = if (task.isChecked) SensorGreen else TextMuted,
                      modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                      text = task.title,
                      style = MaterialTheme.typography.titleMedium,
                      color = if (task.isChecked) TextMuted else TextPrimary,
                    )
                  }
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = task.detail,
                    style = MaterialTheme.typography.bodyMedium,
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
  }
}

@Composable
private fun SeasonToggleChip(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    color = if (selected) CyanData.copy(alpha = 0.16f) else TacticalSurfaceElevated,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, if (selected) CyanData else TacticalSurfaceBorder),
    modifier = modifier.heightIn(min = 48.dp),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.Center,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = if (selected) CyanData else TextSecondary,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) CyanData else TextSecondary,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      )
    }
  }
}
