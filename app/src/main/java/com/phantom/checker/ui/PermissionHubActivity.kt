package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomItem
import android.Manifest
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.phantom.checker.util.CalendarBridge
import com.phantom.checker.util.ContactBridge
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

/**
 * #340 權限授予中心（集中管理全部權限，Shizuku 除外）：
 * - 每項顯示即時狀態（onResume 全量重查）、一句用途說明、直達系統設定頁／權限對話框之快捷按鈕。
 * - 所有系統設定頁 ACTION 皆包 try-catch＋App 詳情頁兜底＋toast，確保小米／三星／OPPO／vivo 不閃退。
 * - Android 13+ 側載「受限制的設定」沿用 #288 restrictedSettingsOemNote() 廠牌引導文案。
 */
class PermissionHubActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PhantomCheckTheme {
                PermissionHubScreen(onBack = { finish() })
            }
        }
    }
}

private fun launchIntentWithAppDetailsFallback(ctx: Context, intent: Intent) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    } catch (_: Exception) {
    }
    try {
        ctx.startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", ctx.packageName, null)
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        return
    } catch (_: Exception) {
    }
    Toast.makeText(ctx, com.phantom.checker.PhantomApplication.str(R.string.perm_hub_open_settings_failed), Toast.LENGTH_SHORT).show()
}

private fun checkExactAlarmGranted(ctx: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.canScheduleExactAlarms() == true
    } else {
        true
    }
}

private fun checkUsageAccessGranted(ctx: Context): Boolean =
    com.phantom.checker.util.UsageStatsBridge.hasUsageStatsPermission(ctx)

