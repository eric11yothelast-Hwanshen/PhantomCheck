package com.phantom.checker.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.drawable.ColorDrawable
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.core.view.WindowCompat
import com.phantom.checker.util.FontScaleMode
import com.phantom.checker.util.PhantomPrefs
import androidx.annotation.StringRes
import com.phantom.checker.R

/** #510 全域高對比度模式狀態（深／淺雙模式皆生效） */
val LocalHighContrast = staticCompositionLocalOf { false }

/**
 * #510 依深淺模式與高對比度回傳適當卡片／膠囊背景不透明度：
 * 深色模式維持原值零回歸；淺色模式提升至 ≥0.92 防止底圖透出造成低對比；高對比度強制不透明 1.0。
 */
@Composable
fun phantomSurfaceAlpha(darkAlpha: Float, lightAlpha: Float = 0.94f): Float {
    if (LocalHighContrast.current) return 1.0f
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) darkAlpha else lightAlpha.coerceAtLeast(0.92f)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

enum class ThemeMode(val prefValue: String, @StringRes val labelRes: Int) {
    SYSTEM("system", R.string.theme_mode_system),
    DARK("dark", R.string.theme_mode_dark),
    LIGHT("light", R.string.theme_mode_light);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)

    companion object {
        fun fromPref(value: String?): ThemeMode =
            entries.firstOrNull { it.prefValue == value } ?: SYSTEM
    }
}

object ThemePreferences {
    private const val PREFS_NAME = "phantom_theme_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_HIGH_CONTRAST = "high_contrast_enabled"

    private val _themeMode = mutableStateOf<ThemeMode?>(null)
    private val _highContrast = mutableStateOf<Boolean?>(null)
    private val _dynamicColor = mutableStateOf<Boolean?>(null)
    private val _fontScaleMode = mutableStateOf<FontScaleMode?>(null)
    private val _glassEffectEnabled = mutableStateOf<Boolean?>(null)
    private val _backgroundVersion = mutableStateOf(0)
    private val _backgroundScrimPercent = mutableStateOf<Int?>(null)
    private val _glassStrengthPercent = mutableStateOf<Int?>(null)
    private val _cardBgMode = mutableStateOf<PhantomPrefs.CardBgMode?>(null)
    private val _cardBgAlphaPercent = mutableStateOf<Int?>(null)

    fun getThemeMode(context: Context): ThemeMode {
        val cached = _themeMode.value
        if (cached != null) return cached
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = ThemeMode.fromPref(prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.prefValue))
        _themeMode.value = loaded
        return loaded
    }

    fun setThemeMode(context: Context, mode: ThemeMode) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME_MODE, mode.prefValue)
            .apply()
        _themeMode.value = mode
    }

    fun isHighContrast(context: Context): Boolean {
        val cached = _highContrast.value
        if (cached != null) return cached
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val loaded = prefs.getBoolean(KEY_HIGH_CONTRAST, false)
        _highContrast.value = loaded
        return loaded
    }

    fun setHighContrast(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_HIGH_CONTRAST, enabled)
            .apply()
        _highContrast.value = enabled
    }

    fun isDynamicColor(context: Context): Boolean {
        val cached = _dynamicColor.value
        if (cached != null) return cached
        val loaded = PhantomPrefs.isDynamicColorEnabled(context)
        _dynamicColor.value = loaded
        return loaded
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        PhantomPrefs.setDynamicColorEnabled(context, enabled)
        _dynamicColor.value = enabled
    }

    fun getFontScaleMode(context: Context): FontScaleMode {
        val cached = _fontScaleMode.value
        if (cached != null) return cached
        val loaded = PhantomPrefs.getFontScaleMode(context)
        _fontScaleMode.value = loaded
        return loaded
    }

    fun setFontScaleMode(context: Context, mode: FontScaleMode) {
        PhantomPrefs.setFontScaleMode(context, mode)
        _fontScaleMode.value = mode
    }

    fun isGlassEffectEnabled(context: Context): Boolean {
        val cached = _glassEffectEnabled.value
        if (cached != null) return cached
        val loaded = PhantomPrefs.isGlassEffectEnabled(context)
        _glassEffectEnabled.value = loaded
        return loaded
    }

    fun setGlassEffectEnabled(context: Context, enabled: Boolean) {
        PhantomPrefs.setGlassEffectEnabled(context, enabled)
        _glassEffectEnabled.value = enabled
    }

    fun getBackgroundVersion(): Int = _backgroundVersion.value

    fun bumpBackgroundVersion() {
        _backgroundVersion.value = _backgroundVersion.value + 1
    }

    fun getBackgroundScrimPercent(context: Context): Int {
        val cached = _backgroundScrimPercent.value
        if (cached != null) return cached
        val loaded = PhantomPrefs.getBackgroundScrimPercent(context)
        _backgroundScrimPercent.value = loaded
        return loaded
    }

    fun setBackgroundScrimPercent(context: Context, percent: Int) {
        PhantomPrefs.setBackgroundScrimPercent(context, percent)
        _backgroundScrimPercent.value = PhantomPrefs.getBackgroundScrimPercent(context)
    }

    fun getGlassStrengthPercent(context: Context): Int {
        val cached = _glassStrengthPercent.value
        if (cached != null) return cached
        val loaded = PhantomPrefs.getGlassStrengthPercent(context)
        _glassStrengthPercent.value = loaded
        return loaded
    }

    fun setGlassStrengthPercent(context: Context, percent: Int) {
        PhantomPrefs.setGlassStrengthPercent(context, percent)
        _glassStrengthPercent.value = PhantomPrefs.getGlassStrengthPercent(context)
    }

    fun getCardBgMode(context: Context): PhantomPrefs.CardBgMode {
        val cached = _cardBgMode.value
        if (cached != null) return cached
        val loaded = PhantomPrefs.getCardBgMode(context)
        _cardBgMode.value = loaded
        return loaded
    }

    fun setCardBgMode(context: Context, mode: PhantomPrefs.CardBgMode) {
        PhantomPrefs.setCardBgMode(context, mode)
        _cardBgMode.value = mode
    }

    fun getCardBgAlphaPercent(context: Context): Int {
        val cached = _cardBgAlphaPercent.value
        if (cached != null) return cached
        val loaded = PhantomPrefs.getCardBgAlphaPercent(context)
        _cardBgAlphaPercent.value = loaded
        return loaded
    }

    fun setCardBgAlphaPercent(context: Context, percent: Int) {
        PhantomPrefs.setCardBgAlphaPercent(context, percent)
        _cardBgAlphaPercent.value = PhantomPrefs.getCardBgAlphaPercent(context)
    }
}

