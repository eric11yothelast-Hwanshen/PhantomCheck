package com.phantom.checker.ui

import android.content.Context
import com.phantom.checker.service.DailySummaryPolicy
import com.phantom.checker.service.HeartbeatPolicy
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.util.PhantomPrefs

data class SettingSearchItem(
    val id: String,
    val cardKey: String,
    val title: String,
    val description: String,
    val aliases: List<String>,
    val page: String? = null
)

/**
 * 設定項目單一來源（SettingsActivity 搜尋與 ChatScreen 對話調整設定共用）。
 */
object SettingsCatalog {

    val ITEMS: List<SettingSearchItem> = listOf(
        SettingSearchItem("quick_token_saver", "quick", "省 token 模式", "暫停每則 AI 掃描與每日摘要排程，僅命中可疑特徵才分析", listOf("省電", "省錢", "節流", "api", "token", "額度")),
        SettingSearchItem("quick_persistent_notif", "quick", "常駐統計通知", "通知欄即時統計與 App 圖示列", listOf("通知", "常駐", "狀態列", "統計通知", "背景")),
        SettingSearchItem("quick_ai_risk", "quick", "AI 風險偵測", "自動分析新通知是否為詐騙或可疑訊息", listOf("防詐", "風險", "偵測", "ai", "掃描")),
        SettingSearchItem("quick_scam_popup", "quick", "詐騙警示彈出通知", "高風險訊息即時彈出警示通知", listOf("詐騙", "警示", "推播", "高風險", "alert")),
        SettingSearchItem("ai_auto_tag", "quick", "AI 自動標籤", "依訊息內容自動套用 AI 管理標籤", listOf("標籤", "自動分類", "tag")),
        SettingSearchItem("ai_providers", "ai", "多 Provider AI 設定", "管理 OpenAI / Gemini / Groq / 自訂端點與 API Key", listOf("key", "apikey", "模型", "openai", "gemini", "provider", "金鑰"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_chat_suggestions", "ai", "AI 助手建議問題", "在 AI 助手分頁顯示快速提問與昨日簡報建議", listOf("建議問題", "快速提問", "昨日簡報", "助手")),
        SettingSearchItem("ai_daily_limit", "ai", "每日 AI 額度上限", "限制每日 AI 呼叫次數", listOf("額度", "上限", "次數", "quota", "limit")),
        SettingSearchItem("ai_chat_rag_limit", "ai", "AI 助手單次閱讀訊息數", "調整 AI 助手每次讀取的歷史訊息上限（50–800 則；省 token 模式降為 100）", listOf("閱讀", "筆數", "訊息數", "rag", "context", "上下文", "400", "上限")),
        SettingSearchItem("ai_daily_summary", "ai", "每日 AI 摘要與排程", "設定每日自動生成摘要時間與摘要通知", listOf("摘要", "日報", "每日", "排程", "summary")),
        SettingSearchItem("ai_auto_reply", "ai", "SMS 自動回覆設定", "開啟 SMS 自動回覆設定頁：啟用開關、回覆時段與回覆範本", listOf("自動回覆", "sms", "簡訊", "回覆", "時段", "reply"), page = "AutoReplySettingsActivity"),
        SettingSearchItem("ai_scam_center", "ai", "詐騙警示中心", "檢視高風險詐騙記錄與處置", listOf("詐騙", "警示", "中心", "高風險", "封鎖"), page = "ScamCenterActivity"),
        SettingSearchItem("ai_alert_keywords", "ai", "即時提醒關鍵字", "設定自訂即時提醒關鍵字，命中時觸發通知提醒", listOf("關鍵字", "提醒", "keyword")),
        SettingSearchItem("notif_style", "notif", "通知列圖示與樣式", "控制常駐通知是否顯示最近 App 圖示與詳細/精簡樣式", listOf("常駐通知", "圖示", "精簡", "詳細", "樣式")),
        SettingSearchItem("notif_persistent_icons", "notif", "常駐通知顯示 App 圖示列", "於常駐統計通知內顯示最近收到訊息的 App 圖示", listOf("常駐通知", "圖示列", "icon")),
        SettingSearchItem("notif_ongoing", "notif", "記錄持續性通知", "是否記錄媒體播放、下載進度等 ongoing 通知", listOf("持續性", "ongoing", "音樂", "下載")),
        SettingSearchItem("notif_quiet_hours", "notif", "勿擾時段", "指定時段內暫停詐騙警示與關鍵字推播", listOf("勿擾", "靜音", "夜間", "睡覺", "quiet")),
        SettingSearchItem("notif_filter_rules", "notif", "過濾關鍵字規則與命中動作", "新增過濾關鍵字並選擇命中後標記已過濾或詐騙警示", listOf("過濾", "關鍵字", "黑名單", "filter")),
        SettingSearchItem("notif_interaction", "notif", "通知點擊行為、左滑刪除與觸覺震動", "調整點擊通知開啟幻訊或原 App、對話左滑刪除與震動回饋", listOf("點擊", "左滑", "刪除", "震動", "haptic", "swipe")),
        SettingSearchItem("notif_swipe_delete", "notif", "對話左滑刪除", "在對話訊息列左滑顯示刪除按鈕", listOf("左滑", "滑動刪除", "swipe")),
        SettingSearchItem("notif_throttle", "notif", "同 key 重複節流與個別 App 覆寫", "相同通知節流秒數、數字微變視為同一則更新與個別 App 節流覆寫", listOf("節流", "同 key", "重複通知", "漏訊", "流量", "數字微變", "覆寫")),
        SettingSearchItem("notif_digit_variance", "notif", "數字微變視為同一則更新", "對非通訊類通知將僅數字變動視為同 key 更新並節流", listOf("數字微變", "節流", "電量", "流量")),
        SettingSearchItem("data_retention", "data", "訊息保留與清理", "訊息保留天數與最大保留則數上限", listOf("保留", "天數", "上限", "清理")),
        SettingSearchItem("data_muted_apps", "data", "靜音 App 管理", "設定特定 App 完全不記錄或靜音記錄", listOf("靜音", "封鎖", "app", "略過")),
        SettingSearchItem("ai_rules", "data", "自訂標籤與白名單規則管理", "管理自訂標籤、App 分類與寄件者/訊息白名單規則", listOf("標籤", "規則", "分類", "標籤管理", "白名單", "whitelist"), page = "TagManagementActivity"),
        SettingSearchItem("data_export_clear", "data", "匯出資料與清除紀錄", "匯出 CSV / JSON 或清除已讀/全部訊息", listOf("匯出", "備份", "csv", "json", "清除", "刪除全部")),
        SettingSearchItem("appearance_widget_tour", "data", "教學重播與桌面小工具", "重播新手或設定頁教學；統計小工具請在桌面長按 → 小工具加入", listOf("小工具", "widget", "桌面", "導覽", "教學")),
        SettingSearchItem("appearance_theme", "appearance", "動態取色、字體大小與液態玻璃", "Material You 動態取色、字體縮放、液態玻璃特效與推薦問題", listOf("外觀", "主題", "字體", "大小", "玻璃", "動態取色", "深色", "淺色")),
        SettingSearchItem("appearance_haptic", "appearance", "觸覺震動回饋", "點擊按鈕與操作開關時提供輕微震動回饋", listOf("震動", "觸覺", "haptic")),
        SettingSearchItem("appearance_keepalive", "appearance", "通知權限、電池優化與保活指引", "檢查通知存取權、關閉電池優化與各品牌自啟動設定", listOf("權限", "電池", "保活", "自啟動", "小米", "三星", "背景被殺")),
        SettingSearchItem("intel_enabled", "intel", "雲端防詐情報預查開關", "收到含網址或加密貨幣地址的訊息時自動查詢防詐情報庫", listOf("防詐情報", "網址檢查", "情報預查", "intel")),
        SettingSearchItem("intel_sources", "intel", "防詐情報四來源與 API Key", "設定 fraud.tw / 165、Google Safe Browsing、PhishTank、Chainabuse 開關與金鑰", listOf("防詐情報", "165", "fraud", "safebrowsing", "phishtank", "chainabuse", "url", "網址檢查", "金鑰")),
        SettingSearchItem("shizuku_mode", "shizuku", "Shizuku 進階模式", "透過 Shizuku 取得進階系統資訊或保活輔助", listOf("shizuku", "進階", "root", "adb")),
        SettingSearchItem("personal_density", "personalization", "卡片密度（舒適 / 緊湊）", "調整首頁訊息卡片與對話氣泡的間距與行數密度", listOf("個人化", "密度", "緊湊", "舒適", "間距", "compact")),
        SettingSearchItem("personal_sort", "personalization", "首頁排序方式", "切換首頁 App 清單依時間新→舊、未讀多→少或風險高→低排序", listOf("個人化", "排序", "未讀", "風險", "時間", "sort")),
        SettingSearchItem("personal_filters", "personalization", "篩選欄顯示自訂", "自訂首頁篩選欄是否顯示星標、詐騙、已過濾與標籤按鈕", listOf("個人化", "篩選", "filter", "星標", "標籤", "隱藏")),
        SettingSearchItem("personal_summary_collapse", "personalization", "摘要卡預設收合", "控制每日與本週 AI 摘要卡是否預設收合為單行預覽", listOf("個人化", "摘要", "收合", "展開", "折疊")),
        SettingSearchItem("personal_time_format", "personalization", "12 / 24 小時制", "切換訊息與對話的時間顯示格式", listOf("個人化", "時間", "12小時", "24小時", "am", "pm"))
    )

    /** 僅允許 boolean 型開關透過 AI 對話直接切換，必須與 ITEMS 一致 */
    val TOGGLEABLE_IDS: Set<String> = setOf(
        "quick_token_saver",
        "quick_persistent_notif",
        "quick_ai_risk",
        "quick_scam_popup",
        "ai_auto_tag",
        "ai_chat_suggestions",
        "ai_daily_summary",
        "notif_persistent_icons",
        "notif_ongoing",
        "notif_quiet_hours",
        "notif_swipe_delete",
        "notif_digit_variance",
        "appearance_haptic",
        "intel_enabled",
        "personal_summary_collapse"
    )

    fun findById(id: String): SettingSearchItem? = ITEMS.firstOrNull { it.id == id }

    fun isToggleable(id: String): Boolean = id in TOGGLEABLE_IDS && findById(id) != null

    fun getBooleanState(ctx: Context, id: String): Boolean? {
        val appCtx = ctx.applicationContext
        return when (id) {
            "quick_token_saver" -> PhantomPrefs.isTokenSaverEnabled(appCtx)
            "quick_persistent_notif" -> PhantomPrefs.isPersistentStatsEnabled(appCtx)
            "quick_ai_risk" -> PhantomPrefs.isAiRiskEnabled(appCtx)
            "quick_scam_popup" -> PhantomPrefs.isScamAlertNotifEnabled(appCtx)
            "ai_auto_tag" -> PhantomPrefs.isAiAutoTagEnabled(appCtx)
            "ai_chat_suggestions" -> PhantomPrefs.isChatSuggestionsEnabled(appCtx)
            "ai_daily_summary" -> PhantomPrefs.isDailySummaryEnabled(appCtx)
            "notif_persistent_icons" -> PhantomPrefs.isPersistentNotifShowIcons(appCtx)
            "notif_ongoing" -> PhantomPrefs.isRecordOngoingEnabled(appCtx)
            "notif_quiet_hours" -> PhantomPrefs.isQuietHoursEnabled(appCtx)
            "notif_swipe_delete" -> PhantomPrefs.isSwipeToDeleteEnabled(appCtx)
            "notif_digit_variance" -> PhantomPrefs.isSameKeyDigitVarianceThrottleEnabled(appCtx)
            "appearance_haptic" -> PhantomPrefs.isHapticFeedbackEnabled(appCtx)
            "intel_enabled" -> PhantomPrefs.isIntelEnabled(appCtx)
            "personal_summary_collapse" -> PhantomPrefs.isSummaryDefaultCollapsed(appCtx)
            else -> null
        }
    }

    /**
     * 套用 boolean 設定（走與 SettingsActivity 完全相同的 PhantomPrefs setter＋既有副作用）。
     * 回傳 Pair(beforeState, afterState)，若 id 不在可開關白名單則回傳 null。
     */
    fun applyBooleanSetting(ctx: Context, id: String, enabled: Boolean): Pair<Boolean, Boolean>? {
        if (!isToggleable(id)) return null
        val appCtx = ctx.applicationContext
        val before = getBooleanState(appCtx, id) ?: return null
        when (id) {
            "quick_token_saver" -> {
                PhantomPrefs.setTokenSaverEnabled(appCtx, enabled)
                DailySummaryPolicy.schedule(appCtx)
            }
            "quick_persistent_notif" -> {
                PhantomPrefs.setPersistentStatsEnabled(appCtx, enabled)
                if (enabled) {
                    HeartbeatPolicy.schedule(appCtx)
                    StatsForegroundService.startOrRefresh(appCtx)
                } else {
                    StatsForegroundService.stop(appCtx)
                }
            }
            "quick_ai_risk" -> PhantomPrefs.setAiRiskEnabled(appCtx, enabled)
            "quick_scam_popup" -> PhantomPrefs.setScamAlertNotifEnabled(appCtx, enabled)
            "ai_auto_tag" -> PhantomPrefs.setAiAutoTagEnabled(appCtx, enabled)
            "ai_chat_suggestions" -> PhantomPrefs.setChatSuggestionsEnabled(appCtx, enabled)
            "ai_daily_summary" -> {
                PhantomPrefs.setDailySummaryEnabled(appCtx, enabled)
                DailySummaryPolicy.schedule(appCtx)
            }
            "notif_persistent_icons" -> {
                PhantomPrefs.setPersistentNotifShowIcons(appCtx, enabled)
                StatsForegroundService.startOrRefresh(appCtx, force = true)
            }
            "notif_ongoing" -> PhantomPrefs.setRecordOngoingEnabled(appCtx, enabled)
            "notif_quiet_hours" -> PhantomPrefs.setQuietHoursEnabled(appCtx, enabled)
            "notif_swipe_delete" -> PhantomPrefs.setSwipeToDeleteEnabled(appCtx, enabled)
            "notif_digit_variance" -> PhantomPrefs.setSameKeyDigitVarianceThrottleEnabled(appCtx, enabled)
            "appearance_haptic" -> PhantomPrefs.setHapticFeedbackEnabled(appCtx, enabled)
            "intel_enabled" -> PhantomPrefs.setIntelEnabled(appCtx, enabled)
            "personal_summary_collapse" -> PhantomPrefs.setSummaryDefaultCollapsed(appCtx, enabled)
            else -> return null
        }
        return before to enabled
    }

    /** 供 SettingsActivity AI 語意搜尋動態產生 catalogText */
    fun buildAiSearchCatalogText(): String =
        ITEMS.joinToString("\n") { item ->
            "${item.id} | ${item.title} | ${item.description} | 別名:${item.aliases.joinToString(",")}"
        }

    /** 供 ChatScreen system prompt 動態產生可開關設定清單（含目前狀態） */
    fun buildToggleablePromptList(ctx: Context): String {
        val appCtx = ctx.applicationContext
        return ITEMS.filter { it.id in TOGGLEABLE_IDS }.joinToString("\n") { item ->
            val stateStr = if (getBooleanState(appCtx, item.id) == true) "開啟" else "關閉"
            "- id=${item.id} | 名稱=${item.title} | 目前狀態=$stateStr | 說明=${item.description}"
        }
    }
}
