package com.phantom.checker.service

/**
 * 全專案通知 ID 集中管理，各區段嚴格互斥，避免互相覆蓋。
 *
 * 區段配置：
 * - 1001..1099：系統/常駐/排程固定通知（1001 常駐統計、1002 開機提醒、1003 每日摘要、1004 週報）
 * - 2000..21999：詐騙警示（2000 + msgId % 20000）
 * - 22000..41999：重要訊息提醒（22000 + msgId % 20000）
 * - 100000..109999：待辦提醒（100000 + messageId % 10000）
 * - 110000..119999：AI 自然語言待辦提醒（110000 + todoId % 10000）
 */
object NotifIds {
    const val STATS_PERSISTENT = 1001
    const val BOOT_ALERT = 1002
    const val DAILY_SUMMARY = 1003
    const val WEEKLY_SUMMARY = 1004
    const val AI_TAG_FAILURE = 1005
    const val AI_OFFLINE_QUEUE = 1006
    const val UPDATE_AVAILABLE = 1007
    const val BRIEFING_MORNING = 1008
    const val BRIEFING_EVENING = 1009
    const val PROACTIVE_PUSH = 1010
    const val GITHUB_ALERT = 1011
    const val CALENDAR_INSIGHT = 1012
    const val TASK_TEMPLATE_BASE = 1050

    private const val CALENDAR_INSIGHT_BASE = 1020
    private const val CALENDAR_INSIGHT_SPAN = 25

    fun calendarInsight(hash: Int): Int =
        CALENDAR_INSIGHT_BASE + Math.floorMod(hash, CALENDAR_INSIGHT_SPAN)

    fun taskTemplate(templateId: Long): Int =
        TASK_TEMPLATE_BASE + Math.floorMod(templateId, 40L).toInt()

    private const val SCAM_ALERT_BASE = 2000
    private const val SCAM_ALERT_SPAN = 20000L

    private const val IMPORTANT_ALERT_BASE = 22000
    private const val IMPORTANT_ALERT_SPAN = 20000L

    private const val REMINDER_BASE = 100000
    private const val REMINDER_SPAN = 10000L

    private const val TODO_REMINDER_BASE = 110000
    private const val TODO_REMINDER_SPAN = 10000L

    private const val REPLY_PROXY_BASE = 120000
    private const val REPLY_PROXY_SPAN = 10000

    fun scamAlert(msgId: Long): Int =
        SCAM_ALERT_BASE + Math.floorMod(msgId, SCAM_ALERT_SPAN).toInt()

    fun importantAlert(msgId: Long): Int =
        IMPORTANT_ALERT_BASE + Math.floorMod(msgId, IMPORTANT_ALERT_SPAN).toInt()

    fun reminder(messageId: Long): Int =
        REMINDER_BASE + Math.floorMod(messageId, REMINDER_SPAN).toInt()

    fun todoReminder(todoId: Long): Int =
        TODO_REMINDER_BASE + Math.floorMod(todoId, TODO_REMINDER_SPAN).toInt()

    fun replyProxy(sessionHash: Int): Int =
        REPLY_PROXY_BASE + Math.floorMod(sessionHash, REPLY_PROXY_SPAN)
}
