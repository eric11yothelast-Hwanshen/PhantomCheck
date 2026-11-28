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

private val SCHTASK_COMMAND_REGEX = Regex("<Command>(.*?)</Command>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
private val SCHTASK_DISABLED_REGEX = Regex("<Settings>[\\s\\S]*?<Enabled>\\s*false\\s*</Enabled>[\\s\\S]*?</Settings>", RegexOption.IGNORE_CASE)

enum class CloseBehavior { MINIMIZE_TO_TRAY, EXIT_APP }
enum class AccentPreset(val key: String) { PURPLE("purple"), BLUE("blue"), GREEN("green"), ORANGE("orange"), PINK("pink"), CYAN("cyan") }
enum class UiDensity(val key: String) { COMPACT("compact"), STANDARD("standard"), COMFORTABLE("comfortable") }
enum class MessageCardStyle(val key: String) { CARD("card"), COMPACT_LIST("compact_list") }
enum class MirrorDisplayMode(val key: String) { BANNER("banner"), TOAST("toast"), NATIVE("native"), BOTH("both"), COMBO("combo") }
enum class FloatingBubbleContent(val key: String) { APP_ONLY("app_only"), SENDER_PREVIEW("sender_preview"), FULL_PREVIEW("full_preview") }
enum class FloatingClickAction(val key: String) { OPEN_AI("open_ai"), OPEN_MAIN("open_main") }
/** #528 懸浮球懸停動作 6 選項（預設＝連線與電量，零回退） */
enum class FloatingHoverAction(val key: String) {
    CONNECTION_BATTERY("connection_battery"),
    LATEST_MESSAGE("latest_message"),
    TODAY_STATS("today_stats"),
    LATEST_INTEL_REPORT("latest_intel_report"),
    CLOCK_DATE("clock_date"),
    CUSTOM_TEXT("custom_text")
}
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

/** #531 桌面訊息檢視模式（對話檢視／依 App 分組） */
enum class DesktopMessagesViewMode(val key: String) {
    CONVERSATION("conversation"),
    GROUP_BY_APP("groupByApp")
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
    val toastBgScrimAlpha: Float = 0.58f, // 0.30..0.90 (#529)
    val toastShowCountdown: Boolean = false, // #529
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
    // #531 Desktop messages view mode (conversation | groupByApp)
    val desktopMessagesViewMode: DesktopMessagesViewMode = DesktopMessagesViewMode.CONVERSATION,
    // #528 Floating orb hover action customization
    val floatingHoverAction: FloatingHoverAction = FloatingHoverAction.CONNECTION_BATTERY,
    val floatingHoverCustomText: String = "",
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

    const val CURRENT_DESKTOP_VERSION = "0.9.0"
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
            sidebarWidthDp = p.getProperty("sidebarWidthDp", "244").toIntOrNull()?.coerceIn(120, 320) ?: 244,
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
            toastDurationSec = p.getProperty("toastDurationSec", "5").toIntOrNull()?.coerceIn(3, 20) ?: 5,
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
            toastBgScrimAlpha = p.getProperty("toastBgScrimAlpha", "0.58").toFloatOrNull()?.coerceIn(0.30f, 0.90f) ?: 0.58f,
            toastShowCountdown = p.getProperty("toastShowCountdown", "false").toBoolean(),
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
            desktopMessagesViewMode = DesktopMessagesViewMode.entries.firstOrNull {
                it.key == p.getProperty("desktopMessagesViewMode", DesktopMessagesViewMode.CONVERSATION.key)
            } ?: DesktopMessagesViewMode.CONVERSATION,
            floatingHoverAction = FloatingHoverAction.entries.firstOrNull {
                it.key == p.getProperty("floatingHoverAction", FloatingHoverAction.CONNECTION_BATTERY.key)
            } ?: FloatingHoverAction.CONNECTION_BATTERY,
            floatingHoverCustomText = p.getProperty("floatingHoverCustomText", "").trim().take(40),
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
            p.setProperty("toastBgScrimAlpha", "%.2f".format(java.util.Locale.US, s.toastBgScrimAlpha.coerceIn(0.30f, 0.90f)))
            p.setProperty("toastShowCountdown", s.toastShowCountdown.toString())
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
            p.setProperty("desktopMessagesViewMode", s.desktopMessagesViewMode.key)
            p.setProperty("floatingHoverAction", s.floatingHoverAction.key)
            p.setProperty("floatingHoverCustomText", s.floatingHoverCustomText.trim().take(40))
            p.setProperty("windowX", s.windowX.toString())
            p.setProperty("windowY", s.windowY.toString())
            p.setProperty("windowWidth", s.windowWidth.toString())
            p.setProperty("windowHeight", s.windowHeight.toString())
            DesktopTls.atomicWriteFile(file, restrictOwnerOnly = false) { tmp ->
                tmp.bufferedWriter(Charsets.UTF_8).use { p.store(it, "PhantomCheck Desktop Settings") }
            }
        }.onFailure { err ->
            DesktopDiagnostics.error("Settings", "Failed saving settings: ${err.javaClass.simpleName}: ${err.message}")
        }
    }

    @Synchronized
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
        val detail: String = "",
        // #530 Dual-track status (Track 1: HKCU Run; Track 2: Per-user Task Scheduler OnLogon)
        val runTrackStatus: AutoStartStatus = status,
        val taskTrackStatus: AutoStartStatus = AutoStartStatus.DISABLED,
        val taskRegisteredCommand: String = ""
    )

    private const val WIN_STARTUP_APPROVED_KEY =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\StartupApproved\\Run"
    const val WIN_SCHED_TASK_NAME = "PhantomCheckDesktopLogon"

    fun isWindowsOs(): Boolean = System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun queryRunTrackStatus(resolvedExe: String): Triple<AutoStartStatus, String, String> {
        val runQuery = runRegCommand(listOf("reg.exe", "query", WIN_RUN_REG_KEY, "/v", WIN_RUN_VALUE_NAME))
        if (runQuery.first != 0) {
            return Triple(AutoStartStatus.DISABLED, "", "")
        }
        val regValue = parseRegSzValue(runQuery.second, WIN_RUN_VALUE_NAME)
        if (regValue.isBlank()) {
            return Triple(AutoStartStatus.DISABLED, "", "")
        }
        val registeredExe = extractExePathFromCommand(regValue)
        if (registeredExe.isBlank() || !File(registeredExe).exists()) {
            return Triple(AutoStartStatus.PATH_MISMATCH, regValue, registeredExe)
        }
        // Check Windows Task Manager StartupApproved\Run binary flag (02/06 = enabled; 03/odd/other = disabled)
        val approvedQuery = runRegCommand(listOf("reg.exe", "query", WIN_STARTUP_APPROVED_KEY, "/v", WIN_RUN_VALUE_NAME))
        if (approvedQuery.first == 0) {
            val hex = parseRegBinaryHex(approvedQuery.second, WIN_RUN_VALUE_NAME)
            if (isStartupApprovedDisabled(hex)) {
                return Triple(AutoStartStatus.DISABLED_BY_TASK_MANAGER, regValue, hex.take(8))
            }
        }
        return Triple(AutoStartStatus.ENABLED, regValue, "")
    }

    private fun queryTaskTrackStatus(): Triple<AutoStartStatus, String, String> {
        val q = runRegCommand(listOf("schtasks.exe", "/query", "/tn", WIN_SCHED_TASK_NAME, "/xml"))
        if (q.first != 0 || q.second.isBlank()) {
            return Triple(AutoStartStatus.DISABLED, "", "")
        }
        val xml = q.second
        val cmdMatch = SCHTASK_COMMAND_REGEX.find(xml)
        val rawCmd = cmdMatch?.groupValues?.getOrNull(1)?.trim().orEmpty()
            .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
        val exe = extractExePathFromCommand(rawCmd)
        if (exe.isBlank() || !File(exe).exists()) {
            return Triple(AutoStartStatus.PATH_MISMATCH, rawCmd, exe)
        }
        val disabledMatch = SCHTASK_DISABLED_REGEX
        if (disabledMatch.containsMatchIn(xml)) {
            return Triple(AutoStartStatus.DISABLED, rawCmd, "Task disabled")
        }
        return Triple(AutoStartStatus.ENABLED, rawCmd, "")
    }

    private fun escapeXml(raw: String): String = raw
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    internal fun buildPerUserLogonTaskXml(exePath: String, userId: String): String {
        val escapedExe = escapeXml(exePath)
        val userTag = if (userId.isNotBlank()) "      <UserId>${escapeXml(userId)}</UserId>\n" else ""
        return """<?xml version="1.0" encoding="UTF-16"?>
<Task version="1.2" xmlns="http://schemas.microsoft.com/windows/2004/02/mit/task">
  <RegistrationInfo>
    <Description>PhantomCheck Desktop per-user logon auto-start (--minimized)</Description>
  </RegistrationInfo>
  <Triggers>
    <LogonTrigger>
      <Enabled>true</Enabled>
$userTag    </LogonTrigger>
  </Triggers>
  <Principals>
    <Principal id="Author">
      <LogonType>InteractiveToken</LogonType>
      <RunLevel>LeastPrivilege</RunLevel>
    </Principal>
  </Principals>
  <Settings>
    <MultipleInstancesPolicy>IgnoreNew</MultipleInstancesPolicy>
    <DisallowStartIfOnBatteries>false</DisallowStartIfOnBatteries>
    <StopIfGoingOnBatteries>false</StopIfGoingOnBatteries>
    <AllowHardTerminate>true</AllowHardTerminate>
    <StartWhenAvailable>true</StartWhenAvailable>
    <RunOnlyIfNetworkAvailable>false</RunOnlyIfNetworkAvailable>
    <AllowStartOnDemand>true</AllowStartOnDemand>
    <Enabled>true</Enabled>
    <Hidden>false</Hidden>
    <ExecutionTimeLimit>PT0S</ExecutionTimeLimit>
    <Priority>4</Priority>
  </Settings>
  <Actions Context="Author">
    <Exec>
      <Command>$escapedExe</Command>
      <Arguments>--minimized</Arguments>
    </Exec>
  </Actions>
</Task>
"""
    }

    private fun registerScheduledTaskTrack(exePath: String): Pair<Int, String> {
        val domain = System.getenv("USERDOMAIN").orEmpty().trim()
        val user = System.getenv("USERNAME").orEmpty().trim().ifBlank { System.getProperty("user.name").orEmpty().trim() }
        val userId = if (domain.isNotBlank() && user.isNotBlank()) "$domain\\$user" else user
        val xmlContent = buildPerUserLogonTaskXml(exePath, userId)
        val tempFile = runCatching {
            File.createTempFile("phantomcheck_task_", ".xml").apply {
                writeText(xmlContent, Charsets.UTF_16)
            }
        }.getOrNull()
        if (tempFile != null) {
            try {
                val res = runRegCommand(listOf("schtasks.exe", "/create", "/tn", WIN_SCHED_TASK_NAME, "/xml", tempFile.absolutePath, "/f"))
                if (res.first == 0) return res
                DesktopDiagnostics.warn("AutoStart", "schtasks XML import failed (exit=${res.first}): ${res.second}; falling back to CLI /sc onlogon /rl LIMITED")
            } finally {
                runCatching { tempFile.delete() }
            }
        }
        return runRegCommand(
            listOf(
                "schtasks.exe", "/create",
                "/tn", WIN_SCHED_TASK_NAME,
                "/tr", "\"$exePath\" --minimized",
                "/sc", "onlogon",
                "/rl", "LIMITED",
                "/f"
            )
        )
    }

    /**
     * #495 / #530 查詢雙軌狀態：軌道一 HKCU\...\Run（含 StartupApproved）＋軌道二 Per-user Task Scheduler OnLogon。
     */
    fun queryWindowsAutoStartStatus(): AutoStartDiagnostic {
        if (!isWindowsOs()) return AutoStartDiagnostic(AutoStartStatus.NOT_WINDOWS, runTrackStatus = AutoStartStatus.NOT_WINDOWS, taskTrackStatus = AutoStartStatus.NOT_WINDOWS)
        val resolvedExe = resolveCurrentWindowsExecutable()
        val (runStatus, runCmd, runDetail) = queryRunTrackStatus(resolvedExe)
        val (taskStatus, taskCmd, taskDetail) = queryTaskTrackStatus()

        val overall = when {
            runStatus == AutoStartStatus.PATH_MISMATCH || taskStatus == AutoStartStatus.PATH_MISMATCH -> AutoStartStatus.PATH_MISMATCH
            runStatus == AutoStartStatus.ENABLED || taskStatus == AutoStartStatus.ENABLED -> AutoStartStatus.ENABLED
            runStatus == AutoStartStatus.DISABLED_BY_TASK_MANAGER -> AutoStartStatus.DISABLED_BY_TASK_MANAGER
            else -> AutoStartStatus.DISABLED
        }
        val combinedDetail = listOf(runDetail, taskDetail).filter { it.isNotBlank() }.joinToString(" | ")
        return AutoStartDiagnostic(
            status = overall,
            registeredCommand = runCmd.ifBlank { taskCmd },
            resolvedExe = resolvedExe,
            detail = combinedDetail,
            runTrackStatus = runStatus,
            taskTrackStatus = taskStatus,
            taskRegisteredCommand = taskCmd
        )
    }

    /**
     * #468 / #495 / #530 系統設定 2：Windows 雙軌開機自啟動（啟用齊寫 HKCU Run＋Task Scheduler OnLogon LIMITED；停用齊清；讀回驗證）。
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
            // Track 1: HKCU Run
            val runCmd = listOf(
                "reg.exe", "add", WIN_RUN_REG_KEY,
                "/v", WIN_RUN_VALUE_NAME,
                "/t", "REG_SZ",
                "/d", "\"$exePath\" --minimized",
                "/f"
            )
            val (runExit, runOut) = runRegCommand(runCmd)
            if (runExit != 0) {
                DesktopDiagnostics.warn("AutoStart", "Track 1 (HKCU Run) add failed (exit=$runExit): $runOut")
            }
            // Track 2: Per-user Task Scheduler OnLogon (LIMITED, no admin/UAC)
            val (taskExit, taskOut) = registerScheduledTaskTrack(exePath)
            if (taskExit != 0) {
                DesktopDiagnostics.warn("AutoStart", "Track 2 (Task Scheduler) create failed (exit=$taskExit): $taskOut")
            }
            if (runExit != 0 && taskExit != 0) {
                DesktopDiagnostics.error("AutoStart", "Both auto-start tracks failed: Run($runExit)=$runOut | Task($taskExit)=$taskOut")
                throw IllegalStateException(DesktopStrings.autoStartRegFailed(runExit, "$runOut | $taskOut"))
            }
            // Read-back verification across both tracks
            val verified = queryWindowsAutoStartStatus()
            when (verified.status) {
                AutoStartStatus.ENABLED -> {
                    update { it.copy(autoStart = true) }
                    DesktopDiagnostics.info("AutoStart", "Dual-track autoStart enabled (Run=${verified.runTrackStatus}, Task=${verified.taskTrackStatus})")
                    verified
                }
                AutoStartStatus.DISABLED_BY_TASK_MANAGER -> {
                    update { it.copy(autoStart = false) }
                    DesktopDiagnostics.warn("AutoStart", "Run disabled by Task Manager and Task track unavailable (${verified.detail})")
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
            val (runExit, runOut) = runRegCommand(listOf("reg.exe", "delete", WIN_RUN_REG_KEY, "/v", WIN_RUN_VALUE_NAME, "/f"))
            val (taskExit, taskOut) = runRegCommand(listOf("schtasks.exe", "/delete", "/tn", WIN_SCHED_TASK_NAME, "/f"))
            val verified = queryWindowsAutoStartStatus()
            if (verified.runTrackStatus == AutoStartStatus.ENABLED ||
                verified.runTrackStatus == AutoStartStatus.PATH_MISMATCH ||
                verified.taskTrackStatus == AutoStartStatus.ENABLED ||
                verified.taskTrackStatus == AutoStartStatus.PATH_MISMATCH
            ) {
                DesktopDiagnostics.error("AutoStart", "Disable verification failed: Run($runExit)=$runOut | Task($taskExit)=$taskOut")
                throw IllegalStateException(DesktopStrings.autoStartRegFailed(runExit, "$runOut | $taskOut"))
            }
            update { it.copy(autoStart = false) }
            DesktopDiagnostics.info("AutoStart", "Dual-track Windows autoStart disabled & verified (Run & Task cleared)")
            AutoStartDiagnostic(
                status = AutoStartStatus.DISABLED,
                resolvedExe = exePath,
                runTrackStatus = AutoStartStatus.DISABLED,
                taskTrackStatus = AutoStartStatus.DISABLED
            )
        }
    }

    /**
     * #495 / #530 啟動時自動校驗與修復：任一軌指向舊路徑（更新換目錄）自動雙軌齊修；若雙軌皆失效則同步誠實狀態。
     */
    fun verifyAndRepairAutoStartOnLaunch(): AutoStartDiagnostic {
        if (!isWindowsOs()) return AutoStartDiagnostic(AutoStartStatus.NOT_WINDOWS, runTrackStatus = AutoStartStatus.NOT_WINDOWS, taskTrackStatus = AutoStartStatus.NOT_WINDOWS)
        val diag = queryWindowsAutoStartStatus()
        val cur = _state.value
        when (diag.status) {
            AutoStartStatus.PATH_MISMATCH -> {
                if (cur.autoStart) {
                    DesktopDiagnostics.warn("AutoStart", "Stale path detected on auto-start track (${diag.detail}); repairing both tracks to ${diag.resolvedExe}")
                    return applyWindowsAutoStart(true).getOrDefault(diag)
                }
            }
            AutoStartStatus.DISABLED_BY_TASK_MANAGER -> {
                if (cur.autoStart) {
                    update { it.copy(autoStart = false) }
                    DesktopDiagnostics.warn("AutoStart", "Auto-start is disabled in Windows Task Manager and Task track inactive; synced UI state to false")
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
        runCatching { proc.outputStream.close() }
        val outputRef = java.util.concurrent.atomic.AtomicReference("")
        val readerThread = Thread({
            val text = runCatching {
                proc.inputStream.bufferedReader().use { it.readText() }.trim()
            }.getOrDefault("")
            outputRef.set(text)
        }, "phantom-reg-proc-reader").apply {
            isDaemon = true
            start()
        }
        val finished = proc.waitFor(12, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            proc.destroyForcibly()
            runCatching { proc.waitFor(2, java.util.concurrent.TimeUnit.SECONDS) }
            runCatching { proc.inputStream.close() }
            runCatching { readerThread.join(1_000L) }
            return@runCatching -1 to "Command timed out (12s)"
        }
        runCatching { readerThread.join(2_000L) }
        proc.exitValue() to outputRef.get()
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
            try {
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
            } finally {
                conn.disconnect()
            }
        }.getOrElse { err ->
            DesktopDiagnostics.error("UpdateCheck", err.message ?: err.javaClass.simpleName)
            DesktopUpdateCheckResult(ok = false, errorMessage = err.message ?: err.javaClass.simpleName)
        }
    }
}
