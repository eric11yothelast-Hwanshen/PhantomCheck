package com.phantom.checker.ui
import androidx.annotation.StringRes
import com.phantom.checker.R

/**
 * v4.0.0 設定中心 8 大分類定義（§5.2 ＋ 使用者裁定：個人化與外觀合併）。
 */
enum class SettingsCategory(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val subtitleHintRes: Int,
) {
    QUICK("quick", R.string.settings_cat_quick_title, R.string.settings_cat_quick_subtitle),
    AI("ai", R.string.settings_cat_ai_title, R.string.settings_cat_ai_subtitle),
    NOTIF("notif", R.string.settings_cat_notif_title, R.string.settings_cat_notif_subtitle),
    ASSISTANT("assistant", R.string.settings_cat_assistant_title, R.string.settings_cat_assistant_subtitle),
    PERMISSIONS("permissions", R.string.settings_cat_permissions_title, R.string.settings_cat_permissions_subtitle),
    APPEARANCE("appearance", R.string.settings_cat_appearance_title, R.string.settings_cat_appearance_subtitle),
    DATA("data", R.string.settings_cat_data_title, R.string.settings_cat_data_subtitle),
    ABOUT("about", R.string.settings_cat_about_title, R.string.settings_cat_about_subtitle),
    ;

    val title: String get() = com.phantom.checker.PhantomApplication.str(titleRes)
    val subtitleHint: String get() = com.phantom.checker.PhantomApplication.str(subtitleHintRes)
}

/**
 * 單一設定項在新 IA 下的完整路由目標（分類、錨點、是否位於進階折疊區、是否跳轉獨立子 Activity）。
 */
data class SettingRoute(
    val id: String,
    val category: SettingsCategory,
    val anchor: String,
    val isAdvanced: Boolean,
    val isFoldedByDefault: Boolean,
    val activityPage: String? = null,
)

data class CatalogConsistencyReport(
    val totalItems: Int,
    val mappedCount: Int,
    val duplicateIds: List<String>,
    val unmappedIds: List<String>,
    val missingAdvancedIds: List<String>,
    val missingDefaultPinnedIds: List<String>,
    val emptyCategories: List<SettingsCategory>,
) {
    val isValid: Boolean
        get() = totalItems > 0 &&
            mappedCount == totalItems &&
            duplicateIds.isEmpty() &&
            unmappedIds.isEmpty() &&
            missingAdvancedIds.isEmpty() &&
            missingDefaultPinnedIds.isEmpty() &&
            emptyCategories.isEmpty()
}

/**
 * 設定目錄分類對照表與一致性驗證（#409 Commit 2）。
 *
 * 鐵律：
 * - 不修改 SettingsCatalog.ITEMS 任何既有項目與 searchByKeywords 演算法（#405/#406）。
 * - 136 個 catalog id 全數映射到 (SettingsCategory, anchor)，任何未映射皆視為一致性失敗。
 * - 附錄 A 裁定 24 項歸進階；Shizuku 依 §5.1/§5.4 預設折疊於「權限與連結」進階區，搜尋點入自動展開。
 */
object SettingsCategoryRegistry {

    /** 附錄 A 裁定之 24 項進階設定 id */
    val ADVANCED_IDS: Set<String> = setOf(
        "ai_daily_limit",
        "ai_min_trigger_length",
        "notif_throttle",
        "appearance_keepalive",
        "proactive_intel_freq_hours",
        "personal_time_format",
        "chat_font_scale",
        "messages_font_scale",
        "chat_bubble_corner_dp",
        "chat_bubble_width_ratio",
        "favorite_apps_max_visible",
        "stats_hourly",
        "stats_unread_ratio",
        "memory_normal_top_k",
        "memory_normal_per_item_chars",
        "memory_saver_top_k",
        "proactive_daily_cap",
        "briefing_daily_cap",
        "proactive_allday_remind_time",
        "proactive_monitor_interval_minutes",
        "proactive_dedup_window_minutes",
        "calendar_insight_interval_minutes",
        "decision_confirm_threshold",
        "update_snooze_minutes",
    )

    /** 預設折疊於分類頁「進階設定」區塊的項目（24 項進階 ＋ Shizuku 進階） */
    val FOLDED_BY_DEFAULT_IDS: Set<String> = ADVANCED_IDS + "shizuku_mode"

