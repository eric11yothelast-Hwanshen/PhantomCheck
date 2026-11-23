package com.phantom.checker.desktop

import java.util.Locale

object DesktopStrings {
    private val isZh: Boolean
        get() = Locale.getDefault().language.startsWith("zh", ignoreCase = true)

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
    val navTodos get() = t("待辦", "Todos")
    val navChat get() = t("AI 助手", "AI Assistant")
    val navRules get() = t("白名單與標籤", "Whitelist & Tags")
    val navSettings get() = t("連線設定", "Connection")

    // Messages
    val filterAll get() = t("全部", "All")
    val filterUnread get() = t("未讀", "Unread")
    val filterStarred get() = t("星號", "Starred")
    val filterScam get() = t("詐騙", "Scam")
    val markAllRead get() = t("全部標已讀", "Mark All Read")
    val markRead get() = t("標已讀", "Mark Read")
    val markReadNote get() = t("僅標記幻訊列表已讀（不清除原 App 未讀）", "Marks read in PhantomCheck list only")
    val replyPlaceholder get() = t("快速回覆內容…", "Quick reply text…")
    val sendReply get() = t("回覆", "Reply")
    val newMessageMirror get() = t("新訊息鏡射：", "New message: ")
    val refresh get() = t("重新整理", "Refresh")

    // Stats
    val statsTotal get() = t("總訊息", "Total")
    val statsToday get() = t("今日訊息", "Today")
    val statsUnread get() = t("未讀數", "Unread")
    val statsScam get() = t("詐騙攔截", "Scam")
    val statsRecentApps get() = t("今日活躍來源 App", "Active Apps Today")

    // Todos
    val todosEmpty get() = t("目前無待辦事項", "No todos")

    // Chat
    val chatInputPlaceholder get() = t("輸入訊息給 AI 夥伴…", "Message AI Assistant…")
    val chatSend get() = t("送出", "Send")
    val chatThinking get() = t("AI 思考中…", "Thinking…")

    // Whitelist & Tags
    val whitelistTitle get() = t("白名單規則", "Whitelist Rules")
    val whitelistSenderHint get() = t("寄件者 / 關鍵字", "Sender / Keyword")
    val whitelistPkgHint get() = t("套件名稱（選填）", "Package name (optional)")
    val add get() = t("新增", "Add")
    val remove get() = t("移除", "Remove")
    val tagsTitle get() = t("標籤管理", "Tags")
    val tagNameHint get() = t("新標籤名稱", "New tag name")
    val notConnectedHint get() = t("請先於「連線設定」配對並連線手機", "Please pair and connect your phone in Connection first")
}
