package com.phantom.checker.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.ClipboardSyncPayload
import com.phantom.checker.shared.GroupReportSummaryDto
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.NoteAddReq
import com.phantom.checker.shared.OpenUrlReq
import com.phantom.checker.shared.SendPhoneNotifReq
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * #518 剪貼簿敏感格式（OTP／驗證碼）偵測工具（對齊手機端 OtpDetector 慣例，常數化 Regex 避免重複編譯）。
 */
internal object DesktopClipboardGuard {
    private val PURE_OTP_REGEX = Regex("^\\d{4,8}$")
    private val KEYWORD_OTP_REGEX = Regex("(驗證碼|認證碼|動態碼|一次性密碼|安全碼|otp|verification\\s*code|passcode|security\\s*code)[^\\d]{0,12}(\\d{4,8})", RegexOption.IGNORE_CASE)
    private val REPORT_TIME_FMT = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    fun looksLikeOtp(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return false
        return PURE_OTP_REGEX.matches(t) || KEYWORD_OTP_REGEX.containsMatchIn(t)
    }

    fun formatReportTime(ms: Long): String =
        if (ms <= 0L) "" else REPORT_TIME_FMT.get().format(Date(ms))
}

/**
 * #518 跨端實用功能包（剪貼簿雙向同步、快速筆記到手機、發送手機通知、尋找手機響鈴、網址互開）。
 */
