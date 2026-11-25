package com.phantom.checker.desktop

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Properties

enum class CloseBehavior { MINIMIZE_TO_TRAY, EXIT_APP }
enum class AccentPreset(val key: String) { PURPLE("purple"), BLUE("blue"), GREEN("green"), ORANGE("orange"), PINK("pink"), CYAN("cyan") }
enum class UiDensity(val key: String) { COMPACT("compact"), STANDARD("standard"), COMFORTABLE("comfortable") }
enum class MessageCardStyle(val key: String) { CARD("card"), COMPACT_LIST("compact_list") }
enum class MirrorDisplayMode(val key: String) { BANNER("banner"), TOAST("toast"), NATIVE("native"), BOTH("both"), COMBO("combo") }
enum class FloatingBubbleContent(val key: String) { APP_ONLY("app_only"), SENDER_PREVIEW("sender_preview"), FULL_PREVIEW("full_preview") }
enum class FloatingClickAction(val key: String) { OPEN_AI("open_ai"), OPEN_MAIN("open_main") }
enum class BgThemePreset(val key: String) { MIDNIGHT("midnight"), AURORA("aurora"), OBSIDIAN("obsidian"), DEEP_OCEAN("deep_ocean") }
enum class GlassBlurLevel(val key: String) { LOW("low"), MEDIUM("medium"), HIGH("high") }
enum class ChatFontSize(val key: String, val multiplier: Float) { SMALL("small", 0.9f), STANDARD("standard", 1.0f), LARGE("large", 1.18f) }
enum class PageTransitionStyle(val key: String) {
    FADE("fade"),
    SLIDE_HORIZONTAL("slide_horizontal"),
    SLIDE_FADE("slide_fade"),
    SCALE_FADE("scale_fade"),
    NONE("none")
}
enum class FullscreenHideMode(val key: String) {
    AUTO_HIDE("auto_hide"),
    NEVER_HIDE("never_hide"),
    HIDE_WITH_HINT("hide_with_hint")
}

/** #520 全域動畫模式（平衡＝完整 draw-phase 動效；省電＝瞬切／短 fade、零無限迴圈） */
enum class AnimationMode(val key: String) {
    BALANCED("balanced"),
    POWER_SAVER("power_saver")
}

/**
 * #468 / #479 / #488 / #497 桌機端設定資料模型（10 系統設定＋16 既有自訂設定＋#488 轉場與深淺＋#497 通知中心四合一＋視窗位置記憶）。
 */
