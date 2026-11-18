package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.PressureReading
import com.example.model.PressureTrend
import com.example.model.PressureUnit
import com.example.model.TimeWindow
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberDim
import com.example.ui.theme.CyanData
import com.example.ui.theme.SensorGreen
import com.example.ui.theme.SensorGreenDark
import com.example.ui.theme.SquallCrimson
import com.example.ui.theme.SquallCrimsonDark
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
import kotlin.math.abs

@Composable
fun HardwareStatusBadge(
  hasHardwareSensor: Boolean,
  unit: PressureUnit,
  onToggleUnit: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val badgeColor = if (hasHardwareSensor) SensorGreen else AmberAccent
  val containerColor = if (hasHardwareSensor) SensorGreenDark else AmberDim
  val label =
    if (hasHardwareSensor) {
      stringResource(R.string.badge_hardware_active)
    } else {
      stringResource(R.string.badge_manual_mode)
    }

  Row(
    modifier = modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Surface(
      color = containerColor.copy(alpha = 0.85f),
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f)),
      modifier = Modifier.weight(1f, fill = false).testTag("hardware_status_badge"),
    ) {
      Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Box(
          modifier =
            Modifier.size(8.dp).clip(CircleShape).background(badgeColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
          imageVector = if (hasHardwareSensor) Icons.Filled.Sensors else Icons.Filled.Edit,
          contentDescription = null,
          tint = badgeColor,
          modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = label,
          style = MaterialTheme.typography.labelMedium,
          color = badgeColor,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }

    Spacer(modifier = Modifier.width(8.dp))

    Surface(
      onClick = onToggleUnit,
      color = TacticalSurfaceElevated,
      shape = RoundedCornerShape(10.dp),
      border = BorderStroke(1.dp, CyanData.copy(alpha = 0.45f)),
      modifier = Modifier.heightIn(min = 48.dp).testTag("unit_toggle_button"),
    ) {
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
      ) {
        Text(
          text = unit.label.uppercase(Locale.US),
          style = MaterialTheme.typography.labelLarge,
          color = CyanData,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }
}

@Composable
fun SquallAlertBanner(
  visible: Boolean,
  deltaP3h: Float?,
  unit: PressureUnit,
  onOpenReadiness: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier,
  ) {
    val transition = rememberInfiniteTransition(label = "squall_pulse")
    val pulseBorder by
      transition.animateColor(
        initialValue = SquallCrimson,
        targetValue = SquallCrimson.copy(alpha = 0.35f),
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "squall_border",
      )
    val pulseBg by
      transition.animateColor(
        initialValue = SquallCrimsonDark,
        targetValue = SquallCrimson.copy(alpha = 0.28f),
        animationSpec =
          infiniteRepeatable(
            animation = tween(durationMillis = 550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
          ),
        label = "squall_bg",
      )

    Surface(
      color = pulseBg,
      shape = RoundedCornerShape(14.dp),
      border = BorderStroke(2.dp, pulseBorder),
      modifier = Modifier.fillMaxWidth().testTag("squall_alert_banner"),
    ) {
      Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Icon(
          imageVector = Icons.Filled.Warning,
          contentDescription = "Squall Warning",
          tint = SquallCrimson,
          modifier = Modifier.size(28.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = stringResource(R.string.squall_warning_title),
            style = MaterialTheme.typography.labelLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
          )
          val deltaLabel = deltaP3h?.let { unit.formatDelta(it) } ?: "--"
          Text(
            text = "3h change: $deltaLabel • Secure camp & monitor VHF",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFFFCDD2),
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
          onClick = onOpenReadiness,
          modifier = Modifier.heightIn(min = 48.dp).testTag("squall_checklist_cta"),
        ) {
          Text(
            text = "Checklist",
            style = MaterialTheme.typography.labelLarge,
            color = AmberAccent,
          )
        }
      }
    }
  }
}

@Composable
fun TrendIconBadge(trend: PressureTrend, modifier: Modifier = Modifier) {
  val (icon, color, text) =
    when (trend) {
      PressureTrend.FALLING ->
        Triple(Icons.AutoMirrored.Filled.TrendingDown, SquallCrimson, "FALLING")
      PressureTrend.STEADY ->
        Triple(Icons.AutoMirrored.Filled.TrendingFlat, CyanData, "STEADY")
      PressureTrend.RISING ->
        Triple(Icons.AutoMirrored.Filled.TrendingUp, SensorGreen, "RISING")
    }
  Surface(
    color = color.copy(alpha = 0.14f),
    shape = RoundedCornerShape(8.dp),
    border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
    modifier = modifier,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = icon,
        contentDescription = text,
        tint = color,
        modifier = Modifier.size(16.dp),
      )
      Spacer(modifier = Modifier.width(4.dp))
      Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}

@Composable
fun BarographCard(
  readings: List<PressureReading>,
  selectedWindow: TimeWindow,
  unit: PressureUnit,
  trend: PressureTrend,
  isSquallWarning: Boolean,
  onSelectWindow: (TimeWindow) -> Unit,
  onLoadDemo: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val referenceEndMs = readings.lastOrNull()?.timestamp ?: System.currentTimeMillis()
  val cutoffMs = referenceEndMs - selectedWindow.hours * 3_600_000L
  val windowReadings =
    remember(readings, selectedWindow) {
      readings.filter { it.timestamp >= cutoffMs }.sortedBy { it.timestamp }
    }

  var scrubbedIndex by remember(windowReadings) { mutableStateOf<Int?>(null) }
  val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }

  Surface(
    color = TacticalSurface,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, TacticalSurfaceBorder),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text = "BAROGRAPH TRACE",
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
          )
          val scrubbed = scrubbedIndex?.let { windowReadings.getOrNull(it) }
          if (scrubbed != null) {
            Text(
              text =
                "${unit.formatWithUnit(scrubbed.pressureHpa)} @ ${timeFormat.format(Date(scrubbed.timestamp))}",
              style = MaterialTheme.typography.labelLarge,
              color = AmberAccent,
            )
          } else {
            Text(
              text = "${windowReadings.size} samples (${selectedWindow.label})",
              style = MaterialTheme.typography.bodyMedium,
              color = TextMuted,
            )
          }
        }
        TrendIconBadge(trend = trend)
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        TimeWindow.entries.forEach { window ->
          val selected = window == selectedWindow
          Surface(
            onClick = { onSelectWindow(window) },
            color = if (selected) CyanData.copy(alpha = 0.16f) else TacticalSurfaceElevated,
            shape = RoundedCornerShape(8.dp),
            border =
              BorderStroke(
                1.dp,
                if (selected) CyanData else TacticalSurfaceBorder,
              ),
            modifier =
              Modifier.weight(1f)
                .heightIn(min = 48.dp)
                .testTag("filter_${window.label.lowercase(Locale.US)}"),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Text(
                text = window.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) CyanData else TextSecondary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (windowReadings.size < 2) {
        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(185.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(TacticalBackground),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
          ) {
            Text(
              text =
                if (windowReadings.isEmpty()) {
                  "No barometric logs in the ${selectedWindow.label} window yet."
                } else {
                  "1 reading logged. Add one more reading to trace pressure curve."
                },
              style = MaterialTheme.typography.bodyMedium,
              color = TextSecondary,
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
              onClick = onLoadDemo,
              border = BorderStroke(1.dp, AmberAccent),
              modifier = Modifier.heightIn(min = 48.dp).testTag("empty_chart_demo_button"),
            ) {
              Text(
                text = "Load Demo Squall Data",
                style = MaterialTheme.typography.labelLarge,
                color = AmberAccent,
              )
            }
          }
        }
      } else {
        val minP = remember(windowReadings) { windowReadings.minOf { it.pressureHpa } }
        val maxP = remember(windowReadings) { windowReadings.maxOf { it.pressureHpa } }
        val spanRaw = (maxP - minP).coerceAtLeast(2.0f)
        val pad = (spanRaw * 0.18f).coerceAtLeast(0.8f)
        val chartMin = minP - pad
        val chartMax = maxP + pad

        Box(
          modifier =
            Modifier.fillMaxWidth()
              .height(200.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(TacticalBackground)
              .padding(vertical = 8.dp, horizontal = 4.dp)
        ) {
          Canvas(
            modifier =
              Modifier.fillMaxSize()
                .pointerInput(windowReadings) {
                  detectTapGestures(
                    onPress = { offset ->
                      val idx = findClosestPointIndex(offset.x, size.width.toFloat(), windowReadings)
                      scrubbedIndex = idx
                      tryAwaitRelease()
                      scrubbedIndex = null
                    }
                  )
                }
                .pointerInput(windowReadings) {
                  detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                      change.consume()
                      scrubbedIndex =
                        findClosestPointIndex(
                          change.position.x,
                          size.width.toFloat(),
                          windowReadings,
                        )
                    },
                    onDragEnd = { scrubbedIndex = null },
                    onDragCancel = { scrubbedIndex = null },
                  )
                }
          ) {
            val w = size.width
            val h = size.height
            val leftPad = 14.dp.toPx()
            val rightPad = 14.dp.toPx()
            val topPad = 12.dp.toPx()
            val bottomPad = 12.dp.toPx()
            val plotW = (w - leftPad - rightPad).coerceAtLeast(1f)
            val plotH = (h - topPad - bottomPad).coerceAtLeast(1f)

            val minTs = windowReadings.first().timestamp
            val maxTs = windowReadings.last().timestamp
            val tsSpan = (maxTs - minTs).coerceAtLeast(1L)
            val pSpan = (chartMax - chartMin).coerceAtLeast(0.5f)

            fun xFor(ts: Long): Float =
              leftPad + ((ts - minTs).toFloat() / tsSpan.toFloat()) * plotW

            fun yFor(p: Float): Float =
              topPad + (1f - ((p - chartMin) / pSpan)) * plotH

            // Horizontal dashed guide lines (Max, Mid, Min)
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
            listOf(maxP, (maxP + minP) / 2f, minP).forEach { guideP ->
              val y = yFor(guideP)
              drawLine(
                color = TacticalSurfaceBorder,
                start = Offset(leftPad, y),
                end = Offset(w - rightPad, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect,
              )
            }

            // Standard 1013.25 hPa reference line if inside visible range
            if (1013.25f in chartMin..chartMax) {
              val yStd = yFor(1013.25f)
              drawLine(
                color = AmberAccent.copy(alpha = 0.45f),
                start = Offset(leftPad, yStd),
                end = Offset(w - rightPad, yStd),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 6f), 0f),
              )
            }

            val points = windowReadings.map { Offset(xFor(it.timestamp), yFor(it.pressureHpa)) }
            val linePath = Path().apply {
              moveTo(points.first().x, points.first().y)
              for (i in 0 until points.lastIndex) {
                val p0 = points[i]
                val p1 = points[i + 1]
                val midX = (p0.x + p1.x) / 2f
                cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
              }
            }

            val fillPath =
              Path().apply {
                addPath(linePath)
                lineTo(points.last().x, h - bottomPad)
                lineTo(points.first().x, h - bottomPad)
                close()
              }

            drawPath(
              path = fillPath,
              brush =
                Brush.verticalGradient(
                  colors =
                    listOf(
                      (if (isSquallWarning) SquallCrimson else CyanData).copy(alpha = 0.30f),
                      Color.Transparent,
                    ),
                  startY = topPad,
                  endY = h - bottomPad,
                ),
            )

            drawPath(
              path = linePath,
              color = CyanData,
              style =
                Stroke(
                  width = 2.8.dp.toPx(),
                  cap = StrokeCap.Round,
                  join = StrokeJoin.Round,
                ),
            )

            // Highlight trailing 3h squall segment in crimson if squall alert is active
            if (isSquallWarning && points.size >= 2) {
              val threeHourCutoff = windowReadings.last().timestamp - 3 * 3_600_000L
              val squallPoints =
                windowReadings
                  .zip(points)
                  .filter { (r, _) -> r.timestamp >= threeHourCutoff }
                  .map { it.second }
              if (squallPoints.size >= 2) {
                val squallPath = Path().apply {
                  moveTo(squallPoints.first().x, squallPoints.first().y)
                  for (i in 0 until squallPoints.lastIndex) {
                    val p0 = squallPoints[i]
                    val p1 = squallPoints[i + 1]
                    val midX = (p0.x + p1.x) / 2f
                    cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
                  }
                }
                drawPath(
                  path = squallPath,
                  color = SquallCrimson,
                  style =
                    Stroke(
                      width = 3.2.dp.toPx(),
                      cap = StrokeCap.Round,
                      join = StrokeJoin.Round,
                    ),
                )
              }
            }

            // Data point nodes
            points.forEachIndexed { idx, pt ->
              val isLast = idx == points.lastIndex
              val isScrubbed = idx == scrubbedIndex
              if (isLast || isScrubbed || windowReadings[idx].isManual) {
                val dotColor =
                  when {
                    isScrubbed -> AmberAccent
                    isLast && isSquallWarning -> SquallCrimson
                    windowReadings[idx].isManual -> AmberAccent
                    else -> CyanData
                  }
                drawCircle(
                  color = TacticalBackground,
                  radius = if (isScrubbed || isLast) 5.dp.toPx() else 3.dp.toPx(),
                  center = pt,
                )
                drawCircle(
                  color = dotColor,
                  radius = if (isScrubbed || isLast) 3.5.dp.toPx() else 2.2.dp.toPx(),
                  center = pt,
                )
              }
            }

            // Crosshair reticle when scrubbing
            scrubbedIndex?.let { idx ->
              points.getOrNull(idx)?.let { pt ->
                drawLine(
                  color = AmberAccent.copy(alpha = 0.7f),
                  start = Offset(pt.x, topPad),
                  end = Offset(pt.x, h - bottomPad),
                  strokeWidth = 1.2.dp.toPx(),
                )
              }
            }
          }

          // Min / Max corner guide labels
          Text(
            text = "MAX ${unit.formatWithUnit(maxP)}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 2.dp),
          )
          Text(
            text = "MIN ${unit.formatWithUnit(minP)}",
            style = MaterialTheme.typography.labelSmall,
            color = TextMuted,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 2.dp),
          )
        }
      }
    }
  }
}

