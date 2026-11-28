package com.phantom.checker.ui

import com.phantom.checker.ui.components.PhantomMotion
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.network.AiClient
import com.phantom.checker.util.DesktopLinkStore
import androidx.core.app.NotificationManagerCompat
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object OnboardingTargets {
    const val NOTIFICATION_GUIDE = "notification_guide"
    const val STATS_CARD = "stats_card"
    const val SETTINGS_GEAR = "settings_gear"
    const val FIRST_APP_ROW = "first_app_row"
    const val FILTER_CHIPS = "filter_chips"
    const val SELECT_BUTTON = "select_button"
    const val AI_ASSISTANT_TAB = "ai_assistant_tab"
    const val MEMORY_TAB = "memory_tab"
    const val NOTES_TAB = "notes_tab"
    const val MEMO_RULES_AREA = "memo_rules_area"
    const val TODO_ADD = "todo_add"
    const val STATS_TAB = "stats_tab"
    const val PARTNER_TAB = "partner_tab"
    const val SEARCH_PILL = "search_pill"
}

/**
 * #590 B1/B2：新手引導與完成度清單偏好（只存在本機 SharedPreferences，不修改既有 onboarding 狀態）。
 * 完成度為即時推導，不儲存權限本身；只儲存「已看過 / 已關閉」旗標。
 */
object RookieChecklistStore {
    private const val PREFS = "phantom_rookie_ux"
    private const val KEY_INTRO_SEEN = "intro_seen"
    private const val KEY_CHECKLIST_DISMISSED = "checklist_dismissed"
    private const val KEY_SETTINGS_ENTRY_SEEN = "settings_entry_seen"
    private const val KEY_MESSAGES_CARD_DISMISSED = "messages_card_dismissed"

    data class RookieChecklistState(
        val hasNotificationAccess: Boolean,
        val hasBatteryOptimizationOff: Boolean,
        val hasAiKey: Boolean,
        val hasDesktopLink: Boolean
    ) {
        val completedCount: Int
            get() = listOf(hasNotificationAccess, hasBatteryOptimizationOff, hasAiKey, hasDesktopLink).count { it }
        val itemCount: Int get() = 4
        val allDone: Boolean get() = completedCount == itemCount
    }

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isIntroSeen(context: Context): Boolean = prefs(context).getBoolean(KEY_INTRO_SEEN, false)
    fun markIntroSeen(context: Context) = prefs(context).edit().putBoolean(KEY_INTRO_SEEN, true).apply()

    fun isChecklistDismissed(context: Context): Boolean = prefs(context).getBoolean(KEY_CHECKLIST_DISMISSED, false)
    fun dismissChecklist(context: Context) = prefs(context).edit().putBoolean(KEY_CHECKLIST_DISMISSED, true).apply()

    fun isSettingsEntrySeen(context: Context): Boolean = prefs(context).getBoolean(KEY_SETTINGS_ENTRY_SEEN, false)
    fun markSettingsEntrySeen(context: Context) = prefs(context).edit().putBoolean(KEY_SETTINGS_ENTRY_SEEN, true).apply()

    fun isMessagesCardDismissed(context: Context): Boolean = prefs(context).getBoolean(KEY_MESSAGES_CARD_DISMISSED, false)
    fun dismissMessagesCard(context: Context) = prefs(context).edit().putBoolean(KEY_MESSAGES_CARD_DISMISSED, true).apply()

    suspend fun readState(context: Context): RookieChecklistState = withContext(Dispatchers.IO) {
        val appCtx = context.applicationContext
        val listener = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
        }.getOrDefault(false)
        val ai = runCatching { AiClient.getActiveProfile(appCtx) != null }.getOrDefault(false)
        val desktop = runCatching { DesktopLinkStore.getPairedDevices(appCtx).isNotEmpty() }.getOrDefault(false)
        RookieChecklistState(
            hasNotificationAccess = listener,
            hasBatteryOptimizationOff = isBatteryOptimizationIgnored(appCtx),
            hasAiKey = ai,
            hasDesktopLink = desktop
        )
    }

    suspend fun shouldShow(context: Context): Boolean =
        !isChecklistDismissed(context) && !readState(context).allDone
}