// Phantom 品牌色板（深紫為主調，詐騙警示維持紅色語義）
object PhantomColors {
    val DeepPurple = Color(0xFF5B21B6)
    val SoftViolet = Color(0xFFC4B5FD)
    val DeepestViolet = Color(0xFF2E1065)
    val LavenderTint = Color(0xFFEDE9FE)

    val MysticIndigo = Color(0xFF4338CA)
    val IndigoGlow = Color(0xFF818CF8)
    val IndigoContainerLight = Color(0xFFE0E7FF)
    val IndigoContainerDark = Color(0xFF312E81)

    val SignalAccent = Color(0xFF7C3AED)
    val SignalAccentLight = Color(0xFFDDD6FE)

    // 詐騙警示語義色（紅）
    val ScamAlertRed = Color(0xFFDC2626)
    val ScamAlertRedLight = Color(0xFFF87171)
    val ScamAlertContainerLight = Color(0xFFFEE2E2)
    val ScamAlertOnContainerLight = Color(0xFF7F1D1D)
    val ScamAlertContainerDark = Color(0xFF991B1B)
    val ScamAlertOnContainerDark = Color(0xFFFEE2E2)

    // 警告語義色（琥珀黃，區別於詐騙紅色 error，供 R10–R14 風險提示／待確認／權限警示全域共用）
    val WarningAmber = Color(0xFFB45309)
    val WarningOnAmber = Color.White
    val WarningContainerLight = Color(0xFFFEF3C7)
    val WarningOnContainerLight = Color(0xFF78350F)

    val WarningAmberLight = Color(0xFFFBBF24)
    val WarningOnAmberDark = Color(0xFF451A03)
    val WarningContainerDark = Color(0xFF78350F)
    val WarningOnContainerDark = Color(0xFFFEF3C7)

    val WarningHighContrastLight = Color(0xFF78350F)
    val WarningHighContrastContainerLight = Color(0xFFFEF3C7)
    val WarningHighContrastOnContainerLight = Color(0xFF451A03)

    val WarningHighContrastDark = Color(0xFFFDE68A)
    val WarningHighContrastOnDark = Color(0xFF290F00)
    val WarningHighContrastContainerDark = Color(0xFF78350F)
    val WarningHighContrastOnContainerDark = Color.White

    val ObsidianBackground = Color(0xFF0E0A18)
    val ObsidianSurface = Color(0xFF161124)
    val ObsidianSurfaceVariant = Color(0xFF241C38)
}

/**
 * 全域語義擴充色票（#548）：補齊 Material3 ColorScheme 未內建之 warning 視覺樣式，
 * 透過 [LocalPhantomColors] 與 `MaterialTheme.colorScheme.warning*` 擴充屬性供全 App（R10–R14）共用。
 */
@androidx.compose.runtime.Immutable
data class PhantomExtendedColors(
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
)

