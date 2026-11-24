package com.phantom.checker.ui.link

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phantom.checker.R
import com.phantom.checker.service.DesktopLinkClient
import com.phantom.checker.service.PhoneLinkConnState
import com.phantom.checker.shared.LinkErrorCode
import com.phantom.checker.shared.LinkProtocol
import com.phantom.checker.shared.QrPairingPayloadV2
import com.phantom.checker.util.DesktopLinkStore
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * #476 / #477 手機端桌面連線共用內容（供 LinkHubActivity 與 MainActivity 底部「連線」分頁共用）：
 * - 手機作為 OkHttp WSS 用戶端，以 TLS SHA-256 指紋 pinning 連線桌面端 WSS 伺服器
 * - 支援 CameraX + ML Kit bundled 全屏掃碼配對、待配對資訊確認卡、CAMERA 權限引導、手動備援與診斷紀錄
 */
@Composable
fun LinkHubContent(
    initialError: String? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val clientStatus by DesktopLinkClient.status.collectAsState()
    val discoveredDesktops by DesktopLinkClient.discoveredDesktops.collectAsState()

    var localError by remember { mutableStateOf(initialError) }
    var linkEnabled by remember {
        mutableStateOf(runCatching { DesktopLinkStore.isLinkEnabled(appCtx) }.getOrDefault(true))
    }
    var pairedDevices by remember {
        mutableStateOf(runCatching { DesktopLinkStore.getPairedDevices(appCtx) }.getOrDefault(emptyList()))
    }
    var unknownAttempts by remember {
        mutableStateOf(runCatching { DesktopLinkStore.getUnknownAttempts(appCtx) }.getOrDefault(emptyList()))
    }
    var auditLogs by remember {
        mutableStateOf(runCatching { DesktopLinkStore.getAuditLogs(appCtx) }.getOrDefault(emptyList()))
    }
    var diagnostics by remember {
        mutableStateOf(runCatching { DesktopLinkStore.getDiagnostics(appCtx) }.getOrDefault(emptyList()))
    }

    // #477 QR Scanner & CAMERA permission states (intentionally non-saveable so Activity recreate never retains stale scan session)
    var showQrScanner by remember { mutableStateOf(false) }
    var cameraPermissionDenied by remember { mutableStateOf(false) }
    var pendingQrPayload by remember { mutableStateOf<QrPairingPayloadV2?>(null) }
    var qrErrorMsg by remember { mutableStateOf<String?>(null) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            cameraPermissionDenied = false
            qrErrorMsg = null
            showQrScanner = true
        } else {
            cameraPermissionDenied = true
            showQrScanner = false
        }
    }

    fun launchQrScan() {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            cameraPermissionDenied = false
            qrErrorMsg = null
            showQrScanner = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Manual pairing fallback inputs
    var manualHostPort by remember { mutableStateOf("") }
    var manualCode by remember { mutableStateOf("") }
    var manualFp by remember { mutableStateOf("") }
    var manualDesktopName by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        DesktopLinkClient.startDiscovery(appCtx)
        onDispose {
            showQrScanner = false
            DesktopLinkClient.stopDiscovery()
        }
    }

    LaunchedEffect(Unit) {
        if (linkEnabled) {
            runCatching { DesktopLinkClient.autoConnectIfPaired(appCtx) }
                .onFailure { err -> if (localError.isNullOrBlank()) localError = err.message ?: err.javaClass.simpleName }
        }
        while (true) {
            delay(1500L)
            linkEnabled = runCatching { DesktopLinkStore.isLinkEnabled(appCtx) }.getOrDefault(linkEnabled)
            pairedDevices = runCatching { DesktopLinkStore.getPairedDevices(appCtx) }.getOrDefault(pairedDevices)
            unknownAttempts = runCatching { DesktopLinkStore.getUnknownAttempts(appCtx) }.getOrDefault(unknownAttempts)
            auditLogs = runCatching { DesktopLinkStore.getAuditLogs(appCtx) }.getOrDefault(auditLogs)
            diagnostics = runCatching { DesktopLinkStore.getDiagnostics(appCtx) }.getOrDefault(diagnostics)
        }
    }

    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }
    val classifiedErrorText = resolveClassifiedErrorText(clientStatus.lastErrorCode, clientStatus.lastErrorDetail)
        ?: qrErrorMsg?.takeIf { it.isNotBlank() }
        ?: localError?.takeIf { it.isNotBlank() }

    if (showQrScanner) {
        QrScannerDialog(
            onScanned = { raw ->
                showQrScanner = false
                when (val parsed = QrPairingParser.parse(raw)) {
                    is QrParseResult.Valid -> {
                        qrErrorMsg = null
                        pendingQrPayload = parsed.payload
                    }
                    is QrParseResult.VersionMismatch -> {
                        pendingQrPayload = null
                        qrErrorMsg = ctx.getString(R.string.link_hub_qr_version_mismatch, parsed.foundVersion, parsed.expectedVersion)
                    }
                    is QrParseResult.Invalid -> {
                        pendingQrPayload = null
                        qrErrorMsg = ctx.getString(R.string.link_hub_qr_invalid, parsed.reason)
                    }
                }
            },
            onDismiss = { showQrScanner = false }
        )
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 自動連線總開關 + 主掃碼配對入口
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.link_hub_client_master_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.link_hub_client_master_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = linkEnabled,
                            onCheckedChange = { checked ->
                                linkEnabled = checked
                                runCatching {
                                    DesktopLinkStore.setLinkEnabled(appCtx, checked)
                                    if (checked) {
                                        DesktopLinkClient.autoConnectIfPaired(appCtx)
                                    } else {
                                        DesktopLinkClient.disconnect(clearError = true)
                                    }
                                    localError = null
                                }.onFailure { err ->
                                    localError = err.message ?: err.javaClass.simpleName
                                }
                            }
                        )
                    }
                    Button(
                        onClick = { launchQrScan() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.link_hub_qr_scan_btn))
                    }
                }
            }
        }

        // 1.5 CAMERA 權限拒絕引導卡
        if (cameraPermissionDenied) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.link_hub_camera_perm_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = stringResource(R.string.link_hub_camera_perm_rationale),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", ctx.packageName, null)
                                    }
                                    runCatching { ctx.startActivity(intent) }
                                }
                            ) {
                                Text(stringResource(R.string.link_hub_camera_open_settings))
                            }
                            Button(onClick = { launchQrScan() }) {
                                Text(stringResource(R.string.link_hub_camera_retry))
                            }
                        }
                    }
                }
            }
        }

        // 1.8 待配對電腦資訊確認卡（掃碼成功後由使用者確認才建立 wss + 指紋 pinning 連線）
        pendingQrPayload?.let { pending ->
            item {
                val mdnsMatch = discoveredDesktops.firstOrNull {
                    it.host == pending.ip && it.port == pending.port &&
                        (it.fingerprintPrefix.isBlank() || pending.fp.startsWith(it.fingerprintPrefix, ignoreCase = true))
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.link_hub_qr_confirm_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.link_hub_qr_confirm_pc, pending.name),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = stringResource(R.string.link_hub_qr_confirm_addr, pending.ip, pending.port),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = stringResource(R.string.link_hub_qr_confirm_fp, pending.fp.take(8)),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace
                        )
                        if (mdnsMatch != null) {
                            Text(
                                text = stringResource(R.string.link_hub_qr_mdns_verified, mdnsMatch.friendlyName),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    DesktopLinkClient.pairAndConnect(
                                        ctx = appCtx,
                                        host = pending.ip,
                                        port = pending.port,
                                        code = pending.code,
                                        expectedFingerprint = pending.fp,
                                        desktopName = pending.name
                                    )
                                    pendingQrPayload = null
                                }
                            ) {
                                Text(stringResource(R.string.link_hub_qr_confirm_btn))
                            }
                            OutlinedButton(onClick = { pendingQrPayload = null }) {
                                Text(stringResource(R.string.link_hub_qr_cancel_btn))
                            }
                        }
                    }
                }
            }
        }

        // 2. 連線狀態與分類錯誤回饋
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.link_hub_status_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    val statusLine = when (clientStatus.connState) {
                        PhoneLinkConnState.CONNECTED -> stringResource(
                            R.string.link_hub_client_connected,
                            clientStatus.connectedDesktopName.ifBlank { clientStatus.connectedHostPort },
                            clientStatus.connectedHostPort
                        )
                        PhoneLinkConnState.CONNECTING -> stringResource(
                            R.string.link_hub_client_connecting,
                            clientStatus.connectedHostPort
                        )
                        PhoneLinkConnState.RECONNECTING -> stringResource(
                            R.string.link_hub_client_reconnecting,
                            clientStatus.reconnectRemainingSec,
                            clientStatus.reconnectTotalSec
                        )
                        PhoneLinkConnState.DISCONNECTED -> stringResource(R.string.link_hub_client_disconnected)
                    }
                    Text(text = statusLine, style = MaterialTheme.typography.bodyMedium)
                    if (!classifiedErrorText.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = classifiedErrorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    if (clientStatus.connState != PhoneLinkConnState.DISCONNECTED) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { DesktopLinkClient.disconnect(clearError = true) }) {
                            Text(stringResource(R.string.link_hub_disconnect_btn))
                        }
                    }
                }
            }
        }

        // 3. 區網 mDNS 發現的桌面端
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.link_hub_discovered_pcs_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (discoveredDesktops.isEmpty()) {
                        Text(
                            text = stringResource(R.string.link_hub_discovered_pcs_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        discoveredDesktops.forEachIndexed { idx, pc ->
                            if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${pc.friendlyName} (${pc.host}:${pc.port})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    if (pc.fingerprintPrefix.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.link_hub_paired_fp_prefix, pc.fingerprintPrefix),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                OutlinedButton(onClick = {
                                    manualHostPort = "${pc.host}:${pc.port}"
                                    manualFp = pc.fingerprintPrefix
                                    manualDesktopName = pc.friendlyName
                                }) {
                                    Text(stringResource(R.string.link_hub_select_pc))
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. 手動配對備援
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.link_hub_manual_pair_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = manualHostPort,
                        onValueChange = { manualHostPort = it },
                        label = { Text(stringResource(R.string.link_hub_manual_host_port_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualCode,
                        onValueChange = { manualCode = it.filter { c -> c.isDigit() }.take(6) },
                        label = { Text(stringResource(R.string.link_hub_manual_code_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = manualFp,
                        onValueChange = { manualFp = it.trim() },
                        label = { Text(stringResource(R.string.link_hub_manual_fp_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        enabled = manualHostPort.isNotBlank() && manualCode.length == 6,
                        onClick = {
                            val clean = manualHostPort.trim().removePrefix("wss://").removePrefix("ws://").substringBefore("/")
                            val host = clean.substringBefore(":").trim()
                            val port = clean.substringAfter(":", "").toIntOrNull()?.coerceIn(1024, 65535)
                                ?: LinkProtocol.DEFAULT_PORT
                            DesktopLinkClient.pairAndConnect(
                                ctx = appCtx,
                                host = host,
                                port = port,
                                code = manualCode,
                                expectedFingerprint = manualFp,
                                desktopName = manualDesktopName
                            )
                        }
                    ) {
                        Text(stringResource(R.string.link_hub_pair_connect_btn))
                    }
                }
            }
        }

        // 5. 已配對電腦列表（名稱／最後連線時間／指紋前 8 碼／一鍵連線／移除）
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.link_hub_paired_devices_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    if (pairedDevices.isEmpty()) {
                        Text(
                            text = stringResource(R.string.link_hub_paired_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        pairedDevices.forEachIndexed { idx, dev ->
                            if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(dev.name.ifBlank { dev.host }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                    if (dev.host.isNotBlank()) {
                                        Text(
                                            text = "${dev.host}:${dev.port}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (dev.fingerprint.isNotBlank()) {
                                        Text(
                                            text = stringResource(R.string.link_hub_paired_fp_prefix, dev.fingerprint.take(8)),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = FontFamily.Monospace,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.link_hub_last_seen, timeFmt.format(Date(dev.lastSeenAt))),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (dev.host.isNotBlank()) {
                                        OutlinedButton(onClick = { DesktopLinkClient.connectToPaired(appCtx, dev) }) {
                                            Text(stringResource(R.string.link_hub_connect_this_pc))
                                        }
                                    }
                                    TextButton(
                                        onClick = {
                                            runCatching {
                                                DesktopLinkClient.unpairDesktop(appCtx, dev.instanceId)
                                                pairedDevices = DesktopLinkStore.getPairedDevices(appCtx)
                                            }.onFailure { err ->
                                                localError = err.message ?: err.javaClass.simpleName
                                            }
                                        }
                                    ) {
                                        Text(stringResource(R.string.link_hub_unpair), color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. 連線診斷紀錄（TCP/TLS/HANDSHAKE/PROTOCOL 階段與錯誤碼）
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.link_hub_diag_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (diagnostics.isNotEmpty()) {
                            TextButton(onClick = {
                                runCatching { DesktopLinkStore.clearDiagnostics(appCtx) }
                                diagnostics = emptyList()
                            }) {
                                Text(stringResource(R.string.link_hub_clear))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (diagnostics.isEmpty()) {
                        Text(
                            text = stringResource(R.string.link_hub_diag_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        diagnostics.take(15).forEach { entry ->
                            Text(
                                text = "${timeFmt.format(Date(entry.timestamp))} [${entry.stage}] ${entry.code} ${entry.detail}".trim(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 7. 未知連線嘗試紀錄
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.link_hub_unknown_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (unknownAttempts.isNotEmpty()) {
                            TextButton(onClick = {
                                runCatching { DesktopLinkStore.clearUnknownAttempts(appCtx) }
                                unknownAttempts = emptyList()
                            }) {
                                Text(stringResource(R.string.link_hub_clear))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (unknownAttempts.isEmpty()) {
                        Text(
                            text = stringResource(R.string.link_hub_unknown_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        unknownAttempts.take(10).forEach { att ->
                            Text(
                                text = "${timeFmt.format(Date(att.timestamp))} · ${att.remoteIp} · ${att.reason}",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 8. 指令審計紀錄（上限 200 筆）
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.link_hub_audit_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (auditLogs.isNotEmpty()) {
                            TextButton(onClick = {
                                runCatching { DesktopLinkStore.clearAuditLogs(appCtx) }
                                auditLogs = emptyList()
                            }) {
                                Text(stringResource(R.string.link_hub_clear))
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (auditLogs.isEmpty()) {
                        Text(
                            text = stringResource(R.string.link_hub_audit_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        auditLogs.take(20).forEach { entry ->
                            val mark = if (entry.ok) "✓" else "✕"
                            Text(
                                text = "${timeFmt.format(Date(entry.timestamp))} · $mark ${entry.command} ${entry.detail}".trim(),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = if (entry.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // 9. Doze 與電池最佳化豁免引導
        item {
            val pm = remember { runCatching { ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager }.getOrNull() }
            val isIgnoringBattery = runCatching { pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true }.getOrDefault(false)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.link_hub_doze_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.link_hub_doze_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isIgnoringBattery) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${ctx.packageName}")
                                }
                                runCatching { ctx.startActivity(intent) }.onFailure {
                                    runCatching { ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                                }
                            }
                        ) {
                            Text(stringResource(R.string.link_hub_battery_exempt_btn))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun resolveClassifiedErrorText(code: String?, detail: String?): String? {
    if (code.isNullOrBlank()) return null
    return when (code) {
        LinkErrorCode.TIMEOUT -> stringResource(R.string.link_hub_err_timeout)
        LinkErrorCode.TCP_UNREACHABLE -> stringResource(R.string.link_hub_err_unreachable)
        LinkErrorCode.TLS_ERROR -> stringResource(R.string.link_hub_err_tls)
        LinkErrorCode.FINGERPRINT_MISMATCH -> stringResource(R.string.link_hub_err_fp_mismatch)
        LinkErrorCode.VERSION_MISMATCH -> stringResource(R.string.link_hub_err_version_mismatch)
        LinkErrorCode.PAIR_CODE_EXPIRED -> stringResource(R.string.link_hub_err_code_expired)
        LinkErrorCode.INVALID_PAIR_CODE -> stringResource(R.string.link_hub_err_code_invalid)
        LinkErrorCode.AUTH_FAILED, LinkErrorCode.UNKNOWN_INSTANCE -> stringResource(R.string.link_hub_err_auth_failed)
        LinkErrorCode.TOO_MANY_CLIENTS -> stringResource(R.string.link_hub_err_too_many)
        else -> stringResource(R.string.link_hub_err_generic, code, detail.orEmpty())
    }
}
