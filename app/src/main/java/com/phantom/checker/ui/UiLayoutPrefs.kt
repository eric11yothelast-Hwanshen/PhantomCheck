package com.phantom.checker.ui

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf

/**
 * v4.0.0 Phantom Aurora II 純 UI 佈局偏好（#409）。
 *
 * 隱私與相容保證：
 * - 位於 ui 套件，獨立使用 `phantom_ui_v400` SharedPreferences，不動 util/PhantomPrefs。
 * - 僅儲存設定項目 id 字串與 UI 旗標，絕不儲存任何訊息內容或個資。
 * - 可隨時於設定中清除最近使用或重設釘選；解除安裝即清除。
 */
object UiLayoutPrefs {
    private const val PREFS_NAME = "phantom_ui_v400"
    private const val KEY_PINNED_IDS = "pinned_setting_ids"
    private const val KEY_PINNED_INITIALIZED = "pinned_initialized"
    private const val KEY_RECENT_SETTING_IDS = "recent_setting_ids"
    private const val KEY_RECENT_SEARCHES = "recent_global_searches"
    private const val KEY_V4_TOUR_PROMPT_SHOWN = "v4_tour_prompt_shown"
    private const val KEY_ANIM_MODE = "anim_mode"
    private const val DELIMITER = "\u001F"

    const val ANIM_MODE_BALANCED = "balanced"
    const val ANIM_MODE_POWER_SAVER = "power_saver"

    const val MAX_PINNED = 6
    const val MAX_RECENT_SETTINGS = 5
    const val MAX_RECENT_SEARCHES = 8

    /** 裁定預設 6 個釘選項目（對應 SettingsCatalog id） */
    val DEFAULT_PINNED_IDS: List<String> = listOf(
        "quick_token_saver",
        "quick_persistent_notif",
        "quick_ai_risk",
        "quick_scam_popup",
        "notif_quiet_hours",
        "ai_providers",
    )

    private val _version = mutableIntStateOf(0)
    val version: Int
        get() = _version.intValue

    private fun bump() {
        _version.intValue = _version.intValue + 1
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun decodeList(raw: String?): List<String> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.split(DELIMITER).filter { it.isNotBlank() }
    }

    private fun encodeList(items: List<String>): String =
        items.filter { it.isNotBlank() }.joinToString(DELIMITER)

    // ── 釘選設定（≤6，超限不自動踢） ──

    fun getPinnedIds(context: Context): List<String> {
        val p = prefs(context)
        if (!p.getBoolean(KEY_PINNED_INITIALIZED, false)) {
            return DEFAULT_PINNED_IDS
        }
        return decodeList(p.getString(KEY_PINNED_IDS, null)).take(MAX_PINNED)
    }

    fun isPinned(context: Context, settingId: String): Boolean =
        getPinnedIds(context).contains(settingId)

    /**
     * 新增釘選。若已達上限 [MAX_PINNED] 回傳 false（不自動踢除既有項，由呼叫端提示「請先取消一項」）。
     */
    fun addPinned(context: Context, settingId: String): Boolean {
        val current = getPinnedIds(context).toMutableList()
        if (current.contains(settingId)) return true
        if (current.size >= MAX_PINNED) return false
        current.add(settingId)
        prefs(context).edit()
            .putBoolean(KEY_PINNED_INITIALIZED, true)
            .putString(KEY_PINNED_IDS, encodeList(current))
            .apply()
        bump()
        return true
    }

    fun removePinned(context: Context, settingId: String) {
        val current = getPinnedIds(context).filterNot { it == settingId }
        prefs(context).edit()
            .putBoolean(KEY_PINNED_INITIALIZED, true)
            .putString(KEY_PINNED_IDS, encodeList(current))
            .apply()
        bump()
    }

    fun resetPinnedToDefaults(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_PINNED_INITIALIZED, true)
            .putString(KEY_PINNED_IDS, encodeList(DEFAULT_PINNED_IDS))
            .apply()
        bump()
    }

    // ── 最近使用設定（≤5，FIFO 去重） ──

    fun getRecentSettingIds(context: Context): List<String> =
        decodeList(prefs(context).getString(KEY_RECENT_SETTING_IDS, null)).take(MAX_RECENT_SETTINGS)

    fun recordRecentSetting(context: Context, settingId: String) {
        if (settingId.isBlank()) return
        val updated = buildList {
            add(settingId)
            getRecentSettingIds(context).forEach { if (it != settingId) add(it) }
        }.take(MAX_RECENT_SETTINGS)
        prefs(context).edit()
            .putString(KEY_RECENT_SETTING_IDS, encodeList(updated))
            .apply()
        bump()
    }

    fun clearRecentSettings(context: Context) {
        prefs(context).edit()
            .remove(KEY_RECENT_SETTING_IDS)
            .apply()
        bump()
    }

    // ── 全域最近搜尋詞（≤8，可逐條刪或全部清除） ──

    fun getRecentSearches(context: Context): List<String> =
        decodeList(prefs(context).getString(KEY_RECENT_SEARCHES, null)).take(MAX_RECENT_SEARCHES)

    fun recordRecentSearch(context: Context, query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val updated = buildList {
            add(trimmed)
            getRecentSearches(context).forEach { if (!it.equals(trimmed, ignoreCase = true)) add(it) }
        }.take(MAX_RECENT_SEARCHES)
        prefs(context).edit()
            .putString(KEY_RECENT_SEARCHES, encodeList(updated))
            .apply()
        bump()
    }

    fun removeRecentSearch(context: Context, query: String) {
        val updated = getRecentSearches(context).filterNot { it == query }
        prefs(context).edit()
            .putString(KEY_RECENT_SEARCHES, encodeList(updated))
            .apply()
        bump()
    }

    fun clearRecentSearches(context: Context) {
        prefs(context).edit()
            .remove(KEY_RECENT_SEARCHES)
            .apply()
        bump()
    }

    // ── v4.0 新版導覽提示旗標（不重置舊版教學完成旗標） ──

    fun hasSeenV4TourPrompt(context: Context): Boolean =
        prefs(context).getBoolean(KEY_V4_TOUR_PROMPT_SHOWN, false)

    fun setSeenV4TourPrompt(context: Context, seen: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_V4_TOUR_PROMPT_SHOWN, seen)
            .apply()
        bump()
    }

    // ── #520 v5.0.0 全域動畫模式（平衡 / 省電，寫入即 bump() 全域生效） ──

    fun getAnimationMode(context: Context): String =
        prefs(context).getString(KEY_ANIM_MODE, ANIM_MODE_BALANCED)
            ?.takeIf { it == ANIM_MODE_BALANCED || it == ANIM_MODE_POWER_SAVER }
            ?: ANIM_MODE_BALANCED

    fun setAnimationMode(context: Context, mode: String) {
        val normalized = if (mode == ANIM_MODE_POWER_SAVER) ANIM_MODE_POWER_SAVER else ANIM_MODE_BALANCED
        prefs(context).edit()
            .putString(KEY_ANIM_MODE, normalized)
            .apply()
        bump()
    }
}
