# 更新日誌（CHANGELOG） / Changelog

本專案遵循 [Keep a Changelog](https://keepachangelog.com/zh-TW/1.1.0/) 格式，並採用語意化版本控制。
This project adheres to [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and uses Semantic Versioning.

## [4.7.0] - 2026-11-24

- **Tag 對照**：[`v4.6.0...v4.7.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.6.0...v4.7.0)（`versionCode = 55`、`versionName = "4.7.0"`；桌面端 `desktopVersion = "0.4.0"`）
- **發佈資產（雙 APK ＋ 桌面端 Windows MSI / Inno Setup 安裝檔）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。
  - `PhantomCheckDesktop-0.4.0.msi` / `PhantomCheckDesktop-0.4.0-setup.exe`：Windows 桌面端連線與訊息管理安裝檔（Compose Multiplatform，`desktopVersion = "0.4.0"`）。

### 行為變更（Behavior Changes）
- **#476 連線方向反轉與憑證指紋綁定（PR #481）**：桌面端改為內建 Ktor Netty WSS 伺服器（監聽 `wss://0.0.0.0:49515/link`），手機端改為 OkHttp WebSocket 主動連線並驗證 TLS SHA-256 指紋。
- **#476 / #477 協定升版強制重配對（PR #481）**：`PROTOCOL_VERSION` 由 `1` 升至 `2`，配對碼效期調整為 3 分鐘單次有效；舊版配對資料會安全清除並引導重新配對。
- **#477 / #478 / #479 桌面端功能與視窗調整（PR #481）**：桌面端新增 QR 配對對話框、啟動單次更新檢查、更新對話框與「關於」分頁；自訂設定由 8 項擴充為 16 項；視窗最小尺寸調整為 `1024x680`（確保 `fontScale=1.25f` 不裁切）。
- **#476 / #480 限流與指令逾時回饋（PR #481）**：桌面端限流改為超限丟棄並回傳 `ERROR(RATE_LIMIT_EXCEEDED)`，連續 3 次才斷線；一般指令 8.5 秒／AI 長指令 130 秒逾時皆提供 UI 回饋與診斷記錄。

### 新增、變更與修正
- **#476 桌面連線架構反轉與診斷可視化（PR #481）**：桌面端新增 `DesktopTls.kt`（RSA 2048 自簽 X509v3 PKCS12 KeyStore，密碼存於 owner-only ACL 目錄）、`DesktopLinkServer.kt`（Ktor `3.6.0` Netty＋`sslConnector`，隨系統匣／`--minimized` 常駐，含埠佔用預檢、Token Bucket 限流與桌面審計紀錄）與 `DesktopMdnsBroadcaster.kt`（JmDNS `3.6.5` 廣播 `_phantomcheck._tcp.local.`）；Inno Setup 新增可選防火牆例外任務（解除安裝可逆清除）。手機端新增 `DesktopLinkClient.kt`（OkHttp WSS＋TLS SHA-256 指紋綁定＋指數退避 `1s→30s`＋`NsdManager` 掃描，保留完整 M2／#467 雙向指令與隱私紅線），移除手機端 Ktor/BouncyCastle 伺服器依賴與舊檔，雙端紀錄連線階段（`TCP`/`TLS`/`HANDSHAKE`/`PROTOCOL`）與分類錯誤碼。
- **#477 手機掃描電腦端 QR Code 配對（PR #481）**：桌面端引入 `com.google.zxing:core:3.5.4` 新增 `DesktopQrDialog.kt`（支援多網卡 IPv4 選擇與 3 分鐘倒數重生）；手機端引入 **bundled** `com.google.mlkit:barcode-scanning:17.3.0`（模型內建於 App，無 Google Play 服務裝置亦可離線掃描）與 CameraX `1.6.2`，新增 `QrPairingParser.kt`（嚴格 JSON/型別/指紋格式防呆）與 `QrScannerOverlay.kt`（離開與 `ON_PAUSE`/`ON_STOP` 立即 `unbindAll()`），掃碼後顯示確認卡並走 TLS 指紋綁定，保留手動輸入電腦區網位址備援與 `CAMERA` 權限引導。
- **#478 桌面端啟動自動檢查更新、更新提示與關於頁（PR #481）**：新增 `DesktopUpdater.kt`（process 級 `AtomicBoolean` 啟動單次檢查，離線／403／429 靜默記日誌；比對 `PhantomCheckDesktop-<semver>-setup.exe`/`.msi` 資產與 `CURRENT_DESKTOP_VERSION`；每日同版本通知去重且尊重 DND）與 `DesktopUpdateDialog.kt`（`DesktopMarkdownText` 瀏覽更新內容、前往 Release 頁面或下載至暫存並確認啟動安裝器）；設定中心新增「關於」分頁顯示版本、`PROTOCOL_VERSION=2` 相容資訊、專案文件連結與最近 10 個 Releases 瀏覽。
- **#479 桌面端語言切換即時生效與自訂擴充 8→16（PR #481）**：修復 `DesktopStrings.isZh` 未訂閱 Compose `SnapshotState` 的問題，改由 `mutableStateOf` 驅動並同步 `trayTooltip` 與全設定 enum 在地化，切換語言即刻全視窗／系統匣生效；自訂設定由 8 項擴充為 16 項（新增自訂強調色 HEX、12/24 小時制、訊息卡圓角 12–24dp、玻璃模糊通透強度、AI 聊天字級、通知音效開關、啟動自動接收已配對手機、啟動預設分頁），並升級支援跨日之 DND 起訖時間選擇器。
- **#480 雙端十項穩定性稽核修復（PR #481）**：完成 10 項稽核修復，包含桌面指令逾時追蹤（8.5s / 130s）與 UI 回報、連線 catch 記錄診斷不吞例外、`RATE_LIMIT_EXCEEDED` 連續 3 次才斷線與首發 11 指令 120ms 間隔防觸發、UTF-8 `Properties` 讀寫與 ISO-8859-1 相容遷移、聊天 `LazyColumn` `itemsIndexed` 複合唯一 key、全分類配對錯誤雙語文案、JmDNS 失敗診斷與 UI 引導、QR JSON 嚴格防呆驗證、設定分頁列改 `FlowRow` 搭配 `1024x680` 最小視窗支援 `125%` 字級縮放，以及 `CAMERA` 全版本相容與非 saveable 掃碼狀態清理。

### English
- **Tag Compare**: [`v4.6.0...v4.7.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.6.0...v4.7.0) (`versionCode = 55`, `versionName = "4.7.0"`; Desktop `desktopVersion = "0.4.0"`)
- **Release Assets (Dual APKs + Desktop Windows MSI / Inno Setup Installer)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.
  - `PhantomCheckDesktop-0.4.0.msi` / `PhantomCheckDesktop-0.4.0-setup.exe`: Windows desktop companion installers (Compose Multiplatform, `desktopVersion = "0.4.0"`).

**Behavior Changes**
- **#476 Socket Direction Reversal & Certificate Fingerprint Pinning (PR #481)**: Desktop now hosts the embedded Ktor Netty WSS server (`wss://0.0.0.0:49515/link`), while the Android app connects as an OkHttp WebSocket client with TLS SHA-256 fingerprint pinning.
- **#476 / #477 Protocol Bump & Mandatory Re-Pairing (PR #481)**: `PROTOCOL_VERSION` bumped from `1` to `2` with single-use 3-minute pairing codes; legacy pairing records are safely cleared with re-pairing guidance.
- **#477 / #478 / #479 Desktop Features & Window Sizing (PR #481)**: Added QR pairing dialog, single launch-time update check, update dialog, and About tab on desktop; expanded customization settings from 8 to 16; adjusted minimum window size to `1024x680` (no clipping at `fontScale=1.25f`).
- **#476 / #480 Rate Limiting & Command Timeout Feedback (PR #481)**: Desktop rate limiter drops excess commands with `ERROR(RATE_LIMIT_EXCEEDED)` and disconnects only after 3 consecutive violations; 8.5s general / 130s AI command timeouts surface UI feedback and diagnostics.

**Added, Changed & Fixed**
- **#476 Desktop Link Architecture Reversal & Diagnostics (PR #481)**: Added `DesktopTls.kt` (RSA 2048 self-signed X509v3 PKCS12 KeyStore with passphrase in owner-only ACL storage), `DesktopLinkServer.kt` (Ktor `3.6.0` Netty + `sslConnector`, tray/`--minimized` lifecycle, port-in-use preflight, Token Bucket rate limiting, and desktop audit log), and `DesktopMdnsBroadcaster.kt` (JmDNS `3.6.5` advertising `_phantomcheck._tcp.local.`); Inno Setup adds an optional reversible firewall task. Android adds `DesktopLinkClient.kt` (OkHttp WSS + TLS SHA-256 pinning + `1s→30s` exponential backoff + `NsdManager` discovery while preserving all M2/#467 two-way handlers and privacy boundaries), removes embedded Ktor/BouncyCastle server dependencies, and tracks connection phases (`TCP`/`TLS`/`HANDSHAKE`/`PROTOCOL`) and classified error codes on both ends.
- **#477 Phone QR Code Scanning for Desktop Pairing (PR #481)**: Desktop integrates `com.google.zxing:core:3.5.4` (`DesktopQrDialog.kt` with site-local IPv4 selector and 3-minute countdown/regeneration); Android integrates **bundled** `com.google.mlkit:barcode-scanning:17.3.0` (works offline without Google Play Services) and CameraX `1.6.2`, adding `QrPairingParser.kt` (strict JSON/type/fingerprint validation) and `QrScannerOverlay.kt` (immediate `unbindAll()` on leave/`ON_PAUSE`/`ON_STOP`) with a pairing confirmation card, manual LAN IP fallback, and `CAMERA` permission guidance.
- **#478 Desktop Launch Auto-Update Check, Update Dialog & About Tab (PR #481)**: Added `DesktopUpdater.kt` (process-level `AtomicBoolean` single check at launch, silent diagnostic logging on offline/403/429, semver comparison against `PhantomCheckDesktop-<semver>-setup.exe`/`.msi` assets and `CURRENT_DESKTOP_VERSION`, daily per-version notification dedup respecting DND) and `DesktopUpdateDialog.kt` (`DesktopMarkdownText` changelog viewer, browser link to Release page, and temp download + confirmed installer launch); added an "About" tab in Settings showing version, `PROTOCOL_VERSION=2` compatibility, project links, and the latest 10 GitHub Releases.
- **#479 Instant Desktop Language Switch, Full Localization & 8→16 Customizations (PR #481)**: Fixed `DesktopStrings.isZh` to use Compose `mutableStateOf` so switching language updates all windows, `trayTooltip`, and localized setting enums immediately; expanded customization settings from 8 to 16 (custom accent HEX, 12/24h time, message card corner radius 12–24dp, glass blur intensity, AI chat font size, notification sound toggle, auto-accept paired phones on launch, default startup tab) and upgraded DND to a cross-midnight start/end time picker.
- **#480 Cross-End 10-Item Stability Audit Fixes (PR #481)**: Resolved 10 audited issues including desktop command timeout tracking (8.5s / 130s) with UI reporting, diagnostic/audit logging instead of swallowed exceptions, 3-strike `RATE_LIMIT_EXCEEDED` disconnect + `120ms` pacing for the 11 initial commands, UTF-8 `Properties` read/write with ISO-8859-1 fallback, composite unique keys in chat `itemsIndexed`, bilingual classified pairing error messages, JmDNS failure diagnostics and UI hints, strict QR JSON validation, `FlowRow` settings tabs with `1024x680` minimum size for `125%` font scaling, and lifecycle-safe `CAMERA` permission/scanner state handling.

## [4.6.0] - 2026-11-24

- **Tag 對照**：[`v4.5.0...v4.6.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.5.0...v4.6.0)（`versionCode = 54`、`versionName = "4.6.0"`；桌面端 `desktopVersion = "0.3.0"`）
- **發佈資產（雙 APK ＋ 桌面端 Windows MSI / Inno Setup 安裝檔）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。
  - `PhantomCheckDesktop-0.3.0.msi` / `PhantomCheckDesktop-0.3.0-setup.exe`：Windows 桌面端連線與訊息管理安裝檔（Compose Multiplatform，`desktopVersion = "0.3.0"`，新增 Inno Setup 免管理員安裝精靈）。

### 新增、變更與修正
- **#465 Netty 修復桌面連線閃退（PR #473）**：將手機端內嵌 WSS 伺服器由 `ktor-server-cio` 改為 `ktor-server-netty` `3.6.0`（保留 `sslConnector` 與輕量化執行緒池 `1/2/4`）；指令限流改為 Token Bucket（5/秒＋突發 12）並搭配桌機初始 7 指令 100ms 間隔；`remoteIp` 實取 `origin.remoteAddress`；`markRead` 對話分支改用專用 DAO（Room 版號不動）；新增埠佔用預檢與雙語提示，並補齊 ProGuard Netty keep/dontwarn 規則與 `packaging` excludes。
- **#466 手機底部欄「連線」分頁（PR #474）**：`MainTab` 由 5 欄擴充為 6 欄，新增「連線（Link）」分頁與自繪 `LinkDevicesIcon`；抽出共用 `LinkHubContent.kt`，`LinkHubActivity` 與分頁共操作同一 singleton `DesktopLinkServer` 並定時呼叫 `touchActivity()` 防止停留期間誤觸 30 分鐘閒置停置；`LiquidGlassTabBar` 動態平分 6 欄，`Onboarding` 改採 key 錨點。
- **#467 7 項雙向同步擴充（PR #474，`PROTOCOL_VERSION` 維持 1）**：全面經已認證 WSS session、Token Bucket 限流與審計紀錄運行（Room schema 版號不動）：① 星標雙向同步（`messages.star.toggle`＋`message.star.sync` 廣播）；② 訊息標籤套用與移除（`messages.tag.apply`/`remove`，`source="desktop"`）；③ 待辦完整 CRUD（`todos.add`/`edit`/`delete`，複用 `ReminderScheduler` 與 `NotifIds`）；④ 記事清單與新增（`notes.list`/`note.add`，含去重與關聯訊息）；⑤ 單則深度分析（`message.deepAnalyze`，複用 `AiClient.deepAnalyzeMessage`、計入每日額度並於失敗時退款）；⑥ 靜音 App 清單查詢與切換（`muted.apps.list`/`toggle`，複用 `PhantomPrefs`）；⑦ 對話串檢視（`messages.conversation`，上限 200 字預覽）＋手機狀態（`phone.status.get`，電量%／充電狀態／運行時長）＋多會話聊天（`chat.sessions.list`/`chat.session.new`）。
- **#468 桌面設定中心（10 系統＋8 自訂）與玻璃 UI／圖表／Markdown 渲染（PR #474）**：模組化拆分 `DesktopSettings`、`DesktopTheme`、`DesktopMarkdown`、`DesktopStatsCanvas`、`DesktopSettingsPanel`、`DesktopDiagnostics`；新增 10 項系統設定（語言即時切換、Windows `HKCU\...\Run` 開機自啟 `--minimized`、關閉行為、原生通知＋00:00–07:00 勿擾、退避上限 15/30/60s、心跳 10/15/30s、預覽長度 90/200/500、配置管理、手動檢查 GitHub Releases、3 份旋轉診斷日誌）與 8 項自訂設定（6 強調色、密度、字級縮放、側欄寬度/折疊、訊息卡樣式、鏡射顯示模式、4 漸層＋自訂本機背景圖含 scrim、動畫開關）；大螢幕採最小 `1180x760` 記憶視窗、訊息 Master-Detail、Canvas 自繪 7 日／24 小時柱狀圖與風險分佈、AI 助手多會話＋Markdown 渲染＋複製＋快速指令＋`Ctrl+Enter` 送出，以及 `Ctrl+1..6`、`Ctrl+F`、`Esc` 鍵盤捷徑。
- **#469 桌面系統匣常駐（預設縮匣，PR #474）**：採用 Compose Multiplatform `1.12.1` 官方 `Tray` API，將 `DesktopLinkClient` 與 `MdnsScanner` 生命週期提升至 `application {}`，關閉視窗預設縮至系統匣維持連線、鏡射與重連；透過 `TrayState.sendNotification` 發送原生通知（受原生通知開關與勿擾時段控制）；支援 `--minimized`/`--tray` 啟動參數，不支援系統匣之環境誠實降級為關閉即結束。
- **#470 Inno Setup 安裝器與雙資產發布（PR #474）**：新增 `desktop/packaging/windows/PhantomCheckDesktop.iss`（固定 `AppId`、per-user `%LOCALAPPDATA%` 免管理員、預設勾選建立桌面捷徑與安裝後立即開啟、解除安裝清理 `HKCU\...\Run` 並保留 `%APPDATA%` 配對設定）；`desktop/build.gradle.kts` 單一來源版本 `desktopVersion = "0.3.0"`，`.github/workflows/build.yml` 與 `release.yml` 同步產出 `.msi` 與 `*-setup.exe` 雙資產。
- **#471 兩端穩定性稽核修復與 v4.5.0 之後的修正（PR #473 / #474）**：`DesktopPaths.kt` 將桌機 `link.properties`（含 `hmacSecret`）遷至 per-user ACL 目錄（`%APPDATA%\PhantomCheckDesktop`／`~/.config/PhantomCheckDesktop`）並設僅擁有者讀寫（`PRIVACY.md` 雙語揭露純 JVM 未接 DPAPI/Keychain）；`DesktopLinkClient.sendCommand` 改回傳 `Boolean` 並於斷線時誠實提示；`CHAT_SEND` 新增 130 秒逾時保護與斷線即時重置；聊天 `LazyColumn` 補齊穩定 `key`；`DesktopLinkServer` 改 per-client `clientCursors` 消除雙裝置漏推競態、`activeSessions.remove(id, sess)` 防止重連誤刪新連線、`NsdManager.onRegistrationFailed` 記錄 `Log.w` 與 `lastError`；`MdnsScanner.stop()` 移出 AWT EDT 關閉 `JmDNS`；全數 `gson.fromJson` 包裹 `runCatching` 並正確重拋 `CancellationException`（與 `#465` 共同完成 `v4.5.0` 之後的連線與穩定性修正）。

### English
- **Tag Compare**: [`v4.5.0...v4.6.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.5.0...v4.6.0) (`versionCode = 54`, `versionName = "4.6.0"`; Desktop `desktopVersion = "0.3.0"`)
- **Release Assets (Dual APKs + Desktop Windows MSI / Inno Setup Installer)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.
  - `PhantomCheckDesktop-0.3.0.msi` / `PhantomCheckDesktop-0.3.0-setup.exe`: Windows desktop companion installers (Compose Multiplatform, `desktopVersion = "0.3.0"`, adding Inno Setup per-user wizard).

**Added, Changed & Fixed**
- **#465 Netty Fix for Desktop Link Crash (PR #473)**: Replaced `ktor-server-cio` with `ktor-server-netty` `3.6.0` on Android's embedded WSS server (preserving `sslConnector` and lightweight `1/2/4` thread pools); upgraded command rate limiting to Token Bucket (`5/s` + burst `12`) paired with `100ms` pacing for the desktop's initial 7 startup commands; resolved `remoteIp` from `origin.remoteAddress`; moved `markRead` conversation branch to a dedicated DAO (Room schema unchanged); added port-in-use preflight with bilingual errors, ProGuard Netty keep/dontwarn rules, and `packaging` excludes.
- **#466 Phone Bottom Bar "Link" Tab (PR #474)**: Expanded `MainTab` from 5 to 6 tabs by adding `LINK` ("Link" / "連線") with a custom-drawn `LinkDevicesIcon`; extracted shared `LinkHubContent.kt` so `LinkHubActivity` and the tab operate the same `DesktopLinkServer` singleton while calling `touchActivity()` periodically to prevent 30-minute idle shutdown while foregrounded; `LiquidGlassTabBar` dynamically distributes 6 tabs and `Onboarding` uses key-based anchors.
- **#467 7 New Two-Way Sync Capabilities (PR #474, `PROTOCOL_VERSION = 1` unchanged)**: All routed over authenticated WSS sessions with Token Bucket rate limiting and audit logging (Room schema unchanged): (1) two-way starring (`messages.star.toggle` + `message.star.sync` broadcast); (2) message tag apply/remove (`messages.tag.apply`/`remove`, `source="desktop"`); (3) full to-do CRUD (`todos.add`/`edit`/`delete`, reusing `ReminderScheduler` and `NotifIds`); (4) note listing and creation (`notes.list`/`note.add`, with dedup and source-message linking); (5) single-message deep analysis (`message.deepAnalyze`, reusing `AiClient.deepAnalyzeMessage`, counting toward daily quota with automatic refund on failure); (6) muted app management (`muted.apps.list`/`toggle`, reusing `PhantomPrefs`); (7) conversation thread view (`messages.conversation`, capped at 200-char preview) + phone status (`phone.status.get`, battery %, charging state, uptime) + multi-session chat (`chat.sessions.list`/`chat.session.new`).
- **#468 Desktop Settings Hub (10 System + 8 Customization) & Glass UI / Charts / Markdown Rendering (PR #474)**: Modularized `DesktopSettings`, `DesktopTheme`, `DesktopMarkdown`, `DesktopStatsCanvas`, `DesktopSettingsPanel`, and `DesktopDiagnostics`; added 10 system settings (instant language switch, Windows `HKCU\...\Run` auto-start `--minimized`, close behavior, native notifications + 00:00–07:00 DND, backoff cap 15/30/60s, heartbeat 10/15/30s, preview length 90/200/500, profile management, manual GitHub Releases check, 3 rotating diagnostic logs) and 8 customization settings (6 accents, density, font scale, sidebar width/collapse, message card style, mirror mode, 4 gradients + custom local background with scrim, animation toggle); large-screen layout with `1180x760` min remembered window, Master-Detail messages, Canvas-drawn 7-day/24-hour bar charts and risk breakdown, multi-session AI Assistant with Markdown rendering, copy, quick prompts, `Ctrl+Enter` send, and `Ctrl+1..6` / `Ctrl+F` / `Esc` shortcuts.
- **#469 Desktop System Tray Resident Mode (Minimize to Tray by Default, PR #474)**: Adopted Compose Multiplatform `1.12.1` official `Tray` API and hoisted `DesktopLinkClient` and `MdnsScanner` lifecycles to `application {}`; closing the window minimizes to the system tray by default while keeping LAN connection, mirroring, and reconnects alive; sends native notifications via `TrayState.sendNotification` (respecting toggle and DND); supports `--minimized`/`--tray` launch flags and gracefully exits on close when `!SystemTray.isSupported()`.
- **#470 Inno Setup Installer & Dual Desktop Assets (PR #474)**: Added `desktop/packaging/windows/PhantomCheckDesktop.iss` (fixed `AppId`, per-user `%LOCALAPPDATA%` without admin rights, desktop shortcut and post-install launch checked by default, uninstaller cleans `HKCU\...\Run` while preserving `%APPDATA%` pairing config); unified `desktopVersion = "0.3.0"` in `desktop/build.gradle.kts`, and updated `.github/workflows/build.yml` and `release.yml` to produce both `.msi` and `*-setup.exe`.
- **#471 Cross-End Stability Audit Fixes & Post-v4.5.0 Fixes (PR #473 / #474)**: `DesktopPaths.kt` migrates desktop `link.properties` (including `hmacSecret`) to per-user ACL directories (`%APPDATA%\PhantomCheckDesktop` / `~/.config/PhantomCheckDesktop`) with owner-only permissions (DPAPI/Keychain limitation documented bilingually in `PRIVACY.md`); `DesktopLinkClient.sendCommand` returns `Boolean` with honest offline feedback; `CHAT_SEND` adds a 130s timeout and immediate reset on disconnect; chat `LazyColumn` adds stable `key`s; `DesktopLinkServer` uses per-client `clientCursors` to eliminate dual-device push races, `activeSessions.remove(id, sess)` to prevent reconnects from removing newer sessions, and logs `NsdManager.onRegistrationFailed` to `Log.w` and `lastError`; `MdnsScanner.stop()` closes `JmDNS` off the AWT EDT; all `gson.fromJson` calls are wrapped in `runCatching` and `CancellationException` is rethrown properly (completing post-v4.5.0 stability fixes together with `#465`).

## [4.5.0] - 2026-11-23

- **Tag 對照**：[`v4.4.0...v4.5.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.4.0...v4.5.0)（`versionCode = 53`、`versionName = "4.5.0"`；桌面端 `packageVersion = "0.2.0"`）
- **發佈資產（雙 APK ＋ 桌面端 Windows 安裝檔）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。
  - `PhantomCheckDesktop` `.msi` / `.exe`：Windows 桌面端連線與訊息管理安裝檔（Compose Multiplatform，`packageVersion = "0.2.0"`）。

### 新增、變更與修正
- **#454 LinkHubActivity 桌面端設定頁閃退修復（PR #459 / #463）**：將 `enableEdgeToEdge()` 移至 `super.onCreate(savedInstanceState)` 之後以符合 Jetpack Compose 生命週期慣例；補齊 `PairedDesktop`、`UnknownAttempt`、`CommandAuditEntry` 與 `qrcode.**` 精準 R8/ProGuard keep 規則防止混淆反序列化異常；為 `LinkHubActivity`、`LinkHubScreen`、`DesktopLinkStore`（`securePrefs` 改捕獲 `Throwable`）與 `DesktopLinkServer.start()` 補齊全路徑 `runCatching` 容錯與降級顯示，並在 QR 預覽失敗時提供可複製的 `wss://` 位址與憑證 SHA-256 文字備援。
- **#455 聯網搜尋智慧化（PR #460 / #463）**：新增前置搜尋必要性判斷與查詢重寫路由器（`AiClient.decideSearchNecessityAndRewrite`），先融合 RAG 記憶與近 12 則歷史判斷是否需聯網並重寫查詢（免搜問題直走既有對話路徑，省 Token 模式或無額度／無金鑰時退回純規則 `ruleBasedSearchDecision`）；`runGroundedQuery` 結合記憶、歷史與淨化後搜尋片段生成回覆並引導行內引用，來源清單收斂為同網域去重最多 3 筆乾淨網域名稱；`FreeWebSearchEngine` 新增 `<script>`/`<style>`/`<noscript>`/`<meta>` 剝除、HTML 實體與 percent-encoding 解碼、可讀字元比例（`<60%` 丟棄）與長度過濾（`<40` 或 `>600` 丟棄）、網域品質分級（官方與權威來源優先、內容農場丟棄）與查詢結果快取。
- **#456 主動情報中樞 v2（PR #461 / #463）**：`WatchTopicEngine` 於查詢前融合非敏感記憶（`PRIVACY_SENSITIVE` 雙層硬排除）、授權勾選之本機日曆未來 7 日要點與 `HealthObservationPolicy` 高階健康趨勢摘要組出個人化查詢（嚴格 ≤100 字元，原始生理序列不出本機）；`DigestComposer` 新增推播前 AI 相關性與實質新訊審查（≤150 token `RELEVANCE_JUDGE_PROMPT`，未通過或無額度即靜默丟棄），通過者生成 3–5 句精簡摘要並附同網域去重最多 2 筆乾淨來源；複用 `#455` 搜尋品質治理與既有 12h 快取／SHA-256 去重／勿擾／每日上限防護。
- **#457 桌面端 Windows MSI/EXE 安裝檔管線與深色玻璃 UI（PR #462 / #463）**：`desktop/build.gradle.kts` 統一 `version` 與 `packageVersion` 為 `0.2.0`、`nativeDistributions` 設定 `TargetFormat.Msi` / `TargetFormat.Exe`、`includeAllModules = true`、正式多解析度 `icon.ico` 與 Windows 安裝選項；`build.yml` 與 `release.yml` 新增 `windows-latest` `desktop-build` 與 `desktop-release` 管線產出 `.msi`＋`.exe`；`DesktopApp`／`DesktopLinkClient`／`DesktopStrings` 升級 16dp 圓角深色玻璃 UI、Spring（`0.85f` / `StiffnessMediumLow`）轉場與按壓動畫、連線／重連 pulse 狀態點與倒數進度條，並維持既有通訊協定與雙向功能完全相容。

### English
- **Tag Compare**: [`v4.4.0...v4.5.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.4.0...v4.5.0) (`versionCode = 53`, `versionName = "4.5.0"`; Desktop `packageVersion = "0.2.0"`)
- **Release Assets (Dual APKs + Desktop Windows Installers)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.
  - `PhantomCheckDesktop` `.msi` / `.exe`: Windows desktop companion installers (Compose Multiplatform, `packageVersion = "0.2.0"`).

**Added, Changed & Fixed**
- **#454 LinkHubActivity Desktop Settings Crash Fix (PR #459 / #463)**: Moved `enableEdgeToEdge()` after `super.onCreate(savedInstanceState)` to align with Jetpack Compose lifecycle conventions; added targeted R8/ProGuard keep rules for `PairedDesktop`, `UnknownAttempt`, `CommandAuditEntry`, and `qrcode.**` to prevent obfuscation deserialization failures; wrapped all code paths in `LinkHubActivity`, `LinkHubScreen`, `DesktopLinkStore` (`securePrefs` catching `Throwable`), and `DesktopLinkServer.start()` with `runCatching` and graceful status fallbacks, plus copyable `wss://` address and certificate SHA-256 text fallback when QR preview fails.
- **#455 Smart Web Search Routing, RAG Integration & Source Quality Governance (PR #460 / #463)**: Added pre-flight search necessity routing and query rewriting (`AiClient.decideSearchNecessityAndRewrite`) combining RAG memory and the last 12 chat turns (non-search queries route directly to standard chat; Token Saver or zero-quota/key-free states fall back to rule-based `ruleBasedSearchDecision`); `runGroundedQuery` fuses RAG memory, history, and sanitized search snippets with inline citation guidance and caps displayed sources to at most 3 domain-deduplicated clean domain names; `FreeWebSearchEngine` adds `<script>`/`<style>`/`<noscript>`/`<meta>` stripping, HTML entity & percent-encoding decoding, readability (`<60%` dropped) and length (`<40` or `>600` chars dropped) filtering, domain quality tiering (prioritizing official/authoritative sources and dropping content farms), and query caching.
- **#456 Proactive Intel Hub v2 (PR #461 / #463)**: `WatchTopicEngine` enriches watch-topic queries on `Dispatchers.IO` with non-sensitive memories (double-enforced `PRIVACY_SENSITIVE` exclusion), authorized 7-day local calendar highlights, and high-level `HealthObservationPolicy` trend labels (strictly `<=100` chars; raw physiological series never leave the device); `DigestComposer` adds a pre-push AI relevance and novelty gate (`<=150` tokens via `RELEVANCE_JUDGE_PROMPT`, silently dropping irrelevant or unverified items), generating concise 3–5 sentence digests with at most 2 domain-deduplicated clean sources while reusing `#455` search quality governance and existing 12h cache / SHA-256 dedup / DND / daily cap protections.
- **#457 Desktop Windows MSI/EXE Installer Pipeline & Dark Glass UI (PR #462 / #463)**: Unified `version` and `packageVersion` to `0.2.0` in `desktop/build.gradle.kts`, configured `nativeDistributions` (`TargetFormat.Msi`, `TargetFormat.Exe`, `includeAllModules = true`, multi-resolution `icon.ico`), and added `windows-latest` `desktop-build` / `desktop-release` jobs in `.github/workflows/build.yml` and `release.yml`; upgraded `DesktopApp`, `DesktopLinkClient`, and `DesktopStrings` with 16dp dark-glass cards, `spring(0.85f, MediumLow)` transitions and pressable buttons, pulsing connection/reconnection indicators with countdown progress bars, and full backward compatibility with the existing LAN link protocol.

## [4.4.0] - 2026-11-23

- **Tag 對照**：[`v4.3.0...v4.4.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.3.0...v4.4.0)（`versionCode = 52`、`versionName = "4.4.0"`）
- **發佈資產（雙 APK）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。

### 新增、變更與修正
- **#447 免費聯網查詢後端與免費 Gemini Key 400 修復（PR #451）**：新增零金鑰 `FreeWebSearchEngine`（依序查詢 DuckDuckGo Instant Answer → DuckDuckGo HTML（含 202/429 5 分鐘負快取）→ Wikipedia API），並針對免費 Gemini key 開啟聯網查詢時觸發的 HTTP 400（不支援 Google Search Grounding）進行偵測、靜默退還該次失敗扣額、快取不支援旗標並自動無縫回退至免費搜尋引擎＋一般模型呼叫；非 Gemini provider 未設 BYOK 搜尋端點時改走免費搜尋引擎（已設 BYOK 則優先 BYOK、失敗再降級免費引擎），全無結果或斷網時誠實回報失敗而不以模型既有知識頂替。
- **#448 主動通知全天行程附註去重與通知 ID 互蓋修復（PR #451）**：修正 `ProactiveEngine.evaluateLocalFingerprintAndPriority` 全天行程附註跨行程重複附註問題（改以 `allDayNote:$dayKey:<allDayIdsFp>` 寫入 `deliveredMilestones` 去重，僅於同日全天事件集合變動時才允許再次附加），並修正 `postBriefingNotification` 固定通知 ID 互蓋問題（改用獨立 `effectiveNotifId` 範圍 `1090..1099` 搭配 `BigTextStyle`）。
- **#449 桌面端連線 M1 連線骨架（PR #451）**：新增 `/shared` 跨平台協定模組與 `/desktop` Compose Multiplatform（`1.12.1`）桌面端骨架、手機端內嵌 Ktor WSS 伺服器（software-backed RSA 2048 + BouncyCastle X509v3 + PKCS12 憑證，私鑰存 `filesDir`、密碼存 `EncryptedSharedPreferences`）、mDNS 服務發現與手動 `host:port` 一鍵複製、6 位數單次配對碼（TTL 5 分鐘）＋ QR 配對與已配對裝置 HMAC challenge 重連、閒置 30 分鐘自動停止省電與 `SettingsCatalog` `desktop_link` 條目（零新 Manifest 權限）。
- **#450 桌面端 M2 雙向操作與連線審計（PR #451）**：`/shared` 擴充白名單 `MessageSummaryDto`（無全文、無敏感記憶、無通訊錄/日曆原始內容）；實作 `messages.list`／`messages.subscribe`（3 秒節流批次推播）／`messages.markRead`、`stats.snapshot`、`chat.send`／`chat.history`、`todos.list`／`todos.toggle`（嚴格單點複用 `ReminderScheduler.completeTodoWithRecurrence`）、`quickReply.send`（複用 `NotificationCollectorService`）與 `whitelist.*`／`tags.*` 雙向操作；加入單客端 `<=5 cmd/s` 限流與 200 筆環形審計紀錄，完成 `DesktopApp` 側邊導覽雙語深色介面並於 `PRIVACY.md` 新增區域網路連線中英雙語章節。

### English
- **Tag Compare**: [`v4.3.0...v4.4.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.3.0...v4.4.0) (`versionCode = 52`, `versionName = "4.4.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.

**Added, Changed & Fixed**
- **#447 Free Web Search Backend & Free Gemini Key HTTP 400 Fix (PR #451)**: Added the zero-key `FreeWebSearchEngine` (querying DuckDuckGo Instant Answer -> DuckDuckGo HTML with a 5-minute negative cache on 202/429 -> Wikipedia API). When a free-tier Gemini key hits HTTP 400 (Google Search Grounding unsupported) during web search, `AiClient` detects `isGroundingUnsupported400`, silently refunds the failed quota attempt, caches the unsupported flag, and seamlessly falls back to the free search engine + standard model call. Non-Gemini providers without a BYOK search endpoint now use the free search engine (while configured BYOK endpoints remain primary with free-engine fallback); offline or empty results report failure honestly instead of hallucinating from prior knowledge.
- **#448 Proactive Notification All-Day Note Dedup & Notification ID Collision Fix (PR #451)**: Fixed `ProactiveEngine.evaluateLocalFingerprintAndPriority` repeatedly appending all-day event notes across different timed events on the same day by deduplicating with `allDayNote:$dayKey:<allDayIdsFp>` in `deliveredMilestones` (only re-appending if the day's all-day event set changes), and fixed `postBriefingNotification` overwriting prior proactive notifications by assigning independent `effectiveNotifId` values (`1090..1099`) with `BigTextStyle`.
- **#449 Desktop Link M1 Connection Skeleton (PR #451)**: Added the `/shared` multiplatform protocol module and `/desktop` Compose Multiplatform (`1.12.1`) desktop app skeleton, embedded Ktor WSS server on Android (software-backed RSA 2048 + BouncyCastle X509v3 + PKCS12 stored in `filesDir` with passphrase in `EncryptedSharedPreferences`), mDNS discovery with one-tap `host:port` copy fallback, 6-digit single-use pairing code (5-minute TTL) + QR pairing, HMAC challenge reconnect for paired devices, 30-minute idle auto-stop, and `SettingsCatalog` `desktop_link` entry (zero new Manifest permissions).
- **#450 Desktop Link M2 Two-Way Actions & Connection Audit (PR #451)**: Extended `/shared` with allowlisted `MessageSummaryDto` (no full message bodies, sensitive memories, or raw contacts/calendar data); implemented `messages.list` / `messages.subscribe` (3s throttled batch push) / `messages.markRead`, `stats.snapshot`, `chat.send` / `chat.history`, `todos.list` / `todos.toggle` (strictly reusing `ReminderScheduler.completeTodoWithRecurrence` as single trigger), `quickReply.send` (reusing `NotificationCollectorService`), and `whitelist.*` / `tags.*` management; added per-client `<=5 cmd/s` rate limiting, a 200-entry ring-buffer audit log, `DesktopApp` sidebar navigation with bilingual dark UI, and a bilingual LAN Desktop Link section in `PRIVACY.md`.

## [4.3.0] - 2026-11-23

- **Tag 對照**：[`v4.2.0...v4.3.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.2.0...v4.3.0)（`versionCode = 51`、`versionName = "4.3.0"`）
- **發佈資產（雙 APK）**：
  - `app-release.apk`：正式簽章＋R8 程式碼縮減與資源壓縮（`isMinifyEnabled = true`、`isShrinkResources = true`），供一般使用者安裝與原簽章覆蓋升級。
  - `app-debug.apk`：除錯建置版本，供開發測試與排錯使用。

### 行為變更（Breaking / Behavior Changes）
- **#437 AI 傳輸層硬化（PR #442，行為變更明示）**：GitHub Models 已於 2026-07-30 全面退役，`AiClient` 直接攔截退役 host（`models.github.ai`、`models.inference.ai.azure.com`）不發無效請求，拋出附公告連結與遷移建議的例外；統一 `Accept: application/json`；非 JSON 回應（`Content-Type` 或本文首字檢查）改拋出保留 HTTP 狀態碼、`Content-Type` 與前 300 字本文的明確例外；`testConnection` payload 形狀（`Hi`／`max_tokens=5`）不變。
- **#440 洞察頁長期目標追蹤（PR #442，行為變更明示）**：完成路徑統一由 `ReminderScheduler.completeTodoWithRecurrence` 單點觸發 `GoalEngine.onTodoCompleted`（寫入前加 `(goalId,todoId)` 查重防護），`NotesTab` 直呼移除、原目標進度 Toast（`notes_s_dd002c`）一併移除；逾期降頻改以 `phantom_goal_engine` 專屬 `SharedPreferences` 持久化期數（不受每日清理影響、防 `WorkManager` 15 分鐘重入）；下期派生錨定 `max(今天, 上期 dueAt + cadenceDays)` 不漂移；里程碑真值化消費 `milestoneJson`（`[3,7,14,30]` 自 `startDate` 起算）。
- **#441 親密度第五階段（PR #442，行為變更明示）**：門檻 `30/100/250` 改為 `50/160/420/900`，新增 Lv5「知己（Kindred）」，既有用戶等效等級可能下修屬預期；權重公式 `2/1/1` 不變。
- **#438 App 內三態語言切換（PR #442，行為變更明示）**：設定新增「語言」三態（跟隨系統／繁體中文／English；API 33+ 走 `LocaleManager` 與系統 per-app 設定同步，API 26–32 走 `attachBaseContext` 平滑重建）；通知、小工具、設定目錄與 AI 輸出語系全跟隨 App 設定；`zh-TW` 系統＋跟隨系統表現與 v4.2.0 一致。

### 新增、變更與修正
- **#439 小工具優化與新增四個桌面小工具（PR #442）**：既有 7 工具（`StatsWidgetProvider`、`StatsMiniWidgetProvider`、`StatsDetailWidgetProvider`、`AiSummaryWidgetProvider`、`NotesWidgetProvider`、`ReminderWidgetProvider`、`RiskActionWidgetProvider`）補齊 `targetCellWidth/Height`、`previewLayout`、專屬向量預覽圖（`widget_preview_*.xml`）、`goAsync` 8.5s timeout 與 `onDisabled` 清理；新增今天待辦（`TodoTodayWidgetProvider`）／目標進度（`GoalProgressWidgetProvider`）／快捷記事（`QuickNoteWidgetProvider`）／健康快照（`HealthSnapWidgetProvider`）四個小工具（零新權限、零新網路、`exported=false`、`FLAG_IMMUTABLE`、健康快取 30 分鐘、未授權顯示教學態）。

### English
- **Tag Compare**: [`v4.2.0...v4.3.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.2.0...v4.3.0) (`versionCode = 51`, `versionName = "4.3.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.

**Breaking / Behavior Changes**
- **#437 AI Transport Layer Hardening (PR #442, Behavior Change)**: GitHub Models was fully retired on 2026-07-30; `AiClient` now blocks retired hosts (`models.github.ai`, `models.inference.ai.azure.com`) before sending invalid requests and throws an exception with the retirement notice URL and migration guidance; unifies `Accept: application/json`; non-JSON responses (checked via `Content-Type` or leading body character) throw explicit exceptions preserving HTTP status code, `Content-Type`, and the first 300 characters of the body; `testConnection` payload shape (`Hi` / `max_tokens=5`) is unchanged.
- **#440 Insights Tab Long-Term Goal Tracking (PR #442, Behavior Change)**: Completion is unified through `ReminderScheduler.completeTodoWithRecurrence` as the single trigger for `GoalEngine.onTodoCompleted` (with `(goalId,todoId)` dedup guard before write), removing direct `NotesTab` calls and the old goal-progress Toast (`notes_s_dd002c`); overdue downgrade periods persist in dedicated `phantom_goal_engine` `SharedPreferences` (unaffected by daily cleanup, preventing 15-minute `WorkManager` re-entry); next-period generation anchors on `max(today, previous dueAt + cadenceDays)` without drift; milestones consume `milestoneJson` (`[3,7,14,30]` from `startDate`) as the source of truth.
- **#441 Intimacy Stage 5 (PR #442, Behavior Change)**: Thresholds updated from `30/100/250` to `50/160/420/900`, adding Lv5 "Kindred (知己)"; existing users' effective level may adjust downward as expected; the `2/1/1` weight formula is unchanged.
- **#438 In-App 3-State Language Switch (PR #442, Behavior Change)**: Added 3-state "Language" setting (Follow System / 繁體中文 / English; API 33+ syncs via `LocaleManager` per-app settings, API 26–32 smoothly recreates via `attachBaseContext`); notifications, widgets, Settings catalog, and AI outputs all follow the app language; `zh-TW` system + Follow System behaves identically to v4.2.0.

**Added, Changed & Fixed**
- **#439 Widget Optimization & 4 New Home-Screen Widgets (PR #442)**: Upgraded all 7 existing widgets (`StatsWidgetProvider`, `StatsMiniWidgetProvider`, `StatsDetailWidgetProvider`, `AiSummaryWidgetProvider`, `NotesWidgetProvider`, `ReminderWidgetProvider`, `RiskActionWidgetProvider`) with `targetCellWidth/Height`, `previewLayout`, dedicated vector previews (`widget_preview_*.xml`), `goAsync` 8.5s timeout, and `onDisabled` cleanup; added 4 new widgets: Today's To-Dos (`TodoTodayWidgetProvider`), Goal Progress (`GoalProgressWidgetProvider`), Quick Note (`QuickNoteWidgetProvider`), and Health Snapshot (`HealthSnapWidgetProvider`) (zero new permissions, zero new network calls, `exported=false`, `FLAG_IMMUTABLE`, 30-minute health cache, onboarding state when unauthorized).

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

### English
- **Tag Compare**: [`v4.1.0...v4.2.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.1.0...v4.2.0) (`versionCode = 50`, `versionName = "4.2.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.

**Breaking / Behavior Changes**
- **Default UI & AI Output Language Changed to English (Non-`zh-TW` Devices)**:
  - Provides dual resource buckets only: default `res/values/strings.xml` (English, fallback = `en`) and Traditional Chinese `res/values-zh-rTW/strings.xml`.
  - **When the device system language is not Traditional Chinese (`zh-TW`), both App UI and AI natural-language outputs (risk analysis, auto-memo, weekly summary, deep analysis, morning/evening briefings, diary annotations, proactive intel, etc.) switch to English**; other unbucketed languages also fall back to English.
  - **Traditional Chinese (`zh-TW`) devices remain byte-for-byte unchanged in UI display**, and AI outputs retain Traditional Chinese and the existing 40-character memo density.

**Added, Changed & Fixed**
- **#429 i18n Round 1: Full-Repo UI Chinese String Extraction & XML `@string` Externalization (PR #432)**:
  - Extracted all user-visible UI strings across the repository (Compose UI, Activities/Dialogs/Toasts/Snackbars, Onboarding, home-screen widgets, Shizuku status, Service notification channels and push copy, `SettingsCatalog` titles/descriptions/aliases, `PhantomPrefs` display enums) into `res/values/strings.xml` (2,898 resource keys).
  - `AndroidManifest.xml` (9 `android:label` entries) and `res/layout/*.xml` (persistent stats notification and 7 widget layouts) converted to `@string/*` references; `AGENTS.md` added Rule 6 "UI String Extraction Discipline" (pure engineering refactor, zero behavior change).
- **#430 i18n Round 2: Default English UI (`values/` English, `values-zh-rTW/` Traditional Chinese) (PR #433, Behavior Change)**:
  - Moved the original Traditional Chinese `strings.xml` (2,898 entries) verbatim into `app/src/main/res/values-zh-rTW/strings.xml` (byte-for-byte identical to pre-migration, zero UI change for `zh-TW` users).
  - Translated the default `app/src/main/res/values/strings.xml` bucket into concise Android UI English (fallback = `en`), unifying core terminology (`PhantomCheck`, `AI Assistant`, `Memo`, `To-do`, `Whitelist`, `Scam Protection`, `Memory Bank`) and ensuring 100% alignment of format placeholders (`%1$s`, `%2$d`, etc.) and `formatted="false"` attributes.
- **#431 i18n Round 3: Locale-Aware AI Output & Multilingual Scam Lexicon (PR #434, Behavior Change)**:
  - **AI Prompt Output Language Automatically Follows System Locale**: `AiClient` (message risk analysis, auto-memo `memo`, weekly summary `summarizeWeekly`, deep analysis `deepAnalyzeMessage`, briefing polish `generateBriefingText`, RAG context summary `summarizeRagContext`), `DiaryPolicy` (diary annotations), `ProactiveEngine`, `ProactiveIntelWorker`, `CalendarInsightWorker`, `TaskTemplateEngine`, `MemoryEngine`, and `ChatScreenParts` all switch Traditional Chinese / English output instructions based on system Locale; JSON schema keys and enum values (`risk_label`, `memo_type`, etc.) remain unchanged, with English memos using an equivalent density cap (60 words / 360 chars) and English sentence-ending punctuation continuation detection.
  - **Rule-Based Scam Layer Adds English Scam-Script Detection**: `IndicatorExtractor`, `MemoryEngine`, and `ContactContext` expand English scam feature lexicons paraphrased from 2025–2026 public advisories by FTC, FBI IC3, FCC, UK Report Fraud, APWG, and FinCEN (covering unpaid toll, package redelivery, bank ATO / fraud department / safe account, Hi Mum number-change impersonation, crypto platform / guaranteed return / seed phrase pig-butchering, and OTP harvesting), enabling bilingual Chinese/English interception even without AI or in Token Saver mode.

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

### English
- **Tag Compare**: [`v4.0.0...v4.1.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v4.0.0...v4.1.0) (`versionCode = 49`, `versionName = "4.1.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.

**Added, Changed & Fixed**
- **#416 Messages Tab Stats Card Redesign (PR #426)**: Single-row 3-segment layout (Today / Unread / Scam) + 1dp vertical dividers; "Mark All Read" moved to the end of the same row with zero height jump from 99→0 unread.
- **#417 Force-Dark Fix (PR #426)**: Window-level theme switched to `android:Theme.Material` with dark `android:windowBackground` / `android:colorBackground` (`phantom_window_bg #FF0E0A18`), eliminating white flashes during Activity transitions while keeping high contrast in Compose.
- **#418 Memory Tab Stats Card & Sub-Tab Separation (PR #426)**: Reduced visual weight of "Long-Term / Short-Term / Conflicts / Immunity" stats card (`alpha 0.35`) and clearly separated it from the "Memory | Rules | Scam Immunity" sub-tabs with `10dp` spacing.
- **#419 Zero-Hallucination AI Health Queries (PR #426)**: Extended Health Connect to read distance (`DistanceRecord`) and calories burned (`TotalCaloriesBurnedRecord`) with per-type permission queries; unauthorized types show "Unauthorized", empty data shows "No records / No data", and AI hallucination-refusal examples + `health_query` action landed.
- **#420 Stats Tab Health Detail Card (PR #426)**: Day / Week / Month switcher (today's health snapshot, 7-day summary + scrollable daily breakdown table, 30-day summary and trend), pure local aggregation with zero tokens.
- **#421 AI Diary Annotation Fix (PR #426)**: Added AI purpose `DIARY_ANNOTATION` (`ai_model_diary_annotation` bindable to an independent model, falling back to default profile); 3-state diagnostics when unavailable (Token Saver mode / no provider configured / annotation type disabled) each with shortcut buttons; added "Generate diary annotations even in Token Saver mode" toggle (`diary_annotation_in_saver`, default off).
- **#422 Proactive vs. Regular Chatroom Switch Overlap Fix (PR #426)**: Clears messages before switching, rebuilds message list with `key(currentSessionId)`, and double-checks inside coroutine to prevent rapid-switch race conditions.
- **#423 Settings Health Check & Search Expansion (PR #426)**: Added `diary_annotation_in_saver`, `ai_model_diary_annotation`, `proactive_intel_enabled`, and `proactive_extract_enabled` to `SettingsCatalog` (all with symmetric read/write); added "Dark / Theme" synonym group and expanded health synonyms with distance/calories; global Settings search now uses the `SETTINGS_SEARCH` purpose profile.
- **#424 Smarter & More Proactive AI (PR #426)**: Morning/evening briefings degrade honestly per section (missing data explicitly marked "No data"); feedback-driven throttling (same topic in 7 days with `👎>=2` scales wait window ×1.5, `👍>=2` with no `👎` scales ×0.75, no feedback unchanged); suppresses proactive delivery if a conversation occurred within 15 minutes (marks dirty for next round); dynamic chatroom suggestion chips (state-driven, pure local, zero tokens, max 4).
- **#425 All-Day Event False Conflict Fix (PR #426)**: `ALL_DAY` events are excluded from timed schedule conflict checks; displays an all-day event info card when detected (applies to both new calendar events and notification-to-schedule).

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

### English
- **Tag Compare**: [`v3.8.0...v4.0.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.8.0...v4.0.0) (`versionCode = 48`, `versionName = "4.0.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.

**Added & Information Architecture Refactor**
- **v4.0.0 Global Information Architecture (IA) Overhaul & Search-First Settings Hub (#409 / PR #413)**:
  - **D1 Navigation 6 Tabs → 5 Tabs ("Insights" merges "Companion | Stats")**: Bottom `LiquidGlassTabBar` consolidated to 5 main tabs: Messages, AI Assistant, Notes, Memory, Insights (`64.dp` height, `N5_TAB_INDICATOR_SPRING` width/offset spring, icon + `labelSmall` + full `contentDescription`); "Stats" merged into "Companion" and renamed "Insights (`MainTab.INSIGHT`)" with a top `PhantomSecondaryTabRow` ("Companion | Stats"); `MainActivity.mapLegacyTabExtra()` seamlessly maps legacy notification, widget, shortcut, and onboarding deep links (`"stats"` / `"STATS"` / `"partner"` / `"PARTNER"` / legacy index `5`).
  - **D2 Search-First Settings Hub (Search + Pinned + Recent + 8 Categories)**: Added `SettingsHomeScreen` and `SettingsCategoryRegistry`, replacing the 13-card single-page scroll with a 2-tier same-Activity architecture (`SettingsCategoryId?` state with N1/N2 shared X-axis transitions, preserving `highlight_card_key` deep links):
    - **Top Dual-Mode Search Bar**: `56.dp` height, `28.dp` pill search bar combining local keyword/synonym search and "✨ AI Semantic Search" across all 136 `SettingsCatalog` items (including collapsed advanced settings).
    - **Pinned Quick Settings (6 default, editable) & Recent Adjustments**: 2-column pinned tiles (up to 6 customizable items; boolean toggles support inline `FluidSwitch`) + "Recently Adjusted" horizontal pill row (up to 5 items).
    - **8 Categories (merging "Personalization" & "Appearance")**: AI & Models, Anti-Scam & Security, Notifications & Interception, AI Companion & Proactive, Permissions & Background, Appearance & Personalization, Data & Backup, About & Updates, each with a 1-line live summary and in-page filter bar.
  - **D3 Messages Home Simplification, Segmented Grouped List & Center Stack Preserved (`MessagesTab`)**: `CenterFocusCardStack` retained with **0 diff** in geometry and scroll physics; top stats bar redesigned as a `surfaceContainer` 3-column card (Today / Unread / High Risk); filter bar simplified to 3 smart pills ("All | Unread | High Risk" with badges) + "Filter ▾" sheet; standard list upgraded to Segmented Grouped List.
  - **D4 Advanced Settings Collapsed by Default & Auto-Expanded on Search (`SettingsAdvancedSection`)**: 24 low-frequency/expert settings and Shizuku watchdog placed in a collapsed "Advanced Settings (N)" section at the bottom of each category, auto-expanding and pulsing (`N7_HIGHLIGHT_PULSE_MS`) on search hits.
  - **D5 Removed Persistent 22dp Pull Handle Visual, Kept Pull-Down Search Gesture**: Removed the persistent `22dp` `PhantomPullSearchHandle` bar; retained list-top `NestedScrollConnection` overscroll gesture (`>56.dp` with `HapticFeedbackType.LongPress`) on Messages, Notes, and Memory tabs plus a persistent "🔍 Search" pill in the `56.dp` top bar.
  - **D6 Visual Onboarding Streamlined (Home 20→8 steps, Settings 9→5 steps)**: `Onboarding.kt` streamlined without resetting existing `onboarding_done` / `settings_tour_done` flags; upgrading users see a one-time `PhantomInlineBanner` in Settings.
  - **D7 Large-Screen (`>= 600.dp`) `NavigationRail`**: Automatically switches to left `NavigationRail` + centered `840.dp` main container on tablets, unfolded foldables, and wide landscape screens.
  - **New Shared Components & 5-Tab / 11-Subpage Alignment**: Added `PhantomPageScaffold.kt`, `GlobalSearchOverlay.kt`, `SettingsRow.kt`, and `UiLayoutPrefs.kt` (`phantom_ui_v400`, storing only catalog IDs, recent search strings, and v4 tour flag); aligned `ChatScreen`, `NotesTab`, `MemoryTab`, `StatsTab` (local zero-LLM "Weekly Highlights" bar), `SettingsActivity`, and 11 sub-activities.
  - **Motion Extensions (`PhantomMotion` N1–N7 & `PredictiveBackHandler`)**: Appended N1–N7 draw-phase constants (`graphicsLayer`) with instant fallback when system animation scale = 0, plus `activity-compose:1.13.0` `PredictiveBackHandler` (`1f → 0.92f`).
  - **Pure UI Overhaul & Zero-Regression Statement**: **0 diff** across `service/`, `data/`, `network/`, and `util/`; Room schema, background services, AI requests, and anti-scam decision logic are untouched, with zero data or background behavior changes, and 100% preservation of `#402–#408` fixes and accessibility `contentDescription`.

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

### English
- **Tag Compare**: [`v3.7.0...v3.8.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.7.0...v3.8.0) (`versionCode = 47`, `versionName = "3.8.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.

**Added & Behavior Changes**
- **Health Connect Chat Summary Injection & Background Proactive Health Alerts (#403 / PR #410, Privacy Behavior Change)**:
  - **Privacy Behavior Change (Chat Health Summary Injection)**: When the user asks about sleep, steps, heart rate, fatigue, or exercise in AI Assistant chat and "Allow AI to read health summary" is enabled, `LocalDataConnector.buildHealthContext()` injects 7-day daily aggregated values (`DailyHealthPoint` sleep hours, steps, resting HR, and 7-day average) and today's morning snapshot (`MorningHealthSnapshot`) as `<health_context>` into the prompt sent to the user's configured AI provider; **only 7-day aggregated values are sent—raw physiological sample series never leave the device** (documented in `PRIVACY.md` Section 2.2). When Health Connect is not installed, permission is not granted, or the toggle is off, AI honestly informs the user and guides them to Settings without fabricating data.
  - **Proactive Health Observation Alerts (`HealthObservationPolicy` in `ProactiveMonitorWorker`)**: Pure local lookup-table rules (zero LLM tokens): 2 consecutive nights sleep `< 6h` (`sleep_2nights_under_6h`), 3 consecutive days steps `< 2000` (`steps_3days_under_2000`), or resting HR `>= 12 bpm` above 7-day average (`hr_elevated_12bpm`, requiring >= 4 valid baseline days); delivers gentle care alerts via `ProactiveEngine.deliverProactiveMessage` (`NotifIds.HEALTH_PROACTIVE = 1014`), strictly capped at 1/day and 2/7 days per rule, respecting DND and daily proactive caps.
  - **Independent Toggle**: Added "Allow AI to read health summary" (`health_ai_query_enabled`, default on) to `PermissionHubActivity` Health Connect card and `SettingsCatalog` (disabling stops chat injection and background alerts without affecting the local 7-day trend chart in Stats).

**Changed & Fixed**
- **Fixed Weekly AI Summary Truncation & Added Full-Text Expand in Stats (#402 / PR #410)**: Raised `AiClient.summarizeWeekly()` `maxTokens` from `1400` (Groq `1000`) to `2400` (Groq `1600`, fallback `1400` on HTTP 413), enforced complete-sentence endings, and auto-continues once (`maxTokens = 1200`) when `finishReason` is `length`/`max_tokens` or `endsWithCompleteSentence` is false; removed `.take(70)` hard truncation in `StatsTab` in favor of `maxLines = 2` + "Expand Full Text".
- **Unified Proactive Message System Notifications, Deep Links & Channel Check (#404 / PR #410)**: Consolidated `ProactiveEngine.deliverBriefing` (`1008`/`1009`) into `deliverProactiveMessage`, sharing dedup, DND, `SESSION_PROACTIVE_BRIEFING`, unread badge, and system notifications; tapping any proactive/briefing notification deep-links directly to the proactive chatroom and clears the unread badge; added `ProactiveEngine.isBriefingChannelBlocked()` one-time banner when `assistant_briefing` is `IMPORTANCE_NONE`.
- **Fixed Settings AI Semantic Search "Unparseable Response Format" (#405 / PR #410)**: Enabled `response_format: {"type": "json_object"}` (with automatic HTTP 400 fallback) + `parseSettingsSearchIdsLenient()` fallback chain + 1 strict retry, clearly distinguishing empty results (`emptyList()`) from parse failure (`null`).
- **Settings Local Keyword Search Tokenization, Synonyms & Colloquial Aliases (#406 / PR #410)**: Added `STOP_WORDS`, bigram sliding tokenization, 16 `SYNONYM_GROUPS`, weighted scoring (`+12` title, `+10` aliases, `+6` description), and 3–5 colloquial `aliases` for all 136 `SettingsCatalog` items.
- **Eliminated Mid-Sentence Truncation in Proactive Messages, Briefings & Continuity Pills (#407 / PR #410)**: Removed `.take(...)` hard truncations in `ProactiveEngine`, `TaskTemplateEngine.runAggregation`, and `PhantomPrefs.saveChatContinuityAnchor` (relaxed to 500 chars); `AiClient.generateBriefingText()` falls back to the full template on truncation; continuity/todo/briefing cards support tap-to-expand.
- **Fixed Update Download Stuck on "Downloading" & Unknown-Sources Permission Guide (#408 / PR #410)**: Persisted `downloadId` + `targetVersion` in `PhantomPrefs`/`AppUpdateHelper`, added 600ms `DownloadManager.query()` polling + `ON_RESUME` re-check, plain-language failure reasons with retry/browser fallback, and Android 8+ `ACTION_MANAGE_UNKNOWN_APP_SOURCES` one-step guide that auto-resumes the system installer on `ON_RESUME` (fully preserving `#374` snooze reminder and `#391` scrollable release notes preview).

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

### English
- **Tag Compare**: [`v3.6.0...v3.7.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.6.0...v3.7.0) (`versionCode = 46`, `versionName = "3.7.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.

**Added & Optimized**
- **Phantom Aurora Global UI/UX Visual & Motion Overhaul (#398 / PR #399)**:
  - **Shared Motion Core `PhantomMotion.kt` (New File)**: Centralizes Material 3 Emphasized curves (`EmphasizedDecelerate`, `EmphasizedAccelerate`), unified expand/collapse specs (`ExpandEnter` spring height + fade in, `ExpandExit` accelerated collapse + fade out), staggered list entry (`Modifier.phantomEnter` and `LazyListScope.phantomItem`, delaying the first 8 items by `36ms` each while tracking played keys per tab via `LocalPhantomEnterRegistry` to avoid replay on scroll), pop-in entry (`Modifier.phantomPopIn`), rotating chevron (`PhantomChevron` 0°↔180° spring replacing static `KeyboardArrowUp`/`KeyboardArrowDown`), glass highlight edge (`Modifier.phantomSheen` drawing adaptive light/dark top highlight stroke and top inner glow via `drawWithCache`), press-click wrapper (`Modifier.phantomPressable`), and directional rolling count text (`PhantomAnimatedCount`). Reads system `Settings.Global.ANIMATOR_DURATION_SCALE` via `rememberMotionEnabled()` and automatically disables manual stagger and infinite loops with static fallback when animation scale = 0.
  - **`FluidSwitch` Fluid Toggle Upgrade (draw-phase)**: Track and thumb colors continuously `lerp`-interpolate with offset/drag progress in `drawBehind`; draws primary-color track outer glow and soft thumb shadow when enabled, and dynamically strokes a rounded checkmark path inside the thumb in the second half (`p > 0.45f`), preserving press squish deformation, half-threshold drag toggle, and `Role.Switch`/`ToggleableState` accessibility semantics.
  - **`LiquidGlassTabBar` Icon Motion Upgrade**: Selected tab icon performs a `graphicsLayer` spring bounce (`1f → 1.12f`) paired with smooth `animateColorAsState` (`180ms`) color transition.
  - **`pressableScale` Press Feedback Upgrade (`Glass.kt`)**: Driven by `Animatable` + `graphicsLayer` scaling with optional `glowShape` primary-color edge glow via `drawWithContent`; releases early if finger movement exceeds `touchSlop` or gesture is consumed by scroll (`change.isConsumed`), preventing cards from getting stuck scaled down during list scrolling.
  - **Shared Cards, Search & Floating Component Polish (`Components.kt`)**: Applied `phantomSheen` highlight stroke to `PhantomGlassCard` (both glass and non-glass branches), `PhantomFloatingSurface`, `MessageCard` (including high-risk scam cards), and `PhantomSearchBarPill`; applied `pressableScale` to OTP copy pills and `PhantomSearchBarPill`; applied `phantomPopIn` to `PhantomDialog` (`0.92f`) and `EmptyStateView` icon (`0.6f`); upgraded `PhantomFullScreenSearchOverlay` to Emphasized enter/exit curves.
  - **Directional Main-Tab Transitions & Unified Motion Across 5 Tabs, Settings, 12 Subpages & Onboarding**:
    - `MainActivity.kt`: 6 main tabs use `AnimatedContent` (left/right slide direction based on tab `ordinal` + short fade exit), fully preserving `SaveableStateProvider(tab.name)` state and providing `LocalPhantomEnterRegistry`.
    - Main Tabs: `MessagesTab` (Today/Unread/Scam stats use `FlowRow` + `PhantomAnimatedCount` supporting `fontScale = 1.3+` wrapping, `PhantomChevron` + `pressableScale` filter toggle, unified offline retry banner expand, `animateItem()` + `phantomEnter` on app groups and message lists), `NotesTab` (`PhantomChevron` + unified expand for todos/local calendar, `animateItem()` + `phantomEnter` on notes), `TodoSection` (`pressableScale` + `graphicsLayer` alpha transition on completion), `MemoryTab` (`PhantomChevron` + unified expand, 10-section `phantomItem` entry), `WatchTopicSheet` (`animateItem()` + `phantomEnter`), `StatsTab` (`PhantomChevron` on briefings/weekly reports, `pressableScale` share button, 13-section `phantomItem` entry), `PartnerTab` (7-section `phantomItem` entry), `DiarySection` (unified AI note expand), `GoalSection` (`pressableScale` + `animateFloatAsState` spring progress bar).
    - Settings & All 12 Subpages: `SettingsActivity` (`CollapsibleSettingsCard` header `pressableScale` + `PhantomChevron` + unified expand, consistent expand motion across quick commands, local calendar list, hidden calendars, and per-app throttle overrides), `ApiKeySettingsActivity` (profile cards `animateItem()` + `phantomEnter`, purpose model cards `phantomItem`), `AssistantHubActivity` (7-section `phantomItem`), `AutoReplySettingsActivity` (4-card `phantomEnter`), `LinkedAppsActivity` (5-section `phantomItem`), `PermissionHubActivity` (4-section `phantomItem`), `ScamCenterActivity` (`animateItem()` + `phantomEnter`), `TagManagementActivity` (`animateItem()` + `phantomEnter`), `WhitelistManagementActivity` (`PhantomChevron` + unified expand for add form and app groups), `ContactsIntegrationSection` (`pressableScale`), `AppThreadsScreen` (`animateItem()` + `phantomEnter`), `ConversationScreen` (`animateItem()` on date dividers and messages), `MessageDetailActivity` (5-card `phantomEnter`).
    - Onboarding & Settings Tour (`Onboarding.kt`): Spotlight hole 4-edge coordinates upgraded from linear `tween` to smooth `spring`; tour cards use Emphasized decelerate + spring entry; restricted-settings troubleshooting uses unified expand spec.
  - **Chatroom Motion & Performance Optimization (`ChatScreen.kt`, `ChatScreenParts.kt`)**: Message bubbles use `animateItem()`; user bubbles add `phantomSheen` highlight edge (100% preserving user-configured `bubbleCornerDp` `8–24dp` and `70%–95%` max width); "Reasoning" collapsible uses `PhantomChevron` + unified expand; send button and side history drawer session rows use `pressableScale`/`animateItem()` (bottom input box never animates position/scale—zero typing jitter); `TypingDotsIndicator` rewritten in draw-phase `graphicsLayer` (phase-driven alpha, scale, and vertical wave bounce), eliminating per-frame composition and falling back to static three dots when system animation scale = 0.
  - **Markdown Typography Polish (`PhantomMarkdownText.kt`)**: Blockquotes updated with `10.dp` right corner radius + `3.5.dp` rounded left capsule bar and comfortable padding; code blocks upgraded to `12.dp` corner radius + `1.dp` border and `12.dp` padding; tables upgraded to `12.dp` outer corner radius with zebra-striped odd rows (`headerBg.copy(alpha = 0.35f)`), with zero changes to Markdown text/syntax parsing logic.
  - **Global Activity Window Transitions (`styles.xml` + `res/anim/phantom_{open,close}_{enter,exit}.xml`)**: Added `PhantomWindowAnimation` to `Theme.PhantomCheck` defining lightweight horizontal slide (`8%p` / `-4%p`) and alpha transitions for subpage open/back, automatically omitted by Android when system window animation scale = 0.
  - **Strict Boundaries & Zero-Regression Guarantee**: Zero new third-party dependencies, no `SharedTransitionLayout`/`LookaheadScope`, **0 diff** in `CenterFocusCardStack` scroll physics and geometry, and all 37 changed files are strictly in `ui/` and `res/` (`data/`, `service/`, `network/`, `util/` have **0 diff**) with zero functional regressions.

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

### English
- **Tag Compare**: [`v3.5.0...v3.6.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.5.0...v3.6.0) (`versionCode = 45`, `versionName = "3.6.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`), for general users and in-place signature upgrades.
  - `app-debug.apk`: Debug build for development testing and troubleshooting.

**Added**
- **Proactive Intel Hub Phase A: Watch Topics, Daily AI Extraction & Dedicated Quota (#388 / PR #395)**:
  - Non-destructive Room upgrade from v22 to v23 (`MIGRATION_22_23` with pure `CREATE TABLE IF NOT EXISTS watch_topics` and `index_watch_topics_status`), adding `WatchTopicEntity` (`category`, `title`, `queryHint`, `source`, `status`, `confidence`, `lastQueriedAt`, `lastDigestHash`, `hitCount`, etc.) and `WatchTopicDao`.
  - Added `WatchTopicEngine` and daily background extraction `WatchTopicExtractWorker` (`phantom_watch_topic_extract`, `ExistingPeriodicWorkPolicy.KEEP`, `NetworkType.CONNECTED` + `requiresBatteryNotLow`): strictly excludes `🔒 Sensitive` memories via both SQL (`activeNormalMemoriesForPrompt`) and Kotlin (`privacyTier != PRIVACY_SENSITIVE`), sanitizes inputs with `AiClient.sanitizeForPrompt`, and extracts up to 5 pending candidates (`source = "ai_extracted"`, `confidence = "low"`)—**never triggering external queries or push notifications before user confirmation**.
  - Added `AiPurpose.PROACTIVE` ("Proactive Intel & Watch Extraction") dedicated model purpose with daily quota cap (default 12/day, auto-refunded on failure), plus shared `WatchTopicManagerDialog` in Memory and Settings tabs (one-tap accept all pending candidates, individual confirm / edit `queryHint` / mute / restore / delete, and manual add).
- **Proactive Intel Hub Phase B: Gemini Grounding Web Search & Chat Toggle (#389 / PR #395)**:
  - Added optional `tools: List<Map<String, Any>>? = null` to `ChatRequest` (ignored by Gson when `null`, byte-for-byte zero impact on non-web requests) and `AiClient.runGroundedQuery()`.
  - Injects `tools = [{"google_search": {}}]` only when the target profile is a Gemini OpenAI-compatible endpoint and model is **Gemini 3+**, with multi-path defensive URL parsing (`annotations[].url_citation.url`, `extra_content.google.grounding_metadata.grounding_chunks[].web.uri`, `grounding_metadata`/`groundingMetadata`, `citations[]`, and inline URL fallback); automatically refunds quota and backs off silently on HTTP 429.
  - Added "🔍 Web Search" toggle chip above `ChatScreen` input bar: native Google Search Grounding is enabled only for Gemini 3+ (other providers like Mistral/Groq without a BYOK external search endpoint are honestly grayed out with tap-to-explain); honestly reports when no results are found or network fails instead of faking real-time results with stale model knowledge.
- **Proactive Intel Hub Phase C/D/E: Periodic Queries, Dedup Digest, Push, Settings Boundaries & Privacy Docs (#390 / PR #395)**:
  - Added `WatchQueryPlanner` (pure local zero-token selection of due topics: 6h for `region`, 24h for others, sorted `high > medium > low`, max 3 per round, auto-skipped in token-saver mode), `DigestComposer` (12h local short cache, SHA-256 digest hash dedup to suppress non-novel pushes, strict "no recent updates" anti-hallucination rule with source links), and `ProactiveIntelWorker` (`phantom_proactive_intel`, `ExistingPeriodicWorkPolicy.UPDATE`, `CONNECTED` + `requiresBatteryNotLow`, `BootReceiver` reboot rescheduling), sharing digest, independent DND, daily proactive cap, and `NotifIds.PROACTIVE_INTEL = 1013` push gate with `ProactiveEngine` briefings.
  - Added "Proactive Intel Hub" Settings card (**master switch off by default**, showing a full privacy & quota dialog on first enable; supports daily auto-extract toggle, 6/12/24h query frequency, watch list management, "Query Now" preview, and "Mistral Daily Analysis + Gemini Proactive Intel" dual-track BYOK/purpose guide), all registered in `SettingsCatalog`; updated `PRIVACY.md` Section 6 stating that **only confirmed short topic strings (`title`/`queryHint`, <= 80 chars) are sent**—never raw notifications, full memories, or sensitive data.
  - Proactive intel messages are written to the dedicated proactive chatroom with clickable links rendered by `PhantomMarkdownText` and a "💬 Follow Up" button to jump to chat with a prefilled follow-up; system notifications include a "Stop Watching" action (handled by `WatchTopicMuteReceiver` with `exported="false"` + `FLAG_IMMUTABLE`, zero trampoline).
- **About & Auto-Update Card Release Notes Preview (#391 / PR #395)**:
  - Extended `AppUpdateHelper`'s `UpdateCheckResult.UpToDate` and `UpdateAvailable` to carry `releaseNotes` (`body`) and `publishedDate` from GitHub `releases/latest` (fetched live without caching).
  - Added shared scrollable `ReleaseNotesPreviewBox` (`PhantomMarkdownText` rendering + `heightIn(max = 260.dp)` + `verticalScroll`, honestly showing "No release notes for this version" when empty), embedded in both Settings About and the home auto-update dialog.
- **Partner Tab Cardless Full-Width Header & Custom Region Background (#392 / PR #395)**:
  - Moved assistant avatar and name in `PartnerTab` to the very top of the page (moving AI Diary and Long-Term Goals below), removed `PhantomGlassCard` framing in favor of a cardless full-width banner (decoupled from Liquid Glass toggle, avatar enlarged to `76.dp`) with a `48.dp` camera/remove button at top-right.
  - Added a dedicated internal storage slot (`assistant_header.jpg`) in `BackgroundImageHelper` using bounds-first `inSampleSize` downsampling and compression (preventing 50MP OOM and avoiding persistent external URI permissions) with a bottom gradient scrim; registered management entry in `SettingsCatalog` (`assistant_header_background`) and Settings Personalization.
- **Assistant & UI Customization Extensions (#393 / PR #395)**:
  - Audited `PhantomPrefs` and `SettingsCatalog` and added 5 high-value customization options under Settings Personalization (all defaults 100% aligned with v3.5.0, with one-tap reset to defaults): (1) AI Chatroom independent font scale (`85%–130%`, default `100%`), (2) Message Center independent font scale (`85%–130%`, default `100%`), (3) chat bubble corner radius (`8–24dp`, default `18dp`), (4) chat bubble max width ratio (`70%–95%`, default `82%`), and (5) Home Favorites app row cap (`All (default) / Max 4 / Max 6 / Max 8`).

**Changed & Fixed**
- **Fixed Action-Only Chat Replies Showing "AI Returned No Displayable Content" (#386 / PR #395)**:
  - Extracted shared `hasExtendedProposals()` and `hasAnyProposal()` in `ParsedChatActions` covering `search`, `todoManage`, `vipPin`, `muteApp`, `markRead`, `export`, `whitelistDelete`, `usageQuery`, `openSetting`, `crossApp`, and `demotedQueryHints`; fixed the UI falsely displaying "(AI returned no displayable content, please retry or switch models)" when AI returned only a fenced JSON action block that executed successfully.
- **Zero-Hallucination Calendar Queries: Always Verbatim from Local Calendar, Honest Refusal When Empty (#387 / PR #395)**:
  - Expanded `LocalDataConnector.isCalendarQuery()` with schedule/course semantic terms paired with question markers, and used `DateTimeFormatter` + `ZoneOffset.UTC` for accurate all-day event boundaries.
  - Added `CalendarBridge.queryScheduleSummary()` and `calendar_query` tool action (pure local `CalendarContract.Instances` query returning verbatim start/end times, title, and location); strengthened `ChatScreenParts` system prompt anti-hallucination rules and refusal examples so schedules always come verbatim from local calendar results, honestly refusing and guiding to Permissions/Settings when unauthorized, unselected, or empty.
- **Global Health Check & OEM Compatibility Re-Verification (#394 / PR #395)**:
  - **Update Download Receiver Security Flag Fix**: Changed `DownloadManager.ACTION_DOWNLOAD_COMPLETE` receiver registration in `SettingsActivity` from `RECEIVER_EXPORTED` to `ContextCompat.RECEIVER_NOT_EXPORTED` to prevent spoofed download-complete broadcasts from external apps.
  - **GitHub Monitor Consecutive-Failure Honest Alert**: Added consecutive-failure counter and throttled logging to `GitHubMonitorWorker`, sending a one-time honest alert on the 3rd consecutive failure to check network or PAT permissions, resetting to zero on success.
  - **Boot Catch-Up 24-Hour Floor**: Applied the 24-hour `catchUpFloorMs` filter to `scheduled` message reminders (`messageDao().allScheduledReminders()`) in `BootReceiver` alongside todos to avoid replaying stale overdue reminders after reboot; completed re-verification of Room `1→23` migration chain, `PendingIntent` `FLAG_IMMUTABLE`, `Dispatchers.IO`, and background restrictions across 5 major OEMs.

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

### English
- **Tag Compare**: [`v3.4.0...v3.5.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.4.0...v3.5.0) (`versionCode = 44`, `versionName = "3.5.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk`: Release-signed with R8 code shrinking and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`).
  - `app-debug.apk`: Debug build for development testing.

**Added & Behavior Changes**
- **Proactive Alert Milestone Dedup & Feedback (#372, #377 / PR #383, Behavior Change)**: Switched proactive alerts from state-based to event-milestone deduplication (at most once per 24h before / N min before / overdue, plus 10-minute content-hash gate); added 👍/👎 feedback, separate daily caps for briefings and proactive alerts, and configurable lead time, all-day alert time, monitor interval, and dedup cooldown.
- **Local Calendar Multi-View & Account-Grouped Management (#373 / PR #383)**: Filters visible/synced calendars by default, adds account-grouped batch selection and "Select All Visible" in Settings, plus Day/Week/Month views in Notes tab.
- **Update Reminder Snooze State Machine (#374 / PR #383, Behavior Change)**: Replaced daily-once gate with `update_snooze_until_ms` state machine (only "Later" sets snooze; dismissing never swallows alerts; max 1 retry per process).
- **Tightened Auto-Scam Detection & False-Positive Reduction (#375 / PR #383, Behavior Change)**: Separated weak `urlPresent` from strong `intelHit`/`cryptoAddressPresent`; automatic `scam` tagging requires intel hit, crypto address, or concrete scam-operation signals; generic links or conflicts with 30-day high-reputation senders downgrade to confirmation cards; tightened `hasSuspiciousFeature` triggers and added feedback write-back to reputation & encrypted benign-domain LRU.
- **Chatroom Bottom-Right Timestamps (#376 / PR #383)**: Unified `10sp` `HH:mm` timestamps (`Locale.US`) on user/AI/proactive/system bubbles with large-font/narrow-screen safety and toggle.
- **AI Diary / Daily Review & Long-Term Goal Tracking (#378, #379 / PR #383)**: Non-destructive Room v21 → v22 (`diary_days`, `goals`, `goal_progress`); daily 00:30 zero-token SQL timeline + monthly overview (optional collapsible AI annotation); long-term goals with recurring sub-todos, auto-downgrade after 3 missed periods, and intimacy integration (100% usable without API key).
- **Declarative Agentic Capability Table & Blocked-Action Rerouting (#380 / PR #383)**: Added `IntentCapabilityRegistry` (`REMINDER`/`MEMO`/`CALENDAR_ADD`/`OPEN_APP`) with automatic safe fallback paths, plain-language failure reasons, and "Execution Method" on confirmation cards.
- **Health Connect Integration & Multi-Brand Health Check (#381, #382 / PR #383)**: Added `androidx.health.connect:connect-client:1.1.0` for local morning-briefing lookup rules (raw values never leave device), 7-day sleep/steps trend chart in Stats, Android 13/14+ Health Connect permission entry, and fixed `BootReceiver` duplicate overdue to-do notifications.

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

### English
- **Tag Compare**: [`v3.3.0...v3.4.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.3.0...v3.4.0) (`versionCode = 43`, `versionName = "3.4.0"`)
- **Release Assets (Dual APKs)**:
  - `app-release.apk` & `app-debug.apk`.

**Added**
- **Proactive Calendar Intel Engine (`CalendarInsightEngine`) (#365 / PR #369)**: Added `CalendarInsightWorker` (`PeriodicWork` default 60 min, configurable 30 / 60 / 120 / 180 min via `ExistingPeriodicWorkPolicy.UPDATE`, rescheduled on boot by `BootReceiver`), reading only user-checked local calendars for events in the next 48h, strictly limiting external queries to "event title + date (`<= 60` chars, `sanitizeForPrompt`)" (strictly forbidding attendees, notes, or location), deduplicating by `eventId|beginMs|intelHash` (zero AI calls when no new intel), auto-skipping when BYOK external search is unset or Token Saver is active, and respecting daily proactive cap and independent DND; added `proactive_calendar_insight_enabled`, `proactive_calendar_insight_notif_enabled`, `proactive_calendar_web_search_enabled`, and `calendar_insight_interval_minutes` wired to `SettingsCatalog` and `AssistantHubActivity`.
- **Proactive Message Chatroom, Auto Memory Extraction, Unresolved Pills & Continuity Anchors (#366 / PR #369)**:
  - `ProactiveEngine` creates a dedicated proactive chatroom (`PROACTIVE_SESSION_TITLE = "AI Assistant Briefings & Proactive Alerts"`), no longer writing to the first regular session; history drawer collapses it by default with a live unread badge (`99+` over 99, cleared on open) and protects it from "Clear All Conversations"; cross-tab system events can be written with zero AI via `appendSystemEventToCurrentSession`.
  - Added auto memory extraction (`memory_auto_extract_enabled`, default on): writes up to 2 non-sensitive, conflict-free memories per chat turn with a subtle notice and one-tap delete BottomSheet below the reply bubble; `🔒 Sensitive` memories and conflicts 100% remain confirmation-gated.
  - Added single-chat continuity components (`chat_pending_capsules_enabled`, default on): displays up to 3 swipeable unresolved-item quick pills at the top plus local compressed continuity anchors (auto-expiring after 7 days, manually closeable).
- **Dual-Threshold Auto-Decision Layer (`DecisionEngine`) & One-Tap False-Positive Feedback (#367 / PR #369)**:
  - Added `DecisionEngine` (pure function, zero AI tokens): deterministic signals take priority (whitelist, filter rules, memo rules, acquaintances/VIP, structured time/OTP/URL/amount, known avatars); without local risk signals, AI semantic scoring alone is capped at `confirmThreshold - 10` (configurable via `decision_confirm_threshold`, default 60, range 40–85) and never triggers high-confidence scam popups or irreversible actions alone.
  - Centralized reversible (auto-execute) vs. irreversible (confirmation- or toggle-gated) action matrix with unified provenance tags; false-positive feedback dialogs in `MessageDetailActivity`, `ScamCenterActivity`, and `Components.kt` support 4-category "This judgment is wrong" feedback written directly back to rules while immediately invalidating caches.
- **Scheduled Task Templates, Declarative Action Registry & Status Fixes (#368 / PR #369)**:
  - Non-destructive Room upgrade v20 → v21 (`MIGRATION_20_21`), adding `task_template` table and indexes; added `TaskTemplateEngine` separating pure local zero-token aggregation from optional AI polishing, with a 3-step wizard and live WYSIWYG preview in `AssistantHubActivity`.
  - Added single-source declarative `LinkedAppActionRegistry` driving both `LinkedAppsActivity` capability cards and `ChatScreenParts` AI capability declarations (including `discord_digest_stats_readonly` read-only demo connector); persisted sync timestamps and error status in `GitHubMonitorWorker` (no longer silently swallowing status).

**Changed & Fixed**
- **Full-Table Whitelist Matching, Unified Dedup Write & One-Time Cleanup (#362 / PR #369)**: Removed `LIMIT 20` truncation in `AiRuleDao.activeWhitelistAll()`, fixing silent failure from the 21st whitelist entry onward; `WhitelistRuleHelper.upsertWhitelist` unified 6 write paths with dedup and re-enabling disabled rules; deep analysis in `MessageDetailActivity`, `PendingAiAnalysisWorker`, and `NotificationCollectorService` re-queries DB whitelist and marks manual-trigger provenance; `NotificationCollectorService.dedupWhitelistRulesIfNeeded` performed one-time exact duplicate row merging (retaining the oldest entry without altering disabled state).
- **Memory Tab Filter Chips Overlap & Large-Font Adaptation Fix (#363 / PR #369)**: In `MemoryTab` expanded mode, moved batch management and collapse button to a dedicated top control bar so the two layered `LazyRow`s occupy full width with `clipToBounds()` and trailing `contentPadding`; kept `8.dp` explicit spacing in collapsed mode; unified `heightIn(min = 34.dp)` across all chips and pills so `fontScale = 1.3` and `360dp` narrow screens have zero clipping or overlap.
- **AI Tool-Calling Precision, Anti-Hallucination Rules & Direct Local Queries (#364 / PR #369)**: `ChatScreenParts` injects system capability declarations and anti-hallucination constraints, implementing single-intent single-card priority (`calendar_free_query > usage_query > search_messages > memory_list > todo_list`) with honest secondary-query hints; added `LocalDataConnector` for local calendar and `UsageStatsBridge`; implemented `usage_query` querying `UsageStatsManager` real foreground time and launch counts (honestly reporting and guiding to permissions when unauthorized), and fixed `PermissionHubActivity` usage-stats permission state and navigation.

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

### English
- **Tag Compare**: [`v3.2.0...v3.3.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.2.0...v3.3.0) (`versionCode = 42`, `versionName = "3.3.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Memory Write Confirmation Toggle (#347 / PR #359)**: Added `memory_write_confirm` (default on); can be turned off so normal AI memories write directly, while `🔒 Sensitive` memories, conflict records, and deletions still always require a confirmation card.
- **Calendar Write Confirmation Toggle & Collapsible Settings List (#348 / PR #359)**: Added `calendar_write_confirm` (default on); when off, still runs conflict checks and writes directly to checked calendars if authorized and conflict-free; local calendar list in Settings now collapses with an `N/M` checked summary.
- **Stats "Morning/Evening Briefing Card" (#349 / PR #359)**: Added a briefing card in Stats displaying the latest morning/evening briefing with expand-to-read-full-text and copy support; legacy preference `stats_summary` auto-migrates to `stats_briefing`.
- **Intimacy Detail Sheet (#350 / PR #359)**: Intimacy card on the Partner tab is now clickable to open details (4-stage journey and point source breakdown), consolidating formula constants into a single source of truth.
- **Settings Hub Card Consolidation (#354 / PR #359)**: Merged "Permission Hub" and "AI Companion & Proactive Push" into a collapsible "Permissions & AI Companion" card (showing `N/7` granted permissions and briefing status when collapsed), with automatic redirect for legacy deep links.
- **Memory & Partner Tab Layout Redesign (#357 / PR #359)**: Added a 4-tile visual memory stats overview at the top of `MemoryTab` (Long-term / Short-term / Conflict / Immune, tap-to-filter) and a "Memories / Rules" layered filter bar; updated `PartnerTab` avatar card with pill badges, unified corner radii, and equal-height two-line 7-day horizontal event cards.

**Behavior Changes**
- **Daily AI Summary Feature Removed (#349 / PR #359)**: Removed scheduling, completion push notifications, dedicated model purpose (`AiPurpose.DAILY_SUMMARY`), and related settings (`ai_daily_summary`, `ai_daily_summary_notif`, `ai_model_daily_summary`, `stats_summary` card); weekly summary is unaffected and its notifications are now gated by token-saver mode; existing `ai_summary` database records are preserved untouched.
- **Briefing Read Scoring Deduplicated by `messageId` (#349 / PR #359)**: Each briefing scores only once across chat reading, Stats card expansion, and notification tap paths.
- **Added `PACKAGE_USAGE_STATS` Permission in AndroidManifest (#346 / PR #359)**: Declared usage stats access (`tools:ignore="ProtectedPermissions"`) for Permission Hub checks and guidance.

**Changed & Fixed**
- **Dynamic Purpose Model Counts & Outdated Copy Fixes (#345 / PR #359)**: Changed purpose model card category count to dynamic counting and fixed token-saver mode and multiple setting descriptions.
- **Removed Duplicate Quick Toggles from Partner Tab (#351 / PR #359)**: Removed personality/verbosity quick toggles from Partner tab while keeping the direct Settings entry.
- **Proactive Notification Pre-Send Permission Check & Guidance (#352 / PR #359)**: Checks system notification permission before sending proactive notifications; shows a warning and direct system notification settings link in the Settings hub card and AI Companion settings when unauthorized.
- **Discord Cloud Alert Payload & Log Security Hardening (#353 / PR #359)**: Switched Discord Webhook payload construction to Gson `JsonObject` (fixing invalid JSON risk from manual escaping) with pre-send authorization check; removed all response content from log outputs.
- **Onboarding & Settings Tour Recompilation (#355 / PR #359)**: Aligned copy and new IA (with deep-link normalization) for the 20-step Home tour and 9-step Settings tour, and rewrote developer jargon in `SettingsCatalog` into plain language.
- **Light/Dark Contrast Audit Fixes (#356 / PR #359)**: `PhantomGlassCard` now provides theme- and `CardBgMode.DARK`-aware `LocalContentColor`; fixed `Scaffold.contentColor` and `TopAppBar` title/back button contrast across multiple settings/hub screens.
- **Integration Re-Check & Minor Polish (#358 / PR #359)**: Hoisted local `data class` in `MemoryTab` to file top level, added non-secret exception logging in `ProactiveEngine`, and completed `SettingsCatalog` consistency and OEM compatibility verification.

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

### English
- **Tag Compare**: [`v3.1.0...v3.2.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.1.0...v3.2.0) (`versionCode = 41`, `versionName = "3.2.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added, Changed & Fixed**
- **Schedule Memo Routing Fix (#333 / PR #342, Behavior Change)**: Schedule notifications default to creating only local calendar suggestion cards (`schedule_memo_route = suggest_calendar`).
- **v3.0.0–v3.1.0 Health Check & Fixes (#334 / PR #342)**: Fixed `CalendarBridge.queryFreeSlotsSummary` `ALL_DAY` UTC cross-day bug and excluded sensitive memory `content` matching in `ChatScreen`.
- **Memory × Anti-Scam Visualization (#335 / PR #342)**: Added "Scam Immunity" filter chip & section in Memory tab with Snackbar jump.
- **Contacts Integration (#336 / PR #342)**: Room v20 (`contact_aliases`), local nickname binding, zero-token relationship import, VIP whitelist sync, and offline acquaintance-impersonation detection (raw contacts never sent to AI).
- **Proactive Push & Personal Briefing Engine (#337 / PR #342)**: 08:45/22:25 briefings, 15-min zero-token SHA-256 change detection, "What I Know About You" card, optional `ACCESS_COARSE_LOCATION`, and BYOK external search endpoint.
- **AI Companion Tab (#338 / PR #342)**: Added 6th tab "AI Companion" with custom name/avatar, companion days, intimacy formula, 7-day activity, and profile card.
- **Linked Apps Hub & Cross-App Action Cards (#339 / PR #342)**: Added `LinkedAppsActivity` (6 local cards, GitHub PAT 15-min polling, encrypted Discord Webhook) and "Confirm Before Execute" cross-app cards.
- **Settings Reorganization & 20/10-Step Tours (#340, #341 / PR #342)**: Added `PermissionHubActivity` and `AssistantHubActivity`; rewrote Home (20-step) and Settings (10-step) tours.

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

### English
- **Tag Compare**: [`v3.0.0...v3.1.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v3.0.0...v3.1.0) (`versionCode = 40`, `versionName = "3.1.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Memory × Anti-Scam Cross-Check & Scam Immunity (#328 / PR #330)**:
  - Added `ContactContext` (L1 offline contacts matching layer): reads `READ_CONTACTS` runtime permission (gracefully falls back to memory-only matching without crashing when denied), caches contact display names via `LruCache` (30-minute TTL), cross-checks contacts with `person` memories (habitual app, late-night/early-morning hours, unknown account claiming acquaintance + urgency), and outputs only anonymous conclusion strings—raw contacts never leave the device.
  - `MemoryEngine.buildAntiFraudPromptBlock` integrates context-contradiction cross-checking (`schedule`/`routine`/`preference` `normal` memories injected into risk analysis) and `scam_immunity` retrieval priority weighting, while continuing dual hard-exclusion of `privacyTier = sensitive`.
  - Added `scam_immunity` ("Scam Immunity") memory category: Scam Center `ScamDetailDialog` added one-tap "🛡️ Add to Scam Immunity Memory" confirmation card, plus weekly `ScamImmunitySummaryWorker` (`ScamImmunityPolicy`) scanning 30-day high-risk records to produce confirmation-gated drafts in Memory tab.
  - `AiClient.analyzeMessage` analysis cache key includes memory/contacts version stamp (`getAntiFraudCacheVersion`) so new immunity memories or permission changes are never masked by stale cache; added "Memory × Anti-Scam Cross-Check" master toggle in Settings (default on; when off, prompt, cache key, and token usage match v3.0.0).
- **Local Calendar Integration (#329 / PR #330)**:
  - Added `CalendarBridge` (pure `ContentResolver` on `Dispatchers.IO`, zero third-party dependencies): supports `READ_CALENDAR`/`WRITE_CALENDAR` runtime permissions, selectable calendar list in Settings (none checked by default, showing read/write status), `Instances` free-slot queries (`>=30` min after deducting existing events), and overlap conflict checks.
  - AI Assistant injects `calendar_free_query`/`calendar_add` instructions only when authorized and calendars are selected: free-slot queries compute locally and report truthfully; adding events always opens a confirmation card showing target writable calendar, time, and non-blocking conflict warnings.
  - When `NotificationCollectorService` detects a schedule-type notification with successfully parsed time and no same-name concurrent event in selected calendars, `LruCache`-throttled logic shows an editable "📅 Add to Calendar" suggestion card (with conflict check) in `MessageDetailActivity`.
  - Falls back to `Intent(Intent.ACTION_INSERT)` pre-filling the system Calendar app when write permission or writable calendars are absent.

**Changed & Fixed**
- **AI Response Compatibility & Collapsible Chain-of-Thought (#326 / PR #330)**:
  - `AiClient` switched to `ResponseMessage` (`JsonElement`) + `extractJsonTextValue` to parse polymorphic `content`/`reasoning`/`reasoning_content`, fully supporting array chunk lists, objects, and plain strings and fixing `BEGIN_ARRAY` crashes on models like `zai-glm-5-3`.
  - `ChatScreen` supports chain-of-thought model responses with a default-collapsed "Reasoning" section above the bubble (expands to show full thinking process, clean final reply, plain-text long-press copy, CoT never replayed to API).
  - Removed `isVisionModelSupported` and image vision false-block gates so attached images are always sent and unsupported providers report honest errors.
  - Upgraded Room database v18 → v19 (`MIGRATION_18_19`), non-destructively adding `chat_messages.thinkingContent` via `ALTER TABLE` while preserving full chat history.
- **Memory Tab UI Optimization (#327 / PR #330)**:
  - 9 `MemoryFilterChip` filter tags support collapse/expand with persisted state (`PhantomPrefs.isMemoryFilterChipsCollapsed`), showing only "All" and active filters when collapsed, matching Messages tab's 34dp circular chevron and pill style.
  - Top search bar is triggered by a 48dp search button (`AnimatedVisibility` + `FocusRequester` auto-focus), staying open when non-empty, collapsing on empty blur, and clearing text before collapsing on `BackHandler`, preserving the "search → select all → batch delete" flow.
  - Standardized Memory tab top bar layout (title, search button, add, gear) and `48dp` touch targets.

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

### English
- **Tag Compare**: [`v2.20.0...v3.0.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.20.0...v3.0.0) (`versionCode = 39`, `versionName = "3.0.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **AI Memory Bank Core Engine (#314 / PR #319)**:
  - Upgraded Room database v17 → v18 (`MIGRATION_17_18`), adding `memories` table (with `category`, `subject`, `content`, `layer`, `status`, `confidence`, `sourceType`/`sourceRef`, `privacyTier`, `createdAt`/`updatedAt`, `lastUsedAt`/`useCount`, etc., and 4 indexes: `(status,layer)`, `(category,subject)`, `(updatedAt)`, `(privacyTier)`).
  - Added `MemoryEngine`: same-`subject` deduplication and conflict detection (`status = conflict` keeps both entries and never overwrites), `resolveConflictGroup` resolution (winner set to `active + confirmed`, loser set to `archived`); short-term memory `expiresAt` defaults to 7 days, archived on startup/query via `lazyArchiveExpiredShortTerm` and daily `MemoryExpiryWorker`.
  - Pure local deterministic Top-K retrieval (zero extra API calls) scored by keywords, category intent, and time conditions, injected into `buildRagSystemPrompt`'s `[Long-Term Memory]` block (with `AiClient.sanitizeForPrompt` anti-injection), updating `lastUsedAt` and `useCount` on hits.
  - Dual hard-exclusion gate for `privacyTier = sensitive` (SQL `privacyTier = 'normal'` + Kotlin `.filter { it.privacyTier != PRIVACY_SENSITIVE }`).
  - `MemoryEngine.loadIntegratedSnapshot` unifies queries for existing memos (`notes (source=auto)`), `ai_rules` (whitelist/tags), `user_tag_rules`, and `memo_rules`, providing unified query, toggle, and delete interfaces (wired to `NotificationCollectorService.invalidateRuleCaches()`, zero extra table migrations).
- **5th Tab "Memory" (#315 / PR #319)**: `MainTab.MEMORY("Memory")` integrated into `LiquidGlassTabBar` (Standard / Compact / Auto-Hide modes), `saveableStateHolder`, and `PhantomBackgroundBox(layer = GLOBAL)`, with full `MemoryTab` UI: top search bar, 9 filter chips (`FlowRow` adapting to large fonts and narrow screens), 8 collapsible sections (⚠️ Conflicts Pending, Long-Term Memories, Short-Term Memories, Archived Memories, Auto-Memos, Whitelist Rules, Tag Rules, Auto-Note/Todo Rules), source & badge display (`AI Inferred` / `Confirmed` / `🔒 Sensitive` / expiry time), two-button conflict resolution, full add/edit dialog, swipe-left archive/restore/delete, multi-select batch delete confirmation dialog, and top-right settings gear jumping directly to Settings "Memory Bank & AI Assistant" card.
- **Custom AI Assistant (#315 / PR #319)**: Supports custom name (default "PhantomCheck"), personality presets (Default / Concise & Pro / Friendly & Detailed / Strict Anti-Scam Advisor / Fully Custom) + custom personality field, and response verbosity (Concise / Balanced / Detailed), injected into `[Assistant Settings]` strictly capped at `<= 120` tokens, with circular avatar and name displayed live in `ChatScreen` top bar and assistant reply bubbles.
- **New Settings Category "Memory Bank & AI Assistant" (#315 / PR #319)**: Collapsible card `card_memory_assistant` (including memory extraction, dual-mode independent injection sliders, custom assistant & circular avatar crop, and privacy tier info), registering 9 settings in `SettingsCatalog` (`memory_chat_capture`, `memory_normal_top_k`, `memory_normal_per_item_chars`, `memory_saver_top_k`, `assistant_custom_name`, `assistant_personality`, `assistant_verbosity`, `assistant_avatar`, `memory_privacy_tier_info`), supporting local search, AI semantic search, and in-chat `setting_toggle` / `setting_adjust` / `open_setting`.

**Changed & Fixed**
- **Token Saver Quick Toggle (#315 / PR #319)**: Added a Token Saver quick-toggle chip and `Info` button in AI Assistant top bar, synced 100% bidirectionally in real time with Settings via `PhantomSettingsBus`; info dialog maps item-by-item to actual `PhantomPrefs.isEffective*` behavior.
- **Safe Browsing Key Switched to BYOK (#316 / PR #318, Behavior Change)**: Removed `BuildConfig.SAFE_BROWSING_API_KEY` / `local.properties` fallback path, unifying exclusively on `EncryptedSharedPreferences` input in Settings; honestly skips Safe Browsing when unset with consistent UI hints, and release builds no longer retain any `SAFE_BROWSING_API_KEY` field.
- **Chat Timestamp Format Fixed to `Locale.US` (#316 / PR #320)**: `CHAT_TIME_FORMATTER` is constructed with `DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.US)`, keeping `zh-TW` display byte-identical while preventing non-ASCII numeral drift on other system locales.
- **Onboarding Auto-Skip Dual Conditions (#316 / PR #320)**: Onboarding auto-skip now requires both a 400ms delay with measured bounds and the target still absent before advancing, preventing false skips on slow devices during tab transitions.
- **Removed Outdated Safe Browsing Card Copy (#316 / PR #320)**: Changed "Key status: Encrypted & stored in App (takes precedence over BuildConfig)" to "Key status: Encrypted & stored in App", and removed the unreachable "Key status: Injected from local.properties" branch (`getSafeBrowsingKeySource` returns only `EncryptedPrefs` / `NONE`).

## [2.20.0] - 2026-11-20

### 修正
- **記事頁待辦事項展開區可捲動與高度上限（#310 / PR #311）**：
  - `NotesTab` 待辦事項展開區（`!todoSectionCollapsed.value`）外層改為 `Column(Modifier.heightIn(max = maxTodoExpandedHeight))`，上限取自 `BoxWithConstraints` 可用高度之 2/3（`maxHeight * 2f / 3f`），避免清單過長時撐高卡片擠壓下方記事區。
  - 篩選膠囊列 `FlowRow` 與 `8.dp` `Spacer` 固定於外層 `Column` 頂部，不隨清單捲動；清單段改為 `Column(Modifier.weight(1f, fill = false).verticalScroll(todoListScrollState))` 支援卡內上下捲動，並以 `key(todoFilter) { rememberScrollState() }` 於切換篩選條件時重置捲動位置至頂部。
  - 短清單或空清單維持依內容 wrap-content 高度呈現，不強制填滿 2/3；收合精簡顯示（`todoSectionCollapsed = true`）與收合狀態持久化維持不變。

### English
- **Tag Compare**: [`v2.19.0...v2.20.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.19.0...v2.20.0) (`versionCode = 38`, `versionName = "2.20.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.
**Fixed**
- **Scrollable & Height-Capped To-Do Expanded Area in Notes Tab (#310 / PR #311)**: Capped `NotesTab` expanded to-do area to 2/3 of `BoxWithConstraints` available height (short lists still wrap-content); pinned filter pills at top, enabled inner vertical scrolling, and reset scroll position on filter switch.

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

### English
- **Tag Compare**: [`v2.18.0...v2.19.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.18.0...v2.19.0) (`versionCode = 37`, `versionName = "2.19.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.
**Added, Changed & Fixed**
- **Center Stack v6 Edge-Fill & Short-List Mode (#298 / PR #302)**: Added symmetric `pAbove`/`pBelow` edge-fill compensation and top-pinned short-list layout in `CenterFocusCardStack`, with `edgeGap = 6dp`.
- **Storage Optimization & Database Maintenance (#299 / PR #303, #306 / PR #307)**: Enabled R8 code/resource shrinking in `release` builds; Room v17 (`MIGRATION_16_17`) indexes; periodic `VACUUM`, 300-entry avatar cache cap, and `intel_cache` cleanup; added storage breakdown and "Compact Database Now" in Settings.
- **Battery & Stability Optimization (#300 / PR #304)**: Relaxed KeepAlive (15→45 min) and Shizuku (300→600 s) polling, `KEEP` Heartbeat debouncing, `StatsForegroundService` rescue backoff, and screen-off early exit.
- **UI Component Splitting (#301 / PR #305)**: Split `MainActivity` and `ChatScreen` into `MessagesTab.kt`, `NotesTab.kt`, `StatsTab.kt`, `TodoSection.kt`, and `ChatScreenParts.kt` (zero behavior change).

## [2.18.0] - 2026-11-19

### 修正
- **移除內建個人化預設與設定頁衝突文案（#292 / PR #293）**：
  - `PhantomPrefs.DEFAULT_MY_DISPLAY_NAMES` 改為空集合，未設定時不預帶任何預設顯示名稱；`isMyDisplayName` 與自我身分偵測於空集合時不再誤判。
  - 移除 `NotificationCollectorService` 內建個人化 AI 規則自動寫入（`seedDefaultAiRulesIfNeeded`），並新增一次性清除機制 `purgePresetAiRulesIfNeeded`（以 `preset_ai_rules_purged_v1` 標記僅刪除舊版自動寫入的兩條預設規則，保留使用者自建規則，資料庫 schema 不變）；同時將 AI prompt 與管理頁範例文案去品牌化。
  - 快速設定「AI 風險分析」副標改為動態引用 `aiMinTriggerLength` 字數門檻，不再寫死 10 字元。
  - 「每日 AI 額度上限」標題與 `SettingsCatalog.NUMERIC_SPECS` 單位統一為「次」，chip 支援顯示並選中非預設值（0–100 次、步進 10 次）。
  - 省 token 模式生效時，「每則訊息 AI 智慧掃描」與「每日 AI 摘要」副標顯示「已暫停」，「AI 風險分析」副標提示「僅命中可疑特徵才分析」。

### English
- **Tag Compare**: [`v2.17.0...v2.18.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.17.0...v2.18.0) (`versionCode = 36`, `versionName = "2.18.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.
**Changed & Fixed**
- **Removed Built-in Personalization Defaults for Full Generalization (#292 / PR #293)**: Removed default "My Name" pre-fill and automatic personal AI rules (one-time cleanup of legacy auto-written telecom whitelist & shopping tag rules on upgrade; user rules preserved).
- **Fixed Conflicting Settings Copy (#292 / PR #293)**: Dynamic trigger character threshold (1–30 chars) in AI Risk Analysis subtitle; aligned daily AI quota (0–100, step 10); marked paused toggles when Token Saver is active.

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

### English
- **Tag Compare**: [`v2.16.0...v2.17.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.16.0...v2.17.0) (`versionCode = 35`, `versionName = "2.17.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.
**Added & Fixed**
- **Home/Settings Onboarding Fixes, OEM Restricted-Settings Guides & One-Tap Shizuku Auth (#288 / PR #289)**: Waits for Android 13+ permission dialogs before showing onboarding; merges 4 empty-DB message steps; adds 4-brand restricted-settings troubleshooting (Xiaomi/Samsung/OPPO/vivo), direct Notification Access detail links, and one-tap Shizuku authorization; fixed 8-step Settings tour.
- **Privacy Policy & Disclaimer (#284 / PR #286)**: Added `PRIVACY.md` and `DISCLAIMER.md`.
- **GPL-3.0 Open-Source License (#285 / PR #287)**: Added `LICENSE` (GNU GPL v3.0) and README License section.

## [2.16.0] - 2026-11-19

### 變更與修正
- **中央堆疊 v5 微調（#280 / PR #281）**：
  - 訊息／記事堆疊上下留白緊湊化：`CenterFocusCardStack` 新增 `edgeGap = 4.dp`（取代原 `8.dp`）延伸上下可視範圍；`MessagesHomeTab` 於 `CENTER` 模式外層區塊間距由 `12.dp` 緊湊為 `8.dp`（非 `CENTER` 模式維持 `12.dp`）；記事頁堆疊容器上方 `Spacer` 於 `CENTER` 模式由 `8.dp` 縮為 `4.dp`（`LIST` 模式維持 `8.dp`），Scaffold 共用外層內距、我的最愛列與底部導覽列不變。
  - 阻尼彈簧吸附：改採官方 `snapFlingBehavior`（`SnapLayoutInfoProvider(listState, SnapPosition.Start)` ＋ `rememberSplineBasedDecay` ＋ `spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)`），維持 `s_i = i * exposure` 吸附幾何與互補式 `contentPadding`。
  - 近焦縮放緩衝與焦點陰影漸變：於同一 `graphicsLayer` draw phase 內加入 `FastOutSlowInEasing` 近焦縮放緩衝（`absU <= 1f` 由 `0.94f` 平滑過渡至 `1.0f`，`absU > 1f` 沿 `0.04f` 遞減至下限 `0.82f`，於 `absU = 1` 連續銜接，pivot 維持頂緣 `TransformOrigin(0.5f, 0f)`），並新增 `cardShape = RoundedCornerShape(20.dp)` 與 `focusShadowBoost = 6.dp` 隨 `eased` 漸變陰影；`translationY` 線性鎖定、`alpha` 邊緣淡出與 `zIndex` 連續公式皆維持不變。

### English
- **Tag Compare**: [`v2.15.0...v2.16.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.15.0...v2.16.0) (`versionCode = 34`, `versionName = "2.16.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.
**Changed & Fixed**
- **Center Stack v5 Polish (#280 / PR #281)**: `edgeGap = 4dp`, official `snapFlingBehavior` with `spring(0.85f, StiffnessMediumLow)`, `FastOutSlowInEasing` near-focus scale (`0.94f → 1.0f` then `0.04f` step-down to `0.82f`), and `6dp` focus shadow gradient.

## [2.15.0] - 2026-11-19

### 變更與修正
- **中央堆疊 v4 修正（#276 / PR #277）**：
  - 修復滑動時焦點上方卡片被裁切／快甩後缺繪：改採對稱 `contentPadding`（`leading >= 3 * exposure` 且與 `trailing` 互補，總捲動區間 `(n - 1) * 40.dp` 與吸附手感不變），確保焦點上方 2~3 張卡片原生落入 `visibleItemsInfo` 穩定組合繪製、露出頂帶可辨識 App 圖示與名稱；唯一 `clipToBounds` 僅保留於最外層堆疊容器（我的最愛下緣至底部導覽上緣）。
  - 初始貼頂與線性過渡：`firstVisibleItemIndex == 0` 且 `offset == 0` 時錨點 `8.dp` 貼頂零留白；頭尾過渡由 `smoothstep` 改為線性 `lerp`（以 `zone` 正規化），連續單調無布林切換。
  - 記事短清單幾何修正：過渡區寬度改為動態 `zone = min(1.5f, (n - 1) / 2f)`，根治 `n <= 4` 時頭尾過渡區重疊造成第 2 則記事沉底；記事頁續用共用堆疊元件、容器高度由 `BoxWithConstraints` 動態計算。
  - 待辦事項到期副標單行：`NotesTodoRow` 到期資訊 `Text` 新增 `maxLines = 1`、`softWrap = false` 與 `TextOverflow.Ellipsis`，不再被右側按鈕擠成兩行（展開／收合兩狀態同時生效）。

### English
- **Tag Compare**: [`v2.14.0...v2.15.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.14.0...v2.15.0) (`versionCode = 33`, `versionName = "2.15.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.
**Changed & Fixed**
- **Center Stack v4 Continuous Inertial Scroll (#276 / PR #277)**: Replaced step-by-step `Pager` with `LazyColumn` + `rememberSnapFlingBehavior` (`viewportCenterOffset`), keeping `92dp` compact cards, `40dp` peek band, and `0.82f` scale floor.

## [2.14.0] - 2026-11-19

### 變更與修正
- **中央堆疊卡片緊湊化（#270 / PR #272）**：
  - 單卡高度鎖定 `92.dp`（`fontScale >= 1.3f` 上限 `104.dp`），內距 `12.dp` 三段式佈局撐滿內容、消除卡片內空黑底。
  - 堆疊錯位與 `edgeFade` 調整為 `40.dp`，縮放每層 `0.04f` 遞減（下限 `0.82f`），底部 `40.dp` 露出帶顯示 App 名稱＋未讀數／記事標籤，訊息頁與記事頁表現完全一致。
- **自動檢查更新改為每次開啟 App 一次（#271 / PR #273）**：
  - 移除每日檢查 gate 與彈窗去重，開啟開關且網路可用時每次開啟 App 檢查 GitHub 最新版本並提示；通知欄通知維持每日每版本最多一則。
  - 以 process 級 `AtomicBoolean` 旗標防重入，同一行程螢幕旋轉與返回頁面不重複請求 API；無網路、403/429、逾時靜默處理。

### English
- **Tag Compare**: [`v2.13.0...v2.14.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.13.0...v2.14.0) (`versionCode = 32`, `versionName = "2.14.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Changed & Fixed**
- **Compact Center Stack Cards (#270 / PR #272)**:
  - Locked single-card height to `92.dp` (`104.dp` cap when `fontScale >= 1.3f`) with a `12.dp` padded three-part layout filling the card and eliminating empty black background space.
  - Tuned stack offset and `edgeFade` to `40.dp`, scale decrement `0.04f` per layer (floor `0.82f`), and a `40.dp` bottom peek band displaying app name + unread count / note tags, behaving identically across Messages and Notes tabs.
- **Auto Update Check Changed to Once Per App Launch (#271 / PR #273)**:
  - Removed the daily check gate and dialog dedup; when enabled and online, checks GitHub's latest release on each app launch and prompts; notification-shade alerts remain capped at once per version per day.
  - Added process-level `AtomicBoolean` re-entrancy guard so screen rotations and returning to the page within the same process do not re-request the API; silently handles offline, 403/429, and timeouts.

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

### English
- **Tag Compare**: [`v2.12.0...v2.13.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.12.0...v2.13.0) (`versionCode = 31`, `versionName = "2.13.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Messages / Notes Center Stack v3 (#263 / PR #264)**:
  - Zero whitespace & full card fill: uses dynamic layer count, `8.dp` margins, and dynamic `contentPadding` (`bottom = viewport - exposure`) so all items are reachable with zero bottom whitespace.
  - Continuous inertial scrolling: switched to `LazyColumn` + `LazyListState` (`cacheWindow 1.0/0.5`) + monotonic `SnapPosition.Start` snapping, removing the one-card-at-a-time limit; `rememberSnapFlingBehavior` supports fast flings across multiple cards with smooth snapping.
  - Continuous lerp transitions & visual parameters: smoothstep continuous interpolation across top/mid/bottom anchors eliminates jumping on card 0 and the head segment; `44.dp` exposure, top-edge pivot `scaleStep = 0.035f`, opaque inside viewport with edge-pixel fade.

**Changed & Fixed**
- **Fixed Dual-Path Duplicate SMS Recording (PR #268)**:
  - `SmsAutoReplyReceiver` now handles auto-reply only and removes `db.messageDao().insert` writes; SMS records are handled solely by the notification interception path (`NotificationCollectorService`) with SHA-256 `dedupKey` deduplication. Time-window checks, sensitive keywords, `TW_MOBILE_REGEX`, 4-hour cooldown, and 3/day cap remain unchanged (`shouldSkipReply` returns early to avoid unnecessary coroutine scheduling).
  - Note: issue #106 pending on-device verification before closing.

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

### English
- **Tag Compare**: [`v2.11.0...v2.12.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.11.0...v2.12.0) (`versionCode = 30`, `versionName = "2.12.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Messages Tab Top-to-Center Stack Refactor (#249)**:
  - Refactored `Components.kt` `CenterFocusCardStack` (v2): fixed `cardHeight = 112.dp` (message preview `maxLines = 1` + `Ellipsis`, app icon `44.dp`, `contentPadding` `12.dp` / compact `8.dp`), `VerticalPager` using `PageSize.Fixed(cardHeight + 24.dp)` and dynamic bottom `contentPadding` so the last card can scroll to center.
  - Equidistant stack constants: `exposure = 24.dp`, `scaleStep = 0.06f` (layer n scale `1 - 0.06n`), `maxVisibleLayers = 4` (`3` when screen height `< 640dp`, `alpha = 0` beyond).
  - Continuous interpolation by scroll progress `f ∈ [0, 1]` between top-aligned (`scroll = 0`, card 0 flush at `topGap = 10.dp` with highest zIndex) and center-aligned (`|scrollOffset| >= 0.5`), computed purely in `graphicsLayer` without recomposition; applied in `MainActivity` Messages tab while preserving Standard List, Unread tab, and tap/long-press gestures.
- **Notes Tab Shared Center Stack & Removal of "Peek Top" Mode (#250)**:
  - Notes "Center Stack" now shares the new `CenterFocusCardStack`: `NoteCardItem` fixed at `112.dp` height, body `maxLines = 2` + `Ellipsis`, pin/edit/delete moved to small top-right icon buttons (no bottom button bar); todo collapse and overdue sorting unchanged.
  - Removed `PhantomPrefs.NoteCardLayoutMode.PEEK_TOP`; `getNoteCardLayoutMode` auto-migrates legacy `"peek_top"` to `CENTER`; removed the card-deck branch from `MainActivity`.
  - Removed "Peek Top / Card Deck" descriptions and keywords from `SettingsActivity` and `SettingsCatalog`; unified `valueLabels` for `home_card_layout` and `notes_card_layout` to `["Standard List", "Center Stack"]` (`LIST → 0`, `CENTER → 1`).
- **AI Auto-Todo Smart Time Inference & 30-Minute Lead Reminder (#252)**:
  - `AiClient.buildSystemPrompt` injects current time in system timezone and instructs AI to infer concrete `yyyy-MM-dd HH:mm` for `todo_due` from message clues (e.g., "tomorrow 9am", "Wednesday night", "before 10/28"), leaving blank only when no clues exist.
  - `NotificationCollectorService.saveAutoMemoIfPresent` uses a fallback chain: `parseStructuredDueAt(todoDue)` (filtering past times) → `ClientTimeParser.parseNaturalReminderTime` → `msg.timestamp + 2h` (wrapped in `try/catch`); applies `REMINDER_LEAD_MINUTES = 30` when an event time exists (or `max(now + 5min, eventTime - 30min)` if `<= now`).
  - `ClientTimeParser` handles year-rollover `MM/dd` (e.g., `1/2` received on 12/31 rolls to next year); duplicate-todo update path applies the same rule and reschedules `ReminderScheduler.scheduleTodo` when `dueAt` changes and `> now`.

**Changed & Fixed**
- **Pinning / Unpinning Notes No Longer Overwrites `updatedAt` (#251)**:
  - `MainActivity.onTogglePinNote` flips only `pinned` on `NoteEntity`, preserving original `updatedAt` display time and maintaining `NotesWidgetProvider.updateAllIfChanged` sync.
- **Per-Purpose Binding & Live Sync for Deep Analysis & Daily Summary Model Chips (#253)**:
  - `MessageDetailActivity` deep-analysis chip and `MainActivity` `StatsTab` daily-summary chip now read `AiClient.getProfileForPurpose` (`DEEP_ANALYSIS` / `DAILY_SUMMARY`), write to `PhantomPrefs.setPurposeProfileId` + `PhantomSettingsBus.notifyChanged()` (no longer overwriting global `setActiveProvider`), and reload on `PhantomSettingsBus.version` and `ON_RESUME`; empty-key check updated to `getProfileForPurpose`.
  - `ChatScreen.AiProviderSwitcherChip` added `label` parameter (default `"Chat Model:"`), showing "Deep Analysis Model:" and "Daily Summary Model:" respectively.
- **Daily & Weekly Summary Punctuality Hardening (#254)**:
  - Added `maybeCatchUp(ctx)` to `DailySummaryPolicy` and `WeeklySummaryPolicy` to check and immediately catch up missed daily/weekly summaries on `MainActivity.onCreate`, `BootReceiver`, and after saving in `SettingsActivity`.
  - `DailySummaryWorker` returns `Result.retry()` (`BackoffPolicy.EXPONENTIAL, 10min`, max 3 times) on failure without overwriting retry schedules, rescheduling tomorrow only on success or cap.
  - `WeeklySummaryPolicy.generateWeeklySummary` added `anchorMs` to compute the scheduled week window (`between(start, start + 7d)`) and `weekKeyFor(anchorMs)`, preventing Doze delays to Monday from shifting into the new week; constraints changed from `RequiresBatteryNotLow` to `NetworkType.CONNECTED`.

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

### English
- **Tag Compare**: [`v2.10.0...v2.11.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.10.0...v2.11.0) (`versionCode = 29`, `versionName = "2.11.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Home Card Layout Rewritten as "Center-Focus Vertical Wheel Stack" (#240)**:
  - Simplified `PhantomPrefs.HomeCardLayoutMode` to `LIST` (Standard List) / `CENTER` (Center Stack), auto-migrating legacy `peek_top`/`peek_bottom` and `card_stack_effect_enabled` to `CENTER`; updated `SettingsActivity` to two pill options.
  - Added `CenterFocusCardStack` in `Components.kt`: `VerticalPager` + `PageSize.FillParentSize` (`beyondViewportPageCount = 2`) where each page uses `graphicsLayer` with `pagerState` offset to compute `translationY` (symmetric vertical stagger), `scale` (`|q|=1 → 0.92f`, `|q|=2 → 0.84f`), `alpha` (hidden beyond distance 2), and `zIndex` (`derivedStateOf`); the focused card stays vertically centered with native Pager snap, and both first and last cards scroll to center.
  - Stack cards use `PhantomGlassCard(overlapOpaqueBoost = true)` for an opaque dark surface (`#F6161822` >= 0.96, light theme surface 0.96) to prevent bleed-through; removed broken peek mode, negative spacing, and custom snap layout from `MessagesHomeTab`.
- **Notes Tab Collapsible Todos + Smart Sort & Three Note Card Layout Modes (#241)**:
  - Added `NoteCardLayoutMode` (`LIST` / `PEEK_TOP` / `CENTER`, key `note_card_layout_mode`) and `notes_todo_collapsed` (default collapsed) in `PhantomPrefs`; refactored `NotesTab` into "search bar + collapsible todo card + `weight(1f)` notes container".
  - Tapping the todo card header (with 34dp chevron) toggles collapse with `AnimatedVisibility` and persistent state; when collapsed, hides filter chips and shows only the nearest upcoming non-overdue incomplete todo (or earliest overdue if all overdue, or a prompt if none).
  - Rewrote `ReminderScheduler.sortTodosSmart`: incomplete non-overdue (`dueAt` ascending) → incomplete overdue (`dueAt` ascending) → completed (completion time descending), shared across AI Assistant and home-screen widget.
  - Note card layout modes: Standard List; "Peek Top" (`LazyColumn` + `SnapLayoutInfoProvider(SnapPosition.Start)` card-deck stack with 52dp `graphicsLayer` offset and descending `zIndex`); "Center Stack" reusing `CenterFocusCardStack`; all sharing extracted `NoteCardItem` (6-line body cap and `overlapOpaqueBoost` in stack modes); notes container fills vertical space when todos are collapsed.
- **Collapsible Sub-Sections in Settings (#242)**:
  - "Per-App Throttle Overrides" and "Custom Quick Commands (N)" are now collapsible sub-sections (default collapsed with count summary), persisted via `PhantomPrefs.isSettingsSubCardExpanded`/`setSettingsSubCardExpanded` (`settings_expanded_subcards`) independently of main category cards (`KEY_COLLAPSED_CARDS`).
- **Home Filter Chip Row Collapsible to Single Line (#245)**:
  - When collapsed, `MessagesHomeTab` filter row shows only "All, Unread" plus the active filter/tag chip on a single non-wrapping line, toggled by a 34dp rounded chevron on the right and persisted via `home_filter_chips_collapsed`.

**Changed & Fixed**
- **Eliminated Square Black Corners Outside Floating Surfaces (#243)**:
  - Added `PhantomDialog` in `Components.kt`: `Dialog(usePlatformDefaultWidth = false)` + `SideEffect` setting window background to `ColorDrawable(TRANSPARENT)` so the opaque dark surface is drawn solely by inner `PhantomFloatingSurface` (`clip → background → border`) with 100% wallpaper transparency outside rounded corners; migrated "Manual Auto-Note Rule" dialog from `AlertDialog` to `PhantomDialog`.
  - Set `containerColor = Color.Transparent` on all 4 `ModalBottomSheet` instances (`MainActivity` message actions & tag picker, `ConversationScreen` message actions, `ChatScreen` todo reminder list), wrapping content in `PhantomFloatingSurface`.
- **UI & Settings Model Live Sync (#244)**:
  - Home/Notes layout modes and collapse states now observe `PhantomSettingsBus.version.collectAsState()` to re-read `PhantomPrefs`, taking effect immediately upon returning from Settings.
- **On-Time Daily / Weekly Summary Triggers (#246)**:
  - Switched `DailySummaryPolicy` / `WeeklySummaryPolicy` from `PeriodicWorkRequest` + `UPDATE` (which kept stale anchors after schedule edits) to `OneTimeWorkRequestBuilder` + `enqueueUniqueWork(REPLACE)` with exact `initialDelay` (60s floor) and `try/finally` self-rescheduling in Worker `doWork` to eliminate schedule drift.
- **AI Assistant / Settings Search Support for New Settings (#247)**:
  - Updated `SettingsCatalog` with `personal_home_card_layout` description/aliases and added `personal_notes_card_layout`, `personal_notes_todo_collapsed`, and `personal_home_filter_chips`; added `valueLabels` option-type numeric settings in `NumericSettingSpec` for `home_card_layout` (0=Standard List / 1=Center Stack) and `notes_card_layout` (0=Standard List / 1=Peek Top / 2=Center Stack), enabling AI Assistant to switch layout modes directly.
- **Release Preparation (#247)**:
  - Bumped version to `versionCode` 29, `versionName` `"2.11.0"`; synced `CHANGELOG.md` and `README.md` feature overview.

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

### English
- **Tag Compare**: [`v2.9.0...v2.10.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.9.0...v2.10.0) (`versionCode = 28`, `versionName = "2.10.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Three-Way Home Card Layout Mode (#230)**:
  - Added `HomeCardLayoutMode` in `PhantomPrefs` (`LIST` default / `PEEK_TOP` card-deck 52dp top peek / `PEEK_BOTTOM` folder-style bottom peek with scale), auto-migrating legacy `card_stack_effect_enabled` (`true → PEEK_BOTTOM`, `false → LIST`).
  - Overlap modes use `MessagesHomeTab` `LazyColumn` + `rememberSnapFlingBehavior` + custom `SnapLayoutInfoProvider` controlling `zIndex` and `graphicsLayer` (`translationY`/`scaleX`) snap, with `PhantomGlassCard(overlapOpaqueBoost = true)` to prevent text bleed-through; removed `StackedAppCard` and legacy toggle, and added 3 side-by-side `FilterChip`s in `SettingsActivity` (wired to `SettingsCatalog` `personal_home_card_layout` and `PhantomSettingsBus`).
- **Sender Avatar Extraction & Multi-Tier Display (#231)**:
  - Upgraded Room to v16 (`MIGRATION_15_16`) with `sender_avatars` table; `NotificationCollectorService` extracts `MessagingStyle.Message.getSenderPerson()?.getIcon()` during `EXTRA_MESSAGES` parsing, downsamples to `<=96px` and compresses to `<=16KB` before upserting (auto-cleaning avatars older than 30 days).
  - Added `SenderAvatarImage` (`LruCache` 128 items) in `AppIconHelper` with priority order `sender_avatars` → circular-cropped app icon → Material colored initial avatar; integrated into `AppThreadsScreen` (40dp) and `ConversationScreen` non-`isOwn` bubbles (28dp top-aligned).
- **Self-Reply Echo Suppression & Self Display Names (#237)**:
  - Added `own_reply_echo` table in `MIGRATION_15_16`; writes suppression rows after successful notification quick-reply or SMS fallback sends, and within 3 minutes `NotificationCollectorService` skips duplicate insertion and deletes the suppression row if normalized tail text matches exactly or `diceBigramSimilarity >= 0.9`.
  - Added `my_display_names` in `PhantomPrefs` (default includes `"Eric Chiu"`, trimmed case-insensitive match) so group notification messages from self are marked `isOwn = true` / `isRead = true` and skip scam alerts; added editor entry in Settings "Messages & Notifications" and `SettingsCatalog` (`notif_my_display_names`).

**Changed & Fixed**
- **Fixed Floating Layer Black Background Bounds & Scrim (#229)**:
  - Added `10.dp` vertical insets and `displayCutoutPadding()` to `ChatScreen` history drawer container so `20.dp` rounded corners sit cleanly inside the wallpaper; unified drawer scrim and `MainActivity` auto-note rule full-screen scrim to `0.45f`, and adjusted `PhantomFloatingSurface` dark surface to `0xEB12151E` (~92% opaque).
- **Left-Aligned Notes Search Pill (#232)**:
  - Removed `weight(1f)` from `PhantomSearchBarPill` on `notes_search_pill` in `MainActivity` `NotesTab`, making it compact left-aligned with `Spacer(weight(1f))` pushing the right `Tune` button (preserving 48dp touch target and safe-area spacing).
- **Velocity-Aware Gesture for AI History Drawer (#233)**:
  - Adjusted `ChatScreen` `AnchoredDraggableState` `positionalThreshold` to `0.35f` and `velocityThreshold` to `400.dp`; drag release uses `VelocityTracker` projection (`offset + velocity * 0.15s`) with `400.dp/s` or `25%` width threshold, keeping deferred lambda `offset {}` reads and zero regression on existing interactions.
- **Strengthened Auto-Todo Similarity Deduplication (#234)**:
  - Enhanced `normalizeForSimilarity` (NFKC, lowercase, punctuation/whitespace normalization) in `NotificationCollectorService.saveAutoMemoIfPresent`, added `findRecentPendingByAppLabel` in `TodoDao`, and applied `diceBigramSimilarity >= 0.9` to update existing todos in place and reschedule reminders/widgets without duplicates.
- **Bottom Tab Bar Liquid Drop & Global Smoothness Optimization (#235, #236)**:
  - Reused `Path` (`rewind()`) and cached `Stroke`/`Color`/`Brush.verticalGradient` in `LiquidGlassTabBar.kt`, tuned liquid spring to `stiffness = 600f, dampingRatio = 0.8f`, and switched `onHorizontalDrag` to synchronous state-driven updates to eliminate per-frame allocations and coroutine churn.
  - Preserved tab state in `MainActivity` via `rememberSaveableStateHolder` (`SaveableStateProvider(selectedTab.name)`); completed codebase audit of `LazyColumn` `key`, `derivedStateOf`, `graphicsLayer` lambdas, `StatsDetailWidgetProvider` SQL `GROUP BY`, and `AppIconHelper` `LruCache`.
- **Release Preparation (#238)**:
  - Bumped version to `versionCode` 28, `versionName` `"2.10.0"`; verified all dependencies at latest stable versions and synced `CHANGELOG.md` and `README.md`.

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

### English
- **Tag Compare**: [`v2.8.0...v2.9.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.8.0...v2.9.0) (`versionCode = 27`, `versionName = "2.9.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Live Settings Sync Bus `PhantomSettingsBus` (#225)**:
  - Added `util/PhantomSettingsBus` (`MutableStateFlow<Long>` version counter + `notifyChanged()`); `SettingsCatalog.applyBooleanSetting` and `applyNumericSetting` emit change notifications immediately after persisting preferences.
  - `MainActivity` (including `StatsTab`), `ChatScreen`, and `SettingsActivity` (including `FilterSettingsScreen` and sub-components `replyProxyEnabled`/`intelEnabled`/`updateAutoCheck`) collect the bus in real time so changes from AI chat or settings search reflect in UI immediately without re-entering or waiting for `ON_RESUME`.

**Changed & Fixed**
- **Fixed Opaque Floating Surface Background & Scrims (#222)**:
  - Added `PhantomFloatingSurface` in `Components.kt` (`.clip(shape)` placed before `.background()`, dark `Color(0xF012151E)`, light `surface.copy(alpha = 0.95f)`, 1dp `outlineVariant` border, without `glassSurface`/blur; in-page glass cards unchanged).
  - Migrated `ChatScreen` history drawer (`cornerRadius = 20.dp`, session card alpha raised to `0.95f` selected / `0.9f` unselected) and `MainActivity` auto-note rule overlay to `PhantomFloatingSurface`, raising full-screen scrims to `0.65f` (`0.65f * progress` for drawer); completed 4-point floating-layer audit.
- **Fixed AI History Drawer Drag Performance (#223)**:
  - Moved `ChatScreen` drawer offset to `Modifier.offset { IntOffset(...) }` lambda reading `drawerState.offset` in layout phase (with `NaN` fallback), scrim alpha to `Modifier.drawBehind` in draw phase, and visibility to `derivedStateOf`; `LaunchedEffect` uses `snapshotFlow` on `currentValue to isAnimationRunning`, eliminating per-frame recomposition during drag.
- **Strengthened Message Card Stack Edge Contrast (#224)**:
  - Changed `StackedAppCard` layer color to `lerp(surfaceVariant, onSurface, 0.25f)`, raised alpha to `0.55f` (layer 1) / `0.35f` (layer 2), adjusted scale to `0.96f` / `0.92f`, and added 1dp `outlineVariant.copy(alpha = 0.4f)` border on layer 1 for clear visibility across light/dark themes and custom wallpapers.
- **Semantic Keyword-Only "Never Auto-Note" & Full-Chain Health Check (#226)**:
  - Restricted literal `ACTION_NEVER` gate in `NotificationCollectorService.saveAutoMemoIfPresent` to rules with `packageName` or `sender`; keyword-only `ACTION_NEVER` rules are evaluated semantically by AI (covering live notifications and `PendingAiAnalysisWorker` offline queue).
  - Structured the note-rule section in `AiClient.buildSystemPrompt` (`id`/`App`/`sender`/`keyword`/`action`) with explicit semantic hit definitions and counter-examples (e.g., Gogolook OTP rule does not block bank OTPs), bumped cache key to `memoV4`, and updated rule panel copy.
- **Release Preparation (#227)**:
  - Bumped version to `versionCode` 27, `versionName` `"2.9.0"`; verified all dependencies at latest stable versions and synced `CHANGELOG.md` and `README.md`.

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

### English
- **Tag Compare**: [`v2.7.0...v2.8.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.7.0...v2.8.0) (`versionCode = 26`, `versionName = "2.8.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Message Card Stack Effect & Personalization Toggle (#212)**:
  - Added `StackedAppCard` wrapper in `Components.kt` (keeping `ui/components/` untouched), drawing 0/1/2 `surfaceVariant` stack layers behind the main card based on message count (with subtle scroll parallax, 140ms tap collapse feedback, translucent muted state, and halved spacing in compact mode).
  - Added "Message Card Stack Effect" `FluidSwitch` (`card_stack_effect_enabled`, default on) under Settings Personalization and registered it in `SettingsCatalog`.
- **AI Assistant Side History Drawer & Tab-Switch Behavior Setting (#215, #216)**:
  - Replaced the old `ModalBottomSheet` with a custom `AnchoredDraggableState` left drawer inside `ChatScreen` (swipe right to open, swipe left to close, `BackHandler` priority close, 40% scrim, and smooth `LazyColumn` scrolling).
  - Added "Resume last conversation when switching to AI Assistant" toggle (`ai_chat_tab_behavior`, default starts new chat) under Settings "AI Assistant & Analysis" and registered it in `SettingsCatalog`.
- **Floating Note Rules Panel & Semantic Keyword Matching (#217)**:
  - Removed the inline rules section from `NotesTab`, replacing it with a top-right `Tune` button (48dp + rule count badge) opening a floating glass rules panel (retaining manual add, natural-language parse confirmation card, toggle, and delete).
  - Upgraded `AiClient.buildSystemPrompt` to semantic category matching (`riskCache` key bumped `memoV2` → `memoV3`), while `MemoRuleEngine` keeps literal `contains` as offline/no-AI fallback.
- **Stats Leaderboard, Stats Settings Category, Card Customization & 4 New Charts (#218)**:
  - Added "Today's Top 5 App Message Volume Leaderboard" card (`todayCountsByApp` + relative bars) and a dedicated "Stats" category card in Settings after Personalization (12 individual card visibility toggles persisted in `PhantomPrefs` `StringSet` and registered in `SettingsCatalog`).
  - Added 4 pure Compose `Canvas` charts: "7-Day Risk Trend Line", "Unread/Read Ratio Donut", "Per-App Share Bars Top 8", and "Todo Completion Rate Donut" (Room stays at v15 with zero migrations, adding only read-only `@Query` methods).
- **Notes Home-Screen Widget (#219)**:
  - Added `NotesWidgetProvider` (2x2, resizable to 4x2, `goAsync()`, `PendingIntent.FLAG_IMMUTABLE`, `updateAllIfChanged` diff updates), showing total count and latest 5 notes (pinned first, 36-char truncation + relative time), tapping opens Notes tab; synced across all `noteDao()` add/edit/delete/pin and auto-memo write paths.

**Changed & Fixed**
- **Fixed Favorites Row Badges & Unread Sort Order (#213)**:
  - Added `unreadCountsByApp()` (excluding `isOwn`) in `MessageDao`; `AppGroupUi` and Favorites badges now use true unread counts (hidden at 0, `99+` above 99), fixing `HomeSortOrder.UNREAD` sorting.
- **Fixed AI Assistant Input Bar Keyboard Overlap (#214)**:
  - Applied `consumeWindowInsets(padding).imePadding()` at a single level in `MainTab.ASSISTANT` (preventing double lift) and smoothly scrolled to the latest message when keyboard opens at list bottom.
- **Release Preparation & Dependency Version Audit (#220)**:
  - Bumped version to `versionCode` 26, `versionName` `"2.8.0"`; audited all dependencies to latest stable versions and synced `CHANGELOG.md` and `README.md`.

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

### English
- **Tag Compare**: [`v2.6.0...v2.7.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.6.0...v2.7.0) (`versionCode = 25`, `versionName = "2.7.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Auto-Note / To-Do Rule Engine & Management UI (#203)**:
  - Upgraded Room to v15 (single non-destructive `MIGRATION_14_15`), adding `memo_rules` table and `MemoRuleDao` supporting "Always Note (`note`)", "Always Todo (`todo`)", and "Never Auto-Add (`never`)" (`never > todo > note` priority).
  - Added pure-function `MemoRuleEngine` running pre-check in `NotificationCollectorService.saveAutoMemoIfPresent` (covering live notifications and `PendingAiAnalysisWorker` offline analysis, 30s TTL cache invalidated on write/toggle/delete), injecting rules into `AiClient.buildSystemPrompt` and `memoRuleSig` into `riskCache` key.
  - Extended `ChatScreen` `add_rule` with `kind="memo"` (confirmation card to DB); added "Auto-Note Rules" management card in Notes tab (list, toggle, delete, manual add dialog, and natural-language `AiClient.parseMemoRule` + confirmation card).
- **Noted/Todoed Home Filters, Todo Source Backtracking & 3-Scope Exclusion (#204)**:
  - Added `Noted` and `Todoed` filters to `MessageFilter`, `ALL_OPTIONAL_HOME_FILTERS`, and Settings custom filter list; added 12dp noted/todoed icons on `MessageCard`.
  - Added "View Source Message" button (jumps to `MessageDetailActivity`) on Notes tab todo rows and search result todo cards; added "Never Auto-Note/Todo" entry on `MessageDetailActivity` and auto-note long press with a 3-scope dialog (sender / app / keyword writing `memo_rules` `never` rules).
- **Right-Aligned Self-Reply Bubbles & Persistence (#207)**:
  - Added `isOwn` column to `messages` (default `0` in `MIGRATION_14_15`); writes `isOwn = true`, `sender = "Me"` records after successful replies in `ConversationScreen` (notification quick reply & SMS fallback), `ChatScreen` `reply_message`, and `ReplyProxyReceiver`.
  - `ChatBubbleItem` renders `isOwn` as right-aligned 78%-width tinted bubbles (`primary` 0.16 alpha, hiding risk dot, AI tags, and OTP extraction); excluded `isOwn = 0` across all stats counts, unread counts, risk distribution, ScamCenter, daily/weekly summary excerpts, and dedup queries.
- **Onboarding Tour Expanded to 18 Steps (#209)**:
  - Expanded Home tour from 15 to 18 steps (`KEY_ONBOARDING_STEP_V2` `coerceIn(1, 18)`), updated `SEARCH_PILL` to "Pull-Down Search" (anchored to top handle), added "Quick Reply", "Auto-Note Rules", and "Noted/Todoed & Source Backtracking" steps, and updated `FILTER_CHIPS` copy for fixed headers; existing completed users are not re-triggered.

**Changed & Fixed**
- **Fixed Home Header & Independently Scrolling Message List (#206)**:
  - Refactored `MessagesHomeTab` to an outer `Column` fixed header (offline banner → stats card → category filter row → favorites row) with `LazyColumn(weight(1f))` independently scrolling message list, preserving `listState` and pagination.
- **Top Pull-Down Search Handle & Stats Card Toolbar Integration (#205)**:
  - Removed top search pill and toolbar from Home; moved refresh/settings buttons into the right column of the stats card (48dp touch targets); added `PhantomPullSearchHandle` (22dp full-width, centered 4dp×120dp pill supporting `anchoredDraggable` pull-down and tap to open full-screen search, auto-hidden on AI Assistant tab).
- **Fixed Conversation Page IME Over-Lifting (#207)**:
  - Added `android:windowSoftInputMode="adjustResize"` for `MainActivity` and `ConversationActivity` in `AndroidManifest.xml`, fixing double lift from `adjustPan` + `.imePadding()`, smoothly scrolling to latest message only when at list bottom.
- **Fixed Search Page & Codebase Text Contrast (#208)**:
  - Replaced residual theme-blind `Color.Gray`/`Color.Red` in `MessageCard`, `MessageDetailActivity`, `ScamCenterActivity`, and `SettingsActivity` with `MaterialTheme.colorScheme.onSurfaceVariant`/`error`; explicitly specified `onSurface` for `ChatBubbleItem` and `MessageCard` body text.
  - Set `PhantomFullScreenSearchOverlay` background to opaque theme color (`alpha = 1.0`); raised `CardBgMode.TRANSPARENT` slider floor to 15% (auto-clamping stored values) and glass-off/battery-saver fallback floor to 0.35.
- **Global Health Check, OEM Compatibility Audit & Release Preparation (#210)**:
  - Added `windowSoftInputMode="adjustResize"` to all text-input Activities (`SettingsActivity`, `AutoReplySettingsActivity`, `ApiKeySettingsActivity`, `AppThreadsActivity`, `MessageDetailActivity`, `TagManagementActivity`, `WhitelistManagementActivity`); audited accessibility insets, cutouts, Room integrity, and `NotifIds` mutual exclusion.
  - Bumped version to `versionCode` 25, `versionName` `"2.7.0"`.

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

### English
- **Tag Compare**: [`v2.5.0...v2.6.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.5.0...v2.6.0) (`versionCode = 24`, `versionName = "2.6.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Bottom Quick-Reply Input Bar on Conversation Detail Page (#197)**:
  - Added a persistent reply input box at the bottom of `ConversationScreen` (`OutlinedTextField` + send button, with `navigationBarsPadding().imePadding()`, auto-scroll to latest message on keyboard open, auto-hidden in multi-select mode).
  - Added optional `conversationKey` parameter to `NotificationCollectorService.sendReplyToNotification`, prioritizing `MessagingStyle` `EXTRA_CONVERSATION_TITLE` (then `EXTRA_TITLE`, then same-package fallback); calls `ReplyProxyManager.startOrRefreshSession` on success to keep continuous notification-shade reply; SMS apps fall back to `SmsManager`, all protected by `try/catch` + `PhantomReply` logcat.
- **Dedicated Whitelist Management Page & Collapse Persistence (#199)**:
  - Added `WhitelistManagementActivity` (`android:exported="false"`, using `PhantomCheckTheme` + `PhantomBackgroundBox` and top-bar back button), moving grouped whitelist UI to a standalone page; Settings data section simplified to a "Whitelist Management (N)" button.
  - Added `whitelist_collapsed_groups` `StringSet` in `PhantomPrefs` to persist per-app group collapse states across navigation and restarts (default all expanded on first run); `SettingsCatalog` `whitelist_management` points to the new Activity.

**Changed & Fixed**
- **Global Dependencies, Build Toolchain & Target SDK Upgrade (#200)**:
  - Upgraded Gradle Wrapper to `9.9.1`, AGP to `9.5.1` (using AGP 9 built-in Kotlin, removing `org.jetbrains.kotlin.android` and `kotlinOptions`), Kotlin Compose plugin to `2.4.21`, `compileSdk` to `37` (required by `core-ktx 1.19.1` / `Compose BOM 2026.09.00` / `lifecycle 2.11.0`), and `targetSdk` to `36` (Android 16).
  - Adopted Jetpack Compose BOM `2026.09.00` (Material 3 `1.4.0`), upgraded `core-ktx` `1.19.1`, `activity-compose` `1.13.0`, `lifecycle-runtime-ktx` `2.11.0`, `work-runtime-ktx` `2.12.0`, `Retrofit` `3.0.0`, and `Room` `2.8.5` (migrated `kapt` → `KSP 2.3.14`).
  - Upgraded `Haze` to `2.1.0` (`haze` + `haze-blur`), rewriting `Glass.kt` to `Modifier.hazeBlur` + `HazeInput.Sources` + custom `HazeBlurStyle` (replacing discontinued `haze-materials`), preserving all three card surface modes, battery-saver degradation, and API < S fallback.
  - Added Rule 6 to `AGENTS.md` release checklist: dependencies must be checked and upgraded to latest stable versions on every release.
- **Fixed Full-Screen Search Overlay Z-Order (#193)**:
  - Moved `PhantomFullScreenSearchOverlay` in `ConversationScreen` and `AppThreadsScreen` outside `Scaffold` into an outer `Box` wrapping the entire `Scaffold` (matching `MainActivity`), fixing top-bar overlap over the search overlay.
- **Unified Mini Glass Search Pill & Toolbar Layout (#194)**:
  - Standardized `PhantomSearchBarPill` to a 36dp visual height pill (left magnifier + "Search" text, >=48dp outer accessibility touch target), moved `MainActivity` refresh and settings gear buttons below the search pill aligned right, unifying search entry visuals across all 4 pages.
- **Transparent Background for Swipe-Left Delete Row (#195)**:
  - Removed full-width opaque background from `SwipeToRevealRow` content layer; outer container is now fully transparent and controls right action button `alpha` by swipe progress so custom wallpapers remain visible behind message bubbles.
- **Crash-Proofed Reply Chain & AI Assistant Actions (#196)**:
  - Changed `ReplyProxyManager` reply `PendingIntent` to `FLAG_MUTABLE` (per Android `RemoteInput` spec; dismiss/timeout actions remain `FLAG_IMMUTABLE`), and wrapped `ensureChannel`/notification build/scheduling in `try/catch` + `PhantomReply` logcat.
  - Added `try/catch` to `ReplyProxyReceiver` broadcast and `goAsync()` coroutine; added `try/catch` + `PhantomChatAction` logcat to `ChatScreen` action coroutines and replaced `messages.first()` with `firstOrNull()`.
- **Todo Flow Real-Time Sync Audit (#198)**:
  - Added `observeAll(): Flow<List<TodoEntity>>` to `TodoDao`; `MainActivity` Notes tab and `ChatScreen` todo menu now observe via `collectAsState` + smart sort so widget delete/complete/add syncs immediately in-app; added `ON_RESUME` refresh for `remindAt` in `MessageDetailActivity`.
- **Version Bump**: `versionCode` 24, `versionName` `"2.6.0"`.

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

### English
- **Tag Compare**: [`v2.4.0...v2.5.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.4.0...v2.5.0) (`versionCode = 23`, `versionName = "2.5.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Continuous Quick Reply from Notification Shade (#183)**:
  - Added `ReplyProxyManager` and `ReplyProxyReceiver` (`android:exported="false"`), creating notification channel `phantom_reply_proxy` (`IMPORTANCE_DEFAULT`, "Quick Reply Conversations") and dedicated `NotifIds` range `120000..129999` (`120000 + Math.floorMod(sessionHash, 10000)`).
  - After `ChatScreen` `reply_message` succeeds, launches/updates a `MessagingStyle` proxy notification (preserving original messages + "Me" replies up to 8 messages; in-memory LRU up to 8 sessions) with "Reply" (`RemoteInput` resultKey `proxy_reply`) and "Dismiss" actions, swipe-dismiss (`deleteIntent`), and `AlarmManager` idle auto-dismiss (15 / 30 / 60 min or never, default 30 min).
  - When `ReplyProxyReceiver` fails to send (including SMS / `^09\d{8}$` `SmsManager` fallback), honestly appends an error message to the conversation thread instead of faking success; added `reply_proxy_enabled` toggle (default on) and timeout option in Settings "Messages & Notifications", registered in `SettingsCatalog` (`notif_reply_proxy`, `notif_reply_proxy_timeout`).
- **Pre-AI Duplicate Notification Folding (#185)**:
  - Added pure-function `NotificationFoldUtil` (without modifying DB or UI lists) folding high-frequency duplicate notifications sharing `packageName`, `sender`, `conversationKey`, `riskLabel`, 24h chained window, and exact `normalized` or Dice bigram >= 0.85 into `[AppLabel] Title (HH:mm–HH:mm repeated N times)` (`N = 1` keeps original format).
  - Safeguards: `scam`/`suspicious` high-risk messages are never folded; messages containing URLs or differing numeric sequences (order IDs / amounts / OTPs / phone numbers) are not folded. Applied to `ChatScreen` RAG context, `DailySummaryPolicy.buildSummaryExcerpts`, `WeeklySummaryPolicy` excerpts, and `MessageDetailActivity` `senderHistorySummary`.
- **Auto Update Check on App Launch (#186)**:
  - `MainActivity` `ON_RESUME` silently checks GitHub's latest release at most once per day (`update_last_check_day`, silent on offline/failure); when a new version is found, posts a once-per-version-per-day notification on channel `app_updates` (`IMPORTANCE_DEFAULT`, `NotifIds.UPDATE_AVAILABLE = 1007`) and shows a foreground `AlertDialog` ("Go to Download" → Settings About + "Later", with opt-out hint).
  - Added `update_auto_check_enabled` toggle (default on) in Settings About and registered `update_auto_check` in `SettingsCatalog`; manual check and download/install flows unchanged.
- **Smart Auto-Note / To-Do Routing (#188)**:
  - Extended `AiClient` single and batch analysis schemas with `memo_type` (`"note" | "todo"`, lenient fallback `"note"`) and `todo_due` (`"yyyy-MM-dd HH:mm"` or empty string), adding `memoV2` flag to `riskCache` key.
  - `NotificationCollectorService.saveAutoMemoIfPresent` routes by `memo_type`: `todo` creates `TodoEntity` (`priority = 1`, `dueAt` from `todo_due` or fallback `msg.timestamp + 2h`), deduplicates before insert via `TodoDao` `@Query` (Room stays at v14 without migration) on `relatedMessageId` and incomplete same-title within 7 days, and triggers `ReminderScheduler.scheduleTodo` and `ReminderWidgetProvider.updateAllIfChanged`.
  - Extracted `ClientTimeParser` (including `NaturalTimeParseResult`) shared by `ChatScreen` and auto-todo.

**Changed & Fixed**
- **Fixed Message Card Black Background Overflow on Conversation Page (#184)**:
  - Fixed `.clip(shape)` ordering in `ui/Components.kt` `PhantomGlassCard` glass branch to `.glassSurface(...)` followed by `.clip(shape)` before drawing `overlayColor`; aligned all branches of `phantomGlassSurface` (including `overlayColor == null` and API < S) to clip with a single `remember(cornerRadius)` `RoundedCornerShape` after `glassSurface` (`ui/components/` untouched).
- **Duplicate Auto-Note Deduplication (#187)**:
  - Added a 7-day window `@Query` in `NoteDao` (Room version unchanged); `saveAutoMemoIfPresent` checks same-`relatedAppLabel` auto-notes within 7 days (exact normalized or Dice bigram >= 0.9) and updates existing notes in place, strictly protecting `pinned` notes and leaving manual notes untouched.
- **Widget Notification Clear Sync & Enlarged Action Buttons (#189)**:
  - `ReminderScheduler.cancel`/`cancelTodo` now also calls `NotificationManager.cancel` for `NotifIds.reminder`/`NotifIds.todoReminder`, and `ReminderWidgetProvider` calls `ReminderScheduler.cancel` when deleting `TYPE_MSG` reminders; unchecking (`done = false`) unchanged.
  - Enlarged `widget_reminder.xml` action buttons to `16sp` bold + `paddingHorizontal="8dp"` + `paddingVertical="6dp"` + `minWidth="28dp"`/`minHeight="32dp"` with `?android:attr/selectableItemBackground`, and adjusted `reminder_widget_info.xml` (including `xml-v31`) `minHeight` to `72dp`.
- **Version Bump**: `versionCode` 23, `versionName` `"2.5.0"`.

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

### English
- **Tag Compare**: [`v2.3.1...v2.4.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.3.1...v2.4.0) (`versionCode = 22`, `versionName = "2.4.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **AI 5-in-1 Token Saver & Vision Model Fix (#174, #173)**:
  - `ChatRequest` supports `prompt_cache_key` (stable session key for chat, fixed key for analysis); analysis system prompts and tag/rule lists are deterministically sorted and placed byte-identically at the front; logs `usage.prompt_tokens_details.cached_tokens`.
  - Refactored `ChatScreen.buildRagSystemPrompt` into a "static prefix (system prompt + rules + full context) + dynamic tail (time, current query)" with RAG messages ordered oldest-to-newest for prefix cache hits; added incremental context summary cache (300–500 tokens, invalidated on day change / RAG limit change).
  - Added "Dual-Model Confidence Routing" (`ai_dual_routing_enabled` default on, `ai_cheap_model` default `mistral-small-2603`, in `SettingsCatalog`): calls cheap model first; adopts risk < 30 or > 70 directly, escalates 30–70 to primary model and takes higher risk (without double-deducting quota).
  - Output conciseness & vision token optimization: added concise-reply rules to Chat system prompt; downsampled image encoding and dynamically estimated `imageTokenOverhead` from actual dimensions; deduplicated high-risk excerpts in daily/weekly summaries, backfilled unanalyzed messages, and capped tokens.
  - Fixed `AiClient.isVisionModelSupported` (#173): switched `knownTextOnly` to an exact list so `mistral-small-2603` (Mistral Small 4 built-in vision) is no longer blocked.
- **Card Surface Mode Settings (#175)**:
  - Added `CardBgMode` (Follow Glass Strength / Transparent Mode 0–100% slider / Dark Surface) under Appearance Settings and registered `card_bg_mode` and `card_bg_transparent_alpha` in `SettingsCatalog`.
- **Comprehensive To-Do Enhancements (#176)**:
  - Upgraded `AppDatabase` to v14 (`MIGRATION_13_14` registered in `AppDatabaseHolder`); added `completedAt`, `priority` (Low/Medium/High), `notes`, and `repeatRule` (Daily/Weekly, auto-spawning next occurrence and rescheduling reminder on completion) to `TodoEntity`.
  - Notes tab now supports overdue color-coding with "Overdue X days" / "Due Today" badges, filters (All / Incomplete / Completed / Overdue), smart sorting (overdue > due date > priority, synced with widget), manual "+" add, and full `TodoEditorDialog` (AI-created todos are also editable).
- **Whitelist Management UI (#177)**:
  - Added a collapsible app-grouped "Whitelist Management" card in Settings (showing sender, app icon, mode, timestamp, duplicate-preventing manual add, enable toggle, swipe/button delete confirmation, and empty state), registered `whitelist_management` in `SettingsCatalog`, and unified all changes to call `invalidateRuleCaches()`.
- **Home-Screen 2x1 To-Do Widget Interactive Buttons (#179)**:
  - Added per-row "Done ✓" (todos only, writes `isDone` + `completedAt`), "Edit ✎" (jumps to Notes tab and opens editor dialog), and "Delete ✕" buttons on `ReminderWidgetProvider` using `PendingIntent.getBroadcast` (request code includes `itemId` to prevent multi-instance collisions) + `goAsync()` async writes and immediate refresh (snapshot key includes all item IDs).
- **Unified Pull-Down Glass Search Pill & Full-Screen Search (#180)**:
  - Implemented `PhantomSearchBarPill` and `PhantomFullScreenSearchOverlay` in `ui/Components.kt` (fade+slide animation, auto-focus, IME inset, live `LazyColumn` search, retained last query, AI semantic search quota entry, and >=48dp accessibility touch targets), replacing search bars across Messages, App Threads, Conversation, and Notes tabs.

**Changed & Fixed**
- **Fixed Card Black Background Overflow (#175)**:
  - Unified `phantomGlassSurface` and `PhantomGlassCard` in `ui/Components.kt` to share a single `RoundedCornerShape` instance for background and border clipping, and fixed double dark-background stacking on API < S fallback (`ui/components/` directory untouched).
- **Whitelist Injection & Comprehensive AI Feature Health Check (#177, #178)**:
  - Permanently injected structured whitelist into `ChatScreen.buildRagSystemPrompt` static prefix (grouped by app when > 20 entries), added `whitelist_delete` confirmation card action and DB read-back verification.
  - Audited all 7 `AiPurpose`s: `CHAT` supplemented with todos (overdue/completed), memos, VIP senders, muted apps, and reminder keyword context; `DEEP_ANALYSIS` supplemented with sender history and risk summary; unified all AI write actions (todos, whitelist, rules, tags) to "execute → read-back verify → honest success/failure report".
- **Onboarding & Settings Tour Fixes & Expansion (#181)**:
  - Removed chained auto-skipping of non-permission steps in `Onboarding.kt` (only permission step auto-skips once; missing targets prompt manual next), added dynamic top/bottom card avoidance around spotlight hole; expanded Home tour to 15 steps (including `SEARCH_PILL`) and Settings tour from 6 to 8 steps (including whitelist, card surface, per-purpose models, and token saver).
- **Version Bump**: `versionCode` 22, `versionName` `"2.4.0"`.

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

### English
- **Tag Compare**: [`v2.3.0...v2.3.1`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.3.0...v2.3.1) (`versionCode = 21`, `versionName = "2.3.1"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Changed & Fixed**
- **AI Tag Dispatch Parse Tolerance & Failure Notification Improvements (#169)**:
  - Strengthened fault tolerance in `AiClient.parseAnalysisJson` and `parseBatchAnalysisJson`: strips Markdown fences → string/escape-safe balanced-brace extraction (taking first object with `risk_score`/`risk`) → lenient preprocessing (full-width colon/comma outside strings, trailing commas) only after strict Gson fails, retaining regex fallback and `PhantomAiParse` logcat diagnostics (reason and length only, never message text).
  - Strengthened `buildSystemPrompt` ("output only one JSON object, no fences or extra text"); OpenAI-compatible endpoints supporting `json_object` (OpenAI/Mistral/Groq/DeepSeek/Gemini) attach `response_format` for single analysis; `analyzeMessageOrThrow` and `testAutoTagDispatch` auto-retry once with a corrective prompt on parse failure (without double-charging daily quota), and test screen shows first 200 chars of raw response on failure.
  - Changed `NotificationCollectorService` dispatch failure notifications to a 24-hour sliding window (5 failures trigger one `IMPORTANCE_LOW` + `setOnlyAlertOnce` notification with 6h cooldown, single success does not reset count, respects DND); added `ai_tag_failure_notif_enabled` toggle (default on in `PhantomPrefs`, toggleable in Settings AI card, registered in `SettingsCatalog`).
- **Fixed Background Page Black Screen & Image Picker Reliability (#170)**:
  - Persisted `pickingLayer` (`layer.name`) and `bgStatusMsg` in `SettingsActivity` via `rememberSaveable` to survive rotation/process recreation during picking; shows explicit prompt instead of silently dropping when `uri` is non-null but layer is lost.
  - `PhantomBackgroundBox` explicitly draws `MaterialTheme.colorScheme.background` when `bgFile == null`, loading, or decode fails (no transparent black bleed); `BackgroundImageHelper.loadBackgroundBitmap` logs decode failures to `PhantomBackground` logcat.
  - Added `ActivityResultContracts.OpenDocument("image/*")` fallback button when Photo Picker fails (non-cancellation) or is unavailable, copying downsampled image into internal storage.
- **Onboarding Tour Expansion & Health Check (#171)**:
  - Expanded Home `OnboardingStep` from 10 to 14 steps (adding "Notes & Auto-Memo", "Stats, Summaries & Offline Queue", "Todo Reminders & Batch Management", "Backgrounds, Appearance & Per-Purpose Models"), updated `PhantomPrefs.KEY_ONBOARDING_STEP_V2` (`1..14`), `OnboardingTargets` (`NOTES_TAB`, `STATS_TAB`), and missing-target auto-skip guard; fixed Settings "Replay Onboarding" to reset step index to 1 without re-triggering completed users.
  - Proofread all 4 Tab ("Messages / AI Assistant / Notes / Stats") tour copy and updated the 6-step Settings tour (`AI`, `NOTIF`, `APPEARANCE`) for v2.3 features.
- **Version Bump**: `versionCode` 21, `versionName` `"2.3.1"`.

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

### English
- **Tag Compare**: [`v2.2.0...v2.3.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.2.0...v2.3.0) (`versionCode = 20`, `versionName = "2.3.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Per-Purpose AI Model Routing (#163)**:
  - Supports binding independent AI Profiles or "Follow Default" for 6 AI purposes (`ai_chat`, `ai_message_analysis`, `ai_daily_summary`, `ai_weekly_summary`, `ai_deep_analysis`, `ai_settings_search`; auto-memo follows per-message risk analysis model); `ApiKeySettingsActivity` added a Purpose Models card (6 dropdown rows with per-row connection test).
  - `ChatScreen` model switcher now switches only `ai_chat` without altering the global default Provider; added 6 entries (`ai_model_chat`, etc.) to `SettingsCatalog`.
- **Offline AI Retry Queue & Auto-Retry (#164)**:
  - Upgraded `AppDatabase` to v13 (`MIGRATION_12_13`) adding `pending_ai_analysis` table; enqueues only transient failures (`UnknownHostException`, `SocketTimeoutException`, `ConnectException`, HTTP 5xx, exhausted 429), honestly skipping 401/403/404/413.
  - Added WorkManager `PendingAiAnalysisWorker` (`NetworkType.CONNECTED`, exponential backoff, unique work `ai_pending_analysis_worker`), retrying up to 20 messages per round and abandoning at `attempts >= 3` with notification; added low-intrusion notification (`NotifIds.AI_OFFLINE_QUEUE`), `AnimatedVisibility` queue banner at top of Messages tab (Pause / Resume / Retry Now), and `ai_offline_retry_enabled` toggle in Settings (default on; disabling clears queue).
- **"Notes" Tab & AI Auto-Memo (#166)**:
  - Replaced "Scam Alerts" main tab with "Notes" (keeping 4 Tabs), integrating todos (shared `todos` table) and manual/auto note CRUD, pinning, and color tags (`notes` table included in `MIGRATION_12_13`); Scam Alert Center moved to entry points in Messages risk card and Stats Top 5 card.
  - Added `ai_auto_memo_enabled` toggle (default off) in Settings; when enabled, `AiClient` generates a <=40-char summary during risk analysis and writes deduplicated auto-memos by `relatedMessageId` (`riskCache` key includes `autoMemo` flag).
- **Customizable AI Analysis Length Threshold (#167)**:
  - `PhantomPrefs.getAiMinTriggerLength` (1–30 chars, default 10) replaces hardcoded threshold in `NotificationCollectorService`; added slider in Settings AI card and registered `ai_min_trigger_length` in `SettingsCatalog` (adjustable via AI chat).

**Changed & Fixed**
- **Dual Fix for Image Vision Upload & Background Image Picker (#162)**:
  - `ChatScreen.encodeImageUriToDataUri` switched to bounds-first `inSampleSize` downsampling (longest edge <= 1024px, retrying once with doubled `inSampleSize` on OOM), detailed honest error reporting, and `PhantomImage` logging; updated `AiClient.isVisionModelSupported` (added Groq `qwen/qwen3.8-27b` and moved `deepseek` to text-only list).
  - Fixed `BackgroundImageHelper.saveBackgroundFromUri` always failing due to `inJustDecodeBounds = true` returning `null` in `use { ... } ?: return failure`, restoring all three background layers (Global / AI Chat / Conversation).
- **Moved "Clear Read" Entry to Settings (#165)**:
  - Removed "Clear Read" button and confirmation dialog from top-right of `MainActivity` Messages tab, moved to Settings "Messages & Notifications" card, and added `notif_clear_read` in `SettingsCatalog` alongside updated `data_export_clear` description.
- **Version Bump**: `versionCode` 20, `versionName` `"2.3.0"`.

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

### English
- **Tag Compare**: [`v2.1.0...v2.2.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.1.0...v2.2.0) (`versionCode = 19`, `versionName = "2.2.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **About Card Update Checker & GitHub APK Download (#157)**:
  - Updated About card author to "Author: Hwanshen (幻神)" and added manual "Check for Updates" button (`GET https://api.github.com/repos/eric11yothelast-Hwanshen/PhantomCheck/releases/latest`, `User-Agent: PhantomCheck-Android`, 10s timeout, semantic comparison with runtime `versionName`; honestly displays errors with "Open GitHub" fallback on offline/timeout/API failure).
  - When a new release is detected, supports downloading `app-release-X.Y.Z.apk` via `DownloadManager` to app-specific `Download/`, launching system installer via `FileProvider` (`updates` `external-files-path` in `file_paths.xml`) + `ACTION_VIEW` (`application/vnd.android.package-archive` + `FLAG_GRANT_READ_URI_PERMISSION`); declared `REQUEST_INSTALL_PACKAGES` in `AndroidManifest.xml`, checks `canRequestPackageInstalls()` and guides to `ACTION_MANAGE_UNKNOWN_APP_SOURCES`; expanded `SettingsCatalog` `about_section` aliases.
- **3-Layer Custom Background Images & Visual Fine-Tuning Sliders (#159)**:
  - Added 3-layer "Background Image" customization in Personalization (Global / AI Chat / Conversation, falling back to Global when unset) using `PickVisualMedia`, power-of-2 `inSampleSize` downsampling to <= screen resolution, and JPEG 85 compression to `filesDir/backgrounds/{global|chat|conversation}.jpg`; `BackgroundImageHelper` uses a single `LruCache(1)` released in `PhantomApplication.onTrimMemory`.
  - Added page-level `PhantomBackgroundBox` wrapper in `ui/Components.kt` (`ui/components/` untouched) rendering `Image(ContentScale.Crop)` + dark readability scrim (default 35% day / 60% night, 0–100% slider), pixel-identical to v2.1.0 when unset; added "Glass Card Strength" slider (0–100%, default 0%, Android < 12 fallback unchanged) and registered `bg_global`, `bg_chat`, `bg_conversation`, `bg_scrim`, `glass_strength` in `SettingsCatalog`.
- **AI Assistant Action Capabilities Expansion (#160)**:
  - Added 9 actions on the existing `parseAllChatActions` / `KNOWN_CHAT_ACTIONS` / confirmation-card queue / `sanitizeForPrompt` framework: read-only `search_messages` (`MessageDao.searchFiltered` defaulting to last 2 days with up to 5 clickable cards opening `MessageDetailActivity`) and `todo_list` (Markdown table); plus confirmation-gated mutating actions `todo_done`, `todo_delete`, `todo_edit` (with fuzzy matching, honest 0/multi-candidate reporting, `ReminderScheduler` reschedule, and widget refresh), `vip_pin` (`PhantomPrefs.toggleVipSender`), `mute_app` (mute list add/remove with candidate hints), `mark_read` (`today`/`app`/`all`, strictly excluding `riskLabel = 'scam'` and refreshing `StatsForegroundService`), and `export_messages` (reusing `MessageExporter.exportToDownloads` CSV export with formula-injection protection).

**Changed & Fixed**
- **Global Codebase Health Check & Performance Fixes (#158)**:
  - Fixed `ChatScreen` `pendingTodoQueue` calling `firstOrNull()` before filtering by `sessionId` (which caused cross-session todo confirmation cards to get stuck); now filters `currentSessionId` first and removes processed items by object reference.
  - Added 30s TTL cache `cachedActiveUserTagRules` in `NotificationCollectorService` cleared by `invalidateRuleCaches()`, and ensured `ChatScreen`, `TagManagementActivity`, `MessageDetailActivity`, `ScamCenterActivity`, and `RiskActionWidgetProvider` call `invalidateRuleCaches()` after writing/toggling/deleting `ai_rules` or `user_tag_rules`.
  - `ChatScreen` `tag_rule` now honestly skips and logs `Log.w` when target tag does not exist instead of falling back to `tags.firstOrNull()`.
  - Pre-compiled hot-path `Regex` constants in `TagRuleEngine` and `NotificationCollectorService`; verified `ReminderWidgetProvider` pure-todo click fallback, `HeartbeatPolicy` `UPDATE` policy, `PhantomPrefs` throttle cache, 7-day SQL aggregation, `AppIconHelper` shared cache, and `BootReceiver` same-ID overwrite.
- **Version Bump**: `versionCode` 19, `versionName` `"2.2.0"`.

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

### English
- **Tag Compare**: [`v2.0.0...v2.1.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v2.0.0...v2.1.0) (`versionCode = 18`, `versionName = "2.1.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **AI Natural-Language To-Do Reminders + Home-Screen Widget Integration (#143)**: Upgraded `AppDatabase` to v12 (`MIGRATION_11_12`) adding `todos` table; AI Assistant supports `reminder_add` with deterministic client-side time-parsing fallback, todo confirmation cards, and list Sheet, scheduled via `AlarmManager.setAndAllowWhileIdle` (mutually exclusive `NotifIds` range `110000..119999`), rescheduled on boot by `BootReceiver`, and synced with `ReminderWidgetProvider`.
- **Manual Multi-Select Batch Management on Conversation & Thread Lists (#144)**: Added multi-select mode (up to 100 items) on `ConversationScreen` and `AppThreadsScreen` supporting batch mark-read / star / delete (disabling `SwipeToRevealRow` and single-item tap during selection); batch delete strictly excludes `riskLabel = 'scam'` and cleans up `message_tags` cross-refs.
- **AI Assistant Default New Chat + One-Tap Clear All Conversations (#145)**: Opening AI Assistant tab defaults to a new chat (instead of auto-loading the last session); history drawer added "Clear All Conversations" with double confirmation (`ChatDao.clearAllSessions()`, blocked while AI is replying).
- **Settings "About" Section (#141)**: Added an "About" card at the bottom of "All Settings" displaying `versionName (versionCode)` via runtime `PackageManager.getPackageInfo`, author, GitHub repository link, third-party components, and privacy notice, registered as `about_section` in `SettingsCatalog`.
- **Quick Commands "Follow Default" Mode & Dynamic Resolution (#146)**: Added `MODE_FOLLOW` (3 built-in default-following commands), `resolveQuickCommandMode` dynamic resolution, and 3-state toggle / live refresh in Settings.

**Changed & Fixed**
- **Fixed LINE Group Conversation Splitting (#140)**: Upgraded `AppDatabase` to v11 (`MIGRATION_10_11`) adding `conversationKey`, parsing `MessagingStyle` (`EXTRA_CONVERSATION_TITLE`, sender `Person`) to separate group and DM threads while keeping fallback behavior for non-`MessagingStyle` notifications and migrating pinned `vipSenders`.
- **Dedicated `SnackbarHost` for AI Assistant Tab (#142)**: `MainActivity` now provides an inline, tab-scoped `SnackbarHost` for AI Assistant (displayed at top of tab, detached from Scaffold bottom overlay) so delete-session and other prompts never cover the bottom input box.
- **Auto-Extract CHANGELOG Sections for Release Notes (#112)**: `.github/workflows/release.yml` automatically extracts the corresponding `CHANGELOG.md` section before release publishing alongside GitHub's auto PR list (first active on this tag).
- **Version Bump**: `versionCode` 18, `versionName` `"2.1.0"`.

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

### English
- **Tag Compare**: [`v1.12.0...v2.0.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.12.0...v2.0.0) (`versionCode = 17`, `versionName = "2.0.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **AI Tag Memory Rules & Priority Order (#131)**:
  - Upgraded `AppDatabase` to v10 (non-destructive `MIGRATION_9_10`), adding `user_tag_rules` table and `MessageTagCrossRef.SOURCE_USER_RULE = "user_rule"`; built deterministic zero-token `TagRuleEngine` (supporting app package/label, sender, keyword, regex, and semantic categories `otp`/`system`/`bill`/`marketing`/`delivery`).
  - Tag application strictly follows `manual > user_rule > ai_rules > auto > App category`, and `processBatchAnalysis` never overwrites manual or user-rule tags; supports creating rules and querying hit chains via AI chat `tag_rule` confirmation cards, plus rule management and testing in `TagManagementActivity`.
- **AI Tag Failure Fallback Notification & Dispatch Test (#132)**:
  - Auto-tagging automatically falls back to `app_category_rules` on network/HTTP failures without interrupting message ingestion or risk flow; added `NotifIds.AI_TAG_FAILURE` (first failure silent, low-priority notification from 2nd consecutive failure with 6h cooldown).
  - Added "AI Tag Dispatch Test" button (counts toward daily quota and shows remaining quota) in Settings AI card and `ai_tag_dispatch_test` in `SettingsCatalog`.
- **AI Assistant Dual-Function Input Menu: Quick Commands + Image Vision (#133)**:
  - Removed top suggested-question row and added a single 48dp `+` collapsible menu left of the input bar ("Quick Commands" supporting direct-send / prefill modes, manageable in Settings or via chat `quick_command_add`; "Upload Image for AI Vision" using `PickVisualMedia`, downsampling to 1024px max edge, sending in OpenAI-compatible `image_url` format, and honestly blocking non-vision models with token warnings).
- **AI Assistant Layout Simplification (#134)**:
  - Smoothly hides the top "PhantomCheck" title bar with `AnimatedVisibility` when switching to AI Assistant, moving search/refresh/settings next to the model switcher; reduced chat input box (`maxLines = 3`) and streamlined send button size.
- **Customizable Bottom Tab Bar Display Mode (#135)**:
  - Added 3-way mode in Appearance Settings and `SettingsCatalog`: "Standard" (default), "Compact" (80% height and icons), and "Auto-Hide" (hides on scroll down, reveals on scroll up, preserving `LiquidGlassTabBar` drag gestures).
- **Three Non-Stats Home-Screen Widgets (#136)**:
  - Added `AiSummaryWidgetProvider` (2x2 latest AI summary), `RiskActionWidgetProvider` (2x2 one-tap mark scam / whitelist via `goAsync`), and `ReminderWidgetProvider` (2x1 todo reminders), all using standard `RemoteViews`, `updatePeriodMillis="0"`, diff updates, and base + `xml-v31` `previewLayout`.

**Changed & Fixed**
- **Fixed AI Numeric Setting Chat Execution Chain & Lifecycle (#127)**: Added colloquial aliases for `ai_chat_rag_limit`, `ai_daily_limit`, and `notif_throttle` plus client-side deterministic `resolveNumericIntentFallback`; fixed `appliedSettingCards` merge/append lifecycle.
- **Bare JSON Action Parsing & Whitelist Fuzzy Lookup (#128)**: Strengthened tokenized fuzzy search fallback for `whitelist` `messageRef`.
- **Groq Free-Tier TPM Budget Control & 413/429 Auto-Fallback (#129)**: Added token estimation, per-provider budget caps (Groq 6500 / others 32000), Groq default `maxTokens=1800`, multi-stage 413 fallback (RAG → history → maxTokens), 429 `Retry-After` backoff retries, and daily quota refund on analysis failure.
- **System Prompt Throttling & Conversation History Compression (#130)**: Settings list loaded only on settings-related queries; RAG deduplicates same-sender similar prefixes `(×N)` with dynamic char caps; histories > 10 turns compress into `[Earlier Conversation Summary]` + latest 8 messages.
- **Performance & M3 Layout Standardization (#137, #138)**: Added 30s TTL cache for `aiRuleDao().active()` in `NotificationCollectorService`, 30s thread-safe TTL cache for per-key throttle/mute lookups in `PhantomPrefs`, unified `AppIconHelper` to 4 MB byte-capped `LruCache`, released static icon bar references when `StatsForegroundService` stops, added Compose `LazyColumn` `contentType` / precomputed date dividers, and aligned M3 12dp card spacing and 48dp minimum touch targets.
- **Version Bump**: `versionCode` 17, `versionName` `"2.0.0"`.

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

### English
- **Tag Compare**: [`v1.13.0...v1.13.1`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.13.0...v1.13.1) (`versionCode = 16`, `versionName = "1.13.1"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Changed & Fixed**
- **HTTP 413 Payload Too Large Protection & Auto-Retry (#122)**:
  - Updated `AiClient` `HttpException` branches to report honestly by status code (`413` dedicated message with provider's raw error, plus distinct messages for `401/403`, `404`, `422`, `429`, `5xx`, no longer misleadingly saying "check API Key or model settings" on non-auth errors).
  - `chatAssistant` halves its per-turn message limit (min `25`) and retries once on `HTTP 413` while honestly noting the reduced scope; `MessageDetailActivity` deep analysis refunds today's quota via `PhantomPrefs.refundTodayAiUsage()` on `413` failure.
- **AI Chat Multi-Action Parsing & Honest Error Reporting (#123)**:
  - Updated `ChatScreen` 5-type action parser to support multiple actions via balanced-brace splitting (`splitBalancedJsonObjects`), allowing multiple independent action commands/objects in one reply; multiple `whitelist` proposals merge into a single checkable confirmation card reporting each item.
  - Invalid actions (empty `messageId`, missing `messageRef`, malformed JSON, or unsupported action) always insert an honest error message instead of being silently dropped; when `cleanReply` is empty with no valid proposals, raw JSON is no longer shown as fallback.
- **In-Chat Numeric Setting Slider Confirmation Cards (#124)**:
  - Added `NumericSettingSpec` and `NUMERIC_SPECS` in `SettingsCatalog` (`ai_chat_rag_limit` 50–800 step 10, `ai_daily_limit` 0–100 step 10, `notif_throttle` 0–300 step 10), aligning Settings `ai_chat_rag_limit` slider to step 10.
  - Added `setting_adjust` action and slider confirmation card in `ChatScreen` (Slider + `+/-` fine-tuning, out-of-range clamping notice, before→after status card), appending `[Adjustable Numeric Settings]` to the system prompt.
- **Settings Navigation Accuracy & Anti-Hallucination Full Catalog (#125)**:
  - Added `{"action":"open_setting","settingId":"..."}` to open `SettingsActivity` and scroll to the target card; system prompt includes `[Full Settings Catalog Summary]` and forbids inventing menu paths.
  - Unknown `settingId` fallback matches top 5 via `SettingsCatalog.keywordSearch` and honestly reports if none match; `setting_candidates` shows "Go" instead of "On/Off" for non-toggle items; standardized `SettingsCatalog` wording to "AI Risk Analysis".
- **Version Bump**: `versionCode` 16, `versionName` `"1.13.1"`.

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

### English
- **Tag Compare**: [`v1.12.1...v1.13.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.12.1...v1.13.0) (`versionCode = 15`, `versionName = "1.13.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added & Enhanced**
- **PhantomMarkdownText Syntax Completion (#114)**:
  - Added support for GFM tables (including alignment rows `:-` / `:-:` / `-:`, content-proportional column widths, and horizontal scrolling for wide tables), italics (`*text*` / `_text_`), strikethrough (`~~text~~`), clickable hyperlinks (`[text](url)` with safe `LinkAnnotation.Url` opening), blockquotes (`> quote`), horizontal rules (`---`), task lists (`- [ ]` / `- [x]`), and one-level nested list indentation.
  - `stripMarkdown()` strips all new syntax (tables converted to `cell / cell`); `buildRagSystemPrompt` encourages tables and rich formatting.
- **AI Default Model Presets Update (#115)**:
  - Updated `AiClient.DEFAULT_PROVIDER_PRESETS` (existing user profiles unaffected; applies to new setups only):
    - **Mistral**: `mistral-small-2603` (default), `mistral-large-4` (previously `mistral-small-latest` / `mistral-large-latest`)
    - **OpenAI**: `gpt-6.1-terra` (default), `gpt-6.1-sol` (previously `gpt-5.2`)
    - **Groq**: `openai/gpt-oss-120b` (default), `openai/gpt-oss-20b`, `qwen/qwen3.8-27b` (previously `qwen/qwen3.6-27b`, etc.)
    - **Gemini**: `gemini-4-hydrogen` (default), `gemini-4-carbon`, `gemini-4-argon`, `gemini-3.8-flash` (previously `gemini-3.8-flash` / `gemini-3.5-flash-lite`)
    - **DeepSeek / OpenRouter**: Unchanged.
- **Customizable AI Assistant Per-Turn Message Limit (#116)**:
  - Added `chatRagMessageLimit` in `PhantomPrefs` (default `400`, range `50–800`); when token-saver mode is enabled, `getEffectiveChatRagMessageLimit()` automatically lowers it to `100` (without overwriting the user's configured value).
  - Added a slider (step 50) with token-usage notice in Settings AI card and registered `ai_chat_rag_limit` in settings search.
- **Structured Whitelist Mechanism (#117)**:
  - Upgraded `AppDatabase` to v9 (non-destructive `MIGRATION_8_9` registered in `AppDatabaseHolder`); added nullable `sender` and `packageName` to `AiRuleEntity`.
  - Added two-option "Add to Whitelist" flow (all messages from this sender / this message only) in `ScamCenterActivity` and `MessageDetailActivity`; `TagManagementActivity` displays whitelisted sender/app metadata.
  - AI Assistant supports `{"action":"whitelist",...}`, rendering a confirmation card before writing to DB; whitelist hits automatically mark safe (`riskScore = 10`, `riskLabel = "safe"`) and skip subsequent AI analysis, scam alerts, and threat-intel pre-checks.
- **Direct Settings Adjustment via AI Chat (#118)**:
  - Added shared single-source `SettingsCatalog` (shared between `SettingsActivity` search and `ChatScreen` chat adjustments).
  - AI Assistant supports `setting_toggle` (restricted to whitelisted boolean toggles, applying the exact same setters and foreground/schedule side effects as Settings, showing "Before → After" and "Go to Settings" cards) and `setting_candidates` (listing candidate toggles for fuzzy descriptions; guiding non-boolean settings to Settings).
- **AI Message Lookup Enhancements & In-Chat Actions (#119)**:
  - Changed default RAG time range to "Today + Yesterday", added "Day Before Yesterday" and sender-name filtering (`MessageDao.recentSenders()`), with `[id=...]` in excerpts.
  - Added `reply_message` (matching `NotificationCollectorService` `activeNotifications` for `RemoteInput` quick reply, honestly reporting when unavailable), `open_app`, `delete_message` (strictly protecting `scam` records from deletion), and `tag_message` (`SOURCE_MANUAL`), all gated by confirmation cards and reporting results in chat.
- **Comprehensive Settings Search Audit (#120)**:
  - Audited `SettingsActivity` UI against `SettingsCatalog.ITEMS` / `TOGGLEABLE_IDS` card by card, fixed `ai_daily_summary` placement to `quick` card, added `ai_daily_summary_notif` and `appearance_glass_effect` search & boolean toggle support, and verified both local keyword and AI semantic search paths.
- **Version Bump**: `versionCode` 15, `versionName` `"1.13.0"`.

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

### English
- **Tag Compare**: [`v1.12.0...v1.12.1`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.12.0...v1.12.1) (`versionCode = 14`, `versionName = "1.12.1"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Changed & Fixed**
- **Fixed Five Misrouted Cards in Settings Search Catalog (#109, PR #110)**:
  - Renamed `notif_auto_reply` to `ai_auto_reply` (`cardKey = "ai"`, title "SMS Auto-Reply Settings", with `sms`/`reply`/`schedule` aliases), fixing "SMS Auto-Reply" routing to the Notifications card.
  - Moved `ai_auto_tag` to `cardKey = "quick"`, matching the Quick Settings "Per-Message AI Smart Scan" toggle.
  - Moved `ai_rules` to `cardKey = "data"`, title "Custom Tag Management".
  - Split `notif_scam_alert` into `quick_scam_popup` (`cardKey = "quick"`, "Scam Alert Pop-Up") and `ai_alert_keywords` (`cardKey = "ai"`, "Instant Alert Keywords").
  - Moved `appearance_widget_tour` to `cardKey = "data"`, title "Tour Replay & Home-Screen Widgets".
- **Added Two Settings Search Entries (#109, PR #110)**:
  - Added `ai_scam_center` (`cardKey = "ai"`, "Scam Alert Center") and `notif_filter_rules` (`cardKey = "notif"`, "Filter Keyword Rules & Hit Actions").
- **AI Navigation "Go" Direct Page Launch (#109, PR #110)**:
  - Added nullable `page` field to `SettingSearchItem` for `AutoReplySettingsActivity`, `ScamCenterActivity`, `TagManagementActivity`, and `ApiKeySettingsActivity` (including `ai_providers`).
  - `aiSearchNote` banner displays a "Go" `TextButton` when the first hit has `page`, opening the target settings screen directly.
- **Added Home-Screen Widget Instructions in Data Management Card (#109, PR #110)**:
  - Added hint text "Home-screen widgets: Long-press home screen → Widgets → PhantomCheck Stats" below "Replay Settings Tour" in the Data Management card.
- **Version Bump**: `versionCode` 14, `versionName` `"1.12.1"`.

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

### English
- **Tag Compare**: [`v1.11.0...v1.12.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.11.0...v1.12.0) (`versionCode = 13`, `versionName = "1.12.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Changed & Fixed**
- **Fixed Settings Search "Same-Key Throttle" Routing (#99)**:
  - Split `data_throttle_retention` in `SettingsActivity` `SETTINGS_SEARCH_CATALOG` into `notif_throttle` (`cardKey = "notif"`, with aliases for "same key", "duplicate notification", "missed message", "traffic", "digit micro-change", "override") and `data_retention` (`cardKey = "data"`, "Message Retention & Cleanup").
  - Updated Settings "Per-App Throttle Overrides" subtitle to clearly indicate `0s = never throttle` and that messaging notifications with updated `postTime` capture identical-content messages.
- **Retention Cleanup Preserves Scam Evidence & Tag Integrity (#100, No Room Migration)**:
  - `MessageDao.prune` and `deleteOlderThanCount` directly exclude scam evidence via `AND riskLabel != 'scam'`, removing `RetentionWorker` re-insert logic to preserve original `id`, `message_tags`, and `intel_results` relations.
  - Added `TagDao.deleteOrphanCrossRefs()` at the end of `RetentionWorker` transaction; added `LEFT JOIN messages` guard to `TagDao.allTagCounts()` matching `todayAiTagCounts()`.
- **Messaging Apps No Longer Drop Consecutive Identical-Content Messages (#101, Passive Listener Preserved)**:
  - `NotificationCollectorService` detects messaging notifications (`CATEGORY_MESSAGE` / `EXTRA_MESSAGES` / `MessagingStyle`) and only drops same-key + same-content + unchanged-`postTime` as system re-posts; identical-content messages with updated `postTime` are captured immediately. Non-messaging notifications (e.g., system/traffic monitors) retain `exactSame || digitMicroChange` throttling.
  - Restricted `dedupCache` blocking to `isSuspectedReplay` (reconnect replay), leaving normal paths to the throttle layer; per-app 0s override bypasses both layers.
- **Centralized Notification ID Management & Collision Prevention (#102)**:
  - Added `NotifIds` object managing mutually exclusive ranges: `1001..1004` (persistent stats / boot reminder / daily summary / weekly report), `scamAlert` `2000..21999`, `importantAlert` `22000..41999`, and `reminder` `100000..109999`, eliminating `ReminderReceiver` vs `alertScam` range overlap and magic numbers.
- **AI Prompt Injection Protection (#103)**:
  - Added `AiClient.sanitizeForPrompt()` converting `<` in isolation tags (`</message_*`, `</user_*`, `</system`, `</msg`) to full-width `＜` (without affecting raw `cacheKey` hashing), applied across `analyzeMessage`, `analyzeMessageBatch`, `deepAnalyzeMessage`, and `ChatScreen.buildRagSystemPrompt` (`chatAssistant` RAG).
- **SMS Auto-Reply Loop-Prevention Cooldown (#104)**:
  - Added `canReplyAndRecord()` in `SmsAutoReplyReceiver` (4-hour per-sender cooldown and 3/day cap stored in `auto_reply_cooldown_prefs`) plus reply-template self-echo matching to prevent infinite two-way auto-reply loops.
- **Technical Debt Cleanup (#105)**:
  - **Immediate Boot Trigger for Overdue Reminders**: Added `MessageDao.allScheduledReminders()` and `ReminderScheduler.deliverReminder()`, so `BootReceiver` immediately delivers reminders that expired while powered off and clears `remindAt`; `MessageDetailActivity` checks `remindAt > System.currentTimeMillis()`.
  - **Self-Healing Schedule Policy**: Changed `HeartbeatPolicy` and `WeeklySummaryPolicy` from `ExistingPeriodicWorkPolicy.KEEP` to `UPDATE`.
  - **Throttle Lookup Cache**: Added 30s TTL cache to `PhantomPrefs.getAppSameKeyThrottleMap` (invalidated in `setAppSameKeyThrottle`) to reduce repeated Gson deserialization on the notification hot path.
  - **Stats SQL Aggregation**: Added `MessageDao.countByDaySince` and `countByHourSince`, replacing full-row `dao.since()` loads in `StatsDetailWidgetProvider.loadSevenDayCounts` and `MainActivity` Stats tab.
  - **Shared Widget Icon Cache**: Added `AppIconHelper.loadAppIconAndroidBitmap` sharing `appIconBitmapCache` (`LruCache`), replacing duplicate decoding in `StatsDetailWidgetProvider`.
  - **Layer Decoupling**: Removed reverse dependency from `PhantomPrefs` to `service.NotificationCollectorService`, replacing with `onRuleCachesInvalidated` callback registration.

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

### English
- **Tag Compare**: [`v1.10.0...v1.11.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.10.0...v1.11.0) (`versionCode = 12`, `versionName = "1.11.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- Advanced Same-Key Throttle Settings (#96): Added "Treat digit micro-changes as same update" toggle (default on) and "Per-App Custom Throttle Seconds" (per-app override and picker dialog) in Settings for independent per-app throttling beyond the global slider.
- Connection State Tracking & Rule Cache Invalidation (#94): Added `isConnected` state tracking and `invalidateRuleCaches()` in `NotificationCollectorService` to refresh caches immediately when rules or keywords change in Settings or Tag Management.

**Changed & Fixed**
- Fixed Missed Consecutive Messages (#96):
  - `NotificationCollectorService` uses bounded `sameKeyHistory` (`LruCache(256)`) for content-aware throttling and stacked message tail extraction, ensuring consecutive new messages under the same notification key are not dropped without enabling polling.
  - Added `MessageDao.existsByDedupKeyInWindow` for DB-windowed replay deduplication (`isSuspectedReplay`), preventing repeated capture by constantly refreshing apps or missed messages on reconnect.
  - Implemented multi-tier `resolveNotificationTitle` fallback (`EXTRA_TITLE` → `EXTRA_TITLE_BIG` → `"Sender: "` prefix → cached app name), fixing empty-title notification handling.
- Fixed AI Assistant Response Truncation (#95):
  - Raised `AiClient.chatAssistant()` `maxTokens` to `4096` with a dedicated `chatOkHttpClient` (`connect 15s / write 30s / read 120s / callTimeout 180s`), keeping other AI paths' `maxTokens` and timeouts unchanged.
  - `ChatChoice` parses `finish_reason` and honestly appends "⚠ This response was truncated due to length limit" when `finish_reason == "length"`.
- Performance, Battery & Memory Optimizations (#94):
  - `ShizukuWatchdogService` uses adaptive backoff (300s steady-state, 60s within 10 min of service restart) to reduce idle wakeups without hurting keep-alive.
  - Conditionalized `requestRebind` in `WatchdogReceiver` and `HeartbeatWorker` (only when `!isConnected`), and replaced `HeartbeatPolicy.policyScope` with `ExistingPeriodicWorkPolicy.KEEP`.
  - Added `appLabelCache` (`LruCache(64)`) and 30s TTL caches for filter rules, app category rules, and alert keywords on `NotificationCollectorService` hot path to reduce repeated DB and system queries.

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

### English
- **Tag Compare**: [`v1.9.0...v1.10.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.9.0...v1.10.0) (`versionCode = 11`, `versionName = "1.10.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- Full Message Tag Display (#83): Extracted shared `MessageTagChipRow` (with `· AI` badge) across message lists, chat bubbles, app threads, and detail screens; tags load via a single `IN` batch query to avoid N+1 scroll lag.
- Settings Search Bar + AI Semantic Search (#88): Supports local instant keyword/alias filtering and natural-language AI semantic navigation (`<user_query>` isolation and whitelist ID validation; auto-expands, scrolls, and highlights target card without modifying values; honestly degrades when no AI provider is configured).
- Personalization Settings (#90): Added card density (Comfortable / Compact), Home sort order (Latest / Unread First / Risk First), custom Home filter bar visibility, default-collapsed summary toggle, and 12/24-hour clock toggle (all included in Settings search).
- Multi-Size Home-Screen Widgets (#87): Added 2x1 Mini (Today / Alerts) and 4x2 Detail (Today / Unread / Alerts + native `RemoteViews` `LinearLayout`/`ImageView` 7-day trend bars + recent source app icons), all supporting `resizeMode`, `xml-v31` `targetCell`, light/dark themes, and diff updates alongside 4x1.
- One-Tap OTP Verification Code Copy (#91): Strong-signal keyword + 4–8 digit detection displaying a one-tap "Copy Code" pill on message cards, chat bubbles, and detail screens.
- Stats Report Share Image Card (#91): Pure local Android `Canvas` generates a `1080×1350` static weekly report image shared via `FileProvider` `ACTION_SEND` (zero LLM, zero message upload).
- Message To-Do Reminders (#91): Supports picking reminder times (30 min / 1 hr / Tonight 20:00 / Tomorrow 09:00) from bottom sheet and detail screen, scheduled via `AlarmManager.setAndAllowWhileIdle` to open detail directly and rebuilt on reboot by `BootReceiver`.

**Changed & Fixed**
- Daily / Weekly AI Summary Cards Collapsed by Default (#84): Stats summary cards collapse to a single-line preview by default with persistent expand state, keeping generate, copy, and share actions.
- Home Filter Bar Reflow (#85): Switched to `FlowRow` auto-wrapping with compact chips so all filters and custom tags are visible without horizontal scrolling.
- Compact Top App Bar (#86): Standardized `TopAppBar` height to 52dp and title to `titleMedium` across screens while preserving 48dp button touch targets.
- Global Layout Optimization (#89): Standardized 16dp horizontal margins and 12dp card spacing per Material 3 across all screens; Scam Center uses shared `EmptyStateView`.
- Database Upgrade: Non-destructive Room 7→8 `MIGRATION_7_8` (adding `messages.remindAt`), plus `riskScore` field mapping for `todayRecentApps`.

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

### English
- **Tag Compare**: [`v1.8.0...v1.9.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.8.0...v1.9.0) (`versionCode = 10`, `versionName = "1.9.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- Home-Screen Widget (4x1): Displays today's message count / unread / scam alerts, opens PhantomCheck on tap, follows system light/dark theme, and updates via diff refresh alongside the persistent notification (no high-frequency alarms).
- Weekly AI Summary Report: Automatically generates weekly stats and risk trends every Sunday at 22:00; Stats tab supports manual generation, viewing past weekly reports, copying, and sharing (not restricted by token-saver mode).
- One-Tap AI Deep Message Analysis: Generates paragraph-style risk explanations (scam type, suspicious indicators, recommended actions) on the message detail screen; cancellable and counts toward daily AI quota.
- Threat-Intel API Key Management: Keys for Safe Browsing / PhishTank / Chainabuse can be entered directly in Settings (encrypted storage, with `local.properties` as fallback).

**Changed & Fixed**
- Fixed AI Assistant Chat Timeouts: Switched Retrofit to a custom OkHttpClient (read 90s / callTimeout 120s), automatically retrying once on chat timeout with honest notice.
- Fixed Threat-Intel Four-Source Connections: Switched fraud.tw to official endpoint `/api/check?d=` (previously 404), added descriptive UA + optional `app_key` for PhishTank (previously 403), and Basic auth + `chain` parameter for Chainabuse (previously 401).
- Persistent Stats Notification Layout: Removed duplicate colored icon on the left, keeping the monochrome small icon so the title is no longer truncated; right-side icon bar ordering unchanged.
- Battery-Saving Optimizations: Diff-based persistent notification refresh (skips rebuild when data is unchanged), merged stats queries into single SQL, skips idle refresh when screen is off (new notifications still update immediately), and icon caching.

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

### English
- **Tag Compare**: [`v1.7.0...v1.8.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.7.0...v1.8.0) (`versionCode = 9`, `versionName = "1.8.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added**
- **Public Anti-Scam Threat Intel (`IntelClient`) Zero-Token Pre-Check (#65)**:
  - `IndicatorExtractor` extracts full URLs (including short links) and cryptocurrency addresses (BTC / ETH / TRON) via regex while preserving token-saver `hasSuspiciousFeature` behavior.
  - Pluggable intel providers queried in order: `fraud.tw` (165 reports) → Google Safe Browsing Lookup API v4 (`BuildConfig.SAFE_BROWSING_API_KEY` injected from `local.properties`, zero secrets in repo) → optional PhishTank / Chainabuse (off by default).
  - Local 72-hour intel cache (`intel_cache`, `sha256(indicators + providerSig)`) and per-message verification results (`intel_results`, non-destructive Room Migration 6→7).
  - Pipeline integration: `NotificationCollectorService` pre-checks before `shouldRunAi`; `ai_rules` whitelist skips pre-check; hits immediately mark high-risk `scam`, bypass LLM and daily quota, and send alerts; misses or offline silently fall back to the standard AI flow (never fabricating hits, sending only indicators and never full text).
  - Added "Anti-Scam Threat Intel" card in Settings (per-source toggles, Safe Browsing key injection status, live per-source connection test, clear 72h cache); message detail and Scam Center display matched source and query time.
- **Live Model Switching in AI Chat / Daily Summary & Natural-Language Rule Creation (#71)**:
  - Added `AiProviderSwitcherChip` to Chat and Stats "Daily AI Summary" card for instant switching among configured AI Providers/models (honestly showing unconfigured state when none are set).
  - When explicitly asked to add a whitelist or tag rule in chat, AI outputs a structured `add_rule` block; UI hides raw JSON and shows a mandatory confirmation dialog before writing to `ai_rules`; `buildRagSystemPrompt` adds `<message_content>` delimiters and anti-prompt-injection instructions.
- **Release Process Documentation (#70)**: Created `CHANGELOG.md`, synced `README.md`, and added the release checklist to `AGENTS.md`.

**Changed & Fixed**
- **Haptic Feedback & Liquid Glass Threshold Fixes (#68)**:
  - `rememberPhantomHaptic` probes predefined effects via `areAllEffectsSupported` on API 30+, falling back to explicit-amplitude `createWaveform` (tap 25ms/220, long press 40ms/255) when unsupported or on API 29, fixing silent haptics on some devices.
  - Lowered `Glass.kt` background blur threshold from API 33 to API 31 (Android 12+), keeping translucent tinted fallback on older versions; added OEM haptic settings path hints and Liquid Glass version notes in Settings.
- **Persistent Stats Notification Layout & Tap Behavior Fixes (#69)**:
  - Switched to `RemoteViews` (`notif_stats_collapsed.xml`) with fixed PhantomCheck icon on the left and "newest on left" horizontal app icon bar on the right, highlighting the latest app with border and dot; kept swipe-dismiss rebuild and `FLAG_NO_CLEAR` keep-alive.
  - When tap behavior is "Open Original App", opens the app of the latest message (falling back to PhantomCheck if unresolvable).
- **Performance, Battery-Saving Fallback & Tour Theme Adaptation (#67)**:
  - `MainActivity` preserves tab state and 30s TTL data cache; `ConversationScreen` uses keyset pagination (`LIMIT 200` on scroll); `AppIconHelper` adds cross-page `LruCache`; `LazyColumn`s include `key` and `derivedStateOf`.
  - Automatically disables glass blur when `PowerManager.isPowerSaveMode` is active; removed `StatsForegroundService` dynamic `CONNECTIVITY_CHANGE` broadcast receiver to reduce battery drain.
  - `Onboarding.kt` and `SettingsTourOverlay` use `MaterialTheme.colorScheme` with stronger card surface and scrim contrast while preserving touch-through inside the spotlight hole.

## [1.7.0] - 2026-11-15

### 新增與修正
- **Shizuku 偵測修復與開關健檢（#64）**：修正 Shizuku binder/授權狀態偵測、新增設定頁 6 步高亮導覽教學、每日摘要複製與分享、氣泡長按複製、AI 回覆 Markdown 渲染（`PhantomMarkdownText`）與助手上下文放寬，以及震動回饋基礎修正。

### English
- **Tag Compare**: [`v1.6.0...v1.7.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.6.0...v1.7.0) (`versionCode = 8`, `versionName = "1.7.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added & Fixed**
- **Shizuku Detection Fix & Toggle Health Check (#64)**: Fixed Shizuku binder/authorization state detection; added 6-step spotlight tour in Settings, daily summary copy & share, bubble long-press copy, Markdown rendering for AI replies (`PhantomMarkdownText`), relaxed assistant context limits, and foundational haptic feedback fixes.

## [1.6.0] - 2026-11-15

### 新增與修正
- **保活強化與 Shizuku 整合（#60）**：整合 Shizuku 進階保活與看門狗（`ShizukuKeepAlive` / `ShizukuWatchdogService`）、凍結高風險 App 能力、修正通知圖示渲染，並於介面顯示 AI 標籤。

### English
- **Tag Compare**: [`v1.5.0...v1.6.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.5.0...v1.6.0) (`versionCode = 7`, `versionName = "1.6.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added & Fixed**
- **Keep-Alive Hardening & Shizuku Integration (#60)**: Integrated Shizuku advanced keep-alive and watchdog (`ShizukuKeepAlive` / `ShizukuWatchdogService`), high-risk app freezing capability, notification icon rendering fixes, and AI tag display in UI.

## [1.5.0] - 2026-11-14

### 新增與修正
- **AI 助手、10 步教學與規則系統（#55）**：新增「AI 助手」RAG 唯讀分頁、10 步穿透式新手引導、常駐通知 400ms 節流原地重建（`repostDirectly`）、零 token App 分類規則與 `ai_rules` 白名單、省 token 模式與多項外觀/通知微調開關，Room Migration 5→6。

### English
- **Tag Compare**: [`v1.4.0...v1.5.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.4.0...v1.5.0) (`versionCode = 6`, `versionName = "1.5.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added & Fixed**
- **AI Assistant, 10-Step Tour & Rule System (#55)**: Added read-only RAG "AI Assistant" tab, 10-step pass-through onboarding tour, 400ms-throttled in-place persistent notification rebuild (`repostDirectly`), zero-token app category rules and `ai_rules` whitelist, token-saver mode, and multiple appearance/notification fine-tuning toggles; Room Migration 5→6.

## [1.4.0] - 2026-11-13

### 新增與修正
- **互動強化、AI 智慧標記與玻璃手感（#52）**：移植 `LiquidGlassTabBar`、`GlassCard`、`FluidSwitch`、`SwipeToRevealRow`；新增勿擾時段、摘要完成推播、可疑連結警告、詐騙週趨勢與搜尋高亮、批次多選與 VIP 置頂，Room Migration 4→5。

### English
- **Tag Compare**: [`v1.3.0...v1.4.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.3.0...v1.4.0) (`versionCode = 5`, `versionName = "1.4.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added & Fixed**
- **Interaction Enhancements, AI Smart Tagging & Glass Feel (#52)**: Ported `LiquidGlassTabBar`, `GlassCard`, `FluidSwitch`, and `SwipeToRevealRow`; added Do Not Disturb schedule, summary-complete push notifications, suspicious link warnings, weekly scam trends, search highlighting, batch multi-select, and VIP pinning; Room Migration 4→5.

## [1.3.0] - 2026-11-12

### 新增與修正
- **全面強化與前景統計服務（#50）**：新增 `StatsForegroundService` 常駐統計通知、訊息詳情頁、標籤系統、CSV 匯出與設定頁重構，Room Migration 3→4。

### English
- **Tag Compare**: [`v1.2.0...v1.3.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.2.0...v1.3.0) (`versionCode = 4`, `versionName = "1.3.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added & Fixed**
- **Comprehensive Enhancements & Foreground Stats Service (#50)**: Added `StatsForegroundService` persistent stats notification, message detail screen, tag system, CSV export, and Settings refactor; Room Migration 3→4.

## [1.2.0] - 2026-11-11

### 新增與修正
- **訊息中心改版（#48）**：依 App 與寄件者分組對話、聊天式對話頁與群組摘要通知過濾修正。

### English
- **Tag Compare**: [`v1.1.0...v1.2.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.1.0...v1.2.0) (`versionCode = 3`, `versionName = "1.2.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added & Fixed**
- **Message Center Overhaul (#48)**: Grouped conversations by app and sender, chat-style conversation screen, and group summary notification filter fixes.

## [1.1.0] - 2026-11-10

### 新增與修正
- 正式簽署版本與基礎 CI/CD 建置發佈管線。

### English
- **Tag Compare**: [`v1.0.0...v1.1.0`](https://github.com/eric11yothelast-Hwanshen/PhantomCheck/compare/v1.0.0...v1.1.0) (`versionCode = 2`, `versionName = "1.1.0"`)
- **Release Assets (Dual APKs)**: `app-release.apk` & `app-debug.apk`.

**Added & Fixed**
- Release-signed build and basic CI/CD build & release pipeline.

## [1.0.0] - 2026-11-09

### 新增
- 幻訊 PhantomCheck 初始版本：`NotificationListenerService` 通知攔截預覽、Mistral AI 詐騙風險分析、關鍵字過濾與 Room 本地資料庫。

### English
- **Initial Release**: `versionCode = 1`, `versionName = "1.0.0"`.

**Added**
- Initial release of PhantomCheck: `NotificationListenerService` notification interception preview, Mistral AI scam risk analysis, keyword filtering, and local Room database.
