package com.phantom.checker.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private enum class SettingsSubTab { CONNECTION, SYSTEM, CUSTOMIZE, ABOUT }

/**
 * #468 / #478 / #488 桌面端設定中心（連線配對＋系統設定＋介面自訂與深淺/動畫＋關於頁）。
 */
@Composable
fun DesktopSettingsCenter(
    settings: DesktopSettings,
    accentColor: Color,
    uiState: DesktopLinkUiState,
    mdnsStatus: MdnsBroadcastStatus,
    server: DesktopLinkServer,
    onUpdateAvailable: (DesktopUpdateOutcome.UpdateAvailable) -> Unit = {},
    onFeedback: (String) -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val scope = rememberCoroutineScope()
    var subTab by remember { mutableStateOf(SettingsSubTab.CONNECTION) }
    val connScroll = rememberScrollState()
    val sysScroll = rememberScrollState()
    val custScroll = rememberScrollState()
    val aboutScroll = rememberScrollState()
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var customBgInput by remember(settings.customBgImagePath) { mutableStateOf(settings.customBgImagePath) }
    var portInput by remember(settings.serverPort) { mutableStateOf(settings.serverPort.toString()) }
    var pairCodePair by remember { mutableStateOf(server.getOrGeneratePairCode()) }
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }

    // #487 Windows Firewall self-check & one-click allow state
    var firewallResult by remember { mutableStateOf<FirewallCheckResult?>(null) }
    var checkingFirewall by remember { mutableStateOf(false) }
    var applyingFirewall by remember { mutableStateOf(false) }

    fun runFirewallCheck(port: Int) {
        if (checkingFirewall) return
        checkingFirewall = true
        scope.launch {
            firewallResult = DesktopFirewallHelper.checkFirewallStatus(port)
            checkingFirewall = false
        }
    }

    // #478 About tab recent 10 releases state
    var recentReleases by remember { mutableStateOf<List<DesktopReleaseEntry>>(emptyList()) }
    var loadingReleases by remember { mutableStateOf(false) }
    var releasesError by remember { mutableStateOf<String?>(null) }

    fun loadRecentReleases() {
        if (loadingReleases) return
        loadingReleases = true
        releasesError = null
        scope.launch {
            DesktopUpdater.fetchRecentReleases()
                .onSuccess { list ->
                    recentReleases = list
                    loadingReleases = false
                }
                .onFailure { err ->
                    releasesError = err.message ?: err.javaClass.simpleName
                    loadingReleases = false
                }
        }
    }

    fun triggerManualUpdateCheck() {
        if (checkingUpdate) return
        checkingUpdate = true
        updateStatus = null
        scope.launch {
            when (val res = DesktopUpdater.checkForUpdate(isAutoStartup = false)) {
                is DesktopUpdateOutcome.UpdateAvailable -> {
                    checkingUpdate = false
                    updateStatus = "${res.currentVersion} → ${res.latestVersion} (${res.tagName})"
                    onUpdateAvailable(res)
                }
                is DesktopUpdateOutcome.UpToDate -> {
                    checkingUpdate = false
                    updateStatus = "${DesktopStrings.updateUpToDate} (v${res.currentVersion})"
                }
                is DesktopUpdateOutcome.Error -> {
                    checkingUpdate = false
                    updateStatus = res.message
                }
                is DesktopUpdateOutcome.SilentSkipped -> {
                    checkingUpdate = false
                }
            }
        }
    }

    androidx.compose.runtime.LaunchedEffect(subTab, uiState.port) {
        if (subTab == SettingsSubTab.CONNECTION) {
            server.refreshLocalIps()
            if (firewallResult == null || firewallResult?.port != uiState.port) {
                runFirewallCheck(uiState.port)
            }
            while (true) {
                kotlinx.coroutines.delay(1000L)
                nowMs = System.currentTimeMillis()
                server.getCurrentPairCode(nowMs)?.let { pairCodePair = it }
            }
        } else if (subTab == SettingsSubTab.ABOUT && recentReleases.isEmpty() && !loadingReleases) {
            loadRecentReleases()
        }
    }

    val timeFmt = remember { java.text.SimpleDateFormat("MM/dd HH:mm:ss", java.util.Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = subTab == SettingsSubTab.CONNECTION,
                onClick = { subTab = SettingsSubTab.CONNECTION },
                label = { Text(DesktopStrings.settingsTabConn) }
            )
            FilterChip(
                selected = subTab == SettingsSubTab.SYSTEM,
                onClick = { subTab = SettingsSubTab.SYSTEM },
                label = { Text(DesktopStrings.settingsTabSystem) }
            )
            FilterChip(
                selected = subTab == SettingsSubTab.CUSTOMIZE,
                onClick = { subTab = SettingsSubTab.CUSTOMIZE },
                label = { Text(DesktopStrings.settingsTabCustom16) }
            )
            FilterChip(
                selected = subTab == SettingsSubTab.ABOUT,
                onClick = { subTab = SettingsSubTab.ABOUT },
                label = { Text(DesktopStrings.settingsTabAbout) }
            )
        }
        Text(DesktopStrings.shortcutsHint, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)

        val effectiveTransition = if (settings.animationsEnabled) settings.pageTransitionStyle else PageTransitionStyle.NONE
        val renderSubTab: @Composable (SettingsSubTab) -> Unit = { activeSub ->
            val scrollState = when (activeSub) {
                SettingsSubTab.CONNECTION -> connScroll
                SettingsSubTab.SYSTEM -> sysScroll
                SettingsSubTab.CUSTOMIZE -> custScroll
                SettingsSubTab.ABOUT -> aboutScroll
            }
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
            when (activeSub) {
                SettingsSubTab.CONNECTION -> {
                    // 1. Desktop WSS Server Status + Port + mDNS + Firewall hint
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.serverStatusTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                            Text(
                                text = when (uiState.connState) {
                                    LinkConnState.CONNECTED, LinkConnState.WAITING_FOR_PHONE ->
                                        DesktopStrings.serverListeningOn(uiState.port, uiState.connectedPhones.size)
                                    LinkConnState.ERROR ->
                                        "${DesktopStrings.statusServerError}: ${uiState.lastError.orEmpty()}"
                                    LinkConnState.STOPPED ->
                                        DesktopStrings.statusServerStopped
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (uiState.connState == LinkConnState.ERROR) DesktopPalette.GlassError else tokens.textPrimary
                            )
                            if (uiState.certFingerprint.isNotBlank()) {
                                Text(
                                    text = "${DesktopStrings.fingerprintPrefix}${uiState.certFingerprint.take(16)}…",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = tokens.textMuted
                                )
                            }
                            Text(
                                text = if (mdnsStatus.isAdvertising) {
                                    "${DesktopStrings.mdnsActiveLabel}: ${mdnsStatus.serviceName} (${mdnsStatus.boundIp}:${uiState.port})"
                                } else if (!mdnsStatus.lastError.isNullOrBlank()) {
                                    "${DesktopStrings.mdnsFailedLabel} (${mdnsStatus.lastError})"
                                } else {
                                    DesktopStrings.mdnsFailedLabel
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (mdnsStatus.isAdvertising) DesktopPalette.GlassSuccess else DesktopPalette.GlassWarning
                            )
                            if (uiState.localIps.isNotEmpty()) {
                                Text(
                                    text = "${DesktopStrings.serverLanIpsLabel}: ${uiState.localIps.joinToString("  |  ") { "$it:${uiState.port}" }}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = tokens.textSecondary
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = portInput,
                                    onValueChange = { portInput = it.filter { ch -> ch.isDigit() }.take(5) },
                                    label = { Text(DesktopStrings.serverPortLabel) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                SpringButton(
                                    onClick = {
                                        val parsed = portInput.toIntOrNull()?.coerceIn(1024, 65535)
                                            ?: com.phantom.checker.shared.LinkProtocol.DEFAULT_PORT
                                        DesktopSettingsStore.update { it.copy(serverPort = parsed) }
                                        server.restart(parsed)
                                    },
                                    animationsEnabled = settings.animationsEnabled
                                ) { Text(DesktopStrings.serverRestartBtn) }
                                SpringOutlinedButton(
                                    onClick = {
                                        if (uiState.isServerRunning) server.stop() else server.start()
                                    },
                                    animationsEnabled = settings.animationsEnabled
                                ) { Text(if (uiState.isServerRunning) DesktopStrings.serverStopBtn else DesktopStrings.serverStartBtn) }
                                if (uiState.connectedPhones.isNotEmpty()) {
                                    SpringOutlinedButton(
                                        onClick = { server.disconnectPhones() },
                                        animationsEnabled = settings.animationsEnabled
                                    ) { Text(DesktopStrings.disconnectBtn) }
                                }
                            }
                            Text(DesktopStrings.serverFirewallHint, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                        }
                    }

                    // 1.5 #487 Windows Firewall Self-Check & One-Click Allow
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.firewallSectionTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                            val fw = firewallResult
                            if (fw == null) {
                                Text(
                                    text = if (checkingFirewall) DesktopStrings.firewallChecking else DesktopStrings.firewallSafetyNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                            } else if (!fw.isWindows) {
                                Text(DesktopStrings.firewallNonWindows, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            } else {
                                if (!fw.error.isNullOrBlank()) {
                                    Text(fw.error, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.GlassError)
                                } else {
                                    Text(
                                        text = if (fw.portRulePresent) DesktopStrings.firewallPortRuleOk(fw.port) else DesktopStrings.firewallPortRuleMissing(fw.port),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (fw.portRulePresent) DesktopPalette.GlassSuccess else DesktopPalette.GlassError
                                    )
                                    Text(
                                        text = if (fw.programRulePresent) DesktopStrings.firewallProgRuleOk else DesktopStrings.firewallProgRuleMissing,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (fw.programRulePresent) DesktopPalette.GlassSuccess else tokens.textMuted
                                    )
                                    if (fw.profiles.isNotEmpty()) {
                                        val profText = fw.profiles.joinToString("  |  ") { p ->
                                            val alias = p.interfaceAlias.ifBlank { p.name }
                                            "$alias: ${p.category}"
                                        }
                                        Text(
                                            text = "${DesktopStrings.firewallProfilesLabel} $profText",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = tokens.textSecondary
                                        )
                                    }
                                    if (fw.hasPublicProfile && !fw.portRulePresent) {
                                        Text(
                                            text = DesktopStrings.firewallPublicWarning,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = DesktopPalette.GlassWarning
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                SpringOutlinedButton(
                                    enabled = !checkingFirewall && !applyingFirewall,
                                    onClick = { runFirewallCheck(uiState.port) },
                                    animationsEnabled = settings.animationsEnabled
                                ) {
                                    Text(if (checkingFirewall) DesktopStrings.firewallChecking else DesktopStrings.firewallCheckBtn)
                                }
                                if (DesktopFirewallHelper.isWindowsOs()) {
                                    SpringButton(
                                        enabled = !applyingFirewall,
                                        onClick = {
                                            applyingFirewall = true
                                            scope.launch {
                                                DesktopFirewallHelper.allowInWindowsFirewall(uiState.port)
                                                    .onSuccess { updated ->
                                                        firewallResult = updated
                                                        onFeedback(DesktopStrings.firewallAllowSuccess(uiState.port))
                                                    }
                                                    .onFailure { err ->
                                                        onFeedback(DesktopStrings.firewallAllowFailed(err.message ?: err.javaClass.simpleName))
                                                    }
                                                applyingFirewall = false
                                            }
                                        },
                                        animationsEnabled = settings.animationsEnabled,
                                        containerColor = accentColor
                                    ) {
                                        Text(if (applyingFirewall) DesktopStrings.firewallApplying else DesktopStrings.firewallAllowBtn)
                                    }
                                }
                            }
                            Text(DesktopStrings.firewallSafetyNote, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                        }
                    }

                    // 2. Configure New Phone (QR Code + Single-use 6-digit Pairing Code, 3 min TTL)
                    var showQrDialog by remember { mutableStateOf(false) }
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(DesktopStrings.pairNewPhoneTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                                SpringButton(
                                    enabled = uiState.isServerRunning && uiState.certFingerprint.length == 64,
                                    onClick = { showQrDialog = true },
                                    animationsEnabled = settings.animationsEnabled,
                                    containerColor = accentColor
                                ) { Text(DesktopStrings.qrConfigurePhoneBtn) }
                            }
                            val remSec = ((pairCodePair.second - nowMs) / 1000L).coerceAtLeast(0L)
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (remSec > 0L) pairCodePair.first else "------",
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (remSec > 0L) accentColor else DesktopPalette.GlassError
                                )
                                Text(
                                    text = if (remSec > 0L) {
                                        DesktopStrings.pairCodeRemaining("%d:%02d".format(remSec / 60L, remSec % 60L))
                                    } else {
                                        DesktopStrings.pairCodeExpired
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (remSec > 0L) tokens.textSecondary else DesktopPalette.GlassError
                                )
                                Spacer(Modifier.weight(1f))
                                SpringOutlinedButton(
                                    onClick = { pairCodePair = server.generatePairCode() },
                                    animationsEnabled = settings.animationsEnabled
                                ) { Text(DesktopStrings.pairCodeRegenerate) }
                            }
                        }
                    }
                    if (showQrDialog) {
                        val (_, bgBottom) = DesktopPalette.bgGradientColors(settings.bgTheme)
                        DesktopQrPairingDialog(
                            uiState = uiState,
                            server = server,
                            accentColor = accentColor,
                            containerColor = bgBottom,
                            animationsEnabled = settings.animationsEnabled,
                            onDismiss = { showQrDialog = false }
                        )
                    }

                    // 3. Paired Phones List
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.pairedPhonesTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                                if (uiState.pairedPhones.isNotEmpty()) {
                                    SpringOutlinedButton(
                                        onClick = { server.clearAllPairings() },
                                        animationsEnabled = settings.animationsEnabled
                                    ) { Text(DesktopStrings.unpairBtn, color = DesktopPalette.GlassError) }
                                }
                            }
                            if (uiState.pairedPhones.isEmpty()) {
                                Text(DesktopStrings.pairedPhonesEmpty, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                            } else {
                                uiState.pairedPhones.forEach { phone ->
                                    HorizontalDivider(color = tokens.border)
                                    val isOnline = uiState.connectedPhones.any { it.instanceId == phone.instanceId }
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${if (isOnline) "● " else ""}${phone.name}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isOnline) DesktopPalette.GlassSuccess else tokens.textPrimary
                                            )
                                            Text(
                                                text = "${phone.instanceId.take(8)}… · ${timeFmt.format(java.util.Date(phone.lastSeenAt))}",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = tokens.textMuted
                                            )
                                        }
                                        SpringOutlinedButton(
                                            onClick = { server.revokePhone(phone.instanceId) },
                                            animationsEnabled = settings.animationsEnabled
                                        ) { Text(DesktopStrings.remove, color = DesktopPalette.GlassError) }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Server Audit Log
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.serverAuditTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                if (uiState.auditLogs.isNotEmpty()) {
                                    androidx.compose.material3.TextButton(onClick = { server.clearAuditLogs() }) {
                                        Text(DesktopStrings.clearLogsBtn)
                                    }
                                }
                            }
                            if (uiState.auditLogs.isEmpty()) {
                                Text(DesktopStrings.serverAuditEmpty, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                            } else {
                                uiState.auditLogs.take(20).forEach { entry ->
                                    val mark = if (entry.ok) "✓" else "✕"
                                    Text(
                                        text = "${timeFmt.format(java.util.Date(entry.timestamp))} · $mark ${entry.event} ${entry.detail}".trim(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (entry.ok) tokens.textSecondary else DesktopPalette.GlassError
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSubTab.SYSTEM -> {
                    // 1. Language
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(DesktopStrings.sysLangTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("auto" to DesktopStrings.langAuto, "zh-TW" to "繁體中文", "en" to "English").forEach { (code, label) ->
                                    FilterChip(
                                        selected = settings.language == code,
                                        onClick = { DesktopSettingsStore.update { it.copy(language = code) } },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Auto-start + 3. Close Behavior
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.sysAutoStartTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = settings.autoStart,
                                    onCheckedChange = { enable ->
                                        DesktopSettingsStore.applyWindowsAutoStart(enable)
                                            .onSuccess { onFeedback(DesktopStrings.feedbackAutoStart(enable)) }
                                            .onFailure { onFeedback(it.message ?: it.javaClass.simpleName) }
                                    }
                                )
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.sysCloseBehaviorTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(
                                    selected = settings.closeBehavior == CloseBehavior.MINIMIZE_TO_TRAY,
                                    onClick = { DesktopSettingsStore.update { it.copy(closeBehavior = CloseBehavior.MINIMIZE_TO_TRAY) } },
                                    label = { Text(DesktopStrings.closeMinimizeTray) }
                                )
                                FilterChip(
                                    selected = settings.closeBehavior == CloseBehavior.EXIT_APP,
                                    onClick = { DesktopSettingsStore.update { it.copy(closeBehavior = CloseBehavior.EXIT_APP) } },
                                    label = { Text(DesktopStrings.closeExitApp) }
                                )
                            }
                        }
                    }

                    // 4. Native Notifications & DND (with Start/End Hour Picker supporting overnight window)
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.sysNativeNotifTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.enableNativeNotif, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Switch(
                                    checked = settings.nativeNotifications,
                                    onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(nativeNotifications = v) } }
                                )
                            }
                            val crossDayTag = if (settings.dndStartHour > settings.dndEndHour) " ${DesktopStrings.dndCrossDayHint}" else ""
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${DesktopStrings.enableDnd} (${"%02d:00–%02d:00".format(settings.dndStartHour, settings.dndEndHour)}$crossDayTag)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tokens.textSecondary
                                )
                                Switch(
                                    checked = settings.dndEnabled,
                                    onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(dndEnabled = v) } }
                                )
                            }
                            if (settings.dndEnabled) {
                                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                                androidx.compose.foundation.layout.FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(DesktopStrings.dndStartLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                        SpringOutlinedButton(
                                            onClick = { DesktopSettingsStore.update { it.copy(dndStartHour = (it.dndStartHour + 23) % 24) } },
                                            animationsEnabled = settings.animationsEnabled
                                        ) { Text("-") }
                                        Text("%02d:00".format(settings.dndStartHour), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = tokens.textPrimary)
                                        SpringOutlinedButton(
                                            onClick = { DesktopSettingsStore.update { it.copy(dndStartHour = (it.dndStartHour + 1) % 24) } },
                                            animationsEnabled = settings.animationsEnabled
                                        ) { Text("+") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(DesktopStrings.dndEndLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                        SpringOutlinedButton(
                                            onClick = { DesktopSettingsStore.update { it.copy(dndEndHour = (it.dndEndHour + 23) % 24) } },
                                            animationsEnabled = settings.animationsEnabled
                                        ) { Text("-") }
                                        Text("%02d:00".format(settings.dndEndHour), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = tokens.textPrimary)
                                        SpringOutlinedButton(
                                            onClick = { DesktopSettingsStore.update { it.copy(dndEndHour = (it.dndEndHour + 1) % 24) } },
                                            animationsEnabled = settings.animationsEnabled
                                        ) { Text("+") }
                                    }
                                }
                            }
                        }
                    }

                    // 5. Backoff + 6. Ping + 7. Preview Length
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.sysBackoffTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 30, 60).forEach { sec ->
                                    FilterChip(
                                        selected = settings.maxReconnectBackoffSec == sec,
                                        onClick = { DesktopSettingsStore.update { it.copy(maxReconnectBackoffSec = sec) } },
                                        label = { Text("${sec}s") }
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.sysPingTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(10, 15, 30).forEach { sec ->
                                    FilterChip(
                                        selected = settings.pingIntervalSec == sec,
                                        onClick = { DesktopSettingsStore.update { it.copy(pingIntervalSec = sec) } },
                                        label = { Text("${sec}s") }
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.sysPreviewLenTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(90, 200, 500).forEach { len ->
                                    FilterChip(
                                        selected = settings.previewLength == len,
                                        onClick = { DesktopSettingsStore.update { it.copy(previewLength = len) } },
                                        label = { Text("$len") }
                                    )
                                }
                            }
                        }
                    }

                    // 8. Config/Cache + 9. Manual Update Check + 10. Diagnostics
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.sysConfigTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Text(DesktopPaths.configDir.absolutePath, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = tokens.textMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpringOutlinedButton(onClick = {
                                    DesktopDiagnostics.openConfigFolder().onFailure { onFeedback(it.message ?: DesktopStrings.feedbackOpenFailed) }
                                }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.openConfigFolder) }
                                SpringOutlinedButton(onClick = {
                                    DesktopSettingsStore.resetSettingsToDefaults()
                                    onFeedback(DesktopStrings.feedbackSettingsReset)
                                }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.resetSettingsBtn) }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.sysUpdateTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                SpringButton(
                                    enabled = !checkingUpdate,
                                    animationsEnabled = settings.animationsEnabled,
                                    onClick = { triggerManualUpdateCheck() }
                                ) { Text(if (checkingUpdate) DesktopStrings.checkingUpdate else DesktopStrings.checkUpdateBtn) }
                                updateStatus?.let { st ->
                                    Text(st, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.sysDiagTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpringOutlinedButton(onClick = {
                                    DesktopDiagnostics.openLogsFolder().onFailure { onFeedback(it.message ?: DesktopStrings.feedbackOpenFailed) }
                                }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.openLogsFolder) }
                                SpringOutlinedButton(onClick = {
                                    DesktopDiagnostics.exportDiagnosticBundle()
                                        .onSuccess { onFeedback(DesktopStrings.feedbackExported(it.absolutePath)) }
                                        .onFailure { onFeedback(it.message ?: DesktopStrings.feedbackExportFailed) }
                                }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.exportDiagBtn) }
                            }
                        }
                    }
                }

                SettingsSubTab.CUSTOMIZE -> {
                    var customHexInput by remember(settings.customAccentHex) { mutableStateOf(settings.customAccentHex) }
                    val hexInvalid = customHexInput.isNotBlank() && DesktopPalette.parseHexColor(customHexInput) == null

                    // 1. Accent Color (6 presets) + 9. Custom Accent HEX
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.custAccentTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AccentPreset.entries.forEach { preset ->
                                    val c = DesktopPalette.accentColor(preset)
                                    val selected = settings.accentPreset == preset && settings.customAccentHex.isBlank()
                                    Box(
                                        modifier = Modifier
                                            .size(if (selected) 34.dp else 28.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else tokens.border, CircleShape)
                                            .clickable {
                                                customHexInput = ""
                                                DesktopSettingsStore.update { it.copy(accentPreset = preset, customAccentHex = "") }
                                            }
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.custCustomHexTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customHexInput,
                                    onValueChange = { customHexInput = it.take(7) },
                                    label = { Text(DesktopStrings.custCustomHexHint) },
                                    isError = hexInvalid,
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                SpringButton(
                                    enabled = customHexInput.isBlank() || !hexInvalid,
                                    onClick = {
                                        val normalized = if (customHexInput.isBlank()) "" else {
                                            val raw = customHexInput.trim().removePrefix("#").uppercase()
                                            "#$raw"
                                        }
                                        DesktopSettingsStore.update { it.copy(customAccentHex = normalized) }
                                    },
                                    animationsEnabled = settings.animationsEnabled,
                                    containerColor = accentColor
                                ) { Text(DesktopStrings.todoSave) }
                            }
                            if (hexInvalid) {
                                Text(DesktopStrings.custCustomHexInvalid, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.GlassError)
                            }
                        }
                    }

                    // 2. Density + 3. Font Scale + 10. Time Format + 11. Card Corner + 12. Glass Blur + 13. Chat Font Size
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.custDensityTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                UiDensity.entries.forEach { d ->
                                    FilterChip(
                                        selected = settings.density == d,
                                        onClick = { DesktopSettingsStore.update { it.copy(density = d) } },
                                        label = { Text(DesktopStrings.densityLabel(d)) }
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.custFontScaleTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0.9f to "90%", 1.0f to "100%", 1.1f to "110%", 1.25f to "125%").forEach { (sc, lbl) ->
                                    FilterChip(
                                        selected = kotlin.math.abs(settings.fontScale - sc) < 0.01f,
                                        onClick = { DesktopSettingsStore.update { it.copy(fontScale = sc) } },
                                        label = { Text(lbl) }
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.custTimeFormatTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = settings.use24HourTime,
                                    onClick = { DesktopSettingsStore.update { it.copy(use24HourTime = true) } },
                                    label = { Text(DesktopStrings.time24h) }
                                )
                                FilterChip(
                                    selected = !settings.use24HourTime,
                                    onClick = { DesktopSettingsStore.update { it.copy(use24HourTime = false) } },
                                    label = { Text(DesktopStrings.time12h) }
                                )
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.custCardCornerTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(12, 16, 20, 24).forEach { dpVal ->
                                    FilterChip(
                                        selected = settings.messageCardCornerDp == dpVal,
                                        onClick = { DesktopSettingsStore.update { it.copy(messageCardCornerDp = dpVal) } },
                                        label = { Text("${dpVal}dp") }
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.custGlassBlurTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                GlassBlurLevel.entries.forEach { lvl ->
                                    FilterChip(
                                        selected = settings.glassBlurLevel == lvl,
                                        onClick = {
                                            val mappedOpacity = when (lvl) {
                                                GlassBlurLevel.LOW -> 0.88f
                                                GlassBlurLevel.MEDIUM -> 0.74f
                                                GlassBlurLevel.HIGH -> 0.56f
                                            }
                                            DesktopSettingsStore.update { it.copy(glassBlurLevel = lvl, surfaceOpacity = mappedOpacity) }
                                        },
                                        label = { Text(DesktopStrings.glassBlurLabel(lvl)) }
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.custChatFontSizeTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                ChatFontSize.entries.forEach { sz ->
                                    FilterChip(
                                        selected = settings.chatFontSize == sz,
                                        onClick = { DesktopSettingsStore.update { it.copy(chatFontSize = sz) } },
                                        label = { Text(DesktopStrings.chatFontSizeLabel(sz)) }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Sidebar Width & Collapse + 5. Message Card Style + 6. Mirror Display Mode
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.custSidebarTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf(210, 244, 280).forEach { w ->
                                    FilterChip(
                                        selected = settings.sidebarWidthDp == w,
                                        onClick = { DesktopSettingsStore.update { it.copy(sidebarWidthDp = w) } },
                                        label = { Text("${w}dp") }
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(DesktopStrings.collapseSidebar, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                Switch(
                                    checked = settings.sidebarCollapsed,
                                    onCheckedChange = { c -> DesktopSettingsStore.update { it.copy(sidebarCollapsed = c) } }
                                )
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.custCardStyleTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MessageCardStyle.entries.forEach { st ->
                                    FilterChip(
                                        selected = settings.messageCardStyle == st,
                                        onClick = { DesktopSettingsStore.update { it.copy(messageCardStyle = st) } },
                                        label = { Text(DesktopStrings.cardStyleLabel(st)) }
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.custMirrorModeTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                MirrorDisplayMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.mirrorMode == mode,
                                        onClick = { DesktopSettingsStore.update { it.copy(mirrorMode = mode) } },
                                        label = { Text(DesktopStrings.mirrorModeLabel(mode)) }
                                    )
                                }
                            }
                        }
                    }

                    // 7. Background Theme + 8. Animations + 14. Notif Sound + 15. Auto-Connect + 16. Default Startup Tab
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.custBgThemeTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                BgThemePreset.entries.forEach { bg ->
                                    FilterChip(
                                        selected = settings.bgTheme == bg,
                                        onClick = { DesktopSettingsStore.update { it.copy(bgTheme = bg) } },
                                        label = { Text(DesktopStrings.bgThemeLabel(bg)) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customBgInput,
                                    onValueChange = { customBgInput = it },
                                    label = { Text(DesktopStrings.customBgPathHint) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                SpringOutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            DesktopBackgroundHelper.pickAndCopyBackgroundImage()
                                                .onSuccess { copiedPath ->
                                                    if (!copiedPath.isNullOrBlank()) {
                                                        customBgInput = copiedPath
                                                        DesktopSettingsStore.update { it.copy(customBgImagePath = copiedPath) }
                                                        onFeedback(DesktopStrings.bgPickerSaved)
                                                    }
                                                }
                                                .onFailure { err ->
                                                    onFeedback(DesktopStrings.bgPickerFailed(err.message ?: err.javaClass.simpleName))
                                                }
                                        }
                                    },
                                    animationsEnabled = settings.animationsEnabled
                                ) { Text(DesktopStrings.bgPickerBtn) }
                                SpringButton(
                                    onClick = {
                                        val trimmed = customBgInput.trim()
                                        if (trimmed.isBlank()) {
                                            DesktopBackgroundHelper.removeManagedBackground(settings.customBgImagePath)
                                            DesktopSettingsStore.update { it.copy(customBgImagePath = "") }
                                            onFeedback(DesktopStrings.bgRemoved)
                                        } else {
                                            DesktopSettingsStore.update { it.copy(customBgImagePath = trimmed) }
                                        }
                                    },
                                    animationsEnabled = settings.animationsEnabled,
                                    containerColor = accentColor
                                ) { Text(DesktopStrings.todoSave) }
                                if (settings.customBgImagePath.isNotBlank()) {
                                    SpringOutlinedButton(
                                        onClick = {
                                            DesktopBackgroundHelper.removeManagedBackground(settings.customBgImagePath)
                                            customBgInput = ""
                                            DesktopSettingsStore.update { it.copy(customBgImagePath = "") }
                                            onFeedback(DesktopStrings.bgRemoved)
                                        },
                                        animationsEnabled = settings.animationsEnabled
                                    ) { Text(DesktopStrings.clearBgImage) }
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            // #488 Surface Opacity Slider (0.55..0.96)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.custSurfaceOpacityTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                                Text("${(settings.surfaceOpacity * 100f).toInt()}%", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = accentColor)
                            }
                            Slider(
                                value = settings.surfaceOpacity,
                                onValueChange = { v -> DesktopSettingsStore.update { it.copy(surfaceOpacity = v.coerceIn(0.55f, 0.96f)) } },
                                valueRange = 0.55f..0.96f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            HorizontalDivider(color = tokens.border)
                            // #488 Background Dim / Scrim Depth Slider (0.15..0.85)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.custBgScrimTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                                Text("${(settings.bgScrimAlpha * 100f).toInt()}%", style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = accentColor)
                            }
                            Slider(
                                value = settings.bgScrimAlpha,
                                onValueChange = { v -> DesktopSettingsStore.update { it.copy(bgScrimAlpha = v.coerceIn(0.15f, 0.85f)) } },
                                valueRange = 0.15f..0.85f,
                                modifier = Modifier.fillMaxWidth()
                            )
                            HorizontalDivider(color = tokens.border)
                            // #488 High-Contrast Text Toggle
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.custHighContrastTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = settings.highContrastText,
                                    onCheckedChange = { hc -> DesktopSettingsStore.update { it.copy(highContrastText = hc) } }
                                )
                            }
                            HorizontalDivider(color = tokens.border)
                            // #488 Page Transition Style Selector (4+ styles + Off)
                            Text(DesktopStrings.custTransitionTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                PageTransitionStyle.entries.forEach { style ->
                                    FilterChip(
                                        selected = settings.pageTransitionStyle == style,
                                        onClick = { DesktopSettingsStore.update { it.copy(pageTransitionStyle = style) } },
                                        label = { Text(DesktopStrings.transitionStyleLabel(style)) }
                                    )
                                }
                            }
                            HorizontalDivider(color = tokens.border)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.custAnimTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = settings.animationsEnabled,
                                    onCheckedChange = { a -> DesktopSettingsStore.update { it.copy(animationsEnabled = a) } }
                                )
                            }
                            HorizontalDivider(color = tokens.border)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.custNotifSoundTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = settings.notificationSoundEnabled,
                                    onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(notificationSoundEnabled = v) } }
                                )
                            }
                            HorizontalDivider(color = tokens.border)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.custAutoConnectTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = settings.autoConnectOnStartup,
                                    onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(autoConnectOnStartup = v) } }
                                )
                            }
                            HorizontalDivider(color = tokens.border)
                            Text(DesktopStrings.custDefaultTabTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("MESSAGES", "STATS", "TODOS", "CHAT", "RULES", "SETTINGS").forEach { tabKey ->
                                    FilterChip(
                                        selected = settings.defaultStartupTab == tabKey,
                                        onClick = { DesktopSettingsStore.update { it.copy(defaultStartupTab = tabKey) } },
                                        label = { Text(DesktopStrings.startupTabLabel(tabKey)) }
                                    )
                                }
                            }
                        }
                    }
                }

                SettingsSubTab.ABOUT -> {
                    // 1. Version & Protocol Compatibility + Manual Update Check
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.aboutTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                            Text(DesktopStrings.versionLabel, style = MaterialTheme.typography.bodyMedium, color = accentColor)
                            Text(DesktopStrings.aboutProtocolCompat, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                SpringButton(
                                    enabled = !checkingUpdate,
                                    animationsEnabled = settings.animationsEnabled,
                                    containerColor = accentColor,
                                    onClick = { triggerManualUpdateCheck() }
                                ) { Text(if (checkingUpdate) DesktopStrings.checkingUpdate else DesktopStrings.checkUpdateBtn) }
                                updateStatus?.let { st ->
                                    Text(st, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                                }
                            }
                        }
                    }

                    // 2. Project Links (Repo / Releases / LICENSE / PRIVACY / DISCLAIMER)
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.aboutLinksTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                            androidx.compose.foundation.layout.FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    DesktopStrings.aboutRepoBtn to DesktopUpdater.REPO_URL,
                                    DesktopStrings.aboutReleasesBtn to DesktopUpdater.RELEASES_URL,
                                    DesktopStrings.aboutLicenseBtn to DesktopUpdater.LICENSE_URL,
                                    DesktopStrings.aboutPrivacyBtn to DesktopUpdater.PRIVACY_URL,
                                    DesktopStrings.aboutDisclaimerBtn to DesktopUpdater.DISCLAIMER_URL
                                ).forEach { (label, url) ->
                                    SpringOutlinedButton(
                                        onClick = {
                                            DesktopUpdater.openExternalUrl(url)
                                                .onFailure { onFeedback(it.message ?: DesktopStrings.openLinkFailed) }
                                        },
                                        animationsEnabled = settings.animationsEnabled
                                    ) { Text(label) }
                                }
                            }
                        }
                    }

                    // 3. Recent 10 Releases Markdown Changelog Viewer
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.aboutReleaseNotesTitle, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                                SpringOutlinedButton(
                                    enabled = !loadingReleases,
                                    onClick = { loadRecentReleases() },
                                    animationsEnabled = settings.animationsEnabled
                                ) { Text(DesktopStrings.refresh) }
                            }
                            if (loadingReleases) {
                                Text(DesktopStrings.aboutLoadingNotes, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                            } else if (!releasesError.isNullOrBlank()) {
                                Text(releasesError.orEmpty(), style = MaterialTheme.typography.bodySmall, color = DesktopPalette.GlassError)
                            } else if (recentReleases.isEmpty()) {
                                Text(DesktopStrings.updateNoNotes, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                            } else {
                                recentReleases.forEachIndexed { idx, rel ->
                                    if (idx > 0) HorizontalDivider(color = tokens.border, modifier = Modifier.padding(vertical = 6.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${rel.tagName}  (${rel.publishedDate})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = accentColor
                                        )
                                        rel.desktopAssetVersion?.let { ver ->
                                            Text(
                                                text = "Desktop v$ver",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontFamily = FontFamily.Monospace,
                                                color = tokens.textMuted
                                            )
                                        }
                                    }
                                    DesktopMarkdownText(
                                        markdown = rel.body.ifBlank { DesktopStrings.updateNoNotes },
                                        accentColor = accentColor
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        }
        if (effectiveTransition != PageTransitionStyle.NONE) {
            AnimatedContent(
                targetState = subTab,
                transitionSpec = {
                    val forward = targetState.ordinal >= initialState.ordinal
                    DesktopMotion.pageTransition(effectiveTransition, forward)
                },
                label = "settings_subtab_content",
                modifier = Modifier.fillMaxSize()
            ) { activeSub -> renderSubTab(activeSub) }
        } else {
            Box(modifier = Modifier.fillMaxSize()) { renderSubTab(subTab) }
        }
    }
}
