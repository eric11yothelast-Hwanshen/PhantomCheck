# 更新日誌（CHANGELOG）

本專案遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/) 格式，並採用語意化版本控制。

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
