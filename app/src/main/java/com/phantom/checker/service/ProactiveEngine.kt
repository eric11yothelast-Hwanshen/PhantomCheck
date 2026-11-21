package com.phantom.checker.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.phantom.checker.R
import com.phantom.checker.data.ChatMessageEntity
import com.phantom.checker.data.ChatSessionEntity
import com.phantom.checker.data.MemoryEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.ui.MainActivity
import com.phantom.checker.util.CalendarBridge
import com.phantom.checker.util.EncryptedByokStore
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * #337 主動推播與簡報引擎：
 * - 零 token 變動偵測：本機計算 todo / calendar / 近 24h 重要通知 SHA-256 指紋；無實質變動零 AI 呼叫。
 * - 三類主動情境：行程待辦提醒、跨上下文撞期附替代空檔、興趣與地區情報（含選配外部搜尋 BYOK）。
 * - 嚴守鐵律：絕不偽裝使用者訊息；通知一律 PendingIntent.getActivity 直達（零 trampoline）；勿擾仍寫入聊天不發通知。
 */
object ProactiveEngine {

    const val CHANNEL_ASSISTANT_BRIEFING = "assistant_briefing"
    private const val BRIEFING_SESSION_TITLE = "AI 助手簡報與主動提醒"

    data class MonitorCheckResult(
        val fingerprintChanged: Boolean,
        val hasHighPriority: Boolean,
        val highPrioritySummary: String?
    )

    /** 零 token 本機指紋計算與高優先級檢查（全程 Dispatchers.IO，零 AI 呼叫） */
    suspend fun evaluateLocalFingerprintAndPriority(
        ctx: Context,
        nowMs: Long = System.currentTimeMillis()
    ): MonitorCheckResult = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val db = AppDatabaseHolder.db(appCtx)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val twoDaysEndMs = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()

