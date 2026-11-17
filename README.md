# 幻訊 PhantomCheck

Android 智慧防詐訊息管理 App：在不觸發原 App 已讀的前提下攔截通知預覽，結合關鍵字過濾、AI 詐騙風險分析、詐騙警示中心與 SMS 自動回覆，守護日常通訊安全。

---

## 系統需求

- **作業系統**：Android 8.0（API 26）以上
- **液態玻璃模糊效果**：Android 12（API 31）以上支援背景即時模糊；更低版本或省電模式自動降級為半透明壓色
- **必要權限**：通知存取權（Notification Listener）；Android 13+ 建議開啟通知權限以顯示詐騙警示與常駐統計

---

## 核心功能

- 📩 **通知攔截預覽**：透過 `NotificationListenerService` 即時讀取通知內容，無需開啟 LINE、IG 或簡訊等原 App，不觸發「已讀」。
- 🕵️ **公開防詐情報預查（零 Token）**：AI 分析前先抽取 URL、短連結與加密貨幣地址（BTC/ETH/TRON），依序查詢本地 72 小時快取、`fraud.tw`（165 通報）、Google Safe Browsing v4 及可選 PhishTank / Chainabuse（不傳送訊息全文，命中直接警示且不消耗每日 AI 額度）。
- 🛡️ **多 Provider AI 詐騙分析與即時切換**：支援 Mistral、OpenAI、Groq、OpenRouter、Gemini（OpenAI 相容）、DeepSeek 等多組 AI Profile，可於 AI 助手與統計頁摘要卡即時切換模型。
- 🚫 **智慧通知去重與過濾**：自動過濾持續性通知、群組摘要與斷線重播重複通知，支援同 Key 重複節流與個別 App 覆寫（`0 秒 = 永不節流`；通訊類通知若 `postTime` 更新則收錄同內容新訊息）、關鍵字過濾（含指定 App）與 App 靜音清單。
- 📊 **常駐統計通知與儀表板**：自訂 `RemoteViews` 常駐通知左側固定顯示幻訊圖示、右側「左新右舊」呈現最近通知 App 並標記最新一則，點擊遵循原 App／幻訊設定（滑掉無縫原地重建 + 15 分鐘心跳 + Shizuku 跨行程看門狗）；App 內提供 4 Tab 統計儀表板。
- 🤖 **AI 助手、自然語言待辦與昨日簡報**：內建唯讀 RAG 對話助手（預設開啟新對話，支援多會話切換與一鍵清除全部對話）、`PhantomMarkdownText` 渲染、「昨日簡報」、自然語言待辦提醒（同步鬧鐘排程與桌面小工具），以及透過對話提出新增白名單／標籤規則（強制彈出確認對話框防範 prompt injection）。
- 🏷️ **AI 智慧標記、App 分類與規則管理**：單次 AI 分析支援 0~3 個標籤＋零 token App 分類規則自動疊加；支援自訂自然語言白名單與標籤規則；氣泡顯示最多 3 個標籤色點。
- 💬 **App 分組與聊天式對話頁**：依 App 與 `MessagingStyle` 對話鍵分組瀏覽（精準區分群組與私聊），支援我的最愛與 VIP 寄件者置頂、對話 keyset 分頁載入（LIMIT 200）、左滑氣泡刪除，以及首頁清單、對話頁與對話清單多選批次管理（上限 100，詐騙保護排除）。
- 🧭 **視覺化新手與設定教學**：10 步主頁高光引導＋6 步設定頁導覽，支援淺色、深色、動態取色三模式高對比閱讀與洞內觸控穿透。
- 🔋 **省 token 模式與省電優化**：一鍵開啟省 token 模式；省電模式下自動停用玻璃模糊；支援關閉液態玻璃模糊效果以節省 GPU。
- 🚨 **詐騙警示中心、勿擾時段與 SMS 自動回覆**：集中顯示高風險詐騙訊息、情報來源與趨勢比較；支援勿擾時段與 SMS 自動回覆（含 per-sender 4 小時冷卻、單日上限 3 次與範本自回防護）。
- 📄 **CSV 匯出、保留政策與加密金鑰**：一鍵匯出 CSV 至 `Downloads`；`EncryptedSharedPreferences` 本機加密儲存 AI 與情報源（Safe Browsing／PhishTank／Chainabuse）API Key，支援設定頁直接輸入（`local.properties` 為 fallback）。

