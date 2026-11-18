package com.phantom.checker.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.withTransaction
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.IntelResultEntity
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.TagEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.service.AppDatabaseHolder
import com.phantom.checker.service.ReminderScheduler
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.shizuku.ShizukuKeepAlive
import com.phantom.checker.shizuku.ShizukuManager
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.OtpDetector
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AiClient.init(applicationContext)
        val messageId = intent.getLongExtra(EXTRA_MESSAGE_ID, -1L)
        setContent {
            PhantomCheckTheme {
                MessageDetailScreen(
                    messageId = messageId,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_MESSAGE_ID = "extra_message_id"

        fun createIntent(ctx: Context, messageId: Long): Intent {
            return Intent(ctx, MessageDetailActivity::class.java).apply {
                putExtra(EXTRA_MESSAGE_ID, messageId)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MessageDetailScreen(
    messageId: Long,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var message by remember { mutableStateOf<MessageEntity?>(null) }
    var intelResult by remember { mutableStateOf<IntelResultEntity?>(null) }
    var allTags by remember { mutableStateOf(listOf<TagEntity>()) }
    var selectedTagIds by remember { mutableStateOf(setOf<Long>()) }
    var tagSourceByTagId by remember { mutableStateOf(mapOf<Long, String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var isDeepAnalyzing by remember { mutableStateOf(false) }
    var deepAnalyzeJob by remember { mutableStateOf<Job?>(null) }
    var deepAnalysisMarkdown by remember { mutableStateOf<String?>(null) }
    var deepAnalysisError by remember { mutableStateOf<String?>(null) }
    var profiles by remember { mutableStateOf(AiClient.getProfiles(appCtx)) }
    var activeProfile by remember { mutableStateOf(AiClient.getActiveProfile(appCtx)) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var resumeRefreshTrigger by remember { mutableIntStateOf(0) }

    val lifecycleOwner = ctx as? LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeRefreshTrigger++
            }
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose {
            lifecycleOwner?.lifecycle?.removeObserver(observer)
        }
    }

    LaunchedEffect(messageId, resumeRefreshTrigger) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val loaded = db.messageDao().byId(messageId)
            if (loaded != null && !loaded.isRead) {
                db.messageDao().update(loaded.copy(isRead = true))
                StatsForegroundService.startOrRefresh(appCtx)
            }
            message = loaded?.copy(isRead = true)
            intelResult = db.intelDao().getResult(messageId)
            allTags = db.tagDao().all()
            val refs = db.tagDao().crossRefsForMessage(messageId)
            selectedTagIds = refs.map { it.tagId }.toSet()
            tagSourceByTagId = refs.associate { it.tagId to it.source }
        }
        isLoading = false
    }

    if (showDeleteConfirm && message != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("刪除訊息") },
            text = { Text("確定要永久刪除這則訊息嗎？") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.withTransaction {
                                    db.tagDao().deleteCrossRefsByMessage(messageId)
                                    db.messageDao().deleteById(messageId)
                                }
                                StatsForegroundService.startOrRefresh(appCtx)
                            }
                            showDeleteConfirm = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("確認刪除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showReminderPicker && message != null) {
        val target = message!!
        ReminderPickerDialog(
            onDismiss = { showReminderPicker = false },
            onSelectTimestamp = { ts ->
                showReminderPicker = false
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppDatabaseHolder.db(appCtx).messageDao().setRemindAt(target.id, ts)
                    }
                    ReminderScheduler.schedule(appCtx, target.id, ts)
                    message = target.copy(remindAt = ts)
                    val formatted = PhantomPrefs.formatMessageTime(appCtx, ts, includeDate = true)
                    snackbarHostState.showSnackbar("已排程提醒：$formatted")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                expandedHeight = 52.dp,
                title = {
                    Text(
                        text = "訊息詳情",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    message?.let { msg ->
                        IconButton(onClick = {
                            val nextStar = !msg.isStarred
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDatabaseHolder.db(appCtx).messageDao().setStarred(msg.id, nextStar)
                                }
                                message = msg.copy(isStarred = nextStar)
                                snackbarHostState.showSnackbar(if (nextStar) "已加入星標" else "已移除星標")
                            }
                        }) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "星標",
                                tint = if (msg.isStarred) Color(0xFFE6A100) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "刪除",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        val glassState = remember { HazeState() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .phantomGlassSource(glassState)
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            when {
                isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                message == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("找不到此訊息（可能已被刪除）", color = Color.Gray)
                    }
                }
                else -> {
                    val msg = message!!
                    val timeStr = remember(msg.timestamp) {
                        PhantomPrefs.formatMessageTime(ctx, msg.timestamp, includeDate = true)
                    }
                    val launchIntent = remember(msg.packageName) {
                        try {
                            ctx.packageManager.getLaunchIntentForPackage(msg.packageName)
                        } catch (_: Exception) {
                            null
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            contentPadding = 14.dp
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppIconImage(
                                    packageName = msg.packageName,
                                    contentDescription = msg.appLabel,
                                    size = 44.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = msg.sender,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${msg.appLabel}・$timeStr",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = msg.content,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            val otpCode = remember(msg.content) { OtpDetector.extractOtp(msg.content) }
                            if (otpCode != null) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedButton(
                                    onClick = {
                                        val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("phantom_otp", otpCode))
                                        Toast.makeText(ctx, "已複製驗證碼 $otpCode", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("複製驗證碼 $otpCode")
                                }
                            }
                            if (msg.remindAt != null && msg.remindAt > System.currentTimeMillis()) {
                                val remindStr = remember(msg.remindAt) {
                                    PhantomPrefs.formatMessageTime(ctx, msg.remindAt, includeDate = true)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "⏰ 已排程待辦提醒：$remindStr",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // 風險分數條與 AI 分析
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            contentPadding = 14.dp
                        ) {
                            val riskColor = when (msg.riskLabel) {
                                "scam" -> MaterialTheme.colorScheme.error
                                "suspicious" -> Color(0xFFE6A100)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            val riskText = when (msg.riskLabel) {
                                "scam" -> "高風險詐騙 (${msg.riskScore}/100)"
                                "suspicious" -> "疑似風險 (${msg.riskScore}/100)"
                                "safe" -> "安全 (${msg.riskScore}/100)"
                                else -> "尚未分析"
                            }
                            Text(
                                text = "AI 風險評估：$riskText",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = riskColor
                            )
                            Spacer(Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { (msg.riskScore.coerceIn(0, 100)) / 100f },
                                modifier = Modifier.fillMaxWidth(),
                                color = riskColor
                            )
                            if (msg.riskReason.isNotBlank()) {
                                Spacer(Modifier.height(8.dp))
                                PhantomMarkdownText(
                                    markdown = msg.riskReason,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            intelResult?.let { intel ->
                                val intelTimeStr = remember(intel.checkedAt) {
                                    SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault())
                                        .format(Date(intel.checkedAt))
                                }
                                Spacer(Modifier.height(6.dp))
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
                            if (hasSuspiciousUrlHint(msg.riskReason)) {
                                Spacer(Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = "可疑連結警告",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = "偵測到可疑連結，請勿輕易點擊或輸入個人資料",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (isAnalyzing) return@Button
                                    scope.launch {
                                        isAnalyzing = true
                                        val autoTagEnabled = PhantomPrefs.isAiAutoTagEnabled(appCtx)
                                        val (aiTags, aiRules) = withContext(Dispatchers.IO) {
                                            val db = AppDatabaseHolder.db(appCtx)
                                            val tags = if (autoTagEnabled) db.tagDao().aiManagedTags() else emptyList()
                                            val rules = db.aiRuleDao().active()
                                            tags to rules
                                        }
                                        val res = AiClient.analyzeMessage(
                                            sender = msg.sender,
                                            content = msg.content,
                                            tags = aiTags,
                                            rules = aiRules,
                                            enableAutoTag = autoTagEnabled,
                                            context = appCtx,
                                            bypassCache = true
                                        )
                                        isAnalyzing = false
                                        if (res != null) {
                                            val updated = msg.copy(
                                                riskScore = res.riskScore,
                                                riskLabel = res.riskLabel,
                                                riskReason = res.riskReason
                                            )
                                            val refreshedRefs = withContext(Dispatchers.IO) {
                                                val db = AppDatabaseHolder.db(appCtx)
                                                db.withTransaction {
                                                    db.messageDao().update(updated)
                                                    if (autoTagEnabled && res.tagIds.isNotEmpty()) {
                                                        val existingRefs = db.tagDao().crossRefsForMessage(msg.id)
                                                        val manualIds = existingRefs
                                                            .filter { it.source == MessageTagCrossRef.SOURCE_MANUAL }
                                                            .map { it.tagId }
                                                            .toSet()
                                                        val refs = res.tagIds
                                                            .filter { it !in manualIds }
                                                            .map { tid ->
                                                                MessageTagCrossRef(
                                                                    messageId = msg.id,
                                                                    tagId = tid,
                                                                    source = MessageTagCrossRef.SOURCE_AUTO
                                                                )
                                                            }
                                                        if (refs.isNotEmpty()) {
                                                            db.tagDao().addTagsToMessageBatch(refs)
                                                        }
                                                    }
                                                }
                                                db.tagDao().crossRefsForMessage(msg.id)
                                            }
                                            message = updated
                                            selectedTagIds = refreshedRefs.map { it.tagId }.toSet()
                                            tagSourceByTagId = refreshedRefs.associate { it.tagId to it.source }
                                            snackbarHostState.showSnackbar("AI 分析完成：${res.riskLabel} (${res.riskScore}分)")
                                        } else {
                                            snackbarHostState.showSnackbar("AI 分析失敗，請確認 API 設定或網路")
                                        }
                                    }
                                },
                                enabled = !isAnalyzing,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressableScale()
                            ) {
                                if (isAnalyzing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("分析中…")
                                } else {
                                    Text("AI 分析此則")
                                }
                            }
                        }

                        // AI 深度分析（段落式風險解說）
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            contentPadding = 14.dp
                        ) {
                            Text(
                                text = "AI 深度分析",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "產生段落式風險解說（詐騙類型、可疑點、建議處理動作），計入每日 AI 額度。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            AiProviderSwitcherChip(
                                profiles = profiles,
                                activeProfile = activeProfile,
                                onSelectProfile = { selected ->
                                    AiClient.setActiveProvider(appCtx, selected.id)
                                    activeProfile = selected
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已切換模型：${selected.name}・${selected.model}")
                                    }
                                }
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        if (isDeepAnalyzing) return@OutlinedButton
                                        if (AiClient.getActiveProfile(appCtx) == null) {
                                            deepAnalysisError = "尚未設定 AI Provider API Key，請至設定頁配置"
                                            return@OutlinedButton
                                        }
                                        if (!PhantomPrefs.canUseAiTodayAndIncrement(appCtx)) {
                                            val limit = PhantomPrefs.getDailyAiLimit(appCtx)
                                            val msgLimit = "今日 AI 額度已達上限（$limit 次），請明日再試或於設定調整"
                                            deepAnalysisError = msgLimit
                                            scope.launch { snackbarHostState.showSnackbar(msgLimit) }
                                            return@OutlinedButton
                                        }
                                        deepAnalyzeJob = scope.launch {
                                            isDeepAnalyzing = true
                                            deepAnalysisError = null
                                            try {
                                                val intelSummary = intelResult?.let { intel ->
                                                    if (intel.isHit) {
                                                        "命中 ${intel.sourceLabel}（${intel.detail}）"
                                                    } else {
                                                        "已查證公開情報，無命中紀錄"
                                                    }
                                                }
                                                val senderHistorySummary = withContext(Dispatchers.IO) {
                                                    val senderKey = msg.sender.trim()
                                                    if (senderKey.isEmpty()) return@withContext null
                                                    val past = AppDatabaseHolder.db(appCtx).messageDao().search(senderKey)
                                                        .filter { it.id != msg.id && it.sender.equals(senderKey, ignoreCase = true) }
                                                        .sortedByDescending { it.timestamp }
                                                    if (past.isEmpty()) {
                                                        "此寄件者過去無其他紀錄"
                                                    } else {
                                                        val highRiskCount = past.count { it.riskScore >= 70 }
                                                        val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())
                                                        val foldedPast = com.phantom.checker.util.NotificationFoldUtil.fold(past)
                                                        val recentLines = foldedPast.take(3).joinToString("；") { entry ->
                                                            val m = entry.representative
                                                            if (entry.isFolded) {
                                                                "${com.phantom.checker.util.NotificationFoldUtil.formatFoldedHeadline(entry)} 風險${m.riskScore}分"
                                                            } else {
                                                                "${fmt.format(Date(m.timestamp))} 風險${m.riskScore}分「${m.content.replace("\n", " ").take(40)}」"
                                                            }
                                                        }
                                                        "過去共 ${past.size} 則（高風險 $highRiskCount 則），近期：$recentLines"
                                                    }
                                                }
                                                val result = AiClient.deepAnalyzeMessage(
                                                    sender = msg.sender,
                                                    content = msg.content,
                                                    appLabel = msg.appLabel,
                                                    intelSummary = intelSummary,
                                                    senderHistorySummary = senderHistorySummary,
                                                    context = appCtx
                                                )
                                                result.onSuccess { md ->
                                                    deepAnalysisMarkdown = md
                                                }.onFailure { err ->
                                                    if (err.message?.contains("HTTP 413") == true) {
                                                        PhantomPrefs.refundTodayAiUsage(appCtx)
                                                    }
                                                    deepAnalysisError = err.message ?: "深度分析失敗"
                                                }
                                            } catch (_: CancellationException) {
                                                deepAnalysisError = "已取消深度分析"
                                            } finally {
                                                isDeepAnalyzing = false
                                                deepAnalyzeJob = null
                                            }
                                        }
                                    },
                                    enabled = !isDeepAnalyzing,
                                    modifier = Modifier
                                        .weight(1f)
                                        .pressableScale()
                                ) {
                                    if (isDeepAnalyzing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("深度分析中…")
                                    } else {
                                        Text("一鍵 AI 深度分析")
                                    }
                                }
                                if (isDeepAnalyzing) {
                                    TextButton(
                                        onClick = {
                                            deepAnalyzeJob?.cancel()
                                        }
                                    ) {
                                        Text("取消")
                                    }
                                }
                            }
                            if (!deepAnalysisError.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = deepAnalysisError!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (!deepAnalysisMarkdown.isNullOrBlank()) {
                                Spacer(Modifier.height(10.dp))
                                PhantomMarkdownText(
                                    markdown = deepAnalysisMarkdown!!,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        val plain = stripMarkdown(deepAnalysisMarkdown!!)
                                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("deep_ai_analysis", plain))
                                        scope.launch { snackbarHostState.showSnackbar("已複製深度分析結果") }
                                    }) {
                                        Text("複製解說")
                                    }
                                }
                            }
                        }

                        // 標籤選擇
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 20.dp,
                            contentPadding = 14.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "自訂標籤",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = {
                                    ctx.startActivity(Intent(ctx, TagManagementActivity::class.java))
                                }) {
                                    Text("管理標籤")
                                }
                            }
                            if (allTags.isEmpty()) {
                                Text(
                                    text = "尚未建立任何標籤，點擊右上角「管理標籤」新增。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    allTags.forEach { tag ->
                                        val isSelected = tag.id in selectedTagIds
                                        val isAuto = isSelected && tagSourceByTagId[tag.id] == MessageTagCrossRef.SOURCE_AUTO
                                        val tagColor = parseTagColor(tag.colorHex)
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        val tagDao = AppDatabaseHolder.db(appCtx).tagDao()
                                                        if (isSelected) {
                                                            tagDao.removeTagFromMessage(msg.id, tag.id)
                                                        } else {
                                                            tagDao.addTagToMessage(
                                                                MessageTagCrossRef(
                                                                    messageId = msg.id,
                                                                    tagId = tag.id,
                                                                    source = MessageTagCrossRef.SOURCE_MANUAL
                                                                )
                                                            )
                                                        }
                                                    }
                                                    if (isSelected) {
                                                        selectedTagIds = selectedTagIds - tag.id
                                                        tagSourceByTagId = tagSourceByTagId - tag.id
                                                    } else {
                                                        selectedTagIds = selectedTagIds + tag.id
                                                        tagSourceByTagId = tagSourceByTagId + (tag.id to MessageTagCrossRef.SOURCE_MANUAL)
                                                    }
                                                }
                                            },
                                            label = { Text(if (isAuto) "${tag.name} (AI)" else tag.name) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = tagColor.copy(alpha = 0.22f)
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 操作列
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("phantom_message", msg.content))
                                    Toast.makeText(ctx, "已複製訊息內容", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .pressableScale()
                            ) {
                                Text("複製內容")
                            }
                            if (launchIntent != null) {
                                OutlinedButton(
                                    onClick = {
                                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        ctx.startActivity(launchIntent)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .pressableScale()
                                ) {
                                    Text("開啟原 App")
                                }
                            }
                        }

                        var showWhitelistFlow by remember { mutableStateOf(false) }
                        if (showWhitelistFlow) {
                            WhitelistAddDialogFlow(
                                sender = msg.sender,
                                appLabel = msg.appLabel,
                                packageName = msg.packageName,
                                content = msg.content,
                                onDismiss = { showWhitelistFlow = false },
                                onConfirm = { isSenderScope ->
                                    scope.launch {
                                        val updatedMsg = msg.copy(
                                            riskScore = 10,
                                            riskLabel = "safe",
                                            riskReason = "使用者手動加入白名單"
                                        )
                                        withContext(Dispatchers.IO) {
                                            val db = AppDatabaseHolder.db(appCtx)
                                            val ruleText = if (isSenderScope) {
                                                "白名單寄件者：${msg.sender}（${msg.appLabel}）"
                                            } else {
                                                msg.content.trim().take(120).ifBlank { "白名單訊息（${msg.sender}）" }
                                            }
                                            db.aiRuleDao().insert(
                                                AiRuleEntity(
                                                    ruleText = ruleText,
                                                    kind = AiRuleEntity.KIND_WHITELIST,
                                                    sender = if (isSenderScope) msg.sender.trim() else null,
                                                    packageName = msg.packageName
                                                )
                                            )
                                            com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                            db.messageDao().update(updatedMsg)
                                        }
                                        message = updatedMsg
                                        showWhitelistFlow = false
                                        snackbarHostState.showSnackbar("已加入白名單")
                                    }
                                }
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showWhitelistFlow = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .pressableScale()
                            ) {
                                Text("加入白名單")
                            }
                            OutlinedButton(
                                onClick = { showReminderPicker = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .pressableScale()
                            ) {
                                Text(if (msg.remindAt != null) "修改提醒時間" else "稍後處理")
                            }
                            if (msg.remindAt != null) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                AppDatabaseHolder.db(appCtx).messageDao().setRemindAt(msg.id, null)
                                            }
                                            ReminderScheduler.cancel(appCtx, msg.id)
                                            message = msg.copy(remindAt = null)
                                            snackbarHostState.showSnackbar("已取消待辦提醒")
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .pressableScale()
                                ) {
                                    Text("取消提醒")
                                }
                            }
                        }

                        val shizukuReady = remember(msg.packageName) {
                            ShizukuManager.init(appCtx)
                            ShizukuManager.state.value.isReady && msg.packageName != appCtx.packageName
                        }
                        var showSuspendConfirm by remember { mutableStateOf(false) }

                        if (showSuspendConfirm) {
                            AlertDialog(
                                onDismissRequest = { showSuspendConfirm = false },
                                title = { Text("確認凍結來源 App") },
                                text = { Text("確定要透過 Shizuku 凍結「${msg.appLabel} (${msg.packageName})」嗎？凍結後可至「設定 → Shizuku 進階」解除凍結。") },
                                confirmButton = {
                                    Button(onClick = {
                                        scope.launch {
                                            val res = ShizukuKeepAlive.suspendTargetApp(appCtx, msg.packageName)
                                            showSuspendConfirm = false
                                            snackbarHostState.showSnackbar(
                                                if (res.isSuccess) "已凍結 ${msg.appLabel}" else "凍結失敗：${res.output}"
                                            )
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

                        if (shizukuReady) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            val res = ShizukuKeepAlive.forceStopTargetApp(appCtx, msg.packageName)
                                            snackbarHostState.showSnackbar(
                                                if (res.isSuccess) "已強行停止 ${msg.appLabel}" else "操作失敗：${res.output}"
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Shizuku 強行停止")
                                }
                                OutlinedButton(
                                    onClick = { showSuspendConfirm = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("凍結來源 App", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
