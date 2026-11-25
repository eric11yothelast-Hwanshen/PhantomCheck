package com.phantom.checker.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * #468 / #479 雙語文案中心（以 Compose SnapshotState 驅動，切換 auto / zh-TW / en 時全視窗與系統匣即時重組生效）。
 */
object DesktopStrings {
    private var languageState by mutableStateOf(DesktopSettingsStore.state.value.language)

    fun syncLanguage(lang: String) {
        languageState = lang
    }

    private val isZh: Boolean
        get() = when (languageState) {
            "zh-TW" -> true
            "en" -> false
            else -> Locale.getDefault().language.startsWith("zh", ignoreCase = true)
        }

    fun t(zh: String, en: String): String = if (isZh) zh else en

    val appTitle get() = t("幻訊 PhantomCheck 桌面端", "PhantomCheck Desktop")
    val statusScanning get() = t("掃描區網裝置中 (_phantomcheck._tcp.)…", "Scanning LAN devices (_phantomcheck._tcp.)…")
    val statusConnected get() = t("已連線：", "Connected: ")
    val statusReconnecting get() = t("連線中斷，指數退避重連中…", "Disconnected, reconnecting with backoff…")
    val statusUnpaired get() = t("尚未配對", "Not paired")
    val discoveredDevices get() = t("區網發現裝置", "Discovered Devices")
    val noDevicesFound get() = t("尚未掃描到裝置（可改用下方手動輸入位址與配對碼）", "No devices found yet (use manual host:port + code below)")
    val manualPairTitle get() = t("手動配對（mDNS 不可用時備援）", "Manual Pairing (mDNS Fallback)")
    val hostPortLabel get() = t("手機 IP:埠（例如 192.168.1.50:49515）", "Phone IP:Port (e.g. 192.168.1.50:49515)")
    val pairCodeLabel get() = t("6 位數配對碼", "6-digit Pairing Code")
    val pairBtn get() = t("配對並連線", "Pair & Connect")
    val disconnectBtn get() = t("中斷連線", "Disconnect")
    val unpairBtn get() = t("清除本地配對", "Clear Local Pairing")
    val firewallHint get() = t(
        "提示：若掃描或連線失敗，請檢查 Windows 防火牆是否允許存取區域網路，或改用手動輸入 IP:埠。",
        "Tip: If discovery or connection fails, check your firewall LAN rules or enter IP:Port manually."
    )

    // Navigation
    val navMessages get() = t("訊息", "Messages")
    val navStats get() = t("統計", "Stats")
    val navTodos get() = t("待辦與記事", "To-Do & Notes")
    val navChat get() = t("AI 助手", "AI Assistant")
    val navRules get() = t("過濾與專注", "Filters & Focus")
    val navSettings get() = t("設定中心", "Settings")

    // Messages
    val filterAll get() = t("全部", "All")
    val filterUnread get() = t("未讀", "Unread")
    val filterStarred get() = t("星標", "Starred")
    val filterGroup get() = t("群組", "Groups")
    val filterScam get() = t("詐騙", "Scam")
    val markAllRead get() = t("全部標已讀", "Mark All Read")
    val markRead get() = t("標已讀", "Mark Read")
    val conversationMarkRead get() = t("對話標已讀", "Mark Thread Read")
    val markReadNote get() = t("僅標記幻訊列表已讀（不清除原 App 未讀）", "Marks read in PhantomCheck list only")
    val replyPlaceholder get() = t("快速回覆內容…", "Quick reply text…")
    val sendReply get() = t("回覆", "Reply")
    val newMessageMirror get() = t("新訊息鏡射：", "New message: ")
    val refresh get() = t("重新整理", "Refresh")
    val searchMessagesHint get() = t("搜尋 App、寄件人或預覽內容 (Ctrl+F)…", "Search app, sender, or preview (Ctrl+F)…")
    val selectMessageHint get() = t("請在左側點選一則對話檢視詳情與完整對話串", "Select a conversation on the left to view details & full thread")
    val groupBadge get() = t("群組", "Group")

    // Stats
    val statsTotal get() = t("總訊息", "Total")
    val statsToday get() = t("今日訊息", "Today")
    val statsUnread get() = t("未讀數", "Unread")
    val statsScam get() = t("詐騙攔截", "Scam")
    val statsRecentApps get() = t("今日活躍來源 App", "Active Apps Today")
    val statsWeeklyTopApps get() = t("本週活躍 App Top 5", "Weekly Top 5 Active Apps")
    val stats7DayChart get() = t("近 7 日訊息趨勢", "7-Day Message Volume")
    val stats30DayChart get() = t("近 30 日訊息趨勢", "30-Day Message Volume")
    val statsHourlyChart get() = t("今日 24 小時分佈", "Today's Hourly Volume")
    val stats7DayMode get() = t("7 日", "7 Days")
    val stats30DayMode get() = t("30 日", "30 Days")
    val statsHourlyMode get() = t("每小時", "Hourly")
    val statsRiskDist get() = t("風險分類分佈", "Risk Distribution")
    fun statsMsgCount(n: Int): String = t("$n 則", "$n msgs")

    // Todos
    val todosEmpty get() = t("目前無待辦事項", "No to-do items")

