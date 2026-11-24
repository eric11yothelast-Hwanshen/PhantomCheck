package com.phantom.checker.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.phantom.checker.shared.LinkProtocol
import com.phantom.checker.shared.QrPairingPayloadV2
import kotlinx.coroutines.delay
import java.awt.image.BufferedImage

/**
 * #477 桌面端「配置新手機」QR 配對對話框（ZXing core 3.5.4）：
 * - 產生 JSON：{"v":2,"name":"<PC>","ip":"<LAN IP>","port":49515,"fp":"<64hex>","code":"<6digit>"}
 * - 多網卡時列出所有 site-local IPv4 供切換
 * - 6 位配對碼 3 分鐘單次效期、即時倒數、過期明確標示、支援手動重新產生
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopQrPairingDialog(
    uiState: DesktopLinkUiState,
    server: DesktopLinkServer,
    accentColor: Color,
    containerColor: Color,
    animationsEnabled: Boolean,
    onDismiss: () -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val gson = remember { Gson() }
    val ips = remember(uiState.localIps) {
        uiState.localIps.ifEmpty { DesktopMdnsBroadcaster.listSiteLocalIpv4Addresses() }
    }
    var selectedIp by remember(ips) { mutableStateOf(ips.firstOrNull().orEmpty()) }
    var pairCodePair by remember { mutableStateOf(server.getOrGeneratePairCode()) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        server.refreshLocalIps()
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
            server.getCurrentPairCode(nowMs)?.let { pairCodePair = it }
        }
    }

    val remSec = ((pairCodePair.second - nowMs) / 1000L).coerceAtLeast(0L)
    val isExpired = remSec <= 0L

    val qrPayloadJson = remember(selectedIp, uiState.port, uiState.certFingerprint, uiState.desktopName, pairCodePair.first, isExpired) {
        if (selectedIp.isBlank() || uiState.certFingerprint.length != 64 || isExpired) ""
        else gson.toJson(
            QrPairingPayloadV2(
                v = LinkProtocol.PROTOCOL_VERSION,
                name = uiState.desktopName.ifBlank { "PhantomCheck-PC" },
                ip = selectedIp,
                port = uiState.port,
                fp = uiState.certFingerprint.lowercase(),
                code = pairCodePair.first
            )
        )
    }

    val qrBitmap: ImageBitmap? = remember(qrPayloadJson) {
        if (qrPayloadJson.isBlank()) null else generateQrImageBitmap(qrPayloadJson, 320)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = containerColor,
        title = {
            Text(DesktopStrings.qrDialogTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = DesktopStrings.qrDialogHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = tokens.textSecondary,
                    modifier = Modifier.fillMaxWidth()
                )

                if (ips.size > 1) {
                    Text(
                        text = DesktopStrings.qrSelectNicLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ips.forEach { ip ->
                            FilterChip(
                                selected = selectedIp == ip,
                                onClick = { selectedIp = ip },
                                label = { Text(ip, fontFamily = FontFamily.Monospace) }
                            )
                        }
                    }
                } else if (selectedIp.isBlank()) {
                    Text(
                        text = DesktopStrings.qrNoLanIpError,
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopPalette.GlassError,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Box(
                    modifier = Modifier
                        .size(224.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (qrBitmap != null) Color.White else tokens.surfaceElevated)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap,
                            contentDescription = DesktopStrings.qrDialogTitle,
                            modifier = Modifier.size(204.dp)
                        )
                    } else {
                        Text(
                            text = if (isExpired) DesktopStrings.pairCodeExpired else DesktopStrings.qrUnavailableHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = DesktopPalette.GlassError
                        )
                    }
                }

                Text(
                    text = if (isExpired) "------" else pairCodePair.first,
                    style = MaterialTheme.typography.headlineSmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isExpired) DesktopPalette.GlassError else accentColor
                )

                Text(
                    text = if (!isExpired) {
                        DesktopStrings.pairCodeRemaining("%d:%02d".format(remSec / 60L, remSec % 60L))
                    } else {
                        DesktopStrings.pairCodeExpired
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isExpired) DesktopPalette.GlassError else tokens.textSecondary
                )

                if (selectedIp.isNotBlank()) {
                    Text(
                        text = "$selectedIp:${uiState.port} · ${DesktopStrings.fingerprintPrefix}${uiState.certFingerprint.take(8)}…",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = tokens.textMuted
                    )
                }

                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpringButton(
                        onClick = {
                            server.refreshLocalIps()
                            pairCodePair = server.generatePairCode()
                            nowMs = System.currentTimeMillis()
                        },
                        animationsEnabled = animationsEnabled,
                        containerColor = accentColor
                    ) {
                        Text(DesktopStrings.pairCodeRegenerate)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(DesktopStrings.dismiss)
            }
        }
    )
}

internal fun generateQrImageBitmap(content: String, sizePx: Int = 320): ImageBitmap? {
    return runCatching {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val image = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until h) {
            for (x in 0 until w) {
                image.setRGB(x, y, if (matrix.get(x, y)) 0xFF0A0E17.toInt() else 0xFFFFFFFF.toInt())
            }
        }
        image.toComposeImageBitmap()
    }.onFailure { err ->
        DesktopDiagnostics.warn("QR", "QR generation failed: ${err.javaClass.simpleName}")
    }.getOrNull()
}
