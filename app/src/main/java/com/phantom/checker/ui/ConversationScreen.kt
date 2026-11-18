package com.phantom.checker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.withTransaction
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.MessageTagRow
import com.phantom.checker.data.TagEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.service.ReplyProxyManager
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.ui.components.SwipeToRevealRow
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val MAX_BATCH_SELECT = 100
private val TW_PHONE_REGEX = Regex("^09\\d{8}$")

class ConversationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiClient.init(applicationContext)
        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val appLabel = intent.getStringExtra(EXTRA_APP_LABEL).orEmpty().ifBlank { packageName }
        val conversationKey = intent.getStringExtra(EXTRA_CONVERSATION_KEY)
            ?: intent.getStringExtra(EXTRA_SENDER).orEmpty()
        val displayTitle = intent.getStringExtra(EXTRA_DISPLAY_TITLE)
            ?.takeIf { it.isNotBlank() }
            ?: conversationKey
        setContent {
            PhantomCheckTheme {
                ConversationScreen(
                    packageName = packageName,
                    appLabel = appLabel,
                    conversationKey = conversationKey,
                    displayTitle = displayTitle,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "extra_package_name"
        private const val EXTRA_APP_LABEL = "extra_app_label"
        private const val EXTRA_SENDER = "extra_sender"
        private const val EXTRA_CONVERSATION_KEY = "extra_conversation_key"
        private const val EXTRA_DISPLAY_TITLE = "extra_display_title"

        fun createIntent(
            ctx: Context,
            packageName: String,
            appLabel: String,
            conversationKey: String,
            displayTitle: String = conversationKey
        ): Intent {
            return Intent(ctx, ConversationActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_APP_LABEL, appLabel)
                putExtra(EXTRA_CONVERSATION_KEY, conversationKey)
                putExtra(EXTRA_DISPLAY_TITLE, displayTitle)
                putExtra(EXTRA_SENDER, conversationKey)
            }
        }
    }
}