data class DesktopSettings(
    // 10 System settings
    val language: String = "auto", // auto | zh-TW | en
    val autoStart: Boolean = false,
    val closeBehavior: CloseBehavior = CloseBehavior.MINIMIZE_TO_TRAY,
    val nativeNotifications: Boolean = true,
    val dndEnabled: Boolean = true,
    val dndStartHour: Int = 0,
    val dndEndHour: Int = 7,
    val maxReconnectBackoffSec: Int = 30, // 15 | 30 | 60
    val pingIntervalSec: Int = 15, // 10 | 15 | 30
    val previewLength: Int = 200, // 90 | 200 | 500
    val serverPort: Int = com.phantom.checker.shared.LinkProtocol.DEFAULT_PORT,
    // 16 Custom settings (original 8 + #479 new 8 — keys preserved for 0.4.0 upgrade compatibility)
    val accentPreset: AccentPreset = AccentPreset.BLUE,
    val customAccentHex: String = "", // #RRGGBB (optional override)
    val density: UiDensity = UiDensity.STANDARD,
    val fontScale: Float = 1.0f, // 0.9 | 1.0 | 1.1 | 1.25
    val sidebarWidthDp: Int = 244, // 210 | 244 | 280
    val sidebarCollapsed: Boolean = false,
    val messageCardStyle: MessageCardStyle = MessageCardStyle.CARD,
    val mirrorMode: MirrorDisplayMode = MirrorDisplayMode.COMBO,
    val bgTheme: BgThemePreset = BgThemePreset.MIDNIGHT,
    val customBgImagePath: String = "",
    val animationsEnabled: Boolean = true,
    val use24HourTime: Boolean = true,
    val messageCardCornerDp: Int = 16, // 12 | 16 | 20 | 24
    val glassBlurLevel: GlassBlurLevel = GlassBlurLevel.MEDIUM,
    val chatFontSize: ChatFontSize = ChatFontSize.STANDARD,
    val notificationSoundEnabled: Boolean = true,
    val autoConnectOnStartup: Boolean = true,
    val defaultStartupTab: String = "MESSAGES", // MESSAGES | STATS | TODOS | CHAT | RULES | SETTINGS
    // #488 Page transition & surface depth / background emergence settings
    val pageTransitionStyle: PageTransitionStyle = PageTransitionStyle.SLIDE_FADE,
    val surfaceOpacity: Float = 0.74f, // 0.55f..0.96f (default matches existing MEDIUM glass alpha)
    val bgScrimAlpha: Float = 0.56f, // 0.15f..0.85f (controls custom background emergence)
    val highContrastText: Boolean = false,
    // #497 Notification Center 4-in-1 (banner auto-dismiss, bottom-right toast, floating assistant orb)
    val bannerEnabled: Boolean = true,
    val bannerDurationSec: Int = 5, // 3 | 5 | 8 | 0 (never)
    val toastEnabled: Boolean = true,
    val toastDurationSec: Int = 5, // 3 | 5 | 8
    val floatingAssistantEnabled: Boolean = false,
    val floatingOrbSizeDp: Int = 36, // 28 | 36 | 48
    val floatingOrbOpacity: Float = 0.72f, // 0.45 | 0.72 | 0.95
    val floatingBubbleSec: Int = 5, // 3 | 5 | 8
    val floatingBubbleContent: FloatingBubbleContent = FloatingBubbleContent.SENDER_PREVIEW,
    val floatingClickAction: FloatingClickAction = FloatingClickAction.OPEN_AI,
    val floatingOrbX: Int = Int.MIN_VALUE,
    val floatingOrbY: Int = Int.MIN_VALUE,
    // #501 Filters & Focus collapsible section persistence
    val rulesCollapseAllowlist: Boolean = false,
    val rulesCollapseTags: Boolean = false,
    val rulesCollapseMuted: Boolean = false,
    // #504 Light/Dark theme mode toggle
    val lightTheme: Boolean = false,
    // #513 Toast customization, frequency control & one-click DND (all additive, preserving existing keys)
    val quickDndActive: Boolean = false,
    val toastWidthDp: Int = 360, // 280..480
    val toastHeightDp: Int = 112, // 84..180
    val toastShowTitle: Boolean = true,
    val toastMaxLines: Int = 2, // 1..4
    val toastShowAppName: Boolean = true,
    val toastShowTime: Boolean = true,
    val toastBgImagePath: String = "",
    val toastTextColorHex: String = "",
    val toastSoundCustomWavPath: String = "",
    val toastSoundVolume: Float = 0.8f, // 0.0..1.0
    val toastMaxPerMinute: Int = 0, // 0 = unlimited, 1..20
    val toastAggregateMinutes: Int = 0, // 0 = instant, 1..15
    val toastOnlyStarred: Boolean = false,
    val floatingMaxPerMinute: Int = 0, // 0 = unlimited, 1..20
    val floatingOnlyStarred: Boolean = false,
    // #514 Floating orb edge-snap toggle
    val floatingSnapToEdge: Boolean = true,
    // #515 Floating orb radial menu order & fullscreen auto-hide mode
    val floatingFullscreenMode: FullscreenHideMode = FullscreenHideMode.AUTO_HIDE,
    val floatingMenuOrder: String = "ai,main,search,todos,dnd,note,clipboard,settings",
    // #518 Cross-end clipboard sync (opt-in, default OFF)
    val clipboardSyncEnabled: Boolean = false,
    val clipboardMaxChars: Int = 5000,
    val clipboardOtpGuard: Boolean = true,
    val clipboardOnlyWhenFocused: Boolean = true,
    // #520 Global Animation Mode (migrated from animationsEnabled; dual-written for downgrade compatibility)
    val animationMode: AnimationMode = if (animationsEnabled) AnimationMode.BALANCED else AnimationMode.POWER_SAVER,
    // Window geometry persistence
    val windowX: Int = Int.MIN_VALUE,
    val windowY: Int = Int.MIN_VALUE,
    val windowWidth: Int = 1280,
    val windowHeight: Int = 820
) {
    fun isInDndWindow(cal: Calendar = Calendar.getInstance()): Boolean {
        if (!dndEnabled) return false
        val h = cal.get(Calendar.HOUR_OF_DAY)
        return if (dndStartHour <= dndEndHour) {
            h in dndStartHour until dndEndHour
        } else {
            h >= dndStartHour || h < dndEndHour
        }
    }

    fun isEffectiveDndNow(cal: Calendar = Calendar.getInstance()): Boolean =
        quickDndActive || isInDndWindow(cal)
}

data class DesktopUpdateCheckResult(
    val ok: Boolean,
    val latestTag: String = "",
    val htmlUrl: String = "",
    val errorMessage: String = ""
)

object DesktopSettingsStore {

    const val CURRENT_DESKTOP_VERSION = "0.6.0"
    private const val WIN_RUN_REG_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run"
    private const val WIN_RUN_VALUE_NAME = "PhantomCheckDesktop"
    private val validStartupTabs = setOf("MESSAGES", "STATS", "TODOS", "CHAT", "RULES", "SETTINGS", "HOME", "REPORTS", "TOOLS")

    private val _state = MutableStateFlow(loadFromDisk())
    val state: StateFlow<DesktopSettings> = _state.asStateFlow()

