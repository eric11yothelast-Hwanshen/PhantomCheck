package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomItem
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * #340 「AI 夥伴與主動推播」獨立設定頁：
 * 集中收納早晚簡報排程、主動推播主題與節流勿擾、畫像卡、外部搜尋端點 BYOK、通訊錄整合、連結應用入口與 Persona 快捷。
 */
class AssistantHubActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
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

private fun parseHourMinute(hhmm: String, defaultHour: Int = 9, defaultMinute: Int = 0): Pair<Int, Int> {
    val parts = hhmm.trim().split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: defaultHour
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: defaultMinute
    return hour to minute
}

private fun showSystemTimePicker(
    ctx: android.content.Context,
    currentHHmm: String,
    defaultHour: Int = 9,
    defaultMinute: Int = 0,
    onPicked: (String) -> Unit
) {
    val (initHour, initMinute) = parseHourMinute(currentHHmm, defaultHour, defaultMinute)
    val is24Hour = android.text.format.DateFormat.is24HourFormat(ctx)
    android.app.TimePickerDialog(
        ctx,
        { _, hourOfDay, minute ->
            onPicked(String.format(Locale.US, "%02d:%02d", hourOfDay, minute))
        },
        initHour,
        initMinute,
        is24Hour
    ).show()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AssistantHubScreen(onBack: () -> Unit) {
    val resCtx = LocalContext.current
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
    var briefingDailyCap by remember { mutableFloatStateOf(PhantomPrefs.getBriefingDailyCap(appCtx).toFloat()) }
    var reminderLeadMin by remember { mutableStateOf(PhantomPrefs.getProactiveReminderLeadMinutes(appCtx)) }
    var allDayRemindTime by remember { mutableStateOf(PhantomPrefs.getProactiveAllDayRemindTime(appCtx)) }
    var monitorIntervalMin by remember { mutableStateOf(PhantomPrefs.getProactiveMonitorIntervalMinutes(appCtx)) }
    var dedupWindowMin by remember { mutableStateOf(PhantomPrefs.getProactiveDedupWindowMinutes(appCtx)) }
    var dndEnabled by remember { mutableStateOf(PhantomPrefs.isProactiveDndEnabled(appCtx)) }
    var dndStart by remember { mutableStateOf(PhantomPrefs.getProactiveDndStart(appCtx)) }
    var dndEnd by remember { mutableStateOf(PhantomPrefs.getProactiveDndEnd(appCtx)) }
    var calInsightEnabled by remember { mutableStateOf(PhantomPrefs.isProactiveCalendarInsightEnabled(appCtx)) }
    var calInsightNotifEnabled by remember { mutableStateOf(PhantomPrefs.isProactiveCalendarInsightNotifEnabled(appCtx)) }
    var calInsightWebSearchEnabled by remember { mutableStateOf(PhantomPrefs.isProactiveCalendarWebSearchEnabled(appCtx)) }
    var calInsightIntervalMin by remember { mutableStateOf(PhantomPrefs.getCalendarInsightIntervalMinutes(appCtx)) }
    var memoryAutoExtract by remember { mutableStateOf(PhantomPrefs.isMemoryAutoExtractEnabled(appCtx)) }
    var diaryAnnotationInSaver by remember { mutableStateOf(PhantomPrefs.isDiaryAnnotationInSaverEnabled(appCtx)) }
    var chatPendingCapsules by remember { mutableStateOf(PhantomPrefs.isChatPendingCapsulesEnabled(appCtx)) }
    var regionKeyword by remember { mutableStateOf(PhantomPrefs.getProactiveRegionKeyword(appCtx)) }

    var extUrl by remember { mutableStateOf(EncryptedByokStore.getExternalSearchBaseUrl(appCtx)) }
    var extModel by remember { mutableStateOf(EncryptedByokStore.getExternalSearchModel(appCtx)) }
    var extKey by remember { mutableStateOf(EncryptedByokStore.getExternalSearchApiKey(appCtx)) }

    var personalityPreset by remember { mutableStateOf(PhantomPrefs.getAssistantPersonalityPreset(appCtx)) }
    var verbosity by remember { mutableStateOf(PhantomPrefs.getAssistantVerbosity(appCtx)) }
    var notifEnabled by remember { mutableStateOf(ProactiveEngine.areNotificationsEnabled(appCtx)) }
    var batteryIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(appCtx)) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                notifEnabled = ProactiveEngine.areNotificationsEnabled(appCtx)
                batteryIgnored = isBatteryOptimizationIgnored(appCtx)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(ctx, resCtx.getString(R.string.assistant_hub_s_74c529), Toast.LENGTH_SHORT).show()
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
                Toast.makeText(ctx, resCtx.getString(R.string.assistant_hub_s_d77963, city), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(ctx, resCtx.getString(R.string.assistant_hub_s_052f3b), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val contentOptions = listOf(
        PhantomPrefs.BRIEFING_CONTENT_SCHEDULE to stringResource(R.string.assistant_hub_s_919d34),
        PhantomPrefs.BRIEFING_CONTENT_TODO to stringResource(R.string.assistant_hub_s_fe0250),
        PhantomPrefs.BRIEFING_CONTENT_IMPORTANT_NOTIFS to stringResource(R.string.assistant_hub_s_24612a)
    )

    PhantomBackgroundBox(
        layer = com.phantom.checker.util.BackgroundLayer.GLOBAL,
        glassState = glassState,
        modifier = Modifier.fillMaxSize()
    ) {
        val hubListState = androidx.compose.foundation.lazy.rememberLazyListState()
        val hubScope = rememberCoroutineScope()
        com.phantom.checker.ui.components.PhantomPageScaffold(
            title = stringResource(R.string.catalog_assistant_hub_title),
            onBack = onBack,
            topBarStyle = com.phantom.checker.ui.components.PhantomTopBarStyle.Medium,
            containerColor = Color.Transparent
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                val anchors = remember {
                    listOf(
                        resCtx.getString(R.string.chat_s_8aa9b3) to 0,
                        resCtx.getString(R.string.assistant_hub_s_c3154c) to 1,
                        resCtx.getString(R.string.assistant_hub_s_598b36) to 2,
                        resCtx.getString(R.string.assistant_hub_s_cfa756) to 3,
                        resCtx.getString(R.string.assistant_hub_s_25e5e0) to 4,
                        resCtx.getString(R.string.assistant_hub_s_5ee95c) to 5,
                        resCtx.getString(R.string.assistant_hub_s_06d0f3) to 6
                    )
                }
                androidx.compose.foundation.lazy.LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(anchors.size) { idx ->
                        val (label, targetIdx) = anchors[idx]
                        androidx.compose.material3.AssistChip(
                            onClick = { hubScope.launch { hubListState.animateScrollToItem(targetIdx) } },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
                LazyColumn(
                    state = hubListState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .hazeSource(glassState),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                // 1. 早晚個人簡報排程
                phantomItem(0, key = "hub_briefing") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.assistant_hub_s_bee195), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.assistant_hub_s_6c23c2),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.assistant_hub_s_ce14ad), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                                label = { Text(stringResource(R.string.assistant_hub_s_40fa87)) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showSystemTimePicker(ctx, morningTime, defaultHour = 8, defaultMinute = 0) { picked ->
                                            morningTime = picked
                                        }
                                    }) {
                                        Text("🕒")
                                    }
                                },
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
                                Text(stringResource(R.string.assistant_hub_s_d639b0), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
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
                                label = { Text(stringResource(R.string.assistant_hub_s_dd2f0c)) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showSystemTimePicker(ctx, eveningTime, defaultHour = 21, defaultMinute = 0) { picked ->
                                            eveningTime = picked
                                        }
                                    }) {
                                        Text("🕒")
                                    }
                                },
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
                                    text = stringResource(R.string.assistant_hub_s_b4ff6a),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                                OutlinedButton(
                                    onClick = { ProactiveEngine.openAppNotificationSettings(ctx) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.settings_s_e9a953))
                                }
                            }
                            if (!batteryIgnored) {
                                Text(
                                    text = stringResource(R.string.assistant_hub_s_901722),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.warning
                                )
                                OutlinedButton(
                                    onClick = { openBatteryOptimizationSettings(ctx) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.assistant_hub_s_b89186))
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    PhantomPrefs.setMorningBriefingTime(appCtx, morningTime)
                                    PhantomPrefs.setEveningBriefingTime(appCtx, eveningTime)
                                    BriefingScheduler.rescheduleAll(appCtx)
                                    Toast.makeText(ctx, resCtx.getString(R.string.assistant_hub_s_57ed3c), Toast.LENGTH_SHORT).show()
                                }) { Text(stringResource(R.string.assistant_hub_s_ae36ac)) }
                                OutlinedButton(onClick = {
                                    scope.launch {
                                        val canNotify = ProactiveEngine.areNotificationsEnabled(appCtx)
                                        notifEnabled = canNotify
                                        ProactiveEngine.deliverBriefing(appCtx, isMorning = true, force = true)
                                        val msg = when {
                                            !canNotify -> resCtx.getString(R.string.assistant_hub_s_bfe543)
                                            ProactiveEngine.isInDndWindow(appCtx) -> resCtx.getString(R.string.assistant_hub_s_137e52)
                                            else -> resCtx.getString(R.string.assistant_hub_s_0f6fa0)
                                        }
                                        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }) { Text(stringResource(R.string.assistant_hub_s_1e1e6e)) }
                            }
                        }
                    }
                }

                // 2. 主動推播主題、每日上限、勿擾時段與地區
                phantomItem(1, key = "hub_proactive_controls") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.assistant_hub_s_43d5ae), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            val topics = listOf(
                                PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE to stringResource(R.string.assistant_hub_s_28ec1f),
                                PhantomPrefs.PROACTIVE_TOPIC_TODO to stringResource(R.string.assistant_hub_s_58b8bb),
                                PhantomPrefs.PROACTIVE_TOPIC_CONFLICT to stringResource(R.string.assistant_hub_s_accab2),
                                PhantomPrefs.PROACTIVE_TOPIC_INTEREST to stringResource(R.string.assistant_hub_s_848bcc),
                                PhantomPrefs.PROACTIVE_TOPIC_REGION to stringResource(R.string.assistant_hub_s_99ed9c),
                                PhantomPrefs.PROACTIVE_TOPIC_EXTERNAL_SEARCH to stringResource(R.string.assistant_hub_s_003c97)
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

                            Text(stringResource(R.string.assistant_hub_s_3c3b8f, dailyCap.roundToInt()), style = MaterialTheme.typography.bodySmall)
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

                            Text(stringResource(R.string.assistant_hub_s_fa1dac, briefingDailyCap.roundToInt()), style = MaterialTheme.typography.bodySmall)
                            Slider(
                                value = briefingDailyCap,
                                onValueChange = { briefingDailyCap = it },
                                onValueChangeFinished = {
                                    PhantomPrefs.setBriefingDailyCap(appCtx, briefingDailyCap.roundToInt())
                                    PhantomSettingsBus.notifyChanged()
                                },
                                valueRange = 1f..10f,
                                steps = 8
                            )

                            Text(stringResource(R.string.assistant_hub_s_ac6e23), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 30, 60).forEach { mins ->
                                    FilterChip(
                                        selected = reminderLeadMin == mins,
                                        onClick = {
                                            reminderLeadMin = mins
                                            PhantomPrefs.setProactiveReminderLeadMinutes(appCtx, mins)
                                            PhantomSettingsBus.notifyChanged()
                                        },
                                        label = { Text(stringResource(R.string.assistant_hub_s_409909, mins), style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = allDayRemindTime,
                                onValueChange = {
                                    allDayRemindTime = it
                                    PhantomPrefs.setProactiveAllDayRemindTime(appCtx, it)
                                    PhantomSettingsBus.notifyChanged()
                                },
                                label = { Text(stringResource(R.string.assistant_hub_s_68d2d3)) },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        showSystemTimePicker(ctx, allDayRemindTime, defaultHour = 9, defaultMinute = 0) { picked ->
                                            allDayRemindTime = picked
                                            PhantomPrefs.setProactiveAllDayRemindTime(appCtx, picked)
                                            PhantomSettingsBus.notifyChanged()
                                        }
                                    }) {
                                        Text("🕒")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(stringResource(R.string.assistant_hub_s_4fe14e), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 30, 60).forEach { mins ->
                                    FilterChip(
                                        selected = monitorIntervalMin == mins,
                                        onClick = {
                                            monitorIntervalMin = mins
                                            PhantomPrefs.setProactiveMonitorIntervalMinutes(appCtx, mins)
                                            BriefingScheduler.scheduleProactiveMonitorWorker(appCtx)
                                            PhantomSettingsBus.notifyChanged()
                                        },
                                        label = { Text(stringResource(R.string.assistant_hub_s_409909, mins), style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            Text(stringResource(R.string.assistant_hub_s_e1a78b), style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(10, 30, 60).forEach { mins ->
                                    FilterChip(
                                        selected = dedupWindowMin == mins,
                                        onClick = {
                                            dedupWindowMin = mins
                                            PhantomPrefs.setProactiveDedupWindowMinutes(appCtx, mins)
                                            PhantomSettingsBus.notifyChanged()
                                        },
                                        label = { Text(stringResource(R.string.assistant_hub_s_409909, mins), style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }

                            val (upCount, downCount) = remember { PhantomPrefs.getProactiveFeedbackCounts(appCtx) }
                            if (upCount > 0 || downCount > 0) {
                                Text(
                                    text = stringResource(R.string.assistant_hub_s_529e85, upCount, downCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.assistant_hub_s_81852a), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                                    label = { Text(stringResource(R.string.assistant_hub_s_32ac23)) },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            showSystemTimePicker(ctx, dndStart, defaultHour = 22, defaultMinute = 0) { picked ->
                                                dndStart = picked
                                                PhantomPrefs.setProactiveDndStart(appCtx, picked)
                                            }
                                        }) {
                                            Text("🕒")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = dndEnd,
                                    onValueChange = {
                                        dndEnd = it
                                        PhantomPrefs.setProactiveDndEnd(appCtx, it)
                                    },
                                    label = { Text(stringResource(R.string.assistant_hub_s_51d928)) },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = {
                                            showSystemTimePicker(ctx, dndEnd, defaultHour = 7, defaultMinute = 0) { picked ->
                                                dndEnd = picked
                                                PhantomPrefs.setProactiveDndEnd(appCtx, picked)
                                            }
                                        }) {
                                            Text("🕒")
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Text(
                                stringResource(R.string.assistant_hub_s_5bec5d),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.assistant_hub_s_67ca0e), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                                Text(stringResource(R.string.assistant_hub_s_c24054), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                                Text(stringResource(R.string.assistant_hub_s_1d23df), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                                stringResource(R.string.assistant_hub_s_3324cd),
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
                                        label = { Text(if (mins < 60) stringResource(R.string.assistant_hub_s_0ac3cd, mins) else stringResource(R.string.assistant_hub_s_130374, mins / 60), maxLines = 1) }
                                    )
                                }
                            }

                            HorizontalDivider(Modifier.padding(vertical = 4.dp))

                            Text(
                                stringResource(R.string.assistant_hub_s_8ae5e7),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.assistant_hub_s_ec53ad), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                                Text(stringResource(R.string.assistant_hub_s_a89a9f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FluidSwitch(
                                    checked = chatPendingCapsules,
                                    onCheckedChange = {
                                        chatPendingCapsules = it
                                        PhantomPrefs.setChatPendingCapsulesEnabled(appCtx, it)
                                        PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.assistant_hub_s_953dc5), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                FluidSwitch(
                                    checked = diaryAnnotationInSaver,
                                    onCheckedChange = {
                                        diaryAnnotationInSaver = it
                                        PhantomPrefs.setDiaryAnnotationInSaverEnabled(appCtx, it)
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
                                    label = { Text(stringResource(R.string.assistant_hub_s_9ca5db)) },
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
                                    Text(stringResource(R.string.assistant_hub_s_1b8790))
                                }
                            }
                        }
                    }
                }

                // 3. 「我對你的瞭解」畫像卡
                phantomItem(2, key = "hub_portrait") {
                    PortraitCardSection(
                        glassState = glassState,
                        onShowMessage = { msg -> Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show() }
                    )
                }

                // 4. 外部情報搜尋端點 BYOK（選配 OpenAI 相容端點）
                phantomItem(3, key = "hub_external_search") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(stringResource(R.string.assistant_hub_s_b86e98), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(
                                stringResource(R.string.assistant_hub_s_994bd7),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = extUrl,
                                onValueChange = { extUrl = it },
                                label = { Text(stringResource(R.string.assistant_hub_s_78636f)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = extModel,
                                onValueChange = { extModel = it },
                                label = { Text(stringResource(R.string.assistant_hub_s_63b073)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = extKey,
                                onValueChange = { extKey = it },
                                label = { Text(stringResource(R.string.assistant_hub_s_8b7c08)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(onClick = {
                                EncryptedByokStore.setExternalSearchBaseUrl(appCtx, extUrl)
                                EncryptedByokStore.setExternalSearchModel(appCtx, extModel)
                                EncryptedByokStore.setExternalSearchApiKey(appCtx, extKey)
                                Toast.makeText(ctx, resCtx.getString(R.string.assistant_hub_s_025c4f), Toast.LENGTH_SHORT).show()
                            }) {
                                Text(stringResource(R.string.assistant_hub_s_c2a89c))
                            }
                        }
                    }
                }

                // 5. 通訊錄整合（復用 ContactsIntegrationSection）
                phantomItem(4, key = "hub_contacts") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        ContactsIntegrationSection(
                            onOpenPermissionHub = {
                                ctx.startActivity(Intent(ctx, PermissionHubActivity::class.java))
                            }
                        )
                    }
                }

                // 6. 連結應用程式中心入口 ＋ 助手 Persona 快捷
                phantomItem(5, key = "hub_linked_and_persona") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.assistant_hub_s_7b7458), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            OutlinedButton(
                                onClick = { ctx.startActivity(Intent(ctx, LinkedAppsActivity::class.java)) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.assistant_hub_s_1b083d))
                            }
                            HorizontalDivider()
                            Text(stringResource(R.string.assistant_hub_s_15c312), style = MaterialTheme.typography.labelMedium)
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
                            Text(stringResource(R.string.settings_s_c3194a), style = MaterialTheme.typography.labelMedium)
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
                                Text(stringResource(R.string.assistant_hub_s_fbc15e))
                            }
                        }
                    }
                }

                // 7. #367 雙門檻決策閾值 ＋ #368 定時任務模板（三步嚮導＋所見即所得預覽）
                phantomItem(6, key = "hub_task_templates_and_decision") {
                    val templates by remember(appCtx) {
                        com.phantom.checker.service.AppDatabaseHolder.db(appCtx).taskTemplateDao().observeAll()
                    }.collectAsState(initial = emptyList())
                    var decisionThreshold by remember {
                        mutableFloatStateOf(PhantomPrefs.getDecisionConfirmThreshold(appCtx).toFloat())
                    }
                    var showWizard by remember { mutableStateOf(false) }
                    var wizardStep by remember { mutableStateOf(1) }
                    var tplName by remember { mutableStateOf(resCtx.getString(R.string.assistant_hub_s_7d64fb)) }
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
                            previewText = resCtx.getString(R.string.assistant_hub_s_621855)
                            val agg = com.phantom.checker.service.TaskTemplateEngine.runAggregation(appCtx, tplQueryKey)
                            previewText = agg.fullText
                        }
                    }

                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                stringResource(R.string.assistant_hub_s_ae91c4),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.assistant_hub_s_0b24cd, decisionThreshold.toInt(), PhantomPrefs.getDecisionFeedbackTotalCount(appCtx)),
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
                                    stringResource(R.string.assistant_hub_s_e49eae, templates.size),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Button(onClick = {
                                    wizardStep = 1
                                    tplName = resCtx.getString(R.string.assistant_hub_s_7d64fb)
                                    tplQueryKey = com.phantom.checker.data.TaskTemplateEntity.QUERY_MESSAGES_DIGEST
                                    tplTriggerType = com.phantom.checker.data.TaskTemplateEntity.TRIGGER_DAILY
                                    tplPolish = !PhantomPrefs.isTokenSaverEnabled(appCtx)
                                    showWizard = true
                                }) {
                                    Text(stringResource(R.string.assistant_hub_s_24a65b))
                                }
                            }
                            Text(
                                stringResource(R.string.assistant_hub_s_ef35f7),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (templates.isEmpty()) {
                                Text(
                                    stringResource(R.string.assistant_hub_s_b02e27),
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
                                                        com.phantom.checker.data.TaskTemplateEntity.TRIGGER_WEEKLY -> resCtx.getString(R.string.assistant_hub_s_95dae0, tpl.triggerTime)
                                                        com.phantom.checker.data.TaskTemplateEntity.TRIGGER_INTERVAL -> resCtx.getString(R.string.assistant_hub_s_81c947, tpl.intervalMinutes)
                                                        else -> resCtx.getString(R.string.assistant_hub_s_e188a6, tpl.triggerTime)
                                                    }
                                                    Text(
                                                        resCtx.getString(R.string.assistant_hub_s_717be1, com.phantom.checker.service.TaskTemplateEngine.queryKeyLabel(tpl.queryKey), freqDesc, if (tpl.polishEnabled) resCtx.getString(R.string.assistant_hub_s_3cdcda) else resCtx.getString(R.string.assistant_hub_s_871fdc)),
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
                                                        Toast.makeText(ctx, resCtx.getString(R.string.assistant_hub_s_342715, tpl.name), Toast.LENGTH_SHORT).show()
                                                    }
                                                }) {
                                                    Text(resCtx.getString(R.string.assistant_hub_s_970fbe))
                                                }
                                                TextButton(onClick = { deleteTemplateTarget = tpl }) {
                                                    Text(resCtx.getString(R.string.common_delete), color = MaterialTheme.colorScheme.error)
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
                            title = { Text(stringResource(R.string.assistant_hub_s_0621c2, wizardStep)) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    when (wizardStep) {
                                        1 -> {
                                            Text(stringResource(R.string.assistant_hub_s_f7a2f3), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            OutlinedTextField(
                                                value = tplName,
                                                onValueChange = { tplName = it },
                                                label = { Text(stringResource(R.string.assistant_hub_s_c9b6e0)) },
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
                                            Text(stringResource(R.string.assistant_hub_s_7dc716), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                listOf(
                                                    com.phantom.checker.data.TaskTemplateEntity.TRIGGER_DAILY to stringResource(R.string.assistant_hub_s_08d65b),
                                                    com.phantom.checker.data.TaskTemplateEntity.TRIGGER_WEEKLY to stringResource(R.string.chat_s_d6847a),
                                                    com.phantom.checker.data.TaskTemplateEntity.TRIGGER_INTERVAL to stringResource(R.string.assistant_hub_s_3d3c93)
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
                                                            label = { Text(stringResource(R.string.assistant_hub_s_0ac3cd, mins)) }
                                                        )
                                                    }
                                                }
                                            } else {
                                                OutlinedTextField(
                                                    value = tplTriggerTime,
                                                    onValueChange = { tplTriggerTime = it },
                                                    label = { Text(stringResource(R.string.assistant_hub_s_4cfc16)) },
                                                    singleLine = true,
                                                    trailingIcon = {
                                                        IconButton(onClick = {
                                                            showSystemTimePicker(ctx, tplTriggerTime, defaultHour = 9, defaultMinute = 0) { picked ->
                                                                tplTriggerTime = picked
                                                            }
                                                        }) {
                                                            Text("🕒")
                                                        }
                                                    },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(stringResource(R.string.assistant_hub_s_155188), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                FluidSwitch(checked = tplPolish, onCheckedChange = { tplPolish = it })
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(stringResource(R.string.assistant_hub_s_1b7282), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                                FluidSwitch(checked = tplDeliverNotif, onCheckedChange = { tplDeliverNotif = it })
                                            }
                                        }
                                        else -> {
                                            Text(stringResource(R.string.assistant_hub_s_d94608), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = previewText.ifBlank { resCtx.getString(R.string.common_loading) },
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
                                            Toast.makeText(ctx, resCtx.getString(R.string.assistant_hub_s_9b93b8, entity.name), Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    Text(if (wizardStep < 3) stringResource(R.string.settings_s_ea0ef2) else stringResource(R.string.assistant_hub_s_08e150))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    if (wizardStep > 1) wizardStep-- else showWizard = false
                                }) {
                                    Text(if (wizardStep > 1) stringResource(R.string.assistant_hub_s_75ef12) else stringResource(R.string.common_cancel))
                                }
                            }
                        )
                    }

                    deleteTemplateTarget?.let { target ->
                        AlertDialog(
                            onDismissRequest = { deleteTemplateTarget = null },
                            title = { Text(stringResource(R.string.assistant_hub_s_b70d26)) },
                            text = { Text(stringResource(R.string.assistant_hub_s_146bda, target.name)) },
                            confirmButton = {
                                TextButton(onClick = {
                                    val toDel = target
                                    deleteTemplateTarget = null
                                    scope.launch {
                                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                            com.phantom.checker.service.AppDatabaseHolder.db(appCtx).taskTemplateDao().deleteById(toDel.id)
                                        }
                                        com.phantom.checker.service.TaskTemplateScheduler.cancel(appCtx, toDel.id)
                                        Toast.makeText(ctx, resCtx.getString(R.string.assistant_hub_s_1226d6, toDel.name), Toast.LENGTH_SHORT).show()
                                    }
                                }) {
                                    Text(stringResource(R.string.common_confirm_delete), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { deleteTemplateTarget = null }) {
                                    Text(stringResource(R.string.common_cancel))
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
}
