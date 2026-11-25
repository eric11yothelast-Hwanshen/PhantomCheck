# 隱私權政策（Privacy Policy）

**最後更新日期**：2026 年 11 月 19 日  
**適用對象**：幻訊 PhantomCheck（以下稱「本 App」）終端使用者

本文件以白話清楚說明本 App 如何讀取、處理、儲存與保護您的資料。本 App 的核心設計原則為**「資料留在您的手機裡，開發者看不到、也不會收集您的訊息」**。

---

## 一、我們收集哪些資料？

本 App 僅在您的裝置本機處理以下資訊，用於提供通知管理、防詐分析與統計功能：

1. **通知內容（透過系統「通知存取權」）**
   - 當您授予 `NotificationListenerService`（通知存取權）後，本 App 會被動接收系統通知廣播，讀取來源 App 名稱／套件名稱、通知標題（寄件者）、內文與時間戳記（含必要之對話識別資訊與通知頭像）。
   - 讀取通知不會開啟 LINE、Instagram、簡訊等原 App，因此**不會觸發原 App 的「已讀」狀態**。

2. **簡訊內容（僅於啟用 SMS 自動回覆時）**
   - 若您主動開啟 SMS 自動回覆功能並授予簡訊權限，本 App 的簡訊接收器（`SmsAutoReplyReceiver`）會在收到簡訊時讀取來電號碼與簡訊內容，以判斷是否符合自動回覆條件（包含單一號碼 4 小時冷卻、單日最多回覆 3 次、範本自回防護，以及跳過驗證碼／銀行／短網址等保護機制）。

3. **本機使用統計與個人設定**
   - 首頁與統計分頁顯示的訊息量、未讀數、風險趨勢與各 App 佔比等數據，皆由上述已儲存於您手機內的資料**在本機即時彙算**，不會從外部伺服器下載或回傳任何統計報表。

---

## 二、資料儲存在哪裡？會傳給誰？

### 1. 無自家伺服器、無帳號系統、無遙測／分析 SDK
- 本 App **沒有**開發者自建的後端伺服器，**不需要**註冊或登入任何帳號，也**未嵌入**任何第三方遙測、行為追蹤、廣告或崩潰分析 SDK（如 Firebase Analytics、Crashlytics 等）。
- 所有攔截到的訊息、過濾規則、記事、待辦事項與統計快取皆儲存於您裝置本機的 SQLite 資料庫（Room）中，**不會回傳給開發者**。

### 2. AI 詐騙分析與對話（BYOK：使用者自帶金鑰）
- 本 App 採用 **BYOK（Bring Your Own Key）** 模式，開發者不代收也不中轉您的 AI 請求。
- 僅當您主動於設定頁輸入 API Key 並啟用 AI 分析、深度分析、AI 助手對話或每日／每週摘要時，相關訊息內容才會**直接從您的手機傳送至您自行選擇與設定的 AI 服務商（Provider）**（例如 Mistral、OpenAI、Groq、OpenRouter、Gemini、DeepSeek 或您自訂的相容端點）。
- 當您已授權 Health Connect 並開啟「允許 AI 讀取健康摘要」且於 AI 助手進行健康類提問時，僅有本機聚合後的近 7 日睡眠、步數與心率「匯總數值」會隨提問送至您設定的 AI 服務商（原始生理時間序列絕不出本機；關閉該開關則完全不傳送）。
- 傳送至該 AI 服務商的資料將依**該服務商各自的隱私權政策與服務條款**處理；全程**不經過**本 App 開發者。

### 3. 公開防詐情報預查（零全文外傳）
- 在送出 AI 分析前，本 App 可先執行公開防詐情報預查：系統僅會從訊息中抽取 **URL 網址、短連結與加密貨幣地址（BTC / ETH / TRON）**，**絕不會傳送訊息全文或寄件者資訊**。
- 查詢順序為：**本機 72 小時快取 → `fraud.tw`（台灣 165 通報網域查詢，僅傳送網域主機名稱）→ Google Safe Browsing v4（僅傳送網址）→ 可選之 PhishTank（僅傳送網址）與 Chainabuse（僅傳送加密貨幣地址與鏈別）**。其中 PhishTank 與 Chainabuse 預設為關閉，僅於您手動開啟後才會連線查詢。