    private fun loadFromDisk(): DesktopSettings {
        val file = DesktopPaths.settingsPropertiesFile
        if (!file.exists()) return DesktopSettings()
        val p = Properties()
        runCatching { file.bufferedReader(Charsets.UTF_8).use { p.load(it) } }
            .onFailure {
                runCatching { file.inputStream().use { p.load(it) } }
            }
        return DesktopSettings(
            language = p.getProperty("language", "auto"),
            autoStart = p.getProperty("autoStart", "false").toBoolean(),
            closeBehavior = runCatching { CloseBehavior.valueOf(p.getProperty("closeBehavior", "MINIMIZE_TO_TRAY")) }.getOrDefault(CloseBehavior.MINIMIZE_TO_TRAY),
            nativeNotifications = p.getProperty("nativeNotifications", "true").toBoolean(),
            dndEnabled = p.getProperty("dndEnabled", "true").toBoolean(),
            dndStartHour = p.getProperty("dndStartHour", "0").toIntOrNull()?.coerceIn(0, 23) ?: 0,
            dndEndHour = p.getProperty("dndEndHour", "7").toIntOrNull()?.coerceIn(0, 23) ?: 7,
            maxReconnectBackoffSec = p.getProperty("maxReconnectBackoffSec", "30").toIntOrNull()?.takeIf { it in listOf(15, 30, 60) } ?: 30,
            pingIntervalSec = p.getProperty("pingIntervalSec", "15").toIntOrNull()?.takeIf { it in listOf(10, 15, 30, 60) } ?: 15,
            previewLength = p.getProperty("previewLength", "200").toIntOrNull()?.takeIf { it in listOf(90, 200, 500) } ?: 200,
            serverPort = p.getProperty("serverPort", com.phantom.checker.shared.LinkProtocol.DEFAULT_PORT.toString()).toIntOrNull()?.coerceIn(1024, 65535) ?: com.phantom.checker.shared.LinkProtocol.DEFAULT_PORT,
            accentPreset = AccentPreset.entries.firstOrNull { it.key == p.getProperty("accentPreset", "blue") } ?: AccentPreset.BLUE,
            customAccentHex = p.getProperty("customAccentHex", "").trim(),
            density = UiDensity.entries.firstOrNull { it.key == p.getProperty("density", "standard") } ?: UiDensity.STANDARD,
            fontScale = p.getProperty("fontScale", "1.0").toFloatOrNull()?.coerceIn(0.9f, 1.25f) ?: 1.0f,
            sidebarWidthDp = p.getProperty("sidebarWidthDp", "244").toIntOrNull()?.coerceIn(190, 320) ?: 244,
            sidebarCollapsed = p.getProperty("sidebarCollapsed", "false").toBoolean(),
            messageCardStyle = MessageCardStyle.entries.firstOrNull { it.key == p.getProperty("messageCardStyle", "card") } ?: MessageCardStyle.CARD,
            mirrorMode = MirrorDisplayMode.entries.firstOrNull { it.key == p.getProperty("mirrorMode", "both") } ?: MirrorDisplayMode.BOTH,
            bgTheme = BgThemePreset.entries.firstOrNull { it.key == p.getProperty("bgTheme", "midnight") } ?: BgThemePreset.MIDNIGHT,
            customBgImagePath = p.getProperty("customBgImagePath", ""),
            animationsEnabled = p.getProperty("animationsEnabled", "true").toBoolean(),
            use24HourTime = p.getProperty("use24HourTime", "true").toBoolean(),
            messageCardCornerDp = p.getProperty("messageCardCornerDp", "16").toIntOrNull()?.takeIf { it in listOf(12, 16, 20, 24) } ?: 16,
            glassBlurLevel = GlassBlurLevel.entries.firstOrNull { it.key == p.getProperty("glassBlurLevel", "medium") } ?: GlassBlurLevel.MEDIUM,
            chatFontSize = ChatFontSize.entries.firstOrNull { it.key == p.getProperty("chatFontSize", "standard") } ?: ChatFontSize.STANDARD,
            notificationSoundEnabled = p.getProperty("notificationSoundEnabled", "true").toBoolean(),
            autoConnectOnStartup = p.getProperty("autoConnectOnStartup", "true").toBoolean(),
            defaultStartupTab = p.getProperty("defaultStartupTab", "MESSAGES").takeIf { it in validStartupTabs } ?: "MESSAGES",
            pageTransitionStyle = PageTransitionStyle.entries.firstOrNull { it.key == p.getProperty("pageTransitionStyle", "slide_fade") } ?: PageTransitionStyle.SLIDE_FADE,
            surfaceOpacity = p.getProperty("surfaceOpacity", "0.74").toFloatOrNull()?.coerceIn(0.55f, 0.96f) ?: 0.74f,
            bgScrimAlpha = p.getProperty("bgScrimAlpha", "0.56").toFloatOrNull()?.coerceIn(0.15f, 0.85f) ?: 0.56f,
            highContrastText = p.getProperty("highContrastText", "false").toBoolean(),
            bannerEnabled = p.getProperty("bannerEnabled", "true").toBoolean(),
            bannerDurationSec = p.getProperty("bannerDurationSec", "5").toIntOrNull()?.takeIf { it in listOf(0, 3, 5, 8) } ?: 5,
            toastEnabled = p.getProperty("toastEnabled", "true").toBoolean(),
            toastDurationSec = p.getProperty("toastDurationSec", "5").toIntOrNull()?.takeIf { it in listOf(3, 5, 8) } ?: 5,
            floatingAssistantEnabled = p.getProperty("floatingAssistantEnabled", "false").toBoolean(),
            floatingOrbSizeDp = p.getProperty("floatingOrbSizeDp", "36").toIntOrNull()?.takeIf { it in listOf(28, 36, 48) } ?: 36,
            floatingOrbOpacity = p.getProperty("floatingOrbOpacity", "0.72").toFloatOrNull()?.coerceIn(0.35f, 1.0f) ?: 0.72f,
            floatingBubbleSec = p.getProperty("floatingBubbleSec", "5").toIntOrNull()?.takeIf { it in listOf(3, 5, 8) } ?: 5,
            floatingBubbleContent = FloatingBubbleContent.entries.firstOrNull { it.key == p.getProperty("floatingBubbleContent", "sender_preview") } ?: FloatingBubbleContent.SENDER_PREVIEW,
            floatingClickAction = FloatingClickAction.entries.firstOrNull { it.key == p.getProperty("floatingClickAction", "open_ai") } ?: FloatingClickAction.OPEN_AI,
            floatingOrbX = p.getProperty("floatingOrbX", Int.MIN_VALUE.toString()).toIntOrNull() ?: Int.MIN_VALUE,
            floatingOrbY = p.getProperty("floatingOrbY", Int.MIN_VALUE.toString()).toIntOrNull() ?: Int.MIN_VALUE,
            rulesCollapseAllowlist = p.getProperty("rulesCollapseAllowlist", "false").toBoolean(),
            rulesCollapseTags = p.getProperty("rulesCollapseTags", "false").toBoolean(),
            rulesCollapseMuted = p.getProperty("rulesCollapseMuted", "false").toBoolean(),
            lightTheme = p.getProperty("lightTheme", "false").toBoolean(),
            quickDndActive = p.getProperty("quickDndActive", "false").toBoolean(),
            toastWidthDp = p.getProperty("toastWidthDp", "360").toIntOrNull()?.coerceIn(280, 480) ?: 360,
            toastHeightDp = p.getProperty("toastHeightDp", "112").toIntOrNull()?.coerceIn(84, 180) ?: 112,
            toastShowTitle = p.getProperty("toastShowTitle", "true").toBoolean(),
            toastMaxLines = p.getProperty("toastMaxLines", "2").toIntOrNull()?.coerceIn(1, 4) ?: 2,
            toastShowAppName = p.getProperty("toastShowAppName", "true").toBoolean(),
            toastShowTime = p.getProperty("toastShowTime", "true").toBoolean(),
            toastBgImagePath = p.getProperty("toastBgImagePath", ""),
            toastTextColorHex = p.getProperty("toastTextColorHex", "").trim(),
            toastSoundCustomWavPath = p.getProperty("toastSoundCustomWavPath", ""),
            toastSoundVolume = p.getProperty("toastSoundVolume", "0.8").toFloatOrNull()?.coerceIn(0f, 1f) ?: 0.8f,
            toastMaxPerMinute = p.getProperty("toastMaxPerMinute", "0").toIntOrNull()?.coerceIn(0, 20) ?: 0,
            toastAggregateMinutes = p.getProperty("toastAggregateMinutes", "0").toIntOrNull()?.coerceIn(0, 15) ?: 0,
            toastOnlyStarred = p.getProperty("toastOnlyStarred", "false").toBoolean(),
            floatingMaxPerMinute = p.getProperty("floatingMaxPerMinute", "0").toIntOrNull()?.coerceIn(0, 20) ?: 0,
            floatingOnlyStarred = p.getProperty("floatingOnlyStarred", "false").toBoolean(),
            floatingSnapToEdge = p.getProperty("floatingSnapToEdge", "true").toBoolean(),
            floatingFullscreenMode = FullscreenHideMode.entries.firstOrNull { it.key == p.getProperty("floatingFullscreenMode", "auto_hide") } ?: FullscreenHideMode.AUTO_HIDE,
            floatingMenuOrder = p.getProperty("floatingMenuOrder", "ai,main,search,todos,dnd,note,clipboard,settings").ifBlank { "ai,main,search,todos,dnd,note,clipboard,settings" },
            clipboardSyncEnabled = p.getProperty("clipboardSyncEnabled", "false").toBoolean(),
            clipboardMaxChars = p.getProperty("clipboardMaxChars", "5000").toIntOrNull()?.coerceIn(500, 5000) ?: 5000,
            clipboardOtpGuard = p.getProperty("clipboardOtpGuard", "true").toBoolean(),
            clipboardOnlyWhenFocused = p.getProperty("clipboardOnlyWhenFocused", "true").toBoolean(),
            animationMode = run {
                val rawMode = p.getProperty("animationMode")
                if (!rawMode.isNullOrBlank()) {
                    AnimationMode.entries.firstOrNull { it.key == rawMode } ?: AnimationMode.BALANCED
                } else {
                    val legacyAnim = p.getProperty("animationsEnabled", "true").toBoolean()
                    if (legacyAnim) AnimationMode.BALANCED else AnimationMode.POWER_SAVER
                }
            },
            windowX = p.getProperty("windowX", Int.MIN_VALUE.toString()).toIntOrNull() ?: Int.MIN_VALUE,
            windowY = p.getProperty("windowY", Int.MIN_VALUE.toString()).toIntOrNull() ?: Int.MIN_VALUE,
            windowWidth = p.getProperty("windowWidth", "1280").toIntOrNull()?.coerceAtLeast(880) ?: 1280,
            windowHeight = p.getProperty("windowHeight", "820").toIntOrNull()?.coerceAtLeast(560) ?: 820
        )
    }