    // Chat
    val chatInputPlaceholder get() = t("輸入訊息給 AI 夥伴（Ctrl+Enter 送出）…", "Message AI Assistant (Ctrl+Enter to send)…")
    val chatSend get() = t("送出", "Send")
    val chatThinking get() = t("AI 思考中…", "Thinking…")
    val chatSessionsTitle get() = t("對話列表", "Sessions")
    val chatNewSession get() = t("+ 新對話", "+ New Chat")
    val chatCopy get() = t("複製", "Copy")
    val chatCopied get() = t("已複製到剪貼簿", "Copied to clipboard")
    val quickPromptSummary get() = t("摘要今日重要訊息", "Summarize today's key messages")
    val quickPromptScam get() = t("幫我檢查近期可疑詐騙", "Check recent suspicious scams")
    val quickPromptTodos get() = t("整理未完成待辦", "Review pending to-dos")

    // Whitelist & Tags
    val whitelistTitle get() = t("白名單規則", "Allowlist Rules")
    val whitelistSenderHint get() = t("寄件者 / 關鍵字", "Sender / Keyword")
    val whitelistPkgHint get() = t("套件名稱（選填）", "Package name (optional)")
    val add get() = t("新增", "Add")
    val remove get() = t("移除", "Remove")
    val tagsTitle get() = t("標籤管理", "Tags")
    val tagNameHint get() = t("新標籤名稱", "New tag name")
    val notConnectedHint get() = t("請先於「設定中心」配對並連線手機", "Please pair and connect your phone in Settings first")

    // Micro labels & #471
    val selectDevice get() = t("選擇", "Select")
    val dismiss get() = t("關閉", "Dismiss")
    val ok get() = t("好", "OK")
    val replySent get() = t("回覆已送出", "Reply sent")
    val connectingPulse get() = t("正在建立加密連線…", "Establishing encrypted link…")
    val fingerprintPrefix get() = t("指紋：", "FP: ")
    val emptyMessages get() = t("目前無符合條件的訊息", "No messages match this filter")
    val emptyChat get() = t("開始與手機端 AI 夥伴對話", "Start chatting with your AI Assistant")
    val emptyWhitelist get() = t("尚未設定白名單規則", "No allowlist rules yet")
    val emptyTags get() = t("尚未建立標籤", "No tags yet")
    val versionLabel get() = t("桌面端 v${DesktopSettingsStore.CURRENT_DESKTOP_VERSION}", "Desktop v${DesktopSettingsStore.CURRENT_DESKTOP_VERSION}")
    val commandNotSentDisconnected get() = t("目前未連線手機，操作未送出", "Not connected to phone — action not sent")
    val chatTimeoutError get() = t("AI 回覆逾時（130 秒）或連線中斷，請稍後重試", "AI reply timed out (130s) or connection dropped. Please retry.")

    // #467 Bidirectional Sync
    val star get() = t("星標", "Star")
    val unstar get() = t("取消星標", "Unstar")
    val deepAnalyze get() = t("深度分析", "Deep Analyze")
    val deepAnalyzing get() = t("深度分析中…", "Analyzing…")
    val deepAnalysisTitle get() = t("AI 深度風險分析", "AI Deep Risk Analysis")
    val addNote get() = t("記一筆", "Note")
    val noteAdded get() = t("已新增記事", "Note saved")
    val notesTitle get() = t("記事本", "Notes")
    val notesEmpty get() = t("尚無記事", "No notes yet")
    val noteInputHint get() = t("輸入記事內容…", "Write a note…")
    val todoAddHint get() = t("新增待辦標題…", "New todo title…")
    val todoEdit get() = t("編輯", "Edit")
    val todoSave get() = t("儲存", "Save")
    val todoDelete get() = t("刪除", "Delete")
    val mutedAppsTitle get() = t("靜音 App 管理", "Muted Apps")
    val mutedAppsEmpty get() = t("尚無可管理的 App", "No apps to manage yet")
    val conversationBtn get() = t("對話串", "Thread")
    val conversationTitle get() = t("對話串檢視（上限 200 字預覽）", "Conversation Thread (200-char preview)")
    val conversationEmpty get() = t("尚無同對話訊息", "No messages in this thread")
    val phoneBattery get() = t("手機電量", "Phone Battery")
    val charging get() = t("充電中", "Charging")
    val applyTags get() = t("標籤", "Tags")