private val PhantomLightExtendedColors = PhantomExtendedColors(
    warning = PhantomColors.WarningAmber,
    onWarning = PhantomColors.WarningOnAmber,
    warningContainer = PhantomColors.WarningContainerLight,
    onWarningContainer = PhantomColors.WarningOnContainerLight,
)

private val PhantomDarkExtendedColors = PhantomExtendedColors(
    warning = PhantomColors.WarningAmberLight,
    onWarning = PhantomColors.WarningOnAmberDark,
    warningContainer = PhantomColors.WarningContainerDark,
    onWarningContainer = PhantomColors.WarningOnContainerDark,
)

private val PhantomHighContrastLightExtendedColors = PhantomExtendedColors(
    warning = PhantomColors.WarningHighContrastLight,
    onWarning = Color.White,
    warningContainer = PhantomColors.WarningHighContrastContainerLight,
    onWarningContainer = PhantomColors.WarningHighContrastOnContainerLight,
)

private val PhantomHighContrastDarkExtendedColors = PhantomExtendedColors(
    warning = PhantomColors.WarningHighContrastDark,
    onWarning = PhantomColors.WarningHighContrastOnDark,
    warningContainer = PhantomColors.WarningHighContrastContainerDark,
    onWarningContainer = PhantomColors.WarningHighContrastOnContainerDark,
)

val LocalPhantomColors = staticCompositionLocalOf { PhantomLightExtendedColors }

val androidx.compose.material3.ColorScheme.warning: Color
    @Composable get() = LocalPhantomColors.current.warning

val androidx.compose.material3.ColorScheme.onWarning: Color
    @Composable get() = LocalPhantomColors.current.onWarning

val androidx.compose.material3.ColorScheme.warningContainer: Color
    @Composable get() = LocalPhantomColors.current.warningContainer

val androidx.compose.material3.ColorScheme.onWarningContainer: Color
    @Composable get() = LocalPhantomColors.current.onWarningContainer

private val PhantomLightColorScheme = lightColorScheme(
    primary = PhantomColors.DeepPurple,
    onPrimary = Color.White,
    primaryContainer = PhantomColors.LavenderTint,
    onPrimaryContainer = PhantomColors.DeepestViolet,
    secondary = PhantomColors.MysticIndigo,
    onSecondary = Color.White,
    secondaryContainer = PhantomColors.IndigoContainerLight,
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = PhantomColors.SignalAccent,
    onTertiary = Color.White,
    tertiaryContainer = PhantomColors.SignalAccentLight,
    onTertiaryContainer = PhantomColors.DeepestViolet,
    error = PhantomColors.ScamAlertRed,
    onError = Color.White,
    errorContainer = PhantomColors.ScamAlertContainerLight,
    onErrorContainer = PhantomColors.ScamAlertOnContainerLight,
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF14101F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF14101F),
    surfaceVariant = Color(0xFFEEEAF8),
    onSurfaceVariant = Color(0xFF3E3650),
    outline = Color(0xFF6B617D),
    outlineVariant = Color(0xFFC7BEDB),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F4FC),
    surfaceContainer = Color(0xFFF1ECF9),
    surfaceContainerHigh = Color(0xFFEAE3F6),
    surfaceContainerHighest = Color(0xFFE2D9F2)
)

private val PhantomHighContrastLightColorScheme = lightColorScheme(
    primary = Color(0xFF3B0764),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE9D5FF),
    onPrimaryContainer = Color(0xFF1E0038),
    secondary = Color(0xFF1E1B4B),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDBEAFE),
    onSecondaryContainer = Color(0xFF0F172A),
    tertiary = Color(0xFF4C1D95),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF1E0038),
    error = Color(0xFF991B1B),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF450A0A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF1E293B),
    outline = Color(0xFF0F172A),
    outlineVariant = Color(0xFF334155),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFF1F5F9),
    surfaceContainerHigh = Color(0xFFE2E8F0),
    surfaceContainerHighest = Color(0xFFCBD5E1)
)

