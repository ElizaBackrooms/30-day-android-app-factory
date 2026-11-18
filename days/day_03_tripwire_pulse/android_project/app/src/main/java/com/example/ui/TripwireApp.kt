package com.example.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.AppTab
import com.example.ui.components.MicPermissionRationaleDialog
import com.example.ui.components.NotificationPermissionRationaleDialog
import com.example.ui.screens.AlertsScreen
import com.example.ui.screens.HelpPrivacyScreen
import com.example.ui.screens.RemoteAlertsScreen
import com.example.ui.screens.WatchScreen
import com.example.ui.theme.MonoReadoutSmall
import com.example.viewmodel.SentryViewModel

private data class NavDestination(
    val tab: AppTab,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val Destinations = listOf(
    NavDestination(
        tab = AppTab.WATCH,
        labelRes = R.string.tab_watch,
        selectedIcon = Icons.Rounded.Sensors,
        unselectedIcon = Icons.Outlined.Sensors,
        testTag = "nav_tab_watch"
    ),
    NavDestination(
        tab = AppTab.ALERTS,
        labelRes = R.string.tab_alerts,
        selectedIcon = Icons.Rounded.Notifications,
        unselectedIcon = Icons.Outlined.Notifications,
        testTag = "nav_tab_alerts"
    ),
    NavDestination(
        tab = AppTab.REMOTE,
        labelRes = R.string.tab_remote,
        selectedIcon = Icons.Rounded.CloudUpload,
        unselectedIcon = Icons.Outlined.CloudUpload,
        testTag = "nav_tab_remote"
    ),
    NavDestination(
        tab = AppTab.HELP_PRIVACY,
        labelRes = R.string.tab_help_privacy,
        selectedIcon = Icons.Rounded.HelpOutline,
        unselectedIcon = Icons.Outlined.HelpOutline,
        testTag = "nav_tab_help_privacy"
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripwireApp(
    viewModel: SentryViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showMicRationale by remember { mutableStateOf(false) }
    var showNotifRationale by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMicPermissionResult(granted)
    }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    // Back gesture/button from secondary tabs returns smoothly to the Watch console
    BackHandler(enabled = uiState.selectedTab != AppTab.WATCH) {
        viewModel.selectTab(AppTab.WATCH)
    }

    LaunchedEffect(uiState.statusBannerMessage) {
        val msg = uiState.statusBannerMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    if (showMicRationale) {
        MicPermissionRationaleDialog(
            onConfirm = {
                showMicRationale = false
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            },
            onDismiss = {
                showMicRationale = false
                viewModel.onMicPermissionResult(false)
            }
        )
    }

    if (showNotifRationale) {
        NotificationPermissionRationaleDialog(
            onConfirm = {
                showNotifRationale = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.onNotificationPermissionResult(true)
                }
            },
            onDismiss = {
                showNotifRationale = false
                viewModel.onNotificationPermissionResult(false)
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        val badgeColor = when {
                            uiState.isAlarmActive -> MaterialTheme.colorScheme.errorContainer
                            uiState.isArmed -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val badgeTextColor = when {
                            uiState.isAlarmActive -> MaterialTheme.colorScheme.onErrorContainer
                            uiState.isArmed -> MaterialTheme.colorScheme.onPrimaryContainer
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Surface(
                            color = badgeColor,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = when {
                                    uiState.isAlarmActive -> "ALERT"
                                    uiState.isArmed -> "ARMED"
                                    else -> "STANDBY"
                                },
                                style = MonoReadoutSmall,
                                color = badgeTextColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            },
            bottomBar = {
                if (!useNavigationRail) {
                    NavigationBar {
                        Destinations.forEach { dest ->
                            val selected = uiState.selectedTab == dest.tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    NavTabIcon(
                                        dest = dest,
                                        selected = selected,
                                        alertCount = uiState.alerts.size
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Destinations.forEach { dest ->
                            val selected = uiState.selectedTab == dest.tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = { viewModel.selectTab(dest.tab) },
                                icon = {
                                    NavTabIcon(
                                        dest = dest,
                                        selected = selected,
                                        alertCount = uiState.alerts.size
                                    )
                                },
                                label = { Text(stringResource(dest.labelRes)) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (uiState.selectedTab) {
                        AppTab.WATCH -> WatchScreen(
                            uiState = uiState,
                            onToggleArmed = viewModel::toggleArmed,
                            onClearAlarm = viewModel::clearActiveAlarm,
                            onToggleDemoMode = viewModel::toggleDemoMode,
                            onTriggerDemoImpulse = viewModel::triggerDemoImpulse,
                            onToggleAxisTraces = viewModel::toggleShowAxisTraces,
                            onShakeThresholdChange = viewModel::updateShakeThreshold,
                            onSoundThresholdChange = viewModel::updateSoundThreshold,
                            onGateModeSelected = viewModel::updateGateMode,
                            onRequestMicGateToggle = { wantEnable ->
                                if (!wantEnable) {
                                    viewModel.disableMicGate()
                                } else if (uiState.micPermissionGranted) {
                                    viewModel.enableMicGateIfPermitted()
                                } else {
                                    showMicRationale = true
                                }
                            },
                            onRequestNotificationsToggle = { wantEnable ->
                                if (!wantEnable) {
                                    viewModel.disableNotifications()
                                } else if (uiState.notificationPermissionGranted) {
                                    viewModel.enableNotificationsIfPermitted()
                                } else {
                                    showNotifRationale = true
                                }
                            },
                            onVibrateToggle = viewModel::updateVibrateOnTrigger
                        )

                        AppTab.ALERTS -> AlertsScreen(
                            uiState = uiState,
                            onWipeAllData = viewModel::wipeAllLocalData
                        )

                        AppTab.REMOTE -> RemoteAlertsScreen(
                            uiState = uiState,
                            onOptInChanged = viewModel::setOnlineAlertsOptIn,
                            onProviderSelected = viewModel::selectWebhookProvider,
                            onUrlChanged = viewModel::updateWebhookUrl,
                            onLabelChanged = viewModel::updateWebhookLabel,
                            onGeneratePrivateNtfy = viewModel::generatePrivateNtfyTopic,
                            onSendTestAlert = viewModel::sendTestWebhookAlert
                        )

                        AppTab.HELP_PRIVACY -> HelpPrivacyScreen(
                            uiState = uiState,
                            onSaveHistoryChanged = viewModel::setSaveHistoryOnPhone,
                            onWipeAllLocalData = viewModel::wipeAllLocalData
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NavTabIcon(
    dest: NavDestination,
    selected: Boolean,
    alertCount: Int
) {
    val icon = if (selected) dest.selectedIcon else dest.unselectedIcon
    val label = stringResource(dest.labelRes)
    if (dest.tab == AppTab.ALERTS && alertCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(if (alertCount > 99) "99+" else alertCount.toString())
                }
            }
        ) {
            Icon(imageVector = icon, contentDescription = label)
        }
    } else {
        Icon(imageVector = icon, contentDescription = label)
    }
}