    fun resolveCategory(item: SettingSearchItem): SettingsCategory {
        if (item.id == "ai_scam_center") return SettingsCategory.DATA
        return when (item.cardKey) {
            "quick" -> SettingsCategory.QUICK
            "ai" -> SettingsCategory.AI
            "notif" -> SettingsCategory.NOTIF
            "intel", "memory_assistant" -> SettingsCategory.ASSISTANT
            "shizuku" -> SettingsCategory.PERMISSIONS
            "hub" -> when (item.page) {
                "PermissionHubActivity", "LinkedAppsActivity" -> SettingsCategory.PERMISSIONS
                else -> SettingsCategory.ASSISTANT
            }
            "personalization", "appearance", "stats" -> SettingsCategory.APPEARANCE
            "data" -> SettingsCategory.DATA
            "about" -> SettingsCategory.ABOUT
            else -> error("Unmapped SettingsCatalog cardKey='${item.cardKey}' for id='${item.id}'")
        }
    }

    /** 舊版 highlight_card_key 深連結 → 新版 8 分類相容映射 */
    fun categoryForLegacyCardKey(cardKey: String?): SettingsCategory? = when (cardKey) {
        "quick" -> SettingsCategory.QUICK
        "ai" -> SettingsCategory.AI
        "notif" -> SettingsCategory.NOTIF
        "hub", "assistant_hub", "memory_assistant", "intel" -> SettingsCategory.ASSISTANT
        "permission_hub", "shizuku", "linked_apps" -> SettingsCategory.PERMISSIONS
        "personalization", "appearance", "stats" -> SettingsCategory.APPEARANCE
        "data", "whitelist" -> SettingsCategory.DATA
        "about" -> SettingsCategory.ABOUT
        else -> null
    }

    /** 全量 136 項路由表（id -> SettingRoute） */
    val ROUTES: Map<String, SettingRoute> by lazy {
        SettingsCatalog.ITEMS.associate { item ->
            val category = resolveCategory(item)
            val isAdv = item.id in ADVANCED_IDS
            val isFolded = item.id in FOLDED_BY_DEFAULT_IDS
            item.id to SettingRoute(
                id = item.id,
                category = category,
                anchor = "${category.id}:${item.cardKey}:${item.id}",
                isAdvanced = isAdv,
                isFoldedByDefault = isFolded,
                activityPage = item.page,
            )
        }
    }

    fun routeFor(settingId: String): SettingRoute? = ROUTES[settingId]

    fun categoryFor(settingId: String): SettingsCategory? = ROUTES[settingId]?.category

    fun isAdvanced(settingId: String): Boolean = settingId in ADVANCED_IDS

    fun isFoldedByDefault(settingId: String): Boolean = settingId in FOLDED_BY_DEFAULT_IDS

    fun itemsInCategory(category: SettingsCategory): List<SettingSearchItem> =
        SettingsCatalog.ITEMS.filter { ROUTES[it.id]?.category == category }

    fun commonItemsInCategory(category: SettingsCategory): List<SettingSearchItem> =
        itemsInCategory(category).filterNot { it.id in FOLDED_BY_DEFAULT_IDS }

    fun advancedItemsInCategory(category: SettingsCategory): List<SettingSearchItem> =
        itemsInCategory(category).filter { it.id in FOLDED_BY_DEFAULT_IDS }

    /**
     * 一致性自檢：斷言所有 SettingsCatalog.ITEMS id 皆能解析出 (category, anchor)，
     * 且 24 項進階 id、6 項預設釘選 id 皆存在於 catalog 中。
     */
    fun verifyCatalogConsistency(): CatalogConsistencyReport {
        val items = SettingsCatalog.ITEMS
        val idCounts = items.groupingBy { it.id }.eachCount()
        val duplicates = idCounts.filterValues { it > 1 }.keys.toList()
        val allIds = items.map { it.id }.toSet()
        val unmapped = items.filter { ROUTES[it.id] == null }.map { it.id }
        val missingAdvanced = ADVANCED_IDS.filterNot { it in allIds }
        val missingPinned = UiLayoutPrefs.DEFAULT_PINNED_IDS.filterNot { it in allIds }
        val emptyCats = SettingsCategory.entries.filter { itemsInCategory(it).isEmpty() }
        return CatalogConsistencyReport(
            totalItems = items.size,
            mappedCount = items.size - unmapped.size,
            duplicateIds = duplicates,
            unmappedIds = unmapped,
            missingAdvancedIds = missingAdvanced,
            missingDefaultPinnedIds = missingPinned,
            emptyCategories = emptyCats,
        )
    }
}
