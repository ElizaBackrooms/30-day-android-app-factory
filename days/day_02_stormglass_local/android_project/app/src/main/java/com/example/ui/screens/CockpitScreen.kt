package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.PressureTrend
import com.example.model.TimeWindow
import com.example.ui.StormGlassUiState
import com.example.ui.components.BarographCard
import com.example.ui.components.HardwareStatusBadge
import com.example.ui.components.QuickManualLogBar
import com.example.ui.components.SquallAlertBanner
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanData
import com.example.ui.theme.SensorGreen
import com.example.ui.theme.SquallCrimson
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceBorder
import com.example.ui.theme.TacticalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun CockpitScreen(
  state: StormGlassUiState,
  onToggleUnit: () -> Unit,
  onSelectWindow: (TimeWindow) -> Unit,
  onNudgeManual: (Float) -> Unit,
  onSetExactManual: (Float) -> Unit,
  onLogReading: () -> Unit,
  onLoadDemo: () -> Unit,
  onOpenForecaster: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val forecast = state.forecast
  val deltaP = forecast?.deltaP3h
  val deltaColor =
    when {
      state.isSquallWarning -> SquallCrimson
      forecast?.trend == PressureTrend.FALLING -> AmberAccent
      forecast?.trend == PressureTrend.RISING -> SensorGreen
      else -> CyanData
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
      HardwareStatusBadge(
        hasHardwareSensor = state.hasHardwareSensor,
        unit = state.unit,
        onToggleUnit = onToggleUnit,
      )

      SquallAlertBanner(
        visible = state.isSquallWarning,
        deltaP3h = deltaP,
        unit = state.unit,
        onOpenReadiness = onOpenForecaster,
      )

      // Station Pressure & MSLP Hero Card
      Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(16.dp),
        border =
          BorderStroke(
            1.dp,
            if (state.isSquallWarning) SquallCrimson.copy(alpha = 0.65f) else TacticalSurfaceBorder,
          ),
        modifier = Modifier.fillMaxWidth().testTag("pressure_readout_card"),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
          ) {
            Column {
              Text(
                text = "LOCAL AIR PRESSURE",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
              )
              Spacer(modifier = Modifier.height(4.dp))
              Row(verticalAlignment = Alignment.Bottom) {
                Text(
                  text = state.unit.formatValue(state.currentStationPressureHpa),
                  style = MaterialTheme.typography.displayLarge,
                  color = TextPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = state.unit.label,
                  style = MaterialTheme.typography.titleMedium,
                  color = CyanData,
                  modifier = Modifier.padding(bottom = 6.dp),
                )
              }
            }

            Surface(
              color = deltaColor.copy(alpha = 0.14f),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, deltaColor.copy(alpha = 0.45f)),
            ) {
              Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.End,
              ) {
                Text(
                  text =
                    if (forecast?.isThreeHourWindowComplete == true) "3-HR CHANGE" else "RECENT CHANGE",
                  style = MaterialTheme.typography.labelSmall,
                  color = TextSecondary,
                )
                Text(
                  text = deltaP?.let { state.unit.formatDelta(it) } ?: "--",
                  style = MaterialTheme.typography.titleMedium,
                  color = deltaColor,
                  fontWeight = FontWeight.Bold,
                )
              }
            }
          }

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            MetricMiniTile(
              label = "SEA LEVEL EQUIV. (${String.format(Locale.US, "%.0f", state.elevationMeters)}m)",
              value = state.unit.formatWithUnit(state.currentMslpHpa),
              accent = CyanData,
              modifier = Modifier.weight(1f),
            )
            MetricMiniTile(
              label = "WEATHER OUTLOOK (#${forecast?.index?.toString()?.padStart(2, '0') ?: "--"})",
              value = forecast?.title ?: "Awaiting Log",
              accent = if (state.isSquallWarning) SquallCrimson else AmberAccent,
              modifier = Modifier.weight(1.35f),
            )
          }
        }
      }

      BarographCard(
        readings = state.readings,
        selectedWindow = state.selectedWindow,
        unit = state.unit,
        trend = forecast?.trend ?: PressureTrend.STEADY,
        isSquallWarning = state.isSquallWarning,
        onSelectWindow = onSelectWindow,
        onLoadDemo = onLoadDemo,
      )

      QuickManualLogBar(
        stagedPressureHpa = state.manualInputPressureHpa,
        unit = state.unit,
        hasHardwareSensor = state.hasHardwareSensor,
        onNudge = onNudgeManual,
        onSetExact = onSetExactManual,
        onLogReading = onLogReading,
      )

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun MetricMiniTile(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier,
) {
  Surface(
    color = TacticalSurfaceElevated,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, TacticalSurfaceBorder),
    modifier = modifier,
  ) {
    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = TextMuted,
        maxLines = 1,
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        color = accent,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
      )
    }
  }
}