    @Synchronized
    fun update(transform: (DesktopSettings) -> DesktopSettings) {
        val rawNext = transform(_state.value)
        // Dual-sync animationMode <-> animationsEnabled so legacy callers & new callers stay consistent
        val prev = _state.value
        val next = when {
            rawNext.animationMode != prev.animationMode ->
                rawNext.copy(animationsEnabled = rawNext.animationMode == AnimationMode.BALANCED)
            rawNext.animationsEnabled != prev.animationsEnabled ->
                rawNext.copy(animationMode = if (rawNext.animationsEnabled) AnimationMode.BALANCED else AnimationMode.POWER_SAVER)
            else -> rawNext
        }
        _state.value = next
        DesktopStrings.syncLanguage(next.language)
        saveToDisk(next)
    }

    private fun saveToDisk(s: DesktopSettings) {
        runCatching {
            val file = DesktopPaths.settingsPropertiesFile
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            val p = Properties()
            p.setProperty("language", s.language)
            p.setProperty("autoStart", s.autoStart.toString())
            p.setProperty("closeBehavior", s.closeBehavior.name)
            p.setProperty("nativeNotifications", s.nativeNotifications.toString())
            p.setProperty("dndEnabled", s.dndEnabled.toString())
            p.setProperty("dndStartHour", s.dndStartHour.toString())
            p.setProperty("dndEndHour", s.dndEndHour.toString())
            p.setProperty("maxReconnectBackoffSec", s.maxReconnectBackoffSec.toString())
            p.setProperty("pingIntervalSec", s.pingIntervalSec.toString())
            p.setProperty("previewLength", s.previewLength.toString())
            p.setProperty("serverPort", s.serverPort.toString())
            p.setProperty("accentPreset", s.accentPreset.key)
            p.setProperty("customAccentHex", s.customAccentHex)
            p.setProperty("density", s.density.key)
            p.setProperty("fontScale", s.fontScale.toString())
            p.setProperty("sidebarWidthDp", s.sidebarWidthDp.toString())
            p.setProperty("sidebarCollapsed", s.sidebarCollapsed.toString())
            p.setProperty("messageCardStyle", s.messageCardStyle.key)
            p.setProperty("mirrorMode", s.mirrorMode.key)
            p.setProperty("bgTheme", s.bgTheme.key)
            p.setProperty("customBgImagePath", s.customBgImagePath)
            p.setProperty("animationsEnabled", (s.animationMode == AnimationMode.BALANCED).toString())
            p.setProperty("animationMode", s.animationMode.key)
            p.setProperty("use24HourTime", s.use24HourTime.toString())
            p.setProperty("messageCardCornerDp", s.messageCardCornerDp.toString())
            p.setProperty("glassBlurLevel", s.glassBlurLevel.key)
            p.setProperty("chatFontSize", s.chatFontSize.key)
            p.setProperty("notificationSoundEnabled", s.notificationSoundEnabled.toString())
            p.setProperty("autoConnectOnStartup", s.autoConnectOnStartup.toString())
            p.setProperty("defaultStartupTab", s.defaultStartupTab)
            p.setProperty("pageTransitionStyle", s.pageTransitionStyle.key)
            p.setProperty("surfaceOpacity", "%.2f".format(java.util.Locale.US, s.surfaceOpacity.coerceIn(0.55f, 0.96f)))
            p.setProperty("bgScrimAlpha", "%.2f".format(java.util.Locale.US, s.bgScrimAlpha.coerceIn(0.15f, 0.85f)))
            p.setProperty("highContrastText", s.highContrastText.toString())
            p.setProperty("bannerEnabled", s.bannerEnabled.toString())
            p.setProperty("bannerDurationSec", s.bannerDurationSec.toString())
            p.setProperty("toastEnabled", s.toastEnabled.toString())
            p.setProperty("toastDurationSec", s.toastDurationSec.toString())
            p.setProperty("floatingAssistantEnabled", s.floatingAssistantEnabled.toString())
            p.setProperty("floatingOrbSizeDp", s.floatingOrbSizeDp.toString())
            p.setProperty("floatingOrbOpacity", "%.2f".format(java.util.Locale.US, s.floatingOrbOpacity.coerceIn(0.35f, 1.0f)))
            p.setProperty("floatingBubbleSec", s.floatingBubbleSec.toString())
            p.setProperty("floatingBubbleContent", s.floatingBubbleContent.key)
            p.setProperty("floatingClickAction", s.floatingClickAction.key)
            p.setProperty("floatingOrbX", s.floatingOrbX.toString())
            p.setProperty("floatingOrbY", s.floatingOrbY.toString())
            p.setProperty("rulesCollapseAllowlist", s.rulesCollapseAllowlist.toString())
            p.setProperty("rulesCollapseTags", s.rulesCollapseTags.toString())
            p.setProperty("rulesCollapseMuted", s.rulesCollapseMuted.toString())
            p.setProperty("lightTheme", s.lightTheme.toString())
            p.setProperty("quickDndActive", s.quickDndActive.toString())
            p.setProperty("toastWidthDp", s.toastWidthDp.toString())
            p.setProperty("toastHeightDp", s.toastHeightDp.toString())
            p.setProperty("toastShowTitle", s.toastShowTitle.toString())
            p.setProperty("toastMaxLines", s.toastMaxLines.toString())
            p.setProperty("toastShowAppName", s.toastShowAppName.toString())
            p.setProperty("toastShowTime", s.toastShowTime.toString())
            p.setProperty("toastBgImagePath", s.toastBgImagePath)
            p.setProperty("toastTextColorHex", s.toastTextColorHex)
            p.setProperty("toastSoundCustomWavPath", s.toastSoundCustomWavPath)
            p.setProperty("toastSoundVolume", "%.2f".format(java.util.Locale.US, s.toastSoundVolume.coerceIn(0f, 1f)))
            p.setProperty("toastMaxPerMinute", s.toastMaxPerMinute.toString())
            p.setProperty("toastAggregateMinutes", s.toastAggregateMinutes.toString())
            p.setProperty("toastOnlyStarred", s.toastOnlyStarred.toString())
            p.setProperty("floatingMaxPerMinute", s.floatingMaxPerMinute.toString())
            p.setProperty("floatingOnlyStarred", s.floatingOnlyStarred.toString())
            p.setProperty("floatingSnapToEdge", s.floatingSnapToEdge.toString())
            p.setProperty("floatingFullscreenMode", s.floatingFullscreenMode.key)
            p.setProperty("floatingMenuOrder", s.floatingMenuOrder)
            p.setProperty("clipboardSyncEnabled", s.clipboardSyncEnabled.toString())
            p.setProperty("clipboardMaxChars", s.clipboardMaxChars.toString())
            p.setProperty("clipboardOtpGuard", s.clipboardOtpGuard.toString())
            p.setProperty("clipboardOnlyWhenFocused", s.clipboardOnlyWhenFocused.toString())
            p.setProperty("windowX", s.windowX.toString())
            p.setProperty("windowY", s.windowY.toString())
            p.setProperty("windowWidth", s.windowWidth.toString())
            p.setProperty("windowHeight", s.windowHeight.toString())
            file.bufferedWriter(Charsets.UTF_8).use { p.store(it, "PhantomCheck Desktop Settings") }
        }.onFailure { err ->
            DesktopDiagnostics.error("Settings", "Failed saving settings: ${err.javaClass.simpleName}: ${err.message}")
        }
    }

