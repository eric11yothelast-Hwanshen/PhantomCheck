# 更新日誌（CHANGELOG）

本專案遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/) 格式，並採用語意化版本控制。

## [2.0.0] - 2026-11-17

### 新增
- **AI 標籤記憶規則與優先序（#131）**：
  - `AppDatabase` 升版至 v10（非破壞性遷移 `MIGRATION_9_10`），新增 `user_tag_rules` 表與 `MessageTagCrossRef.SOURCE_USER_RULE = "user_rule"`；建立確定性零 token `TagRuleEngine`（支援 App package／label、寄件者、關鍵字、正則與語意類別 `otp`/`system`/`bill`/`marketing`/`delivery`）。
  - 標籤套用嚴格遵守 `manual > user_rule > ai_rules > auto > App 分類`，`processBatchAnalysis` 不覆寫手動與使用者規則標籤；支援透過 AI 對話 `tag_rule` 確認卡建立規則與查詢命中鏈，並於 `TagManagementActivity` 提供規則管理與測試。
- **AI 標籤失敗降級通知與分發測試（#132）**：
  - 自動標籤因網路或 HTTP 失敗時自動降回 `app_category_rules`，不中斷訊息入庫與風險流程；新增 `NotifIds.AI_TAG_FAILURE`，首次失敗靜默、連續失敗第 2 次起低優先通知（6 小時冷卻防重複）。
  - 設定頁 AI 卡新增「AI 標籤分發測試」按鈕（計入每日額度並顯示剩餘額度）與 `SettingsCatalog` 搜尋項目 `ai_tag_dispatch_test`。
- **AI 助手雙功能輸入選單：常用指令＋圖片視覺檢視（#133）**：
  - 移除頂部建議問題橫列，於輸入列左側新增單一 48dp `+` 收合選單（「常用指令」支援直接執行／預輸入雙模式，可於設定頁管理或由對話 `quick_command_add` 新增；「上傳圖片讓 AI 檢視」支援 `PickVisualMedia` 縮圖至長邊 1024px 並以 OpenAI 相容 `image_url` 格式送出，對不支援視覺的模型誠實阻擋與提示 token 消耗）。
- **AI 助手版面精簡（#134）**：
  - 切換至 AI 助手分頁時以 `AnimatedVisibility` 平滑隱藏頂部「幻訊 PhantomCheck」標題列，將搜尋／重新整理／設定入口整合至模型切換列旁；縮小對話輸入框（`maxLines = 3`）與精簡送出鈕尺寸。
- **底部分頁欄顯示模式自訂（#135）**：
  - 外觀設定卡與 `SettingsCatalog` 新增三選一模式：「標準」（預設）、「縮小」（80% 高度與圖示）與「自動隱藏」（向下捲動隱藏、向上捲動浮現，保留 `LiquidGlassTabBar` 既有跟手拖曳手勢）。
- **三款非統計桌面小工具（#136）**：
  - 新增 `AiSummaryWidgetProvider`（2x2 最新 AI 摘要）、`RiskActionWidgetProvider`（2x2 高風險一鍵標記詐騙／加入白名單，`goAsync` 執行）與 `ReminderWidgetProvider`（2x1 待辦提醒），均採標準 `RemoteViews`、`updatePeriodMillis="0"`、差異更新與 base + `xml-v31` `previewLayout` 配置。

### 變更與修正
- **AI 數值設定對話執行鏈與生命週期修復（#127）**：補齊 `ai_chat_rag_limit`、`ai_daily_limit`、`notif_throttle` 口語別名與 `resolveNumericIntentFallback` 用戶端確定性 fallback；修復 `appliedSettingCards` 合併追加生命週期。
- **裸 JSON 動作解析與白名單模糊定位（#128）**：強化 `whitelist` `messageRef` 分詞模糊搜尋 fallback。
- **Groq 免費方案 TPM 預算控制與 413/429 自動降級（#129）**：新增 token 估算、Provider 預算上限（Groq 6500 / 其他 32000）、Groq 預設 `maxTokens=1800`、多階段 413 降級（RAG → history → maxTokens）、429 `Retry-After` 退避重試，以及分析失敗退還每日額度。
- **System Prompt 節流與對話歷史壓縮（#130）**：設定清單僅於設定相關查詢條件載入；RAG 依同寄件者相似前綴去重 `(×N)` 與動態字數上限；超過 10 輪歷史壓縮為 `[較早對話摘要]` + 最近 8 則。
- **效能與 M3 版面一致化（#137, #138）**：`NotificationCollectorService` `aiRuleDao().active()` 導入 30 秒 TTL 快取、`AppIconHelper` 通知圖示條改走 `LruCache`；全專案對齊 M3 8dp 網格、12dp 卡片間距與 48dp 最小觸控目標。
- **版號更新**：`versionCode` 17、`versionName` `"2.0.0"`。

