package com.phantom.checker.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.phantom.checker.service.BriefingScheduler
import com.phantom.checker.service.ProactiveEngine
import com.phantom.checker.ui.components.FluidSwitch
import com.phantom.checker.ui.components.PortraitCardSection
import com.phantom.checker.util.EncryptedByokStore
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomPrefs.AssistantPersonalityPreset
import com.phantom.checker.util.PhantomPrefs.AssistantVerbosity
import com.phantom.checker.util.PhantomSettingsBus
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/**
 * #340 「AI 夥伴與主動推播」獨立設定頁：
 * 集中收納早晚簡報排程、主動推播主題與節流勿擾、畫像卡、外部搜尋端點 BYOK、通訊錄整合、連結應用入口與 Persona 快捷。
 */
class AssistantHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PhantomCheckTheme {
                AssistantHubScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AssistantHubScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val glassState = remember { HazeState() }

    var morningEnabled by remember { mutableStateOf(PhantomPrefs.isMorningBriefingEnabled(appCtx)) }
    var morningTime by remember { mutableStateOf(PhantomPrefs.getMorningBriefingTime(appCtx)) }
    var morningContents by remember { mutableStateOf(PhantomPrefs.getMorningBriefingContents(appCtx)) }

    var eveningEnabled by remember { mutableStateOf(PhantomPrefs.isEveningBriefingEnabled(appCtx)) }
    var eveningTime by remember { mutableStateOf(PhantomPrefs.getEveningBriefingTime(appCtx)) }
    var eveningContents by remember { mutableStateOf(PhantomPrefs.getEveningBriefingContents(appCtx)) }

    var activeTopics by remember { mutableStateOf(PhantomPrefs.getProactiveTopics(appCtx)) }
    var dailyCap by remember { mutableFloatStateOf(PhantomPrefs.getProactiveDailyCap(appCtx).toFloat()) }
    var dndEnabled by remember { mutableStateOf(PhantomPrefs.isProactiveDndEnabled(appCtx)) }
    var dndStart by remember { mutableStateOf(PhantomPrefs.getProactiveDndStart(appCtx)) }
    var dndEnd by remember { mutableStateOf(PhantomPrefs.getProactiveDndEnd(appCtx)) }
    var calInsightEnabled by remember { mutableStateOf(PhantomPrefs.isProactiveCalendarInsightEnabled(appCtx)) }
    var calInsightNotifEnabled by remember { mutableStateOf(PhantomPrefs.isProactiveCalendarInsightNotifEnabled(appCtx)) }
    var calInsightWebSearchEnabled by remember { mutableStateOf(PhantomPrefs.isProactiveCalendarWebSearchEnabled(appCtx)) }
    var calInsightIntervalMin by remember { mutableStateOf(PhantomPrefs.getCalendarInsightIntervalMinutes(appCtx)) }
    var memoryAutoExtract by remember { mutableStateOf(PhantomPrefs.isMemoryAutoExtractEnabled(appCtx)) }
    var chatPendingCapsules by remember { mutableStateOf(PhantomPrefs.isChatPendingCapsulesEnabled(appCtx)) }
    var regionKeyword by remember { mutableStateOf(PhantomPrefs.getProactiveRegionKeyword(appCtx)) }

    var extUrl by remember { mutableStateOf(EncryptedByokStore.getExternalSearchBaseUrl(appCtx)) }
    var extModel by remember { mutableStateOf(EncryptedByokStore.getExternalSearchModel(appCtx)) }
    var extKey by remember { mutableStateOf(EncryptedByokStore.getExternalSearchApiKey(appCtx)) }