private val PhantomDarkColorScheme = darkColorScheme(
    primary = PhantomColors.SoftViolet,
    onPrimary = PhantomColors.DeepestViolet,
    primaryContainer = Color(0xFF4C1D95),
    onPrimaryContainer = PhantomColors.LavenderTint,
    secondary = PhantomColors.IndigoGlow,
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = PhantomColors.IndigoContainerDark,
    onSecondaryContainer = PhantomColors.IndigoContainerLight,
    tertiary = Color(0xFFA78BFA),
    onTertiary = PhantomColors.DeepestViolet,
    tertiaryContainer = Color(0xFF3B0764),
    onTertiaryContainer = PhantomColors.SignalAccentLight,
    error = PhantomColors.ScamAlertRedLight,
    onError = Color(0xFF450A0A),
    errorContainer = PhantomColors.ScamAlertContainerDark,
    onErrorContainer = PhantomColors.ScamAlertOnContainerDark,
    background = PhantomColors.ObsidianBackground,
    onBackground = Color(0xFFF3EFFB),
    surface = PhantomColors.ObsidianSurface,
    onSurface = Color(0xFFF3EFFB),
    surfaceVariant = PhantomColors.ObsidianSurfaceVariant,
    onSurfaceVariant = Color(0xFFCAC2D9),
    outline = Color(0xFF938AA6),
    outlineVariant = Color(0xFF3E3556),
    surfaceContainerLowest = Color(0xFF0A0712),
    surfaceContainerLow = Color(0xFF140F21),
    surfaceContainer = Color(0xFF1B152B),
    surfaceContainerHigh = Color(0xFF231C36),
    surfaceContainerHighest = Color(0xFF2D2444)
)

private val PhantomHighContrastDarkColorScheme = darkColorScheme(
    primary = Color(0xFFDDD6FE),
    onPrimary = Color(0xFF1A0536),
    primaryContainer = Color(0xFF3B0764),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFBFDBFE),
    onSecondary = Color(0xFF0F172A),
    secondaryContainer = Color(0xFF1E3A8A),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFFE9D5FF),
    onTertiary = Color(0xFF1A0536),
    tertiaryContainer = Color(0xFF4C1D95),
    onTertiaryContainer = Color.White,
    error = Color(0xFFFCA5A5),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF0B0F1A),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF18223A),
    onSurfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFF94A3B8),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0B0F1A),
    surfaceContainer = Color(0xFF111827),
    surfaceContainerHigh = Color(0xFF18223A),
    surfaceContainerHighest = Color(0xFF1E293B)
)

private fun TextStyle.scaled(factor: Float): TextStyle =
    if (factor == 1.0f) this else copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)

private fun scaledTypography(factor: Float): Typography {
    val base = Typography()
    if (factor == 1.0f) return base
    return Typography(
        displayLarge = base.displayLarge.scaled(factor),
        displayMedium = base.displayMedium.scaled(factor),
        displaySmall = base.displaySmall.scaled(factor),
        headlineLarge = base.headlineLarge.scaled(factor),
        headlineMedium = base.headlineMedium.scaled(factor),
        headlineSmall = base.headlineSmall.scaled(factor),
        titleLarge = base.titleLarge.scaled(factor),
        titleMedium = base.titleMedium.scaled(factor),
        titleSmall = base.titleSmall.scaled(factor),
        bodyLarge = base.bodyLarge.scaled(factor),
        bodyMedium = base.bodyMedium.scaled(factor),
        bodySmall = base.bodySmall.scaled(factor),
        labelLarge = base.labelLarge.scaled(factor),
        labelMedium = base.labelMedium.scaled(factor),
        labelSmall = base.labelSmall.scaled(factor)
    )
}

@Composable
fun PhantomCheckTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val mode = ThemePreferences.getThemeMode(context)
    val highContrast = ThemePreferences.isHighContrast(context)
    val useDarkTheme = darkTheme ?: when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val useDynamic = !highContrast &&
        ThemePreferences.isDynamicColor(context) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        highContrast -> if (useDarkTheme) PhantomHighContrastDarkColorScheme else PhantomHighContrastLightColorScheme
        useDynamic -> if (useDarkTheme) {
            dynamicDarkColorScheme(context).copy(
                primary = PhantomColors.SoftViolet,
                onPrimary = PhantomColors.DeepestViolet,
            )
        } else {
            dynamicLightColorScheme(context).copy(
                primary = PhantomColors.DeepPurple,
                onPrimary = Color.White,
            )
        }
        else -> if (useDarkTheme) PhantomDarkColorScheme else PhantomLightColorScheme
    }
    val extendedColors = when {
        highContrast -> if (useDarkTheme) PhantomHighContrastDarkExtendedColors else PhantomHighContrastLightExtendedColors
        useDarkTheme -> PhantomDarkExtendedColors
        else -> PhantomLightExtendedColors
    }
    if (!view.isInEditMode) {
        val bgArgb = colorScheme.background.toArgb()
        SideEffect {
            val window = context.findActivity()?.window
            if (window != null) {
                window.setBackgroundDrawable(ColorDrawable(bgArgb))
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !useDarkTheme
                controller.isAppearanceLightNavigationBars = !useDarkTheme
            }
        }
    }
    val fontScale = ThemePreferences.getFontScaleMode(context).scale
    CompositionLocalProvider(
        LocalHighContrast provides highContrast,
        LocalPhantomColors provides extendedColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography(fontScale),
            content = content
        )
    }
}