- 🎨 **個人化三層背景圖片與視覺微調**：支援全域／AI 聊天／對話頁三層背景圖自訂（逐層 fallback、降採樣儲存防 OOM）、暗色可讀性遮罩（scrim）與液態玻璃卡片強度滑桿。
- 🔍 **關於卡檢查更新**：支援手動查詢 GitHub Releases 最新版本並透過 `DownloadManager` 與 `FileProvider` 呼出系統 APK 安裝器。

---

## 版本紀錄

完整變更紀錄請參閱 [CHANGELOG.md](CHANGELOG.md)。

### v2.2.0 更新重點（versionCode 19）

- **關於卡更新檢查與 APK 下載安裝（#157）**：作者更新為「幻神（Hwanshen）」，新增手動「檢查更新」（GitHub REST API，10 秒逾時）與 `DownloadManager` ＋ `FileProvider` ＋ `ACTION_VIEW` 系統安裝器整合（含 `REQUEST_INSTALL_PACKAGES` 與未知來源授權引導）。
- **個人化多層背景圖與視覺滑桿（#159）**：支援全域／AI 聊天／對話頁三層背景圖（`PickVisualMedia` ＋ JPEG 85 降採樣與單張 `LruCache`）、背景暗色遮罩強度（0–100%，預設日間 35%／夜間 60%）與玻璃卡片強度滑桿。
- **AI 助手操作能力擴充（#160）**：新增 `search_messages`（內嵌可點擊結果卡）、`todo_list`／`todo_done`／`todo_delete`／`todo_edit`、`vip_pin`、`mute_app`、`mark_read`（保留詐騙紀錄）與 `export_messages`（複用 `MessageExporter` CSV 匯出），mutating 動作一律經確認卡執行。
- **全域程式碼健檢修正（#158）**：修復跨會話 `pendingTodoQueue` 阻塞、補齊 `user_tag_rules` 30 秒 TTL 快取與全路徑規則寫入即時失效、修正 `tag_rule` 缺失標籤改為誠實跳過、預編譯熱路徑 Regex 並完成多項排程與快取驗證。

### v2.1.0 更新重點（versionCode 18）

- **AI 自然語言待辦提醒與小工具整合（#143）**：Room 升版至 v12（`MIGRATION_11_12`）新增 `todos` 資料表；AI 助手支援 `reminder_add` 與確定性時間解析 fallback、待辦確認卡與管理 Sheet，透過 `AlarmManager.setAndAllowWhileIdle` 排程與 `BootReceiver` 開機重排，並同步刷新 `ReminderWidgetProvider`。
- **對話頁／對話清單多選批量管理（#144）**：補齊 `ConversationScreen` 與 `AppThreadsScreen` 多選批次操作（已讀／星標／刪除，上限 100 則），選取模式停用左滑與詳情誤觸，批次刪除嚴格保護詐騙訊息並清理 `message_tags` cross-ref。
- **AI 助手與常用指令體驗改善（#142, #145, #146）**：進入 AI 助手預設開啟新對話並於歷史抽屜提供「清除全部對話」；AI 分頁改用獨立內嵌 `SnackbarHost` 不再遮擋輸入框；常用指令新增「跟隨預設」模式並動態解析預設行為。
- **群組對話分割、關於區塊與發佈流程（#140, #141, #112）**：Room v11（`MIGRATION_10_11`）導入 `MessagingStyle` `conversationKey` 修復 LINE 群組對話分割；設定頁最底新增「關於」區塊（runtime 自動同步版本號）；`release.yml` 改為抽取 `CHANGELOG.md` 版本段落搭配自動 PR 清單。

### v2.0.0 更新重點（versionCode 17）

