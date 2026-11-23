package com.phantom.checker.service

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.phantom.checker.data.TaskTemplateEntity
import com.phantom.checker.network.AiClient
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * #368 定時任務模板引擎：
 * - 聚合段（100% 本地 SQL/規則，零 AI token、永不誤判）與潤飾段（AI 選配，掛省 token 模式）嚴格分離
 * - 支援三步嚮導的「所見即所得預覽」直接呼叫 runAggregation()
 * - 推送統一格式：標題＋3 條重點＋寫入專屬主動訊息聊天室（含 deep link）
 */
object TaskTemplateEngine {

    data class AggregationResult(
        val queryKey: String,
        val title: String,
        val bullets: List<String>,
        val fullText: String
    )

    fun queryKeyLabel(queryKey: String): String = when (queryKey) {
        TaskTemplateEntity.QUERY_MESSAGES_DIGEST -> "今日訊息彙整"
        TaskTemplateEntity.QUERY_NOTES_DIGEST -> "近 7 日記事彙整"
        TaskTemplateEntity.QUERY_TODOS_DIGEST -> "待辦事項盤點"
        TaskTemplateEntity.QUERY_ANTIFRAUD_DIGEST -> "近 7 日防詐安全報告"
        else -> queryKey
    }

    /** 純本地零 token 聚合段（永不呼叫 AI） */
    suspend fun runAggregation(ctx: Context, queryKey: String): AggregationResult = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val db = AppDatabaseHolder.db(appCtx)
        val nowMs = System.currentTimeMillis()
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val weekAgo = nowMs - 7L * 24L * 60L * 60L * 1000L
        val timeFmt = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault())

        when (queryKey) {
            TaskTemplateEntity.QUERY_NOTES_DIGEST -> {
                val notes = runCatching { db.noteDao().all() }.getOrDefault(emptyList())
                val recent = notes.filter { it.createdAt >= weekAgo }
                val bullets = buildList {
                    add("記事庫共 ${notes.size} 筆，近 7 日新增 ${recent.size} 筆")
                    recent.take(2).forEach { n ->
                        add("📝 ${n.content.replace("\n", " ").trim()}（${timeFmt.format(Date(n.createdAt))}）")
                    }
                    if (recent.isEmpty()) add("近 7 日尚無新增記事")
                }.take(3)
                AggregationResult(
                    queryKey = queryKey,
                    title = "近 7 日記事彙整",
                    bullets = bullets,
                    fullText = formatUnifiedOutput("近 7 日記事彙整", bullets)
                )
            }
            TaskTemplateEntity.QUERY_TODOS_DIGEST -> {
                val pending = runCatching { db.todoDao().pendingTodos() }.getOrDefault(emptyList())
                val overdue = pending.count { it.dueAt < nowMs }
                val bullets = buildList {
                    add("尚有 ${pending.size} 項未完成待辦（逾期 $overdue 項）")
                    pending.sortedBy { it.dueAt }.take(2).forEach { t ->
                        add("📌 ${t.title.trim()}（到期 ${timeFmt.format(Date(t.dueAt))}）")
                    }
                    if (pending.isEmpty()) add("目前所有待辦皆已完成")
                }.take(3)
                AggregationResult(
                    queryKey = queryKey,
                    title = "待辦事項盤點",
                    bullets = bullets,
                    fullText = formatUnifiedOutput("待辦事項盤點", bullets)
                )
            }
            TaskTemplateEntity.QUERY_ANTIFRAUD_DIGEST -> {
                val recentMsgs = runCatching {
                    db.messageDao().paged(200, 0).filter { it.timestamp >= weekAgo }
                }.getOrDefault(emptyList())
                val scams = recentMsgs.filter { it.riskLabel == "scam" || it.riskScore >= 70 }
                val warnings = recentMsgs.filter { it.riskLabel == "warning" && it.riskScore < 70 }
                val bullets = buildList {
                    add("近 7 日攔截高風險詐騙 ${scams.size} 則、待確認警示 ${warnings.size} 則")
                    scams.take(2).forEach { m ->
                        add("🛡️ [${m.appLabel}] ${m.sender.ifBlank { "未知寄件者" }}（${m.riskScore} 分）")
                    }
                    if (scams.isEmpty()) add("近 7 日無高風險詐騙命中，狀態良好")
                }.take(3)
                AggregationResult(
                    queryKey = queryKey,
                    title = "近 7 日防詐安全報告",
                    bullets = bullets,
                    fullText = formatUnifiedOutput("近 7 日防詐安全報告", bullets)
                )
            }
            else -> {
                val stats = runCatching { db.messageDao().statsOverview(todayStart) }.getOrNull()
                val groups = runCatching { db.messageDao().groupByApp() }.getOrDefault(emptyList())
                val topApps = groups.sortedByDescending { it.cnt }.take(3)
                    .joinToString("、") { "${it.appLabel.ifBlank { it.packageName }}(${it.cnt})" }
                    .ifBlank { "尚無資料" }
                val bullets = listOf(
                    "今日收到 ${stats?.today ?: 0} 則訊息（全庫未讀 ${stats?.unread ?: 0} 則）",
                    "主要來源 App：$topApps",
                    "全庫高風險訊息：${stats?.scam ?: 0} 則"
                )
                AggregationResult(
                    queryKey = TaskTemplateEntity.QUERY_MESSAGES_DIGEST,
                    title = "今日訊息彙整",
                    bullets = bullets,
                    fullText = formatUnifiedOutput("今日訊息彙整", bullets)
                )
            }
        }
    }

    private fun formatUnifiedOutput(title: String, bullets: List<String>): String = buildString {
        appendLine("**📋 定時任務｜$title**")
        bullets.take(3).forEachIndexed { idx, line ->
            appendLine("${idx + 1}. $line")
        }
    }.trim()

    /** 執行模板（聚合段 → 選配 AI 潤飾段 → 投遞主動訊息聊天室＋可選通知） */
    suspend fun executeTemplate(ctx: Context, template: TaskTemplateEntity): Long? = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val agg = runAggregation(appCtx, template.queryKey)
        val usePolish = template.polishEnabled && !PhantomPrefs.isTokenSaverEnabled(appCtx)
        val finalBody = if (usePolish) {
            val assistantName = PhantomPrefs.getAssistantName(appCtx)
            val polished = AiClient.generateBriefingText(
                context = appCtx,
                systemPrompt = "你是「$assistantName」，請將以下本地彙整的 3 點重點潤飾為簡短溫暖的繁體中文定時報告（≤160字，保留所有數字與項目，不可編造）。",
                userPrompt = agg.fullText,
                maxTokens = 240
            )
            if (!polished.isNullOrBlank()) "**📋 ${template.name}**\n$polished" else agg.fullText
        } else {
            agg.fullText.replace("定時任務｜${agg.title}", template.name)
        }

        val sendNotif = template.deliverTo == TaskTemplateEntity.DELIVER_PROACTIVE_AND_NOTIF
        val msgId = ProactiveEngine.deliverProactiveMessage(
            ctx = appCtx,
            title = "📋 ${template.name}",
            body = finalBody,
            notifId = NotifIds.taskTemplate(template.id),
            sendNotif = sendNotif,
            respectDailyCap = true
        )
        runCatching {
            AppDatabaseHolder.db(appCtx).taskTemplateDao().markLastRun(template.id, System.currentTimeMillis())
        }
        msgId
    }
}

