package com.phantom.checker.ui

import com.phantom.checker.ui.components.phantomEnter
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.OutlinedTextField
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
import com.phantom.checker.data.WhitelistRuleHelper
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
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

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

    com.phantom.checker.ui.components.PhantomPageScaffold(
        title = stringResource(R.string.catalog_ai_scam_center_title),
        onBack = onBack,
        topBarStyle = com.phantom.checker.ui.components.PhantomTopBarStyle.Medium,
        containerColor = MaterialTheme.colorScheme.background,
        actions = {
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.common_refresh)
                )
            }
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
                        title = stringResource(R.string.scam_center_s_20a0cd),
                        description = stringResource(R.string.scam_center_s_e8a8ea)
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
                            text = stringResource(R.string.scam_center_s_6fc667, weekScamCount, monthScamCount),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(4.dp))
                        val diff = weekScamCount - lastWeekScamCount
                        val trendText = when {
                            lastWeekScamCount == 0 && weekScamCount == 0 -> stringResource(R.string.scam_center_s_a71698)
                            lastWeekScamCount == 0 -> stringResource(R.string.scam_center_s_af04da)
                            diff == 0 -> stringResource(R.string.scam_center_s_a71698)
                            diff > 0 -> {
                                val pct = (diff * 100) / lastWeekScamCount
                                stringResource(R.string.scam_center_s_7ad735, pct, lastWeekScamCount)
                            }
                            else -> {
                                val pct = ((-diff) * 100) / lastWeekScamCount
                                stringResource(R.string.scam_center_s_7ccbfd, pct, lastWeekScamCount)
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
                                text = stringResource(R.string.scam_center_s_3fe0ff, top3RiskyApps.joinToString("、") { "${it.appLabel.ifBlank { it.packageName }} (${it.cnt})" }),
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
                                text = stringResource(R.string.scam_center_s_4ff991, scamMessages.size),
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
                        itemsIndexed(scamMessages, key = { _, it -> it.id }) { idx, msg ->
                            Box(modifier = Modifier.animateItem().phantomEnter(idx, key = msg.id)) {
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
            text = stringResource(R.string.scam_center_s_f094d2, score),
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
    val resCtx = LocalContext.current
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
                        resCtx.getString(R.string.scam_center_s_1191ba, intel.sourceLabel, intelTimeStr)
                    } else {
                        resCtx.getString(R.string.scam_center_s_a93acf, intelTimeStr)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = resCtx.getString(R.string.scam_center_s_168334),
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
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    val fullTimeText = remember(message.timestamp) {
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp))
    }
    var showSuspendConfirm by remember { mutableStateOf(false) }
    var showWhitelistFlow by remember { mutableStateOf(false) }
    var showImmunityConfirm by remember { mutableStateOf(false) }
    var showDecisionFeedback by remember { mutableStateOf(false) }
    if (showDecisionFeedback) {
        DecisionFeedbackDialog(
            message = message,
            onDismiss = { showDecisionFeedback = false },
            onFeedbackApplied = { _, toastMsg ->
                showDecisionFeedback = false
                Toast.makeText(ctx, toastMsg, Toast.LENGTH_SHORT).show()
                onWhitelistAdded()
            }
        )
    }
    val initialImmunityDraft = remember(message.id) {
        com.phantom.checker.data.MemoryEngine.buildScamImmunityDraftFromMessage(message)
    }
    var immunitySubject by remember(message.id) { mutableStateOf(initialImmunityDraft.first) }
    var immunityContent by remember(message.id) { mutableStateOf(initialImmunityDraft.second) }
    val shizukuReady = remember {
        ShizukuManager.init(appCtx)
        ShizukuManager.state.value.isReady && message.packageName != appCtx.packageName
    }

    if (showImmunityConfirm) {
        AlertDialog(
            onDismissRequest = { showImmunityConfirm = false },
            title = { Text(stringResource(R.string.scam_center_s_7825bd)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.scam_center_s_8c0e94),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = immunitySubject,
                        onValueChange = { immunitySubject = it },
                        label = { Text(stringResource(R.string.scam_center_s_31626a)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = immunityContent,
                        onValueChange = { immunityContent = it },
                        label = { Text(stringResource(R.string.scam_center_s_e2b4e7)) },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = immunitySubject.isNotBlank() && immunityContent.isNotBlank(),
                    onClick = {
                        scope.launch {
                            val db = AppDatabaseHolder.db(appCtx)
                            val sourceRef = com.phantom.checker.data.MemoryEngine.formatNotificationSourceRef(
                                messageId = message.id,
                                appLabel = message.appLabel,
                                sender = message.sender,
                                timestampMs = message.timestamp,
                                timeFormatted = fullTimeText
                            )
                            com.phantom.checker.data.MemoryEngine.writeMemoryWithConflictDetection(
                                db = db,
                                category = com.phantom.checker.data.MemoryEntity.CATEGORY_SCAM_IMMUNITY,
                                subject = immunitySubject.trim(),
                                content = immunityContent.trim(),
                                layer = com.phantom.checker.data.MemoryEntity.LAYER_LONG_TERM,
                                privacyTier = com.phantom.checker.data.MemoryEntity.PRIVACY_NORMAL,
                                confidence = com.phantom.checker.data.MemoryEntity.CONFIDENCE_INFERRED,
                                sourceType = com.phantom.checker.data.MemoryEntity.SOURCE_NOTIFICATION,
                                sourceRef = sourceRef
                            )
                            showImmunityConfirm = false
                            Toast.makeText(ctx, resCtx.getString(R.string.scam_center_s_3d57ef), Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(R.string.scam_center_s_94bf1d))
                }
            },
            dismissButton = {
                TextButton(onClick = { showImmunityConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
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
                    val upsertRes = withContext(Dispatchers.IO) {
                        val db = AppDatabaseHolder.db(appCtx)
                        val ruleText = if (isSenderScope) {
                            "白名單寄件者：${message.sender}（${message.appLabel}）"
                        } else {
                            message.content.trim().take(120).ifBlank { "白名單訊息（${message.sender}）" }
                        }
                        val res = WhitelistRuleHelper.upsertWhitelist(
                            dao = db.aiRuleDao(),
                            ruleText = ruleText,
                            sender = if (isSenderScope) message.sender.trim() else null,
                            packageName = message.packageName,
                            onCacheInvalidate = { com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches() }
                        )
                        if (res.isSuccess) {
                            db.messageDao().update(
                                message.copy(
                                    riskScore = 10,
                                    riskLabel = "safe",
                                    riskReason = "使用者手動加入白名單"
                                )
                            )
                        }
                        res
                    }
                    showWhitelistFlow = false
                    Toast.makeText(ctx, upsertRes.message, Toast.LENGTH_SHORT).show()
                    if (upsertRes.isSuccess) onWhitelistAdded()
                }
            }
        )
    }

    if (showSuspendConfirm) {
        AlertDialog(
            onDismissRequest = { showSuspendConfirm = false },
            title = { Text(stringResource(R.string.detail_s_d95488)) },
            text = { Text(stringResource(R.string.detail_s_e4bc66, message.appLabel, message.packageName)) },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        val res = ShizukuKeepAlive.suspendTargetApp(appCtx, message.packageName)
                        showSuspendConfirm = false
                        Toast.makeText(
                            ctx,
                            if (res.isSuccess) resCtx.getString(R.string.detail_s_5b13aa, message.appLabel) else resCtx.getString(R.string.detail_s_ec28fb, res.output),
                            Toast.LENGTH_SHORT
                        ).show()
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
                    text = stringResource(R.string.scam_center_s_2b99b0),
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
                DetailRow(label = stringResource(R.string.scam_center_s_165225), value = message.sender)
                DetailRow(
                    label = stringResource(R.string.scam_center_s_a954a1),
                    value = if (message.packageName.isNotBlank() && message.appLabel != message.packageName) {
                        "${message.appLabel} (${message.packageName})"
                    } else {
                        message.appLabel
                    }
                )
                DetailRow(label = stringResource(R.string.scam_center_s_edf2f8), value = fullTimeText)
                DetailRow(label = stringResource(R.string.scam_center_s_c704be), value = "${message.riskScore} / 100")

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Text(
                    text = stringResource(R.string.scam_center_s_b2984c),
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
                    text = stringResource(R.string.scam_center_s_375047),
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
                        markdown = message.riskReason.ifBlank { resCtx.getString(R.string.scam_center_s_6f35f7) },
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

                OutlinedButton(
                    onClick = { showWhitelistFlow = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.scam_center_s_f5143d))
                }

                OutlinedButton(
                    onClick = { showImmunityConfirm = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.scam_center_s_a74410))
                }

                if (shizukuReady) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.scam_center_s_325911),
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
                                        if (res.isSuccess) resCtx.getString(R.string.detail_s_59632d, message.appLabel) else resCtx.getString(R.string.detail_s_d45d8d, res.output),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.scam_center_s_4827cb))
                        }
                        TextButton(
                            onClick = { showSuspendConfirm = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.scam_center_s_359f97), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
                OutlinedButton(
                    onClick = { showDecisionFeedback = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_s_c930fe))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("scam_message", message.content))
                    Toast.makeText(ctx, resCtx.getString(R.string.detail_s_00399e), Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(stringResource(R.string.scam_center_s_34455c))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_close))
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
