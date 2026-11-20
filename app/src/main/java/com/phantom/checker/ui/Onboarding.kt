package com.phantom.checker.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantom.checker.service.NotificationCollectorService
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.delay

object OnboardingTargets {
    const val NOTIFICATION_GUIDE = "notification_guide"
    const val STATS_CARD = "stats_card"
    const val SETTINGS_GEAR = "settings_gear"
    const val FIRST_APP_ROW = "first_app_row"
    const val FILTER_CHIPS = "filter_chips"
    const val SELECT_BUTTON = "select_button"
    const val AI_ASSISTANT_TAB = "ai_assistant_tab"
    const val NOTES_TAB = "notes_tab"
    const val MEMO_RULES_AREA = "memo_rules_area"
    const val TODO_ADD = "todo_add"
    const val STATS_TAB = "stats_tab"
    const val SEARCH_PILL = "search_pill"
}

enum class OnboardingStep(val index: Int) {
    PERMISSION(1),
    HOME_APP(2),
    LONG_PRESS(3),
    SWIPE_DELETE(4),
    QUICK_REPLY(5),
    MULTI_SELECT(6),
    FILTER_CHIPS(7),
    AI_ASSISTANT(8),
    NOTES_TAB(9),
    MEMO_RULES(10),
    NOTED_TODOED_SOURCE(11),
    STATS_AND_SUMMARY(12),
    TAGS_AND_RULES(13),
    TODO_AND_BATCH(14),
    APPEARANCE_AND_BG(15),
    KEEP_ALIVE(16),
    SEARCH_PILL(17),
    DONE(18);

    companion object {
        fun fromIndex(index: Int): OnboardingStep =
            entries.firstOrNull { it.index == index } ?: PERMISSION
    }
}

fun isBatteryOptimizationIgnored(context: Context): Boolean {
    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

fun openBatteryOptimizationSettings(context: Context) {
    val reqIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(reqIntent)
    } catch (_: Exception) {
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(fallback) }
    }
}

fun openNotificationListenerSettings(context: Context) {
    val addNewTask = context !is Activity
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
            putExtra(
                Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                ComponentName(context.packageName, NotificationCollectorService::class.java.name).flattenToString()
            )
            if (addNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(detailIntent)
            return
        } catch (_: Exception) {
        }
    }
    val listIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        if (addNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(listIntent)
    } catch (_: Exception) {
    }
}

fun openAppDetailsSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:${context.packageName}")
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}

fun restrictedSettingsGuideSteps(): String = listOf(
    "1. 在通知存取頁點一下「幻訊」的灰色開關，會彈出「受限制的設定」提示（Android 13+ 側載安裝會出現；這是系統安全機制，非故障）。",
    "2. 點彈窗內「前往應用程式資訊」。",
    "3. 在應用程式資訊頁找「允許受限制的設定」（右上角 ⋮ 選單內，或頁面最底部直接出現）並點擊允許。",
    "4. 返回通知存取頁，開關已可開啟。"
).joinToString("\n")

fun restrictedSettingsOemNote(): String {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
            "提示：若右上角沒有 ⋮，請滑到頁面最底尋找「允許受限制的設定」。開啟時系統要求按住 10 秒倒數（高敏感權限確認）屬正常現象，完成即可。"
        m.contains("samsung") ->
            "提示：One UI 6.1.1+ 的「自動封鎖程式」預設開啟，可能阻擋側載 App 的特殊權限，可到「設定→安全性與隱私→自動封鎖程式」暫時關閉；部分機型的「允許受限制的設定」直接顯示在應用程式資訊頁底部。"
        m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
            "提示：入口在右上角 ⋮ 或頁面底部；ColorOS／OxygenOS 13–15 版本差異較大，若 OxygenOS 15 找不到入口，可改用下方 Shizuku 一鍵授權。"
        m.contains("vivo") || m.contains("iqoo") ->
            "提示：在受限彈窗點「更多選項」，或應用程式資訊頁 ⋮／頁面底部選「解除設定限制」。"
        else -> ""
    }
}

