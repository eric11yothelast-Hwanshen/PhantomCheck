package com.phantom.checker.util

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.phantom.checker.data.MessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.min

/**
 * 本機日曆整合橋接層（#329）：純 Android CalendarProvider ContentResolver，零第三方套件。
 *
 * 鐵律遵守：
 * 1. 所有 ContentResolver 查詢／寫入一律執行於 Dispatchers.IO，主執行緒零呼叫。
 * 2. 僅查詢／使用使用者在設定頁明確勾選之日曆 ID（預設全不勾）。
 * 3. 日曆資料完全留存本機，絕不外送原始日曆內容給 AI API；AI 查詢僅回傳本地計算結論文字（≤400 tokens）。
 * 4. 權限拒絕時誠實降級，不 crash；寫入權限未授予時提供 Intent(ACTION_INSERT) 預填 fallback。
 */
object CalendarBridge {

    data class CalendarAccountInfo(
        val id: Long,
        val displayName: String,
        val accountName: String,
        val accountType: String,
        val accessLevel: Int,
        val color: Int
    ) {
        val isWritable: Boolean
            get() = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
    }

    data class CalendarEventInstance(
        val eventId: Long,
        val calendarId: Long,
        val title: String,
        val beginMs: Long,
        val endMs: Long,
        val allDay: Boolean,
        val location: String? = null
    )

    data class FreeSlot(
        val beginMs: Long,
        val endMs: Long
    )

    data class NotificationCalendarSuggestion(
        val messageId: Long,
        val title: String,
        val startMs: Long,
        val endMs: Long,
        val notes: String
    )

    private const val MIN_FREE_SLOT_MS = 30L * 60L * 1000L // ≥30 分鐘才列為可用空檔
    private const val DEFAULT_EVENT_DURATION_MS = 60L * 60L * 1000L // 預設 1 小時
    private const val MAX_SUMMARY_CHARS = 780 // 嚴格控制 ≤400 tokens

    private val SCHEDULE_KEYWORDS = listOf(
        "訂位", "預約", "餐廳", "面試", "聚餐", "聚會", "約會", "開會", "會議",
        "看診", "回診", "掛號", "門診", "繳費", "截止", "到期", "班機", "高鐵",
        "火車", "取票", "演出", "講座", "考試", "上課", "報到", "簽約", "取貨"
    )

    private val suggestionCache = LruCache<Long, NotificationCalendarSuggestion>(64)
    private val promptedMessageIds = LruCache<Long, Boolean>(256)

    fun hasReadPermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** 僅在已授予讀取權限且使用者已勾選至少一個日曆時啟用 AI 日曆整合 */
    fun isCalendarEnabledForAi(ctx: Context): Boolean =
        hasReadPermission(ctx) && PhantomPrefs.getSelectedCalendarIds(ctx).isNotEmpty()

