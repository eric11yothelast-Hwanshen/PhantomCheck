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
        val highPrioritySummary: String?,
        val pendingMilestoneKeys: List<String> = emptyList()
    )

    /** 零 token 本機指紋計算與事件里程碑高優先級檢查（全程 Dispatchers.IO，零 AI 呼叫） */
    suspend fun evaluateLocalFingerprintAndPriority(
        ctx: Context,
        nowMs: Long = System.currentTimeMillis()
    ): MonitorCheckResult = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val db = AppDatabaseHolder.db(appCtx)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val dayKey = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val dayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStartMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
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

        // #372 事件里程碑式去重 ＋ #377 可自訂提前分鐘數與全天事件提醒時刻
        val deliveredMilestones = PhantomPrefs.getPrunedProactiveMilestones(appCtx, dayKey)
        val newMilestoneKeys = mutableListOf<String>()
        val highPriorityParts = mutableListOf<String>()
        val leadMinutes = PhantomPrefs.getProactiveReminderLeadMinutes(appCtx)
        val leadWindowMs = leadMinutes * 60_000L

        // #424 回饋驅動主題節流：近 7 日單一主題 👎≥2 時等待窗拉長 1.5 倍（從未給回饋者倍數=1.0，與 v4.0.0 行為一致）
        val scheduleThrottled = isTopicThrottledByFeedback(appCtx, PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE, nowMs)
        val conflictThrottled = isTopicThrottledByFeedback(appCtx, PhantomPrefs.PROACTIVE_TOPIC_CONFLICT, nowMs)
        val todoThrottled = isTopicThrottledByFeedback(appCtx, PhantomPrefs.PROACTIVE_TOPIC_TODO, nowMs)

        // 1. 行程即將開始（依使用者設定提前分鐘數窗口）＋ 跨上下文撞期
        val soonEvents = calEvents
            .filter { !it.allDay && it.beginMs in nowMs..(nowMs + leadWindowMs) }
            .sortedBy { it.beginMs }
        if (soonEvents.isNotEmpty() && !scheduleThrottled && PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE)) {
            for (ev in soonEvents) {
                val soonKey = "event:${ev.eventId}:$dayKey:soon${leadMinutes}m"
                if (soonKey !in deliveredMilestones) {
                    val hhmm = Instant.ofEpochMilli(ev.beginMs).atZone(zone)
                        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.US))
                    highPriorityParts.add("⏰ 行程即將開始（$hhmm，提前 ${leadMinutes} 分鐘）：${ev.title}")
                    newMilestoneKeys.add(soonKey)
                    if (!conflictThrottled && PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_CONFLICT)) {
                        val conflictKey = "event:${ev.eventId}:$dayKey:conflict"
                        if (conflictKey !in deliveredMilestones) {
                            val conflicts = CalendarBridge.findConflictingEvents(appCtx, ev.beginMs, ev.endMs)
                                .filter { it.eventId != ev.eventId }
                            if (conflicts.isNotEmpty()) {
                                val freeSummary = CalendarBridge.queryFreeSlotsSummary(appCtx, days = 1, dayFilter = "today")
                                highPriorityParts.add("⚠️ 偵測到撞期：${CalendarBridge.formatConflictSummary(conflicts)}\n替代空檔參考：${freeSummary.trim()}")
                                newMilestoneKeys.add(conflictKey)
                            } else {
                                val allDayNotes = CalendarBridge.findAllDayEventsInWindow(appCtx, ev.beginMs, ev.endMs)
                                    .filter { it.eventId != ev.eventId }
                                CalendarBridge.formatAllDayInfoNote(allDayNotes)?.let { highPriorityParts.add(it) }
                            }
                        }
                    }
                    break // 每次檢查優先聚焦最近一筆未送達的即將開始行程
                }
            }
        }

        // 2. 待辦關鍵里程碑（逾期 / 提前 leadMinutes 分鐘內 / 24 小時內）
        if (!todoThrottled && PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_TODO)) {
            val overdueWindowStart = nowMs - 30L * 60_000L
            val soonEnd = nowMs + leadWindowMs
            val due24hEnd = nowMs + 24L * 60L * 60_000L
            val candidateTodos = pendingTodos
                .filter { it.dueAt in overdueWindowStart..due24hEnd }
                .sortedBy { it.dueAt }
            for (todo in candidateTodos) {
                val (milestone, label) = when {
                    todo.dueAt < nowMs -> "overdue" to "⚠️ 待辦已逾期：${todo.title}"
                    todo.dueAt <= soonEnd -> "due${leadMinutes}m" to "⏰ ${leadMinutes} 分鐘內到期待辦：${todo.title}"
                    else -> "due24h" to "📌 24 小時內待辦：${todo.title}"
                }
                val key = "todo:${todo.id}:$dayKey:$milestone"
                if (key !in deliveredMilestones) {
                    highPriorityParts.add(label)
                    newMilestoneKeys.add(key)
                    break // 聚焦最緊急的一筆未送達里程碑，避免單卡過長
                }
            }
        }

        // 3. 今日全天事件：達使用者設定之提醒時刻後，每筆全天事件當日僅首次偵測投遞一次
        val allDayRemindTime = BriefingScheduler.parseHHmm(
            PhantomPrefs.getProactiveAllDayRemindTime(appCtx),
            LocalTime.of(9, 0)
        )
        val nowLocalTime = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime()
        val todayAllDay = calEvents.filter { it.allDay && it.beginMs < tomorrowStartMs && it.endMs > dayStartMs }
        if (todayAllDay.isNotEmpty() &&
            !nowLocalTime.isBefore(allDayRemindTime) &&
            PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE)
        ) {
            val unseenAllDay = todayAllDay.filter { ev ->
                "allDay:${ev.eventId}:$dayKey" !in deliveredMilestones
            }
            if (unseenAllDay.isNotEmpty()) {
                val titles = unseenAllDay.take(3).joinToString("、") { it.title }
                highPriorityParts.add("📅 今日全天事件：$titles")
                unseenAllDay.forEach { ev ->
                    newMilestoneKeys.add("allDay:${ev.eventId}:$dayKey")
                }
            }
        }

        val hasHigh = highPriorityParts.isNotEmpty()
        if (hasHigh) {
            PhantomPrefs.setProactiveDirty(appCtx, true)
        }
        MonitorCheckResult(
            fingerprintChanged = changed,
            hasHighPriority = hasHigh,
            highPrioritySummary = highPriorityParts.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            pendingMilestoneKeys = newMilestoneKeys
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
        val cap = PhantomPrefs.getBriefingDailyCap(appCtx)
        if (!force && PhantomPrefs.getBriefingDailySentCount(appCtx, dayKey) >= cap) {
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
        val dayStartMs = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val tomorrowStartMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        if (isMorning) {
            // #424 早報段落 1：今日本機日曆行程（含衝突檢查後提醒，未授權／無行程誠實顯示「無資料」）
            if (PhantomPrefs.BRIEFING_CONTENT_SCHEDULE in prefsSet) {
                if (CalendarBridge.isCalendarEnabledForAi(appCtx)) {
                    val calSummary = runCatching {
                        CalendarBridge.queryFreeSlotsSummary(appCtx, days = 1, dayFilter = "today")
                    }.getOrDefault("").trim()
                    val timedToday = runCatching {
                        CalendarBridge.queryInstances(appCtx, dayStartMs, tomorrowStartMs)
                            .filter { !it.allDay }
                            .sortedBy { it.beginMs }
                    }.getOrDefault(emptyList())
                    val conflictingPairs = timedToday.zipWithNext().filter { (a, b) -> b.beginMs < a.endMs }
                    val conflictLine = if (conflictingPairs.isNotEmpty()) {
                        val titles = conflictingPairs.take(2).joinToString("；") { (a, b) -> "${a.title} 與 ${b.title}" }
                        "\n⚠️ 今日行程撞期提醒：$titles"
                    } else ""
                    facts.add(
                        if (calSummary.isNotBlank()) "📅 今日行程：$calSummary$conflictLine"
                        else "📅 今日行程：無資料"
                    )
                } else {
                    facts.add("📅 今日行程：無資料（本機日曆尚未授權或未勾選）")
                }
            }
            // #424 早報段落 2：今日待辦
            if (PhantomPrefs.BRIEFING_CONTENT_TODO in prefsSet) {
                val todos = runCatching { db.todoDao().pendingTodos() }.getOrDefault(emptyList())
                val overdue = todos.count { it.dueAt < nowMs }
                if (todos.isNotEmpty()) {
                    val topTitles = todos.take(4).joinToString("、") { it.title.trim() }
                    facts.add("✅ 今日待辦：未完成 ${todos.size} 筆（逾期 $overdue 筆）— $topTitles")
                } else {
                    facts.add("✅ 今日待辦：無資料")
                }
            }
        } else {
            // #424 晚報段落 1：今日回顧一句（純本機統計，零編造）
            val todayStats = runCatching { db.messageDao().statsOverview(dayStartMs) }.getOrNull()
            val doneTodayCount = runCatching {
                db.todoDao().all().count { it.isDone && it.completedAt != null && it.completedAt in dayStartMs until tomorrowStartMs }
            }.getOrDefault(0)
            val recapText = if (todayStats != null) {
                "今日共收錄 ${todayStats.today} 則通知（未讀 ${todayStats.unread} 則、高風險 ${todayStats.scam} 則），完成 $doneTodayCount 項待辦。"
            } else {
                "今日完成 $doneTodayCount 項待辦。"
            }
            facts.add("📝 今日回顧：$recapText")

            // #424 晚報段落 2：明日行程預覽（逐字引自 CalendarBridge，缺資料寫「無資料」）
            if (PhantomPrefs.BRIEFING_CONTENT_SCHEDULE in prefsSet) {
                if (CalendarBridge.isCalendarEnabledForAi(appCtx)) {
                    val tomorrowSummary = runCatching {
                        CalendarBridge.queryFreeSlotsSummary(appCtx, days = 1, dayFilter = "tomorrow")
                    }.getOrDefault("").trim()
                    facts.add(
                        if (tomorrowSummary.isNotBlank()) "📅 明日行程預覽：$tomorrowSummary"
                        else "📅 明日行程預覽：無資料"
                    )
                } else {
                    facts.add("📅 明日行程預覽：無資料（本機日曆尚未授權或未勾選）")
                }
            }

            // #424 晚報段落 3：逾期未辦
            if (PhantomPrefs.BRIEFING_CONTENT_TODO in prefsSet) {
                val todos = runCatching { db.todoDao().pendingTodos() }.getOrDefault(emptyList())
                val overdueTodos = todos.filter { it.dueAt < nowMs }
                if (overdueTodos.isNotEmpty()) {
                    val overdueTitles = overdueTodos.take(4).joinToString("、") { it.title.trim() }
                    facts.add("⏰ 逾期未辦（${overdueTodos.size} 筆）：$overdueTitles")
                } else {
                    facts.add("⏰ 逾期未辦：無資料")
                }
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

        // #381 / #424 Health Connect 生理數據整合（晨間簡報健康快照：昨夜睡眠／昨日步數，依授權；缺資料寫「無資料」）
        if (isMorning && PhantomPrefs.isHealthBriefingEnabled(appCtx)) {
            val hcPerm = runCatching {
                com.phantom.checker.util.HealthConnectBridge.getPermissionStatus(appCtx)
            }.getOrDefault(com.phantom.checker.util.HealthConnectBridge.HealthPermissionStatus())
            if (hcPerm.anyGranted) {
                val snapshot = runCatching {
                    com.phantom.checker.util.HealthConnectBridge.readMorningSnapshot(appCtx)
                }.getOrNull()
                if (snapshot != null) {
                    val sleepStr = when {
                        !hcPerm.sleep -> "未授權"
                        snapshot.lastNightSleepHours > 0f -> String.format(Locale.US, "%.1f 小時", snapshot.lastNightSleepHours)
                        else -> "無資料"
                    }
                    val stepsStr = when {
                        !hcPerm.steps -> "未授權"
                        snapshot.yesterdaySteps > 0L -> String.format(Locale.US, "%,d 步", snapshot.yesterdaySteps)
                        else -> "無資料"
                    }
                    facts.add("❤️ 健康快照：昨夜睡眠 $sleepStr、昨日步數 $stepsStr")
                    val hasEarlyBefore9Am = if (CalendarBridge.isCalendarEnabledForAi(appCtx)) {
                        val nineAm = today.atTime(LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
                        runCatching {
                            CalendarBridge.queryInstances(appCtx, dayStartMs, nineAm)
                                .any { !it.allDay && it.beginMs in dayStartMs until nineAm }
                        }.getOrDefault(false)
                    } else false
                    com.phantom.checker.util.HealthConnectBridge.evaluateLookupRule(snapshot, hasEarlyBefore9Am)
                        ?.let { ruleAdvice ->
                            facts.add("❤️ 生理節奏建議：$ruleAdvice")
                        }
                } else {
                    facts.add("❤️ 健康快照：無資料")
                }
            } else {
                facts.add("❤️ 健康快照：無資料（Health Connect 尚未授權）")
                if (PhantomPrefs.shouldShowHealthSoftInvite(appCtx, nowMs)) {
                    facts.add("💡 連接穿戴裝置可獲得睡眠建議（可至權限授予中心開啟 Health Connect）")
                    PhantomPrefs.markHealthSoftInviteShown(appCtx, nowMs)
                }
            }
        }

        // #390 外部情報：優先共用主動情報中樞（planner＋query/12h快取）；未啟用時保留既有 BYOK 單發查詢 fallback
        var externalIntel: String? = null
        if (!tokenSaver) {
            if (PhantomPrefs.isProactiveIntelEnabled(appCtx)) {
                externalIntel = DigestComposer.getBriefingIntelSummary(appCtx)
            } else if (PhantomPrefs.isProactiveTopicEnabled(appCtx, PhantomPrefs.PROACTIVE_TOPIC_EXTERNAL_SEARCH) &&
                EncryptedByokStore.isExternalSearchConfigured(appCtx)
            ) {
                val queryTerms = (interestKeywords + listOfNotNull(regionKeyword.takeIf { it.isNotBlank() })).joinToString(" ")
                if (queryTerms.isNotBlank()) {
                    externalIntel = AiClient.queryExternalSearchEndpoint(appCtx, "今日 $queryTerms 重要焦點")
                }
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
                systemPrompt = "你是「$assistantName」，請將以下本機整理的事實潤飾為簡潔溫暖的繁體中文簡報（≤220字，保留所有關鍵數字與項目，不可編造，且務必以完整句號收尾）。",
                userPrompt = templateBody,
                maxTokens = 480
            )
            if (!polished.isNullOrBlank()) "**$headerTitle**\n$polished" else templateBody
        }

        val briefingMilestone = if (force) {
            emptyList()
        } else {
            listOf("briefing:${if (isMorning) "morning" else "evening"}:$dayKey")
        }
        val notifId = if (isMorning) NotifIds.BRIEFING_MORNING else NotifIds.BRIEFING_EVENING
        val msgId = deliverProactiveMessage(
            ctx = appCtx,
            title = headerTitle,
            body = finalBody,
            notifId = notifId,
            sendNotif = true,
            respectDailyCap = false, // 簡報走獨立 getBriefingDailyCap 額度，已於上方檢查
            nowMs = nowMs,
            milestoneKeys = briefingMilestone,
            bypassActiveChatGuard = force
        ) ?: return@withContext false

        PhantomPrefs.setLastBriefingDay(appCtx, isMorning, dayKey)
        PhantomPrefs.setProactiveDirty(appCtx, false)
        PhantomPrefs.incrementBriefingDailySentCount(appCtx, dayKey)
        PhantomPrefs.saveLastBriefingMeta(appCtx, finalBody, nowMs, msgId)
        if (PhantomPrefs.isDiscordForwardEnabled(appCtx) && EncryptedByokStore.isDiscordConfigured(appCtx)) {
            runCatching {
                CloudExtensionsClient.sendDiscordMessage(appCtx, finalBody)
            }
        }
        true
    }

    /**
     * #372 統一主動提醒去重閘門：
     * 1) 若提供 milestoneKeys 且全部已於今日投遞過 → 擋下。
     * 2) 同 contentHash 於近 10 分鐘內已投遞 → 擋下（雙保險）。
     */
    private fun shouldAllowProactiveDelivery(
        appCtx: Context,
        contentForHash: String,
        milestoneKeys: List<String>,
        dayKey: String,
        nowMs: Long
    ): String? {
        if (milestoneKeys.isNotEmpty()) {
            val delivered = PhantomPrefs.getPrunedProactiveMilestones(appCtx, dayKey)
            if (milestoneKeys.all { it in delivered }) return null
        }
        val hash = sha256Hex(contentForHash.trim())
        if (PhantomPrefs.isProactiveContentHashRecent(appCtx, hash, nowMs)) {
            return null
        }
        return hash
    }

    /** 發送高優先級主動提醒（零 token 模板優先，統一經由 milestone + 10 分鐘內容去重閘門，遵守上限與勿擾） */
    suspend fun deliverProactiveAlert(
        ctx: Context,
        title: String,
        body: String,
        nowMs: Long = System.currentTimeMillis(),
        milestoneKeys: List<String> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val assistantName = PhantomPrefs.getAssistantName(appCtx)
        val fullText = "**🔔 $assistantName 主動提醒｜$title**\n$body"
        val msgId = deliverProactiveMessage(
            ctx = appCtx,
            title = "$assistantName：$title",
            body = fullText,
            notifId = NotifIds.PROACTIVE_PUSH,
            sendNotif = true,
            respectDailyCap = true,
            nowMs = nowMs,
            milestoneKeys = milestoneKeys,
            previewOverride = body
        )
        msgId != null
    }

    /**
     * #390 Phase C/E 主動情報摘要投遞：
     * - 沿用 deliverProactiveMessage 全套紀律（專屬主動訊息聊天室、10 分鐘雜湊去重、每日上限、勿擾仍寫聊天不響通知）。
     * - 通知直達 MainActivity（PendingIntent.getActivity 零 trampoline），附「不再關注」BroadcastReceiver 動作鍵。
     */
    suspend fun deliverProactiveIntelDigest(
        ctx: Context,
        title: String,
        body: String,
        topicIds: List<Long>,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val msgId = deliverProactiveMessage(
            ctx = appCtx,
            title = title,
            body = body,
            notifId = NotifIds.PROACTIVE_INTEL,
            sendNotif = false,
            respectDailyCap = true,
            nowMs = nowMs
        ) ?: return@withContext false

        if (!isInDndWindow(appCtx, nowMs) && areNotificationsEnabled(appCtx)) {
            val nm = appCtx.getSystemService(NotificationManager::class.java)
            val ch = ensureBriefingChannel(appCtx)
            if (nm != null && (ch == null || ch.importance != NotificationManager.IMPORTANCE_NONE)) {
                val openIntent = Intent(appCtx, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(DailySummaryPolicy.EXTRA_OPEN_TAB, BriefingScheduler.TAB_ASSISTANT)
                    putExtra(BriefingScheduler.EXTRA_BRIEFING_MSG_ID, msgId)
                }
                val contentPi = PendingIntent.getActivity(
                    appCtx,
                    NotifIds.PROACTIVE_INTEL,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val muteIntent = Intent(appCtx, WatchTopicMuteReceiver::class.java).apply {
                    action = WatchTopicMuteReceiver.ACTION_MUTE_WATCH_TOPICS
                    putExtra(WatchTopicMuteReceiver.EXTRA_TOPIC_IDS, topicIds.toLongArray())
                    putExtra(WatchTopicMuteReceiver.EXTRA_NOTIF_ID, NotifIds.PROACTIVE_INTEL)
                }
                val mutePi = PendingIntent.getBroadcast(
                    appCtx,
                    NotifIds.PROACTIVE_INTEL + 1000,
                    muteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val preview = body.replace("**", "").replace("\n", " ").take(110)
                val notif = NotificationCompat.Builder(appCtx, CHANNEL_ASSISTANT_BRIEFING)
                    .setSmallIcon(R.drawable.ic_stat_phantom)
                    .setContentTitle(title)
                    .setContentText(preview)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(preview))
                    .setAutoCancel(true)
                    .setContentIntent(contentPi)
                    .addAction(0, com.phantom.checker.PhantomApplication.str(R.string.notif_proactive_s_a4b3d1), mutePi)
                    .build()
                try {
                    nm.notify(NotifIds.PROACTIVE_INTEL, notif)
                } catch (_: SecurityException) {
                }
            }
        }
        true
    }

    const val PROACTIVE_SESSION_TITLE = "AI 助手簡報與主動提醒"

    // #424 回饋驅動節流常數與對話中不打斷靜默窗
    const val FEEDBACK_LOOKBACK_DAYS = 7L
    const val FEEDBACK_DOWN_THROTTLE_MIN_COUNT = 2
    const val FEEDBACK_DOWN_WINDOW_MULTIPLIER = 1.5
    const val FEEDBACK_UP_FLOOR_MULTIPLIER = 0.75
    const val ACTIVE_CHAT_QUIET_WINDOW_MS = 15L * 60_000L

    private fun messageMatchesProactiveTopic(content: String, topic: String): Boolean = when (topic) {
        PhantomPrefs.PROACTIVE_TOPIC_SCHEDULE ->
            content.contains("行程即將開始") || content.contains("今日全天事件") || content.contains("📅")
        PhantomPrefs.PROACTIVE_TOPIC_CONFLICT ->
            content.contains("偵測到撞期") || content.contains("行程撞期")
        PhantomPrefs.PROACTIVE_TOPIC_TODO ->
            content.contains("待辦")
        PhantomPrefs.PROACTIVE_TOPIC_INTEREST, PhantomPrefs.PROACTIVE_TOPIC_REGION ->
            content.contains("情報") || content.contains("焦點")
        else -> false
    }

    /**
     * #424 依近 7 日該主題 👍/👎 回饋計算等待窗倍率：
     * - 👎 ≥ 2：倍率 1.5（拉長等待窗降低打擾）
     * - 👍 ≥ 2 且 👎 == 0：倍率 0.75（縮短至 floor 上限）
     * - 從未給回饋：1.0（與 v4.0.0 預設行為一致）
     */
    internal suspend fun computeTopicCooldownMultiplier(
        appCtx: Context,
        topic: String,
        nowMs: Long = System.currentTimeMillis()
    ): Double = withContext(Dispatchers.IO) {
        val db = AppDatabaseHolder.db(appCtx)
        val proactiveSession = db.chatDao().allSessions()
            .firstOrNull { it.title == PROACTIVE_SESSION_TITLE }
            ?: return@withContext 1.0
        val cutoffMs = nowMs - FEEDBACK_LOOKBACK_DAYS * 24L * 60L * 60L * 1000L
        val recentMsgs = db.chatDao().messagesForSession(proactiveSession.id)
            .filter { it.createdAt >= cutoffMs && messageMatchesProactiveTopic(it.content, topic) }
        if (recentMsgs.isEmpty()) return@withContext 1.0
        var upCount = 0
        var downCount = 0
        recentMsgs.forEach { msg ->
            when (PhantomPrefs.getProactiveMessageFeedback(appCtx, msg.id)) {
                "up", "helpful" -> upCount++
                "down", "noisy" -> downCount++
            }
        }
        when {
            downCount >= FEEDBACK_DOWN_THROTTLE_MIN_COUNT -> FEEDBACK_DOWN_WINDOW_MULTIPLIER
            upCount >= 2 && downCount == 0 -> FEEDBACK_UP_FLOOR_MULTIPLIER
            else -> 1.0
        }
    }

    /** #424 判斷指定主題是否因近期 👎≥2 回饋尚處於拉長後的等待冷卻窗內 */
    internal suspend fun isTopicThrottledByFeedback(
        appCtx: Context,
        topic: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val multiplier = computeTopicCooldownMultiplier(appCtx, topic, nowMs)
        if (multiplier <= 1.0) return@withContext false
        val db = AppDatabaseHolder.db(appCtx)
        val proactiveSession = db.chatDao().allSessions()
            .firstOrNull { it.title == PROACTIVE_SESSION_TITLE }
            ?: return@withContext false
        val lastTopicMsg = db.chatDao().messagesForSession(proactiveSession.id)
            .lastOrNull { messageMatchesProactiveTopic(it.content, topic) }
            ?: return@withContext false
        val baseWindowMin = PhantomPrefs.getProactiveDedupWindowMinutes(appCtx).coerceAtLeast(10)
        val effectiveWaitMs = (baseWindowMin * multiplier * 60_000.0).toLong()
        (nowMs - lastTopicMsg.createdAt) in 0L until effectiveWaitMs
    }

    /** #424 判斷使用者是否正於近 15 分鐘內與 AI 對話（一般聊天室最後一筆 user 訊息在 15 分鐘內） */
    internal suspend fun isUserActivelyChatting(
        db: com.phantom.checker.data.AppDatabase,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val recentNormalSessions = db.chatDao().allSessions()
            .filter { it.title != PROACTIVE_SESSION_TITLE }
            .take(5)
        for (session in recentNormalSessions) {
            val lastUserTs = db.chatDao().messagesForSession(session.id)
                .lastOrNull { it.role == ChatMessageEntity.ROLE_USER }
                ?.createdAt ?: continue
            if (nowMs - lastUserTs in 0L..ACTIVE_CHAT_QUIET_WINDOW_MS) {
                return true
            }
        }
        return false
    }

    /** 取得或建立專屬「主動訊息」聊天室（不再誤寫入一般對話第一筆 session） */
    suspend fun getOrCreateProactiveSessionId(
        db: com.phantom.checker.data.AppDatabase,
        nowMs: Long = System.currentTimeMillis()
    ): Long {
        val sessions = db.chatDao().allSessions()
        return sessions.firstOrNull { it.title == PROACTIVE_SESSION_TITLE }?.id
            ?: db.chatDao().insertSession(
                ChatSessionEntity(title = PROACTIVE_SESSION_TITLE, createdAt = nowMs)
            )
    }

    /** 統一投遞主動訊息（寫入專屬主動訊息聊天室＋可選通知；統一經過里程碑＋10分鐘內容雜湊去重閘門、每日上限、15 分鐘對話不打斷與獨立勿擾） */
    suspend fun deliverProactiveMessage(
        ctx: Context,
        title: String,
        body: String,
        notifId: Int = NotifIds.PROACTIVE_PUSH,
        sendNotif: Boolean = true,
        respectDailyCap: Boolean = true,
        nowMs: Long = System.currentTimeMillis(),
        milestoneKeys: List<String> = emptyList(),
        previewOverride: String? = null,
        bypassActiveChatGuard: Boolean = false
    ): Long? = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val dayKey = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        if (respectDailyCap &&
            PhantomPrefs.getProactiveDailySentCount(appCtx, dayKey) >= PhantomPrefs.getProactiveDailyCap(appCtx)
        ) {
            return@withContext null
        }
        val db = AppDatabaseHolder.db(appCtx)
        // #424 使用者 15 分鐘內有對話不投遞主動訊息，標記 dirty 併入下一輪
        if (!bypassActiveChatGuard && isUserActivelyChatting(db, nowMs)) {
            PhantomPrefs.setProactiveDirty(appCtx, true)
            return@withContext null
        }
        val contentHash = shouldAllowProactiveDelivery(appCtx, body, milestoneKeys, dayKey, nowMs)
            ?: return@withContext null
        val msgId = writeAssistantMessage(db, body, nowMs)
        PhantomPrefs.incrementProactiveDailySentCount(appCtx, dayKey)
        PhantomPrefs.recordProactiveContentHash(appCtx, contentHash, nowMs)
        if (milestoneKeys.isNotEmpty()) {
            PhantomPrefs.markProactiveMilestonesDelivered(appCtx, milestoneKeys, dayKey)
        }
        if (sendNotif && !isInDndWindow(appCtx, nowMs)) {
            postBriefingNotification(
                ctx = appCtx,
                notifId = notifId,
                title = title,
                preview = (previewOverride ?: body).replace("**", "").replace("\n", " ").take(90),
                messageId = msgId
            )
        }
        msgId
    }

    /** #366 跨分頁事件系統訊息（零 AI 呼叫，寫入當前或主動對話 session，標記系統事件 provenance） */
    suspend fun appendSystemEventToCurrentSession(
        ctx: Context,
        summary: String,
        targetSessionId: Long? = null,
        nowMs: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val db = AppDatabaseHolder.db(ctx.applicationContext)
        val sid = targetSessionId
            ?: db.chatDao().allSessions().firstOrNull { it.title != PROACTIVE_SESSION_TITLE }?.id
            ?: getOrCreateProactiveSessionId(db, nowMs)
        db.chatDao().insertMessage(
            ChatMessageEntity(
                sessionId = sid,
                role = ChatMessageEntity.ROLE_ASSISTANT,
                content = "📌 [系統事件] ${summary.trim()}",
                createdAt = nowMs
            )
        )
    }

    private suspend fun writeAssistantMessage(
        db: com.phantom.checker.data.AppDatabase,
        content: String,
        nowMs: Long
    ): Long {
        val sessionId = getOrCreateProactiveSessionId(db, nowMs)
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
        val appCtx = ctx.applicationContext
        return StatsForegroundService.hasPostNotificationsPermission(appCtx) &&
            androidx.core.app.NotificationManagerCompat.from(appCtx).areNotificationsEnabled()
    }

    fun ensureBriefingChannel(ctx: Context): NotificationChannel? {
        val nm = ctx.applicationContext.getSystemService(NotificationManager::class.java) ?: return null
        val channel = NotificationChannel(
            CHANNEL_ASSISTANT_BRIEFING,
            com.phantom.checker.PhantomApplication.str(R.string.notif_proactive_s_04d38e),
            NotificationManager.IMPORTANCE_DEFAULT
        )
        nm.createNotificationChannel(channel)
        return nm.getNotificationChannel(CHANNEL_ASSISTANT_BRIEFING)
    }

    /** #404 偵測 assistant_briefing 通知頻道是否被使用者手動停用（importance == IMPORTANCE_NONE） */
    fun isBriefingChannelBlocked(ctx: Context): Boolean {
        val ch = ensureBriefingChannel(ctx) ?: return false
        return ch.importance == NotificationManager.IMPORTANCE_NONE
    }

    fun openBriefingChannelSettings(ctx: Context) {
        ensureBriefingChannel(ctx)
        val chIntent = Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, CHANNEL_ASSISTANT_BRIEFING)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching {
            ctx.startActivity(chIntent)
        }.onFailure {
            openAppNotificationSettings(ctx)
        }
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
        val ch = ensureBriefingChannel(ctx)
        if (ch != null && ch.importance == NotificationManager.IMPORTANCE_NONE) return
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

/** #337 / #372 15 分鐘週期零 token 變動偵測與里程碑提醒 Worker（無實質變動零 AI 呼叫） */
class ProactiveMonitorWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        return try {
            val check = ProactiveEngine.evaluateLocalFingerprintAndPriority(applicationContext)
            if (check.hasHighPriority && !check.highPrioritySummary.isNullOrBlank() && check.pendingMilestoneKeys.isNotEmpty()) {
                ProactiveEngine.deliverProactiveAlert(
                    ctx = applicationContext,
                    title = com.phantom.checker.PhantomApplication.str(R.string.notif_proactive_s_bae241),
                    body = check.highPrioritySummary,
                    milestoneKeys = check.pendingMilestoneKeys
                )
            }
            com.phantom.checker.util.GoalEngine.evaluateMissedGoalPushes(applicationContext)
            HealthObservationPolicy.evaluateAndDeliverIfNeeded(applicationContext)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("PhantomProactive", "Monitor worker failed: ${e.javaClass.simpleName}")
            Result.success()
        }
    }
}
