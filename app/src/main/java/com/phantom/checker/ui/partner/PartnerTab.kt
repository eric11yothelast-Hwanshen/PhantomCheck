package com.phantom.checker.ui.partner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.MemoryEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.PhantomGlassCard
import com.phantom.checker.ui.components.PortraitCardSection
import com.phantom.checker.util.AssistantAvatarCircle
import com.phantom.checker.util.AssistantAvatarHelper
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomPrefs.AssistantPersonalityPreset
import com.phantom.checker.util.PhantomPrefs.AssistantVerbosity
import com.phantom.checker.util.PhantomSettingsBus
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * #338 AI 夥伴分頁（底部最右側分頁，標籤為使用者為助手取的名字，未命名 fallback「夥伴」）：
 * - 展示頭像、名字、陪伴天數、親密度等級、近 7 天事件橫向卡、「我對你的瞭解」畫像卡與快捷設定。
 * - 不與既有 ASSISTANT 聊天分頁重複，本頁為夥伴個人檔案與儀表板。
 */
// #338 親密度公式常數（僅供顯示，不影響任何功能權限）：
// points = 已確認記憶數 × 2 ＋ 聊天互動則數 × 1 ＋ 主動簡報點閱 × 1
private const val INTIMACY_WEIGHT_CONFIRMED_MEMORY = 2
private const val INTIMACY_WEIGHT_CHAT_MESSAGE = 1
private const val INTIMACY_WEIGHT_BRIEFING_READ = 1

private data class IntimacyLevelInfo(
    val level: Int,
    val title: String,
    val points: Int,
    val nextThreshold: Int,
    val progress: Float
)

private fun computeIntimacyLevel(
    confirmedMemories: Int,
    chatMessageCount: Int,
    briefingReadCount: Int
): IntimacyLevelInfo {
    val pts = confirmedMemories * INTIMACY_WEIGHT_CONFIRMED_MEMORY +
        chatMessageCount * INTIMACY_WEIGHT_CHAT_MESSAGE +
        briefingReadCount * INTIMACY_WEIGHT_BRIEFING_READ
    return when {
        pts < 30 -> IntimacyLevelInfo(1, "初識", pts, 30, (pts / 30f).coerceIn(0f, 1f))
        pts < 100 -> IntimacyLevelInfo(2, "熟絡", pts, 100, ((pts - 30) / 70f).coerceIn(0f, 1f))
        pts < 250 -> IntimacyLevelInfo(3, "默契", pts, 250, ((pts - 100) / 150f).coerceIn(0f, 1f))
        else -> IntimacyLevelInfo(4, "摯友", pts, 250, 1f)
    }
}

