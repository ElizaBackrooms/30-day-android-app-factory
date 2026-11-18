package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.engine.SeismographMath
import com.example.engine.SeismographSample
import com.example.ui.theme.AxisXColor
import com.example.ui.theme.AxisYColor
import com.example.ui.theme.AxisZColor
import com.example.ui.theme.MonoReadoutSmall
import kotlin.math.abs

@Composable
fun LiveSeismographChart(
    samples: List<SeismographSample>,
    shakeThresholdMs2: Float,
    isArmed: Boolean,
    isAlarmActive: Boolean,
    showAxisTraces: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val traceColor = when {
        isAlarmActive -> colorScheme.error
        isArmed -> colorScheme.primary
        else -> colorScheme.onSurfaceVariant
    }
    val thresholdColor = colorScheme.secondary
    val gridColor = colorScheme.outlineVariant.copy(alpha = 0.55f)
    val chartBg = colorScheme.surfaceContainerLowest

    val maxScaleMs2 = maxOf(shakeThresholdMs2 * 1.65f, 3.0f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(chartBg)
            .border(1.dp, colorScheme.outlineVariant, MaterialTheme.shapes.medium)
            .padding(12.dp)
            .testTag("live_seismograph_chart")
            .semantics {
                contentDescription = "Live vibration seismograph waveform showing recent movement spikes against your trigger limit."
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SHAKE TRACE (0 - ${SeismographMath.formatTwoDecimals(maxScaleMs2)} m/s²)",
                style = MonoReadoutSmall,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = "LIMIT ${SeismographMath.formatTwoDecimals(shakeThresholdMs2)}",
                style = MonoReadoutSmall,
                color = thresholdColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@Canvas

                // Precision horizontal & vertical instrument grid
                val horizontalLines = 4
                for (i in 0..horizontalLines) {
                    val y = (h / horizontalLines) * i
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }
                val verticalLines = 6
                for (i in 1 until verticalLines) {
                    val x = (w / verticalLines) * i
                    drawLine(
                        color = gridColor.copy(alpha = 0.45f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 1f
                    )
                }

                // Trigger threshold dashed line
                val thresholdNorm = (shakeThresholdMs2 / maxScaleMs2).coerceIn(0f, 1f)
                val thresholdY = h - (thresholdNorm * h)
                drawLine(
                    color = thresholdColor,
                    start = Offset(0f, thresholdY),
                    end = Offset(w, thresholdY),
                    strokeWidth = 2.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )

                if (samples.size < 2) {
                    // Draw quiet baseline at bottom when starting up
                    drawLine(
                        color = traceColor.copy(alpha = 0.5f),
                        start = Offset(0f, h - 2f),
                        end = Offset(w, h - 2f),
                        strokeWidth = 2f
                    )
                    return@Canvas
                }

                val maxPoints = SeismographMath.MAX_HISTORY_SAMPLES.coerceAtLeast(samples.size)
                val stepX = w / (maxPoints - 1).coerceAtLeast(1)
                val xOffset = w - ((samples.size - 1) * stepX)

                // Optional subtle X / Y / Z raw axis traces around midpoint
                if (showAxisTraces) {
                    val midY = h * 0.62f
                    val axisScale = (h * 0.32f) / maxScaleMs2
                    val xPath = Path()
                    val yPath = Path()
                    val zPath = Path()

                    samples.forEachIndexed { index, sample ->
                        val px = xOffset + index * stepX
                        val xPy = (midY - (sample.x * axisScale)).coerceIn(2f, h - 2f)
                        val yPy = (midY - (sample.y * axisScale)).coerceIn(2f, h - 2f)
                        val zPy = (midY - (sample.z * axisScale)).coerceIn(2f, h - 2f)
                        if (index == 0) {
                            xPath.moveTo(px, xPy)
                            yPath.moveTo(px, yPy)
                            zPath.moveTo(px, zPy)
                        } else {
                            xPath.lineTo(px, xPy)
                            yPath.lineTo(px, yPy)
                            zPath.lineTo(px, zPy)
                        }
                    }

                    drawPath(xPath, color = AxisXColor.copy(alpha = 0.35f), style = Stroke(width = 1.6f))
                    drawPath(yPath, color = AxisYColor.copy(alpha = 0.35f), style = Stroke(width = 1.6f))
                    drawPath(zPath, color = AxisZColor.copy(alpha = 0.35f), style = Stroke(width = 1.6f))
                }

                // Primary combined shake delta trace + translucent under-fill
                val mainPath = Path()
                val fillPath = Path()

                samples.forEachIndexed { index, sample ->
                    val px = xOffset + index * stepX
                    val norm = (abs(sample.deltaMs2) / maxScaleMs2).coerceIn(0f, 1f)
                    val py = h - (norm * h)

                    if (index == 0) {
                        mainPath.moveTo(px, py)
                        fillPath.moveTo(px, h)
                        fillPath.lineTo(px, py)
                    } else {
                        mainPath.lineTo(px, py)
                        fillPath.lineTo(px, py)
                    }

                    if (sample.isTriggered) {
                        drawLine(
                            color = colorScheme.error.copy(alpha = 0.75f),
                            start = Offset(px, 0f),
                            end = Offset(px, h),
                            strokeWidth = 2.5f
                        )
                        drawCircle(
                            color = colorScheme.error,
                            radius = 5.5f,
                            center = Offset(px, py)
                        )
                    }
                }

                val lastX = xOffset + (samples.size - 1) * stepX
                fillPath.lineTo(lastX, h)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            traceColor.copy(alpha = 0.28f),
                            traceColor.copy(alpha = 0.02f)
                        )
                    )
                )

                drawPath(
                    path = mainPath,
                    color = traceColor,
                    style = Stroke(
                        width = 3.2f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }

        if (showAxisTraces) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LegendDot(color = traceColor, label = "Total Shake")
                LegendDot(color = AxisXColor, label = "X")
                LegendDot(color = AxisYColor, label = "Y")
                LegendDot(color = AxisZColor, label = "Z")
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MonoReadoutSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun SoundLevelMeterBar(
    currentDb: Float,
    thresholdDb: Float,
    active: Boolean,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val fillNorm = ((currentDb - 20f) / 85f).coerceIn(0f, 1f)
    val threshNorm = ((thresholdDb - 20f) / 85f).coerceIn(0f, 1f)
    val barColor = when {
        !active -> colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        currentDb >= thresholdDb -> colorScheme.error
        else -> colorScheme.primary
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sound_level_meter")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (active) "Sound Gate (Mic dB)" else "Sound Gate (Mic Off)",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
            Text(
                text = if (active) "${currentDb.toInt()} dB / limit ${thresholdDb.toInt()} dB" else "Off (Shake-only)",
                style = MonoReadoutSmall,
                color = if (active && currentDb >= thresholdDb) colorScheme.error else colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(MaterialTheme.shapes.extraSmall)
        ) {
            val w = size.width
            val h = size.height
            drawRect(color = colorScheme.surfaceContainerHigh)
            drawRect(
                color = barColor,
                size = androidx.compose.ui.geometry.Size(w * fillNorm, h)
            )
            val tx = w * threshNorm
            drawLine(
                color = colorScheme.secondary,
                start = Offset(tx, 0f),
                end = Offset(tx, h),
                strokeWidth = 3f
            )
        }
    }
}
