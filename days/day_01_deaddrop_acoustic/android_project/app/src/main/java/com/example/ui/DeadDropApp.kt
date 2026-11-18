package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.R
import com.example.ui.components.TacticalBadge
import com.example.ui.screens.BeaconLogScreen
import com.example.ui.screens.DecoderRxScreen
import com.example.ui.screens.ModemTxScreen
import com.example.ui.theme.AmberSync
import com.example.ui.theme.OledBlack
import com.example.ui.theme.PhosphorGreen
import com.example.ui.theme.PhosphorGreenDim
import com.example.ui.theme.SosRed
import com.example.ui.theme.TacticalBorder
import com.example.ui.theme.TacticalCard
import com.example.ui.theme.TacticalSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary

@Composable
fun DeadDropApp(viewModel: MainViewModel = viewModel()) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val auditEntries by viewModel.auditEntries.collectAsStateWithLifecycle()
  val persistHistoryOptIn by viewModel.persistHistoryOptIn.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }

  LaunchedEffect(state.snackbarMessage) {
    val msg = state.snackbarMessage
    if (msg != null) {
      snackbarHostState.showSnackbar(msg)
      viewModel.consumeSnackbar()
    }
  }

  if (state.selectedTab != DeadDropTab.MODEM_TX) {
    BackHandler { viewModel.selectTab(DeadDropTab.MODEM_TX) }
  }

  Scaffold(
    modifier = Modifier.fillMaxSize().background(OledBlack),
    containerColor = OledBlack,
    contentWindowInsets = WindowInsets.safeDrawing,
    snackbarHost = {
      SnackbarHost(hostState = snackbarHostState) { data ->
        Snackbar(
          snackbarData = data,
          containerColor = TacticalCard,
          contentColor = PhosphorGreen,
          shape = RoundedCornerShape(8.dp),
          modifier = Modifier.border(1.dp, PhosphorGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        )
      }
    },
    topBar = {
      TacticalTopHeader(state = state)
    },
    bottomBar = {
      DeadDropBottomBar(
        selectedTab = state.selectedTab,
        onSelectTab = viewModel::selectTab,
      )
    },
  ) { innerPadding ->
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.TopCenter,
    ) {
      Box(modifier = Modifier.fillMaxSize().widthIn(max = 680.dp)) {
        when (state.selectedTab) {
          DeadDropTab.MODEM_TX ->
            ModemTxScreen(
              state = state,
              onCallsignChange = viewModel::updateCallsign,
              onPacketTypeChange = viewModel::updatePacketType,
              onMessageChange = viewModel::updateMessage,
              onCoordinatesChange = viewModel::updateCoordinates,
              onToggleManualCoords = viewModel::toggleManualCoords,
              onFetchGps = viewModel::fetchGpsCoordinates,
              onLocationPermissionResult = viewModel::onLocationPermissionResult,
              onTransmitClick = viewModel::transmitAudio,
              onLoopbackClick = viewModel::runLoopbackSelfTest,
              onOnlineRelayToggle = viewModel::setOnlineRelayEnabled,
              onOnlineRelayUrlChange = viewModel::updateOnlineRelayUrl,
              onGenerateRandomTopic = viewModel::generateRandomOnlineTopic,
              onSendOnlineCopy = viewModel::sendOnlineCopy,
              onCheckOnlineInbox = viewModel::checkOnlineInbox,
            )
          DeadDropTab.DECODER_RX ->
            DecoderRxScreen(
              state = state,
              onToggleRx = viewModel::toggleRxListening,
              onClearFeed = viewModel::clearRxFeed,
              onCopyPacket = viewModel::showSnackbar,
            )
          DeadDropTab.BEACON_LOG ->
            BeaconLogScreen(
              state = state,
              auditEntries = auditEntries,
              persistHistoryOptIn = persistHistoryOptIn,
              onPersistOptInChange = viewModel::setPersistHistoryOptIn,
              onBeaconMessageChange = viewModel::updateBeaconMessage,
              onBeaconWpmChange = viewModel::updateBeaconWpm,
              onBeaconLoopChange = viewModel::setBeaconLoop,
              onToggleBeacon = viewModel::toggleBeacon,
              onExportLog = viewModel::exportAuditLogText,
              onWipeAllData = viewModel::wipeAllLocalData,
              onShowMessage = viewModel::showSnackbar,
            )
        }
      }
    }
  }
}

