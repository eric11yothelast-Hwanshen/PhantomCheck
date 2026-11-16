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
- 🚫 **智慧通知去重與過濾**：自動過濾持續性通知、群組摘要與 10 分鐘內重複通知，支援關鍵字過濾（含指定 App）與 App 靜音清單。
- 📊 **常駐統計通知與儀表板**：自訂 `RemoteViews` 常駐通知左側固定顯示幻訊圖示、右側「左新右舊」呈現最近通知 App 並標記最新一則，點擊遵循原 App／幻訊設定（滑掉無縫原地重建 + 15 分鐘心跳 + Shizuku 跨行程看門狗）；App 內提供 4 Tab 統計儀表板。
- 🤖 **AI 助手、昨日簡報與自然語言建規則**：內建唯讀 RAG 對話助手，支援多會話切換、`PhantomMarkdownText` 渲染、「昨日簡報」，以及透過對話提出新增白名單／標籤規則（強制彈出確認對話框防範 prompt injection）。
- 🏷️ **AI 智慧標記、App 分類與規則管理**：單次 AI 分析支援 0~3 個標籤＋零 token App 分類規則自動疊加；支援自訂自然語言白名單與標籤規則；氣泡顯示最多 3 個標籤色點。
- 💬 **App 分組與聊天式對話頁**：依 App 與寄件者分組瀏覽，支援我的最愛與 VIP 寄件者置頂、對話 keyset 分頁載入（LIMIT 200）、左滑氣泡刪除、平面清單多選批次操作。
- 🧭 **視覺化新手與設定教學**：10 步主頁高光引導＋6 步設定頁導覽，支援淺色、深色、動態取色三模式高對比閱讀與洞內觸控穿透。
- 🔋 **省 token 模式與省電優化**：一鍵開啟省 token 模式；省電模式下自動停用玻璃模糊；支援關閉液態玻璃模糊效果以節省 GPU。
- 🚨 **詐騙警示中心、勿擾時段與 SMS 自動回覆**：集中顯示高風險詐騙訊息、情報來源與趨勢比較；支援勿擾時段與 SMS 自動回覆。
- 📄 **CSV 匯出、保留政策與加密金鑰**：一鍵匯出 CSV 至 `Downloads`；`EncryptedSharedPreferences` 本機加密儲存 AI 與情報源（Safe Browsing／PhishTank／Chainabuse）API Key，支援設定頁直接輸入（`local.properties` 為 fallback）。

---

## 版本紀錄

完整變更紀錄請參閱 [CHANGELOG.md](CHANGELOG.md)。

### v1.10.0 更新重點

- **標籤全面顯示與效能優化**（#83）：對話氣泡、訊息清單、App 對話串與詳情頁統一顯示標籤 chips（含 `· AI` 標記），採單次 `IN` 批次查詢避免 N+1 查詢退化。
- **介面精簡與全域排版統一**（#84, #85, #86, #89）：每日／本週 AI 摘要卡預設收合＋狀態持久化；首頁篩選欄改用 `FlowRow` 自動換行免滑動；頂部標題列縮小至 `titleMedium`／52dp；全頁統一 16dp 邊距與 12dp 卡片間距。
- **設定頁搜尋與個人化設定**（#88, #90）：本地關鍵字／別名即時過濾＋AI 語意導覽定位；新增卡片密度、首頁排序、篩選欄自訂、摘要預設收合與 12/24 小時制。
- **OTP 驗證碼複製、統計圖卡分享與待辦提醒**（#91）：強訊號 OTP 一鍵複製、純本機 Canvas 產出週報圖片分享、訊息「稍後處理」排程通知與重開機重建（Room `MIGRATION_7_8`）。
- **多尺寸桌面小工具**（#87）：新增 2x1 迷你與 4x2 詳情（含 7 日迷你趨勢條與最近來源 App），連同 4x1 皆支援 `resizeMode`、`xml-v31` `targetCell`、深淺主題與差異刷新。

---

## 安裝指南（免編譯直接安裝）

1. **從 GitHub Releases 下載 APK（正式發佈版）**
   - 前往本倉庫右側 **Releases** 頁面，選擇最新版本（如 `v1.10.0`）下載隨附的 `.apk` 檔案。

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
