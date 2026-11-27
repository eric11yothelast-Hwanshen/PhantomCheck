package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomItem
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

/**
 * #339 連結應用程式中心：
 * - 即用能力：鬧鐘與計時器（免特殊權限）。
 * - 本機能力卡（onResume 即時重查狀態）：日曆、通訊錄、通知存取、應用使用情況、位置（選配）、電池優化白名單。
 * - 雲端擴充 BYOK（EncryptedSharedPreferences 遮罩儲存）：GitHub PAT 輪詢與 Discord Webhook。
 */
class LinkedAppsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
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

private fun hasUsageStatsAccess(ctx: Context): Boolean =
    com.phantom.checker.util.UsageStatsBridge.hasUsageStatsPermission(ctx)

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
    Toast.makeText(ctx, com.phantom.checker.PhantomApplication.str(R.string.linked_apps_open_settings_unsupported), Toast.LENGTH_SHORT).show()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun LinkedAppsScreen(onBack: () -> Unit) {
    val resCtx = LocalContext.current
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
        com.phantom.checker.ui.components.PhantomPageScaffold(
            title = stringResource(R.string.catalog_linked_apps_center_title),
            onBack = onBack,
            topBarStyle = com.phantom.checker.ui.components.PhantomTopBarStyle.Medium,
            containerColor = Color.Transparent
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
                phantomItem(0, key = "ready_alarm_timer") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(R.string.linked_apps_s_77d570),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                stringResource(R.string.linked_apps_s_1db47c),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    openSystemActionSafely(ctx, Intent(AlarmClock.ACTION_SHOW_ALARMS), fallbackToAppDetails = false)
                                }) { Text(stringResource(R.string.linked_apps_s_d9f78d)) }
                                OutlinedButton(onClick = {
                                    openSystemActionSafely(ctx, Intent(AlarmClock.ACTION_SHOW_TIMERS), fallbackToAppDetails = false)
                                }) { Text(stringResource(R.string.linked_apps_s_8cef6b)) }
                            }
                        }
                    }
                }

                // 2. 本機能力卡
                phantomItem(1, key = "local_capabilities") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.linked_apps_s_04841f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                            CapabilityRow(stringResource(R.string.linked_apps_s_995981), calGranted, stringResource(R.string.linked_apps_s_be9bc1)) {
                                permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                            }
                            HorizontalDivider()
                            CapabilityRow(stringResource(R.string.linked_apps_s_b5afaf), contactsGranted, stringResource(R.string.linked_apps_s_2e4f98)) {
                                permLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
                            }
                            HorizontalDivider()
                            CapabilityRow(stringResource(R.string.linked_apps_s_ca5156), notifListenerGranted, stringResource(R.string.linked_apps_s_248430)) {
                                openNotificationListenerSettings(ctx)
                            }
                            HorizontalDivider()
                            CapabilityRow(stringResource(R.string.linked_apps_s_c86362), usageGranted, stringResource(R.string.linked_apps_s_f22bed)) {
                                openSystemActionSafely(ctx, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                            }
                            HorizontalDivider()
                            CapabilityRow(stringResource(R.string.linked_apps_s_f9c441), locationGranted, stringResource(R.string.linked_apps_s_369920)) {
                                permLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION))
                            }
                            HorizontalDivider()
                            CapabilityRow(stringResource(R.string.linked_apps_s_2b77be), batteryIgnored, stringResource(R.string.linked_apps_s_a3b27c)) {
                                openSystemActionSafely(
                                    ctx,
                                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                )
                            }
                        }
                    }
                }

                // 3. 雲端擴充 BYOK：GitHub
                phantomItem(2, key = "cloud_github") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.linked_apps_s_c0adeb),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
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
                                stringResource(R.string.linked_apps_s_9062cf),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = githubPat,
                                onValueChange = { githubPat = it },
                                label = { Text(stringResource(R.string.linked_apps_s_9123ca)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = githubRepos,
                                onValueChange = { githubRepos = it },
                                label = { Text(stringResource(R.string.linked_apps_s_b9c238)) },
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
                                        label = { Text(stringResource(R.string.assistant_hub_s_81c947, mins)) }
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    EncryptedByokStore.setGitHubPat(appCtx, githubPat)
                                    EncryptedByokStore.setGitHubRepos(appCtx, githubRepos)
                                    CloudExtensionsClient.scheduleGitHubPolling(appCtx)
                                    Toast.makeText(ctx, resCtx.getString(R.string.linked_apps_s_6b2b56), Toast.LENGTH_SHORT).show()
                                }) { Text(stringResource(R.string.common_save)) }
                                OutlinedButton(onClick = {
                                    EncryptedByokStore.setGitHubPat(appCtx, githubPat)
                                    scope.launch {
                                        val (_, msg) = CloudExtensionsClient.testGitHubPat(appCtx)
                                        githubStatus = msg
                                    }
                                }) { Text(stringResource(R.string.settings_s_48b807)) }
                            }
                            if (githubStatus.isNotBlank()) {
                                Text(githubStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // 4. 雲端擴充 BYOK：Discord Webhook
                phantomItem(3, key = "cloud_discord") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    stringResource(R.string.linked_apps_s_449a7f),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                FluidSwitch(
                                    checked = discordEnabled,
                                    onCheckedChange = {
                                        discordEnabled = it
                                        PhantomPrefs.setDiscordForwardEnabled(appCtx, it)
                                    }
                                )
                            }
                            Text(
                                stringResource(R.string.linked_apps_s_67f81c),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = discordWebhook,
                                onValueChange = { discordWebhook = it },
                                label = { Text(stringResource(R.string.linked_apps_s_8c33cc)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    EncryptedByokStore.setDiscordWebhookUrl(appCtx, discordWebhook)
                                    Toast.makeText(ctx, resCtx.getString(R.string.linked_apps_s_f6f55b), Toast.LENGTH_SHORT).show()
                                }) { Text(stringResource(R.string.common_save)) }
                                OutlinedButton(onClick = {
                                    EncryptedByokStore.setDiscordWebhookUrl(appCtx, discordWebhook)
                                    scope.launch {
                                        val (_, msg) = CloudExtensionsClient.sendDiscordMessage(appCtx, resCtx.getString(R.string.linked_apps_s_037838))
                                        discordStatus = msg
                                    }
                                }) { Text(stringResource(R.string.linked_apps_s_ca1c6d)) }
                            }
                            if (discordStatus.isNotBlank()) {
                                Text(discordStatus, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // 4. #368 宣告式動作登錄檔（Action Registry：與 AI 助手能力宣告共用單一來源）
                phantomItem(4, key = "linked_action_registry") {
                    PhantomGlassCard(glassState = glassState, cornerRadius = 18.dp, contentPadding = 14.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.linked_apps_s_120204),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                stringResource(R.string.linked_apps_s_864221),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            com.phantom.checker.util.LinkedAppActionRegistry.ACTIONS.forEach { act ->
                                val st = act.evaluateStatus(appCtx)
                                val isGray = st.availability == com.phantom.checker.util.LinkedAppActionRegistry.Availability.NEEDS_BYOK ||
                                    st.availability == com.phantom.checker.util.LinkedAppActionRegistry.Availability.NEEDS_PERMISSION
                                val isWarn = st.availability == com.phantom.checker.util.LinkedAppActionRegistry.Availability.WARNING
                                val rwBadge = if (act.isReadOnly) resCtx.getString(R.string.linked_apps_s_8351a4) else resCtx.getString(R.string.linked_apps_s_736b95)
                                HorizontalDivider()
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = "${act.title} [$rwBadge]",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = when {
                                            isWarn -> MaterialTheme.colorScheme.warning
                                            isGray -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                                            else -> MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = act.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = when {
                                            isWarn -> "⚠️ ${st.statusText}"
                                            isGray -> "⏳ ${st.statusText}"
                                            else -> "✅ ${st.statusText}"
                                        } + if (st.lastSyncAtMs > 0L || st.nextSyncAtMs > 0L) {
                                            " · ${com.phantom.checker.util.LinkedAppActionRegistry.formatSyncWindow(st.lastSyncAtMs, st.nextSyncAtMs)}"
                                        } else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = when {
                                            isWarn -> MaterialTheme.colorScheme.warning
                                            isGray -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                            else -> MaterialTheme.colorScheme.primary
                                        }
                                    )
                                }
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
    val resCtx = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.chat_s_c1aacd, title, if (granted) resCtx.getString(R.string.linked_apps_s_7c63f8) else resCtx.getString(R.string.linked_apps_s_3df23c)),
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
            Text(if (granted) stringResource(R.string.linked_apps_s_4989b5) else stringResource(R.string.settings_s_5df1b3))
        }
    }
}
