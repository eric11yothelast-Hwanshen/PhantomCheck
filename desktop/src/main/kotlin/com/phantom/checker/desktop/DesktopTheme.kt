package com.phantom.checker.desktop

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

/**
 * #488 桌面端深淺維度設計 Token（單一色彩來源）：
 * - 由 surfaceOpacity (0.55..0.96)、bgScrimAlpha (0.15..0.85)、highContrastText 驅動
 * - 所有卡片、側欄、對話框、Canvas 圖表、Markdown 區塊皆透過 LocalDesktopTokens.current 取色
 */
@Immutable
data class DesktopThemeTokens(
    val sidebarBg: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val codeBg: Color,
    val scrimColor: Color,
    val dialogContainer: Color,
    val chartGridLine: Color
) {
    companion object {
        val Default = from(DesktopSettings())

        fun from(settings: DesktopSettings): DesktopThemeTokens {
            val opacity = settings.surfaceOpacity.coerceIn(0.55f, 0.96f)
            val scrim = settings.bgScrimAlpha.coerceIn(0.15f, 0.85f)
            val hc = settings.highContrastText
            val (_, bgBottom) = DesktopPalette.bgGradientColors(settings.bgTheme)
            val elevatedAlpha = (opacity + 0.08f).coerceAtMost(0.98f)
            val sidebarAlpha = (opacity + 0.10f).coerceAtMost(0.96f)
            if (settings.lightTheme) {
                return DesktopThemeTokens(
                    sidebarBg = Color(0xFFF1F5F9).copy(alpha = sidebarAlpha),
                    surface = Color(0xFFFFFFFF).copy(alpha = opacity),
                    surfaceElevated = Color(0xFFF8FAFC).copy(alpha = elevatedAlpha),
                    border = Color(0xFF0F172A).copy(alpha = if (hc) 0.22f else 0.12f),
                    textPrimary = if (hc) Color(0xFF000000) else Color(0xFF0F172A),
                    textSecondary = if (hc) Color(0xFF1E293B) else Color(0xFF334155),
                    textMuted = if (hc) Color(0xFF475569) else Color(0xFF64748B),
                    codeBg = Color(0xFF0F172A).copy(alpha = 0.08f),
                    scrimColor = Color(0xFFE2E8F0).copy(alpha = scrim),
                    dialogContainer = Color(0xFFF8FAFC).copy(alpha = 0.98f),
                    chartGridLine = Color(0xFF0F172A).copy(alpha = if (hc) 0.18f else 0.10f)
                )
            }
            return DesktopThemeTokens(
                sidebarBg = Color(0xFF121826).copy(alpha = sidebarAlpha),
                surface = Color(0xFF182032).copy(alpha = opacity),
                surfaceElevated = Color(0xFF202A40).copy(alpha = elevatedAlpha),
                border = Color.White.copy(alpha = if (hc) 0.20f else 0.09f),
                textPrimary = if (hc) Color(0xFFFFFFFF) else Color(0xFFEBF1F8),
                textSecondary = if (hc) Color(0xFFDDE7F5) else Color(0xFFB4C0D3),
                textMuted = if (hc) Color(0xFFAAB8CE) else Color(0xFF7D8AA1),
                codeBg = Color.Black.copy(alpha = if (hc) 0.54f else 0.38f),
                scrimColor = Color.Black.copy(alpha = scrim),
                dialogContainer = bgBottom.copy(alpha = 0.97f),
                chartGridLine = Color.White.copy(alpha = if (hc) 0.22f else 0.11f)
            )
        }
    }
}

val LocalDesktopTokens = staticCompositionLocalOf { DesktopThemeTokens.Default }

/**
 * #488 桌面端頁面切換動效常數（draw-phase 友善、全常數化、無布林階躍切換參數）。
 */
object DesktopMotion {
    const val FADE_IN_MS = 180
    const val FADE_OUT_MS = 130
    const val SLIDE_IN_MS = 220
    const val SLIDE_OUT_MS = 160
    const val SCALE_IN_MS = 200
    const val SCALE_OUT_MS = 140
    const val SCALE_INITIAL = 0.96f
    const val SCALE_TARGET_EXIT = 0.98f
    const val SLIDE_DIVISOR = 12

