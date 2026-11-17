package com.phantom.checker.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MuteMode(val prefValue: String, val label: String) {
    BLOCK_RECORD("block", "完全不記錄"),
    SILENT_RECORD("silent", "記錄但不進統計提醒");

    companion object {
        fun fromPref(value: String?): MuteMode =
            entries.firstOrNull { it.prefValue == value } ?: BLOCK_RECORD
    }
}

enum class NotifClickBehavior(val prefValue: String, val label: String) {
    OPEN_PHANTOM("phantom", "開啟幻訊對話"),
    OPEN_ORIGINAL_APP("original", "開啟原 App");

    companion object {
        fun fromPref(value: String?): NotifClickBehavior =
            entries.firstOrNull { it.prefValue == value } ?: OPEN_PHANTOM
    }
}

enum class FontScaleMode(val prefValue: String, val label: String, val scale: Float) {
    SMALL("small", "小", 0.9f),
    NORMAL("normal", "標準", 1.0f),
    LARGE("large", "大", 1.15f);

    companion object {
        fun fromPref(value: String?): FontScaleMode =
            entries.firstOrNull { it.prefValue == value } ?: NORMAL
    }
}

enum class PersistentNotifStyle(val prefValue: String, val label: String) {
    DETAILED("detailed", "詳細（今日/未讀/詐騙）"),
    COMPACT("compact", "精簡（僅今日）");

    companion object {
        fun fromPref(value: String?): PersistentNotifStyle =
            entries.firstOrNull { it.prefValue == value } ?: DETAILED
    }
}

enum class CardDensity(val prefValue: String, val label: String) {
    COMFORTABLE("comfortable", "舒適"),
    COMPACT("compact", "緊湊");

    companion object {
        fun fromPref(value: String?): CardDensity =
            entries.firstOrNull { it.prefValue == value } ?: COMFORTABLE
    }
}

enum class HomeSortOrder(val prefValue: String, val label: String) {
    RECENT("recent", "時間新→舊"),
    UNREAD("unread", "未讀多→少"),
    RISK("risk", "風險高→低");

    companion object {
        fun fromPref(value: String?): HomeSortOrder =
            entries.firstOrNull { it.prefValue == value } ?: RECENT
    }
}

/**
 * 幻訊 App 通用偏好設定（SharedPreferences）。
 */
object PhantomPrefs {
    private const val PREFS_NAME = "phantom_app_prefs"
    private const val KEY_RECORD_ONGOING = "record_ongoing_notifications"
    private const val KEY_PERSISTENT_STATS = "persistent_stats_notification"
    private const val KEY_FAVORITE_APPS = "favorite_apps"
    private const val KEY_MUTED_APPS = "muted_apps"
    private const val KEY_MUTE_MODES = "muted_app_modes"
    private const val KEY_SCAM_ALERT_NOTIF = "scam_alert_notif_enabled"
    private const val KEY_AI_RISK_ENABLED = "ai_risk_enabled"
    private const val KEY_DAILY_AI_LIMIT = "daily_ai_limit"
    private const val KEY_AI_USAGE_DATE = "ai_usage_date"
    private const val KEY_AI_USAGE_COUNT = "ai_usage_count"
    private const val KEY_DAILY_SUMMARY_ENABLED = "daily_summary_enabled"
    private const val KEY_DAILY_SUMMARY_HOUR = "daily_summary_hour"
    private const val KEY_DAILY_SUMMARY_MINUTE = "daily_summary_minute"
    private const val KEY_NOTIF_CLICK_BEHAVIOR = "notif_click_behavior"
    private const val KEY_SAME_KEY_THROTTLE_SEC = "same_key_throttle_sec"
    private const val KEY_SAME_KEY_DIGIT_VARIANCE_THROTTLE = "same_key_digit_variance_throttle"
    private const val KEY_APP_SAME_KEY_THROTTLE_MAP = "app_same_key_throttle_map"
    private const val KEY_RETENTION_MAX_COUNT = "retention_max_count"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color_enabled"
    private const val KEY_FONT_SCALE = "font_scale_mode"
    private const val KEY_ALERT_KEYWORDS = "instant_alert_keywords"
    private const val KEY_ONBOARDING_COMPLETED_V1 = "onboarding_completed_v1"
    private const val KEY_ONBOARDING_STEP_V2 = "onboarding_step_v2"
    private const val KEY_SETTINGS_TOUR_COMPLETED_V1 = "settings_tour_completed_v1"
    private const val KEY_OEM_AUTOSTART_CONFIRMED = "oem_autostart_confirmed"
    private const val KEY_AI_AUTO_TAG_ENABLED = "ai_auto_tag_enabled"
    private const val KEY_DEFAULT_AI_TAGS_SEEDED = "default_ai_tags_seeded_v1"
    private const val KEY_DEFAULT_APP_CAT_SEEDED = "default_app_cat_seeded_v1"
    private const val KEY_DEFAULT_AI_RULES_SEEDED = "default_ai_rules_seeded_v1"
    private const val KEY_QUIET_HOURS_ENABLED = "quiet_hours_enabled"
    private const val KEY_QUIET_START_MIN = "quiet_hours_start_minute"
    private const val KEY_QUIET_END_MIN = "quiet_hours_end_minute"
    private const val KEY_DAILY_SUMMARY_NOTIF = "daily_summary_notif_enabled"
    private const val KEY_VIP_SENDERS_MAP = "vip_senders_per_app"
    private const val KEY_COLLAPSED_CARDS = "settings_collapsed_cards"
    private const val KEY_TOKEN_SAVER_ENABLED = "token_saver_enabled"
    private const val KEY_PERSISTENT_NOTIF_ICONS = "persistent_notif_show_icons"
    private const val KEY_PERSISTENT_NOTIF_STYLE = "persistent_notif_style"
    private const val KEY_SWIPE_DELETE_ENABLED = "swipe_delete_enabled"
    private const val KEY_HAPTIC_ENABLED = "haptic_feedback_enabled"
    private const val KEY_GLASS_EFFECT_ENABLED = "glass_effect_enabled"
    private const val KEY_CHAT_SUGGESTIONS_ENABLED = "chat_suggestions_enabled"
    private const val KEY_CHAT_RAG_MESSAGE_LIMIT = "chat_rag_message_limit"