    var personalityPreset by remember { mutableStateOf(PhantomPrefs.getAssistantPersonalityPreset(appCtx)) }
    var verbosity by remember { mutableStateOf(PhantomPrefs.getAssistantVerbosity(appCtx)) }
    var notifEnabled by remember { mutableStateOf(ProactiveEngine.areNotificationsEnabled(appCtx)) }
    var batteryIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(appCtx)) }
    val lifecycleOwner = ctx as? androidx.lifecycle.LifecycleOwner
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifEnabled = ProactiveEngine.areNotificationsEnabled(appCtx)
                batteryIgnored = isBatteryOptimizationIgnored(appCtx)
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(ctx, "未授予粗略位置權限，請直接手動輸入地區名稱", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val city = withContext(Dispatchers.IO) {
                try {
                    val lm = appCtx.getSystemService(LocationManager::class.java)
                    @Suppress("MissingPermission")
                    val loc = lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: lm?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
                    if (loc != null) {
                        @Suppress("DEPRECATION")
                        val addrs = Geocoder(appCtx, Locale.TAIWAN).getFromLocation(loc.latitude, loc.longitude, 1)
                        addrs?.firstOrNull()?.let { it.adminArea ?: it.locality ?: it.subAdminArea }
                    } else null
                } catch (_: Exception) {
                    null
                }
            }
            if (!city.isNullOrBlank()) {
                regionKeyword = city
                PhantomPrefs.setProactiveRegionKeyword(appCtx, city)
                Toast.makeText(ctx, "已填入地區：$city（僅儲存城市文字）", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, "目前無法取得定位城市，請手動輸入", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val contentOptions = listOf(
        PhantomPrefs.BRIEFING_CONTENT_SCHEDULE to "今日／明日行程",
        PhantomPrefs.BRIEFING_CONTENT_TODO to "待辦延後提醒",
        PhantomPrefs.BRIEFING_CONTENT_IMPORTANT_NOTIFS to "重點通知摘要"
    )

    PhantomBackgroundBox(
        layer = com.phantom.checker.util.BackgroundLayer.GLOBAL,
        glassState = glassState,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                TopAppBar(
                    title = { Text("AI 夥伴與主動推播", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        titleContentColor = MaterialTheme.colorScheme.onBackground,
                        navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        ) { padding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .hazeSource(glassState),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. 早晚個人簡報排程
                item(key = "hub_briefing") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("☀️🌙 早晚個人簡報排程", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "使用 setAlarmClock 精準排程並搭配 15 分鐘零 token 指紋比對；無變動日零 AI 呼叫。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("早間簡報（預設 08:45）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                FluidSwitch(
                                    checked = morningEnabled,
                                    onCheckedChange = {
                                        morningEnabled = it
                                        PhantomPrefs.setMorningBriefingEnabled(appCtx, it)
                                        BriefingScheduler.rescheduleAll(appCtx)
                                        PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }
                            OutlinedTextField(
                                value = morningTime,
                                onValueChange = { morningTime = it },
                                label = { Text("早間簡報時間（HH:mm）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                contentOptions.forEach { (k, label) ->
                                    val sel = k in morningContents
                                    FilterChip(
                                        selected = sel,
                                        onClick = {
                                            morningContents = if (sel) morningContents - k else morningContents + k
                                            PhantomPrefs.setMorningBriefingContents(appCtx, morningContents)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("晚間簡報（預設 22:25）", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                FluidSwitch(
                                    checked = eveningEnabled,
                                    onCheckedChange = {
                                        eveningEnabled = it
                                        PhantomPrefs.setEveningBriefingEnabled(appCtx, it)
                                        BriefingScheduler.rescheduleAll(appCtx)
                                        PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }
                            OutlinedTextField(
                                value = eveningTime,
                                onValueChange = { eveningTime = it },
                                label = { Text("晚間簡報時間（HH:mm）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                contentOptions.forEach { (k, label) ->
                                    val sel = k in eveningContents
                                    FilterChip(
                                        selected = sel,
                                        onClick = {
                                            eveningContents = if (sel) eveningContents - k else eveningContents + k
                                            PhantomPrefs.setEveningBriefingContents(appCtx, eveningContents)
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            if (!notifEnabled) {
                                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = "⚠️ 主動通知未開啟：簡報仍會寫入 AI 助手聊天，但無法顯示於手機通知欄。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                OutlinedButton(
                                    onClick = { ProactiveEngine.openAppNotificationSettings(ctx) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("前往系統通知設定")
                                }
                            }
                            if (!batteryIgnored) {
                                Text(
                                    text = "💡 建議將幻訊加入電池最佳化白名單，避免小米／Samsung／OPPO／vivo 背景限流影響定時簡報。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { openBatteryOptimizationSettings(ctx) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("開啟電池優化白名單設定")
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    PhantomPrefs.setMorningBriefingTime(appCtx, morningTime)
                                    PhantomPrefs.setEveningBriefingTime(appCtx, eveningTime)
                                    BriefingScheduler.rescheduleAll(appCtx)
                                    Toast.makeText(ctx, "已更新早晚簡報排程", Toast.LENGTH_SHORT).show()
                                }) { Text("儲存時間並重排") }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val canNotify = ProactiveEngine.areNotificationsEnabled(appCtx)
                                        notifEnabled = canNotify
                                        ProactiveEngine.deliverBriefing(appCtx, isMorning = true, force = true)
                                        val msg = when {
                                            !canNotify -> "已寫入 AI 助手（通知未開啟，未發送通知欄推播）"
                                            ProactiveEngine.isInDndWindow(appCtx) -> "已寫入 AI 助手（目前為勿擾時段，不彈出通知）"
                                            else -> "已發送測試簡報至 AI 助手與系統通知欄"
                                        }
                                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text("立即試發") }
                            }
                        }
                    }
                }

                // 2. 主動推播主題、每日上限、勿擾時段與地區
                item(key = "hub_proactive_controls") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🔔 主動推播主題、節流與獨立勿擾", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            val topics = listOf(
                                PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE to "行程提醒",
                                PhantomPrefs.PROACTIVE_TOPIC_TODO to "待辦到期",
                                PhantomPrefs.PROACTIVE_TOPIC_CONFLICT to "跨上下文撞期",
                                PhantomPrefs.PROACTIVE_TOPIC_INTEREST to "興趣情報",
                                PhantomPrefs.PROACTIVE_TOPIC_REGION to "地區情報",
                                PhantomPrefs.PROACTIVE_TOPIC_EXTERNAL_SEARCH to "外部情報搜尋"
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                topics.forEach { (key, label) ->
                                    val sel = key in activeTopics
                                    FilterChip(
                                        selected = sel,
                                        onClick = {
                                            PhantomPrefs.setProactiveTopicEnabled(appCtx, key, !sel)
                                            activeTopics = PhantomPrefs.getProactiveTopics(appCtx)
                                            BriefingScheduler.scheduleProactiveMonitorWorker(appCtx)
                                            PhantomSettingsBus.notifyChanged()
                                        },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Text("每日主動推播上限：${dailyCap.roundToInt()} 則（1–20 可調）", style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = dailyCap,
                                onValueChange = { dailyCap = it },
                                onValueChangeFinished = {
                                    PhantomPrefs.setProactiveDailyCap(appCtx, dailyCap.roundToInt())
                                    PhantomSettingsBus.notifyChanged()
                                },
                                valueRange = 1f..20f,
                                steps = 18
                            )

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("主動推播獨立勿擾（期間仍寫入聊天不發通知）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FluidSwitch(
                                    checked = dndEnabled,
                                    onCheckedChange = {
                                        dndEnabled = it
                                        PhantomPrefs.setProactiveDndEnabled(appCtx, it)
                                        PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = dndStart,
                                    onValueChange = {
                                        dndStart = it
                                        PhantomPrefs.setProactiveDndStart(appCtx, it)
                                    },
                                    label = { Text("開始（HH:mm）") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = dndEnd,
                                    onValueChange = {
                                        dndEnd = it
                                        PhantomPrefs.setProactiveDndEnd(appCtx, it)
                                    },
                                    label = { Text("結束（HH:mm）") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Text(
                                "📅 主動日曆情報（CalendarInsightEngine）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("定時讀日曆並主動彙整情報", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FluidSwitch(
                                    checked = calInsightEnabled,
                                    onCheckedChange = {
                                        calInsightEnabled = it
                                        PhantomPrefs.setProactiveCalendarInsightEnabled(appCtx, it)
                                        com.phantom.checker.service.CalendarInsightScheduler.scheduleOrUpdate(appCtx)
                                        PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("產出時發送通知（關＝僅寫入主動聊天室）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FluidSwitch(
                                    checked = calInsightNotifEnabled,
                                    onCheckedChange = {
                                        calInsightNotifEnabled = it
                                        PhantomPrefs.setProactiveCalendarInsightNotifEnabled(appCtx, it)
                                        PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("允許上網查即將到來事件（僅傳標題＋日期 ≤60字）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FluidSwitch(
                                    checked = calInsightWebSearchEnabled,
                                    onCheckedChange = {
                                        calInsightWebSearchEnabled = it
                                        PhantomPrefs.setProactiveCalendarWebSearchEnabled(appCtx, it)
                                        PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }
                            Text(
                                "檢查頻率（套用 WorkManager UPDATE 立即生效）",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf(30, 60, 120, 180).forEach { mins ->
                                    FilterChip(
                                        selected = calInsightIntervalMin == mins,
                                        onClick = {
                                            calInsightIntervalMin = mins
                                            PhantomPrefs.setCalendarInsightIntervalMinutes(appCtx, mins)
                                            com.phantom.checker.service.CalendarInsightScheduler.scheduleOrUpdate(appCtx)
                                            PhantomSettingsBus.notifyChanged()
                                        },
                                        label = { Text(if (mins < 60) "${mins}分" else "${mins / 60}小時", maxLines = 1) }
                                    )
                                }
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Text(
                                "💬 對話自動記憶與連續性",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("對話自動記憶擷取（僅寫入非敏感無衝突，敏感一律進確認卡）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FluidSwitch(
                                    checked = memoryAutoExtract,
                                    onCheckedChange = {
                                        memoryAutoExtract = it
                                        PhantomPrefs.setMemoryAutoExtractEnabled(appCtx, it)
                                        PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("對話頂部未決事項膠囊列與壓縮錨點", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FluidSwitch(
                                    checked = chatPendingCapsules,
                                    onCheckedChange = {
                                        chatPendingCapsules = it
                                        PhantomPrefs.setChatPendingCapsulesEnabled(appCtx, it)
                                        PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = regionKeyword,
                                    onValueChange = {
                                        regionKeyword = it
                                        PhantomPrefs.setProactiveRegionKeyword(appCtx, it)
                                    },
                                    label = { Text("關注地區（例：台北）") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(onClick = {
                                    if (ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    } else {
                                        locationLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                                    }
                                }) {
                                    Text("定位城市")
                                }
                            }
                        }
                    }
                }

                // 3. 「我對你的瞭解」畫像卡
                item(key = "hub_portrait") {
                    PortraitCardSection(
                        glassState = glassState,
                        onShowMessage = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
                    )
                }

                // 4. 外部情報搜尋端點 BYOK（選配 OpenAI 相容端點）
                item(key = "hub_external_search") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🌐 外部情報搜尋端點（選配 BYOK 加密儲存）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                "可選填 OpenAI 相容搜尋端點（如 Perplexity https://api.perplexity.ai / sonar）；未設定時簡報將誠實跳過不報錯。省 token 模式自動停用。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = extUrl,
                                onValueChange = { extUrl = it },
                                label = { Text("Base URL（例：https://api.perplexity.ai）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = extModel,
                                onValueChange = { extModel = it },
                                label = { Text("Model（預設 sonar）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = extKey,
                                onValueChange = { extKey = it },
                                label = { Text("API Key（遮罩顯示）") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = {
                                EncryptedByokStore.setExternalSearchBaseUrl(appCtx, extUrl)
                                EncryptedByokStore.setExternalSearchModel(appCtx, extModel)
                                EncryptedByokStore.setExternalSearchApiKey(appCtx, extKey)
                                Toast.makeText(ctx, "已安全儲存外部搜尋端點設定", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("儲存外部搜尋設定")
                            }
                        }
                    }
                }

                // 5. 通訊錄整合（復用 ContactsIntegrationSection）
                item(key = "hub_contacts") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        ContactsIntegrationSection(
                            onOpenPermissionHub = {
                                ctx.startActivity(Intent(ctx, PermissionHubActivity::class.java))
                            }
                        )
                    }
                }

                // 6. 連結應用程式中心入口 ＋ 助手 Persona 快捷
                item(key = "hub_linked_and_persona") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🔗 連結應用程式與助手個性", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(
                                onClick = { ctx.startActivity(Intent(ctx, LinkedAppsActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("開啟連結應用程式中心（GitHub／Discord／系統能力）")
                            }
                            HorizontalDivider()
                            Text("助手個性風格", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistantPersonalityPreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = personalityPreset == preset,
                                        onClick = {
                                            personalityPreset = preset
                                            PhantomPrefs.setAssistantPersonalityPreset(appCtx, preset)
                                            PhantomSettingsBus.notifyChanged()
                                        },
                                        label = { Text(preset.label) }
                                    )
                                }
                            }
                            Text("回答詳細度", style = MaterialTheme.typography.labelMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AssistantVerbosity.entries.forEach { v ->
                                    FilterChip(
                                        selected = verbosity == v,
                                        onClick = {
                                            verbosity = v
                                            PhantomPrefs.setAssistantVerbosity(appCtx, v)
                                            PhantomSettingsBus.notifyChanged()
                                        },
                                        label = { Text(v.label) }
                                    )
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    ctx.startActivity(
                                        Intent(ctx, SettingsActivity::class.java).apply {
                                            putExtra("highlight_card_key", "memory_assistant")
                                        }
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("前往進階助手改名／換頭像設定")
                            }
                        }
                    }
                }

                // 7. #367 雙門檻決策閾值 ＋ #368 定時任務模板（三步嚮導＋所見即所得預覽）
                item(key = "hub_task_templates_and_decision") {
                    val templates by remember(appCtx) {
                        com.phantom.checker.service.AppDatabaseHolder.db(appCtx).taskTemplateDao().observeAll()
                    }.collectAsState(initial = emptyList())
                    var decisionThreshold by remember {
                        mutableFloatStateOf(PhantomPrefs.getDecisionConfirmThreshold(appCtx).toFloat())
                    }
                    var showWizard by remember { mutableStateOf(false) }
                    var wizardStep by remember { mutableStateOf(1) }
                    var tplName by remember { mutableStateOf("每日重點彙整") }
                    var tplQueryKey by remember { mutableStateOf(com.phantom.checker.data.TaskTemplateEntity.QUERY_MESSAGES_DIGEST) }
                    var tplTriggerType by remember { mutableStateOf(com.phantom.checker.data.TaskTemplateEntity.TRIGGER_DAILY) }
                    var tplTriggerTime by remember { mutableStateOf("09:00") }
                    var tplIntervalMin by remember { mutableStateOf(60) }
                    var tplPolish by remember { mutableStateOf(!PhantomPrefs.isTokenSaverEnabled(appCtx)) }
                    var tplDeliverNotif by remember { mutableStateOf(true) }
                    var previewText by remember { mutableStateOf("") }
                    var deleteTemplateTarget by remember { mutableStateOf<com.phantom.checker.data.TaskTemplateEntity?>(null) }

                    LaunchedEffect(showWizard, wizardStep, tplQueryKey) {
                        if (showWizard && wizardStep == 3) {
                            previewText = "載入即時聚合預覽中…"
                            val agg = com.phantom.checker.service.TaskTemplateEngine.runAggregation(appCtx, tplQueryKey)
                            previewText = agg.fullText
                        }
                    }

                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                "⚖️ 雙門檻自動決策閾值",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "置信閾值：${decisionThreshold.toInt()} 分（低於此門檻或僅 AI 語義無本地訊號一律進確認卡；已回饋修正 ${PhantomPrefs.getDecisionFeedbackTotalCount(appCtx)} 次）",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = decisionThreshold,
                                onValueChange = { decisionThreshold = it },
                                onValueChangeFinished = {
                                    val snapped = (decisionThreshold.toInt() / 5) * 5
                                    decisionThreshold = snapped.toFloat()
                                    PhantomPrefs.setDecisionConfirmThreshold(appCtx, snapped)
                                    PhantomSettingsBus.notifyChanged()
                                },
                                valueRange = 40f..85f,
                                steps = 8
                            )

                            HorizontalDivider()

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "📋 定時任務模板（${templates.size} 個）",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Button(onClick = {
                                    wizardStep = 1
                                    tplName = "每日重點彙整"
                                    tplQueryKey = com.phantom.checker.data.TaskTemplateEntity.QUERY_MESSAGES_DIGEST
                                    tplTriggerType = com.phantom.checker.data.TaskTemplateEntity.TRIGGER_DAILY
                                    tplPolish = !PhantomPrefs.isTokenSaverEnabled(appCtx)
                                    showWizard = true
                                }) {
                                    Text("新增模板")
                                }
                            }
                            Text(
                                "聚合段純本地 SQL/規則零 token，潤飾段可獨立開關（與省 token 模式連動）。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (templates.isEmpty()) {
                                Text(
                                    "尚未建立自訂定時任務模板，點擊「新增模板」體驗三步嚮導與所見即所得預覽。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                templates.forEach { tpl ->
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(tpl.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                                    val freqDesc = when (tpl.triggerType) {
                                                        com.phantom.checker.data.TaskTemplateEntity.TRIGGER_WEEKLY -> "每週 ${tpl.triggerTime}"
                                                        com.phantom.checker.data.TaskTemplateEntity.TRIGGER_INTERVAL -> "每 ${tpl.intervalMinutes} 分鐘"
                                                        else -> "每日 ${tpl.triggerTime}"
                                                    }
                                                    Text(
                                                        "${com.phantom.checker.service.TaskTemplateEngine.queryKeyLabel(tpl.queryKey)} · $freqDesc · ${if (tpl.polishEnabled) "AI潤飾開" else "純聚合零token"}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                FluidSwitch(
                                                    checked = tpl.isEnabled,
                                                    onCheckedChange = { on ->
                                                        scope.launch {
                                                            val updated = tpl.copy(isEnabled = on)
                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                                com.phantom.checker.service.AppDatabaseHolder.db(appCtx).taskTemplateDao().update(updated)
                                                            }
                                                            com.phantom.checker.service.TaskTemplateScheduler.scheduleOrUpdate(appCtx, updated)
                                                        }
                                                    }
                                                )
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                TextButton(onClick = {
                                                    scope.launch {
                                                        com.phantom.checker.service.TaskTemplateEngine.executeTemplate(appCtx, tpl)
                                                        Toast.makeText(ctx, "已試跑「${tpl.name}」並寫入主動訊息聊天室", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Text("立即試跑")
                                                }
                                                TextButton(onClick = { deleteTemplateTarget = tpl }) {
                                                    Text("刪除", color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (showWizard) {
                        AlertDialog(
                            onDismissRequest = { showWizard = false },
                            title = { Text("定時任務模板嚮導（步驟 $wizardStep / 3）") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when (wizardStep) {
                                        1 -> {
                                            Text("① 選擇資料源與名稱（本地 SQL/規則白名單）", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            OutlinedTextField(
                                                value = tplName,
                                                onValueChange = { tplName = it },
                                                label = { Text("模板名稱") },
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                com.phantom.checker.data.TaskTemplateEntity.ALLOWED_QUERY_KEYS.forEach { key ->
                                                    FilterChip(
                                                        selected = tplQueryKey == key,
                                                        onClick = {
                                                            tplQueryKey = key
                                                            tplName = com.phantom.checker.service.TaskTemplateEngine.queryKeyLabel(key)
                                                        },
                                                        label = { Text(com.phantom.checker.service.TaskTemplateEngine.queryKeyLabel(key)) }
                                                    )
                                                }
                                            }
                                        }
                                        2 -> {
                                            Text("② 選擇頻率與潤飾／推送模式", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                listOf(
                                                    com.phantom.checker.data.TaskTemplateEntity.TRIGGER_DAILY to "每日",
                                                    com.phantom.checker.data.TaskTemplateEntity.TRIGGER_WEEKLY to "每週",
                                                    com.phantom.checker.data.TaskTemplateEntity.TRIGGER_INTERVAL to "定時循環"
                                                ).forEach { (t, lbl) ->
                                                    FilterChip(
                                                        selected = tplTriggerType == t,
                                                        onClick = { tplTriggerType = t },
                                                        label = { Text(lbl) }
                                                    )
                                                }
                                            }
                                            if (tplTriggerType == com.phantom.checker.data.TaskTemplateEntity.TRIGGER_INTERVAL) {
                                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    listOf(30, 60, 120, 180).forEach { mins ->
                                                        FilterChip(
                                                            selected = tplIntervalMin == mins,
                                                            onClick = { tplIntervalMin = mins },
                                                            label = { Text("${mins}分") }
                                                        )
                                                    }
                                                }
                                            } else {
                                                OutlinedTextField(
                                                    value = tplTriggerTime,
                                                    onValueChange = { tplTriggerTime = it },
                                                    label = { Text("觸發時間（HH:mm）") },
                                                    singleLine = true,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("AI 潤飾（關閉＝純聚合零 token）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                FluidSwitch(checked = tplPolish, onCheckedChange = { tplPolish = it })
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("同時發送通知（關＝僅寫入主動聊天室）", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                FluidSwitch(checked = tplDeliverNotif, onCheckedChange = { tplDeliverNotif = it })
                                            }
                                        }
                                        else -> {
                                            Text("③ 所見即所得預覽（即時執行本地聚合段，零 token）", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = previewText.ifBlank { "載入中…" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = {
                                    if (wizardStep < 3) {
                                        wizardStep++
                                    } else {
                                        scope.launch {
                                            val entity = com.phantom.checker.data.TaskTemplateEntity(
                                                name = tplName.trim().ifBlank { com.phantom.checker.service.TaskTemplateEngine.queryKeyLabel(tplQueryKey) },
                                                isEnabled = true,
                                                triggerType = tplTriggerType,
                                                triggerTime = tplTriggerTime.trim().ifBlank { "09:00" },
                                                intervalMinutes = tplIntervalMin,
                                                queryKey = tplQueryKey,
                                                polishEnabled = tplPolish,
                                                deliverTo = if (tplDeliverNotif) {
                                                    com.phantom.checker.data.TaskTemplateEntity.DELIVER_PROACTIVE_AND_NOTIF
                                                } else {
                                                    com.phantom.checker.data.TaskTemplateEntity.DELIVER_PROACTIVE_CHAT
                                                }
                                            )
                                            val newId = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                com.phantom.checker.service.AppDatabaseHolder.db(appCtx).taskTemplateDao().insert(entity)
                                            }
                                            com.phantom.checker.service.TaskTemplateScheduler.scheduleOrUpdate(appCtx, entity.copy(id = newId))
                                            showWizard = false
                                            Toast.makeText(ctx, "已建立定時任務模板「${entity.name}」", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Text(if (wizardStep < 3) "下一步" else "確認建立")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    if (wizardStep > 1) wizardStep-- else showWizard = false
                                }) {
                                    Text(if (wizardStep > 1) "上一步" else "取消")
                                }
                            }
                        )
                    }

                    deleteTemplateTarget?.let { target ->
                        AlertDialog(
                            onDismissRequest = { deleteTemplateTarget = null },
                            title = { Text("確認刪除定時任務模板") },
                            text = { Text("確定要刪除「${target.name}」嗎？此操作不可復原並將取消對應排程。") },
                            confirmButton = {
                                TextButton(onClick = {
                                    val toDel = target
                                    deleteTemplateTarget = null
                                    scope.launch {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.phantom.checker.service.AppDatabaseHolder.db(appCtx).taskTemplateDao().deleteById(toDel.id)
                                        }
                                        com.phantom.checker.service.TaskTemplateScheduler.cancel(appCtx, toDel.id)
                                        Toast.makeText(ctx, "已刪除模板「${toDel.name}」", Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Text("確認刪除", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { deleteTemplateTarget = null }) {
                                    Text("取消")
                                }
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}
