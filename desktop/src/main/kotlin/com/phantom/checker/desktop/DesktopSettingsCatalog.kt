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
        DesktopSettingEntry("server_port", "CONNECTION", "伺服器埠號與連線狀態", "Server Port & Status", "port wss server mdns lan ip fingerprint 埠號 伺服器 連線"),
        DesktopSettingEntry("firewall", "CONNECTION", "Windows 防火牆檢查與允許", "Windows Firewall Check", "firewall 防火牆 規則 allow"),
        DesktopSettingEntry("qr_pair", "CONNECTION", "配對新手機（QR Code／配對碼）", "Pair New Phone (QR / Code)", "qr pair code 配對 掃碼"),
        DesktopSettingEntry("paired_phones", "CONNECTION", "已配對手機清單", "Paired Phones List", "paired device revoke unpair 已配對 解除"),
        DesktopSettingEntry("language", "SYSTEM", "介面語言", "Interface Language", "language zh en 中文 英文 語言"),
        DesktopSettingEntry("autostart", "SYSTEM", "Windows 開機自動啟動", "Start on Windows Login", "autostart boot startup 開機 自啟"),
        DesktopSettingEntry("close_behavior", "SYSTEM", "關閉視窗行為（縮至系統匣）", "Close Behavior (Tray)", "tray minimize exit 關閉 系統匣"),
        DesktopSettingEntry("banner_toast", "SYSTEM", "訊息橫幅與右下角彈窗自訂", "Banner & Toast Customization", "toast banner size color sound frequency volume 彈窗 橫幅 音量 頻率 測試通知"),
        DesktopSettingEntry("floating_orb", "SYSTEM", "懸浮助手球（吸附／半圓選單／全螢幕隱藏）", "Floating Assistant Orb", "orb floating snap radial fullscreen 懸浮球 吸附 半圓 全螢幕"),
        DesktopSettingEntry("dnd_schedule", "SYSTEM", "勿擾模式與排程", "Do Not Disturb & Schedule", "dnd quiet hours 勿擾 靜音 時段"),
        DesktopSettingEntry("backoff_ping", "SYSTEM", "重連退避／Ping 間隔／預覽長度", "Reconnect Backoff / Ping / Preview", "backoff ping preview 重連 間隔 預覽"),
        DesktopSettingEntry("diag_update", "SYSTEM", "診斷匯出／手動檢查更新／重設設定", "Diagnostics / Updates / Reset", "log diagnostic export update reset 診斷 更新 重設"),
        DesktopSettingEntry("accent_color", "CUSTOMIZE", "強調色與自訂 HEX", "Accent Color & Custom HEX", "accent color hex 強調色 顏色"),
        DesktopSettingEntry("light_hc", "CUSTOMIZE", "淺色模式與高對比文字", "Light Mode & High Contrast", "light dark theme high contrast 淺色 深色 高對比"),
        DesktopSettingEntry("anim_mode", "CUSTOMIZE", "全域動畫模式（平衡／省電）", "Global Animation Mode (Balanced / Power-saver)", "animation motion power saver balanced 動畫 動效 省電 平衡 轉場"),
        DesktopSettingEntry("density_font", "CUSTOMIZE", "介面密度／字級／卡片圓角／毛玻璃", "Density / Font Scale / Corner / Glass", "density font scale corner blur 密度 字級 圓角 毛玻璃"),
        DesktopSettingEntry("sidebar_layout", "CUSTOMIZE", "側欄寬度／收合／預設起始頁", "Sidebar Width / Collapse / Startup Tab", "sidebar collapse startup tab home 側欄 收合 起始頁 總覽"),
        DesktopSettingEntry("about_links", "ABOUT", "版本資訊與隱私權政策", "About & Privacy Policy", "about version license privacy 關於 版本 隱私")
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
