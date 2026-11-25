package com.phantom.checker.util

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.annotation.StringRes
import com.phantom.checker.R

enum class MuteMode(val prefValue: String, @StringRes val labelRes: Int) {
    BLOCK_RECORD("block", R.string.prefs_mute_block),
    SILENT_RECORD("silent", R.string.prefs_mute_silent);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

    companion object {
        fun fromPref(value: String?): MuteMode =
            entries.firstOrNull { it.prefValue == value } ?: BLOCK_RECORD
    }
}

enum class NotifClickBehavior(val prefValue: String, @StringRes val labelRes: Int) {
    OPEN_PHANTOM("phantom", R.string.prefs_notif_click_phantom),
    OPEN_ORIGINAL_APP("original", R.string.common_open_original_app);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

    companion object {
        fun fromPref(value: String?): NotifClickBehavior =
            entries.firstOrNull { it.prefValue == value } ?: OPEN_PHANTOM
    }
}

enum class FontScaleMode(val prefValue: String, @StringRes val labelRes: Int, val scale: Float) {
    SMALL("small", R.string.prefs_font_small, 0.9f),
    NORMAL("normal", R.string.prefs_font_normal, 1.0f),
    LARGE("large", R.string.prefs_font_large, 1.15f);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

    companion object {
        fun fromPref(value: String?): FontScaleMode =
            entries.firstOrNull { it.prefValue == value } ?: NORMAL
    }
}

enum class PersistentNotifStyle(val prefValue: String, @StringRes val labelRes: Int) {
    DETAILED("detailed", R.string.prefs_pnotif_detailed),
    COMPACT("compact", R.string.prefs_pnotif_compact);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

    companion object {
        fun fromPref(value: String?): PersistentNotifStyle =
            entries.firstOrNull { it.prefValue == value } ?: DETAILED
    }
}

enum class PersistentNotifSubtitleMode(val prefValue: String, @StringRes val labelRes: Int) {
    LINK_STATUS("link_status", R.string.prefs_pnotif_sub_link),
    UNREAD_COUNT("unread_count", R.string.prefs_pnotif_sub_unread),
    BOTH("both", R.string.prefs_pnotif_sub_both),
    HIDDEN("hidden", R.string.prefs_pnotif_sub_hidden);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

    companion object {
        fun fromPref(value: String?): PersistentNotifSubtitleMode =
            entries.firstOrNull { it.prefValue == value } ?: LINK_STATUS
    }
}

enum class CardDensity(val prefValue: String, @StringRes val labelRes: Int) {
    COMFORTABLE("comfortable", R.string.prefs_density_comfortable),
    COMPACT("compact", R.string.prefs_density_compact);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

    companion object {
        fun fromPref(value: String?): CardDensity =
            entries.firstOrNull { it.prefValue == value } ?: COMFORTABLE
    }
}

enum class AiPurpose(val id: String, @StringRes val labelRes: Int, val catalogId: String) {
    CHAT("ai_chat", R.string.prefs_ai_purpose_chat, "ai_model_chat"),
    MESSAGE_ANALYSIS("ai_message_analysis", R.string.prefs_ai_purpose_msg, "ai_model_message_analysis"),
    WEEKLY_SUMMARY("ai_weekly_summary", R.string.prefs_ai_purpose_weekly, "ai_model_weekly_summary"),
    DEEP_ANALYSIS("ai_deep_analysis", R.string.prefs_ai_purpose_deep, "ai_model_deep_analysis"),
    SETTINGS_SEARCH("ai_settings_search", R.string.prefs_ai_purpose_search, "ai_model_settings_search"),
    AUTO_MEMO("ai_auto_memo", R.string.prefs_ai_purpose_memo, "ai_model_auto_memo"),
    BRIEFING("ai_briefing", R.string.prefs_ai_purpose_briefing, "ai_model_briefing"),
    DIARY_ANNOTATION("ai_diary_annotation", R.string.prefs_ai_purpose_diary, "ai_model_diary_annotation"),
    PROACTIVE("ai_proactive", R.string.prefs_ai_purpose_proactive, "ai_model_proactive");

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)
}

enum class TabBarDisplayMode(val prefValue: String, @StringRes val labelRes: Int) {
    STANDARD("standard", R.string.prefs_font_normal),
    COMPACT("compact", R.string.prefs_tab_compact),
    AUTO_HIDE("auto_hide", R.string.prefs_tab_autohide);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

    companion object {
        fun fromPref(value: String?): TabBarDisplayMode =
            entries.firstOrNull { it.prefValue == value } ?: STANDARD
    }
}

enum class HomeSortOrder(val prefValue: String, @StringRes val labelRes: Int) {
    RECENT("recent", R.string.prefs_sort_recent),
    UNREAD("unread", R.string.prefs_sort_unread),
    RISK("risk", R.string.prefs_sort_risk);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

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
    private const val KEY_PRESET_AI_RULES_PURGED = "preset_ai_rules_purged_v1"
    private const val KEY_WHITELIST_DEDUP_DONE_V1 = "whitelist_dedup_done_v1"
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

    enum class CardBgMode(val prefValue: String, @StringRes val labelRes: Int) {
        FOLLOW_GLASS("follow_glass", R.string.prefs_cardbg_glass),
        TRANSPARENT("transparent", R.string.prefs_cardbg_transparent),
        DARK("dark", R.string.prefs_cardbg_dark);

        val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

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

    /** #388 主動情報中樞（AiPurpose.PROACTIVE）獨立每日額度（預設 12 次/日，不吃聊天／分析配額） */
    fun getProactiveAiDailyLimit(ctx: Context): Int =
        prefs(ctx).getInt("ai_proactive_daily_limit", 12).coerceIn(1, 50)

    @Synchronized
    fun getProactiveAiTodayUsed(ctx: Context): Int {
        val p = prefs(ctx)
        return if (p.getString("ai_proactive_usage_date", "") == todayDateStr()) {
            p.getInt("ai_proactive_usage_count", 0)
        } else 0
    }

    @Synchronized
    fun canUseProactiveAiTodayAndIncrement(ctx: Context): Boolean {
        val limit = getProactiveAiDailyLimit(ctx)
        val p = prefs(ctx)
        val today = todayDateStr()
        val storedDate = p.getString("ai_proactive_usage_date", "")
        val currentCount = if (storedDate == today) p.getInt("ai_proactive_usage_count", 0) else 0
        if (currentCount >= limit) return false
        p.edit()
            .putString("ai_proactive_usage_date", today)
            .putInt("ai_proactive_usage_count", currentCount + 1)
            .apply()
        return true
    }

    @Synchronized
    fun refundTodayProactiveAiUsage(ctx: Context) {
        val p = prefs(ctx)
        val today = todayDateStr()
        if (p.getString("ai_proactive_usage_date", "") == today) {
            val currentCount = p.getInt("ai_proactive_usage_count", 0)
            if (currentCount > 0) {
                p.edit().putInt("ai_proactive_usage_count", currentCount - 1).apply()
            }
        }
    }

    /** #388 / #390 主動情報總開關（預設 false；關閉時零 AI 呼叫、零外送、worker 不排程） */
    fun isProactiveIntelEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("proactive_intel_enabled", false)

    fun setProactiveIntelEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("proactive_intel_enabled", enabled).apply()
    }

    /** #388 / #390 每日自動萃取關注主題開關（預設 true，須總開關開啟才生效） */
    fun isProactiveExtractEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("proactive_extract_enabled", true)

