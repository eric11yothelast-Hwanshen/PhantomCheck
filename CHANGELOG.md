# 更新日誌（CHANGELOG）

本專案遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/) 格式，並採用語意化版本控制。

## [4.2.0] - 2026-11-23

- **Tag 對照**：[`v4.1.0...v4.2.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.1.0...v4.2.0)（`versionCode = 50`、`versionName = "4.2.0"`）
- **發佈資產（雙 APK）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。

### 行為變更（Breaking / Behavior Changes）
- **預設介面與 AI 輸出語言改為英文（非繁中裝置）**：
  - 本版本僅提供雙語資源桶：預設資源桶 `res/values/strings.xml`（English，fallback = `en`）與繁體中文資源桶 `res/values-zh-rTW/strings.xml`。
  - **裝置系統語言非繁體中文（`zh-TW`）時，App 介面（UI）與 AI 自然語言輸出（風險分析、自動備忘錄、每週摘要、深度分析、早晚簡報、回顧註記、主動情報等）一律改為英文（English）**；其他未單獨建桶之語言亦 fallback 至英文。
  - **繁體中文（`zh-TW`）裝置使用者介面顯示逐 byte 不變**，AI 輸出維持繁體中文與既有 40 字備忘錄密度。

### 新增、變更與修正
- **#429 i18n 第一輪：全庫 UI 中文字串抽離與 XML 全 `@string` 化（PR #432）**：
  - 將全庫使用者可見 UI 字串（Compose 介面、Activity／Dialog／Toast／Snackbar、Onboarding 教學、桌面小工具、Shizuku 狀態、Service 通知渠道與推播文案、`SettingsCatalog` 標題／描述／別名、`PhantomPrefs` 顯示枚舉）全數抽離至 `res/values/strings.xml`（共 2,898 條資源 key）。
  - `AndroidManifest.xml`（9 處 `android:label`）與 `res/layout/*.xml`（常駐統計通知與 7 個桌面小工具版面）全面改為 `@string/*` 資源引用；`AGENTS.md` 新增第 6 條「UI 字串抽離紀律」（純工程重構，零行為變更）。
- **#430 i18n 第二輪：預設介面改英文（`values/` 為英文、`values-zh-rTW/` 為繁中）（PR #433，行為變更）**：
  - 原繁體中文 `strings.xml`（2,898 條）原封不動遷入 `app/src/main/res/values-zh-rTW/strings.xml`（與遷移前逐 byte 一致，`zh-TW` 使用者介面零變動）。
  - 預設資源桶 `app/src/main/res/values/strings.xml` 全面翻譯為精簡 Android UI 英文（fallback = `en`），統一核心術語（`PhantomCheck`、`AI Assistant`、`Memo`、`To-do`、`Whitelist`、`Scam Protection`、`Memory Bank`），並確保所有插值參數（`%1$s`、`%2$d` 等）與 `formatted="false"` 屬性 100% 對齊。
- **#431 i18n 第三輪：功能層 AI 輸出語言跟隨與詐騙詞庫多語系支援（PR #434，行為變更）**：
  - **AI Prompt 輸出語言自動跟隨系統語系**：`AiClient`（訊息風險分析、自動備忘錄 `memo`、每週摘要 `summarizeWeekly`、深度分析 `deepAnalyzeMessage`、簡報潤飾 `generateBriefingText`、RAG 情境摘要 `summarizeRagContext`）、`DiaryPolicy`（回顧註記）、`ProactiveEngine`、`ProactiveIntelWorker`、`CalendarInsightWorker`、`TaskTemplateEngine`、`MemoryEngine` 與 `ChatScreenParts` 全面依系統 Locale 自動切換繁中／英文輸出指令；JSON 結構鍵與枚舉值（`risk_label`、`memo_type` 等）維持不變，英文備忘錄採等量密度上限（60 words／360 chars）並支援英文句尾標點續寫判定。
  - **詐騙規則層新增英文話術偵測**：`IndicatorExtractor`、`MemoryEngine` 與 `ContactContext` 依據 2025–2026 FTC、FBI IC3、FCC、UK Report Fraud、APWG 與 FinCEN 公開通報意譯擴充英文詐騙特徵詞庫（涵蓋 unpaid toll 未繳過路費、package redelivery 假包裹補費、bank ATO／fraud department／safe account 假銀行警示、Hi Mum 換號急用冒充、crypto platform／guaranteed return／seed phrase 殺豬盤與驗證碼索取），無 AI 或省 token 模式下規則層同樣具備中英文雙語攔截能力。

## [4.1.0] - 2026-11-23

- **Tag 對照**：[`v4.0.0...v4.1.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.0.0...v4.1.0)（`versionCode = 49`、`versionName = "4.1.0"`）
- **發佈資產（雙 APK）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。

### 新增、變更與修正
- **#416 訊息頁統計卡重新排版（PR #426）**：單行三段式（今日／未讀／詐騙）＋ 1dp 垂直分隔線，「全部標已讀」移至同行尾端，unread 99→0 高度零跳動。
- **#417 強制深色修正（PR #426）**：視窗層 theme 改 `android:Theme.Material` 並設定 `android:windowBackground`／`android:colorBackground` 為深色（phantom_window_bg #FF0E0A18），消除 Activity 轉場白底閃現；Compose 層維持高對比。
- **#418 記憶頁統計卡與次級分頁分離（PR #426）**：記憶頁「長期／短期／衝突／免疫」統計卡降低視覺重量（alpha 0.35），與「記憶｜規則｜防詐免疫」次級分頁以 10dp 間距明確分離。
- **#419 AI 健康查詢零幻覺（PR #426）**：Health Connect 擴充距離（DistanceRecord）與消耗熱量（TotalCaloriesBurnedRecord）讀取，改為 per-type 權限查詢；未授權型別顯示「未授權」、無資料顯示「無紀錄／無資料」，AI 拒答幻覺正反例與 health_query 動作落地。
- **#420 統計頁健康明細卡（PR #426）**：日／週／月切換（當日健康快照、近 7 日彙總＋可滑動每日明細表、近 30 日彙總與趨勢），本機聚合零 token。
- **#421 AI 回顧註記修復（PR #426）**：新增 AI 用途 DIARY_ANNOTATION（ai_model_diary_annotation 可綁定獨立模型，未綁定沿用預設 profile）；不可用時三態診斷（省 token 模式／未設定供應商／同類註記被停權）各附捷徑按鈕；新增「省 token 模式下仍生成回顧註記」開關（diary_annotation_in_saver，預設關閉）。
- **#422 主動聊天室與一般聊天室切換重疊修復（PR #426）**：切換前清空訊息、以 key(currentSessionId) 重建訊息列表、coroutine 內二次確認防止快速切換競態。
- **#423 設定健檢與搜尋擴展（PR #426）**：catalogue 新增 diary_annotation_in_saver、ai_model_diary_annotation、proactive_intel_enabled、proactive_extract_enabled 條目（皆有對稱讀寫實作）；同義詞組新增「深色／主題」組、健康組擴充距離／熱量；全域設定搜尋改用 SETTINGS_SEARCH 用途 profile。
- **#424 AI 更智能與主動（PR #426）**：早晚簡報逐段誠實降級（缺資料明確標「無資料」）；回饋驅動節流（近 7 日同主題 👎≥2 等待窗 ×1.5，👍≥2 且無 👎 ×0.75，無回饋不變）；15 分鐘內有對話不投遞主動訊息（標記 dirty 併入下一輪）；聊天室動態建議 chips（狀態驅動、純本機、零 token、最多 4 顆）。
- **#425 全天行程誤判撞期修復（PR #426）**：全天（ALL_DAY）事件不計入定時行程衝突判定；偵測到全天事件時顯示全天事件資訊卡（新增日曆事件與通知轉日程皆適用）。

## [4.0.0] - 2026-11-23

- **Tag 對照**：[`v3.8.0...v4.0.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.8.0...v4.0.0)（`versionCode = 48`、`versionName = "4.0.0"`）
- **發佈資產（雙 APK）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。

### 新增與資訊架構重構
- **v4.0.0 全域資訊架構（IA）大改版與設定中心重構（#409 / PR #413）**：
  - **D1 導覽架構 6 Tab → 5 Tab（「洞察」整併「夥伴｜統計」）**：底部 `LiquidGlassTabBar` 收斂為「訊息、AI 助手、記事、記憶、洞察」5 個主分頁（高度 `64.dp`、選中指示器套用 `N5_TAB_INDICATOR_SPRING` 寬度與位移彈簧、圖示＋`labelSmall` 雙顯示與完整 `contentDescription`）；原「統計」併入「夥伴」改名「洞察（`MainTab.INSIGHT`）」，於頁面頂部以 `PhantomSecondaryTabRow`（「夥伴｜統計」二級膠囊切換）無縫切換；`MainActivity.mapLegacyTabExtra()` 將既有系統通知、桌面小工具、快捷方式與教學深連結（`"stats"`／`"STATS"`／`"partner"`／`"PARTNER"`／舊索引 `5`）全數無縫映射至「洞察」對應子分頁。
  - **D2 設定中心重構（搜尋優先＋釘選常用＋最近使用＋8 大分類頁）**：新增 `SettingsHomeScreen` 與 `SettingsCategoryRegistry`，將原單頁 13 張長捲動折疊卡改為同 Activity 兩層架構（以 `SettingsCategoryId?` 狀態搭配 N1／N2 共享 X 軸轉場切換總覽與分類頁，完整相容既有 `highlight_card_key` 深連結直達對應分類與設定項）：
    - **頂部雙模搜尋框**：`56.dp` 高度、`28.dp` 全圓角搜尋列，整合本地關鍵字分詞／同義詞即時檢索與「✨ AI 語意搜尋」按鈕，直接跨全分類（含預設折疊之進階設定）檢索 `SettingsCatalog` 全部 136 項設定。
    - **釘選常用設定（預設 6 項、可編輯）與最近使用**：提供雙欄釘選磁貼（預設 6 項高頻設定，長按磁貼或點擊「編輯釘選」可自訂至多 6 項；布林開關類支援於磁貼內直接切換 `FluidSwitch`）及「最近調整」橫向膠囊列（至多 5 項）。
    - **8 大分類頁（合併「個人化」與「外觀」）**：收斂為「AI 與模型」、「防詐與安全」、「通知與攔截」、「AI 夥伴與主動」、「權限與背景」、「外觀與個人化」（整併原個人化與外觀設定）、「資料與備份」、「關於與更新」8 大分類，每列動態顯示 1 行即時狀態摘要；分類子頁內亦提供頁內過濾搜尋框。
  - **D3 訊息首頁減法、分段群組清單與中央堆疊保留（`MessagesTab`）**：
    - `CenterFocusCardStack` 中央聚焦垂直滾輪堆疊完整保留為可選卡片排列模式（幾何與捲動物理 **0 diff**）。
    - 頂部統計列重構為 `surfaceContainer` 圓角三欄卡（今日訊息／未讀／高風險，點擊高風險直達詐騙警示中心）；篩選列收斂為「全部｜未讀｜高風險（含數量徽章）」3 顆智慧膠囊＋「篩選 ▾」ModalBottomSheet（收納星標、已記事、已待辦、分類標籤與排序）；一般條列模式重做為圓角分段群組清單（Segmented Grouped List，同群組內 `0.5.dp` 內縮分隔線與頂／中／底／單卡動態圓角）。
  - **D4 進階設定預設折疊與搜尋自動展開（`SettingsAdvancedSection`）**：將 24 項低頻／專家級設定 ID 與 Shizuku 跨行程看門狗區塊收納於各分類頁底部的「進階設定（N 項）」折疊區（預設收合）；所有折疊項目 100% 保留於 `SettingsCatalog`，透過設定搜尋（本地關鍵字或 AI 語意搜尋）或頁內搜尋命中時自動展開進階區塊、捲動定位並執行 `N7_HIGHLIGHT_PULSE_MS` 高亮脈衝。
  - **D5 移除常駐 22dp 下拉把手視覺，保留下拉搜尋手勢**：移除 `MainActivity` 頂部常駐 22dp `PhantomPullSearchHandle` 視覺橫條；於訊息、記事與記憶分頁完整保留清單頂端 `NestedScrollConnection` 下拉超界手勢（下拉超過 `56.dp` 搭配 `HapticFeedbackType.LongPress` 觸覺回饋即開啟搜尋），並於 `56.dp` 緊湊頂部列右側提供常駐全域搜尋膠囊鈕（「🔍 搜尋」）。
  - **D6 視覺化教學精簡（主頁 20→8 步、設定 9→5 步）**：`Onboarding.kt` 主頁新手教學由 20 步精簡為 8 步核心導覽、設定中心教學由 9 步精簡為 5 步導覽（完整保留 `spring` 挖洞框平滑過渡、四廠牌受限設定疑難排解與 Shizuku 一鍵授權）；既有 `onboarding_done` 與 `settings_tour_done` 完成旗標絕不重置，舊使用者升級後不強制重跑，僅於設定總覽頂部顯示一次性 `PhantomInlineBanner`（「✨ v4.0 介面已更新：瀏覽 5 步快速導覽」）。
  - **D7 大螢幕（`>= 600.dp`）`NavigationRail` 導覽軌**：於平板、摺疊機展開態與橫向大螢幕（可用寬度 `>= 600.dp`）自動切換為左側 `NavigationRail` 導覽軌＋右側置中主內容容器（最大寬度 `840.dp`），並隱藏底部導覽列。
  - **新共用元件與五大分頁／11 子頁骨架統一**：
    - `PhantomPageScaffold.kt`（新檔）：集中定義 `AuroraTokens`、`PhantomPageScaffold`（支援 Small／Medium／Large 頂部列）、`PhantomPredictiveBackContainer`、`PhantomSecondaryTabRow`（N6 二級膠囊滑動指示器）與 `PhantomInlineBanner`。
    - `GlobalSearchOverlay.kt`（新檔）：全域搜尋覆蓋層，支援「全部／訊息／設定／記憶／記事」5 範圍篩選 chips、最近搜尋紀錄（至多 8 筆，支援單筆移除與清空）、常用動作快捷列、釘選設定磁貼、分區結果清單與離線安全之設定 AI 語意搜尋。
    - `SettingsRow.kt`（新檔）：統一分段式設定列元件家族（`SettingsGroupCard`、`SettingsSwitchRow`、`SettingsNavigationRow`、`SettingsOptionRow`、`SettingsSliderRow`、`SettingsInfoRow`、`SettingsAdvancedSection`），確保 `>= 56.dp` 最小觸控高度與無障礙語意。
    - `UiLayoutPrefs.kt`（新檔）：獨立 `phantom_ui_v400` SharedPreferences，**僅儲存設定 catalog ID（釘選與最近調整）、最近搜尋短字串與 v4 導覽提示旗標**，零訊息內文與零敏感資料。
    - 分頁與子頁視覺對齊：`ChatScreen`（緊湊頂部列含省 token 狀態徽章與 `⋮` 選單、輸入框上方單行 `LazyRow` 整合模型切換與「🔍 聯網」膠囊，完整保留左側邊緣滑出歷史抽屜手勢）、`NotesTab`（N6 `PhantomSecondaryTabRow` 切換本機日曆「日｜週｜月」）、`MemoryTab`（頂部四格統計整合為單張 `surfaceContainer` 卡＋N6「記憶｜規則｜防詐免疫」二級切換）、`StatsTab`（頂部新增純本機零 LLM「本週重點」自然語言摘要條，既有 13 張統計卡零刪減）、`SettingsActivity` 關於頁版本狀態卡，以及 11 個子頁面（`ApiKeySettingsActivity`、`AssistantHubActivity` 含頂部 7 區錨點 chip 列、`PermissionHubActivity` 含頂部未就緒權限總覽卡、`LinkedAppsActivity`、`AutoReplySettingsActivity`、`ScamCenterActivity`、`TagManagementActivity`、`WhitelistManagementActivity`、`MessageDetailActivity`、`AppThreadsScreen`、`ConversationScreen`）全面套用統一骨架。
  - **動效擴充（`PhantomMotion` 追加 N1–N7 與 `PredictiveBackHandler`）**：
    - `PhantomMotion.kt` 僅追加 N1–N7 常數（`N1_SHARED_AXIS_ENTER_MS = 260`／`EXIT_MS = 180`、`N2_SHARED_AXIS_SLIDE_DP = 24`、`N3_SEARCH_SCRIM_ENTER_MS = 220`／`EXIT_MS = 160`、`N4_PREDICTIVE_BACK_MIN_SCALE = 0.92f`、`N5_TAB_INDICATOR_SPRING`、`N6_SECONDARY_TAB_SPRING`、`N7_HIGHLIGHT_PULSE_MS = 900`），v3.7.0 既有動效常數零修改；所有動畫均於 `graphicsLayer` draw-phase 執行，系統動畫縮放＝0（`rememberMotionEnabled() == false`）時自動退場為瞬時切換。
    - 採用 `activity-compose:1.13.0` 原生 `PredictiveBackHandler` 實作設定分類子頁與全域搜尋覆蓋層之預測式返回手勢縮放預覽（`1f → 0.92f`）。
  - **純 UI 層改版與零回歸聲明**：本版本 `service/`、`data/`、`network/`、`util/` 四大目錄 **0 diff**，Room 資料庫 schema、背景服務、AI 請求與防詐決策邏輯零修改，純屬 UI 層資訊架構與互動升級，無任何資料或背景行為變更，並 100% 保留 `#402–#408` 修復行為與無障礙 `contentDescription`。

## [3.8.0] - 2026-11-23

- **Tag 對照**：[`v3.7.0...v3.8.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.7.0...v3.8.0)（`versionCode = 47`、`versionName = "3.8.0"`）
- **發佈資產（雙 APK）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。

### 新增與行為變更
- **Health Connect 聊天健康匯總查詢注入與背景主動健康觀察提醒（#403 / PR #410，隱私行為變更）**：
  - **隱私行為變更（聊天健康提問匯總注入）**：當使用者於 AI 助手對話主動詢問睡眠、步數、心率、疲勞或運動狀況，且「允許 AI 讀取健康摘要」開關為開啟時，`LocalDataConnector.buildHealthContext()` 會將近 7 日每日匯總數值（`DailyHealthPoint` 睡眠時數、步數、靜止心率及 7 日均值）與今日晨間快照（`MorningHealthSnapshot`）以 `<health_context>` 注入當次對話提示詞送往使用者自行設定的 AI 服務商；**僅傳送近 7 日匯總數值，原始生理採樣序列（raw samples）絕不出本機**（已同步載明於 `PRIVACY.md` 第二節第 2 點）。未安裝 Health Connect、未授予讀取權限或關閉開關時，AI 誠實告知未授權／未開啟並引導至設定，絕不捏造數據。
  - **主動健康觀察提醒（`HealthObservationPolicy` 掛載於 `ProactiveMonitorWorker`）**：新增純本地查表規則（零 LLM token 消耗），包含連續 2 晚睡眠 `< 6` 小時（`sleep_2nights_under_6h`）、連續 3 日步數 `< 2000` 步（`steps_3days_under_2000`）、當日靜止心率高於 7 日均值 `>= 12 bpm`（`hr_elevated_12bpm`，需至少 4 日有效基線）；命中時透過 `ProactiveEngine.deliverProactiveMessage`（`NotifIds.HEALTH_PROACTIVE = 1014`）發送溫和關懷提醒，嚴守每條規則每日最多 1 次、每 7 日最多 2 次之頻率上限及勿擾時段與每日主動訊息總上限。
  - **獨立開關**：「權限授予中心（`PermissionHubActivity`）」Health Connect 卡片與 `SettingsCatalog`（`health_ai_query_enabled`）新增「允許 AI 讀取健康摘要」開關（預設開啟；關閉後即停止聊天健康匯總注入與背景主動健康觀察提醒，不影響統計頁本機 7 日生理趨勢圖）。

### 變更與修正
- **修復本週 AI 摘要輸出截斷與統計頁全文展開（#402 / PR #410）**：
  - `AiClient.summarizeWeekly()` 將 `maxTokens` 上限由 `1400`（Groq `1000`）提升至 `2400`（Groq `1600`，遇 HTTP 413 降級重試為 `1400`），強化系統提示詞要求每段與結尾必須以完整句號收尾；改用 `postChatCompletionDetailed` 檢查 `finishReason`（`length`／`max_tokens`）與句尾標點（`endsWithCompleteSentence`），若未完句自動發起一次續寫請求（`maxTokens = 1200`）並經 `mergeContinuationText` 與 `ensureCompleteSentenceEnding` 確保完整句收尾。
  - 統計頁（`StatsTab`）本週 AI 摘要卡移除收合狀態下的 `.take(70)` 硬截斷，改為 `maxLines = 2`＋`TextOverflow.Ellipsis` 搭配「展開全文」按鈕，展開後完整顯示 Markdown 全文且手動生成完成後自動展開；`AiSummaryEntity.summary` 一律儲存未截斷全文。
- **主動訊息投遞統一發送系統通知、深層連結直達與通知頻道健檢引導（#404 / PR #410）**：
  - 盤點全庫 assistant-initiated 主動訊息寫入路徑，將早晚簡報（`ProactiveEngine.deliverBriefing`）統一收斂委派至 `deliverProactiveMessage`（早安簡報 `NotifIds.BRIEFING_MORNING = 1008`、晚安簡報 `NotifIds.BRIEFING_EVENING = 1009`），與主動行程／目標／健康提醒（`1010`／`1014`）、日曆洞察（`1020..1044`）、定時任務模板（`1050..1089`）及主動情報摘要（`1013`）全面共用去重、勿擾、寫入專屬主動訊息聊天室（`SESSION_PROACTIVE_BRIEFING`）、未讀紅點與系統通知發送紀律。
  - 點擊任何主動提醒或早晚簡報系統通知（攜帶 `EXTRA_BRIEFING_MSG_ID`），`MainActivity` 與 `ChatAssistantTab` 直接切換並打開「AI 助手簡報與主動提醒」主動聊天室且自動清除未讀徽章。
  - 新增 `ProactiveEngine.isBriefingChannelBlocked()` 檢查 `assistant_briefing` 通知頻道是否被設為 `IMPORTANCE_NONE`；若頻道被停用，於聊天室頂部顯示一次性引導卡（「前往開啟通知頻道」直達系統頻道設定／「知道了」持久化關閉）。
- **修復設定頁 AI 語意搜尋「回傳格式無法解析」（#405 / PR #410）**：
  - `AiClient.searchSettings()` 啟用 `response_format: {"type": "json_object"}`（若自架或第三方端點回傳 HTTP 400 則自動移除 `response_format` 無縫退回），強化提示詞明確規定回傳 `{"ids": [...]}`（查無相符時回傳 `{"ids": []}`）。
  - 新增 `parseSettingsSearchIdsLenient()` 容錯解析鏈：依序剝除 Markdown code fence 與前後雜訊 → 括號平衡擷取首個 `{...}` 或 `[...]` 區段 → 寬鬆 schema 解析（相容 `ids`／`results`／`settings`／`items`、裸陣列與物件陣列 `id`／`key`／`settingId`）→ 已知 catalog ID token 掃描；首次解析失敗時自動以極簡嚴格提示詞重試一次，仍失敗才退回本地搜尋並提示，且明確區分「查無相符設定（`emptyList()`）」與「格式解析失敗（`null`）」，命中結果於搜尋框下方渲染可點擊直達清單並同步過濾設定卡片。
- **設定頁本地關鍵字搜尋分詞評分、同義詞與口語別名補強（#406 / PR #410）**：
  - `SettingsCatalog.searchByKeywords()` 新增口語虛詞去除（`STOP_WORDS`）、雙字元滑動切詞與 16 組高頻同義詞擴展（`SYNONYM_GROUPS`，涵蓋字體／字級／字太大、訊息卡／卡片／堆疊／密度／92dp、通知／推播／太吵、常用／最愛／置頂、深色／夜間／護眼、備份／換手機等），依 title（`+12`）、aliases（`+10`）、description（`+6`）加權計分排序，並 100% 保留單一關鍵字直覺子字串匹配行為。
  - 為 `SettingsCatalog` 全部 136 個設定項目逐一補齊 3–5 個口語化 `aliases`，並於設定頁顯示可點擊的「🔍 找到 N 項相關設定（點選直達）」結果卡。
- **根治主動訊息、早晚簡報、話題接續列與待辦膠囊半句截斷（#407 / PR #410）**：
  - `AiClient.generateBriefingText()` 強化完整句收尾要求並檢查 `finishReason`（`length`／`max_tokens`）與 `endsWithCompleteSentence()`；若潤飾輸出被截斷或於句中中斷則回傳 `null`，自動退回完整的原始模板全文。
  - 移除 `ProactiveEngine`（日曆摘要 `.take(260)`、待辦標題 `.take(20)`、空檔建議 `.take(160)`、`saveLastBriefingMeta` `.take(120)`）、`TaskTemplateEngine.runAggregation`（記事與待辦 `.take(28)`）與 `PhantomPrefs.saveChatContinuityAnchor`（`.take(80)` 放寬至 500 字）之顯示與儲存端硬截斷；聊天室頂部「↪ 接續：上次你提到…」膠囊、未決待辦膠囊與夥伴頁「最新早晚簡報」預覽卡皆支援點擊展開檢視完整全文。
- **修復更新下載卡「下載中」不彈出安裝頁與安裝未知應用權限引導（#408 / PR #410）**：
  - `PhantomPrefs` 與 `AppUpdateHelper` 新增 `downloadId`＋`targetVersion` 持久化（版本不符時自動清理舊紀錄與殘留 `.apk` 檔案）；除 `BroadcastReceiver` 外新增 `Dispatchers.IO` 協程每 600ms 輪詢 `DownloadManager.query()` 並綁定 `ON_RESUME` 重查，背景或關閉畫面期間下載完成後返回 App 即自動切換為「安裝更新（vX.Y.Z）」並觸發安裝流程。
  - 下載失敗（`STATUS_FAILED`）或 APK 檔案損毀（`< 100 KB`）時顯示具體白話原因（網路中斷、空間不足、HTTP 錯誤等）並提供「重新下載」與「改用瀏覽器下載更新」備援按鈕；Android 8+ 若 `canRequestPackageInstalls()` 未授權，`installApk()` 回傳 `NEEDS_UNKNOWN_SOURCES_PERMISSION` 並於卡片內顯示一步引導鈕跳轉 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`，授權返回後 `ON_RESUME` 自動續接彈出系統安裝器（完整保留 `#374` 稍後提醒 snooze 與 `#391` 更新說明捲動預覽）。

## [3.7.0] - 2026-11-23

- **Tag 對照**：[`v3.6.0...v3.7.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.6.0...v3.7.0)（`versionCode = 46`、`versionName = "3.7.0"`）
- **發佈資產（雙 APK）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。

### 新增與優化
- **Phantom Aurora 全域 UI/UX 視覺與動效大改版（#398 / PR #399）**：
  - **共用動效核心 `PhantomMotion.kt`（新檔）**：集中定義 Material 3 Emphasized 曲線（`EmphasizedDecelerate`、`EmphasizedAccelerate`）、收合展開統一進出場規格（`ExpandEnter` 彈性高度展開＋淡入、`ExpandExit` 加速收起＋淡出）、清單交錯上浮進場（`Modifier.phantomEnter` 與 `LazyListScope.phantomItem`，前 8 項依序每項延遲 `36ms`，並以 `LocalPhantomEnterRegistry` 記錄同分頁已播放過的 key 避免捲動重複播放）、彈出進場（`Modifier.phantomPopIn`）、旋轉箭頭（`PhantomChevron` 0°↔180° spring 取代靜態 `KeyboardArrowUp`/`KeyboardArrowDown` 切換）、玻璃高光邊（`Modifier.phantomSheen`，`drawWithCache` 繪製深淺色自適應上緣高光描邊與頂部內暈）、按壓點擊封裝（`Modifier.phantomPressable`）與數值方向滾動文字（`PhantomAnimatedCount`）；透過 `rememberMotionEnabled()` 讀取系統 `Settings.Global.ANIMATOR_DURATION_SCALE`，當系統動畫縮放＝0 時自動關閉手動 stagger 與無限循環動畫並靜態退場。
  - **`FluidSwitch` 流體開關升級（draw-phase）**：軌道與 thumb 色彩隨位移/拖曳進度於 `drawBehind` 連續 `lerp` 插值，開啟時繪製主色軌道外暈與 thumb 柔影，並於進度後半段（`p > 0.45f`）在 thumb 內動態描繪圓角勾勾路徑；維持既有按壓 squish 變形、拖曳過半閾值切換與無障礙 `Role.Switch`/`ToggleableState` 語意不變。
  - **`LiquidGlassTabBar` 圖示動效升級**：選中分頁時圖示以 `graphicsLayer` 執行 spring 輕彈放大（`1f → 1.12f`）並搭配 `animateColorAsState`（`180ms`）平滑色彩過渡。
  - **`pressableScale` 按壓回饋升級（`Glass.kt`）**：改以 `Animatable` 驅動 `graphicsLayer` 縮放並支援選配 `glowShape` 於按壓時以 `drawWithContent` 疊加主色邊光；偵測手指移動超過 `touchSlop` 或手勢被捲動消費（`change.isConsumed`）時立即提前釋放回彈，防止捲動清單時卡片卡在縮小狀態。
  - **共用卡片、搜尋與浮動元件質感升級（`Components.kt`）**：`PhantomGlassCard`（玻璃與非玻璃分支）、`PhantomFloatingSurface`、`MessageCard`（含高風險詐騙卡）與 `PhantomSearchBarPill` 全面疊加 `phantomSheen` 高光描邊；OTP 驗證碼複製膠囊與 `PhantomSearchBarPill` 套用 `pressableScale`；`PhantomDialog`（`0.92f`）與 `EmptyStateView` 圖示（`0.6f`）套用 `phantomPopIn` 彈出進場；`PhantomFullScreenSearchOverlay` 升級為 Emphasized 進出場曲線。
  - **主分頁方向感切換與五大分頁／設定子頁／教學遮罩動效一致化**：
    - `MainActivity.kt`：6 主分頁切換套用 `AnimatedContent`（依分頁 `ordinal` 決定左右滑入方向＋短淡出退場），並完整保留 `SaveableStateProvider(tab.name)` 分頁狀態與提供 `LocalPhantomEnterRegistry`。
    - 主分頁：`MessagesTab`（首頁今日／未讀／詐騙統計改用 `FlowRow`＋`PhantomAnimatedCount` 支援 `fontScale = 1.3+` 自動換行、篩選收合鈕 `PhantomChevron`＋`pressableScale`、離線重試提示卡統一展開規格、App 群組與訊息清單 `animateItem()`＋`phantomEnter`）、`NotesTab`（待辦與本機日曆收合 `PhantomChevron`＋統一展開規格、記事清單 `animateItem()`＋`phantomEnter`）、`TodoSection`（待辦列 `pressableScale`＋完成態透明度 `graphicsLayer` 過渡）、`MemoryTab`（收合區塊 `PhantomChevron`＋統一展開規格、10 區塊 `phantomItem` 進場）、`WatchTopicSheet`（關注清單 `animateItem()`＋`phantomEnter`）、`StatsTab`（簡報與週報 `PhantomChevron`、分享鈕 `pressableScale`、13 統計區塊 `phantomItem` 進場）、`PartnerTab`（7 區塊 `phantomItem` 進場）、`DiarySection`（AI 註記統一展開規格）、`GoalSection`（目標卡 `pressableScale`＋進度條 `animateFloatAsState` spring 過渡）。
    - 設定系統與全部 12 子頁：`SettingsActivity`（`CollapsibleSettingsCard` 標題列 `pressableScale`＋`PhantomChevron`＋統一展開規格，自訂常用指令、本機日曆清單、隱藏日曆、個別 App 節流覆寫展開動效全面一致化）、`ApiKeySettingsActivity`（Profile 卡 `animateItem()`＋`phantomEnter`、用途模型卡 `phantomItem`）、`AssistantHubActivity`（7 區 `phantomItem`）、`AutoReplySettingsActivity`（4 卡 `phantomEnter`）、`LinkedAppsActivity`（5 區 `phantomItem`）、`PermissionHubActivity`（4 區 `phantomItem`）、`ScamCenterActivity`（`animateItem()`＋`phantomEnter`）、`TagManagementActivity`（`animateItem()`＋`phantomEnter`）、`WhitelistManagementActivity`（新增表單與 App 分組 `PhantomChevron`＋統一展開規格）、`ContactsIntegrationSection`（`pressableScale`）、`AppThreadsScreen`（對話串 `animateItem()`＋`phantomEnter`）、`ConversationScreen`（日期分隔列與訊息列 `animateItem()`）、`MessageDetailActivity`（5 張詳情卡 `phantomEnter`）。
    - 新手與設定教學（`Onboarding.kt`）：高亮挖洞框（spotlight hole）4 邊座標由線性 `tween` 升級為 `spring` 流暢滑移、教學字卡改用 Emphasized 減速＋spring 進場、受限設定疑難排解區塊套用統一展開規格。
  - **聊天室動效與效能優化（`ChatScreen.kt`、`ChatScreenParts.kt`）**：聊天訊息氣泡套用 `animateItem()`；使用者氣泡疊加 `phantomSheen` 高光邊（100% 沿用使用者自訂 `bubbleCornerDp` `8–24dp` 圓角與 `70%–95%` 最大寬度比例）；「推演思路」折疊區換用 `PhantomChevron`＋統一展開規格；送出按鈕與側邊歷史抽屜會話列套用 `pressableScale`／`animateItem()`（底部輸入框本身不參與位移或縮放動畫、打字零抖動）；`TypingDotsIndicator` 重寫為 draw-phase `graphicsLayer`（相位驅動 alpha、scale 與垂直波浪彈跳），消除既有每幀 composition 重組並於系統動畫縮放＝0 時自動退場為靜態三圓點。
  - **Markdown 排版精修（`PhantomMarkdownText.kt`）**：引用塊改為右側 `10.dp` 圓角＋左側 `3.5.dp` 圓角膠囊飾條與更舒適內距；程式碼區塊升級 `12.dp` 圓角＋`1.dp` 細邊框與 `12.dp` 內距；表格外框升級 `12.dp` 圓角並新增奇數資料列斑馬紋底色（`headerBg.copy(alpha = 0.35f)`），Markdown 文字與語法解析邏輯零變動。
  - **全域 Activity 視窗轉場（`styles.xml`＋`res/anim/phantom_{open,close}_{enter,exit}.xml`）**：`Theme.PhantomCheck` 新增 `PhantomWindowAnimation` 定義子頁面開啟／返回之輕量水平位移（`8%p`／`-4%p`）與透明度轉場，系統視窗動畫縮放＝0 時由 Android 框架自動省略。
  - **嚴格邊界與零回歸保證**：本輪**零新增第三方依賴**、未使用 `SharedTransitionLayout`／`LookaheadScope`、`CenterFocusCardStack` 中央聚焦堆疊捲動物理與幾何 **0 diff**，全數 37 處檔案變更皆位於 `ui/` 與 `res/`（`data/`、`service/`、`network/`、`util/` 皆 **0 diff**），零功能回歸。

## [3.6.0] - 2026-11-22

- **Tag 對照**：[`v3.5.0...v3.6.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.5.0...v3.6.0)（`versionCode = 45`、`versionName = "3.6.0"`）
- **發佈資產（雙 APK）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。

### 新增功能
- **主動情報中樞 Phase A：關注實體檔、每日 AI 萃取候選與獨立額度（#388 / PR #395）**：
  - Room 資料庫由 v22 非破壞性升級至 v23（`MIGRATION_22_23` 純 `CREATE TABLE IF NOT EXISTS watch_topics` 與 `index_watch_topics_status`），新增 `WatchTopicEntity`（含 `category`、`title`、`queryHint`、`source`、`status`、`confidence`、`lastQueriedAt`、`lastDigestHash`、`hitCount` 等欄位）與 `WatchTopicDao`。
  - 新增 `WatchTopicEngine` 與每日背景萃取 `WatchTopicExtractWorker`（`phantom_watch_topic_extract`、`ExistingPeriodicWorkPolicy.KEEP`、`NetworkType.CONNECTED`＋`requiresBatteryNotLow`）：以 SQL（`activeNormalMemoriesForPrompt`）與 Kotlin（`privacyTier != PRIVACY_SENSITIVE`）雙重硬性排除 `🔒 敏感` 記憶，全素材經 `AiClient.sanitizeForPrompt` 清洗後萃取至多 5 筆待確認候選（`source = "ai_extracted"`、`confidence = "low"`），**待使用者確認前絕不直接觸發外部查詢或推播**。
  - 新增 `AiPurpose.PROACTIVE`（「主動情報與關注萃取」）獨立模型用途與每日額度上限（預設 12 次／日，失敗自動退還額度）；新增記憶分頁與設定頁共用的 `WatchTopicManagerDialog`（支援待確認候選一鍵全部接受、逐筆確認／編輯 `queryHint`／靜音／恢復／刪除及手動新增）。
- **主動情報中樞 Phase B：Gemini Grounding 聯網查詢與聊天頁聯網開關（#389 / PR #395）**：
  - `ChatRequest` 新增選配 `tools: List<Map<String, Any>>? = null`（Gson 預設忽略 `null`，既有非聯網請求逐位元組零影響），並新增 `AiClient.runGroundedQuery()`。
  - 僅當目標 Profile 為 Gemini OpenAI 相容端點且模型為 **Gemini 3+** 時注入 `tools = [{"google_search": {}}]`，並多路徑防禦性解析來源網址（`annotations[].url_citation.url`、`extra_content.google.grounding_metadata.grounding_chunks[].web.uri`、`grounding_metadata`／`groundingMetadata`、`citations[]` 與內文 URL fallback）；遇 HTTP 429 自動退還額度並靜默退避。
  - `ChatScreen` 輸入列上方新增「🔍 聯網」切換 chip：僅 Gemini 3+ 可啟用原生 Google Search Grounding（其他 provider 如 Mistral／Groq 若未設定 BYOK 外部搜尋端點則誠實灰顯並點擊說明原因）；查無結果或網路失敗時誠實回報，絕不以模型舊知識偽裝即時聯網結果。
- **主動情報中樞 Phase C/D/E：週期查詢、去重整理、主動推送、設定疆界與隱私文件（#390 / PR #395）**：
  - 新增 `WatchQueryPlanner`（純本機零 token 挑選到期主題：`region` 類 6 小時、其餘 24 小時，依 `high > medium > low` 排序每輪至多 3 筆，省 token 模式自動跳過）、`DigestComposer`（12 小時本地短快取、SHA-256 摘要雜湊去重無新意零推播、查無新訊嚴守「近期無新資訊」零幻覺鐵律並附來源連結）與 `ProactiveIntelWorker`（`phantom_proactive_intel`、`ExistingPeriodicWorkPolicy.UPDATE`、`CONNECTED`＋`requiresBatteryNotLow`、`BootReceiver` 開機重排），並與 `ProactiveEngine` 早晚簡報共用情報摘要、獨立勿擾、每日主動上限與 `NotifIds.PROACTIVE_INTEL = 1013` 推播閘門。
  - 設定頁新增「主動情報中樞」設定卡（**總開關預設關閉**，首次開啟彈出完整隱私與額度說明對話框；支援每日自動萃取開關、6／12／24 小時查詢頻率、關注清單管理、「立即查詢」預覽及「Mistral 日常分析 ＋ Gemini 主動情報」分用途或 BYOK 雙軌引導），全數註冊進 `SettingsCatalog`；同步增修 `PRIVACY.md` 第 6 節載明開啟時**僅送出已確認之關注主題短字串（`title`／`queryHint`，≤80 字）**，絕不外送原始通知、記憶全文或敏感資料。
  - 主動情報訊息寫入專屬主動訊息聊天室並由 `PhantomMarkdownText` 渲染可點擊來源連結、氣泡下方提供「💬 深入追問」按鈕一鍵切至對話並預填追問；系統通知附「不再關注」動作鈕（由 `WatchTopicMuteReceiver` `exported="false"`＋`FLAG_IMMUTABLE` 直接靜音該主題，零 trampoline）。
- **關於與自動更新卡顯示最新版本更新內容（#391 / PR #395）**：
  - `AppUpdateHelper` 之 `UpdateCheckResult.UpToDate` 與 `UpdateAvailable` 擴充攜帶 GitHub `releases/latest` 的 `releaseNotes`（`body`）與 `publishedDate`（每次即時抓取不快取）。
  - 新增共用捲動式 `ReleaseNotesPreviewBox`（`PhantomMarkdownText` 渲染＋`heightIn(max = 260.dp)`＋`verticalScroll`，空內容誠實顯示「此版本無更新說明」），同步嵌入設定頁「關於」卡片與首頁自動更新提醒對話框。
- **夥伴頁頭像名字置頂去卡片化與區域背景自訂（#392 / PR #395）**：
  - `PartnerTab` 將助手頭像與名字區塊移至全頁最頂端第一項（AI 日記與長程目標後移），移除 `PhantomGlassCard` 外框改為全寬去卡片化橫幅（與液態玻璃開關解耦，頭像放大至 `76.dp`），右上角提供 `48.dp` 相機／移除按鈕。
  - `BackgroundImageHelper` 新增夥伴頂部橫幅專屬內部儲存槽位（`assistant_header.jpg`），沿用 bounds-first `inSampleSize` 降採樣與壓縮（防 50MP 大圖 OOM、免持久化外部 URI 權限），底部搭配漸層 scrim 自然銜接下方內容；同步於 `SettingsCatalog`（`assistant_header_background`）與設定頁「個人化」新增管理入口。
- **助手與介面客製化擴充（#393 / PR #395）**：
  - 盤點 `PhantomPrefs` 與 `SettingsCatalog` 後於設定頁「個人化」新增 5 項高價值自訂項目（預設值皆 100% 對齊 v3.5.0 既有行為，並提供一鍵恢復預設）：① AI 聊天室字體獨立縮放（`85%–130%`，預設 `100%`）、② 訊息中心字體獨立縮放（`85%–130%`，預設 `100%`）、③ 聊天氣泡圓角半徑（`8–24dp`，預設 `18dp`）、④ 聊天氣泡最大寬度比例（`70%–95%`，預設 `82%`）、⑤ 首頁我的最愛 App 橫列顯示上限（`全部（預設）／最多 4 個／最多 6 個／最多 8 個`）。

### 變更與修正
- **修復聊天純動作回覆誤顯示「AI 未回傳可顯示的內容」（#386 / PR #395）**：
  - `ParsedChatActions` 抽出共用 `hasExtendedProposals()` 與 `hasAnyProposal()`，統一涵蓋 `search`、`todoManage`、`vipPin`、`muteApp`、`markRead`、`export`、`whitelistDelete`、`usageQuery`、`openSetting`、`crossApp` 與 `demotedQueryHints`；修正 AI 僅回傳 fenced JSON 動作區塊（如刪除待辦、查詢使用情況等）並成功執行時，前端因 `cleanReply` 為空而誤套用「（AI 未回傳可顯示的內容，請重試或切換模型）」錯誤字串的問題。
- **日曆行程查詢根治幻覺：一律逐字引自本機日曆、查無誠實拒答（#387 / PR #395）**：
  - `LocalDataConnector.isCalendarQuery()` 擴充課表與排程語意詞（課／堂／節／上完後／接下來／下一個／之後／待會／還有什麼／星期幾等）搭配問句標記雙條件觸發，並以 `DateTimeFormatter`＋`ZoneOffset.UTC` 精準處理全天事件邊界。
  - 新增 `CalendarBridge.queryScheduleSummary()` 與 `calendar_query` 工具動作（純本機查詢 `CalendarContract.Instances` 逐字回傳真實起迄時間、標題與地點）；同時強化 `ChatScreenParts` 系統提示防幻覺鐵律與拒答正反例，具體行程一律逐字來自本機日曆查詢結果，未授權、未勾選日曆或該時段查無行程時誠實拒答並引導至權限／設定頁，嚴禁模型自行推算或編造課表。
- **全域健檢與 OEM 相容複驗（#394 / PR #395）**：
  - **更新下載 Receiver 安全旗標修正**：`SettingsActivity` 註冊 `DownloadManager.ACTION_DOWNLOAD_COMPLETE` 廣播接收器由 `RECEIVER_EXPORTED` 修正為 `ContextCompat.RECEIVER_NOT_EXPORTED`，防止外部應用偽造下載完成廣播。
  - **GitHub 監控連續失敗誠實通知**：`GitHubMonitorWorker` 新增連續失敗計數與降頻日誌，連續失敗達第 3 次時發出單次誠實提醒通知引導檢查網路或 PAT 權限，成功同步後自動歸零重置。
  - **開機重排 Catch-up 補齊 24 小時地板**：`BootReceiver` 除待辦事項外，對 `scheduled` 訊息提醒（`messageDao().allScheduledReminders()`）同步套用 24 小時 `catchUpFloorMs` 過濾，避免重開機時補發過久以前的歷史逾期訊息提醒；並完成 Room `1→23` 遷移鏈、`PendingIntent` `FLAG_IMMUTABLE`、`Dispatchers.IO` 與五大 OEM 廠牌背景限制相容複驗。

## [3.5.0] - 2026-11-22

### 新增功能
- **AI 日記／每日回顧（#378 / PR #383）**：
  - 每日 00:30 本地 `DiaryWorker`（獨立 unique work＋`maybeCatchUp` 補跑 `retries≤3`＋`BootReceiver` 重排）以零 token 純 SQL 聚合昨日完成的待辦、重要記事、高風險攔截與行程時間軸寫入 `diary_days`；於「AI 夥伴」分頁頂部新增日記卡片，支援月份切換與每月總覽。
  - AI 註記預設收合、僅讀取 `timelineJson`（絕不二次掃描原始通知）、展開才生成一次並快取至 `aiNote`，支援「不準」回饋降低同類權重；無 API key 時時間軸與月份總覽 100% 離線可用。
- **長程目標追蹤（#379 / PR #383）**：
  - 新增 `GoalEngine` 與 `GoalSection`：對話中採結構訊號前置＋置信度門檻（`<60` 完全靜默、`≥60` 必經可編輯確認卡，含「不感興趣」同訊息不再詢問與「判斷有誤」回饋），確認後自動拆解週期子待辦（標記 `[目標#id]`）並複用 `ReminderScheduler` 排程提醒；進度僅計算明確關聯之子待辦。
  - 連續 3 個週期逾期未互動時自動降頻一級並於主動聊天室告知一次（絕不自動升頻）；夥伴分頁新增目標卡（進度條、連續天數、下一個里程碑日期、手動新增／暫停／刪除／調頻）並將目標完成納入夥伴親密度互動計數；無 API key 時手動建立與追蹤全流程 100% 可用。
- **宣告式 Agentic 能力表與受阻自動改道（#380 / PR #383）**：
  - 新增 `IntentCapabilityRegistry` 全枚舉有序路徑表（`REMINDER`：`REMINDER_EXACT_ALARM` → `REMINDER_SYSTEM_NOTIF` → `REMINDER_CALENDAR_ALLDAY`；`MEMO`：`MEMO_LOCAL_NOTE` → `MEMO_PROACTIVE_CHAT`；`CALENDAR_ADD`：`CALENDAR_DIRECT_WRITE` → `CALENDAR_ACTION_INSERT` → `CALENDAR_LOCAL_TODO`；`OPEN_APP`：`OPEN_APP_DIRECT` → `OPEN_APP_CHOOSER`），AI 僅選意圖、不自創路徑。
  - 執行時若偵測權限或系統限制受阻（如 `canScheduleExactAlarms() == false`、無日曆寫入權限或未安裝目標 App），自動依序降級至下一條安全路徑並顯示一次性改道告知；全路徑受阻時回報白話原因並附直達 `PermissionHubActivity` 按鈕；確認卡新增「執行方式」說明，且維持 `CrossAppActionExecutor.execute()` 僅由確認卡 `onClick` 觸發之鐵律。
- **Health Connect 生理數據整合（#381 / PR #383）**：
  - 新增 `HealthConnectBridge` 整合 `androidx.health.connect:connect-client:1.1.0`（讀取 `SleepSessionRecord`、`StepsRecord`、`HeartRateRecord`），`getOrCreate` 嚴格捕捉 `IllegalStateException`/`Throwable` 供無 GMS／未安裝 ROM 誠實降級；`PermissionHubActivity` 新增授權狀態與引導卡。
  - 晨間簡報（08:45 `BriefingScheduler`）於本機聚合前一晚睡眠、昨日步數與靜止心率，經固定查表式規則（如睡眠 `<6h` 且早間有行程、步數趨勢、心率突增）僅將命中的單句規則結論交由 AI 潤飾（原始數值絕不出本機，無命中則整段不顯示，同步失敗靜默跳過不影響簡報）；未授權時每週最多一次軟性邀請；`StatsTab` 新增 7 日睡眠／步數迷你條趨勢圖（無 API key 照常顯示）。
- **聊天室訊息右下角時間戳（#376 / PR #383）**：使用者、AI、主動訊息與系統事件氣泡右下角統一顯示 `10sp` `HH:mm` 時間戳（讀取 `ChatMessageEntity.createdAt`，嚴格沿用既有 `CHAT_TIME_FORMATTER` `Locale.US`，`softWrap = false` 獨立底右列支援 `fontScale = 1.3` 與窄螢幕不裁切，可於設定開關）。
- **本機日曆多檢視與帳號分組治理（#373 / PR #383）**：`CalendarBridge.listCalendars` 增讀 `VISIBLE` 與 `SYNC_EVENTS`；設定頁預設過濾顯示中日曆、依帳號分組並提供批次全選、「一鍵全選顯示中日曆」與可收合隱藏日曆；`NotesTab` 新增本機日曆「日／週／月」檢視切換與零勾選導引卡；`queryFreeSlotsSummary` 附註已勾選／未勾選日曆數量。
- **主動訊息回饋與自訂設定擴充（#377 / PR #383）**：早晚簡報與主動提醒拆分獨立每日上限；每則主動訊息氣泡提供「👍 有幫助／👎 打擾」輕量回饋（累積打擾自動抑制同類）；將提前提醒分鐘數（15／30／60 分）、全天事件提醒時刻、主動監控間隔、去重冷卻時間、聊天時間戳開關與更新提醒延後（snooze）間隔全面接入 `SettingsCatalog`、設定頁 UI 與 `PhantomSettingsBus`。

### 行為變更
- **主動提醒改事件里程碑式去重（#372 / PR #383）**：`ProactiveEngine` 將狀態式觸發改為事件里程碑式去重（`type:id:dayKey:milestone` 與 `allDay:id:dayKey`，同一待辦或日曆事件於「24h 前 (`due24h`)／提前 N 分鐘 (`lead`)／逾期 (`overdue`)」各里程碑僅提醒一次，跨日自動清理過期 key），並將 `deliverProactiveAlert` 統一匯入 `deliverProactiveMessage` 去重閘門搭配 10 分鐘 content hash 雙重防護，根治 15 分鐘 Worker 因無關新訊息指紋變動而重複轟炸同一提醒的問題。
- **防詐自動判定收斂與可疑關鍵詞收斂（#375 / PR #383）**：
  - `DecisionEngine` 將原 `hasUrlOrCrypto` 拆分為弱訊號 `urlPresent` 與強訊號 `intelHit`／`cryptoAddressPresent`：規則 5 自動標記 `scam` 收斂為必須具備「情報命中 (`intelHit`)、加密貨幣地址 (`cryptoAddressPresent`) 或具體詐騙操作訊號 (`fraud_cue`)」等強訊號；僅含一般連結（`urlPresent`）或與 30 天發送者高信譽／熟人訊號衝突時，一律走規則 4／規則 6 降級為確認卡（`ConfirmCard`），絕不單獨自動判定為詐騙。
  - 收斂 `IndicatorExtractor.hasSuspiciousFeature`（省 token 模式 AI 前置閘門）觸發詞，移除單獨出現的裸詞（`$`、`元`、`客服`、`投資`、`領取`、`扣款`、`中獎`），改要求複合詐騙操作訊號；AI 風險分析 prompt 增補良性通知負面範例與具體證據要求；誤判回饋同步寫回 30 天發送者信譽與 `EncryptedByokStore` 加密儲存之良性網域 LRU（上限 100 筆）。
- **更新提醒移除每日 gate 改 snooze 狀態機（#374 / PR #383）**：`AppUpdateHelper` 與 `MainActivity` 移除舊版 `version|day` 每日僅提示一次的 gate，改為 `update_snooze_until_ms` 狀態機——只要開啟 App、網路可用、GitHub 有新版本且未處於 snooze 期間，必定顯示更新對話框與通知；僅點擊「稍後更新」才會設定 snooze，直接關閉或滑掉不設 snooze；單一進程檢查失敗最多自動重試一次（無網路時不消耗進程檢查旗標）。
- **資料庫非破壞升級至 version 22（#378、#379 / PR #383）**：`AppDatabase` 由 v21 經單一非破壞性 `MIGRATION_21_22`（純 `CREATE TABLE IF NOT EXISTS` 與索引）升級至 v22，新增 `diary_days`、`goals` 與 `goal_progress` 三張資料表，既有資料 100% 保留。
- **新增 Health Connect 穩定版依賴（#381 / PR #383）**：`app/build.gradle.kts` 新增 `androidx.health.connect:connect-client:1.1.0` 依賴，並於 `AndroidManifest.xml` 宣告健康資料讀取權限與權限說明 Activity intent-filter / activity-alias。

### 變更與修正
- **v3.4.x 全功能健檢與多廠牌相容修復（#382 / PR #383）**：
  - 補齊 `AndroidManifest.xml` 中 Health Connect 於 Android 13（`androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`）與 Android 14+（`android.intent.action.VIEW_PERMISSION_USAGE` + `HEALTH_PERMISSIONS` category 之 `activity-alias`）權限說明入口，解決實機點擊授權無反應問題。
  - 修復 `BootReceiver` 於每次重開機或應用更新時對所有歷史逾期待辦無條件呼叫 `deliverTodoReminder` 造成重複通知轟炸的 bug，改為限制 24 小時逾期窗口內且加上每日里程碑去重 key（`boot_todo:<id>:<dayKey>`）。
  - 完成跨頁狀態隔離（`ChatScreen` 確認卡綁定 `sessionId`）、四廠牌受限設定導引、深淺色／`fontScale = 1.3`／窄螢幕防裁切與安全常數清點審計。

## [3.4.0] - 2026-11-21

### 新增功能
- **主動日曆情報引擎（`CalendarInsightEngine`）（#365 / PR #369）**：新增 `CalendarInsightWorker`（`PeriodicWork` 預設 60 分鐘，支援 30／60／120／180 分鐘 `ExistingPeriodicWorkPolicy.UPDATE` 切換，開機於 `BootReceiver` 重排），僅讀取使用者已勾選之本機日曆未來 48 小時事件，外部查詢嚴格限「事件標題＋日期（≤60 字、`sanitizeForPrompt`）」（嚴禁參與人、備註、位置），以 `eventId|beginMs|intelHash` 去重（無新情報零 AI 呼叫）、未設 BYOK 外部搜尋或省 token 模式自動跳過，並遵守每日主動上限與獨立勿擾時段；新增 `proactive_calendar_insight_enabled`、`proactive_calendar_insight_notif_enabled`、`proactive_calendar_web_search_enabled`、`calendar_insight_interval_minutes` 並接通 `SettingsCatalog` 與 `AssistantHubActivity`。
- **主動訊息聊天室、對話自動記憶擷取、未決事項膠囊與壓縮錨點（#366 / PR #369）**：
  - `ProactiveEngine` 建立專屬主動訊息聊天室（`PROACTIVE_SESSION_TITLE = "AI 助手簡報與主動提醒"`），不再誤寫入一般對話第一筆 session；聊天室抽屜預設收合並顯示即時未讀徽章（超過 99 顯示 `99+`，點開即清零），「清除全部對話」保護主動訊息聊天室不被誤刪；跨分頁系統事件可經 `appendSystemEventToCurrentSession` 零 AI 寫入。
  - 新增對話自動記憶擷取（`memory_auto_extract_enabled`，預設開啟）：每則對話至多直接寫入 2 筆非敏感無衝突記憶並於回覆氣泡下顯示小字提醒與一鍵刪除 BottomSheet；`🔒 敏感` 記憶與衝突項目仍 100% 維持確認卡。
  - 新增單一對話框連續性元件（`chat_pending_capsules_enabled`，預設開啟）：頂部顯示至多 3 顆可滑除未決事項快速膠囊與本地壓縮接續錨點（7 天自動過期、可手動關閉）。
- **雙門檻自動決策層（`DecisionEngine`）與一鍵誤判回饋（#367 / PR #369）**：
  - 新增 `DecisionEngine`（純函式、零 AI token）：確定性訊號優先（白名單、過濾規則、記事規則、熟人／VIP、結構化時間／OTP／URL／金額、已知頭像），無本地風險訊號時 AI 語義單獨給分上限為 `confirmThreshold - 10`（可於 `decision_confirm_threshold` 調整，預設 60、範圍 40–85），且絕不單獨觸發高置信詐騙彈窗或不可逆動作。
  - 集中定義可逆（自動執行）／不可逆（必經確認或開關）動作矩陣，並統一輸出 provenance 標記；`MessageDetailActivity`、`ScamCenterActivity` 與 `Components.kt` 誤判回饋對話框支援「此判斷有誤」四類回饋直接寫回規則庫並立即失效相關快取。
- **定時任務模板、宣告式 Action Registry 與狀態修復（#368 / PR #369）**：
  - Room 資料庫由 v20 非破壞性升級至 v21（`MIGRATION_20_21`），新增 `task_template` 資料表與索引；新增 `TaskTemplateEngine` 將純本地零 token 聚合段與選配 AI 潤飾段分離，並於 `AssistantHubActivity` 提供三步嚮導與即時「所見即所得」預覽。
  - 新增 `LinkedAppActionRegistry` 單一來源宣告式登錄檔同時驅動 `LinkedAppsActivity` 能力卡與 `ChatScreenParts` AI 能力宣告（含 `discord_digest_stats_readonly` 唯讀示範連接器）；補齊 `GitHubMonitorWorker` 同步時間與異常狀態持久化寫入（不再靜默吞掉狀態）。

### 變更與修正
- **白名單全量比對、統一去重寫入與一次性清理（#362 / PR #369）**：`AiRuleDao.activeWhitelistAll()` 解除 `LIMIT 20` 截斷，修正第 21 筆以後白名單靜默失效；`WhitelistRuleHelper.upsertWhitelist` 統一 6 處寫入路徑進行去重與停用規則重新啟用；`MessageDetailActivity`、`PendingAiAnalysisWorker` 與 `NotificationCollectorService` 深度分析重查 DB 白名單並標註手動觸發 provenance；`NotificationCollectorService.dedupWhitelistRulesIfNeeded` 執行一次性精確重複列合併（保留最舊一筆，不改動停用狀態）。
- **記憶頁篩選 chips 遮擋與大字體適配修復（#363 / PR #369）**：`MemoryTab` 展開模式將批次管理與收合鈕移至獨立頂部控制列，兩排分層 `LazyRow` 獨佔全寬並加上 `clipToBounds()` 與 trailing `contentPadding`；收合模式保留 `8.dp` 明確間距；所有 chip 與膠囊統一採用 `heightIn(min = 34.dp)` 支援 `fontScale = 1.3` 與 `360dp` 窄螢幕不截字、不重疊。
- **AI 工具調用精度、防幻覺鐵律與本地直連查詢（#364 / PR #369）**：`ChatScreenParts` 注入系統能力宣告與防幻覺約束，實作單意圖單卡優先序（`calendar_free_query > usage_query > search_messages > memory_list > todo_list`）與次要查詢誠實提示；新增 `LocalDataConnector` 直連本機日曆與 `UsageStatsBridge`；實作 `usage_query` 查詢系統 `UsageStatsManager` 真實前景時間與啟動次數（未授權誠實回報並引導至授權頁），同步修復 `PermissionHubActivity` 使用情況權限狀態與跳轉。

## [3.3.0] - 2026-11-21

### 新增功能
- **記憶寫入確認開關（#347 / PR #359）**：新增 `memory_write_confirm`（預設開啟），可選擇關閉讓一般 AI 記憶直接寫入；`🔒 敏感` 記憶、衝突紀錄與刪除動作仍一律必經確認卡。
- **日曆寫入確認開關與設定清單收合（#348 / PR #359）**：新增 `calendar_write_confirm`（預設開啟），關閉時仍執行撞期檢查，無撞期且已授權即直接寫入已勾選日曆；設定頁本機日曆清單改為收合顯示（顯示已勾選 `N/M` 概覽）。
- **統計頁「早晚簡報卡」（#349 / PR #359）**：統計頁新增「早晚簡報卡」顯示最近一次早晚簡報，支援展開閱讀完整內容與複製；舊統計卡偏好 `stats_summary` 自動遷移為 `stats_briefing`。
- **親密度詳情頁（#350 / PR #359）**：夥伴頁親密度卡可點擊開啟詳情（四階段旅程、各積分來源明細），公式常數收斂為單一來源。
- **設定頁樞紐卡整併（#354 / PR #359）**：將「權限授予中心」與「AI 夥伴與主動推播」合併為「權限與 AI 夥伴」收合卡（收合時顯示已授予權數 `N/7` 與早晚簡報狀態），舊深連結自動轉址。
- **記憶頁與夥伴頁介面重排版（#357 / PR #359）**：`MemoryTab` 頂部新增四格圖像化記憶統計總覽（長期／短期／衝突／免疫，點擊直達篩選）與「記憶／規則」分層篩選列；`PartnerTab` 頭像卡改為膠囊徽章、統一卡片圓角與近 7 天橫向事件卡等高雙行視覺化。

### 行為變更
- **每日 AI 摘要功能整體移除（#349 / PR #359）**：排程、完成推播、專用模型用途（`AiPurpose.DAILY_SUMMARY`）與相關設定項（`ai_daily_summary`、`ai_daily_summary_notif`、`ai_model_daily_summary`、統計卡 `stats_summary`）全部移除；每週摘要不受影響，每週摘要通知改由省 token 模式控管；既有 `ai_summary` 資料庫資料保留不動。
- **簡報點閱計分改以 `messageId` 去重（#349 / PR #359）**：同則簡報僅計分一次，聊天閱讀、統計頁展開與通知點擊三路徑共用去重機制。
- **AndroidManifest 新增 `PACKAGE_USAGE_STATS` 權限（#346 / PR #359）**：宣告使用情況存取權限（`tools:ignore="ProtectedPermissions"`），供權限授予中心檢查與引導顯示。

### 變更與修正
- **用途模型動態計數與過期文案修正（#345 / PR #359）**：用途模型卡分類數改為動態計數，並修正省 token 模式與多處設定說明文案。
- **夥伴頁移除重複快速切換（#351 / PR #359）**：夥伴頁移除個性／詳細度快速切換，保留直達設定頁入口。
- **主動通知授權前檢與引導（#352 / PR #359）**：主動通知發送前檢查系統通知授權；未授權時於設定樞紐卡與 AI 夥伴設定頁顯示警告與直達系統通知設定引導。
- **Discord 雲端警示 payload 與 Log 安全強化（#353 / PR #359）**：Discord Webhook 改以 Gson `JsonObject` 組裝 payload（修正手動跳脫造成無效 JSON 的風險）並補齊授權前檢；Log 輸出移除任何回應內容。
- **新手教學與設定教學重編（#355 / PR #359）**：對齊主頁 20 步與設定教學 9 步文案及新資訊架構（深連結正規化），並全面白話化 `SettingsCatalog` 開發術語。
- **深淺色對比度稽核修正（#356 / PR #359）**：`PhantomGlassCard` 統一提供主題與 `CardBgMode.DARK` 感知的 `LocalContentColor`，多個設定／樞紐頁補齊 `Scaffold.contentColor` 與 `TopAppBar` 標題／返回鈕對比色。
- **整合二檢與小型瑕疵修正（#358 / PR #359）**：修正 `MemoryTab` 局部 `data class` 提升至檔案頂層、`ProactiveEngine` 補齊非機密異常日誌，並完成 `SettingsCatalog` 一致性與 OEM 相容性驗證。

## [3.2.0] - 2026-11-20

### 行為變更
- **行程類通知預設改為只建立日曆建議卡（#333 / PR #342）**：`NotificationCollectorService.saveAutoMemoIfPresent` 新增前置行程分流，無顯式 `ACTION_TODO`／`ACTION_NOTE` 規則覆寫、具備未來時間、屬行程類且日曆已啟用時，預設（`schedule_memo_route = "suggest_calendar"`）僅建立日曆建議卡並直接返回，不再重複建立待辦／記事或排程提醒鬧鐘；可於設定頁「行程類通知分流」（`schedule_memo_route`）切回舊行為 `legacy_todo_note`。
- **新增 `ACCESS_COARSE_LOCATION` 選配宣告（#337、#339、#340 / PR #342）**：於 `AndroidManifest.xml` 宣告 `ACCESS_COARSE_LOCATION`（純選配，僅在使用者於「AI 夥伴與主動推播」點擊取得地區或於「連結應用程式中心」授權時一次性取得粗略位置進行地區反解，不背景持續追蹤）。
- **資料庫升級至 version 20（#336 / PR #342）**：Room 資料庫由 v19 非破壞性升級至 v20（`MIGRATION_19_20`），新增 `contact_aliases` 資料表與 `(alias, packageName)` 索引以儲存通訊軟體暱稱與本名綁定，完整保留既有資料。

### 新增
- **記憶 × 防詐可視化補全（#335 / PR #342）**：`MemoryTab` 新增「防詐免疫」篩選 chip 與獨立可收合專區，可透過 `MemoryEngine.extractRelatedMessageId` 解析來源並顯示「查看來源訊息 #ID →」直達 `MessageDetailActivity`；`ChatScreen` 寫入／更新 `scam_immunity` 記憶後於 Snackbar 提供「查看」按鈕，一鍵切換至記憶分頁並預選「防詐免疫」篩選。
- **通訊錄整合（#336 / PR #342）**：新增 `ContactBridge`（`ContactsContract.Data` MIMETYPE 查詢、零 token 關係單位匯入嚮導含 diff 去重、VIP 信任圈同步至 `AiRuleEntity` 白名單並以 `[contacts_vip] ` 前綴標記且支援整批撤銷、10 分鐘節流 `ContentObserver`）；`ContactContext.evaluateContradictionSignals` 升級比對 `contact_aliases` 視同熟人（輸出維持匿名結論，原始通訊錄絕不外送）；聊天頁支援純本機暱稱綁定確認卡。
- **主動式推播與個人簡報引擎（#337 / PR #342）**：新增 `BriefingScheduler`（早 08:45／晚 22:25，`setAlarmClock` 主路徑＋`setExactAndAllowWhileIdle`→`setAndAllowWhileIdle` fallback、`BootReceiver` 與 `BriefingAlarmReceiver` 監聽 `TIME_SET`／`TIMEZONE_CHANGED` 重排、錯過 `<2h` 補發）、`ProactiveEngine`＋`ProactiveMonitorWorker`（15 分鐘零 token SHA-256 指紋偵測、無變動零 AI 呼叫、整合行程待辦／撞期附空檔／興趣地區／選配 BYOK 外部搜尋端點、寫入 AI 助手聊天＋`assistant_briefing` 通知頻道直達 `PendingIntent.getActivity`，支援獨立勿擾、每日上限預設 6 則與省 token 連動）、`AiPurpose.BRIEFING`、`EncryptedByokStore` 加密金鑰儲存，以及共用 `PortraitCardSection`（「我對你的瞭解」畫像卡 ≤200 tokens，7 天或 +20 確認記憶觸發、雙重硬排除敏感記憶、注入優先序改畫像卡先行＋`topK` 減半）。
- **AI 夥伴分頁（#338 / PR #342）**：`MainTab` 最右新增第 6 分頁 `PARTNER`，`LiquidGlassTabBar` 三模式同步擴充至 6 分頁並支援助手自訂頭像縮圖與 `fontScale 1.3` 省略保護，標籤顯示使用者自訂助手名稱（未自訂 fallback「夥伴」，上限 6 字元）；新增 `PartnerTab` 呈現大頭像、陪伴天數、親密度公式（`已確認記憶×2 + 聊天互動×1 + 簡報點閱×1`）、近 7 天橫向動態卡、共用 `PortraitCardSection`、主動主題與個性快捷及「開始聊天」入口。
- **連結應用程式中心與「確認後執行」跨 App 動作卡（#339 / PR #342）**：新增 `LinkedAppsActivity`（鬧鐘計時器即用、6 項本機能力卡於 `onResume` 即時重查與快捷、GitHub PAT 15 分鐘輪詢 `GitHubMonitorWorker` 追蹤 Build 失敗／PR 與 Discord Webhook 遮罩輸入＋測試鈕，金鑰全部經 `EncryptedByokStore` 加密儲存）；新增 `CrossAppActionExecutor` 與聊天動作解析（`gmail_compose`／`maps_navigate`／`set_alarm`／`open_app`），一律先顯示確認卡並由使用者點擊「執行」才呼叫 `startActivity`（無 `EXTRA_SKIP_UI`、失敗 fallback chooser／toast）。
- **設定重整：權限授予中心與 AI 夥伴設定樞紐（#340 / PR #342）**：新增 `PermissionHubActivity`（集中管理通知、SMS、通訊錄、日曆、精確鬧鐘、電池優化與使用情況 7 項權限，Shizuku 保留於主設定頁獨立卡片，沿用 `restrictedSettingsOemNote()` 四廠牌安全 fallback）與 `AssistantHubActivity`（集中收納早晚簡報、主動推播、畫像卡、外部搜尋 BYOK、通訊錄整合與連結應用入口）；主設定頁僅新增兩張樞紐卡並同步 `SettingsCatalog.ITEMS` 與聊天 `open_setting` 路由。

### 變更與修正
- **v3.0.0–v3.1.0 全功能健檢與修復（#334 / PR #342）**：修正 `CalendarBridge.queryFreeSlotsSummary` 對 `ALL_DAY` 事件未以 UTC `LocalDate` 邊界對齊與 ±1 日視窗導致 UTC+8 跨日誤判空檔的問題；修正 `ChatScreen` 的 `memory_view`／`memory_list` 關鍵字比對曾涵蓋敏感記憶 `content` 欄位，避免側錄探測。
- **新手教學 20 步與設定教學 10 步重寫（#341 / PR #342）**：主頁教學重寫為 20 步（涵蓋 `MEMORY`、`PARTNER` 分頁、防詐免疫、通訊錄綁定、日曆建議卡、早晚簡報與權限授予中心，保留 400ms 雙條件 auto-skip 與空資料庫合併步）；設定教學擴充為 10 步（新增「權限授予中心」與「AI 夥伴與主動推播」兩步，同步 `SETTINGS_CARD_ORDER` 與 `BringIntoViewRequester`＋`withFrameNanos`）。

## [3.1.0] - 2026-11-20

### 新增
- **記憶 × 防詐交叉檢查與防詐免疫（#328 / PR #330）**：
  - 新增 `ContactContext`（L1 離線通訊錄比對層）：讀取 `READ_CONTACTS` runtime 權限（權限拒絕時自動降級純記憶比對、不閃退），以 `LruCache`（30 分鐘 TTL）快取聯絡人顯示名稱，交叉比對通訊錄與 `person` 記憶（慣用 App、深夜／凌晨時段、陌生帳號自稱熟人＋急迫語氣），僅輸出匿名結論字串，原始通訊錄絕不外送。
  - `MemoryEngine.buildAntiFraudPromptBlock` 整合情境矛盾交叉檢查（`schedule`／`routine`／`preference` 類 `normal` 記憶注入風險分析）與 `scam_immunity` 檢索優先加權，並持續雙重硬排除 `privacyTier = sensitive`。
  - 新增 `scam_immunity`（「防詐免疫」）記憶分類：警示中心 `ScamDetailDialog` 新增「🛡️ 加入防詐免疫記憶」一鍵確認卡，並新增每週 `ScamImmunitySummaryWorker`（`ScamImmunityPolicy`）掃描近 30 天高風險紀錄產出待確認草稿，於記憶頁經確認卡核准才寫入。
  - `AiClient.analyzeMessage` 分析快取鍵納入記憶／通訊錄版本戳記（`getAntiFraudCacheVersion`），避免新免疫記憶或權限變更被舊快取遮蔽；設定頁新增「記憶 × 防詐交叉檢查」總開關（預設開啟；關閉時 prompt、快取鍵與 token 用量與 v3.0.0 完全一致）。
- **本機日曆整合（#329 / PR #330）**：
  - 新增 `CalendarBridge`（純 `ContentResolver` 於 `Dispatchers.IO` 執行，零第三方依賴）：支援 `READ_CALENDAR`／`WRITE_CALENDAR` runtime 權限、設定頁勾選開放日曆清單（預設全不勾、標示可讀寫狀態），以及 `Instances` 空檔查詢（扣除既有行程後 ≥30 分鐘）與重疊撞期檢查。
  - AI 助手僅於已授權且已勾選日曆時注入 `calendar_free_query`／`calendar_add` 指令：查詢本機空檔直接於本地計算並如實呈現；新增行程一律彈出確認卡顯示目標可寫日曆、時間與撞期警示（警示不阻擋）。
  - `NotificationCollectorService` 偵測行程類通知且時間解析成功、勾選日曆無同名同時段事件時，經 `LruCache` 節流於 `MessageDetailActivity` 顯示「📅 加入日曆」可編輯建議卡（含撞期檢查）。
  - 無寫入權限或無可寫日曆時，提供 `Intent(Intent.ACTION_INSERT)` 預填系統日曆 App fallback。

### 變更與修正
- **AI 回應相容修正與思考鏈折疊顯示（#326 / PR #330）**：
  - `AiClient` 改以 `ResponseMessage`（`JsonElement`）＋ `extractJsonTextValue` 解析多型回應 `content`／`reasoning`／`reasoning_content`，全面相容陣列 chunk 清單、物件與純字串格式，修正 `zai-glm-5-3` 等模型回傳陣列導致 `BEGIN_ARRAY` 崩潰的問題。
  - `ChatScreen` 支援思考鏈模型回應，於氣泡上方新增預設收合的「推演思路」折疊區塊（展開顯示完整思考過程，正式回覆無殘片，長按複製維持純文字，思考鏈不回放至 API）。
  - 移除 `isVisionModelSupported` 與圖片視覺誤判閘門，附加圖片一律送出，不支援時如實回報 provider 錯誤。
  - Room 資料庫升版 v18 → v19（`MIGRATION_18_19`），以 `ALTER TABLE` 非破壞性新增 `chat_messages.thinkingContent` 欄位，完整保留歷史對話。
- **記憶頁 UI 優化（#327 / PR #330）**：
  - 9 顆 `MemoryFilterChip` 篩選標籤列支援收合／展開並持久化狀態（`PhantomPrefs.isMemoryFilterChipsCollapsed`），收合時僅保留「全部」與目前生效條件，並對齊訊息頁 34dp 圓形箭頭按鈕與膠囊樣式。
  - 頂部搜尋框改由 48dp 搜尋按鈕觸發展開（`AnimatedVisibility`＋`FocusRequester` 自動聚焦），支援有字維持展開、清空失焦收合與 `BackHandler` 先清字再收合，保留「搜尋→全選→批次刪除」完整流程。
  - 統一記憶頁頂部列（標題、搜尋鈕、新增、齒輪）排版與 48dp 觸控目標。

## [3.0.0] - 2026-11-20

### 新增
- **AI 記憶庫核心引擎（#314 / PR #319）**：
  - Room 資料庫升級 v17 → v18（`MIGRATION_17_18`），新增 `memories` 資料表（含 `category`、`subject`、`content`、`layer`、`status`、`confidence`、`sourceType`／`sourceRef`、`privacyTier`、`createdAt`／`updatedAt`、`lastUsedAt`／`useCount` 等欄位與 `(status,layer)`、`(category,subject)`、`(updatedAt)`、`(privacyTier)` 四組索引）。
  - 新增 `MemoryEngine`：同 `subject` 去重與衝突偵測（`status = conflict` 雙筆保留、絕不覆蓋），`resolveConflictGroup` 裁定（勝出筆設為 `active + confirmed`，落敗筆設為 `archived`）；短期記憶 `expiresAt` 預設 7 天，啟動／查詢時 `lazyArchiveExpiredShortTerm` 與每日 `MemoryExpiryWorker` 過期封存。
  - 純本地確定性 Top-K 檢索（0 額外 API 呼叫），依關鍵字、分類意圖與時間條件計分排序，注入 `buildRagSystemPrompt` 的「【長期記憶】」區塊（套用 `AiClient.sanitizeForPrompt` 防注入），命中後更新 `lastUsedAt` 與 `useCount`。
  - `privacyTier = sensitive` 雙重硬性排除閘門（SQL `privacyTier = 'normal'` ＋ Kotlin `.filter { it.privacyTier != PRIVACY_SENSITIVE }`）。
  - `MemoryEngine.loadIntegratedSnapshot` 整合查詢既有備忘錄（`notes (source=auto)`）、`ai_rules`（白名單／標籤）、`user_tag_rules` 與 `memo_rules`，提供統一查詢、啟停與刪除介面（連動 `NotificationCollectorService.invalidateRuleCaches()`，零資料表遷移）。
- **第 5 個分頁「記憶」（#315 / PR #319）**：`MainTab.MEMORY("記憶")` 接入 `LiquidGlassTabBar`（標準／縮小／自動隱藏三模式）、`saveableStateHolder` 與 `PhantomBackgroundBox(layer = GLOBAL)`，完整 `MemoryTab` UI：頂部搜尋列、9 顆篩選 chips（`FlowRow` 自適應大字體與窄螢幕）、8 個可收合區塊（⚠️ 衝突待確認、長期記憶、短期記憶、已封存記憶、自動備忘錄、白名單規則、標籤規則、自動記事／待辦規則）、來源與徽章顯示（`AI 推測` / `已確認` / `🔒 敏感` / 到期時間）、雙按鈕衝突裁定、完整新增／編輯對話框、左滑封存／恢復／刪除、多選批次刪除確認對話框，以及右上角設定齒輪直達設定頁「記憶庫與 AI 助手」卡片。
- **自訂 AI 助手（#315 / PR #319）**：支援自訂名字（預設「幻訊」）、個性 preset（預設／簡潔專業／親切詳細／嚴謹防詐顧問／完全自訂）＋自訂個性欄位、回答詳細度（精簡／適中／詳細），注入【助手設定】嚴格管控 ≤120 tokens，並於 `ChatScreen` 頂列與助手回覆氣泡即時顯示圓形頭像與名字。
- **設定頁新增分類「記憶庫與 AI 助手」（#315 / PR #319）**：可收合卡 `card_memory_assistant`（含記憶擷取、雙模式獨立注入滑桿、自訂助手與圓形頭像裁切、隱私分級說明），並於 `SettingsCatalog` 註冊 `memory_chat_capture`、`memory_normal_top_k`、`memory_normal_per_item_chars`、`memory_saver_top_k`、`assistant_custom_name`、`assistant_personality`、`assistant_verbosity`、`assistant_avatar`、`memory_privacy_tier_info` 共 9 項設定，支援本地搜尋、AI 語意搜尋與聊天 `setting_toggle` / `setting_adjust` / `open_setting`。

### 變更與修正

- **省 token 快捷開關（#315 / PR #319）**：AI 助手頁頂列新增省 token 快捷開關 chip 與 `Info` 資訊按鈕，透過 `PhantomSettingsBus` 與設定頁 100% 雙向即時同步；彈窗逐條對照 `PhantomPrefs.isEffective*` 實際行為。
- **Safe Browsing key 改 BYOK（#316 / PR #318，行為變更）**：移除 `BuildConfig.SAFE_BROWSING_API_KEY`／`local.properties` fallback 路徑，統一僅設定頁 `EncryptedSharedPreferences` 輸入；未設定時誠實跳過該源且 UI 提示一致，release 建置後已無 `SAFE_BROWSING_API_KEY` 欄位殘留。
- **聊天時間格式固定 Locale.US（#316 / PR #320）**：`CHAT_TIME_FORMATTER` 以 `DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US)` 建立，zh-TW 顯示逐字不變，其他系統地區不再出現非 ASCII 數字漂移。
- **教學 auto-skip 雙條件（#316 / PR #320）**：新手教學 auto-skip 延遲 400ms 後仍需 bounds 已量測且目標仍缺席，才自動跳步，避免極慢機型在分頁切換進行中誤跳。
- **Safe Browsing 卡過時文案移除（#316 / PR #320）**：「Key 狀態：已於 App 加密儲存（優先於 BuildConfig）」改為「Key 狀態：已於 App 加密儲存」，並移除不可達的「Key 狀態：已從 local.properties 注入」分支（`getSafeBrowsingKeySource` 僅回傳 EncryptedPrefs／NONE）。

## [2.20.0] - 2026-11-20

### 修正
- **記事頁待辦事項展開區可捲動與高度上限（#310 / PR #311）**：
  - `NotesTab` 待辦事項展開區（`!todoSectionCollapsed.value`）外層改為 `Column(Modifier.heightIn(max = maxTodoExpandedHeight))`，上限取自 `BoxWithConstraints` 可用高度之 2/3（`maxHeight * 2f / 3f`），避免清單過長時撐高卡片擠壓下方記事區。
  - 篩選膠囊列 `FlowRow` 與 `8.dp` `Spacer` 固定於外層 `Column` 頂部，不隨清單捲動；清單段改為 `Column(Modifier.weight(1f, fill = false).verticalScroll(todoListScrollState))` 支援卡內上下捲動，並以 `key(todoFilter) { rememberScrollState() }` 於切換篩選條件時重置捲動位置至頂部。
  - 短清單或空清單維持依內容 wrap-content 高度呈現，不強制填滿 2/3；收合精簡顯示（`todoSectionCollapsed = true`）與收合狀態持久化維持不變。

## [2.19.0] - 2026-11-20

### 新增
- **儲存空間明細與資料庫壓縮維護（#299 / PR #303）**：
  - 設定頁「資料管理」新增唯讀儲存空間明細（顯示資料庫含 WAL／SHM、背景圖片、快取與合計大小）與「立即壓縮資料庫」按鈕。
  - 新增 `DbMaintenance` 與 `VacuumWorker` 執行 `wal_checkpoint(TRUNCATE)` 與交易外 `VACUUM`（`RetentionWorker` 刪除超過 1000 列且 24 小時節流自動觸發，手動清除已讀／刪除全部亦非同步排程）。

### 變更與修正
- **中央堆疊 v6 首尾邊界填滿與短清單模式（#298 / PR #302）**：
  - `CenterFocusCardStack` 新增首尾邊界填滿補償（`pAbove`／`pBelow` 對稱等效 fill pitch，上限 `0.98 * cardH`），頭尾定點零留白且全幅模式整組展開滿高。
  - `edgeGap` 由 `4.dp` 調整為 `6.dp`（預設值與訊息頁／記事頁兩處呼叫點同步），首卡距我的最愛與尾卡距底部導覽列皆為 `6.dp`。
  - 新增短清單置頂模式（`neededPitch > maxPitch`，約 2–4 則時固定於 `topAnchor` 緊貼標題列下 `6.dp` 堆疊；單筆維持單卡路徑）。
- **空間優化與資料庫索引（#299 / PR #303）**：
  - `release` 建置類型啟用 R8 程式碼縮減（`isMinifyEnabled = true`）與資源壓縮（`isShrinkResources = true`），並於 `proguard-rules.pro` 保留 Gson 序列化模型與 Shizuku 跨行程介面。
  - Room 資料庫升版至 v17（`MIGRATION_16_17`），補齊 `messages(isOwn, timestamp)`、`messages(isOwn, isRead)`、`todos(relatedMessageId)`、`sender_avatars(updatedAt)` 與 `intel_cache(checkedAt)` 索引。
  - `sender_avatars` 頭像快取上限設為 300 筆（保留最新 `updatedAt`），`intel_cache` 72 小時過期列隨 `RetentionWorker` 定期清除。
- **電量與穩定性優化（#300 / PR #304）**：
  - `KeepAlivePolicy` watchdog 間隔放寬為 15→45 分鐘，`ShizukuWatchdogService` 穩態輪詢放寬為 300→600 秒（8 小時安全網、1 秒 rescue、5 秒 rebind 維持不變）。
  - `HeartbeatPolicy` 平時改採 `ExistingWorkPolicy.KEEP` 消除重排抖動，模式切換時以 `UPDATE` 切換 15/30 分鐘；關閉常駐統計通知時仍保留 30 分鐘低頻通知監聽重連檢查（不啟動前景服務與精確鬧鐘）。
  - `StatsForegroundService` 補齊 `startForegroundCompat` 三層例外防護（失敗即 `stopSelf()`）、rescue 指數退避（60 秒窗口第 3 次內 1 秒重試，超過改 30 秒起加倍封頂 5 分鐘），並於熄屏且未逾 staleness 時在 DB 查詢前早退（亮屏由 `ScreenStateReceiver` 補齊）。
  - 熱路徑 Regex 與時間格式常數化：`AiClient`、`NotificationCollectorService`、`ChatScreen` 預編譯 Regex 與 `DateTimeFormatter`，`PhantomPrefs.formatMessageTime` 改用 `ThreadLocal` 快取。
- **主畫面與對話介面元件拆分（#301 / PR #305）**：
  - 將 `MainActivity` 與 `ChatScreen` 拆分為獨立分頁／元件檔案（`MessagesTab.kt`、`NotesTab.kt`、`StatsTab.kt`、`TodoSection.kt`、`ChatScreenParts.kt`），純架構整理、零行為變更。
- **立即壓縮資料庫等待邏輯修正（#306 / PR #307）**：
  - 修正 `DbMaintenance` 於背景 `VACUUM` 任務已排隊時點擊「立即壓縮資料庫」的等待判斷，避免長時間等待或卡住。

## [2.18.0] - 2026-11-19

### 修正
- **移除內建個人化預設與設定頁衝突文案（#292 / PR #293）**：
  - `PhantomPrefs.DEFAULT_MY_DISPLAY_NAMES` 改為空集合，未設定時不預帶任何預設顯示名稱；`isMyDisplayName` 與自我身分偵測於空集合時不再誤判。
  - 移除 `NotificationCollectorService` 內建個人化 AI 規則自動寫入（`seedDefaultAiRulesIfNeeded`），並新增一次性清除機制 `purgePresetAiRulesIfNeeded`（以 `preset_ai_rules_purged_v1` 標記僅刪除舊版自動寫入的兩條預設規則，保留使用者自建規則，資料庫 schema 不變）；同時將 AI prompt 與管理頁範例文案去品牌化。
  - 快速設定「AI 風險分析」副標改為動態引用 `aiMinTriggerLength` 字數門檻，不再寫死 10 字元。
  - 「每日 AI 額度上限」標題與 `SettingsCatalog.NUMERIC_SPECS` 單位統一為「次」，chip 支援顯示並選中非預設值（0–100 次、步進 10 次）。
  - 省 token 模式生效時，「每則訊息 AI 智慧掃描」與「每日 AI 摘要」副標顯示「已暫停」，「AI 風險分析」副標提示「僅命中可疑特徵才分析」。

## [2.17.0] - 2026-11-19

### 新增
- **隱私權政策與免責聲明文件（#284 / PR #286）**：新增 [PRIVACY.md](PRIVACY.md) 隱私權政策與 [DISCLAIMER.md](DISCLAIMER.md) 免責聲明（純文件新增，App 功能無變動）。
- **GPL-3.0 開源授權文件（#285 / PR #287）**：新增根目錄 [LICENSE](LICENSE)（GNU GPL v3.0 官方全文）並於 README 補齊「授權」說明段落（純文件新增）。

### 變更與修正
- **主頁／設定頁新手教學修復、各廠牌通知存取受限引導與 Shizuku 一鍵授權（#288 / PR #289）**：
  - Android 13 以上首次啟動時，新手教學會等候系統通知權限彈窗結束後才顯示，不再與系統視窗重疊。
  - 權限步驟高亮「尚未開啟通知存取權限」引導卡，並新增可展開的「開關變灰／無法開啟？」疑難排解，提供小米／Redmi／POCO、Samsung（自動封鎖程式）、OPPO／realme／OnePlus、vivo／iQOO 四廠牌專屬「允許受限制的設定」解鎖說明與「前往應用程式資訊」按鈕。
  - 資料庫尚無訊息時，教學的四個訊息操作步驟合併為單一提示步，不再連續無亮區空轉。
  - 教學依步驟自動切換分頁（AI 助手／記事／統計／訊息），並新增設定齒輪、自動記事規則與新增待辦的獨立高亮目標。
  - 搜尋把手步驟改為置中膠囊形高亮，指引位置更加明顯可見。
  - 設定頁教學修復為完整 8 步導覽：導覽期間自動清空搜尋列，並正確展開個人化與 Shizuku 卡片、捲動定位白名單卡片，索引不再錯位。
  - 「前往開啟通知存取權限」升級為直達本 App 專屬通知存取詳情頁（Android 11 以上支援；不支援的系統自動改開總清單頁）。
  - Shizuku 已連線時，可點擊「Shizuku 一鍵授權」直接授予通知存取權限並即時刷新狀態。

## [2.16.0] - 2026-11-19

### 變更與修正
- **中央堆疊 v5 微調（#280 / PR #281）**：
  - 訊息／記事堆疊上下留白緊湊化：`CenterFocusCardStack` 新增 `edgeGap = 4.dp`（取代原 `8.dp`）延伸上下可視範圍；`MessagesHomeTab` 於 `CENTER` 模式外層區塊間距由 `12.dp` 緊湊為 `8.dp`（非 `CENTER` 模式維持 `12.dp`）；記事頁堆疊容器上方 `Spacer` 於 `CENTER` 模式由 `8.dp` 縮為 `4.dp`（`LIST` 模式維持 `8.dp`），Scaffold 共用外層內距、我的最愛列與底部導覽列不變。
  - 阻尼彈簧吸附：改採官方 `snapFlingBehavior`（`SnapLayoutInfoProvider(listState, SnapPosition.Start)` ＋ `rememberSplineBasedDecay` ＋ `spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)`），維持 `s_i = i * exposure` 吸附幾何與互補式 `contentPadding`。
  - 近焦縮放緩衝與焦點陰影漸變：於同一 `graphicsLayer` draw phase 內加入 `FastOutSlowInEasing` 近焦縮放緩衝（`absU <= 1f` 由 `0.94f` 平滑過渡至 `1.0f`，`absU > 1f` 沿 `0.04f` 遞減至下限 `0.82f`，於 `absU = 1` 連續銜接，pivot 維持頂緣 `TransformOrigin(0.5f, 0f)`），並新增 `cardShape = RoundedCornerShape(20.dp)` 與 `focusShadowBoost = 6.dp` 隨 `eased` 漸變陰影；`translationY` 線性鎖定、`alpha` 邊緣淡出與 `zIndex` 連續公式皆維持不變。

## [2.15.0] - 2026-11-19

### 變更與修正
- **中央堆疊 v4 修正（#276 / PR #277）**：
  - 修復滑動時焦點上方卡片被裁切／快甩後缺繪：改採對稱 `contentPadding`（`leading >= 3 * exposure` 且與 `trailing` 互補，總捲動區間 `(n - 1) * 40.dp` 與吸附手感不變），確保焦點上方 2~3 張卡片原生落入 `visibleItemsInfo` 穩定組合繪製、露出頂帶可辨識 App 圖示與名稱；唯一 `clipToBounds` 僅保留於最外層堆疊容器（我的最愛下緣至底部導覽上緣）。
  - 初始貼頂與線性過渡：`firstVisibleItemIndex == 0` 且 `offset == 0` 時錨點 `8.dp` 貼頂零留白；頭尾過渡由 `smoothstep` 改為線性 `lerp`（以 `zone` 正規化），連續單調無布林切換。
  - 記事短清單幾何修正：過渡區寬度改為動態 `zone = min(1.5f, (n - 1) / 2f)`，根治 `n <= 4` 時頭尾過渡區重疊造成第 2 則記事沉底；記事頁續用共用堆疊元件、容器高度由 `BoxWithConstraints` 動態計算。
  - 待辦事項到期副標單行：`NotesTodoRow` 到期資訊 `Text` 新增 `maxLines = 1`、`softWrap = false` 與 `TextOverflow.Ellipsis`，不再被右側按鈕擠成兩行（展開／收合兩狀態同時生效）。

## [2.14.0] - 2026-11-19

### 變更與修正
- **中央堆疊卡片緊湊化（#270 / PR #272）**：
  - 單卡高度鎖定 `92.dp`（`fontScale >= 1.3f` 上限 `104.dp`），內距 `12.dp` 三段式佈局撐滿內容、消除卡片內空黑底。
  - 堆疊錯位與 `edgeFade` 調整為 `40.dp`，縮放每層 `0.04f` 遞減（下限 `0.82f`），底部 `40.dp` 露出帶顯示 App 名稱＋未讀數／記事標籤，訊息頁與記事頁表現完全一致。
- **自動檢查更新改為每次開啟 App 一次（#271 / PR #273）**：
  - 移除每日檢查 gate 與彈窗去重，開啟開關且網路可用時每次開啟 App 檢查 GitHub 最新版本並提示；通知欄通知維持每日每版本最多一則。
  - 以 process 級 `AtomicBoolean` 旗標防重入，同一行程螢幕旋轉與返回頁面不重複請求 API；無網路、403/429、逾時靜默處理。

## [2.13.0] - 2026-11-19

### 新增
- **訊息／記事中央堆疊 v3（#263 / PR #264）**：
  - 零留白與全卡填滿：採動態層數、`8.dp` 邊距與動態 `contentPadding`（`bottom = viewport - exposure`），確保全項目可達且消除底部留白。
  - 連續慣性滑動：改用 `LazyColumn`＋`LazyListState`（`cacheWindow 1.0/0.5`）＋`SnapPosition.Start` 統一單調吸附，移除一次一張限制，`rememberSnapFlingBehavior` 支援快滑跨多張後平滑吸附。
  - 連續 lerp 過渡與視覺參數：頂／中／底三態錨點 smoothstep 連續插值，根治第 0 張與頭段瞬移；露出 `44.dp`、頂緣 pivot `scaleStep = 0.035f` 遞減，viewport 內維持不透明並於邊緣像素淡出。

### 變更與修正
- **SMS 雙路徑重複記錄修正（PR #268）**：
  - `SmsAutoReplyReceiver` 專職自動回覆並移除 `db.messageDao().insert` 寫入，簡訊記錄統一由通知攔截路徑（`NotificationCollectorService`）單一記錄與 SHA-256 `dedupKey` 去重；時段判斷、敏感關鍵字、`TW_MOBILE_REGEX`、4 小時冷卻與每日 3 封上限等自動回覆行為維持不變（`shouldSkipReply` 提前 return 省去多餘協程排程）。
  - 附註：issue #106 待實機驗證後關閉。

## [2.12.0] - 2026-11-19

### 新增
- **訊息頁中央置頂堆疊重構（#249）**：
  - 重構 `Components.kt` `CenterFocusCardStack`（v2）：固定卡片高度 `cardHeight = 112.dp`（訊息預覽 `maxLines = 1` + `Ellipsis`、App 圖示 `44.dp`、`contentPadding` `12.dp` / compact `8.dp`），`VerticalPager` 改採 `PageSize.Fixed(cardHeight + 24.dp)` 與動態底部 `contentPadding`（確保末卡可滑動至正中央）。
  - 等距堆疊常數：`exposure = 24.dp`、`scaleStep = 0.06f`（第 n 層縮放 `1 - 0.06n`）、`maxVisibleLayers = 4`（螢幕高 `< 640dp` 時為 `3`，超過層數 `alpha` 歸零）。
  - 頂部對齊（`scroll = 0`，第 0 張貼齊頂部 `topGap = 10.dp`、zIndex 最高）與中央對齊（`|scrollOffset| >= 0.5` 完成過渡、焦點卡置中且上下對稱）間以滾動進度 `f ∈ [0, 1]` 連續內插，全數僅經 `graphicsLayer` 計算不觸發重組；`MainActivity` 訊息區套用並保留一般條列、未讀分頁與點擊／長按手勢。
- **記事頁共用中央置頂堆疊並移除「往下露頂部」模式（#250）**：
  - 記事「中央堆疊」改共用新版 `CenterFocusCardStack`：`NoteCardItem` 固定高度 `112.dp`、內文 `maxLines = 2` + `Ellipsis`，將置頂／編輯／刪除改為右上角小圖示按鈕，底部不再露出按鈕排；待辦收合與逾期排序維持不變。
  - 移除 `PhantomPrefs.NoteCardLayoutMode.PEEK_TOP`，`getNoteCardLayoutMode` 讀到舊值 `"peek_top"` 時自動遷移為 `CENTER`；`MainActivity` 刪除撲克牌式分支。
  - `SettingsActivity` 與 `SettingsCatalog` 同步移除「往下露頂部／撲克牌」描述與關鍵字，`home_card_layout` 與 `notes_card_layout` 的 `valueLabels` 統一為 `["一般條列", "中央堆疊"]`（索引 `LIST → 0`、`CENTER → 1`）。
- **AI 自動待辦智慧時間推斷與提前 30 分鐘提醒（#252）**：
  - `AiClient.buildSystemPrompt` 注入系統時區當前時間，要求 AI 從訊息線索（如「明天 9 點」「週三晚」「10/28 前」「下週一到」）推算具體 `yyyy-MM-dd HH:mm` 填入 `todo_due`，無任何線索才留空。
  - `NotificationCollectorService.saveAutoMemoIfPresent` 採 `parseStructuredDueAt(todoDue)`（過濾過去時間）→ `ClientTimeParser.parseNaturalReminderTime` 解析訊息內容 → `msg.timestamp + 2h` fallback 鏈（全程 `try/catch`）；有事件時間時套用 `REMINDER_LEAD_MINUTES = 30` 提前 30 分鐘提醒（若 `<= now` 則 `max(now + 5min, eventTime - 30min)`）。
  - `ClientTimeParser` 補齊跨年 `MM/dd` 進位（如 12/31 收到 `1/2` 落在明年）；duplicate 待辦更新路徑同步套用該規則並在 `dueAt` 變更且 `> now` 時重排 `ReminderScheduler.scheduleTodo`。

### 變更與修正
- **記事置頂／取消置頂不再覆寫 `updatedAt`（#251）**：
  - `MainActivity.onTogglePinNote` 更新 `NoteEntity` 時僅反轉 `pinned` 欄位，保留原 `updatedAt` 顯示時間，並維持 `NotesWidgetProvider.updateAllIfChanged` 同步刷新。
- **深度分析與每日摘要模型切換 chip 分用途綁定並即時同步（#253）**：
  - `MessageDetailActivity` 深度分析 chip 與 `MainActivity` `StatsTab` 每日摘要 chip 改以 `AiClient.getProfileForPurpose`（`DEEP_ANALYSIS` / `DAILY_SUMMARY`）顯示，選擇時寫入 `PhantomPrefs.setPurposeProfileId`＋`PhantomSettingsBus.notifyChanged()`（不再寫入全域 `setActiveProvider`），並隨 `PhantomSettingsBus.version` 與 `ON_RESUME` 重讀；空 key 檢查同步改為 `getProfileForPurpose`。
  - `ChatScreen.AiProviderSwitcherChip` 新增 `label` 參數（預設 `"對話模型："`），深度分析顯示「深度分析模型：」、每日摘要顯示「每日摘要模型：」。
- **每日與每週摘要準時性補強（#254）**：
  - `DailySummaryPolicy` 與 `WeeklySummaryPolicy` 新增 `maybeCatchUp(ctx)`，於 `MainActivity.onCreate`、`BootReceiver` 與 `SettingsActivity` 儲存設定後檢查並立即補跑錯過之當日／當週摘要。
  - `DailySummaryWorker` 失敗時改回傳 `Result.retry()`（`BackoffPolicy.EXPONENTIAL, 10min`，最多 3 次）不覆寫重試排程，成功或達上限後再重排明日。
  - `WeeklySummaryPolicy.generateWeeklySummary` 新增 `anchorMs` 參數，以排程錨定週（`between(start, start + 7d)`）計算區間與 `weekKeyFor(anchorMs)`，避免 Doze 延遲至週一時誤抓新週；constraints 移除 `RequiresBatteryNotLow`、改為 `NetworkType.CONNECTED`。

## [2.11.0] - 2026-11-18

### 新增
- **首頁卡片排列模式重寫為「中央聚焦垂直滾輪堆疊」（#240）**：
  - `PhantomPrefs.HomeCardLayoutMode` 精簡為 `LIST`（一般條列）／`CENTER`（中央堆疊）兩種，舊 `peek_top`／`peek_bottom` 與 legacy `card_stack_effect_enabled` 自動遷移為 `CENTER`；`SettingsActivity` 對應改為兩顆膠囊選項。
  - `Components.kt` 新增 `CenterFocusCardStack`：`VerticalPager` + `PageSize.FillParentSize`（`beyondViewportPageCount = 2`）可滾動分頁容器，每頁以 `graphicsLayer` 依 `pagerState` 頁偏移動態計算 `translationY`（對稱上下錯開）、`scale`（|q|=1 → 0.92f、|q|=2 → 0.84f）、`alpha`（距離 > 2 隱藏）與 `zIndex`（`derivedStateOf` 隨焦點變化），焦點卡恆在容器垂直正中央、鬆手由 Pager 原生 snap 吸附，首末卡皆可捲動至正中央。
  - 堆疊模式卡片以 `PhantomGlassCard(overlapOpaqueBoost = true)` 提升不透明深色底（`#F6161822` ≥ 0.96，亮色主題 surface 0.96）防後卡文字透出；`MessagesHomeTab` 移除壞掉的 peek 模式、負 spacing 與自訂 snap 佈局。
- **記事分頁待辦收合＋智慧排序、記事卡片三種排列模式（#241）**：
  - `PhantomPrefs` 新增 `NoteCardLayoutMode`（`LIST`／`PEEK_TOP`／`CENTER`，key `note_card_layout_mode`）與 `notes_todo_collapsed`（預設收合）；`MainActivity` `NotesTab` 重構為「搜尋列＋可收合待辦卡＋weight(1f) 記事容器」結構。
  - 待辦卡標題列可點擊切換收合（＋ 34dp 箭頭），`AnimatedVisibility` 平滑過渡且狀態持久化；收合時隱藏篩選膠囊列、只顯示距離現在最近且尚未逾期的 1 則未完成待辦（全部逾期時順延最早逾期那則、無未完成時顯示提示文字）。
  - `ReminderScheduler.sortTodosSmart` 重寫：未完成未逾期（dueAt 升冪）→ 已逾期未完成（dueAt 升冪，過期自動下移）→ 已完成（完成時間降冪）；與 AI 助手、桌面小工具共用同一排序。
  - 記事卡片排列模式：一般條列為原垂直列表；「往下露頂部」為 `LazyColumn` + `SnapLayoutInfoProvider(SnapPosition.Start)` 撲克牌式堆疊（逐卡 `graphicsLayer` 依 `layoutInfo` 錯開 52dp、`zIndex` 遞減）；「中央堆疊」重用 `CenterFocusCardStack`；共用新抽出的 `NoteCardItem`（堆疊模式限 6 行內文、`overlapOpaqueBoost` 防透字）；待辦收起時記事容器自動填滿垂直空間。
- **設定頁子區塊可收合（#242）**：
  - 「個別 App 節流覆寫」與「自訂常用指令（N 則）」改為可收合子區塊（標題列點擊／箭頭切換，預設收合並顯示數量摘要）；展開狀態以新 `PhantomPrefs.isSettingsSubCardExpanded`／`setSettingsSubCardExpanded`（key `settings_expanded_subcards`）持久化，獨立於大分類卡片（`KEY_COLLAPSED_CARDS`），大卡片收合重開不會重置子區塊。
- **首頁篩選標籤列可收合成單行（#245）**：
  - `MessagesHomeTab` 篩選 chip 列收合時僅顯示「全部、未讀」與作用中篩選／標籤 chip，其餘隱藏（單行不換行），右側 34dp 圓角箭頭切換；狀態以 `home_filter_chips_collapsed` 持久化，不再溢出換行。

### 變更與修正
- **根除浮動層外層直角黑底（#243）**：
  - `Components.kt` 新增 `PhantomDialog`：`Dialog(usePlatformDefaultWidth = false)` + `SideEffect` 將 window 背景 `setBackgroundDrawable(ColorDrawable(TRANSPARENT))` 設為全透明，深色不透明底只由內層 `PhantomFloatingSurface`（clip → background → border）繪製，圓角框線外 100% 透出桌布；「手動新增自動記事規則」彈窗由 `AlertDialog` 改用 `PhantomDialog`。
  - `MainActivity`（訊息操作、標籤選擇）、`ConversationScreen`（訊息操作）、`ChatScreen`（待辦提醒清單）共 4 處 `ModalBottomSheet` 一律 `containerColor = Color.Transparent`，內容改包 `PhantomFloatingSurface` 圓角容器。
- **介面與設定模型同步（#244）**：
  - 首頁／記事排列模式與收合狀態統一以 `PhantomSettingsBus.version.collectAsState()` 驅動 `remember` 重新讀取 `PhantomPrefs`，設定頁變更回分頁立即生效。
- **每日／本週摘要準時觸發（#246）**：
  - `DailySummaryPolicy`／`WeeklySummaryPolicy` 由 `PeriodicWorkRequest` + `UPDATE`（不重錨點，改設定後仍按舊時間觸發）改為 `OneTimeWorkRequestBuilder` + `enqueueUniqueWork(REPLACE)`，`initialDelay` 為距下次觸發的精確時間（下限 60 秒），Worker `doWork` 以 `try/finally` 結束時自我重新排程，杜絕排程漂移。
- **AI 助手／設定搜尋支援新設定（#247）**：
  - `SettingsCatalog` 更新 `personal_home_card_layout` 描述與別名，新增 `personal_notes_card_layout`、`personal_notes_todo_collapsed`、`personal_home_filter_chips` 搜尋項；`NumericSettingSpec` 新增 `valueLabels` 選項型數值設定，登錄 `home_card_layout`（0=一般條列／1=中央堆疊）與 `notes_card_layout`（0=一般條列／1=往下露頂部／2=中央堆疊），AI 助手可對話直接切換排列模式，系統提示輸出選項對照與目前值。
- **發佈準備（#247）**：
  - 版號更新：`versionCode` 29、`versionName` "2.11.0"；同步 `CHANGELOG.md` 與 `README.md` 功能總覽。

## [2.10.0] - 2026-11-18

### 新增
- **首頁卡片排列模式三選一（#230）**：
  - `PhantomPrefs` 新增 `HomeCardLayoutMode`（`LIST` 一般條列預設／`PEEK_TOP` 撲克牌式往下露頂部 52dp／`PEEK_BOTTOM` 資料夾式往上露底部邊緣縮放），自動遷移舊 `card_stack_effect_enabled`（`true → PEEK_BOTTOM`、`false → LIST`）。
  - 重疊模式由 `MessagesHomeTab` `LazyColumn` 搭配 `rememberSnapFlingBehavior`＋自訂 `SnapLayoutInfoProvider` 於列表層控制 `zIndex` 與 `graphicsLayer`（`translationY`／`scaleX`）滑動吸附，並透過 `PhantomGlassCard(overlapOpaqueBoost = true)` 提升深色毛玻璃不透明度與邊框陰影防止底層透字；全鏈移除 `StackedAppCard` 與舊開關，並於 `SettingsActivity` 改為三顆並排 `FilterChip`（接入 `SettingsCatalog` `personal_home_card_layout` 與 `PhantomSettingsBus` 即時生效）。
- **寄件者頭像擷取與多層顯示（#231）**：
  - Room 升版至 v16（`MIGRATION_15_16`），新增 `sender_avatars` 表；`NotificationCollectorService` 於 `EXTRA_MESSAGES` 解析處擷取 `MessagingStyle.Message.getSenderPerson()?.getIcon()`，降採樣至 `≤96px` 並壓縮至 `≤16KB` 後 upsert 寫入（自動清理 30 天前舊頭像）。
  - `AppIconHelper` 新增 `SenderAvatarImage`（`LruCache` 128 項快取），顯示優先序為 `sender_avatars` → 圓形裁切 App 圖示 → 寄件者首字母 Material 彩色圓頭像；整合至 `AppThreadsScreen`（40dp）與 `ConversationScreen` 非 `isOwn` 氣泡（28dp 頂端對齊）。
- **自己回覆抑制與自我身分名稱設定（#237）**：
  - 隨 `MIGRATION_15_16` 新增 `own_reply_echo` 表；於通知快速回覆與 SMS fallback 成功送出後寫入抑制列，3 分鐘內 `NotificationCollectorService` 解析新訊息尾巴若命中（正規化全等或 `diceBigramSimilarity ≥ 0.9`）即略過重複入庫並刪除抑制列。
  - `PhantomPrefs` 新增 `my_display_names`（預設含 `"Eric Chiu"`，trim 不分大小寫比對），群組通知寄件者命中時直接標記 `isOwn = true`／`isRead = true` 並跳過詐騙警示；設定頁「訊息與通知」卡片與 `SettingsCatalog`（`notif_my_display_names`）新增編輯入口。

### 變更與修正
- **浮動層黑底範圍與遮罩修復（#229）**：
  - `ChatScreen` 歷史對話抽屜容器補上上下 `10.dp` 垂直 inset 與 `displayCutoutPadding()`，使 `20.dp` 圓角完整落在桌布內；抽屜 scrim 與 `MainActivity` 自動記事規則全螢幕 scrim 統一調降為 `0.45f`，`PhantomFloatingSurface` 深色底調整為 `0xEB12151E`（約 92% 不透明）。
- **記事搜尋膠囊置左（#232）**：
  - `MainActivity` `NotesTab` `notes_search_pill` 移除 `PhantomSearchBarPill` 的 `weight(1f)`，改為緊湊置左並以 `Spacer(weight(1f))` 推開右側 `Tune` 按鈕（保留 48dp 觸控目標與安全區域間距）。
- **AI 歷史對話抽屜速度感知手勢（#233）**：
  - `ChatScreen` `AnchoredDraggableState` `positionalThreshold` 調整為 `0.35f`、`velocityThreshold` 調整為 `400.dp`；拖曳放手改用 `VelocityTracker` 投影（`offset + velocity * 0.15s`）搭配 `400.dp/s` 或 `25%` 寬度判定開啟／關閉，維持 lambda `offset {}` 延遲讀取與所有現有互動零回歸。
- **自動待辦相似去重強化（#234）**：
  - `NotificationCollectorService.saveAutoMemoIfPresent` 強化 `normalizeForSimilarity`（NFKC、小寫、標點與空白正規化），`TodoDao` 新增 `findRecentPendingByAppLabel`，補齊 `diceBigramSimilarity ≥ 0.9` 判定，命中時原地更新既有待辦並重排提醒與小工具而不重複建立。
- **底部分頁欄水滴與全域流暢度優化（#235, #236）**：
  - `LiquidGlassTabBar.kt` 重用 `Path`（`rewind()`）、`Stroke`／`Color`／`Brush.verticalGradient` 快取物件，水滴彈簧調升為 `stiffness = 600f, dampingRatio = 0.8f`，並將 `onHorizontalDrag` 改為同步狀態驅動以消除每幀配置與 coroutine churn。
  - `MainActivity` 以 `rememberSaveableStateHolder`（`SaveableStateProvider(selectedTab.name)`）保留分頁切換狀態；完成全庫 `LazyColumn` `key`、`derivedStateOf`、`graphicsLayer` lambda 與 `StatsDetailWidgetProvider` SQL `GROUP BY`／`AppIconHelper` `LruCache` 慣例稽核。
- **發佈準備（#238）**：
  - 版號更新：`versionCode` 28、`versionName` `"2.10.0"`；復核全依賴為最新穩定版並同步 `CHANGELOG.md` 與 `README.md`。

## [2.9.0] - 2026-11-18

### 新增
- **設定即時同步匯流排 `PhantomSettingsBus`（#225）**：
  - 新增 `util/PhantomSettingsBus`（`MutableStateFlow<Long>` 版本計數＋`notifyChanged()`），`SettingsCatalog.applyBooleanSetting` 與 `applyNumericSetting` 成功寫入偏好後立即發送變更通知。
  - `MainActivity`（含 `StatsTab`）、`ChatScreen` 與 `SettingsActivity`（含 `FilterSettingsScreen` 及子元件 `replyProxyEnabled`／`intelEnabled`／`updateAutoCheck`）即時收集匯流排，AI 對話或設定搜尋修改後不需重進頁面或等待 `ON_RESUME` 即同步反映於 UI。

### 變更與修正
- **浮動層不透明實底與遮罩修復（#222）**：
  - `Components.kt` 新增 `PhantomFloatingSurface`（`.clip(shape)` 置於 `.background()` 之前，深色底色 `Color(0xF012151E)`、淺色 `surface.copy(alpha = 0.95f)`、1dp `outlineVariant` 描邊，不使用 `glassSurface`／blur；頁面內玻璃卡片行為維持不變）。
  - `ChatScreen` 歷史對話抽屜（`cornerRadius = 20.dp`、session 卡 alpha 提升為選中 `0.95f`／未選 `0.9f`）與 `MainActivity` 自動記事規則懸浮面板改用 `PhantomFloatingSurface`，兩者全螢幕 scrim 統一調升至 `0.65f`（抽屜為 `0.65f * progress`）；完成全庫浮動層四點稽核。
- **AI 歷史對話抽屜拖曳效能修復（#223）**：
  - `ChatScreen` 抽屜位移改由 `Modifier.offset { IntOffset(...) }` lambda 於 layout 階段讀取 `drawerState.offset`（含 `NaN` fallback），scrim 透明度改由 `Modifier.drawBehind` 於 draw 階段繪製，可見性由 `derivedStateOf` 控制；`LaunchedEffect` 改用 `snapshotFlow` 收集 `currentValue to isAnimationRunning`，消除拖曳期間每幀重組。
- **訊息卡片堆疊邊緣對比強化（#224）**：
  - `StackedAppCard` 層色改用 `lerp(surfaceVariant, onSurface, 0.25f)`，alpha 調升為層 1 `0.55f`／層 2 `0.35f`，scale 調整為 `0.96f`／`0.92f`，層 1 補上 1dp `outlineVariant.copy(alpha = 0.4f)` 描邊，深淺主題與背景圖情境下皆清晰可辨。
- **自動記事 keyword-only「不再記事」語意化與全鏈健檢（#226）**：
  - `NotificationCollectorService.saveAutoMemoIfPresent` 的字面 `ACTION_NEVER` 閘門改為僅對含 `packageName` 或 `sender` 條件的規則生效，keyword-only `ACTION_NEVER` 交由 AI 語意判斷（涵蓋即時通知與 `PendingAiAnalysisWorker` 離線補佇列）。
  - `AiClient.buildSystemPrompt` 記事規則段強化為結構化條列（`id`／`App`／`寄件者`／`關鍵字`／`動作`）、明確語意命中定義與反例（如 Gogolook 驗證碼規則不誤殺銀行 OTP），快取 key 升至 `memoV4`，並更新記事規則面板說明文案。
- **發佈準備（#227）**：
  - 版號更新：`versionCode` 27、`versionName` `"2.9.0"`；復核全依賴為最新穩定版並同步 `CHANGELOG.md` 與 `README.md`。

## [2.8.0] - 2026-11-18

### 新增
- **訊息卡片堆疊效果與個人化開關（#212）**：
  - `Components.kt` 新增 `StackedAppCard` 包裝層（維持 `ui/components/` 零改動），依訊息數量於主卡後方繪製 0／1／2 層 `surfaceVariant` 視覺堆疊（支援捲動微幅視差、點擊 140ms 收合回饋、靜音半透明與緊湊模式層距減半）。
  - 設定頁「個人化」新增「訊息卡片堆疊效果」`FluidSwitch` 開關（`card_stack_effect_enabled`，預設開啟）並同步寫入 `SettingsCatalog`。
- **AI 助手側邊歷史抽屜與分頁切換行為設定（#215, #216）**：
  - 拆除舊 `ModalBottomSheet`，改為於 `ChatScreen` 內容區自製 `AnchoredDraggableState` 左側抽屜（支援右滑開啟、左滑收回、`BackHandler` 優先關閉、40% 遮罩與 `LazyColumn` 流暢捲動）。
  - 設定頁「AI 助手與分析」新增「切到 AI 助手時接續上次對話」開關（`ai_chat_tab_behavior`，預設開啟新對話，可切換為自動接續最近會話）並同步寫入 `SettingsCatalog`。
- **記事規則懸浮視窗與關鍵字 AI 語意判斷（#217）**：
  - 移除 `NotesTab` 內嵌規則區塊，改於右上角配置 `Tune` 按鈕（48dp＋規則數角標）展開懸浮玻璃規則面板（保留手動新增、自然語言解析確認卡、啟停與刪除）。
  - `AiClient.buildSystemPrompt` 升級為語意同類比對指示（`riskCache` key 由 `memoV2` 升至 `memoV3`），`MemoRuleEngine` 保留字面 `contains` 作為未啟用 AI／離線降級方案。
- **統計頁排行榜、統計設定分類、卡片自訂與 4 張新圖表（#218）**：
  - 新增「今日 App 訊息量排行榜 Top 5」卡片（`todayCountsByApp`＋相對量橫條），並於設定頁「個人化」之後新增獨立「統計」分類卡片（12 張統計卡可個別勾選顯示／隱藏，`PhantomPrefs` `StringSet` 持久化並全數接入 `SettingsCatalog`）。
  - 新增「7 日風險趨勢折線」、「未讀／已讀比例環形」、「各 App 佔比橫條 Top 8」與「待辦完成率環形」4 張純 Compose `Canvas` 圖表（Room 維持 v15 零 migration，僅新增唯讀 `@Query`）。
- **記事桌面小工具（#219）**：
  - 新增 `NotesWidgetProvider`（2x2，支援縮放至 4x2、`goAsync()`、`PendingIntent.FLAG_IMMUTABLE`、`updateAllIfChanged` 差異更新），顯示總筆數與最近 5 則記事（置頂優先、36 字截斷＋相對時間），點擊直達 App 記事分頁；全庫 `noteDao()` 新增／編輯／刪除／釘選與自動備忘錄寫入點即時同步刷新。

### 變更與修正
- **我的最愛橫列角標與未讀排序修復（#213）**：
  - `MessageDao` 新增 `unreadCountsByApp()`（排除 `isOwn`），`AppGroupUi` 與我的最愛角標改用真實未讀數（0 隱藏、>99 顯示 `99+`），同步修正 `HomeSortOrder.UNREAD` 排序基準。
- **AI 助手輸入列鍵盤遮擋修復（#214）**：
  - 於 `MainTab.ASSISTANT` 內容層單一位置補上 `consumeWindowInsets(padding).imePadding()`（避免 double lift），並於鍵盤彈出且清單貼底時平滑捲動至最新訊息。
- **發佈準備與依賴版本稽核（#220）**：
  - 版號更新：`versionCode` 26、`versionName` `"2.8.0"`；完成全依賴穩定版稽核並同步 `CHANGELOG.md` 與 `README.md`。

## [2.7.0] - 2026-11-18

### 新增
- **自動記事／待辦規則引擎與管理介面（#203）**：
  - Room 升版至 v15（單一非破壞性 `MIGRATION_14_15`），新增 `memo_rules` 資料表與 `MemoRuleDao`，支援「永遠記事（`note`）」、「永遠待辦（`todo`）」與「永遠不要自動加入（`never`）」三種動作（優先序 `never > todo > note`）。
  - 新增純函式 `MemoRuleEngine`，於 `NotificationCollectorService.saveAutoMemoIfPresent`（涵蓋即時通知與 `PendingAiAnalysisWorker` 離線補分析）執行前置規則檢查（30 秒 TTL 快取，寫入/切換/刪除即時失效），並於 `AiClient.buildSystemPrompt` 注入規則與將 `memoRuleSig` 納入 `riskCache` key。
  - `ChatScreen` `add_rule` 擴充支援 `kind="memo"`（彈出確認卡寫入 DB）；「記事」分頁新增「自動記事規則」管理卡（列表、開關、刪除、手動新增對話框與自然語言 `AiClient.parseMemoRule` 解析＋確認卡）。
- **已記事／已待辦首頁篩選、待辦來源回溯與三範圍排除（#204）**：
  - `MessageFilter` 新增 `Noted`（已記事）與 `Todoed`（已待辦）篩選按鈕，並加入 `ALL_OPTIONAL_HOME_FILTERS` 與設定頁自訂篩選清單；`MessageCard` 新增 12dp 已記事／已待辦小圖示。
  - 「記事」分頁待辦列與搜尋結果待辦卡新增「查看來源訊息」按鈕（跳轉 `MessageDetailActivity`）；`MessageDetailActivity` 與自動記事長按新增「不再自動記事／待辦」入口，提供三範圍確認對話框（寄件者／App／關鍵字，寫入 `memo_rules` `never` 規則）。
- **自己回覆右側氣泡與持久化（#207）**：
  - `messages` 新增 `isOwn` 欄位（隨 `MIGRATION_14_15` 建立，預設 `0`），於 `ConversationScreen`（通知快速回覆與 SMS fallback）、`ChatScreen` `reply_message` 與 `ReplyProxyReceiver` 三處回覆成功後寫入 `isOwn = true`、`sender = "我"` 之對話紀錄。
  - `ChatBubbleItem` 針對 `isOwn` 渲染靠右對齊、78% 寬度、疊加 `primary` 0.16 alpha 淡色氣泡（隱藏風險點、AI 標籤與 OTP 擷取）；所有統計計數、未讀數、風險分布、ScamCenter、每日／每週摘要節錄與去重查詢全面加入 `isOwn = 0` 排除。
- **新手教學擴充至 18 步（#209）**：
  - 主頁教學由 15 步擴充為 18 步（同步 `KEY_ONBOARDING_STEP_V2` `coerceIn(1, 18)`），`SEARCH_PILL` 改為「下拉搜尋」（錨定頂部把手），新增「快速回覆」、「自動記事規則」與「已記事／已待辦與來源回溯」步驟，並更新 `FILTER_CHIPS` 文案說明固定表頭；舊已完成教學用戶不重跳。

### 變更與修正
- **首頁固定表頭與清單獨立捲動（#206）**：
  - `MessagesHomeTab` 重構為外層 `Column` 固定區（離線橫幅 → 統計卡 → 分類篩選列 → 我的最愛列）搭配 `LazyColumn(weight(1f))` 獨立捲動訊息清單，維持 `listState` 與分頁載入行為不變。
- **頂部下拉搜尋把手與統計卡工具列整合（#205）**：
  - 移除主頁頂部搜尋膠囊與工具列，重新整理／設定按鈕移入統計卡右側欄（48dp 觸控目標）；新增 `PhantomPullSearchHandle`（22dp 全寬、中央 4dp×120dp 膠囊，支援 `anchoredDraggable` 下拉與點擊開啟全螢幕搜尋，AI 助手分頁自動隱藏）。
- **對話頁 IME 過度上移修復（#207）**：
  - `AndroidManifest.xml` 為 `MainActivity` 與 `ConversationActivity` 補齊 `android:windowSoftInputMode="adjustResize"`，解決系統預設 `adjustPan` 疊加 `.imePadding()` 造成的 double lift；鍵盤開啟時僅在清單貼底狀態平滑捲至最新訊息。
- **搜尋頁與全庫文字對比度修復（#208）**：
  - 清除 `MessageCard`、`MessageDetailActivity`、`ScamCenterActivity` 與 `SettingsActivity` 殘留之 `Color.Gray`／`Color.Red` 主題盲色，統一改用 `MaterialTheme.colorScheme.onSurfaceVariant`／`error`；`ChatBubbleItem` 與 `MessageCard` 內文顯式指定 `onSurface`。
  - `PhantomFullScreenSearchOverlay` 背景改為不透明純主題色（alpha `1.0`）；`CardBgMode.TRANSPARENT` 滑桿下限調升至 15%（存量值自動 clamp），玻璃關閉／省電模式 fallback 下限提升至 0.35。
- **全域健檢、OEM 相容稽核與發佈準備（#210）**：
  - 補齊全專案有文字輸入之 Activity（`SettingsActivity`、`AutoReplySettingsActivity`、`ApiKeySettingsActivity`、`AppThreadsActivity`、`MessageDetailActivity`、`TagManagementActivity`、`WhitelistManagementActivity`）`windowSoftInputMode="adjustResize"`；完成無障礙 insets、cutout、Room 完整性與 `NotifIds` 互斥稽核。
  - 版號更新：`versionCode` 25、`versionName` `"2.7.0"`。

## [2.6.0] - 2026-11-18

### 新增
- **對話詳情頁底部快速回覆輸入列（#197）**：
  - `ConversationScreen` 底部新增常駐回覆輸入框（`OutlinedTextField`＋發送按鈕，含 `navigationBarsPadding().imePadding()`、鍵盤彈出自動捲動至最新訊息、多選模式下自動隱藏）。
  - `NotificationCollectorService.sendReplyToNotification` 新增可選 `conversationKey` 參數，優先以 `MessagingStyle` `EXTRA_CONVERSATION_TITLE` 精準定位對話（其次 `EXTRA_TITLE`、再同套件 fallback）；送出成功後呼叫 `ReplyProxyManager.startOrRefreshSession` 維持通知欄連續回覆；SMS 類 App fallback 走 `SmsManager` 既有簡訊回覆路徑，全程具備 `try/catch`＋`PhantomReply` logcat 保護。
- **獨立白名單管理頁面與收合持久化（#199）**：
  - 新增 `WhitelistManagementActivity`（`android:exported="false"`，採 `PhantomCheckTheme`＋`PhantomBackgroundBox` 與頂欄返回鍵），將分組白名單管理 UI 移入獨立頁面；`SettingsActivity` 資料管理區精簡為「白名單管理（N 筆）」入口按鈕。
  - `PhantomPrefs` 新增 `whitelist_collapsed_groups` `StringSet` 持久化各 App 分組收合狀態（跨進出與重啟保留，首次無記錄時預設全部展開）；`SettingsCatalog` `whitelist_management` 同步指向新 Activity。

### 變更與修正
- **全域依賴、編譯工具鏈與目標 SDK 升級（#200）**：
  - 升級 Gradle Wrapper 至 `9.9.1`、AGP 至 `9.5.1`（啟用 AGP 9 內建 Kotlin，移除 `org.jetbrains.kotlin.android` 與 `kotlinOptions`）、Kotlin Compose plugin 至 `2.4.21`、`compileSdk` 升至 `37`（滿足 `core-ktx 1.19.1`／`Compose BOM 2026.09.00`／`lifecycle 2.11.0` 編譯要求）、`targetSdk` 升至 `36`（Android 16）。
  - 導入 Jetpack Compose BOM `2026.09.00`（Material 3 `1.4.0`），升級 `core-ktx` `1.19.1`、`activity-compose` `1.13.0`、`lifecycle-runtime-ktx` `2.11.0`、`work-runtime-ktx` `2.12.0`、`Retrofit` `3.0.0`、`Room` `2.8.5`（完成 `kapt` → `KSP 2.3.14` 遷移）。
  - 升級 `Haze` 至 `2.1.0`（`haze`＋`haze-blur`），重寫 `Glass.kt` 遷移至 `Modifier.hazeBlur`＋`HazeInput.Sources`＋自建 `HazeBlurStyle`（取代已停產的 `haze-materials`），完整保留三種卡片底色模式、省電降級與 API < S fallback。
  - `AGENTS.md` 發佈檢查清單新增第 6 項常態規則：每次發佈須檢查並升級依賴至最新穩定版。
- **全螢幕搜尋覆蓋層層級修正（#193）**：
  - 將 `ConversationScreen` 與 `AppThreadsScreen` 之 `PhantomFullScreenSearchOverlay` 移出 `Scaffold` 改由外層 `Box` 包住整個 `Scaffold`（比照 `MainActivity` 寫法），修正搜尋覆蓋層被頂欄遮擋的問題。
- **迷你玻璃搜尋膠囊與工具列排版統一（#194）**：
  - `PhantomSearchBarPill` 改為 36dp 視覺高度膠囊（左放大鏡圖標＋「搜尋」文字，外層保留 ≥48dp 無障礙觸控目標），`MainActivity` 重新整理與設定齒輪按鈕移至搜尋膠囊下方右側對齊，四個頁面搜尋入口視覺風格統一。
- **左滑刪除列背景透明化（#195）**：
  - 移除 `SwipeToRevealRow` 內容層的全寬不透明底色，外層容器改為完全透明並依滑動位移進度控制右側動作按鈕 `alpha`，讓自訂背景圖在訊息氣泡後方完整可見。
- **回覆鏈與 AI 助手動作全面防崩（#196）**：
  - `ReplyProxyManager` 回覆用 `PendingIntent` 改為 `FLAG_MUTABLE`（符合 Android `RemoteInput` 官方規範；關閉／逾時等動作維持 `FLAG_IMMUTABLE`），`ensureChannel`／通知建立／排程全部加上 `try/catch`＋`PhantomReply` logcat。
  - `ReplyProxyReceiver` 廣播與 `goAsync()` 協程補齊 `try/catch`；`ChatScreen` 動作協程加上 `try/catch`＋`PhantomChatAction` logcat，並將 `messages.first()` 改為 `firstOrNull()` 空集合防禦。
- **待辦事項 Flow 即時同步稽核（#198）**：
  - `TodoDao` 新增 `observeAll(): Flow<List<TodoEntity>>`，`MainActivity` 記事分頁與 `ChatScreen` 待辦選單改採 `collectAsState`＋智慧排序即時觀測，桌面小工具刪除／完成／新增後 App 內立即同步；`MessageDetailActivity` 補齊 `ON_RESUME` 刷新 `remindAt` 顯示。
- **版號更新**：`versionCode` 24、`versionName` `"2.6.0"`。

## [2.5.0] - 2026-11-18

### 新增
- **通知欄連續快速回覆（#183）**：
  - 新增 `ReplyProxyManager` 與 `ReplyProxyReceiver`（`android:exported="false"`），建立通知頻道 `phantom_reply_proxy`（`IMPORTANCE_DEFAULT`，名稱「快速回覆對話」）與 `NotifIds` 專屬區段 `120000..129999`（`120000 + Math.floorMod(sessionHash, 10000)`）。
  - `ChatScreen` `reply_message` 送出成功後啟動／更新 `MessagingStyle` 代理通知（保留原始訊息＋「我」回覆紀錄，上限 8 則；Session 採 in-memory LRU 上限 8 組），提供「回覆」（`RemoteInput` resultKey `proxy_reply`）與「關閉」按鈕，支援滑掉（`deleteIntent`）與 `AlarmManager` 閒置逾時自動收起（15 / 30 / 60 分鐘或不自動收起，預設 30 分鐘）。
  - `ReplyProxyReceiver` 送出失敗時（含簡訊 `sms` / `^09\d{8}$` `SmsManager` fallback 失敗）誠實將錯誤訊息附加至對話紀錄串，不假裝成功；設定頁「訊息與通知」卡新增 `reply_proxy_enabled` 開關（預設開）與逾時選項，並註冊 `notif_reply_proxy` 與 `notif_reply_proxy_timeout` 至 `SettingsCatalog`。
- **AI 送出前重複通知折疊（#185）**：
  - 新增純函式 `NotificationFoldUtil`（不修改資料庫與既有 UI 清單），依同 `packageName`、`sender`、`conversationKey`、`riskLabel`、24 小時鏈式時間窗與 `normalized` 相同或 Dice bigram ≥ 0.85 折疊高頻重複通知為 `[AppLabel] 標題（HH:mm–HH:mm 共重複 N 次）`（`N = 1` 維持原格式）。
  - 保護條款：`scam`／`suspicious` 高風險訊息逐則保留不折疊；內含網址或數字序列（訂單編號／金額／OTP／電話）不一致者不折疊。套用至 `ChatScreen` RAG 訊息區、`DailySummaryPolicy.buildSummaryExcerpts`、`WeeklySummaryPolicy` 節錄與 `MessageDetailActivity` `senderHistorySummary`。
- **開啟 App 自動檢查更新（#186）**：
  - `MainActivity` `ON_RESUME` 每日最多一次背景靜默檢查 GitHub 最新版本（`update_last_check_day`，無網路或失敗靜默）；有新版本時於頻道 `app_updates`（`IMPORTANCE_DEFAULT`，`NotifIds.UPDATE_AVAILABLE = 1007`）發送每版本每日一次通知，並於前景顯示 `AlertDialog`（「前往下載」導向設定頁關於卡＋「稍後再說」，內文含「不想再收到更新提醒？可至 設定 → 關於 關閉自動檢查更新。」）。
  - 設定頁關於卡新增 `update_auto_check_enabled` 開關（預設開），並註冊 `update_auto_check` 至 `SettingsCatalog`（別名含「檢查更新」「更新」「版本」）；手動檢查與下載安裝流程維持不變。
- **自動記事／待辦智慧分流（#188）**：
  - `AiClient` 單則與批次分析 schema 擴充 `memo_type`（`"note" | "todo"`，缺漏寬容 fallback `"note"`）與 `todo_due`（`"yyyy-MM-dd HH:mm"` 或空字串），`riskCache` key 加入 `memoV2` 旗標。
  - `NotificationCollectorService.saveAutoMemoIfPresent` 依 `memo_type` 分流：`todo` 建立 `TodoEntity`（`priority = 1`、`dueAt` 解析 `todo_due` 或 fallback `msg.timestamp + 2h`），insert 前以 `TodoDao` 新增 `@Query`（Room 版本維持 14 無 migration）進行 `relatedMessageId` 與 7 天內未完成同標題去重，並呼叫 `ReminderScheduler.scheduleTodo` 與 `ReminderWidgetProvider.updateAllIfChanged`。
  - 抽出 `ClientTimeParser`（含 `NaturalTimeParseResult`）供 `ChatScreen` 與自動待辦共用。

### 變更與修正
- **對話頁訊息卡片黑底溢出修復（#184）**：
  - 修正 `ui/Components.kt` `PhantomGlassCard` 玻璃分支 `.clip(shape)` 順序，改為 `.glassSurface(...)` 之後 `.clip(shape)` 再繪製 `overlayColor`；同步補齊 `phantomGlassSurface` 全分支（含 `overlayColor == null` 與 API < S）於 `glassSurface` 之後使用單一 `remember(cornerRadius)` `RoundedCornerShape` 裁切（`ui/components/` 維持零改動）。
- **自動記事重複記錄去重（#187）**：
  - `NoteDao` 新增 7 天視窗查詢 `@Query`（Room 版本不變），`saveAutoMemoIfPresent` 於寫入前比對同 `relatedAppLabel` 7 天內自動記事（正規化相同或 Dice bigram ≥ 0.9）改為更新既有記事，並嚴格保護 `pinned` 記事不被覆蓋，手動記事不受影響。
- **桌面小工具通知同步清除與按鈕放大（#189）**：
  - `ReminderScheduler.cancel`／`cancelTodo` 同步呼叫 `NotificationManager.cancel` 清除已發出之 `NotifIds.reminder`／`NotifIds.todoReminder`，並於 `ReminderWidgetProvider` 刪除 `TYPE_MSG` 提醒時補齊呼叫 `ReminderScheduler.cancel`；取消勾選（`done = false`）行為維持不變。
  - `widget_reminder.xml` 動作按鈕放大至 `16sp` bold＋`paddingHorizontal="8dp"`＋`paddingVertical="6dp"`＋`minWidth="28dp"`／`minHeight="32dp"` 與 `?android:attr/selectableItemBackground` 點擊回饋，`reminder_widget_info.xml`（含 `xml-v31`）`minHeight` 調整為 `72dp`。
- **版號更新**：`versionCode` 23、`versionName` `"2.5.0"`。

## [2.4.0] - 2026-11-17

### 新增
- **AI 省 Token 五合一與視覺模型修正（#174, #173）**：
  - `ChatRequest` 支援 `prompt_cache_key`（對話使用穩定 session key、分析使用固定 key），分析系統提示與標籤/規則清單採明確排序且位元組級一致置於最前；記錄 `usage.prompt_tokens_details.cached_tokens`。
  - 重構 `ChatScreen.buildRagSystemPrompt` 為「靜態前綴（系統提示＋規則＋完整上下文）＋動態尾段（時間、本筆查詢）」，RAG 訊息改採舊→新固定順序以逐筆命中前綴快取；並導入增量情境摘要快取（300~500 token，支援跨日/RAG 上限變更失效與降級）。
  - 新增「雙模型信心路由」（`ai_dual_routing_enabled` 預設開、`ai_cheap_model` 預設 `mistral-small-2603`，進 `SettingsCatalog`）：先打便宜模型，risk < 30 或 > 70 直接採用，30~70 才升級原模型重判並取較高風險（不重複扣減額度）。
  - 輸出精簡與視覺 Token 優化：Chat 系統提示加入回覆精簡規範；圖片編碼降採樣並依實際尺寸動態估算 `imageTokenOverhead`；每日／週報摘要去重高風險節錄、補齊未分析訊息並設定 token 上限。
  - 修復 `AiClient.isVisionModelSupported`（#173）：`knownTextOnly` 改為精確清單，`mistral-small-2603`（Mistral Small 4 內建視覺）不再被誤擋。
- **卡片底色模式設定（#175）**：
  - 新增 `CardBgMode`（跟隨玻璃強度／透明模式 0–100% 滑桿／深色底）於外觀設定區，並註冊 `card_bg_mode` 與 `card_bg_transparent_alpha` 至 `SettingsCatalog`。
- **待辦事項全面強化（#176）**：
  - `AppDatabase` 升版至 v14（`MIGRATION_13_14` 註冊於 `AppDatabaseHolder`），`TodoEntity` 新增 `completedAt`、`priority`（低/中/高）、`notes` 與 `repeatRule`（每日/每週，完成後自動派生下一筆並重排提醒）。
  - 記事分頁支援逾期變色與「已逾期 X 天」／「今天到期」標籤、篩選（全部/未完成/已完成/逾期）、智慧排序（逾期 > 到期日 > 優先級，與桌面小工具同步）、手動新增「+」與完整 `TodoEditorDialog`（AI 建立之待辦亦可編輯）。
- **白名單管理介面（#177）**：
  - 設定頁新增可依 App 分組收合的「白名單管理」卡片（顯示寄件者、App 圖示、模式、時間、防重複手動新增、啟用切換、滑動/按鈕刪除確認與空狀態），並註冊 `whitelist_management` 至 `SettingsCatalog`；任何變更統一呼叫 `invalidateRuleCaches()`。
- **桌面 2x1 待辦小工具互動按鈕（#179）**：
  - `ReminderWidgetProvider` 新增每列「完成 ✓」（僅待辦顯示，寫入 `isDone`＋`completedAt`）、「編輯 ✎」（跳轉主頁記事分頁並開啟編輯對話框）、「刪除 ✕」按鈕，採 `PendingIntent.getBroadcast`（request code 含 `itemId` 防多實例衝突）＋ `goAsync()` 非同步寫入與立即刷新（快照 key 納入全項 ID）。
- **統一下拉式玻璃搜尋槓與全螢幕搜尋（#180）**：
  - 於 `ui/Components.kt` 實作 `PhantomSearchBarPill` 與 `PhantomFullScreenSearchOverlay`（fade+slide 動畫、自動聚焦、IME inset、即時 `LazyColumn` 搜尋、保留上次字串、AI 語意搜尋額度入口與 ≥48dp 無障礙觸控目標），替換訊息頁、App 對話清單、對話頁與記事頁搜尋欄。

### 變更與修正
- **卡片黑底溢出修復（#175）**：
  - `ui/Components.kt` `phantomGlassSurface` 與 `PhantomGlassCard` 統一共用單一 `RoundedCornerShape` 實例進行背景與外框裁切，並修正 API < S fallback 雙層深色背景疊加問題（`ui/components/` 目錄維持零改動）。
- **白名單注入與 AI 功能全面健檢（#177, #178）**：
  - `ChatScreen.buildRagSystemPrompt` 靜態前綴常設注入結構化白名單（超過 20 筆依 App 分組壓縮），新增 `whitelist_delete` 確認卡動作與 DB 回讀驗證。
  - 盤點全 7 項 `AiPurpose`：`CHAT` 補齊待辦（含逾期/完成）、備忘錄、VIP 寄件者、靜音 App 與提醒關鍵字上下文；`DEEP_ANALYSIS` 補齊寄件者歷史訊息與風險摘要；統一所有 AI 寫入動作（待辦、白名單、規則、標籤）採「執行→回讀驗證→成敗明確回報」模式。
- **新手與設定教學修復與擴充（#181）**：
  - 移除 `Onboarding.kt` 非必要步驟之連鎖自動跳步（僅權限步驟單次自動跳過，缺目標改提示並手動下一步），新增依洞區中心動態上下避讓之字卡定位；主頁教學擴充至 15 步（納入 `SEARCH_PILL`），設定頁導覽由 6 步擴充至 8 步（納入白名單管理、卡片底色、分用途模型與省 token 設定）。
- **版號更新**：`versionCode` 22、`versionName` `"2.4.0"`。

## [2.3.1] - 2026-11-17

### 變更與修正
- **AI 標籤分發解析容錯與失敗通知改進（#169）**：
  - `AiClient.parseAnalysisJson` 與 `parseBatchAnalysisJson` 強化容錯：依序剝除 Markdown 圍欄、以字串／跳脫安全之平衡括號掃描擷取候選片段（多物件取首個含 `risk_score`/`risk` 者）、嚴格 Gson 解析失敗後才套用寬容預處理（字串外部全形冒號／逗號轉半形、移除 trailing comma），並保留既有正則降級與新增 `PhantomAiParse` logcat 診斷（僅記原因與長度，不記訊息原文）。
  - `buildSystemPrompt` 加強「只輸出一個 JSON 物件、不得使用圍欄與附加說明」規範；支援 `json_object` 之 OpenAI 相容端點（OpenAI／Mistral／Groq／DeepSeek／Gemini）於單則分析帶上 `response_format`；`analyzeMessageOrThrow` 與 `testAutoTagDispatch` 解析失敗時附 corrective 訊息自動重試一次（不重扣每日額度），測試畫面失敗時顯示模型原始回應前 200 字輔助診斷。
  - `NotificationCollectorService` 分發失敗通知改採 24 小時滑動時間窗計數（累積 5 次觸發一次 `IMPORTANCE_LOW`＋`setOnlyAlertOnce` 通知，保留 6 小時冷卻、單次成功不重置計數並尊重勿擾時段）；新增 `ai_tag_failure_notif_enabled` 開關（`PhantomPrefs` 預設開、設定頁 AI 卡可切換、`SettingsCatalog` 註冊搜尋與對話開關）。
- **訊息背景頁黑底修復與選圖可靠度提升（#170）**：
  - `SettingsActivity` 背景圖層選擇狀態 `pickingLayer`（存 `layer.name`）與 `bgStatusMsg` 改用 `rememberSaveable` 保存，防止選圖期間旋轉或行程重建遺失目標圖層；`uri` 非空但圖層遺失時顯示明確提示而非靜默丟棄。
  - `PhantomBackgroundBox` 於 `bgFile == null`、bitmap 載入中或解碼失敗時明確繪製 `MaterialTheme.colorScheme.background`（不再透空露黑），`BackgroundImageHelper.loadBackgroundBitmap` 解碼失敗補齊 `PhantomBackground` logcat 紀錄。
  - Photo Picker 失敗（非使用者取消）或無法開啟時，提供 `ActivityResultContracts.OpenDocument("image/*")` 檔案選擇器備援按鈕並同樣降採樣複製進內部儲存。
- **新手教學擴充與健檢（#171）**：
  - 主頁新手教學 `OnboardingStep` 由 10 步擴充為 14 步（新增「記事與自動備忘錄」、「統計、摘要與離線佇列」、「待辦提醒與批次管理」、「背景圖片、外觀與分用途模型」），同步更新 `PhantomPrefs.KEY_ONBOARDING_STEP_V2` 上限（`1..14`）、`OnboardingTargets`（納入 `NOTES_TAB`、`STATS_TAB`）與目標缺失自動跳過防護（含防重入與版面穩定延遲）；修正設定頁「重新播放新手教學」同步重置步驟索引為 1；已完成教學的舊用戶不被重新打擾。
  - 全面校對 4 Tab（「訊息 / AI 助手 / 記事 / 統計」）教學文案，並更新設定頁 6 步導覽（`AI`、`NOTIF`、`APPEARANCE`）納入 v2.3 新功能。
- **版號更新**：`versionCode` 21、`versionName` `"2.3.1"`。

## [2.3.0] - 2026-11-17

### 新增
- **分用途 AI 模型路由（#163）**：
  - 支援 6 種 AI 用途（`ai_chat`、`ai_message_analysis`、`ai_daily_summary`、`ai_weekly_summary`、`ai_deep_analysis`、`ai_settings_search`）各自綁定獨立 AI Profile 或「跟隨預設」（自動備忘錄跟隨每則訊息風險分析模型）；`ApiKeySettingsActivity` 新增用途模型設定卡（6 列下拉選單與逐列測試連線）。
  - `ChatScreen` 模型切換器改為僅切換 `ai_chat` 用途模型（不再更動全域預設 Provider），並於 `SettingsCatalog` 新增 `ai_model_chat` 等 6 項設定搜尋條目。
- **離線 AI 補分析佇列與自動重試（#164）**：
  - `AppDatabase` 升版至 v13（`MIGRATION_12_13`）新增 `pending_ai_analysis` 資料表；僅針對暫時性失敗（`UnknownHostException`、`SocketTimeoutException`、`ConnectException`、HTTP 5xx、429 耗盡）自動入隊，401/403/404/413 誠實略過。
  - 新增 WorkManager `PendingAiAnalysisWorker`（`NetworkType.CONNECTED`、指數退避、unique work `ai_pending_analysis_worker`），每輪最多補分析 20 則，`attempts >= 3` 放棄並通知；提供低干擾通知（`NotifIds.AI_OFFLINE_QUEUE`）、訊息頁頂部 `AnimatedVisibility` 佇列橫幅（暫停／繼續／立即重試）與設定頁 `ai_offline_retry_enabled` 開關（預設開，關閉即清空佇列）。
- **「記事」分頁與 AI 自動備忘錄（#166）**：
  - 主分頁將「詐騙警示」替換為「記事」（維持 4 Tab），整合待辦事項（共用 `todos` 表）與手動／自動記事 CRUD、釘選與色標（`notes` 表併入 `MIGRATION_12_13`）；詐騙警示中心改由訊息頁風險卡與統計頁 Top 5 卡「查看詐騙警示中心」進入。
  - 設定頁新增 `ai_auto_memo_enabled` 開關（預設關），開啟後 `AiClient` 於風險分析同時產出 40 字內繁中重點摘要，並以 `relatedMessageId` 去重寫入自動備忘錄（`riskCache` 鍵含 `autoMemo` 旗標）。
- **AI 分析字數門檻自訂（#167）**：
  - `PhantomPrefs.getAiMinTriggerLength`（1–30 字，預設 10）取代 `NotificationCollectorService` 硬編碼門檻，設定頁 AI 卡新增滑桿並於 `SettingsCatalog` 註冊 `ai_min_trigger_length`（支援 AI 對話調整）。

### 變更與修正
- **圖片視覺上傳與背景選圖雙重修復（#162）**：
  - `ChatScreen.encodeImageUriToDataUri` 改採 bounds-first `inSampleSize` 降採樣（最長邊 ≤ 1024px，OOM 時加倍 `inSampleSize` 重試一次）、細分錯誤原因誠實回報並記錄 `PhantomImage` log；更新 `AiClient.isVisionModelSupported`（納入 Groq `qwen/qwen3.8-27b` 並將 `deepseek` 納入純文字模型清單）。
  - 修復 `BackgroundImageHelper.saveBackgroundFromUri` 因 `inJustDecodeBounds = true` 回傳 `null` 導致 `use { ... } ?: return failure` 永遠觸發的 bug，恢復全域／AI 聊天／對話頁三層選圖。
- **「清除已讀」入口移至設定頁（#165）**：
  - 移除 `MainActivity` 訊息頁右上角「清除已讀」按鈕與確認對話框，移至設定頁「訊息與通知」卡片，並於 `SettingsCatalog` 新增 `notif_clear_read` 與同步 `data_export_clear` 描述。
- **版號更新**：`versionCode` 20、`versionName` `"2.3.0"`。

## [2.2.0] - 2026-11-17

### 新增
- **關於卡檢查更新與從 GitHub 下載 APK（#157）**：
  - 「關於」卡片作者更新為「作者：幻神（Hwanshen）」，並新增手動觸發「檢查更新」按鈕（`GET https://api.github.com/repos/eric11yothelast-Hwanshen/PhantomCheck/releases/latest`，`User-Agent: PhantomCheck-Android`，逾時 10 秒，與 runtime `versionName` 語意比較；無網路／逾時／API 失敗誠實顯示錯誤並提供「前往 GitHub」替代按鈕）。
  - 偵測到新版本時支援以 `DownloadManager` 下載 `app-release-X.Y.Z.apk` 至 App 專屬 `Download/` 目錄，透過 `FileProvider`（`file_paths.xml` 新增 `updates` `external-files-path`）＋ `ACTION_VIEW`（`application/vnd.android.package-archive` ＋ `FLAG_GRANT_READ_URI_PERMISSION`）呼出系統安裝器；`AndroidManifest.xml` 宣告 `REQUEST_INSTALL_PACKAGES`，安裝前檢查 `canRequestPackageInstalls()` 並引導至 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`；`SettingsCatalog` `about_section` 擴充「檢查更新／更新／update／下載」別名。
- **三層背景圖片自訂與視覺微調滑桿（#159）**：
  - 個人化設定卡新增「背景圖片」三層自訂（全域背景／AI 聊天背景／對話頁背景，未設定時逐層 fallback 至全域背景），透過 `PickVisualMedia` 選圖後以 2 的冪 `inSampleSize` 降採樣至螢幕解析度以下並以 JPEG 85 壓縮儲存至 `filesDir/backgrounds/{global|chat|conversation}.jpg`；`BackgroundImageHelper` 採單張 `LruCache(1)` 並於 `PhantomApplication.onTrimMemory` 釋放。
  - 於 `ui/Components.kt` 新增頁面層包裝 `PhantomBackgroundBox`（`ui/components/` 零改動），在設定背景圖時於底層繪製 `Image(ContentScale.Crop)` ＋暗色可讀性遮罩（scrim，預設日間 35%／夜間 60%，提供 0–100% 滑桿），未設定背景圖時與 v2.1.0 逐像素一致；新增「玻璃卡片強度」滑桿（0–100%，預設 0%，Android 12 以下降級行為不變），並於 `SettingsCatalog` 新增 `bg_global`、`bg_chat`、`bg_conversation`、`bg_scrim`、`glass_strength` 五項設定搜尋條目。
- **AI 助手操作能力擴充（#160）**：
  - 沿用既有 `parseAllChatActions`／`KNOWN_CHAT_ACTIONS`／確認卡佇列／`sanitizeForPrompt` 安全隔離框架新增 9 項動作：唯讀 `search_messages`（`MessageDao.searchFiltered` 預設查詢近 2 天，內嵌最多 5 筆可點擊開啟 `MessageDetailActivity` 的搜尋結果卡）與 `todo_list`（Markdown 表格）；以及一律經確認卡執行的 mutating 動作 `todo_done`、`todo_delete`、`todo_edit`（含模糊比對與 0 筆／多筆候選誠實回報，並重排 `ReminderScheduler` 與刷新小工具）、`vip_pin`（呼叫 `PhantomPrefs.toggleVipSender`）、`mute_app`（靜音清單增刪與候選清單提示）、`mark_read`（`today`/`app`/`all`，嚴格排除 `riskLabel = 'scam'` 並刷新 `StatsForegroundService`）與 `export_messages`（複用 `MessageExporter.exportToDownloads` CSV 匯出與公式注入防護）。

### 變更與修正
- **全域程式碼健檢與效能修正（#158）**：
  - 修復 `ChatScreen` `pendingTodoQueue` 先取 `firstOrNull()` 再過濾 `sessionId` 導致跨會話待辦確認卡被舊會話卡住的 bug，改為先過濾 `currentSessionId` 再取首筆並依物件參考移除已處理項目。
  - `NotificationCollectorService` 新增 `cachedActiveUserTagRules` 30 秒 TTL 快取並於 `invalidateRuleCaches()` 清除，補齊 `ChatScreen`、`TagManagementActivity`、`MessageDetailActivity`、`ScamCenterActivity` 與 `RiskActionWidgetProvider` 寫入／切換／刪除 `ai_rules` 與 `user_tag_rules` 後立即呼叫 `invalidateRuleCaches()`。
  - `ChatScreen` `tag_rule` 目標標籤不存在時改為誠實跳過並記錄 `Log.w`，不再誤 fallback 至 `tags.firstOrNull()`。
  - `TagRuleEngine` 與 `NotificationCollectorService` 熱路徑 `Regex` 預先編譯為常數；並完成 `ReminderWidgetProvider` 純待辦點擊 fallback、`HeartbeatPolicy` `UPDATE` 策略、`PhantomPrefs` 節流快取、7 日 SQL 聚合、`AppIconHelper` 共用快取與 `BootReceiver` 同 ID 覆蓋驗證。
- **版號更新**：`versionCode` 19、`versionName` `"2.2.0"`。

## [2.1.0] - 2026-11-17

### 新增
- **AI 自然語言待辦提醒＋桌面小工具整合（#143）**：`AppDatabase` 升版至 v12（`MIGRATION_11_12`）新增 `todos` 資料表；AI 助手支援 `reminder_add` 與用戶端確定性時間解析 fallback、待辦確認卡與清單 Sheet，透過 `AlarmManager.setAndAllowWhileIdle` 排程（`NotifIds` 互斥區段 `110000..119999`）並於開機由 `BootReceiver` 重排，同步刷新 `ReminderWidgetProvider`。
- **對話頁與對話清單手動多選批量管理（#144）**：`ConversationScreen` 與 `AppThreadsScreen` 新增多選模式（上限 100 則），支援批次已讀／星標／刪除（選取模式中停用 `SwipeToRevealRow` 與單則詳情誤觸）；批次刪除嚴格排除 `riskLabel = 'scam'` 並同步清理 `message_tags` cross-ref。
- **AI 助手預設新對話＋一鍵清除全部對話（#145）**：進入 AI 助手分頁預設為新對話（不自動載入上次會話），歷史對話抽屜新增「清除全部對話」按鈕與二次確認（`ChatDao.clearAllSessions()`，AI 回覆中拒絕清除）。
- **設定頁「關於」區塊（#141）**：於「全部設定」最底新增「關於」卡片，透過 runtime `PackageManager.getPackageInfo` 自動同步顯示 `versionName (versionCode)`、作者、GitHub 倉庫連結、第三方元件與隱私說明，並於 `SettingsCatalog` 註冊搜尋項目 `about_section`。
- **常用指令「跟隨預設」模式與動態解析（#146）**：`QuickChatCommand` 新增 `MODE_FOLLOW`（內建三則預設跟隨）、`resolveQuickCommandMode` 動態解析與設定頁三態切換／即時刷新。

### 變更與修正
- **LINE 群組對話分割修復（#140）**：`AppDatabase` 升版至 v11（`MIGRATION_10_11`）新增 `conversationKey` 欄位，解析 `MessagingStyle`（`EXTRA_CONVERSATION_TITLE`、sender `Person`）區分群組與私聊對話，無 `MessagingStyle` 通知維持原 fallback 行為並同步遷移置頂 `vipSenders`。
- **AI 助手分頁獨立 SnackbarHost（#142）**：`MainActivity` 為 AI 助手分頁配置分頁專屬、內嵌於版面的 `SnackbarHost`（顯示於分頁頂部、脫離 Scaffold 底部 overlay），徹底避免刪除會話等提示遮擋底部輸入框。
- **Release Notes 自動抽取 CHANGELOG 段落（#112）**：`.github/workflows/release.yml` 於發佈步驟前自動抽取對應版本之 `CHANGELOG.md` 段落並與 GitHub 自動 PR 清單共存（本版 tag 首次生效）。
- **版號更新**：`versionCode` 18、`versionName` `"2.1.0"`。

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
- **效能與 M3 版面一致化（#137, #138）**：`NotificationCollectorService` `aiRuleDao().active()` 導入 30 秒 TTL 快取、`PhantomPrefs` 同 key 節流／靜音模式查表導入 30 秒執行緒安全 TTL 快取、`AppIconHelper` 統一為 4 MB 位元組上限 `LruCache`、`StatsForegroundService` 停用時釋放靜態圖示條參照並補齊 Compose `LazyColumn` `contentType`／預計算日期分隔；全專案對齊 M3 12dp 卡片間距與 48dp 最小觸控目標。
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
