package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppTab
import com.example.sensor.BarometerSensorController
import com.example.ui.screens.CockpitScreen
import com.example.ui.screens.ForecasterScreen
import com.example.ui.screens.GuideScreen
import com.example.ui.screens.HistorySettingsScreen
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.CyanData
import com.example.ui.theme.SquallCrimson
import com.example.ui.theme.TacticalBackground
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TacticalSurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StormGlassApp(
  viewModel: StormGlassViewModel = viewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val snackbarHostState = remember { SnackbarHostState() }

  val sensorController = remember(context) {
    BarometerSensorController(
      context = context,
      onFilteredPressure = { viewModel.onLiveHardwarePressure(it) },
      onAutoLogSample = { viewModel.onAutoSensorSample(it) },
    )
  }

  LaunchedEffect(sensorController) {
    viewModel.setHasHardwareSensor(sensorController.hasHardwareSensor)
  }

  DisposableEffect(lifecycleOwner, sensorController) {
    val observer = LifecycleEventObserver { _, event ->
      when (event) {
        Lifecycle.Event.ON_RESUME -> sensorController.start()
        Lifecycle.Event.ON_PAUSE -> sensorController.stop()
        else -> Unit
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
      sensorController.start()
    }
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
      sensorController.stop()
    }
  }

  LaunchedEffect(state.snackbarMessage) {
    val msg = state.snackbarMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeSnackbar()
    }
  }

  BackHandler(enabled = state.activeTab != AppTab.COCKPIT) {
    viewModel.selectTab(AppTab.COCKPIT)
  }

  Scaffold(
    containerColor = TacticalBackground,
    contentWindowInsets = WindowInsets.safeDrawing,
    topBar = {
      TopAppBar(
        colors =
          TopAppBarDefaults.topAppBarColors(
            containerColor = TacticalBackground,
            titleContentColor = TextPrimary,
          ),
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
              imageVector = Icons.Filled.Speed,
              contentDescription = null,
              tint = if (state.isSquallWarning) SquallCrimson else CyanData,
              modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "STORMGLASS LOCAL",
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
            )
          }
        },
        actions = {
          Surface(
            color = TacticalSurfaceElevated,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.padding(end = 12.dp),
          ) {
            Text(
              text = if (state.onlineWeatherEnabled) "ONLINE READY" else "OFFLINE",
              style = MaterialTheme.typography.labelSmall,
              color = if (state.onlineWeatherEnabled) AmberAccent else TextMuted,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
          }
        },
      )
    },
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = TacticalSurfaceElevated,
          contentColor = TextPrimary,
          actionColor = AmberAccent,
        )
      }
    },
    bottomBar = {
      NavigationBar(
        containerColor = TacticalSurface,
        contentColor = TextPrimary,
      ) {
        AppTab.entries.forEach { tab ->
          val selected = state.activeTab == tab
          val (selectedIcon, unselectedIcon) =
            when (tab) {
              AppTab.COCKPIT -> Icons.Filled.Speed to Icons.Outlined.Speed
              AppTab.FORECASTER -> Icons.Filled.Explore to Icons.Outlined.Explore
              AppTab.LOG -> Icons.Filled.History to Icons.Outlined.History
              AppTab.GUIDE ->
                Icons.AutoMirrored.Filled.MenuBook to Icons.AutoMirrored.Outlined.MenuBook
            }
          NavigationBarItem(
            selected = selected,
            onClick = { viewModel.selectTab(tab) },
            icon = {
              Icon(
                imageVector = if (selected) selectedIcon else unselectedIcon,
                contentDescription = tab.label,
              )
            },
            label = {
              Text(
                text = tab.label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
              )
            },
            colors =
              NavigationBarItemDefaults.colors(
                selectedIconColor = AmberAccent,
                selectedTextColor = AmberAccent,
                indicatorColor = AmberAccent.copy(alpha = 0.16f),
                unselectedIconColor = TextSecondary,
                unselectedTextColor = TextSecondary,
              ),
            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
          )
        }
      }
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      when (state.activeTab) {
        AppTab.COCKPIT ->
          CockpitScreen(
            state = state,
            onToggleUnit = viewModel::toggleUnit,
            onSelectWindow = viewModel::selectTimeWindow,
            onNudgeManual = viewModel::nudgeManualPressure,
            onSetExactManual = viewModel::setManualPressureExact,
            onLogReading = { viewModel.logManualReading() },
            onLoadDemo = { viewModel.loadDemoSquallData(navigateToCockpit = false) },
            onOpenForecaster = { viewModel.selectTab(AppTab.FORECASTER) },
          )
        AppTab.FORECASTER ->
          ForecasterScreen(
            state = state,
            onElevationChange = viewModel::setElevation,
            onWindChange = viewModel::setWindDirection,
            onSeasonChange = viewModel::setSeason,
            onToggleChecklist = viewModel::toggleChecklistItem,
            onOnlineWeatherToggle = viewModel::setOnlineWeatherEnabled,
            onLatitudeChange = viewModel::updateLatitudeInput,
            onLongitudeChange = viewModel::updateLongitudeInput,
            onFetchLiveStation = viewModel::fetchLiveStationWeather,
            onApplyOnlineWind = viewModel::applyOnlineWindDirection,
          )
        AppTab.LOG ->
          HistorySettingsScreen(
            state = state,
            onThresholdChange = viewModel::setSquallThreshold,
            onPersistOptInChange = viewModel::setPersistLocalData,
            onTestSoundAlert = viewModel::testSoundAlert,
            onExportCsv = {
              val csv = viewModel.buildCsvForClipboard()
              val clipboard =
                context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
              clipboard?.setPrimaryClip(ClipData.newPlainText("StormGlass Barograph CSV", csv))
              viewModel.notifyCsvCopied()
            },
            onGenerateDemoData = { viewModel.loadDemoSquallData(navigateToCockpit = false) },
            onClearHistory = viewModel::wipeAllLocalData,
            onDeleteReading = viewModel::deleteReading,
          )
        AppTab.GUIDE ->
          GuideScreen(
            hasHardwareSensor = state.hasHardwareSensor,
            onLoadDemoSquallData = { viewModel.loadDemoSquallData(navigateToCockpit = true) },
          )
      }
    }
  }
}
