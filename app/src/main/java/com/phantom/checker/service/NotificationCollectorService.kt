package com.phantom.checker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.LruCache
import androidx.core.app.NotificationCompat
import com.phantom.checker.R
import com.phantom.checker.data.AiRuleEntity
import com.phantom.checker.data.AppCategoryRuleEntity
import com.phantom.checker.data.AppDatabase
import com.phantom.checker.data.FilterRule
import com.phantom.checker.data.IntelResultEntity
import com.phantom.checker.data.MemoRuleEntity
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.NoteEntity
import com.phantom.checker.data.PendingAiAnalysisEntity
import com.phantom.checker.data.TagEntity
import com.phantom.checker.data.TodoEntity
import com.phantom.checker.data.UserTagRuleEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.network.IndicatorExtractor
import com.phantom.checker.network.IntelClient
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.ui.MessageDetailActivity
import com.phantom.checker.ui.ScamCenterActivity
import com.phantom.checker.util.AiPurpose
import com.phantom.checker.util.ClientTimeParser
import com.phantom.checker.util.MuteMode
import com.phantom.checker.util.NotifClickBehavior
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.widget.ReminderWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.Calendar

/**
 * 核心：通知攔截服務。
 * 讀取通知純粹是「被動接收系統廣播」，不會開啟原 App，因此不觸發已讀。
 */
