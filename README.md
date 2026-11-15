# 幻訊 PhantomCheck

Android 智慧防詐訊息管理 App：在不觸發原 App 已讀的前提下攔截通知預覽，結合關鍵字過濾、AI 詐騙風險分析、詐騙警示中心與 SMS 自動回覆，守護日常通訊安全。

---

## 核心功能

- 📩 **通知攔截預覽**：透過 `NotificationListenerService` 即時讀取通知內容，無需開啟 LINE、IG 或簡訊等原 App，不觸發「已讀」。
- 🛡️ **AI 詐騙分析**：串接 Mistral API 自動評估訊息詐騙風險（0–100 分），標註 `safe` / `suspicious` / `scam` 等級並提供風險原因解析。
- 🚫 **關鍵字過濾（含指定 App）**：自訂過濾關鍵字並支援「全部 App」或「指定 App Package」範圍，命中後直接標記為已過濾，節省 AI 分析額度。
- 🗂️ **三 Tab 瀏覽與跨 App 搜尋**：主畫面提供「全部／警示／已過濾」三 Tab 分類檢視，並支援跨 App 關鍵字與寄件者搜尋。
- 🚨 **詐騙警示中心**：集中顯示高風險詐騙訊息，點擊可進入風險詳情頁檢視完整分析依據與寄件資訊。
- ⏰ **SMS 自動回覆**：可設定啟用開關、回覆內容與指定時段，於收到簡訊時自動回覆；通訊軟體通知亦可搭配快速回覆。
- 📄 **CSV 匯出**：於設定頁一鍵將攔截的訊息紀錄匯出為 CSV 檔案至手機 `Downloads` 資料夾，方便備份或稽核。
- 🧹 **訊息保留政策**：透過背景排程自動清理超過保留天數的舊訊息，避免本地資料庫無限膨脹。
- 🔑 **App 內 API Key 設定**：支援直接在 App 內輸入與管理 Mistral API Key，無須手動修改 `local.properties` 重新編譯。
- 🎨 **深淺主題切換**：採用 Phantom 深紫品牌色系（搭配紅色詐騙警示語義色），支援「跟隨系統／強制深色／強制淺色」即時切換。

---

## 安裝指南（免編譯直接安裝）

1. **從 GitHub Actions 下載 APK**
   - 開啟本倉庫的 GitHub 頁面，點選上方 **Actions** 分頁。
   - 在左側選擇 **Android Build Check**，點入最新一筆 `main` 分支且狀態為綠燈（✓）的執行紀錄。
   - 捲動至頁面底部的 **Artifacts** 區塊，下載 `phantomcheck-debug-<編號>` 壓縮檔並解壓縮，取得 `app-debug.apk`。

2. **在 Android 裝置安裝 APK**
   - 將 `app-debug.apk` 傳至 Android 手機（或直接在手機瀏覽器下載）。
   - 點擊 APK 檔案進行安裝；若系統提示「為了安全起見，您的手機目前不允許安裝來自這個來源的未知應用程式」，請依畫面引導開啟「允許來自此來源的應用程式」後完成安裝。

3. **開啟通知讀取權限**
   - 首次開啟「幻訊 PhantomCheck」後，點擊畫面上方的權限引導（或前往手機「設定 → 應用程式 → 特殊應用程式存取權 → 通知存取權」）。
   - 找到 **幻訊 PhantomCheck** 並開啟開關，授權 App 讀取通知以進行預覽與防詐分析。

4. **在 App 內輸入 Mistral API Key**
   - 回到 App 主畫面，點擊右上角 **API Key 設定**（鑰匙圖示）進入設定頁。
   - 輸入你的 Mistral API Key 並點擊儲存。金鑰會透過 `EncryptedSharedPreferences` 加密儲存於本機，不會上傳至任何第三方伺服器。
   - 完成後即可開始接收通知並進行 AI 詐騙偵測。

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
