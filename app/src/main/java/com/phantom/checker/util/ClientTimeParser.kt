package com.phantom.checker.util

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

sealed class NaturalTimeParseResult {
    data class Success(val epochMillis: Long) : NaturalTimeParseResult()
    data class PastTime(val epochMillis: Long) : NaturalTimeParseResult()
    data object Unparseable : NaturalTimeParseResult()
}

/**
 * 客戶端確定性時間解析器：
 * - 支援結構化 ISO-8601 / yyyy-MM-dd HH:mm（AI 輸出）
 * - 支援繁體中文自然語言相對/絕對時間（ChatScreen 提醒功能共用）
 */
object ClientTimeParser {

    private val STRUCTURED_FORMATTERS = listOf(
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy/MM/dd HH:mm",
        "yyyy/MM/dd HH:mm:ss"
    ).map { DateTimeFormatter.ofPattern(it) }

    private val HALF_HOUR_LATER_REGEX = Regex("""半(?:個)?小時(?:以)?後""")
    private val HOURS_LATER_REGEX = Regex("""(\d+(?:\.\d+)?)\s*(?:個)?小時(?:以)?後""")
    private val MINUTES_LATER_REGEX = Regex("""(\d+)\s*分鐘(?:以)?後""")
    private val DAYS_LATER_REGEX = Regex("""(\d+)\s*天(?:以)?後""")
    private val YMD_DATE_REGEX = Regex("""(\d{4})[/\-](\d{1,2})[/\-](\d{1,2})""")
    private val MD_DATE_REGEX = Regex("""(\d{1,2})[月/\-](\d{1,2})日?""")
    private val DOW_DATE_REGEX = Regex("""(下)?(?:這|本)?(?:星期|禮拜|週)([一二三四五六日天])""")
    private val COLON_TIME_REGEX = Regex("""(\d{1,2})\s*[:：]\s*(\d{2})""")
    private val CN_TIME_REGEX = Regex("""(\d{1,2})\s*[點時]\s*(半|(\d{1,2})\s*分?)?""")

