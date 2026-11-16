# 更新日誌（CHANGELOG）

本專案遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/) 格式，並採用語意化版本控制。

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
