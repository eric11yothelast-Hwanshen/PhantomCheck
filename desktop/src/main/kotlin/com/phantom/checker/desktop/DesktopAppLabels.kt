package com.phantom.checker.desktop

import androidx.compose.ui.graphics.Color

/**
 * #494 / #498 桌面端共用 App 顯示名稱與色彩映射（優先採用手機端傳來的 appLabel，未連線或舊版手機則以常見套件表 fallback）。
 */
object DesktopAppLabels {

    private data class AppMeta(val zh: String, val en: String, val colorHex: Long)

    private val GENERIC_PACKAGE_SEGMENTS = setOf("com", "org", "net", "android", "app", "apps", "mobile", "client")

    private val knownApps: Map<String, AppMeta> = mapOf(
        "jp.naver.line.android" to AppMeta("LINE", "LINE", 0xFF06C755),
        "com.facebook.orca" to AppMeta("Messenger", "Messenger", 0xFF0084FF),
        "com.facebook.katana" to AppMeta("Facebook", "Facebook", 0xFF1877F2),
        "com.instagram.android" to AppMeta("Instagram", "Instagram", 0xFFE1306C),
        "com.whatsapp" to AppMeta("WhatsApp", "WhatsApp", 0xFF25D366),
        "org.telegram.messenger" to AppMeta("Telegram", "Telegram", 0xFF229ED9),
        "org.telegram.messenger.web" to AppMeta("Telegram", "Telegram", 0xFF229ED9),
        "com.discord" to AppMeta("Discord", "Discord", 0xFF5865F2),
        "com.tencent.mm" to AppMeta("WeChat 微信", "WeChat", 0xFF07C160),
        "com.Slack" to AppMeta("Slack", "Slack", 0xFF4A154B),
        "com.microsoft.teams" to AppMeta("Teams", "Microsoft Teams", 0xFF6264A7),
        "com.google.android.gm" to AppMeta("Gmail", "Gmail", 0xFFEA4335),
        "com.microsoft.office.outlook" to AppMeta("Outlook", "Outlook", 0xFF0078D4),
        "com.google.android.apps.messaging" to AppMeta("Google 訊息", "Google Messages", 0xFF1A73E8),
        "com.samsung.android.messaging" to AppMeta("三星訊息", "Samsung Messages", 0xFF1428A0),
        "com.android.mms" to AppMeta("簡訊", "Messages", 0xFF4285F4),
        "com.miui.mms" to AppMeta("小米簡訊", "Xiaomi Messages", 0xFFFF6900),
        "com.google.android.dialer" to AppMeta("電話", "Phone", 0xFF34A853),
        "com.samsung.android.dialer" to AppMeta("三星電話", "Samsung Phone", 0xFF34A853),
        "com.kakao.talk" to AppMeta("KakaoTalk", "KakaoTalk", 0xFFFEE500),
        "org.thoughtcrime.securesms" to AppMeta("Signal", "Signal", 0xFF3A76F0),
        "com.viber.voip" to AppMeta("Viber", "Viber", 0xFF7360F2),
        "com.twitter.android" to AppMeta("X (Twitter)", "X", 0xFF1DA1F2),
        "com.instagram.barcelona" to AppMeta("Threads", "Threads", 0xFF94A3B8),
        "com.zhiliaoapp.musically" to AppMeta("TikTok", "TikTok", 0xFFEE1D52),
        "com.ss.android.ugc.trill" to AppMeta("TikTok", "TikTok", 0xFFEE1D52),
        "com.google.android.youtube" to AppMeta("YouTube", "YouTube", 0xFFFF0000),
        "com.shopee.tw" to AppMeta("蝦皮購物", "Shopee", 0xFFEE4D2D),
        "com.momo.android" to AppMeta("momo 購物網", "momo", 0xFFD6006E),
        "com.ubercab.eats" to AppMeta("Uber Eats", "Uber Eats", 0xFF06C167),
        "com.global.foodpanda.android" to AppMeta("foodpanda", "foodpanda", 0xFFD70F64),
        "com.google.android.calendar" to AppMeta("Google 日曆", "Google Calendar", 0xFF4285F4),
        "com.samsung.android.calendar" to AppMeta("三星日曆", "Samsung Calendar", 0xFF1428A0),
        "com.google.android.apps.maps" to AppMeta("Google 地圖", "Google Maps", 0xFF34A853),
        "com.android.vending" to AppMeta("Google Play", "Google Play", 0xFF01875F),
        "com.android.systemui" to AppMeta("系統介面", "System UI", 0xFF64748B),
        "android" to AppMeta("Android 系統", "Android System", 0xFF3DDC84),
        "com.xiaomi.market" to AppMeta("小米應用商店", "Xiaomi GetApps", 0xFFFF6900),
        "com.sec.android.app.samsungapps" to AppMeta("Galaxy Store", "Galaxy Store", 0xFF1428A0),
        "com.phantom.checker" to AppMeta("幻訊 PhantomCheck", "PhantomCheck", 0xFF6EA8FE)
    )

    private val fallbackPalette = listOf(
        Color(0xFF6EA8FE),
        Color(0xFF38BDF8),
        Color(0xFF34D399),
        Color(0xFFA78BFA),
        Color(0xFFF472B6),
        Color(0xFFFBBF24),
        Color(0xFFFB7185),
        Color(0xFF2DD4BF)
    )

    fun resolveLabel(packageName: String, dtoLabel: String = ""): String {
        val cleanDto = dtoLabel.trim()
        if (cleanDto.isNotEmpty() && cleanDto != packageName) return cleanDto
        val meta = knownApps[packageName]
        if (meta != null) return DesktopStrings.t(meta.zh, meta.en)
        if (packageName.isBlank()) return DesktopStrings.t("未知應用", "Unknown App")
        val segments = packageName.split('.').filter { it.isNotBlank() && it !in GENERIC_PACKAGE_SEGMENTS }
        val candidate = segments.lastOrNull() ?: packageName.substringAfterLast('.')
        return candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    fun resolveColor(packageName: String, colorHex: String = ""): Color {
        val parsed = parseHexColorOrNull(colorHex)
        if (parsed != null) return parsed
        knownApps[packageName]?.let { return Color(it.colorHex) }
        val idx = (packageName.hashCode() and 0x7FFFFFFF) % fallbackPalette.size
        return fallbackPalette[idx]
    }

    fun parseHexColorOrNull(hex: String): Color? {
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6 && clean.length != 8) return null
        val parsed = clean.toLongOrNull(16) ?: return null
        return if (clean.length == 6) Color(0xFF000000L or parsed) else Color(parsed)
    }

    fun avatarInitial(sender: String, appLabel: String): String {
        val source = sender.trim().ifBlank { appLabel.trim() }
        return source.firstOrNull()?.uppercase() ?: "?"
    }
}
