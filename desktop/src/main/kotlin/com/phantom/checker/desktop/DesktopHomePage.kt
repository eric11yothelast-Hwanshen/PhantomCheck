package com.phantom.checker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.MessageSummaryDto
import com.phantom.checker.shared.StatsSnapshotDto
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * #520 桌面「總覽 Home」頁：
 * - Row A：連線 Hero 卡（含 #517 電量/充電/網路/最後已知時間與狀態驅動主按鈕）＋控制卡（#513 一鍵勿擾/測試通知、#515 懸浮球、暫停通知 1h）
 * - Row B：KPI 4 卡（總數/今日/未讀/詐騙，點擊直達訊息頁）
 * - Row C：最近訊息前 5 ＋跨端功能快捷格（#509 群組報告、#518 跨端工具、AI 助手、設定）
 */
@Composable
fun DesktopHomePage(
    settings: DesktopSettings,
    tokens: DesktopThemeTokens,
    accentColor: Color,
    windowClass: DesktopWindowClass,
    uiState: DesktopLinkUiState,
    stats: StatsSnapshotDto?,
    recentMessages: List<MessageSummaryDto>,
    server: DesktopLinkServer,
    onNavigateTab: (String) -> Unit,
    onFeedback: (String) -> Unit
) {
    val phoneStatus by DesktopDeviceStatusHolder.status.collectAsState()
    val pausedUntilMs by DesktopNotificationBus.pausedUntilMs.collectAsState()
    val isNotifPaused = pausedUntilMs > System.currentTimeMillis()
    var showQrDialog by remember { mutableStateOf(false) }

    val isConnected = uiState.connState == LinkConnState.CONNECTED
    val statusDotColor = when (uiState.connState) {
        LinkConnState.CONNECTED -> tokens.success
        LinkConnState.WAITING_FOR_PHONE -> tokens.warning
        LinkConnState.ERROR -> tokens.error
        LinkConnState.STOPPED -> tokens.textMuted
    }
    val statusHeadline = when (uiState.connState) {
        LinkConnState.CONNECTED -> uiState.pairedDeviceName.ifBlank { DesktopStrings.statusConnected }
        LinkConnState.WAITING_FOR_PHONE -> DesktopStrings.serverListeningOn(uiState.port, 0)
        LinkConnState.ERROR -> DesktopStrings.statusServerError
        LinkConnState.STOPPED -> DesktopStrings.statusServerStopped
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .then(if (windowClass.maxContentWidth != androidx.compose.ui.unit.Dp.Unspecified) Modifier.widthIn(max = windowClass.maxContentWidth) else Modifier)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(windowClass.pagePadding),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = DesktopStrings.tabHome,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tokens.textPrimary
            )

            // Row A: Hero Connection Card + Quick Control Card
            val renderHeroCard: @Composable (Modifier) -> Unit = { mod ->
                GlassCard(modifier = mod) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(statusDotColor))
                            Text(
                                text = statusHeadline,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = tokens.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        phoneStatus?.let { st ->
                            val chargeLabel = when {
                                !st.isCharging -> ""
                                st.chargeType == "wireless" -> " · ${DesktopStrings.chargeWireless}"
                                st.chargeType == "usb" -> " · USB"
                                st.chargeType == "ac" -> " · AC"
                                else -> " ⚡"
                            }
                            val netLabel = when (st.networkType) {
                                "wifi" -> if (st.wifiSsid.isNotBlank()) "Wi-Fi (${st.wifiSsid})" else "Wi-Fi"
                                "cellular" -> "Cellular"
                                else -> ""
                            }
                            val infoLine = buildList {
                                if (st.batteryLevel >= 0) add("📱 ${st.batteryLevel}%$chargeLabel")
                                if (netLabel.isNotBlank()) add(netLabel)
                                if (st.storageAvailMb >= 0L) add("${DesktopStrings.storageAvail} ${st.storageAvailMb / 1024}GB")
                                if (!isConnected && st.updatedAtMs > 0L) {
                                    add("${DesktopStrings.lastKnownAt} ${DesktopClipboardGuard.formatReportTime(st.updatedAtMs)}")
                                }
                            }.joinToString("  ·  ")
                            if (infoLine.isNotBlank()) {
                                Text(infoLine, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            when {
                                isConnected -> {
                                    SpringOutlinedButton(
                                        onClick = { server.disconnectPhones() },
                                        animationsEnabled = settings.animationsEnabled
                                    ) { Text(DesktopStrings.disconnectBtn) }
                                }
                                uiState.isPaired -> {
                                    SpringButton(
                                        onClick = { server.restart(uiState.port) },
                                        animationsEnabled = settings.animationsEnabled,
                                        containerColor = accentColor
                                    ) { Text(DesktopStrings.serverRestartBtn) }
                                }
                                else -> {
                                    SpringButton(
                                        onClick = { showQrDialog = true },
                                        animationsEnabled = settings.animationsEnabled,
                                        containerColor = accentColor
                                    ) { Text(DesktopStrings.qrConfigurePhoneBtn) }
                                }
                            }
                            SpringOutlinedButton(
                                onClick = { onNavigateTab("SETTINGS") },
                                animationsEnabled = settings.animationsEnabled
                            ) { Text(DesktopStrings.navSettings) }
                        }
                    }
                }
            }

            val renderControlCard: @Composable (Modifier) -> Unit = { mod ->
                GlassCard(modifier = mod) {
                    Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (settings.quickDndActive) DesktopStrings.quickDndOn else DesktopStrings.quickDndOff,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (settings.quickDndActive) tokens.warning else tokens.textPrimary
                            )
                            Switch(
                                checked = settings.quickDndActive,
                                onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(quickDndActive = v) } }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = DesktopStrings.enableFloatingAssistant,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = settings.floatingAssistantEnabled,
                                onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(floatingAssistantEnabled = v) } }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = {
                                val sent = DesktopNotificationBus.dispatchTestNotification(settings)
                                onFeedback(if (sent) DesktopStrings.testNotifSent else DesktopStrings.testNotifSuppressedDnd)
                            }) { Text(DesktopStrings.sendTestNotifBtn) }
                            OutlinedButton(onClick = {
                                if (isNotifPaused) DesktopNotificationBus.resumeNotifications()
                                else DesktopNotificationBus.pauseForOneHour()
                            }) { Text(if (isNotifPaused) DesktopStrings.trayResumeNotif else DesktopStrings.trayPauseNotif1h) }
                        }
                    }
                }
            }

            if (windowClass == DesktopWindowClass.COMPACT) {
                renderHeroCard(Modifier.fillMaxWidth())
                renderControlCard(Modifier.fillMaxWidth())
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    renderHeroCard(Modifier.weight(0.58f))
                    renderControlCard(Modifier.weight(0.42f))
                }
            }

            // Row B: 4 KPI Cards (clickable to jump to Messages)
            val kpis = listOf(
                Triple(DesktopStrings.statsTotal, (stats?.total ?: recentMessages.size).toString(), tokens.accentText),
                Triple(DesktopStrings.statsToday, (stats?.today ?: 0).toString(), tokens.success),
                Triple(DesktopStrings.statsUnread, (stats?.unread ?: recentMessages.count { !it.isRead }).toString(), tokens.warning),
                Triple(DesktopStrings.statsScam, (stats?.scam ?: 0).toString(), tokens.error)
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                kpis.forEach { (label, value, color) ->
                    GlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateTab("MESSAGES") }
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(label, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
                        }
                    }
                }
            }

            // Row C: Recent Messages (Top 5) + Quick Cross-End Entries
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(DesktopStrings.navMessages, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                        OutlinedButton(onClick = { onNavigateTab("MESSAGES") }) { Text(DesktopStrings.navMessages) }
                    }
                    if (recentMessages.isEmpty()) {
                        Text(DesktopStrings.emptyMessages, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                    } else {
                        recentMessages.take(5).forEach { m ->
                            HorizontalDivider(color = tokens.border)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onNavigateTab("MESSAGES") }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${DesktopAppLabels.resolveLabel(m.packageName, m.appLabel)} · ${m.sender}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = tokens.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = m.preview.take(90),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = tokens.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Row D: Quick Cross-End Shortcuts (#509 / #518 / AI)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(
                    DesktopStrings.tabReports to "REPORTS",
                    DesktopStrings.tabTools to "TOOLS",
                    DesktopStrings.navChat to "CHAT",
                    DesktopStrings.navTodos to "TODOS"
                ).forEach { (label, tabKey) ->
                    GlassCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateTab(tabKey) }
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().padding(14.dp), contentAlignment = Alignment.Center) {
                            Text(label, style = MaterialTheme.typography.titleSmall, color = tokens.accentText, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    if (showQrDialog) {
        DesktopQrPairingDialog(
            uiState = uiState,
            server = server,
            accentColor = accentColor,
            containerColor = tokens.dialogContainer,
            animationsEnabled = settings.animationsEnabled,
            onDismiss = { showQrDialog = false }
        )
    }
}