    fun setProactiveExtractEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("proactive_extract_enabled", enabled).apply()
    }

    /** #390 主動情報查詢頻率（6 / 12 / 24 小時，預設 6 小時） */
    fun getProactiveIntelFreqHours(ctx: Context): Int =
        prefs(ctx).getInt("proactive_intel_freq_hours", 6).let { if (it in setOf(6, 12, 24)) it else 6 }

    fun setProactiveIntelFreqHours(ctx: Context, hours: Int) {
        val v = if (hours in setOf(6, 12, 24)) hours else 6
        prefs(ctx).edit().putInt("proactive_intel_freq_hours", v).apply()
    }

    fun hasSeenProactiveIntelConsent(ctx: Context): Boolean =
        prefs(ctx).getBoolean("proactive_intel_consent_seen", false)

    fun setSeenProactiveIntelConsent(ctx: Context, seen: Boolean) {
        prefs(ctx).edit().putBoolean("proactive_intel_consent_seen", seen).apply()
    }

    fun getWatchTopicLastExtractDay(ctx: Context): String =
        prefs(ctx).getString("watch_topic_last_extract_day", "").orEmpty()

    fun setWatchTopicLastExtractDay(ctx: Context, dayKey: String) {
        prefs(ctx).edit().putString("watch_topic_last_extract_day", dayKey).apply()
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

    /** 舊版內建個人化預設 AI 規則是否已清除 */
    fun isPresetAiRulesPurged(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_PRESET_AI_RULES_PURGED, false)

    fun setPresetAiRulesPurged(ctx: Context, purged: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_PRESET_AI_RULES_PURGED, purged).apply()
    }

    /** #362 一次性重複白名單合併修復是否已執行 */
    fun isWhitelistDedupDoneV1(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_WHITELIST_DEDUP_DONE_V1, false)

    fun setWhitelistDedupDoneV1(ctx: Context, done: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_WHITELIST_DEDUP_DONE_V1, done).apply()
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

    /** #512 常駐通知副標內容（桌面連線狀態／未讀數／兩者／隱藏；預設連線狀態，未配對回退未讀數） */
    fun getPersistentNotifSubtitleMode(ctx: Context): PersistentNotifSubtitleMode =
        PersistentNotifSubtitleMode.fromPref(prefs(ctx).getString("persistent_notif_subtitle_mode", null))

    fun setPersistentNotifSubtitleMode(ctx: Context, mode: PersistentNotifSubtitleMode) {
        prefs(ctx).edit().putString("persistent_notif_subtitle_mode", mode.prefValue).apply()
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

    private val messageTimeFormatters = object : ThreadLocal<MutableMap<String, SimpleDateFormat>>() {
        override fun initialValue(): MutableMap<String, SimpleDateFormat> = HashMap(8)
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
        val locale = Locale.getDefault()
        val key = "${locale.toLanguageTag()}|$pattern"
        val map = messageTimeFormatters.get()!!
        val sdf = map.getOrPut(key) { SimpleDateFormat(pattern, locale) }
        return sdf.format(Date(timestamp))
    }

    fun hasBackgroundImage(ctx: Context, layer: BackgroundLayer): Boolean =
        prefs(ctx).getBoolean("bg_enabled_${layer.name.lowercase()}", false)

    fun setHasBackgroundImage(ctx: Context, layer: BackgroundLayer, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("bg_enabled_${layer.name.lowercase()}", enabled).apply()
    }

    /** #392 夥伴（助手）頁頂部橫幅背景自訂狀態 */
    fun hasAssistantHeaderBackground(ctx: Context): Boolean =
        prefs(ctx).getBoolean("assistant_header_background", false)

    fun setHasAssistantHeaderBackground(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("assistant_header_background", enabled).apply()
    }

    /** #393 AI 聊天室字體獨立縮放（0.85f..1.30f，預設 1.0f＝v3.5.0 現行） */
    fun getChatFontScale(ctx: Context): Float =
        prefs(ctx).getFloat("chat_font_scale", 1.0f).coerceIn(0.85f, 1.30f)

    fun setChatFontScale(ctx: Context, scale: Float) {
        prefs(ctx).edit().putFloat("chat_font_scale", scale.coerceIn(0.85f, 1.30f)).apply()
    }

    /** #393 訊息中心字體獨立縮放（0.85f..1.30f，預設 1.0f＝v3.5.0 現行） */
    fun getMessagesFontScale(ctx: Context): Float =
        prefs(ctx).getFloat("messages_font_scale", 1.0f).coerceIn(0.85f, 1.30f)

    fun setMessagesFontScale(ctx: Context, scale: Float) {
        prefs(ctx).edit().putFloat("messages_font_scale", scale.coerceIn(0.85f, 1.30f)).apply()
    }

    /** #393 聊天氣泡圓角半徑（8..24 dp，預設 18 dp＝v3.5.0 現行） */
    fun getChatBubbleCornerDp(ctx: Context): Int =
        prefs(ctx).getInt("chat_bubble_corner_dp", 18).coerceIn(8, 24)

    fun setChatBubbleCornerDp(ctx: Context, dp: Int) {
        prefs(ctx).edit().putInt("chat_bubble_corner_dp", dp.coerceIn(8, 24)).apply()
    }

    /** #393 聊天氣泡寬度比例（70..95%，預設 82%＝v3.5.0 現行；助手氣泡自動＋6% 上限 96%） */
    fun getChatBubbleWidthPercent(ctx: Context): Int =
        prefs(ctx).getInt("chat_bubble_width_ratio", 82).coerceIn(70, 95)

    fun setChatBubbleWidthPercent(ctx: Context, percent: Int) {
        prefs(ctx).edit().putInt("chat_bubble_width_ratio", percent.coerceIn(70, 95)).apply()
    }

    /** #393 首頁我的最愛 App 橫列顯示數上限（0＝全部顯示＝v3.5.0 預設；否則 4/6/8） */
    fun getFavoriteAppsMaxVisible(ctx: Context): Int =
        prefs(ctx).getInt("favorite_apps_max_visible", 0).let { if (it <= 0) 0 else it.coerceIn(2, 12) }

    fun setFavoriteAppsMaxVisible(ctx: Context, maxCount: Int) {
        val v = if (maxCount <= 0) 0 else maxCount.coerceIn(2, 12)
        prefs(ctx).edit().putInt("favorite_apps_max_visible", v).apply()
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

    /** #374 更新提醒 snooze 截止時間（epoch ms，預設 0 表示未 snooze） */
    fun getUpdateSnoozeUntilMs(ctx: Context): Long =
        prefs(ctx).getLong("update_snooze_until_ms", 0L)

    fun setUpdateSnoozeUntilMs(ctx: Context, untilMs: Long) {
        prefs(ctx).edit().putLong("update_snooze_until_ms", untilMs.coerceAtLeast(0L)).apply()
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

    enum class HomeCardLayoutMode(val key: String, @StringRes val labelRes: Int) {
        LIST("list", R.string.catalog_layout_list),
        CENTER("center", R.string.catalog_layout_stack);

        val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)
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
    enum class NoteCardLayoutMode(val key: String, @StringRes val labelRes: Int) {
        LIST("list", R.string.catalog_layout_list),
        CENTER("center", R.string.catalog_layout_stack);

        val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)
    }

    private const val KEY_NOTE_CARD_LAYOUT_MODE = "note_card_layout_mode"

    fun getNoteCardLayoutMode(ctx: Context): NoteCardLayoutMode {
        val raw = prefs(ctx).getString(KEY_NOTE_CARD_LAYOUT_MODE, null)
        return when (raw) {
            "peek_top" -> NoteCardLayoutMode.CENTER
            else -> NoteCardLayoutMode.entries.firstOrNull { it.key == raw } ?: NoteCardLayoutMode.LIST
        }
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

    /** 記憶頁篩選標籤列收合狀態（避免 9 顆標籤換行佔兩行） */
    private const val KEY_MEMORY_FILTER_CHIPS_COLLAPSED = "memory_filter_chips_collapsed"

    fun isMemoryFilterChipsCollapsed(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_MEMORY_FILTER_CHIPS_COLLAPSED, false)

    fun setMemoryFilterChipsCollapsed(ctx: Context, collapsed: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_MEMORY_FILTER_CHIPS_COLLAPSED, collapsed).apply()
    }

    /** #328 記憶×防詐交叉檢查總開關（預設開啟；關閉時風險分析行為與 v3.0.0 完全一致） */
    private const val KEY_ANTI_FRAUD_CROSS_CHECK = "anti_fraud_cross_check_enabled"

    fun isAntiFraudCrossCheckEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ANTI_FRAUD_CROSS_CHECK, true)

    fun setAntiFraudCrossCheckEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ANTI_FRAUD_CROSS_CHECK, enabled).apply()
    }

    /** #328 每週自動彙總之待確認「防詐免疫」記憶草稿（一律需使用者確認卡核准才寫入 memories） */
    private const val KEY_PENDING_SCAM_IMMUNITY_SUBJECT = "pending_scam_immunity_subject"
    private const val KEY_PENDING_SCAM_IMMUNITY_CONTENT = "pending_scam_immunity_content"
    private const val KEY_PENDING_SCAM_IMMUNITY_UPDATED_AT = "pending_scam_immunity_updated_at"

    data class PendingScamImmunityDraft(
        val subject: String,
        val content: String,
        val updatedAtMs: Long
    )

    fun getPendingScamImmunityDraft(ctx: Context): PendingScamImmunityDraft? {
        val p = prefs(ctx)
        val subj = p.getString(KEY_PENDING_SCAM_IMMUNITY_SUBJECT, null)?.trim().orEmpty()
        val content = p.getString(KEY_PENDING_SCAM_IMMUNITY_CONTENT, null)?.trim().orEmpty()
        if (subj.isEmpty() || content.isEmpty()) return null
        return PendingScamImmunityDraft(
            subject = subj,
            content = content,
            updatedAtMs = p.getLong(KEY_PENDING_SCAM_IMMUNITY_UPDATED_AT, 0L)
        )
    }

    fun savePendingScamImmunityDraft(ctx: Context, subject: String, content: String, nowMs: Long = System.currentTimeMillis()) {
        prefs(ctx).edit()
            .putString(KEY_PENDING_SCAM_IMMUNITY_SUBJECT, subject.trim())
            .putString(KEY_PENDING_SCAM_IMMUNITY_CONTENT, content.trim())
            .putLong(KEY_PENDING_SCAM_IMMUNITY_UPDATED_AT, nowMs)
            .apply()
    }

    fun clearPendingScamImmunityDraft(ctx: Context) {
        prefs(ctx).edit()
            .remove(KEY_PENDING_SCAM_IMMUNITY_SUBJECT)
            .remove(KEY_PENDING_SCAM_IMMUNITY_CONTENT)
            .remove(KEY_PENDING_SCAM_IMMUNITY_UPDATED_AT)
            .apply()
    }

    /** #329 本機日曆整合：使用者勾選開放給 AI 查詢／寫入的日曆 ID 清單（預設全不勾） */
    private const val KEY_CALENDAR_SELECTED_IDS = "calendar_selected_ids"
    private const val KEY_CALENDAR_WRITE_CONFIRM_ENABLED = "calendar_write_confirm_enabled"
    private const val KEY_CALENDAR_LIST_EXPANDED = "calendar_list_expanded"

    fun getSelectedCalendarIds(ctx: Context): Set<Long> {
        val raw = prefs(ctx).getString(KEY_CALENDAR_SELECTED_IDS, "").orEmpty().trim()
        if (raw.isEmpty()) return emptySet()
        return raw.split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .toSet()
    }

    fun setSelectedCalendarIds(ctx: Context, ids: Set<Long>) {
        val encoded = ids.sorted().joinToString(",")
        prefs(ctx).edit().putString(KEY_CALENDAR_SELECTED_IDS, encoded).apply()
    }

    /** #348 日曆寫入前是否顯示確認卡（預設開） */
    fun isCalendarWriteConfirmEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CALENDAR_WRITE_CONFIRM_ENABLED, true)

    fun setCalendarWriteConfirmEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CALENDAR_WRITE_CONFIRM_ENABLED, enabled).apply()
        PhantomSettingsBus.notifyChanged()
    }

    /** #348 本機日曆勾選清單展開狀態（預設收合） */
    fun isCalendarListExpanded(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_CALENDAR_LIST_EXPANDED, false)

    fun setCalendarListExpanded(ctx: Context, expanded: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_CALENDAR_LIST_EXPANDED, expanded).apply()
        PhantomSettingsBus.notifyChanged()
    }

    /** #333 行程類備忘分流：suggest_calendar（預設，優先日曆建議卡不再進待辦/記事）或 legacy_todo_note（沿用 v3.1） */
    const val SCHEDULE_MEMO_ROUTE_SUGGEST_CALENDAR = "suggest_calendar"
    const val SCHEDULE_MEMO_ROUTE_LEGACY_TODO_NOTE = "legacy_todo_note"
    private const val KEY_SCHEDULE_MEMO_ROUTE = "schedule_memo_route"

    fun getScheduleMemoRoute(ctx: Context): String {
        val raw = prefs(ctx).getString(KEY_SCHEDULE_MEMO_ROUTE, SCHEDULE_MEMO_ROUTE_SUGGEST_CALENDAR)
        return if (raw == SCHEDULE_MEMO_ROUTE_LEGACY_TODO_NOTE) {
            SCHEDULE_MEMO_ROUTE_LEGACY_TODO_NOTE
        } else {
            SCHEDULE_MEMO_ROUTE_SUGGEST_CALENDAR
        }
    }

    fun setScheduleMemoRoute(ctx: Context, route: String) {
        val safe = if (route == SCHEDULE_MEMO_ROUTE_LEGACY_TODO_NOTE) {
            SCHEDULE_MEMO_ROUTE_LEGACY_TODO_NOTE
        } else {
            SCHEDULE_MEMO_ROUTE_SUGGEST_CALENDAR
        }
        prefs(ctx).edit().putString(KEY_SCHEDULE_MEMO_ROUTE, safe).apply()
    }

    /** #336 通訊錄 VIP 信任圈同步設定（off / starred / group） */
    const val CONTACTS_VIP_MODE_OFF = "off"
    const val CONTACTS_VIP_MODE_STARRED = "starred"
    const val CONTACTS_VIP_MODE_GROUP = "group"
    private const val KEY_CONTACTS_VIP_MODE = "contacts_vip_mode"
    private const val KEY_CONTACTS_VIP_GROUP_ID = "contacts_vip_group_id"
    private const val KEY_CONTACTS_VIP_LAST_SYNC_AT = "contacts_vip_last_sync_at"

    fun getContactsVipMode(ctx: Context): String {
        return when (prefs(ctx).getString(KEY_CONTACTS_VIP_MODE, CONTACTS_VIP_MODE_OFF)) {
            CONTACTS_VIP_MODE_STARRED -> CONTACTS_VIP_MODE_STARRED
            CONTACTS_VIP_MODE_GROUP -> CONTACTS_VIP_MODE_GROUP
            else -> CONTACTS_VIP_MODE_OFF
        }
    }

    fun setContactsVipMode(ctx: Context, mode: String) {
        val safe = when (mode) {
            CONTACTS_VIP_MODE_STARRED -> CONTACTS_VIP_MODE_STARRED
            CONTACTS_VIP_MODE_GROUP -> CONTACTS_VIP_MODE_GROUP
            else -> CONTACTS_VIP_MODE_OFF
        }
        prefs(ctx).edit().putString(KEY_CONTACTS_VIP_MODE, safe).apply()
    }

    fun getContactsVipGroupId(ctx: Context): Long? {
        val v = prefs(ctx).getLong(KEY_CONTACTS_VIP_GROUP_ID, -1L)
        return if (v > 0L) v else null
    }

    fun setContactsVipGroupId(ctx: Context, groupId: Long?) {
        prefs(ctx).edit().putLong(KEY_CONTACTS_VIP_GROUP_ID, groupId ?: -1L).apply()
    }

    fun getContactsVipLastSyncAt(ctx: Context): Long =
        prefs(ctx).getLong(KEY_CONTACTS_VIP_LAST_SYNC_AT, 0L)

    fun setContactsVipLastSyncAt(ctx: Context, ts: Long) {
        prefs(ctx).edit().putLong(KEY_CONTACTS_VIP_LAST_SYNC_AT, ts).apply()
    }

    // ==================== #337 主動式推播與個人簡報引擎 ====================

    const val BRIEFING_CONTENT_SCHEDULE = "schedule"
    const val BRIEFING_CONTENT_TODO = "todo"
    const val BRIEFING_CONTENT_IMPORTANT_NOTIFS = "important_notifs"
    val DEFAULT_BRIEFING_CONTENT = setOf(
        BRIEFING_CONTENT_SCHEDULE,
        BRIEFING_CONTENT_TODO,
        BRIEFING_CONTENT_IMPORTANT_NOTIFS
    )

    const val PROACTIVE_TOPIC_SCHEDULE = "schedule"
    const val PROACTIVE_TOPIC_TODO = "todo"
    const val PROACTIVE_TOPIC_CONFLICT = "conflict"
    const val PROACTIVE_TOPIC_INTEREST = "interest"
    const val PROACTIVE_TOPIC_REGION = "region"
    const val PROACTIVE_TOPIC_EXTERNAL_SEARCH = "external_search"
    val DEFAULT_PROACTIVE_TOPICS = setOf(
        PROACTIVE_TOPIC_SCHEDULE,
        PROACTIVE_TOPIC_TODO,
        PROACTIVE_TOPIC_CONFLICT
    )

    fun isMorningBriefingEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("briefing_morning_enabled", true)

    fun setMorningBriefingEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("briefing_morning_enabled", enabled).apply()
    }

    fun getMorningBriefingTime(ctx: Context): String =
        prefs(ctx).getString("briefing_morning_time", "08:45").orEmpty().ifBlank { "08:45" }

    fun setMorningBriefingTime(ctx: Context, hhmm: String) {
        prefs(ctx).edit().putString("briefing_morning_time", hhmm.trim()).apply()
    }

    fun isEveningBriefingEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("briefing_evening_enabled", true)

    fun setEveningBriefingEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("briefing_evening_enabled", enabled).apply()
    }

    fun getEveningBriefingTime(ctx: Context): String =
        prefs(ctx).getString("briefing_evening_time", "22:25").orEmpty().ifBlank { "22:25" }

    fun setEveningBriefingTime(ctx: Context, hhmm: String) {
        prefs(ctx).edit().putString("briefing_evening_time", hhmm.trim()).apply()
    }

    fun getMorningBriefingContents(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("briefing_morning_contents", DEFAULT_BRIEFING_CONTENT) ?: DEFAULT_BRIEFING_CONTENT

    fun setMorningBriefingContents(ctx: Context, items: Set<String>) {
        prefs(ctx).edit().putStringSet("briefing_morning_contents", items.toSet()).apply()
    }

    fun getEveningBriefingContents(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("briefing_evening_contents", DEFAULT_BRIEFING_CONTENT) ?: DEFAULT_BRIEFING_CONTENT

    fun setEveningBriefingContents(ctx: Context, items: Set<String>) {
        prefs(ctx).edit().putStringSet("briefing_evening_contents", items.toSet()).apply()
    }

    fun getProactiveTopics(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("proactive_topics", DEFAULT_PROACTIVE_TOPICS) ?: DEFAULT_PROACTIVE_TOPICS

    fun isProactiveTopicEnabled(ctx: Context, topic: String): Boolean {
        if (isTokenSaverEnabled(ctx) &&
            (topic == PROACTIVE_TOPIC_EXTERNAL_SEARCH || topic == PROACTIVE_TOPIC_INTEREST || topic == PROACTIVE_TOPIC_REGION)
        ) {
            return false
        }
        return topic in getProactiveTopics(ctx)
    }

    fun setProactiveTopicEnabled(ctx: Context, topic: String, enabled: Boolean) {
        val current = getProactiveTopics(ctx).toMutableSet()
        if (enabled) current.add(topic) else current.remove(topic)
        prefs(ctx).edit().putStringSet("proactive_topics", current).apply()
    }

    fun getProactiveDailyCap(ctx: Context): Int =
        prefs(ctx).getInt("proactive_daily_cap", 6).coerceIn(1, 20)

    fun setProactiveDailyCap(ctx: Context, cap: Int) {
        prefs(ctx).edit().putInt("proactive_daily_cap", cap.coerceIn(1, 20)).apply()
    }

    fun isProactiveDndEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("proactive_dnd_enabled", true)

    fun setProactiveDndEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("proactive_dnd_enabled", enabled).apply()
    }

    fun getProactiveDndStart(ctx: Context): String =
        prefs(ctx).getString("proactive_dnd_start", "00:00").orEmpty().ifBlank { "00:00" }

    fun setProactiveDndStart(ctx: Context, hhmm: String) {
        prefs(ctx).edit().putString("proactive_dnd_start", hhmm.trim()).apply()
    }

    fun getProactiveDndEnd(ctx: Context): String =
        prefs(ctx).getString("proactive_dnd_end", "07:00").orEmpty().ifBlank { "07:00" }

    fun setProactiveDndEnd(ctx: Context, hhmm: String) {
        prefs(ctx).edit().putString("proactive_dnd_end", hhmm.trim()).apply()
    }

    fun getProactiveRegionKeyword(ctx: Context): String =
        prefs(ctx).getString("proactive_region_keyword", "").orEmpty().trim()

    fun setProactiveRegionKeyword(ctx: Context, region: String) {
        prefs(ctx).edit().putString("proactive_region_keyword", region.trim().take(32)).apply()
    }

    fun getProactiveLastFingerprint(ctx: Context): String =
        prefs(ctx).getString("proactive_last_fingerprint", "").orEmpty()

    fun setProactiveLastFingerprint(ctx: Context, sha: String) {
        prefs(ctx).edit().putString("proactive_last_fingerprint", sha).apply()
    }

    fun isProactiveDirty(ctx: Context): Boolean =
        prefs(ctx).getBoolean("proactive_dirty_flag", true)

    fun setProactiveDirty(ctx: Context, dirty: Boolean) {
        prefs(ctx).edit().putBoolean("proactive_dirty_flag", dirty).apply()
    }

    fun getProactiveDailySentCount(ctx: Context, todayKey: String): Int {
        val p = prefs(ctx)
        if (p.getString("proactive_daily_date", "") != todayKey) return 0
        return p.getInt("proactive_daily_count", 0)
    }

    fun incrementProactiveDailySentCount(ctx: Context, todayKey: String) {
        val p = prefs(ctx)
        val prev = if (p.getString("proactive_daily_date", "") == todayKey) p.getInt("proactive_daily_count", 0) else 0
        val total7d = p.getInt("proactive_push_total_count", 0) + 1
        p.edit()
            .putString("proactive_daily_date", todayKey)
            .putInt("proactive_daily_count", prev + 1)
            .putInt("proactive_push_total_count", total7d)
            .apply()
    }

    fun getProactivePushTotalCount(ctx: Context): Int =
        prefs(ctx).getInt("proactive_push_total_count", 0)

    // ---- #372 主動提醒事件里程碑與 10 分鐘內容去重閘門 ----

    private const val KEY_PROACTIVE_MILESTONE_KEYS = "proactive_delivered_milestones"
    private const val KEY_PROACTIVE_RECENT_HASHES = "proactive_recent_content_hashes"
    private const val PROACTIVE_CONTENT_DEDUP_WINDOW_MS = 10L * 60L * 1000L

    /**
     * 讀取已投遞的里程碑 key 集合，並自動清理非當日（dayKey 不符）之過期 key。
     * key 格式：type:id:dayKey:milestone 或 allDay:eventId:dayKey。
     */
    fun getPrunedProactiveMilestones(ctx: Context, todayKey: String): Set<String> {
        val p = prefs(ctx)
        val raw = p.getStringSet(KEY_PROACTIVE_MILESTONE_KEYS, emptySet()).orEmpty()
        if (raw.isEmpty()) return emptySet()
        val kept = raw.filterTo(LinkedHashSet()) { key ->
            val parts = key.split(':')
            // 格式為 type:id:dayKey:milestone 或 allDay:id:dayKey -> 第 3 欄為 dayKey
            parts.size >= 3 && parts[2] == todayKey
        }
        if (kept.size != raw.size) {
            p.edit().putStringSet(KEY_PROACTIVE_MILESTONE_KEYS, kept).apply()
        }
        return kept
    }

    fun isProactiveMilestoneDelivered(ctx: Context, milestoneKey: String, todayKey: String): Boolean {
        return milestoneKey in getPrunedProactiveMilestones(ctx, todayKey)
    }

    fun markProactiveMilestonesDelivered(ctx: Context, milestoneKeys: Collection<String>, todayKey: String) {
        if (milestoneKeys.isEmpty()) return
        val kept = getPrunedProactiveMilestones(ctx, todayKey).toMutableSet()
        kept.addAll(milestoneKeys.filter { it.isNotBlank() })
        val trimmed = if (kept.size > 200) kept.toList().takeLast(200).toSet() else kept
        prefs(ctx).edit().putStringSet(KEY_PROACTIVE_MILESTONE_KEYS, trimmed).apply()
    }

    /**
     * 檢查 contentHash 是否於近 10 分鐘內已發送；同時清理逾時紀錄。
     * 儲存格式：以 '\n' 分隔的 "hash|epochMs" 字串（避免 StringSet 可變集合陷阱）。
     */
    fun isProactiveContentHashRecent(ctx: Context, contentHash: String, nowMs: Long): Boolean {
        val p = prefs(ctx)
        val raw = p.getString(KEY_PROACTIVE_RECENT_HASHES, "").orEmpty()
        if (raw.isBlank()) return false
        val windowMs = getProactiveDedupWindowMinutes(ctx) * 60L * 1000L
        val cutoff = nowMs - windowMs
        val kept = mutableListOf<String>()
        var hit = false
        raw.lineSequence().forEach { line ->
            val idx = line.indexOf('|')
            if (idx <= 0) return@forEach
            val hash = line.substring(0, idx)
            val ts = line.substring(idx + 1).toLongOrNull() ?: return@forEach
            if (ts >= cutoff) {
                kept.add(line)
                if (hash == contentHash) hit = true
            }
        }
        val normalized = kept.joinToString("\n")
        if (normalized != raw) {
            p.edit().putString(KEY_PROACTIVE_RECENT_HASHES, normalized).apply()
        }
        return hit
    }

    fun recordProactiveContentHash(ctx: Context, contentHash: String, nowMs: Long) {
        val p = prefs(ctx)
        val raw = p.getString(KEY_PROACTIVE_RECENT_HASHES, "").orEmpty()
        val windowMs = getProactiveDedupWindowMinutes(ctx) * 60L * 1000L
        val cutoff = nowMs - windowMs
        val kept = raw.lineSequence()
            .filter { line ->
                val idx = line.indexOf('|')
                if (idx <= 0) return@filter false
                val ts = line.substring(idx + 1).toLongOrNull() ?: return@filter false
                ts >= cutoff && line.substring(0, idx) != contentHash
            }
            .toMutableList()
        kept.add("$contentHash|$nowMs")
        val trimmed = if (kept.size > 40) kept.takeLast(40) else kept
        p.edit().putString(KEY_PROACTIVE_RECENT_HASHES, trimmed.joinToString("\n")).apply()
    }

    // ---- #377 主動訊息體驗與自訂設定擴充 ----

    /** 簡報獨立每日上限（1–10，預設 2；與主動提醒上限分離） */
    fun getBriefingDailyCap(ctx: Context): Int =
        prefs(ctx).getInt("briefing_daily_cap", 2).coerceIn(1, 10)

    fun setBriefingDailyCap(ctx: Context, cap: Int) {
        prefs(ctx).edit().putInt("briefing_daily_cap", cap.coerceIn(1, 10)).apply()
    }

    fun getBriefingDailySentCount(ctx: Context, todayKey: String): Int {
        val p = prefs(ctx)
        if (p.getString("briefing_daily_date", "") != todayKey) return 0
        return p.getInt("briefing_daily_count", 0)
    }

    fun incrementBriefingDailySentCount(ctx: Context, todayKey: String) {
        val p = prefs(ctx)
        val prev = if (p.getString("briefing_daily_date", "") == todayKey) p.getInt("briefing_daily_count", 0) else 0
        p.edit()
            .putString("briefing_daily_date", todayKey)
            .putInt("briefing_daily_count", prev + 1)
            .apply()
    }

    /** 行程／待辦主動提醒提前分鐘數（15 / 30 / 60，預設 30） */
    fun getProactiveReminderLeadMinutes(ctx: Context): Int {
        val v = prefs(ctx).getInt("proactive_reminder_lead_minutes", 30)
        return when (v) {
            15, 30, 60 -> v
            else -> 30
        }
    }

    fun setProactiveReminderLeadMinutes(ctx: Context, minutes: Int) {
        val safe = when (minutes) {
            15, 30, 60 -> minutes
            else -> 30
        }
        prefs(ctx).edit().putInt("proactive_reminder_lead_minutes", safe).apply()
    }

    /** 今日全天事件提醒時刻（HH:mm，預設 09:00） */
    fun getProactiveAllDayRemindTime(ctx: Context): String =
        prefs(ctx).getString("proactive_allday_remind_time", "09:00").orEmpty().ifBlank { "09:00" }

    fun setProactiveAllDayRemindTime(ctx: Context, hhmm: String) {
        prefs(ctx).edit().putString("proactive_allday_remind_time", hhmm.trim().ifBlank { "09:00" }).apply()
    }

    /** 聊天室氣泡時間戳顯示開關（預設開） */
    fun isChatTimestampEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("chat_bubble_timestamp_enabled", true)

    fun setChatTimestampEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("chat_bubble_timestamp_enabled", enabled).apply()
    }

    /** 更新提醒 snooze 間隔分鐘數（30 / 60 / 240，預設 60） */
    fun getUpdateSnoozeMinutes(ctx: Context): Int {
        val v = prefs(ctx).getInt("update_snooze_minutes", 60)
        return when (v) {
            30, 60, 240 -> v
            else -> 60
        }
    }

    fun setUpdateSnoozeMinutes(ctx: Context, minutes: Int) {
        val safe = when (minutes) {
            30, 60, 240 -> minutes
            else -> 60
        }
        prefs(ctx).edit().putInt("update_snooze_minutes", safe).apply()
    }

    /** 主動監控 Worker 檢查間隔分鐘數（15 / 30 / 60，預設 15） */
    fun getProactiveMonitorIntervalMinutes(ctx: Context): Int {
        val v = prefs(ctx).getInt("proactive_monitor_interval_minutes", 15)
        return when (v) {
            15, 30, 60 -> v
            else -> 15
        }
    }

    fun setProactiveMonitorIntervalMinutes(ctx: Context, minutes: Int) {
        val safe = when (minutes) {
            15, 30, 60 -> minutes
            else -> 15
        }
        prefs(ctx).edit().putInt("proactive_monitor_interval_minutes", safe).apply()
    }

    /** 主動提醒同內容去重冷卻分鐘數（10 / 30 / 60，預設 10） */
    fun getProactiveDedupWindowMinutes(ctx: Context): Int {
        val v = prefs(ctx).getInt("proactive_dedup_window_minutes", 10)
        return when (v) {
            10, 30, 60 -> v
            else -> 10
        }
    }

    fun setProactiveDedupWindowMinutes(ctx: Context, minutes: Int) {
        val safe = when (minutes) {
            10, 30, 60 -> minutes
            else -> 10
        }
        prefs(ctx).edit().putInt("proactive_dedup_window_minutes", safe).apply()
    }

    /** 每則主動訊息回饋（👍 helpful / 👎 noisy），輕量存 prefs 不新增 DB 表 */
    fun recordProactiveMessageFeedback(ctx: Context, messageId: Long, helpful: Boolean) {
        if (messageId <= 0L) return
        val p = prefs(ctx)
        val raw = p.getString("proactive_msg_feedback_map", "").orEmpty()
        val entries = LinkedHashMap<Long, String>()
        raw.split(',').forEach { pair ->
            val idx = pair.indexOf(':')
            if (idx > 0) {
                val id = pair.substring(0, idx).toLongOrNull() ?: return@forEach
                entries[id] = pair.substring(idx + 1)
            }
        }
        val prev = entries[messageId]
        val nextVal = if (helpful) "up" else "down"
        if (prev == nextVal) return
        entries[messageId] = nextVal
        val trimmed = entries.entries.toList().let { if (it.size > 120) it.takeLast(120) else it }
        var up = p.getInt("proactive_feedback_helpful_count", 0)
        var down = p.getInt("proactive_feedback_noisy_count", 0)
        if (prev == "up") up = (up - 1).coerceAtLeast(0)
        if (prev == "down") down = (down - 1).coerceAtLeast(0)
        if (helpful) up++ else down++
        p.edit()
            .putString("proactive_msg_feedback_map", trimmed.joinToString(",") { "${it.key}:${it.value}" })
            .putInt("proactive_feedback_helpful_count", up)
            .putInt("proactive_feedback_noisy_count", down)
            .apply()
    }

    fun getProactiveMessageFeedback(ctx: Context, messageId: Long): String? {
        if (messageId <= 0L) return null
        val raw = prefs(ctx).getString("proactive_msg_feedback_map", "").orEmpty()
        raw.split(',').forEach { pair ->
            val idx = pair.indexOf(':')
            if (idx > 0 && pair.substring(0, idx).toLongOrNull() == messageId) {
                return pair.substring(idx + 1)
            }
        }
        return null
    }

    fun getProactiveFeedbackCounts(ctx: Context): Pair<Int, Int> {
        val p = prefs(ctx)
        return p.getInt("proactive_feedback_helpful_count", 0) to p.getInt("proactive_feedback_noisy_count", 0)
    }

    fun getLastBriefingDay(ctx: Context, isMorning: Boolean): String =
        prefs(ctx).getString(if (isMorning) "briefing_last_morning_day" else "briefing_last_evening_day", "").orEmpty()

    fun setLastBriefingDay(ctx: Context, isMorning: Boolean, dayKey: String) {
        prefs(ctx).edit()
            .putString(if (isMorning) "briefing_last_morning_day" else "briefing_last_evening_day", dayKey)
            .apply()
    }

    fun getLastBriefingSummary(ctx: Context): String =
        prefs(ctx).getString("briefing_last_summary", "").orEmpty()

    fun getLastBriefingTimestamp(ctx: Context): Long =
        prefs(ctx).getLong("briefing_last_ts", 0L)

    fun getLastBriefingMessageId(ctx: Context): Long =
        prefs(ctx).getLong("briefing_last_msg_id", -1L)

    fun saveLastBriefingMeta(ctx: Context, summary: String, ts: Long, messageId: Long) {
        prefs(ctx).edit()
            .putString("briefing_last_summary", summary.take(200))
            .putLong("briefing_last_ts", ts)
            .putLong("briefing_last_msg_id", messageId)
            .apply()
    }

    fun getBriefingReadCount(ctx: Context): Int =
        prefs(ctx).getInt("briefing_read_count", 0)

    fun incrementBriefingReadCount(ctx: Context) {
        val p = prefs(ctx)
        p.edit().putInt("briefing_read_count", p.getInt("briefing_read_count", 0) + 1).apply()
        PhantomSettingsBus.notifyChanged()
    }

    /**
     * #349 以 messageId 去重計入「簡報點閱」親密值；同則簡報在聊天與統計頁僅計一次。
     * 回傳 true 表示本次為首次點閱並已計分。
     */
    fun markBriefingReadOnce(ctx: Context, messageId: Long): Boolean {
        if (messageId <= 0L) return false
        val p = prefs(ctx)
        val raw = p.getString("briefing_counted_msg_ids", "").orEmpty()
        val ids = raw.split(',').mapNotNull { it.trim().toLongOrNull() }.toMutableList()
        if (messageId in ids) return false
        ids.add(messageId)
        val trimmed = if (ids.size > 30) ids.takeLast(30) else ids
        p.edit()
            .putString("briefing_counted_msg_ids", trimmed.joinToString(","))
            .putInt("briefing_read_count", p.getInt("briefing_read_count", 0) + 1)
            .apply()
        PhantomSettingsBus.notifyChanged()
        return true
    }

    // ---- #337 「我對你的瞭解」畫像卡 ----

    fun getPortraitCardText(ctx: Context): String =
        prefs(ctx).getString("memory_portrait_text", "").orEmpty().trim()

    fun getPortraitCardUpdatedAt(ctx: Context): Long =
        prefs(ctx).getLong("memory_portrait_updated_at", 0L)

    fun getPortraitConfirmedCountAtGen(ctx: Context): Int =
        prefs(ctx).getInt("memory_portrait_confirmed_count", 0)

    fun savePortraitCard(ctx: Context, text: String, updatedAt: Long, confirmedCount: Int) {
        prefs(ctx).edit()
            .putString("memory_portrait_text", text.trim().take(420))
            .putLong("memory_portrait_updated_at", updatedAt)
            .putInt("memory_portrait_confirmed_count", confirmedCount)
            .apply()
    }

    fun getPortraitDailyGenCount(ctx: Context, todayKey: String): Int {
        val p = prefs(ctx)
        if (p.getString("memory_portrait_gen_date", "") != todayKey) return 0
        return p.getInt("memory_portrait_gen_count", 0)
    }

    fun incrementPortraitDailyGenCount(ctx: Context, todayKey: String) {
        val p = prefs(ctx)
        val prev = if (p.getString("memory_portrait_gen_date", "") == todayKey) p.getInt("memory_portrait_gen_count", 0) else 0
        p.edit()
            .putString("memory_portrait_gen_date", todayKey)
            .putInt("memory_portrait_gen_count", prev + 1)
            .apply()
    }

    // ---- #339 連結應用程式中心：GitHub 監控與 Discord 轉發開關 ----

    fun isGitHubMonitorEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("cloud_github_monitor_enabled", false)

    fun setGitHubMonitorEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("cloud_github_monitor_enabled", enabled).apply()
    }

    fun getGitHubPollIntervalMinutes(ctx: Context): Int =
        prefs(ctx).getInt("cloud_github_poll_interval_min", 15).coerceIn(15, 180)

    fun setGitHubPollIntervalMinutes(ctx: Context, minutes: Int) {
        prefs(ctx).edit().putInt("cloud_github_poll_interval_min", minutes.coerceIn(15, 180)).apply()
    }

    fun getLastGitHubAlertKey(ctx: Context): String =
        prefs(ctx).getString("cloud_github_last_alert_key", "").orEmpty()

    fun setLastGitHubAlertKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString("cloud_github_last_alert_key", key).apply()
    }

    fun getLastGitHubPrKey(ctx: Context): String =
        prefs(ctx).getString("cloud_github_last_pr_key", "").orEmpty()

    fun setLastGitHubPrKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString("cloud_github_last_pr_key", key).apply()
    }

    fun isDiscordForwardEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("cloud_discord_forward_enabled", false)

    fun setDiscordForwardEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("cloud_discord_forward_enabled", enabled).apply()
    }

    data class StatsCardOption(val key: String, @StringRes val titleRes: Int, @StringRes val subtitleRes: Int, val defaultVisible: Boolean) {
        val title: String get() = com.phantom.checker.PhantomApplication.str(titleRes)
        val subtitle: String get() = com.phantom.checker.PhantomApplication.str(subtitleRes)
    }

    val STATS_CARD_OPTIONS: List<StatsCardOption> = listOf(
        StatsCardOption("stats_briefing", R.string.prefs_stats_briefing_title, R.string.prefs_stats_briefing_sub, true),
        StatsCardOption("stats_weekly_bar", R.string.stats_s_b1fd50, R.string.prefs_stats_weekly_bar_sub, true),
        StatsCardOption("stats_risk_dist", R.string.prefs_stats_risk_dist_title, R.string.prefs_stats_risk_dist_sub, true),
        StatsCardOption("stats_hourly", R.string.stats_s_5c449d, R.string.prefs_stats_hourly_sub, true),
        StatsCardOption("stats_daily_top_apps", R.string.stats_s_a5df5e, R.string.prefs_stats_daily_top_apps_sub, true),
        StatsCardOption("stats_ai_tags", R.string.stats_s_a167e9, R.string.prefs_stats_ai_tags_sub, true),
        StatsCardOption("stats_harassment", R.string.prefs_stats_harassment_title, R.string.prefs_stats_harassment_sub, true),
        StatsCardOption("stats_top_risky", R.string.stats_s_147fec, R.string.prefs_stats_top_risky_sub, true),
        StatsCardOption("stats_risk_trend", R.string.prefs_stats_risk_trend_title, R.string.prefs_stats_risk_trend_sub, false),
        StatsCardOption("stats_unread_ratio", R.string.prefs_stats_unread_ratio_title, R.string.prefs_stats_unread_ratio_sub, false),
        StatsCardOption("stats_app_share", R.string.prefs_stats_app_share_title, R.string.prefs_stats_app_share_sub, false),
        StatsCardOption("stats_todo_done", R.string.prefs_stats_todo_done_title, R.string.prefs_stats_todo_done_sub, false)
    )

    val DEFAULT_VISIBLE_STATS_CARDS: Set<String> =
        STATS_CARD_OPTIONS.filter { it.defaultVisible }.map { it.key }.toSet()

    fun getVisibleStatsCards(ctx: Context): Set<String> {
        val raw = prefs(ctx).getStringSet("visible_stats_cards", DEFAULT_VISIBLE_STATS_CARDS)
            ?: DEFAULT_VISIBLE_STATS_CARDS
        return if ("stats_summary" in raw && "stats_briefing" !in raw) {
            (raw - "stats_summary") + "stats_briefing"
        } else {
            raw - "stats_summary"
        }
    }

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

    /** 預設不帶任何名稱（通用原則：不內建與特定使用者相關的預設值） */
    val DEFAULT_MY_DISPLAY_NAMES: Set<String> = emptySet()

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

    // ==================== #314 / #315 記憶庫與 AI 助手設定 ====================

    private const val KEY_MEMORY_CHAT_CAPTURE_ENABLED = "memory_chat_capture_enabled"
    private const val KEY_MEMORY_WRITE_CONFIRM_ENABLED = "memory_write_confirm_enabled"
    private const val KEY_MEMORY_NORMAL_TOP_K = "memory_normal_top_k"
    private const val KEY_MEMORY_SAVER_TOP_K = "memory_saver_top_k"
    private const val KEY_MEMORY_NORMAL_PER_ITEM_CHARS = "memory_normal_per_item_chars"
    private const val KEY_MEMORY_SECTION_COLLAPSED = "memory_section_collapsed"

    const val DEFAULT_MEMORY_NORMAL_TOP_K = 8
    const val MIN_MEMORY_NORMAL_TOP_K = 1
    const val MAX_MEMORY_NORMAL_TOP_K = 8

    const val DEFAULT_MEMORY_SAVER_TOP_K = 4
    const val MIN_MEMORY_SAVER_TOP_K = 1
    const val MAX_MEMORY_SAVER_TOP_K = 4

    const val DEFAULT_MEMORY_NORMAL_PER_ITEM_CHARS = 70 // 約 50 tokens 中文
    const val MIN_MEMORY_NORMAL_PER_ITEM_CHARS = 30
    const val MAX_MEMORY_NORMAL_PER_ITEM_CHARS = 120
    const val TOKEN_SAVER_MEMORY_PER_ITEM_CHARS = 36    // 約 25 tokens 中文

    const val MEMORY_NORMAL_TOTAL_TOKEN_BUDGET = 250
    const val MEMORY_SAVER_TOTAL_TOKEN_BUDGET = 120

    /** 聊天記憶擷取開關（預設開） */
    fun isMemoryChatCaptureEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_MEMORY_CHAT_CAPTURE_ENABLED, true)

    fun setMemoryChatCaptureEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_MEMORY_CHAT_CAPTURE_ENABLED, enabled).apply()
    }

    /** 記憶寫入前是否顯示確認卡（預設開；敏感記憶與衝突仍一律確認） */
    fun isMemoryWriteConfirmEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_MEMORY_WRITE_CONFIRM_ENABLED, true)

    fun setMemoryWriteConfirmEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_MEMORY_WRITE_CONFIRM_ENABLED, enabled).apply()
        PhantomSettingsBus.notifyChanged()
    }

    /** 正常模式記憶注入筆數 Top-K（範圍 1–8，預設 8；與省 token 模式獨立儲存、互不覆寫） */
    fun getMemoryNormalTopK(ctx: Context): Int =
        prefs(ctx).getInt(KEY_MEMORY_NORMAL_TOP_K, DEFAULT_MEMORY_NORMAL_TOP_K)
            .coerceIn(MIN_MEMORY_NORMAL_TOP_K, MAX_MEMORY_NORMAL_TOP_K)

    fun setMemoryNormalTopK(ctx: Context, topK: Int) {
        val clamped = topK.coerceIn(MIN_MEMORY_NORMAL_TOP_K, MAX_MEMORY_NORMAL_TOP_K)
        prefs(ctx).edit().putInt(KEY_MEMORY_NORMAL_TOP_K, clamped).apply()
    }

    /** 省 token 模式記憶注入筆數 Top-K（範圍 1–4，預設 4；與正常模式獨立儲存、互不覆寫） */
    fun getMemorySaverTopK(ctx: Context): Int =
        prefs(ctx).getInt(KEY_MEMORY_SAVER_TOP_K, DEFAULT_MEMORY_SAVER_TOP_K)
            .coerceIn(MIN_MEMORY_SAVER_TOP_K, MAX_MEMORY_SAVER_TOP_K)

    fun setMemorySaverTopK(ctx: Context, topK: Int) {
        val clamped = topK.coerceIn(MIN_MEMORY_SAVER_TOP_K, MAX_MEMORY_SAVER_TOP_K)
        prefs(ctx).edit().putInt(KEY_MEMORY_SAVER_TOP_K, clamped).apply()
    }

    /** 正常模式每筆記憶預覽截斷字數（範圍 30–120 字，預設 70 字 ≈ 50 tokens 中文） */
    fun getMemoryNormalPerItemChars(ctx: Context): Int =
        prefs(ctx).getInt(KEY_MEMORY_NORMAL_PER_ITEM_CHARS, DEFAULT_MEMORY_NORMAL_PER_ITEM_CHARS)
            .coerceIn(MIN_MEMORY_NORMAL_PER_ITEM_CHARS, MAX_MEMORY_NORMAL_PER_ITEM_CHARS)

    fun setMemoryNormalPerItemChars(ctx: Context, chars: Int) {
        val clamped = chars.coerceIn(MIN_MEMORY_NORMAL_PER_ITEM_CHARS, MAX_MEMORY_NORMAL_PER_ITEM_CHARS)
        prefs(ctx).edit().putInt(KEY_MEMORY_NORMAL_PER_ITEM_CHARS, clamped).apply()
    }

    /** 實際生效的記憶注入筆數（依省 token 模式切換對應的獨立設定，絕不覆寫原設定值） */
    fun getEffectiveMemoryInjectionTopK(ctx: Context): Int =
        if (isTokenSaverEnabled(ctx)) getMemorySaverTopK(ctx) else getMemoryNormalTopK(ctx)

    /** 實際生效的每筆記憶預覽字數上限（省 token 模式約 25 tokens 中文，正常模式約 50 tokens 中文） */
    fun getEffectiveMemoryPerItemMaxChars(ctx: Context): Int =
        if (isTokenSaverEnabled(ctx)) TOKEN_SAVER_MEMORY_PER_ITEM_CHARS else getMemoryNormalPerItemChars(ctx)

    /** 實際生效的記憶注入總 token 預算（正常模式 ≤250 tokens，省 token 模式 ≤120 tokens） */
    fun getEffectiveMemoryTotalTokenBudget(ctx: Context): Int =
        if (isTokenSaverEnabled(ctx)) MEMORY_SAVER_TOTAL_TOKEN_BUDGET else MEMORY_NORMAL_TOTAL_TOKEN_BUDGET

    /** 記憶分頁各區塊收合狀態（沿用 settings_collapsed_cards 風格，預設展開） */
    fun isMemorySectionExpanded(ctx: Context, sectionKey: String): Boolean {
        val collapsed = prefs(ctx).getStringSet(KEY_MEMORY_SECTION_COLLAPSED, emptySet()) ?: emptySet()
        return sectionKey !in collapsed
    }

    fun setMemorySectionExpanded(ctx: Context, sectionKey: String, expanded: Boolean) {
        val collapsed = (prefs(ctx).getStringSet(KEY_MEMORY_SECTION_COLLAPSED, emptySet()) ?: emptySet()).toMutableSet()
        if (expanded) collapsed.remove(sectionKey) else collapsed.add(sectionKey)
        prefs(ctx).edit().putStringSet(KEY_MEMORY_SECTION_COLLAPSED, collapsed).apply()
    }

    // ==================== #315 B3 自訂 AI 助手（名字／個性 preset／自訂個性／詳細度／頭像） ====================

    enum class AssistantPersonalityPreset(val key: String, @StringRes val labelRes: Int, val promptInstruction: String) {
        DEFAULT("default", R.string.common_default, "語氣平衡自然，兼顧訊息整理與防詐提醒。"),
        CONCISE_PRO("concise_pro", R.string.prefs_persona_concise_pro, "語氣商務專業、精準俐落，直接給結論與關鍵數據，不寒暄。"),
        WARM_DETAILED("warm_detailed", R.string.prefs_persona_warm, "語氣親切溫和、有耐心，清楚解釋來龍去脈與貼心提醒。"),
        STRICT_ANTI_SCAM("strict_anti_scam", R.string.prefs_persona_anti_scam, "以資安與防詐最高標準審視訊息，主動點出可疑話術、連結與查證步驟。"),
        CUSTOM("custom", R.string.prefs_persona_custom, "");

        val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

        companion object {
            fun fromKey(raw: String?): AssistantPersonalityPreset =
                entries.firstOrNull { it.key == raw } ?: DEFAULT
        }
    }

    enum class AssistantVerbosity(val key: String, @StringRes val labelRes: Int, val promptInstruction: String) {
        CONCISE("concise", R.string.prefs_verbosity_concise, "回答極度精簡，控制在 3 句或 3 點以內。"),
        STANDARD("standard", R.string.prefs_verbosity_standard, "回答條理分明，篇幅適中，重點突出。"),
        DETAILED("detailed", R.string.common_details, "回答完整深入，提供背景脈絡、分點說明與具體建議。");

        val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

        companion object {
            fun fromKey(raw: String?): AssistantVerbosity =
                entries.firstOrNull { it.key == raw } ?: STANDARD
        }
    }

    private const val KEY_ASSISTANT_NAME = "assistant_custom_name"
    private const val KEY_ASSISTANT_PERSONALITY_PRESET = "assistant_personality_preset"
    private const val KEY_ASSISTANT_CUSTOM_PERSONALITY = "assistant_custom_personality_text"
    private const val KEY_ASSISTANT_VERBOSITY = "assistant_verbosity_mode"
    private const val KEY_ASSISTANT_HAS_AVATAR = "assistant_has_custom_avatar"

    const val DEFAULT_ASSISTANT_NAME = "幻訊"
    const val MAX_ASSISTANT_NAME_LENGTH = 20
    const val MAX_ASSISTANT_CUSTOM_PERSONALITY_CHARS = 120
    const val MAX_ASSISTANT_PERSONA_TOKEN_BUDGET = 120

    /** AI 助手名字（預設「幻訊」） */
    fun getAssistantName(ctx: Context): String =
        prefs(ctx).getString(KEY_ASSISTANT_NAME, DEFAULT_ASSISTANT_NAME)
            ?.trim()
            ?.take(MAX_ASSISTANT_NAME_LENGTH)
            ?.ifEmpty { DEFAULT_ASSISTANT_NAME }
            ?: DEFAULT_ASSISTANT_NAME

    fun setAssistantName(ctx: Context, name: String) {
        val clean = name.trim().take(MAX_ASSISTANT_NAME_LENGTH).ifEmpty { DEFAULT_ASSISTANT_NAME }
        prefs(ctx).edit().putString(KEY_ASSISTANT_NAME, clean).apply()
        ensurePartnerFirstActivatedAt(ctx)
    }

    /** #338 AI 夥伴分頁標籤文字：未自訂時 fallback「夥伴」，截斷 ≤6 字元避免破版 */
    fun getPartnerTabLabel(ctx: Context): String {
        val raw = prefs(ctx).getString(KEY_ASSISTANT_NAME, null)?.trim().orEmpty()
        if (raw.isEmpty()) return "夥伴"
        return raw.take(6)
    }

    /** #338 陪伴天數起算時間（首次開啟夥伴分頁或首次自訂助手名時記錄） */
    fun ensurePartnerFirstActivatedAt(ctx: Context, nowMs: Long = System.currentTimeMillis()): Long {
        val p = prefs(ctx)
        val existing = p.getLong("partner_first_activated_at", 0L)
        if (existing > 0L) return existing
        p.edit().putLong("partner_first_activated_at", nowMs).apply()
        return nowMs
    }

    fun getPartnerCompanionDays(ctx: Context, nowMs: Long = System.currentTimeMillis()): Int {
        val start = ensurePartnerFirstActivatedAt(ctx, nowMs)
        val diffDays = ((nowMs - start).coerceAtLeast(0L) / (24L * 60L * 60L * 1000L)).toInt()
        return (diffDays + 1).coerceAtLeast(1)
    }

    /** AI 助手個性 Preset */
    fun getAssistantPersonalityPreset(ctx: Context): AssistantPersonalityPreset =
        AssistantPersonalityPreset.fromKey(prefs(ctx).getString(KEY_ASSISTANT_PERSONALITY_PRESET, null))

    fun setAssistantPersonalityPreset(ctx: Context, preset: AssistantPersonalityPreset) {
        prefs(ctx).edit().putString(KEY_ASSISTANT_PERSONALITY_PRESET, preset.key).apply()
    }

    /** AI 助手自訂個性描述（最多 120 字） */
    fun getAssistantCustomPersonality(ctx: Context): String =
        prefs(ctx).getString(KEY_ASSISTANT_CUSTOM_PERSONALITY, "")
            ?.trim()
            ?.take(MAX_ASSISTANT_CUSTOM_PERSONALITY_CHARS)
            .orEmpty()

    fun setAssistantCustomPersonality(ctx: Context, text: String) {
        val clean = text.trim().take(MAX_ASSISTANT_CUSTOM_PERSONALITY_CHARS)
        prefs(ctx).edit().putString(KEY_ASSISTANT_CUSTOM_PERSONALITY, clean).apply()
    }

    /** AI 助手回答詳細度 */
    fun getAssistantVerbosity(ctx: Context): AssistantVerbosity =
        AssistantVerbosity.fromKey(prefs(ctx).getString(KEY_ASSISTANT_VERBOSITY, null))

    fun setAssistantVerbosity(ctx: Context, verbosity: AssistantVerbosity) {
        prefs(ctx).edit().putString(KEY_ASSISTANT_VERBOSITY, verbosity.key).apply()
    }

    /** 是否已設定自訂 AI 助手頭像 */
    fun hasAssistantAvatar(ctx: Context): Boolean =
        prefs(ctx).getBoolean(KEY_ASSISTANT_HAS_AVATAR, false)

    fun setHasAssistantAvatar(ctx: Context, hasAvatar: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ASSISTANT_HAS_AVATAR, hasAvatar).apply()
    }

    /**
     * 組裝注入 chatAssistant systemPrompt 的【助手設定】區塊（嚴格控管 ≤ 120 tokens，並套用 sanitizeForPrompt 防注入）。
     */
    fun buildAssistantPersonaPromptBlock(ctx: Context): String {
        val name = com.phantom.checker.network.AiClient.sanitizeForPrompt(
            getAssistantName(ctx).replace("\n", " ").trim()
        ).take(MAX_ASSISTANT_NAME_LENGTH)
        val preset = getAssistantPersonalityPreset(ctx)
        val customText = com.phantom.checker.network.AiClient.sanitizeForPrompt(
            getAssistantCustomPersonality(ctx).replace("\n", " ").trim()
        ).take(MAX_ASSISTANT_CUSTOM_PERSONALITY_CHARS)
        val verbosity = getAssistantVerbosity(ctx)

        val personalityDesc = when {
            preset == AssistantPersonalityPreset.CUSTOM && customText.isNotEmpty() -> customText
            customText.isNotEmpty() -> "${preset.promptInstruction}（補充：$customText）"
            else -> preset.promptInstruction
        }.take(110)

        val rawBlock = buildString {
            appendLine("【助手設定】")
            appendLine("- 你的名字：$name")
            appendLine("- 個性與風格：$personalityDesc")
            append("- 回答詳細度（${verbosity.label}）：${verbosity.promptInstruction}")
        }
        // 確保總預算 ≤ 120 tokens（約 160 中文字）
        return if (com.phantom.checker.data.MemoryEngine.estimateMemoryTokens(rawBlock) <= MAX_ASSISTANT_PERSONA_TOKEN_BUDGET) {
            rawBlock
        } else {
            rawBlock.take(160).trimEnd()
        }
    }

    // ---- #365 CalendarInsightEngine：定時日曆情報與主動發言設定（全預設開） ----

    fun isProactiveCalendarInsightEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("proactive_calendar_insight_enabled", true)

    fun setProactiveCalendarInsightEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("proactive_calendar_insight_enabled", enabled).apply()
    }

    fun isProactiveCalendarInsightNotifEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("proactive_calendar_insight_notif_enabled", true)

    fun setProactiveCalendarInsightNotifEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("proactive_calendar_insight_notif_enabled", enabled).apply()
    }

    fun isProactiveCalendarWebSearchEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("proactive_calendar_web_search_enabled", true)

    fun setProactiveCalendarWebSearchEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("proactive_calendar_web_search_enabled", enabled).apply()
    }

    /** 日曆情報檢查頻率（分鐘）：30 / 60 / 120 / 180，預設 60 */
    fun getCalendarInsightIntervalMinutes(ctx: Context): Int =
        prefs(ctx).getInt("calendar_insight_interval_minutes", 60).let { v ->
            if (v in listOf(30, 60, 120, 180)) v else 60
        }

    fun setCalendarInsightIntervalMinutes(ctx: Context, minutes: Int) {
        val safe = if (minutes in listOf(30, 60, 120, 180)) minutes else 60
        prefs(ctx).edit().putInt("calendar_insight_interval_minutes", safe).apply()
    }

    fun hasCalendarInsightSeenKey(ctx: Context, key: String): Boolean {
        val raw = prefs(ctx).getString("calendar_insight_seen_keys_v1", "").orEmpty()
        if (raw.isEmpty()) return false
        return raw.split('\n').any { it == key }
    }

    fun markCalendarInsightSeenKey(ctx: Context, key: String) {
        val raw = prefs(ctx).getString("calendar_insight_seen_keys_v1", "").orEmpty()
        val list = raw.split('\n').filter { it.isNotBlank() }.toMutableList()
        if (key in list) return
        list.add(key)
        val trimmed = if (list.size > 200) list.takeLast(200) else list
        prefs(ctx).edit().putString("calendar_insight_seen_keys_v1", trimmed.joinToString("\n")).apply()
    }

    // ---- #366 主動訊息聊天室、對話自動記憶擷取、未決事項膠囊列與壓縮錨點 ----

    fun getProactiveChatLastReadAt(ctx: Context): Long =
        prefs(ctx).getLong("proactive_chat_last_read_at", 0L)

    fun setProactiveChatLastReadAt(ctx: Context, ts: Long) {
        prefs(ctx).edit().putLong("proactive_chat_last_read_at", ts).apply()
    }

    fun isMemoryAutoExtractEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("memory_auto_extract_enabled", true)

    fun setMemoryAutoExtractEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("memory_auto_extract_enabled", enabled).apply()
    }

    fun isChatPendingCapsulesEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("chat_pending_capsules_enabled", true)

    fun setChatPendingCapsulesEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("chat_pending_capsules_enabled", enabled).apply()
    }

    fun saveChatContinuityAnchor(ctx: Context, text: String, nowMs: Long = System.currentTimeMillis()) {
        prefs(ctx).edit()
            .putString("chat_continuity_anchor_text", text.trim())
            .putLong("chat_continuity_anchor_at", nowMs)
            .putBoolean("chat_continuity_anchor_dismissed", false)
            .apply()
    }

    /** 取得未過期（7 天內）且未手動關閉的壓縮錨點，過期則回傳 null */
    fun getActiveChatContinuityAnchor(ctx: Context, nowMs: Long = System.currentTimeMillis()): String? {
        val p = prefs(ctx)
        if (p.getBoolean("chat_continuity_anchor_dismissed", false)) return null
        val text = p.getString("chat_continuity_anchor_text", "").orEmpty().trim()
        if (text.isEmpty()) return null
        val createdAt = p.getLong("chat_continuity_anchor_at", 0L)
        val sevenDaysMs = 7L * 24L * 60L * 60L * 1000L
        if (createdAt <= 0L || (nowMs - createdAt) > sevenDaysMs) return null
        return text
    }

    fun dismissChatContinuityAnchor(ctx: Context) {
        prefs(ctx).edit().putBoolean("chat_continuity_anchor_dismissed", true).apply()
    }

    // ---- #367 雙門檻自動決策閾值與判錯回饋統計 ----

    fun getDecisionConfirmThreshold(ctx: Context): Int =
        prefs(ctx).getInt("decision_confirm_threshold", DecisionEngine.DEFAULT_CONFIRM_BELOW_SCORE).coerceIn(40, 85)

    fun setDecisionConfirmThreshold(ctx: Context, threshold: Int) {
        prefs(ctx).edit().putInt("decision_confirm_threshold", threshold.coerceIn(40, 85)).apply()
    }

    fun recordDecisionFeedback(ctx: Context, errorType: String) {
        val p = prefs(ctx)
        val total = p.getInt("decision_feedback_total", 0) + 1
        val typeKey = "decision_feedback_type_$errorType"
        val typeCount = p.getInt(typeKey, 0) + 1
        p.edit()
            .putInt("decision_feedback_total", total)
            .putInt(typeKey, typeCount)
            .apply()
    }

    fun getDecisionFeedbackTotalCount(ctx: Context): Int =
        prefs(ctx).getInt("decision_feedback_total", 0)

    // ---- #375 發送者 30 天輕量信譽統計（零 DB migration，自動淘汰逾 30 天與超過 200 筆） ----

    private const val KEY_SENDER_REPUTATION_30D = "sender_reputation_30d"
    private const val SENDER_REPUTATION_WINDOW_MS = 30L * 24L * 60L * 60L * 1000L
    private const val SENDER_REPUTATION_TRUST_THRESHOLD = 10
    private const val MAX_SENDER_REPUTATION_ENTRIES = 200

    private fun senderReputationKey(packageName: String, sender: String): String =
        "${packageName.trim().lowercase(Locale.ROOT)}|${sender.trim().lowercase(Locale.ROOT)}"
            .replace("\n", " ")
            .replace("=", "_")

    fun recordSenderLowRiskMessage(
        ctx: Context,
        packageName: String,
        sender: String,
        nowMs: Long = System.currentTimeMillis(),
        bonus: Int = 1
    ) {
        if (sender.isBlank()) return
        val k = senderReputationKey(packageName, sender)
        val cutoff = nowMs - SENDER_REPUTATION_WINDOW_MS
        val p = prefs(ctx)
        val map = LinkedHashMap<String, Pair<Int, Long>>()
        p.getString(KEY_SENDER_REPUTATION_30D, "").orEmpty().lineSequence().forEach { line ->
            val eq = line.lastIndexOf('=')
            if (eq <= 0) return@forEach
            val keyPart = line.substring(0, eq)
            val valParts = line.substring(eq + 1).split(':')
            val count = valParts.getOrNull(0)?.toIntOrNull() ?: return@forEach
            val ts = valParts.getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (ts >= cutoff) {
                map[keyPart] = count to ts
            }
        }
        val prev = map.remove(k)?.first ?: 0
        map[k] = (prev + bonus.coerceAtLeast(1)).coerceAtMost(9999) to nowMs
        val entries = map.entries.toList()
        val trimmed = if (entries.size > MAX_SENDER_REPUTATION_ENTRIES) {
            entries.takeLast(MAX_SENDER_REPUTATION_ENTRIES)
        } else {
            entries
        }
        val encoded = trimmed.joinToString("\n") { (key, v) -> "$key=${v.first}:${v.second}" }
        p.edit().putString(KEY_SENDER_REPUTATION_30D, encoded).apply()
    }

    fun getSenderLowRiskCount30d(
        ctx: Context,
        packageName: String,
        sender: String,
        nowMs: Long = System.currentTimeMillis()
    ): Int {
        if (sender.isBlank()) return 0
        val k = senderReputationKey(packageName, sender)
        val cutoff = nowMs - SENDER_REPUTATION_WINDOW_MS
        val raw = prefs(ctx).getString(KEY_SENDER_REPUTATION_30D, "").orEmpty()
        if (raw.isBlank()) return 0
        raw.lineSequence().forEach { line ->
            val eq = line.lastIndexOf('=')
            if (eq <= 0) return@forEach
            if (line.substring(0, eq) == k) {
                val valParts = line.substring(eq + 1).split(':')
                val count = valParts.getOrNull(0)?.toIntOrNull() ?: 0
                val ts = valParts.getOrNull(1)?.toLongOrNull() ?: 0L
                return if (ts >= cutoff) count else 0
            }
        }
        return 0
    }

    fun isTrustedSenderHistory(
        ctx: Context,
        packageName: String,
        sender: String,
        hasKnownAvatar: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        if (hasKnownAvatar) return true
        return getSenderLowRiskCount30d(ctx, packageName, sender, nowMs) >= SENDER_REPUTATION_TRUST_THRESHOLD
    }

    // ---- #368 連結應用中心動作登錄檔：同步時間與靜默錯誤狀態 ----

    fun getLinkedAppLastSyncAt(ctx: Context, connectorId: String): Long =
        prefs(ctx).getLong("linked_app_last_sync_$connectorId", 0L)

    fun setLinkedAppLastSyncAt(ctx: Context, connectorId: String, ts: Long) {
        prefs(ctx).edit().putLong("linked_app_last_sync_$connectorId", ts).apply()
    }

    fun getLinkedAppLastError(ctx: Context, connectorId: String): String =
        prefs(ctx).getString("linked_app_last_err_$connectorId", "").orEmpty()

    fun setLinkedAppLastError(ctx: Context, connectorId: String, err: String) {
        prefs(ctx).edit().putString("linked_app_last_err_$connectorId", err.take(120)).apply()
    }

    // ---- #378 AI 日記／每日回顧 ----

    fun isDiaryDailyEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("diary_daily_enabled", true)

    fun setDiaryDailyEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("diary_daily_enabled", enabled).apply()
    }

    fun getDiaryAnnotationDislikeCount(ctx: Context, category: String): Int =
        prefs(ctx).getInt("diary_annotation_dislike_${category.lowercase(Locale.ROOT)}", 0)

    fun incrementDiaryAnnotationDislike(ctx: Context, category: String) {
        val key = "diary_annotation_dislike_${category.lowercase(Locale.ROOT)}"
        val p = prefs(ctx)
        p.edit().putInt(key, p.getInt(key, 0) + 1).apply()
    }

    /** #421 重置指定類別的回顧註記不準降權計數 */
    fun resetDiaryAnnotationDislike(ctx: Context, category: String) {
        val key = "diary_annotation_dislike_${category.lowercase(Locale.ROOT)}"
        prefs(ctx).edit().remove(key).apply()
    }

    /** #421 省 token 模式下是否仍允許生成每日回顧 AI 註記（預設關閉；開啟將耗用少量 token） */
    fun isDiaryAnnotationInSaverEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("diary_annotation_in_saver", false)

    fun setDiaryAnnotationInSaverEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("diary_annotation_in_saver", enabled).apply()
    }

    // ---- #379 長程目標追蹤 ----

    fun isGoalTrackingEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("goal_tracking_enabled", true)

    fun setGoalTrackingEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("goal_tracking_enabled", enabled).apply()
    }

    fun isGoalCandidateDismissed(ctx: Context, messageHash: String): Boolean {
        val set = prefs(ctx).getStringSet("goal_dismissed_hashes", emptySet()).orEmpty()
        return messageHash in set
    }

    fun markGoalCandidateDismissed(ctx: Context, messageHash: String) {
        if (messageHash.isBlank()) return
        val p = prefs(ctx)
        val next = p.getStringSet("goal_dismissed_hashes", emptySet()).orEmpty().toMutableSet()
        next.add(messageHash)
        val trimmed = if (next.size > 100) next.toList().takeLast(100).toSet() else next
        p.edit().putStringSet("goal_dismissed_hashes", trimmed).apply()
    }

    // ---- #381 Health Connect 生理數據整合 ----

    fun isHealthBriefingEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("health_briefing_enabled", true)

    fun setHealthBriefingEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("health_briefing_enabled", enabled).apply()
    }

    fun isHealthSoftInviteEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("health_soft_invite_enabled", true)

    fun setHealthSoftInviteEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("health_soft_invite_enabled", enabled).apply()
    }

    /** #403 允許 AI 讀取健康摘要（預設開啟；關閉時不注入聊天 <health_context>，亦不執行主動健康觀察） */
    fun isHealthAiQueryEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("health_ai_query_enabled", true)

    fun setHealthAiQueryEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("health_ai_query_enabled", enabled).apply()
    }

    /** #403 記錄與檢查每條主動健康觀察規則近 7 日已投遞次數（每規則每週至多 2 次） */
    fun getHealthRuleWeeklyCount(ctx: Context, ruleId: String, nowMs: Long = System.currentTimeMillis()): Int {
        val p = prefs(ctx)
        val raw = p.getString("health_proactive_weekly_history", "").orEmpty()
        if (raw.isBlank()) return 0
        val cutoff = nowMs - 7L * 24L * 60L * 60L * 1000L
        var count = 0
        val kept = mutableListOf<String>()
        raw.lineSequence().forEach { line ->
            val parts = line.split('|')
            if (parts.size < 2) return@forEach
            val rId = parts[0]
            val ts = parts[1].toLongOrNull() ?: return@forEach
            if (ts >= cutoff) {
                kept.add(line)
                if (rId == ruleId) count++
            }
        }
        val normalized = kept.joinToString("\n")
        if (normalized != raw) {
            p.edit().putString("health_proactive_weekly_history", normalized).apply()
        }
        return count
    }

    fun recordHealthRuleDelivered(ctx: Context, ruleId: String, nowMs: Long = System.currentTimeMillis()) {
        val p = prefs(ctx)
        val raw = p.getString("health_proactive_weekly_history", "").orEmpty()
        val cutoff = nowMs - 7L * 24L * 60L * 60L * 1000L
        val kept = raw.lineSequence()
            .filter { line ->
                val parts = line.split('|')
                val ts = parts.getOrNull(1)?.toLongOrNull() ?: return@filter false
                ts >= cutoff
            }
            .toMutableList()
        kept.add("$ruleId|$nowMs")
        val trimmed = if (kept.size > 60) kept.takeLast(60) else kept
        p.edit().putString("health_proactive_weekly_history", trimmed.joinToString("\n")).apply()
    }

    /** 未授權 Health Connect 時每 7 天最多一次軟性邀請 */
    fun shouldShowHealthSoftInvite(ctx: Context, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!isHealthBriefingEnabled(ctx) || !isHealthSoftInviteEnabled(ctx)) return false
        val last = prefs(ctx).getLong("health_last_soft_invite_ms", 0L)
        return (nowMs - last) >= 7L * 24L * 60L * 60L * 1000L
    }

    fun markHealthSoftInviteShown(ctx: Context, nowMs: Long = System.currentTimeMillis()) {
        prefs(ctx).edit().putLong("health_last_soft_invite_ms", nowMs).apply()
    }

    /** #404 主動通知頻道（assistant_briefing）被停用時的一次性聊天室頂部引導卡開關 */
    fun isBriefingChannelGuideDismissed(ctx: Context): Boolean =
        prefs(ctx).getBoolean("briefing_channel_guide_dismissed", false)

    fun setBriefingChannelGuideDismissed(ctx: Context, dismissed: Boolean) {
        prefs(ctx).edit().putBoolean("briefing_channel_guide_dismissed", dismissed).apply()
    }

    // ---- #408 APK 更新下載狀態持久化（downloadId + version + fileName + 防重自動安裝 flag） ----

    fun saveActiveApkDownload(ctx: Context, downloadId: Long, version: String, fileName: String) {
        prefs(ctx).edit()
            .putLong("update_apk_download_id", downloadId)
            .putString("update_apk_download_version", version.trim())
            .putString("update_apk_download_file_name", fileName.trim())
            .putBoolean("update_apk_auto_install_launched", false)
            .apply()
    }

    fun getActiveApkDownloadId(ctx: Context): Long =
        prefs(ctx).getLong("update_apk_download_id", -1L)

    fun getActiveApkDownloadVersion(ctx: Context): String =
        prefs(ctx).getString("update_apk_download_version", "").orEmpty().trim()

    fun getActiveApkDownloadFileName(ctx: Context): String =
        prefs(ctx).getString("update_apk_download_file_name", "").orEmpty().trim()

    fun isActiveApkAutoInstallLaunched(ctx: Context): Boolean =
        prefs(ctx).getBoolean("update_apk_auto_install_launched", false)

    fun setActiveApkAutoInstallLaunched(ctx: Context, launched: Boolean) {
        prefs(ctx).edit().putBoolean("update_apk_auto_install_launched", launched).apply()
    }

    fun clearActiveApkDownload(ctx: Context) {
        prefs(ctx).edit()
            .remove("update_apk_download_id")
            .remove("update_apk_download_version")
            .remove("update_apk_download_file_name")
            .remove("update_apk_auto_install_launched")
            .apply()
    }

    // ---- #438 App 內語言切換（跟隨系統／繁體中文／English） ----

    const val APP_LOCALE_SYSTEM = "system"
    const val APP_LOCALE_ZH_TW = "zh-TW"
    const val APP_LOCALE_EN = "en"
    private const val KEY_APP_LANGUAGE_MODE = "app_language_mode"

    fun normalizeAppLanguageMode(raw: String?): String = when (raw?.trim()) {
        APP_LOCALE_ZH_TW -> APP_LOCALE_ZH_TW
        APP_LOCALE_EN -> APP_LOCALE_EN
        else -> APP_LOCALE_SYSTEM
    }

    fun getAppLanguageMode(ctx: Context): String =
        normalizeAppLanguageMode(prefs(ctx).getString(KEY_APP_LANGUAGE_MODE, APP_LOCALE_SYSTEM))

    fun setAppLanguageMode(ctx: Context, mode: String, notify: Boolean = true) {
        val normalized = normalizeAppLanguageMode(mode)
        prefs(ctx).edit().putString(KEY_APP_LANGUAGE_MODE, normalized).apply()
        if (notify) {
            PhantomSettingsBus.notifyChanged()
        }
    }

    // ---- #501 Filters & Focus collapsible sections, 1h focus mute, and Settings advanced collapse ----

    fun isFilterSectionExpanded(ctx: Context, sectionKey: String, defaultExpanded: Boolean = true): Boolean =
        prefs(ctx).getBoolean("filter_sec_exp_$sectionKey", defaultExpanded)

    fun setFilterSectionExpanded(ctx: Context, sectionKey: String, expanded: Boolean) {
        prefs(ctx).edit().putBoolean("filter_sec_exp_$sectionKey", expanded).apply()
    }

    fun getFocusMuteUntilMs(ctx: Context): Long =
        prefs(ctx).getLong("focus_mute_until_ms", 0L)

    fun setFocusMuteUntilMs(ctx: Context, untilMs: Long) {
        prefs(ctx).edit().putLong("focus_mute_until_ms", untilMs).apply()
        PhantomSettingsBus.notifyChanged()
    }

    fun isSettingsAdvancedExpanded(ctx: Context): Boolean =
        prefs(ctx).getBoolean("settings_home_advanced_expanded", false)

    fun setSettingsAdvancedExpanded(ctx: Context, expanded: Boolean) {
        prefs(ctx).edit().putBoolean("settings_home_advanced_expanded", expanded).apply()
    }

    // ---- #518 Cross-End Clipboard Sync (opt-in, default OFF, OTP guard ON) ----

    fun isClipboardSyncEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("cross_end_clipboard_sync_enabled", false)

    fun setClipboardSyncEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("cross_end_clipboard_sync_enabled", enabled).apply()
        PhantomSettingsBus.notifyChanged()
    }

    fun isClipboardOtpGuardEnabled(ctx: Context): Boolean =
        prefs(ctx).getBoolean("cross_end_clipboard_otp_guard", true)

    fun setClipboardOtpGuardEnabled(ctx: Context, enabled: Boolean) {
        prefs(ctx).edit().putBoolean("cross_end_clipboard_otp_guard", enabled).apply()
    }

    fun getClipboardMaxChars(ctx: Context): Int =
        prefs(ctx).getInt("cross_end_clipboard_max_chars", 5000).coerceIn(500, 5000)
}



