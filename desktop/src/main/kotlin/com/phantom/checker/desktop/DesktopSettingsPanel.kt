package com.phantom.checker.desktop

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

private enum class SettingsSubTab { CONNECTION, SYSTEM, CUSTOMIZE }

/**
 * #468 桌面端設定中心（連線配對＋10 項系統設定＋8 項自訂設定）。
 */
@Composable
fun DesktopSettingsCenter(
    settings: DesktopSettings,
    accentColor: Color,
    uiState: DesktopLinkUiState,
    devices: List<DiscoveredPhone>,
    isScanning: Boolean,
    manualHostPort: String,
    onManualHostPortChange: (String) -> Unit,
    manualCode: String,
    onManualCodeChange: (String) -> Unit,
    selectedFingerprintPrefix: String,
    onSelectDevice: (DiscoveredPhone) -> Unit,
    onPairAndConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onClearPairing: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var subTab by remember { mutableStateOf(SettingsSubTab.CONNECTION) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var customBgInput by remember(settings.customBgImagePath) { mutableStateOf(settings.customBgImagePath) }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                label = { Text(DesktopStrings.settingsTabCustom) }
            )
            Spacer(Modifier.weight(1f))
            Text(DesktopStrings.shortcutsHint, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextMuted)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (subTab) {
                SettingsSubTab.CONNECTION -> {
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.discoveredDevices, style = MaterialTheme.typography.titleMedium, color = DesktopPalette.TextPrimary)
                            Text(DesktopStrings.firewallHint, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextMuted)
                            if (uiState.isPaired) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SpringOutlinedButton(onClick = onDisconnect, animationsEnabled = settings.animationsEnabled) {
                                        Text(DesktopStrings.disconnectBtn)
                                    }
                                    SpringOutlinedButton(onClick = onClearPairing, animationsEnabled = settings.animationsEnabled) {
                                        Text(DesktopStrings.unpairBtn, color = DesktopPalette.GlassError)
                                    }
                                }
                            }
                            if (devices.isEmpty()) {
                                Text(
                                    text = if (isScanning) DesktopStrings.statusScanning else DesktopStrings.noDevicesFound,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = DesktopPalette.TextMuted
                                )
                            } else {
                                devices.forEach { dev ->
                                    HorizontalDivider(color = DesktopPalette.GlassBorder)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("${dev.friendlyName} (${dev.host}:${dev.port})", style = MaterialTheme.typography.bodyMedium, color = DesktopPalette.TextPrimary)
                                            if (dev.fingerprintPrefix.isNotBlank()) {
                                                Text("${DesktopStrings.fingerprintPrefix}${dev.fingerprintPrefix}…", style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextMuted, fontFamily = FontFamily.Monospace)
                                            }
                                        }
                                        SpringOutlinedButton(onClick = { onSelectDevice(dev) }, animationsEnabled = settings.animationsEnabled) {
                                            Text(DesktopStrings.selectDevice)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(DesktopStrings.manualPairTitle, style = MaterialTheme.typography.titleMedium, color = DesktopPalette.TextPrimary)
                            OutlinedTextField(value = manualHostPort, onValueChange = onManualHostPortChange, label = { Text(DesktopStrings.hostPortLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = manualCode, onValueChange = onManualCodeChange, label = { Text(DesktopStrings.pairCodeLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            if (selectedFingerprintPrefix.isNotBlank()) {
                                Text("${DesktopStrings.fingerprintPrefix}$selectedFingerprintPrefix…", style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextMuted, fontFamily = FontFamily.Monospace)
                            }
                            SpringButton(
                                onClick = onPairAndConnect,
                                enabled = manualHostPort.isNotBlank() && manualCode.length == 6,
                                animationsEnabled = settings.animationsEnabled
                            ) { Text(DesktopStrings.pairBtn) }
                        }
                    }
                }

                SettingsSubTab.SYSTEM -> {
                    // 1. Language
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(DesktopStrings.sysLangTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("auto" to "Auto", "zh-TW" to "繁體中文", "en" to "English").forEach { (code, label) ->
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
                                Text(DesktopStrings.sysAutoStartTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = settings.autoStart,
                                    onCheckedChange = { enable ->
                                        DesktopSettingsStore.applyWindowsAutoStart(enable)
                                            .onSuccess { onFeedback("Auto-start: $it") }
                                            .onFailure { onFeedback(it.message ?: it.javaClass.simpleName) }
                                    }
                                )
                            }
                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Text(DesktopStrings.sysCloseBehaviorTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                    // 4. Native Notifications & DND
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.sysNativeNotifTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.enableNativeNotif, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextSecondary)
                                Switch(
                                    checked = settings.nativeNotifications,
                                    onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(nativeNotifications = v) } }
                                )
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("${DesktopStrings.enableDnd} (${"%02d:00–%02d:00".format(settings.dndStartHour, settings.dndEndHour)})", style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextSecondary)
                                Switch(
                                    checked = settings.dndEnabled,
                                    onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(dndEnabled = v) } }
                                )
                            }
                        }
                    }

                    // 5. Backoff + 6. Ping + 7. Preview Length
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.sysBackoffTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 30, 60).forEach { sec ->
                                    FilterChip(
                                        selected = settings.maxReconnectBackoffSec == sec,
                                        onClick = { DesktopSettingsStore.update { it.copy(maxReconnectBackoffSec = sec) } },
                                        label = { Text("${sec}s") }
                                    )
                                }
                            }
                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Text(DesktopStrings.sysPingTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(10, 15, 30).forEach { sec ->
                                    FilterChip(
                                        selected = settings.pingIntervalSec == sec,
                                        onClick = { DesktopSettingsStore.update { it.copy(pingIntervalSec = sec) } },
                                        label = { Text("${sec}s") }
                                    )
                                }
                            }
                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Text(DesktopStrings.sysPreviewLenTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
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
                            Text(DesktopStrings.sysConfigTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Text(DesktopPaths.configDir.absolutePath, style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace, color = DesktopPalette.TextMuted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpringOutlinedButton(onClick = {
                                    DesktopDiagnostics.openConfigFolder().onFailure { onFeedback(it.message ?: "Open failed") }
                                }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.openConfigFolder) }
                                SpringOutlinedButton(onClick = {
                                    DesktopSettingsStore.resetSettingsToDefaults()
                                    onFeedback("Settings reset")
                                }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.resetSettingsBtn) }
                            }
                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Text(DesktopStrings.sysUpdateTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                SpringButton(
                                    enabled = !checkingUpdate,
                                    animationsEnabled = settings.animationsEnabled,
                                    onClick = {
                                        checkingUpdate = true
                                        updateStatus = null
                                        scope.launch {
                                            val res = DesktopSettingsStore.checkLatestRelease()
                                            checkingUpdate = false
                                            updateStatus = if (res.ok) "Latest: ${res.latestTag} (${res.htmlUrl})" else "Error: ${res.errorMessage}"
                                        }
                                    }
                                ) { Text(if (checkingUpdate) DesktopStrings.checkingUpdate else DesktopStrings.checkUpdateBtn) }
                                updateStatus?.let { st ->
                                    Text(st, style = MaterialTheme.typography.labelSmall, color = DesktopPalette.TextSecondary)
                                }
                            }
                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Text(DesktopStrings.sysDiagTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SpringOutlinedButton(onClick = {
                                    DesktopDiagnostics.openLogsFolder().onFailure { onFeedback(it.message ?: "Open failed") }
                                }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.openLogsFolder) }
                                SpringOutlinedButton(onClick = {
                                    DesktopDiagnostics.exportDiagnosticBundle()
                                        .onSuccess { onFeedback("Exported: ${it.absolutePath}") }
                                        .onFailure { onFeedback(it.message ?: "Export failed") }
                                }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.exportDiagBtn) }
                            }
                        }
                    }
                }

                SettingsSubTab.CUSTOMIZE -> {
                    // 1. Accent Color (6 presets)
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.custAccentTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                AccentPreset.entries.forEach { preset ->
                                    val c = DesktopPalette.accentColor(preset)
                                    val selected = settings.accentPreset == preset
                                    Box(
                                        modifier = Modifier
                                            .size(if (selected) 34.dp else 28.dp)
                                            .clip(CircleShape)
                                            .background(c)
                                            .border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else DesktopPalette.GlassBorder, CircleShape)
                                            .clickable { DesktopSettingsStore.update { it.copy(accentPreset = preset) } }
                                    )
                                }
                            }
                        }
                    }

                    // 2. Density + 3. Font Scale
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.custDensityTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                UiDensity.entries.forEach { d ->
                                    FilterChip(
                                        selected = settings.density == d,
                                        onClick = { DesktopSettingsStore.update { it.copy(density = d) } },
                                        label = { Text(d.name) }
                                    )
                                }
                            }
                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Text(DesktopStrings.custFontScaleTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0.9f to "90%", 1.0f to "100%", 1.1f to "110%", 1.25f to "125%").forEach { (sc, lbl) ->
                                    FilterChip(
                                        selected = kotlin.math.abs(settings.fontScale - sc) < 0.01f,
                                        onClick = { DesktopSettingsStore.update { it.copy(fontScale = sc) } },
                                        label = { Text(lbl) }
                                    )
                                }
                            }
                        }
                    }

                    // 4. Sidebar Width & Collapse + 5. Message Card Style + 6. Mirror Display Mode
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.custSidebarTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf(210, 244, 280).forEach { w ->
                                    FilterChip(
                                        selected = settings.sidebarWidthDp == w,
                                        onClick = { DesktopSettingsStore.update { it.copy(sidebarWidthDp = w) } },
                                        label = { Text("${w}dp") }
                                    )
                                }
                                Spacer(Modifier.weight(1f))
                                Text(DesktopStrings.collapseSidebar, style = MaterialTheme.typography.bodySmall, color = DesktopPalette.TextSecondary)
                                Switch(
                                    checked = settings.sidebarCollapsed,
                                    onCheckedChange = { c -> DesktopSettingsStore.update { it.copy(sidebarCollapsed = c) } }
                                )
                            }
                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Text(DesktopStrings.custCardStyleTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MessageCardStyle.entries.forEach { st ->
                                    FilterChip(
                                        selected = settings.messageCardStyle == st,
                                        onClick = { DesktopSettingsStore.update { it.copy(messageCardStyle = st) } },
                                        label = { Text(st.name) }
                                    )
                                }
                            }
                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Text(DesktopStrings.custMirrorModeTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MirrorDisplayMode.entries.forEach { mode ->
                                    FilterChip(
                                        selected = settings.mirrorMode == mode,
                                        onClick = { DesktopSettingsStore.update { it.copy(mirrorMode = mode) } },
                                        label = { Text(mode.name) }
                                    )
                                }
                            }
                        }
                    }

                    // 7. Background Theme (4 Gradients + Custom Image) + 8. Animations
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(DesktopStrings.custBgThemeTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                BgThemePreset.entries.forEach { bg ->
                                    FilterChip(
                                        selected = settings.bgTheme == bg,
                                        onClick = { DesktopSettingsStore.update { it.copy(bgTheme = bg) } },
                                        label = { Text(bg.name) }
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
                                SpringButton(
                                    onClick = { DesktopSettingsStore.update { it.copy(customBgImagePath = customBgInput.trim()) } },
                                    animationsEnabled = settings.animationsEnabled,
                                    containerColor = accentColor
                                ) { Text(DesktopStrings.todoSave) }
                                if (settings.customBgImagePath.isNotBlank()) {
                                    SpringOutlinedButton(
                                        onClick = {
                                            customBgInput = ""
                                            DesktopSettingsStore.update { it.copy(customBgImagePath = "") }
                                        },
                                        animationsEnabled = settings.animationsEnabled
                                    ) { Text(DesktopStrings.clearBgImage) }
                                }
                            }
                            HorizontalDivider(color = DesktopPalette.GlassBorder)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(DesktopStrings.custAnimTitle, style = MaterialTheme.typography.titleSmall, color = DesktopPalette.TextPrimary)
                                Switch(
                                    checked = settings.animationsEnabled,
                                    onCheckedChange = { a -> DesktopSettingsStore.update { it.copy(animationsEnabled = a) } }
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
