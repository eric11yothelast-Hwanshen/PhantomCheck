package com.phantom.checker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

@Composable
fun PhantomCheckTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PhantomDarkColorScheme else PhantomLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