/**
 * #590 B2：訊息頁頂部的完成設定進度卡（可關閉，點擊直達設定）。
 */
@Composable
fun RookieSetupProgressCard(
    state: RookieChecklistStore.RookieChecklistState,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSettings)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.rookie_checklist_messages_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.rookie_checklist_dismiss))
                }
            }
            Text(
                text = stringResource(
                    R.string.rookie_checklist_messages_card_body,
                    (state.itemCount - state.completedCount).coerceAtLeast(1)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            androidx.compose.material3.LinearProgressIndicator(
                progress = { state.completedCount.toFloat() / state.itemCount.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = stringResource(R.string.rookie_checklist_progress, state.completedCount, state.itemCount),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * #590 B1/B4：完成設定清單（可逐項直達、必建議與可跳過標示）。全部完成後由呼叫端隱藏。
 */
@Composable
fun RookieChecklistCard(
    state: RookieChecklistStore.RookieChecklistState,
    onOpenItem: (RookieChecklistItem) -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.rookie_checklist_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.rookie_checklist_progress, state.completedCount, state.itemCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.rookie_checklist_dismiss))
                }
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { state.completedCount.toFloat() / state.itemCount.toFloat() },
                modifier = Modifier.fillMaxWidth()
            )
            RookieChecklistRow(
                done = state.hasNotificationAccess,
                title = stringResource(R.string.rookie_checklist_notifications),
                description = stringResource(R.string.rookie_checklist_notifications_desc),
                optional = false
            ) { onOpenItem(RookieChecklistItem.NOTIFICATIONS) }
            RookieChecklistRow(
                done = state.hasBatteryOptimizationOff,
                title = stringResource(R.string.rookie_checklist_battery),
                description = stringResource(R.string.rookie_checklist_battery_desc),
                optional = false
            ) { onOpenItem(RookieChecklistItem.BATTERY) }
            RookieChecklistRow(
                done = state.hasAiKey,
                title = stringResource(R.string.rookie_checklist_ai_key),
                description = stringResource(R.string.rookie_checklist_ai_key_desc),
                optional = true
            ) { onOpenItem(RookieChecklistItem.AI_KEY) }
            RookieChecklistRow(
                done = state.hasDesktopLink,
                title = stringResource(R.string.rookie_checklist_desktop),
                description = stringResource(R.string.rookie_checklist_desktop_desc),
                optional = true
            ) { onOpenItem(RookieChecklistItem.DESKTOP) }
        }
    }
}

/** #590 B1 checklist items; used by both Messages and Settings surfaces. */
enum class RookieChecklistItem { NOTIFICATIONS, BATTERY, AI_KEY, DESKTOP }

@Composable
private fun RookieChecklistRow(
    done: Boolean,
    title: String,
    description: String,
    optional: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (done) "✓" else "○",
            style = MaterialTheme.typography.titleMedium,
            color = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = stringResource(if (optional) R.string.rookie_checklist_optional else R.string.rookie_checklist_required),
            style = MaterialTheme.typography.labelSmall,
            color = if (optional) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * #590 B1：首啟三張價值卡（可略過、不擋主流程）。顯示一次後由呼叫端持久化。
 */
@Composable
fun RookieIntroOverlay(
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    val pages = listOf(
        Triple(R.string.rookie_intro_title_1, R.string.rookie_intro_body_1, "🛡️"),
        Triple(R.string.rookie_intro_title_2, R.string.rookie_intro_body_2, "💬"),
        Triple(R.string.rookie_intro_title_3, R.string.rookie_intro_body_3, "✨")
    )
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSkip) {
                    Text(stringResource(R.string.rookie_intro_skip))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = pages[page].third,
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                text = stringResource(pages[page].first),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Text(
                text = stringResource(pages[page].second),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.widthIn(max = 520.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pages.indices.forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == page) 22.dp else 8.dp, 8.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .background(
                                if (index == page) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page > 0) {
                    OutlinedButton(onClick = { page -= 1 }) {
                        Text(stringResource(R.string.onboarding_s_fd6afc))
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (page < pages.lastIndex) page += 1 else onFinish()
                    }
                ) {
                    Text(stringResource(if (page < pages.lastIndex) R.string.rookie_intro_next else R.string.rookie_intro_start))
                }
            }
            Text(
                text = stringResource(R.string.rookie_intro_counter, page + 1, pages.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/** D6 裁定：主頁教學由 20 步精簡為 8 步（§4.10） */
enum class OnboardingStep(val index: Int) {
    PERMISSION(1),
    SEARCH_PILL(2),
    MESSAGES(3),
    AI_ASSISTANT(4),
    NOTES_TAB(5),
    MEMORY_TAB(6),
    INSIGHT_TAB(7),
    SETTINGS_CENTER(8);

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
    com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_1d638d),
    com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_70cd3b),
    com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_a6e90d),
    com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_b57a5b)
).joinToString("\n")