### 4. API Key 本機加密保護
- 您於設定頁輸入的所有 AI Provider API Key 與情報源 API Key（Safe Browsing、PhishTank、Chainabuse），皆透過 Android Jetpack Security 的 `EncryptedSharedPreferences`（AES-256 加密）儲存於本機裝置，**不會離開您的手機，也不會上傳至任何第三方或開發者**。

### 5. 版本更新檢查
- 當您使用手動「檢查更新」或開啟自動檢查更新時，本 App 僅會向 GitHub Releases API（`api.github.com`）發送**未認證（無帳號、無 Token）的公開 GET 請求**以取得最新版本號與發佈資訊，請求內容**不包含任何個人資料、訊息內容或裝置識別碼**。

### 6. 主動情報查詢（預設關閉、外送最小化）
- **預設關閉**：主動情報中樞（`proactive_intel_enabled`）預設為關閉；未開啟或啟用「省 Token 模式」時，背景 Worker 不會排程，亦不會發出任何 AI 呼叫或外部查詢。
- **外送最小化**：開啟後，背景查詢僅將您「關注主題清單」中的查詢關鍵字字串（`queryHint`＋「最新動態」，上限 200 字）直接傳送至您自行設定的 AI 服務商（建議於「API 金鑰設定」將「主動情報萃取與摘要」綁定 Gemini 3+ 設定檔，或設定 BYOK 外部搜尋端點）；**絕不傳送原始通知全文、聯絡人清單或 `privacyTier=sensitive` 敏感記憶**。
- **每日萃取隔離**：若開啟「每日自動萃取關注主題」，萃取階段會以本機 SQL 與程式碼雙層硬排除所有 `privacyTier=sensitive` 敏感記憶，所有素材經提示詞淨化（`sanitizeForPrompt`）後才送往您設定的 AI 服務商；萃取候選僅寫入本機資料庫待確認，絕不直接推播。
- **憑證與快取**：所有外部端點金鑰一律儲存於 Android `EncryptedSharedPreferences`，絕不寫入程式碼或日誌；查詢結果僅於本機 Room 資料庫暫存 12 小時供去重與簡報共用。

### 7. 區域網路連線（電腦端連結）／ LAN Connection (Desktop Link)
- **僅限區域網路（LAN-only）**：電腦端連結功能預設關閉；啟用後僅於您的本地 Wi-Fi 區域網路開啟加密 WebSocket（`wss://`）伺服器，**無任何雲端中繼、外部伺服器或開發者節點介入**。
  *(Disabled by default. When enabled, the server listens only on your local Wi-Fi LAN over encrypted `wss://` with **zero cloud relay or developer servers**.)*
- **傳輸加密與雙重驗證**：全程使用本機軟體產生之自簽 TLS 憑證（RSA 2048，私鑰存於 App 私有空間並以 `EncryptedSharedPreferences` 保護密碼），搭配 QR / 單次 6 位數配對碼（TTL 5 分鐘）、憑證 SHA-256 指紋綁定及 HMAC challenge-response 驗證；未知裝置或指紋不符一律立即拒絕並記錄。
  *(Protected by a locally generated self-signed TLS certificate, SHA-256 fingerprint pinning, single-use 6-digit pairing codes (5-minute TTL), and HMAC challenge-response authentication. Unknown instances or mismatched fingerprints are rejected and logged.)*
- **電腦端可讀寫範圍與硬排除紅線**：電腦端僅作為顯示器與遙控器，不永久落地您的訊息資料庫；僅傳輸訊息摘要白名單（寄件者、列表 90 字預覽／對話串檢視上限 200 字預覽、時間、風險標籤、星號、標籤與已讀狀態）、本機統計快照、待辦清單、記事清單、靜音 App 設定、手機裝置狀態（電量與充電方式、Wi-Fi／行動網路類型、可用儲存空間、訊息總數與未讀數、勿擾狀態與 App 版本）、AI 助手對話與白名單／標籤設定。**`privacyTier=sensitive` 敏感記憶、通訊錄原始內容、日曆原始內容與訊息全文絕不透過桌面端協定傳送。**
  *(The desktop client acts strictly as a remote display and controller without persisting your message database. Only whitelisted message previews (90 chars in list view, up to 200 chars in conversation thread view — partial preview only), local stats, todos, notes, muted app settings, phone device status (battery level & charging type, Wi-Fi/cellular network type, available storage, message/unread counts, DND state, and app version), AI chat, and whitelist/tag rules are transmitted. **Sensitive memories (`PRIVACY_SENSITIVE`), raw contacts, raw calendar entries, and full message bodies are never transmitted.**)*