    /**
     * 僅解析結構化時間字串（如 AI 輸出的 `yyyy-MM-dd HH:mm`），不猜測自然語言；
     * 解析失敗或空白回傳 null。
     */
    fun parseStructuredDueAt(rawTime: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (rawTime.isNullOrBlank()) return null
        val trimmed = rawTime.trim()
        runCatching { ZonedDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        for (formatter in STRUCTURED_FORMATTERS) {
            val ldt = runCatching { LocalDateTime.parse(trimmed, formatter) }.getOrNull()
            if (ldt != null) {
                return ldt.atZone(zone).toInstant().toEpochMilli()
            }
        }
        return null
    }

    fun parseNaturalReminderTime(
        rawTime: String?,
        rawQuery: String,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): NaturalTimeParseResult {
        val nowZdt = Instant.ofEpochMilli(nowMs).atZone(zone)

        // 1. 優先嘗試結構化 ISO-8601 / yyyy-MM-dd HH:mm（AI 輸出）
        parseStructuredDueAt(rawTime, zone)?.let { ms ->
            return if (ms > nowMs) NaturalTimeParseResult.Success(ms) else NaturalTimeParseResult.PastTime(ms)
        }

        // 2. 用戶端確定性自然語言時間解析（先看 rawTime，若無再看 rawQuery）
        val candidates = listOfNotNull(rawTime?.takeIf { it.isNotBlank() }, rawQuery.takeIf { it.isNotBlank() })
        for (text in candidates) {
            parseNaturalTextWithZone(text, nowZdt, nowMs, zone)?.let { return it }
        }
        return NaturalTimeParseResult.Unparseable
    }

    private fun parseNaturalTextWithZone(
        text: String,
        nowZdt: ZonedDateTime,
        nowMs: Long,
        zone: ZoneId
    ): NaturalTimeParseResult? {
        val s = text.trim()
        if (s.isEmpty()) return null

        // 2a. 相對時長（以 Instant/ZonedDateTime 累加，跨 DST 切換仍精確）
        if (HALF_HOUR_LATER_REGEX.containsMatchIn(s)) {
            val target = nowZdt.plusMinutes(30).withSecond(0).withNano(0)
            return NaturalTimeParseResult.Success(target.toInstant().toEpochMilli())
        }
        HOURS_LATER_REGEX.find(s)?.let { m ->
            val hours = m.groupValues[1].toDoubleOrNull()
            if (hours != null && hours > 0) {
                val mins = (hours * 60.0).roundToInt().coerceAtLeast(1)
                val target = nowZdt.plusMinutes(mins.toLong()).withSecond(0).withNano(0)
                return NaturalTimeParseResult.Success(target.toInstant().toEpochMilli())
            }
        }
        MINUTES_LATER_REGEX.find(s)?.let { m ->
            val mins = m.groupValues[1].toLongOrNull()
            if (mins != null && mins > 0L) {
                val target = nowZdt.plusMinutes(mins).withSecond(0).withNano(0)
                return NaturalTimeParseResult.Success(target.toInstant().toEpochMilli())
            }
        }

        // 2b. 日期解析（含「X 天後」，保留時間部分往下繼續解析）
        val today = nowZdt.toLocalDate()
        var parsedDate: LocalDate? = null
        var hasExplicitDate = false

        DAYS_LATER_REGEX.find(s)?.let { m ->
            val days = m.groupValues[1].toLongOrNull()
            if (days != null && days > 0L) {
                parsedDate = today.plusDays(days)
                hasExplicitDate = true
            }
        }
        if (parsedDate == null) {
            YMD_DATE_REGEX.find(s)?.let { m ->
                val y = m.groupValues[1].toIntOrNull()
                val mo = m.groupValues[2].toIntOrNull()
                val d = m.groupValues[3].toIntOrNull()
                if (y != null && mo != null && d != null) {
                    parsedDate = runCatching { LocalDate.of(y, mo, d) }.getOrNull()
                    if (parsedDate != null) hasExplicitDate = true
                }
            }
        }
        if (parsedDate == null) {
            MD_DATE_REGEX.find(s)?.let { m ->
                val mo = m.groupValues[1].toIntOrNull()
                val d = m.groupValues[2].toIntOrNull()
                if (mo != null && d != null) {
                    var candidate = runCatching { LocalDate.of(today.year, mo, d) }.getOrNull()
                    if (candidate != null && candidate.isBefore(today) &&
                        java.time.temporal.ChronoUnit.DAYS.between(candidate, today) > 180
                    ) {
                        candidate = runCatching { LocalDate.of(today.year + 1, mo, d) }.getOrNull()
                    }
                    parsedDate = candidate
                    if (parsedDate != null) hasExplicitDate = true
                }
            }
        }
        if (parsedDate == null) {
            when {
                s.contains("大後天") -> { parsedDate = today.plusDays(3); hasExplicitDate = true }
                s.contains("後天") || s.contains("後日") -> { parsedDate = today.plusDays(2); hasExplicitDate = true }
                s.contains("明天") || s.contains("明日") || s.contains("明早") || s.contains("明晚") -> {
                    parsedDate = today.plusDays(1); hasExplicitDate = true
                }
                s.contains("今天") || s.contains("今日") || s.contains("今晚") -> {
                    parsedDate = today; hasExplicitDate = true
                }
            }
        }
        if (parsedDate == null) {
            val dowMatch = DOW_DATE_REGEX.find(s)
            if (dowMatch != null) {
                val isNextWeek = dowMatch.groupValues[1] == "下"
                val dow = when (dowMatch.groupValues[2]) {
                    "一" -> DayOfWeek.MONDAY
                    "二" -> DayOfWeek.TUESDAY
                    "三" -> DayOfWeek.WEDNESDAY
                    "四" -> DayOfWeek.THURSDAY
                    "五" -> DayOfWeek.FRIDAY
                    "六" -> DayOfWeek.SATURDAY
                    else -> DayOfWeek.SUNDAY
                }
                parsedDate = if (isNextWeek) {
                    val thisWeeksMon = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    thisWeeksMon.plusWeeks(1).with(TemporalAdjusters.nextOrSame(dow))
                } else {
                    today.with(TemporalAdjusters.nextOrSame(dow))
                }
                hasExplicitDate = true
            }
        }

        // 2c. 時間解析（HH:mm、X點Y分、時段詞）
        var hour: Int? = null
        var minute = 0
        var hasExplicitTime = false

        val colonMatch = COLON_TIME_REGEX.find(s)
        if (colonMatch != null) {
            val h = colonMatch.groupValues[1].toIntOrNull()
            val min = colonMatch.groupValues[2].toIntOrNull()
            if (h != null && min != null && h in 0..23 && min in 0..59) {
                hour = h
                minute = min
                hasExplicitTime = true
            }
        }
        if (!hasExplicitTime) {
            val cnTimeMatch = CN_TIME_REGEX.find(s)
            if (cnTimeMatch != null) {
                val h = cnTimeMatch.groupValues[1].toIntOrNull()
                val tail = cnTimeMatch.groupValues[2]
                val minDigits = cnTimeMatch.groupValues[3]
                if (h != null && h in 0..23) {
                    hour = h
                    minute = when {
                        tail == "半" -> 30
                        minDigits.isNotEmpty() -> minDigits.toIntOrNull()?.coerceIn(0, 59) ?: 0
                        else -> 0
                    }
                    hasExplicitTime = true
                }
            }
        }

        val isMorning = s.contains("早上") || s.contains("上午") || s.contains("明早")
        val isNoon = s.contains("中午") || s.contains("午間")
        val isAfternoon = s.contains("下午") || s.contains("午後")
        val isEvening = s.contains("傍晚")
        val isNight = s.contains("晚上") || s.contains("今晚") || s.contains("明晚") || s.contains("夜裡")
        val isDawn = s.contains("凌晨") || s.contains("半夜")
        val hasPeriodWord = isMorning || isNoon || isAfternoon || isEvening || isNight || isDawn

        var rollToNextDay = false
        if (hasExplicitTime && hour != null) {
            val h = hour!!
            when {
                isNight && h == 12 -> {
                    hour = 0
                    rollToNextDay = true
                }
                isDawn && h == 12 -> hour = 0
                isNoon && h in 1..6 -> hour = h + 12
                (isAfternoon || isEvening || isNight) && h in 1..11 -> hour = h + 12
                isMorning && h == 12 -> hour = 0
                else -> hour = h
            }
        } else if (hasPeriodWord) {
            hour = when {
                isDawn -> 1
                isMorning -> 9
                isNoon -> 12
                isAfternoon -> 15
                isEvening -> 18
                isNight -> 20
                else -> 9
            }
            minute = 0
            hasExplicitTime = true
        }

        if (!hasExplicitDate && !hasExplicitTime) {
            return null
        }

        val baseDate = parsedDate ?: today
        val finalDate = if (rollToNextDay) baseDate.plusDays(1) else baseDate
        val finalTime = if (hasExplicitTime && hour != null) LocalTime.of(hour!!, minute) else LocalTime.of(9, 0)
        var targetLdt = LocalDateTime.of(finalDate, finalTime)
        var targetMs = targetLdt.atZone(zone).toInstant().toEpochMilli()

        // 只給時間沒給日期：若今天該時間已過，則順延至明天
        if (!hasExplicitDate && !rollToNextDay && targetMs <= nowMs) {
            targetLdt = targetLdt.plusDays(1)
            targetMs = targetLdt.atZone(zone).toInstant().toEpochMilli()
        }

        return if (targetMs > nowMs) {
            NaturalTimeParseResult.Success(targetMs)
        } else {
            NaturalTimeParseResult.PastTime(targetMs)
        }
    }
}