    fun resetSettingsToDefaults() {
        val defaults = DesktopSettings()
        _state.value = defaults
        DesktopStrings.syncLanguage(defaults.language)
        saveToDisk(defaults)
        DesktopDiagnostics.info("Settings", "Settings reset to defaults")
    }

    /**
     * #495 Windows 開機自啟動診斷狀態：
     * - ENABLED：Run 機碼存在、目標 exe 存在、且未被工作管理員（StartupApproved\Run）停用
     * - DISABLED：Run 機碼未設定
     * - DISABLED_BY_TASK_MANAGER：Run 機碼存在，但 StartupApproved\Run 旗標被工作管理員設為停用
     * - PATH_MISMATCH：Run 機碼存在，但指向的 exe 檔案不存在或為錯誤路徑
     */
    enum class AutoStartStatus {
        ENABLED,
        DISABLED,
        DISABLED_BY_TASK_MANAGER,
        PATH_MISMATCH,
        NOT_WINDOWS,
        ERROR
    }

    data class AutoStartDiagnostic(
        val status: AutoStartStatus,
        val registeredCommand: String = "",
        val resolvedExe: String = "",
        val detail: String = ""
    )

    private const val WIN_STARTUP_APPROVED_KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run"

    fun isWindowsOs(): Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    /**
     * #495 查詢目前 HKCU\...\Run 與 StartupApproved\Run 的真實狀態（讀回驗證）。
     */
    fun queryWindowsAutoStartStatus(): AutoStartDiagnostic {
        if (!isWindowsOs()) return AutoStartDiagnostic(AutoStartStatus.NOT_WINDOWS)
        val resolvedExe = resolveCurrentWindowsExecutable()
        val runQuery = runRegCommand(listOf("reg.exe", "query", WIN_RUN_REG_KEY, "/v", WIN_RUN_VALUE_NAME))
        if (runQuery.first != 0) {
            return AutoStartDiagnostic(AutoStartStatus.DISABLED, resolvedExe = resolvedExe)
        }
        val regValue = parseRegSzValue(runQuery.second, WIN_RUN_VALUE_NAME)
        if (regValue.isBlank()) {
            return AutoStartDiagnostic(AutoStartStatus.DISABLED, resolvedExe = resolvedExe)
        }
        val registeredExe = extractExePathFromCommand(regValue)
        if (registeredExe.isBlank() || !File(registeredExe).exists()) {
            return AutoStartDiagnostic(
                status = AutoStartStatus.PATH_MISMATCH,
                registeredCommand = regValue,
                resolvedExe = resolvedExe,
                detail = registeredExe
            )
        }
        // Check Windows Task Manager StartupApproved\Run binary flag (02/06 = enabled; 03/odd/other = disabled)
        val approvedQuery = runRegCommand(listOf("reg.exe", "query", WIN_STARTUP_APPROVED_KEY, "/v", WIN_RUN_VALUE_NAME))
        if (approvedQuery.first == 0) {
            val hex = parseRegBinaryHex(approvedQuery.second, WIN_RUN_VALUE_NAME)
            if (isStartupApprovedDisabled(hex)) {
                return AutoStartDiagnostic(
                    status = AutoStartStatus.DISABLED_BY_TASK_MANAGER,
                    registeredCommand = regValue,
                    resolvedExe = resolvedExe,
                    detail = hex.take(8)
                )
            }
        }
        return AutoStartDiagnostic(
            status = AutoStartStatus.ENABLED,
            registeredCommand = regValue,
            resolvedExe = resolvedExe
        )
    }