    fun pageTransition(style: PageTransitionStyle, forward: Boolean): ContentTransform {
        val dir = if (forward) 1 else -1
        return when (style) {
            PageTransitionStyle.NONE -> EnterTransition.None togetherWith ExitTransition.None
            PageTransitionStyle.FADE ->
                fadeIn(tween(FADE_IN_MS, easing = FastOutSlowInEasing)) togetherWith
                    fadeOut(tween(FADE_OUT_MS, easing = FastOutSlowInEasing))
            PageTransitionStyle.SLIDE_HORIZONTAL ->
                slideInHorizontally(tween(SLIDE_IN_MS, easing = FastOutSlowInEasing)) { fullWidth -> dir * (fullWidth / SLIDE_DIVISOR) } togetherWith
                    slideOutHorizontally(tween(SLIDE_OUT_MS, easing = FastOutSlowInEasing)) { fullWidth -> -dir * (fullWidth / SLIDE_DIVISOR) }
            PageTransitionStyle.SLIDE_FADE ->
                (fadeIn(tween(FADE_IN_MS, easing = FastOutSlowInEasing)) +
                    slideInHorizontally(tween(SLIDE_IN_MS, easing = FastOutSlowInEasing)) { fullWidth -> dir * (fullWidth / SLIDE_DIVISOR) }) togetherWith
                    (fadeOut(tween(FADE_OUT_MS, easing = FastOutSlowInEasing)) +
                        slideOutHorizontally(tween(SLIDE_OUT_MS, easing = FastOutSlowInEasing)) { fullWidth -> -dir * (fullWidth / SLIDE_DIVISOR) })
            PageTransitionStyle.SCALE_FADE ->
                (fadeIn(tween(SCALE_IN_MS, easing = FastOutSlowInEasing)) +
                    scaleIn(tween(SCALE_IN_MS, easing = FastOutSlowInEasing), initialScale = SCALE_INITIAL)) togetherWith
                    (fadeOut(tween(SCALE_OUT_MS, easing = FastOutSlowInEasing)) +
                        scaleOut(tween(SCALE_OUT_MS, easing = FastOutSlowInEasing), targetScale = SCALE_TARGET_EXIT))
        }
    }
}

/**
 * #468 / #488 桌面端深色玻璃設計系統（支援 6 種強調色、4 種預設漸層、自訂背景圖、深淺 token、密度與字級縮放、動畫開關）。
 */
object DesktopPalette {
    val GlassSidebarBg = Color(0xFF121826).copy(alpha = 0.86f)
    val GlassSurface = Color(0xFF182032).copy(alpha = 0.74f)
    val GlassSurfaceElevated = Color(0xFF202A40).copy(alpha = 0.82f)
    val GlassBorder = Color.White.copy(alpha = 0.09f)
    val GlassSuccess = Color(0xFF4ADE80)
    val GlassWarning = Color(0xFFFBBF24)
    val GlassError = Color(0xFFFF7B72)
    val TextPrimary = Color(0xFFEBF1F8)
    val TextSecondary = Color(0xFFB4C0D3)
    val TextMuted = Color(0xFF7D8AA1)

    fun accentColor(preset: AccentPreset): Color = when (preset) {
        AccentPreset.PURPLE -> Color(0xFFA78BFA)
        AccentPreset.BLUE -> Color(0xFF6EA8FE)
        AccentPreset.GREEN -> Color(0xFF34D399)
        AccentPreset.ORANGE -> Color(0xFFFB923C)
        AccentPreset.PINK -> Color(0xFFF472B6)
        AccentPreset.CYAN -> Color(0xFF22D3EE)
    }

