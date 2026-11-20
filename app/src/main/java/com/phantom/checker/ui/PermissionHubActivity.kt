package com.phantom.checker.ui

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
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

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
    Toast.makeText(ctx, "無法開啟系統設定頁，請手動前往系統設定", Toast.LENGTH_SHORT).show()
}

private fun checkExactAlarmGranted(ctx: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        am?.canScheduleExactAlarms() == true
    } else {
        true
    }
}

private fun checkUsageAccessGranted(ctx: Context): Boolean {
    return try {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

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
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("權限授予中心", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
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
                // 1. 通知與通知存取（含 Android 13+ 側載受限設定四廠牌引導）
                item(key = "perm_notifications") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("🔔 通知權限與監聽存取", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            PermissionRow(
                                name = "顯示通知（POST_NOTIFICATIONS）",
                                granted = postNotifGranted,
                                desc = "發送詐騙警示、待辦提醒、早晚簡報與常駐統計通知",
                                actionLabel = if (postNotifGranted) "系統通知設定" else "立即授予"
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
                                name = "通知存取（NotificationListener）",
                                granted = notifListenerGranted,
                                desc = "攔截各 App 通知以執行風險分析與自動備忘",
                                actionLabel = if (notifListenerGranted) "前往管理" else "前往開啟"
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
                item(key = "perm_data") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("📂 簡訊、通訊錄與日曆權限", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            PermissionRow(
                                name = "簡訊收發（SMS）",
                                granted = smsGranted,
                                desc = "自動簡訊防詐分析與自動回覆功能（選配）",
                                actionLabel = if (smsGranted) "App 權限頁" else "立即授予"
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
                                name = "通訊錄讀取（READ_CONTACTS）",
                                granted = contactsGranted,
                                desc = "純本機防詐熟人比對、暱稱綁定、關係匯入與 VIP 信任圈（絕不外送 AI）",
                                actionLabel = if (contactsGranted) "App 權限頁" else "立即授予"
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
                                name = "本機日曆（讀寫）",
                                granted = calendarReadGranted && calendarWriteGranted,
                                desc = if (calendarReadGranted && !calendarWriteGranted) {
                                    "已授權讀取；補授寫入後可直接新增行程免跳系統表單"
                                } else {
                                    "行程分流建議卡、空檔查詢與撞期偵測（僅存取您勾選的日曆）"
                                },
                                actionLabel = if (calendarReadGranted && calendarWriteGranted) "App 權限頁" else "立即授予"
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
                        }
                    }
                }

                // 3. 系統排程與保活權限（精確鬧鐘 / 電池優化 / 使用情況）
                item(key = "perm_system") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("⚙️ 排程、電池與使用情況權限", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            PermissionRow(
                                name = "精確鬧鐘（SCHEDULE_EXACT_ALARM）",
                                granted = exactAlarmGranted,
                                desc = "確保待辦提醒、早晚簡報與 Watchdog 保活準時觸發（變更時自動重排）",
                                actionLabel = if (exactAlarmGranted) "前往管理" else "前往開啟"
                            ) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    launchIntentWithAppDetailsFallback(
                                        ctx,
                                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}"))
                                    )
                                } else {
                                    Toast.makeText(ctx, "此 Android 版本預設允許精確鬧鐘", Toast.LENGTH_SHORT).show()
                                }
                            }
                            HorizontalDivider()
                            PermissionRow(
                                name = "忽略電池優化",
                                granted = batteryIgnored,
                                desc = "避免系統省電機制中斷背景通知監聽與定時簡報",
                                actionLabel = if (batteryIgnored) "電池設定" else "加入白名單"
                            ) {
                                launchIntentWithAppDetailsFallback(
                                    ctx,
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                )
                            }
                            HorizontalDivider()
                            PermissionRow(
                                name = "應用使用情況存取",
                                granted = usageGranted,
                                desc = "供連結應用程式中心判斷前景使用狀態（選配）",
                                actionLabel = if (usageGranted) "前往管理" else "前往開啟"
                            ) {
                                launchIntentWithAppDetailsFallback(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                        }
                    }
                }

                // 4. Shizuku 說明列（不移入本頁，保留於主設定頁 Shizuku 卡）
                item(key = "perm_shizuku_note") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Shizuku 進階保活權限", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "Shizuku 授權與看門狗控制維持獨立保留在主設定頁的「Shizuku 強化保活」卡片中。",
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
                                Text("前往 Shizuku")
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$name · ${if (granted) "✅ 已授予" else "⚠️ 未授予"}",
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