    // #468 Settings Center (10 System + 8 Custom)
    val settingsTabConn get() = t("連線配對", "Connection")
    val settingsTabSystem get() = t("系統設定 (10)", "System (10)")
    val settingsTabCustom get() = t("介面自訂 (8)", "Customize (8)")
    val sysLangTitle get() = t("1. 界面語言（即時生效）", "1. Interface Language (Instant)")
    val sysAutoStartTitle get() = t("2. 開機自動啟動（Windows HKCU\\Run，啟動縮至系統匣）", "2. Launch at Startup (starts in system tray)")
    val sysCloseBehaviorTitle get() = t("3. 關閉視窗行為", "3. Window Close Behavior")
    val closeMinimizeTray get() = t("縮到系統匣背景運行（預設）", "Minimize to System Tray (Default)")
    val closeExitApp get() = t("直接結束程式", "Exit Application")
    val sysNativeNotifTitle get() = t("4. 系統原生通知與勿擾時段（預設 00:00–07:00）", "4. Native Notifications & DND (00:00–07:00)")
    val enableNativeNotif get() = t("啟用系統原生通知", "Enable Native Notifications")
    val enableDnd get() = t("啟用勿擾時段", "Enable Do Not Disturb")
    val sysBackoffTitle get() = t("5. 自動重連退避上限（秒）", "5. Max Reconnect Backoff (sec)")
    val sysPingTitle get() = t("6. WebSocket 心跳間隔（秒）", "6. WebSocket Ping Interval (sec)")
    val sysPreviewLenTitle get() = t("7. 訊息預覽顯示上限（字數）", "7. Message Preview Display Limit (chars)")
    val sysConfigTitle get() = t("8. 快取與配置管理", "8. Cache & Configuration")
    val openConfigFolder get() = t("開啟配置資料夾", "Open Config Folder")
    val resetSettingsBtn get() = t("還原預設設定", "Reset Settings")
    val sysUpdateTitle get() = t("9. 檢查更新（手動觸發）", "9. Check for Updates (Manual)")
    val checkUpdateBtn get() = t("立即檢查 GitHub Releases", "Check GitHub Releases")
    val checkingUpdate get() = t("檢查中…", "Checking…")
    val sysDiagTitle get() = t("10. 診斷紀錄（3 份旋轉日誌）", "10. Diagnostics (3-File Rotating Log)")
    val openLogsFolder get() = t("開啟日誌資料夾", "Open Logs Folder")
    val exportDiagBtn get() = t("匯出診斷報告", "Export Diagnostics")

    val custAccentTitle get() = t("1. 強調色（6 色）", "1. Accent Color (6 Presets)")
    val custDensityTitle get() = t("2. 介面密度", "2. UI Density")
    val custFontScaleTitle get() = t("3. 字級縮放", "3. Font Scale")
    val custSidebarTitle get() = t("4. 側欄寬度與折疊", "4. Sidebar Width & Collapse")
    val collapseSidebar get() = t("折疊側欄", "Collapse Sidebar")
    val custCardStyleTitle get() = t("5. 訊息卡樣式", "5. Message Card Style")
    val custMirrorModeTitle get() = t("6. 新訊息鏡射顯示方式", "6. New Message Mirror Display")
    val custBgThemeTitle get() = t("7. 背景主題（4 預設漸層＋自訂背景圖）", "7. Background Theme (4 Gradients + Custom Image)")
    val customBgPathHint get() = t("自訂本機背景圖絕對路徑（留空使用漸層）", "Local image path (leave blank for gradient)")
    val clearBgImage get() = t("清除背景圖", "Clear Image")
    val custAnimTitle get() = t("8. 動畫效果（低階機可關閉以提升效能）", "8. Animations (Disable for performance mode)")
    val shortcutsHint get() = t("捷徑：Ctrl+1..6 切換分頁 · Ctrl+F 搜尋訊息 · Esc 關閉彈窗", "Shortcuts: Ctrl+1..6 switch tabs · Ctrl+F search · Esc close dialogs")

    // #469 Tray strings
    val trayOpen get() = t("開啟 PhantomCheck", "Open PhantomCheck")
    val trayExit get() = t("結束程式", "Exit")
    val trayUnsupportedNotice get() = t("此系統環境不支援系統匣（SystemTray），關閉視窗將直接結束程式。", "System tray is not supported in this environment; closing will exit the app.")

    fun reconnectingCountdown(remSec: Int, totalSec: Int): String =
        t("將於 ${remSec}s 後重連（退避 ${totalSec}s）", "Reconnecting in ${remSec}s (backoff ${totalSec}s)")

    fun riskLabelText(raw: String): String = when (raw.lowercase()) {
        "scam" -> t("詐騙", "SCAM")
        "suspicious" -> t("可疑", "SUSPICIOUS")
        else -> t("安全", "SAFE")
    }

    // #476 Reversed Desktop WSS Server & Diagnostics
    val serverStatusTitle get() = t("桌面端 WSS 伺服器狀態", "Desktop WSS Server Status")
    val statusWaitingForPhone get() = t("等待手機連線中…", "Waiting for phone…")
    val statusServerStopped get() = t("伺服器已停止", "Server stopped")
    val statusServerError get() = t("伺服器啟動失敗", "Server error")
    val serverStartBtn get() = t("啟動伺服器", "Start Server")
    val serverStopBtn get() = t("停止伺服器", "Stop Server")
    val serverRestartBtn get() = t("套用埠並重啟", "Apply Port & Restart")
    val serverPortLabel get() = t("監聽埠（預設 49515）", "Listen Port (default 49515)")
    val serverLanIpsLabel get() = t("本機區網位址（供手機手動輸入備援）", "Local LAN Addresses (for phone manual fallback)")
    val mdnsActiveLabel get() = t("mDNS 廣播中", "mDNS Advertising")
    val mdnsFailedLabel get() = t("mDNS 廣播失敗（請以手動位址或 QR 配對）", "mDNS broadcast failed (use manual address or QR)")
    val serverFirewallHint get() = t(
        "Windows 防火牆說明：首次啟動監聽時請在系統彈窗點選「允許存取（私人網路）」。若手機顯示無法連線，請確認 Windows 防火牆已放行 PhantomCheckDesktop.exe 的輸入規則。",
        "Windows Firewall: Click 'Allow access (Private networks)' on the first prompt. If your phone cannot reach this PC, allow inbound connections for PhantomCheckDesktop.exe in Windows Firewall."
    )
    val pairNewPhoneTitle get() = t("配對新手機（單次 6 位碼，3 分鐘有效）", "Pair New Phone (Single-Use 6-Digit Code, 3 min)")
    val pairCodeRegenerate get() = t("重新產生配對碼", "Regenerate Code")
    val pairCodeExpired get() = t("配對碼已過期，請重新產生", "Pairing code expired — regenerate")
    val pairedPhonesTitle get() = t("已配對手機", "Connected Devices")
    val pairedPhonesEmpty get() = t("尚未配對任何手機（請在手機端「連線」分頁配對）", "No connected devices yet (pair from the Link tab on your phone)")
    val serverAuditTitle get() = t("伺服器連線與指令審計（上限 200 筆）", "Server Connection & Command Audit (max 200)")
    val serverAuditEmpty get() = t("尚無審計紀錄", "No audit entries yet")
    val clearLogsBtn get() = t("清除", "Clear")

