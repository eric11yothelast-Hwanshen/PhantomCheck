package com.phantom.checker.ui.partner

import com.phantom.checker.ui.components.phantomItem
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.data.MemoryEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.ui.AssistantHubActivity
import com.phantom.checker.ui.PhantomGlassCard
import com.phantom.checker.ui.ThemePreferences
import com.phantom.checker.ui.components.PortraitCardSection
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.AssistantAvatarCircle
import com.phantom.checker.util.AssistantAvatarHelper
import com.phantom.checker.util.BackgroundImageHelper
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * #338 / #350 AI 夥伴分頁（底部最右側分頁，標籤為使用者為助手取的名字，未命名 fallback「夥伴」）：
 * - 展示頭像、名字、陪伴天數、親密度等級（可點進詳情旅程）、近 7 天事件橫向卡、「我對你的瞭解」畫像卡與快捷設定。
 * - 不與既有 ASSISTANT 聊天分頁重複，本頁為夥伴個人檔案與儀表板。
 */
// #338 / #350 親密度公式常數（單一來源，不得在他處寫死副本）：
// points = 已確認記憶數 × 2 ＋ 聊天互動則數 × 1 ＋ 主動簡報點閱 × 1
private const val INTIMACY_WEIGHT_CONFIRMED_MEMORY = 2
private const val INTIMACY_WEIGHT_CHAT_MESSAGE = 1
private const val INTIMACY_WEIGHT_BRIEFING_READ = 1

private const val INTIMACY_THRESHOLD_LV2 = 30
private const val INTIMACY_THRESHOLD_LV3 = 100
private const val INTIMACY_THRESHOLD_LV4 = 250

private data class IntimacyStage(
    val level: Int,
    val title: String,
    val minPoints: Int,
    val maxPoints: Int?,
    val tagline: String
)

private val INTIMACY_STAGES: List<IntimacyStage> = listOf(
    IntimacyStage(1, "初識", 0, INTIMACY_THRESHOLD_LV2, "剛開始認識彼此的生活節奏"),
    IntimacyStage(2, "熟絡", INTIMACY_THRESHOLD_LV2, INTIMACY_THRESHOLD_LV3, "逐漸熟悉你的偏好與日常關注"),
    IntimacyStage(3, "默契", INTIMACY_THRESHOLD_LV3, INTIMACY_THRESHOLD_LV4, "能提前幫你抓出重要訊息與行程"),
    IntimacyStage(4, "摯友", INTIMACY_THRESHOLD_LV4, null, "最懂你的專屬數位夥伴")
)

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
    val stage = INTIMACY_STAGES.lastOrNull { pts >= it.minPoints } ?: INTIMACY_STAGES.first()
    val nextThreshold = stage.maxPoints ?: stage.minPoints
    val progress = if (stage.maxPoints == null) {
        1f
    } else {
        val span = (stage.maxPoints - stage.minPoints).coerceAtLeast(1)
        ((pts - stage.minPoints).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }
    return IntimacyLevelInfo(
        level = stage.level,
        title = stage.title,
        points = pts,
        nextThreshold = nextThreshold,
        progress = progress
    )
}