    fun parseHexColor(hex: String): Color? {
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6) return null
        val rgb = clean.toLongOrNull(16) ?: return null
        return Color(0xFF000000L or rgb)
    }

    fun resolveAccentColor(preset: AccentPreset, customHex: String): Color =
        parseHexColor(customHex) ?: accentColor(preset)

    fun glassSurface(level: GlassBlurLevel): Color = when (level) {
        GlassBlurLevel.LOW -> Color(0xFF182032).copy(alpha = 0.88f)
        GlassBlurLevel.MEDIUM -> Color(0xFF182032).copy(alpha = 0.74f)
        GlassBlurLevel.HIGH -> Color(0xFF182032).copy(alpha = 0.56f)
    }

    fun glassSurfaceElevated(level: GlassBlurLevel): Color = when (level) {
        GlassBlurLevel.LOW -> Color(0xFF202A40).copy(alpha = 0.92f)
        GlassBlurLevel.MEDIUM -> Color(0xFF202A40).copy(alpha = 0.82f)
        GlassBlurLevel.HIGH -> Color(0xFF202A40).copy(alpha = 0.66f)
    }

    fun bgGradientColors(preset: BgThemePreset): Pair<Color, Color> = when (preset) {
        BgThemePreset.MIDNIGHT -> Color(0xFF0A0E17) to Color(0xFF101726)
        BgThemePreset.AURORA -> Color(0xFF0B1120) to Color(0xFF1B1535)
        BgThemePreset.OBSIDIAN -> Color(0xFF08090C) to Color(0xFF13151B)
        BgThemePreset.DEEP_OCEAN -> Color(0xFF071520) to Color(0xFF0E2538)
    }

    fun cardPadding(density: UiDensity): Dp = when (density) {
        UiDensity.COMPACT -> 10.dp
        UiDensity.STANDARD -> 14.dp
        UiDensity.COMFORTABLE -> 18.dp
    }

    fun listSpacing(density: UiDensity): Dp = when (density) {
        UiDensity.COMPACT -> 6.dp
        UiDensity.STANDARD -> 10.dp
        UiDensity.COMFORTABLE -> 14.dp
    }

    fun typography(scale: Float): Typography {
        val s = scale.coerceIn(0.9f, 1.25f)
        return Typography(
            headlineSmall = TextStyle(fontSize = (24f * s).sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
            titleMedium = TextStyle(fontSize = (16f * s).sp, fontWeight = FontWeight.SemiBold),
            titleSmall = TextStyle(fontSize = (14f * s).sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
            bodyMedium = TextStyle(fontSize = (14f * s).sp, fontWeight = FontWeight.Normal, lineHeight = (20f * s).sp),
            bodySmall = TextStyle(fontSize = (13f * s).sp, fontWeight = FontWeight.Normal, lineHeight = (18f * s).sp),
            labelMedium = TextStyle(fontSize = (12f * s).sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
            labelSmall = TextStyle(fontSize = (11f * s).sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp)
        )
    }

    fun loadBackgroundImage(path: String): ImageBitmap? {
        if (path.isBlank()) return null
        return runCatching {
            val file = File(path)
            if (!file.exists() || !file.isFile) return null
            SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
        }.getOrNull()
    }
}

val DesktopSpringFloat = spring<Float>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow
)
val DesktopSpringOffset = spring<IntOffset>(
    dampingRatio = 0.85f,
    stiffness = Spring.StiffnessMediumLow
)

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    cornerDp: Dp? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val s by DesktopSettingsStore.state.collectAsState()
    val tokens = LocalDesktopTokens.current
    val resolvedBg = containerColor ?: tokens.surface
    val resolvedCorner = cornerDp ?: s.messageCardCornerDp.dp
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(resolvedCorner),
        colors = CardDefaults.cardColors(containerColor = resolvedBg),
        border = BorderStroke(1.dp, tokens.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

@Composable
fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animationsEnabled: Boolean = true,
    containerColor: Color? = null,
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (animationsEnabled && pressed && enabled) 0.97f else 1f,
        animationSpec = DesktopSpringFloat,
        label = "btn_scale"
    )
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        colors = if (containerColor != null) ButtonDefaults.buttonColors(containerColor = containerColor) else ButtonDefaults.buttonColors(),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content
    )
}

@Composable
fun SpringOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    animationsEnabled: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val tokens = LocalDesktopTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (animationsEnabled && pressed && enabled) 0.97f else 1f,
        animationSpec = DesktopSpringFloat,
        label = "outlined_btn_scale"
    )
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tokens.border),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content
    )
}
