package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StormGlassColorScheme =
  darkColorScheme(
    primary = AmberAccent,
    onPrimary = TacticalBackground,
    primaryContainer = AmberDim,
    onPrimaryContainer = AmberAccent,
    secondary = CyanData,
    onSecondary = TacticalBackground,
    secondaryContainer = CyanDim,
    onSecondaryContainer = CyanData,
    tertiary = SensorGreen,
    onTertiary = TacticalBackground,
    tertiaryContainer = SensorGreenDark,
    onTertiaryContainer = SensorGreen,
    error = SquallCrimson,
    onError = TextPrimary,
    errorContainer = SquallCrimsonDark,
    onErrorContainer = SquallCrimson,
    background = TacticalBackground,
    onBackground = TextPrimary,
    surface = TacticalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TacticalSurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = TacticalSurfaceBorder,
    outlineVariant = TacticalSurfaceBorder,
  )

private val StormGlassShapes =
  Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
  )

@Composable
fun StormGlassTheme(content: @Composable () -> Unit) {
  MaterialTheme(
    colorScheme = StormGlassColorScheme,
    typography = StormGlassTypography,
    shapes = StormGlassShapes,
    content = content,
  )
}
