package com.phantom.checker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.ui.components.GlassCard
import com.phantom.checker.ui.components.glassSurface
import com.phantom.checker.ui.components.pressableScale
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            .glassSurface(
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
    GlassCard(
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
            text = "允許顯示通知後，偵測到高風險詐騙訊息時才能立即發出警示通知。",
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
                    text = message.sender,
                    style = MaterialTheme.typography.titleSmall
                )
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
            text = message.content,
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
                text = message.riskReason,
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
                .glassSurface(
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
        GlassCard(
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