private fun formatDateDivider(ts: Long): String {
    val msgCal = Calendar.getInstance().apply { timeInMillis = ts }
    val todayCal = Calendar.getInstance()
    val yesterdayCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: Calendar, b: Calendar) =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(msgCal, todayCal) -> "今天"
        sameDay(msgCal, yesterdayCal) -> "昨天"
        else -> SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(ts))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConversationScreen(
    packageName: String,
    appLabel: String,
    conversationKey: String,
    displayTitle: String = conversationKey,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var messages by remember { mutableStateOf(listOf<MessageEntity>()) }
    var allTags by remember { mutableStateOf(listOf<TagEntity>()) }
    var msgTagIdsMap by remember { mutableStateOf<Map<Long, Set<Long>>>(emptyMap()) }
    var msgTagsMap by remember { mutableStateOf<Map<Long, List<MessageTagRow>>>(emptyMap()) }
    var swipeToDeleteEnabled by remember { mutableStateOf(PhantomPrefs.isSwipeToDeleteEnabled(appCtx)) }
    val haptic = rememberPhantomHaptic()
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()

    var showMenu by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var sheetMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var deleteCandidate by remember { mutableStateOf<MessageEntity?>(null) }
    var reminderTarget by remember { mutableStateOf<MessageEntity?>(null) }
    var revealedMessageId by remember { mutableStateOf<Long?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var hasMoreOlder by remember { mutableStateOf(false) }
    var isLoadingMoreOlder by remember { mutableStateOf(false) }

    var isMultiSelectMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var replyText by rememberSaveable { mutableStateOf("") }
    var isSendingReply by remember { mutableStateOf(false) }

    val isImeVisible = WindowInsets.isImeVisible
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val toggleSelection: (Long) -> Unit = { id ->
        if (id in selectedMessageIds) {
            selectedMessageIds = selectedMessageIds - id
        } else if (selectedMessageIds.size >= MAX_BATCH_SELECT) {
            scope.launch {
                snackbarHostState.showSnackbar("單次最多選取 $MAX_BATCH_SELECT 則訊息")
            }
        } else {
            selectedMessageIds = selectedMessageIds + id
        }
    }

    BackHandler(enabled = isMultiSelectMode) {
        isMultiSelectMode = false
        selectedMessageIds = emptySet()
    }

    val shouldLoadOlder by remember {
        androidx.compose.runtime.derivedStateOf {
            listState.firstVisibleItemIndex <= 3
        }
    }

    val launchIntent = remember(packageName) {
        try {
            ctx.packageManager.getLaunchIntentForPackage(packageName)
        } catch (_: Exception) {
            null
        }
    }

    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                swipeToDeleteEnabled = PhantomPrefs.isSwipeToDeleteEnabled(appCtx)
                refreshTrigger++
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(packageName, conversationKey, refreshTrigger) {
        isLoading = true
        val pageSize = 200
        val loaded = withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val dao = db.messageDao()
            dao.markConversationRead(packageName, conversationKey)
            StatsForegroundService.startOrRefresh(appCtx)
            allTags = db.tagDao().all()
            val msgs = dao.byConversationKeyset(packageName, conversationKey, null, pageSize).asReversed()
            val ids = msgs.map { it.id }
            val tagRows = if (ids.isEmpty()) emptyList() else db.tagDao().tagsForMessages(ids)
            msgTagsMap = tagRows.groupBy { it.messageId }
            msgTagIdsMap = tagRows.groupBy { it.messageId }
                .mapValues { (_, list) -> list.map { it.tagId }.toSet() }
            msgs
        }
        messages = loaded
        hasMoreOlder = loaded.size >= pageSize
        isLoading = false
        if (loaded.isNotEmpty() && refreshTrigger <= 1) {
            listState.scrollToItem(loaded.lastIndex)
        }
    }

    LaunchedEffect(shouldLoadOlder, hasMoreOlder, isLoading, isLoadingMoreOlder) {
        if (!shouldLoadOlder || !hasMoreOlder || isLoading || isLoadingMoreOlder) return@LaunchedEffect
        val oldestTs = messages.firstOrNull()?.timestamp ?: return@LaunchedEffect
        isLoadingMoreOlder = true
        val pageSize = 200
        val (older, olderTagRows) = withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val olderMsgs = db.messageDao()
                .byConversationKeyset(packageName, conversationKey, oldestTs, pageSize)
                .asReversed()
            val olderIds = olderMsgs.map { it.id }
            val tagRows = if (olderIds.isEmpty()) emptyList() else db.tagDao().tagsForMessages(olderIds)
            olderMsgs to tagRows
        }
        if (older.isNotEmpty()) {
            val existingIds = messages.map { it.id }.toHashSet()
            val uniqueOlder = older.filter { it.id !in existingIds }
            if (uniqueOlder.isNotEmpty()) {
                val olderTagsGrouped = olderTagRows.groupBy { it.messageId }
                msgTagsMap = msgTagsMap + olderTagsGrouped
                msgTagIdsMap = msgTagIdsMap + olderTagsGrouped.mapValues { (_, list) ->
                    list.map { it.tagId }.toSet()
                }
                val prevFirstIdx = listState.firstVisibleItemIndex
                val prevOffset = listState.firstVisibleItemScrollOffset
                messages = uniqueOlder + messages
                listState.scrollToItem(prevFirstIdx + uniqueOlder.size, prevOffset)
            }
        }
        hasMoreOlder = older.size >= pageSize
        isLoadingMoreOlder = false
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清除此對話") },
            text = { Text("確定要永久刪除與「$displayTitle」的所有對話訊息嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByConversation(packageName, conversationKey)
                                    db.messageDao().deleteByConversation(packageName, conversationKey)
                                }
                                StatsForegroundService.startOrRefresh(appCtx)
                            }
                            showClearConfirm = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認清除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    deleteCandidate?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("刪除訊息") },
            text = { Text("確定要永久刪除這則訊息嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByMessage(target.id)
                                    db.messageDao().deleteById(target.id)
                                }
                                StatsForegroundService.startOrRefresh(appCtx)
                            }
                            deleteCandidate = null
                            refreshTrigger++
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showBatchDeleteConfirm && selectedMessageIds.isNotEmpty()) {
        val count = selectedMessageIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteConfirm = false },
            title = { Text("批次刪除訊息") },
            text = { Text("確定要永久刪除已選取的 $count 則訊息嗎？詐騙訊息將受保護保留，其餘刪除後無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        val ids = selectedMessageIds.toList()
                        scope.launch {
                            val (deletedCount, protectedCount) = withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    val deletableIds = db.messageDao().nonScamIdsFrom(ids)
                                    val protected = ids.size - deletableIds.size
                                    if (deletableIds.isNotEmpty()) {
                                        db.tagDao().deleteCrossRefsByMessageIds(deletableIds)
                                        db.messageDao().deleteNonScamByIds(deletableIds)
                                    }
                                    deletableIds.size to protected
                                }
                            }
                            StatsForegroundService.startOrRefresh(appCtx)
                            showBatchDeleteConfirm = false
                            isMultiSelectMode = false
                            selectedMessageIds = emptySet()
                            refreshTrigger++
                            val msgText = when {
                                deletedCount > 0 && protectedCount > 0 ->
                                    "已刪除 $deletedCount 則訊息，另有 $protectedCount 則詐騙訊息受保護保留"
                                deletedCount == 0 && protectedCount > 0 ->
                                    "所選 $protectedCount 則皆為詐騙訊息，已受保護保留"
                                else ->
                                    "已刪除 $deletedCount 則訊息"
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

    sheetMessage?.let { msg ->
        val currentTags = msgTagIdsMap[msg.id] ?: emptySet()
        ModalBottomSheet(onDismissRequest = { sheetMessage = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "訊息操作",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val nextStar = !msg.isStarred
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDatabaseHolder.db(appCtx).messageDao().setStarred(msg.id, nextStar)
                                }
                                sheetMessage = null
                                refreshTrigger++
                                snackbarHostState.showSnackbar(if (nextStar) "已加入星標" else "已移除星標")
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (msg.isStarred) "取消星標" else "加入星標")
                    }
                    OutlinedButton(
                        onClick = {
                            if (isAnalyzing) return@OutlinedButton
                            scope.launch {
                                isAnalyzing = true
                                val res = AiClient.analyzeRisk(
                                    sender = msg.sender,
                                    content = msg.content,
                                    context = appCtx,
                                    bypassCache = true
                                )
                                isAnalyzing = false
                                if (res != null) {
                                    withContext(Dispatchers.IO) {
                                        AppDatabaseHolder.db(appCtx).messageDao().update(
                                            msg.copy(
                                                riskScore = res.first,
                                                riskLabel = res.second,
                                                riskReason = res.third
                                            )
                                        )
                                    }
                                    sheetMessage = null
                                    refreshTrigger++
                                    snackbarHostState.showSnackbar("AI 分析完成：${res.second} (${res.first}分)")
                                } else {
                                    snackbarHostState.showSnackbar("AI 分析失敗，請確認 API 設定")
                                }
                            }
                        },
                        enabled = !isAnalyzing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isAnalyzing) "分析中…" else "AI 分析")
                    }
                }

                if (allTags.isNotEmpty()) {
                    Text("標籤", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        allTags.forEach { tag ->
                            val selected = tag.id in currentTags
                            val tagColor = parseTagColor(tag.colorHex)
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            val tagDao = AppDatabaseHolder.db(appCtx).tagDao()
                                            if (selected) {
                                                tagDao.removeTagFromMessage(msg.id, tag.id)
                                            } else {
                                                tagDao.addTagToMessage(MessageTagCrossRef(msg.id, tag.id))
                                            }
                                        }
                                        refreshTrigger++
                                    }
                                },
                                label = { Text(tag.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = tagColor.copy(alpha = 0.22f)
                                )
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            sheetMessage = null
                            reminderTarget = msg
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("稍後處理")
                    }
                    OutlinedButton(
                        onClick = {
                            sheetMessage = null
                            ctx.startActivity(MessageDetailActivity.createIntent(ctx, msg.id))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("詳情")
                    }
                    Button(
                        onClick = {
                            sheetMessage = null
                            deleteCandidate = msg
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("刪除")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }

    reminderTarget?.let { target ->
        ReminderPickerDialog(
            onDismiss = { reminderTarget = null },
            onSelectTimestamp = { ts ->
                reminderTarget = null
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabaseHolder.db(appCtx).messageDao().setRemindAt(target.id, ts)
                    }
                    com.phantom.checker.service.ReminderScheduler.schedule(appCtx, target.id, ts)
                    refreshTrigger++
                    val formatted = PhantomPrefs.formatMessageTime(appCtx, ts, includeDate = true)
                    snackbarHostState.showSnackbar("已排程提醒：$formatted")
                }
            }
        )
    }

    val glassState = remember { HazeState() }
    var showConvSearch by remember { mutableStateOf(false) }
    var convSearchQuery by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        topBar = {
            if (isMultiSelectMode) {
                TopAppBar(
                    expandedHeight = 52.dp,
                    title = {
                        Text(
                            text = "已選 ${selectedMessageIds.size}/$MAX_BATCH_SELECT 則",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isMultiSelectMode = false
                            selectedMessageIds = emptySet()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "取消選取"
                            )
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = messages.isNotEmpty(),
                            onClick = {
                                val loadedIds = messages.map { it.id }
                                val targetIds = if (loadedIds.size > MAX_BATCH_SELECT) {
                                    loadedIds.takeLast(MAX_BATCH_SELECT).toSet()
                                } else {
                                    loadedIds.toSet()
                                }
                                selectedMessageIds = targetIds
                                scope.launch {
                                    val msg = if (loadedIds.size > MAX_BATCH_SELECT) {
                                        "已載入 ${loadedIds.size} 則，單次最多選取 $MAX_BATCH_SELECT 則（已選 ${targetIds.size} 則）"
                                    } else {
                                        "已全選目前載入的 ${targetIds.size} 則訊息"
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
                        Column {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = appLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        if (launchIntent != null) {
                            TextButton(onClick = {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                ctx.startActivity(launchIntent)
                            }) {
                                Text("開啟原 App")
                            }
                        }
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                if (messages.isNotEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("批次選取") },
                                        onClick = {
                                            showMenu = false
                                            revealedMessageId = null
                                            isMultiSelectMode = true
                                            selectedMessageIds = emptySet()
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("清除此對話", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        showClearConfirm = true
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
                            enabled = selectedMessageIds.isNotEmpty(),
                            onClick = {
                                val ids = selectedMessageIds.toList()
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppDatabaseHolder.db(appCtx).messageDao().markReadByIds(ids)
                                    }
                                    StatsForegroundService.startOrRefresh(appCtx)
                                    isMultiSelectMode = false
                                    selectedMessageIds = emptySet()
                                    refreshTrigger++
                                    snackbarHostState.showSnackbar("已標記 ${ids.size} 則為已讀")
                                }
                            }
                        ) { Text("已讀") }
                        TextButton(
                            enabled = selectedMessageIds.isNotEmpty(),
                            onClick = {
                                val ids = selectedMessageIds.toList()
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        AppDatabaseHolder.db(appCtx).messageDao().setStarredByIds(ids, true)
                                    }
                                    isMultiSelectMode = false
                                    selectedMessageIds = emptySet()
                                    refreshTrigger++
                                    snackbarHostState.showSnackbar("已將 ${ids.size} 則加入星標")
                                }
                            }
                        ) { Text("星標") }
                        TextButton(
                            enabled = selectedMessageIds.isNotEmpty(),
                            onClick = { showBatchDeleteConfirm = true }
                        ) { Text("刪除", color = MaterialTheme.colorScheme.error) }
                        TextButton(
                            onClick = {
                                isMultiSelectMode = false
                                selectedMessageIds = emptySet()
                            }
                        ) { Text("取消") }
                    }
                }
            } else {
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = replyText,
                            onValueChange = { replyText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("回覆 $displayTitle…") },
                            maxLines = 3,
                            enabled = !isSendingReply,
                            shape = RoundedCornerShape(22.dp)
                        )
                        IconButton(
                            enabled = replyText.isNotBlank() && !isSendingReply,
                            onClick = {
                                val textToSend = replyText.trim()
                                if (textToSend.isEmpty() || isSendingReply) return@IconButton
                                isSendingReply = true
                                val lastMsg = messages.lastOrNull()
                                val targetSender = lastMsg?.sender?.ifBlank { displayTitle } ?: displayTitle
                                val targetSnippet = lastMsg?.content.orEmpty()
                                scope.launch {
                                    try {
                                        val res = withContext(Dispatchers.IO) {
                                            NotificationCollectorService.sendReplyToNotification(
                                                context = appCtx,
                                                packageName = packageName,
                                                sender = targetSender,
                                                contentSnippet = targetSnippet,
                                                replyText = textToSend,
                                                conversationKey = conversationKey
                                            )
                                        }
                                        when (res) {
                                            is NotificationCollectorService.Companion.ReplySendResult.Success -> {
                                                ReplyProxyManager.startOrRefreshSession(
                                                    ctx = appCtx,
                                                    packageName = packageName,
                                                    appLabel = appLabel,
                                                    sender = targetSender,
                                                    originalSnippet = targetSnippet,
                                                    initialReplyText = textToSend
                                                )
                                                replyText = ""
                                                snackbarHostState.showSnackbar("已送出")
                                            }
                                            is NotificationCollectorService.Companion.ReplySendResult.Unavailable -> {
                                                val isSmsFallback = packageName == "sms" ||
                                                    TW_PHONE_REGEX.matches(targetSender.trim()) ||
                                                    TW_PHONE_REGEX.matches(conversationKey.trim())
                                                if (isSmsFallback) {
                                                    val phone = when {
                                                        TW_PHONE_REGEX.matches(targetSender.trim()) -> targetSender.trim()
                                                        TW_PHONE_REGEX.matches(conversationKey.trim()) -> conversationKey.trim()
                                                        else -> targetSender.trim()
                                                    }
                                                    try {
                                                        withContext(Dispatchers.IO) {
                                                            @Suppress("DEPRECATION")
                                                            SmsManager.getDefault().sendTextMessage(
                                                                phone,
                                                                null,
                                                                textToSend,
                                                                null,
                                                                null
                                                            )
                                                        }
                                                        ReplyProxyManager.startOrRefreshSession(
                                                            ctx = appCtx,
                                                            packageName = packageName,
                                                            appLabel = appLabel,
                                                            sender = targetSender,
                                                            originalSnippet = targetSnippet,
                                                            initialReplyText = textToSend
                                                        )
                                                        replyText = ""
                                                        snackbarHostState.showSnackbar("已透過簡訊送出")
                                                    } catch (e: Exception) {
                                                        Log.e("PhantomReply", "ConversationScreen SMS fallback failed pkg=$packageName phone=$phone", e)
                                                        snackbarHostState.showSnackbar("無法送出簡訊：${e.message ?: "發送失敗"}，建議開啟原 App 回覆")
                                                    }
                                                } else {
                                                    Log.w("PhantomReply", "ConversationScreen reply unavailable pkg=$packageName reason=${res.reason}")
                                                    snackbarHostState.showSnackbar(res.reason)
                                                }
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("PhantomReply", "ConversationScreen reply failed pkg=$packageName convKey=$conversationKey", e)
                                        snackbarHostState.showSnackbar("回覆失敗：${e.message ?: "未知錯誤"}，建議開啟原 App 回覆")
                                    } finally {
                                        isSendingReply = false
                                    }
                                }
                            }
                        ) {
                            if (isSendingReply) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "送出回覆"
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        PhantomBackgroundBox(
            layer = com.phantom.checker.util.BackgroundLayer.CONVERSATION,
            glassState = glassState,
            modifier = Modifier.fillMaxSize()
        ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            PhantomSearchBarPill(
                glassState = glassState,
                query = convSearchQuery,
                placeholder = "搜尋此對話訊息…",
                onClick = { showConvSearch = true }
            )
            Spacer(Modifier.height(8.dp))
            if (messages.isNotEmpty()) {
                val scamCnt = messages.count { it.riskLabel == "scam" }
                val suspCnt = messages.count { it.riskLabel == "suspicious" }
                val safeCnt = messages.size - scamCnt - suspCnt
                val activeDays = remember(messages) {
                    val dayFmt = SimpleDateFormat("yyyyMMdd", Locale.US)
                    messages.map { dayFmt.format(Date(it.timestamp)) }.toSet().size
                }
                PhantomGlassCard(
                    glassState = glassState,
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    contentPadding = 10.dp
                ) {
                    Text(
                        text = "此對話共 ${messages.size} 則・活躍 $activeDays 天・安全 $safeCnt・疑似 $suspCnt・詐騙 $scamCnt",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (scamCnt > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            when {
                isLoading && messages.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                messages.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.Email,
                        title = "尚無對話訊息",
                        description = "與 $displayTitle 的通知訊息將顯示於此處。"
                    )
                }
                else -> {
                    val dateHeaders = remember(messages) {
                        var lastDay: String? = null
                        messages.map { msg ->
                            val day = formatDateDivider(msg.timestamp)
                            val show = day != lastDay
                            lastDay = day
                            if (show) day else null
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            messages,
                            key = { _, m -> m.id },
                            contentType = { _, _ -> "message_bubble" }
                        ) { idx, msg ->
                            val headerDay = dateHeaders.getOrNull(idx)
                            if (headerDay != null) {
                                val currentDay = headerDay
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    HorizontalDivider(modifier = Modifier.weight(1f))
                                    Text(
                                        text = currentDay,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    HorizontalDivider(modifier = Modifier.weight(1f))
                                }
                            }
                            val isSelected = msg.id in selectedMessageIds
                            val bubbleContent: @Composable () -> Unit = {
                                ChatBubbleItem(
                                    message = msg,
                                    glassState = glassState,
                                    tags = msgTagsMap[msg.id].orEmpty(),
                                    showSenderName = msg.sender.isNotBlank() && msg.sender != conversationKey,
                                    isMultiSelectMode = isMultiSelectMode,
                                    isSelected = isSelected,
                                    onToggleSelect = { toggleSelection(msg.id) },
                                    onClick = {
                                        revealedMessageId = null
                                        ctx.startActivity(MessageDetailActivity.createIntent(ctx, msg.id))
                                    },
                                    onLongClick = {
                                        haptic(HapticFeedbackType.LongPress)
                                        revealedMessageId = null
                                        sheetMessage = msg
                                    }
                                )
                            }
                            if (swipeToDeleteEnabled && !isMultiSelectMode) {
                                SwipeToRevealRow(
                                    revealed = revealedMessageId == msg.id,
                                    onReveal = { revealedMessageId = msg.id },
                                    onCollapse = { if (revealedMessageId == msg.id) revealedMessageId = null },
                                    actions = {
                                        Button(
                                            onClick = {
                                                revealedMessageId = null
                                                deleteCandidate = msg
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.error,
                                                contentColor = MaterialTheme.colorScheme.onError
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text("刪除")
                                        }
                                    }
                                ) {
                                    bubbleContent()
                                }
                            } else {
                                bubbleContent()
                            }
                        }
                    }
            }
        }
        }
    }

    val cq = convSearchQuery.trim()
    val matchedConvMsgs = remember(messages, cq) {
        if (cq.isEmpty()) emptyList()
        else messages.filter {
            it.content.contains(cq, ignoreCase = true) || it.sender.contains(cq, ignoreCase = true)
        }
    }
    PhantomFullScreenSearchOverlay(
        visible = showConvSearch,
        query = convSearchQuery,
        onQueryChange = { convSearchQuery = it },
        onDismiss = { showConvSearch = false },
        placeholder = "搜尋與 $displayTitle 的對話內容…",
        resultsContent = {
            if (cq.isEmpty()) {
                item(key = "conv_search_hint") {
                    Text(
                        text = "輸入關鍵字搜尋此對話中的所有訊息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else if (matchedConvMsgs.isEmpty()) {
                item(key = "conv_search_empty") {
                    Text(
                        text = "找不到符合「$cq」的訊息。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            } else {
                items(matchedConvMsgs, key = { "csr_${it.id}" }) { msg ->
                    ChatBubbleItem(
                        message = msg,
                        glassState = glassState,
                        tags = msgTagsMap[msg.id].orEmpty(),
                        onClick = {
                            showConvSearch = false
                            ctx.startActivity(MessageDetailActivity.createIntent(ctx, msg.id))
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
private fun ChatBubbleItem(
    message: MessageEntity,
    glassState: HazeState,
    tags: List<MessageTagRow> = emptyList(),
    showSenderName: Boolean = false,
    isMultiSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val ctx = LocalContext.current
    val isCompact = PhantomPrefs.getCardDensity(ctx) == com.phantom.checker.util.CardDensity.COMPACT
    val is24h = PhantomPrefs.is24HourFormat(ctx)
    val timeText = remember(message.timestamp, is24h) {
        PhantomPrefs.formatMessageTime(ctx, message.timestamp, includeDate = false)
    }
    val riskDotColor: Color? = when (message.riskLabel) {
        "scam" -> MaterialTheme.colorScheme.error
        "suspicious" -> Color(0xFFE6A100)
        else -> null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
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
                shape = RoundedCornerShape(16.dp)
            )
        } else {
            Modifier
        }
        PhantomGlassCard(
            glassState = glassState,
            modifier = Modifier
                .fillMaxWidth(if (isMultiSelectMode) 1f else 0.84f)
                .then(selectionModifier)
                .combinedClickable(
                    onClick = { if (isMultiSelectMode) onToggleSelect() else onClick() },
                    onLongClick = { if (isMultiSelectMode) onToggleSelect() else onLongClick() }
                ),
            cornerRadius = 16.dp,
            contentPadding = if (isCompact) 8.dp else 12.dp
        ) {
            if (showSenderName) {
                Text(
                    text = message.sender,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium
            )
            if (message.riskReason.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                PhantomMarkdownText(
                    markdown = message.riskReason,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (tags.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                MessageTagChipRow(tags = tags)
            }
            val otpCode = remember(message.content) {
                com.phantom.checker.util.OtpDetector.extractOtp(message.content)
            }
            if (otpCode != null) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = {
                        if (isMultiSelectMode) {
                            onToggleSelect()
                        } else {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            cm.setPrimaryClip(android.content.ClipData.newPlainText("otp_code", otpCode))
                            android.widget.Toast.makeText(ctx, "已複製驗證碼 $otpCode", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(
                        text = "複製驗證碼 $otpCode",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (message.remindAt != null && message.remindAt > System.currentTimeMillis()) {
                    Text(
                        text = "⏰ 待辦",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (message.isStarred) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "星標",
                        tint = Color(0xFFE6A100),
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (riskDotColor != null) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(riskDotColor)
                    )
                }
            }
        }
    }
}
