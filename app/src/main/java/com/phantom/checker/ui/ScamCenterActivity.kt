package com.phantom.checker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import dev.chrisbanes.haze.HazeState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.IntelResultEntity
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.shizuku.ShizukuKeepAlive
import com.phantom.checker.shizuku.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ScamCenterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PhantomCheckTheme {
                ScamCenterScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScamCenterScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext

    var scamMessages by remember { mutableStateOf(listOf<MessageEntity>()) }
    var weekScamCount by remember { mutableIntStateOf(0) }
    var lastWeekScamCount by remember { mutableIntStateOf(0) }
    var monthScamCount by remember { mutableIntStateOf(0) }
    var top3RiskyApps by remember { mutableStateOf(listOf<com.phantom.checker.data.AppRiskRow>()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var selectedMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var intelResults by remember { mutableStateOf(mapOf<Long, IntelResultEntity>()) }

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

    LaunchedEffect(refreshTrigger) {
        isLoading = true
        val now = System.currentTimeMillis()
        val weekAgo = now - java.util.concurrent.TimeUnit.DAYS.toMillis(7)
        val twoWeeksAgo = now - java.util.concurrent.TimeUnit.DAYS.toMillis(14)
        val monthAgo = now - java.util.concurrent.TimeUnit.DAYS.toMillis(30)
        withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val dao = db.messageDao()
            val scams = dao.scams()
            scamMessages = scams
            intelResults = if (scams.isNotEmpty()) {
                db.intelDao().getResults(scams.map { it.id }).associateBy { it.messageId }
            } else {
                emptyMap()
            }
            weekScamCount = dao.scamCountSince(weekAgo)
            lastWeekScamCount = dao.scamCountBetween(twoWeeksAgo, weekAgo)
            monthScamCount = dao.scamCountSince(monthAgo)
            top3RiskyApps = dao.topRiskyApps().take(3)
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 52.dp,
                title = {
                    Text(
                        text = "詐騙警示中心",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
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
                    IconButton(onClick = { refreshTrigger++ }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "重新整理"
                        )
                    }
                }
            )
        }
    ) { padding ->
        val glassState = remember { HazeState() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .phantomGlassSource(glassState)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when {
                isLoading && scamMessages.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                scamMessages.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Default.Warning,
                        title = "目前尚無高風險詐騙訊息",
                        description = "當 AI 偵測到高風險訊息時，將自動收錄於此處。"
                    )
                }
                else -> {
                    PhantomGlassCard(
                        glassState = glassState,
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        contentPadding = 12.dp
                    ) {
                        Text(
                            text = "本週詐騙 $weekScamCount 則・本月詐騙 $monthScamCount 則",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                        val diff = weekScamCount - lastWeekScamCount
                        val trendText = when {
                            lastWeekScamCount == 0 && weekScamCount == 0 -> "本週 vs 上週：→ 持平 (0%)"
                            lastWeekScamCount == 0 -> "本週 vs 上週：↑ +100%（上週 0 則）"
                            diff == 0 -> "本週 vs 上週：→ 持平 (0%)"
                            diff > 0 -> {
                                val pct = (diff * 100) / lastWeekScamCount
                                "本週 vs 上週：↑ +$pct%（上週 $lastWeekScamCount 則）"
                            }
                            else -> {
                                val pct = ((-diff) * 100) / lastWeekScamCount
                                "本週 vs 上週：↓ -$pct%（上週 $lastWeekScamCount 則）"
                            }
                        }
                        Text(
                            text = trendText,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = if (diff > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                        if (top3RiskyApps.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "最高風險 App Top 3：${top3RiskyApps.joinToString("、") { "${it.appLabel.ifBlank { it.packageName }} (${it.cnt})" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize(spring())
                            .phantomGlassSurface(
                                state = glassState,
                                fallbackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                                outlineColor = MaterialTheme.colorScheme.error,
                                cornerRadius = 20.dp
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "共 ${scamMessages.size} 筆高風險詐騙訊息，點擊卡片可查看完整風險分析",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(scamMessages, key = { it.id }) { msg ->
                            ScamMessageCard(
                                message = msg,
                                intelResult = intelResults[msg.id],
                                glassState = glassState,
                                onClick = { selectedMessage = msg }
                            )
                        }
                    }
                }
            }
        }
    }

    selectedMessage?.let { msg ->
        ScamDetailDialog(
            message = msg,
            intelResult = intelResults[msg.id],
            onDismiss = { selectedMessage = null },
            onWhitelistAdded = {
                selectedMessage = null
                refreshTrigger++
            }
        )
    }
}

@Composable
private fun RiskScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.error
    ) {
        Text(
            text = "風險 $score",
            color = MaterialTheme.colorScheme.onError,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ScamMessageCard(
    message: MessageEntity,
    intelResult: IntelResultEntity?,
    glassState: HazeState,
    onClick: () -> Unit
) {
    val timeText = remember(message.timestamp) {
        SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .animateContentSize(spring())
            .phantomGlassSurface(
                state = glassState,
                fallbackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                outlineColor = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                cornerRadius = 20.dp
            )
            .clickable(onClick = onClick)
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onErrorContainer) {
            Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "[${message.appLabel}] ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                    )
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    RiskScoreBadge(score = message.riskScore)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (message.riskReason.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stripMarkdown(message.riskReason),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            intelResult?.let { intel ->
                val intelTimeStr = remember(intel.checkedAt) {
                    SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(intel.checkedAt))
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (intel.isHit) {
                        "情報來源：${intel.sourceLabel}（$intelTimeStr）"
                    } else {
                        "防詐情報：已查證，無命中紀錄（$intelTimeStr）"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = "點擊查看詳情與複製內容 ›",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Medium
            )
            }
        }
    }
}

@Composable
private fun ScamDetailDialog(
    message: MessageEntity,
    intelResult: IntelResultEntity?,
    onDismiss: () -> Unit,
    onWhitelistAdded: () -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val fullTimeText = remember(message.timestamp) {
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp))
    }
    var showSuspendConfirm by remember { mutableStateOf(false) }
    var showWhitelistFlow by remember { mutableStateOf(false) }
    val shizukuReady = remember {
        ShizukuManager.init(appCtx)
        ShizukuManager.state.value.isReady && message.packageName != appCtx.packageName
    }

    if (showWhitelistFlow) {
        WhitelistAddDialogFlow(
            sender = message.sender,
            appLabel = message.appLabel,
            packageName = message.packageName,
            content = message.content,
            onDismiss = { showWhitelistFlow = false },
            onConfirm = { isSenderScope ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        val db = AppDatabaseHolder.db(appCtx)
                        val ruleText = if (isSenderScope) {
                            "白名單寄件者：${message.sender}（${message.appLabel}）"
                        } else {
                            message.content.trim().take(120).ifBlank { "白名單訊息（${message.sender}）" }
                        }
                        db.aiRuleDao().insert(
                            AiRuleEntity(
                                ruleText = ruleText,
                                kind = AiRuleEntity.KIND_WHITELIST,
                                sender = if (isSenderScope) message.sender.trim() else null,
                                packageName = message.packageName
                            )
                        )
                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                        db.messageDao().update(
                            message.copy(
                                riskScore = 10,
                                riskLabel = "safe",
                                riskReason = "使用者手動加入白名單"
                            )
                        )
                    }
                    showWhitelistFlow = false
                    Toast.makeText(ctx, "已加入白名單", Toast.LENGTH_SHORT).show()
                    onWhitelistAdded()
                }
            }
        )
    }

    if (showSuspendConfirm) {
        AlertDialog(
            onDismissRequest = { showSuspendConfirm = false },
            title = { Text("確認凍結來源 App") },
            text = { Text("確定要透過 Shizuku 凍結「${message.appLabel} (${message.packageName})」嗎？凍結後可至「設定 → Shizuku 進階」解除凍結。") },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val res = ShizukuKeepAlive.suspendTargetApp(appCtx, message.packageName)
                        showSuspendConfirm = false
                        Toast.makeText(
                            ctx,
                            if (res.isSuccess) "已凍結 ${message.appLabel}" else "凍結失敗：${res.output}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text("確認凍結")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSuspendConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "詐騙風險詳情",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                RiskScoreBadge(score = message.riskScore)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(label = "寄件者", value = message.sender)
                DetailRow(
                    label = "來源 App",
                    value = if (message.packageName.isNotBlank() && message.appLabel != message.packageName) {
                        "${message.appLabel} (${message.packageName})"
                    } else {
                        message.appLabel
                    }
                )
                DetailRow(label = "接收時間", value = fullTimeText)
                DetailRow(label = "風險分數", value = "${message.riskScore} / 100")

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text(
                    text = "完整訊息內容",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Text(
                    text = "AI 風險分析理由",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                ) {
                    PhantomMarkdownText(
                        markdown = message.riskReason.ifBlank { "（無詳細分析理由）" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                intelResult?.let { intel ->
                    val intelTimeStr = remember(intel.checkedAt) {
                        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(intel.checkedAt))
                    }
                    Text(
                        text = if (intel.isHit) {
                            "防詐情報命中：${intel.sourceLabel}（查詢時間：$intelTimeStr）"
                        } else {
                            "防詐情報：已查證，無命中紀錄（查詢時間：$intelTimeStr）"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (intel.isHit) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                OutlinedButton(
                    onClick = { showWhitelistFlow = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("加入白名單（這不是詐騙）")
                }

                if (shizukuReady) {
                    HorizontalDivider()
                    Text(
                        text = "Shizuku 進階處置",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = {
                                scope.launch {
                                    val res = ShizukuKeepAlive.forceStopTargetApp(appCtx, message.packageName)
                                    Toast.makeText(
                                        ctx,
                                        if (res.isSuccess) "已強行停止 ${message.appLabel}" else "操作失敗：${res.output}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("強行停止")
                        }
                        TextButton(
                            onClick = { showSuspendConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("凍結 App", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("scam_message", message.content))
                    Toast.makeText(ctx, "已複製訊息內容", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("複製訊息內容")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color.Gray,
            modifier = Modifier.width(76.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}
