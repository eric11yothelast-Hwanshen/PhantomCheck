package com.phantom.checker.service

import android.content.Context
import com.phantom.checker.util.HealthConnectBridge
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * #403 主動健康觀察策略（HealthObservationPolicy）：
 * 1. 純本機查表規則驅動，零 AI 呼叫，原始生理時間序列永不出本機。
 * 2. 規則：
 *    - `sleep_2nights_under_6h`：連續 2 晚睡眠 < 6h（且 > 0h）
 *    - `steps_3days_under_2000`：連續 3 日（最近 3 個完整日，不含今日進行中）步數 < 2000（且 > 0）
 *    - `hr_elevated_12bpm`：昨日平均心率較近 7 日基準高 ≥ 12 bpm
 * 3. 節流與投遞紀律：
 *    - 僅於「允許 AI 讀取健康摘要」（`PhantomPrefs.isHealthAiQueryEnabled`）開啟且 Health Connect 已授權時執行。
 *    - 里程碑 key 格式：`health:{ruleId}:{dayKey}`（同規則當日僅一次）。
 *    - 每條規則每週（滾動 7 日）至多投遞 2 次。
 *    - 投遞統一走 `ProactiveEngine.deliverProactiveMessage`（notifId = `NotifIds.HEALTH_PROACTIVE`），完整遵守去重、每日上限與獨立勿擾。
 */
object HealthObservationPolicy {

    const val RULE_SLEEP_2NIGHTS_UNDER_6H = "sleep_2nights_under_6h"
    const val RULE_STEPS_3DAYS_UNDER_2000 = "steps_3days_under_2000"
    const val RULE_HR_ELEVATED_12BPM = "hr_elevated_12bpm"

    private const val MAX_WEEKLY_PER_RULE = 2

    data class HealthRuleMatch(
        val ruleId: String,
        val title: String,
        val message: String
    )

    /**
     * 純函數查表評估（方便測試與背景呼叫共用）：
     * @param trend 由舊到新排序的近 7 日 `DailyHealthPoint`（最後一筆 `trend.last()` 為今日，倒數第二筆為昨日）
     */
    internal fun evaluateRules(trend: List<HealthConnectBridge.DailyHealthPoint>): List<HealthRuleMatch> {
        if (trend.size < 2) return emptyList()
        val matches = mutableListOf<HealthRuleMatch>()

        // 規則 1：連續 2 晚睡眠 < 6h（昨晚 = todayPoint.sleepHours，前晚 = yesterdayPoint.sleepHours）
        val todayPoint = trend.last()
        val yesterdayPoint = trend[trend.size - 2]
        if (todayPoint.sleepHours in 0.1f..5.99f && yesterdayPoint.sleepHours in 0.1f..5.99f) {
            val s1 = String.format(Locale.US, "%.1f", yesterdayPoint.sleepHours)
            val s2 = String.format(Locale.US, "%.1f", todayPoint.sleepHours)
            matches.add(
                HealthRuleMatch(
                    ruleId = RULE_SLEEP_2NIGHTS_UNDER_6H,
                    title = "健康觀察提醒：連續兩晚睡眠不足 6 小時",
                    message = "🩺 **健康觀察提醒**\n系統本機聚合發現您前晚（$s1 小時）與昨晚（$s2 小時）連續 2 晚睡眠未滿 6 小時。建議今晚提早 30–45 分鐘休息，讓身體充分恢復。"
                )
            )
        }

        // 規則 2：連續 3 日步數 < 2000（取最近 3 個已結束的完整日，即排除今日最後一筆；若不足 4 筆則取前 3 筆）
        val completedDays = if (trend.size >= 4) trend.dropLast(1).takeLast(3) else trend.take(3)
        if (completedDays.size == 3 && completedDays.all { it.steps in 1L..1999L }) {
            val avgSteps = completedDays.map { it.steps }.average().toLong()
            matches.add(
                HealthRuleMatch(
                    ruleId = RULE_STEPS_3DAYS_UNDER_2000,
                    title = "健康觀察提醒：連續三日活動步數偏低",
                    message = "🩺 **健康觀察提醒**\n系統本機聚合發現您最近連續 3 日步數皆低於 2,000 步（平均約 $avgSteps 步）。建議今日趁空檔起身散步或伸展 10–15 分鐘，活絡循環。"
                )
            )
        }

        // 規則 3：昨日平均心率較 7 日基準高 ≥ 12 bpm
        val hrSamples = trend.mapNotNull { it.avgHeartRateBpm.takeIf { bpm -> bpm > 0L } }
        val baselineHr = if (hrSamples.size >= 3) hrSamples.average().toLong() else 0L
        val yesterdayHr = yesterdayPoint.avgHeartRateBpm
        if (yesterdayHr > 0L && baselineHr > 0L && (yesterdayHr - baselineHr) >= 12L) {
            val diff = yesterdayHr - baselineHr
            matches.add(
                HealthRuleMatch(
                    ruleId = RULE_HR_ELEVATED_12BPM,
                    title = "健康觀察提醒：昨日平均心率高於七日基準",
                    message = "🩺 **健康觀察提醒**\n系統本機聚合發現您昨日平均心率（${yesterdayHr} bpm）較近 7 日基準（${baselineHr} bpm）高出 ${diff} bpm。建議今日放緩步調、留意水分補充與休息。"
                )
            )
        }

        return matches
    }

    /**
     * 由 `ProactiveMonitorWorker` 週期呼叫（全程於 `Dispatchers.IO` 聚合讀取，零 AI 呼叫）。
     */
    suspend fun evaluateAndDeliverIfNeeded(
        ctx: Context,
        nowMs: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.isHealthAiQueryEnabled(appCtx)) return@withContext
        if (HealthConnectBridge.checkAvailability(appCtx) != HealthConnectBridge.SdkAvailability.AVAILABLE) {
            return@withContext
        }
        if (!HealthConnectBridge.hasAllReadPermissions(appCtx)) return@withContext

        val trend = HealthConnectBridge.readLast7DaysTrend(appCtx)
        if (trend.isEmpty()) return@withContext

        val matches = evaluateRules(trend)
        if (matches.isEmpty()) return@withContext

        val dayKey = LocalDate.now(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_LOCAL_DATE)
        for (match in matches) {
            val milestoneKey = "health:${match.ruleId}:$dayKey"
            if (PhantomPrefs.isProactiveMilestoneDelivered(appCtx, milestoneKey, dayKey)) {
                continue
            }
            if (PhantomPrefs.getHealthRuleWeeklyCount(appCtx, match.ruleId, nowMs) >= MAX_WEEKLY_PER_RULE) {
                continue
            }
            val msgId = ProactiveEngine.deliverProactiveMessage(
                ctx = appCtx,
                title = match.title,
                body = match.message,
                notifId = NotifIds.HEALTH_PROACTIVE,
                sendNotif = true,
                respectDailyCap = true,
                nowMs = nowMs,
                milestoneKeys = listOf(milestoneKey)
            )
            if (msgId != null) {
                PhantomPrefs.recordHealthRuleDelivered(appCtx, match.ruleId, nowMs)
            }
        }
    }
}
