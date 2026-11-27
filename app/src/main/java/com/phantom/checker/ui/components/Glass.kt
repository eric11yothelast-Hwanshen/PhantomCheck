package com.phantom.checker.ui.components

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.platform.LocalViewConfiguration
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource

/**
 * 玻璃態元件：API 31+ (S) 使用 Haze 2.x 背景模糊＋高光描邊；
 * 低版本自動降級為半透明壓色（不呼叫進階 API，保證 API 26+ 不崩潰）。
 */

/** 標記為模糊來源（放在可捲動內容的最上層容器） */
@Composable
fun Modifier.glassSource(state: HazeState): Modifier = this.hazeSource(state)

/**
 * 玻璃表面修飾詞：API 31+ 背景模糊；低版本半透明壓色＋描邊。
 */
@Composable
fun Modifier.glassSurface(
    state: HazeState,
    fallbackColor: Color,
    outlineColor: Color? = null,
    cornerRadius: Dp = 24.dp,
): Modifier {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val highContrast = com.phantom.checker.ui.LocalHighContrast.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val borderWidth = if (highContrast) 1.5.dp else 1.dp
    val resolvedOutline = when {
        highContrast -> MaterialTheme.colorScheme.outline
        outlineColor != null -> outlineColor
        isDark -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    if (highContrast) {
        return clip(shape)
            .background(color = MaterialTheme.colorScheme.surface)
            .border(width = borderWidth, color = resolvedOutline, shape = shape)
    }
    val effectiveFallback = if (!isDark && fallbackColor.alpha < 0.92f) {
        fallbackColor.copy(alpha = 0.94f)
    } else {
        fallbackColor
    }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val surfaceColor = MaterialTheme.colorScheme.surface
        val blurStyle = remember(surfaceColor, isDark, effectiveFallback) {
            HazeBlurStyle {
                blurEnabled(true)
                blurRadius(20.dp)
                noiseFactor(0.15f)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(
                            surfaceColor.copy(alpha = if (isDark) 0.55f else 0.92f)
                        )
                    )
                )
                fallbackColorEffect(HazeColorEffect.tint(effectiveFallback))
            }
        }
        clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(state),
                style = blurStyle,
            )
            .border(width = borderWidth, color = resolvedOutline, shape = shape)
    } else {
        clip(shape)
            .background(color = effectiveFallback)
            .border(width = borderWidth, color = resolvedOutline, shape = shape)
    }
}

/**
 * 玻璃卡片容器：內容需先以 Modifier.glassSource(state) 標記為模糊來源。
 */
@Composable
fun GlassCard(
    glassState: HazeState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            .glassSurface(
                state = glassState,
                fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                cornerRadius = cornerRadius,
            ),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * 按壓微縮放微互動（#398 升級）：按壓時 spring 縮放至 [targetScale]，放開回彈；
 * 傳入 [glowShape] 時於按壓中以 draw-phase 疊加主色邊光。
 * 不消費點擊事件；手指移動超過 touch slop 或手勢被他人消費（捲動）時提前釋放，避免捲動中卡在縮小狀態。
 * 動畫值僅在 graphicsLayer / drawWithContent lambda 讀取，不觸發重組。
 */
fun Modifier.pressableScale(targetScale: Float = 0.96f, glowShape: Shape? = null): Modifier = composed {
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val glowColor = MaterialTheme.colorScheme.primary
    val slop = LocalViewConfiguration.current.touchSlop
    val safeTargetScale = if (targetScale.isFinite()) targetScale.coerceIn(0.5f, 1.2f) else 0.96f
    this
        .graphicsLayer {
            val p = if (press.value.isFinite()) press.value.coerceIn(0f, 1.2f) else 0f
            val s = 1f - (1f - safeTargetScale) * p
            scaleX = s
            scaleY = s
        }
        .then(
            if (glowShape != null) {
                Modifier.drawWithContent {
                    drawContent()
                    val p = if (press.value.isFinite()) press.value.coerceIn(0f, 1f) else 0f
                    if (p > 0f) {
                        val outline = glowShape.createOutline(size, layoutDirection, this)
                        drawOutline(outline, glowColor.copy(alpha = 0.07f * p))
                        drawOutline(
                            outline,
                            glowColor.copy(alpha = 0.35f * p),
                            style = Stroke(width = 1.5.dp.toPx()),
                        )
                    }
                }
            } else {
                Modifier
            },
        )
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                scope.launch {
                    press.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 800f))
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (change.isConsumed ||
                            (change.position - down.position).getDistance() > slop
                        ) {
                            break
                        }
                    }
                } finally {
                    scope.launch {
                        press.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 600f))
                    }
                }
            }
        }
}