    /**
     * #468 / #495 系統設定 2：Windows 開機自動啟動（寫入後 reg query 讀回驗證 + StartupApproved 偵測 + 錯誤誠實回報）。
     */
    fun applyWindowsAutoStart(enable: Boolean): Result<AutoStartDiagnostic> = runCatching {
        if (!isWindowsOs()) {
            throw UnsupportedOperationException(DesktopStrings.autoStartNotWindows)
        }
        val exePath = resolveCurrentWindowsExecutable()
        if (enable) {
            if (exePath.isBlank() || (!File(exePath).exists() && !exePath.endsWith(".exe", ignoreCase = true))) {
                throw IllegalStateException(DesktopStrings.autoStartExeNotFound(exePath))
            }
            val cmd = listOf(
                "reg.exe", "add", WIN_RUN_REG_KEY,
                "/v", WIN_RUN_VALUE_NAME,
                "/t", "REG_SZ",
                "/d", "\"$exePath\" --minimized",
                "/f"
            )
            val (exit, output) = runRegCommand(cmd)
            if (exit != 0) {
                DesktopDiagnostics.error("AutoStart", "reg.exe add failed (exit=$exit): $output")
                throw IllegalStateException(DesktopStrings.autoStartRegFailed(exit, output))
            }
            // Read-back verification
            val verified = queryWindowsAutoStartStatus()
            when (verified.status) {
                AutoStartStatus.ENABLED -> {
                    update { it.copy(autoStart = true) }
                    DesktopDiagnostics.info("AutoStart", "Windows autoStart enabled & verified: ${verified.registeredCommand}")
                    verified
                }
                AutoStartStatus.DISABLED_BY_TASK_MANAGER -> {
                    // Honest state: do NOT pretend enabled when Task Manager blocks it
                    update { it.copy(autoStart = false) }
                    DesktopDiagnostics.warn("AutoStart", "HKCU Run written but disabled by Windows Task Manager (StartupApproved=${verified.detail})")
                    verified
                }
                AutoStartStatus.PATH_MISMATCH -> {
                    update { it.copy(autoStart = false) }
                    DesktopDiagnostics.error("AutoStart", "Read-back exe path does not exist: ${verified.detail}")
                    throw IllegalStateException(DesktopStrings.autoStartPathInvalid(verified.detail))
                }
                else -> {
                    update { it.copy(autoStart = false) }
                    DesktopDiagnostics.error("AutoStart", "Read-back verification failed: ${verified.status}")
                    throw IllegalStateException(DesktopStrings.autoStartVerifyFailed)
                }
            }
        } else {
            val (exit, output) = runRegCommand(listOf("reg.exe", "delete", WIN_RUN_REG_KEY, "/v", WIN_RUN_VALUE_NAME, "/f"))
            // delete returns non-zero when value doesn't exist — verify via read-back
            val verified = queryWindowsAutoStartStatus()
            if (verified.status == AutoStartStatus.ENABLED || verified.status == AutoStartStatus.PATH_MISMATCH) {
                DesktopDiagnostics.error("AutoStart", "reg.exe delete failed (exit=$exit): $output")
                throw IllegalStateException(DesktopStrings.autoStartRegFailed(exit, output))
            }
            update { it.copy(autoStart = false) }
            DesktopDiagnostics.info("AutoStart", "Windows autoStart disabled & verified")
            AutoStartDiagnostic(AutoStartStatus.DISABLED, resolvedExe = exePath)
        }
    }

