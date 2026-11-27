package com.phantom.checker.desktop

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * #520 §7 commit 13：桌面端統一對話框外殼 PhantomDialog
 * - 統一使用 LocalDesktopTokens.current.dialogContainer（零寫死深色）、圓角 20dp、內距 24dp
 * - 標題列＋右上 ✕ 關閉鈕（≥32dp 點擊目標）、底部按鈕右對齊、Esc 關閉、HC 粗邊框零陰影
 * - 動效 D8：平衡模式 alpha 180ms + scale 0.96→1（draw-phase graphicsLayer）；省電模式 fade80 無縮放
 */
@Composable
fun PhantomDialog(
    onDismissRequest: () -> Unit,
    title: String,
    subtitle: String? = null,
    subtitleColor: Color = Color.Unspecified,
    maxWidth: Dp = 560.dp,
    maxHeight: Dp = 680.dp,
    dismissible: Boolean = true,
    containerColor: Color = Color.Unspecified,
    dismissButton: (@Composable RowScope.() -> Unit)? = null,
    confirmButton: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val motion = LocalDesktopMotion.current
    val resolvedContainer = if (containerColor == Color.Unspecified) tokens.dialogContainer else containerColor
    val resolvedSubtitleColor = if (subtitleColor == Color.Unspecified) tokens.textMuted else subtitleColor

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val animAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = if (motion.isBalanced) 180 else 80, easing = FastOutSlowInEasing),
        label = "phantom_dialog_alpha"
    )
    val animScale by animateFloatAsState(
        targetValue = if (appeared || motion.isPowerSaver) 1f else 0.96f,
        animationSpec = tween(durationMillis = if (motion.isBalanced) 200 else 0, easing = FastOutSlowInEasing),
        label = "phantom_dialog_scale"
    )

    Dialog(
        onDismissRequest = { if (dismissible) onDismissRequest() },
        properties = DialogProperties(
            dismissOnBackPress = dismissible,
            dismissOnClickOutside = dismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = resolvedContainer,
            border = BorderStroke(if (tokens.isHighContrast) 2.dp else 1.dp, tokens.border),
            shadowElevation = if (tokens.isHighContrast) 0.dp else 12.dp,
            modifier = Modifier
                .widthIn(min = 320.dp, max = maxWidth)
                .heightIn(max = maxHeight)
                .padding(16.dp)
                .graphicsLayer {
                    alpha = animAlpha
                    scaleX = animScale
                    scaleY = animScale
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row: Title (+ optional subtitle) + ✕ Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = tokens.textPrimary
                        )
                        if (!subtitle.isNullOrBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = resolvedSubtitleColor
                            )
                        }
                    }
                    if (dismissible) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable { onDismissRequest() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "✕",
                                style = MaterialTheme.typography.labelLarge,
                                color = tokens.textSecondary
                            )
                        }
                    }
                }

                HorizontalDivider(color = tokens.border)

                // Dialog Body
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )

                // Footer Row: Right-aligned buttons (confirmButton on the far right)
                if (dismissButton != null || confirmButton != null) {
                    Spacer(Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        dismissButton?.invoke(this)
                        confirmButton?.invoke(this)
                    }
                }
            }
        }
    }
}
