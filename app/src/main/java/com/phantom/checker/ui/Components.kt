package com.phantom.checker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.phantom.checker.util.BackgroundImageHelper
import com.phantom.checker.util.BackgroundLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.AppGroupRow
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.MessageTagRow
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
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassStrength = ThemePreferences.getGlassStrengthPercent(ctx)
    val cardBgMode = ThemePreferences.getCardBgMode(ctx)
    val cardBgAlpha = (ThemePreferences.getCardBgAlphaPercent(ctx) / 100f).coerceAtLeast(0.15f)
    val darkCardColor = if (isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    } else {
        Color(0xFF16181F).copy(alpha = 0.94f)
    }
    val resolvedFallback = when (cardBgMode) {
        PhantomPrefs.CardBgMode.FOLLOW_GLASS -> fallbackColor
        PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(alpha = cardBgAlpha.coerceAtLeast(0.35f))
        PhantomPrefs.CardBgMode.DARK -> darkCardColor
    }

    return if (ThemePreferences.isGlassEffectEnabled(ctx) && !isPowerSave) {
        val clippedBase = this.glassSurface(
            state = state,
            fallbackColor = resolvedFallback,
            outlineColor = outlineColor,
            cornerRadius = cornerRadius
        ).clip(shape)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val overlayColor: Color? = when (cardBgMode) {
                PhantomPrefs.CardBgMode.FOLLOW_GLASS -> if (glassStrength > 0) {
                    MaterialTheme.colorScheme.surface.copy(alpha = (glassStrength / 100f) * 0.45f)
                } else null
                PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(alpha = cardBgAlpha)
                PhantomPrefs.CardBgMode.DARK -> darkCardColor
            }
            if (overlayColor != null) {
                clippedBase.background(overlayColor, shape)
            } else {
                clippedBase
            }
        } else {
            clippedBase
        }
    } else {
        val resolvedOutline = outlineColor ?: if (isDark) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
        this
            .clip(shape)
            .background(resolvedFallback, shape)
            .border(width = 1.dp, color = resolvedOutline, shape = shape)
    }
}

/**
 * #222 浮動層專用不透明容器（歷史對話抽屜、記事規則面板等）。
 * 不使用 glassSurface/blur，避免圓角外溢出與底層文字透出；.clip(shape) 必在 .background() 之前。
 */
