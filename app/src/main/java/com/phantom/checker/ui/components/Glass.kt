package com.phantom.checker.ui.components

import android.os.Build
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    val shape = RoundedCornerShape(cornerRadius)
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val resolvedOutline = outlineColor
        ?: if (isDark) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val surfaceColor = MaterialTheme.colorScheme.surface
        val blurStyle = remember(surfaceColor, isDark, fallbackColor) {
            HazeBlurStyle {
                blurEnabled(true)
                blurRadius(20.dp)
                noiseFactor(0.15f)
                colorEffects(
                    listOf(
                        HazeColorEffect.tint(
                            surfaceColor.copy(alpha = if (isDark) 0.55f else 0.72f)
                        )
                    )
                )
                fallbackColorEffect(HazeColorEffect.tint(fallbackColor))
            }
        }
        clip(shape)
            .hazeBlur(
                input = HazeInput.Sources(state),
                style = blurStyle,
            )
            .border(width = 1.dp, color = resolvedOutline, shape = shape)
    } else {
        clip(shape)
            .background(color = fallbackColor)
            .border(width = 1.dp, color = resolvedOutline, shape = shape)
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
 * 按壓微縮放微互動：按壓時縮放至 0.96，放開以 spring 回彈；不消費點擊事件。
 */
fun Modifier.pressableScale(targetScale: Float = 0.96f): Modifier = composed {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressableScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                isPressed = true
                try {
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                } finally {
                    isPressed = false
                }
            }
        }
}