    fun serverListeningOn(port: Int, connected: Int): String =
        t("監聽中（埠 $port）· 已連線 $connected 台手機", "Listening on port $port · $connected phone(s) connected")

    fun serverPortInUse(port: Int): String =
        t("連接埠 $port 已被其他程式佔用，請更換連接埠並重啟伺服器。", "Port $port is already in use. Change the port and restart the server.")

    fun pairCodeRemaining(mmss: String): String =
        t("有效剩餘 $mmss（單次有效）", "Valid for $mmss (single-use)")

    fun classifiedError(code: String): String = when (code) {
        com.phantom.checker.shared.LinkErrorCode.PORT_IN_USE ->
            t("連接埠已被佔用，請至連線設定更換埠號", "Port is already in use — change port in Connection settings")
        com.phantom.checker.shared.LinkErrorCode.VERSION_MISMATCH ->
            t("協定版本不符（桌面為 v${com.phantom.checker.shared.LinkProtocol.PROTOCOL_VERSION}），請更新雙端並重新配對", "Protocol version mismatch (desktop v${com.phantom.checker.shared.LinkProtocol.PROTOCOL_VERSION}) — update both sides and re-pair")
        com.phantom.checker.shared.LinkErrorCode.FINGERPRINT_MISMATCH ->
            t("憑證指紋不符，已拒絕連線（請重新配對）", "Certificate fingerprint mismatch — connection rejected (please re-pair)")
        com.phantom.checker.shared.LinkErrorCode.INVALID_PAIR_CODE ->
            t("6 位配對碼錯誤", "Invalid 6-digit pairing code")
        com.phantom.checker.shared.LinkErrorCode.PAIR_CODE_EXPIRED ->
            t("配對碼已過期，請重新產生", "Pairing code expired — please regenerate")
        com.phantom.checker.shared.LinkErrorCode.AUTH_FAILED ->
            t("身分驗證失敗（HMAC 不符），請重新配對", "Authentication failed (HMAC mismatch) — please re-pair")
        com.phantom.checker.shared.LinkErrorCode.TOO_MANY_CLIENTS ->
            t("已達同時連線上限（最多 2 台手機）", "Maximum concurrent phones (2) reached")
        com.phantom.checker.shared.LinkErrorCode.TLS_ERROR ->
            t("TLS 憑證初始化或握手失敗", "TLS certificate initialization or handshake failed")
        com.phantom.checker.shared.LinkErrorCode.TIMEOUT ->
            t("指令等待手機回應逾時，請檢查區網連線", "Command timed out waiting for phone response")
        else -> t("連線錯誤：$code", "Connection error: $code")
    }

    // #477 QR Pairing Dialog
    val qrConfigurePhoneBtn get() = t("配置新手機（顯示 QR Code）", "Pair New Device (Show QR Code)")
    val qrDialogTitle get() = t("掃描 QR Code 配對新手機", "Scan QR Code to Pair")
    val qrDialogHint get() = t(
        "請在手機端開啟「幻訊 → 連線 → 掃描電腦 QR Code」，掃描下方條碼並確認電腦名稱與憑證指紋。",
        "Open PhantomCheck on your phone → Link → Scan Desktop QR Code, then verify the PC name and fingerprint."
    )
    val qrSelectNicLabel get() = t("偵測到多張網卡，請選擇手機同區網的 IPv4 位址：", "Multiple network interfaces detected — pick the IPv4 on your phone's Wi-Fi:")
    val qrNoLanIpError get() = t("尚未偵測到區網 IPv4 位址，請確認電腦已連上區域網路。", "No LAN IPv4 address detected. Check your network connection.")
    val qrUnavailableHint get() = t("伺服器尚未就緒或無法產生 QR Code", "Server not ready or unable to render QR code")