@Composable
fun PhantomFloatingSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = if (isDark) {
        Color(0xF012151E)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor, shape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun PhantomGlassCard(
    glassState: HazeState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 16.dp,
    tintColor: Color? = null,
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
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassStrength = ThemePreferences.getGlassStrengthPercent(ctx)
    val cardBgMode = ThemePreferences.getCardBgMode(ctx)
    val cardBgAlpha = (ThemePreferences.getCardBgAlphaPercent(ctx) / 100f).coerceAtLeast(0.15f)
    val darkCardColor = if (isDark) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    } else {
        Color(0xFF16181F).copy(alpha = 0.94f)
    }
    val outline = if (isDark) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    if (ThemePreferences.isGlassEffectEnabled(ctx) && !isPowerSave) {
        val fallbackColor = when (cardBgMode) {
            PhantomPrefs.CardBgMode.FOLLOW_GLASS -> MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
            PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(alpha = cardBgAlpha.coerceAtLeast(0.35f))
            PhantomPrefs.CardBgMode.DARK -> darkCardColor
        }
        val overlayColor: Color? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            when (cardBgMode) {
                PhantomPrefs.CardBgMode.FOLLOW_GLASS -> if (glassStrength > 0) {
                    MaterialTheme.colorScheme.surface.copy(alpha = (glassStrength / 100f) * 0.45f)
                } else null
                PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(alpha = cardBgAlpha)
                PhantomPrefs.CardBgMode.DARK -> darkCardColor
            }
        } else {
            null
        }

        Box(
            modifier = modifier
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .glassSurface(
                    state = glassState,
                    fallbackColor = fallbackColor,
                    cornerRadius = cornerRadius
                )
                .clip(shape)
                .let { m -> if (overlayColor != null) m.background(overlayColor, shape) else m }
                .let { m -> if (tintColor != null) m.background(tintColor, shape) else m }
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    } else {
        val solidBg = when (cardBgMode) {
            PhantomPrefs.CardBgMode.FOLLOW_GLASS -> MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(alpha = cardBgAlpha.coerceAtLeast(0.35f))
            PhantomPrefs.CardBgMode.DARK -> darkCardColor
        }
        Box(
            modifier = modifier
                .clip(shape)
                .background(solidBg, shape)
                .let { m -> if (tintColor != null) m.background(tintColor, shape) else m }
                .border(width = 1.dp, color = outline, shape = shape)
        ) {
            Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    }
}

/**
 * 頁面層背景圖片包裝：未設定任何背景圖時直接渲染 content（與 v2.1.0 逐像素一致）。
 * 已設定時於底層繪製降採樣裁切背景圖＋暗色可讀性遮罩（scrim），純裝飾、零觸控攔截。
 */
@Composable
fun PhantomBackgroundBox(
    layer: BackgroundLayer,
    glassState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val bgVersion = ThemePreferences.getBackgroundVersion()
    val bgFile = remember(ctx, layer, bgVersion) {
        BackgroundImageHelper.resolveBackgroundFile(ctx, layer)
    }
    val fallbackBg = MaterialTheme.colorScheme.background
    if (bgFile == null) {
        Box(
            modifier = modifier
                .background(fallbackBg)
                .phantomGlassSource(glassState),
            content = content
        )
        return
    }

    val isDark = fallbackBg.luminance() < 0.5f
    val scrimPref = ThemePreferences.getBackgroundScrimPercent(ctx)
    val scrimAlpha = if (scrimPref < 0) {
        if (isDark) 0.60f else 0.35f
    } else {
        (scrimPref / 100f).coerceIn(0f, 1f)
    }

    BoxWithConstraints(
        modifier = modifier
            .background(fallbackBg)
            .phantomGlassSource(glassState)
    ) {
        val targetW = constraints.maxWidth
        val targetH = constraints.maxHeight
        var bitmap by remember(bgFile.absolutePath, bgFile.lastModified(), targetW, targetH, bgVersion) {
            mutableStateOf<ImageBitmap?>(null)
        }
        LaunchedEffect(bgFile.absolutePath, bgFile.lastModified(), targetW, targetH, bgVersion) {
            bitmap = BackgroundImageHelper.loadBackgroundBitmap(ctx, bgFile, targetW, targetH)
        }
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
        }
        content()
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
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .pressableScale()
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
    hasNote: Boolean = false,
    hasTodo: Boolean = false,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val isCompact = PhantomPrefs.getCardDensity(ctx) == com.phantom.checker.util.CardDensity.COMPACT
    val cardPad = if (isCompact) 8.dp else 12.dp
    val vertPad = if (isCompact) 2.dp else 4.dp
    val is24h = PhantomPrefs.is24HourFormat(ctx)
    val timeText = remember(message.timestamp, is24h) {
        PhantomPrefs.formatMessageTime(ctx, message.timestamp, includeDate = true)
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
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
                if (hasNote) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "已記事",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (hasTodo) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已待辦",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (message.riskLabel == "scam") {
                    Text(
                        text = "  ⚠️ 詐騙 ${message.riskScore}",
                        color = MaterialTheme.colorScheme.error,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(if (isCompact) 2.dp else 4.dp))
        Text(
            text = highlightMatches(message.content, highlightQuery),
            style = MaterialTheme.typography.bodySmall,
            color = if (highlightAsScam) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = if (isCompact) 2 else 4,
            overflow = TextOverflow.Ellipsis
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
            Spacer(Modifier.height(if (isCompact) 2.dp else 4.dp))
            Text(
                text = stripMarkdown(message.riskReason),
                color = if (highlightAsScam) {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = if (isCompact) 1 else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (message.remindAt != null && message.remindAt > System.currentTimeMillis()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "⏰ 待辦提醒 · ${PhantomPrefs.formatMessageTime(ctx, message.remindAt, includeDate = true)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        val otpCode = remember(message.content) {
            com.phantom.checker.util.OtpDetector.extractOtp(message.content)
        }
        if (otpCode != null) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("otp_code", otpCode))
                    android.widget.Toast.makeText(ctx, "已複製驗證碼 $otpCode", android.widget.Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(
                    text = "複製驗證碼 $otpCode",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (highlightAsScam) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = vertPad)
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
                Column(modifier = Modifier.padding(cardPad), content = cardInner)
            }
        }
    } else {
        PhantomGlassCard(
            glassState = glassState,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = vertPad),
            cornerRadius = 20.dp,
            contentPadding = cardPad,
            content = cardInner
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageTagChipRow(
    tags: List<MessageTagRow>,
    maxShow: Int = 3,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    val visible = tags.take(maxShow)
    val overflow = tags.size - visible.size
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visible.forEach { tag ->
            val tagColor = parseTagColor(tag.colorHex)
            val isAuto = tag.source == MessageTagCrossRef.SOURCE_AUTO
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tagColor.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(tagColor)
                    )
                    Text(
                        text = if (isAuto) "${tag.name} · AI" else tag.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (overflow > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ReminderPickerDialog(
    onDismiss: () -> Unit,
    onSelectTimestamp: (Long) -> Unit
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("稍後處理提醒") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "選擇提醒時間，屆時將發送通知並可直接開啟訊息詳情：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        onSelectTimestamp(System.currentTimeMillis() + 60L * 60_000L)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("1 小時後")
                }
                OutlinedButton(
                    onClick = {
                        val cal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 20)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(java.util.Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        onSelectTimestamp(cal.timeInMillis)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("今晚 20:00")
                }
                OutlinedButton(
                    onClick = {
                        val cal = java.util.Calendar.getInstance().apply {
                            add(java.util.Calendar.DAY_OF_YEAR, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 9)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        onSelectTimestamp(cal.timeInMillis)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("明天早上 09:00")
                }
                Button(
                    onClick = {
                        val nowCal = java.util.Calendar.getInstance()
                        android.app.TimePickerDialog(
                            ctx,
                            { _, hourOfDay, minute ->
                                val target = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(java.util.Calendar.MINUTE, minute)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                    if (timeInMillis <= System.currentTimeMillis()) {
                                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                                    }
                                }
                                onSelectTimestamp(target.timeInMillis)
                            },
                            nowCal.get(java.util.Calendar.HOUR_OF_DAY),
                            nowCal.get(java.util.Calendar.MINUTE),
                            PhantomPrefs.is24HourFormat(ctx)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("自訂時間")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * 兩段式加入白名單對話框：
 * 1. 選擇「此寄件者全部訊息」或「僅此則訊息」
 * 2. 顯示即將加入的規則內容（寄件者＋App＋內容摘錄）並確認寫入
 */
@Composable
fun WhitelistAddDialogFlow(
    sender: String,
    appLabel: String,
    packageName: String,
    content: String,
    onDismiss: () -> Unit,
    onConfirm: (isSenderScope: Boolean) -> Unit
) {
    var selectedScopeIsSender by remember { mutableStateOf<Boolean?>(null) }

    if (selectedScopeIsSender == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("加入白名單") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "請選擇要加入白名單的範圍：",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { selectedScopeIsSender = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("此寄件者全部訊息（${sender.ifBlank { "未知寄件者" }}）")
                    }
                    OutlinedButton(
                        onClick = { selectedScopeIsSender = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("僅此則訊息")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        )
    } else {
        val isSender = selectedScopeIsSender == true
        val excerpt = content.replace("\n", " ").trim().take(80)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("確認加入白名單") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isSender) {
                            "規則類型：此寄件者全部訊息（後續同 App 同寄件者訊息將視為安全並跳過情報預查與警示）"
                        } else {
                            "規則類型：僅此則訊息（標記此則為安全並加入內容比對白名單）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("寄件者：${sender.ifBlank { "未知" }}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text("來源 App：$appLabel ($packageName)", style = MaterialTheme.typography.bodySmall)
                    if (excerpt.isNotBlank()) {
                        Text("內容摘錄：$excerpt", style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onConfirm(isSender) }) {
                    Text("確認加入")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedScopeIsSender = null }) {
                    Text("返回")
                }
            }
        )
    }
}

/**
 * #194 統一頂部迷你玻璃搜尋膠囊：36dp 高、wrap-content 寬、放大鏡＋「搜尋」（觸控目標 ≥ 48dp）。
 */
@Composable
fun PhantomSearchBarPill(
    glassState: HazeState,
    query: String,
    placeholder: String = "搜尋",
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null
) {
    val shape = remember { RoundedCornerShape(18.dp) }
    val labelText = if (query.isNotBlank()) query else "搜尋"
    Row(
        modifier = modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .semantics { contentDescription = "開啟搜尋：$placeholder" },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .height(36.dp)
                    .widthIn(min = 92.dp, max = 160.dp)
                    .phantomGlassSurface(
                        state = glassState,
                        fallbackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        cornerRadius = 18.dp
                    )
                    .clip(shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (query.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingActions != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = trailingActions
            )
        }
    }
}

/**
 * #205 頂部下拉搜尋把手：22dp 高、無文字、中央 4dp×120dp 膠囊，支援下拉（anchoredDraggable）或點擊開啟搜尋。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhantomPullSearchHandle(
    glassState: HazeState,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val decaySpec = rememberSplineBasedDecay<Float>()
    val dragDistancePx = with(density) { 48.dp.toPx() }
    val state = remember {
        AnchoredDraggableState(
            initialValue = 0f,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 800.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
            decayAnimationSpec = decaySpec
        )
    }

    SideEffect {
        state.updateAnchors(
            DraggableAnchors {
                0f at 0f
                1f at dragDistancePx
            }
        )
    }

    LaunchedEffect(state.currentValue) {
        if (state.currentValue == 1f) {
            onOpenSearch()
            state.snapTo(0f)
        }
    }

    val progress = if (dragDistancePx > 0f && !state.offset.isNaN()) {
        (state.offset / dragDistancePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val pillBaseColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .anchoredDraggable(state, Orientation.Vertical)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenSearch
            )
            .semantics {
                contentDescription = "下拉開啟搜尋"
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(4.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.3f * progress
                    alpha = 0.45f + 0.35f * progress
                    translationY = progress * with(density) { 4.dp.toPx() }
                }
                .clip(RoundedCornerShape(2.dp))
                .phantomGlassSurface(
                    state = glassState,
                    fallbackColor = pillBaseColor.copy(alpha = 0.45f),
                    cornerRadius = 2.dp
                )
                .background(pillBaseColor.copy(alpha = 0.45f))
        )
    }
}

/**
 * #180 統一全螢幕下拉搜尋覆蓋層：fade+slide、自動聚焦、IME inset、LazyColumn 效能、AI 語意搜尋入口、返回手勢關閉。
 */
@Composable
fun PhantomFullScreenSearchOverlay(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = "搜尋訊息、寄件者或關鍵字…",
    onAiSemanticSearch: (() -> Unit)? = null,
    aiQuotaHint: String = "AI 語意搜尋（消耗 1 次額度）",
    isAiSearching: Boolean = false,
    filterRow: (@Composable () -> Unit)? = null,
    resultsContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    androidx.activity.compose.BackHandler(enabled = visible) {
        onDismiss()
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { -it / 8 },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { -it / 8 }
    ) {
        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(visible) {
            if (visible) {
                kotlinx.coroutines.delay(60)
                runCatching { focusRequester.requestFocus() }
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "關閉搜尋"
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = "清除搜尋")
                                }
                            }
                        }
                    )
                }
                if (filterRow != null) {
                    Spacer(Modifier.height(8.dp))
                    filterRow()
                }
                if (onAiSemanticSearch != null && query.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAiSemanticSearch,
                        enabled = !isAiSearching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        if (isAiSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("AI 語意搜尋中…")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(aiQuotaHint)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    content = resultsContent
                )
            }
        }
    }
}

/**
 * #204 「不再自動記事/待辦」三範圍二段確認對話框：僅此寄件者（預設）/ 僅此 App / 含此關鍵字。
 */
@Composable
fun NeverAutoMemoConfirmDialog(
    packageName: String?,
    appLabel: String,
    sender: String?,
    defaultKeyword: String,
    onDismiss: () -> Unit,
    onConfirm: (packageName: String?, sender: String?, keyword: String?) -> Unit
) {
    val hasSender = !sender.isNullOrBlank()
    val hasPkg = !packageName.isNullOrBlank()
    var selectedScope by remember(sender, packageName) {
        mutableStateOf(if (hasSender) 0 else if (hasPkg) 1 else 2)
    }
    var keywordText by remember(defaultKeyword) {
        mutableStateOf(defaultKeyword.trim().take(20))
    }
    val canConfirm = when (selectedScope) {
        0 -> hasSender
        1 -> hasPkg
        else -> keywordText.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("不再自動記事／待辦") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "選擇不再自動加入記事或待辦的套用範圍：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasSender) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = 0 },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedScope == 0,
                            onClick = { selectedScope = 0 }
                        )
                        Text(
                            text = "僅此寄件者（$sender）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (hasPkg) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = 1 },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedScope == 1,
                            onClick = { selectedScope = 1 }
                        )
                        Text(
                            text = "僅此 App（${appLabel.ifBlank { packageName!! }}）",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedScope = 2 },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedScope == 2,
                        onClick = { selectedScope = 2 }
                    )
                    Text(
                        text = "含此關鍵字（AI 語意比對相似訊息）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (selectedScope == 2) {
                    OutlinedTextField(
                        value = keywordText,
                        onValueChange = { keywordText = it },
                        label = { Text("關鍵字") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedScope) {
                        0 -> onConfirm(packageName?.takeIf { it.isNotBlank() }, sender?.trim(), null)
                        1 -> onConfirm(packageName?.trim(), null, null)
                        else -> onConfirm(null, null, keywordText.trim())
                    }
                },
                enabled = canConfirm
            ) {
                Text("確定不再加入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

/**
 * #212 訊息分頁 App 卡片堆疊包裝層：在主卡後方依訊息總數繪製 0~2 層裝飾卡片，
 * 支援滑動視差（讀 listState 於 graphicsLayer lambda 內，不引起重組）與點擊收合動畫。
 */
@Composable
fun StackedAppCard(
    totalCount: Int,
    enabled: Boolean,
    isCompact: Boolean,
    isMuted: Boolean = false,
    listState: LazyListState? = null,
    isCollapsed: Boolean = false,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val layerCount = when {
        !enabled || totalCount <= 1 -> 0
        totalCount <= 10 -> 1
        else -> 2
    }
    if (layerCount == 0) {
        Box(modifier = modifier) {
            content()
        }
        return
    }
    val collapseFactor by animateFloatAsState(
        targetValue = if (isCollapsed) 0f else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "stack_collapse"
    )
    val baseAlphaScale = if (isMuted) 0.5f else 1f
    val stepY = if (isCompact) 3.dp else 6.dp
    val bottomSpace = stepY * layerCount
    val layerColor = lerp(
        MaterialTheme.colorScheme.surfaceVariant,
        MaterialTheme.colorScheme.onSurface,
        0.25f
    )
    val layerOutline = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    val layerShape = RoundedCornerShape(20.dp)

    Box(modifier = modifier.padding(bottom = bottomSpace)) {
        if (layerCount >= 2) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val parallaxPx = if (listState != null) {
                            val raw = ((listState.firstVisibleItemIndex * 2f) +
                                (listState.firstVisibleItemScrollOffset * 0.02f)) % 4f - 2f
                            (raw * 0.5f).dp.toPx()
                        } else {
                            0f
                        }
                        val scale = 1f - 0.08f * collapseFactor
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.35f * baseAlphaScale * collapseFactor
                        translationY = (stepY * 2).toPx() * collapseFactor + parallaxPx * collapseFactor
                    }
                    .clip(layerShape)
                    .background(layerColor)
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    val parallaxPx = if (listState != null) {
                        val raw = ((listState.firstVisibleItemIndex * 2f) +
                            (listState.firstVisibleItemScrollOffset * 0.02f)) % 4f - 2f
                        (raw * 0.35f).dp.toPx()
                    } else {
                        0f
                    }
                    val scale = 1f - 0.04f * collapseFactor
                    scaleX = scale
                    scaleY = scale
                    alpha = 0.55f * baseAlphaScale * collapseFactor
                    translationY = stepY.toPx() * collapseFactor + parallaxPx * collapseFactor
                }
                .clip(layerShape)
                .background(layerColor)
                .border(width = 1.dp, color = layerOutline, shape = layerShape)
        )
        content()
    }
}




