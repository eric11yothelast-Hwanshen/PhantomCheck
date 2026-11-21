package com.phantom.checker.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * #364 確定性本地資料直連（LocalDataConnector）：
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
        "日曆", "行事曆", "行程", "空檔", "日程",
        "今天要做", "今天有什麼", "今天有甚麼", "今日行程",
        "明天要做", "明天有什麼", "明天有甚麼", "明日行程",
        "這週有什麼", "這週行程", "本週行程", "下週行程",
        "calendar", "schedule"
    )

    private val USAGE_KEYWORDS = listOf(
        "使用情況", "使用時間", "使用記錄", "使用紀錄",
        "開了幾次", "啟動次數", "最常用", "螢幕時間",
        "app使用", "app 使用", "usage", "screen time"
    )

    suspend fun resolveLocalContext(ctx: Context, query: String): LocalContextInjection =
        withContext(Dispatchers.IO) {
            val lower = query.lowercase(Locale.ROOT)
            val hitsCalendar = CALENDAR_KEYWORDS.any { lower.contains(it) }
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
            return "本機日曆尚未授權或尚未勾選任何要查詢的日曆，無法讀取本機行程。請誠實告知使用者查不到，並建議至「權限中心／日曆設定」開啟權限與勾選日曆，嚴禁編造任何行程。"
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
            return "本機日曆未來 $days 天查無行程（已直接查詢勾選的本機日曆，結果為空；請如實告知使用者這段期間沒有排定行程，嚴禁編造）。"
        }
        val fmt = SimpleDateFormat("MM/dd (E) HH:mm", Locale.TAIWAN)
        val dayFmt = SimpleDateFormat("MM/dd (E)", Locale.TAIWAN)
        return buildString {
            appendLine("本機日曆未來 $days 天真實行程（共 ${events.size} 筆，僅限以下項目，嚴禁補充或編造其他行程）：")
            events.take(30).forEach { ev ->
                val timeDesc = if (ev.allDay) {
                    "${dayFmt.format(Date(ev.beginMs))} 全天"
                } else {
                    "${fmt.format(Date(ev.beginMs))}–${SimpleDateFormat("HH:mm", Locale.TAIWAN).format(Date(ev.endMs))}"
                }
                val loc = ev.location?.takeIf { it.isNotBlank() }?.let { " @ $it" }.orEmpty()
                appendLine("- $timeDesc：${ev.title}$loc")
            }
        }.trim()
    }
}
