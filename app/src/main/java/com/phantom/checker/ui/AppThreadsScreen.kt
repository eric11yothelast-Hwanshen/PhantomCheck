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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

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
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
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
    val resCtx = LocalContext.current
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
                snackbarHostState.showSnackbar(resCtx.getString(R.string.threads_s_1add49, MAX_BATCH_SELECT))
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
            // 改以各對話索引 LIMIT 1 取代 dao.byApp(packageName) 全表載入，避免大量訊息 OOM
            val latestByConv = groups.associate { g ->
                g.conversationKey to dao.byConversationKeyset(packageName, g.conversationKey, null, 1).firstOrNull()
            }
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
                        Text(if (target.isPinned) stringResource(R.string.threads_s_6c1483) else stringResource(R.string.threads_s_ed02fb))
                    }
                    OutlinedButton(
                        onClick = {
                            actionThread = null
                            deleteThreadConfirm = target
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.threads_s_475252), color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { actionThread = null }) {
                    Text(stringResource(R.string.common_close))
                }
            }
        )
    }

    deleteThreadConfirm?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteThreadConfirm = null },
            title = { Text(stringResource(R.string.threads_s_475252)) },
            text = { Text(stringResource(R.string.threads_s_a5821f, target.displayTitle)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.messageDao().deleteByConversation(packageName, target.conversationKey)
                                    db.messageDao().deleteAllMessageOrphans()
                                }
                                StatsForegroundService.startOrRefresh(appCtx)
                                com.phantom.checker.widget.WidgetUpdater.scheduleRefresh(appCtx)
                            }
                            deleteThreadConfirm = null
                            refreshTrigger++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteThreadConfirm = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.threads_s_3d8da0)) },
            text = { Text(stringResource(R.string.threads_s_94bb7c, appLabel)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.messageDao().deleteByApp(packageName)
                                    db.messageDao().deleteAllMessageOrphans()
                                }
                                StatsForegroundService.startOrRefresh(appCtx)
                                com.phantom.checker.widget.WidgetUpdater.scheduleRefresh(appCtx)
                            }
                            showDeleteConfirm = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    if (showBatchDeleteConfirm && selectedConversationKeys.isNotEmpty()) {
        val count = selectedConversationKeys.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text(stringResource(R.string.threads_s_cdc200)) },
            text = { Text(stringResource(R.string.threads_s_ba30a9, count)) },
            confirmButton = {
                Button(
                    onClick = {
                        val keys = selectedConversationKeys.toList()
                        scope.launch {
                            val (deletedCount, protectedCount) = withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    var protected = 0
                                    var deleted = 0
                                    keys.chunked(500).forEach { batch ->
                                        protected += db.messageDao().countScamByConversations(packageName, batch)
                                        deleted += db.messageDao().deleteNonScamByConversations(packageName, batch)
                                    }
                                    if (deleted > 0) {
                                        db.messageDao().deleteAllMessageOrphans()
                                    }
                                    deleted to protected
                                }
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            com.phantom.checker.widget.WidgetUpdater.scheduleRefresh(appCtx)
                            showBatchDeleteConfirm = false
                            isMultiSelectMode = false
                            selectedConversationKeys = emptySet()
                            refreshTrigger++
                            val msgText = when {
                                deletedCount > 0 && protectedCount > 0 ->
                                    resCtx.getString(R.string.threads_s_8b7bd6, deletedCount, protectedCount)
                                deletedCount == 0 && protectedCount > 0 ->
                                    resCtx.getString(R.string.threads_s_17d2b5, protectedCount)
                                else ->
                                    resCtx.getString(R.string.threads_s_908dd9, deletedCount)
                            }
                            snackbarHostState.showSnackbar(msgText)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
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
                    expandedHeight = com.phantom.checker.ui.components.AuroraTokens.SmallTopBarHeight,
                    title = {
                        Text(
                            text = stringResource(R.string.threads_s_38eeab, selectedConversationKeys.size, MAX_BATCH_SELECT),
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
                                contentDescription = stringResource(R.string.common_deselect)
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
                                        resCtx.getString(R.string.threads_s_0d6447, MAX_BATCH_SELECT, targetKeys.size)
                                    } else {
                                        resCtx.getString(R.string.threads_s_acf7af, targetKeys.size)
                                    }
                                    snackbarHostState.showSnackbar(msg)
                                }
                            }
                        ) {
                            Text(stringResource(R.string.common_select_all))
                        }
                    }
                )
            } else {
                TopAppBar(
                    expandedHeight = com.phantom.checker.ui.components.AuroraTokens.SmallTopBarHeight,
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
                                contentDescription = stringResource(R.string.common_back)
                            )
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.common_more_actions))
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (threads.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.common_batch_select)) },
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
                                            stringResource(R.string.threads_s_3d8da0),
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
                                        val dao = AppDatabaseHolder.db(appCtx).messageDao()
                                        keys.chunked(500).forEach { batch ->
                                            dao.markConversationsRead(packageName, batch)
                                        }
                                    }
                                    StatsForegroundService.startOrRefresh(appCtx)
                                    com.phantom.checker.widget.WidgetUpdater.scheduleRefresh(appCtx)
                                    isMultiSelectMode = false
                                    selectedConversationKeys = emptySet()
                                    refreshTrigger++
                                    snackbarHostState.showSnackbar(resCtx.getString(R.string.threads_s_41658f, keys.size))
                                }
                            }
                        ) { Text(stringResource(R.string.common_mark_read)) }
                        TextButton(
                            enabled = selectedConversationKeys.isNotEmpty(),
                            onClick = { showBatchDeleteConfirm = true }
                        ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error) }
                        TextButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedConversationKeys = emptySet()
                            }
                        ) { Text(stringResource(R.string.common_cancel)) }
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
                        title = stringResource(R.string.threads_s_b220e9),
                        description = stringResource(R.string.threads_s_39025f, appLabel)
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
                                placeholder = stringResource(R.string.threads_s_94f32c, appLabel),
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
        placeholder = stringResource(R.string.threads_s_1ef71e, appLabel),
        resultsContent = {
            if (tq.isEmpty()) {
                item(key = "thread_search_hint") {
                    Text(
                        text = stringResource(R.string.threads_s_7a24ca),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (matchedThreads.isEmpty()) {
                item(key = "thread_search_empty") {
                    Text(
                        text = stringResource(R.string.threads_s_aa5e0f, tq),
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
                                        text = stringResource(R.string.common_pin),
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
                                    text = stringResource(R.string.main_s_ddadbf, thread.count),
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
                                        text = stringResource(R.string.messages_s_b7a0c3, thread.unreadCount),
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
                                onClick = onOpenActions
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = stringResource(R.string.threads_s_f1c594),
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