- **AI 標籤記憶與降級防護（#131, #132）**：Room 升版至 v10（`MIGRATION_9_10`），新增確定性零 token `TagRuleEngine` 與 `user_rule` 標籤記憶（遵守 `manual > user_rule > ai_rules > auto > App 分類`）；AI 標籤失敗自動降回 App 分類並採雙重防吵低優先通知，設定頁新增「AI 標籤分發測試」。
- **AI 助手可靠度與 Token 最佳化（#127–#130）**：修復裸 JSON 與數值設定執行鏈、Groq 免費方案 TPM 預算控管與 413/429 多階段自動降級、條件式設定目錄載入、RAG 去重與長對話摘要壓縮。
- **多模態輸入、版面精簡與自訂導覽（#133–#135）**：輸入區新增「常用指令（直接執行／預輸入）」與「圖片上傳視覺檢視」雙功能選單；切到 AI 分頁平滑隱藏頂部標題列並精簡輸入框；外觀設定新增底部分頁欄「標準／縮小／自動隱藏」三模式。
- **三款非統計桌面小工具與全域最佳化（#136–#138）**：新增「最新 AI 摘要 (2x2)」、「高風險處置 (2x2)」與「待辦提醒 (2x1)」桌面小工具；完成通知熱路徑快取、圖示 `LruCache` 統一與 M3 版面 8dp/48dp 一致性巡檢。

### v1.13.1 更新重點（versionCode 16）

- **AI 對話穩定性與多動作修正（#122, #123）**：`AiClient` 新增 `HTTP 413` 專屬錯誤訊息與 chat 單次閱讀上限減半自動重試（最低 25 則），深度分析遇 `413` 不消耗每日額度；`ChatScreen` 支援多動作解析、大宗白名單合併勾選確認卡，無效動作誠實回報且不回退顯示 raw JSON。
- **對話數值調整與設定導覽（#124, #125）**：新增 `setting_adjust` 滑桿確認卡直接調整單次閱讀上限、每日 AI 額度與同 key 節流秒數；新增 `open_setting` 導覽至設定頁目標卡片，system prompt 補齊全量設定清單摘要杜絕編造 UI 路徑，並修正未知設定 fallback 與非開關候選按鈕。

### v1.13.0 更新重點（versionCode 15）

- **AI 對話全方位強化（#114, #116, #118, #119）**：`PhantomMarkdownText` 補全表格（含對齊與橫向捲動）、斜體、刪除線、連結、引用、分隔線、任務清單與巢狀清單；新增 AI 助手單次閱讀訊息數滑桿（50–800，預設 400，省 token 模式自動降為 100）；支援透過對話直接切換 boolean 設定開關（共用 `SettingsCatalog`）；RAG 預設改為今天＋昨天並支援寄件者過濾，新增通知快速回覆、開啟原 App、刪除訊息（保護詐騙證據）與貼標籤確認卡操作。
- **結構化白名單與模型預設更新（#115, #117）**：更新各家 AI Provider 預設模型清單（Mistral `mistral-small-2603`、OpenAI `gpt-6.1-terra`、Groq `openai/gpt-oss-120b`、Gemini `gemini-4-hydrogen` 等）；Room 升版至 v9（`MIGRATION_8_9`），支援寄件者／單則訊息結構化白名單，命中即標記安全並跳過後續分析、警示與情報預查。
- **設定搜尋全面稽核（#120）**：逐卡對位 `SettingsCatalog` 與實際 UI，補齊每日摘要推播與液態玻璃特效條目並驗證本地／AI 雙路徑搜尋。

### v1.12.1 更新重點（versionCode 14）

- **設定搜尋 catalog 修正與直達頁面（#109）**：修正設定搜尋目錄五項對錯卡（含 SMS 自動回覆改定位至 AI 卡）、新增「詐騙警示中心」與「過濾關鍵字規則與命中動作」搜尋定位、AI 定位列支援「前往」直達對應頁面（自動回覆設定、詐騙警示中心、自訂標籤管理、API Key 設定），並於資料管理卡補齊桌面小工具加入說明。

### v1.12.0 更新重點（versionCode 13）

- **設定搜尋與通知收錄修正**（#99, #101）：拆分設定搜尋目錄的「同 key 重複節流」至通知卡並對齊副標說明；通訊類通知若 `postTime` 更新即收錄同內容連續訊息（`dedupCache` 僅擋斷線重播，非通訊類維持節流）。
- **資料完整性與安全強化**（#100, #102, #103, #104）：保留清理直接排除詐騙證據並清理孤兒標籤；`NotifIds` 集中管理互斥通知 ID；`AiClient.sanitizeForPrompt` 防護四個 AI Prompt Injection 注入點；SMS 自動回覆新增 4 小時冷卻、單日上限 3 次與範本自回防護。
- **技術債清理與效能優化**（#105）：過期提醒開機即觸發、`HeartbeatPolicy`/`WeeklySummaryPolicy` 改用 `UPDATE`、`PhantomPrefs` 節流表 30 秒快取與分層解耦、7 日／小時統計 SQL 聚合與 Widget 共用圖示 `LruCache`。