    /**
     * #495 啟動時自動校驗與修復：若 Run 指向舊路徑（例如更新換目錄）自動改寫為正確 exe；若被工作管理員停用則同步誠實狀態。
     */
    fun verifyAndRepairAutoStartOnLaunch(): AutoStartDiagnostic {
        if (!isWindowsOs()) return AutoStartDiagnostic(AutoStartStatus.NOT_WINDOWS)
        val diag = queryWindowsAutoStartStatus()
        val cur = _state.value
        when (diag.status) {
            AutoStartStatus.PATH_MISMATCH -> {
                if (cur.autoStart) {
                    DesktopDiagnostics.warn("AutoStart", "Stale Run path detected (${diag.detail}); repairing to ${diag.resolvedExe}")
                    return applyWindowsAutoStart(true).getOrDefault(diag)
                }
            }
            AutoStartStatus.DISABLED_BY_TASK_MANAGER -> {
                if (cur.autoStart) {
                    update { it.copy(autoStart = false) }
                    DesktopDiagnostics.warn("AutoStart", "Auto-start is disabled in Windows Task Manager; synced UI state to false")
                }
            }
            AutoStartStatus.ENABLED -> {
                if (!cur.autoStart) {
                    update { it.copy(autoStart = true) }
                }
            }
            else -> Unit
        }
        return diag
    }

