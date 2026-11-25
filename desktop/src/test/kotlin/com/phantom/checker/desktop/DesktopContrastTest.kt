package com.phantom.checker.desktop

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * #520 WCAG 2.1 對比度自動化守門測試：
 * 針對 6 種預設強調色 × 4 套主題面（Dark / Light / HC-Dark / HC-Light），
 * 驗證實際 surface 上之 textPrimary/textSecondary/textMuted/accentText/success/warning/error
 * 及 HC 非文字邊框皆符合 WCAG 門檻（一般文字 ≥4.5:1、高對比 ≥7.0:1、非文字 ≥3.0:1）。
 */
class DesktopContrastTest {

    @Test
    fun allSixAccentsAcrossFourThemeModesMeetWcagContrast() {
        val modes = listOf(
            Triple("Dark", false, false),
            Triple("Light", true, false),
            Triple("HC-Dark", false, true),
            Triple("HC-Light", true, true)
        )

        for (preset in AccentPreset.entries) {
            for ((label, isLight, isHc) in modes) {
                val settings = DesktopSettings(
                    accentPreset = preset,
                    lightTheme = isLight,
                    highContrastText = isHc
                )
                val tokens = DesktopThemeTokens.from(settings)
                val pageBg = if (isLight) Color(0xFFE8EEF7) else Color(0xFF0A0E17)
                val effSurface = DesktopThemeTokens.compositeOver(tokens.surface, pageBg)
                val minText = if (isHc) 7.0 else 4.5

                fun assertRatio(name: String, fg: Color, bg: Color, threshold: Double) {
                    val ratio = DesktopThemeTokens.contrastRatio(fg, bg)
                    assertTrue(
                        ratio >= threshold,
                        "[$label / ${preset.key}] $name contrast ratio ${"%.2f".format(ratio)} < $threshold"
                    )
                }

                assertRatio("textPrimary", tokens.textPrimary, effSurface, minText)
                assertRatio("textSecondary", tokens.textSecondary, effSurface, minText)
                assertRatio("textMuted", tokens.textMuted, effSurface, minText)
                assertRatio("accentText", tokens.accentText, effSurface, minText)
                assertRatio("success", tokens.success, effSurface, minText)
                assertRatio("warning", tokens.warning, effSurface, minText)
                assertRatio("error", tokens.error, effSurface, minText)
                assertRatio("onAccent", tokens.onAccent, tokens.accent, 4.5)

                if (isHc) {
                    assertRatio("border(non-text)", tokens.border, effSurface, 3.0)
                    assertTrue(tokens.popupBg.alpha >= 0.99f, "[$label] HC popupBg must be opaque")
                } else {
                    assertTrue(tokens.popupBg.alpha >= 0.94f, "[$label] popupBg alpha must be >= 0.94")
                }
            }
        }
    }

    @Test
    fun animationModeDefaultsAndPowerSaverProfile() {
        val balanced = DesktopMotionProfile(AnimationMode.BALANCED)
        assertTrue(balanced.isBalanced)
        assertTrue(balanced.allowInfiniteLoops)
        assertTrue(balanced.showToastCountdownBar)

        val powerSaver = DesktopMotionProfile(AnimationMode.POWER_SAVER)
        assertTrue(powerSaver.isPowerSaver)
        assertTrue(!powerSaver.allowInfiniteLoops)
        assertTrue(!powerSaver.showToastCountdownBar)
        assertTrue(!powerSaver.allowPressScale)
    }
}