@Composable
private fun TacticalTopHeader(state: DeadDropUiState) {
  Column(
    modifier = Modifier.fillMaxWidth().background(TacticalSurface).statusBarsPadding(),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = stringResource(R.string.app_header_title),
          fontFamily = FontFamily.Monospace,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp,
          letterSpacing = 1.0.sp,
          color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
          text = stringResource(R.string.app_header_spec),
          fontFamily = FontFamily.Monospace,
          fontSize = 10.sp,
          letterSpacing = 0.6.sp,
          color = TextMuted,
        )
      }

      val (statusText, statusColor) =
        when {
          state.isTransmitting -> "PLAYING" to SosRed
          state.isBeaconRunning -> "FLASHING" to AmberSync
          state.rxSyncStatus == RxSyncStatus.SYNC_LOCK -> "SIGNAL LOCK" to AmberSync
          state.isRxListening -> "LISTENING" to PhosphorGreen
          state.onlineRelayEnabled -> "ONLINE READY" to AmberSync
          else -> "OFFLINE READY" to PhosphorGreen
        }

      TacticalBadge(
        text = statusText,
        color = statusColor,
        filled = state.isTransmitting || state.isRxListening || state.isBeaconRunning,
        dotColor = statusColor,
        modifier = Modifier.testTag("global_status_badge"),
      )
    }
    HorizontalDivider(thickness = 1.dp, color = TacticalBorder)
  }
}

private data class NavTabItem(
  val tab: DeadDropTab,
  val labelRes: Int,
  val icon: ImageVector,
  val tag: String,
)

@Composable
private fun DeadDropBottomBar(
  selectedTab: DeadDropTab,
  onSelectTab: (DeadDropTab) -> Unit,
) {
  val items =
    listOf(
      NavTabItem(DeadDropTab.MODEM_TX, R.string.tab_tx, Icons.Rounded.GraphicEq, "nav_tab_tx"),
      NavTabItem(DeadDropTab.DECODER_RX, R.string.tab_rx, Icons.Rounded.Hearing, "nav_tab_rx"),
      NavTabItem(
        DeadDropTab.BEACON_LOG,
        R.string.tab_beacon,
        Icons.Rounded.FlashlightOn,
        "nav_tab_beacon",
      ),
    )

  Column(modifier = Modifier.fillMaxWidth().background(TacticalSurface)) {
    HorizontalDivider(thickness = 1.dp, color = TacticalBorder)
    NavigationBar(
      containerColor = TacticalSurface,
      contentColor = PhosphorGreen,
      tonalElevation = 0.dp,
      windowInsets = WindowInsets.navigationBars,
    ) {
      items.forEach { item ->
        val selected = selectedTab == item.tab
        NavigationBarItem(
          selected = selected,
          onClick = { onSelectTab(item.tab) },
          icon = {
            Icon(
              imageVector = item.icon,
              contentDescription = stringResource(item.labelRes),
              modifier = Modifier.size(22.dp),
            )
          },
          label = {
            Text(
              text = stringResource(item.labelRes),
              fontFamily = FontFamily.Monospace,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              fontSize = 10.sp,
              letterSpacing = 0.5.sp,
            )
          },
          colors =
            NavigationBarItemDefaults.colors(
              selectedIconColor = PhosphorGreen,
              selectedTextColor = PhosphorGreen,
              indicatorColor = PhosphorGreenDim,
              unselectedIconColor = TextSecondary,
              unselectedTextColor = TextSecondary,
            ),
          modifier = Modifier.testTag(item.tag),
        )
      }
    }
  }
}