private fun findClosestPointIndex(
  touchX: Float,
  totalWidth: Float,
  readings: List<PressureReading>,
): Int {
  if (readings.size <= 1) return 0
  val minTs = readings.first().timestamp
  val maxTs = readings.last().timestamp
  val tsSpan = (maxTs - minTs).coerceAtLeast(1L)
  val fraction = ((touchX / totalWidth).coerceIn(0f, 1f))
  val targetTs = minTs + (fraction * tsSpan).toLong()
  return readings.indices.minByOrNull { abs(readings[it].timestamp - targetTs) } ?: 0
}

@Composable
fun QuickManualLogBar(
  stagedPressureHpa: Float,
  unit: PressureUnit,
  hasHardwareSensor: Boolean,
  onNudge: (Float) -> Unit,
  onSetExact: (Float) -> Unit,
  onLogReading: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var showDirectEntryDialog by remember { mutableStateOf(false) }

  Surface(
    color = TacticalSurface,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.4f)),
    modifier = modifier.fillMaxWidth(),
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column {
          Text(
            text =
              if (hasHardwareSensor) "MANUAL LOG / OVERRIDE" else "QUICK MANUAL LOG BAR",
            style = MaterialTheme.typography.labelMedium,
            color = AmberAccent,
          )
          Text(
            text = "Tap value for direct wrist-barometer entry",
            style = MaterialTheme.typography.bodyMedium,
            color = TextMuted,
          )
        }
        Surface(
          color = TacticalSurfaceElevated,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, AmberAccent.copy(alpha = 0.55f)),
          modifier =
            Modifier.heightIn(min = 48.dp)
              .clip(RoundedCornerShape(10.dp))
              .clickable { showDirectEntryDialog = true }
              .testTag("direct_pressure_entry_trigger"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(
              text = unit.formatWithUnit(stagedPressureHpa),
              style = MaterialTheme.typography.titleMedium,
              color = TextPrimary,
              fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
              imageVector = Icons.Filled.Edit,
              contentDescription = "Edit exact pressure",
              tint = AmberAccent,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        NudgeButton("-1.0", -1.0f, onNudge, Modifier.weight(1f).testTag("nudge_minus_1"))
        NudgeButton("-0.1", -0.1f, onNudge, Modifier.weight(1f).testTag("nudge_minus_01"))
        NudgeButton("+0.1", +0.1f, onNudge, Modifier.weight(1f).testTag("nudge_plus_01"))
        NudgeButton("+1.0", +1.0f, onNudge, Modifier.weight(1f).testTag("nudge_plus_1"))
      }

      Spacer(modifier = Modifier.height(10.dp))

      Button(
        onClick = onLogReading,
        colors =
          ButtonDefaults.buttonColors(
            containerColor = AmberAccent,
            contentColor = TacticalBackground,
          ),
        shape = RoundedCornerShape(12.dp),
        modifier =
          Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("log_reading_button"),
      ) {
        Icon(
          imageVector = Icons.Filled.Add,
          contentDescription = null,
          modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = stringResource(R.string.log_current_reading),
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.Bold,
        )
      }
    }
  }

  if (showDirectEntryDialog) {
    var inputText by remember(stagedPressureHpa) {
      mutableStateOf(String.format(Locale.US, "%.1f", stagedPressureHpa))
    }
    var parseError by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showDirectEntryDialog = false },
      containerColor = TacticalSurface,
      title = {
        Text(
          text = "Direct Barometer Entry (hPa)",
          style = MaterialTheme.typography.titleLarge,
          color = TextPrimary,
        )
      },
      text = {
        Column {
          Text(
            text = "Enter station pressure in hPa / mbar (850.0 – 1085.0).",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
          )
          Spacer(modifier = Modifier.height(12.dp))
          OutlinedTextField(
            value = inputText,
            onValueChange = {
              if (it.length <= 10) {
                inputText = it
                parseError = false
              }
            },
            label = { Text("Pressure (hPa)") },
            singleLine = true,
            isError = parseError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().testTag("direct_pressure_input_field"),
          )
          if (parseError) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Enter a valid number between 850 and 1085 hPa.",
              style = MaterialTheme.typography.bodyMedium,
              color = SquallCrimson,
            )
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = {
            val parsed = inputText.trim().toFloatOrNull()
            if (parsed != null && parsed in 850f..1085f) {
              onSetExact(parsed)
              showDirectEntryDialog = false
            } else {
              parseError = true
            }
          }
        ) {
          Text("Apply", color = AmberAccent)
        }
      },
      dismissButton = {
        TextButton(onClick = { showDirectEntryDialog = false }) {
          Text("Cancel", color = TextSecondary)
        }
      },
    )
  }
}

@Composable
private fun NudgeButton(
  label: String,
  delta: Float,
  onNudge: (Float) -> Unit,
  modifier: Modifier = Modifier,
) {
  OutlinedButton(
    onClick = { onNudge(delta) },
    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    border = BorderStroke(1.dp, TacticalSurfaceBorder),
    shape = RoundedCornerShape(10.dp),
    colors =
      ButtonDefaults.outlinedButtonColors(
        containerColor = TacticalSurfaceElevated,
        contentColor = if (delta < 0f) CyanData else AmberAccent,
      ),
    modifier = modifier.heightIn(min = 48.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelLarge,
      fontWeight = FontWeight.Bold,
      maxLines = 1,
    )
  }
}