    // #478 Auto Update & About Tab
    val settingsTabAbout get() = t("關於", "About")
    val updateDialogTitle get() = t("發現桌面端新版本", "Desktop Update Available")
    val updateNoNotes get() = t("（此版本無更新說明）", "(No release notes provided)")
    val updateOpenReleaseBtn get() = t("前往 Release 頁面", "Open Release Page")
    val updateDownloadInstallBtn get() = t("下載並執行安裝", "Download & Install")
    val updateRunInstallerBtn get() = t("啟動安裝程式", "Launch Installer")
    val updateInstallerLaunched get() = t("已啟動安裝程式", "Installer launched")
    val updateUpToDate get() = t("目前已是最新版本", "You are up to date")
    val updateNotifTitle get() = t("PhantomCheck 桌面端有新版本", "PhantomCheck Desktop Update Available")
    val openLinkFailed get() = t("無法開啟外部連結", "Failed to open external link")
    val aboutTitle get() = t("關於 PhantomCheck 桌面端", "About PhantomCheck Desktop")
    val aboutProtocolCompat get() = t("相容手機端協定版本：v${com.phantom.checker.shared.LinkProtocol.PROTOCOL_VERSION}", "Compatible Phone Protocol: v${com.phantom.checker.shared.LinkProtocol.PROTOCOL_VERSION}")
    val aboutLinksTitle get() = t("專案連結與條款", "Project Links & Legal")
    val aboutRepoBtn get() = t("GitHub 倉庫", "GitHub Repo")
    val aboutReleasesBtn get() = t("所有版本 (Releases)", "Releases")
    val aboutLicenseBtn get() = t("授權條款 (GPL-3.0)", "LICENSE (GPL-3.0)")
    val aboutPrivacyBtn get() = t("隱私政策 (PRIVACY)", "PRIVACY")
    val aboutDisclaimerBtn get() = t("免責聲明 (DISCLAIMER)", "DISCLAIMER")
    val aboutReleaseNotesTitle get() = t("更新內容（最近 10 個版本）", "Release Notes (Recent 10 Releases)")
    val aboutLoadingNotes get() = t("正在載入更新內容…", "Loading release notes…")

    fun updateDownloading(pct: Int): String = t("正在下載安裝檔… $pct%", "Downloading installer… $pct%")
    fun updateReadyToLaunch(name: String): String = t("已下載至暫存：$name，點擊「啟動安裝程式」繼續。", "Downloaded to temp: $name. Click 'Launch Installer' to continue.")
    fun updateNotifBody(ver: String): String = t("桌面端 v$ver 已發佈，開啟設定中心即可檢視更新內容並下載。", "Desktop v$ver is available. Open Settings to view notes and install.")

    // #479 Full Localization & 16 Custom Settings + DND Picker
    val settingsTabCustom16 get() = t("介面自訂 (16)", "Customize (16)")
    val langAuto get() = t("跟隨系統 (Auto)", "Follow System (Auto)")
    val feedbackSettingsReset get() = t("已還原為預設設定", "Settings reset to defaults")
    val feedbackOpenFailed get() = t("開啟資料夾失敗", "Failed to open folder")
    val feedbackExportFailed get() = t("匯出診斷報告失敗", "Failed to export diagnostics")
    fun feedbackAutoStart(enabled: Boolean): String =
        if (enabled) t("已啟用開機自動啟動", "Launch at startup enabled") else t("已停用開機自動啟動", "Launch at startup disabled")
    fun feedbackExported(path: String): String = t("已匯出診斷報告：$path", "Exported diagnostics: $path")

    val dndStartLabel get() = t("開始時間", "Start Hour")
    val dndEndLabel get() = t("結束時間", "End Hour")
    val dndCrossDayHint get() = t("（跨日）", "(overnight)")

    val custCustomHexTitle get() = t("9. 自訂強調色 HEX（留空沿用上方預設）", "9. Custom Accent HEX (blank = preset)")
    val custCustomHexHint get() = t("例如 #FF6B6B", "e.g. #FF6B6B")
    val custCustomHexInvalid get() = t("格式須為 #RRGGBB", "Must be #RRGGBB")
    val custTimeFormatTitle get() = t("10. 時間顯示格式", "10. Time Format")
    val time24h get() = t("24 小時制", "24-hour")
    val time12h get() = t("12 小時制 (AM/PM)", "12-hour (AM/PM)")
    val custCardCornerTitle get() = t("11. 訊息卡圓角", "11. Message Card Corner Radius")
    val custGlassBlurTitle get() = t("12. 玻璃模糊通透強度", "12. Glass Blur / Translucency")
    val custChatFontSizeTitle get() = t("13. AI 聊天字級", "13. AI Chat Font Size")
    val custNotifSoundTitle get() = t("14. 新訊息通知音效", "14. Notification Sound")
    val custAutoConnectTitle get() = t("15. 啟動時自動啟動伺服器接收已配對手機", "15. Auto-start Server for Paired Phone on Launch")
    val custDefaultTabTitle get() = t("16. 啟動預設分頁", "16. Default Startup Tab")

    fun densityLabel(d: UiDensity): String = when (d) {
        UiDensity.COMPACT -> t("緊湊 (Compact)", "Compact")
        UiDensity.STANDARD -> t("標準 (Standard)", "Standard")
        UiDensity.COMFORTABLE -> t("舒適 (Comfortable)", "Comfortable")
    }

