package com.phantom.checker.desktop

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * #520 桌面端響應式視窗分級（對齊 1366×768@125% 至 4K@100% 矩陣）：
 * - COMPACT  < 1000dp：側欄自動收合 76dp、單窗格、16dp 外距
 * - MEDIUM   1000–1279dp：側欄 210dp、雙窗格、24dp 外距
 * - EXPANDED 1280–1759dp：使用者側欄寬、雙窗格、24dp 外距、最大內容寬 1120dp
 * - LARGE    >= 1760dp：三窗格支援、32dp 外距、最大內容寬 1280dp
 */
enum class DesktopWindowClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
    LARGE;

    val pagePadding: Dp
        get() = when (this) {
            COMPACT -> 16.dp
            MEDIUM, EXPANDED -> 24.dp
            LARGE -> 32.dp
        }

    val maxContentWidth: Dp
        get() = when (this) {
            COMPACT, MEDIUM -> Dp.Unspecified
            EXPANDED -> 1120.dp
            LARGE -> 1280.dp
        }

    companion object {
        fun fromWidth(widthDp: Dp): DesktopWindowClass = when {
            widthDp < 1000.dp -> COMPACT
            widthDp < 1280.dp -> MEDIUM
            widthDp < 1760.dp -> EXPANDED
            else -> LARGE
        }
    }
}
