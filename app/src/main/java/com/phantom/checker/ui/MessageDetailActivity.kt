package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomEnter
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.room.withTransaction
import com.phantom.checker.data.WhitelistRuleHelper
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
import com.phantom.checker.util.AiPurpose
import com.phantom.checker.util.OtpDetector
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import com.phantom.checker.widget.WidgetUpdater
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private data class MessageDetailSnapshot(
    val message: MessageEntity?,
    val intelResult: IntelResultEntity?,
    val allTags: List<TagEntity>,
    val selectedTagIds: Set<Long>,
    val tagSourceByTagId: Map<Long, String>,
    val calendarSuggestion: com.phantom.checker.util.CalendarBridge.NotificationCalendarSuggestion?,
    val calendarWritableCals: List<com.phantom.checker.util.CalendarBridge.CalendarAccountInfo>,
    val calendarConflicts: List<com.phantom.checker.util.CalendarBridge.CalendarEventInstance>,
    val calendarAllDayEvents: List<com.phantom.checker.util.CalendarBridge.CalendarEventInstance>
)

class MessageDetailActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.phantom.checker.util.AppLocaleHelper.wrapContext(newBase))
    }
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
    val resCtx = LocalContext.current
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
    val settingsVersion by PhantomSettingsBus.version.collectAsState()
    var resumeRefreshTrigger by remember { mutableIntStateOf(0) }
    val profiles = remember(settingsVersion, resumeRefreshTrigger) { AiClient.getProfiles(appCtx) }
    var activeProfile by remember(settingsVersion, resumeRefreshTrigger) {
        mutableStateOf(AiClient.getProfileForPurpose(appCtx, AiPurpose.DEEP_ANALYSIS))
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var showNeverMemoDialog by remember { mutableStateOf(false) }
    var hasNeverMemoRule by remember { mutableStateOf(false) }
    var calendarSuggestion by remember { mutableStateOf<com.phantom.checker.util.CalendarBridge.NotificationCalendarSuggestion?>(null) }
    var calendarWritableCals by remember { mutableStateOf<List<com.phantom.checker.util.CalendarBridge.CalendarAccountInfo>>(emptyList()) }
    var calendarConflicts by remember { mutableStateOf<List<com.phantom.checker.util.CalendarBridge.CalendarEventInstance>>(emptyList()) }
    var calendarAllDayEvents by remember { mutableStateOf<List<com.phantom.checker.util.CalendarBridge.CalendarEventInstance>>(emptyList()) }

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
        val snapshot = withContext(Dispatchers.IO) {
            val db = AppDatabaseHolder.db(appCtx)
            val loaded = db.messageDao().byId(messageId)
            if (loaded != null && !loaded.isRead) {
                db.messageDao().update(loaded.copy(isRead = true))
                StatsForegroundService.startOrRefresh(appCtx)
                WidgetUpdater.scheduleRefresh(appCtx)
            }
            val loadedIntel = db.intelDao().getResult(messageId)
            val loadedAllTags = db.tagDao().all()
            val refs = db.tagDao().crossRefsForMessage(messageId)
            val sug = if (loaded != null) {
                com.phantom.checker.util.CalendarBridge.evaluateMessageForSuggestion(appCtx, loaded)
            } else null
            val writableCals = if (sug != null) {
                com.phantom.checker.util.CalendarBridge.listSelectedWritableCalendars(appCtx)
            } else emptyList()
            val conflicts = if (sug != null) {
                com.phantom.checker.util.CalendarBridge.findConflictingEvents(
                    ctx = appCtx,
                    startMs = sug.startMs,
                    endMs = sug.endMs
                )
            } else emptyList()
            val allDayEvents = if (sug != null && conflicts.isEmpty()) {
                com.phantom.checker.util.CalendarBridge.findAllDayEventsInWindow(
                    ctx = appCtx,
                    startMs = sug.startMs,
                    endMs = sug.endMs
                )
            } else emptyList()
            MessageDetailSnapshot(
                message = loaded?.copy(isRead = true),
                intelResult = loadedIntel,
                allTags = loadedAllTags,
                selectedTagIds = refs.map { it.tagId }.toSet(),
                tagSourceByTagId = refs.associate { it.tagId to it.source },
                calendarSuggestion = sug,
                calendarWritableCals = writableCals,
                calendarConflicts = conflicts,
                calendarAllDayEvents = allDayEvents
            )
        }
        message = snapshot.message
        intelResult = snapshot.intelResult
        allTags = snapshot.allTags
        selectedTagIds = snapshot.selectedTagIds
        tagSourceByTagId = snapshot.tagSourceByTagId
        calendarSuggestion = snapshot.calendarSuggestion
        calendarWritableCals = snapshot.calendarWritableCals
        calendarConflicts = snapshot.calendarConflicts
        calendarAllDayEvents = snapshot.calendarAllDayEvents
        isLoading = false
    }

    if (showDeleteConfirm && message != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.common_delete_message)) },
            text = { Text(stringResource(R.string.conv_s_ee4417)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                val db = AppDatabaseHolder.db(appCtx)
                                db.messageDao().deleteByIdWithOrphans(messageId)
                                StatsForegroundService.startOrRefresh(appCtx)
                                WidgetUpdater.scheduleRefresh(appCtx)
                            }
                            showDeleteConfirm = false
                            onBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.common_confirm_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
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
                    WidgetUpdater.scheduleRefresh(appCtx)
                    message = target.copy(remindAt = ts)
                    val formatted = PhantomPrefs.formatMessageTime(appCtx, ts, includeDate = true)
                    snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_ed678a, formatted))
                }
            }
        )
    }

    com.phantom.checker.ui.components.PhantomPageScaffold(
        title = stringResource(R.string.detail_s_0c5f2b),
        onBack = onBack,
        topBarStyle = com.phantom.checker.ui.components.PhantomTopBarStyle.Small,
        containerColor = MaterialTheme.colorScheme.background,
        actions = {
            message?.let { msg ->
                IconButton(onClick = {
                    val nextStar = !msg.isStarred
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AppDatabaseHolder.db(appCtx).messageDao().setStarred(msg.id, nextStar)
                        }
                        message = msg.copy(isStarred = nextStar)
                        snackbarHostState.showSnackbar(if (nextStar) resCtx.getString(R.string.common_starred_added) else resCtx.getString(R.string.common_starred_removed))
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = resCtx.getString(R.string.common_star),
                        tint = if (msg.isStarred) MaterialTheme.colorScheme.warning else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = resCtx.getString(R.string.common_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
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
                        Text(stringResource(R.string.detail_s_46946d), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            modifier = Modifier.phantomEnter(0, key = "md_card_0").fillMaxWidth(),
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
                                        Toast.makeText(ctx, resCtx.getString(R.string.common_s_8b8320, otpCode), Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text(stringResource(R.string.common_s_f1f3ce, otpCode))
                                }
                            }
                            if (msg.remindAt != null && msg.remindAt > System.currentTimeMillis()) {
                                val remindStr = remember(msg.remindAt) {
                                    PhantomPrefs.formatMessageTime(ctx, msg.remindAt, includeDate = true)
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.detail_s_9526b4, remindStr),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        // #329 通知自動轉行程「📅 加入日曆」一鍵建議卡（含撞期檢查與 ACTION_INSERT fallback）
                        calendarSuggestion?.let { sug ->
                            var editCalTitle by remember(sug.messageId) { mutableStateOf(sug.title) }
                            var editCalNotes by remember(sug.messageId) { mutableStateOf(sug.notes) }
                            var selectedCalId by remember(sug.messageId, calendarWritableCals) {
                                mutableStateOf(calendarWritableCals.firstOrNull()?.id)
                            }
                            val hasWritePerm = remember(sug.messageId, resumeRefreshTrigger) {
                                com.phantom.checker.util.CalendarBridge.hasWritePermission(appCtx)
                            }
                            val startStr = remember(sug.startMs) {
                                PhantomPrefs.formatMessageTime(ctx, sug.startMs, includeDate = true)
                            }
                            val endStr = remember(sug.endMs) {
                                PhantomPrefs.formatMessageTime(ctx, sug.endMs, includeDate = false)
                            }
                            PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.phantomEnter(1, key = "md_card_1").fillMaxWidth(),
                                cornerRadius = 20.dp,
                                contentPadding = 14.dp
                            ) {
                                Text(
                                    text = resCtx.getString(R.string.detail_s_d46585),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = resCtx.getString(R.string.detail_s_a99e2d, startStr, endStr),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editCalTitle,
                                    onValueChange = { editCalTitle = it },
                                    label = { Text(stringResource(R.string.detail_s_f205ec)) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = editCalNotes,
                                    onValueChange = { editCalNotes = it },
                                    label = { Text(stringResource(R.string.common_remark)) },
                                    minLines = 1,
                                    maxLines = 3,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                if (calendarWritableCals.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = resCtx.getString(R.string.detail_s_7de6b6),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        calendarWritableCals.forEach { cal ->
                                            FilterChip(
                                                selected = selectedCalId == cal.id,
                                                onClick = { selectedCalId = cal.id },
                                                label = { Text(cal.displayName, style = MaterialTheme.typography.labelSmall) }
                                            )
                                        }
                                    }
                                }
                                if (calendarConflicts.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = resCtx.getString(R.string.detail_s_93ec95, com.phantom.checker.util.CalendarBridge.formatConflictSummary(calendarConflicts)),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                } else {
                                    com.phantom.checker.util.CalendarBridge.formatAllDayInfoNote(calendarAllDayEvents)?.let { infoNote ->
                                        Spacer(Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = infoNote,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(8.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            com.phantom.checker.util.CalendarBridge.dismissSuggestion(sug.messageId)
                                            calendarSuggestion = null
                                        }
                                    ) {
                                        Text(resCtx.getString(R.string.common_skip))
                                    }
                                    Button(
                                        enabled = editCalTitle.isNotBlank(),
                                        onClick = {
                                            val targetCalId = selectedCalId
                                            if (hasWritePerm && targetCalId != null) {
                                                scope.launch {
                                                    val res = com.phantom.checker.util.CalendarBridge.insertEvent(
                                                        ctx = appCtx,
                                                        calendarId = targetCalId,
                                                        title = editCalTitle.trim(),
                                                        startMs = sug.startMs,
                                                        endMs = sug.endMs,
                                                        allDay = false,
                                                        description = editCalNotes.trim()
                                                    )
                                                    com.phantom.checker.util.CalendarBridge.dismissSuggestion(sug.messageId)
                                                    calendarSuggestion = null
                                                    res.fold(
                                                        onSuccess = {
                                                            snackbarHostState.showSnackbar(resCtx.getString(R.string.detail_s_48a1d7, editCalTitle.trim()))
                                                        },
                                                        onFailure = { err ->
                                                            snackbarHostState.showSnackbar(resCtx.getString(R.string.detail_s_0db8f4, err.localizedMessage ?: resCtx.getString(R.string.common_unknown_error)))
                                                        }
                                                    )
                                                }
                                            } else {
                                                runCatching {
                                                    val intent = com.phantom.checker.util.CalendarBridge.buildInsertEventIntent(
                                                        title = editCalTitle.trim(),
                                                        startMs = sug.startMs,
                                                        endMs = sug.endMs,
                                                        allDay = false,
                                                        description = editCalNotes.trim()
                                                    )
                                                    ctx.startActivity(intent)
                                                    com.phantom.checker.util.CalendarBridge.dismissSuggestion(sug.messageId)
                                                    calendarSuggestion = null
                                                }.onFailure {
                                                    Toast.makeText(ctx, resCtx.getString(R.string.detail_s_670c4a), Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    ) {
                                        Text(
                                            if (hasWritePerm && selectedCalId != null) {
                                                if (calendarConflicts.isNotEmpty()) resCtx.getString(R.string.detail_s_edb2ff) else resCtx.getString(R.string.detail_s_7d0c50)
                                            } else {
                                                resCtx.getString(R.string.detail_s_f12b81)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // 風險分數條與 AI 分析
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.phantomEnter(2, key = "md_card_2").fillMaxWidth(),
                            cornerRadius = 20.dp,
                            contentPadding = 14.dp
                        ) {
                            val riskColor = when (msg.riskLabel) {
                                "scam" -> MaterialTheme.colorScheme.error
                                "warning", "suspicious" -> MaterialTheme.colorScheme.warning
                                else -> MaterialTheme.colorScheme.primary
                            }
                            val riskText = when (msg.riskLabel) {
                                "scam" -> stringResource(R.string.detail_s_526177, msg.riskScore)
                                "warning", "suspicious" -> stringResource(R.string.detail_s_5ed046, msg.riskScore)
                                "safe" -> stringResource(R.string.detail_s_ec55a0, msg.riskScore)
                                else -> stringResource(R.string.detail_s_c767ca)
                            }
                            Text(
                                text = stringResource(R.string.detail_s_e36c7f, riskText),
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
                                        resCtx.getString(R.string.detail_s_de193c, intel.sourceLabel, intelTimeStr)
                                    } else {
                                        resCtx.getString(R.string.detail_s_d3956d, intelTimeStr)
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
                                            contentDescription = stringResource(R.string.detail_s_9de2c3),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = stringResource(R.string.detail_s_3f1d02),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                            var showWhitelistedAnalyzeConfirm by remember { mutableStateOf(false) }

                            fun runDeepAnalysis(wasWhitelisted: Boolean) {
                                if (isAnalyzing) return
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
                                        val annotatedReason = if (wasWhitelisted) {
                                            "手動觸發（白名單寄件者）：${res.riskReason}"
                                        } else {
                                            res.riskReason
                                        }
                                        val updated = msg.copy(
                                            riskScore = res.riskScore,
                                            riskLabel = res.riskLabel,
                                            riskReason = annotatedReason
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
                                        snackbarHostState.showSnackbar(resCtx.getString(R.string.main_s_b57b71, res.riskLabel, res.riskScore))
                                    } else {
                                        snackbarHostState.showSnackbar(resCtx.getString(R.string.detail_s_b5b956))
                                    }
                                }
                            }

                            if (showWhitelistedAnalyzeConfirm) {
                                AlertDialog(
                                    onDismissRequest = { showWhitelistedAnalyzeConfirm = false },
                                    title = { Text(stringResource(R.string.detail_s_ad2b97)) },
                                    text = {
                                        Text(
                                            stringResource(R.string.detail_s_846c64, msg.sender.ifBlank { msg.appLabel })
                                        )
                                    },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            showWhitelistedAnalyzeConfirm = false
                                            runDeepAnalysis(wasWhitelisted = true)
                                        }) {
                                            Text(stringResource(R.string.detail_s_aaed09))
                                        }
                                    },
                                    dismissButton = {
                                        TextButton(onClick = { showWhitelistedAnalyzeConfirm = false }) {
                                            Text(stringResource(R.string.common_cancel))
                                        }
                                    }
                                )
                            }

                            Spacer(Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    if (isAnalyzing) return@Button
                                    scope.launch {
                                        val whitelistedNow = withContext(Dispatchers.IO) {
                                            val db = AppDatabaseHolder.db(appCtx)
                                            val wlRules = db.aiRuleDao().activeWhitelistAll()
                                            com.phantom.checker.network.IntelClient.isMessageWhitelisted(
                                                sender = msg.sender,
                                                packageName = msg.packageName,
                                                appLabel = msg.appLabel,
                                                content = msg.content,
                                                aiRules = wlRules,
                                                indicators = com.phantom.checker.network.IndicatorExtractor.extract(msg.content)
                                            )
                                        }
                                        if (whitelistedNow) {
                                            showWhitelistedAnalyzeConfirm = true
                                        } else {
                                            runDeepAnalysis(wasWhitelisted = false)
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
                                    Text(stringResource(R.string.common_analyzing))
                                } else {
                                    Text(stringResource(R.string.detail_s_6e2adf))
                                }
                            }
                        }

                        // AI 深度分析（段落式風險解說）
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.phantomEnter(3, key = "md_card_3").fillMaxWidth(),
                            cornerRadius = 20.dp,
                            contentPadding = 14.dp
                        ) {
                            Text(
                                text = stringResource(R.string.detail_s_936432),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.detail_s_67d2f1),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            AiProviderSwitcherChip(
                                profiles = profiles,
                                activeProfile = activeProfile,
                                label = stringResource(R.string.detail_s_1bb186),
                                onSelectProfile = { selected ->
                                    PhantomPrefs.setPurposeProfileId(appCtx, AiPurpose.DEEP_ANALYSIS.id, selected.id)
                                    PhantomSettingsBus.notifyChanged()
                                    activeProfile = selected
                                    scope.launch {
                                        snackbarHostState.showSnackbar(resCtx.getString(R.string.detail_s_924807, selected.name, selected.model))
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
                                        if (AiClient.getProfileForPurpose(appCtx, AiPurpose.DEEP_ANALYSIS) == null) {
                                            deepAnalysisError = resCtx.getString(R.string.detail_s_c36500)
                                            return@OutlinedButton
                                        }
                                        if (!PhantomPrefs.canUseAiTodayAndIncrement(appCtx)) {
                                            val limit = PhantomPrefs.getDailyAiLimit(appCtx)
                                            val msgLimit = resCtx.getString(R.string.detail_s_26c1d9, limit)
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
                                                    deepAnalysisError = err.message ?: resCtx.getString(R.string.detail_s_130dda)
                                                }
                                            } catch (_: CancellationException) {
                                                deepAnalysisError = resCtx.getString(R.string.detail_s_706914)
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
                                        Text(stringResource(R.string.detail_s_8ad2a6))
                                    } else {
                                        Text(stringResource(R.string.detail_s_9d4fa9))
                                    }
                                }
                                if (isDeepAnalyzing) {
                                    TextButton(
                                        onClick = {
                                            deepAnalyzeJob?.cancel()
                                        }
                                    ) {
                                        Text(stringResource(R.string.common_cancel))
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
                                        scope.launch { snackbarHostState.showSnackbar(resCtx.getString(R.string.detail_s_46b0ca)) }
                                    }) {
                                        Text(stringResource(R.string.detail_s_8accd1))
                                    }
                                }
                            }
                        }

                        // 標籤選擇
                        PhantomGlassCard(
                            glassState = glassState,
                            modifier = Modifier.phantomEnter(4, key = "md_card_4").fillMaxWidth(),
                            cornerRadius = 20.dp,
                            contentPadding = 14.dp
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.detail_s_f65f7e),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                TextButton(onClick = {
                                    ctx.startActivity(Intent(ctx, TagManagementActivity::class.java))
                                }) {
                                    Text(stringResource(R.string.common_manage_tags))
                                }
                            }
                            if (allTags.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.detail_s_4d490f),
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
                                                        WidgetUpdater.scheduleRefresh(appCtx)
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
                                    Toast.makeText(ctx, resCtx.getString(R.string.detail_s_00399e), Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .pressableScale()
                            ) {
                                Text(stringResource(R.string.common_copy_content))
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
                                    Text(stringResource(R.string.common_open_original_app))
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
                                        val upsertRes = withContext(Dispatchers.IO) {
                                            val db = AppDatabaseHolder.db(appCtx)
                                            val ruleText = if (isSenderScope) {
                                                "白名單寄件者：${msg.sender}（${msg.appLabel}）"
                                            } else {
                                                msg.content.trim().take(120).ifBlank { "白名單訊息（${msg.sender}）" }
                                            }
                                            val res = WhitelistRuleHelper.upsertWhitelist(
                                                dao = db.aiRuleDao(),
                                                ruleText = ruleText,
                                                sender = if (isSenderScope) msg.sender.trim() else null,
                                                packageName = msg.packageName,
                                                onCacheInvalidate = {
                                                    com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                                }
                                            )
                                            if (res.isSuccess) {
                                                db.messageDao().update(updatedMsg)
                                                WidgetUpdater.scheduleRefresh(appCtx)
                                            }
                                            res
                                        }
                                        if (upsertRes.isSuccess) {
                                            message = updatedMsg
                                        }
                                        showWhitelistFlow = false
                                        snackbarHostState.showSnackbar(upsertRes.message)
                                    }
                                }
                            )
                        }

                        var showDecisionFeedback by remember { mutableStateOf(false) }
                        if (showDecisionFeedback) {
                            DecisionFeedbackDialog(
                                message = msg,
                                onDismiss = { showDecisionFeedback = false },
                                onFeedbackApplied = { updatedMsg, toastMsg ->
                                    if (updatedMsg != null) {
                                        message = updatedMsg
                                    }
                                    showDecisionFeedback = false
                                    scope.launch { snackbarHostState.showSnackbar(toastMsg) }
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
                                Text(stringResource(R.string.common_add_whitelist))
                            }
                            OutlinedButton(
                                onClick = { showDecisionFeedback = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .pressableScale()
                            ) {
                                Text(stringResource(R.string.common_report_misjudgment))
                            }
                            OutlinedButton(
                                onClick = { showReminderPicker = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .pressableScale()
                            ) {
                                Text(if (msg.remindAt != null) stringResource(R.string.common_edit_reminder_time) else stringResource(R.string.common_remind_later))
                            }
                            if (msg.remindAt != null) {
                                OutlinedButton(
                                    onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) {
                                                AppDatabaseHolder.db(appCtx).messageDao().setRemindAt(msg.id, null)
                                            }
                                            ReminderScheduler.cancel(appCtx, msg.id)
                                            WidgetUpdater.scheduleRefresh(appCtx)
                                            message = msg.copy(remindAt = null)
                                            snackbarHostState.showSnackbar(resCtx.getString(R.string.detail_s_f677a6))
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .pressableScale()
                                ) {
                                    Text(stringResource(R.string.common_cancel_reminder))
                                }
                            }
                        }

                        LaunchedEffect(msg.id, resumeRefreshTrigger) {
                            hasNeverMemoRule = withContext(Dispatchers.IO) {
                                val activeRules = AppDatabaseHolder.db(appCtx).memoRuleDao().active()
                                com.phantom.checker.util.MemoRuleEngine.match(
                                    rules = activeRules,
                                    packageName = msg.packageName,
                                    sender = msg.sender,
                                    content = msg.content
                                )?.action == com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER
                            }
                        }

                        OutlinedButton(
                            onClick = { showNeverMemoDialog = true },
                            enabled = !hasNeverMemoRule,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressableScale()
                        ) {
                            Text(if (hasNeverMemoRule) stringResource(R.string.detail_s_b2b0dd) else stringResource(R.string.common_s_5ad72e))
                        }

                        if (showNeverMemoDialog) {
                            NeverAutoMemoConfirmDialog(
                                packageName = msg.packageName,
                                appLabel = msg.appLabel,
                                sender = msg.sender,
                                defaultKeyword = msg.content,
                                onDismiss = { showNeverMemoDialog = false },
                                onConfirm = { pkg, snd, kw ->
                                    showNeverMemoDialog = false
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            AppDatabaseHolder.db(appCtx).memoRuleDao().insert(
                                                com.phantom.checker.data.MemoRuleEntity(
                                                    packageName = pkg,
                                                    sender = snd,
                                                    keyword = kw,
                                                    action = com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER,
                                                    isEnabled = true,
                                                    createdAt = System.currentTimeMillis(),
                                                    source = com.phantom.checker.data.MemoRuleEntity.SOURCE_DETAIL
                                                )
                                            )
                                            com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                        }
                                        hasNeverMemoRule = true
                                        snackbarHostState.showSnackbar(resCtx.getString(R.string.notes_s_b617bb))
                                    }
                                }
                            )
                        }

                        val shizukuReady = remember(msg.packageName) {
                            ShizukuManager.init(appCtx)
                            ShizukuManager.state.value.isReady && msg.packageName != appCtx.packageName
                        }
                        var showSuspendConfirm by remember { mutableStateOf(false) }

                        if (showSuspendConfirm) {
                            AlertDialog(
                                onDismissRequest = { showSuspendConfirm = false },
                                title = { Text(stringResource(R.string.detail_s_d95488)) },
                                text = { Text(stringResource(R.string.detail_s_e4bc66, msg.appLabel, msg.packageName)) },
                                confirmButton = {
                                    Button(onClick = {
                                        scope.launch {
                                            val res = ShizukuKeepAlive.suspendTargetApp(appCtx, msg.packageName)
                                            showSuspendConfirm = false
                                            snackbarHostState.showSnackbar(
                                                if (res.isSuccess) resCtx.getString(R.string.detail_s_5b13aa, msg.appLabel) else resCtx.getString(R.string.detail_s_ec28fb, res.output)
                                            )
                                        }
                                    }) {
                                        Text(stringResource(R.string.common_confirm_freeze))
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showSuspendConfirm = false }) {
                                        Text(stringResource(R.string.common_cancel))
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
                                                if (res.isSuccess) resCtx.getString(R.string.detail_s_59632d, msg.appLabel) else resCtx.getString(R.string.detail_s_d45d8d, res.output)
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.detail_s_e491cf))
                                }
                                OutlinedButton(
                                    onClick = { showSuspendConfirm = true },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(stringResource(R.string.detail_s_d8f9ce), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