## [1.13.1] - 2026-11-17

### 變更與修正
- **HTTP 413 Payload 過大防護與自動重試（#122）**：
  - `AiClient` 各 `HttpException` 分支改為依狀態碼誠實回報（`413` 專屬提示並附上 Provider 原始錯誤訊息，`401/403`、`404`、`422`、`429`、`5xx` 各別明確說明，不再於非認證錯誤誤導「請確認 API Key 或模型設定」）。
  - `chatAssistant` 遇到 `HTTP 413` 時自動將單次閱讀上限減半（最低 `25` 則）重試一次並誠實標注已縮減閱讀範圍；`MessageDetailActivity` 深度分析若遇 `413` 失敗則透過 `PhantomPrefs.refundTodayAiUsage()` 退還當日額度。
- **AI 對話多動作解析與誠實錯誤回報（#123）**：
  - `ChatScreen` 五類動作解析器改為支援多動作與大括號平衡拆解（`splitBalancedJsonObjects`），允許單次回覆同時包含多個獨立動作指令或多個物件；多筆 `whitelist` 提案合併為單張可勾選確認卡逐列顯示並回報每筆結果。
  - 無效動作（`messageId` 為空、`messageRef` 查無、JSON 損壞或不支援動作）一律插入誠實錯誤訊息，不再靜默丟棄；`cleanReply` 為空且無有效提案時不再 fallback 顯示原始 raw JSON 回覆。
- **對話數值設定滑桿確認卡（#124）**：
  - `SettingsCatalog` 新增 `NumericSettingSpec` 與 `NUMERIC_SPECS`（`ai_chat_rag_limit` 50–800 步進 10、`ai_daily_limit` 0–100 步進 10、`notif_throttle` 0–300 步進 10），並將設定頁 `ai_chat_rag_limit` 滑桿對齊步進 10。
  - `ChatScreen` 新增 `setting_adjust` 動作與滑桿確認卡（支援 Slider 與 `+/-` 微調、超範圍自動夾緊提醒、套用後顯示前→後狀態卡），system prompt 同步附上【可調整數值設定清單】。
- **設定導覽正確性與全量目錄防編造（#125）**：
  - 新增 `{"action":"open_setting","settingId":"..."}` 直接開啟 `SettingsActivity` 並定位至目標設定卡；system prompt 附上【全量設定清單摘要】並禁止編造不存在的選單路徑。
  - 未知 `settingId` fallback 改為以 `SettingsCatalog.keywordSearch` 對全目錄關鍵字比對前 5 筆，無匹配則誠實回報；`setting_candidates` 卡對非開關項目顯示「前往」按鈕而非「開／關」；統一 `SettingsCatalog`「AI 風險分析」措辭。
- **版號更新**：`versionCode` 16、`versionName` `"1.13.1"`。

## [1.13.0] - 2026-11-17

### 新增與強化
- **PhantomMarkdownText 語法補全（#114）**：
  - 新增支援 GFM 表格（含對齊行 `:-` / `:-:` / `-:` 解析、欄位依內容比例寬度與寬表橫向捲動）、斜體（`*text*` / `_text_`）、刪除線（`~~text~~`）、可點擊超連結（`[text](url)` 含 `LinkAnnotation.Url` 安全開啟）、引用區塊（`> quote`）、水平分隔線（`---`）、任務清單（`- [ ]` / `- [x]`）與一層巢狀清單縮排。
  - `stripMarkdown()` 同步剝除上述新語法（表格轉為 `cell / cell`），`buildRagSystemPrompt` 同步鼓勵表格與豐富格式排版。
