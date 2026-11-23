package com.phantom.checker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.phantom.checker.R
import com.phantom.checker.service.DesktopLinkServer
import com.phantom.checker.shared.LinkProtocol
import com.phantom.checker.shared.QrPairingPayload
import com.phantom.checker.ui.theme.PhantomCheckTheme
import com.phantom.checker.util.AppLocaleHelper
import com.phantom.checker.util.DesktopLinkStore
import kotlinx.coroutines.delay
import qrcode.QRCode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LinkHubActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocaleHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (DesktopLinkStore.isLinkEnabled(applicationContext)) {
            DesktopLinkServer.start(applicationContext)
        }
        setContent {
            PhantomCheckTheme {
                LinkHubScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LinkHubScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val serverStatus by DesktopLinkServer.status.collectAsState()

    var linkEnabled by remember { mutableStateOf(DesktopLinkStore.isLinkEnabled(appCtx)) }
    var pairCodePair by remember { mutableStateOf(DesktopLinkStore.getOrGeneratePairCode()) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var pairedDevices by remember { mutableStateOf(DesktopLinkStore.getPairedDevices(appCtx)) }
    var unknownAttempts by remember { mutableStateOf(DesktopLinkStore.getUnknownAttempts(appCtx)) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
            pairedDevices = DesktopLinkStore.getPairedDevices(appCtx)
            unknownAttempts = DesktopLinkStore.getUnknownAttempts(appCtx)
        }
    }

    val remainingSec = ((pairCodePair.second - nowMs) / 1000L).coerceAtLeast(0L)
    val hostIp = serverStatus.localIp.ifBlank { DesktopLinkServer.getLocalWifiIpAddress().orEmpty() }
    val hostPortText = if (hostIp.isNotBlank()) "$hostIp:${serverStatus.port}" else ":${serverStatus.port}"
    val wssUrl = if (hostIp.isNotBlank()) "wss://$hostIp:${serverStatus.port}${LinkProtocol.WS_PATH}" else ""

    val qrBitmap: ImageBitmap? = remember(wssUrl, serverStatus.fingerprint, pairCodePair.first) {
        if (wssUrl.isBlank() || serverStatus.fingerprint.isBlank()) null
        else runCatching {
            val payload = QrPairingPayload(
                wssUrl = wssUrl,
                fingerprint = serverStatus.fingerprint,
                code = pairCodePair.first,
                deviceName = Build.MODEL.ifBlank { "Android" },
                protocolVersion = LinkProtocol.PROTOCOL_VERSION
            )
            val pngBytes = QRCode.ofSquares()
                .build(Gson().toJson(payload))
                .renderToBytes()
            BitmapFactory.decodeByteArray(pngBytes, 0, pngBytes.size)?.asImageBitmap()
        }.getOrNull()
    }

    val timeFmt = remember { SimpleDateFormat("MM/dd HH:mm:ss", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.link_hub_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.link_hub_back)
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 1. 總開關
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.link_hub_master_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.link_hub_master_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = linkEnabled,
                            onCheckedChange = { checked ->
                                linkEnabled = checked
                                DesktopLinkStore.setLinkEnabled(appCtx, checked)
                                if (checked) {
                                    DesktopLinkServer.start(appCtx)
                                } else {
                                    DesktopLinkServer.stop(appCtx)
                                }
                            }
                        )
                    }
                }
            }

            // 2. 狀態列＋手動備援位址
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
                        Text(
                            text = if (serverStatus.isRunning) {
                                stringResource(R.string.link_hub_status_running, serverStatus.port, serverStatus.connectedClients.size)
                            } else {
                                stringResource(R.string.link_hub_status_stopped)
                            },
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (!serverStatus.lastError.isNullOrBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = serverStatus.lastError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.link_hub_manual_address_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = hostPortText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            OutlinedButton(
                                onClick = {
                                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    cm?.setPrimaryClip(ClipData.newPlainText("PhantomCheck Host", hostPortText))
                                    Toast.makeText(ctx, ctx.getString(R.string.link_hub_copied_toast), Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text(stringResource(R.string.link_hub_copy_address))
                            }
                        }
                        if (serverStatus.fingerprint.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = stringResource(R.string.link_hub_fingerprint_label, serverStatus.fingerprint.take(16)),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 3. QR 配對與 6 位數配對碼
            if (linkEnabled) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.link_hub_pair_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            if (qrBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .size(180.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = qrBitmap,
                                        contentDescription = stringResource(R.string.link_hub_qr_desc),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            Text(
                                text = pairCodePair.first,
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            val mins = remainingSec / 60L
                            val secs = remainingSec % 60L
                            Text(
                                text = if (remainingSec > 0) {
                                    stringResource(R.string.link_hub_code_ttl, "%d:%02d".format(mins, secs))
                                } else {
                                    stringResource(R.string.link_hub_code_expired)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    pairCodePair = DesktopLinkStore.generatePairCode()
                                }
                            ) {
                                Text(stringResource(R.string.link_hub_regenerate_code))
                            }
                        }
                    }
                }
            }

            // 4. 已配對裝置
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
                                        Text(dev.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        Text(
                                            text = stringResource(R.string.link_hub_last_seen, timeFmt.format(Date(dev.lastSeenAt))),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    TextButton(
                                        onClick = {
                                            DesktopLinkServer.revokeDevice(appCtx, dev.instanceId)
                                            pairedDevices = DesktopLinkStore.getPairedDevices(appCtx)
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

            // 5. 未知連線嘗試紀錄
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
                                    DesktopLinkStore.clearUnknownAttempts(appCtx)
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

            // 6. Doze 與電池最佳化豁免引導
            item {
                val pm = remember { ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager }
                val isIgnoringBattery = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
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
}