    fun cardStyleLabel(s: MessageCardStyle): String = when (s) {
        MessageCardStyle.CARD -> t("卡片 (Card)", "Card")
        MessageCardStyle.COMPACT_LIST -> t("精簡列表 (Compact List)", "Compact List")
    }

    fun mirrorModeLabel(m: MirrorDisplayMode): String = when (m) {
        MirrorDisplayMode.BANNER -> t("視窗橫幅 (Banner)", "In-App Banner")
        MirrorDisplayMode.TOAST -> t("右下小彈窗 (Toast)", "Corner Toast")
        MirrorDisplayMode.NATIVE -> t("系統通知 (Native)", "System Notification")
        MirrorDisplayMode.BOTH -> t("橫幅＋系統 (Both)", "Banner + System")
        MirrorDisplayMode.COMBO -> t("全開組合 (Combo)", "All Combined")
    }

    fun bgThemeLabel(b: BgThemePreset): String = when (b) {
        BgThemePreset.MIDNIGHT -> t("午夜藍 (Midnight)", "Midnight")
        BgThemePreset.AURORA -> t("極光紫 (Aurora)", "Aurora")
        BgThemePreset.OBSIDIAN -> t("黑曜石 (Obsidian)", "Obsidian")
        BgThemePreset.DEEP_OCEAN -> t("深海藍 (Deep Ocean)", "Deep Ocean")
    }

    fun glassBlurLabel(g: GlassBlurLevel): String = when (g) {
        GlassBlurLevel.LOW -> t("低 (Low)", "Low")
        GlassBlurLevel.MEDIUM -> t("中 (Medium)", "Medium")
        GlassBlurLevel.HIGH -> t("高 (High)", "High")
    }

    fun chatFontSizeLabel(c: ChatFontSize): String = when (c) {
        ChatFontSize.SMALL -> t("小 (Small)", "Small")
        ChatFontSize.STANDARD -> t("標準 (Standard)", "Standard")
        ChatFontSize.LARGE -> t("大 (Large)", "Large")
    }

    fun startupTabLabel(key: String): String = when (key) {
        "MESSAGES" -> navMessages
        "STATS" -> navStats
        "TODOS" -> navTodos
        "CHAT" -> navChat
        "RULES" -> navRules
        "SETTINGS" -> navSettings
        else -> navMessages
    }

    // #487 Windows Firewall Self-Check & One-Click Allow
    val firewallSectionTitle get() = t("Windows 防火牆自我檢查與一鍵放行", "Windows Firewall Self-Check & One-Click Allow")
    val firewallCheckBtn get() = t("檢查防火牆狀態", "Check Firewall Status")
    val firewallChecking get() = t("檢查中…", "Checking…")
    val firewallAllowBtn get() = t("一鍵允許防火牆（管理員）", "One-Click Allow Firewall (UAC)")
    val firewallApplying get() = t("套用中（請確認 UAC 提示）…", "Applying (approve UAC prompt)…")
    val firewallNonWindows get() = t("非 Windows 系統：請依您的作業系統防火牆設定放行 TCP 輸入連線。", "Non-Windows OS: ensure your OS firewall allows inbound TCP on the listen port.")
    fun firewallPortRuleOk(port: Int): String = t("✓ 已找到埠規則：TCP $port 輸入允許（Profile: Any）", "✓ Port rule found: Inbound TCP $port allowed (Profile: Any)")
    fun firewallPortRuleMissing(port: Int): String = t("✕ 尚未建立 TCP $port 輸入允許規則（手機連線將逾時 TIMEOUT）", "✕ No inbound allow rule for TCP $port (phone will TIMEOUT)")
    val firewallProgRuleOk get() = t("✓ 已找到程式執行檔輸入規則（雙保險）", "✓ App executable inbound rule found (backup)")
    val firewallProgRuleMissing get() = t("• 尚未建立程式執行檔規則（可一併透過一鍵允許建立）", "• App executable rule not present (created together via One-Click Allow)")
    val firewallPublicWarning get() = t(
        "⚠ 偵測到「公用網路（Public）」介面（例如 Windows 行動熱點／ICS 192.168.137.x）：Windows 防火牆預設會靜默丟棄輸入連線導致手機逾時，請點擊下方「一鍵允許防火牆」放行。",
        "⚠ 'Public' network profile detected (e.g., Windows Mobile Hotspot / ICS 192.168.137.x): Windows Firewall silently drops inbound TCP causing phone TIMEOUT. Click 'One-Click Allow Firewall' below."
    )
    val firewallProfilesLabel get() = t("目前網路介面分類：", "Active network profiles:")
    val firewallSafetyNote get() = t(
        "安全說明：僅新增 PhantomCheck 專屬的 TCP 輸入允許規則，絕不停用防火牆或修改其他程式規則。",
        "Safety: Only adds PhantomCheck's dedicated inbound TCP allow rules; never disables Firewall or modifies other rules."
    )
    fun firewallAllowSuccess(port: Int): String = t("已新增 PhantomCheck 防火牆允許規則（TCP $port，全設定檔）", "Added PhantomCheck Firewall allow rule (TCP $port, Profile: Any)")
    fun firewallAllowFailed(err: String): String = t("防火牆設定未完成（可能取消 UAC）：$err", "Firewall update not completed (UAC may have been cancelled): $err")