- **AI 預設模型清單更新（#115）**：
  - 更新 `AiClient.DEFAULT_PROVIDER_PRESETS`（既有使用者已儲存之 profile 不受影響，僅影響新建設定）：
    - **Mistral**：`mistral-small-2603`（預設）、`mistral-large-4`（原 `mistral-small-latest` / `mistral-large-latest`）
    - **OpenAI**：`gpt-6.1-terra`（預設）、`gpt-6.1-sol`（原 `gpt-5.2`）
    - **Groq**：`openai/gpt-oss-120b`（預設）、`openai/gpt-oss-20b`、`qwen/qwen3.8-27b`（原 `qwen/qwen3.6-27b` 等）
    - **Gemini**：`gemini-4-hydrogen`（預設）、`gemini-4-carbon`、`gemini-4-argon`、`gemini-3.8-flash`（原 `gemini-3.8-flash` / `gemini-3.5-flash-lite`）
    - **DeepSeek / OpenRouter**：維持不變。
- **AI 助手單次閱讀訊息數設定（#116）**：
  - `PhantomPrefs` 新增 `chatRagMessageLimit`（預設 `400`，範圍 `50–800`），省 token 模式啟用時透過 `getEffectiveChatRagMessageLimit()` 自動降為 `100`（不覆寫使用者原設定值）。
  - 設定頁 AI 卡新增滑桿（步進 50）與 token 消耗提醒，並加入設定搜尋 `ai_chat_rag_limit`。
- **結構化白名單機制（#117）**：
  - `AppDatabase` 升版至 v9（非破壞性遷移 `MIGRATION_8_9` 註冊於 `AppDatabaseHolder`），`AiRuleEntity` 新增 nullable `sender` 與 `packageName`。
  - `ScamCenterActivity` 與 `MessageDetailActivity` 新增「加入白名單」雙選項流程（此寄件者全部訊息／僅此則訊息）；`TagManagementActivity` 顯示白名單寄件者／App 中繼資料。
  - AI 助手支援 `{"action":"whitelist",...}`，先渲染訊息確認卡、使用者確認後才寫入資料庫；命中白名單後自動標記安全（`riskScore = 10`、`riskLabel = "safe"`）並跳過後續 AI 分析、詐騙警示與防詐情報預查。
- **AI 對話直接調整設定（#118）**：
  - 新增共用單一來源 `SettingsCatalog`（`SettingsActivity` 搜尋與 `ChatScreen` 對話調整共用）。
  - AI 助手支援 `setting_toggle`（僅限 boolean 開關白名單，套用與設定頁完全相同之 setter 與前景服務/排程副作用，並顯示「變更前 → 變更後」與「前往設定頁」卡片）與 `setting_candidates`（模糊描述列候選開關卡；非 boolean 設定引導至設定頁）。
- **AI 訊息查找強化與對話執行操作（#119）**：
  - RAG 時間範圍預設改為「今天＋昨天」，新增「前天」與依寄件者名稱過濾（`MessageDao.recentSenders()`），節錄附帶 `[id=...]`。
  - 新增 `reply_message`（透過 `NotificationCollectorService` `activeNotifications` 匹配並以 `RemoteInput` 快速回覆，找不到時誠實回報）、`open_app`、`delete_message`（強制保護 `scam` 詐騙紀錄不予刪除）與 `tag_message`（`SOURCE_MANUAL`），全部先渲染確認卡、確認後執行並於對話回報結果。
- **設定搜尋全面稽核（#120）**：
  - 逐卡核對 `SettingsActivity` 實際 UI 與 `SettingsCatalog.ITEMS` / `TOGGLEABLE_IDS`，修正 `ai_daily_summary` 歸屬至 `quick` 卡、補齊 `ai_daily_summary_notif`（每日摘要排程時間與完成推播）與 `appearance_glass_effect`（液態玻璃模糊效果）搜尋與 boolean 切換支援，並驗證本地關鍵字與 AI 語意搜尋雙路徑。
- **版號更新**：`versionCode` 15、`versionName` `"1.13.0"`。