fun restrictedSettingsOemNote(): String {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_8326c1)
        m.contains("samsung") ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_4728f1)
        m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_ef67c1)
        m.contains("vivo") || m.contains("iqoo") ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_ff779b)
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
                Text(if (expanded) stringResource(R.string.onboarding_s_fd6afc) else stringResource(R.string.onboarding_s_cc3077))
            }
            if (shizukuReady) {
                OutlinedButton(onClick = onShizukuGrant) {
                    Text(stringResource(R.string.onboarding_s_b21584))
                }
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = PhantomMotion.ExpandEnter,
            exit = PhantomMotion.ExpandExit
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
                    Text(stringResource(R.string.onboarding_s_423703))
                }
            }
        }
    }
}

fun oemAutostartGuideText(): String {
    val m = Build.MANUFACTURER.lowercase()
    return when {
        m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_454929)
        m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_e1f863)
        m.contains("vivo") || m.contains("iqoo") ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_f889ff)
        m.contains("samsung") ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_bd1fc3)
        m.contains("huawei") || m.contains("honor") ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_281ab8)
        else ->
            com.phantom.checker.PhantomApplication.str(R.string.onboarding_s_38a87d)
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
            OnboardingStep.SEARCH_PILL -> OnboardingTargets.SEARCH_PILL
            OnboardingStep.AI_ASSISTANT -> OnboardingTargets.AI_ASSISTANT_TAB
            OnboardingStep.NOTES_TAB -> OnboardingTargets.NOTES_TAB
            OnboardingStep.MEMORY_TAB -> OnboardingTargets.MEMORY_TAB
            OnboardingStep.INSIGHT_TAB -> OnboardingTargets.PARTNER_TAB
            OnboardingStep.SETTINGS_CENTER -> OnboardingTargets.SETTINGS_GEAR
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
            OnboardingStep.MESSAGES -> {
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
            OnboardingStep.AI_ASSISTANT -> targetBounds[OnboardingTargets.AI_ASSISTANT_TAB]
            OnboardingStep.NOTES_TAB -> targetBounds[OnboardingTargets.NOTES_TAB]
            OnboardingStep.MEMORY_TAB -> targetBounds[OnboardingTargets.MEMORY_TAB]
            OnboardingStep.INSIGHT_TAB -> targetBounds[OnboardingTargets.PARTNER_TAB]
            OnboardingStep.SETTINGS_CENTER -> targetBounds[OnboardingTargets.SETTINGS_GEAR]
            OnboardingStep.SEARCH_PILL -> null
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
    val holeSpring = remember {
        androidx.compose.animation.core.spring<Float>(
            dampingRatio = 0.85f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        )
    }
    val animLeft by animateFloatAsState(
        targetValue = rawHoleRect?.left ?: 0f,
        animationSpec = holeSpring,
        label = "holeLeft"
    )
    val animTop by animateFloatAsState(
        targetValue = rawHoleRect?.top ?: 0f,
        animationSpec = holeSpring,
        label = "holeTop"
    )
    val animRight by animateFloatAsState(
        targetValue = rawHoleRect?.right ?: 0f,
        animationSpec = holeSpring,
        label = "holeRight"
    )
    val animBottom by animateFloatAsState(
        targetValue = rawHoleRect?.bottom ?: 0f,
        animationSpec = holeSpring,
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
            enter = fadeIn(tween(240, easing = PhantomMotion.EmphasizedDecelerate)) + slideInVertically(
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = 0.82f,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
                ),
                initialOffsetY = { if (placeCardAtTop) -it / 3 else it / 3 }
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
                        stringResource(R.string.onboarding_s_e6af49, step.index, totalSteps) to
                            stringResource(R.string.onboarding_s_2f4039)
                    OnboardingStep.SEARCH_PILL ->
                        stringResource(R.string.onboarding_s_d9f86e, step.index, totalSteps) to
                            stringResource(R.string.onboarding_s_c2d8f9)
                    OnboardingStep.MESSAGES -> {
                        val noMsgNote = if (!hasAnyAppRows) {
                            stringResource(R.string.onboarding_s_28b5c1)
                        } else ""
                        stringResource(R.string.onboarding_s_6198bd, step.index, totalSteps) to
                            stringResource(R.string.onboarding_s_2afa9b, noMsgNote)
                    }
                    OnboardingStep.AI_ASSISTANT ->
                        stringResource(R.string.onboarding_s_d4d7e0, step.index, totalSteps) to
                            stringResource(R.string.onboarding_s_24ccaa)
                    OnboardingStep.NOTES_TAB ->
                        stringResource(R.string.onboarding_s_96848a, step.index, totalSteps) to
                            stringResource(R.string.onboarding_s_d51dad)
                    OnboardingStep.MEMORY_TAB ->
                        stringResource(R.string.onboarding_s_86bd41, step.index, totalSteps) to
                            stringResource(R.string.onboarding_s_95981c)
                    OnboardingStep.INSIGHT_TAB ->
                        stringResource(R.string.onboarding_s_604ffb, step.index, totalSteps) to
                            stringResource(R.string.onboarding_s_941ead)
                    OnboardingStep.SETTINGS_CENTER ->
                        stringResource(R.string.onboarding_s_61c048, step.index, totalSteps) to
                            stringResource(R.string.onboarding_s_c6881c)
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

                if (step == OnboardingStep.SETTINGS_CENTER) {
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
                                    text = if (isBatteryWhitelistOk) stringResource(R.string.onboarding_s_efe0dd) else stringResource(R.string.onboarding_s_739ac4),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (isBatteryWhitelistOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.onboarding_s_d9b828),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!isBatteryWhitelistOk) {
                                OutlinedButton(onClick = onOpenBatterySettings) {
                                    Text(stringResource(R.string.chat_s_bfb145))
                                }
                            }
                        }

                        // 2. OEM 自啟動（誠實手動確認）
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = stringResource(R.string.onboarding_s_92669f),
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
                                    text = stringResource(R.string.onboarding_s_743bd7),
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
                                text = if (isListenerEnabled) stringResource(R.string.onboarding_s_b9f01c) else stringResource(R.string.onboarding_s_7f3bc8),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isListenerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            if (!isListenerEnabled) {
                                OutlinedButton(onClick = onOpenNotificationSettings) {
                                    Text(stringResource(R.string.perm_hub_s_8d2b78))
                                }
                            }
                        }
                        if (!isListenerEnabled) {
                            Text(
                                text = stringResource(R.string.onboarding_s_7c853f),
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
                    if (step != OnboardingStep.SETTINGS_CENTER) {
                        TextButton(onClick = onSkip) {
                            Text(stringResource(R.string.settings_s_3002a7))
                        }
                    } else {
                        OutlinedButton(onClick = onOpenSettings) {
                            Text(stringResource(R.string.chat_s_bfb145))
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (step == OnboardingStep.PERMISSION) {
                            OutlinedButton(onClick = onOpenNotificationSettings) {
                                Text(stringResource(R.string.perm_hub_s_8d2b78))
                            }
                        }
                        if (step == OnboardingStep.SETTINGS_CENTER) {
                            Button(onClick = onFinish) {
                                Text(stringResource(R.string.common_done))
                            }
                        } else {
                            Button(onClick = throttledNext) {
                                Text(stringResource(R.string.settings_s_ea0ef2))
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
