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
import android.util.LruCache
import com.phantom.checker.R
import com.phantom.checker.data.AppCategoryRuleEntity
import com.phantom.checker.data.AppDatabase
import com.phantom.checker.data.FilterRule
import com.phantom.checker.data.IntelResultEntity
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.TagEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.network.IndicatorExtractor
import com.phantom.checker.network.IntelClient
import com.phantom.checker.ui.MessageDetailActivity
import com.phantom.checker.ui.ScamCenterActivity
import com.phantom.checker.util.MuteMode
import com.phantom.checker.util.NotifClickBehavior
import com.phantom.checker.util.PhantomPrefs
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
        AiClient.init(applicationContext)
    }

    override fun onDestroy() {
        isConnected = false
        scope.cancel()
        super.onDestroy()
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        isConnected = true
        connectTime = System.currentTimeMillis()
        StatsForegroundService.startOrRefresh(applicationContext, force = true)
        KeepAlivePolicy.scheduleAll(applicationContext)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
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

        val rawText = (
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim()
                    ?.takeIf { it.isNotEmpty() }
            ) ?: return

        val title = resolveNotificationTitle(extras, rawText, pkg)

        // 內容感知同 sbn.key 更新節流 + 疊加式通知尾巴提取
        val sbnKey = sbn.key
        val throttleSec = PhantomPrefs.getEffectiveSameKeyThrottleSeconds(applicationContext, pkg)
        val digitVarianceEnabled = PhantomPrefs.isSameKeyDigitVarianceThrottleEnabled(applicationContext)
        var text = rawText
        if (!sbnKey.isNullOrBlank()) {
            val prevRecord = synchronized(sameKeyHistory) { sameKeyHistory.get(sbnKey) }
            if (prevRecord != null) {
                if (throttleSec > 0 && (sbn.postTime - prevRecord.timestamp) < throttleSec * 1000L) {
                    val exactSame = (title == prevRecord.title && rawText == prevRecord.text)
                    val digitMicroChange = digitVarianceEnabled &&
                        title == prevRecord.title &&
                        normalizeDigitsAndWhitespace(rawText) == normalizeDigitsAndWhitespace(prevRecord.text)
                    if (exactSame || digitMicroChange) {
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

        // 10 分鐘同內容去重
        val dedupKey = sha256("$pkg|$sbnKey|$title|$text")
        synchronized(dedupCache) {
            val lastTime = dedupCache.get(dedupKey)
            if (lastTime != null && (sbn.postTime - lastTime) < DEDUP_WINDOW_MS) {
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
                    content = text,
                    timestamp = sbn.postTime,
                    riskScore = if (isRuleScam) 95 else 0,
                    riskLabel = if (isRuleScam) "scam" else "",
                    riskReason = if (isRuleScam) "命中自訂詐騙過濾規則：${matchedRule?.keyword}" else "",
                    isFiltered = isRuleFiltered,
                    isRead = silentMuted,
                    dedupKey = dedupKey
                )
            )

            // App 分類標籤（零 token 規則式，insert 後直接打標籤）
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

            // 雲端防詐情報預查（零 LLM token，僅送 URL / crypto 地址；ai_rules 白名單優先跳過）
            if (PhantomPrefs.isIntelEnabled(applicationContext)) {
                val indicators = IndicatorExtractor.extract(text)
                if (indicators.isNotEmpty()) {
                    val activeAiRules = try {
                        db.aiRuleDao().active()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (!IntelClient.isWhitelisted(indicators, text, activeAiRules)) {
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
                text.length >= MIN_AI_CONTENT_LENGTH
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
        val aiRules = db.aiRuleDao().active()

        val results = AiClient.analyzeMessageBatch(
            items = items,
            tags = aiTags,
            enableAutoTag = enableAutoTag,
            context = applicationContext,
            rules = aiRules
        )
        if (results.isEmpty()) return

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
                val refs = result.tagIds.map { tagId ->
                    MessageTagCrossRef(
                        messageId = msgId,
                        tagId = tagId,
                        source = MessageTagCrossRef.SOURCE_AUTO
                    )
                }
                db.tagDao().addTagsToMessageBatch(refs)
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
        val notifId = 2000 + ((msgId % 40000L).toInt())
        val pendingIntent = buildClickPendingIntent(ctx, notifId, msgId, pkg)
        val plainReason = com.phantom.checker.ui.stripMarkdown(reason)
            .replace(Regex("\\s+"), " ")
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
        val notifId = 50000 + ((msgId % 40000L).toInt())
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

    companion object {
        @Volatile
        var isConnected: Boolean = false

        private const val DEDUP_WINDOW_MS = 10L * 60L * 1000L
        private const val REPLAY_GRACE_MS = 5000L
        private const val MIN_AI_CONTENT_LENGTH = 10
        private const val STATS_RESPAWN_BURST_MS = 400L
        private const val BATCH_COALESCE_MS = 2500L
        private const val BATCH_WINDOW_MS = 60_000L
        private const val MAX_BATCH_SIZE = 5
        private const val RULE_CACHE_TTL_MS = 30_000L

        private val ruleCacheLock = Any()
        private var cachedFilterRules: Pair<Long, List<FilterRule>>? = null
        private val cachedAppCatRules = object : LruCache<String, Pair<Long, List<AppCategoryRuleEntity>>>(64) {}
        private var cachedAlertKeywordsPair: Pair<Long, List<String>>? = null

        fun invalidateRuleCaches() {
            synchronized(ruleCacheLock) {
                cachedFilterRules = null
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
            aiHint = "個人通訊、到貨通知、驗證碼以外的帳務、優惠券/P幣/折扣碼、行程提醒等使用者需要行動或受益的訊息"
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
                AppDatabase.MIGRATION_7_8
            ).addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onOpen(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    super.onOpen(db)
                    seedDefaultAiTagsIfNeeded(appCtx, db)
                    seedAppCategoryTagsAndRulesIfNeeded(appCtx, db)
                    seedDefaultAiRulesIfNeeded(appCtx, db)
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

    private fun seedDefaultAiRulesIfNeeded(
        appCtx: Context,
        db: androidx.sqlite.db.SupportSQLiteDatabase
    ) {
        if (PhantomPrefs.isDefaultAiRulesSeeded(appCtx)) return
        try {
            db.beginTransaction()
            try {
                val now = System.currentTimeMillis()
                val whitelistText = "台灣大哥大 twm5g.co 官方短連結的代收通知不是詐騙"
                val hasWl = db.query("SELECT COUNT(*) FROM ai_rules WHERE ruleText = ?", arrayOf(whitelistText))
                    .use { c -> c.moveToFirst() && c.getInt(0) > 0 }
                if (!hasWl) {
                    db.execSQL(
                        "INSERT INTO ai_rules (ruleText, targetTagId, kind, isEnabled, createdAt) VALUES (?, NULL, 'whitelist', 1, ?)",
                        arrayOf<Any>(whitelistText, now)
                    )
                }

                var mustSeeId: Long? = null
                db.query("SELECT id FROM tags WHERE name = ? LIMIT 1", arrayOf("必看")).use { c ->
                    if (c.moveToFirst()) mustSeeId = c.getLong(0)
                }
                val tagRuleText = "PChome 含 P 幣或折扣碼的訊息標必看"
                val hasTagRule = db.query("SELECT COUNT(*) FROM ai_rules WHERE ruleText = ?", arrayOf(tagRuleText))
                    .use { c -> c.moveToFirst() && c.getInt(0) > 0 }
                if (!hasTagRule && mustSeeId != null) {
                    db.execSQL(
                        "INSERT INTO ai_rules (ruleText, targetTagId, kind, isEnabled, createdAt) VALUES (?, ?, 'tag', 1, ?)",
                        arrayOf<Any>(tagRuleText, mustSeeId!!, now + 1)
                    )
                }

                db.setTransactionSuccessful()
                PhantomPrefs.setDefaultAiRulesSeeded(appCtx, true)
            } finally {
                db.endTransaction()
            }
        } catch (_: Exception) {
        }
    }
}
