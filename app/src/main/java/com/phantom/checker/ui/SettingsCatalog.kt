package com.phantom.checker.ui

import android.content.Context
import com.phantom.checker.service.HeartbeatPolicy
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.util.PhantomPrefs
import com.phantom.checker.util.PhantomSettingsBus
import kotlinx.coroutines.launch

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
        SettingSearchItem("quick_token_saver", "quick", "省 token 模式", "暫停每則 AI 掃描並降載對話注入，僅命中可疑特徵才分析", listOf("省電", "省錢", "節流", "api", "token", "額度")),
        SettingSearchItem("quick_persistent_notif", "quick", "常駐統計通知", "通知欄即時統計與 App 圖示列", listOf("通知", "常駐", "狀態列", "統計通知", "背景")),
        SettingSearchItem("quick_ai_risk", "quick", "AI 風險分析", "內文達觸發字數門檻且未命中過濾規則時自動評估風險（門檻可於「AI 分析觸發字數門檻」調整）", listOf("防詐", "風險", "偵測", "分析", "ai", "掃描")),
        SettingSearchItem("quick_scam_popup", "quick", "詐騙警示彈出通知", "高風險訊息即時彈出警示通知", listOf("詐騙", "警示", "推播", "高風險", "alert")),
        SettingSearchItem("ai_auto_tag", "quick", "每則訊息 AI 智慧掃描", "AI 分析時自動指派「必看／行銷／垃圾通知」等標籤", listOf("標籤", "自動分類", "智慧掃描", "tag")),
        SettingSearchItem("ai_providers", "ai", "多 Provider AI 設定", "管理 OpenAI / Gemini / Groq / 自訂端點與 API Key", listOf("key", "apikey", "模型", "openai", "gemini", "provider", "金鑰"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_tag_dispatch_test", "ai", "AI 標籤分發測試", "以固定樣本訊息測試目前 AI Provider 的風險與自動標籤分發結果", listOf("測試", "分發", "標籤測試", "智慧掃描", "失敗", "tag")),
        SettingSearchItem("ai_chat_suggestions", "ai", "AI 助手常用指令選單", "在 AI 助手輸入列 + 選單顯示常用指令（關閉後僅保留圖片上傳）", listOf("常用指令", "建議問題", "快速提問", "昨日簡報", "助手")),
        SettingSearchItem("ai_chat_tab_behavior", "ai", "切到 AI 助手時接續上次對話", "預設為開啟新對話；開啟後切換到 AI 助手分頁時自動載入最近一次對話", listOf("接續", "繼續", "上次對話", "新對話", "歷史對話", "session")),
        SettingSearchItem("ai_quick_commands", "ai", "AI 助手常用指令與預設模式", "管理自訂常用指令，設定點擊後「直接執行」或「預輸入」", listOf("常用指令", "快捷指令", "直接執行", "預輸入", "指令", "command")),
        SettingSearchItem("ai_daily_limit", "ai", "每日 AI 額度上限", "限制每日 AI 呼叫次數", listOf("額度", "上限", "次數", "quota", "limit", "每日額度", "每日ai額度", "每日上限")),
        SettingSearchItem("ai_chat_rag_limit", "ai", "AI 助手單次閱讀訊息數", "調整 AI 助手每次讀取的歷史訊息上限（50–800 則；省 token 模式降為 100）", listOf("閱讀", "筆數", "訊息數", "rag", "context", "上下文", "400", "上限", "單次閱讀訊息數", "單次載入訊息數", "載入筆數", "閱讀訊息數", "載入訊息數")),
        SettingSearchItem("ai_auto_reply", "ai", "SMS 自動回覆設定", "開啟 SMS 自動回覆設定頁：啟用開關、回覆時段與回覆範本", listOf("自動回覆", "sms", "簡訊", "回覆", "時段", "reply"), page = "AutoReplySettingsActivity"),
        SettingSearchItem("ai_scam_center", "ai", "詐騙警示中心", "檢視高風險詐騙記錄與處置（可由訊息頁風險卡或統計頁進入）", listOf("詐騙", "警示", "中心", "高風險", "封鎖"), page = "ScamCenterActivity"),
        SettingSearchItem("ai_alert_keywords", "ai", "即時提醒關鍵字", "設定自訂即時提醒關鍵字，命中時觸發通知提醒", listOf("關鍵字", "提醒", "keyword")),
        SettingSearchItem("ai_min_trigger_length", "ai", "AI 分析觸發字數門檻", "設定新訊息內容至少達幾字才觸發 AI 分析（1–30 字，預設 10 字）", listOf("字數", "長度", "門檻", "觸發", "最短", "幾個字", "分析字數")),
        SettingSearchItem("ai_offline_retry_enabled", "ai", "離線 AI 分析自動補佇列", "斷網或暫時性錯誤失敗時自動排入佇列，恢復網路後補齊風險分析", listOf("離線", "補分析", "重試", "佇列", "網路恢復")),
        SettingSearchItem("ai_auto_memo_enabled", "ai", "自動備忘錄", "AI 分析訊息時自動擷取約定時間、繳費期限、取貨編號等備忘事項寫入記事分頁", listOf("備忘錄", "自動筆記", "memo", "記事", "筆記")),
        SettingSearchItem("ai_tag_failure_notif_enabled", "ai", "AI 標籤分發失敗通知", "24 小時內 AI 標籤分發累積失敗 5 次時發送低干擾提醒（含 6 小時冷卻）", listOf("分發失敗", "標籤通知", "AI 失敗提醒", "失敗通知", "標籤失敗")),
        SettingSearchItem("ai_dual_model_routing", "ai", "雙模型信心路由", "先以輕量模型快速判斷風險，30~70 分模糊帶才升級主模型重判（不重複扣額度）", listOf("雙模型", "信心路由", "省 token", "routing", "small", "省錢")),
        SettingSearchItem("ai_dual_model_routing_model", "ai", "雙模型路由輕量模型", "設定信心路由第一關使用的輕量模型名稱（預設 mistral-small-2603）", listOf("路由模型", "輕量模型", "mistral-small-2603", "routing")),
        SettingSearchItem("ai_model_chat", "ai", "分用途模型：AI 助手對話", "為 AI 助手對話（含圖片）獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "對話模型", "切換模型", "chat"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_message_analysis", "ai", "分用途模型：每則訊息風險分析", "為新訊息自動風險分析獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "分析模型", "風險分析", "message"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_weekly_summary", "ai", "分用途模型：每週摘要", "為每週 AI 摘要獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "摘要模型", "每週摘要", "weekly"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_deep_analysis", "ai", "分用途模型：訊息深度分析", "為訊息深度分析獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "深度分析模型", "deep"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_settings_search", "ai", "分用途模型：設定 AI 語意搜尋", "為設定頁 AI 語意搜尋獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "搜尋模型", "設定搜尋", "search"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("notif_style", "notif", "常駐通知樣式", "控制常駐通知為精簡或詳細樣式", listOf("常駐通知", "精簡", "詳細", "樣式")),
        SettingSearchItem("notif_persistent_icons", "notif", "常駐通知顯示 App 圖示條", "於常駐統計通知內顯示最近收到訊息的 App 圖示", listOf("常駐通知", "圖示列", "圖示條", "icon")),
        SettingSearchItem("notif_ongoing", "notif", "記錄持續性通知", "是否記錄音樂播放、下載進度等持續顯示中的通知", listOf("持續性", "ongoing", "音樂", "下載")),
        SettingSearchItem("notif_quiet_hours", "notif", "勿擾時段", "指定時段內暫停詐騙警示與關鍵字推播", listOf("勿擾", "靜音", "夜間", "睡覺", "quiet")),
        SettingSearchItem("notif_filter_rules", "notif", "過濾關鍵字規則與命中動作", "新增過濾關鍵字並選擇命中後標記已過濾、不記錄或詐騙警示", listOf("過濾", "關鍵字", "黑名單", "filter")),
        SettingSearchItem("notif_interaction", "notif", "通知點擊行為", "調整點擊通知開啟幻訊或原 App", listOf("點擊", "開啟", "通知行為")),
        SettingSearchItem("notif_swipe_delete", "notif", "對話頁滑動刪除", "在對話訊息氣泡向左滑動快速刪除", listOf("左滑", "滑動刪除", "swipe")),
        SettingSearchItem("notif_throttle", "notif", "重複通知節流與個別 App 覆寫", "避免相同通知短時間重複記錄，可自訂節流秒數與單一 App 例外", listOf("節流", "同 key", "重複通知", "漏訊", "覆寫", "節流秒數", "重複節流")),
        SettingSearchItem("notif_digit_variance", "notif", "數字微變視為同一則更新", "對電量、流量等監控通知，僅數字變動時視為同一則更新並節流", listOf("數字微變", "節流", "電量", "流量")),
        SettingSearchItem("notif_reply_proxy", "notif", "通知欄連續快速回覆", "透過 AI 助手回覆後於通知欄保留對話輸入框，可連續回覆多則", listOf("連續回覆", "快速回覆", "代理回覆", "通知欄回覆", "reply")),
        SettingSearchItem("notif_reply_proxy_timeout", "notif", "回覆通知閒置自動收起", "設定代理回覆通知閒置多久後自動收起（15 / 30 / 60 分鐘或不自動收起）", listOf("連續回覆", "逾時", "自動收起", "閒置", "回覆通知")),
        SettingSearchItem("notif_my_display_names", "notif", "我的名稱（自我身分偵測）", "設定自己在群組通知中的顯示名稱，命中時標記為自己傳送且不計入統計", listOf("我的名稱", "自我身分", "我的顯示名稱", "自己回覆", "群組名稱", "isOwn")),
        SettingSearchItem("notif_clear_read", "notif", "清除已讀訊息", "永久刪除所有已讀訊息（保留未讀訊息）", listOf("清除已讀", "已讀", "刪除已讀", "清已讀")),
        SettingSearchItem("data_retention", "data", "訊息保留與清理", "訊息保留天數與最大保留則數上限", listOf("保留", "天數", "上限", "清理")),
        SettingSearchItem("data_muted_apps", "data", "App 靜音清單", "設定特定 App 完全不記錄或靜默記錄", listOf("靜音", "封鎖", "app", "略過")),
        SettingSearchItem("ai_rules", "data", "自訂標籤規則管理", "管理自訂標籤、App 分類與使用者標籤規則", listOf("標籤", "規則", "分類", "標籤管理"), page = "TagManagementActivity"),
        SettingSearchItem("whitelist_management", "data", "白名單管理", "依 App 分組檢視、新增、啟停或刪除寄件者與訊息白名單", listOf("白名單", "whitelist", "信任", "寄件者", "免警示", "略過詐騙"), page = "WhitelistManagementActivity"),
        SettingSearchItem("data_export_clear", "data", "匯出資料與清除紀錄", "匯出 CSV 或刪除全部訊息", listOf("匯出", "備份", "csv", "清除", "刪除全部")),
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
        SettingSearchItem("personal_summary_collapse", "personalization", "摘要卡預設收合", "控制本週 AI 摘要與早晚簡報卡是否預設收合為單行預覽", listOf("個人化", "摘要", "收合", "展開", "折疊")),
        SettingSearchItem("personal_home_card_layout", "personalization", "首頁卡片排列模式", "切換首頁 App 卡片為一般條列或中央堆疊（焦點卡片在正中央、上下對稱縮小堆疊的垂直滾輪）", listOf("堆疊", "條列", "卡片排列", "中央堆疊", "滾輪", "重疊", "個人化")),
        SettingSearchItem("personal_notes_card_layout", "personalization", "記事卡片排列模式", "獨立設定記事分頁卡片排列：一般條列或中央堆疊滾輪（不影響首頁）", listOf("記事", "備忘錄", "卡片排列", "堆疊", "滾輪", "中央堆疊")),
        SettingSearchItem("personal_notes_todo_collapsed", "personalization", "記事分頁待辦事項收合", "收起記事分頁上方的待辦事項區，僅顯示最快到期的未完成待辦以節省空間", listOf("待辦", "收合", "收起", "展開", "待辦事項")),
        SettingSearchItem("personal_home_filter_chips", "personalization", "首頁篩選標籤列收合", "將首頁頂部「全部／未讀」等篩選標籤收合成單行，點箭頭可展開（不與記事分頁共用）", listOf("篩選", "標籤", "chip", "收合", "未讀", "換行")),
        SettingSearchItem("personal_time_format", "personalization", "時間顯示格式（12 / 24 小時制）", "切換訊息與對話的時間顯示格式", listOf("個人化", "時間", "12小時", "24小時", "am", "pm")),
        SettingSearchItem("bg_global", "personalization", "全域背景圖片", "設定套用於訊息中心、統計、AI 助手與對話頁的全域底圖", listOf("個人化", "背景", "桌布", "wallpaper", "圖片", "全域")),
        SettingSearchItem("bg_chat", "personalization", "AI 聊天背景圖片", "自訂 AI 助手分頁背景圖片（未設定時沿用全域背景）", listOf("個人化", "背景", "桌布", "wallpaper", "AI", "聊天")),
        SettingSearchItem("stats_card_visibility", "stats", "統計頁卡片顯示自訂", "勾選要在統計分頁顯示的圖表與統計卡片", listOf("統計", "圖表", "排行榜", "自訂", "卡片", "折線圖", "環形圖")),
        SettingSearchItem("stats_briefing", "stats", "統計卡：早晚簡報", "控制統計頁是否顯示最近一次早晚簡報卡片", listOf("統計", "早晚簡報", "簡報", "briefing", "stats")),
        SettingSearchItem("stats_weekly_bar", "stats", "統計卡：本週 7 日訊息量", "控制統計頁是否顯示本週 7 日訊息量長條圖", listOf("統計", "本週", "7日", "長條圖", "訊息量")),
        SettingSearchItem("stats_risk_dist", "stats", "統計卡：風險分布三色", "控制統計頁是否顯示風險分布三色計數卡", listOf("統計", "風險分布", "安全", "疑似", "詐騙")),
        SettingSearchItem("stats_hourly", "stats", "統計卡：今日每小時分布", "控制統計頁是否顯示今日 24 小時訊息分布圖", listOf("統計", "每小時", "時段", "分布")),
        SettingSearchItem("stats_daily_top_apps", "stats", "統計卡：今日 App 訊息量排行榜 Top 5", "控制統計頁是否顯示今日收到訊息最多的前五名 App 排行榜", listOf("統計", "排行榜", "top5", "今日", "app")),
        SettingSearchItem("stats_ai_tags", "stats", "統計卡：今日 AI 智慧標記", "控制統計頁是否顯示今日 AI 智慧標記分類統計", listOf("統計", "ai標記", "智慧標記", "標籤")),
        SettingSearchItem("stats_harassment", "stats", "統計卡：騷擾佔比環形", "控制統計頁是否顯示騷擾佔比環形圖", listOf("統計", "騷擾", "行銷", "垃圾通知", "環形圖")),
        SettingSearchItem("stats_top_risky", "stats", "統計卡：Top 5 高風險 App", "控制統計頁是否顯示累計疑似與詐騙訊息前五名 App", listOf("統計", "高風險", "top5", "詐騙app")),
        SettingSearchItem("stats_risk_trend", "stats", "統計卡：7 日風險趨勢折線", "控制統計頁是否顯示近 7 日疑似與詐騙訊息趨勢折線圖", listOf("統計", "風險趨勢", "折線圖", "7日", "詐騙趨勢")),
        SettingSearchItem("stats_unread_ratio", "stats", "統計卡：未讀 / 已讀比例環形", "控制統計頁是否顯示未讀與已讀訊息比例環形圖", listOf("統計", "未讀", "已讀", "比例", "環形圖")),
        SettingSearchItem("stats_app_share", "stats", "統計卡：各 App 訊息佔比橫條前 8", "控制統計頁是否顯示各 App 訊息佔比前 8 名橫條圖", listOf("統計", "佔比", "app佔比", "橫條圖", "top8")),
        SettingSearchItem("stats_todo_done", "stats", "統計卡：待辦完成率環形", "控制統計頁是否顯示待辦事項完成率環形圖", listOf("統計", "待辦", "完成率", "環形圖", "todo")),
        SettingSearchItem("bg_conversation", "personalization", "對話頁背景圖片", "自訂對話頁背景圖片（未設定時沿用全域背景）", listOf("個人化", "背景", "桌布", "wallpaper", "對話")),
        SettingSearchItem("bg_scrim", "personalization", "背景暗色遮罩強度", "調整背景圖片上的暗色遮罩透明度以確保文字可讀性", listOf("個人化", "背景", "遮罩", "scrim", "暗度", "對比")),
        SettingSearchItem("glass_strength", "personalization", "玻璃卡片強度", "調整液態玻璃卡片的表面不透明度與視覺強度", listOf("個人化", "玻璃", "強度", "毛玻璃", "透明", "glass")),
        SettingSearchItem("appearance_card_bg", "appearance", "卡片底色模式", "選擇跟隨玻璃強度、透明模式（0–100% 滑桿）或深色底", listOf("卡片", "底色", "背景", "透明", "深色", "黑底", "外觀")),
        SettingSearchItem("assistant_header_background", "personalization", "夥伴頁頭部背景", "自訂或移除夥伴（助手）頁頂部橫幅背景圖片", listOf("夥伴", "助手", "頭部", "橫幅", "背景", "封面", "圖片")),
        SettingSearchItem("chat_font_scale", "personalization", "AI 聊天室字體獨立縮放", "獨立調整 AI 聊天室氣泡文字大小（0.85x–1.30x），不影響系統字體與其他分頁", listOf("聊天字體", "AI字體", "字體大小", "聊天縮放", "font")),
        SettingSearchItem("messages_font_scale", "personalization", "訊息中心字體獨立縮放", "獨立調整首頁訊息中心清單字體大小（0.85x–1.30x），不影響其他分頁", listOf("訊息字體", "首頁字體", "訊息大小", "清單縮放")),
        SettingSearchItem("chat_bubble_corner_dp", "personalization", "聊天氣泡圓角半徑", "調整 AI 聊天室對話氣泡的圓角弧度（8–24 dp，預設 18 dp）", listOf("氣泡", "圓角", "對話框", "bubble", "外觀")),
        SettingSearchItem("chat_bubble_width_ratio", "personalization", "聊天氣泡寬度比例", "調整 AI 聊天室對話氣泡的最大寬度比例（70%–95%，預設 82%）", listOf("氣泡寬度", "對話寬度", "bubble", "排版")),
        SettingSearchItem("favorite_apps_max_visible", "personalization", "首頁我的最愛 App 顯示上限", "限制首頁「我的最愛 App」橫列顯示的卡片數量（全部／4／6／8）", listOf("我的最愛", "最愛數量", "橫列", "釘選", "首頁")),
        SettingSearchItem("memory_chat_capture", "memory_assistant", "聊天記憶擷取", "允許 AI 在對話中擷取個人事實、人物、行程與偏好", listOf("記憶", "記憶庫", "擷取", "聊天記憶", "自動記憶", "memory", "確認卡")),
        SettingSearchItem("memory_write_confirm", "memory_assistant", "記憶寫入前先顯示確認卡", "開啟時，AI 產生的新記憶會先在 AI 助手對話中顯示確認卡，由您選擇寫入／保留；關閉時直接寫入（敏感記憶與衝突仍必經確認）。您隨時可在記憶頁手動新增、編輯或刪除記憶。", listOf("確認卡", "記憶確認", "寫入確認", "AI 記憶")),
        SettingSearchItem("memory_normal_top_k", "memory_assistant", "記憶注入筆數（正常模式）", "設定正常模式下每次 AI 對話注入的相關記憶筆數上限（1–8 筆，預設 8 筆，總預算 ≤250 tokens）", listOf("記憶", "注入", "筆數", "topk", "正常模式", "記憶預算", "記憶筆數")),
        SettingSearchItem("memory_normal_per_item_chars", "memory_assistant", "記憶單筆截斷字數（正常模式）", "設定正常模式下每筆注入記憶的預覽截斷字數（30–120 字，預設 70 字 ≈ 50 tokens）", listOf("記憶", "字數", "截斷", "單筆", "token", "記憶長度")),
        SettingSearchItem("memory_saver_top_k", "memory_assistant", "記憶注入筆數（省 token 模式）", "獨立設定省 token 模式下每次 AI 對話注入的記憶筆數上限（1–4 筆，預設 4 筆，總預算 ≤120 tokens，與正常模式互不覆寫）", listOf("記憶", "省 token", "省電", "注入", "筆數", "省token記憶")),
        SettingSearchItem("assistant_custom_name", "memory_assistant", "AI 助手名字", "自訂 AI 助手顯示稱呼與對話身分（預設「幻訊」）", listOf("助手名字", "名字", "稱呼", "改名", "自訂助手", "幻訊", "name")),
        SettingSearchItem("assistant_personality", "memory_assistant", "AI 助手個性與風格", "選擇內建個性（預設／簡潔專業／親切詳細／嚴謹防詐顧問）或輸入完全自訂個性描述", listOf("個性", "風格", "語氣", "人設", "簡潔", "親切", "嚴謹", "自訂助手", "persona")),
        SettingSearchItem("assistant_verbosity", "memory_assistant", "AI 助手回答詳細度", "切換 AI 助手回答篇幅為精簡、適中或詳細", listOf("詳細度", "精簡", "適中", "詳細", "篇幅", "長短", "回答長度", "verbosity")),
        SettingSearchItem("assistant_avatar", "memory_assistant", "AI 助手頭像", "從相簿選圖、圓形裁切並降採樣儲存為 AI 助手頭像，或重設為預設圖示", listOf("頭像", "大頭貼", "照片", "圖片", "圓形裁切", "avatar", "自訂助手")),
        SettingSearchItem("memory_privacy_tier_info", "memory_assistant", "記憶庫隱私分級說明", "一般記憶可參與本地檢索注入；標為「🔒 敏感」的記憶在任何對外 AI 請求皆由程式碼硬性排除，100% 僅留存本機", listOf("隱私", "敏感", "分級", "外送", "安全", "sensitive", "記憶隱私")),
        SettingSearchItem("ai_anti_fraud_cross_check", "memory_assistant", "記憶 × 防詐交叉檢查", "結合本機通訊錄與一般記憶做離線矛盾比對（熟人冒充、情境破綻、防詐免疫），絕不外送原始通訊錄或敏感記憶", listOf("防詐", "交叉檢查", "熟人冒充", "通訊錄", "矛盾", "免疫", "記憶防詐")),
        SettingSearchItem("calendar_integration", "memory_assistant", "本機日曆整合", "勾選開放給 AI 助手查詢空檔與寫入行程的本機日曆帳號（預設全不勾，日曆資料僅留存本機絕不外送）", listOf("日曆", "行事曆", "行程", "空檔", "撞期", "calendar")),
        SettingSearchItem("calendar_write_confirm", "memory_assistant", "日曆寫入前先顯示確認卡", "開啟時，寫入行程前會在 AI 助手對話中顯示行程確認卡並檢查撞期；關閉時仍會執行撞期檢查，無撞期且已授權寫入權限則直接寫入。", listOf("日曆確認", "行程確認", "寫入同意", "日曆", "確認卡")),
        SettingSearchItem("schedule_memo_route", "memory_assistant", "行程類通知分流", "行程類通知優先提示加入日曆，不再自動建立待辦/記事", listOf("行程", "分流", "日曆", "待辦", "記事", "建議卡", "schedule", "calendar")),
        SettingSearchItem("permission_hub", "hub", "權限授予中心", "集中檢視並快速授予通知、簡訊、通訊錄、日曆、精確鬧鐘、電池優化與使用情況權限（Shizuku 保留於獨立卡片）", listOf("權限", "授權", "通知存取", "精確鬧鐘", "電池優化", "受限制的設定", "permission", "hub"), page = "PermissionHubActivity"),
        SettingSearchItem("assistant_hub", "hub", "AI 夥伴與主動推播", "集中管理早晚簡報、主動推播主題、每日上限、勿擾時段、畫像卡、自訂外部搜尋服務與通訊錄整合", listOf("夥伴", "主動推播", "簡報", "畫像卡", "勿擾", "外部搜尋", "通訊錄", "assistant", "hub"), page = "AssistantHubActivity"),
        SettingSearchItem("contacts_integration", "hub", "通訊錄整合（暱稱／匯入／VIP）", "管理通訊軟體暱稱綁定本名、免 AI 額度關係匯入與 VIP 信任圈白名單同步（原始通訊錄絕不外送 AI）", listOf("通訊錄", "聯絡人", "暱稱", "綁定", "匯入", "vip", "信任圈", "contacts"), page = "AssistantHubActivity"),
        SettingSearchItem("briefing_morning_enabled", "hub", "早間個人簡報", "每日定時（預設 08:45）整理今日行程、待辦與重點通知寫入 AI 助手並發送通知", listOf("早間簡報", "早報", "簡報", "主動推播", "briefing"), page = "AssistantHubActivity"),
        SettingSearchItem("briefing_evening_enabled", "hub", "晚間個人簡報", "每日定時（預設 22:25）回顧今日重點並預覽明日行程與待辦", listOf("晚間簡報", "晚報", "簡報", "主動推播", "briefing"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_daily_cap", "hub", "每日主動提醒上限", "限制 AI 助手每日主動提醒則數上限（預設 6 則，1–20 可調，與簡報上限獨立）", listOf("主動推播", "上限", "節流", "cap", "主動提醒"), page = "AssistantHubActivity"),
        SettingSearchItem("briefing_daily_cap", "hub", "每日早晚簡報上限", "限制早間與晚間簡報每日合計上限（預設 2 則，1–10 可調，與主動提醒獨立）", listOf("簡報上限", "早晚簡報", "上限", "briefing cap"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_reminder_lead_minutes", "hub", "行程／待辦提醒提前時間", "設定主動提醒提前觸發分鐘數（15／30／60 分鐘，預設 30 分鐘）", listOf("提前提醒", "提前分鐘", "lead", "行程提醒", "待辦提醒"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_allday_remind_time", "hub", "全天事件提醒時刻", "設定今日全天事件主動提醒開始時刻（HH:mm，預設 09:00）", listOf("全天事件", "提醒時間", "allday", "09:00"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_monitor_interval_minutes", "hub", "主動監控檢查間隔", "設定零 token 本地指紋與里程碑背景檢查週期（15／30／60 分鐘，預設 15 分鐘）", listOf("監控頻率", "檢查間隔", "monitor", "worker"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_dedup_window_minutes", "hub", "主動提醒去重冷卻時間", "同內容主動提醒於此冷卻時間內絕不重複發送（10／30／60 分鐘，預設 10 分鐘）", listOf("去重", "冷卻", "防重複", "dedup"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_dnd_enabled", "hub", "主動推播獨立勿擾時段", "設定主動推播的獨立勿擾時段（預設 00:00–07:00，期間仍寫入聊天但不發通知）", listOf("勿擾", "主動推播", "靜音", "dnd"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_calendar_insight_enabled", "hub", "主動日曆情報", "定時讀取勾選日曆並依即將到來事件主動彙整情報寫入主動訊息聊天室（預設開啟）", listOf("日曆情報", "主動日曆", "行事曆", "calendar", "insight"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_calendar_insight_notif_enabled", "hub", "日曆情報發送通知", "主動日曆情報產出時是否發送通知（關閉時僅寫入主動訊息聊天室，預設開啟）", listOf("日曆情報", "通知", "calendar", "notif"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_calendar_web_search_enabled", "hub", "日曆情報允許上網查詢", "允許對未來 48 小時事件標題＋日期透過已設定 BYOK 端點查詢外部情報（嚴禁參與人與備註，預設開啟）", listOf("日曆情報", "上網", "外部搜尋", "byok", "search"), page = "AssistantHubActivity"),
        SettingSearchItem("calendar_insight_interval_minutes", "hub", "日曆情報檢查頻率", "設定主動日曆情報定時檢查週期（30／60／120／180 分鐘，預設 60 分鐘）", listOf("日曆情報", "頻率", "週期", "interval"), page = "AssistantHubActivity"),
        SettingSearchItem("memory_auto_extract_enabled", "hub", "對話自動記憶擷取", "對話中出現個人資訊（人、事、時、地、偏好、習慣）時自動寫入非敏感無衝突記憶並於氣泡下顯示小字提醒（敏感與衝突仍進確認卡，預設開啟）", listOf("自動記憶", "擷取", "記憶", "memory", "extract"), page = "AssistantHubActivity"),
        SettingSearchItem("chat_pending_capsules_enabled", "hub", "未決事項膠囊列", "對話框頂部顯示至多 3 顆可滑除的未決事項快速膠囊與壓縮接續錨點（預設開啟）", listOf("膠囊", "未決事項", "錨點", "連續性", "capsules"), page = "AssistantHubActivity"),
        SettingSearchItem("chat_bubble_timestamp_enabled", "about", "聊天室訊息右下角時間戳", "控制 AI 對話與主動訊息氣泡右下角是否顯示 HH:mm 發送時間（預設開啟）", listOf("時間戳", "發送時間", "聊天時間", "timestamp")),
        SettingSearchItem("diary_daily_enabled", "hub", "AI 日記／每日回顧", "每日 00:30 零 token 本機整理前一日時間軸，AI 註記展開快取且可「不準」降權（無 API key 亦可用）", listOf("日記", "每日回顧", "時間軸", "diary", "註記"), page = "AssistantHubActivity"),
        SettingSearchItem("goal_tracking_enabled", "hub", "長程目標追蹤", "偵測習慣／目標訊號必經確認卡建立、自動拆解週期子待辦並支援只降不升頻率調整（無 API key 亦可手動建立）", listOf("目標", "長程目標", "習慣", "打卡", "goal"), page = "AssistantHubActivity"),
        SettingSearchItem("health_briefing_enabled", "hub", "Health Connect 生理節奏整合", "晨間簡報附查表式睡眠／步數／心率規則建議（增強不依賴、原始數值不出本機）", listOf("健康", "睡眠", "步數", "心率", "health connect", "生理"), page = "PermissionHubActivity"),
        SettingSearchItem("health_soft_invite_enabled", "hub", "Health Connect 軟性邀請", "未授權 Health Connect 時於晨間簡報每週最多一次顯示軟性連接邀請", listOf("穿戴", "手錶", "health", "軟邀請"), page = "PermissionHubActivity"),
        SettingSearchItem("decision_confirm_threshold", "hub", "雙門檻自動決策置信閾值", "調整 AI 自動判定高風險與降級確認卡的分數門檻（預設 60，範圍 40–85）", listOf("雙門檻", "決策", "閾值", "防詐門檻", "確認卡", "decision", "threshold"), page = "AssistantHubActivity"),
        SettingSearchItem("ai_model_briefing", "ai", "簡報與畫像卡模型", "為早晚簡報與「我對你的瞭解」畫像卡指定獨立 AI 模型", listOf("簡報模型", "畫像卡模型", "briefing", "模型"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_proactive", "ai", "主動情報萃取與摘要模型", "為主動情報中樞（關注主題萃取與動態摘要）指定獨立 AI 模型與設定檔", listOf("主動情報", "情報模型", "proactive", "關注主題", "模型"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("proactive_intel_enabled", "intel", "主動情報中樞（總開關）", "定期將關注主題關鍵字送往設定的 AI 供應商查詢最新動態並摘要推播（預設關閉）", listOf("主動情報", "情報中樞", "關注動態", "proactive", "自動查詢")),
        SettingSearchItem("proactive_extract_enabled", "intel", "每日自動萃取關注主題", "每日從近期非敏感素材自動萃取最多 5 個待確認關注主題（敏感記憶硬排除）", listOf("萃取", "關注主題", "自動萃取", "watch_topics")),
        SettingSearchItem("proactive_intel_freq_hours", "intel", "主動情報查詢頻率", "設定背景主動情報週期（6／12／24 小時，預設 6 小時，僅充電電量足夠且連網執行）", listOf("查詢頻率", "主動情報頻率", "6小時", "12小時", "24小時")),
        SettingSearchItem("proactive_watch_topics", "intel", "關注主題清單與立即查詢", "管理手動／AI 萃取的關注主題、靜音或刪除，並可手動立即查詢預覽摘要", listOf("關注清單", "立即查詢", "預覽", "主題管理", "不再關注")),
        SettingSearchItem("linked_apps_center", "hub", "連結應用程式中心", "集中檢視日曆、通訊錄、通知、鬧鐘、電池優化狀態與自填金鑰雲端擴充（GitHub／Discord）", listOf("連結應用", "github", "discord", "webhook", "鬧鐘", "gmail", "地圖", "跨app", "linked"), page = "LinkedAppsActivity"),
        SettingSearchItem("update_auto_check", "about", "自動檢查更新", "每次開啟 App 自動檢查更新；選擇「稍後更新」將於設定間隔後再次提醒。", listOf("檢查更新", "更新", "版本", "自動更新", "update")),
        SettingSearchItem("update_snooze_minutes", "about", "更新提醒延後間隔", "點擊「稍後更新」後暫停提醒的時間（30 分鐘／1 小時／4 小時，預設 1 小時）", listOf("稍後更新", "延後", "snooze", "更新間隔")),
        SettingSearchItem("about_section", "about", "關於", "應用程式名稱、版本號、作者、GitHub 倉庫、第三方元件與隱私說明", listOf("版本", "作者", "關於", "about", "version", "檢查更新", "更新", "update", "下載"))
    )

    /** 僅允許 boolean 型開關透過 AI 對話直接切換，必須與 ITEMS 一致 */
    val TOGGLEABLE_IDS: Set<String> = setOf(
        "quick_token_saver",
        "quick_persistent_notif",
        "quick_ai_risk",
        "quick_scam_popup",
        "ai_auto_tag",
        "ai_offline_retry_enabled",
        "ai_auto_memo_enabled",
        "ai_tag_failure_notif_enabled",
        "ai_dual_model_routing",
        "ai_chat_suggestions",
        "ai_chat_tab_behavior",
        "memory_chat_capture",
        "memory_write_confirm",
        "calendar_write_confirm",
        "ai_anti_fraud_cross_check",
        "briefing_morning_enabled",
        "briefing_evening_enabled",
        "proactive_dnd_enabled",
        "proactive_calendar_insight_enabled",
        "proactive_calendar_insight_notif_enabled",
        "proactive_calendar_web_search_enabled",
        "memory_auto_extract_enabled",
        "chat_pending_capsules_enabled",
        "notif_persistent_icons",
        "notif_ongoing",
        "notif_quiet_hours",
        "notif_swipe_delete",
        "notif_digit_variance",
        "notif_reply_proxy",
        "appearance_glass_effect",
        "appearance_haptic",
        "intel_enabled",
        "personal_summary_collapse",
        "stats_briefing",
        "stats_weekly_bar",
        "stats_risk_dist",
        "stats_hourly",
        "stats_daily_top_apps",
        "stats_ai_tags",
        "stats_harassment",
        "stats_top_risky",
        "stats_risk_trend",
        "stats_unread_ratio",
        "stats_app_share",
        "stats_todo_done",
        "chat_bubble_timestamp_enabled",
        "diary_daily_enabled",
        "goal_tracking_enabled",
        "health_briefing_enabled",
        "health_soft_invite_enabled",
        "update_auto_check"
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
            "ai_offline_retry_enabled" -> PhantomPrefs.isAiOfflineRetryEnabled(appCtx)
            "ai_auto_memo_enabled" -> PhantomPrefs.isAiAutoMemoEnabled(appCtx)
            "ai_tag_failure_notif_enabled" -> PhantomPrefs.isAiTagFailureNotifEnabled(appCtx)
            "ai_dual_model_routing" -> PhantomPrefs.isDualModelRoutingEnabled(appCtx)
            "ai_chat_suggestions" -> PhantomPrefs.isChatSuggestionsEnabled(appCtx)
            "ai_chat_tab_behavior" -> PhantomPrefs.isAiChatTabContinue(appCtx)
            "memory_chat_capture" -> PhantomPrefs.isMemoryChatCaptureEnabled(appCtx)
            "memory_write_confirm" -> PhantomPrefs.isMemoryWriteConfirmEnabled(appCtx)
            "calendar_write_confirm" -> PhantomPrefs.isCalendarWriteConfirmEnabled(appCtx)
            "ai_anti_fraud_cross_check" -> PhantomPrefs.isAntiFraudCrossCheckEnabled(appCtx)
            "briefing_morning_enabled" -> PhantomPrefs.isMorningBriefingEnabled(appCtx)
            "briefing_evening_enabled" -> PhantomPrefs.isEveningBriefingEnabled(appCtx)
            "proactive_dnd_enabled" -> PhantomPrefs.isProactiveDndEnabled(appCtx)
            "proactive_calendar_insight_enabled" -> PhantomPrefs.isProactiveCalendarInsightEnabled(appCtx)
            "proactive_calendar_insight_notif_enabled" -> PhantomPrefs.isProactiveCalendarInsightNotifEnabled(appCtx)
            "proactive_calendar_web_search_enabled" -> PhantomPrefs.isProactiveCalendarWebSearchEnabled(appCtx)
            "memory_auto_extract_enabled" -> PhantomPrefs.isMemoryAutoExtractEnabled(appCtx)
            "chat_pending_capsules_enabled" -> PhantomPrefs.isChatPendingCapsulesEnabled(appCtx)
            "notif_persistent_icons" -> PhantomPrefs.isPersistentNotifShowIcons(appCtx)
            "notif_ongoing" -> PhantomPrefs.isRecordOngoingEnabled(appCtx)
            "notif_quiet_hours" -> PhantomPrefs.isQuietHoursEnabled(appCtx)
            "notif_swipe_delete" -> PhantomPrefs.isSwipeToDeleteEnabled(appCtx)
            "notif_digit_variance" -> PhantomPrefs.isSameKeyDigitVarianceThrottleEnabled(appCtx)
            "notif_reply_proxy" -> PhantomPrefs.isReplyProxyEnabled(appCtx)
            "appearance_glass_effect" -> ThemePreferences.isGlassEffectEnabled(appCtx)
            "appearance_haptic" -> PhantomPrefs.isHapticFeedbackEnabled(appCtx)
            "intel_enabled" -> PhantomPrefs.isIntelEnabled(appCtx)
            "personal_summary_collapse" -> PhantomPrefs.isSummaryDefaultCollapsed(appCtx)
            "stats_briefing",
            "stats_weekly_bar",
            "stats_risk_dist",
            "stats_hourly",
            "stats_daily_top_apps",
            "stats_ai_tags",
            "stats_harassment",
            "stats_top_risky",
            "stats_risk_trend",
            "stats_unread_ratio",
            "stats_app_share",
            "stats_todo_done" -> PhantomPrefs.isStatsCardVisible(appCtx, id)
            "chat_bubble_timestamp_enabled" -> PhantomPrefs.isChatTimestampEnabled(appCtx)
            "diary_daily_enabled" -> PhantomPrefs.isDiaryDailyEnabled(appCtx)
            "goal_tracking_enabled" -> PhantomPrefs.isGoalTrackingEnabled(appCtx)
            "health_briefing_enabled" -> PhantomPrefs.isHealthBriefingEnabled(appCtx)
            "health_soft_invite_enabled" -> PhantomPrefs.isHealthSoftInviteEnabled(appCtx)
            "update_auto_check" -> PhantomPrefs.isUpdateAutoCheckEnabled(appCtx)
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
            "quick_token_saver" -> PhantomPrefs.setTokenSaverEnabled(appCtx, enabled)
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
            "ai_offline_retry_enabled" -> {
                PhantomPrefs.setAiOfflineRetryEnabled(appCtx, enabled)
                if (!enabled) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        com.phantom.checker.service.PendingAiAnalysisPolicy.disableAndClear(appCtx)
                    }
                }
            }
            "ai_auto_memo_enabled" -> PhantomPrefs.setAiAutoMemoEnabled(appCtx, enabled)
            "ai_tag_failure_notif_enabled" -> PhantomPrefs.setAiTagFailureNotifEnabled(appCtx, enabled)
            "ai_dual_model_routing" -> PhantomPrefs.setDualModelRoutingEnabled(appCtx, enabled)
            "ai_chat_suggestions" -> PhantomPrefs.setChatSuggestionsEnabled(appCtx, enabled)
            "ai_chat_tab_behavior" -> PhantomPrefs.setAiChatTabBehavior(
                appCtx,
                if (enabled) PhantomPrefs.AI_CHAT_TAB_CONTINUE else PhantomPrefs.AI_CHAT_TAB_NEW
            )
            "memory_chat_capture" -> PhantomPrefs.setMemoryChatCaptureEnabled(appCtx, enabled)
            "memory_write_confirm" -> PhantomPrefs.setMemoryWriteConfirmEnabled(appCtx, enabled)
            "calendar_write_confirm" -> PhantomPrefs.setCalendarWriteConfirmEnabled(appCtx, enabled)
            "ai_anti_fraud_cross_check" -> PhantomPrefs.setAntiFraudCrossCheckEnabled(appCtx, enabled)
            "briefing_morning_enabled" -> {
                PhantomPrefs.setMorningBriefingEnabled(appCtx, enabled)
                com.phantom.checker.service.BriefingScheduler.rescheduleAll(appCtx)
            }
            "briefing_evening_enabled" -> {
                PhantomPrefs.setEveningBriefingEnabled(appCtx, enabled)
                com.phantom.checker.service.BriefingScheduler.rescheduleAll(appCtx)
            }
            "proactive_dnd_enabled" -> PhantomPrefs.setProactiveDndEnabled(appCtx, enabled)
            "proactive_calendar_insight_enabled" -> {
                PhantomPrefs.setProactiveCalendarInsightEnabled(appCtx, enabled)
                com.phantom.checker.service.CalendarInsightScheduler.scheduleOrUpdate(appCtx)
            }
            "proactive_calendar_insight_notif_enabled" -> PhantomPrefs.setProactiveCalendarInsightNotifEnabled(appCtx, enabled)
            "proactive_calendar_web_search_enabled" -> PhantomPrefs.setProactiveCalendarWebSearchEnabled(appCtx, enabled)
            "memory_auto_extract_enabled" -> PhantomPrefs.setMemoryAutoExtractEnabled(appCtx, enabled)
            "chat_pending_capsules_enabled" -> PhantomPrefs.setChatPendingCapsulesEnabled(appCtx, enabled)
            "notif_persistent_icons" -> {
                PhantomPrefs.setPersistentNotifShowIcons(appCtx, enabled)
                StatsForegroundService.startOrRefresh(appCtx, force = true)
            }
            "notif_ongoing" -> PhantomPrefs.setRecordOngoingEnabled(appCtx, enabled)
            "notif_quiet_hours" -> PhantomPrefs.setQuietHoursEnabled(appCtx, enabled)
            "notif_swipe_delete" -> PhantomPrefs.setSwipeToDeleteEnabled(appCtx, enabled)
            "notif_digit_variance" -> PhantomPrefs.setSameKeyDigitVarianceThrottleEnabled(appCtx, enabled)
            "notif_reply_proxy" -> PhantomPrefs.setReplyProxyEnabled(appCtx, enabled)
            "appearance_glass_effect" -> ThemePreferences.setGlassEffectEnabled(appCtx, enabled)
            "appearance_haptic" -> PhantomPrefs.setHapticFeedbackEnabled(appCtx, enabled)
            "intel_enabled" -> PhantomPrefs.setIntelEnabled(appCtx, enabled)
            "personal_summary_collapse" -> PhantomPrefs.setSummaryDefaultCollapsed(appCtx, enabled)
            "stats_briefing",
            "stats_weekly_bar",
            "stats_risk_dist",
            "stats_hourly",
            "stats_daily_top_apps",
            "stats_ai_tags",
            "stats_harassment",
            "stats_top_risky",
            "stats_risk_trend",
            "stats_unread_ratio",
            "stats_app_share",
            "stats_todo_done" -> PhantomPrefs.setStatsCardVisible(appCtx, id, enabled)
            "chat_bubble_timestamp_enabled" -> PhantomPrefs.setChatTimestampEnabled(appCtx, enabled)
            "diary_daily_enabled" -> {
                PhantomPrefs.setDiaryDailyEnabled(appCtx, enabled)
                com.phantom.checker.service.DiaryPolicy.schedule(appCtx)
            }
            "goal_tracking_enabled" -> PhantomPrefs.setGoalTrackingEnabled(appCtx, enabled)
            "health_briefing_enabled" -> PhantomPrefs.setHealthBriefingEnabled(appCtx, enabled)
            "health_soft_invite_enabled" -> PhantomPrefs.setHealthSoftInviteEnabled(appCtx, enabled)
            "update_auto_check" -> PhantomPrefs.setUpdateAutoCheckEnabled(appCtx, enabled)
            else -> return null
        }
        PhantomSettingsBus.notifyChanged()
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
        val zeroLabel: String? = null,
        /** 選項型數值設定：以整數索引對應的選項標籤（如 0=一般條列、1=中央堆疊） */
        val valueLabels: List<String>? = null
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
            when {
                valueLabels != null -> valueLabels.getOrNull(value) ?: "$value $unit"
                value == 0 && zeroLabel != null -> zeroLabel
                else -> "$value $unit"
            }
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
            unit = "次",
            zeroLabel = "不限"
        ),
        NumericSettingSpec(
            id = "notif_throttle",
            title = "同 key 重複節流秒數",
            min = 0,
            max = 300,
            step = 10,
            unit = "秒",
            zeroLabel = "0 秒（關閉）"
        ),
        NumericSettingSpec(
            id = "ai_min_trigger_length",
            title = "AI 分析觸發字數門檻",
            min = 1,
            max = 30,
            step = 1,
            unit = "字"
        ),
        NumericSettingSpec(
            id = "home_card_layout",
            title = "首頁卡片排列模式",
            min = 0,
            max = 1,
            step = 1,
            unit = "模式",
            valueLabels = listOf("一般條列", "中央堆疊")
        ),
        NumericSettingSpec(
            id = "notes_card_layout",
            title = "記事卡片排列模式",
            min = 0,
            max = 1,
            step = 1,
            unit = "模式",
            valueLabels = listOf("一般條列", "中央堆疊")
        ),
        NumericSettingSpec(
            id = "memory_normal_top_k",
            title = "記憶注入筆數（正常模式）",
            min = PhantomPrefs.MIN_MEMORY_NORMAL_TOP_K,
            max = PhantomPrefs.MAX_MEMORY_NORMAL_TOP_K,
            step = 1,
            unit = "筆"
        ),
        NumericSettingSpec(
            id = "memory_normal_per_item_chars",
            title = "記憶單筆截斷字數（正常模式）",
            min = PhantomPrefs.MIN_MEMORY_NORMAL_PER_ITEM_CHARS,
            max = PhantomPrefs.MAX_MEMORY_NORMAL_PER_ITEM_CHARS,
            step = 10,
            unit = "字"
        ),
        NumericSettingSpec(
            id = "memory_saver_top_k",
            title = "記憶注入筆數（省 token 模式）",
            min = PhantomPrefs.MIN_MEMORY_SAVER_TOP_K,
            max = PhantomPrefs.MAX_MEMORY_SAVER_TOP_K,
            step = 1,
            unit = "筆"
        ),
        NumericSettingSpec(
            id = "proactive_daily_cap",
            title = "每日主動推播上限",
            min = 1,
            max = 20,
            step = 1,
            unit = "則"
        ),
        NumericSettingSpec(
            id = "calendar_insight_interval_minutes",
            title = "日曆情報檢查頻率",
            min = 30,
            max = 180,
            step = 30,
            unit = "分鐘"
        ),
        NumericSettingSpec(
            id = "decision_confirm_threshold",
            title = "雙門檻自動決策置信閾值",
            min = 40,
            max = 85,
            step = 5,
            unit = "分"
        )
    )

    fun findNumericSpec(id: String): NumericSettingSpec? = NUMERIC_SPECS.firstOrNull { it.id == id }

    fun getNumericValue(ctx: Context, id: String): Int? {
        val appCtx = ctx.applicationContext
        return when (id) {
            "ai_chat_rag_limit" -> PhantomPrefs.getChatRagMessageLimit(appCtx)
            "ai_daily_limit" -> PhantomPrefs.getDailyAiLimit(appCtx)
            "notif_throttle" -> PhantomPrefs.getSameKeyThrottleSeconds(appCtx)
            "ai_min_trigger_length" -> PhantomPrefs.getAiMinTriggerLength(appCtx)
            "home_card_layout" -> when (PhantomPrefs.getHomeCardLayoutMode(appCtx)) {
                PhantomPrefs.HomeCardLayoutMode.LIST -> 0
                PhantomPrefs.HomeCardLayoutMode.CENTER -> 1
            }
            "notes_card_layout" -> when (PhantomPrefs.getNoteCardLayoutMode(appCtx)) {
                PhantomPrefs.NoteCardLayoutMode.LIST -> 0
                PhantomPrefs.NoteCardLayoutMode.CENTER -> 1
            }
            "memory_normal_top_k" -> PhantomPrefs.getMemoryNormalTopK(appCtx)
            "memory_normal_per_item_chars" -> PhantomPrefs.getMemoryNormalPerItemChars(appCtx)
            "memory_saver_top_k" -> PhantomPrefs.getMemorySaverTopK(appCtx)
            "proactive_daily_cap" -> PhantomPrefs.getProactiveDailyCap(appCtx)
            "calendar_insight_interval_minutes" -> PhantomPrefs.getCalendarInsightIntervalMinutes(appCtx)
            "decision_confirm_threshold" -> PhantomPrefs.getDecisionConfirmThreshold(appCtx)
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
            "ai_min_trigger_length" -> PhantomPrefs.setAiMinTriggerLength(appCtx, target)
            "home_card_layout" -> PhantomPrefs.setHomeCardLayoutMode(
                appCtx,
                if (target == 1) PhantomPrefs.HomeCardLayoutMode.CENTER else PhantomPrefs.HomeCardLayoutMode.LIST
            )
            "notes_card_layout" -> PhantomPrefs.setNoteCardLayoutMode(
                appCtx,
                if (target == 1) PhantomPrefs.NoteCardLayoutMode.CENTER else PhantomPrefs.NoteCardLayoutMode.LIST
            )
            "memory_normal_top_k" -> PhantomPrefs.setMemoryNormalTopK(appCtx, target)
            "memory_normal_per_item_chars" -> PhantomPrefs.setMemoryNormalPerItemChars(appCtx, target)
            "memory_saver_top_k" -> PhantomPrefs.setMemorySaverTopK(appCtx, target)
            "proactive_daily_cap" -> PhantomPrefs.setProactiveDailyCap(appCtx, target)
            "calendar_insight_interval_minutes" -> {
                val snapped = listOf(30, 60, 120, 180).minByOrNull { kotlin.math.abs(it - target) } ?: 60
                PhantomPrefs.setCalendarInsightIntervalMinutes(appCtx, snapped)
                com.phantom.checker.service.CalendarInsightScheduler.scheduleOrUpdate(appCtx)
            }
            "decision_confirm_threshold" -> PhantomPrefs.setDecisionConfirmThreshold(appCtx, target)
            else -> return null
        }
        PhantomSettingsBus.notifyChanged()
        return Triple(before, target, wasClamped)
    }

    /** 供 ChatScreen system prompt 動態產生可調整數值設定清單（含範圍與目前值） */
    fun buildNumericPromptList(ctx: Context): String {
        val appCtx = ctx.applicationContext
        return NUMERIC_SPECS.joinToString("\n") { spec ->
            val current = getNumericValue(appCtx, spec.id) ?: spec.min
            val optionsPart = spec.valueLabels?.let { labels ->
                " | 選項=" + labels.mapIndexed { i, l -> "$i=$l" }.joinToString("/")
            } ?: ""
            "- id=${spec.id} | 名稱=${spec.title} | 範圍=${spec.min}–${spec.max}（步進 ${spec.step} ${spec.unit}） | 目前值=${spec.formatValue(current)}$optionsPart"
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
            "載入", "閱讀", "筆數", "訊息數", "額度", "節流", "上限", "秒數", "記憶",
            "單次", "rag", "limit", "quota", "throttle", "調", "改", "設"
        ).any { lower.contains(it) }
        if (!hasNumericIntentKeyword) return null

        val explicitRules: List<Pair<String, List<String>>> = listOf(
            "memory_saver_top_k" to listOf(
                "省 token 記憶", "省token記憶", "省電記憶", "saver_top_k"
            ),
            "memory_normal_top_k" to listOf(
                "記憶注入筆數", "記憶筆數", "注入記憶筆數", "normal_top_k"
            ),
            "memory_normal_per_item_chars" to listOf(
                "記憶單筆字數", "記憶截斷字數", "記憶字數"
            ),
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