private fun checkBatteryUnrestricted(ctx: Context): Boolean {
    return try {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        pm.isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (_: Exception) {
        false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionHubScreen(onBack: () -> Unit) {
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val glassState = remember { HazeState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    fun isPostNotifGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true

    fun isSmsGranted(): Boolean =
        ContextCompat.checkSelfPermission(appCtx, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    var postNotifGranted by remember { mutableStateOf(isPostNotifGranted()) }
    var notifListenerGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName))
    }
    var smsGranted by remember { mutableStateOf(isSmsGranted()) }
    var contactsGranted by remember { mutableStateOf(ContactBridge.hasReadContactsPermission(appCtx)) }
    var calendarReadGranted by remember { mutableStateOf(CalendarBridge.hasReadPermission(appCtx)) }
    var calendarWriteGranted by remember { mutableStateOf(CalendarBridge.hasWritePermission(appCtx)) }
    var exactAlarmGranted by remember { mutableStateOf(checkExactAlarmGranted(appCtx)) }
    var batteryIgnored by remember { mutableStateOf(checkBatteryUnrestricted(appCtx)) }
    var usageGranted by remember { mutableStateOf(checkUsageAccessGranted(appCtx)) }

    fun refreshAll() {
        postNotifGranted = isPostNotifGranted()
        notifListenerGranted = NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
        smsGranted = isSmsGranted()
        contactsGranted = ContactBridge.hasReadContactsPermission(appCtx)
        calendarReadGranted = CalendarBridge.hasReadPermission(appCtx)
        calendarWriteGranted = CalendarBridge.hasWritePermission(appCtx)
        exactAlarmGranted = checkExactAlarmGranted(appCtx)
        batteryIgnored = checkBatteryUnrestricted(appCtx)
        usageGranted = checkUsageAccessGranted(appCtx)
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshAll()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val runtimePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshAll() }

    val oemNote = remember { restrictedSettingsOemNote() }

    PhantomBackgroundBox(
        layer = com.phantom.checker.util.BackgroundLayer.GLOBAL,
        glassState = glassState,
        modifier = Modifier.fillMaxSize()
    ) {
        com.phantom.checker.ui.components.PhantomPageScaffold(
            title = stringResource(R.string.search_s_1579f9),
            onBack = onBack,
            topBarStyle = com.phantom.checker.ui.components.PhantomTopBarStyle.Medium,
            containerColor = Color.Transparent
        ) { padding ->
            val pendingPermCount = listOf(
                postNotifGranted,
                notifListenerGranted,
                batteryIgnored,
                exactAlarmGranted,
                contactsGranted,
                calendarReadGranted
            ).count { !it }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .hazeSource(glassState),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "perm_overview") {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(
                            com.phantom.checker.ui.components.AuroraTokens.CornerCard
                        ),
                        color = if (pendingPermCount > 0) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (pendingPermCount > 0) stringResource(R.string.perm_hub_s_1d8146, pendingPermCount) else stringResource(R.string.perm_hub_s_0654c2),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (pendingPermCount > 0) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                            Text(
                                text = if (pendingPermCount > 0) {
                                    stringResource(R.string.perm_hub_s_d50874)
                                } else {
                                    stringResource(R.string.perm_hub_s_c3b64d)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                // 1. 通知與通知存取（含 Android 13+ 側載受限設定四廠牌引導）
                phantomItem(0, key = "perm_notifications") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.perm_hub_s_472f0e), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            PermissionRow(
                                name = stringResource(R.string.perm_hub_s_554943),
                                granted = postNotifGranted,
                                desc = stringResource(R.string.perm_hub_s_0269fa),
                                actionLabel = if (postNotifGranted) stringResource(R.string.perm_hub_s_7a3926) else stringResource(R.string.perm_hub_s_4665c7)
                            ) {
                                if (!postNotifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    runtimePermLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
                                } else {
                                    launchIntentWithAppDetailsFallback(
                                        ctx,
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                                    )
                                }
                            }
                            HorizontalDivider()
                            PermissionRow(
                                name = stringResource(R.string.perm_hub_s_b07a41),
                                granted = notifListenerGranted,
                                desc = stringResource(R.string.perm_hub_s_43da2e),
                                actionLabel = if (notifListenerGranted) stringResource(R.string.perm_hub_s_d96d99) else stringResource(R.string.perm_hub_s_8d2b78)
                            ) {
                                openNotificationListenerSettings(ctx)
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notifListenerGranted) {
                                Text(
                                    text = oemNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // 2. 核心資料權限（SMS / 通訊錄 / 日曆）
                phantomItem(1, key = "perm_data") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.perm_hub_s_179aa2), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            PermissionRow(
                                name = stringResource(R.string.perm_hub_s_becc64),
                                granted = smsGranted,
                                desc = stringResource(R.string.perm_hub_s_30a359),
                                actionLabel = if (smsGranted) stringResource(R.string.perm_hub_s_50912e) else stringResource(R.string.perm_hub_s_4665c7)
                            ) {
                                if (!smsGranted) {
                                    runtimePermLauncher.launch(arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS))
                                } else {
                                    launchIntentWithAppDetailsFallback(
                                        ctx,
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                                    )
                                }
                            }
                            HorizontalDivider()
                            PermissionRow(
                                name = stringResource(R.string.perm_hub_s_535411),
                                granted = contactsGranted,
                                desc = stringResource(R.string.perm_hub_s_b023a2),
                                actionLabel = if (contactsGranted) stringResource(R.string.perm_hub_s_50912e) else stringResource(R.string.perm_hub_s_4665c7)
                            ) {
                                if (!contactsGranted) {
                                    runtimePermLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                                } else {
                                    launchIntentWithAppDetailsFallback(
                                        ctx,
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                                    )
                                }
                            }
                            HorizontalDivider()
                            PermissionRow(
                                name = stringResource(R.string.linked_apps_s_995981),
                                granted = calendarReadGranted && calendarWriteGranted,
                                desc = if (calendarReadGranted && !calendarWriteGranted) {
                                    stringResource(R.string.perm_hub_s_0c6070)
                                } else {
                                    stringResource(R.string.perm_hub_s_2fbbe7)
                                },
                                actionLabel = if (calendarReadGranted && calendarWriteGranted) stringResource(R.string.perm_hub_s_50912e) else stringResource(R.string.perm_hub_s_4665c7)
                            ) {
                                if (!calendarReadGranted || !calendarWriteGranted) {
                                    runtimePermLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                                } else {
                                    launchIntentWithAppDetailsFallback(
                                        ctx,
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                                    )
                                }
                            }
                            HorizontalDivider()
                            val hcAvailability = remember { com.phantom.checker.util.HealthConnectBridge.checkAvailability(appCtx) }
                            var hcPermStatus by remember { mutableStateOf(com.phantom.checker.util.HealthConnectBridge.HealthPermissionStatus()) }
                            var healthAiQueryEnabled by remember { mutableStateOf(PhantomPrefs.isHealthAiQueryEnabled(appCtx)) }
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                hcPermStatus = com.phantom.checker.util.HealthConnectBridge.getPermissionStatus(appCtx)
                            }
                            val hcPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                                androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
                            ) { grantedSet ->
                                hcPermStatus = com.phantom.checker.util.HealthConnectBridge.HealthPermissionStatus(
                                    sleep = com.phantom.checker.util.HealthConnectBridge.PERM_READ_SLEEP in grantedSet,
                                    steps = com.phantom.checker.util.HealthConnectBridge.PERM_READ_STEPS in grantedSet,
                                    heartRate = com.phantom.checker.util.HealthConnectBridge.PERM_READ_HEART_RATE in grantedSet,
                                    distance = com.phantom.checker.util.HealthConnectBridge.PERM_READ_DISTANCE in grantedSet,
                                    calories = com.phantom.checker.util.HealthConnectBridge.PERM_READ_TOTAL_CALORIES in grantedSet
                                )
                            }
                            val hcGranted = hcPermStatus.allGranted
                            PermissionRow(
                                name = stringResource(R.string.perm_hub_s_efd3d3),
                                granted = hcPermStatus.anyGranted,
                                desc = when {
                                    hcAvailability == com.phantom.checker.util.HealthConnectBridge.SdkAvailability.UNAVAILABLE ->
                                        stringResource(R.string.perm_hub_s_5698c6)
                                    hcAvailability == com.phantom.checker.util.HealthConnectBridge.SdkAvailability.PROVIDER_UPDATE_REQUIRED ->
                                        stringResource(R.string.perm_hub_s_d93e7c)
                                    hcPermStatus.allGranted ->
                                        stringResource(R.string.perm_hub_s_57b167)
                                    hcPermStatus.anyGranted ->
                                        stringResource(R.string.perm_hub_s_8c2be9, hcPermStatus.grantedCount)
                                    else ->
                                        stringResource(R.string.perm_hub_s_a4d302)
                                },
                                actionLabel = when {
                                    hcAvailability != com.phantom.checker.util.HealthConnectBridge.SdkAvailability.AVAILABLE -> stringResource(R.string.perm_hub_s_f5ef17)
                                    hcGranted -> stringResource(R.string.perm_hub_s_c75ad9)
                                    hcPermStatus.anyGranted -> stringResource(R.string.perm_hub_s_a1dcba)
                                    else -> stringResource(R.string.perm_hub_s_4665c7)
                                }
                            ) {
                                if (hcAvailability != com.phantom.checker.util.HealthConnectBridge.SdkAvailability.AVAILABLE || hcGranted) {
                                    runCatching {
                                        ctx.startActivity(com.phantom.checker.util.HealthConnectBridge.buildInstallOrSettingsIntent(ctx))
                                    }
                                } else {
                                    runCatching {
                                        hcPermLauncher.launch(com.phantom.checker.util.HealthConnectBridge.ALL_READ_PERMISSIONS)
                                    }.onFailure {
                                        runCatching {
                                            ctx.startActivity(com.phantom.checker.util.HealthConnectBridge.buildInstallOrSettingsIntent(ctx))
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = stringResource(R.string.catalog_health_ai_query_enabled_title),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.perm_hub_s_ee6fe2),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                com.phantom.checker.ui.components.FluidSwitch(
                                    checked = healthAiQueryEnabled,
                                    onCheckedChange = { enabled ->
                                        healthAiQueryEnabled = enabled
                                        PhantomPrefs.setHealthAiQueryEnabled(appCtx, enabled)
                                        com.phantom.checker.util.PhantomSettingsBus.notifyChanged()
                                    }
                                )
                            }
                        }
                    }
                }

                // 3. 系統排程與保活權限（精確鬧鐘 / 電池優化 / 使用情況）
                phantomItem(2, key = "perm_system") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.perm_hub_s_414efd), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            PermissionRow(
                                name = stringResource(R.string.perm_hub_s_3fdee4),
                                granted = exactAlarmGranted,
                                desc = stringResource(R.string.perm_hub_s_174d82),
                                actionLabel = if (exactAlarmGranted) stringResource(R.string.perm_hub_s_d96d99) else stringResource(R.string.perm_hub_s_8d2b78)
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    launchIntentWithAppDetailsFallback(
                                        ctx,
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                                    )
                                } else {
                                    Toast.makeText(ctx, resCtx.getString(R.string.perm_hub_s_f9724d), Toast.LENGTH_SHORT).show()
                                }
                            }
                            HorizontalDivider()
                            PermissionRow(
                                name = stringResource(R.string.perm_hub_s_967952),
                                granted = batteryIgnored,
                                desc = stringResource(R.string.perm_hub_s_79848c),
                                actionLabel = if (batteryIgnored) stringResource(R.string.perm_hub_s_911344) else stringResource(R.string.common_add_whitelist)
                            ) {
                                launchIntentWithAppDetailsFallback(
                                    ctx,
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                )
                            }
                            HorizontalDivider()
                            PermissionRow(
                                name = stringResource(R.string.perm_hub_s_767546),
                                granted = usageGranted,
                                desc = stringResource(R.string.perm_hub_s_5b4737),
                                actionLabel = if (usageGranted) stringResource(R.string.perm_hub_s_d96d99) else stringResource(R.string.perm_hub_s_8d2b78)
                            ) {
                                if (!com.phantom.checker.util.UsageStatsBridge.openUsageAccessSettings(ctx)) {
                                    launchIntentWithAppDetailsFallback(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                }
                            }
                        }
                    }
                }

                // 4. Shizuku 說明列（不移入本頁，保留於主設定頁 Shizuku 卡）
                phantomItem(3, key = "perm_shizuku_note") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.perm_hub_s_3af0f6), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    stringResource(R.string.perm_hub_s_114952),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = {
                                ctx.startActivity(
                                    Intent(ctx, SettingsActivity::class.java).apply {
                                        putExtra("highlight_card_key", "shizuku")
                                    }
                                )
                            }) {
                                Text(stringResource(R.string.perm_hub_s_76e7f8))
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    name: String,
    granted: Boolean,
    desc: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    val resCtx = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.chat_s_c1aacd, name, if (granted) resCtx.getString(R.string.perm_hub_s_d95bf2) else resCtx.getString(R.string.linked_apps_s_3df23c)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (granted) {
            OutlinedButton(onClick = onAction) { Text(actionLabel) }
        } else {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}