class NotificationCollectorService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var connectTime: Long = 0L

    @Volatile
    private var lastStatsRespawnMs: Long = 0L

    private data class KeyRecord(
        val timestamp: Long,
        val title: String,
        val text: String
    )

    private val dedupCache = object : LruCache<String, Long>(256) {}
    private val sameKeyHistory = object : LruCache<String, KeyRecord>(256) {}
    private val appLabelCache = object : LruCache<String, String>(64) {}

    private data class PendingBatch(
        val items: MutableList<AiClient.BatchAnalyzeItem> = mutableListOf(),
        var job: Job? = null,
        var firstAddedMs: Long = System.currentTimeMillis()
    )

    private val batchLock = Any()
    private val pendingBatches = HashMap<String, PendingBatch>()

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        AiClient.init(applicationContext)
        PhantomPrefs.onRuleCachesInvalidated = { invalidateRuleCaches() }
    }

    override fun onDestroy() {
        if (activeInstance === this) activeInstance = null
        PhantomPrefs.onRuleCachesInvalidated = null
        isConnected = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        activeInstance = this
        isConnected = true
        connectTime = System.currentTimeMillis()
        StatsForegroundService.startOrRefresh(applicationContext, force = true)
        KeepAlivePolicy.scheduleAll(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        if (activeInstance === this) activeInstance = null
        isConnected = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                requestRebind(ComponentName(this, NotificationCollectorService::class.java))
            } catch (_: Exception) {
                KeepAlivePolicy.scheduleRebindFallback(applicationContext)
            }
        } else {
            KeepAlivePolicy.scheduleRebindFallback(applicationContext)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        val removed = sbn ?: return
        if (removed.packageName == packageName &&
            removed.id == StatsForegroundService.STATS_NOTIF_ID
        ) {
            val now = System.currentTimeMillis()
            // 僅對同秒內批次清除做 3 秒節流；單次滑掉立即在 listener 內直發通知（<300ms）
            if (now - lastStatsRespawnMs < STATS_RESPAWN_BURST_MS) return
            lastStatsRespawnMs = now
            StatsForegroundService.repostDirectly(applicationContext)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg == packageName) return

        // 疑似系統重播旗標：不直接丟棄，於背景協程以 DB dedupKey 查證是否為斷線漏收補投
        val connectedAt = connectTime
        val isSuspectedReplay = connectedAt > 0L && sbn.postTime < connectedAt - REPLAY_GRACE_MS

        // 兩段式 App 靜音檢查
        val isMuted = PhantomPrefs.isAppMuted(applicationContext, pkg)
        val muteMode = if (isMuted) PhantomPrefs.getAppMuteMode(applicationContext, pkg) else null
        if (muteMode == MuteMode.BLOCK_RECORD) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        // 持續性通知過濾（預設略過）
        val isOngoing = sbn.isOngoing ||
            (notification.flags and Notification.FLAG_ONGOING_EVENT) != 0 ||
            (notification.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0
        if (isOngoing && !PhantomPrefs.isRecordOngoingEnabled(applicationContext)) return

        // 群組摘要通知過濾
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0 ||
            extras.getBoolean("android.isGroupSummary", false)
        if (isGroupSummary) return

        val messagingStyle = try {
            NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(notification)
        } catch (_: Exception) {
            null
        }
        val lastStyleMessage = messagingStyle?.messages?.lastOrNull()

        val rawText = (
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() }
                ?: lastStyleMessage?.text?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() }
            ) ?: return

        val rawConversationTitle = (
            messagingStyle?.conversationTitle?.toString()
                ?: extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString()
            )?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedConversationTitle = rawConversationTitle
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        @Suppress("DEPRECATION")
        val styleSender = (
            lastStyleMessage?.person?.name?.toString()
                ?: lastStyleMessage?.sender?.toString()
            )?.trim()?.takeIf { it.isNotEmpty() }
            ?: extractLastSenderFromExtraMessages(extras)
        val senderPersonIcon = extractLastSenderIconFromExtraMessages(extras)
            ?: lastStyleMessage?.person?.icon?.toIcon(applicationContext)

        val fallbackTitle = resolveNotificationTitle(extras, rawText, pkg)
        val title = styleSender ?: fallbackTitle
        val conversationKey = normalizedConversationTitle ?: title
        val isMessagingNotif = notification.category == Notification.CATEGORY_MESSAGE ||
            extras.containsKey(Notification.EXTRA_MESSAGES) ||
            extras.getString(Notification.EXTRA_TEMPLATE) == Notification.MessagingStyle::class.java.name

        // 內容感知同 sbn.key 更新節流 + 疊加式通知尾巴提取
        val sbnKey = sbn.key
        val throttleSec = PhantomPrefs.getEffectiveSameKeyThrottleSeconds(applicationContext, pkg)
        val digitVarianceEnabled = PhantomPrefs.isSameKeyDigitVarianceThrottleEnabled(applicationContext)
        var text = rawText
        if (!sbnKey.isNullOrBlank()) {
            val prevRecord = synchronized(sameKeyHistory) { sameKeyHistory.get(sbnKey) }
            if (prevRecord != null) {
                val exactSame = (title == prevRecord.title && rawText == prevRecord.text)
                if (isMessagingNotif && exactSame && sbn.postTime == prevRecord.timestamp) {
                    // 通訊類通知同 key + 同內容 + postTime 未變視為系統 re-post
                    return
                }
                if (throttleSec > 0 && (sbn.postTime - prevRecord.timestamp) < throttleSec * 1000L) {
                    val shouldThrottleExact = exactSame && !isMessagingNotif
                    val digitMicroChange = digitVarianceEnabled &&
                        !isMessagingNotif &&
                        title == prevRecord.title &&
                        normalizeDigitsAndWhitespace(rawText) == normalizeDigitsAndWhitespace(prevRecord.text)
                    if (shouldThrottleExact || digitMicroChange) {
                        return
                    }
                }
                if (prevRecord.text.isNotEmpty() &&
                    rawText.length > prevRecord.text.length &&
                    rawText.startsWith(prevRecord.text)
                ) {
                    val tail = rawText.substring(prevRecord.text.length)
                        .trimStart('\n', '\r', ' ', '\t')
                    if (tail.isNotEmpty()) {
                        text = tail
                    }
                }
            }
        }

        // 斷線重播去重快取（一般路徑交由上方 throttle 層判斷，不以固定 10 分鐘吞訊）
        val dedupKey = sha256("$pkg|$sbnKey|$title|$text")
        synchronized(dedupCache) {
            val lastTime = dedupCache.get(dedupKey)
            if (isSuspectedReplay && lastTime != null && (sbn.postTime - lastTime) < DEDUP_WINDOW_MS) {
                return
            }
            dedupCache.put(dedupKey, sbn.postTime)
        }
        if (!sbnKey.isNullOrBlank()) {
            synchronized(sameKeyHistory) {
                sameKeyHistory.put(sbnKey, KeyRecord(sbn.postTime, title, rawText))
            }
        }

        scope.launch {
            val db = AppDatabaseHolder.db(applicationContext)
            if (isSuspectedReplay) {
                val alreadyRecorded = db.messageDao().existsByDedupKeyInWindow(
                    dedupKey = dedupKey,
                    minTs = sbn.postTime - DEDUP_WINDOW_MS,
                    maxTs = sbn.postTime + DEDUP_WINDOW_MS
                )
                if (alreadyRecorded) {
                    return@launch
                }
            }
            // #237 自己回覆 echo 抑制：3 分鐘內命中（normalize 全等或 dice >= 0.9）→ 略過 insert 並刪除命中列
            val nowMs = System.currentTimeMillis()
            val echoDao = db.ownReplyEchoDao()
            val recentEchoes = echoDao.recentByPkg(pkg, nowMs - OWN_REPLY_ECHO_WINDOW_MS)
            if (recentEchoes.isNotEmpty()) {
                val myNames = PhantomPrefs.getMyDisplayNames(applicationContext)
                val normText = normalizeForSimilarity(text)
                val strippedText = stripOwnSenderPrefix(text, myNames)
                val normStripped = if (strippedText != null) normalizeForSimilarity(strippedText) else null
                val matchedEcho = recentEchoes.firstOrNull { echo ->
                    val convMatches = echo.conversationKey.isBlank() ||
                        conversationKey.isBlank() ||
                        echo.conversationKey.equals(conversationKey, ignoreCase = true) ||
                        echo.conversationKey.equals(title, ignoreCase = true)
                    if (!convMatches) return@firstOrNull false
                    val matchesRaw = normText.isNotEmpty() && (
                        echo.normalizedText == normText ||
                            diceBigramSimilarity(echo.normalizedText, normText) >= 0.9
                        )
                    val matchesStripped = !normStripped.isNullOrEmpty() && (
                        echo.normalizedText == normStripped ||
                            diceBigramSimilarity(echo.normalizedText, normStripped) >= 0.9
                        )
                    matchesRaw || matchesStripped
                }
                if (matchedEcho != null) {
                    echoDao.deleteById(matchedEcho.id)
                    return@launch
                }
            }

            // #237 自我身分偵測：群組通知寄件者命中「我的名稱」→ 標記 isOwn=true
            val isOwnSender = PhantomPrefs.isMyDisplayName(applicationContext, styleSender ?: title)

            val rules = cachedActiveFilterRules(db)
            val matchedRule = rules.firstOrNull { rule ->
                if (rule.packageName != null && rule.packageName != pkg) return@firstOrNull false
                text.contains(rule.keyword, ignoreCase = true)
            }

            if (matchedRule?.action == FilterRule.ACTION_DROP) {
                return@launch
            }

            val isRuleScam = matchedRule?.action == FilterRule.ACTION_SCAM
            val isRuleFiltered = matchedRule != null
            val silentMuted = muteMode == MuteMode.SILENT_RECORD

            val id = db.messageDao().insert(
                MessageEntity(
                    packageName = pkg,
                    appLabel = cachedAppLabel(pkg),
                    sender = title,
                    conversationKey = conversationKey,
                    content = text,
                    timestamp = sbn.postTime,
                    riskScore = if (isRuleScam && !isOwnSender) 95 else 0,
                    riskLabel = if (isRuleScam && !isOwnSender) "scam" else "",
                    riskReason = if (isRuleScam && !isOwnSender) "命中自訂詐騙過濾規則：${matchedRule?.keyword}" else "",
                    isFiltered = isRuleFiltered && !isOwnSender,
                    isRead = silentMuted || isOwnSender,
                    dedupKey = dedupKey,
                    isOwn = isOwnSender
                )
            )
            if (isOwnSender) {
                return@launch
            }
            if (senderPersonIcon != null && title.isNotBlank()) {
                saveSenderAvatar(db, pkg, title, senderPersonIcon, nowMs)
            }

            // 使用者標籤記憶規則（確定性、零 LLM token，優先序高於 App 分類與 AI 標籤）
            val userTagRules = try {
                cachedActiveUserTagRules(db)
            } catch (_: Exception) {
                emptyList()
            }
            val matchedUserTagRules = com.phantom.checker.util.TagRuleEngine.evaluateTagRules(
                rules = userTagRules,
                packageName = pkg,
                appLabel = cachedAppLabel(pkg),
                sender = title,
                content = text
            )
            if (matchedUserTagRules.isNotEmpty()) {
                val userRefs = matchedUserTagRules.map { it.targetTagId }.distinct().map { tagId ->
                    MessageTagCrossRef(
                        messageId = id,
                        tagId = tagId,
                        source = MessageTagCrossRef.SOURCE_USER_RULE
                    )
                }
                db.tagDao().addTagsToMessageBatch(userRefs)
            } else {
                // App 分類標籤（零 token 規則式，僅在未命中使用者標籤規則時套用）
                val appCatRules = cachedAppCategoryRules(db, pkg)
                if (appCatRules.isNotEmpty()) {
                    val catRefs = appCatRules.map { rule ->
                        MessageTagCrossRef(
                            messageId = id,
                            tagId = rule.tagId,
                            source = MessageTagCrossRef.SOURCE_AUTO
                        )
                    }
                    db.tagDao().addTagsToMessageBatch(catRefs)
                }
            }

            if (!silentMuted) {
                StatsForegroundService.startOrRefresh(applicationContext, force = true)
            }

            // 即時提醒關鍵字檢查（與過濾規則獨立）
            if (!silentMuted) {
                val alertKeywords = cachedAlertKeywords(applicationContext)
                val hitAlertKw = alertKeywords.firstOrNull { kw ->
                    text.contains(kw, ignoreCase = true) || title.contains(kw, ignoreCase = true)
                }
                if (hitAlertKw != null) {
                    alertImportant(applicationContext, id, pkg, title, text)
                }
            }

            if (isRuleScam && !silentMuted) {
                alertScam(
                    applicationContext,
                    id,
                    pkg,
                    title,
                    "命中自訂詐騙過濾規則：${matchedRule?.keyword}"
                )
                return@launch
            }

            val activeAiRules = try {
                cachedActiveAiRules(db)
            } catch (_: Exception) {
                emptyList()
            }
            val indicators = IndicatorExtractor.extract(text)
            if (IntelClient.isMessageWhitelisted(
                    sender = title,
                    packageName = pkg,
                    appLabel = cachedAppLabel(pkg),
                    content = text,
                    aiRules = activeAiRules,
                    indicators = indicators
                )
            ) {
                db.messageDao().byId(id)?.let { currentMsg ->
                    db.messageDao().update(
                        currentMsg.copy(
                            riskScore = 10,
                            riskLabel = "safe",
                            riskReason = "命中白名單規則"
                        )
                    )
                }
                return@launch
            }

            // 雲端防詐情報預查（零 LLM token，僅送 URL / crypto 地址；ai_rules 白名單優先跳過）
            if (PhantomPrefs.isIntelEnabled(applicationContext) && indicators.isNotEmpty()) {
                val intelResult = try {
                    IntelClient.checkIndicators(applicationContext, db, indicators)
                } catch (_: Exception) {
                    null
                }
                if (intelResult != null) {
                    try {
                        db.intelDao().putResult(
                            IntelResultEntity(
                                messageId = id,
                                indicatorsSummary = indicators.summaryText(),
                                isHit = intelResult.isHit,
                                sourceLabel = intelResult.sourceLabel,
                                detail = intelResult.detail,
                                checkedAt = intelResult.checkedAt
                            )
                        )
                    } catch (_: Exception) {
                    }
                    if (intelResult.isHit) {
                        val hitReason = "【防詐情報命中：${intelResult.sourceLabel}】${intelResult.detail}"
                        db.messageDao().byId(id)?.let { currentMsg ->
                            db.messageDao().update(
                                currentMsg.copy(
                                    riskScore = 95,
                                    riskLabel = "scam",
                                    riskReason = hitReason
                                )
                            )
                        }
                        if (!silentMuted) {
                            alertScam(applicationContext, id, pkg, title, hitReason)
                            StatsForegroundService.startOrRefresh(applicationContext, force = true)
                        }
                        return@launch
                    }
                }
            }

            // AI 風險分析與智慧標記：省 token 模式下僅命中可疑特徵才分析
            val tokenSaver = PhantomPrefs.isTokenSaverEnabled(applicationContext)
            val shouldRunAi = if (tokenSaver) {
                hasSuspiciousFeature(text, isRuleFiltered)
            } else {
                !isRuleFiltered
            }
            if (shouldRunAi &&
                !silentMuted &&
                PhantomPrefs.isAiRiskEnabled(applicationContext) &&
                text.length >= PhantomPrefs.getAiMinTriggerLength(applicationContext)
            ) {
                enqueueForBatchAnalysis(
                    pkg = pkg,
                    item = AiClient.BatchAnalyzeItem(
                        messageId = id,
                        sender = title,
                        content = text
                    )
                )
            }
        }
    }

    private fun enqueueForBatchAnalysis(pkg: String, item: AiClient.BatchAnalyzeItem) {
        val batchKey = "$pkg|${item.sender}"
        val now = System.currentTimeMillis()
        var immediateFlush: List<AiClient.BatchAnalyzeItem>? = null
        synchronized(batchLock) {
            val existing = pendingBatches[batchKey]
            val batch = if (existing == null || (now - existing.firstAddedMs) > BATCH_WINDOW_MS) {
                existing?.job?.cancel()
                PendingBatch(firstAddedMs = now).also { pendingBatches[batchKey] = it }
            } else {
                existing
            }
            batch.items.add(item)
            if (batch.items.size >= MAX_BATCH_SIZE) {
                batch.job?.cancel()
                immediateFlush = batch.items.toList()
                pendingBatches.remove(batchKey)
            } else {
                batch.job?.cancel()
                batch.job = scope.launch {
                    delay(BATCH_COALESCE_MS)
                    val toProcess = synchronized(batchLock) {
                        pendingBatches.remove(batchKey)?.items?.toList().orEmpty()
                    }
                    if (toProcess.isNotEmpty()) {
                        processBatchAnalysis(pkg, toProcess)
                    }
                }
            }
        }
        immediateFlush?.let { items ->
            scope.launch { processBatchAnalysis(pkg, items) }
        }
    }

    private suspend fun processBatchAnalysis(pkg: String, items: List<AiClient.BatchAnalyzeItem>) {
        if (items.isEmpty()) return
        if (!PhantomPrefs.canUseAiTodayAndIncrement(applicationContext)) return

        val db = AppDatabaseHolder.db(applicationContext)
        val enableAutoTag = PhantomPrefs.isEffectiveAiAutoTagEnabled(applicationContext)
        val aiTags = if (enableAutoTag) db.tagDao().aiManagedTags() else emptyList()
        val aiRules = cachedActiveAiRules(db)
        val memoRules = cachedActiveMemoRules(db)

        val outcome = AiClient.analyzeMessageBatchDetailed(
            items = items,
            tags = aiTags,
            enableAutoTag = enableAutoTag,
            context = applicationContext,
            rules = aiRules,
            memoRules = memoRules
        )
        val results = when (outcome) {
            is AiClient.BatchAnalysisOutcome.Success -> outcome.results
            is AiClient.BatchAnalysisOutcome.TransientFailure -> {
                PhantomPrefs.refundTodayAiUsage(applicationContext)
                if (PhantomPrefs.isAiOfflineRetryEnabled(applicationContext)) {
                    val dao = db.pendingAiAnalysisDao()
                    val wasEmpty = dao.pendingCount() == 0
                    val now = System.currentTimeMillis()
                    dao.enqueueAll(
                        items.map {
                            PendingAiAnalysisEntity(
                                messageId = it.messageId,
                                queuedAt = now,
                                attempts = 0
                            )
                        }
                    )
                    if (wasEmpty) {
                        PendingAiAnalysisPolicy.notifyQueued(applicationContext, items.size)
                    }
                    PendingAiAnalysisPolicy.enqueue(applicationContext)
                }
                emptyMap()
            }
            is AiClient.BatchAnalysisOutcome.PermanentFailure -> {
                PhantomPrefs.refundTodayAiUsage(applicationContext)
                emptyMap()
            }
        }
        if (results.isEmpty()) {
            if (outcome is AiClient.BatchAnalysisOutcome.Success) {
                PhantomPrefs.refundTodayAiUsage(applicationContext)
            }
            if (enableAutoTag) {
                // 自動降回 App 分類標籤（不中斷訊息入庫與風險流程）
                val appCatRules = cachedAppCategoryRules(db, pkg)
                if (appCatRules.isNotEmpty()) {
                    items.forEach { item ->
                        val existing = db.tagDao().crossRefsForMessage(item.messageId)
                        if (existing.isEmpty()) {
                            val refs = appCatRules.map { rule ->
                                MessageTagCrossRef(
                                    messageId = item.messageId,
                                    tagId = rule.tagId,
                                    source = MessageTagCrossRef.SOURCE_AUTO
                                )
                            }
                            db.tagDao().addTagsToMessageBatch(refs)
                        }
                    }
                }
                if (AiClient.getProfileForPurpose(applicationContext, AiPurpose.MESSAGE_ANALYSIS) != null &&
                    outcome !is AiClient.BatchAnalysisOutcome.TransientFailure
                ) {
                    recordAndMaybeNotifyAiTagFailure(applicationContext)
                }
            }
            return
        }
        resetAiTagFailureCount(applicationContext)

        var hasNewScam = false
        results.forEach { (msgId, result) ->
            val msg = db.messageDao().byId(msgId) ?: return@forEach
            db.messageDao().update(
                msg.copy(
                    riskScore = result.riskScore,
                    riskLabel = result.riskLabel,
                    riskReason = result.riskReason
                )
            )
            if (enableAutoTag && result.tagIds.isNotEmpty()) {
                val existingRefs = db.tagDao().crossRefsForMessage(msgId)
                val hasHigherPriorityTag = existingRefs.any {
                    it.source == MessageTagCrossRef.SOURCE_MANUAL ||
                        it.source == MessageTagCrossRef.SOURCE_USER_RULE
                }
                if (!hasHigherPriorityTag) {
                    val refs = result.tagIds.map { tagId ->
                        MessageTagCrossRef(
                            messageId = msgId,
                            tagId = tagId,
                            source = MessageTagCrossRef.SOURCE_AUTO
                        )
                    }
                    db.tagDao().addTagsToMessageBatch(refs)
                }
            }
            if (PhantomPrefs.isAiAutoMemoEnabled(applicationContext) && result.memo.isNotBlank()) {
                saveAutoMemoIfPresent(
                    db = db,
                    msg = msg,
                    rawMemo = result.memo,
                    memoType = result.memoType,
                    todoDue = result.todoDue,
                    ctx = applicationContext
                )
            }
            if (result.riskLabel == "scam") {
                hasNewScam = true
                alertScam(applicationContext, msgId, pkg, msg.sender, result.riskReason)
            }
        }
        if (hasNewScam) {
            StatsForegroundService.startOrRefresh(applicationContext, force = true)
        }
    }

    @Suppress("DEPRECATION")
    private fun extractLastSenderFromExtraMessages(extras: android.os.Bundle): String? {
        val parcelables = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
        val msgs = Notification.MessagingStyle.Message.getMessagesFromBundleArray(parcelables)
        val last = msgs.lastOrNull() ?: return null
        val name = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            last.senderPerson?.name?.toString() ?: last.sender?.toString()
        } else {
            last.sender?.toString()
        }
        return name?.trim()?.takeIf { it.isNotEmpty() }
    }

    @Suppress("DEPRECATION")
    private fun extractLastSenderIconFromExtraMessages(extras: android.os.Bundle): android.graphics.drawable.Icon? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return try {
            val parcelables = extras.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null
            val msgs = Notification.MessagingStyle.Message.getMessagesFromBundleArray(parcelables)
            msgs.lastOrNull()?.senderPerson?.icon
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun saveSenderAvatar(
        db: AppDatabase,
        packageName: String,
        sender: String,
        icon: android.graphics.drawable.Icon,
        nowMs: Long
    ) {
        try {
            val drawable = icon.loadDrawable(applicationContext) ?: return
            val srcW = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
            val srcH = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
            val maxDim = maxOf(srcW, srcH)
            val scale = if (maxDim > 96) 96f / maxDim.toFloat() else 1f
            val targetW = (srcW * scale).toInt().coerceIn(1, 96)
            val targetH = (srcH * scale).toInt().coerceIn(1, 96)
            val bmp = android.graphics.Bitmap.createBitmap(
                targetW,
                targetH,
                android.graphics.Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            drawable.setBounds(0, 0, targetW, targetH)
            drawable.draw(canvas)

            val baos = java.io.ByteArrayOutputStream()
            bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, baos)
            var bytes = baos.toByteArray()
            if (bytes.size > 16 * 1024) {
                for (q in listOf(85, 70, 50)) {
                    baos.reset()
                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, q, baos)
                    bytes = baos.toByteArray()
                    if (bytes.size <= 16 * 1024) break
                }
            }
            bmp.recycle()
            if (bytes.isEmpty() || bytes.size > 16 * 1024) return

            val avatarDao = db.senderAvatarDao()
            avatarDao.upsert(
                com.phantom.checker.data.SenderAvatarEntity(
                    packageName = packageName,
                    sender = sender.trim(),
                    icon = bytes,
                    updatedAt = nowMs
                )
            )
            if (nowMs % 20L == 0L) {
                avatarDao.deleteOlderThan(nowMs - 30L * 24L * 60L * 60L * 1000L)
            }
            if (avatarDao.count() > com.phantom.checker.data.SenderAvatarDao.MAX_ROWS) {
                avatarDao.pruneToMaxRows(com.phantom.checker.data.SenderAvatarDao.MAX_ROWS)
            }
        } catch (_: Exception) {
        }
    }

    private fun resolveNotificationTitle(
        extras: android.os.Bundle,
        rawText: String,
        pkg: String
    ): String {
        val primary = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        if (!primary.isNullOrEmpty()) return primary

        val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim()
        if (!bigTitle.isNullOrEmpty()) return bigTitle

        val colonIdx = rawText.indexOfFirst { it == '：' || it == ':' }
        if (colonIdx in 1..20) {
            val prefix = rawText.substring(0, colonIdx).trim()
            if (prefix.isNotEmpty() && !prefix.contains('\n') && !prefix.contains('\r')) {
                return prefix
            }
        }
        return cachedAppLabel(pkg)
    }

    private fun normalizeDigitsAndWhitespace(input: String): String =
        input.filter { !it.isDigit() && !it.isWhitespace() }

    private fun cachedAppLabel(pkg: String): String {
        synchronized(appLabelCache) {
            appLabelCache.get(pkg)?.let { return it }
        }
        val resolved = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(pkg, 0)
            ).toString()
        } catch (_: Exception) {
            pkg
        }
        synchronized(appLabelCache) {
            appLabelCache.put(pkg, resolved)
        }
        return resolved
    }

    private fun buildClickPendingIntent(
        ctx: Context,
        requestCode: Int,
        msgId: Long,
        packageName: String
    ): PendingIntent {
        val behavior = PhantomPrefs.getNotifClickBehavior(ctx)
        val intent = if (behavior == NotifClickBehavior.OPEN_ORIGINAL_APP) {
            ctx.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            } ?: Intent(ctx, ScamCenterActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        } else {
            MessageDetailActivity.createIntent(ctx, msgId).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        return PendingIntent.getActivity(
            ctx,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun alertScam(
        ctx: Context,
        msgId: Long,
        pkg: String,
        sender: String,
        reason: String
    ) {
        if (!PhantomPrefs.isScamAlertNotifEnabled(ctx)) return
        if (PhantomPrefs.isInQuietHoursNow(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel("scam_alert", "詐騙警示", NotificationManager.IMPORTANCE_HIGH)
        )
        val notifId = NotifIds.scamAlert(msgId)
        val pendingIntent = buildClickPendingIntent(ctx, notifId, msgId, pkg)
        val plainReason = com.phantom.checker.ui.stripMarkdown(reason)
            .replace(WHITESPACE_REGEX, " ")
            .trim()
            .ifBlank { "請前往詐騙警示中心查看風險分析" }
        val notif = Notification.Builder(ctx, "scam_alert")
            .setSmallIcon(R.drawable.ic_stat_phantom)
            .setContentTitle("⚠️ 偵測到高風險訊息：$sender")
            .setContentText(plainReason)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(notifId, notif)
        } catch (_: SecurityException) {
        }
    }

    private fun alertImportant(
        ctx: Context,
        msgId: Long,
        pkg: String,
        sender: String,
        content: String
    ) {
        if (PhantomPrefs.isInQuietHoursNow(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel("important_alert", "重要訊息提醒", NotificationManager.IMPORTANCE_HIGH)
        )
        val notifId = NotifIds.importantAlert(msgId)
        val pendingIntent = buildClickPendingIntent(ctx, notifId, msgId, pkg)
        val notif = Notification.Builder(ctx, "important_alert")
            .setSmallIcon(R.drawable.ic_stat_phantom)
            .setContentTitle("🔔 重要訊息：$sender")
            .setContentText(content.take(120))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(notifId, notif)
        } catch (_: SecurityException) {
        }
    }

    private fun aiTagFailurePrefs(ctx: Context) =
        ctx.getSharedPreferences("phantom_ai_tag_status", Context.MODE_PRIVATE)

    private fun resetAiTagFailureCount(@Suppress("UNUSED_PARAMETER") ctx: Context) {
        // 單次成功不重置 24 小時時間窗計數（僅時間窗滑出才清除）
    }

    private fun recordAndMaybeNotifyAiTagFailure(ctx: Context) {
        val prefs = aiTagFailurePrefs(ctx)
        val now = System.currentTimeMillis()
        val windowMs = 24L * 3600_000L
        val cooldownMs = 6L * 3600_000L
        val recentTimestamps = prefs.getString("failure_timestamps", "")
            .orEmpty()
            .split(",")
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it in (now - windowMs)..now }
            .toMutableList()
        recentTimestamps.add(now)
        val count = recentTimestamps.size
        val lastNotifAt = prefs.getLong("last_failure_notif_at", 0L)

        val editor = prefs.edit()
            .putString("failure_timestamps", recentTimestamps.joinToString(","))

        val shouldNotify = count >= 5 &&
            (now - lastNotifAt) >= cooldownMs &&
            PhantomPrefs.isAiTagFailureNotifEnabled(ctx) &&
            !PhantomPrefs.isInQuietHoursNow(ctx)

        if (shouldNotify) {
            editor.putLong("last_failure_notif_at", now).apply()
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val chId = "ai_tag_status"
            nm.createNotificationChannel(
                NotificationChannel(chId, "AI 標籤狀態通知", NotificationManager.IMPORTANCE_LOW)
            )
            val openAppIntent = PendingIntent.getActivity(
                ctx,
                NotifIds.AI_TAG_FAILURE,
                Intent(ctx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val bodyText = "AI 標籤分發近 24 小時已失敗 $count 次，請檢查所用模型的指令遵循能力，或至設定更換訊息分析模型"
            val notif = Notification.Builder(ctx, chId)
                .setSmallIcon(R.drawable.ic_stat_phantom)
                .setContentTitle("AI 標籤分發多次失敗")
                .setContentText(bodyText)
                .setStyle(Notification.BigTextStyle().bigText(bodyText))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openAppIntent)
                .build()
            try {
                nm.notify(NotifIds.AI_TAG_FAILURE, notif)
            } catch (_: SecurityException) {
            }
        } else {
            editor.apply()
        }
    }

    companion object {
        @Volatile
        var isConnected: Boolean = false

        @Volatile
        private var activeInstance: NotificationCollectorService? = null

        sealed class ReplySendResult {
            data object Success : ReplySendResult()
            data class Unavailable(val reason: String) : ReplySendResult()
        }

        fun sendReplyToNotification(
            context: Context,
            packageName: String,
            sender: String,
            contentSnippet: String,
            replyText: String,
            conversationKey: String? = null
        ): ReplySendResult {
            val service = activeInstance
            if (service == null) {
                Log.w("PhantomReply", "sendReplyToNotification: activeInstance is null pkg=$packageName")
                return ReplySendResult.Unavailable("通知監聽服務尚未連線，無法取得即時通知，建議開啟原 App 回覆。")
            }
            val trimmedReply = replyText.trim()
            if (trimmedReply.isEmpty()) {
                return ReplySendResult.Unavailable("回覆內容不可為空。")
            }
            val active = try {
                service.activeNotifications?.toList().orEmpty()
            } catch (e: Exception) {
                Log.e("PhantomReply", "sendReplyToNotification: activeNotifications failed pkg=$packageName", e)
                return ReplySendResult.Unavailable("無法讀取目前通知列狀態，建議開啟原 App 回覆。")
            }
            val pkgNotifs = active.filter { it.packageName == packageName }
            if (pkgNotifs.isEmpty()) {
                Log.w("PhantomReply", "sendReplyToNotification: no active notifs for pkg=$packageName")
                return ReplySendResult.Unavailable("該通知已被清除或不在通知列中，無法快速回覆，建議開啟原 App 回覆。")
            }

            val senderNorm = sender.trim()
            val convKeyNorm = conversationKey?.replace(WHITESPACE_REGEX, " ")?.trim().orEmpty()
            val snippetNorm = contentSnippet.trim().take(40)
            var foundMatchingNotifWithoutRemoteInput = false

            val candidates = pkgNotifs.sortedByDescending { sbn ->
                val extras = sbn.notification?.extras ?: return@sortedByDescending 0
                val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                    ?.toString()
                    ?.replace(WHITESPACE_REGEX, " ")
                    ?.trim()
                    .orEmpty()
                val t = (extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                    ?: "").trim()
                val body = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: "").trim()
                var score = 0
                if (convKeyNorm.isNotEmpty()) {
                    if (convTitle.equals(convKeyNorm, ignoreCase = true)) score += 20
                    else if (convTitle.contains(convKeyNorm, ignoreCase = true)) score += 12
                    else if (t.equals(convKeyNorm, ignoreCase = true)) score += 10
                }
                if (senderNorm.isNotEmpty() && t.equals(senderNorm, ignoreCase = true)) score += 10
                else if (senderNorm.isNotEmpty() && t.contains(senderNorm, ignoreCase = true)) score += 5
                if (snippetNorm.isNotEmpty() && body.contains(snippetNorm, ignoreCase = true)) score += 4
                val hasRemoteInput = sbn.notification?.actions?.any { act ->
                    act.actionIntent != null && act.remoteInputs?.any { it.allowFreeFormInput } == true
                } == true
                if (hasRemoteInput) score += 1
                score
            }

            for (sbn in candidates) {
                val notif = sbn.notification ?: continue
                val extras = notif.extras ?: continue
                val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)
                    ?.toString()
                    ?.replace(WHITESPACE_REGEX, " ")
                    ?.trim()
                    .orEmpty()
                val t = (extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()
                    ?: "").trim()
                val convMatches = convKeyNorm.isNotEmpty() && (
                    convTitle.equals(convKeyNorm, ignoreCase = true) ||
                        convTitle.contains(convKeyNorm, ignoreCase = true) ||
                        t.equals(convKeyNorm, ignoreCase = true)
                    )
                val senderMatches = (senderNorm.isEmpty() && convKeyNorm.isEmpty()) ||
                    convMatches ||
                    (senderNorm.isNotEmpty() && (
                        t.equals(senderNorm, ignoreCase = true) ||
                            t.contains(senderNorm, ignoreCase = true) ||
                            senderNorm.contains(t, ignoreCase = true)
                        ))
                if (!senderMatches && pkgNotifs.size > 1) continue

                val actions = notif.actions ?: emptyArray()
                val replyAction = actions.firstOrNull { act ->
                    act.actionIntent != null &&
                        act.remoteInputs?.any { it.allowFreeFormInput } == true
                }
                if (replyAction == null) {
                    foundMatchingNotifWithoutRemoteInput = true
                    continue
                }
                val remoteInput = replyAction.remoteInputs?.firstOrNull { it.allowFreeFormInput } ?: continue
                return try {
                    val replyIntent = Intent()
                    val bundle = android.os.Bundle().apply {
                        putCharSequence(remoteInput.resultKey, trimmedReply)
                    }
                    android.app.RemoteInput.addResultsToIntent(arrayOf(remoteInput), replyIntent, bundle)
                    replyAction.actionIntent.send(context, 0, replyIntent)
                    val echoConvKey = convKeyNorm.ifBlank { senderNorm }
                    val appCtx = context.applicationContext
                    CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                        try {
                            recordOwnReplyEcho(appCtx, packageName, echoConvKey, trimmedReply)
                        } catch (_: Exception) {
                        }
                    }
                    Log.d("PhantomReply", "sendReplyToNotification: sent pkg=$packageName convKey=$convKeyNorm sender=$senderNorm")
                    ReplySendResult.Success
                } catch (e: Exception) {
                    Log.e("PhantomReply", "sendReplyToNotification: actionIntent.send failed pkg=$packageName convKey=$convKeyNorm", e)
                    ReplySendResult.Unavailable("傳送快速回覆失敗（${e.message ?: "未知錯誤"}），建議開啟原 App 回覆。")
                }
            }

            Log.w("PhantomReply", "sendReplyToNotification: no suitable RemoteInput pkg=$packageName convKey=$convKeyNorm sender=$senderNorm noRemoteInput=$foundMatchingNotifWithoutRemoteInput")
            return if (foundMatchingNotifWithoutRemoteInput) {
                ReplySendResult.Unavailable("該通知不支援快速回覆（無 RemoteInput），建議開啟原 App 回覆。")
            } else {
                ReplySendResult.Unavailable("找不到來自「${sender.ifBlank { convKeyNorm.ifBlank { packageName } }}」的可回覆通知，可能已被清除，建議開啟原 App 回覆。")
            }
        }

        private const val DEDUP_WINDOW_MS = 10L * 60L * 1000L
        private const val REPLAY_GRACE_MS = 5000L
        private const val STATS_RESPAWN_BURST_MS = 400L
        private const val BATCH_COALESCE_MS = 2500L
        private const val BATCH_WINDOW_MS = 60_000L
        private const val MAX_BATCH_SIZE = 5
        private const val RULE_CACHE_TTL_MS = 30_000L
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val PUNCTUATION_REGEX = Regex("[\\p{Punct}\\p{IsPunctuation}]+")

        private val ruleCacheLock = Any()
        private var cachedFilterRules: Pair<Long, List<FilterRule>>? = null
        private var cachedAiRules: Pair<Long, List<AiRuleEntity>>? = null
        private var cachedUserTagRules: Pair<Long, List<UserTagRuleEntity>>? = null
        private var cachedMemoRules: Pair<Long, List<MemoRuleEntity>>? = null
        private val cachedAppCatRules = object : LruCache<String, Pair<Long, List<AppCategoryRuleEntity>>>(64) {}
        private var cachedAlertKeywordsPair: Pair<Long, List<String>>? = null

        fun invalidateRuleCaches() {
            synchronized(ruleCacheLock) {
                cachedFilterRules = null
                cachedAiRules = null
                cachedUserTagRules = null
                cachedMemoRules = null
                cachedAppCatRules.evictAll()
                cachedAlertKeywordsPair = null
            }
        }

        private suspend fun cachedActiveFilterRules(db: AppDatabase): List<FilterRule> {
            val now = System.currentTimeMillis()
            synchronized(ruleCacheLock) {
                val entry = cachedFilterRules
                if (entry != null && (now - entry.first) < RULE_CACHE_TTL_MS) {
                    return entry.second
                }
            }
            val fresh = db.filterDao().active()
            synchronized(ruleCacheLock) {
                cachedFilterRules = System.currentTimeMillis() to fresh
            }
            return fresh
        }

        private suspend fun cachedActiveAiRules(db: AppDatabase): List<AiRuleEntity> {
            val now = System.currentTimeMillis()
            synchronized(ruleCacheLock) {
                val entry = cachedAiRules
                if (entry != null && (now - entry.first) < RULE_CACHE_TTL_MS) {
                    return entry.second
                }
            }
            val fresh = db.aiRuleDao().active()
            synchronized(ruleCacheLock) {
                cachedAiRules = System.currentTimeMillis() to fresh
            }
            return fresh
        }

        private suspend fun cachedActiveUserTagRules(db: AppDatabase): List<UserTagRuleEntity> {
            val now = System.currentTimeMillis()
            synchronized(ruleCacheLock) {
                val entry = cachedUserTagRules
                if (entry != null && (now - entry.first) < RULE_CACHE_TTL_MS) {
                    return entry.second
                }
            }
            val fresh = db.userTagRuleDao().active()
            synchronized(ruleCacheLock) {
                cachedUserTagRules = System.currentTimeMillis() to fresh
            }
            return fresh
        }

        suspend fun cachedActiveMemoRules(db: AppDatabase): List<MemoRuleEntity> {
            val now = System.currentTimeMillis()
            synchronized(ruleCacheLock) {
                val entry = cachedMemoRules
                if (entry != null && (now - entry.first) < RULE_CACHE_TTL_MS) {
                    return entry.second
                }
            }
            val fresh = db.memoRuleDao().active()
            synchronized(ruleCacheLock) {
                cachedMemoRules = System.currentTimeMillis() to fresh
            }
            return fresh
        }

        private suspend fun cachedAppCategoryRules(db: AppDatabase, pkg: String): List<AppCategoryRuleEntity> {
            val now = System.currentTimeMillis()
            synchronized(ruleCacheLock) {
                val entry = cachedAppCatRules.get(pkg)
                if (entry != null && (now - entry.first) < RULE_CACHE_TTL_MS) {
                    return entry.second
                }
            }
            val fresh = db.appCategoryRuleDao().activeByPackage(pkg)
            synchronized(ruleCacheLock) {
                cachedAppCatRules.put(pkg, System.currentTimeMillis() to fresh)
            }
            return fresh
        }

        private fun cachedAlertKeywords(ctx: Context): List<String> {
            val now = System.currentTimeMillis()
            synchronized(ruleCacheLock) {
                val entry = cachedAlertKeywordsPair
                if (entry != null && (now - entry.first) < RULE_CACHE_TTL_MS) {
                    return entry.second
                }
            }
            val fresh = PhantomPrefs.getAlertKeywords(ctx)
            synchronized(ruleCacheLock) {
                cachedAlertKeywordsPair = System.currentTimeMillis() to fresh
            }
            return fresh
        }

        fun hasSuspiciousFeature(content: String, isRuleFiltered: Boolean): Boolean =
            IndicatorExtractor.hasSuspiciousFeature(content, isRuleFiltered)

        fun startOfTodayMillis(): Long {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

        fun alertScamFromWorker(
            ctx: Context,
            msgId: Long,
            pkg: String,
            sender: String,
            reason: String
        ) {
            if (!PhantomPrefs.isScamAlertNotifEnabled(ctx)) return
            if (PhantomPrefs.isInQuietHoursNow(ctx)) return
            val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel("scam_alert", "詐騙警示", NotificationManager.IMPORTANCE_HIGH)
            )
            val notifId = NotifIds.scamAlert(msgId)
            val intent = MessageDetailActivity.createIntent(ctx, msgId).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                ctx,
                notifId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val plainReason = com.phantom.checker.ui.stripMarkdown(reason)
                .replace(WHITESPACE_REGEX, " ")
                .trim()
                .ifBlank { "請前往詐騙警示中心查看風險分析" }
            val notif = Notification.Builder(ctx, "scam_alert")
                .setSmallIcon(R.drawable.ic_stat_phantom)
                .setContentTitle("⚠️ 偵測到高風險訊息：$sender")
                .setContentText(plainReason)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build()
            try {
                nm.notify(notifId, notif)
            } catch (_: SecurityException) {
            }
        }

        private const val AUTO_MEMO_DEDUP_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L
        private const val OWN_REPLY_ECHO_WINDOW_MS = 3L * 60L * 1000L
        private const val REMINDER_LEAD_MINUTES = 30L

        internal fun stripOwnSenderPrefix(text: String, myNames: Set<String>): String? {
            val trimmed = text.trimStart()
            val colonIdx = trimmed.indexOfFirst { it == ':' || it == '：' }
            if (colonIdx !in 1..24) return null
            val prefix = trimmed.substring(0, colonIdx).trim()
            val isOwnPrefix = prefix == "我" || myNames.any { it.equals(prefix, ignoreCase = true) }
            if (!isOwnPrefix) return null
            return trimmed.substring(colonIdx + 1).trimStart()
        }

        suspend fun recordOwnReplyEcho(
            ctx: Context,
            packageName: String,
            conversationKey: String,
            replyText: String
        ) {
            val norm = normalizeForSimilarity(replyText)
            if (norm.isEmpty()) return
            val now = System.currentTimeMillis()
            val dao = AppDatabaseHolder.db(ctx.applicationContext).ownReplyEchoDao()
            dao.deleteExpired(now - OWN_REPLY_ECHO_WINDOW_MS)
            dao.insert(
                com.phantom.checker.data.OwnReplyEchoEntity(
                    packageName = packageName,
                    conversationKey = conversationKey.trim(),
                    normalizedText = norm,
                    sentAt = now
                )
            )
        }

        internal fun normalizeForSimilarity(text: String): String =
            java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
                .trim()
                .lowercase(java.util.Locale.ROOT)
                .replace(PUNCTUATION_REGEX, " ")
                .replace(WHITESPACE_REGEX, " ")
                .trim()

        internal fun diceBigramSimilarity(a: String, b: String): Double {
            if (a == b) return 1.0
            if (a.length < 2 || b.length < 2) return 0.0
            val aBigrams = HashMap<String, Int>(a.length)
            for (i in 0 until a.length - 1) {
                val bg = a.substring(i, i + 2)
                aBigrams[bg] = (aBigrams[bg] ?: 0) + 1
            }
            var intersection = 0
            val bCount = b.length - 1
            for (i in 0 until bCount) {
                val bg = b.substring(i, i + 2)
                val rem = aBigrams[bg] ?: 0
                if (rem > 0) {
                    intersection++
                    aBigrams[bg] = rem - 1
                }
            }
            return (2.0 * intersection) / ((a.length - 1) + bCount)
        }

        suspend fun saveAutoMemoIfPresent(
            db: AppDatabase,
            msg: MessageEntity,
            rawMemo: String,
            memoType: String = AiClient.MEMO_TYPE_NOTE,
            todoDue: String = "",
            ctx: Context? = null
        ) {
            val clean = rawMemo.trim().take(40)
            if (clean.isEmpty()) return

            val matchedRule = try {
                val rules = cachedActiveMemoRules(db).filterNot {
                    it.action == MemoRuleEntity.ACTION_NEVER &&
                        it.packageName.isNullOrBlank() &&
                        it.sender.isNullOrBlank()
                }
                com.phantom.checker.util.MemoRuleEngine.match(
                    rules = rules,
                    packageName = msg.packageName,
                    sender = msg.sender,
                    content = msg.content
                )
            } catch (_: Exception) {
                null
            }
            if (matchedRule?.action == MemoRuleEntity.ACTION_NEVER) return
            val effectiveMemoType = when (matchedRule?.action) {
                MemoRuleEntity.ACTION_TODO -> AiClient.MEMO_TYPE_TODO
                MemoRuleEntity.ACTION_NOTE -> AiClient.MEMO_TYPE_NOTE
                else -> memoType
            }

            val now = System.currentTimeMillis()
            val normClean = normalizeForSimilarity(clean)
            val appLabel = msg.appLabel.ifBlank { msg.packageName }

            if (effectiveMemoType == AiClient.MEMO_TYPE_TODO) {
                if (db.todoDao().findByRelatedMessageId(msg.id) != null) return
                val since = now - AUTO_MEMO_DEDUP_WINDOW_MS
                val recentPending = db.todoDao().findRecentPendingByAppLabel(appLabel, since)
                val duplicateTodo = recentPending.firstOrNull { cand ->
                    val normCand = normalizeForSimilarity(cand.title)
                    normCand == normClean || diceBigramSimilarity(normCand, normClean) >= 0.9
                }
                val eventTime: Long? = try {
                    val structured = ClientTimeParser.parseStructuredDueAt(todoDue)?.takeIf { it > now }
                    structured ?: when (
                        val res = ClientTimeParser.parseNaturalReminderTime(null, msg.content, now)
                    ) {
                        is com.phantom.checker.util.NaturalTimeParseResult.Success -> res.epochMillis
                        is com.phantom.checker.util.NaturalTimeParseResult.PastTime,
                        com.phantom.checker.util.NaturalTimeParseResult.Unparseable -> null
                    }
                } catch (_: Exception) {
                    null
                }
                val leadMs = REMINDER_LEAD_MINUTES * 60L * 1000L
                val minFutureMs = 5L * 60L * 1000L
                val computedDue: Long? = eventTime?.let { evt ->
                    val leadDue = evt - leadMs
                    if (leadDue <= now) maxOf(now + minFutureMs, leadDue) else leadDue
                }
                if (duplicateTodo != null) {
                    val updatedDue = computedDue ?: duplicateTodo.dueAt
                    db.todoDao().update(
                        duplicateTodo.copy(
                            title = clean,
                            dueAt = updatedDue,
                            relatedMessageId = msg.id
                        )
                    )
                    val appCtx = ctx?.applicationContext
                    if (appCtx != null) {
                        if (computedDue != null && computedDue > now && computedDue != duplicateTodo.dueAt) {
                            ReminderScheduler.scheduleTodo(appCtx, duplicateTodo.id, computedDue)
                        }
                        ReminderWidgetProvider.updateAllIfChanged(appCtx)
                    }
                    return
                }

                val dueAt = computedDue ?: (msg.timestamp + 2L * 60L * 60L * 1000L)
                val newId = db.todoDao().insert(
                    TodoEntity(
                        title = clean,
                        dueAt = dueAt,
                        isDone = false,
                        createdAt = now,
                        relatedMessageId = msg.id,
                        sourceSessionId = null,
                        priority = 1
                    )
                )
                val appCtx = ctx?.applicationContext
                if (appCtx != null) {
                    if (dueAt > now) {
                        ReminderScheduler.scheduleTodo(appCtx, newId, dueAt)
                    }
                    ReminderWidgetProvider.updateAllIfChanged(appCtx)
                }
                return
            }

            val existing = db.noteDao().findAutoByMessageId(msg.id)
            if (existing != null) {
                if (existing.pinned) return
                db.noteDao().update(existing.copy(content = clean, updatedAt = now))
                com.phantom.checker.widget.NotesWidgetProvider.updateAllIfChanged(ctx)
                return
            }

            val since = now - AUTO_MEMO_DEDUP_WINDOW_MS
            val recent = db.noteDao().findRecentAutoByAppLabel(appLabel, since)
            val duplicate = recent.firstOrNull { cand ->
                val normCand = normalizeForSimilarity(cand.content)
                normCand == normClean || diceBigramSimilarity(normCand, normClean) >= 0.9
            }
            if (duplicate != null) {
                if (duplicate.pinned) return
                db.noteDao().update(
                    duplicate.copy(
                        content = clean,
                        updatedAt = now,
                        relatedMessageId = msg.id
                    )
                )
                com.phantom.checker.widget.NotesWidgetProvider.updateAllIfChanged(ctx)
                return
            }

            db.noteDao().insert(
                NoteEntity(
                    content = clean,
                    source = NoteEntity.SOURCE_AUTO,
                    relatedMessageId = msg.id,
                    relatedAppLabel = appLabel,
                    createdAt = now,
                    updatedAt = now
                )
            )
            com.phantom.checker.widget.NotesWidgetProvider.updateAllIfChanged(ctx)
        }

        private fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/** Database 單例 */
object AppDatabaseHolder {
    @Volatile private var instance: AppDatabase? = null

    private data class DefaultAiTagSpec(
        val name: String,
        val colorHex: String,
        val sortOrder: Int,
        val aiHint: String
    )

    private data class DefaultAppCategorySpec(
        val name: String,
        val colorHex: String,
        val sortOrder: Int,
        val packages: List<String>
    )

    private val DEFAULT_AI_TAGS = listOf(
        DefaultAiTagSpec(
            name = "必看",
            colorHex = "#4CAF50",
            sortOrder = 0,
            aiHint = "個人通訊、到貨通知、驗證碼以外的帳務、優惠券/點數回饋/折扣碼、行程提醒等使用者需要行動或受益的訊息"
        ),
        DefaultAiTagSpec(
            name = "行銷",
            colorHex = "#FF9800",
            sortOrder = 1,
            aiHint = "純促銷、廣告、電子報、無具體優惠的行銷內容"
        ),
        DefaultAiTagSpec(
            name = "垃圾通知",
            colorHex = "#9E9E9E",
            sortOrder = 2,
            aiHint = "系統雜訊、重複狀態通知、無意義更新"
        )
    )

    private val DEFAULT_APP_CATEGORIES = listOf(
        DefaultAppCategorySpec(
            name = "社交",
            colorHex = "#2563EB",
            sortOrder = 10,
            packages = listOf(
                "jp.naver.line.android",
                "com.facebook.orca",
                "com.instagram.android",
                "com.discord",
                "org.telegram.messenger",
                "com.whatsapp"
            )
        ),
        DefaultAppCategorySpec(
            name = "購物",
            colorHex = "#D97706",
            sortOrder = 11,
            packages = listOf(
                "com.pchome.shopping",
                "com.PChome.ecshopping",
                "com.momo.mobile.shopping",
                "com.shopee.tw",
                "com.android.vending"
            )
        ),
        DefaultAppCategorySpec(
            name = "金融帳務",
            colorHex = "#059669",
            sortOrder = 12,
            packages = listOf(
                "com.twm.android.twm",
                "com.taiwanmobile.myfintech",
                "com.cht.emome",
                "com.fet.fetnet",
                "com.cathaybk.mymobibank.android",
                "com.ctbcbank.mbank",
                "com.esunbank.ebank",
                "com.richart.mobile",
                "tw.com.taishinbank.mobile",
                "com.linebank.tw"
            )
        ),
        DefaultAppCategorySpec(
            name = "物流",
            colorHex = "#0891B2",
            sortOrder = 13,
            packages = listOf(
                "com.tcat.android",
                "tw.com.president.seven",
                "com.seventw.app",
                "com.familymart.android",
                "com.hilife.app",
                "com.dhl.express.dhl"
            )
        ),
        DefaultAppCategorySpec(
            name = "系統工具",
            colorHex = "#4B5563",
            sortOrder = 14,
            packages = listOf(
                "com.android.systemui",
                "com.google.android.gms",
                "com.android.settings"
            )
        )
    )

    fun db(ctx: Context): AppDatabase {
        val appCtx = ctx.applicationContext
        return instance ?: synchronized(this) {
            instance ?: androidx.room.Room.databaseBuilder(
                appCtx, AppDatabase::class.java, "phantomcheck.db"
            ).addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19
            ).addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    seedDefaultAiTagsIfNeeded(appCtx, db)
                    seedAppCategoryTagsAndRulesIfNeeded(appCtx, db)
                    purgePresetAiRulesIfNeeded(appCtx, db)
                }
            }).build().also { instance = it }
        }
    }

    private fun seedDefaultAiTagsIfNeeded(
        appCtx: Context,
        db: androidx.sqlite.db.SupportSQLiteDatabase
    ) {
        val totalCount = try {
            db.query("SELECT COUNT(*) FROM tags WHERE isAiManaged = 1").use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }
        } catch (_: Exception) {
            0
        }
        if (PhantomPrefs.isDefaultAiTagsSeeded(appCtx) && totalCount > 0) return
        try {
            db.beginTransaction()
            try {
                DEFAULT_AI_TAGS.forEach { spec ->
                    val cursor = db.query("SELECT COUNT(*) FROM tags WHERE name = ?", arrayOf(spec.name))
                    val exists = cursor.use { c -> c.moveToFirst() && c.getInt(0) > 0 }
                    if (!exists) {
                        db.execSQL(
                            "INSERT INTO tags (name, colorHex, sortOrder, aiHint, isAiManaged, isAppCategory) VALUES (?, ?, ?, ?, 1, 0)",
                            arrayOf<Any>(spec.name, spec.colorHex, spec.sortOrder, spec.aiHint)
                        )
                    }
                }
                db.setTransactionSuccessful()
                PhantomPrefs.setDefaultAiTagsSeeded(appCtx, true)
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
        }
    }

    private fun seedAppCategoryTagsAndRulesIfNeeded(
        appCtx: Context,
        db: androidx.sqlite.db.SupportSQLiteDatabase
    ) {
        if (PhantomPrefs.isDefaultAppCatSeeded(appCtx)) return
        try {
            db.beginTransaction()
            try {
                DEFAULT_APP_CATEGORIES.forEach { cat ->
                    var tagId: Long = -1L
                    db.query("SELECT id FROM tags WHERE name = ? LIMIT 1", arrayOf(cat.name)).use { c ->
                        if (c.moveToFirst()) tagId = c.getLong(0)
                    }
                    if (tagId <= 0L) {
                        db.execSQL(
                            "INSERT INTO tags (name, colorHex, sortOrder, aiHint, isAiManaged, isAppCategory) VALUES (?, ?, ?, '', 0, 1)",
                            arrayOf<Any>(cat.name, cat.colorHex, cat.sortOrder)
                        )
                        db.query("SELECT id FROM tags WHERE name = ? LIMIT 1", arrayOf(cat.name)).use { c ->
                            if (c.moveToFirst()) tagId = c.getLong(0)
                        }
                    }
                    if (tagId > 0L) {
                        cat.packages.forEach { pkg ->
                            val exists = db.query(
                                "SELECT COUNT(*) FROM app_category_rules WHERE packageName = ? AND tagId = ?",
                                arrayOf<Any>(pkg, tagId)
                            ).use { c -> c.moveToFirst() && c.getInt(0) > 0 }
                            if (!exists) {
                                db.execSQL(
                                    "INSERT INTO app_category_rules (packageName, tagId, isEnabled) VALUES (?, ?, 1)",
                                    arrayOf<Any>(pkg, tagId)
                                )
                            }
                        }
                    }
                }
                db.setTransactionSuccessful()
                PhantomPrefs.setDefaultAppCatSeeded(appCtx, true)
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
        }
    }

    /**
     * 舊版本曾內建與特定使用者相關的預設 AI 規則（電信白名單與購物標籤），
     * 為使 App 保持通用，改為一次性清除這些由 App 自動寫入的預設規則；
     * 不影響使用者自行新增的規則。
     */
    private fun purgePresetAiRulesIfNeeded(
        appCtx: Context,
        db: androidx.sqlite.db.SupportSQLiteDatabase
    ) {
        if (PhantomPrefs.isPresetAiRulesPurged(appCtx)) return
        val presetRuleTexts = listOf(
            "台灣大哥大 twm5g.co 官方短連結的代收通知不是詐騙",
            "PChome 含 P 幣或折扣碼的訊息標必看"
        )
        try {
            db.beginTransaction()
            try {
                presetRuleTexts.forEach { ruleText ->
                    db.execSQL("DELETE FROM ai_rules WHERE ruleText = ?", arrayOf<Any>(ruleText))
                }
                db.setTransactionSuccessful()
                PhantomPrefs.setPresetAiRulesPurged(appCtx, true)
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
        }
    }
}