## [1.12.1] - 2026-11-16

### 變更與修正
- **修正設定搜尋 catalog 五項對錯卡（#109，PR #110）**：
  - 將 `notif_auto_reply` 改為 `ai_auto_reply`（`cardKey = "ai"`、title「SMS 自動回覆設定」，補齊 `sms`／`簡訊`／`回覆`／`時段`／`reply` 搜尋別名），修正「SMS 自動回覆」定位至通知卡的回報主因。
  - 將 `ai_auto_tag` 改為 `cardKey = "quick"`，對應快速設定卡「每則訊息 AI 智慧掃描」開關。
  - 將 `ai_rules` 改為 `cardKey = "data"`、title「自訂標籤管理」。
  - 將 `notif_scam_alert` 拆分為 `quick_scam_popup`（`cardKey = "quick"`，「詐騙警示彈出通知」）與 `ai_alert_keywords`（`cardKey = "ai"`，「即時提醒關鍵字」）。
  - 將 `appearance_widget_tour` 改為 `cardKey = "data"`、title「教學重播與桌面小工具」。
- **新增兩項設定搜尋定位（#109，PR #110）**：
  - 新增 `ai_scam_center`（`cardKey = "ai"`，「詐騙警示中心」）與 `notif_filter_rules`（`cardKey = "notif"`，「過濾關鍵字規則與命中動作」）。
- **AI 定位支援「前往」直達頁面（#109，PR #110）**：
  - `SettingSearchItem` 新增 nullable `page` 欄位，設定 `AutoReplySettingsActivity`（自動回覆設定）、`ScamCenterActivity`（詐騙警示中心）、`TagManagementActivity`（自訂標籤管理）與 `ApiKeySettingsActivity`（API Key 設定，含 `ai_providers` 補齊）。
  - `aiSearchNote` 定位橫幅在第一個命中項目具備 `page` 時顯示「前往」`TextButton`，點擊直接開啟對應設定頁。
- **資料管理卡補齊桌面小工具加入說明（#109，PR #110）**：
  - 於資料管理卡「重新播放設定教學」按鈕下方補上「桌面小工具：桌面長按 → 小工具 → 幻訊統計」提示小字。
- **版號更新**：`versionCode` 14、`versionName` `"1.12.1"`。

## [1.12.0] - 2026-11-16

### 變更與修正
- **設定搜尋「同 key 節流」定位修正（#99）**：
  - 將 `SettingsActivity` `SETTINGS_SEARCH_CATALOG` 中的 `data_throttle_retention` 拆分為 `notif_throttle`（`cardKey = "notif"`，補齊「同 key」「重複通知」「漏訊」「流量」「數字微變」「覆寫」等搜尋別名）與 `data_retention`（`cardKey = "data"`，「訊息保留與清理」）。
  - 同步更新設定頁「個別 App 節流覆寫」副標說明，清楚標示 `0 秒 = 永不節流` 與通訊類通知 `postTime` 更新時收錄同內容訊息的行為。
- **保留清理維護詐騙證據與標籤完整性（#100，免 Room migration）**：
  - `MessageDao.prune` 與 `deleteOlderThanCount` 改以 `AND riskLabel != 'scam'` 直接排除詐騙證據，移除 `RetentionWorker` 重插邏輯以保留原 `id`、`message_tags` 與 `intel_results` 關聯。
  - 新增 `TagDao.deleteOrphanCrossRefs()` 並於 `RetentionWorker` transaction 結尾清理孤兒關聯；`TagDao.allTagCounts()` 補齊 `LEFT JOIN messages` 防護對齊 `todayAiTagCounts()`。
- **通訊類同內容連續訊息不再吞訊（#101，維持被動 listener）**：
  - `NotificationCollectorService` 偵測通訊類通知（`CATEGORY_MESSAGE` / `EXTRA_MESSAGES` / `MessagingStyle`），僅於同 key + 同內容 + `postTime` 未變時視為系統 re-post 丟棄；`postTime` 有更新的同內容訊息即時收錄。非通訊類通知（如系統／流量監控）維持 `exactSame || digitMicroChange` 節流。
  - `dedupCache` 改為僅於 `isSuspectedReplay`（斷線重播補投）時阻擋，一般路徑交由 throttle 層判斷；個別 App 覆寫設為 0 秒時兩層皆放行。