private data class RecentEventCardData(
    val key: String,
    val emoji: String,
    val title: String,
    val value: String,
    val subtitle: String
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PartnerTab(
    glassState: HazeState,
    onStartChat: () -> Unit,
    onOpenPersonaSettings: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val db = remember(appCtx) { AppDatabaseHolder.db(appCtx) }

    val settingsTick by PhantomSettingsBus.version.collectAsState()
    val rawMemories by remember(db) { db.memoryDao().observeAll() }.collectAsState(initial = emptyList())

    var assistantName by remember(settingsTick) { mutableStateOf(PhantomPrefs.getAssistantName(appCtx)) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var personalityPreset by remember(settingsTick) { mutableStateOf(PhantomPrefs.getAssistantPersonalityPreset(appCtx)) }
    var verbosity by remember(settingsTick) { mutableStateOf(PhantomPrefs.getAssistantVerbosity(appCtx)) }
    var activeTopics by remember(settingsTick) { mutableStateOf(PhantomPrefs.getProactiveTopics(appCtx)) }
    var chatMsgCount by remember { mutableStateOf(0) }

    val companionDays = remember(settingsTick) { PhantomPrefs.getPartnerCompanionDays(appCtx) }
    val briefingReadCount = remember(settingsTick) { PhantomPrefs.getBriefingReadCount(appCtx) }
    val lastBriefingSummary = remember(settingsTick) { PhantomPrefs.getLastBriefingSummary(appCtx) }
    val proactivePushTotal = remember(settingsTick) { PhantomPrefs.getProactivePushTotalCount(appCtx) }

    LaunchedEffect(settingsTick) {
        assistantName = PhantomPrefs.getAssistantName(appCtx)
        avatarBitmap = AssistantAvatarHelper.loadAvatarBitmap(appCtx)
        chatMsgCount = withContext(Dispatchers.IO) {
            runCatching {
                db.chatDao().allSessions().sumOf { s -> db.chatDao().messagesForSession(s.id).size }
            }.getOrDefault(0)
        }
    }

    val nowMs = System.currentTimeMillis()
    val since7d = nowMs - 7L * 24L * 60L * 60L * 1000L
    val confirmedMemoryCount = remember(rawMemories) {
        rawMemories.count {
            it.status == MemoryEntity.STATUS_ACTIVE && it.confidence == MemoryEntity.CONFIDENCE_CONFIRMED
        }
    }
    val newConfirmed7d = remember(rawMemories, since7d) {
        rawMemories.count {
            it.createdAt >= since7d && it.confidence == MemoryEntity.CONFIDENCE_CONFIRMED
        }
    }
    val conflictCount = remember(rawMemories) {
        rawMemories.count { it.status == MemoryEntity.STATUS_CONFLICT }
    }
    val intimacy = remember(confirmedMemoryCount, chatMsgCount, briefingReadCount) {
        computeIntimacyLevel(confirmedMemoryCount, chatMsgCount, briefingReadCount)
    }

    val recentCards = remember(lastBriefingSummary, newConfirmed7d, conflictCount, proactivePushTotal) {
        listOf(
            RecentEventCardData(
                key = "briefing",
                emoji = "📰",
                title = "最新早晚簡報",
                value = if (lastBriefingSummary.isNotBlank()) "已送達" else "尚無紀錄",
                subtitle = lastBriefingSummary.ifBlank { "啟用早晚簡報後將於指定時段整理今日重點" }
            ),
            RecentEventCardData(
                key = "mem_7d",
                emoji = "🧠",
                title = "近 7 天新增確認記憶",
                value = "$newConfirmed7d 筆",
                subtitle = if (newConfirmed7d > 0) "已納入個人長期記憶" else "在對話中說「記住…」即可累積"
            ),
            RecentEventCardData(
                key = "conflict",
                emoji = "⚖️",
                title = "待裁定記憶衝突",
                value = "$conflictCount 筆",
                subtitle = if (conflictCount > 0) "可至記憶分頁選擇保留版本" else "目前記憶庫狀態一致無衝突"
            ),
            RecentEventCardData(
                key = "push",
                emoji = "🔔",
                title = "累計主動簡報／提醒",
                value = "$proactivePushTotal 次",
                subtitle = if (proactivePushTotal > 0) "已點閱簡報 $briefingReadCount 次" else "零 token 變動偵測待命中"
            )
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. 夥伴頭像、名字、陪伴天數與快捷按鈕
        item(key = "partner_profile_header") {
            PhantomGlassCard(
                glassState = glassState,
                cornerRadius = 20.dp,
                contentPadding = 16.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AssistantAvatarCircle(
                            avatarBitmap = avatarBitmap,
                            assistantName = assistantName,
                            size = 68.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = assistantName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "已陪伴你第 $companionDays 天 · ${personalityPreset.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onStartChat,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("開始聊天", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onOpenPersonaSettings,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("改名／換頭像", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // 2. 親密度進度條與等級
        item(key = "partner_intimacy_card") {
            PhantomGlassCard(
                glassState = glassState,
                cornerRadius = 18.dp,
                contentPadding = 14.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Lv.${intimacy.level} ${intimacy.title}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = if (intimacy.level >= 4) {
                                "${intimacy.points} pt（最高階）"
                            } else {
                                "${intimacy.points} / ${intimacy.nextThreshold} pt"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    LinearProgressIndicator(
                        progress = { intimacy.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                    )
                    Text(
                        text = "已確認記憶 $confirmedMemoryCount（×2）＋ 對話互動 $chatMsgCount（×1）＋ 簡報點閱 $briefingReadCount（×1）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. 近 7 天事件橫向滑動卡
        item(key = "partner_recent_events") {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "近 7 天互動動態",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(recentCards, key = { it.key }) { card ->
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.width(204.dp),
                            cornerRadius = 16.dp,
                            contentPadding = 12.dp
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${card.emoji} ${card.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = card.value,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = card.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. 共用「我對你的瞭解」畫像卡（#337 同一元件復用）
        item(key = "partner_portrait_card") {
            PortraitCardSection(
                glassState = glassState,
                onShowMessage = onShowMessage
            )
        }

        // 5. 快捷區：主動推播主題開關 ＋ 個性／詳細度快速切換
        item(key = "partner_quick_controls") {
            PhantomGlassCard(
                glassState = glassState,
                cornerRadius = 18.dp,
                contentPadding = 14.dp
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "主動推播主題快捷",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val topicOptions = listOf(
                        PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE to "行程提醒",
                        PhantomPrefs.PROACTIVE_TOPIC_TODO to "待辦到期",
                        PhantomPrefs.PROACTIVE_TOPIC_CONFLICT to "撞期偵測",
                        PhantomPrefs.PROACTIVE_TOPIC_INTEREST to "興趣情報",
                        PhantomPrefs.PROACTIVE_TOPIC_REGION to "地區情報"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        topicOptions.forEach { (key, label) ->
                            val selected = key in activeTopics
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    PhantomPrefs.setProactiveTopicEnabled(appCtx, key, !selected)
                                    activeTopics = PhantomPrefs.getProactiveTopics(appCtx)
                                    PhantomSettingsBus.notifyChanged()
                                },
                                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }

                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "個性風格快速切換",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistantPersonalityPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = personalityPreset == preset,
                                onClick = {
                                    personalityPreset = preset
                                    PhantomPrefs.setAssistantPersonalityPreset(appCtx, preset)
                                    PhantomSettingsBus.notifyChanged()
                                    onShowMessage("已切換為「${preset.label}」")
                                },
                                label = { Text(preset.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }

                    Text(
                        text = "回答詳細度",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AssistantVerbosity.entries.forEach { v ->
                            FilterChip(
                                selected = verbosity == v,
                                onClick = {
                                    verbosity = v
                                    PhantomPrefs.setAssistantVerbosity(appCtx, v)
                                    PhantomSettingsBus.notifyChanged()
                                },
                                label = { Text(v.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        }
    }
}
