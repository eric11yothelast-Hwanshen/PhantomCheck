package com.phantom.checker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 流體水滴玻璃分頁列：僅替換底部分頁列的「視覺層」——
 * 選中指示器為 Canvas 繪製的液態水滴（Bézier 變形＋spring 位移，切換分頁時水滴從舊位置流到新位置），
 * 背景沿用 glassSurface（API 33+ Haze 毛玻璃、低版本半透明壓色——水滴動畫為純 Canvas，API 26+ 全版本可用）。
 * 分頁行為鐵律不動：route、onClick、selectedTab 狀態保存全部由呼叫端傳入，本元件零導航邏輯。
 */
@Composable
fun LiquidGlassTabBar(
    tabCount: Int,
    selectedTabIndex: Int,
    glassState: HazeState,
    tabs: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit = {},
) {
    BoxWithConstraints(
        modifier = modifier.glassSurface(
            state = glassState,
            fallbackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            cornerRadius = 0.dp,
        ),
    ) {
        val barWidth = maxWidth
        val scope = rememberCoroutineScope()
        val density = LocalDensity.current
        val tabWidthPx = if (tabCount > 0) {
            with(density) { barWidth.toPx() } / tabCount
        } else {
            0f
        }
        val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
        val currentOnSelect by rememberUpdatedState(onSelect)
        var isDragging by remember { mutableStateOf(false) }
        var dragVelocityIndex by remember { mutableFloatStateOf(0f) }
        var dragSnapJob by remember { mutableStateOf<Job?>(null) }
        val velocityTracker = remember { VelocityTracker() }
        val dropletTop = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        val dropletBottom = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        val sheenColor = Color.White.copy(alpha = 0.35f)
        val animatedIndex = remember { Animatable(selectedTabIndex.toFloat()) }
        LaunchedEffect(selectedTabIndex) {
            if (animatedIndex.targetValue != selectedTabIndex.toFloat()) {
                animatedIndex.animateTo(
                    selectedTabIndex.toFloat(),
                    spring(
                        dampingRatio = 0.78f,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            if (tabCount <= 0) return@Canvas
            val currentTabWidthPx = barWidth.toPx() / tabCount
            val index = animatedIndex.value
            val velocity = if (isDragging) dragVelocityIndex else animatedIndex.velocity
            val centerX = currentTabWidthPx * (index + 0.5f)
            val baseWidth = currentTabWidthPx * 0.72f
            val baseHeight = 56.dp.toPx()
            val stretch = min(0.38f, abs(velocity) / 6f)
            val halfWidth = baseWidth * (1f + stretch) / 2f
            val halfHeight = baseHeight * (1f - stretch * 0.45f) / 2f
            val centerY = size.height / 2f
            val left = centerX - halfWidth
            val right = centerX + halfWidth
            val top = centerY - halfHeight
            val bottom = centerY + halfHeight
            val path = Path().apply {
                moveTo(left, centerY)
                cubicTo(left, top, left + halfWidth * 0.6f, top, centerX, top)
                cubicTo(right - halfWidth * 0.6f, top, right, top, right, centerY)
                cubicTo(right, bottom, right - halfWidth * 0.6f, bottom, centerX, bottom)
                cubicTo(left + halfWidth * 0.6f, bottom, left, bottom, left, centerY)
                close()
            }
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(dropletTop, dropletBottom),
                    startY = top,
                    endY = bottom,
                ),
            )
            drawPath(
                path = path,
                brush = Brush.verticalGradient(
                    colors = listOf(sheenColor, Color.Transparent),
                    startY = top,
                    endY = top + halfHeight,
                ),
            )
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.3f),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(tabCount) {
                    if (tabCount <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { _ ->
                            isDragging = true
                            dragVelocityIndex = 0f
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            isDragging = false
                            val indexVelocity = velocityTracker.calculateVelocity().x / tabWidthPx
                            val projected = animatedIndex.value + indexVelocity * 0.25f
                            val target = if (abs(indexVelocity) < 1f) {
                                animatedIndex.value.roundToInt()
                            } else {
                                projected.roundToInt().coerceIn(0, tabCount - 1)
                            }
                            dragVelocityIndex = 0f
                            scope.launch {
                                animatedIndex.animateTo(
                                    target.toFloat(),
                                    spring(
                                        dampingRatio = 0.78f,
                                        stiffness = Spring.StiffnessMediumLow,
                                    ),
                                )
                            }
                            if (target != currentSelectedTabIndex) currentOnSelect(target)
                        },
                        onDragCancel = {
                            isDragging = false
                            dragVelocityIndex = 0f
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            dragVelocityIndex = velocityTracker.calculateVelocity().x / tabWidthPx
                            val x = change.position.x
                            val targetIndex = (x / tabWidthPx - 0.5f)
                                .coerceIn(0f, (tabCount - 1).toFloat())
                            dragSnapJob?.cancel()
                            dragSnapJob = scope.launch { animatedIndex.snapTo(targetIndex) }
                        },
                    )
                }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(80.dp),
        ) {
            tabs()
        }
    }
}

/** 單一分頁項：可點擊（無漣漪延遲，點擊立即回呼）＋圖示＋標籤；選中色由呼叫端傳入。 */
@Composable
fun LiquidGlassTabBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
            maxLines = 1,
        )
    }
}