- **通知 ID 集中管理與防撞號（#102）**：
  - 新增 `NotifIds` object 集中管理互斥區段：`1001..1004`（常駐統計／開機提醒／每日摘要／週報）、`scamAlert` `2000..21999`、`importantAlert` `22000..41999`、`reminder` `100000..109999`，消除 `ReminderReceiver` 與 `alertScam` 區間重疊與裸數字常數。
- **AI Prompt Injection 防護（#103）**：
  - 新增 `AiClient.sanitizeForPrompt()`，將 `</message_*`、`</user_*`、`</system`、`</msg` 等隔離標籤的 `<` 轉為全形 `＜`（不影響 `cacheKey` 原始內容計算），並套用於 `analyzeMessage`、`analyzeMessageBatch`、`deepAnalyzeMessage` 與 `ChatScreen.buildRagSystemPrompt`（`chatAssistant` RAG）四個注入點。
- **SMS 自動回覆防回圈冷卻（#104）**：
  - `SmsAutoReplyReceiver` 新增 `canReplyAndRecord()`（per-sender 4 小時冷卻與單日上限 3 次，記錄於 `auto_reply_cooldown_prefs`）及回覆範本自回比對，避免雙向自動回覆無限互發。
- **技術債清理（#105）**：
  - **過期提醒開機即觸發**：新增 `MessageDao.allScheduledReminders()` 與 `ReminderScheduler.deliverReminder()`，`BootReceiver` 對開機期間已過期提醒立即觸發並清除 `remindAt`；`MessageDetailActivity` 改以 `remindAt > System.currentTimeMillis()` 顯示狀態。
  - **排程自癒策略**：`HeartbeatPolicy` 與 `WeeklySummaryPolicy` 的 `ExistingPeriodicWorkPolicy.KEEP` 改為 `UPDATE`。
  - **節流查詢快取**：`PhantomPrefs.getAppSameKeyThrottleMap` 加入 30 秒 TTL 快取（於 `setAppSameKeyThrottle` 失效），減少通知熱路徑重複 Gson 反序列化。
  - **統計 SQL 聚合**：新增 `MessageDao.countByDaySince` 與 `countByHourSince`，取代 `StatsDetailWidgetProvider.loadSevenDayCounts` 與 `MainActivity` 統計頁的 `dao.since()` 全欄位載入。
  - **Widget 圖示共用快取**：新增 `AppIconHelper.loadAppIconAndroidBitmap` 共用 `appIconBitmapCache`（`LruCache`），取代 `StatsDetailWidgetProvider` 重複 decode。
  - **分層解耦**：移除 `PhantomPrefs` 對 `service.NotificationCollectorService` 的反向依賴，改為 `onRuleCachesInvalidated` callback 註冊。

## [1.11.0] - 2026-11-16

### 新增
- 同 Key 節流進階設定（#96）：於設定頁新增「數字微變視為同一則更新」開關（預設開啟）與「依 App 自訂節流秒數」（per-app 節流覆寫與選擇對話框），在全域滑桿外支援個別 App 獨立調整節流行為。
- 連線狀態追蹤與規則快取失效機制（#94）：`NotificationCollectorService` 新增 `isConnected` 狀態追蹤與 `invalidateRuleCaches()`，於設定頁或標籤管理頁修改規則與關鍵字時即時刷新快取。

### 變更與修正
- 連續訊息漏收修復（#96）：
  - `NotificationCollectorService` 改用有界 `sameKeyHistory`（`LruCache(256)`）實作內容感知節流與疊加訊息尾巴提取，在不啟用任何輪詢的前提下，確保同一通知 key 的連續新訊息不被誤攔。
  - 新增 `MessageDao.existsByDedupKeyInWindow` 進行 DB 視窗查證式防重複收錄（`isSuspectedReplay`），避免持續刷新型 App 反覆收錄或重連補收漏訊。
  - 實作 `resolveNotificationTitle` 多層 fallback（`EXTRA_TITLE` → `EXTRA_TITLE_BIG` → `「寄件者：」` 前綴 → 快取 App 名稱），修正空標題通知處理異常。
