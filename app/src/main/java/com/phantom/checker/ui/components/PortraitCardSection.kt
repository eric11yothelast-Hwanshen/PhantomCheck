package com.phantom.checker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.MemoryEngine
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.PhantomGlassCard
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.launch

/**
 * #337 / #338 共用「我對你的瞭解」個人畫像卡元件：
 * - 內嵌於 MemoryTab 頂部（#337）與 PartnerTab（#338）。
 * - 首次開啟／每 7 天／新增 ≥20 筆已確認記憶自動觸發一次更新；另提供手動「重新生成」。
 * - 嚴守敏感雙閘門：敏感記憶絕不進入畫像。
 */
@Composable
fun PortraitCardSection(
    glassState: HazeState,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val db = remember(appCtx) { AppDatabaseHolder.db(appCtx) }

    var portraitText by remember { mutableStateOf(PhantomPrefs.getPortraitCardText(appCtx)) }
    var updatedAt by remember { mutableStateOf(PhantomPrefs.getPortraitCardUpdatedAt(appCtx)) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (MemoryEngine.shouldRefreshPortrait(appCtx, db)) {
            isRefreshing = true
            val next = MemoryEngine.generatePortraitCard(appCtx, db, forceManual = false)
            if (!next.isNullOrBlank()) {
                portraitText = next
                updatedAt = PhantomPrefs.getPortraitCardUpdatedAt(appCtx)
            }
            isRefreshing = false
        }
    }

    PhantomGlassCard(
        glassState = glassState,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        contentPadding = 14.dp
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✨ 我對你的瞭解",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    enabled = !isRefreshing,
                    onClick = {
                        isRefreshing = true
                        scope.launch {
                            val next = MemoryEngine.generatePortraitCard(appCtx, db, forceManual = true)
                            isRefreshing = false
                            if (!next.isNullOrBlank()) {
                                portraitText = next
                                updatedAt = PhantomPrefs.getPortraitCardUpdatedAt(appCtx)
                                onShowMessage("已更新「我對你的瞭解」畫像卡")
                            } else {
                                onShowMessage("尚無一般記憶可供生成畫像（敏感記憶絕不納入）")
                            }
                        }
                    }
                ) {
                    Text(if (isRefreshing) "生成中…" else "重新生成")
                }
            }
            Text(
                text = portraitText.ifBlank {
                    "目前尚未建立畫像摘要。新增幾筆一般記憶後將自動濃縮為精簡畫像，對話時優先注入以節省 token。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            val meta = buildString {
                if (updatedAt > 0L) {
                    append("更新於 ${PhantomPrefs.formatMessageTime(appCtx, updatedAt, includeDate = true)} · ")
                }
                append("🔒 敏感記憶絕不納入畫像 · 對話優先注入")
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