object TaskTemplateScheduler {
    private fun workName(templateId: Long) = "phantom_task_template_$templateId"
    const val KEY_TEMPLATE_ID = "template_id"

    fun scheduleOrUpdate(ctx: Context, template: TaskTemplateEntity) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        if (!template.isEnabled) {
            wm.cancelUniqueWork(workName(template.id))
            return
        }
        val periodMinutes: Long = when (template.triggerType) {
            TaskTemplateEntity.TRIGGER_WEEKLY -> 7L * 24L * 60L
            TaskTemplateEntity.TRIGGER_INTERVAL -> template.intervalMinutes.coerceIn(15, 1440).toLong()
            else -> 24L * 60L // daily
        }
        val input = Data.Builder().putLong(KEY_TEMPLATE_ID, template.id).build()
        val req = PeriodicWorkRequestBuilder<TaskTemplateWorker>(periodMinutes, TimeUnit.MINUTES)
            .setInputData(input)
            .build()
        wm.enqueueUniquePeriodicWork(
            workName(template.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            req
        )
    }

    fun cancel(ctx: Context, templateId: Long) {
        WorkManager.getInstance(ctx.applicationContext).cancelUniqueWork(workName(templateId))
    }

    suspend fun rescheduleAllEnabled(ctx: Context) = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val all = runCatching { AppDatabaseHolder.db(appCtx).taskTemplateDao().all() }.getOrDefault(emptyList())
        all.forEach { t -> scheduleOrUpdate(appCtx, t) }
    }
}

class TaskTemplateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val id = inputData.getLong(TaskTemplateScheduler.KEY_TEMPLATE_ID, -1L)
            if (id <= 0L) return@withContext Result.success()
            val template = AppDatabaseHolder.db(applicationContext).taskTemplateDao().byId(id)
                ?: return@withContext Result.success()
            if (!template.isEnabled) return@withContext Result.success()
            TaskTemplateEngine.executeTemplate(applicationContext, template)
            Result.success()
        } catch (e: Exception) {
            android.util.Log.w("PhantomTaskTpl", "TaskTemplateWorker failed: ${e.javaClass.simpleName}")
            Result.success()
        }
    }
}