- AI 訊息助手回應截斷修復（#95）：
  - `AiClient.chatAssistant()` 的 `maxTokens` 調升至 `4096`，並改用專用 `chatOkHttpClient`（`connect 15s / write 30s / read 120s / callTimeout 180s`）；其餘 AI 路徑的 `maxTokens` 與逾時維持不變。
  - `ChatChoice` 解析 `finish_reason`，當回應因長度上限截斷（`finish_reason == "length"`）時於結尾誠實標注「⚠ 本回應因長度上限被截斷」。
- 效能、電量與記憶體最佳化（#94）：
  - `ShizukuWatchdogService` 改採自適應退避（平穩期 300 秒、拉起服務後 10 分鐘內 60 秒），減少閒置喚醒而不影響保活。
  - `WatchdogReceiver` 與 `HeartbeatWorker` 條件化 `requestRebind`（僅於 `!isConnected` 時觸發），並移除 `HeartbeatPolicy.policyScope` 改用 `ExistingPeriodicWorkPolicy.KEEP`。
  - `NotificationCollectorService` 熱路徑導入 `appLabelCache`（`LruCache(64)`）與 30 秒 TTL 過濾規則／App 分類規則／警示關鍵字快取，降低重複資料庫與系統查詢開銷。

## [1.10.0] - 2026-11-16

### 新增
- 訊息標籤全面顯示（#83）：抽離共用 `MessageTagChipRow`（含 `· AI` 標記），統一訊息清單、對話氣泡、App 對話串與詳情頁顯示；標籤採單次 `IN` 批次查詢載入，避免 N+1 拖慢捲動。
- 設定頁搜尋框＋AI 語意搜尋（#88）：支援本地即時關鍵字／別名過濾，以及自然語言 AI 語意導覽（`<user_query>` 隔離與白名單 id 驗證，自動展開捲動並高亮目標卡片，僅導覽不改值；未設定 AI provider 時誠實降級）。
- 個人化設定（#90）：新增卡片密度（舒適／緊湊）、首頁排序（最新／未讀優先／風險優先）、首頁篩選欄自訂顯示、摘要預設收合開關、12/24 小時制切換（全部納入設定頁搜尋）。
- 多尺寸桌面小工具（#87）：新增 2x1 迷你（今日／警示）與 4x2 詳情（今日／未讀／警示＋原生 `RemoteViews` `LinearLayout`/`ImageView` 7 日趨勢比例條＋最近來源 App 圖示），連同既有 4x1 皆支援 `resizeMode`、`xml-v31` `targetCell` 與深淺主題，統一走差異刷新。
- OTP 驗證碼一鍵複製（#91）：強訊號關鍵字＋4–8 位數字偵測，於訊息卡、對話氣泡與詳情頁顯示「複製驗證碼」一鍵複製。
- 統計報告分享圖卡（#91）：純本機 Android `Canvas` 產生 `1080×1350` 靜態週報圖片並經 `FileProvider` 發起 `ACTION_SEND` 分享（不經 LLM、不上傳訊息）。
- 訊息待辦提醒（#91）：支援底頁與詳情頁選擇提醒時間（30 分鐘後／1 小時後／今晚 20:00／明早 09:00），以 `AlarmManager.setAndAllowWhileIdle` 排程通知直開詳情，並於 `BootReceiver` 重開機自動重建。

### 變更與修正
- 每日／本週 AI 摘要卡預設收合（#84）：統計頁摘要卡預設收合為單行預覽並持久化展開狀態，保留生成、複製與分享入口。
- 首頁篩選欄重排（#85）：改用 `FlowRow` 自動換行與緊湊晶片設計，全部篩選與自訂標籤免橫向滑動一次可見。
- 頂部標題列縮小（#86）：各頁面 `TopAppBar` 高度收斂為 52dp、標題改為 `titleMedium`，維持 48dp 按鈕觸控目標。
- 全域排版優化（#89）：依 Material 3 規範統一各頁 16dp 水平邊距與 12dp 卡片間距，詐騙中心改用共用 `EmptyStateView`。
- 資料庫升級：Room 7→8 非破壞性 `MIGRATION_7_8`（新增 `messages.remindAt` 欄位），並補齊 `todayRecentApps` 的 `riskScore` 欄位映射。

