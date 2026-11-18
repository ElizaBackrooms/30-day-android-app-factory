package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val TripwireDarkColorScheme = darkColorScheme(
    primary = PhosphorTeal,
    onPrimary = PhosphorTealDark,
    primaryContainer = PhosphorTealContainer,
    onPrimaryContainer = PhosphorTealOnContainer,
    secondary = SignalAmber,
    onSecondary = SignalAmberDark,
    secondaryContainer = SignalAmberContainer,
    onSecondaryContainer = SignalAmberOnContainer,
    tertiary = AxisXColor,
    onTertiary = ObsidianBg,
    error = AlertCrimson,
    onError = Color.White,
    errorContainer = AlertCrimsonContainer,
    onErrorContainer = AlertCrimsonOnContainer,
    background = ObsidianBg,
    onBackground = SlateInkLight,
    surface = ObsidianSurface,
    onSurface = SlateInkLight,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = SlateInkMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurface,
    surfaceContainer = ObsidianSurfaceElevated,
    surfaceContainerHigh = ObsidianSurfaceHighest,
    outline = ObsidianOutline,
    outlineVariant = ObsidianOutlineVariant
)

private val TripwireLightColorScheme = lightColorScheme(
    primary = DeepTeal,
    onPrimary = Color.White,
    primaryContainer = DeepTealContainer,
    onPrimaryContainer = DeepTealOnContainer,
    secondary = BurntAmber,
    onSecondary = Color.White,
    secondaryContainer = BurntAmberContainer,
    onSecondaryContainer = BurntAmberOnContainer,
    tertiary = Color(0xFF0284C7),
    onTertiary = Color.White,
    error = SignalRed,
    onError = Color.White,
    errorContainer = SignalRedContainer,
    onErrorContainer = SignalRedOnContainer,
    background = AlabasterBg,
    onBackground = GraphiteInk,
    surface = AlabasterSurface,
    onSurface = GraphiteInk,
    surfaceVariant = AlabasterSurfaceElevated,
    onSurfaceVariant = GraphiteInkMuted,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = AlabasterSurface,
    surfaceContainer = AlabasterSurfaceElevated,
    surfaceContainerHigh = AlabasterSurfaceHighest,
    outline = AlabasterOutline,
    outlineVariant = AlabasterOutlineVariant
)

val TripwireShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun TripwirePulseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) TripwireDarkColorScheme else TripwireLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = TripwireTypography,
        shapes = TripwireShapes,
        content = content
    )
}
