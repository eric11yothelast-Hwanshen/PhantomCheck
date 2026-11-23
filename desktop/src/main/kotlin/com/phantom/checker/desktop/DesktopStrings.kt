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
    val m1SkeletonNote get() = t(
        "M1 連線骨架就緒（已完成 TLS 指紋驗證、配對碼與 HMAC 重連）。",
        "M1 link skeleton ready (TLS fingerprint pinning, pairing code, and HMAC reconnect active)."
    )
}
