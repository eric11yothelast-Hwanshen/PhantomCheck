package com.phantom.checker.desktop

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
 * #468 桌面端深色玻璃設計系統（支援 6 種強調色、4 種預設漸層、自訂背景圖、密度與字級縮放、動畫開關）。
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
    containerColor: Color = DesktopPalette.GlassSurface,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, DesktopPalette.GlassBorder),
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
        border = BorderStroke(1.dp, DesktopPalette.GlassBorder),
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        content = content
    )
}