    /** 列出裝置所有可讀日曆（含權限等級與是否可寫）；全程 Dispatchers.IO */
    suspend fun listCalendars(ctx: Context): List<CalendarAccountInfo> = withContext(Dispatchers.IO) {
        if (!hasReadPermission(ctx)) return@withContext emptyList()
        val result = mutableListOf<CalendarAccountInfo>()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.CALENDAR_COLOR
        )
        try {
            ctx.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, ${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val displayName = cursor.getString(1)?.trim().orEmpty().ifEmpty { "未命名日曆 #$id" }
                    val accountName = cursor.getString(2)?.trim().orEmpty()
                    val accountType = cursor.getString(3)?.trim().orEmpty()
                    val accessLevel = cursor.getInt(4)
                    val color = cursor.getInt(5)
                    result.add(
                        CalendarAccountInfo(
                            id = id,
                            displayName = displayName,
                            accountName = accountName,
                            accountType = accountType,
                            accessLevel = accessLevel,
                            color = color
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        result
    }

    /** 取得使用者已勾選且具寫入權限（ACCESS_LEVEL ≥ CONTRIBUTOR）的日曆清單 */
    suspend fun listSelectedWritableCalendars(ctx: Context): List<CalendarAccountInfo> = withContext(Dispatchers.IO) {
        val selected = PhantomPrefs.getSelectedCalendarIds(ctx)
        if (selected.isEmpty()) return@withContext emptyList()
        listCalendars(ctx).filter { it.id in selected && it.isWritable }
    }

    /**
     * 透過 CalendarContract.Instances 範圍查詢指定時段內之所有事件實例（僅限已勾選之日曆 ID）。
     * 全程 Dispatchers.IO，主執行緒零呼叫。
     */
    suspend fun queryInstances(
        ctx: Context,
        beginMs: Long,
        endMs: Long,
        selectedCalendarIds: Set<Long> = PhantomPrefs.getSelectedCalendarIds(ctx)
    ): List<CalendarEventInstance> = withContext(Dispatchers.IO) {
        if (!hasReadPermission(ctx) || selectedCalendarIds.isEmpty() || endMs <= beginMs) {
            return@withContext emptyList()
        }
        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, beginMs)
        ContentUris.appendId(uriBuilder, endMs)
        val uri = uriBuilder.build()

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION
        )
        val placeholders = selectedCalendarIds.joinToString(",") { "?" }
        val selection = "${CalendarContract.Instances.CALENDAR_ID} IN ($placeholders)"
        val selectionArgs = selectedCalendarIds.map { it.toString() }.toTypedArray()

        val list = mutableListOf<CalendarEventInstance>()
        try {
            ctx.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${CalendarContract.Instances.BEGIN} ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val eventId = cursor.getLong(0)
                    val calId = cursor.getLong(1)
                    val title = cursor.getString(2)?.trim().orEmpty().ifEmpty { "（無標題行程）" }
                    val b = cursor.getLong(3)
                    val e = cursor.getLong(4)
                    val allDay = cursor.getInt(5) == 1
                    val loc = cursor.getString(6)?.trim()?.takeIf { it.isNotEmpty() }
                    list.add(
                        CalendarEventInstance(
                            eventId = eventId,
                            calendarId = calId,
                            title = title,
                            beginMs = b,
                            endMs = max(e, b),
                            allDay = allDay,
                            location = loc
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return@withContext emptyList()
        } catch (_: Exception) {
            return@withContext emptyList()
        }
        list
    }

    /** 寫入前重疊衝突檢查（部分重疊亦計入），與空檔查詢共用 queryInstances 層 */
    suspend fun findConflictingEvents(
        ctx: Context,
        startMs: Long,
        endMs: Long,
        selectedCalendarIds: Set<Long> = PhantomPrefs.getSelectedCalendarIds(ctx)
    ): List<CalendarEventInstance> = withContext(Dispatchers.IO) {
        val safeEnd = max(endMs, startMs + 60_000L)
        queryInstances(ctx, startMs, safeEnd, selectedCalendarIds).filter { inst ->
            inst.beginMs < safeEnd && inst.endMs > startMs
        }
    }

    /**
     * 計算本機日曆指定區間之行程與每日空檔（扣除既有行程後 ≥30 分鐘之空檔）。
     * 回傳文字已嚴格控管於 ≤400 tokens，且純為本機查詢結果，絕不編造。
     */
    suspend fun queryFreeSlotsSummary(
        ctx: Context,
        days: Int = 7,
        fromHHmm: String? = null,
        toHHmm: String? = null,
        dayFilter: String? = null
    ): String = withContext(Dispatchers.IO) {
        if (!hasReadPermission(ctx)) {
            return@withContext "尚未授予「日曆讀取權限」，無法查詢本機行程。請前往「設定 → 記憶庫與 AI 助手 → 本機日曆整合」授予權限並勾選欲開放的日曆。"
        }
        val selectedIds = PhantomPrefs.getSelectedCalendarIds(ctx)
        if (selectedIds.isEmpty()) {
            return@withContext "尚未勾選任何本機日曆。請前往「設定 → 記憶庫與 AI 助手 → 本機日曆整合」勾選要開放給助手查詢的日曆帳號。"
        }

        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val clampedDays = days.coerceIn(1, 14)

        val targetDates: List<LocalDate> = when (dayFilter?.trim()?.lowercase(Locale.ROOT)) {
            "today", "今天", "今日" -> listOf(today)
            "tomorrow", "明天", "明日" -> listOf(today.plusDays(1))
            "monday", "mon", "週一", "星期一" -> listOf(nextOrSameDayOfWeek(today, DayOfWeek.MONDAY))
            "tuesday", "tue", "週二", "星期二" -> listOf(nextOrSameDayOfWeek(today, DayOfWeek.TUESDAY))
            "wednesday", "wed", "週三", "星期三" -> listOf(nextOrSameDayOfWeek(today, DayOfWeek.WEDNESDAY))
            "thursday", "thu", "週四", "星期四" -> listOf(nextOrSameDayOfWeek(today, DayOfWeek.THURSDAY))
            "friday", "fri", "週五", "星期五" -> listOf(nextOrSameDayOfWeek(today, DayOfWeek.FRIDAY))
            "saturday", "sat", "週六", "星期六" -> listOf(nextOrSameDayOfWeek(today, DayOfWeek.SATURDAY))
            "sunday", "sun", "週日", "星期日" -> listOf(nextOrSameDayOfWeek(today, DayOfWeek.SUNDAY))
            else -> (0 until clampedDays).map { today.plusDays(it.toLong()) }
        }

        val winStart = parseLocalTimeOrDefault(fromHHmm, LocalTime.of(6, 0))
        val winEnd = parseLocalTimeOrDefault(toHHmm, LocalTime.of(23, 59))
        val rangeStartMs = targetDates.first().atStartOfDay(zone).toInstant().toEpochMilli()
        val rangeEndMs = targetDates.last().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val allInstances = queryInstances(ctx, rangeStartMs, rangeEndMs, selectedIds)
        val dateFmt = DateTimeFormatter.ofPattern("MM/dd (E)", Locale.TRADITIONAL_CHINESE)
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("📅 本機日曆查詢結果（僅統計已勾選之 ${selectedIds.size} 個日曆，空檔門檻 ≥30 分鐘）：")

        for (date in targetDates) {
            val dayStartMs = date.atTime(winStart).atZone(zone).toInstant().toEpochMilli()
            val dayEndMs = date.atTime(winEnd).atZone(zone).toInstant().toEpochMilli()
            if (dayEndMs <= dayStartMs) continue

            val dayEvents = allInstances.filter { inst ->
                inst.beginMs < dayEndMs && inst.endMs > dayStartMs
            }.sortedBy { it.beginMs }

            // 合併重疊忙碌區間以精確計算空檔
            val mergedBusy = mutableListOf<Pair<Long, Long>>()
            for (ev in dayEvents) {
                val b = max(ev.beginMs, dayStartMs)
                val e = min(ev.endMs, dayEndMs)
                if (e <= b) continue
                if (mergedBusy.isEmpty() || b > mergedBusy.last().second) {
                    mergedBusy.add(b to e)
                } else {
                    val last = mergedBusy.removeAt(mergedBusy.lastIndex)
                    mergedBusy.add(last.first to max(last.second, e))
                }
            }

            val freeSlots = mutableListOf<FreeSlot>()
            var cursor = dayStartMs
            for ((b, e) in mergedBusy) {
                if (b - cursor >= MIN_FREE_SLOT_MS) {
                    freeSlots.add(FreeSlot(cursor, b))
                }
                cursor = max(cursor, e)
            }
            if (dayEndMs - cursor >= MIN_FREE_SLOT_MS) {
                freeSlots.add(FreeSlot(cursor, dayEndMs))
            }

            val freeText = if (freeSlots.isEmpty()) {
                "無空檔"
            } else {
                freeSlots.joinToString("、") { slot ->
                    val s = Instant.ofEpochMilli(slot.beginMs).atZone(zone).format(timeFmt)
                    val e = Instant.ofEpochMilli(slot.endMs).atZone(zone).format(timeFmt)
                    "$s–$e"
                }
            }

            val eventsText = if (dayEvents.isEmpty()) {
                "無排定行程"
            } else {
                dayEvents.take(4).joinToString("；") { ev ->
                    if (ev.allDay) {
                        "全天 ${ev.title}"
                    } else {
                        val s = Instant.ofEpochMilli(ev.beginMs).atZone(zone).format(timeFmt)
                        val e = Instant.ofEpochMilli(ev.endMs).atZone(zone).format(timeFmt)
                        "$s–$e ${ev.title}"
                    }
                } + if (dayEvents.size > 4) "（另有 ${dayEvents.size - 4} 筆）" else ""
            }

            val line = "- **${date.format(dateFmt)}**｜空檔：$freeText｜行程：$eventsText\n"
            if (sb.length + line.length > MAX_SUMMARY_CHARS) {
                sb.append("…（其餘日期已依顯示長度上限省略）")
                break
            }
            sb.append(line)
        }

        sb.toString().trim()
    }

    /**
     * 寫入單筆事件至本機日曆（ContentResolver.insert，全程 Dispatchers.IO）。
     * 妥善處理 EVENT_TIMEZONE 與 ALL_DAY UTC 邊界規範。
     */
    suspend fun insertEvent(
        ctx: Context,
        calendarId: Long,
        title: String,
        startMs: Long,
        endMs: Long,
        allDay: Boolean = false,
        location: String? = null,
        description: String? = null
    ): Result<Uri> = withContext(Dispatchers.IO) {
        if (!hasWritePermission(ctx)) {
            return@withContext Result.failure(SecurityException("尚未授予日曆寫入權限（WRITE_CALENDAR）"))
        }
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("行程標題不可為空"))
        }
        val (dtStart, dtEnd, tzId) = if (allDay) {
            // Android Calendar Provider 規範：ALL_DAY 事件須以 UTC 午夜對齊並使用 UTC 時區
            val localDate = Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault()).toLocalDate()
            val utcStart = localDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            val utcEnd = localDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            Triple(utcStart, utcEnd, "UTC")
        } else {
            val safeEnd = if (endMs > startMs) endMs else startMs + DEFAULT_EVENT_DURATION_MS
            Triple(startMs, safeEnd, TimeZone.getDefault().id)
        }
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, cleanTitle)
            put(CalendarContract.Events.DTSTART, dtStart)
            put(CalendarContract.Events.DTEND, dtEnd)
            put(CalendarContract.Events.EVENT_TIMEZONE, tzId)
            put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)
            if (!location.isNullOrBlank()) {
                put(CalendarContract.Events.EVENT_LOCATION, location.trim())
            }
            if (!description.isNullOrBlank()) {
                put(CalendarContract.Events.DESCRIPTION, description.trim())
            }
        }
        try {
            val inserted = ctx.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            if (inserted != null) {
                Result.success(inserted)
            } else {
                Result.failure(IllegalStateException("日曆寫入失敗，系統未回傳事件 URI"))
            }
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** 權限未授予時的 fallback：以 ACTION_INSERT Intent 預填欄位開啟系統日曆 App */
    fun buildInsertEventIntent(
        title: String,
        startMs: Long,
        endMs: Long,
        allDay: Boolean = false,
        location: String? = null,
        description: String? = null
    ): Intent {
        val safeEnd = if (endMs > startMs) endMs else startMs + DEFAULT_EVENT_DURATION_MS
        return Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title.trim())
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMs)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, safeEnd)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, allDay)
            if (!location.isNullOrBlank()) {
                putExtra(CalendarContract.Events.EVENT_LOCATION, location.trim())
            }
            if (!description.isNullOrBlank()) {
                putExtra(CalendarContract.Events.DESCRIPTION, description.trim())
            }
        }
    }

    /** 寫入成功後開啟系統日曆檢視該事件 */
    fun buildViewEventIntent(eventUri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).setData(eventUri)

    /** 判斷通知／備忘是否屬於訂位、面試、聚餐、繳費截止等行程類型 */
    fun isScheduleTypeMessage(content: String, memoText: String? = null): Boolean {
        val combined = "$content ${memoText.orEmpty()}"
        return SCHEDULE_KEYWORDS.any { combined.contains(it, ignoreCase = true) }
    }

    /**
     * 通知轉行程自動檢查（NotificationCollectorService 備忘流程呼叫，全程 Dispatchers.IO）：
     * - 未勾選日曆或無權限不觸發。
     * - 同 messageId 以 LruCache 節流僅提示一次。
     * - 若勾選日曆同標題或同時段已存在事件則不重複建議。
     */
    suspend fun maybeSuggestFromNotification(
        ctx: Context,
        messageId: Long,
        title: String,
        startMs: Long,
        notes: String
    ): NotificationCalendarSuggestion? = withContext(Dispatchers.IO) {
        if (!isCalendarEnabledForAi(ctx)) return@withContext null
        synchronized(promptedMessageIds) {
            if (promptedMessageIds.get(messageId) == true) {
                return@withContext synchronized(suggestionCache) { suggestionCache.get(messageId) }
            }
        }
        val endMs = startMs + DEFAULT_EVENT_DURATION_MS
        val existing = findConflictingEvents(ctx, startMs, endMs)
        val normTitle = title.trim().lowercase(Locale.ROOT)
        val alreadyExists = existing.any { ev ->
            val evTitle = ev.title.trim().lowercase(Locale.ROOT)
            (evTitle.isNotEmpty() && (evTitle == normTitle || evTitle.contains(normTitle) || normTitle.contains(evTitle))) ||
                (kotlin.math.abs(ev.beginMs - startMs) <= 15L * 60L * 1000L)
        }
        synchronized(promptedMessageIds) {
            promptedMessageIds.put(messageId, true)
        }
        if (alreadyExists) return@withContext null

        val suggestion = NotificationCalendarSuggestion(
            messageId = messageId,
            title = title.trim().take(60),
            startMs = startMs,
            endMs = endMs,
            notes = notes.trim().take(200)
        )
        synchronized(suggestionCache) {
            suggestionCache.put(messageId, suggestion)
        }
        suggestion
    }

    fun getCachedSuggestion(messageId: Long): NotificationCalendarSuggestion? =
        synchronized(suggestionCache) { suggestionCache.get(messageId) }

    fun dismissSuggestion(messageId: Long) {
        synchronized(suggestionCache) { suggestionCache.remove(messageId) }
        synchronized(promptedMessageIds) { promptedMessageIds.put(messageId, true) }
    }

    /** 訊息詳情頁開啟時即時評估是否應顯示「📅 加入日曆」建議卡（Dispatchers.IO） */
    suspend fun evaluateMessageForSuggestion(
        ctx: Context,
        msg: MessageEntity
    ): NotificationCalendarSuggestion? = withContext(Dispatchers.IO) {
        getCachedSuggestion(msg.id)?.let { return@withContext it }
        if (!isCalendarEnabledForAi(ctx)) return@withContext null
        if (!isScheduleTypeMessage(msg.content)) return@withContext null
        val parsed = ClientTimeParser.parseNaturalReminderTime(null, msg.content, msg.timestamp)
        val startMs = (parsed as? NaturalTimeParseResult.Success)?.epochMillis
            ?: return@withContext null
        if (startMs <= System.currentTimeMillis() - 60L * 60L * 1000L) return@withContext null
        val title = "${msg.sender.ifBlank { msg.appLabel }}：${msg.content.replace("\n", " ").trim().take(28)}"
        maybeSuggestFromNotification(
            ctx = ctx,
            messageId = msg.id,
            title = title,
            startMs = startMs,
            notes = "來源：${msg.appLabel} / ${msg.sender}\n${msg.content.take(120)}"
        )
    }

    fun formatConflictSummary(conflicts: List<CalendarEventInstance>): String {
        val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)
        return conflicts.take(3).joinToString("；") { ev ->
            if (ev.allDay) {
                "${ev.title}（全天）"
            } else {
                "${ev.title} ${fmt.format(Date(ev.beginMs))}–${SimpleDateFormat("HH:mm", Locale.US).format(Date(ev.endMs))}"
            }
        }
    }

    private fun nextOrSameDayOfWeek(from: LocalDate, target: DayOfWeek): LocalDate {
        var d = from
        for (i in 0..6) {
            if (d.dayOfWeek == target) return d
            d = d.plusDays(1)
        }
        return from
    }

    private fun parseLocalTimeOrDefault(raw: String?, fallback: LocalTime): LocalTime {
        if (raw.isNullOrBlank()) return fallback
        val clean = raw.trim()
        if (clean == "24:00") return LocalTime.of(23, 59)
        return try {
            LocalTime.parse(clean, DateTimeFormatter.ofPattern("H:mm"))
        } catch (_: Exception) {
            fallback
        }
    }
}
