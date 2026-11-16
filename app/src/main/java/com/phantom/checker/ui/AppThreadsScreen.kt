package com.phantom.checker.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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

data class SenderThreadUi(
    val sender: String,
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
    var threads by remember { mutableStateOf(listOf<SenderThreadUi>()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var actionThread by remember { mutableStateOf<SenderThreadUi?>(null) }
    var deleteThreadConfirm by remember { mutableStateOf<SenderThreadUi?>(null) }

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
        threads = withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val dao = db.messageDao()
            val vipSenders = PhantomPrefs.getVipSenders(appCtx, packageName)
            val groups: List<SenderGroupRow> = dao.groupBySender(packageName)
            val allAppMsgs = dao.byApp(packageName)
            val latestBySender = allAppMsgs.groupBy { it.sender }
                .mapValues { (_, list) -> list.maxByOrNull { it.timestamp } }
            val latestIds = latestBySender.values.mapNotNull { it?.id }.distinct()
            val tagsMap = if (latestIds.isEmpty()) {
                emptyMap()
            } else {
                db.tagDao().tagsForMessages(latestIds).groupBy { it.messageId }
            }
            groups.map { g ->
                val latestMsg = latestBySender[g.sender]
                SenderThreadUi(
                    sender = g.sender,
                    count = g.cnt,
                    unreadCount = g.unreadCnt,
                    lastTs = g.lastTs,
                    lastPreview = latestMsg?.content.orEmpty(),
                    isPinned = g.sender in vipSenders,
                    latestTags = latestMsg?.let { tagsMap[it.id] }.orEmpty()
                )
            }.sortedWith(compareByDescending<SenderThreadUi> { it.isPinned }.thenByDescending { it.lastTs })
        }
        isLoading = false
    }

    actionThread?.let { target ->
        AlertDialog(
            onDismissRequest = { actionThread = null },
            title = { Text(target.sender, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            PhantomPrefs.toggleVipSender(appCtx, packageName, target.sender)
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
            text = { Text("確定要永久刪除與「${target.sender}」的所有對話訊息嗎？此操作無法復原。") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByConversation(packageName, target.sender)
                                    db.messageDao().deleteByConversation(packageName, target.sender)
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIconImage(
                            packageName = packageName,
                            contentDescription = appLabel,
                            size = 28.dp
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = appLabel,
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
    ) { padding ->
        val glassState = remember { HazeState() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .phantomGlassSource(glassState)
                .padding(padding)
                .padding(12.dp)
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
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(threads, key = { it.sender }) { thread ->
                            SenderThreadCard(
                                thread = thread,
                                glassState = glassState,
                                onClick = {
                                    ctx.startActivity(
                                        ConversationActivity.createIntent(
                                            ctx = ctx,
                                            packageName = packageName,
                                            appLabel = appLabel,
                                            sender = thread.sender
                                        )
                                    )
                                },
                                onLongClick = { actionThread = thread }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SenderThreadCard(
    thread: SenderThreadUi,
    glassState: HazeState,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val timeText = remember(thread.lastTs) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(thread.lastTs))
    }

    PhantomGlassCard(
        glassState = glassState,
        modifier = Modifier
            .fillMaxWidth()
            .pressableScale()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        cornerRadius = 20.dp,
        contentPadding = 12.dp
    ) {
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
                    text = thread.sender,
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
