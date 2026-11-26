package com.phantom.checker.desktop

/**
 * #520 桌面設定中心雙語可搜尋目錄（涵蓋既有 26 項設定與 #513/#514/#515/#518/#520 新增設定，支援中英關鍵字即時跳轉）。
 */
data class DesktopSettingEntry(
    val id: String,
    val subTab: String, // CONNECTION | SYSTEM | CUSTOMIZE | ABOUT
    val titleZh: String,
    val titleEn: String,
    val keywords: String
)

object DesktopSettingsCatalog {
    val entries: List<DesktopSettingEntry> = listOf(
        // CONNECTION (5)
        DesktopSettingEntry("server_port", "CONNECTION", "伺服器埠號與連線狀態", "Server Port & Status", "port wss server mdns lan ip fingerprint 埠號 伺服器 連線"),
        DesktopSettingEntry("firewall", "CONNECTION", "Windows 防火牆檢查與允許", "Windows Firewall Check", "firewall 防火牆 規則 allow"),
        DesktopSettingEntry("qr_pair", "CONNECTION", "配對新手機（QR Code／配對碼）", "Pair New Phone (QR / Code)", "qr pair code 配對 掃碼"),
        DesktopSettingEntry("paired_phones", "CONNECTION", "已配對手機清單與解除", "Paired Phones & Unpair", "paired device revoke unpair 已配對 解除"),
        DesktopSettingEntry("auto_connect", "CONNECTION", "自動連線與伺服器自啟", "Auto-Connect & Server Startup", "auto connect reconnect 自動 連線"),
        // SYSTEM (11)
        DesktopSettingEntry("language", "SYSTEM", "介面語言（繁體中文／English）", "Interface Language", "language zh en 中文 英文 語言"),
        DesktopSettingEntry("autostart", "SYSTEM", "Windows 開機自動啟動", "Start on Windows Login", "autostart boot startup 開機 自啟"),
        DesktopSettingEntry("close_behavior", "SYSTEM", "關閉視窗行為（縮至系統匣）", "Close Behavior (Minimize to Tray)", "tray minimize exit 關閉 系統匣"),
        DesktopSettingEntry("banner_settings", "SYSTEM", "頂部訊息橫幅開關與秒數", "Top Message Banner & Duration", "banner top 橫幅 秒數"),
        DesktopSettingEntry("toast_custom", "SYSTEM", "右下角彈窗尺寸／內容／背景／顏色／音量／頻率", "Corner Toast Size / Content / Sound / Frequency", "toast popup size color sound frequency volume test 彈窗 音量 頻率 測試通知"),
        DesktopSettingEntry("mirror_native_notif", "SYSTEM", "鏡像模式／原生系統通知／通知音效", "Mirror Mode / Native Notification / Sound", "mirror native sound 鏡像 原生 通知音"),
        DesktopSettingEntry("floating_orb", "SYSTEM", "懸浮助手球（大小／透明度／吸附／懸停動作／半圓選單／全螢幕隱藏）", "Floating Orb (Size / Snap / Hover / Radial / Fullscreen)", "orb floating snap hover action radial fullscreen 懸浮球 吸附 懸停 半圓 全螢幕"),
        DesktopSettingEntry("dnd_schedule", "SYSTEM", "勿擾模式與排程時段", "Do Not Disturb & Quiet Hours", "dnd quiet hours 勿擾 靜音 時段"),
        DesktopSettingEntry("backoff_ping", "SYSTEM", "重連退避與 Ping 心跳間隔", "Reconnect Backoff & Ping Interval", "backoff ping interval 重連 間隔 心跳"),
        DesktopSettingEntry("time_preview", "SYSTEM", "24 小時制時間與預覽長度", "24-Hour Time & Preview Length", "time 24h preview length 時間 預覽 長度"),
        DesktopSettingEntry("diag_reset", "SYSTEM", "診斷匯出／開啟日誌資料夾／重設設定", "Diagnostics / Log Folder / Reset Settings", "log diagnostic export reset 診斷 日誌 匯出 重設"),
        // CUSTOMIZE (8)
        DesktopSettingEntry("accent_color", "CUSTOMIZE", "強調色預設與自訂 HEX", "Accent Color & Custom HEX", "accent color hex 強調色 顏色"),
        DesktopSettingEntry("light_hc", "CUSTOMIZE", "淺色主題與高對比度文字", "Light Theme & High Contrast Text", "light dark theme high contrast 淺色 深色 高對比"),
        DesktopSettingEntry("anim_mode", "CUSTOMIZE", "全域動畫模式（平衡／省電）", "Global Animation Mode (Balanced / Power-saver)", "animation motion power saver balanced 動畫 動效 省電 平衡"),
        DesktopSettingEntry("page_transition", "CUSTOMIZE", "頁面轉場樣式", "Page Transition Style", "transition slide fade scale 轉場 樣式"),
        DesktopSettingEntry("density_font", "CUSTOMIZE", "介面密度／全域字級／聊天字級", "UI Density / Font Scale / Chat Font Size", "density font scale chat size 密度 字級 聊天"),
        DesktopSettingEntry("card_glass", "CUSTOMIZE", "卡片樣式／圓角／毛玻璃等級", "Card Style / Corner Radius / Glass Level", "card style corner glass blur 卡片 圓角 毛玻璃"),
        DesktopSettingEntry("messages_view_mode", "CUSTOMIZE", "訊息列表檢視模式（對話／依 App 分組）", "Messages View Mode (Conversations / Group by App)", "messages view mode conversation group app 訊息 檢視 分組"),
        DesktopSettingEntry("background_scrim", "CUSTOMIZE", "背景主題／自訂背景圖／表面不透明度／遮罩", "Background Theme / Custom Image / Opacity / Scrim", "background wallpaper opacity scrim 背景 桌布 透明度 遮罩"),
        DesktopSettingEntry("sidebar_startup", "CUSTOMIZE", "側欄寬度／收合與預設起始頁", "Sidebar Width / Collapse & Default Startup Tab", "sidebar collapse startup tab home 側欄 收合 起始頁 總覽"),
        // ABOUT (2)
        DesktopSettingEntry("check_updates", "ABOUT", "檢查桌面端更新與近期版本說明", "Check Updates & Release Notes", "update release version 更新 版本"),
        DesktopSettingEntry("about_links", "ABOUT", "關於 PhantomCheck 與隱私權／授權條款", "About PhantomCheck, Privacy & License", "about version license privacy repo 關於 版本 隱私 授權")
    )

    fun search(query: String): List<DesktopSettingEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return emptyList()
        return entries.filter { e ->
            e.titleZh.lowercase().contains(q) ||
                e.titleEn.lowercase().contains(q) ||
                e.keywords.lowercase().contains(q)
        }
    }
}