@Composable
fun DesktopToolsPage(
    settings: DesktopSettings,
    tokens: DesktopThemeTokens,
    accentColor: Color,
    isConnected: Boolean,
    latestReceivedClipboard: String,
    server: DesktopLinkServer,
    onFeedback: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var noteTitle by remember { mutableStateOf("") }
    var noteBody by remember { mutableStateOf("") }
    var notifTitle by remember { mutableStateOf("") }
    var notifBody by remember { mutableStateOf("") }
    var openUrlInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = DesktopStrings.toolsTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = tokens.textPrimary
        )

        // 1. Bidirectional Clipboard Sync (#518 flagship)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(DesktopStrings.clipSyncTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                    Switch(
                        checked = settings.clipboardSyncEnabled,
                        onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(clipboardSyncEnabled = v) } }
                    )
                }
                Text(DesktopStrings.clipSyncDesc, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)

                if (settings.clipboardSyncEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(DesktopStrings.clipOtpGuardLabel, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        Switch(
                            checked = settings.clipboardOtpGuard,
                            onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(clipboardOtpGuard = v) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(DesktopStrings.clipFocusOnlyLabel, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                        Switch(
                            checked = settings.clipboardOnlyWhenFocused,
                            onCheckedChange = { v -> DesktopSettingsStore.update { it.copy(clipboardOnlyWhenFocused = v) } }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        SpringButton(
                            enabled = isConnected,
                            animationsEnabled = settings.animationsEnabled,
                            containerColor = accentColor,
                            onClick = {
                                val text = runCatching {
                                    val cb = Toolkit.getDefaultToolkit().systemClipboard
                                    if (cb.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                                        cb.getData(DataFlavor.stringFlavor) as? String
                                    } else null
                                }.getOrNull()?.trim().orEmpty()
                                when {
                                    text.isEmpty() -> onFeedback(DesktopStrings.clipEmpty)
                                    text.length > settings.clipboardMaxChars -> onFeedback(DesktopStrings.clipTooLong)
                                    settings.clipboardOtpGuard && DesktopClipboardGuard.looksLikeOtp(text) -> onFeedback(DesktopStrings.clipOtpBlocked)
                                    else -> {
                                        scope.launch {
                                            val ok = server.sendCommand(
                                                LinkMsgType.CLIPBOARD_SYNC,
                                                UUID.randomUUID().toString(),
                                                ClipboardSyncPayload(text = text, source = "desktop", timestampMs = System.currentTimeMillis())
                                            )
                                            onFeedback(if (ok) DesktopStrings.clipSent else DesktopStrings.commandNotSentDisconnected)
                                        }
                                    }
                                }
                            }
                        ) { Text(DesktopStrings.clipPushBtn) }
                    }
                    if (latestReceivedClipboard.isNotBlank()) {
                        HorizontalDivider(color = tokens.border)
                        Text(DesktopStrings.clipLatestLabel, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(tokens.surfaceElevated)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = latestReceivedClipboard.take(240),
                                style = MaterialTheme.typography.bodySmall,
                                color = tokens.textPrimary,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = {
                                runCatching {
                                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(latestReceivedClipboard), null)
                                    onFeedback(DesktopStrings.copiedFeedback)
                                }
                            }) { Text(DesktopStrings.copyBtn) }
                        }
                    }
                }
            }
        }

        // 2. Quick Note to Phone (reuses NOTE_ADD)
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(DesktopStrings.quickNoteTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                OutlinedTextField(
                    value = noteTitle,
                    onValueChange = { noteTitle = it.take(80) },
                    label = { Text(DesktopStrings.quickNoteTitleHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteBody,
                    onValueChange = { noteBody = it.take(2000) },
                    placeholder = { Text(DesktopStrings.quickNoteBodyHint) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                SpringButton(
                    enabled = isConnected && noteBody.isNotBlank(),
                    animationsEnabled = settings.animationsEnabled,
                    containerColor = accentColor,
                    onClick = {
                        val t = noteTitle.trim()
                        val b = noteBody.trim()
                        val fullContent = if (t.isNotBlank()) "[$t]\n$b" else b
                        scope.launch {
                            val ok = server.sendCommand(LinkMsgType.NOTE_ADD, UUID.randomUUID().toString(), NoteAddReq(content = fullContent))
                            if (ok) {
                                noteTitle = ""
                                noteBody = ""
                                onFeedback(DesktopStrings.quickNoteSendBtn)
                            } else onFeedback(DesktopStrings.commandNotSentDisconnected)
                        }
                    }
                ) { Text(DesktopStrings.quickNoteSendBtn) }
            }
        }

        // 3. Send Notification to Phone
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(DesktopStrings.sendNotifTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                OutlinedTextField(
                    value = notifTitle,
                    onValueChange = { notifTitle = it.take(80) },
                    label = { Text(DesktopStrings.sendNotifTitleHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = notifBody,
                    onValueChange = { notifBody = it.take(400) },
                    placeholder = { Text(DesktopStrings.sendNotifBodyHint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SpringButton(
                    enabled = isConnected && notifBody.isNotBlank(),
                    animationsEnabled = settings.animationsEnabled,
                    containerColor = accentColor,
                    onClick = {
                        val t = notifTitle.trim().ifBlank { "PhantomCheck Desktop" }
                        val b = notifBody.trim()
                        scope.launch {
                            val ok = server.sendCommand(LinkMsgType.SEND_PHONE_NOTIF, UUID.randomUUID().toString(), SendPhoneNotifReq(title = t, body = b))
                            if (ok) {
                                notifTitle = ""
                                notifBody = ""
                                onFeedback(DesktopStrings.sendNotifBtn)
                            } else onFeedback(DesktopStrings.commandNotSentDisconnected)
                        }
                    }
                ) { Text(DesktopStrings.sendNotifBtn) }
            }
        }

        // 4. Find My Phone (Ring Alert) + 5. Open URL on Phone
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(DesktopStrings.findPhoneTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                Text(DesktopStrings.findPhoneDesc, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                SpringButton(
                    enabled = isConnected,
                    animationsEnabled = settings.animationsEnabled,
                    containerColor = tokens.warning,
                    onClick = {
                        scope.launch {
                            val ok = server.sendCommand(LinkMsgType.FIND_PHONE_RING, UUID.randomUUID().toString(), emptyMap<String, String>())
                            onFeedback(if (ok) DesktopStrings.findPhoneBtn else DesktopStrings.commandNotSentDisconnected)
                        }
                    }
                ) { Text(DesktopStrings.findPhoneBtn) }

                HorizontalDivider(color = tokens.border)
                Text(DesktopStrings.openUrlTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = openUrlInput,
                        onValueChange = { openUrlInput = it.take(2048) },
                        placeholder = { Text(DesktopStrings.openUrlHint) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    SpringButton(
                        enabled = isConnected && openUrlInput.isNotBlank(),
                        animationsEnabled = settings.animationsEnabled,
                        containerColor = accentColor,
                        onClick = {
                            val url = openUrlInput.trim()
                            val validHttpUrl = runCatching {
                                val uri = java.net.URI(url)
                                val scheme = uri.scheme?.lowercase()
                                (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
                            }.getOrDefault(false)
                            if (!validHttpUrl) {
                                onFeedback(DesktopStrings.openUrlInvalid)
                            } else {
                                scope.launch {
                                    val ok = server.sendCommand(LinkMsgType.OPEN_URL_ON_PEER, UUID.randomUUID().toString(), OpenUrlReq(url = url))
                                    if (ok) {
                                        openUrlInput = ""
                                        onFeedback(DesktopStrings.openUrlBtn)
                                    } else onFeedback(DesktopStrings.commandNotSentDisconnected)
                                }
                            }
                        }
                    ) { Text(DesktopStrings.openUrlBtn) }
                }
            }
        }
    }
}

/**
 * #509 / #518 桌面側欄群組報告檢視頁（由手機端 GROUP_REPORTS_LIST 即時同步）。
 */
@Composable
fun DesktopGroupReportsPage(
    settings: DesktopSettings,
    tokens: DesktopThemeTokens,
    accentColor: Color,
    isConnected: Boolean,
    reports: List<GroupReportSummaryDto>,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = DesktopStrings.reportsTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = tokens.textPrimary
            )
            SpringOutlinedButton(
                enabled = isConnected,
                animationsEnabled = settings.animationsEnabled,
                onClick = onRefresh
            ) { Text(DesktopStrings.reportsRefreshBtn) }
        }

        if (reports.isEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = DesktopStrings.reportsEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.textSecondary,
                    modifier = Modifier.padding(20.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(reports, key = { it.id }) { report ->
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${report.appLabel} · ${report.conversationTitle}",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = tokens.textPrimary
                                )
                                Text(
                                    text = "${DesktopClipboardGuard.formatReportTime(report.createdAt)} · ${report.messageCount}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textMuted
                                )
                            }
                            HorizontalDivider(color = tokens.border)
                            DesktopMarkdownText(
                                markdown = report.summaryMarkdown,
                                textColor = tokens.textPrimary,
                                accentColor = accentColor
                            )
                        }
                    }
                }
            }
        }
    }
}
