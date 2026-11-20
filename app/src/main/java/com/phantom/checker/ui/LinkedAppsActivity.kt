package com.phantom.checker.ui

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.provider.AlarmClock
import android.provider.Settings
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.phantom.checker.service.CloudExtensionsClient
import com.phantom.checker.ui.components.FluidSwitch
import com.phantom.checker.util.CalendarBridge
import com.phantom.checker.util.ContactBridge
import com.phantom.checker.util.EncryptedByokStore
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * #339 連結應用程式中心：
 * - 即用能力：鬧鐘與計時器（免特殊權限）。
 * - 本機能力卡（onResume 即時重查狀態）：日曆、通訊錄、通知存取、應用使用情況、位置（選配）、電池優化白名單。
 * - 雲端擴充 BYOK（EncryptedSharedPreferences 遮罩儲存）：GitHub PAT 輪詢與 Discord Webhook。
 */
class LinkedAppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            PhantomCheckTheme {
                LinkedAppsScreen(onBack = { finish() })
            }
        }
    }
}

private fun hasUsageStatsAccess(ctx: Context): Boolean {
    return try {
        val appOps = ctx.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName)
        }
        mode == AppOpsManager.MODE_ALLOWED
    } catch (_: Exception) {
        false
    }
}

private fun openSystemActionSafely(ctx: Context, intent: Intent, fallbackToAppDetails: Boolean = true) {
    try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return
    } catch (_: Exception) {
    }
    if (fallbackToAppDetails) {
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
    }
    Toast.makeText(ctx, "此裝置不支援直接開啟該設定頁", Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LinkedAppsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val glassState = remember { HazeState() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var calGranted by remember { mutableStateOf(CalendarBridge.hasReadPermission(appCtx)) }
    var contactsGranted by remember { mutableStateOf(ContactBridge.hasReadContactsPermission(appCtx)) }
    var notifListenerGranted by remember {
        mutableStateOf(NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName))
    }
    var usageGranted by remember { mutableStateOf(hasUsageStatsAccess(appCtx)) }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var batteryIgnored by remember { mutableStateOf(isBatteryOptimizationIgnored(appCtx)) }

    fun refreshStatuses() {
        calGranted = CalendarBridge.hasReadPermission(appCtx)
        contactsGranted = ContactBridge.hasReadContactsPermission(appCtx)
        notifListenerGranted = NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
        usageGranted = hasUsageStatsAccess(appCtx)
        locationGranted = ContextCompat.checkSelfPermission(appCtx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        batteryIgnored = isBatteryOptimizationIgnored(appCtx)
    }

    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshStatuses()
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshStatuses() }

    // 雲端擴充 BYOK 狀態（讀取自 EncryptedByokStore，遮罩輸入）
    var githubEnabled by remember { mutableStateOf(PhantomPrefs.isGitHubMonitorEnabled(appCtx)) }
    var githubInterval by remember { mutableStateOf(PhantomPrefs.getGitHubPollIntervalMinutes(appCtx)) }
    var githubPat by remember { mutableStateOf(EncryptedByokStore.getGitHubPat(appCtx)) }
    var githubRepos by remember { mutableStateOf(EncryptedByokStore.getGitHubRepos(appCtx)) }
    var githubStatus by remember { mutableStateOf("") }

    var discordEnabled by remember { mutableStateOf(PhantomPrefs.isDiscordForwardEnabled(appCtx)) }
    var discordWebhook by remember { mutableStateOf(EncryptedByokStore.getDiscordWebhookUrl(appCtx)) }
    var discordStatus by remember { mutableStateOf("") }

    PhantomBackgroundBox(
        layer = com.phantom.checker.util.BackgroundLayer.GLOBAL,
        glassState = glassState,
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = { Text("連結應用程式中心", fontWeight = FontWeight.Bold) },
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
                // 1. 即用能力（不需特殊權限）
                item(key = "ready_alarm_timer") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("⏰ 鬧鐘與計時器（即用，免特殊權限）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                "AI 助手可提出設定鬧鐘／計時器行動卡，一律由您點擊確認後交由系統時鐘 App 執行。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    openSystemActionSafely(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS), fallbackToAppDetails = false)
                                }) { Text("開啟系統鬧鐘") }
                                OutlinedButton(onClick = {
                                    openSystemActionSafely(ctx, Intent(AlarmClock.ACTION_SHOW_TIMERS), fallbackToAppDetails = false)
                                }) { Text("開啟計時器") }
                            }
                        }
                    }
                }

                // 2. 本機能力卡
                item(key = "local_capabilities") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("本機系統能力狀態（返回即自動刷新）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                            CapabilityRow("本機日曆（讀寫）", calGranted, "行程分流、空檔查詢與撞期偵測") {
                                permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                            }
                            HorizontalDivider()
                            CapabilityRow("本機通訊錄", contactsGranted, "暱稱綁定、關係匯入與 VIP 信任圈（絕不外送 AI）") {
                                permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                            }
                            HorizontalDivider()
                            CapabilityRow("通知存取（監聽服務）", notifListenerGranted, "核心通知攔截與風險分析") {
                                openNotificationListenerSettings(ctx)
                            }
                            HorizontalDivider()
                            CapabilityRow("應用使用情況", usageGranted, "輔助判斷前景使用情境（選配）") {
                                openSystemActionSafely(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                            HorizontalDivider()
                            CapabilityRow("粗略位置（選配）", locationGranted, "僅於手動點擊定位城市時一次性反解地名文字") {
                                permLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                            HorizontalDivider()
                            CapabilityRow("電池優化白名單", batteryIgnored, "降低背景排程與早晚簡報遭系統省電休眠") {
                                openSystemActionSafely(
                                    ctx,
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                )
                            }
                        }
                    }
                }

                // 3. 雲端擴充 BYOK：GitHub
                item(key = "cloud_github") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🐙 GitHub 雲端擴充（BYOK 加密儲存）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                FluidSwitch(
                                    checked = githubEnabled,
                                    onCheckedChange = {
                                        githubEnabled = it
                                        PhantomPrefs.setGitHubMonitorEnabled(appCtx, it)
                                        CloudExtensionsClient.scheduleGitHubPolling(appCtx)
                                    }
                                )
                            }
                            Text(
                                "建議使用 Fine-grained PAT 且僅勾選 Actions(Read) 與 Pull requests(Read) 最小權限；金鑰僅存於本機 EncryptedSharedPreferences。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = githubPat,
                                onValueChange = { githubPat = it },
                                label = { Text("GitHub PAT（遮罩顯示）") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = githubRepos,
                                onValueChange = { githubRepos = it },
                                label = { Text("監控倉庫（owner/repo，逗號分隔）") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(15, 30, 60).forEach { mins ->
                                    FilterChip(
                                        selected = githubInterval == mins,
                                        onClick = {
                                            githubInterval = mins
                                            PhantomPrefs.setGitHubPollIntervalMinutes(appCtx, mins)
                                            CloudExtensionsClient.scheduleGitHubPolling(appCtx)
                                        },
                                        label = { Text("每 ${mins} 分鐘") }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    EncryptedByokStore.setGitHubPat(appCtx, githubPat)
                                    EncryptedByokStore.setGitHubRepos(appCtx, githubRepos)
                                    CloudExtensionsClient.scheduleGitHubPolling(appCtx)
                                    Toast.makeText(ctx, "已安全儲存 GitHub 設定", Toast.LENGTH_SHORT).show()
                                }) { Text("儲存") }
                                OutlinedButton(onClick = {
                                    EncryptedByokStore.setGitHubPat(appCtx, githubPat)
                                    scope.launch {
                                        val (_, msg) = CloudExtensionsClient.testGitHubPat(appCtx)
                                        githubStatus = msg
                                    }
                                }) { Text("測試連線") }
                            }
                            if (githubStatus.isNotBlank()) {
                                Text(githubStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // 4. 雲端擴充 BYOK：Discord Webhook
                item(key = "cloud_discord") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("💬 Discord Webhook（BYOK 加密儲存）", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                FluidSwitch(
                                    checked = discordEnabled,
                                    onCheckedChange = {
                                        discordEnabled = it
                                        PhantomPrefs.setDiscordForwardEnabled(appCtx, it)
                                    }
                                )
                            }
                            Text(
                                "啟用後可將早晚簡報轉發至您指定的 Discord 頻道；Webhook URL 僅加密儲存於本機。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = discordWebhook,
                                onValueChange = { discordWebhook = it },
                                label = { Text("Discord Webhook URL（遮罩顯示）") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    EncryptedByokStore.setDiscordWebhookUrl(appCtx, discordWebhook)
                                    Toast.makeText(ctx, "已安全儲存 Discord Webhook", Toast.LENGTH_SHORT).show()
                                }) { Text("儲存") }
                                OutlinedButton(onClick = {
                                    EncryptedByokStore.setDiscordWebhookUrl(appCtx, discordWebhook)
                                    scope.launch {
                                        val (_, msg) = CloudExtensionsClient.sendDiscordMessage(appCtx, "幻訊測試推播：Discord Webhook 連線正常！")
                                        discordStatus = msg
                                    }
                                }) { Text("測試推播") }
                            }
                            if (discordStatus.isNotBlank()) {
                                Text(discordStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
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
private fun CapabilityRow(
    title: String,
    granted: Boolean,
    subtitle: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$title · ${if (granted) "✅ 已授權" else "⚠️ 未授予"}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onAction) {
            Text(if (granted) "管理" else "前往授權")
        }
    }
}