@Composable
internal fun RestrictedSettingsTroubleshootSection(
    onOpenAppDetails: () -> Unit,
    shizukuReady: Boolean = false,
    onShizukuGrant: () -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "收合疑難排解 ▲" else "開關變灰／無法開啟？")
            }
            if (shizukuReady) {
                OutlinedButton(onClick = onShizukuGrant) {
                    Text("Shizuku 一鍵授權")
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = restrictedSettingsGuideSteps(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val oemNote = restrictedSettingsOemNote()
                if (oemNote.isNotEmpty()) {
                    Text(
                        text = oemNote,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedButton(onClick = onOpenAppDetails) {
                    Text("前往應用程式資訊")
                }
            }
        }
    }
}

fun oemAutostartGuideText(): String {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
            "小米 / Redmi：安全中心 → 授權管理 → 自啟動 → 開啟「幻訊」"
        m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
            "OPPO / realme / OnePlus：手機管家 → 權限隱私 → 自啟動 → 開啟「幻訊」"
        m.contains("vivo") || m.contains("iqoo") ->
            "vivo / iQOO：i 管家 → 應用管理 → 權限管理 → 自啟動 → 開啟「幻訊」"
        m.contains("samsung") ->
            "Samsung：設定 → 應用程式 → 幻訊 → 電池 → 選取「不受限制」"
        m.contains("huawei") || m.contains("honor") ->
            "華為 / 榮耀：手機管家 → 應用啟動管理 → 關閉自動管理並允許自啟動與背景活動"
        else ->
            "請到系統「設定 → 應用程式 → 幻訊 → 電池」允許背景運行與自啟動"
    }
}

