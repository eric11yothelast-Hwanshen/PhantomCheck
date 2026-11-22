package com.phantom.checker.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

/**
 * #364 / #387 確定性本地資料直連（LocalDataConnector）：
 * 送 AI 前先做零 token 本地意圖偵測，直接從本機日曆 / UsageStatsManager 取得真實資料並以
 * <calendar_context> / <usage_context> 注入系統提示，防止模型編造行程或使用數字。
 */
object LocalDataConnector {

    data class LocalContextInjection(
        val calendarContext: String? = null,
        val usageContext: String? = null,
        val needsUsagePermissionPrompt: Boolean = false
    )

    private val CALENDAR_KEYWORDS = listOf(
        "日曆", "行事曆", "行程", "空檔", "日程", "課表",
        "今天要做", "今天有什麼", "今天有甚麼", "今日行程",
        "明天要做", "明天有什麼", "明天有甚麼", "明日行程",
        "這週有什麼", "這週行程", "本週行程", "下週行程",
        "calendar", "schedule"
    )

    /** #387 排程／課表語意詞：需搭配問句標記才觸發，避免一般閒聊誤注入 */
    private val SCHEDULE_SEMANTIC_KEYWORDS = listOf(
        "課", "堂", "節", "上完後", "上完", "接下來", "下一個",
        "下一堂", "下一節", "下堂", "下節", "之後", "待會",
        "還有什麼", "還有甚麼", "幾點", "教室",
        "禮拜一", "禮拜二", "禮拜三", "禮拜四", "禮拜五", "禮拜六", "禮拜日", "禮拜天",
        "星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日", "星期天",
        "週一", "週二", "週三", "週四", "週五", "週六", "週日"
    )

    private val SCHEDULE_QUESTION_MARKERS = listOf(
        "?", "？", "什麼", "甚麼", "啥", "哪", "幾",
        "有沒有", "嗎", "呢", "怎麼", "是不是",
        "幫我查", "幫我看", "查一下", "看一下", "列出", "告訴我"
    )

    private val USAGE_KEYWORDS = listOf(
        "使用情況", "使用時間", "使用記錄", "使用紀錄",
        "開了幾次", "啟動次數", "最常用", "螢幕時間",
        "app使用", "app 使用", "usage", "screen time"
    )

    /** #387 判斷提問是否涉及日曆／課表／排程（直接關鍵字 OR 排程語意＋問句） */
    internal fun isCalendarQuery(query: String): Boolean {
        val lower = query.lowercase(Locale.ROOT)
        if (CALENDAR_KEYWORDS.any { lower.contains(it) }) return true
        val hasScheduleSemantic = SCHEDULE_SEMANTIC_KEYWORDS.any { lower.contains(it) }
        val hasQuestion = SCHEDULE_QUESTION_MARKERS.any { lower.contains(it) }
        return hasScheduleSemantic && hasQuestion
    }

    suspend fun resolveLocalContext(ctx: Context, query: String): LocalContextInjection =
        withContext(Dispatchers.IO) {
            val lower = query.lowercase(Locale.ROOT)
            val hitsCalendar = isCalendarQuery(query)
            val hitsUsage = USAGE_KEYWORDS.any { lower.contains(it) }

            val calendarCtx = if (hitsCalendar) buildCalendarContext(ctx, lower) else null
            var needsUsagePerm = false
            val usageCtx = if (hitsUsage) {
                val days = when {
                    lower.contains("這週") || lower.contains("本週") || lower.contains("一週") || lower.contains("7天") || lower.contains("七天") -> 7
                    lower.contains("三天") || lower.contains("3天") -> 3
                    else -> 1
                }
                val summary = UsageStatsBridge.queryUsageSummary(ctx, days = days, top = 8)
                if (!summary.granted) {
                    needsUsagePerm = true
                    "App 使用情況存取權限尚未授權，無法查詢真實 App 使用次數與前景時間。請誠實告知使用者「尚未授權，無法查詢」，並建議點擊前往「權限中心」開啟「應用使用情況存取」，嚴禁估算或編造任何數字。"
                } else {
                    summary.promptContext
                }
            } else {
                null
            }

            LocalContextInjection(
                calendarContext = calendarCtx,
                usageContext = usageCtx,
                needsUsagePermissionPrompt = needsUsagePerm
            )
        }

    private suspend fun buildCalendarContext(ctx: Context, lowerQuery: String): String {
        if (!CalendarBridge.isCalendarEnabledForAi(ctx)) {
            return "本機日曆尚未授權或尚未勾選任何要查詢的日曆，無法讀取本機行程。請誠實告知使用者「查不到本機日曆中相關資料」，並建議至「權限中心／本機日曆設定」開啟權限與勾選日曆，嚴禁編造任何行程。"
        }
        val days = when {
            lowerQuery.contains("今天") || lowerQuery.contains("今日") -> 1
            lowerQuery.contains("明天") || lowerQuery.contains("明日") -> 2
            lowerQuery.contains("下週") || lowerQuery.contains("兩週") -> 14
            else -> 7
        }
        val startCal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val beginMs = startCal.timeInMillis
        val endMs = beginMs + days.toLong() * 24L * 60L * 60L * 1000L
        val events = CalendarBridge.queryInstances(ctx, beginMs, endMs)
        if (events.isEmpty()) {
            return "本機日曆未來 $days 天查無行程（已直接查詢勾選的本機日曆，結果為空；只能回答「查不到本機日曆中相關資料」並引導至權限中心勾選日曆，嚴禁編造任何行程）。"
        }
        val zone = ZoneId.systemDefault()
        val dtFmt = DateTimeFormatter.ofPattern("MM/dd (E) HH:mm", Locale.TAIWAN)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
        val dayFmt = DateTimeFormatter.ofPattern("MM/dd (E)", Locale.TAIWAN)
        return buildString {
            appendLine("本機日曆未來 $days 天真實行程（共 ${events.size} 筆，僅限以下項目，嚴禁補充或編造其他行程）：")
            events.take(30).forEach { ev ->
                val timeDesc = if (ev.allDay) {
                    val utcDate = Instant.ofEpochMilli(ev.beginMs).atZone(ZoneOffset.UTC).toLocalDate()
                    "${utcDate.format(dayFmt)} 全天"
                } else {
                    val s = Instant.ofEpochMilli(ev.beginMs).atZone(zone).format(dtFmt)
                    val e = Instant.ofEpochMilli(ev.endMs).atZone(zone).format(timeFmt)
                    "$s–$e"
                }
                val loc = ev.location?.takeIf { it.isNotBlank() }?.let { " @ $it" }.orEmpty()
                appendLine("- $timeDesc：${ev.title}$loc")
            }
        }.trim()
    }
}