## [1.9.0] - 2026-11-16

### 新增
- 桌面小工具（4x1）：今日訊息數／未讀／詐騙警示，點擊開啟幻訊，跟隨系統深淺色，隨常駐通知差異刷新更新（不增高頻鬧鐘）。
- 每週 AI 摘要報告：每週日 22:00 自動生成當週統計與風險趨勢，統計頁可手動生成、查看歷史週報、複製與分享（不受省 token 模式限制）。
- 訊息一鍵 AI 深度分析：訊息詳情頁產生段落式風險解說（詐騙類型、可疑點、建議處理動作），可取消，計入每日 AI 額度。
- 防詐情報金鑰管理：Safe Browsing／PhishTank／Chainabuse 的 key 可直接在設定頁輸入（加密儲存，`local.properties` 為 fallback）。

### 變更與修正
- AI 助手對話逾時修復：Retrofit 改用自訂 OkHttpClient（read 90s／callTimeout 120s），對話逾時自動重試一次並誠實標注。
- 防詐情報四來源連線修復：fraud.tw 改用官方端點 `/api/check?d=`（原先 404）；PhishTank 描述性 UA＋可選 `app_key`（原先 403）；Chainabuse Basic 認證＋`chain` 參數（原先 401）。
- 常駐統計通知版面：移除左側重複彩色圖示，保留單色小圖示，標題不再被卡；右側圖示條排序不變。
- 省電優化：常駐通知差異刷新（數據未變跳過重建）、統計查詢合併為單一 SQL、熄屏時跳過閒置刷新（新通知仍即時更新）、圖示快取。

## [1.8.0] - 2026-11-16

### 新增
- **公開防詐情報庫（`IntelClient`）零 token 預查（#65）**：
  - 抽取層 `IndicatorExtractor` 支援完整 URL（含短網址）與加密貨幣地址（BTC / ETH / TRON）正則抽取，並維持省 token 模式 `hasSuspiciousFeature` 既有行為。
  - 可插拔情報提供者依序查詢：`fraud.tw`（165 通報）→ Google Safe Browsing Lookup API v4（`local.properties` 注入 `BuildConfig.SAFE_BROWSING_API_KEY`，倉庫零機密）→ 可選 PhishTank / Chainabuse（預設關閉）。
  - 本地 72 小時情報快取（`intel_cache`、`sha256(indicators + providerSig)`）與每則訊息查證結果（`intel_results`，Room Migration 6→7 非破壞性升級）。
  - 管線整合：`NotificationCollectorService` 於 `shouldRunAi` 前預查；`ai_rules` 白名單優先跳過；命中直接寫入高風險 `scam`、跳過 LLM 與每日額度並發送警示；未命中或離線靜默降級回原 AI 流程（不造假命中、僅送指標不送全文）。
  - 設定頁新增「防詐情報」卡片（逐源開關、Safe Browsing Key 注入狀態、逐源真實連線測試、清除 72h 快取）；訊息詳情與詐騙中心顯示命中來源與查詢時間。
- **AI 對話／每日摘要即時切換模型與自然語言新增規則（#71）**：
  - 對話頁與統計頁「每日 AI 摘要」卡新增 `AiProviderSwitcherChip`，可即時切換已設定的 AI Provider／模型（未設定時誠實顯示尚未設定）。
  - 對話中明確要求新增白名單或標籤規則時，AI 輸出結構化 `add_rule` 區塊，前端隱藏原始 JSON 並彈出強制確認對話框，經使用者確認後才寫入 `ai_rules`；`buildRagSystemPrompt` 加上 `<message_content>` 分隔與防 Prompt Injection 指示。
- **發佈流程文件化（#70）**：建立 `CHANGELOG.md`、同步 `README.md`，並於 `AGENTS.md` 新增發佈檢查清單。

