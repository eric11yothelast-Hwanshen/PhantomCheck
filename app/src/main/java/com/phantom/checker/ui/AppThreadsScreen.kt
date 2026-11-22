package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomEnter
import androidx.compose.foundation.lazy.itemsIndexed
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.withTransaction
import com.phantom.checker.data.MessageTagRow
import com.phantom.checker.data.SenderGroupRow
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val MAX_BATCH_SELECT = 100

data class SenderThreadUi(
    val conversationKey: String,
    val displayTitle: String,
    val count: Int,
    val unreadCount: Int,
    val lastTs: Long,
    val lastPreview: String,
    val isPinned: Boolean = false,
    val latestTags: List<MessageTagRow> = emptyList()
)

class AppThreadsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty().ifBlank { packageName }
        setContent {
            PhantomCheckTheme {
                AppThreadsScreen(
                    packageName = packageName,
                    appLabel = appLabel,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_APP_LABEL = "extra_app_label"

        fun createIntent(ctx: Context, packageName: String, appLabel: String): Intent {
            return Intent(ctx, AppThreadsActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_LABEL, appLabel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppThreadsScreen(
    packageName: String,
    appLabel: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = rememberPhantomHaptic()
    var threads by remember { mutableStateOf(listOf<SenderThreadUi>()) }
    var avatarMap by remember { mutableStateOf<Map<String, ByteArray>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var actionThread by remember { mutableStateOf<SenderThreadUi?>(null) }
    var deleteThreadConfirm by remember { mutableStateOf<SenderThreadUi?>(null) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedConversationKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }

    val toggleSelection: (String) -> Unit = { convKey ->
        if (convKey in selectedConversationKeys) {
            selectedConversationKeys = selectedConversationKeys - convKey
        } else if (selectedConversationKeys.size >= MAX_BATCH_SELECT) {
            scope.launch {
                snackbarHostState.showSnackbar("單次最多選取 $MAX_BATCH_SELECT 個對話")
            }
        } else {
            selectedConversationKeys = selectedConversationKeys + convKey
        }
    }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedConversationKeys = emptySet()
    }

    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(packageName, refreshTrigger) {
        isLoading = true
        val (loadedThreads, loadedAvatars) = withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val dao = db.messageDao()
            val vipSenders = PhantomPrefs.getVipSenders(appCtx, packageName)
            val groups: List<SenderGroupRow> = dao.groupByConversation(packageName)
            val allAppMsgs = dao.byApp(packageName)
            val latestByConv = allAppMsgs.groupBy { it.conversationKey.ifBlank { it.sender } }
                .mapValues { (_, list) -> list.maxByOrNull { it.timestamp } }
            val latestIds = latestByConv.values.mapNotNull { it?.id }.distinct()
            val tagsMap = if (latestIds.isEmpty()) {
                emptyMap()
            } else {
                db.tagDao().tagsForMessages(latestIds).groupBy { it.messageId }
            }
            val avatars = db.senderAvatarDao().byPackage(packageName).associate { it.sender to it.icon }
            val threadList = groups.map { g ->
                val convKey = g.conversationKey
                val latestMsg = latestByConv[convKey]
                val displayTitle = convKey.ifBlank { latestMsg?.sender.orEmpty() }
                val preview = if (latestMsg != null && latestMsg.sender.isNotBlank() && latestMsg.sender != convKey) {
                    "${latestMsg.sender}: ${latestMsg.content}"
                } else {
                    latestMsg?.content.orEmpty()
                }
                SenderThreadUi(
                    conversationKey = convKey,
                    displayTitle = displayTitle,
                    count = g.cnt,
                    unreadCount = g.unreadCnt,
                    lastTs = g.lastTs,
                    lastPreview = preview,
                    isPinned = convKey in vipSenders || (latestMsg != null && latestMsg.sender in vipSenders),
                    latestTags = latestMsg?.let { tagsMap[it.id] }.orEmpty()
                )
            }.sortedWith(compareByDescending<SenderThreadUi> { it.isPinned }.thenByDescending { it.lastTs })
            threadList to avatars
        }
        threads = loadedThreads
        avatarMap = loadedAvatars
        isLoading = false
    }

    actionThread?.let { target ->
        AlertDialog(
            onDismissRequest = { actionThread = null },
            title = { Text(target.displayTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            PhantomPrefs.toggleVipSender(appCtx, packageName, target.conversationKey)
                            actionThread = null
                            refreshTrigger++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (target.isPinned) "取消頂部釘選" else "釘選至頂部")
                    }
                    OutlinedButton(
                        onClick = {
                            actionThread = null
                            deleteThreadConfirm = target
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("刪除此對話", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionThread = null }) {
                    Text("關閉")
                }
            }
        )
    }

    deleteThreadConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteThreadConfirm = null },
            title = { Text("刪除此對話") },
            text = { Text("確定要永久刪除與「${target.displayTitle}」的所有對話訊息嗎？此操作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByConversation(packageName, target.conversationKey)
                                    db.messageDao().deleteByConversation(packageName, target.conversationKey)
                                }
                                StatsForegroundService.startOrRefresh(appCtx)
                            }
                            deleteThreadConfirm = null
                            refreshTrigger++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteThreadConfirm = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("清除此 App 所有訊息") },
            text = { Text("確定要永久刪除「$appLabel」的所有攔截訊息嗎？此操作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByApp(packageName)
                                    db.messageDao().deleteByApp(packageName)
                                }
                                StatsForegroundService.startOrRefresh(appCtx)
                            }
                            showDeleteConfirm = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showBatchDeleteConfirm && selectedConversationKeys.isNotEmpty()) {
        val count = selectedConversationKeys.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批次刪除對話") },
            text = { Text("確定要永久刪除已選取的 $count 個對話嗎？詐騙訊息將受保護保留，其餘刪除後無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        val keys = selectedConversationKeys.toList()
                        scope.launch {
                            val (deletedCount, protectedCount) = withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    val protected = db.messageDao().countScamByConversations(packageName, keys)
                                    db.tagDao().deleteNonScamCrossRefsByConversations(packageName, keys)
                                    val deleted = db.messageDao().deleteNonScamByConversations(packageName, keys)
                                    deleted to protected
                                }
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            showBatchDeleteConfirm = false
                            isMultiSelectMode = false
                            selectedConversationKeys = emptySet()
                            refreshTrigger++
                            val msgText = when {
                                deletedCount > 0 && protectedCount > 0 ->
                                    "已刪除 $deletedCount 則訊息，另有 $protectedCount 則詐騙訊息受保護保留"
                                deletedCount == 0 && protectedCount > 0 ->
                                    "所選對話中的 $protectedCount 則皆為詐騙訊息，已受保護保留"
                                else ->
                                    "已刪除所選對話共 $deletedCount 則訊息"
                            }
                            snackbarHostState.showSnackbar(msgText)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    val glassState = remember { HazeState() }
    var showThreadSearch by remember { mutableStateOf(false) }
    var threadSearchQuery by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    expandedHeight = 52.dp,
                    title = {
                        Text(
                            text = "已選 ${selectedConversationKeys.size}/$MAX_BATCH_SELECT 個對話",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isMultiSelectMode = false
                            selectedConversationKeys = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消選取"
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = threads.isNotEmpty(),
                            onClick = {
                                val keys = threads.map { it.conversationKey }
                                val targetKeys = if (keys.size > MAX_BATCH_SELECT) {
                                    keys.take(MAX_BATCH_SELECT).toSet()
                                } else {
                                    keys.toSet()
                                }
                                selectedConversationKeys = targetKeys
                                scope.launch {
                                    val msg = if (keys.size > MAX_BATCH_SELECT) {
                                        "單次最多選取 $MAX_BATCH_SELECT 個對話（已選 ${targetKeys.size} 個）"
                                    } else {
                                        "已全選 ${targetKeys.size} 個對話"
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        ) {
                            Text("全選")
                        }
                    }
                )
            } else {
                TopAppBar(
                    expandedHeight = 52.dp,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(
                                packageName = packageName,
                                contentDescription = appLabel,
                                size = 24.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = appLabel,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回"
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (threads.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("批次選取") },
                                        onClick = {
                                            showMenu = false
                                            isMultiSelectMode = true
                                            selectedConversationKeys = emptySet()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "清除此 App 所有訊息",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isMultiSelectMode) {
                Surface(
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            enabled = selectedConversationKeys.isNotEmpty(),
                            onClick = {
                                val keys = selectedConversationKeys.toList()
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppDatabaseHolder.db(appCtx).messageDao().markConversationsRead(packageName, keys)
                                    }
                                    StatsForegroundService.startOrRefresh(appCtx)
                                    isMultiSelectMode = false
                                    selectedConversationKeys = emptySet()
                                    refreshTrigger++
                                    snackbarHostState.showSnackbar("已將 ${keys.size} 個對話標記為已讀")
                                }
                            }
                        ) { Text("標已讀") }
                        TextButton(
                            enabled = selectedConversationKeys.isNotEmpty(),
                            onClick = { showBatchDeleteConfirm = true }
                        ) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                        TextButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedConversationKeys = emptySet()
                            }
                        ) { Text("取消") }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .phantomGlassSource(glassState)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when {
                isLoading && threads.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                threads.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.Email,
                        title = "此 App 尚無對話紀錄",
                        description = "當收到來自 $appLabel 的通知時，將會自動依寄件者分組顯示。"
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item(key = "threads_search_pill") {
                            PhantomSearchBarPill(
                                glassState = glassState,
                                query = threadSearchQuery,
                                placeholder = "搜尋 $appLabel 對話或寄件者…",
                                onClick = { showThreadSearch = true }
                            )
                        }
                        itemsIndexed(threads, key = { _, it -> it.conversationKey }) { idx, thread ->
                            val isSelected = thread.conversationKey in selectedConversationKeys
                            Box(modifier = Modifier.animateItem().phantomEnter(idx, key = thread.conversationKey)) {
                            SenderThreadCard(
                                packageName = packageName,
                                thread = thread,
                                avatarBytes = avatarMap[thread.displayTitle] ?: avatarMap[thread.conversationKey],
                                glassState = glassState,
                                isMultiSelectMode = isMultiSelectMode,
                                isSelected = isSelected,
                                onToggleSelect = { toggleSelection(thread.conversationKey) },
                                onOpenActions = { actionThread = thread },
                                onClick = {
                                    ctx.startActivity(
                                        ConversationActivity.createIntent(
                                            ctx = ctx,
                                            packageName = packageName,
                                            appLabel = appLabel,
                                            conversationKey = thread.conversationKey,
                                            displayTitle = thread.displayTitle
                                        )
                                    )
                                },
                                onLongClick = {
                                    haptic(HapticFeedbackType.LongPress)
                                    isMultiSelectMode = true
                                    selectedConversationKeys = setOf(thread.conversationKey)
                                }
                            )
                            }
                        }
                    }
                }
            }
        }
    }

    val tq = threadSearchQuery.trim()
    val matchedThreads = remember(threads, tq) {
        if (tq.isEmpty()) emptyList()
        else threads.filter {
            it.displayTitle.contains(tq, ignoreCase = true) ||
                it.lastPreview.contains(tq, ignoreCase = true)
        }
    }
    PhantomFullScreenSearchOverlay(
        visible = showThreadSearch,
        query = threadSearchQuery,
        onQueryChange = { threadSearchQuery = it },
        onDismiss = { showThreadSearch = false },
        placeholder = "搜尋 $appLabel 寄件者或最新訊息…",
        resultsContent = {
            if (tq.isEmpty()) {
                item(key = "thread_search_hint") {
                    Text(
                        text = "輸入寄件者或內容關鍵字搜尋此 App 對話。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (matchedThreads.isEmpty()) {
                item(key = "thread_search_empty") {
                    Text(
                        text = "找不到符合「$tq」的對話。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                items(matchedThreads, key = { "sr_${it.conversationKey}" }) { thread ->
                    SenderThreadCard(
                        packageName = packageName,
                        thread = thread,
                        avatarBytes = avatarMap[thread.displayTitle] ?: avatarMap[thread.conversationKey],
                        glassState = glassState,
                        onClick = {
                            showThreadSearch = false
                            ctx.startActivity(
                                ConversationActivity.createIntent(
                                    ctx = ctx,
                                    packageName = packageName,
                                    appLabel = appLabel,
                                    conversationKey = thread.conversationKey,
                                    displayTitle = thread.displayTitle
                                )
                            )
                        },
                        onLongClick = {}
                    )
                }
            }
        }
    )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SenderThreadCard(
    packageName: String,
    thread: SenderThreadUi,
    avatarBytes: ByteArray? = null,
    glassState: HazeState,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onOpenActions: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val ctx = LocalContext.current
    val is24h = PhantomPrefs.is24HourFormat(ctx)
    val timeText = remember(thread.lastTs, is24h) {
        PhantomPrefs.formatMessageTime(ctx, thread.lastTs, includeDate = true)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMultiSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() }
            )
        }
        val selectionModifier = if (isMultiSelectMode && isSelected) {
            Modifier.border(
                width = 1.5.dp,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(20.dp)
            )
        } else {
            Modifier
        }
        PhantomGlassCard(
            glassState = glassState,
            modifier = Modifier
                .weight(1f)
                .then(if (isMultiSelectMode) Modifier else Modifier.pressableScale())
                .then(selectionModifier)
                .combinedClickable(
                    onClick = { if (isMultiSelectMode) onToggleSelect() else onClick() },
                    onLongClick = { if (isMultiSelectMode) onToggleSelect() else onLongClick() }
                ),
            cornerRadius = 20.dp,
            contentPadding = 12.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SenderAvatarImage(
                    packageName = packageName,
                    sender = thread.displayTitle,
                    avatarBytes = avatarBytes,
                    size = 40.dp
                )
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (thread.isPinned) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "置頂",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = thread.displayTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${thread.count} 則",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (thread.unreadCount > 0) {
                                Spacer(Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Text(
                                        text = "未讀 ${thread.unreadCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!isMultiSelectMode) {
                            IconButton(
                                onClick = onOpenActions,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "對話選項",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = thread.lastPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (thread.latestTags.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        MessageTagChipRow(tags = thread.latestTags)
                    }
                }
            }
        }
    }
}