- **指令審計與平台限制**：所有來自電腦端的寫入與查詢指令皆寫入手機端審計紀錄（上限 200 筆）；Android Doze 休眠或關閉螢幕時，系統可能暫停區網連線（屬作業系統平台限制）。電腦端之配對憑證（`link.properties`，含 HMAC 金鑰）儲存於各作業系統之個人專屬權限目錄（Windows 為 `%APPDATA%\PhantomCheckDesktop`，Linux 為 `~/.config/PhantomCheckDesktop`，並套用僅擁有者可讀寫權限）；因純 JVM 桌面端尚未串接原生 Windows DPAPI／macOS Keychain 加密，建議於受信任之個人帳戶使用並啟用系統磁碟加密。您隨時可在手機「桌面端連線」頁面一鍵「移除配對」，立即撤銷該電腦的 HMAC 憑證並清除其審計紀錄。
  *(Desktop commands are recorded in an on-device audit log (up to 200 entries). Android Doze or screen-off power saving may pause LAN connections as a platform limitation. On desktop, pairing credentials (`link.properties`, including the HMAC secret) are stored in the per-user ACL directory (`%APPDATA%\PhantomCheckDesktop` on Windows, `~/.config/PhantomCheckDesktop` on Linux) with owner-only file permissions where supported; because the pure-JVM desktop client does not yet integrate native Windows DPAPI / macOS Keychain encryption, use a trusted OS account with disk encryption enabled. You can tap "Unpair" at any time in Desktop Link settings to revoke the device's HMAC secret and clear its audit log.)*
- **Windows 防火牆一鍵放行（僅手動觸發）**：桌面端「設定中心 → 連線配對」提供免管理員之防火牆規則與網路分類（Private / Public）唯讀檢查；僅於您主動點擊「一鍵允許防火牆」時，才會透過系統 UAC 提權執行 `New-NetFirewallRule`，專門新增 PhantomCheck 的 TCP 輸入允許規則（`PhantomCheck Desktop (Link)` 與 `PhantomCheck Desktop (App)`），**絕不停用系統防火牆、絕不修改其他程式規則、絕不於背景自行提權**，且診斷紀錄僅記載規則狀態與埠號。
  *(**Windows Firewall One-Click Allow (User-Triggered Only)**: Desktop Settings → Connection performs a read-only, non-admin check of firewall rules and network profile categories (Private / Public). Only when you explicitly click "One-Click Allow Firewall" does it request UAC elevation to run `New-NetFirewallRule` solely for PhantomCheck's dedicated inbound TCP allow rules (`PhantomCheck Desktop (Link)` and `PhantomCheck Desktop (App)`). It **never disables Windows Firewall, never modifies other rules, and never elevates in the background**; diagnostic logs record only rule status and port numbers.)*

### 8. 指定來源 AI 分析（群組報告）／ Designated Source AI Analysis (Group Reports)
- **預設關閉、手動選定**：群組報告功能不會自動監控任何 App 或群組；僅於您在設定頁手動選定特定 App 與群組並啟用後，背景排程（WorkManager）才會針對該群組自上次報告以來的新訊息（雙層硬排除您自行回覆之 `isOwn=true` 訊息）進行摘要。
  *(**Opt-in Only**: Group Reports never monitor any app or group automatically. Only after you manually select specific apps and groups and enable them in Settings does a background WorkManager job summarize new messages received since the last report, strictly excluding your own replies (`isOwn=true`) at both SQL and application layers.)*
- **BYOK 直連與額度保護**：選定群組的訊息經提示詞淨化（`sanitizeForPrompt`）後，直接從您的手機傳送至您自行設定的 AI 服務商（BYOK，不經開發者伺服器），並受「省 Token 模式」與每日 AI 額度上限控管；超額或無網路時自動順延，絕不額外外傳其他未選定之群組或敏感記憶。
  *(**BYOK Direct & Quota-Gated**: Selected group messages are sanitized via `sanitizeForPrompt` and sent directly from your phone to your configured BYOK AI provider (never through developer servers), gated by Token Saver mode and daily AI quotas. Other unselected groups and sensitive memories are never included.)*
- **本機儲存與隨時關閉**：生成的報告依您的選擇儲存於本機獨立報告檢視（`group_reports`）或記事（`notes`），您可隨時停用或刪除任何目標與報告紀錄。
  *(**On-Device Storage & Instant Control**: Generated reports are stored locally either in the dedicated report viewer (`group_reports`) or in Notes (`notes`) per your choice, and you can disable or delete any target or report at any time.)*

---

## 三、資料匯出與刪除

- **主動匯出 CSV**：您可以在 App 內一鍵將訊息匯出為 CSV 檔案至手機的 `Downloads`（下載）資料夾。此操作完全由您主動觸發，檔案僅存於您的裝置中。
- **App 內清理**：您可於 App 內單筆刪除、批次刪除訊息，或透過「訊息保留政策」定期自動清理過期資料（受保護之高風險詐騙證據可依設定保留）。
- **解除安裝即全面刪除**：由於所有資料庫、快取、設定與加密儲存的 API Key 皆存放於本 App 的裝置私有空間，**當您解除安裝本 App 時，系統即會刪除全部本機資料與金鑰**（您先前主動匯出至系統 `Downloads` 資料夾的 CSV 檔案除外，需由您自行管理刪除）。

### 9. 跨端剪貼簿同步與跨端實用工具（#518）／ Cross-End Clipboard Sync & Utility Tools

- **預設關閉（Opt-in）**：跨端剪貼簿同步於手機與電腦端皆**預設關閉**，須由使用者於雙端主動開啟後才會運作。
- **僅限區網 TLS 配對通道**：所有剪貼簿文字、快速筆記、手機通知推送、尋找手機響鈴與網址互開指令，皆僅透過使用者本機區域網路內之 WSS + 憑證指紋釘選（TLS Pinning）配對連線直接點對點傳輸，**不經過任何雲端伺服器或第三方中繼**。
- **安全與隱私防護**：
  - 剪貼簿同步僅支援純文字，單筆上限 5,000 字元；
  - 內建**敏感格式保護**（預設開啟），沿用本機 `OtpDetector` 規則自動辨識並攔截一次性驗證碼（OTP）／4–8 位數驗證碼，絕不推送至對端；
  - 受限於 Android 10+ 系統隱私規範，手機端剪貼簿僅於 App 位於前景時由使用者主動推送，絕不在背景靜默監聽剪貼簿；
  - 網址互開嚴格驗證僅允許 `http://` 與 `https://` 協定，封鎖 `file://`、`javascript:`、`intent://` 等危險協定；所有跨端指令均寫入本機連線審計紀錄並受既有速率限制保護。

**English**: Cross-end clipboard sync is **disabled by default** on both phone and desktop. When explicitly enabled, plain text (capped at 5,000 characters) and cross-end utility commands (quick notes, phone notifications, Find-My-Phone ring, and `http://`/`https://`-only URL handoff) travel strictly over your paired local-network TLS-pinned WebSocket connection with **zero cloud relay**. Built-in OTP protection (enabled by default, reusing `OtpDetector`) automatically blocks one-time verification codes from being pushed. Respecting Android 10+ system privacy restrictions, phone clipboard reading occurs only in the foreground upon explicit user action.

---

## 四、聯絡管道與問題回報

若您對本隱私權政策或資料處理方式有任何疑問，歡迎透過以下方式聯繫：

- **一般問題與建議**：請於 GitHub 倉庫提出 Issue：[PhantomCheck Issues](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/issues)
- **資安漏洞回報**：請勿直接公開張貼，請依 [SECURITY.md](SECURITY.md) 指引透過 GitHub Security Advisory 私密回報。