### 變更與修正
- **震動回饋與液態玻璃門檻修正（#68）**：
  - `rememberPhantomHaptic` 於 API 30+ 使用 `areAllEffectsSupported` 探測預定義效果，不支援或 API 29 時 fallback 至明確振幅 `createWaveform`（點擊 25ms/220、長按 40ms/255），解決部分機型靜音問題。
  - `Glass.kt` 背景模糊門檻從 API 33 修正為 API 31（Android 12+），更低版本維持半透明壓色降級；設定頁補齊廠牌觸感設定路徑提示與液態玻璃版本說明。
- **常駐統計通知版面與點擊行為修正（#69）**：
  - 改用 `RemoteViews`（`notif_stats_collapsed.xml`）固定左側幻訊圖示、右側「左新右舊」水平 App 圖示條並以邊框與角點標示最新 App；保留滑掉重生與 `FLAG_NO_CLEAR` 保活機制。
  - 點擊行為設為「開啟原 App」時改為開啟最新一則訊息所屬 App（無法解析時 fallback 開啟幻訊）。
- **效能優化、省電降級與教學主題適配（#67）**：
  - `MainActivity` 保留分頁狀態與 30 秒 TTL 資料快取；`ConversationScreen` 改用 keyset 分頁（`LIMIT 200` 滾動載入）；`AppIconHelper` 新增跨頁 `LruCache`；`LazyColumn` 補齊 `key` 與 `derivedStateOf`。
  - `PowerManager.isPowerSaveMode` 啟用時自動停用玻璃模糊；移除 `StatsForegroundService` 的 `CONNECTIVITY_CHANGE` 動態廣播註冊以降低耗電。
  - `Onboarding.kt` 與 `SettingsTourOverlay` 改用 `MaterialTheme.colorScheme` 並強化卡片底色與遮罩對比，維持洞內觸控穿透。

## [1.7.0] - 2026-11-15

### 新增與修正
- **Shizuku 偵測修復與開關健檢（#64）**：修正 Shizuku binder/授權狀態偵測、新增設定頁 6 步高亮導覽教學、每日摘要複製與分享、氣泡長按複製、AI 回覆 Markdown 渲染（`PhantomMarkdownText`）與助手上下文放寬，以及震動回饋基礎修正。

## [1.6.0] - 2026-11-15

### 新增與修正
- **保活強化與 Shizuku 整合（#60）**：整合 Shizuku 進階保活與看門狗（`ShizukuKeepAlive` / `ShizukuWatchdogService`）、凍結高風險 App 能力、修正通知圖示渲染，並於介面顯示 AI 標籤。

## [1.5.0] - 2026-11-14

### 新增與修正
- **AI 助手、10 步教學與規則系統（#55）**：新增「AI 助手」RAG 唯讀分頁、10 步穿透式新手引導、常駐通知 400ms 節流原地重建（`repostDirectly`）、零 token App 分類規則與 `ai_rules` 白名單、省 token 模式與多項外觀/通知微調開關，Room Migration 5→6。

## [1.4.0] - 2026-11-13

### 新增與修正
- **互動強化、AI 智慧標記與玻璃手感（#52）**：移植 `LiquidGlassTabBar`、`GlassCard`、`FluidSwitch`、`SwipeToRevealRow`；新增勿擾時段、摘要完成推播、可疑連結警告、詐騙週趨勢與搜尋高亮、批次多選與 VIP 置頂，Room Migration 4→5。

## [1.3.0] - 2026-11-12

### 新增與修正
- **全面強化與前景統計服務（#50）**：新增 `StatsForegroundService` 常駐統計通知、訊息詳情頁、標籤系統、CSV 匯出與設定頁重構，Room Migration 3→4。

## [1.2.0] - 2026-11-11

### 新增與修正
- **訊息中心改版（#48）**：依 App 與寄件者分組對話、聊天式對話頁與群組摘要通知過濾修正。

## [1.1.0] - 2026-11-10

### 新增與修正
- 正式簽署版本與基礎 CI/CD 建置發佈管線。

## [1.0.0] - 2026-11-09

### 新增
- 幻訊 PhantomCheck 初始版本：`NotificationListenerService` 通知攔截預覽、Mistral AI 詐騙風險分析、關鍵字過濾與 Room 本地資料庫。
