package com.example.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TacticalDarkColorScheme =
  darkColorScheme(
    primary = PhosphorGreen,
    onPrimary = PhosphorGreenOn,
    primaryContainer = PhosphorGreenDim,
    onPrimaryContainer = PhosphorGreen,
    secondary = AmberSync,
    onSecondary = AmberSyncOn,
    secondaryContainer = AmberSyncDim,
    onSecondaryContainer = AmberSync,
    tertiary = PhosphorGreen,
    onTertiary = PhosphorGreenOn,
    background = OledBlack,
    onBackground = TextPrimary,
    surface = TacticalSurface,
    onSurface = TextPrimary,
    surfaceVariant = TacticalCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = OledBlack,
    surfaceContainerLow = TacticalSurface,
    surfaceContainer = TacticalCard,
    surfaceContainerHigh = TacticalCardElevated,
    surfaceContainerHighest = TacticalCardElevated,
    outline = TacticalBorder,
    outlineVariant = TacticalBorderBright,
    error = SosRed,
    onError = SosRedOn,
    errorContainer = SosRedDim,
    onErrorContainer = SosRed,
  )

@Composable
fun DeadDropTheme(content: @Composable () -> Unit) {
  val view = LocalView.current
  if (!view.isInEditMode) {
    SideEffect {
      val window = (view.context as? Activity)?.window
      if (window != null) {
        WindowCompat.getInsetsController(window, view).apply {
          isAppearanceLightStatusBars = false
          isAppearanceLightNavigationBars = false
        }
      }
    }
  }

  MaterialTheme(
    colorScheme = TacticalDarkColorScheme,
    typography = Typography,
    content = content,
  )
}
