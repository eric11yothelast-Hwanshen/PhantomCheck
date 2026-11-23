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
        SettingSearchItem("quick_token_saver", "quick", "省 token 模式", "暫停每則 AI 掃描並降載對話注入，僅命中可疑特徵才分析", listOf("省電", "省錢", "節流", "api", "token", "額度", "省流", "省額度", "降低消耗", "暫停掃描")),
        SettingSearchItem("quick_persistent_notif", "quick", "常駐統計通知", "通知欄即時統計與 App 圖示列", listOf("通知", "常駐", "狀態列", "統計通知", "背景", "頂部通知", "下拉通知欄", "常駐列", "通知中心")),
        SettingSearchItem("quick_ai_risk", "quick", "AI 風險分析", "內文達觸發字數門檻且未命中過濾規則時自動評估風險（門檻可於「AI 分析觸發字數門檻」調整）", listOf("防詐", "風險", "偵測", "分析", "ai", "掃描", "詐騙檢查", "自動判讀", "風險評分", "防騙")),
        SettingSearchItem("quick_scam_popup", "quick", "詐騙警示彈出通知", "高風險訊息即時彈出警示通知", listOf("詐騙", "警示", "推播", "高風險", "alert", "彈窗", "詐騙提醒", "危險通知", "防詐警告")),
        SettingSearchItem("ai_auto_tag", "quick", "每則訊息 AI 智慧掃描", "AI 分析時自動指派「必看／行銷／垃圾通知」等標籤", listOf("標籤", "自動分類", "智慧掃描", "tag", "必看", "垃圾通知", "行銷分類", "自動貼標")),
        SettingSearchItem("ai_providers", "ai", "多 Provider AI 設定", "管理 OpenAI / Gemini / Groq / 自訂端點與 API Key", listOf("key", "apikey", "模型", "openai", "gemini", "provider", "金鑰", "mistral", "deepseek", "openrouter", "換模型", "金鑰設定"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_tag_dispatch_test", "ai", "AI 標籤分發測試", "以固定樣本訊息測試目前 AI Provider 的風險與自動標籤分發結果", listOf("測試", "分發", "標籤測試", "智慧掃描", "失敗", "tag", "連線測試", "模型測試", "試跑分析")),
        SettingSearchItem("ai_chat_suggestions", "ai", "AI 助手常用指令選單", "在 AI 助手輸入列 + 選單顯示常用指令（關閉後僅保留圖片上傳）", listOf("常用指令", "建議問題", "快速提問", "昨日簡報", "助手", "加號選單", "推薦問題", "對話建議")),
        SettingSearchItem("ai_chat_tab_behavior", "ai", "切到 AI 助手時接續上次對話", "預設為開啟新對話；開啟後切換到 AI 助手分頁時自動載入最近一次對話", listOf("接續", "繼續", "上次對話", "新對話", "歷史對話", "session", "預設對話", "保留聊天", "自動載入對話")),
        SettingSearchItem("ai_quick_commands", "ai", "AI 助手常用指令與預設模式", "管理自訂常用指令，設定點擊後「直接執行」或「預輸入」", listOf("常用指令", "快捷指令", "直接執行", "預輸入", "指令", "command", "自訂快捷", "一鍵提問", "聊天快捷鍵")),
        SettingSearchItem("ai_daily_limit", "ai", "每日 AI 額度上限", "限制每日 AI 呼叫次數", listOf("額度", "上限", "次數", "quota", "limit", "每日額度", "每日ai額度", "每日上限", "呼叫上限", "用量限制", "一天幾次")),
        SettingSearchItem("ai_chat_rag_limit", "ai", "AI 助手單次閱讀訊息數", "調整 AI 助手每次讀取的歷史訊息上限（50–800 則；省 token 模式降為 100）", listOf("閱讀", "筆數", "訊息數", "rag", "context", "上下文", "400", "上限", "單次閱讀訊息數", "單次載入訊息數", "載入筆數", "閱讀訊息數", "載入訊息數", "讀幾則", "歷史筆數")),
        SettingSearchItem("ai_auto_reply", "ai", "SMS 自動回覆設定", "開啟 SMS 自動回覆設定頁：啟用開關、回覆時段與回覆範本", listOf("自動回覆", "sms", "簡訊", "回覆", "時段", "reply", "簡訊代回", "忙碌回覆", "自動回簡訊"), page = "AutoReplySettingsActivity"),
        SettingSearchItem("ai_scam_center", "ai", "詐騙警示中心", "檢視高風險詐騙記錄與處置（可由訊息頁風險卡或統計頁進入）", listOf("詐騙", "警示", "中心", "高風險", "封鎖", "可疑訊息", "防詐中心", "風險清單", "165"), page = "ScamCenterActivity"),
        SettingSearchItem("ai_alert_keywords", "ai", "即時提醒關鍵字", "設定自訂即時提醒關鍵字，命中時觸發通知提醒", listOf("關鍵字", "提醒", "keyword", "重要字詞", "命中通知", "特定字眼提醒", "自訂提醒詞")),
        SettingSearchItem("ai_min_trigger_length", "ai", "AI 分析觸發字數門檻", "設定新訊息內容至少達幾字才觸發 AI 分析（1–30 字，預設 10 字）", listOf("字數", "長度", "門檻", "觸發", "最短", "幾個字", "分析字數", "短訊息跳過", "最少字數", "幾字以上")),
        SettingSearchItem("ai_offline_retry_enabled", "ai", "離線 AI 分析自動補佇列", "斷網或暫時性錯誤失敗時自動排入佇列，恢復網路後補齊風險分析", listOf("離線", "補分析", "重試", "佇列", "網路恢復", "沒網路", "斷線重試", "背景補掃")),
        SettingSearchItem("ai_auto_memo_enabled", "ai", "自動備忘錄", "AI 分析訊息時自動擷取約定時間、繳費期限、取貨編號等備忘事項寫入記事分頁", listOf("備忘錄", "自動筆記", "memo", "記事", "筆記", "取貨碼", "自動記待辦", "繳費提醒", "自動摘錄")),
        SettingSearchItem("ai_tag_failure_notif_enabled", "ai", "AI 標籤分發失敗通知", "24 小時內 AI 標籤分發累積失敗 5 次時發送低干擾提醒（含 6 小時冷卻）", listOf("分發失敗", "標籤通知", "AI 失敗提醒", "失敗通知", "標籤失敗", "api錯誤提醒", "分析失敗通知", "金鑰失效提醒")),
        SettingSearchItem("ai_dual_model_routing", "ai", "雙模型信心路由", "先以輕量模型快速判斷風險，30~70 分模糊帶才升級主模型重判（不重複扣額度）", listOf("雙模型", "信心路由", "省 token", "routing", "small", "省錢", "大小模型", "兩段式分析", "智慧路由")),
        SettingSearchItem("ai_dual_model_routing_model", "ai", "雙模型路由輕量模型", "設定信心路由第一關使用的輕量模型名稱（預設 mistral-small-2603）", listOf("路由模型", "輕量模型", "mistral-small-2603", "routing", "第一關模型", "小模型名稱", "初篩模型")),
        SettingSearchItem("ai_model_chat", "ai", "分用途模型：AI 助手對話", "為 AI 助手對話（含圖片）獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "對話模型", "切換模型", "chat", "聊天模型", "助手模型", "看圖模型"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_message_analysis", "ai", "分用途模型：每則訊息風險分析", "為新訊息自動風險分析獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "分析模型", "風險分析", "message", "掃描模型", "防詐模型", "即時分析模型"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_weekly_summary", "ai", "分用途模型：每週摘要", "為每週 AI 摘要獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "摘要模型", "每週摘要", "weekly", "週報模型", "統計週報", "本週摘要模型"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_deep_analysis", "ai", "分用途模型：訊息深度分析", "為訊息深度分析獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "深度分析模型", "deep", "詳情分析", "單則深度解說", "高階分析模型"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_settings_search", "ai", "分用途模型：設定 AI 語意搜尋", "為設定頁 AI 語意搜尋獨立選擇 Provider 模型或跟隨預設", listOf("模型", "分用途", "搜尋模型", "設定搜尋", "search", "語意搜尋模型", "找設定模型"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("notif_style", "notif", "常駐通知樣式", "控制常駐通知為精簡或詳細樣式", listOf("常駐通知", "精簡", "詳細", "樣式", "通知欄外觀", "常駐大小", "通知折疊")),
        SettingSearchItem("notif_persistent_icons", "notif", "常駐通知顯示 App 圖示條", "於常駐統計通知內顯示最近收到訊息的 App 圖示", listOf("常駐通知", "圖示列", "圖示條", "icon", "app小圖示", "狀態欄圖標", "通知欄圖示")),
        SettingSearchItem("notif_ongoing", "notif", "記錄持續性通知", "是否記錄音樂播放、下載進度等持續顯示中的通知", listOf("持續性", "ongoing", "音樂", "下載", "播放中", "常駐型通知", "導航通知", "計時器通知")),
        SettingSearchItem("notif_quiet_hours", "notif", "勿擾時段", "指定時段內暫停詐騙警示與關鍵字推播", listOf("勿擾", "靜音", "夜間", "睡覺", "quiet", "半夜不響", "通知太吵", "晚上安靜", "休息時間")),
        SettingSearchItem("notif_filter_rules", "notif", "過濾關鍵字規則與命中動作", "新增過濾關鍵字並選擇命中後標記已過濾、不記錄或詐騙警示", listOf("過濾", "關鍵字", "黑名單", "filter", "廣告過濾", "垃圾攔截", "不記錄關鍵字", "擋廣告")),
        SettingSearchItem("notif_interaction", "notif", "通知點擊行為", "調整點擊通知開啟幻訊或原 App", listOf("點擊", "開啟", "通知行為", "點通知跳轉", "打開原app", "點一下開啟")),
        SettingSearchItem("notif_swipe_delete", "notif", "對話頁滑動刪除", "在對話訊息氣泡向左滑動快速刪除", listOf("左滑", "滑動刪除", "swipe", "手勢刪除", "滑掉訊息", "對話左滑")),
        SettingSearchItem("notif_throttle", "notif", "重複通知節流與個別 App 覆寫", "避免相同通知短時間重複記錄，可自訂節流秒數與單一 App 例外", listOf("節流", "同 key", "重複通知", "漏訊", "覆寫", "節流秒數", "重複節流", "通知太吵", "洗版防制", "連續通知冷卻")),
        SettingSearchItem("notif_digit_variance", "notif", "數字微變視為同一則更新", "對電量、流量等監控通知，僅數字變動時視為同一則更新並節流", listOf("數字微變", "節流", "電量", "流量", "百分比跳動", "網速通知", "進度百分比")),
        SettingSearchItem("notif_reply_proxy", "notif", "通知欄連續快速回覆", "透過 AI 助手回覆後於通知欄保留對話輸入框，可連續回覆多則", listOf("連續回覆", "快速回覆", "代理回覆", "通知欄回覆", "reply", "直接回訊息", "免開app回覆", "下拉直接回")),
        SettingSearchItem("notif_reply_proxy_timeout", "notif", "回覆通知閒置自動收起", "設定代理回覆通知閒置多久後自動收起（15 / 30 / 60 分鐘或不自動收起）", listOf("連續回覆", "逾時", "自動收起", "閒置", "回覆通知", "回覆框消失", "自動關閉回覆")),
        SettingSearchItem("notif_my_display_names", "notif", "我的名稱（自我身分偵測）", "設定自己在群組通知中的顯示名稱，命中時標記為自己傳送且不計入統計", listOf("我的名稱", "自我身分", "我的顯示名稱", "自己回覆", "群組名稱", "isOwn", "自己的名字", "排除自己傳的", "群組暱稱")),
        SettingSearchItem("notif_clear_read", "notif", "清除已讀訊息", "永久刪除所有已讀訊息（保留未讀訊息）", listOf("清除已讀", "已讀", "刪除已讀", "清已讀", "釋放空間", "清理看過的", "一鍵刪已讀")),
        SettingSearchItem("data_retention", "data", "訊息保留與清理", "訊息保留天數與最大保留則數上限", listOf("保留", "天數", "上限", "清理", "自動刪除舊訊息", "留幾天", "儲存上限", "資料庫容量")),
        SettingSearchItem("data_muted_apps", "data", "App 靜音清單", "設定特定 App 完全不記錄或靜默記錄", listOf("靜音", "封鎖", "app", "略過", "不攔截app", "忽略應用程式", "不收通知", "郵件通知", "信箱靜音")),
        SettingSearchItem("ai_rules", "data", "自訂標籤規則管理", "管理自訂標籤、App 分類與使用者標籤規則", listOf("標籤", "規則", "分類", "標籤管理", "自訂分類", "郵件標籤", "app歸類", "條件貼標"), page = "TagManagementActivity"),
        SettingSearchItem("whitelist_management", "data", "白名單管理", "依 App 分組檢視、新增、啟停或刪除寄件者與訊息白名單", listOf("白名單", "whitelist", "信任", "寄件者", "免警示", "略過詐騙", "誤判排除", "安全名單", "信任聯絡人"), page = "WhitelistManagementActivity"),
        SettingSearchItem("data_export_clear", "data", "匯出資料與清除紀錄", "匯出 CSV 或刪除全部訊息", listOf("匯出", "備份", "csv", "清除", "刪除全部", "清空資料庫", "下載紀錄", "轉出報表")),
        SettingSearchItem("appearance_widget_tour", "data", "教學重播與桌面小工具", "重播新手或設定頁教學；統計小工具請在桌面長按 → 小工具加入", listOf("小工具", "widget", "桌面", "導覽", "教學", "新手引導", "使用說明", "桌面捷徑")),
        SettingSearchItem("appearance_theme", "appearance", "主題模式、動態取色與字體大小", "深淺色主題、Material You 動態色與字體縮放", listOf("外觀", "主題", "字體", "大小", "動態取色", "深色", "淺色", "字太大", "字太小", "字級", "暗黑模式", "夜間模式", "文字縮放")),
        SettingSearchItem("appearance_glass_effect", "appearance", "液態玻璃模糊效果", "Android 12+ 模糊特效；更低版本或省電模式自動停用", listOf("液態玻璃", "玻璃", "模糊", "特效", "glass", "毛玻璃", "背景虛化", "半透明模糊")),
        SettingSearchItem("appearance_tab_bar_mode", "appearance", "底部分頁欄顯示模式", "切換底部分頁欄為「標準」、「縮小」或「自動隱藏」", listOf("分頁列", "分頁欄", "導覽列", "縮小", "自動隱藏", "底部", "tabbar", "下方選單", "底部按鈕列", "滑動隱藏底欄")),
        SettingSearchItem("appearance_haptic", "appearance", "觸覺回饋", "切換分頁與長按操作時提供震動回饋", listOf("震動", "觸覺", "haptic", "按鍵震動", "手感回饋", "關閉震動")),
        SettingSearchItem("appearance_keepalive", "appearance", "通知權限、電池優化與保活指引", "檢查通知存取權、電池優化白名單、OEM 自啟動與前景保活服務", listOf("權限", "電池", "保活", "自啟動", "小米", "三星", "背景被殺", "收不到通知", "省電白名單", "背景執行", "hyperos", "one ui", "coloros")),
        SettingSearchItem("intel_enabled", "intel", "啟用公開防詐情報預查", "收到含網址或加密貨幣地址的訊息時自動查詢防詐情報庫", listOf("防詐情報", "網址檢查", "情報預查", "intel", "釣魚網址", "短網址掃描", "惡意連結")),
        SettingSearchItem("intel_sources", "intel", "防詐情報四來源與 API Key", "設定 fraud.tw / 165、Google Safe Browsing、PhishTank、Chainabuse 開關與金鑰", listOf("防詐情報", "165", "fraud", "safebrowsing", "phishtank", "chainabuse", "url", "網址檢查", "金鑰", "虛擬貨幣詐騙", "釣魚網站庫")),
        SettingSearchItem("shizuku_mode", "shizuku", "Shizuku 進階模式", "透過 Shizuku 取得進階系統資訊或保活輔助", listOf("shizuku", "進階", "root", "adb", "無線偵錯", "自動授權", "強化保活")),
        SettingSearchItem("personal_density", "personalization", "卡片密度（舒適 / 緊湊）", "調整首頁訊息卡片與對話氣泡的間距與行數密度", listOf("個人化", "密度", "緊湊", "舒適", "間距", "compact", "訊息卡", "訊息卡調整", "卡片高度", "卡片太高", "卡片大小", "列表間距", "行距")),
        SettingSearchItem("personal_sort", "personalization", "首頁 App 排序", "切換首頁 App 清單依時間新→舊、未讀多→少或風險高→低排序", listOf("個人化", "排序", "未讀", "風險", "時間", "sort", "訊息卡順序", "排列順序", "誰在最上面", "app順序")),
        SettingSearchItem("personal_filters", "personalization", "首頁篩選欄顯示項目", "自訂首頁篩選欄是否顯示星標、詐騙、已過濾與標籤按鈕", listOf("個人化", "篩選", "filter", "星標", "標籤", "隱藏", "頂部按鈕", "分類按鈕", "訊息篩選列")),
        SettingSearchItem("personal_summary_collapse", "personalization", "摘要卡預設收合", "控制本週 AI 摘要與早晚簡報卡是否預設收合為單行預覽", listOf("個人化", "摘要", "收合", "展開", "折疊", "週報收起", "簡報卡折疊", "統計摘要展開")),
        SettingSearchItem("personal_home_card_layout", "personalization", "首頁卡片排列模式", "切換首頁 App 卡片為一般條列或中央堆疊（焦點卡片在正中央、上下對稱縮小堆疊的垂直滾輪）", listOf("堆疊", "條列", "卡片排列", "中央堆疊", "滾輪", "重疊", "個人化", "訊息卡", "訊息卡調整", "卡片高度", "卡片太高", "92dp", "首頁訊息卡", "訊息頁面卡片")),
        SettingSearchItem("personal_notes_card_layout", "personalization", "記事卡片排列模式", "獨立設定記事分頁卡片排列：一般條列或中央堆疊滾輪（不影響首頁）", listOf("記事", "備忘錄", "卡片排列", "堆疊", "滾輪", "中央堆疊", "記事卡高度", "筆記排列", "記事頁堆疊")),
        SettingSearchItem("personal_notes_todo_collapsed", "personalization", "記事分頁待辦事項收合", "收起記事分頁上方的待辦事項區，僅顯示最快到期的未完成待辦以節省空間", listOf("待辦", "收合", "收起", "展開", "待辦事項", "折疊待辦", "記事頂部待辦", "隱藏待辦清單")),
        SettingSearchItem("personal_home_filter_chips", "personalization", "首頁篩選標籤列收合", "將首頁頂部「全部／未讀」等篩選標籤收合成單行，點箭頭可展開（不與記事分頁共用）", listOf("篩選", "標籤", "chip", "收合", "未讀", "換行", "單行標籤", "折疊篩選列", "首頁頂部標籤")),
        SettingSearchItem("personal_time_format", "personalization", "時間顯示格式（12 / 24 小時制）", "切換訊息與對話的時間顯示格式", listOf("個人化", "時間", "12小時", "24小時", "am", "pm", "上午下午", "幾點幾分格式", "時制切換")),
        SettingSearchItem("bg_global", "personalization", "全域背景圖片", "設定套用於訊息中心、統計、AI 助手與對話頁的全域底圖", listOf("個人化", "背景", "桌布", "wallpaper", "圖片", "全域", "換底圖", "自訂背景", "相簿背景")),
        SettingSearchItem("bg_chat", "personalization", "AI 聊天背景圖片", "自訂 AI 助手分頁背景圖片（未設定時沿用全域背景）", listOf("個人化", "背景", "桌布", "wallpaper", "AI", "聊天", "聊天室底圖", "助手背景", "對話桌布")),
        SettingSearchItem("stats_card_visibility", "stats", "統計頁卡片顯示自訂", "勾選要在統計分頁顯示的圖表與統計卡片", listOf("統計", "圖表", "排行榜", "自訂", "卡片", "折線圖", "環形圖", "隱藏統計卡", "統計頁排版", "顯示圖表")),
        SettingSearchItem("stats_briefing", "stats", "統計卡：早晚簡報", "控制統計頁是否顯示最近一次早晚簡報卡片", listOf("統計", "早晚簡報", "簡報", "briefing", "stats", "統計頁簡報", "早報卡", "晚報卡")),
        SettingSearchItem("stats_weekly_bar", "stats", "統計卡：本週 7 日訊息量", "控制統計頁是否顯示本週 7 日訊息量長條圖", listOf("統計", "本週", "7日", "長條圖", "訊息量", "每日訊息柱狀圖", "一週趨勢圖", "七天訊息數")),
        SettingSearchItem("stats_risk_dist", "stats", "統計卡：風險分布三色", "控制統計頁是否顯示風險分布三色計數卡", listOf("統計", "風險分布", "安全", "疑似", "詐騙", "三色卡", "紅黃綠統計", "風險比例")),
        SettingSearchItem("stats_hourly", "stats", "統計卡：今日每小時分布", "控制統計頁是否顯示今日 24 小時訊息分布圖", listOf("統計", "每小時", "時段", "分布", "24小時圖表", "活躍時段", "幾點最多訊息")),
        SettingSearchItem("stats_daily_top_apps", "stats", "統計卡：今日 App 訊息量排行榜 Top 5", "控制統計頁是否顯示今日收到訊息最多的前五名 App 排行榜", listOf("統計", "排行榜", "top5", "今日", "app", "前五名", "今天最吵app", "通知最多app")),
        SettingSearchItem("stats_ai_tags", "stats", "統計卡：今日 AI 智慧標記", "控制統計頁是否顯示今日 AI 智慧標記分類統計", listOf("統計", "ai標記", "智慧標記", "標籤", "分類計數", "必看行銷統計", "今日標籤數")),
        SettingSearchItem("stats_harassment", "stats", "統計卡：騷擾佔比環形", "控制統計頁是否顯示騷擾佔比環形圖", listOf("統計", "騷擾", "行銷", "垃圾通知", "環形圖", "圓餅圖", "廣告比例", "垃圾訊息佔比")),
        SettingSearchItem("stats_top_risky", "stats", "統計卡：Top 5 高風險 App", "控制統計頁是否顯示累計疑似與詐騙訊息前五名 App", listOf("統計", "高風險", "top5", "詐騙app", "危險app排行", "最多詐騙來源", "可疑app前五")),
        SettingSearchItem("stats_risk_trend", "stats", "統計卡：7 日風險趨勢折線", "控制統計頁是否顯示近 7 日疑似與詐騙訊息趨勢折線圖", listOf("統計", "風險趨勢", "折線圖", "7日", "詐騙趨勢", "一週詐騙曲線", "七天風險變化")),
        SettingSearchItem("stats_unread_ratio", "stats", "統計卡：未讀 / 已讀比例環形", "控制統計頁是否顯示未讀與已讀訊息比例環形圖", listOf("統計", "未讀", "已讀", "比例", "環形圖", "未讀率", "已讀圓餅圖", "看過比例")),
        SettingSearchItem("stats_app_share", "stats", "統計卡：各 App 訊息佔比橫條前 8", "控制統計頁是否顯示各 App 訊息佔比前 8 名橫條圖", listOf("統計", "佔比", "app佔比", "橫條圖", "top8", "前八名", "總訊息排行", "長條占比")),
        SettingSearchItem("stats_todo_done", "stats", "統計卡：待辦完成率環形", "控制統計頁是否顯示待辦事項完成率環形圖", listOf("統計", "待辦", "完成率", "環形圖", "todo", "任務達成率", "待辦進度圖", "完成比例")),
        SettingSearchItem("bg_conversation", "personalization", "對話頁背景圖片", "自訂對話頁背景圖片（未設定時沿用全域背景）", listOf("個人化", "背景", "桌布", "wallpaper", "對話", "單一對話底圖", "訊息詳情背景", "聊天清單桌布")),
        SettingSearchItem("bg_scrim", "personalization", "背景暗色遮罩強度", "調整背景圖片上的暗色遮罩透明度以確保文字可讀性", listOf("個人化", "背景", "遮罩", "scrim", "暗度", "對比", "底圖太亮", "壓暗背景", "背景透明度")),
        SettingSearchItem("glass_strength", "personalization", "玻璃卡片強度", "調整液態玻璃卡片的表面不透明度與視覺強度", listOf("個人化", "玻璃", "強度", "毛玻璃", "透明", "glass", "卡片透明度", "玻璃濃度", "訊息卡透明")),
        SettingSearchItem("appearance_card_bg", "appearance", "卡片底色模式", "選擇跟隨玻璃強度、透明模式（0–100% 滑桿）或深色底", listOf("卡片", "底色", "背景", "透明", "深色", "黑底", "外觀", "訊息卡底色", "卡片顏色", "純黑卡片")),
        SettingSearchItem("assistant_header_background", "personalization", "夥伴頁頭部背景", "自訂或移除夥伴（助手）頁頂部橫幅背景圖片", listOf("夥伴", "助手", "頭部", "橫幅", "背景", "封面", "圖片", "banner", "夥伴頁頂圖", "看板背景")),
        SettingSearchItem("chat_font_scale", "personalization", "AI 聊天室字體獨立縮放", "獨立調整 AI 聊天室氣泡文字大小（0.85x–1.30x），不影響系統字體與其他分頁", listOf("聊天字體", "AI字體", "字體大小", "聊天縮放", "font", "字太大", "字太小", "字級", "對話文字大小", "氣泡字體")),
        SettingSearchItem("messages_font_scale", "personalization", "訊息中心字體獨立縮放", "獨立調整首頁訊息中心清單字體大小（0.85x–1.30x），不影響其他分頁", listOf("訊息字體", "首頁字體", "訊息大小", "清單縮放", "字太大", "字太小", "字級", "訊息卡字體", "訊息卡調整", "通知字體大小")),
        SettingSearchItem("chat_bubble_corner_dp", "personalization", "聊天氣泡圓角半徑", "調整 AI 聊天室對話氣泡的圓角弧度（8–24 dp，預設 18 dp）", listOf("氣泡", "圓角", "對話框", "bubble", "外觀", "弧度", "方角氣泡", "圓弧度")),
        SettingSearchItem("chat_bubble_width_ratio", "personalization", "聊天氣泡寬度比例", "調整 AI 聊天室對話氣泡的最大寬度比例（70%–95%，預設 82%）", listOf("氣泡寬度", "對話寬度", "bubble", "排版", "聊天寬度", "對話框太窄", "氣泡太寬", "訊息框寬度")),
        SettingSearchItem("favorite_apps_max_visible", "personalization", "首頁我的最愛 App 顯示上限", "限制首頁「我的最愛 App」橫列顯示的卡片數量（全部／4／6／8）", listOf("我的最愛", "最愛數量", "橫列", "釘選", "首頁", "常用app", "常用清單", "App 常用清單", "最愛列數", "常用應用程式")),
        SettingSearchItem("memory_chat_capture", "memory_assistant", "聊天記憶擷取", "允許 AI 在對話中擷取個人事實、人物、行程與偏好", listOf("記憶", "記憶庫", "擷取", "聊天記憶", "自動記憶", "memory", "確認卡", "記住我說的話", "個人偏好記憶", "對話記憶")),
        SettingSearchItem("memory_write_confirm", "memory_assistant", "記憶寫入前先顯示確認卡", "開啟時，AI 產生的新記憶會先在 AI 助手對話中顯示確認卡，由您選擇寫入／保留；關閉時直接寫入（敏感記憶與衝突仍必經確認）。您隨時可在記憶頁手動新增、編輯或刪除記憶。", listOf("確認卡", "記憶確認", "寫入確認", "AI 記憶", "先問再記", "記憶同意卡", "手動確認記憶")),
        SettingSearchItem("memory_normal_top_k", "memory_assistant", "記憶注入筆數（正常模式）", "設定正常模式下每次 AI 對話注入的相關記憶筆數上限（1–8 筆，預設 8 筆，總預算 ≤250 tokens）", listOf("記憶", "注入", "筆數", "topk", "正常模式", "記憶預算", "記憶筆數", "帶入幾條記憶", "記憶條數")),
        SettingSearchItem("memory_normal_per_item_chars", "memory_assistant", "記憶單筆截斷字數（正常模式）", "設定正常模式下每筆注入記憶的預覽截斷字數（30–120 字，預設 70 字 ≈ 50 tokens）", listOf("記憶", "字數", "截斷", "單筆", "token", "記憶長度", "單條記憶字數", "記憶摘要長度")),
        SettingSearchItem("memory_saver_top_k", "memory_assistant", "記憶注入筆數（省 token 模式）", "獨立設定省 token 模式下每次 AI 對話注入的記憶筆數上限（1–4 筆，預設 4 筆，總預算 ≤120 tokens，與正常模式互不覆寫）", listOf("記憶", "省 token", "省電", "注入", "筆數", "省token記憶", "省流記憶筆數", "低耗記憶上限")),
        SettingSearchItem("assistant_custom_name", "memory_assistant", "AI 助手名字", "自訂 AI 助手顯示稱呼與對話身分（預設「幻訊」）", listOf("助手名字", "名字", "稱呼", "改名", "自訂助手", "幻訊", "name", "幫AI取名", "夥伴名字", "機器人名稱")),
        SettingSearchItem("assistant_personality", "memory_assistant", "AI 助手個性與風格", "選擇內建個性（預設／簡潔專業／親切詳細／嚴謹防詐顧問）或輸入完全自訂個性描述", listOf("個性", "風格", "語氣", "人設", "簡潔", "親切", "嚴謹", "自訂助手", "persona", "說話方式", "AI態度", "角色設定")),
        SettingSearchItem("assistant_verbosity", "memory_assistant", "AI 助手回答詳細度", "切換 AI 助手回答篇幅為精簡、適中或詳細", listOf("詳細度", "精簡", "適中", "詳細", "篇幅", "長短", "回答長度", "verbosity", "話太多", "回答太長", "簡短回答")),
        SettingSearchItem("assistant_avatar", "memory_assistant", "AI 助手頭像", "從相簿選圖、圓形裁切並降採樣儲存為 AI 助手頭像，或重設為預設圖示", listOf("頭像", "大頭貼", "照片", "圖片", "圓形裁切", "avatar", "自訂助手", "換頭貼", "AI大頭照", "夥伴圖片")),
        SettingSearchItem("memory_privacy_tier_info", "memory_assistant", "記憶庫隱私分級說明", "一般記憶可參與本地檢索注入；標為「🔒 敏感」的記憶在任何對外 AI 請求皆由程式碼硬性排除，100% 僅留存本機", listOf("隱私", "敏感", "分級", "外送", "安全", "sensitive", "記憶隱私", "私密記憶", "不出本機", "資料保護")),
        SettingSearchItem("ai_anti_fraud_cross_check", "memory_assistant", "記憶 × 防詐交叉檢查", "結合本機通訊錄與一般記憶做離線矛盾比對（熟人冒充、情境破綻、防詐免疫），絕不外送原始通訊錄或敏感記憶", listOf("防詐", "交叉檢查", "熟人冒充", "通訊錄", "矛盾", "免疫", "記憶防詐", "親友詐騙", "猜猜我是誰", "冒名頂替")),
        SettingSearchItem("calendar_integration", "memory_assistant", "本機日曆整合", "勾選開放給 AI 助手查詢空檔與寫入行程的本機日曆帳號（預設全不勾，日曆資料僅留存本機絕不外送）", listOf("日曆", "行事曆", "行程", "空檔", "撞期", "calendar", "課表查詢", "勾選日曆", "同步行事曆")),
        SettingSearchItem("calendar_write_confirm", "memory_assistant", "日曆寫入前先顯示確認卡", "開啟時，寫入行程前會在 AI 助手對話中顯示行程確認卡並檢查撞期；關閉時仍會執行撞期檢查，無撞期且已授權寫入權限則直接寫入。", listOf("日曆確認", "行程確認", "寫入同意", "日曆", "確認卡", "加行程確認", "行事曆確認卡")),
        SettingSearchItem("schedule_memo_route", "memory_assistant", "行程類通知分流", "行程類通知優先提示加入日曆，不再自動建立待辦/記事", listOf("行程", "分流", "日曆", "待辦", "記事", "建議卡", "schedule", "calendar", "訂位通知", "預約轉日曆")),
        SettingSearchItem("permission_hub", "hub", "權限授予中心", "集中檢視並快速授予通知、簡訊、通訊錄、日曆、精確鬧鐘、電池優化與使用情況權限（Shizuku 保留於獨立卡片）", listOf("權限", "授權", "通知存取", "精確鬧鐘", "電池優化", "受限制的設定", "permission", "hub", "使用情況存取", "螢幕時間權限", "健康權限"), page = "PermissionHubActivity"),
        SettingSearchItem("assistant_hub", "hub", "AI 夥伴與主動推播", "集中管理早晚簡報、主動推播主題、每日上限、勿擾時段、畫像卡、自訂外部搜尋服務與通訊錄整合", listOf("夥伴", "主動推播", "簡報", "畫像卡", "勿擾", "外部搜尋", "通訊錄", "assistant", "hub", "主動提醒設定", "早晚報設定", "AI夥伴中心"), page = "AssistantHubActivity"),
        SettingSearchItem("contacts_integration", "hub", "通訊錄整合（暱稱／匯入／VIP）", "管理通訊軟體暱稱綁定本名、免 AI 額度關係匯入與 VIP 信任圈白名單同步（原始通訊錄絕不外送 AI）", listOf("通訊錄", "聯絡人", "暱稱", "綁定", "匯入", "vip", "信任圈", "contacts", "電話簿", "朋友本名", "熟人白名單"), page = "AssistantHubActivity"),
        SettingSearchItem("briefing_morning_enabled", "hub", "早間個人簡報", "每日定時（預設 08:45）整理今日行程、待辦與重點通知寫入 AI 助手並發送通知", listOf("早間簡報", "早報", "簡報", "主動推播", "briefing", "早上簡報", "晨間簡報", "早安報告", "早上幾點簡報"), page = "AssistantHubActivity"),
        SettingSearchItem("briefing_evening_enabled", "hub", "晚間個人簡報", "每日定時（預設 22:25）回顧今日重點並預覽明日行程與待辦", listOf("晚間簡報", "晚報", "簡報", "主動推播", "briefing", "晚上簡報", "晚上簡報幾點", "夜間簡報", "睡前簡報", "今日回顧"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_daily_cap", "hub", "每日主動提醒上限", "限制 AI 助手每日主動提醒則數上限（預設 6 則，1–20 可調，與簡報上限獨立）", listOf("主動推播", "上限", "節流", "cap", "主動提醒", "推播次數", "通知太吵", "減少主動通知", "一天幾則提醒"), page = "AssistantHubActivity"),
        SettingSearchItem("briefing_daily_cap", "hub", "每日早晚簡報上限", "限制早間與晚間簡報每日合計上限（預設 2 則，1–10 可調，與主動提醒獨立）", listOf("簡報上限", "早晚簡報", "上限", "briefing cap", "一天幾次簡報", "早晚報次數"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_reminder_lead_minutes", "hub", "行程／待辦提醒提前時間", "設定主動提醒提前觸發分鐘數（15／30／60 分鐘，預設 30 分鐘）", listOf("提前提醒", "提前分鐘", "lead", "行程提醒", "待辦提醒", "提早幾分鐘", "開會前提醒", "到期前通知"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_allday_remind_time", "hub", "全天事件提醒時刻", "設定今日全天事件主動提醒開始時刻（HH:mm，預設 09:00）", listOf("全天事件", "提醒時間", "allday", "09:00", "整天行程幾點提醒", "全日活動通知"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_monitor_interval_minutes", "hub", "主動監控檢查間隔", "設定零 token 本地指紋與里程碑背景檢查週期（15／30／60 分鐘，預設 15 分鐘）", listOf("監控頻率", "檢查間隔", "monitor", "worker", "背景掃描間隔", "幾分鐘檢查一次", "巡檢週期"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_dedup_window_minutes", "hub", "主動提醒去重冷卻時間", "同內容主動提醒於此冷卻時間內絕不重複發送（10／30／60 分鐘，預設 10 分鐘）", listOf("去重", "冷卻", "防重複", "dedup", "重複推播間隔", "同樣提醒不重發"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_dnd_enabled", "hub", "主動推播獨立勿擾時段", "設定主動推播的獨立勿擾時段（預設 00:00–07:00，期間仍寫入聊天但不發通知）", listOf("勿擾", "主動推播", "靜音", "dnd", "半夜不推播", "睡覺不響通知", "主動訊息靜音", "通知太吵"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_calendar_insight_enabled", "hub", "主動日曆情報", "定時讀取勾選日曆並依即將到來事件主動彙整情報寫入主動訊息聊天室（預設開啟）", listOf("日曆情報", "主動日曆", "行事曆", "calendar", "insight", "行程情報提醒", "活動行前提醒"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_calendar_insight_notif_enabled", "hub", "日曆情報發送通知", "主動日曆情報產出時是否發送通知（關閉時僅寫入主動訊息聊天室，預設開啟）", listOf("日曆情報", "通知", "calendar", "notif", "行事曆情報推播", "日曆通知開關"), page = "AssistantHubActivity"),
        SettingSearchItem("proactive_calendar_web_search_enabled", "hub", "日曆情報允許上網查詢", "允許對未來 48 小時事件標題＋日期透過已設定 BYOK 端點查詢外部情報（嚴禁參與人與備註，預設開啟）", listOf("日曆情報", "上網", "外部搜尋", "byok", "search", "行程聯網搜尋", "查活動外部資訊"), page = "AssistantHubActivity"),
        SettingSearchItem("calendar_insight_interval_minutes", "hub", "日曆情報檢查頻率", "設定主動日曆情報定時檢查週期（30／60／120／180 分鐘，預設 60 分鐘）", listOf("日曆情報", "頻率", "週期", "interval", "行事曆檢查間隔", "多久查一次日曆"), page = "AssistantHubActivity"),
        SettingSearchItem("memory_auto_extract_enabled", "hub", "對話自動記憶擷取", "對話中出現個人資訊（人、事、時、地、偏好、習慣）時自動寫入非敏感無衝突記憶並於氣泡下顯示小字提醒（敏感與衝突仍進確認卡，預設開啟）", listOf("自動記憶", "擷取", "記憶", "memory", "extract", "免確認自動記", "對話小字記憶", "自動記住習慣"), page = "AssistantHubActivity"),
        SettingSearchItem("chat_pending_capsules_enabled", "hub", "未決事項膠囊列", "對話框頂部顯示至多 3 顆可滑除的未決事項快速膠囊與壓縮接續錨點（預設開啟）", listOf("膠囊", "未決事項", "錨點", "連續性", "capsules", "話題接續列", "上次你提到", "聊天頂部膠囊"), page = "AssistantHubActivity"),
        SettingSearchItem("chat_bubble_timestamp_enabled", "about", "聊天室訊息右下角時間戳", "控制 AI 對話與主動訊息氣泡右下角是否顯示 HH:mm 發送時間（預設開啟）", listOf("時間戳", "發送時間", "聊天時間", "timestamp", "氣泡時間", "對話幾點傳的", "右下角時間")),
        SettingSearchItem("diary_daily_enabled", "hub", "AI 日記／每日回顧", "每日 00:30 零 token 本機整理前一日時間軸，AI 註記展開快取且可「不準」降權（無 API key 亦可用）", listOf("日記", "每日回顧", "時間軸", "diary", "註記", "昨天做了什麼", "生活紀錄", "自動日記"), page = "AssistantHubActivity"),
        SettingSearchItem("goal_tracking_enabled", "hub", "長程目標追蹤", "偵測習慣／目標訊號必經確認卡建立、自動拆解週期子待辦並支援只降不升頻率調整（無 API key 亦可手動建立）", listOf("目標", "長程目標", "習慣", "打卡", "goal", "養成習慣", "週期計畫", "進度追蹤"), page = "AssistantHubActivity"),
        SettingSearchItem("health_briefing_enabled", "hub", "Health Connect 生理節奏整合", "晨間簡報附查表式睡眠／步數／心率規則建議（增強不依賴、原始數值不出本機）", listOf("健康", "睡眠", "步數", "心率", "health connect", "生理", "早報健康建議", "穿戴數據", "睡多久"), page = "PermissionHubActivity"),
        SettingSearchItem("health_soft_invite_enabled", "hub", "Health Connect 軟性邀請", "未授權 Health Connect 時於晨間簡報每週最多一次顯示軟性連接邀請", listOf("穿戴", "手錶", "health", "軟邀請", "智慧手錶提醒", "健康連接邀請", "手環提示"), page = "PermissionHubActivity"),
        SettingSearchItem("health_ai_query_enabled", "hub", "允許 AI 讀取健康摘要", "開啟時，聊天詢問睡眠／步數／心率僅注入近 7 日匯總數值，並啟用查表式主動健康觀察提醒（預設開啟）", listOf("健康摘要", "允許AI讀取健康", "睡眠", "步數", "心率", "health connect", "健康觀察", "運動", "卡路里", "問AI睡多久"), page = "PermissionHubActivity"),
        SettingSearchItem("decision_confirm_threshold", "hub", "雙門檻自動決策置信閾值", "調整 AI 自動判定高風險與降級確認卡的分數門檻（預設 60，範圍 40–85）", listOf("雙門檻", "決策", "閾值", "防詐門檻", "確認卡", "decision", "threshold", "幾分算詐騙", "風險分數線"), page = "AssistantHubActivity"),
        SettingSearchItem("ai_model_briefing", "ai", "簡報與畫像卡模型", "為早晚簡報與「我對你的瞭解」畫像卡指定獨立 AI 模型", listOf("簡報模型", "畫像卡模型", "briefing", "模型", "早晚報模型", "個人畫像模型"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("ai_model_proactive", "ai", "主動情報萃取與摘要模型", "為主動情報中樞（關注主題萃取與動態摘要）指定獨立 AI 模型與設定檔", listOf("主動情報", "情報模型", "proactive", "關注主題", "模型", "時事查詢模型", "話題萃取模型"), page = "ApiKeySettingsActivity"),
        SettingSearchItem("proactive_intel_enabled", "intel", "主動情報中樞（總開關）", "定期將關注主題關鍵字送往設定的 AI 供應商查詢最新動態並摘要推播（預設關閉）", listOf("主動情報", "情報中樞", "關注動態", "proactive", "自動查詢", "追蹤新聞", "主題推播總開關")),
        SettingSearchItem("proactive_extract_enabled", "intel", "每日自動萃取關注主題", "每日從近期非敏感素材自動萃取最多 5 個待確認關注主題（敏感記憶硬排除）", listOf("萃取", "關注主題", "自動萃取", "watch_topics", "推薦關注話題", "自動找興趣")),
        SettingSearchItem("proactive_intel_freq_hours", "intel", "主動情報查詢頻率", "設定背景主動情報週期（6／12／24 小時，預設 6 小時，僅充電電量足夠且連網執行）", listOf("查詢頻率", "主動情報頻率", "6小時", "12小時", "24小時", "多久查一次新聞", "情報更新間隔")),
        SettingSearchItem("proactive_watch_topics", "intel", "關注主題清單與立即查詢", "管理手動／AI 萃取的關注主題、靜音或刪除，並可手動立即查詢預覽摘要", listOf("關注清單", "立即查詢", "預覽", "主題管理", "不再關注", "追蹤話題列表", "手動查動態")),
        SettingSearchItem("linked_apps_center", "hub", "連結應用程式中心", "集中檢視日曆、通訊錄、通知、鬧鐘、電池優化狀態與自填金鑰雲端擴充（GitHub／Discord）", listOf("連結應用", "github", "discord", "webhook", "鬧鐘", "gmail", "地圖", "跨app", "linked", "郵件通知", "外部連動", "雲端擴充"), page = "LinkedAppsActivity"),
        SettingSearchItem("update_auto_check", "about", "自動檢查更新", "每次開啟 App 自動檢查更新；選擇「稍後更新」將於設定間隔後再次提醒。", listOf("檢查更新", "更新", "版本", "自動更新", "update", "新版本通知", "升級提醒", "下載更新")),
        SettingSearchItem("update_snooze_minutes", "about", "更新提醒延後間隔", "點擊「稍後更新」後暫停提醒的時間（30 分鐘／1 小時／4 小時，預設 1 小時）", listOf("稍後更新", "延後", "snooze", "更新間隔", "暫停更新提醒", "晚點更新")),
        SettingSearchItem("about_section", "about", "關於", "應用程式名稱、版本號、作者、GitHub 倉庫、第三方元件與隱私說明", listOf("版本", "作者", "關於", "about", "version", "檢查更新", "更新", "update", "下載", "下載卡住", "安裝更新", "apk下載", "更新卡"))
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
        "health_ai_query_enabled",
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
            "health_ai_query_enabled" -> PhantomPrefs.isHealthAiQueryEnabled(appCtx)
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
            "health_ai_query_enabled" -> PhantomPrefs.setHealthAiQueryEnabled(appCtx, enabled)
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

    private val STOP_WORDS: List<String> = listOf(
        "請問", "幫我", "可以", "如何", "怎麼", "在哪裡", "在哪", "哪裡",
        "設定頁", "設定", "頁面", "分頁", "功能", "調整", "修改", "更改", "變更", "切換",
        "控制", "管理", "選項", "相關", "有關", "想要", "一下", "的", "了", "嗎", "呢", "與", "和", "及"
    )

    /** #406 同義詞群組：任一詞命中時自動展開同組別名參與比對 */
    private val SYNONYM_GROUPS: List<Set<String>> = listOf(
        setOf("字體", "字級", "字大小", "文字大小", "字太大", "字太小", "縮放", "font"),
        setOf("訊息卡", "卡片", "堆疊", "滾輪", "卡片高度", "卡片太高", "排列", "密度", "間距", "92dp"),
        setOf("通知", "推播", "提醒", "警示", "通知欄", "狀態列"),
        setOf("常用", "最愛", "我的最愛", "釘選", "常用清單", "常用app"),
        setOf("晚上", "晚間", "夜間", "睡前", "晚報"),
        setOf("早上", "早間", "晨間", "早晨", "早報"),
        setOf("簡報", "早晚報", "個人簡報", "briefing"),
        setOf("太吵", "勿擾", "靜音", "半夜", "安靜", "節流"),
        setOf("健康", "睡眠", "睡多久", "步數", "走路", "心率", "心跳", "運動", "卡路里", "生理", "health"),
        setOf("下載", "更新", "升級", "新版本", "安裝", "apk", "下載卡住"),
        setOf("郵件", "信箱", "email", "gmail", "信件"),
        setOf("氣泡", "對話框", "聊天框", "圓角", "寬度", "bubble"),
        setOf("桌布", "背景", "底圖", "圖片", "封面", "wallpaper"),
        setOf("待辦", "提醒事項", "任務", "todo"),
        setOf("記事", "備忘錄", "筆記", "memo"),
        setOf("防詐", "詐騙", "可疑", "高風險", "165"),
        setOf("省電", "省錢", "省流", "省token", "額度", "token")
    )

    private data class IndexedCatalogItem(
        val item: SettingSearchItem,
        val titleLower: String,
        val aliasesLower: List<String>,
        val descLower: String,
        val fullHaystack: String
    )

    private val INDEXED_ITEMS: List<IndexedCatalogItem> by lazy {
        ITEMS.map { item ->
            val t = item.title.lowercase(java.util.Locale.ROOT)
            val a = item.aliases.map { it.lowercase(java.util.Locale.ROOT) }
            val d = item.description.lowercase(java.util.Locale.ROOT)
            val h = "$t ${a.joinToString(" ")} $d ${item.id.lowercase(java.util.Locale.ROOT)}"
            IndexedCatalogItem(item, t, a, d, h)
        }
    }

    /**
     * #406 將自然語言／多詞查詢拆解為有效語意 token：
     * - 保留原始空白切分 token（確保單一關鍵字既有行為 100% 不回退）
     * - 對較長中文複合句去除虛詞（的、頁面、設定、調整…）後提取核心詞與 2-gram 子詞
     */
    internal fun extractSearchTokens(rawQuery: String): List<String> {
        val cleaned = rawQuery.trim().lowercase(java.util.Locale.ROOT)
        if (cleaned.isEmpty()) return emptyList()

        val whitespaceTokens = cleaned.split(Regex("[\\s,，、/／|｜]+")).filter { it.isNotEmpty() }
        // 單一短詞（≤ 4 字且不含虛詞）直接回傳，確保單一關鍵字行為零回歸
        if (whitespaceTokens.size == 1 && cleaned.length <= 3 && STOP_WORDS.none { cleaned.contains(it) }) {
            return listOf(cleaned)
        }

        val tokens = LinkedHashSet<String>()
        // 1. 先依虛詞與標點切分核心片段
        var stripped = cleaned
        for (sw in STOP_WORDS) {
            stripped = stripped.replace(sw, " ")
        }
        val coreParts = stripped.split(Regex("[\\s,，、/／|｜？?！!。]+")).filter { it.isNotEmpty() }
        for (part in coreParts) {
            tokens.add(part)
            // 若片段仍偏長（例如「訊息卡高度」或「晚上簡報幾點」），補充已知領域核心詞與 2 字元滑動詞
            if (part.length >= 3) {
                for (group in SYNONYM_GROUPS) {
                    for (term in group) {
                        if (term.length >= 2 && part.contains(term)) {
                            tokens.add(term)
                        }
                    }
                }
            }
            if (part.length in 4..8 && part.all { it.code > 127 }) {
                for (i in 0..part.length - 2) {
                    val bi = part.substring(i, i + 2)
                    if (bi !in STOP_WORDS) {
                        tokens.add(bi)
                    }
                }
            }
        }

        if (tokens.isEmpty()) {
            tokens.addAll(whitespaceTokens)
        }
        return tokens.toList()
    }

    private fun expandSynonyms(token: String): Set<String> {
        val out = LinkedHashSet<String>()
        out.add(token)
        for (group in SYNONYM_GROUPS) {
            if (token in group || group.any { it.length >= 2 && (token.contains(it) || it.contains(token)) }) {
                out.addAll(group)
            }
        }
        return out
    }

    /**
     * #406 設定關鍵字搜尋（逐詞命中評分＋虛詞去除＋同義詞展開）：
     * - 完整句直接子字串命中給最高分（確保單一關鍵字 100% 不回歸）
     * - 分詞後每個 token 比對 title（權重高）、aliases（權重中高）、description（權重中），同義詞命中給次高權重
     * - 全量 136 條本機記憶體索引評分耗時 < 1ms
     */
    fun searchByKeywords(rawQuery: String): List<SettingSearchItem> {
        val cleaned = rawQuery.trim().lowercase(java.util.Locale.ROOT)
        if (cleaned.isEmpty()) return emptyList()

        val tokens = extractSearchTokens(cleaned)
        val expandedPerToken: List<Pair<String, Set<String>>> = tokens.map { tok ->
            tok to expandSynonyms(tok)
        }

        data class Scored(val item: SettingSearchItem, val score: Int, val matchedTokens: Int)
        val scoredList = mutableListOf<Scored>()

        for (idxItem in INDEXED_ITEMS) {
            var score = 0
            var matchedTokens = 0

            // 1. 完整原始查詢直接命中（單一關鍵字行為 100% 保持）
            if (idxItem.titleLower.contains(cleaned)) {
                score += 120
            } else if (idxItem.aliasesLower.any { it.contains(cleaned) }) {
                score += 100
            } else if (idxItem.fullHaystack.contains(cleaned)) {
                score += 70
            }

            // 2. 分詞逐 token 評分
            for ((rawTok, syns) in expandedPerToken) {
                var tokScore = 0
                if (idxItem.titleLower.contains(rawTok)) {
                    tokScore = maxOf(tokScore, 36)
                }
                if (idxItem.aliasesLower.any { it == rawTok }) {
                    tokScore = maxOf(tokScore, 34)
                } else if (idxItem.aliasesLower.any { it.contains(rawTok) }) {
                    tokScore = maxOf(tokScore, 26)
                }
                if (idxItem.descLower.contains(rawTok)) {
                    tokScore = maxOf(tokScore, 16)
                }

                // 若直接詞未命中，檢查同義詞表
                if (tokScore == 0) {
                    for (syn in syns) {
                        if (syn == rawTok) continue
                        if (idxItem.titleLower.contains(syn)) {
                            tokScore = maxOf(tokScore, 20)
                        } else if (idxItem.aliasesLower.any { it.contains(syn) }) {
                            tokScore = maxOf(tokScore, 16)
                        } else if (idxItem.descLower.contains(syn)) {
                            tokScore = maxOf(tokScore, 10)
                        }
                    }
                }

                if (tokScore > 0) {
                    matchedTokens++
                    score += tokScore
                }
            }

            if (matchedTokens >= 2) {
                score += matchedTokens * 12
            }

            if (score > 0) {
                scoredList.add(Scored(idxItem.item, score, matchedTokens))
            }
        }

        if (scoredList.isEmpty()) return emptyList()
        val maxScore = scoredList.maxOf { it.score }
        // 若已有高分精準命中（例如 ≥ 50 分），過濾掉僅靠單一泛用同義詞擦邊的低分雜訊
        val minThreshold = when {
            maxScore >= 100 -> 28
            maxScore >= 60 -> 22
            else -> 12
        }
        return scoredList
            .filter { it.score >= minThreshold }
            .sortedWith(compareByDescending<Scored> { it.score }.thenByDescending { it.matchedTokens })
            .map { it.item }
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
