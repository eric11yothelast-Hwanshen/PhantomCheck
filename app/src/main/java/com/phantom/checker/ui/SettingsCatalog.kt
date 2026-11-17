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
        SettingSearchItem("quick_ai_risk", "quick", "AI 風險分析", "自動分析新通知是否為詐騙或可疑訊息", listOf("防詐", "風險", "偵測", "分析", "ai", "掃描")),
        SettingSearchItem("quick_scam_popup", "quick", "詐騙警示彈出通知", "高風險訊息即時彈出警示通知", listOf("詐騙", "警示", "推播", "高風險", "alert")),
        SettingSearchItem("ai_auto_tag", "quick", "每則訊息 AI 智慧掃描", "AI 分析時自動指派「必看／行銷／垃圾通知」等標籤", listOf("標籤", "自動分類", "智慧掃描", "tag")),
        SettingSearchItem("ai_providers", "ai", "多 Provider AI 設定", "管理 OpenAI / Gemini / Groq / 自訂端點與 API Key", listOf("key", "apikey", "模型", "openai", "gemini", "provider", "金鑰"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_tag_dispatch_test", "ai", "AI 標籤分發測試", "以固定樣本訊息測試目前 AI Provider 的風險與自動標籤分發結果", listOf("測試", "分發", "標籤測試", "智慧掃描", "失敗", "tag")),
        SettingSearchItem("ai_chat_suggestions", "ai", "AI 助手建議問題", "在 AI 助手分頁顯示快速提問與昨日簡報建議", listOf("建議問題", "快速提問", "昨日簡報", "助手")),
        SettingSearchItem("ai_quick_commands", "ai", "AI 助手常用指令與預設模式", "管理自訂常用指令，設定點擊後「直接執行」或「預輸入」", listOf("常用指令", "快捷指令", "直接執行", "預輸入", "指令", "command")),
        SettingSearchItem("ai_daily_limit", "ai", "每日 AI 額度上限", "限制每日 AI 呼叫次數", listOf("額度", "上限", "次數", "quota", "limit", "每日額度", "每日ai額度", "每日上限")),
        SettingSearchItem("ai_chat_rag_limit", "ai", "AI 助手單次閱讀訊息數", "調整 AI 助手每次讀取的歷史訊息上限（50–800 則；省 token 模式降為 100）", listOf("閱讀", "筆數", "訊息數", "rag", "context", "上下文", "400", "上限", "單次閱讀訊息數", "單次載入訊息數", "載入筆數", "閱讀訊息數", "載入訊息數")),
        SettingSearchItem("ai_daily_summary", "quick", "每日 AI 摘要", "排程每日自動生成訊息摘要", listOf("摘要", "日報", "每日", "summary")),
        SettingSearchItem("ai_daily_summary_notif", "ai", "每日摘要排程時間與完成推播", "調整每日摘要排程時間與生成後低干擾通知", listOf("摘要", "推播", "排程", "時間", "每日摘要")),
        SettingSearchItem("ai_auto_reply", "ai", "SMS 自動回覆設定", "開啟 SMS 自動回覆設定頁：啟用開關、回覆時段與回覆範本", listOf("自動回覆", "sms", "簡訊", "回覆", "時段", "reply"), page = "AutoReplySettingsActivity"),
        SettingSearchItem("ai_scam_center", "ai", "詐騙警示中心", "檢視高風險詐騙記錄與處置", listOf("詐騙", "警示", "中心", "高風險", "封鎖"), page = "ScamCenterActivity"),
        SettingSearchItem("ai_alert_keywords", "ai", "即時提醒關鍵字", "設定自訂即時提醒關鍵字，命中時觸發通知提醒", listOf("關鍵字", "提醒", "keyword")),
        SettingSearchItem("notif_style", "notif", "常駐通知樣式", "控制常駐通知為精簡或詳細樣式", listOf("常駐通知", "精簡", "詳細", "樣式")),
        SettingSearchItem("notif_persistent_icons", "notif", "常駐通知顯示 App 圖示條", "於常駐統計通知內顯示最近收到訊息的 App 圖示", listOf("常駐通知", "圖示列", "圖示條", "icon")),
        SettingSearchItem("notif_ongoing", "notif", "記錄持續性通知", "是否記錄媒體播放、下載進度等 ongoing 通知", listOf("持續性", "ongoing", "音樂", "下載")),
        SettingSearchItem("notif_quiet_hours", "notif", "勿擾時段", "指定時段內暫停詐騙警示與關鍵字推播", listOf("勿擾", "靜音", "夜間", "睡覺", "quiet")),
        SettingSearchItem("notif_filter_rules", "notif", "過濾關鍵字規則與命中動作", "新增過濾關鍵字並選擇命中後標記已過濾、不記錄或詐騙警示", listOf("過濾", "關鍵字", "黑名單", "filter")),
        SettingSearchItem("notif_interaction", "notif", "通知點擊行為", "調整點擊通知開啟幻訊或原 App", listOf("點擊", "開啟", "通知行為")),
        SettingSearchItem("notif_swipe_delete", "notif", "對話頁滑動刪除", "在對話訊息氣泡向左滑動快速刪除", listOf("左滑", "滑動刪除", "swipe")),
        SettingSearchItem("notif_throttle", "notif", "同 key 重複節流與個別 App 覆寫", "相同通知節流秒數與個別 App 節流覆寫", listOf("節流", "同 key", "重複通知", "漏訊", "覆寫", "節流秒數", "重複節流")),
        SettingSearchItem("notif_digit_variance", "notif", "數字微變視為同一則更新", "對監控類通知將僅數字變動視為同 key 更新並節流", listOf("數字微變", "節流", "電量", "流量")),
        SettingSearchItem("data_retention", "data", "訊息保留與清理", "訊息保留天數與最大保留則數上限", listOf("保留", "天數", "上限", "清理")),
        SettingSearchItem("data_muted_apps", "data", "App 靜音清單", "設定特定 App 完全不記錄或靜默記錄", listOf("靜音", "封鎖", "app", "略過")),
        SettingSearchItem("ai_rules", "data", "自訂標籤與白名單規則管理", "管理自訂標籤、App 分類與寄件者/訊息白名單規則", listOf("標籤", "規則", "分類", "標籤管理", "白名單", "whitelist"), page = "TagManagementActivity"),
        SettingSearchItem("data_export_clear", "data", "匯出資料與清除紀錄", "匯出 CSV 或清除已讀/全部訊息", listOf("匯出", "備份", "csv", "清除", "刪除全部")),
        SettingSearchItem("appearance_widget_tour", "data", "教學重播與桌面小工具", "重播新手或設定頁教學；統計小工具請在桌面長按 → 小工具加入", listOf("小工具", "widget", "桌面", "導覽", "教學")),
        SettingSearchItem("appearance_theme", "appearance", "主題模式、動態取色與字體大小", "深淺色主題、Material You 動態色與字體縮放", listOf("外觀", "主題", "字體", "大小", "動態取色", "深色", "淺色")),
        SettingSearchItem("appearance_glass_effect", "appearance", "液態玻璃模糊效果", "Android 12+ 模糊特效；更低版本或省電模式自動停用", listOf("液態玻璃", "玻璃", "模糊", "特效", "glass")),
        SettingSearchItem("appearance_tab_bar_mode", "appearance", "底部分頁欄顯示模式", "切換底部分頁欄為「標準」、「縮小」或「自動隱藏」", listOf("分頁列", "分頁欄", "導覽列", "縮小", "自動隱藏", "底部", "tabbar")),
        SettingSearchItem("appearance_haptic", "appearance", "觸覺回饋", "切換分頁與長按操作時提供震動回饋", listOf("震動", "觸覺", "haptic")),
        SettingSearchItem("appearance_keepalive", "appearance", "通知權限、電池優化與保活指引", "檢查通知存取權、電池優化白名單、OEM 自啟動與前景保活服務", listOf("權限", "電池", "保活", "自啟動", "小米", "三星", "背景被殺")),
        SettingSearchItem("intel_enabled", "intel", "啟用公開防詐情報預查", "收到含網址或加密貨幣地址的訊息時自動查詢防詐情報庫", listOf("防詐情報", "網址檢查", "情報預查", "intel")),
        SettingSearchItem("intel_sources", "intel", "防詐情報四來源與 API Key", "設定 fraud.tw / 165、Google Safe Browsing、PhishTank、Chainabuse 開關與金鑰", listOf("防詐情報", "165", "fraud", "safebrowsing", "phishtank", "chainabuse", "url", "網址檢查", "金鑰")),
        SettingSearchItem("shizuku_mode", "shizuku", "Shizuku 進階模式", "透過 Shizuku 取得進階系統資訊或保活輔助", listOf("shizuku", "進階", "root", "adb")),
        SettingSearchItem("personal_density", "personalization", "卡片密度（舒適 / 緊湊）", "調整首頁訊息卡片與對話氣泡的間距與行數密度", listOf("個人化", "密度", "緊湊", "舒適", "間距", "compact")),
        SettingSearchItem("personal_sort", "personalization", "首頁 App 排序", "切換首頁 App 清單依時間新→舊、未讀多→少或風險高→低排序", listOf("個人化", "排序", "未讀", "風險", "時間", "sort")),
        SettingSearchItem("personal_filters", "personalization", "首頁篩選欄顯示項目", "自訂首頁篩選欄是否顯示星標、詐騙、已過濾與標籤按鈕", listOf("個人化", "篩選", "filter", "星標", "標籤", "隱藏")),
        SettingSearchItem("personal_summary_collapse", "personalization", "摘要卡預設收合", "控制每日與本週 AI 摘要卡是否預設收合為單行預覽", listOf("個人化", "摘要", "收合", "展開", "折疊")),
        SettingSearchItem("personal_time_format", "personalization", "時間顯示格式（12 / 24 小時制）", "切換訊息與對話的時間顯示格式", listOf("個人化", "時間", "12小時", "24小時", "am", "pm"))
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
        "ai_daily_summary_notif",
        "notif_persistent_icons",
        "notif_ongoing",
        "notif_quiet_hours",
        "notif_swipe_delete",
        "notif_digit_variance",
        "appearance_glass_effect",
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
            "ai_daily_summary_notif" -> PhantomPrefs.isDailySummaryNotifEnabled(appCtx)
            "notif_persistent_icons" -> PhantomPrefs.isPersistentNotifShowIcons(appCtx)
            "notif_ongoing" -> PhantomPrefs.isRecordOngoingEnabled(appCtx)
            "notif_quiet_hours" -> PhantomPrefs.isQuietHoursEnabled(appCtx)
            "notif_swipe_delete" -> PhantomPrefs.isSwipeToDeleteEnabled(appCtx)
            "notif_digit_variance" -> PhantomPrefs.isSameKeyDigitVarianceThrottleEnabled(appCtx)
            "appearance_glass_effect" -> ThemePreferences.isGlassEffectEnabled(appCtx)
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
            "ai_daily_summary_notif" -> PhantomPrefs.setDailySummaryNotifEnabled(appCtx, enabled)
            "notif_persistent_icons" -> {
                PhantomPrefs.setPersistentNotifShowIcons(appCtx, enabled)
                StatsForegroundService.startOrRefresh(appCtx, force = true)
            }
            "notif_ongoing" -> PhantomPrefs.setRecordOngoingEnabled(appCtx, enabled)
            "notif_quiet_hours" -> PhantomPrefs.setQuietHoursEnabled(appCtx, enabled)
            "notif_swipe_delete" -> PhantomPrefs.setSwipeToDeleteEnabled(appCtx, enabled)
            "notif_digit_variance" -> PhantomPrefs.setSameKeyDigitVarianceThrottleEnabled(appCtx, enabled)
            "appearance_glass_effect" -> ThemePreferences.setGlassEffectEnabled(appCtx, enabled)
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

    data class NumericSettingSpec(
        val id: String,
        val title: String,
        val min: Int,
        val max: Int,
        val step: Int,
        val unit: String,
        val zeroLabel: String? = null
    ) {
        fun clampAndSnap(raw: Int): Pair<Int, Boolean> {
            val wasClamped = raw < min || raw > max
            val clamped = raw.coerceIn(min, max)
            val snapped = if (step > 1) {
                (((clamped - min + step / 2) / step) * step + min).coerceIn(min, max)
            } else {
                clamped
            }
            return snapped to wasClamped
        }

        fun formatValue(value: Int): String =
            if (value == 0 && zeroLabel != null) zeroLabel else "$value $unit"
    }

    val NUMERIC_SPECS: List<NumericSettingSpec> = listOf(
        NumericSettingSpec(
            id = "ai_chat_rag_limit",
            title = "AI 助手單次閱讀訊息數",
            min = PhantomPrefs.MIN_CHAT_RAG_MESSAGE_LIMIT,
            max = PhantomPrefs.MAX_CHAT_RAG_MESSAGE_LIMIT,
            step = 10,
            unit = "則"
        ),
        NumericSettingSpec(
            id = "ai_daily_limit",
            title = "每日 AI 額度上限",
            min = 0,
            max = 100,
            step = 10,
            unit = "則",
            zeroLabel = "不限（0 則）"
        ),
        NumericSettingSpec(
            id = "notif_throttle",
            title = "同 key 重複節流秒數",
            min = 0,
            max = 300,
            step = 10,
            unit = "秒",
            zeroLabel = "0 秒（關閉）"
        )
    )

    fun findNumericSpec(id: String): NumericSettingSpec? = NUMERIC_SPECS.firstOrNull { it.id == id }

    fun getNumericValue(ctx: Context, id: String): Int? {
        val appCtx = ctx.applicationContext
        return when (id) {
            "ai_chat_rag_limit" -> PhantomPrefs.getChatRagMessageLimit(appCtx)
            "ai_daily_limit" -> PhantomPrefs.getDailyAiLimit(appCtx)
            "notif_throttle" -> PhantomPrefs.getSameKeyThrottleSeconds(appCtx)
            else -> null
        }
    }

    /**
     * 套用數值設定（走與 SettingsActivity 完全相同的 PhantomPrefs setter）。
     * 回傳 Triple(before, after, wasClamped)，若 id 不在可調整清單則回傳 null。
     */
    fun applyNumericSetting(ctx: Context, id: String, rawValue: Int): Triple<Int, Int, Boolean>? {
        val spec = findNumericSpec(id) ?: return null
        val appCtx = ctx.applicationContext
        val before = getNumericValue(appCtx, id) ?: return null
        val (target, wasClamped) = spec.clampAndSnap(rawValue)
        when (id) {
            "ai_chat_rag_limit" -> PhantomPrefs.setChatRagMessageLimit(appCtx, target)
            "ai_daily_limit" -> PhantomPrefs.setDailyAiLimit(appCtx, target)
            "notif_throttle" -> PhantomPrefs.setSameKeyThrottleSeconds(appCtx, target)
            else -> return null
        }
        return Triple(before, target, wasClamped)
    }

    /** 供 ChatScreen system prompt 動態產生可調整數值設定清單（含範圍與目前值） */
    fun buildNumericPromptList(ctx: Context): String {
        val appCtx = ctx.applicationContext
        return NUMERIC_SPECS.joinToString("\n") { spec ->
            val current = getNumericValue(appCtx, spec.id) ?: spec.min
            "- id=${spec.id} | 名稱=${spec.title} | 範圍=${spec.min}–${spec.max}（步進 ${spec.step} ${spec.unit}） | 目前值=${spec.formatValue(current)}"
        }
    }

    /** 供 ChatScreen system prompt 動態產生全量設定清單摘要（含非開關項目，避免 AI 編造路徑） */
    fun buildAllSettingsPromptSummary(): String =
        ITEMS.joinToString("\n") { item ->
            "- id=${item.id} | 名稱=${item.title} | 說明=${item.description}"
        }

    /** 對全 catalog（id / 標題 / 別名 / 說明）進行關鍵字比對，回傳最相關的前 N 筆 */
    fun keywordSearch(query: String, limit: Int = 5): List<SettingSearchItem> {
        val tokens = query.trim().lowercase()
            .split(Regex("[\\s_\\-/、,，]+"))
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return emptyList()
        return ITEMS.mapNotNull { item ->
            val idLower = item.id.lowercase()
            val titleLower = item.title.lowercase()
            val descLower = item.description.lowercase()
            val aliasesLower = item.aliases.map { it.lowercase() }
            var score = 0
            tokens.forEach { tok ->
                if (idLower == tok) score += 10
                else if (idLower.contains(tok)) score += 4
                if (titleLower.contains(tok)) score += 6
                if (aliasesLower.any { it == tok }) score += 6
                else if (aliasesLower.any { it.contains(tok) }) score += 4
                if (descLower.contains(tok)) score += 2
            }
            if (score > 0) item to score else null
        }.sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    /**
     * 用戶端確定性數值設定意圖解析（當模型未產出 setting_adjust 時作為 fallback）。
     * 必須同時命中「數字」與「數值設定關鍵詞」，避免一般訊息查詢誤觸。
     */
    fun resolveNumericIntentFallback(userText: String): Pair<NumericSettingSpec, Int>? {
        val text = userText.trim()
        if (text.isEmpty()) return null
        val lower = text.lowercase()

        val adjustNumMatch = Regex("(?:調成|調到|調整為|調整成|改成|改為|改到|設為|設成|設到|設定為|限制為|上限為?)\\s*(\\d+)")
            .find(lower)
        val allNumbers = Regex("\\d+").findAll(text).mapNotNull { it.value.toIntOrNull() }.toList()
        val targetValue = adjustNumMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: allNumbers.lastOrNull()
            ?: return null

        val hasNumericIntentKeyword = listOf(
            "載入", "閱讀", "筆數", "訊息數", "額度", "節流", "上限", "秒數",
            "單次", "rag", "limit", "quota", "throttle", "調", "改", "設"
        ).any { lower.contains(it) }
        if (!hasNumericIntentKeyword) return null

        val explicitRules: List<Pair<String, List<String>>> = listOf(
            "ai_chat_rag_limit" to listOf(
                "單次閱讀", "閱讀訊息", "單次載入", "載入訊息", "載入筆數",
                "閱讀筆數", "訊息數", "上下文", "chat_rag", "rag"
            ),
            "ai_daily_limit" to listOf(
                "每日額度", "每日 ai 額度", "每日ai額度", "每日上限",
                "ai 額度", "ai額度", "額度上限", "daily_limit", "quota"
            ),
            "notif_throttle" to listOf(
                "節流", "同 key", "同key", "重複通知", "throttle"
            )
        )
        for ((specId, phrases) in explicitRules) {
            if (phrases.any { lower.contains(it) }) {
                val spec = findNumericSpec(specId) ?: continue
                return spec to targetValue
            }
        }

        val numericIds = NUMERIC_SPECS.map { it.id }.toSet()
        val matchedFromCatalog = keywordSearch(text, limit = 5)
            .firstOrNull { it.id in numericIds }
            ?.let { findNumericSpec(it.id) }
        if (matchedFromCatalog != null) {
            return matchedFromCatalog to targetValue
        }
        return null
    }
}