    // #488 Page Transitions, Surface Depth/Opacity & Custom Background Emergence
    val custTransitionTitle get() = t("頁面切換動畫樣式（支援關閉動畫退場）", "Page Transition Animation Style")
    fun transitionStyleLabel(style: PageTransitionStyle): String = when (style) {
        PageTransitionStyle.FADE -> t("淡入淡出 (Fade)", "Fade")
        PageTransitionStyle.SLIDE_HORIZONTAL -> t("水平滑動 (Slide)", "Horizontal Slide")
        PageTransitionStyle.SLIDE_FADE -> t("滑動＋淡入 (Slide+Fade)", "Slide + Fade")
        PageTransitionStyle.SCALE_FADE -> t("微縮放＋淡入 (Scale+Fade)", "Scale + Fade")
        PageTransitionStyle.NONE -> t("關閉動畫 (Off)", "Off (No Animation)")
    }
    val custSurfaceOpacityTitle get() = t("玻璃卡片不透明度（讓自訂背景浮現）", "Glass Card Opacity (Background Emergence)")
    val custBgScrimTitle get() = t("背景遮罩深淺（數值越低背景越清晰）", "Background Dim / Scrim Depth (Lower = Clearer Background)")
    val custHighContrastTitle get() = t("文字對比強化（高亮度背景時提升可讀性）", "High-Contrast Text (Boost readability over custom backgrounds)")
    val bgPickerBtn get() = t("選擇圖片…", "Choose Image…")
    val bgPickerTitle get() = t("選擇桌面背景圖片", "Choose Desktop Background Image")
    val bgPickerSaved get() = t("已套用並複製背景圖片至本機配置目錄", "Background image applied & copied to config folder")
    fun bgPickerFailed(err: String): String = t("載入背景圖片失敗：$err", "Failed to load background image: $err")
    val bgRemoved get() = t("已移除自訂背景圖並還原漸層", "Custom background removed; gradient restored")

    // #495 Windows Auto-Start Read-back Verification, StartupApproved Detection & Single-Instance
    val autoStartNotWindows get() = t("僅 Windows 支援開機自動啟動機碼設定", "Windows startup registry is only supported on Windows")
    fun autoStartExeNotFound(path: String): String = t("找不到有效的執行檔路徑：$path", "Executable path not found: $path")
    fun autoStartRegFailed(code: Int, detail: String): String = t("登錄檔寫入失敗（exit=$code）：$detail", "Registry update failed (exit=$code): $detail")
    fun autoStartPathInvalid(path: String): String = t("開機啟動路徑失效（檔案不存在）：$path", "Startup target file does not exist: $path")
    val autoStartVerifyFailed get() = t("登錄檔讀回驗證失敗，請確認權限或重試", "Registry read-back verification failed. Please retry.")
    val autoStartVerifiedEnabled get() = t("✓ 已寫入並驗證開機自動啟動（啟動時常駐系統匣）", "✓ Launch at startup enabled and verified (starts in system tray)")
    val autoStartDisabledByTaskMgrTitle get() = t("⚠ 已被 Windows 工作管理員停用開機啟動", "⚠ Disabled in Windows Startup Apps / Task Manager")
    val autoStartDisabledByTaskMgrDesc get() = t(
        "系統登錄檔的 StartupApproved 旗標顯示您曾在工作管理員停用 PhantomCheck 啟動。請開啟 Windows「啟動應用程式」或工作管理員的「啟動」分頁重新啟用。",
        "Windows StartupApproved indicates PhantomCheck was disabled in Task Manager. Open Windows Startup Apps (or Task Manager → Startup) to re-enable it."
    )
    val autoStartOpenTaskMgrBtn get() = t("開啟 Windows 啟動設定", "Open Startup Settings")
    val autoStartRecheckBtn get() = t("重新檢查狀態", "Re-check Status")
    val autoStartPathMismatchDesc get() = t("偵測到舊版安裝路徑的啟動項目，點擊修復以指向目前執行檔。", "Detected stale startup path from a previous install. Click Repair to update it.")
    val autoStartRepairBtn get() = t("立即修復路徑", "Repair Path")

    // #497 Notification Center 4-in-1 (Banner / Corner Toast / Floating Assistant / Tray Menu)
    val notifCenterTitle get() = t("4. 通知中心（橫幅／右下彈窗／懸浮助手／勿擾）", "4. Notification Center (Banner / Toast / Floating Assistant / DND)")
    val enableBanner get() = t("啟用頂部橫幅通知", "Enable Top Banner Notification")
    val bannerDurationLabel get() = t("橫幅停留秒數", "Banner Auto-Dismiss")
    fun durationSecLabel(sec: Int): String = if (sec <= 0) t("不自動收回", "Keep Open") else t("${sec} 秒", "${sec}s")
    val enableToast get() = t("啟用右下角小彈窗（全螢幕自動隱藏）", "Enable Corner Toast (hidden in fullscreen)")
    val toastDurationLabel get() = t("小彈窗停留秒數", "Toast Duration")
    val floatingAssistantTitle get() = t("懸浮助手", "Floating Assistant")
    val enableFloatingAssistant get() = t("啟用桌面懸浮球（可拖曳吸邊＋AI 快問＋氣泡通知）", "Enable Floating Orb (drag & snap, quick AI, message bubbles)")
    val floatingOrbSizeLabel get() = t("懸浮球尺寸", "Orb Size")
    val floatingOrbOpacityLabel get() = t("閒置透明度", "Idle Opacity")
    val floatingBubbleSecLabel get() = t("氣泡停留秒數", "Bubble Duration")
    val floatingBubbleContentLabel get() = t("氣泡顯示內容", "Bubble Content")
    val floatingClickActionLabel get() = t("點擊懸浮球行為", "Orb Click Action")
    val floatingAiHint get() = t("輸入問題給 AI 助手…", "Ask AI Assistant…")

