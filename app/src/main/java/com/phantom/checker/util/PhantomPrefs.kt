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

enum class AiPurpose(val id: String, val label: String, val catalogId: String) {
    CHAT("ai_chat", "AI 助手對話", "ai_model_chat"),
    MESSAGE_ANALYSIS("ai_message_analysis", "每則訊息風險分析", "ai_model_message_analysis"),
    DAILY_SUMMARY("ai_daily_summary", "每日摘要", "ai_model_daily_summary"),
    WEEKLY_SUMMARY("ai_weekly_summary", "每週摘要", "ai_model_weekly_summary"),
    DEEP_ANALYSIS("ai_deep_analysis", "訊息深度分析", "ai_model_deep_analysis"),
    SETTINGS_SEARCH("ai_settings_search", "設定 AI 語意搜尋", "ai_model_settings_search"),
    AUTO_MEMO("ai_auto_memo", "自動備忘錄生成", "ai_model_auto_memo")
}

enum class TabBarDisplayMode(val prefValue: String, val label: String) {
    STANDARD("standard", "標準"),
    COMPACT("compact", "縮小"),
    AUTO_HIDE("auto_hide", "自動隱藏");

    companion object {
        fun fromPref(value: String?): TabBarDisplayMode =
            entries.firstOrNull { it.prefValue == value } ?: STANDARD
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
    private const val KEY_WHITELIST_COLLAPSED_GROUPS = "whitelist_collapsed_groups"
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
private const val KEY_EXPANDED_SUBCARDS = "settings_expanded_subcards"
    private const val KEY_TOKEN_SAVER_ENABLED = "token_saver_enabled"
    private const val KEY_PERSISTENT_NOTIF_ICONS = "persistent_notif_show_icons"
    private const val KEY_PERSISTENT_NOTIF_STYLE = "persistent_notif_style"
    private const val KEY_SWIPE_DELETE_ENABLED = "swipe_delete_enabled"
    private const val KEY_HAPTIC_ENABLED = "haptic_feedback_enabled"
    private const val KEY_GLASS_EFFECT_ENABLED = "glass_effect_enabled"
    private const val KEY_CHAT_SUGGESTIONS_ENABLED = "chat_suggestions_enabled"
    private const val KEY_CHAT_RAG_MESSAGE_LIMIT = "chat_rag_message_limit"
    private const val KEY_DUAL_MODEL_ROUTING_ENABLED = "ai_dual_model_routing_enabled"
    private const val KEY_DUAL_MODEL_ROUTING_MODEL = "ai_dual_model_routing_model"
    private const val KEY_CHAT_RAG_CACHE_PREFIX = "chat_rag_cache_v1_"
    private const val KEY_CARD_BG_MODE = "card_bg_mode"
    private const val KEY_CARD_BG_ALPHA = "card_bg_alpha_percent"

    const val MAX_ALERT_KEYWORDS = 20
    const val DEFAULT_CHAT_RAG_MESSAGE_LIMIT = 400
    const val MIN_CHAT_RAG_MESSAGE_LIMIT = 50
    const val MAX_CHAT_RAG_MESSAGE_LIMIT = 800
    const val TOKEN_SAVER_CHAT_RAG_LIMIT = 100
    const val DEFAULT_DUAL_MODEL_ROUTING_MODEL = "mistral-small-2603"

    enum class CardBgMode(val prefValue: String, val label: String) {
        FOLLOW_GLASS("follow_glass", "跟隨玻璃強度"),
        TRANSPARENT("transparent", "透明模式"),
        DARK("dark", "深色底");

        companion object {
            fun fromPref(raw: String?): CardBgMode =
                entries.firstOrNull { it.prefValue == raw } ?: FOLLOW_GLASS
        }
    }

    data class ChatRagSessionCache(
        val sessionId: Long,
        val summary: String,
        val lastMsgTimestamp: Long,
        val ragLimit: Int,
        val dateKey: Long
    )

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

    /** 白名單管理分組收合狀態（已收合的 packageName 集合，空字串代表全域規則） */
    fun getWhitelistCollapsedGroups(ctx: Context): MutableSet<String> =
        prefs(ctx).getStringSet(KEY_WHITELIST_COLLAPSED_GROUPS, emptySet())?.toMutableSet()
            ?: mutableSetOf()

    fun setWhitelistCollapsedGroups(ctx: Context, groups: Set<String>) {
        prefs(ctx).edit().putStringSet(KEY_WHITELIST_COLLAPSED_GROUPS, HashSet(groups)).apply()
    }

    private const val PREF_MAP_CACHE_TTL_MS = 30_000L
    private val prefCacheLock = Any()
    @Volatile private var cachedAppMuteModeMap: Map<String, String>? = null
    @Volatile private var cachedAppMuteModeMapAt: Long = 0L

    fun getAppMuteModeMap(ctx: Context): Map<String, String> {
        val now = System.currentTimeMillis()
        synchronized(prefCacheLock) {
            val cached = cachedAppMuteModeMap
            if (cached != null && (now - cachedAppMuteModeMapAt) < PREF_MAP_CACHE_TTL_MS) {
                return cached
            }
        }
        val raw = prefs(ctx).getString(KEY_MUTE_MODES, null)
        val parsed = if (raw == null) {
            emptyMap()
        } else {
            try {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson<Map<String, String>>(raw, type) ?: emptyMap()
            } catch (_: Exception) {
                emptyMap()
            }
        }
        synchronized(prefCacheLock) {
            cachedAppMuteModeMap = parsed
            cachedAppMuteModeMapAt = System.currentTimeMillis()
        }
        return parsed
    }

    fun getAppMuteMode(ctx: Context, packageName: String): MuteMode =
        MuteMode.fromPref(getAppMuteModeMap(ctx)[packageName])

    fun setAppMuteMode(ctx: Context, packageName: String, mode: MuteMode) {
        val map = getAppMuteModeMap(ctx).toMutableMap()
        map[packageName] = mode.prefValue
        val snapshot = map.toMap()
        synchronized(prefCacheLock) {
            cachedAppMuteModeMap = snapshot
            cachedAppMuteModeMapAt = System.currentTimeMillis()
        }
        prefs(ctx).edit().putString(KEY_MUTE_MODES, gson.toJson(snapshot)).apply()
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
    @Volatile private var cachedSameKeyThrottleSec: Int? = null
    @Volatile private var cachedSameKeyThrottleSecAt: Long = 0L

    fun getSameKeyThrottleSeconds(ctx: Context): Int {
        val now = System.currentTimeMillis()
        synchronized(prefCacheLock) {
            val cached = cachedSameKeyThrottleSec
            if (cached != null && (now - cachedSameKeyThrottleSecAt) < PREF_MAP_CACHE_TTL_MS) {
                return cached
            }
        }
        val value = prefs(ctx).getInt(KEY_SAME_KEY_THROTTLE_SEC, 60).coerceIn(0, 300)
        synchronized(prefCacheLock) {
            cachedSameKeyThrottleSec = value
            cachedSameKeyThrottleSecAt = System.currentTimeMillis()
        }
        return value
    }

    fun setSameKeyThrottleSeconds(ctx: Context, seconds: Int) {
        val clamped = seconds.coerceIn(0, 300)
        synchronized(prefCacheLock) {
            cachedSameKeyThrottleSec = clamped
            cachedSameKeyThrottleSecAt = System.currentTimeMillis()
        }
        prefs(ctx).edit().putInt(KEY_SAME_KEY_THROTTLE_SEC, clamped).apply()
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
        synchronized(prefCacheLock) {
            val cached = cachedAppThrottleMap
            if (cached != null && (now - cachedAppThrottleMapAt) < APP_THROTTLE_CACHE_TTL_MS) {
                return cached
            }
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
        synchronized(prefCacheLock) {
            cachedAppThrottleMap = parsed
            cachedAppThrottleMapAt = System.currentTimeMillis()
        }
        return parsed
    }

    fun setAppSameKeyThrottle(ctx: Context, packageName: String, seconds: Int?) {
        val map = getAppSameKeyThrottleMap(ctx).toMutableMap()
        if (seconds == null) {
            map.remove(packageName)
        } else {
            map[packageName] = seconds.coerceIn(0, 300)
        }
        val snapshot = map.toMap()
        synchronized(prefCacheLock) {
            cachedAppThrottleMap = snapshot
            cachedAppThrottleMapAt = System.currentTimeMillis()
        }
        prefs(ctx).edit().putString(KEY_APP_SAME_KEY_THROTTLE_MAP, gson.toJson(snapshot)).apply()
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

    fun getAllVipSenders(ctx: Context): Map<String, List<String>> =
        getVipSendersMap(ctx).toMap()

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

    /** 設定頁子區塊（個別 App 節流覆寫、自訂常用指令等）展開狀態：預設收合，獨立於大分類卡片收合，不互相重置 */
    fun isSettingsSubCardExpanded(ctx: Context, cardKey: String): Boolean {
        val expanded = prefs(ctx).getStringSet(KEY_EXPANDED_SUBCARDS, emptySet()) ?: emptySet()
        return cardKey in expanded
    }

    fun setSettingsSubCardExpanded(ctx: Context, cardKey: String, expanded: Boolean) {
        val expandedSet = (prefs(ctx).getStringSet(KEY_EXPANDED_SUBCARDS, emptySet()) ?: emptySet()).toMutableSet()
        if (expanded) expandedSet.add(cardKey) else expandedSet.remove(cardKey)
        prefs(ctx).edit().putStringSet(KEY_EXPANDED_SUBCARDS, expandedSet).apply()
    }

    /** 新手引導步驟索引（1~18），中斷可續 */
    fun getOnboardingStep(ctx: Context): Int =
        prefs(ctx).getInt(KEY_ONBOARDING_STEP_V2, 1).coerceIn(1, 18)

    fun setOnboardingStep(ctx: Context, stepIndex: Int) {
        prefs(ctx).edit().putInt(KEY_ONBOARDING_STEP_V2, stepIndex.coerceIn(1, 18)).apply()
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

    /** 雙模型信心路由開關（預設開啟：先以輕量模型判斷，30~70 分才升級主模型重判） */
    fun isDualModelRoutingEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_DUAL_MODEL_ROUTING_ENABLED, true)

    fun setDualModelRoutingEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_DUAL_MODEL_ROUTING_ENABLED, enabled).apply()
    }

    /** 雙模型信心路由第一關輕量模型名稱（預設 mistral-small-2603） */
    fun getDualModelRoutingModel(ctx: Context): String =
        prefs(ctx).getString(KEY_DUAL_MODEL_ROUTING_MODEL, DEFAULT_DUAL_MODEL_ROUTING_MODEL)
            ?.trim()
            ?.ifEmpty { DEFAULT_DUAL_MODEL_ROUTING_MODEL }
            ?: DEFAULT_DUAL_MODEL_ROUTING_MODEL

    fun setDualModelRoutingModel(ctx: Context, model: String) {
        val clean = model.trim().ifEmpty { DEFAULT_DUAL_MODEL_ROUTING_MODEL }
        prefs(ctx).edit().putString(KEY_DUAL_MODEL_ROUTING_MODEL, clean).apply()
    }

    /** 讀取指定對話 session 的 RAG 增量摘要快取 */
    fun getChatRagSessionCache(ctx: Context, sessionId: Long): ChatRagSessionCache? {
        val raw = prefs(ctx).getString("$KEY_CHAT_RAG_CACHE_PREFIX$sessionId", null) ?: return null
        return runCatching { gson.fromJson(raw, ChatRagSessionCache::class.java) }.getOrNull()
    }

    fun saveChatRagSessionCache(ctx: Context, cache: ChatRagSessionCache) {
        prefs(ctx).edit()
            .putString("$KEY_CHAT_RAG_CACHE_PREFIX${cache.sessionId}", gson.toJson(cache))
            .apply()
    }

    fun clearChatRagSessionCache(ctx: Context, sessionId: Long) {
        prefs(ctx).edit().remove("$KEY_CHAT_RAG_CACHE_PREFIX$sessionId").apply()
    }

    /** 卡片底色模式（跟隨玻璃強度 / 透明模式 / 深色底） */
    fun getCardBgMode(ctx: Context): CardBgMode =
        CardBgMode.fromPref(prefs(ctx).getString(KEY_CARD_BG_MODE, null))

    fun setCardBgMode(ctx: Context, mode: CardBgMode) {
        prefs(ctx).edit().putString(KEY_CARD_BG_MODE, mode.prefValue).apply()
    }

    /** 卡片底色透明模式不透明度（15–100%，預設 35%） */
    fun getCardBgAlphaPercent(ctx: Context): Int =
        prefs(ctx).getInt(KEY_CARD_BG_ALPHA, 35).coerceIn(15, 100)

    fun setCardBgAlphaPercent(ctx: Context, percent: Int) {
        prefs(ctx).edit().putInt(KEY_CARD_BG_ALPHA, percent.coerceIn(15, 100)).apply()
    }

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

    /** 底部分頁欄顯示模式（標準／縮小／自動隱藏，預設標準） */
    fun getTabBarDisplayMode(ctx: Context): TabBarDisplayMode =
        TabBarDisplayMode.fromPref(prefs(ctx).getString("tab_bar_display_mode", null))

    fun setTabBarDisplayMode(ctx: Context, mode: TabBarDisplayMode) {
        prefs(ctx).edit().putString("tab_bar_display_mode", mode.prefValue).apply()
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
        val mode: String = MODE_FOLLOW
    ) {
        companion object {
            const val MODE_DIRECT = "direct"
            const val MODE_PREFILL = "prefill"
            const val MODE_FOLLOW = "follow"
        }
    }

    private fun defaultQuickChatCommands(): List<QuickChatCommand> = listOf(
        QuickChatCommand("default_yesterday", "昨日簡報", "請幫我整理昨日簡報", QuickChatCommand.MODE_FOLLOW),
        QuickChatCommand("default_must_see", "今日必看訊息", "今天有哪些必看訊息？", QuickChatCommand.MODE_FOLLOW),
        QuickChatCommand("default_scam_risk", "最可能的詐騙風險", "最近最可能的詐騙風險是什麼？", QuickChatCommand.MODE_FOLLOW)
    )

    fun getQuickCommandDefaultMode(ctx: Context): String {
        val raw = prefs(ctx).getString("quick_command_default_mode", QuickChatCommand.MODE_DIRECT)
        return if (raw == QuickChatCommand.MODE_PREFILL) QuickChatCommand.MODE_PREFILL else QuickChatCommand.MODE_DIRECT
    }

    fun setQuickCommandDefaultMode(ctx: Context, mode: String) {
        val norm = if (mode == QuickChatCommand.MODE_PREFILL) QuickChatCommand.MODE_PREFILL else QuickChatCommand.MODE_DIRECT
        prefs(ctx).edit().putString("quick_command_default_mode", norm).apply()
    }

    fun resolveQuickCommandMode(cmdMode: String, defaultMode: String): String {
        return when (cmdMode) {
            QuickChatCommand.MODE_FOLLOW ->
                if (defaultMode == QuickChatCommand.MODE_PREFILL) QuickChatCommand.MODE_PREFILL else QuickChatCommand.MODE_DIRECT
            QuickChatCommand.MODE_PREFILL -> QuickChatCommand.MODE_PREFILL
            else -> QuickChatCommand.MODE_DIRECT
        }
    }

    fun resolveQuickCommandMode(ctx: Context, cmdMode: String): String =
        resolveQuickCommandMode(cmdMode, getQuickCommandDefaultMode(ctx))

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

    fun resetBuiltInQuickCommandsToFollow(ctx: Context): List<QuickChatCommand> {
        val updated = getQuickChatCommands(ctx).map {
            if (it.id.startsWith("default_")) it.copy(mode = QuickChatCommand.MODE_FOLLOW) else it
        }
        saveQuickChatCommands(ctx, updated)
        return updated
    }

    fun addQuickChatCommand(ctx: Context, label: String, prompt: String, mode: String): List<QuickChatCommand> {
        val current = getQuickChatCommands(ctx).toMutableList()
        val normMode = when (mode) {
            QuickChatCommand.MODE_PREFILL -> QuickChatCommand.MODE_PREFILL
            QuickChatCommand.MODE_FOLLOW -> QuickChatCommand.MODE_FOLLOW
            else -> QuickChatCommand.MODE_DIRECT
        }
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

    val ALL_OPTIONAL_HOME_FILTERS: Set<String> = setOf("starred", "scams", "filtered", "noted", "todoed", "tag")

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

    fun hasBackgroundImage(ctx: Context, layer: BackgroundLayer): Boolean =
        prefs(ctx).getBoolean("bg_enabled_${layer.name.lowercase()}", false)

    fun setHasBackgroundImage(ctx: Context, layer: BackgroundLayer, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("bg_enabled_${layer.name.lowercase()}", enabled).apply()
    }

    /** 背景暗色遮罩強度（-1 = 預設：日間 35%、夜間 60%；0..100 = 自訂百分比） */
    fun getBackgroundScrimPercent(ctx: Context): Int =
        prefs(ctx).getInt("bg_scrim_percent", -1).let { if (it < 0) -1 else it.coerceIn(0, 100) }

    fun setBackgroundScrimPercent(ctx: Context, percent: Int) {
        val v = if (percent < 0) -1 else percent.coerceIn(0, 100)
        prefs(ctx).edit().putInt("bg_scrim_percent", v).apply()
    }

    /** 玻璃卡片強度（0..100，預設 0 = 原版薄霧玻璃） */
    fun getGlassStrengthPercent(ctx: Context): Int =
        prefs(ctx).getInt("glass_strength_percent", 0).coerceIn(0, 100)

    fun setGlassStrengthPercent(ctx: Context, percent: Int) {
        prefs(ctx).edit().putInt("glass_strength_percent", percent.coerceIn(0, 100)).apply()
    }

    /** 新訊息 AI 分析觸發字數門檻（1..30，預設 10） */
    fun getAiMinTriggerLength(ctx: Context): Int =
        prefs(ctx).getInt("ai_min_trigger_length", 10).coerceIn(1, 30)

    fun setAiMinTriggerLength(ctx: Context, length: Int) {
        prefs(ctx).edit().putInt("ai_min_trigger_length", length.coerceIn(1, 30)).apply()
    }

    @Volatile private var cachedPurposeModelMap: Map<String, String>? = null

    fun getPurposeModelMap(ctx: Context): Map<String, String> {
        synchronized(prefCacheLock) {
            cachedPurposeModelMap?.let { return it }
        }
        val raw = prefs(ctx).getString("ai_purpose_model_map", null)
        val parsed = if (raw.isNullOrBlank()) {
            emptyMap()
        } else {
            runCatching {
                val type = object : TypeToken<Map<String, String>>() {}.type
                gson.fromJson<Map<String, String>>(raw, type) ?: emptyMap()
            }.getOrDefault(emptyMap())
        }
        synchronized(prefCacheLock) {
            cachedPurposeModelMap = parsed
        }
        return parsed
    }

    fun getPurposeProfileId(ctx: Context, purposeId: String): String? =
        getPurposeModelMap(ctx)[purposeId]?.takeIf { it.isNotBlank() }

    fun setPurposeProfileId(ctx: Context, purposeId: String, profileId: String?) {
        val next = getPurposeModelMap(ctx).toMutableMap()
        if (profileId.isNullOrBlank()) {
            next.remove(purposeId)
        } else {
            next[purposeId] = profileId
        }
        val frozen = next.toMap()
        synchronized(prefCacheLock) {
            cachedPurposeModelMap = frozen
        }
        prefs(ctx).edit().putString("ai_purpose_model_map", gson.toJson(frozen)).apply()
    }

    /** 離線 AI 分析自動補佇列開關（預設開） */
    fun isAiOfflineRetryEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("ai_offline_retry_enabled", true)

    fun setAiOfflineRetryEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("ai_offline_retry_enabled", enabled).apply()
    }

    /** 離線 AI 分析自動補佇列是否暫停（預設 false） */
    fun isAiOfflineRetryPaused(ctx: Context): Boolean =
        prefs(ctx).getBoolean("ai_offline_retry_paused", false)

    fun setAiOfflineRetryPaused(ctx: Context, paused: Boolean) {
        prefs(ctx).edit().putBoolean("ai_offline_retry_paused", paused).apply()
    }

    /** 自動備忘錄開關（預設關） */
    fun isAiAutoMemoEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("ai_auto_memo_enabled", false)

    fun setAiAutoMemoEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("ai_auto_memo_enabled", enabled).apply()
    }

    /** AI 標籤分發失敗通知開關（預設開） */
    fun isAiTagFailureNotifEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("ai_tag_failure_notif_enabled", true)

    fun setAiTagFailureNotifEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("ai_tag_failure_notif_enabled", enabled).apply()
    }

