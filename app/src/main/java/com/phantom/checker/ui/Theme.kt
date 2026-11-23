package com.phantom.checker.ui

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import com.phantom.checker.util.FontScaleMode
import com.phantom.checker.util.PhantomPrefs
import androidx.annotation.StringRes
import com.phantom.checker.R

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

    private val _themeMode = mutableStateOf<ThemeMode?>(null)
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

    val ObsidianBackground = Color(0xFF0E0A18)
    val ObsidianSurface = Color(0xFF161124)
    val ObsidianSurfaceVariant = Color(0xFF241C38)
}

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
    onBackground = Color(0xFF1A1526),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1A1526),
    surfaceVariant = Color(0xFFF3EFFB),
    onSurfaceVariant = Color(0xFF4A4358),
    outline = Color(0xFF7C738D)
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
    outline = Color(0xFF938AA6)
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
    val mode = ThemePreferences.getThemeMode(context)
    val useDarkTheme = darkTheme ?: when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val useDynamic = ThemePreferences.isDynamicColor(context) &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = if (useDynamic) {
        if (useDarkTheme) {
            dynamicDarkColorScheme(context).copy(primary = PhantomColors.SoftViolet)
        } else {
            dynamicLightColorScheme(context).copy(primary = PhantomColors.DeepPurple)
        }
    } else {
        if (useDarkTheme) PhantomDarkColorScheme else PhantomLightColorScheme
    }
    val fontScale = ThemePreferences.getFontScaleMode(context).scale
    MaterialTheme(
        colorScheme = colorScheme,
        typography = scaledTypography(fontScale),
        content = content
    )
}