        val pendingTodos = runCatching { db.todoDao().pendingTodos() }.getOrDefault(emptyList())
        val calEvents = if (CalendarBridge.isCalendarEnabledForAi(appCtx)) {
            runCatching { CalendarBridge.queryInstances(appCtx, dayStartMs, twoDaysEndMs) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val since24h = nowMs - 24L * 60L * 60L * 1000L
        val recentImportant = runCatching {
            db.messageDao().paged(80, 0).filter { it.timestamp >= since24h && (it.riskScore >= 60 || it.isStarred) }
        }.getOrDefault(emptyList())

        val rawSig = buildString {
            append("T:")
            pendingTodos.sortedBy { it.id }.forEach { append("${it.id}:${it.dueAt};") }
            append("|C:")
            calEvents.sortedBy { it.beginMs }.forEach { append("${it.eventId}:${it.beginMs}:${it.endMs};") }
            append("|M:")
            recentImportant.sortedBy { it.id }.forEach { append("${it.id}:${it.riskScore};") }
        }
        val fp = sha256Hex(rawSig)
        val prevFp = PhantomPrefs.getProactiveLastFingerprint(appCtx)
        val changed = fp != prevFp
        if (changed) {
            PhantomPrefs.setProactiveLastFingerprint(appCtx, fp)
            PhantomPrefs.setProactiveDirty(appCtx, true)
        }

        // 高優先級條件：todo 24h 內到期、當天全天事件、事件近 60 分鐘開始
        val urgentTodos = pendingTodos.filter { it.dueAt in (nowMs - 30L * 60_000L)..(nowMs + 24L * 60L * 60_000L) }
        val soonEvents = calEvents.filter { !it.allDay && it.beginMs in nowMs..(nowMs + 60L * 60_000L) }
        val todayAllDay = calEvents.filter { it.allDay }

        val highPriorityParts = mutableListOf<String>()
        if (soonEvents.isNotEmpty() && PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE)) {
            val ev = soonEvents.first()
            val hhmm = Instant.ofEpochMilli(ev.beginMs).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
            highPriorityParts.add("⏰ 行程即將開始（$hhmm）：${ev.title}")
            // 跨上下文撞期檢查
            if (PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_CONFLICT)) {
                val conflicts = CalendarBridge.findConflictingEvents(appCtx, ev.beginMs, ev.endMs)
                    .filter { it.eventId != ev.eventId }
                if (conflicts.isNotEmpty()) {
                    val freeSummary = CalendarBridge.queryFreeSlotsSummary(appCtx, days = 1, dayFilter = "today")
                    highPriorityParts.add("⚠️ 偵測到撞期：${CalendarBridge.formatConflictSummary(conflicts)}\n替代空檔參考：${freeSummary.take(160)}")
                }
            }
        }
        if (urgentTodos.isNotEmpty() && PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_TODO)) {
            val topTodo = urgentTodos.minByOrNull { it.dueAt }!!
            highPriorityParts.add("📌 24 小時內待辦：${topTodo.title}")
        }
        if (todayAllDay.isNotEmpty() && PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE)) {
            highPriorityParts.add("📅 今日全天事件：${todayAllDay.first().title}")
        }

        val hasHigh = highPriorityParts.isNotEmpty()
        if (hasHigh) {
            PhantomPrefs.setProactiveDirty(appCtx, true)
        }
        MonitorCheckResult(
            fingerprintChanged = changed,
            hasHighPriority = hasHigh,
            highPrioritySummary = highPriorityParts.takeIf { it.isNotEmpty() }?.joinToString("\n")
        )
    }

    /** 產出並投遞早／晚個人簡報（寫入 AI 助手聊天＋assistant_briefing 通知） */
    suspend fun deliverBriefing(
        ctx: Context,
        isMorning: Boolean,
        force: Boolean = false
    ): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val nowMs = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)

        if (!force && PhantomPrefs.getLastBriefingDay(appCtx, isMorning) == dayKey) {
            return@withContext false
        }
        val cap = PhantomPrefs.getProactiveDailyCap(appCtx)
        if (!force && PhantomPrefs.getProactiveDailySentCount(appCtx, dayKey) >= cap) {
            return@withContext false
        }

        // 先跑一次零 token 指紋檢查；省 token 模式且無變動（!dirty）時不呼叫 AI 直接跳過
        val monitor = evaluateLocalFingerprintAndPriority(appCtx, nowMs)
        val tokenSaver = PhantomPrefs.isTokenSaverEnabled(appCtx)
        if (!force && tokenSaver && !PhantomPrefs.isProactiveDirty(appCtx) && !monitor.hasHighPriority) {
            return@withContext false
        }

        val db = AppDatabaseHolder.db(appCtx)
        val prefsSet = if (isMorning) {
            PhantomPrefs.getMorningBriefingContents(appCtx)
        } else {
            PhantomPrefs.getEveningBriefingContents(appCtx)
        }
        val assistantName = PhantomPrefs.getAssistantName(appCtx)
        val headerTitle = if (isMorning) "☀️ $assistantName 早間簡報" else "🌙 $assistantName 晚間簡報"

        val facts = mutableListOf<String>()
        if (PhantomPrefs.BRIEFING_CONTENT_SCHEDULE in prefsSet && CalendarBridge.isCalendarEnabledForAi(appCtx)) {
            val dayFilter = if (isMorning) "today" else "tomorrow"
            val calSummary = runCatching {
                CalendarBridge.queryFreeSlotsSummary(appCtx, days = 1, dayFilter = dayFilter)
            }.getOrDefault("")
            if (calSummary.isNotBlank()) facts.add(calSummary.take(260))
        }
        if (PhantomPrefs.BRIEFING_CONTENT_TODO in prefsSet) {
            val todos = runCatching { db.todoDao().pendingTodos() }.getOrDefault(emptyList())
            val overdue = todos.count { it.dueAt < nowMs }
            if (todos.isNotEmpty()) {
                val topTitles = todos.take(4).joinToString("、") { it.title.take(20) }
                facts.add("未完成待辦 ${todos.size} 筆（逾期 $overdue 筆）：$topTitles")
            }
        }
        if (PhantomPrefs.BRIEFING_CONTENT_IMPORTANT_NOTIFS in prefsSet) {
            val since24h = nowMs - 24L * 60L * 60L * 1000L
            val highRisk = runCatching {
                db.messageDao().paged(60, 0).filter { it.timestamp >= since24h && it.riskScore >= 60 }
            }.getOrDefault(emptyList())
            if (highRisk.isNotEmpty()) {
                facts.add("近 24 小時高風險通知 ${highRisk.size} 則（最高風險 ${highRisk.maxOf { it.riskScore }} 分）")
            }
        }

        // 興趣與地區提醒型推播（省 token 模式下自動停用）
        val interestKeywords = if (PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_INTEREST)) {
            runCatching {
                db.memoryDao().activeNormalMemoriesForPrompt(nowMs)
                    .filter { it.category == MemoryEntity.CATEGORY_INTEREST && it.privacyTier == MemoryEntity.PRIVACY_NORMAL }
                    .take(3)
                    .map { it.subject }
            }.getOrDefault(emptyList())
        } else emptyList()
        val regionKeyword = if (PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_REGION)) {
            PhantomPrefs.getProactiveRegionKeyword(appCtx)
        } else ""

        if (interestKeywords.isNotEmpty()) {
            facts.add("關注興趣主題：${interestKeywords.joinToString("、")}")
        }
        if (regionKeyword.isNotBlank()) {
            facts.add("關注地區：$regionKeyword")
        }

        // 選配外部情報搜尋（BYOK，未設定或省 token 模式誠實跳過不報錯）
        var externalIntel: String? = null
        if (!tokenSaver &&
            PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_EXTERNAL_SEARCH) &&
            EncryptedByokStore.isExternalSearchConfigured(appCtx)
        ) {
            val queryTerms = (interestKeywords + listOfNotNull(regionKeyword.takeIf { it.isNotBlank() })).joinToString(" ")
            if (queryTerms.isNotBlank()) {
                externalIntel = AiClient.queryExternalSearchEndpoint(appCtx, "今日 $queryTerms 重要焦點")
            }
        }

        val templateBody = buildString {
            appendLine("**$headerTitle**（${today.format(DateTimeFormatter.ofPattern("MM/dd", Locale.US))}）")
            if (facts.isEmpty()) {
                appendLine("目前行程與待辦平穩，沒有緊急項目。")
            } else {
                facts.forEach { appendLine("- $it") }
            }
            if (!externalIntel.isNullOrBlank()) {
                appendLine("\n**外部情報**")
                appendLine(externalIntel)
            }
        }.trim()

        val finalBody = if (tokenSaver || facts.isEmpty()) {
            templateBody // 省 token 模式純零 token 模板
        } else {
            val polished = AiClient.generateBriefingText(
                context = appCtx,
                systemPrompt = "你是「$assistantName」，請將以下本機整理的事實潤飾為簡潔溫暖的繁體中文簡報（≤220字，保留所有關鍵數字與項目，不可編造）。",
                userPrompt = templateBody,
                maxTokens = 320
            )
            if (!polished.isNullOrBlank()) "**$headerTitle**\n$polished" else templateBody
        }

        val msgId = writeAssistantMessage(db, finalBody, nowMs)
        PhantomPrefs.setLastBriefingDay(appCtx, isMorning, dayKey)
        PhantomPrefs.setProactiveDirty(appCtx, false)
        PhantomPrefs.incrementProactiveDailySentCount(appCtx, dayKey)
        PhantomPrefs.saveLastBriefingMeta(appCtx, finalBody.replace("**", "").take(120), nowMs, msgId)
        if (PhantomPrefs.isDiscordForwardEnabled(appCtx) && EncryptedByokStore.isDiscordConfigured(appCtx)) {
            runCatching {
                CloudExtensionsClient.sendDiscordMessage(appCtx, finalBody)
            }
        }

        if (!isInDndWindow(appCtx, nowMs)) {
            postBriefingNotification(
                ctx = appCtx,
                notifId = if (isMorning) NotifIds.BRIEFING_MORNING else NotifIds.BRIEFING_EVENING,
                title = headerTitle,
                preview = finalBody.replace("**", "").replace("\n", " ").take(90),
                messageId = msgId
            )
        }
        true
    }

    /** 發送高優先級主動提醒（零 token 模板優先，遵守上限與勿擾） */
    suspend fun deliverProactiveAlert(
        ctx: Context,
        title: String,
        body: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val dayKey = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (PhantomPrefs.getProactiveDailySentCount(appCtx, dayKey) >= PhantomPrefs.getProactiveDailyCap(appCtx)) {
            return@withContext false
        }
        val db = AppDatabaseHolder.db(appCtx)
        val assistantName = PhantomPrefs.getAssistantName(appCtx)
        val fullText = "**🔔 $assistantName 主動提醒｜$title**\n$body"
        val msgId = writeAssistantMessage(db, fullText, nowMs)
        PhantomPrefs.incrementProactiveDailySentCount(appCtx, dayKey)
        if (!isInDndWindow(appCtx, nowMs)) {
            postBriefingNotification(
                ctx = appCtx,
                notifId = NotifIds.PROACTIVE_PUSH,
                title = "$assistantName：$title",
                preview = body.replace("\n", " ").take(90),
                messageId = msgId
            )
        }
        true
    }

    private suspend fun writeAssistantMessage(
        db: com.phantom.checker.data.AppDatabase,
        content: String,
        nowMs: Long
    ): Long {
        val sessions = db.chatDao().allSessions()
        val sessionId = sessions.firstOrNull()?.id ?: db.chatDao().insertSession(
            ChatSessionEntity(title = BRIEFING_SESSION_TITLE, createdAt = nowMs)
        )
        return db.chatDao().insertMessage(
            ChatMessageEntity(
                sessionId = sessionId,
                role = ChatMessageEntity.ROLE_ASSISTANT,
                content = content,
                createdAt = nowMs
            )
        )
    }

    fun isInDndWindow(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!PhantomPrefs.isProactiveDndEnabled(ctx)) return false
        val nowTime = Instant.ofEpochMilli(nowMs).atZone(ZoneId.systemDefault()).toLocalTime()
        val start = BriefingScheduler.parseHHmm(PhantomPrefs.getProactiveDndStart(ctx), LocalTime.of(0, 0))
        val end = BriefingScheduler.parseHHmm(PhantomPrefs.getProactiveDndEnd(ctx), LocalTime.of(7, 0))
        return if (start <= end) {
            !nowTime.isBefore(start) && nowTime.isBefore(end)
        } else {
            // 跨午夜時段（例如 23:00–07:00）
            !nowTime.isBefore(start) || nowTime.isBefore(end)
        }
    }

    fun areNotificationsEnabled(ctx: Context): Boolean {
        return androidx.core.app.NotificationManagerCompat.from(ctx.applicationContext).areNotificationsEnabled()
    }

    fun openAppNotificationSettings(ctx: Context) {
        val intent = Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            ctx.startActivity(intent)
        }.onFailure {
            runCatching {
                ctx.startActivity(
                    Intent(
                        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.fromParts("package", ctx.packageName, null)
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun postBriefingNotification(
        ctx: Context,
        notifId: Int,
        title: String,
        preview: String,
        messageId: Long
    ) {
        if (!areNotificationsEnabled(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ASSISTANT_BRIEFING,
                "AI 助手早晚簡報與主動提醒",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        // 絕不用 trampoline：PendingIntent.getActivity 直達 MainActivity Assistant 分頁
        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, BriefingScheduler.TAB_ASSISTANT)
            putExtra(BriefingScheduler.EXTRA_BRIEFING_MSG_ID, messageId)
        }
        val pi = PendingIntent.getActivity(
            ctx,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ASSISTANT_BRIEFING)
            .setSmallIcon(R.drawable.ic_stat_phantom)
            .setContentTitle(title)
            .setContentText(preview)
            .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(notifId, notif)
        } catch (_: SecurityException) {
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/** #337 15 分鐘週期零 token 變動偵測 Worker（無實質變動零 AI 呼叫） */
class ProactiveMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val check = ProactiveEngine.evaluateLocalFingerprintAndPriority(applicationContext)
            if (check.fingerprintChanged && check.hasHighPriority && !check.highPrioritySummary.isNullOrBlank()) {
                ProactiveEngine.deliverProactiveAlert(
                    ctx = applicationContext,
                    title = "重要行程／待辦提醒",
                    body = check.highPrioritySummary
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.success()
        }
    }
}
