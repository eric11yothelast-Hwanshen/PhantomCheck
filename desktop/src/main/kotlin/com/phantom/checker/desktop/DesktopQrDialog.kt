package com.phantom.checker.desktop

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * #477 / #520 桌面端「配置新手機」QR 配對對話框（PhantomDialog 外殼＋ZXing core 3.5.4）：
 * - 產生 JSON：{"v":2,"name":"<PC>","ip":"<LAN IP>","port":49515,"fp":"<64hex>","code":"<6digit>"}
 * - 多網卡時列出所有 site-local IPv4 供切換
 * - 6 位配對碼 3 分鐘單次效期、即時倒數、過期明確標示、支援手動重新產生與一鍵複製手動配對資訊
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
    var copiedNotice by remember { mutableStateOf(false) }
    // #589 A4 step wizard: 1 prepare → 2 scan → 3 verify/success
    var wizardStep by remember { mutableIntStateOf(1) }
    var successPulse by remember { mutableStateOf(false) }
    val successScale by animateFloatAsState(
        targetValue = if (successPulse) 1f else 0.86f,
        animationSpec = tween(320),
        label = "qr_success_scale"
    )
    val isConnected = uiState.connState == LinkConnState.CONNECTED && uiState.connectedPhones.isNotEmpty()

    LaunchedEffect(isConnected) {
        if (isConnected) {
            successPulse = true
            wizardStep = 3
        }
    }

    LaunchedEffect(Unit) {
        server.refreshLocalIps()
        while (true) {
            delay(1000L)
            nowMs = System.currentTimeMillis()
            server.getCurrentPairCode(nowMs)?.let { pairCodePair = it }
        }
    }

    LaunchedEffect(copiedNotice) {
        if (copiedNotice) {
            delay(3_000L)
            copiedNotice = false
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

    PhantomDialog(
        onDismissRequest = onDismiss,
        title = DesktopStrings.qrDialogTitle,
        maxWidth = 440.dp,
        containerColor = if (tokens.isLight || tokens.isHighContrast) tokens.dialogContainer else containerColor,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(DesktopStrings.dismiss)
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(tokens.spacingMd)
        ) {
            // Step indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Top
            ) {
                listOf(
                    1 to DesktopStrings.qrStepReadyTitle,
                    2 to DesktopStrings.qrStepScanTitle,
                    3 to DesktopStrings.qrStepVerifyTitle
                ).forEach { (num, label) ->
                    val active = wizardStep == num
                    val done = wizardStep > num || (num == 3 && isConnected)
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        done -> tokens.success
                                        active -> accentColor
                                        else -> tokens.surfaceElevated
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (done) "✓" else num.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (done || active) tokens.onAccent else tokens.textMuted
                            )
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) tokens.accentText else tokens.textMuted,
                            maxLines = 1
                        )
                    }
                }
            }

            Text(
                text = DesktopStrings.qrWizardTitle,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            // Fixed navigation row stays visible even when QR content scrolls.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (wizardStep > 1) {
                    SpringOutlinedButton(
                        onClick = { wizardStep -= 1 },
                        animationsEnabled = animationsEnabled
                    ) { Text(DesktopStrings.qrBack) }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                SpringButton(
                    onClick = {
                        if (wizardStep < 3) wizardStep += 1 else onDismiss()
                    },
                    animationsEnabled = animationsEnabled,
                    containerColor = accentColor
                ) { Text(if (wizardStep < 3) DesktopStrings.qrNext else DesktopStrings.dismiss) }
            }

            when (wizardStep) {
                1 -> {
                    Text(
                        text = DesktopStrings.qrStepReadyBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = DesktopStrings.qrDialogHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = tokens.textMuted,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                2 -> {
                    Text(
                        text = DesktopStrings.qrStepScanBody,
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
                            color = tokens.error,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(tokens.radiusLg))
                            .background(if (qrBitmap != null) Color.White else tokens.surfaceElevated)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (qrBitmap != null) {
                            Image(
                                bitmap = qrBitmap,
                                contentDescription = DesktopStrings.qrDialogTitle,
                                modifier = Modifier.size(180.dp)
                            )
                        } else {
                            Text(
                                text = if (isExpired) DesktopStrings.pairCodeExpired else DesktopStrings.qrUnavailableHint,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.error
                            )
                        }
                    }

                    Text(
                        text = if (isExpired) "------" else pairCodePair.first,
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpired) tokens.error else tokens.accentText
                    )
                    Text(
                        text = if (!isExpired) {
                            DesktopStrings.pairCodeRemaining("%d:%02d".format(remSec / 60L, remSec % 60L))
                        } else {
                            DesktopStrings.pairCodeExpired
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isExpired) tokens.error else tokens.textSecondary
                    )
                    if (selectedIp.isNotBlank()) {
                        Text(
                            text = "$selectedIp:${uiState.port} · ${DesktopStrings.fingerprintPrefix}${uiState.certFingerprint.take(8)}…",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = tokens.textMuted
                        )
                    }
                    if (copiedNotice) {
                        Text(
                            text = DesktopStrings.manualPairInfoCopied,
                            style = MaterialTheme.typography.labelSmall,
                            color = tokens.success
                        )
                    }

                    // Classified failure guidance (#589 A4)
                    uiState.lastError?.takeIf { it.isNotBlank() && !isConnected }?.let { err ->
                        val code = when {
                            err.contains("port", true) || err.contains("bind", true) -> com.phantom.checker.shared.LinkErrorCode.PORT_IN_USE
                            err.contains("version", true) || err.contains("protocol", true) -> com.phantom.checker.shared.LinkErrorCode.VERSION_MISMATCH
                            err.contains("fingerprint", true) || err.contains("fp", true) -> com.phantom.checker.shared.LinkErrorCode.FINGERPRINT_MISMATCH
                            err.contains("expired", true) -> com.phantom.checker.shared.LinkErrorCode.PAIR_CODE_EXPIRED
                            err.contains("code", true) -> com.phantom.checker.shared.LinkErrorCode.INVALID_PAIR_CODE
                            err.contains("auth", true) || err.contains("hmac", true) -> com.phantom.checker.shared.LinkErrorCode.AUTH_FAILED
                            err.contains("timeout", true) -> com.phantom.checker.shared.LinkErrorCode.TIMEOUT
                            err.contains("tls", true) -> com.phantom.checker.shared.LinkErrorCode.TLS_ERROR
                            else -> ""
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(tokens.radiusMd))
                                .background(tokens.error.copy(alpha = 0.10f))
                                .padding(tokens.spacingMd),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(DesktopStrings.qrFailureTitle, style = MaterialTheme.typography.labelMedium, color = tokens.error, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = if (code.isNotBlank()) DesktopStrings.classifiedError(code) else err,
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textSecondary
                            )
                            Text(DesktopStrings.qrFailureHint, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SpringButton(
                            onClick = {
                                server.refreshLocalIps()
                                pairCodePair = server.generatePairCode()
                                nowMs = System.currentTimeMillis()
                                copiedNotice = false
                            },
                            animationsEnabled = animationsEnabled,
                            containerColor = accentColor
                        ) { Text(DesktopStrings.qrRegenerateCode) }
                        if (selectedIp.isNotBlank() && !isExpired) {
                            SpringOutlinedButton(
                                onClick = {
                                    val manualInfo = "$selectedIp:${uiState.port}\nCode: ${pairCodePair.first}\nFP: ${uiState.certFingerprint.lowercase()}"
                                    runCatching {
                                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(manualInfo), null)
                                        copiedNotice = true
                                    }
                                },
                                animationsEnabled = animationsEnabled
                            ) { Text(DesktopStrings.copyManualPairInfo) }
                        }
                    }
                }
                else -> {
                    if (isConnected) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { scaleX = successScale; scaleY = successScale }
                                .clip(RoundedCornerShape(tokens.radiusLg))
                                .background(tokens.success.copy(alpha = 0.14f))
                                .padding(tokens.spacingXl),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(tokens.spacingSm)
                        ) {
                            Text("🎉", style = MaterialTheme.typography.headlineMedium)
                            Text(DesktopStrings.qrPairSuccess, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tokens.success)
                            Text(DesktopStrings.qrPairSuccessHint, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        }
                    }
                    Text(
                        text = DesktopStrings.qrStepVerifyBody,
                        style = MaterialTheme.typography.bodySmall,
                        color = tokens.textSecondary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(tokens.radiusMd))
                            .background(tokens.surfaceElevated)
                            .padding(tokens.spacingMd),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(DesktopStrings.qrSecurityTitle, style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary, fontWeight = FontWeight.SemiBold)
                        Text(DesktopStrings.qrSecurityFingerprintHint, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        if (uiState.certFingerprint.isNotBlank()) {
                            Text(
                                text = "${DesktopStrings.fingerprintPrefix}${uiState.certFingerprint.take(8)}…",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = tokens.accentText
                            )
                        }
                        Text(DesktopStrings.qrSecurityLanHint, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                    }
                }
            }
        }
    }
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