    const val MAX_ALERT_KEYWORDS = 20
    const val DEFAULT_CHAT_RAG_MESSAGE_LIMIT = 400
    const val MIN_CHAT_RAG_MESSAGE_LIMIT = 50
    const val MAX_CHAT_RAG_MESSAGE_LIMIT = 800
    const val TOKEN_SAVER_CHAT_RAG_LIMIT = 100

    private val gson = Gson()

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 是否記錄持續性通知（預設關閉） */
    fun isRecordOngoingEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_RECORD_ONGOING, false)

    fun setRecordOngoingEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_RECORD_ONGOING, enabled).apply()
    }

    /** 是否顯示通知欄常駐統計通知（預設開啟） */
    fun isPersistentStatsEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_PERSISTENT_STATS, true)

    fun setPersistentStatsEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PERSISTENT_STATS, enabled).apply()
    }

    /** 我的最愛 App 清單（依釘選順序保存 packageName） */
    fun getFavoriteApps(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_FAVORITE_APPS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun isFavoriteApp(ctx: Context, packageName: String): Boolean =
        packageName in getFavoriteApps(ctx)

    /** 釘選或取消釘選我的最愛 App，回傳更新後是否為最愛 */
    fun toggleFavoriteApp(ctx: Context, packageName: String): Boolean {
        val current = getFavoriteApps(ctx).toMutableList()
        val added = if (packageName in current) {
            current.remove(packageName)
            false
        } else {
            current.add(packageName)
            true
        }
        prefs(ctx).edit().putString(KEY_FAVORITE_APPS, gson.toJson(current)).apply()
        return added
    }

    /** App 靜音清單（packageName 集合） */
    fun getMutedApps(ctx: Context): Set<String> =
        prefs(ctx).getStringSet(KEY_MUTED_APPS, emptySet())?.toSet() ?: emptySet()

    fun isAppMuted(ctx: Context, packageName: String): Boolean =
        packageName in getMutedApps(ctx)

    fun setAppMuted(ctx: Context, packageName: String, muted: Boolean, mode: MuteMode? = null) {
        val updated = getMutedApps(ctx).toMutableSet()
        if (muted) updated.add(packageName) else updated.remove(packageName)
        prefs(ctx).edit().putStringSet(KEY_MUTED_APPS, updated).apply()
        if (muted && mode != null) {
            setAppMuteMode(ctx, packageName, mode)
        }
    }

    fun getAppMuteModeMap(ctx: Context): Map<String, String> {
        val raw = prefs(ctx).getString(KEY_MUTE_MODES, null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson<Map<String, String>>(raw, type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun getAppMuteMode(ctx: Context, packageName: String): MuteMode =
        MuteMode.fromPref(getAppMuteModeMap(ctx)[packageName])

    fun setAppMuteMode(ctx: Context, packageName: String, mode: MuteMode) {
        val map = getAppMuteModeMap(ctx).toMutableMap()
        map[packageName] = mode.prefValue
        prefs(ctx).edit().putString(KEY_MUTE_MODES, gson.toJson(map)).apply()
    }

    /** 詐騙警示彈出通知開關（預設開） */
    fun isScamAlertNotifEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SCAM_ALERT_NOTIF, true)

    fun setScamAlertNotifEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SCAM_ALERT_NOTIF, enabled).apply()
    }

    /** AI 風險分析開關（預設開） */
    fun isAiRiskEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AI_RISK_ENABLED, true)

    fun setAiRiskEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AI_RISK_ENABLED, enabled).apply()
    }

    /** 每日 AI 分析上限（預設 50；0 = 不限） */
    fun getDailyAiLimit(ctx: Context): Int =
        prefs(ctx).getInt(KEY_DAILY_AI_LIMIT, 50)

    fun setDailyAiLimit(ctx: Context, limit: Int) {
        prefs(ctx).edit().putInt(KEY_DAILY_AI_LIMIT, limit.coerceAtLeast(0)).apply()
    }

    private fun todayDateStr(): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())

    @Synchronized
    fun getTodayAiUsage(ctx: Context): Int {
        val p = prefs(ctx)
        val today = todayDateStr()
        return if (p.getString(KEY_AI_USAGE_DATE, "") == today) {
            p.getInt(KEY_AI_USAGE_COUNT, 0)
        } else {
            0
        }
    }

    /** 檢查今日 AI 額度並遞增計數；若超過上限回傳 false */
    @Synchronized
    fun canUseAiTodayAndIncrement(ctx: Context): Boolean {
        val limit = getDailyAiLimit(ctx)
        val p = prefs(ctx)
        val today = todayDateStr()
        val storedDate = p.getString(KEY_AI_USAGE_DATE, "")
        val currentCount = if (storedDate == today) p.getInt(KEY_AI_USAGE_COUNT, 0) else 0
        if (limit > 0 && currentCount >= limit) {
            return false
        }
        p.edit()
            .putString(KEY_AI_USAGE_DATE, today)
            .putInt(KEY_AI_USAGE_COUNT, currentCount + 1)
            .apply()
        return true
    }

    /** 若因 HTTP 413 等請求超限未實際完成分析，退還今日已扣減之 1 次額度 */
    @Synchronized
    fun refundTodayAiUsage(ctx: Context) {
        val p = prefs(ctx)
        val today = todayDateStr()
        if (p.getString(KEY_AI_USAGE_DATE, "") == today) {
            val currentCount = p.getInt(KEY_AI_USAGE_COUNT, 0)
            if (currentCount > 0) {
                p.edit().putInt(KEY_AI_USAGE_COUNT, currentCount - 1).apply()
            }
        }
    }

    /** 每日 AI 摘要開關與排程時間（預設開、22:00） */
    fun isDailySummaryEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DAILY_SUMMARY_ENABLED, true)

    fun setDailySummaryEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DAILY_SUMMARY_ENABLED, enabled).apply()
    }

    fun getDailySummaryHour(ctx: Context): Int =
        prefs(ctx).getInt(KEY_DAILY_SUMMARY_HOUR, 22).coerceIn(0, 23)

    fun getDailySummaryMinute(ctx: Context): Int =
        prefs(ctx).getInt(KEY_DAILY_SUMMARY_MINUTE, 0).coerceIn(0, 59)

    fun setDailySummaryTime(ctx: Context, hour: Int, minute: Int) {
        prefs(ctx).edit()
            .putInt(KEY_DAILY_SUMMARY_HOUR, hour.coerceIn(0, 23))
            .putInt(KEY_DAILY_SUMMARY_MINUTE, minute.coerceIn(0, 59))
            .apply()
    }

    /** 通知點擊行為（預設開啟幻訊） */
    fun getNotifClickBehavior(ctx: Context): NotifClickBehavior =
        NotifClickBehavior.fromPref(prefs(ctx).getString(KEY_NOTIF_CLICK_BEHAVIOR, null))

    fun setNotifClickBehavior(ctx: Context, behavior: NotifClickBehavior) {
        prefs(ctx).edit().putString(KEY_NOTIF_CLICK_BEHAVIOR, behavior.prefValue).apply()
    }

    /** 同 sbn.key 更新節流秒數（預設 60，0 = 關閉） */
    fun getSameKeyThrottleSeconds(ctx: Context): Int =
        prefs(ctx).getInt(KEY_SAME_KEY_THROTTLE_SEC, 60).coerceIn(0, 300)

    fun setSameKeyThrottleSeconds(ctx: Context, seconds: Int) {
        prefs(ctx).edit().putInt(KEY_SAME_KEY_THROTTLE_SEC, seconds.coerceIn(0, 300)).apply()
    }

    /** 數字微變視為同一則更新（預設開，抑制監控類通知每分鐘數字跳動洗版） */
    fun isSameKeyDigitVarianceThrottleEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SAME_KEY_DIGIT_VARIANCE_THROTTLE, true)

    fun setSameKeyDigitVarianceThrottleEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SAME_KEY_DIGIT_VARIANCE_THROTTLE, enabled).apply()
    }

    private const val APP_THROTTLE_CACHE_TTL_MS = 30_000L

    @Volatile private var cachedAppThrottleMap: Map<String, Int>? = null
    @Volatile private var cachedAppThrottleMapAt: Long = 0L

    /** Per-app 同 key 節流覆寫表（0 = 永不節流，1..300 = 自訂秒數） */
    fun getAppSameKeyThrottleMap(ctx: Context): Map<String, Int> {
        val now = System.currentTimeMillis()
        val cached = cachedAppThrottleMap
        if (cached != null && (now - cachedAppThrottleMapAt) < APP_THROTTLE_CACHE_TTL_MS) {
            return cached
        }
        val raw = prefs(ctx).getString(KEY_APP_SAME_KEY_THROTTLE_MAP, null)
        val parsed = if (raw == null) {
            emptyMap()
        } else {
            try {
                val type = object : TypeToken<Map<String, Int>>() {}.type
                gson.fromJson<Map<String, Int>>(raw, type) ?: emptyMap()
            } catch (_: Exception) {
                emptyMap()
            }
        }
        cachedAppThrottleMap = parsed
        cachedAppThrottleMapAt = now
        return parsed
    }

    fun setAppSameKeyThrottle(ctx: Context, packageName: String, seconds: Int?) {
        val map = getAppSameKeyThrottleMap(ctx).toMutableMap()
        if (seconds == null) {
            map.remove(packageName)
        } else {
            map[packageName] = seconds.coerceIn(0, 300)
        }
        cachedAppThrottleMap = map
        cachedAppThrottleMapAt = System.currentTimeMillis()
        prefs(ctx).edit().putString(KEY_APP_SAME_KEY_THROTTLE_MAP, gson.toJson(map)).apply()
    }

    fun getEffectiveSameKeyThrottleSeconds(ctx: Context, packageName: String): Int {
        val override = getAppSameKeyThrottleMap(ctx)[packageName]
        return override?.coerceIn(0, 300) ?: getSameKeyThrottleSeconds(ctx)
    }

    /** 訊息保留則數上限（-1 = 不限，1000 / 5000） */
    fun getRetentionMaxCount(ctx: Context): Int =
        prefs(ctx).getInt(KEY_RETENTION_MAX_COUNT, -1)

    fun setRetentionMaxCount(ctx: Context, maxCount: Int) {
        prefs(ctx).edit().putInt(KEY_RETENTION_MAX_COUNT, maxCount).apply()
    }

    /** Material You 動態色（Android 12+，預設關閉） */
    fun isDynamicColorEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DYNAMIC_COLOR, false)

    fun setDynamicColorEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
    }

    /** 字體大小設定 */
    fun getFontScaleMode(ctx: Context): FontScaleMode =
        FontScaleMode.fromPref(prefs(ctx).getString(KEY_FONT_SCALE, null))

    fun setFontScaleMode(ctx: Context, mode: FontScaleMode) {
        prefs(ctx).edit().putString(KEY_FONT_SCALE, mode.prefValue).apply()
    }

    /** 即時提醒關鍵字清單（最多 20 組，獨立於過濾規則） */
    fun getAlertKeywords(ctx: Context): List<String> {
        val raw = prefs(ctx).getString(KEY_ALERT_KEYWORDS, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @Volatile var onRuleCachesInvalidated: (() -> Unit)? = null

    fun addAlertKeyword(ctx: Context, keyword: String): Boolean {
        val trimmed = keyword.trim()
        if (trimmed.isEmpty()) return false
        val current = getAlertKeywords(ctx).toMutableList()
        if (current.size >= MAX_ALERT_KEYWORDS) return false
        if (current.any { it.equals(trimmed, ignoreCase = true) }) return false
        current.add(trimmed)
        prefs(ctx).edit().putString(KEY_ALERT_KEYWORDS, gson.toJson(current)).apply()
        onRuleCachesInvalidated?.invoke()
        return true
    }

    fun removeAlertKeyword(ctx: Context, keyword: String) {
        val updated = getAlertKeywords(ctx).filterNot { it.equals(keyword, ignoreCase = true) }
        prefs(ctx).edit().putString(KEY_ALERT_KEYWORDS, gson.toJson(updated)).apply()
        onRuleCachesInvalidated?.invoke()
    }

    /** 新手引導 v1 完成旗標 */
    fun isOnboardingCompleted(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ONBOARDING_COMPLETED_V1, false)

    fun setOnboardingCompleted(ctx: Context, completed: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ONBOARDING_COMPLETED_V1, completed).apply()
    }

    /** 設定頁高亮教學是否已完成 */
    fun isSettingsTourCompleted(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SETTINGS_TOUR_COMPLETED_V1, false)

    fun setSettingsTourCompleted(ctx: Context, completed: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SETTINGS_TOUR_COMPLETED_V1, completed).apply()
    }

    /** AI 智慧標記開關（預設開） */
    fun isAiAutoTagEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_AI_AUTO_TAG_ENABLED, true)

    fun setAiAutoTagEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_AI_AUTO_TAG_ENABLED, enabled).apply()
    }

    /** 預設 AI 標籤是否已建立 */
    fun isDefaultAiTagsSeeded(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DEFAULT_AI_TAGS_SEEDED, false)

    fun setDefaultAiTagsSeeded(ctx: Context, seeded: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DEFAULT_AI_TAGS_SEEDED, seeded).apply()
    }

    /** 勿擾時段設定（預設關閉，23:00 - 07:00） */
    fun isQuietHoursEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_QUIET_HOURS_ENABLED, false)

    fun setQuietHoursEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_QUIET_HOURS_ENABLED, enabled).apply()
    }

    fun getQuietHoursStartMinute(ctx: Context): Int =
        prefs(ctx).getInt(KEY_QUIET_START_MIN, 23 * 60).coerceIn(0, 23 * 60 + 59)

    fun getQuietHoursEndMinute(ctx: Context): Int =
        prefs(ctx).getInt(KEY_QUIET_END_MIN, 7 * 60).coerceIn(0, 23 * 60 + 59)

    fun setQuietHoursRange(ctx: Context, startMinute: Int, endMinute: Int) {
        prefs(ctx).edit()
            .putInt(KEY_QUIET_START_MIN, startMinute.coerceIn(0, 23 * 60 + 59))
            .putInt(KEY_QUIET_END_MIN, endMinute.coerceIn(0, 23 * 60 + 59))
            .apply()
    }

    fun isInQuietHoursNow(ctx: Context): Boolean {
        if (!isQuietHoursEnabled(ctx)) return false
        val start = getQuietHoursStartMinute(ctx)
        val end = getQuietHoursEndMinute(ctx)
        val cal = java.util.Calendar.getInstance()
        val nowMin = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        return if (start <= end) {
            nowMin in start..end
        } else {
            nowMin >= start || nowMin <= end
        }
    }

    /** 每日摘要生成後推播通知開關（預設開） */
    fun isDailySummaryNotifEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DAILY_SUMMARY_NOTIF, true)

    fun setDailySummaryNotifEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DAILY_SUMMARY_NOTIF, enabled).apply()
    }

    /** VIP 寄件者（per-app sender set） */
    private fun getVipSendersMap(ctx: Context): MutableMap<String, List<String>> {
        val raw = prefs(ctx).getString(KEY_VIP_SENDERS_MAP, null) ?: return mutableMapOf()
        return try {
            val type = object : TypeToken<MutableMap<String, List<String>>>() {}.type
            gson.fromJson<MutableMap<String, List<String>>>(raw, type) ?: mutableMapOf()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun getVipSenders(ctx: Context, packageName: String): Set<String> =
        getVipSendersMap(ctx)[packageName]?.toSet() ?: emptySet()

    fun toggleVipSender(ctx: Context, packageName: String, sender: String): Boolean {
        val map = getVipSendersMap(ctx)
        val list = map[packageName]?.toMutableList() ?: mutableListOf()
        val pinned = if (sender in list) {
            list.remove(sender)
            false
        } else {
            list.add(sender)
            true
        }
        if (list.isEmpty()) map.remove(packageName) else map[packageName] = list
        prefs(ctx).edit().putString(KEY_VIP_SENDERS_MAP, gson.toJson(map)).apply()
        return pinned
    }

    /** 設定頁卡片收合狀態（預設全部展開） */
    fun isSettingsCardExpanded(ctx: Context, cardKey: String): Boolean {
        val collapsed = prefs(ctx).getStringSet(KEY_COLLAPSED_CARDS, emptySet()) ?: emptySet()
        return cardKey !in collapsed
    }

    fun setSettingsCardExpanded(ctx: Context, cardKey: String, expanded: Boolean) {
        val collapsed = (prefs(ctx).getStringSet(KEY_COLLAPSED_CARDS, emptySet()) ?: emptySet()).toMutableSet()
        if (expanded) collapsed.remove(cardKey) else collapsed.add(cardKey)
        prefs(ctx).edit().putStringSet(KEY_COLLAPSED_CARDS, collapsed).apply()
    }

    /** 新手引導步驟索引（1~10），中斷可續 */
    fun getOnboardingStep(ctx: Context): Int =
        prefs(ctx).getInt(KEY_ONBOARDING_STEP_V2, 1).coerceIn(1, 10)

    fun setOnboardingStep(ctx: Context, stepIndex: Int) {
        prefs(ctx).edit().putInt(KEY_ONBOARDING_STEP_V2, stepIndex.coerceIn(1, 10)).apply()
    }

    /** OEM 自啟動手動確認（誠實標記：無法程式檢查，由使用者手動確認） */
    fun isOemAutostartConfirmed(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_OEM_AUTOSTART_CONFIRMED, false)

    fun setOemAutostartConfirmed(ctx: Context, confirmed: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_OEM_AUTOSTART_CONFIRMED, confirmed).apply()
    }

    /** 預設 App 分類標籤與規則是否已建立 */
    fun isDefaultAppCatSeeded(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DEFAULT_APP_CAT_SEEDED, false)

    fun setDefaultAppCatSeeded(ctx: Context, seeded: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DEFAULT_APP_CAT_SEEDED, seeded).apply()
    }

    /** 預設自然語言 AI 規則是否已建立 */
    fun isDefaultAiRulesSeeded(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DEFAULT_AI_RULES_SEEDED, false)

    fun setDefaultAiRulesSeeded(ctx: Context, seeded: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DEFAULT_AI_RULES_SEEDED, seeded).apply()
    }

    /** 省 token 模式（override 層，不覆寫原開關值，預設關閉） */
    fun isTokenSaverEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_TOKEN_SAVER_ENABLED, false)

    fun setTokenSaverEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_TOKEN_SAVER_ENABLED, enabled).apply()
    }

    fun isEffectiveAiAutoTagEnabled(ctx: Context): Boolean =
        !isTokenSaverEnabled(ctx) && isAiAutoTagEnabled(ctx)

    fun isEffectiveDailySummaryEnabled(ctx: Context): Boolean =
        !isTokenSaverEnabled(ctx) && isDailySummaryEnabled(ctx)

    fun isEffectiveDailySummaryNotifEnabled(ctx: Context): Boolean =
        !isTokenSaverEnabled(ctx) && isDailySummaryNotifEnabled(ctx)

    /** AI 助手單次閱讀訊息上限（預設 400，範圍 50–800） */
    fun getChatRagMessageLimit(ctx: Context): Int =
        prefs(ctx).getInt(KEY_CHAT_RAG_MESSAGE_LIMIT, DEFAULT_CHAT_RAG_MESSAGE_LIMIT)
            .coerceIn(MIN_CHAT_RAG_MESSAGE_LIMIT, MAX_CHAT_RAG_MESSAGE_LIMIT)

    fun setChatRagMessageLimit(ctx: Context, limit: Int) {
        val clamped = limit.coerceIn(MIN_CHAT_RAG_MESSAGE_LIMIT, MAX_CHAT_RAG_MESSAGE_LIMIT)
        prefs(ctx).edit().putInt(KEY_CHAT_RAG_MESSAGE_LIMIT, clamped).apply()
    }

    /** 實際生效的 AI 助手單次閱讀上限（省 token 模式開啟時覆寫為 100，不改動使用者設定值） */
    fun getEffectiveChatRagMessageLimit(ctx: Context): Int =
        if (isTokenSaverEnabled(ctx)) TOKEN_SAVER_CHAT_RAG_LIMIT else getChatRagMessageLimit(ctx)

    /** 常駐通知顯示 App 圖示條（預設開） */
    fun isPersistentNotifShowIcons(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_PERSISTENT_NOTIF_ICONS, true)

    fun setPersistentNotifShowIcons(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PERSISTENT_NOTIF_ICONS, enabled).apply()
    }

    /** 常駐通知樣式（詳細 / 精簡） */
    fun getPersistentNotifStyle(ctx: Context): PersistentNotifStyle =
        PersistentNotifStyle.fromPref(prefs(ctx).getString(KEY_PERSISTENT_NOTIF_STYLE, null))

    fun setPersistentNotifStyle(ctx: Context, style: PersistentNotifStyle) {
        prefs(ctx).edit().putString(KEY_PERSISTENT_NOTIF_STYLE, style.prefValue).apply()
    }

    /** 滑動刪除開關（預設開） */
    fun isSwipeToDeleteEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_SWIPE_DELETE_ENABLED, true)

    fun setSwipeToDeleteEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_SWIPE_DELETE_ENABLED, enabled).apply()
    }

    /** 觸覺回饋開關（預設開） */
    fun isHapticFeedbackEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_HAPTIC_ENABLED, true)

    fun setHapticFeedbackEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_HAPTIC_ENABLED, enabled).apply()
    }

    /** 玻璃效果開關（預設開；關閉時全 App 卡片改純色半透明背景省 GPU） */
    fun isGlassEffectEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_GLASS_EFFECT_ENABLED, true)

    fun setGlassEffectEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_GLASS_EFFECT_ENABLED, enabled).apply()
    }

    /** AI 助手建議問題開關（預設開） */
    fun isChatSuggestionsEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CHAT_SUGGESTIONS_ENABLED, true)

    fun setChatSuggestionsEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CHAT_SUGGESTIONS_ENABLED, enabled).apply()
    }

    data class QuickChatCommand(
        val id: String = java.util.UUID.randomUUID().toString(),
        val label: String,
        val prompt: String,
        val mode: String = MODE_DIRECT
    ) {
        companion object {
            const val MODE_DIRECT = "direct"
            const val MODE_PREFILL = "prefill"
        }
    }

    private fun defaultQuickChatCommands(): List<QuickChatCommand> = listOf(
        QuickChatCommand("default_yesterday", "昨日簡報", "請幫我整理昨日簡報", QuickChatCommand.MODE_DIRECT),
        QuickChatCommand("default_must_see", "今日必看訊息", "今天有哪些必看訊息？", QuickChatCommand.MODE_DIRECT),
        QuickChatCommand("default_scam_risk", "最可能的詐騙風險", "最近最可能的詐騙風險是什麼？", QuickChatCommand.MODE_DIRECT)
    )

    fun getQuickCommandDefaultMode(ctx: Context): String {
        val raw = prefs(ctx).getString("quick_command_default_mode", QuickChatCommand.MODE_DIRECT)
        return if (raw == QuickChatCommand.MODE_PREFILL) QuickChatCommand.MODE_PREFILL else QuickChatCommand.MODE_DIRECT
    }

    fun setQuickCommandDefaultMode(ctx: Context, mode: String) {
        val norm = if (mode == QuickChatCommand.MODE_PREFILL) QuickChatCommand.MODE_PREFILL else QuickChatCommand.MODE_DIRECT
        prefs(ctx).edit().putString("quick_command_default_mode", norm).apply()
    }

    fun getQuickChatCommands(ctx: Context): List<QuickChatCommand> {
        val raw = prefs(ctx).getString("quick_chat_commands_v2", null)
        if (raw.isNullOrBlank()) return defaultQuickChatCommands()
        return try {
            val type = object : TypeToken<List<QuickChatCommand>>() {}.type
            val list = gson.fromJson<List<QuickChatCommand>>(raw, type).orEmpty()
                .filter { it.label.isNotBlank() && it.prompt.isNotBlank() }
            if (list.isEmpty()) defaultQuickChatCommands() else list
        } catch (_: Exception) {
            defaultQuickChatCommands()
        }
    }

    fun saveQuickChatCommands(ctx: Context, commands: List<QuickChatCommand>) {
        prefs(ctx).edit().putString("quick_chat_commands_v2", gson.toJson(commands)).apply()
    }

    fun addQuickChatCommand(ctx: Context, label: String, prompt: String, mode: String): List<QuickChatCommand> {
        val current = getQuickChatCommands(ctx).toMutableList()
        val normMode = if (mode == QuickChatCommand.MODE_PREFILL) QuickChatCommand.MODE_PREFILL else QuickChatCommand.MODE_DIRECT
        current.add(QuickChatCommand(label = label.trim(), prompt = prompt.trim(), mode = normMode))
        saveQuickChatCommands(ctx, current)
        return current
    }

    fun deleteQuickChatCommand(ctx: Context, id: String): List<QuickChatCommand> {
        val updated = getQuickChatCommands(ctx).filterNot { it.id == id }
        saveQuickChatCommands(ctx, updated)
        return updated
    }

    /** Shizuku 跨行程看門狗開關（預設開，需 Shizuku 已授權才生效） */
    fun isShizukuWatchdogEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("shizuku_watchdog_enabled", true)

    fun setShizukuWatchdogEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("shizuku_watchdog_enabled", enabled).apply()
    }

    /** Shizuku 已凍結（pm suspend）App 清單，供使用者在設定頁一鍵解除凍結 */
    fun getShizukuSuspendedApps(ctx: Context): List<String> {
        val raw = prefs(ctx).getString("shizuku_suspended_apps", null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<String>>() {}.type
            gson.fromJson<List<String>>(raw, type)?.distinct() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addShizukuSuspendedApp(ctx: Context, packageName: String) {
        val current = getShizukuSuspendedApps(ctx).toMutableList()
        if (packageName !in current) {
            current.add(packageName)
            prefs(ctx).edit().putString("shizuku_suspended_apps", gson.toJson(current)).apply()
        }
    }

    fun removeShizukuSuspendedApp(ctx: Context, packageName: String) {
        val current = getShizukuSuspendedApps(ctx).filter { it != packageName }
        prefs(ctx).edit().putString("shizuku_suspended_apps", gson.toJson(current)).apply()
    }

    /** 雲端防詐情報總開關（預設開） */
    fun isIntelEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("intel_enabled", true)

    fun setIntelEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("intel_enabled", enabled).apply()
    }

    /** 防詐雷達 fraud.tw / 165 開關（預設開） */
    fun isIntelFraudTwEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("intel_fraud_tw_enabled", true)

    fun setIntelFraudTwEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("intel_fraud_tw_enabled", enabled).apply()
    }

    /** Google Safe Browsing v4 開關（預設開） */
    fun isIntelSafeBrowsingEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("intel_safe_browsing_enabled", true)

    fun setIntelSafeBrowsingEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("intel_safe_browsing_enabled", enabled).apply()
    }

    /** PhishTank 開關（可選，預設關） */
    fun isIntelPhishTankEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("intel_phishtank_enabled", false)

    fun setIntelPhishTankEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("intel_phishtank_enabled", enabled).apply()
    }

    /** Chainabuse 鏈上地址查詢開關（可選，預設關） */
    fun isIntelChainabuseEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("intel_chainabuse_enabled", false)

    fun setIntelChainabuseEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("intel_chainabuse_enabled", enabled).apply()
    }

    /** 摘要卡預設是否收合（預設 true = 收合） */
    fun isSummaryDefaultCollapsed(ctx: Context): Boolean =
        prefs(ctx).getBoolean("summary_default_collapsed", true)

    fun setSummaryDefaultCollapsed(ctx: Context, collapsed: Boolean) {
        prefs(ctx).edit()
            .putBoolean("summary_default_collapsed", collapsed)
            .putBoolean("daily_summary_card_expanded", !collapsed)
            .putBoolean("weekly_summary_card_expanded", !collapsed)
            .apply()
    }

    /** 今日 AI 摘要卡是否展開（預設依摘要預設收合設定） */
    fun isDailySummaryExpanded(ctx: Context): Boolean =
        prefs(ctx).getBoolean("daily_summary_card_expanded", !isSummaryDefaultCollapsed(ctx))

    fun setDailySummaryExpanded(ctx: Context, expanded: Boolean) {
        prefs(ctx).edit().putBoolean("daily_summary_card_expanded", expanded).apply()
    }

    /** 本週 AI 摘要卡是否展開（預設依摘要預設收合設定） */
    fun isWeeklySummaryExpanded(ctx: Context): Boolean =
        prefs(ctx).getBoolean("weekly_summary_card_expanded", !isSummaryDefaultCollapsed(ctx))

    fun setWeeklySummaryExpanded(ctx: Context, expanded: Boolean) {
        prefs(ctx).edit().putBoolean("weekly_summary_card_expanded", expanded).apply()
    }

    /** 卡片密度（舒適 / 緊湊） */
    fun getCardDensity(ctx: Context): CardDensity =
        CardDensity.fromPref(prefs(ctx).getString("card_density_mode", CardDensity.COMFORTABLE.prefValue))

    fun setCardDensity(ctx: Context, density: CardDensity) {
        prefs(ctx).edit().putString("card_density_mode", density.prefValue).apply()
    }

    /** 首頁 App 清單排序（時間新→舊 / 未讀多→少 / 風險高→低） */
    fun getHomeSortOrder(ctx: Context): HomeSortOrder =
        HomeSortOrder.fromPref(prefs(ctx).getString("home_sort_order", HomeSortOrder.RECENT.prefValue))

    fun setHomeSortOrder(ctx: Context, order: HomeSortOrder) {
        prefs(ctx).edit().putString("home_sort_order", order.prefValue).apply()
    }

    val ALL_OPTIONAL_HOME_FILTERS: Set<String> = setOf("starred", "scams", "filtered", "tag")

    /** 首頁篩選欄自訂顯示項目（全部與未讀固定顯示，此清單控制 星標/詐騙/已過濾/標籤） */
    fun getVisibleHomeFilters(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("visible_home_filters", ALL_OPTIONAL_HOME_FILTERS)
            ?: ALL_OPTIONAL_HOME_FILTERS

    fun setVisibleHomeFilters(ctx: Context, filters: Set<String>) {
        prefs(ctx).edit().putStringSet("visible_home_filters", filters.toSet()).apply()
    }

    /** 是否使用 24 小時制（預設 true） */
    fun is24HourFormat(ctx: Context): Boolean =
        prefs(ctx).getBoolean("time_format_24h", true)

    fun set24HourFormat(ctx: Context, use24h: Boolean) {
        prefs(ctx).edit().putBoolean("time_format_24h", use24h).apply()
    }

    /** 依 12/24 小時制格式化訊息時間 */
    fun formatMessageTime(ctx: Context, timestamp: Long, includeDate: Boolean = false): String {
        val use24h = is24HourFormat(ctx)
        val pattern = when {
            includeDate && use24h -> "MM/dd HH:mm"
            includeDate && !use24h -> "MM/dd a hh:mm"
            use24h -> "HH:mm"
            else -> "a hh:mm"
        }
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }
}