    /**
     * #495 引導使用者開啟 Windows「啟動應用程式」設定（備援開啟工作管理員）。
     */
    fun openWindowsStartupSettings(): Result<Unit> = runCatching {
        if (!isWindowsOs()) throw UnsupportedOperationException(DesktopStrings.autoStartNotWindows)
        val launched = runCatching {
            ProcessBuilder("cmd.exe", "/c", "start", "", "ms-settings:startupapps").start()
            true
        }.getOrDefault(false)
        if (!launched) {
            ProcessBuilder("taskmgr.exe").start()
        }
    }

    private fun runRegCommand(cmd: List<String>): Pair<Int, String> = runCatching {
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText().trim()
        val exit = proc.waitFor()
        exit to output
    }.getOrElse { err -> -1 to (err.message ?: err.javaClass.simpleName) }

    internal fun parseRegSzValue(output: String, valueName: String): String {
        val line = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(valueName, ignoreCase = true) && it.contains("REG_SZ", ignoreCase = true) }
            ?: return ""
        val idx = line.indexOf("REG_SZ", ignoreCase = true)
        return if (idx >= 0) line.substring(idx + 6).trim() else ""
    }

    internal fun parseRegBinaryHex(output: String, valueName: String): String {
        val line = output.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith(valueName, ignoreCase = true) && it.contains("REG_BINARY", ignoreCase = true) }
            ?: return ""
        val idx = line.indexOf("REG_BINARY", ignoreCase = true)
        return if (idx >= 0) line.substring(idx + 10).trim().replace(" ", "") else ""
    }

    internal fun isStartupApprovedDisabled(hex: String): Boolean {
        if (hex.length < 2) return false
        val firstByte = hex.take(2).toIntOrNull(16) ?: return false
        // Windows Explorer StartupApproved: 0x02 or 0x06 = enabled; 0x03/0x01/0x07 (odd or other) = disabled
        return firstByte != 0x02 && firstByte != 0x06
    }

    internal fun extractExePathFromCommand(cmd: String): String {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return ""
        if (trimmed.startsWith("\"")) {
            val endQuote = trimmed.indexOf('"', 1)
            if (endQuote > 1) return trimmed.substring(1, endQuote).trim()
        }
        return trimmed.substringBefore(" --").trim()
    }

    internal fun resolveCurrentWindowsExecutable(): String {
        val candidates = buildList {
            val jpackagePath = System.getProperty("jpackage.app-path").orEmpty().trim()
            if (jpackagePath.isNotBlank()) add(jpackagePath)
            val procCmd = ProcessHandle.current().info().command().orElse("").trim()
            if (procCmd.isNotBlank() && procCmd.endsWith("PhantomCheckDesktop.exe", ignoreCase = true)) {
                add(procCmd)
            }
            val localAppData = System.getenv("LOCALAPPDATA").orEmpty().trim()
            if (localAppData.isNotBlank()) {
                add(File(localAppData, "PhantomCheckDesktop\\PhantomCheckDesktop.exe").absolutePath)
            }
            val userDir = System.getProperty("user.dir").orEmpty().trim()
            if (userDir.isNotBlank()) {
                add(File(userDir, "PhantomCheckDesktop.exe").absolutePath)
            }
        }
        val validPackaged = candidates.firstOrNull { path ->
            val f = File(path)
            f.exists() && f.isFile && !f.name.equals("java.exe", ignoreCase = true) && !f.name.equals("javaw.exe", ignoreCase = true)
        }
        if (validPackaged != null) return validPackaged
        val localAppData = System.getenv("LOCALAPPDATA").orEmpty().trim()
        if (localAppData.isNotBlank()) {
            return File(localAppData, "PhantomCheckDesktop\\PhantomCheckDesktop.exe").absolutePath
        }
        return ProcessHandle.current().info().command().orElse("PhantomCheckDesktop.exe")
    }

    /**
     * #468 系統設定 9：手動檢查更新（GitHub Releases latest，絕不背景輪詢，錯誤誠實回報）。
     */
    suspend fun checkLatestRelease(): DesktopUpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/eric11yothelast-Hwanshen/PhantomCheck/releases/latest")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "PhantomCheckDesktop/$CURRENT_DESKTOP_VERSION")
            }
            val code = conn.responseCode
            if (code != 200) {
                val err = runCatching { conn.errorStream?.bufferedReader()?.readText()?.take(200) }.getOrNull().orEmpty()
                DesktopDiagnostics.warn("UpdateCheck", "HTTP $code: $err")
                return@runCatching DesktopUpdateCheckResult(ok = false, errorMessage = "HTTP $code")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JsonParser.parseString(body).asJsonObject
            val tag = json.get("tag_name")?.asString.orEmpty()
            val htmlUrl = json.get("html_url")?.asString.orEmpty()
            DesktopDiagnostics.info("UpdateCheck", "Latest release tag=$tag")
            DesktopUpdateCheckResult(ok = true, latestTag = tag, htmlUrl = htmlUrl)
        }.getOrElse { err ->
            DesktopDiagnostics.error("UpdateCheck", err.message ?: err.javaClass.simpleName)
            DesktopUpdateCheckResult(ok = false, errorMessage = err.message ?: err.javaClass.simpleName)
        }
    }
}