### v1.11.0 更新重點

- **連續訊息漏收修復與節流自訂**（#96）：實作內容感知同 Key 節流、疊加訊息尾巴提取、DB 視窗查證式防重複收錄與多層標題 fallback；設定頁新增「數字微變視為同一則更新」開關與 per-app 節流秒數覆寫。
- **AI 訊息助手回應截斷修復**（#95）：`chatAssistant` `maxTokens` 調升至 `4096` 並放寬 chat 專用逾時（`read 120s / callTimeout 180s`），偵測 `finish_reason == "length"` 時誠實標注截斷提示。
- **看門狗與熱路徑省電最佳化**（#94）：`ShizukuWatchdogService` 改為 5 分鐘／60 秒自適應退避、`requestRebind` 僅於未連線時觸發，並於通知熱路徑導入 `appLabelCache`（`LruCache(64)`）與 30 秒 TTL 規則快取。

---

## 安裝指南（免編譯直接安裝）

1. **從 GitHub Releases 下載 APK（正式發佈版）**
   - 前往本倉庫右側 **Releases** 頁面，選擇最新版本（如 `v2.1.0`）下載隨附的 `.apk` 檔案。

2. **或從 GitHub Actions 下載最新開發版 APK**
   - 開啟本倉庫的 GitHub 頁面，點選上方 **Actions** 分頁。
   - 在左側選擇 **Android Build Check**，點入最新一筆 `main` 分支且狀態為綠燈（✓）的執行紀錄。
   - 捲動至頁面底部的 **Artifacts** 區塊，下載 `phantomcheck-debug-<編號>` 壓縮檔並解壓縮，取得 `app-debug.apk`。

3. **在 Android 裝置安裝 APK**
   - 將 APK 傳至 Android 手機點擊安裝；若系統提示未知來源應用程式，請依引導開啟「允許來自此來源的應用程式」。

4. **開啟通知讀取權限與設定 API Key**
   - 依 App 內新手引導開啟「通知存取權」，並於「設定 → API Key 設定」輸入任意支援之 AI Provider Key（金鑰僅加密儲存於本機）。

---

## 技術架構

| 層級 | 技術選型 | 說明 |
|---|---|---|
| **語言與 UI** | Kotlin + Jetpack Compose（Material 3） | 宣告式 UI、Phantom 深淺主題系統與響應式狀態管理 |
| **本地資料庫** | Room Database | 儲存攔截訊息（`messages`）與過濾規則（`filter_rules`），含版本 Migration |
| **背景排程** | WorkManager | 定期執行訊息保留政策清理過期資料 |
| **網路與 AI** | Retrofit / OkHttp + Mistral API | 呼叫 Mistral 模型進行詐騙語意分析與風險評分 |
| **機密與安全** | EncryptedSharedPreferences（AndroidX Security Crypto） | 本機加密儲存使用者輸入的 API Key；無硬編碼機密 |
| **CI 管線** | GitHub Actions | 每個 PR 與 `main` push 自動執行 `gitleaks` 機密掃描與 `./gradlew :app:assembleDebug` 建置，並於 `main` 上傳 APK artifact |

---

## 安全政策

- 所有機密（API key、token、密碼）**永不寫入程式碼、文件或 Git 歷史**。
- 在地端開發時可將測試用 key 放於 `local.properties`（已由 `.gitignore` 排除），或直接透過 App 內設定頁輸入。
- 完整資安規範與漏洞回報方式請見 [SECURITY.md](SECURITY.md)。

---

## 開發協作

本專案由多位 AI agent 與人類協作者共同維護，詳細規範請參閱：

- 協作規則與角色分工：[AGENTS.md](AGENTS.md)
- 貢獻指南與 PR 流程：[CONTRIBUTING.md](CONTRIBUTING.md)
- 本地開發環境設定：[docs/LOCAL_SETUP.md](docs/LOCAL_SETUP.md)