    /** 自動檢查更新開關（預設開） */
    fun isUpdateAutoCheckEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("update_auto_check_enabled", true)

    fun setUpdateAutoCheckEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("update_auto_check_enabled", enabled).apply()
    }

    fun getUpdateLastCheckDay(ctx: Context): String =
        prefs(ctx).getString("update_last_check_day", "").orEmpty()

    fun setUpdateLastCheckDay(ctx: Context, day: String) {
        prefs(ctx).edit().putString("update_last_check_day", day).apply()
    }

    fun getUpdateLastPromptKey(ctx: Context): String =
        prefs(ctx).getString("update_last_prompt_key", "").orEmpty()

    fun setUpdateLastPromptKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString("update_last_prompt_key", key).apply()
    }

    fun getUpdateLastNotifiedKey(ctx: Context): String =
        prefs(ctx).getString("update_last_notified_key", "").orEmpty()

    fun setUpdateLastNotifiedKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString("update_last_notified_key", key).apply()
    }

    /** 通知欄連續快速回覆開關（預設開） */
    fun isReplyProxyEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("reply_proxy_enabled", true)

    fun setReplyProxyEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("reply_proxy_enabled", enabled).apply()
    }

    /** 代理回覆通知閒置自動收起時間（分鐘：15 / 30 / 60 / 0=不自動收起，預設 30） */
    fun getReplyProxyTimeoutMinutes(ctx: Context): Int {
        val v = prefs(ctx).getInt("reply_proxy_timeout_minutes", 30)
        return if (v in setOf(0, 15, 30, 60)) v else 30
    }

    fun setReplyProxyTimeoutMinutes(ctx: Context, minutes: Int) {
        val safe = if (minutes in setOf(0, 15, 30, 60)) minutes else 30
        prefs(ctx).edit().putInt("reply_proxy_timeout_minutes", safe).apply()
    }

    const val AI_CHAT_TAB_NEW = "new"
    const val AI_CHAT_TAB_CONTINUE = "continue"

    /** 切換到 AI 助手分頁時的行為（"new" = 開啟新對話，"continue" = 接續上次對話，預設 "new"） */
    fun getAiChatTabBehavior(ctx: Context): String {
        val v = prefs(ctx).getString("ai_chat_tab_behavior", AI_CHAT_TAB_NEW)
        return if (v == AI_CHAT_TAB_CONTINUE) AI_CHAT_TAB_CONTINUE else AI_CHAT_TAB_NEW
    }

    fun setAiChatTabBehavior(ctx: Context, behavior: String) {
        val safe = if (behavior == AI_CHAT_TAB_CONTINUE) AI_CHAT_TAB_CONTINUE else AI_CHAT_TAB_NEW
        prefs(ctx).edit().putString("ai_chat_tab_behavior", safe).apply()
    }

    fun isAiChatTabContinue(ctx: Context): Boolean =
        getAiChatTabBehavior(ctx) == AI_CHAT_TAB_CONTINUE

    enum class HomeCardLayoutMode(val key: String, val label: String) {
        LIST("list", "一般條列"),
        CENTER("center", "中央堆疊")
    }

    private const val KEY_HOME_CARD_LAYOUT_MODE = "home_card_layout_mode"

    fun getHomeCardLayoutMode(ctx: Context): HomeCardLayoutMode {
        val p = prefs(ctx)
        val raw = p.getString(KEY_HOME_CARD_LAYOUT_MODE, null)
        if (raw != null) {
            return when (raw) {
                // 舊版 peek_top / peek_bottom 統一遷移至中央堆疊
                "peek_top", "peek_bottom" -> HomeCardLayoutMode.CENTER
                else -> HomeCardLayoutMode.entries.firstOrNull { it.key == raw } ?: HomeCardLayoutMode.LIST
            }
        }
        val legacyEnabled = p.getBoolean("card_stack_effect_enabled", false)
        val migrated = if (legacyEnabled) HomeCardLayoutMode.CENTER else HomeCardLayoutMode.LIST
        p.edit().putString(KEY_HOME_CARD_LAYOUT_MODE, migrated.key).remove("card_stack_effect_enabled").apply()
        return migrated
    }

    fun setHomeCardLayoutMode(ctx: Context, mode: HomeCardLayoutMode) {
        prefs(ctx).edit().putString(KEY_HOME_CARD_LAYOUT_MODE, mode.key).apply()
    }

    /** 記事分頁卡片排列模式（獨立於首頁，不互相衝突） */
    enum class NoteCardLayoutMode(val key: String, val label: String) {
        LIST("list", "一般條列"),
        PEEK_TOP("peek_top", "往下露頂部"),
        CENTER("center", "中央堆疊")
    }

    private const val KEY_NOTE_CARD_LAYOUT_MODE = "note_card_layout_mode"

    fun getNoteCardLayoutMode(ctx: Context): NoteCardLayoutMode {
        val raw = prefs(ctx).getString(KEY_NOTE_CARD_LAYOUT_MODE, null)
        return NoteCardLayoutMode.entries.firstOrNull { it.key == raw } ?: NoteCardLayoutMode.LIST
    }

    fun setNoteCardLayoutMode(ctx: Context, mode: NoteCardLayoutMode) {
        prefs(ctx).edit().putString(KEY_NOTE_CARD_LAYOUT_MODE, mode.key).apply()
    }

    /** 記事分頁待辦事項區塊預設收合（節省畫面空間） */
    private const val KEY_NOTES_TODO_COLLAPSED = "notes_todo_collapsed"

    fun isNotesTodoCollapsed(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_NOTES_TODO_COLLAPSED, true)

    fun setNotesTodoCollapsed(ctx: Context, collapsed: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_NOTES_TODO_COLLAPSED, collapsed).apply()
    }

    /** 首頁篩選標籤列收合狀態（避免標籤換行佔兩行） */
    private const val KEY_HOME_FILTER_CHIPS_COLLAPSED = "home_filter_chips_collapsed"

    fun isHomeFilterChipsCollapsed(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_HOME_FILTER_CHIPS_COLLAPSED, false)

    fun setHomeFilterChipsCollapsed(ctx: Context, collapsed: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_HOME_FILTER_CHIPS_COLLAPSED, collapsed).apply()
    }

    data class StatsCardOption(val key: String, val title: String, val subtitle: String, val defaultVisible: Boolean)

    val STATS_CARD_OPTIONS: List<StatsCardOption> = listOf(
        StatsCardOption("stats_summary", "每日 AI 摘要", "顯示今日 AI 重點摘要卡片", true),
        StatsCardOption("stats_weekly_bar", "本週 7 日訊息量", "顯示近 7 日每日訊息長條圖", true),
        StatsCardOption("stats_risk_dist", "風險分布三色", "顯示安全／疑似／詐騙計數卡", true),
        StatsCardOption("stats_hourly", "今日每小時分布", "顯示今日 24 小時訊息分布圖", true),
        StatsCardOption("stats_daily_top_apps", "今日 App 訊息量排行榜 Top 5", "顯示今日收到訊息最多的前五名 App", true),
        StatsCardOption("stats_ai_tags", "今日 AI 智慧標記", "顯示今日 AI 自動標記分類統計", true),
        StatsCardOption("stats_harassment", "騷擾佔比環形", "顯示行銷與垃圾通知佔比環形圖", true),
        StatsCardOption("stats_top_risky", "Top 5 高風險 App", "顯示累計疑似與詐騙訊息前五名 App", true),
        StatsCardOption("stats_risk_trend", "7 日風險趨勢折線", "顯示近 7 日每日疑似與詐騙訊息趨勢", false),
        StatsCardOption("stats_unread_ratio", "未讀 / 已讀比例環形", "顯示未讀與已讀訊息佔比環形圖", false),
        StatsCardOption("stats_app_share", "各 App 訊息佔比橫條前 8", "顯示各 App 歷史訊息量佔比排行", false),
        StatsCardOption("stats_todo_done", "待辦完成率環形", "顯示待辦事項已完成比例環形圖", false)
    )

    val DEFAULT_VISIBLE_STATS_CARDS: Set<String> =
        STATS_CARD_OPTIONS.filter { it.defaultVisible }.map { it.key }.toSet()

    fun getVisibleStatsCards(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("visible_stats_cards", DEFAULT_VISIBLE_STATS_CARDS)
            ?: DEFAULT_VISIBLE_STATS_CARDS

    fun setVisibleStatsCards(ctx: Context, cards: Set<String>) {
        prefs(ctx).edit().putStringSet("visible_stats_cards", cards.toSet()).apply()
    }

    fun isStatsCardVisible(ctx: Context, key: String): Boolean =
        key in getVisibleStatsCards(ctx)

    fun setStatsCardVisible(ctx: Context, key: String, visible: Boolean) {
        val current = getVisibleStatsCards(ctx).toMutableSet()
        if (visible) current.add(key) else current.remove(key)
        setVisibleStatsCards(ctx, current)
    }

    private const val KEY_MY_DISPLAY_NAMES = "my_display_names"
    val DEFAULT_MY_DISPLAY_NAMES: Set<String> = setOf("Eric Chiu")

    fun getMyDisplayNames(ctx: Context): Set<String> {
        val p = prefs(ctx)
        if (!p.contains(KEY_MY_DISPLAY_NAMES)) return DEFAULT_MY_DISPLAY_NAMES
        return (p.getStringSet(KEY_MY_DISPLAY_NAMES, emptySet()) ?: emptySet())
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun setMyDisplayNames(ctx: Context, names: Set<String>) {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        prefs(ctx).edit().putStringSet(KEY_MY_DISPLAY_NAMES, cleaned).apply()
    }

    fun isMyDisplayName(ctx: Context, name: String?): Boolean {
        val trimmed = name?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        return getMyDisplayNames(ctx).any { it.equals(trimmed, ignoreCase = true) }
    }
}