    fun floatingBubbleContentLabel(c: FloatingBubbleContent): String = when (c) {
        FloatingBubbleContent.APP_ONLY -> t("僅 App 名稱", "App Name Only")
        FloatingBubbleContent.SENDER_PREVIEW -> t("寄件人＋摘要", "Sender + Preview")
        FloatingBubbleContent.FULL_PREVIEW -> t("完整摘要", "Full Preview")
    }

    fun floatingClickActionLabel(a: FloatingClickAction): String = when (a) {
        FloatingClickAction.OPEN_AI -> t("展開小型 AI 視窗", "Open Mini AI Chat")
        FloatingClickAction.OPEN_MAIN -> t("開啟主視窗", "Open Main Window")
    }

    // #497 Expanded 8-item System Tray Menu
    fun trayConnStatus(connectedCount: Int, port: Int, running: Boolean): String = when {
        !running -> t("連線狀態：伺服器已停止", "Status: Server Stopped")
        connectedCount > 0 -> t("連線狀態：已連線 $connectedCount 台手機（埠 $port）", "Status: $connectedCount Phone(s) Connected (Port $port)")
        else -> t("連線狀態：等待連線（埠 $port）", "Status: Listening (Port $port)")
    }
    val trayStartServer get() = t("啟動 WSS 伺服器", "Start WSS Server")
    val trayStopServer get() = t("停止 WSS 伺服器", "Stop WSS Server")
    val trayCheckUpdate get() = t("檢查更新", "Check for Updates")
    val trayPauseNotif1h get() = t("暫停通知 1 小時", "Pause Notifications (1 hr)")
    val trayResumeNotif get() = t("恢復通知（目前暫停中）", "Resume Notifications (Paused)")
    fun trayToggleDnd(enabled: Boolean): String =
        if (enabled) t("勿擾模式：開啟（點擊關閉）", "Do Not Disturb: On (Click to Disable)")
        else t("勿擾模式：關閉（點擊開啟）", "Do Not Disturb: Off (Click to Enable)")
    val trayOpenSettings get() = t("開啟設定", "Open Settings")

    // #499 Todos / Notes / AI Assistant full parity
    val todoNotesHint get() = t("備註 / 詳細說明（選填）", "Notes / Details (Optional)")
    val todoPriorityLabel get() = t("優先級", "Priority")
    val todoPriorityLow get() = t("低", "Low")
    val todoPriorityMed get() = t("中", "Medium")
    val todoPriorityHigh get() = t("高", "High")
    val todoDueLabel get() = t("提醒時間", "Reminder Time")
    val todoDue1h get() = t("1 小時後", "In 1 hr")
    val todoDueTonight get() = t("今晚 20:00", "Tonight 20:00")
    val todoDueTomorrow get() = t("明早 09:00", "Tomorrow 09:00")
    val todoDueCustomHint get() = t("自訂 yyyy-MM-dd HH:mm", "Custom yyyy-MM-dd HH:mm")
    val todoRepeatLabel get() = t("重複", "Repeat")
    val todoRepeatNone get() = t("不重複", "None")
    val todoRepeatDaily get() = t("每日", "Daily")
    val todoRepeatWeekly get() = t("每週", "Weekly")
    val todoAddFull get() = t("新增待辦（完整）", "New To-Do")
    val todoOverdue get() = t("已逾期", "Overdue")
    val todoReminderBannerTitle get() = t("待辦到期提醒", "To-Do Reminder")
    val noteEditTitle get() = t("編輯記事", "Edit Note")
    val notePin get() = t("置頂", "Pin")
    val noteUnpin get() = t("取消置頂", "Unpin")
    val noteDelete get() = t("刪除", "Delete")
    val noteColorLabel get() = t("色標", "Color")
    val noteUpdated get() = t("記事已更新", "Note updated")
    val noteDeleted get() = t("記事已刪除", "Note deleted")
    val chatQuoteLatest get() = t("引用最新通知", "Quote Latest Notification")
    val quickPromptUnread get() = t("幫我整理所有未讀訊息重點", "Summarize all unread notifications")

    // #504 Cross-End Parity (Light Mode & Batch Multi-Select)
    val custLightThemeTitle get() = t("淺色模式（亮色玻璃主題）", "Light Mode (Frosted Light Theme)")
    val batchSelectBtn get() = t("批次選取", "Batch Select")
    val batchDoneBtn get() = t("完成選取", "Done")
    val batchMarkReadBtn get() = t("批次已讀", "Mark Selected Read")
    val batchMuteAppsBtn get() = t("批次靜音 App", "Mute Selected Apps")
}
