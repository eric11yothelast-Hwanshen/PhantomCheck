package com.phantom.checker.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.network.MistralClient
import com.phantom.checker.service.AppDatabaseHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MistralClient.init(applicationContext)
        setContent {
            MaterialTheme {
                PhantomCheckApp()
            }
        }
    }
}

private fun isNotificationListenerEnabled(context: Context): Boolean {
    return NotificationManagerCompat.getEnabledListenerPackages(context)
        .contains(context.packageName)
}

private fun isPostNotificationsGranted(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

private enum class MainTab(val title: String) {
    ALL("全部"),
    SCAMS("詐騙警示"),
    FILTERED("已過濾")
}

@Composable
fun PhantomCheckApp() {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext

    var selectedTab by remember { mutableStateOf(MainTab.ALL) }
    var messages by remember { mutableStateOf(listOf<MessageEntity>()) }
    var activeFilterKeywords by remember { mutableStateOf(listOf<String>()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    var isListenerEnabled by remember {
        mutableStateOf(isNotificationListenerEnabled(appCtx))
    }
    var hasPostNotifications by remember {
        mutableStateOf(isPostNotificationsGranted(appCtx))
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPostNotifications = granted
    }

    // Android 13+ 啟動時主動請求 POST_NOTIFICATIONS 權限
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifications) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 回到前景時重新檢查權限狀態並重新載入資料
    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isListenerEnabled = isNotificationListenerEnabled(appCtx)
                hasPostNotifications = isPostNotificationsGranted(appCtx)
                refreshTrigger++
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    // 依分頁載入訊息，或進行跨分頁搜尋（300ms debounce）
    LaunchedEffect(selectedTab, searchQuery, refreshTrigger) {
        val q = searchQuery.trim()
        if (q.isNotEmpty()) {
            delay(300L)
        }
        isLoading = true
        val (loadedMessages, loadedKeywords) = withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val messageDao = db.messageDao()
            val msgs = if (q.isNotEmpty()) {
                messageDao.search(q)
            } else {
                when (selectedTab) {
                    MainTab.ALL -> messageDao.recent()
                    MainTab.SCAMS -> messageDao.scams()
                    MainTab.FILTERED -> messageDao.filtered()
                }
            }
            val keywords = if (selectedTab == MainTab.FILTERED || msgs.any { it.isFiltered }) {
                db.filterDao().active().map { it.keyword }
            } else {
                emptyList()
            }
            msgs to keywords
        }
        messages = loadedMessages
        activeFilterKeywords = loadedKeywords
        isLoading = false
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("幻訊 PhantomCheck", style = MaterialTheme.typography.headlineSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = {
                        ctx.startActivity(Intent(ctx, ScamCenterActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "詐騙警示中心",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新整理"
                        )
                    }
                    IconButton(onClick = {
                        ctx.startActivity(Intent(ctx, ApiKeySettingsActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "API Key 設定"
                        )
                    }
                    IconButton(onClick = {
                        ctx.startActivity(Intent(ctx, SettingsActivity::class.java))
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "過濾關鍵字設定"
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("搜尋寄件人或訊息內容…") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "搜尋"
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "清除搜尋"
                            )
                        }
                    }
                }
            )

            if (!isListenerEnabled) {
                Spacer(Modifier.height(8.dp))
                NotificationListenerGuideCard(
                    onOpenSettings = {
                        ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    }
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasPostNotifications) {
                Spacer(Modifier.height(8.dp))
                PostNotificationGuideCard(
                    onRequestPermission = {
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }

            Spacer(Modifier.height(8.dp))

            TabRow(selectedTabIndex = selectedTab.ordinal) {
                MainTab.entries.forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.title) }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            if (selectedTab == MainTab.SCAMS) {
                OutlinedButton(
                    onClick = { ctx.startActivity(Intent(ctx, ScamCenterActivity::class.java)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("開啟詐騙警示中心（查看完整風險分析）")
                }
                Spacer(Modifier.height(8.dp))
            }

            when {
                isLoading && messages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                messages.isEmpty() -> {
                    val emptyText = if (searchQuery.isNotBlank()) {
                        "找不到符合「$searchQuery」的訊息"
                    } else {
                        when (selectedTab) {
                            MainTab.ALL -> "目前尚無攔截到的訊息"
                            MainTab.SCAMS -> "目前尚無詐騙警示訊息"
                            MainTab.FILTERED -> "目前尚無已過濾的訊息"
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emptyText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(messages, key = { it.id }) { m ->
                            val matchedKeywords = if (selectedTab == MainTab.FILTERED || m.isFiltered) {
                                activeFilterKeywords.filter { kw ->
                                    m.content.contains(kw, ignoreCase = true)
                                }
                            } else {
                                emptyList()
                            }
                            MessageCard(
                                message = m,
                                highlightAsScam = selectedTab == MainTab.SCAMS,
                                matchedKeywords = matchedKeywords
                            )
                        }
                    }
                }
            }
        }
    }
}