@Composable
fun OnboardingOverlay(
    step: OnboardingStep,
    isListenerEnabled: Boolean,
    isBatteryWhitelistOk: Boolean,
    oemAutostartConfirmed: Boolean,
    targetBounds: Map<String, Rect>,
    glassState: HazeState,
    hasAnyAppRows: Boolean = true,
    onOpenNotificationSettings: () -> Unit,
    onOpenAppDetails: () -> Unit = {},
    shizukuReady: Boolean = false,
    onShizukuGrant: () -> Unit = {},
    onOpenBatterySettings: () -> Unit,
    onConfirmOemAutostart: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onNextStep: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    val density = LocalDensity.current
    val expandPx = with(density) { 8.dp.toPx() }
    val cornerPx = with(density) { 12.dp.toPx() }
    val pillHalfWidthPx = with(density) { 90.dp.toPx() }
    val pillHalfHeightPx = with(density) { 16.dp.toPx() }
    val pillCornerPx = with(density) { 16.dp.toPx() }

    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    var overlayHeightPx by remember { mutableFloatStateOf(0f) }
    var cardVisible by remember { mutableStateOf(false) }
    var lastNextClickMs by remember { mutableLongStateOf(0L) }

    val throttledNext: () -> Unit = {
        val now = System.currentTimeMillis()
        if (now - lastNextClickMs >= 500L) {
            lastNextClickMs = now
            onNextStep()
        }
    }

    LaunchedEffect(step) {
        cardVisible = false
        cardVisible = true
    }

    var lastAutoSkippedStep by remember { mutableStateOf<OnboardingStep?>(null) }

    // 僅在權限已授權或必要錨點缺失時單步自動跳轉，避免連鎖跳步
    LaunchedEffect(step, isListenerEnabled, targetBounds) {
        if (lastAutoSkippedStep == step) return@LaunchedEffect
        if (step == OnboardingStep.PERMISSION && isListenerEnabled) {
            lastAutoSkippedStep = step
            onNextStep()
            return@LaunchedEffect
        }
        val requiredTarget = when (step) {
            OnboardingStep.MULTI_SELECT -> OnboardingTargets.SELECT_BUTTON
            OnboardingStep.FILTER_CHIPS,
            OnboardingStep.TAGS_AND_RULES -> OnboardingTargets.FILTER_CHIPS
            OnboardingStep.AI_ASSISTANT -> OnboardingTargets.AI_ASSISTANT_TAB
            OnboardingStep.NOTES_TAB,
            OnboardingStep.NOTED_TODOED_SOURCE -> OnboardingTargets.NOTES_TAB
            OnboardingStep.MEMO_RULES -> OnboardingTargets.MEMO_RULES_AREA
            OnboardingStep.TODO_AND_BATCH -> OnboardingTargets.TODO_ADD
            OnboardingStep.STATS_AND_SUMMARY -> OnboardingTargets.STATS_TAB
            OnboardingStep.APPEARANCE_AND_BG -> OnboardingTargets.SETTINGS_GEAR
            OnboardingStep.SEARCH_PILL -> OnboardingTargets.SEARCH_PILL
            else -> null
        }
        if (requiredTarget != null && targetBounds.isNotEmpty() && !targetBounds.containsKey(requiredTarget)) {
            delay(400L)
            // 雙條件防誤跳：等 400ms 讓分頁切換完成後，再確認 bounds 已量測且目標仍缺席才跳步
            if (targetBounds.isNotEmpty() && !targetBounds.containsKey(requiredTarget)) {
                lastAutoSkippedStep = step
                onNextStep()
            }
        } else if (step != OnboardingStep.PERMISSION) {
            lastAutoSkippedStep = null
        }
    }

    val rawHoleRect: Rect? = remember(step, targetBounds, overlayOrigin) {
        if (step == OnboardingStep.SEARCH_PILL) {
            return@remember targetBounds[OnboardingTargets.SEARCH_PILL]?.let { b ->
                val cx = b.center.x - overlayOrigin.x
                val cy = b.center.y - overlayOrigin.y
                Rect(
                    left = cx - pillHalfWidthPx,
                    top = cy - pillHalfHeightPx,
                    right = cx + pillHalfWidthPx,
                    bottom = cy + pillHalfHeightPx
                )
            }
        }
        val target = when (step) {
            OnboardingStep.PERMISSION -> targetBounds[OnboardingTargets.NOTIFICATION_GUIDE]
            OnboardingStep.QUICK_REPLY -> null
            OnboardingStep.HOME_APP -> {
                val stats = targetBounds[OnboardingTargets.STATS_CARD]
                val appRow = targetBounds[OnboardingTargets.FIRST_APP_ROW]
                when {
                    stats != null && appRow != null -> Rect(
                        left = minOf(stats.left, appRow.left),
                        top = minOf(stats.top, appRow.top),
                        right = maxOf(stats.right, appRow.right),
                        bottom = maxOf(stats.bottom, appRow.bottom)
                    )
                    appRow != null -> appRow
                    else -> stats
                }
            }
            OnboardingStep.LONG_PRESS,
            OnboardingStep.SWIPE_DELETE -> targetBounds[OnboardingTargets.FIRST_APP_ROW]
            OnboardingStep.MULTI_SELECT -> targetBounds[OnboardingTargets.SELECT_BUTTON]
            OnboardingStep.FILTER_CHIPS,
            OnboardingStep.TAGS_AND_RULES -> targetBounds[OnboardingTargets.FILTER_CHIPS]
            OnboardingStep.AI_ASSISTANT -> targetBounds[OnboardingTargets.AI_ASSISTANT_TAB]
            OnboardingStep.NOTES_TAB,
            OnboardingStep.NOTED_TODOED_SOURCE -> targetBounds[OnboardingTargets.NOTES_TAB]
            OnboardingStep.MEMO_RULES -> targetBounds[OnboardingTargets.MEMO_RULES_AREA]
            OnboardingStep.TODO_AND_BATCH -> targetBounds[OnboardingTargets.TODO_ADD]
            OnboardingStep.STATS_AND_SUMMARY -> targetBounds[OnboardingTargets.STATS_TAB]
            OnboardingStep.APPEARANCE_AND_BG -> targetBounds[OnboardingTargets.SETTINGS_GEAR]
            OnboardingStep.SEARCH_PILL,
            OnboardingStep.KEEP_ALIVE,
            OnboardingStep.DONE -> null
        }
        target?.let {
            Rect(
                left = it.left - overlayOrigin.x - expandPx,
                top = it.top - overlayOrigin.y - expandPx,
                right = it.right - overlayOrigin.x + expandPx,
                bottom = it.bottom - overlayOrigin.y + expandPx
            )
        }
    }

    val holeCornerPx = if (step == OnboardingStep.SEARCH_PILL) pillCornerPx else cornerPx
    val hasHole = rawHoleRect != null
    val placeCardAtTop = hasHole && overlayHeightPx > 0f && rawHoleRect!!.center.y > overlayHeightPx * 0.52f
    val animLeft by animateFloatAsState(
        targetValue = rawHoleRect?.left ?: 0f,
        animationSpec = tween(durationMillis = 200),
        label = "holeLeft"
    )
    val animTop by animateFloatAsState(
        targetValue = rawHoleRect?.top ?: 0f,
        animationSpec = tween(durationMillis = 200),
        label = "holeTop"
    )
    val animRight by animateFloatAsState(
        targetValue = rawHoleRect?.right ?: 0f,
        animationSpec = tween(durationMillis = 200),
        label = "holeRight"
    )
    val animBottom by animateFloatAsState(
        targetValue = rawHoleRect?.bottom ?: 0f,
        animationSpec = tween(durationMillis = 200),
        label = "holeBottom"
    )

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = if (isDark) 0.74f else 0.54f)
    val holeOutlineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)

    // 不攔截系統返回鍵（沒有 BackHandler）；Canvas 不攔截觸控
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                overlayOrigin = coords.positionInRoot()
                overlayHeightPx = coords.size.height.toFloat()
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                if (hasHole && animRight > animLeft && animBottom > animTop) {
                    addRoundRect(
                        RoundRect(
                            left = animLeft,
                            top = animTop,
                            right = animRight,
                            bottom = animBottom,
                            cornerRadius = CornerRadius(holeCornerPx, holeCornerPx)
                        )
                    )
                }
            }
            drawPath(
                path = path,
                color = scrimColor
            )
            if (hasHole && animRight > animLeft && animBottom > animTop) {
                drawRoundRect(
                    color = holeOutlineColor,
                    topLeft = Offset(animLeft, animTop),
                    size = androidx.compose.ui.geometry.Size(animRight - animLeft, animBottom - animTop),
                    cornerRadius = CornerRadius(holeCornerPx, holeCornerPx),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                )
            }
        }

        AnimatedVisibility(
            visible = cardVisible,
            enter = fadeIn(tween(200)) + slideInVertically(
                animationSpec = tween(200),
                initialOffsetY = { if (placeCardAtTop) -it / 2 else it / 2 }
            ),
            modifier = Modifier
                .align(if (placeCardAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                .then(
                    if (placeCardAtTop) {
                        Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 20.dp)
                    } else {
                        Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 20.dp)
                    }
                )
        ) {
            PhantomGlassCard(
                glassState = glassState,
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 22.dp,
                contentPadding = 4.dp
            ) {
                androidx.compose.material3.Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                val totalSteps = OnboardingStep.entries.size
                val (title, body) = when (step) {
                    OnboardingStep.PERMISSION ->
                        "步驟 ${step.index} / $totalSteps・開啟權限" to
                            "幻訊需要通知存取權才能即時備份訊息、偵測詐騙風險與自動分類標籤。\n若開關為灰色（受限制的設定），請展開下方疑難排解。"
                    OnboardingStep.HOME_APP ->
                        "步驟 ${step.index} / $totalSteps・訊息分頁與風險卡" to
                            "點選 App 查看對話與訊息；頂部統計與風險卡顯示今日、未讀與詐騙數，並可一鍵進入「詐騙警示中心」。"
                    OnboardingStep.LONG_PRESS -> {
                        if (!hasAnyAppRows) {
                            "步驟 ${step.index} / $totalSteps・訊息操作（目前尚無訊息）" to
                                "長按 App 或訊息可開啟操作選單（星標、釘選、靜音、AI 深度分析、刪除）；進入對話後可向左滑動快速刪除訊息、於底部輸入框快速回覆；篩選或搜尋清單可用「選取」批次處理。目前資料庫尚無訊息，收到通知後即可實際操作，先按「下一步」繼續。"
                        } else {
                            "步驟 ${step.index} / $totalSteps・長按與深度分析" to
                                "長按 App 或訊息可開啟操作選單，進行星標、釘選、靜音、AI 訊息深度分析或刪除。"
                        }
                    }
                    OnboardingStep.SWIPE_DELETE -> {
                        val noMsgNote = if (!targetBounds.containsKey(OnboardingTargets.FIRST_APP_ROW)) {
                            "\n\n（目前尚無訊息，收到通知後即可左滑刪除，請點擊「下一步」繼續）"
                        } else ""
                        "步驟 ${step.index} / $totalSteps・滑動刪除" to
                            "進入對話頁後，將任一訊息向左滑動即可快速刪除（需在「設定 → 訊息與通知」開啟滑動刪除）。$noMsgNote"
                    }
                    OnboardingStep.QUICK_REPLY ->
                        "步驟 ${step.index} / $totalSteps・快速回覆" to
                            "在對話詳情頁底部輸入框可直接回覆訊息（支援通知快速回覆或簡訊），你送出的回覆會顯示在對話右側氣泡並完整保留。"
                    OnboardingStep.MULTI_SELECT ->
                        "步驟 ${step.index} / $totalSteps・多選批次" to
                            "在篩選或搜尋清單點右上角「選取」，可一次批次標記已讀、星標或刪除多則訊息。"
                    OnboardingStep.FILTER_CHIPS ->
                        "步驟 ${step.index} / $totalSteps・固定表頭與分類篩選" to
                            "頂部統計卡、分類篩選列與我的最愛列固定於上方，下方訊息清單可獨立捲動；可依未讀、星標、詐騙、已過濾、已記事、已待辦、App 分類或 AI 標籤快速過濾。"
                    OnboardingStep.AI_ASSISTANT ->
                        "步驟 ${step.index} / $totalSteps・AI 助手" to
                            "切換到「AI 助手」分頁，支援自然語言查訊、建立自動記事規則、常用快速指令、昨日簡報與截圖圖片分析。"
                    OnboardingStep.NOTES_TAB ->
                        "步驟 ${step.index} / $totalSteps・記事與自動備忘錄" to
                            "切換到「記事」分頁可管理備忘與待辦（支援優先級、重複提醒與編輯）；開啟「自動備忘錄」後，AI 分析訊息時會自動擷取繳費、取貨與約定事項寫入。"
                    OnboardingStep.MEMO_RULES ->
                        "步驟 ${step.index} / $totalSteps・自動記事規則" to
                            "在「記事」分頁的「自動記事規則」管理卡，可手動或以自然語言設定哪些寄件者、App 或關鍵字要自動記為記事／待辦，或永遠不要自動加入。"
                    OnboardingStep.NOTED_TODOED_SOURCE ->
                        "步驟 ${step.index} / $totalSteps・已記事／已待辦與來源回溯" to
                            "首頁「已記事／已待辦」篩選標籤可快速找出已建立備忘的訊息；待辦列可點來源圖示回到原訊息，訊息詳情頁也能一鍵「不再自動記事／待辦」。"
                    OnboardingStep.STATS_AND_SUMMARY ->
                        "步驟 ${step.index} / $totalSteps・統計、摘要與離線佇列" to
                            "「統計」分頁整合每日／每週 AI 摘要、訊息趨勢圖表與詐騙警示入口；斷網未分析的訊息會顯示離線補佇列橫幅並在連線後自動補齊。"
                    OnboardingStep.TAGS_AND_RULES ->
                        "步驟 ${step.index} / $totalSteps・標籤、VIP 與 AI 規則" to
                            "在「設定 → 標籤與 AI 規則」可設定零 token App 分類、自然語言白名單與 VIP 寄件者；AI 卡另提供觸發字數門檻與分發失敗通知開關。"
                    OnboardingStep.TODO_AND_BATCH ->
                        "步驟 ${step.index} / $totalSteps・待辦提醒與批次管理" to
                            "在「記事」分頁可手動「+」新增與編輯待辦提醒（含每日／每週重複與逾期標示）；訊息與搜尋清單同樣支援批次管理。"
                    OnboardingStep.APPEARANCE_AND_BG ->
                        "步驟 ${step.index} / $totalSteps・背景圖片、外觀與分用途模型" to
                            "在「設定 → 外觀與保活」可自訂全域／AI 聊天／對話頁三層背景圖片與卡片底色模式；「多 Provider AI 設定」可為對話、分析與摘要分別指定模型。"
                    OnboardingStep.KEEP_ALIVE ->
                        "步驟 ${step.index} / $totalSteps・保活設定" to
                            "完成以下三項檢查，讓幻訊在背景穩定接收與分析通知："
                    OnboardingStep.SEARCH_PILL ->
                        "步驟 ${step.index} / $totalSteps・下拉搜尋" to
                            "頂部把手可下拉或點擊開啟全畫面訊息搜尋，支援即時文字搜尋訊息／待辦／備忘錄，以及 AI 語意搜尋。"
                    OnboardingStep.DONE ->
                        "步驟 ${step.index} / $totalSteps・你學會了！" to
                            "已掌握四大主分頁（訊息、AI 助手、記事、統計）、下拉搜尋、快速回覆、自動記事規則與已記事／已待辦篩選、背景外觀與保活設定。"
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (step == OnboardingStep.PERMISSION) {
                    Spacer(Modifier.height(6.dp))
                    RestrictedSettingsTroubleshootSection(
                        onOpenAppDetails = onOpenAppDetails,
                        shizukuReady = shizukuReady && !isListenerEnabled,
                        onShizukuGrant = onShizukuGrant
                    )
                }

                if (step == OnboardingStep.KEEP_ALIVE) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. 電池白名單
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isBatteryWhitelistOk) "1. 電池最佳化白名單 ✓ 已加入" else "1. 電池最佳化白名單 ✗ 未加入",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isBatteryWhitelistOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "避免系統休眠後中斷背景監聽",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!isBatteryWhitelistOk) {
                                OutlinedButton(onClick = onOpenBatterySettings) {
                                    Text("前往設定")
                                }
                            }
                        }

                        // 2. OEM 自啟動（誠實手動確認）
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "2. 裝置自啟動權限（系統無法自動偵測，請自行確認後勾選）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = oemAutostartGuideText(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = oemAutostartConfirmed,
                                    onCheckedChange = onConfirmOemAutostart
                                )
                                Text(
                                    text = "我已確認完成自啟動設定",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        // 3. 通知存取狀態
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isListenerEnabled) "3. 通知存取權限 ✓ 已開啟" else "3. 通知存取權限 ✗ 未開啟",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isListenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isListenerEnabled) {
                                OutlinedButton(onClick = onOpenNotificationSettings) {
                                    Text("前往開啟")
                                }
                            }
                        }
                        if (!isListenerEnabled) {
                            Text(
                                text = "若開關為灰色（受限制的設定），請展開下方疑難排解。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            RestrictedSettingsTroubleshootSection(
                                onOpenAppDetails = onOpenAppDetails,
                                shizukuReady = shizukuReady,
                                onShizukuGrant = onShizukuGrant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (step != OnboardingStep.DONE) {
                        TextButton(onClick = onSkip) {
                            Text("跳過")
                        }
                    } else {
                        OutlinedButton(onClick = onOpenSettings) {
                            Text("前往設定")
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (step == OnboardingStep.PERMISSION) {
                            OutlinedButton(onClick = onOpenNotificationSettings) {
                                Text("前往開啟")
                            }
                        }
                        if (step == OnboardingStep.DONE) {
                            Button(onClick = onFinish) {
                                Text("完成")
                            }
                        } else {
                            Button(onClick = throttledNext) {
                                Text("下一步")
                            }
                        }
                    }
                }
                    }
                }
            }
    }
}
}