private data class RecentEventCardData(
    val key: String,
    val emoji: String,
    val title: String,
    val value: String,
    val subtitle: String
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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

    val scope = rememberCoroutineScope()
    var assistantName by remember(settingsTick) { mutableStateOf(PhantomPrefs.getAssistantName(appCtx)) }
    var avatarBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var headerBgVersion by remember { mutableIntStateOf(ThemePreferences.getBackgroundVersion()) }
    var headerBgBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var showHeaderBgMenu by remember { mutableStateOf(false) }
    val headerBgPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val res = BackgroundImageHelper.saveAssistantHeaderBackgroundFromUri(appCtx, uri)
            res.fold(
                onSuccess = {
                    ThemePreferences.bumpBackgroundVersion()
                    headerBgVersion = ThemePreferences.getBackgroundVersion()
                    PhantomSettingsBus.notifyChanged()
                    onShowMessage("已更新夥伴頁頭部背景")
                },
                onFailure = { err ->
                    onShowMessage("設定背景失敗：${err.localizedMessage ?: "未知錯誤"}")
                }
            )
        }
    }
    val personalityPreset = remember(settingsTick) { PhantomPrefs.getAssistantPersonalityPreset(appCtx) }
    var activeTopics by remember(settingsTick) { mutableStateOf(PhantomPrefs.getProactiveTopics(appCtx)) }
    var chatMsgCount by remember { mutableStateOf(0) }
    var showIntimacySheet by remember { mutableStateOf(false) }

    val companionDays = remember(settingsTick) { PhantomPrefs.getPartnerCompanionDays(appCtx) }
    val briefingReadCount = remember(settingsTick) { PhantomPrefs.getBriefingReadCount(appCtx) }
    val lastBriefingSummary = remember(settingsTick) { PhantomPrefs.getLastBriefingSummary(appCtx) }
    val proactivePushTotal = remember(settingsTick) { PhantomPrefs.getProactivePushTotalCount(appCtx) }

    var goalProgressCount by remember { mutableStateOf(0) }
    LaunchedEffect(settingsTick, headerBgVersion) {
        assistantName = PhantomPrefs.getAssistantName(appCtx)
        avatarBitmap = AssistantAvatarHelper.loadAvatarBitmap(appCtx)
        val headerBgFile = BackgroundImageHelper.resolveAssistantHeaderBackgroundFile(appCtx)
        headerBgBitmap = if (headerBgFile != null) {
            val dm = appCtx.resources.displayMetrics
            BackgroundImageHelper.loadBackgroundBitmap(
                ctx = appCtx,
                file = headerBgFile,
                targetW = dm.widthPixels.coerceAtLeast(1080),
                targetH = (dm.density * 220f).toInt().coerceAtLeast(480)
            )
        } else {
            null
        }
        chatMsgCount = withContext(Dispatchers.IO) {
            runCatching {
                db.chatDao().allSessions().sumOf { s -> db.chatDao().messagesForSession(s.id).size }
            }.getOrDefault(0)
        }
        goalProgressCount = withContext(Dispatchers.IO) {
            runCatching { db.goalDao().totalProgressCount() }.getOrDefault(0)
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
    val intimacy = remember(confirmedMemoryCount, chatMsgCount, briefingReadCount, goalProgressCount) {
        computeIntimacyLevel(confirmedMemoryCount, chatMsgCount + goalProgressCount, briefingReadCount)
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

    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scrimPref = remember(settingsTick, headerBgVersion) { PhantomPrefs.getBackgroundScrimPercent(appCtx) }
    val scrimAlpha = remember(scrimPref, isDarkTheme) {
        if (scrimPref < 0) {
            if (isDarkTheme) 0.55f else 0.40f
        } else {
            (scrimPref / 100f).coerceIn(0.15f, 0.85f)
        }
    }
    val pageBgColor = MaterialTheme.colorScheme.background

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 0.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // #392 1. 夥伴頂部橫幅：去卡片化全寬區塊、76dp 頭像、名字與 chips、右上角 48dp 更換背景按鈕
        phantomItem(0, key = "partner_profile_header") {
            val hasCustomBg = headerBgBitmap != null
            val titleColor = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 164.dp)
            ) {
                if (headerBgBitmap != null) {
                    Image(
                        bitmap = headerBgBitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    // 漸層遮罩確保文字可讀性並平順過渡至下方頁面底色
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops = arrayOf(
                                        0.0f to Color.Black.copy(alpha = (scrimAlpha * 0.65f).coerceIn(0.15f, 0.75f)),
                                        0.68f to Color.Black.copy(alpha = scrimAlpha),
                                        1.0f to pageBgColor
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                        pageBgColor
                                    )
                                )
                            )
                    )
                }

                // 右上角更換／移除背景按鈕（48dp 觸控目標）
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 6.dp, end = 8.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (hasCustomBg) {
                                showHeaderBgMenu = true
                            } else {
                                try {
                                    headerBgPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (e: Exception) {
                                    onShowMessage("無法開啟相簿選擇器：${e.localizedMessage ?: "不支援"}")
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasCustomBg) Color.Black.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "更換頭部背景",
                            tint = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showHeaderBgMenu,
                        onDismissRequest = { showHeaderBgMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("更換頭部背景") },
                            onClick = {
                                showHeaderBgMenu = false
                                try {
                                    headerBgPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (e: Exception) {
                                    onShowMessage("無法開啟相簿選擇器：${e.localizedMessage ?: "不支援"}")
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("移除背景（恢復預設）") },
                            onClick = {
                                showHeaderBgMenu = false
                                BackgroundImageHelper.removeAssistantHeaderBackground(appCtx)
                                ThemePreferences.bumpBackgroundVersion()
                                headerBgVersion = ThemePreferences.getBackgroundVersion()
                                PhantomSettingsBus.notifyChanged()
                                onShowMessage("已移除夥伴頁頭部背景")
                            }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(end = 44.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        AssistantAvatarCircle(
                            avatarBitmap = avatarBitmap,
                            assistantName = assistantName,
                            size = 76.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = assistantName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (hasCustomBg) {
                                        Color.Black.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                    }
                                ) {
                                    Text(
                                        text = "陪伴第 $companionDays 天",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (hasCustomBg) {
                                        Color.Black.copy(alpha = 0.45f)
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                                    }
                                ) {
                                    Text(
                                        text = personalityPreset.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
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
                            colors = if (hasCustomBg) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("改名／換頭像", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }

        // #378 每日回顧／生活時間軸卡片（無 API key 亦 100% 可用）
        phantomItem(1, key = "partner_diary_card") {
            Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                PartnerDiaryCard(glassState = glassState)
            }
        }

        // #379 長程目標追蹤卡（進度條＋連續天數＋里程碑；無 API key 手動建立全流程可用）
        phantomItem(2, key = "partner_goals_card") {
            Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                PartnerGoalsCard(
                    glassState = glassState,
                    settingsVersion = settingsTick
                )
            }
        }

        // 2. 親密度進度條與等級（#350 可點擊進入詳情頁）
        phantomItem(3, key = "partner_intimacy_card") {
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .pressableScale()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { showIntimacySheet = true },
                cornerRadius = 20.dp,
                contentPadding = 16.dp
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
                            text = if (intimacy.level >= INTIMACY_STAGES.size) {
                                "${intimacy.points} pt（最高階）▸"
                            } else {
                                "${intimacy.points} / ${intimacy.nextThreshold} pt ▸"
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
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Text(
                        text = "已確認記憶 $confirmedMemoryCount（×$INTIMACY_WEIGHT_CONFIRMED_MEMORY）＋ 對話互動 $chatMsgCount（×$INTIMACY_WEIGHT_CHAT_MESSAGE）＋ 簡報點閱 $briefingReadCount（×$INTIMACY_WEIGHT_BRIEFING_READ）· 點擊查看旅程",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. 近 7 天事件橫向滑動卡（#357 等高視覺化對齊）
        phantomItem(4, key = "partner_recent_events") {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "近 7 天互動動態",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
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
                            cornerRadius = 18.dp,
                            contentPadding = 14.dp
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${card.emoji} ${card.title}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = card.value,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = card.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    minLines = 2,
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
        phantomItem(5, key = "partner_portrait_card") {
            Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                PortraitCardSection(
                    glassState = glassState,
                    onShowMessage = onShowMessage
                )
            }
        }

        // 5. 快捷區：主動推播主題開關 ＋ 個性／詳細度快速切換
        phantomItem(6, key = "partner_quick_controls") {
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.padding(horizontal = 14.dp),
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

                    HorizontalDivider()
                    OutlinedButton(
                        onClick = {
                            ctx.startActivity(Intent(ctx, AssistantHubActivity::class.java))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "助手個性、詳細度與主動推播設定",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (showIntimacySheet) {
        IntimacyDetailSheet(
            assistantName = assistantName,
            intimacy = intimacy,
            confirmedMemoryCount = confirmedMemoryCount,
            chatMsgCount = chatMsgCount,
            briefingReadCount = briefingReadCount,
            glassState = glassState,
            onDismiss = { showIntimacySheet = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntimacyDetailSheet(
    assistantName: String,
    intimacy: IntimacyLevelInfo,
    confirmedMemoryCount: Int,
    chatMsgCount: Int,
    briefingReadCount: Int,
    glassState: HazeState,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val progressPercent = (intimacy.progress * 100f).roundToInt()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "與「$assistantName」的親密度",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 大進度環＋目前階段與積分
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp,
                contentPadding = 16.dp
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier.size(136.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 12.dp.toPx()
                            val inset = strokeWidth / 2f
                            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                            drawArc(
                                color = trackColor,
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = 360f * intimacy.progress.coerceIn(0f, 1f),
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Lv.${intimacy.level}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = intimacy.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${intimacy.points} pt",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Text(
                        text = if (intimacy.level >= INTIMACY_STAGES.size) {
                            "已達最高階段「${intimacy.title}」（$progressPercent%）"
                        } else {
                            val remaining = (intimacy.nextThreshold - intimacy.points).coerceAtLeast(0)
                            "距離下一階段還差 $remaining pt（目前進度 $progressPercent%）"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 我們的旅程（縱向階段清單）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "我們的旅程",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                INTIMACY_STAGES.forEach { stage ->
                    val isCurrent = stage.level == intimacy.level
                    val isReached = intimacy.points >= stage.minPoints
                    val rangeLabel = if (stage.maxPoints != null) {
                        "${stage.minPoints}–${stage.maxPoints} pt"
                    } else {
                        "${stage.minPoints}+ pt"
                    }
                    val statusLabel = when {
                        isCurrent -> "目前階段"
                        isReached -> "已達成"
                        else -> "還差 ${(stage.minPoints - intimacy.points).coerceAtLeast(1)} pt"
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "Lv.${stage.level} ${stage.title}（$rangeLabel）",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stage.tagline,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = when {
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    isReached -> MaterialTheme.colorScheme.secondary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // 如何獲得積分與目前累積明細
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "如何獲得積分與目前明細",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    contentPadding = 14.dp
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        IntimacyBreakdownRow(
                            title = "已確認記憶（×$INTIMACY_WEIGHT_CONFIRMED_MEMORY）",
                            subtitle = "在對話中說「記住…」或於記憶頁新增並確認",
                            value = "$confirmedMemoryCount 筆（+${confirmedMemoryCount * INTIMACY_WEIGHT_CONFIRMED_MEMORY} pt）"
                        )
                        HorizontalDivider()
                        IntimacyBreakdownRow(
                            title = "聊天互動（×$INTIMACY_WEIGHT_CHAT_MESSAGE）",
                            subtitle = "與 AI 助手對話交流，每則訊息累積積分",
                            value = "$chatMsgCount 則（+${chatMsgCount * INTIMACY_WEIGHT_CHAT_MESSAGE} pt）"
                        )
                        HorizontalDivider()
                        IntimacyBreakdownRow(
                            title = "簡報點閱（×$INTIMACY_WEIGHT_BRIEFING_READ）",
                            subtitle = "點擊早晚簡報通知、於對話或統計頁展開閱讀早晚簡報",
                            value = "$briefingReadCount 次（+${briefingReadCount * INTIMACY_WEIGHT_BRIEFING_READ} pt）"
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("關閉")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun IntimacyBreakdownRow(
    title: String,
    subtitle: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
