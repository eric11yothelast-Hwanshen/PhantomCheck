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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import com.phantom.checker.ui.PhantomMarkdownText
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes

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

private const val INTIMACY_THRESHOLD_LV2 = 50
private const val INTIMACY_THRESHOLD_LV3 = 160
private const val INTIMACY_THRESHOLD_LV4 = 420
private const val INTIMACY_THRESHOLD_LV5 = 900

private data class IntimacyStage(
    val level: Int,
    @StringRes val titleRes: Int,
    val minPoints: Int,
    val maxPoints: Int?,
    @StringRes val taglineRes: Int
) {
    val title: String get() = com.phantom.checker.PhantomApplication.str(titleRes)
    val tagline: String get() = com.phantom.checker.PhantomApplication.str(taglineRes)
}

private val INTIMACY_STAGES: List<IntimacyStage> = listOf(
    IntimacyStage(1, R.string.partner_stage_1_title, 0, INTIMACY_THRESHOLD_LV2, R.string.partner_stage_1_tagline),
    IntimacyStage(2, R.string.partner_stage_2_title, INTIMACY_THRESHOLD_LV2, INTIMACY_THRESHOLD_LV3, R.string.partner_stage_2_tagline),
    IntimacyStage(3, R.string.partner_stage_3_title, INTIMACY_THRESHOLD_LV3, INTIMACY_THRESHOLD_LV4, R.string.partner_stage_3_tagline),
    IntimacyStage(4, R.string.partner_stage_4_title, INTIMACY_THRESHOLD_LV4, INTIMACY_THRESHOLD_LV5, R.string.partner_stage_4_tagline),
    IntimacyStage(5, R.string.partner_stage_5_title, INTIMACY_THRESHOLD_LV5, null, R.string.partner_stage_5_tagline)
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
    val resCtx = LocalContext.current
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
                    onShowMessage(resCtx.getString(R.string.partner_s_7a5861))
                },
                onFailure = { err ->
                    onShowMessage(resCtx.getString(R.string.partner_s_9e8983, err.localizedMessage ?: resCtx.getString(R.string.common_unknown_error)))
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
                title = resCtx.getString(R.string.partner_s_429e9c),
                value = if (lastBriefingSummary.isNotBlank()) resCtx.getString(R.string.partner_s_eb5cf8) else resCtx.getString(R.string.partner_s_b96a46),
                subtitle = lastBriefingSummary.ifBlank { resCtx.getString(R.string.partner_s_0adea5) }
            ),
            RecentEventCardData(
                key = "mem_7d",
                emoji = "🧠",
                title = resCtx.getString(R.string.partner_s_927e75),
                value = resCtx.getString(R.string.partner_s_a4fda8, newConfirmed7d),
                subtitle = if (newConfirmed7d > 0) resCtx.getString(R.string.partner_s_f4706a) else resCtx.getString(R.string.partner_s_7c45c3)
            ),
            RecentEventCardData(
                key = "conflict",
                emoji = "⚖️",
                title = resCtx.getString(R.string.partner_s_a218ce),
                value = resCtx.getString(R.string.partner_s_a4fda8, conflictCount),
                subtitle = if (conflictCount > 0) resCtx.getString(R.string.partner_s_69b431) else resCtx.getString(R.string.partner_s_58bf7b)
            ),
            RecentEventCardData(
                key = "push",
                emoji = "🔔",
                title = resCtx.getString(R.string.partner_s_9316ea),
                value = resCtx.getString(R.string.partner_s_e893a8, proactivePushTotal),
                subtitle = if (proactivePushTotal > 0) resCtx.getString(R.string.partner_s_7907bd, briefingReadCount) else resCtx.getString(R.string.partner_s_bc5c52)
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
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = com.phantom.checker.ui.phantomSurfaceAlpha(0.35f)),
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
                                    onShowMessage(resCtx.getString(R.string.partner_s_7ca1e3, e.localizedMessage ?: resCtx.getString(R.string.settings_s_fe6b26)))
                                }
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (hasCustomBg) Color.Black.copy(alpha = 0.35f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = com.phantom.checker.ui.phantomSurfaceAlpha(0.55f))
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = stringResource(R.string.partner_s_91acb0),
                            tint = if (hasCustomBg) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showHeaderBgMenu,
                        onDismissRequest = { showHeaderBgMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.partner_s_91acb0)) },
                            onClick = {
                                showHeaderBgMenu = false
                                try {
                                    headerBgPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } catch (e: Exception) {
                                    onShowMessage(resCtx.getString(R.string.partner_s_7ca1e3, e.localizedMessage ?: resCtx.getString(R.string.settings_s_fe6b26)))
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.partner_s_7ce9ea)) },
                            onClick = {
                                showHeaderBgMenu = false
                                BackgroundImageHelper.removeAssistantHeaderBackground(appCtx)
                                ThemePreferences.bumpBackgroundVersion()
                                headerBgVersion = ThemePreferences.getBackgroundVersion()
                                PhantomSettingsBus.notifyChanged()
                                onShowMessage(resCtx.getString(R.string.partner_s_07aa1c))
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
                                        text = stringResource(R.string.partner_s_ca3ec5, companionDays),
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
                            Text(stringResource(R.string.partner_s_8714cb), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        OutlinedButton(
                            onClick = onOpenPersonaSettings,
                            colors = if (hasCustomBg) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = com.phantom.checker.ui.phantomSurfaceAlpha(0.78f)),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.partner_s_2442f2), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                stringResource(R.string.partner_s_4a7739, intimacy.points)
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
                        text = stringResource(R.string.partner_s_a4b510, confirmedMemoryCount, INTIMACY_WEIGHT_CONFIRMED_MEMORY, chatMsgCount, INTIMACY_WEIGHT_CHAT_MESSAGE, briefingReadCount, INTIMACY_WEIGHT_BRIEFING_READ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 3. 近 7 天事件橫向滑動卡（#357 等高視覺化對齊 ＋ #407 點擊簡報卡可展開閱讀全文）
        phantomItem(4, key = "partner_recent_events") {
            var showFullBriefingDialog by remember { mutableStateOf(false) }
            var fullBriefingText by remember { mutableStateOf<String?>(null) }
            val lastMsgId = remember(settingsTick) { PhantomPrefs.getLastBriefingMessageId(appCtx) }

            LaunchedEffect(showFullBriefingDialog, lastMsgId) {
                if (showFullBriefingDialog && lastMsgId > 0L) {
                    fullBriefingText = withContext(Dispatchers.IO) {
                        runCatching {
                            val dao = db.chatDao()
                            dao.allSessions().firstNotNullOfOrNull { s ->
                                dao.messagesForSession(s.id).firstOrNull { it.id == lastMsgId }?.content
                            }
                        }.getOrNull()
                    }
                    PhantomPrefs.markBriefingReadOnce(appCtx, lastMsgId)
                }
            }

            if (showFullBriefingDialog) {
                val bodyToShow = fullBriefingText?.takeIf { it.isNotBlank() } ?: lastBriefingSummary
                AlertDialog(
                    onDismissRequest = { showFullBriefingDialog = false },
                    title = { Text(stringResource(R.string.partner_s_94ebb4)) },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (bodyToShow.isNotBlank()) {
                                PhantomMarkdownText(
                                    markdown = bodyToShow,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                Text(stringResource(R.string.partner_s_77e16f))
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showFullBriefingDialog = false }) {
                            Text(stringResource(R.string.common_close))
                        }
                    }
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.partner_s_61d637),
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
                            modifier = Modifier
                                .width(204.dp)
                                .then(
                                    if (card.key == "briefing" && lastBriefingSummary.isNotBlank()) {
                                        Modifier.clickable { showFullBriefingDialog = true }
                                    } else {
                                        Modifier
                                    }
                                ),
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
                                    text = card.subtitle.replace("**", ""),
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
                        text = stringResource(R.string.partner_s_0c9f5c),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    val topicOptions = listOf(
                        PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE to stringResource(R.string.assistant_hub_s_28ec1f),
                        PhantomPrefs.PROACTIVE_TOPIC_TODO to stringResource(R.string.assistant_hub_s_58b8bb),
                        PhantomPrefs.PROACTIVE_TOPIC_CONFLICT to stringResource(R.string.partner_s_32a566),
                        PhantomPrefs.PROACTIVE_TOPIC_INTEREST to stringResource(R.string.assistant_hub_s_848bcc),
                        PhantomPrefs.PROACTIVE_TOPIC_REGION to stringResource(R.string.assistant_hub_s_99ed9c)
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
                            text = stringResource(R.string.partner_s_e4e0a7),
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
    val resCtx = LocalContext.current
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
                text = stringResource(R.string.partner_s_81611b, assistantName),
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
                            stringResource(R.string.partner_s_63e6ef, intimacy.title, progressPercent)
                        } else {
                            val remaining = (intimacy.nextThreshold - intimacy.points).coerceAtLeast(0)
                            stringResource(R.string.partner_s_838cdd, remaining, progressPercent)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 我們的旅程（縱向階段清單）
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.partner_s_9c7eab),
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
                        isCurrent -> resCtx.getString(R.string.partner_s_d0a81c)
                        isReached -> resCtx.getString(R.string.partner_s_767bcf)
                        else -> resCtx.getString(R.string.partner_s_c9dc39, (stage.minPoints - intimacy.points).coerceAtLeast(1))
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = com.phantom.checker.ui.phantomSurfaceAlpha(0.45f))
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
                    text = stringResource(R.string.partner_s_9960b7),
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
                            title = stringResource(R.string.partner_s_d24af3, INTIMACY_WEIGHT_CONFIRMED_MEMORY),
                            subtitle = stringResource(R.string.partner_s_f5e9cf),
                            value = stringResource(R.string.partner_s_5d7399, confirmedMemoryCount, confirmedMemoryCount * INTIMACY_WEIGHT_CONFIRMED_MEMORY)
                        )
                        HorizontalDivider()
                        IntimacyBreakdownRow(
                            title = stringResource(R.string.partner_s_c7bcc3, INTIMACY_WEIGHT_CHAT_MESSAGE),
                            subtitle = stringResource(R.string.partner_s_dcedd1),
                            value = stringResource(R.string.partner_s_edba3b, chatMsgCount, chatMsgCount * INTIMACY_WEIGHT_CHAT_MESSAGE)
                        )
                        HorizontalDivider()
                        IntimacyBreakdownRow(
                            title = stringResource(R.string.partner_s_341096, INTIMACY_WEIGHT_BRIEFING_READ),
                            subtitle = stringResource(R.string.partner_s_f572e3),
                            value = stringResource(R.string.partner_s_a719e6, briefingReadCount, briefingReadCount * INTIMACY_WEIGHT_BRIEFING_READ)
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.common_close))
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
