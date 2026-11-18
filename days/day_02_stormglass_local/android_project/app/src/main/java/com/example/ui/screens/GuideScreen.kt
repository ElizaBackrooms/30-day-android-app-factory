package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.StormGlassUiState
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanData
import com.example.ui.theme.SensorGreen
import com.example.ui.theme.SquallCrimson
import com.example.ui.theme.TacticalBackground
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceBorder
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun GuideScreen(
  hasHardwareSensor: Boolean,
  onLoadDemoSquallData: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    Column(
      modifier =
        Modifier.widthIn(max = 680.dp)
          .fillMaxWidth()
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 16.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CyanData.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "HOW TO USE STORMGLASS LOCAL",
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text =
              "100% offline barometric weather station built for LineageOS, de-Googled phones, and remote expeditions. Zero internet or cloud services required.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          Text(
            text =
              if (hasHardwareSensor) {
                "Device status: Internal barometer detected. Auto-sampling active."
              } else {
                "Device status: No internal barometer (Manual Logging Mode active)."
              },
            style = MaterialTheme.typography.labelLarge,
            color = if (hasHardwareSensor) SensorGreen else AmberAccent,
          )
        }
      }

      GuideStepCard(
        stepNumber = "1",
        title = "Hardware Sensor vs. Manual Logging Mode",
        icon = Icons.Filled.Sensors,
        accentColor = SensorGreen,
        body =
          "On startup, StormGlass Local checks for a physical pressure sensor. " +
            "If present, it low-pass filters noise and logs [AUTO] samples automatically. " +
            "On devices without a barometer (such as Moto G Stylus 4G variants), the app " +
            "switches to Manual Logging Mode: read hPa/mbar from a wrist barometer (Casio, " +
            "Garmin, Suunto) or NOAA weather radio, adjust with the -1.0 / -0.1 / +0.1 / +1.0 " +
            "nudges or tap the value for direct entry, then tap 'Log Current Reading'.",
      )

      GuideStepCard(
        stepNumber = "2",
        title = "Reading the Barograph & 3-Hour ΔP Trend",
        icon = Icons.Filled.Speed,
        accentColor = CyanData,
        body =
          "Use the 3H, 12H, and 24H chips to inspect short-term squall drops or full-day " +
            "frontal ridges. Drag a finger across the cyan curve to scrub exact timestamps " +
            "and pressures. The 3-hour tendency classifies weather inertia: " +
            "Falling (≤ -1.6 hPa/3h), Steady (-1.6 to +1.6 hPa/3h), or Rising (≥ +1.6 hPa/3h).",
      )

      GuideStepCard(
        stepNumber = "3",
        title = "Elevation, Wind & Season for Zambretti MSLP",
        icon = Icons.Filled.Explore,
        accentColor = AmberAccent,
        body =
          "Open the Forecaster tab and set your approximate elevation (0–3000m) so raw " +
            "station pressure is normalized to Mean Sea Level Pressure (MSLP). Select your " +
            "prevailing wind quadrant (N..NW) and Season (+1 Summer / -1 Winter). The 1915 " +
            "Negretti & Zambra algorithm computes a 1–26 index with confidence and storm-readiness steps.",
      )

      GuideStepCard(
        stepNumber = "4",
        title = "Understanding Squall Warnings (-2.0 hPa / 3h)",
        icon = Icons.Filled.NotificationsActive,
        accentColor = SquallCrimson,
        body =
          "A pressure fall of -2.0 hPa or more within 3 hours signals an approaching squall " +
            "line, cold front, or rapid ridge storm. StormGlass triggers a flashing crimson " +
            "Cockpit banner and sounds a STREAM_ALARM tone via ToneGenerator. Visit the Log " +
            "tab anytime to adjust the sensitivity (-1.0 to -4.0 hPa/3h) or press 'Test Sound Alert'.",
      )

      // Quick-Action Demo Card
      Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "5. QUICK-START FIELD SIMULATION",
            style = MaterialTheme.typography.titleMedium,
            color = AmberAccent,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              "Want to inspect the barograph trace, Zambretti storm index, and squall alarm " +
                "immediately? Load a realistic 24-hour simulated squall drop and jump straight to the Cockpit.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(12.dp))
          Button(
            onClick = onLoadDemoSquallData,
            colors =
              ButtonDefaults.buttonColors(
                containerColor = AmberAccent,
                contentColor = TacticalBackground,
              ),
            shape = RoundedCornerShape(12.dp),
            modifier =
              Modifier.fillMaxWidth()
                .heightIn(min = 50.dp)
                .testTag("guide_load_demo_button"),
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ShowChart,
              contentDescription = null,
              modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.load_demo_squall_data),
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        }
      }

      Surface(
        color = TacticalSurface,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TacticalSurfaceBorder),
        modifier = Modifier.fillMaxWidth(),
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Text(
            text = "6. PRIVACY BY DEFAULT & ONLINE CHECK",
            style = MaterialTheme.typography.titleMedium,
            color = CyanData,
            fontWeight = FontWeight.Bold,
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text =
              "By default, StormGlass saves nothing after you close it and uses zero internet. You can opt into saving readings on your phone (Settings tab) or manually check live Open-Meteo station conditions (Forecast tab).",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              try {
                context.startActivity(
                  Intent(Intent.ACTION_VIEW, Uri.parse(StormGlassUiState.PRIVACY_POLICY_URL))
                )
              } catch (_: Throwable) {}
            },
            border = BorderStroke(1.dp, CyanData.copy(alpha = 0.6f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
          ) {
            Text("Read Privacy Policy", color = CyanData, style = MaterialTheme.typography.labelLarge)
          }
        }
      }

      Spacer(modifier = Modifier.height(8.dp))
    }
  }
}

@Composable
private fun GuideStepCard(
  stepNumber: String,
  title: String,
  icon: ImageVector,
  accentColor: Color,
  body: String,
) {
  Surface(
    color = TacticalSurface,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, TacticalSurfaceBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
          color = accentColor.copy(alpha = 0.18f),
          shape = CircleShape,
          border = BorderStroke(1.dp, accentColor.copy(alpha = 0.6f)),
          modifier = Modifier.size(32.dp),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Text(
              text = stepNumber,
              style = MaterialTheme.typography.labelLarge,
              color = accentColor,
              fontWeight = FontWeight.Bold,
            )
          }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
          text = title,
          style = MaterialTheme.typography.titleMedium,
          color = TextPrimary,
          modifier = Modifier.weight(1f),
        )
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = accentColor,
          modifier = Modifier.size(20.dp),
        )
      }
      Spacer(modifier = Modifier.height(8.dp))
      Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = TextSecondary,
      )
    }
  }
}
