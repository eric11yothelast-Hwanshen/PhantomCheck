package com.phantom.checker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.AppGroupRow
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.ui.components.GlassCard
import com.phantom.checker.ui.components.glassSource
import com.phantom.checker.ui.components.glassSurface
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Modifier.phantomGlassSource(state: HazeState): Modifier {
    val ctx = LocalContext.current
    val isPowerSave = remember(ctx) {
        try {
            (ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }
    return if (ThemePreferences.isGlassEffectEnabled(ctx) && !isPowerSave) {
        this.glassSource(state)
    } else {
        this
    }
}

@Composable
fun Modifier.phantomGlassSurface(
    state: HazeState,
    fallbackColor: Color,
    outlineColor: Color? = null,
    cornerRadius: Dp = 24.dp
): Modifier {
    val ctx = LocalContext.current
    val isPowerSave = remember(ctx) {
        try {
            (ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }
    return if (ThemePreferences.isGlassEffectEnabled(ctx) && !isPowerSave) {
        this.glassSurface(
            state = state,
            fallbackColor = fallbackColor,
            outlineColor = outlineColor,
            cornerRadius = cornerRadius
        )
    } else {
        val shape = RoundedCornerShape(cornerRadius)
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val resolvedOutline = outlineColor ?: if (isDark) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
        this
            .clip(shape)
            .background(fallbackColor)
            .border(width = 1.dp, color = resolvedOutline, shape = shape)
    }
}

@Composable
fun PhantomGlassCard(
    glassState: HazeState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val ctx = LocalContext.current
    val isPowerSave = remember(ctx) {
        try {
            (ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }
    if (ThemePreferences.isGlassEffectEnabled(ctx) && !isPowerSave) {
        GlassCard(
            glassState = glassState,
            modifier = modifier,
            cornerRadius = cornerRadius,
            contentPadding = contentPadding,
            content = content
        )
    } else {
        val shape = RoundedCornerShape(cornerRadius)
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val outline = if (isDark) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
        Box(
            modifier = modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .border(width = 1.dp, color = outline, shape = shape)
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}

@Composable
fun rememberPhantomHaptic(): (HapticFeedbackType) -> Unit {
    val ctx = LocalContext.current
    return remember(ctx) {
        { type ->
            if (PhantomPrefs.isHapticFeedbackEnabled(ctx)) {
                try {
                    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    }
                    if (vibrator?.hasVibrator() == true) {
                        val isLong = type == HapticFeedbackType.LongPress
                        val effect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                            vibrator.areAllEffectsSupported(
                                android.os.VibrationEffect.EFFECT_CLICK,
                                android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                            ) == android.os.Vibrator.VIBRATION_EFFECT_SUPPORT_YES
                        ) {
                            android.os.VibrationEffect.createPredefined(
                                if (isLong) android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                                else android.os.VibrationEffect.EFFECT_CLICK
                            )
                        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            android.os.VibrationEffect.createWaveform(
                                if (isLong) longArrayOf(0L, 40L) else longArrayOf(0L, 25L),
                                if (isLong) intArrayOf(0, 255) else intArrayOf(0, 220),
                                -1
                            )
                        } else {
                            android.os.VibrationEffect.createOneShot(
                                if (isLong) 35L else 18L,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        }
                        vibrator.vibrate(effect)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}

fun parseTagColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF7C3AED)
    }
}

fun hasSuspiciousUrlHint(riskReason: String): Boolean {
    if (riskReason.isBlank()) return false
    val lower = riskReason.lowercase(Locale.US)
    return listOf("連結", "網址", "url", "http", "短鏈", "短網", "ip", "表單", "釣魚網").any {
        lower.contains(it)
    }
}

private fun highlightMatches(text: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var startIndex = 0
        while (startIndex < text.length) {
            val found = text.indexOf(q, startIndex = startIndex, ignoreCase = true)
            if (found < 0) break
            addStyle(
                style = SpanStyle(
                    background = Color(0xFF7C3AED),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                start = found,
                end = found + q.length
            )
            startIndex = found + q.length
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.size(44.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.pressableScale()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportCsvDialog(
    appGroups: List<AppGroupRow>,
    onDismiss: () -> Unit,
    onConfirm: (packageName: String?, daysBack: Int?) -> Unit
) {
    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var selectedDays by remember { mutableStateOf<Int?>(null) }
    var showAppMenu by remember { mutableStateOf(false) }

    val selectedAppLabel = if (selectedPkg == null) {
        "全部 App"
    } else {
        appGroups.firstOrNull { it.packageName == selectedPkg }?.appLabel ?: selectedPkg.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("匯出訊息 CSV") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("來源 App 範圍", style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { showAppMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedAppLabel)
                    }
                    DropdownMenu(
                        expanded = showAppMenu,
                        onDismissRequest = { showAppMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("全部 App") },
                            onClick = {
                                selectedPkg = null
                                showAppMenu = false
                            }
                        )
                        appGroups.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.appLabel.ifBlank { app.packageName }) },
                                onClick = {
                                    selectedPkg = app.packageName
                                    showAppMenu = false
                                }
                            )
                        }
                    }
                }

                Text("時間範圍", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedDays == 7,
                        onClick = { selectedDays = 7 },
                        label = { Text("近 7 天") }
                    )
                    FilterChip(
                        selected = selectedDays == 30,
                        onClick = { selectedDays = 30 },
                        label = { Text("近 30 天") }
                    )
                    FilterChip(
                        selected = selectedDays == null,
                        onClick = { selectedDays = null },
                        label = { Text("全部") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedPkg, selectedDays) }) {
                Text("匯出")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
fun NotificationListenerGuideCard(
    onOpenSettings: () -> Unit,
    glassState: HazeState,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(spring())
            .phantomGlassSurface(
                state = glassState,
                fallbackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                outlineColor = MaterialTheme.colorScheme.error,
                cornerRadius = 20.dp
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "尚未開啟通知存取權限",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "幻訊需要讀取系統通知，才能在不觸發原 App 已讀的情況下備份訊息並進行詐騙風險分析。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onOpenSettings,
                modifier = Modifier.pressableScale(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("前往開啟通知存取權限")
            }
        }
    }
}

@Composable
fun PostNotificationGuideCard(
    onRequestPermission: () -> Unit,
    glassState: HazeState,
    modifier: Modifier = Modifier
) {
    PhantomGlassCard(
        glassState = glassState,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "需要通知顯示權限",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "允許顯示通知後，常駐統計與高風險詐騙警示才能正常顯示。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRequestPermission,
            modifier = Modifier.pressableScale()
        ) {
            Text("允許通知警示")
        }
    }
}

@Composable
fun MessageCard(
    message: MessageEntity,
    glassState: HazeState,
    highlightAsScam: Boolean = false,
    matchedKeywords: List<String> = emptyList(),
    highlightQuery: String = "",
    modifier: Modifier = Modifier
) {
    val timeText = remember(message.timestamp) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    val cardInner: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "[${message.appLabel}] ",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = highlightMatches(message.sender, highlightQuery),
                    style = MaterialTheme.typography.titleSmall
                )
                if (message.isStarred) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "星標",
                        tint = Color(0xFFE6A100),
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (message.riskLabel == "scam") {
                    Text(
                        text = "  ⚠️ 詐騙 ${message.riskScore}",
                        color = if (highlightAsScam) MaterialTheme.colorScheme.error else Color.Red,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else if (message.riskLabel == "suspicious") {
                    Text(
                        text = "  ❓ 疑似 ${message.riskScore}",
                        color = Color(0xFFE6A100),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (message.isFiltered) {
                    Text(
                        text = "  [已過濾]",
                        color = Color.Gray,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlightAsScam) {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                } else {
                    Color.Gray
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = highlightMatches(message.content, highlightQuery),
            style = MaterialTheme.typography.bodySmall
        )
        if (matchedKeywords.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "命中關鍵字：${matchedKeywords.joinToString("、")}",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (message.riskReason.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stripMarkdown(message.riskReason),
                color = if (highlightAsScam) {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                } else {
                    Color.Gray
                },
                style = MaterialTheme.typography.labelSmall
            )
        }
    }

    if (highlightAsScam) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .animateContentSize(spring())
                .phantomGlassSurface(
                    state = glassState,
                    fallbackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f),
                    outlineColor = MaterialTheme.colorScheme.error,
                    cornerRadius = 20.dp
                )
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onErrorContainer
            ) {
                Column(modifier = Modifier.padding(12.dp), content = cardInner)
            }
        }
    } else {
        PhantomGlassCard(
            glassState = glassState,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            cornerRadius = 20.dp,
            contentPadding = 12.dp,
            content = cardInner
        )
    }
}
