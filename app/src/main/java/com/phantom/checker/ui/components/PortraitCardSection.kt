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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()

    var portraitText by remember { mutableStateOf(PhantomPrefs.getPortraitCardText(appCtx)) }
    var updatedAt by remember { mutableStateOf(PhantomPrefs.getPortraitCardUpdatedAt(appCtx)) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val shouldRefresh = withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            MemoryEngine.shouldRefreshPortrait(appCtx, db)
        }
        if (shouldRefresh) {
            isRefreshing = true
            try {
                val (next, nextUpdatedAt) = withContext(Dispatchers.IO) {
                    val db = AppDatabaseHolder.db(appCtx)
                    val generated = MemoryEngine.generatePortraitCard(appCtx, db, forceManual = false)
                    val ts = if (!generated.isNullOrBlank()) PhantomPrefs.getPortraitCardUpdatedAt(appCtx) else updatedAt
                    generated to ts
                }
                if (!next.isNullOrBlank()) {
                    portraitText = next
                    updatedAt = nextUpdatedAt
                }
            } finally {
                isRefreshing = false
            }
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
                    text = stringResource(R.string.portrait_s_b0f0f9),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(
                    enabled = !isRefreshing,
                    onClick = {
                        if (isRefreshing) return@TextButton
                        isRefreshing = true
                        scope.launch {
                            try {
                                val (next, nextUpdatedAt) = withContext(Dispatchers.IO) {
                                    val db = AppDatabaseHolder.db(appCtx)
                                    val generated = MemoryEngine.generatePortraitCard(appCtx, db, forceManual = true)
                                    val ts = if (!generated.isNullOrBlank()) PhantomPrefs.getPortraitCardUpdatedAt(appCtx) else updatedAt
                                    generated to ts
                                }
                                if (!next.isNullOrBlank()) {
                                    portraitText = next
                                    updatedAt = nextUpdatedAt
                                    onShowMessage(resCtx.getString(R.string.portrait_s_c70059))
                                } else {
                                    onShowMessage(resCtx.getString(R.string.portrait_s_2625ee))
                                }
                            } finally {
                                isRefreshing = false
                            }
                        }
                    }
                ) {
                    Text(if (isRefreshing) stringResource(R.string.common_generating) else stringResource(R.string.common_regenerate))
                }
            }
            Text(
                text = portraitText.ifBlank {
                    resCtx.getString(R.string.portrait_s_3098a4)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            val meta = buildString {
                if (updatedAt > 0L) {
                    append(resCtx.getString(R.string.portrait_s_2fafae, PhantomPrefs.formatMessageTime(appCtx, updatedAt, includeDate = true)))
                }
                append(resCtx.getString(R.string.portrait_s_1d3246))
            }
            Text(
                text = meta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
