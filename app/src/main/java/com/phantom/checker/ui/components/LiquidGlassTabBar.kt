package com.phantom.checker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

private const val MAX_INDEX_VELOCITY = 24f

private fun Float.finiteOr(fallback: Float): Float = if (isFinite()) this else fallback

/**
 * 流體水滴玻璃分頁列（S47）：僅替換底部分頁列的「視覺層」——
 * 選中指示器為 Canvas 繪製的液態水滴（Bézier 變形＋spring 位移，切換分頁時水滴從舊位置流到新位置），
 * 背景沿用 glassSurface（API 33+ Haze 毛玻璃、低版本半透明壓色——水滴動畫為純 Canvas，API 26+ 全版本可用）。
 * 分頁行為鐵律不動：route、navigate onClick、selectedTab 狀態保存全部由呼叫端傳入，本元件零導航邏輯。
 */
@Composable
fun LiquidGlassTabBar(
    tabCount: Int,
    selectedTabIndex: Int,
    glassState: dev.chrisbanes.haze.HazeState,
    tabs: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    /** S49 滑動／fling 放手後的分頁切換回呼（點擊行為零變更，仍走各分頁項 onClick） */
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
        val maxIndexInt = (tabCount - 1).coerceAtLeast(0)
        val safeSelectedIndex = if (tabCount > 0) selectedTabIndex.coerceIn(0, maxIndexInt) else 0
        // S49 拖曳狀態：拖曳中水滴直接跟隨手指（snapTo 驅動 animatedIndex，不等動畫），
        // 拖曳速度以「每秒索引數」估計（量綱照 S47-fix），放手後 spring 吸附最近分頁並觸發 onSelect（含 fling 投影）。
        // pointerInput 只以 tabCount 為 key 重啟，避免閉包捕捉過時值
        val currentSelectedTabIndex by rememberUpdatedState(safeSelectedIndex)
        val currentOnSelect by rememberUpdatedState(onSelect)
        var isDragging by remember { mutableStateOf(false) }
        var dragIndex by remember { mutableFloatStateOf(safeSelectedIndex.toFloat()) }
        var dragVelocityIndex by remember { mutableFloatStateOf(0f) }
        // S51：速度改用 Compose VelocityTracker（uptimeMillis 毫秒 Long，無 Float 精度損失）——
        // 舊法以 System.nanoTime().toFloat() 算時間差，開機奈秒數（約 10^14）轉 Float 後精度只有 8~16ms，
        // dt 常為 0 被硬補 → 瞬時速度暴增 → fling 投影永遠夾到第一個或最後一個分頁。
        val velocityTracker = remember { VelocityTracker() }
        val dropletTop = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        val dropletBottom = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
        val sheenColor = remember { Color.White.copy(alpha = 0.35f) }
        val rimColor = remember { Color.White.copy(alpha = 0.3f) }
        val dropletColors = remember(dropletTop, dropletBottom) { listOf(dropletTop, dropletBottom) }
        val sheenColors = remember(sheenColor) { listOf(sheenColor, Color.Transparent) }
        val dropletPath = remember { Path() }
        val rimStroke = remember(density) { Stroke(width = with(density) { 1.dp.toPx() }) }
        val brushCache = remember(dropletColors, sheenColors) {
            object {
                var lastTop = Float.NaN
                var lastBottom = Float.NaN
                var lastHalfH = Float.NaN
                var dropletBrush: Brush? = null
                var sheenBrush: Brush? = null
            }
        }
        val animatedIndex = remember { Animatable(safeSelectedIndex.toFloat()) }
        val dropletSpring = remember { PhantomMotion.NavIndicatorSpring }
        LaunchedEffect(safeSelectedIndex, tabCount) {
            if (tabCount <= 0) return@LaunchedEffect
            val target = safeSelectedIndex.toFloat()
            if (!animatedIndex.value.isFinite()) {
                animatedIndex.snapTo(target)
            } else if (animatedIndex.targetValue != target) {
                animatedIndex.animateTo(
                    target,
                    dropletSpring,
                )
            }
        }
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(top = if (compact) 4.dp else 6.dp, bottom = if (compact) 4.dp else 6.dp)
        ) {
            if (tabCount <= 0 || size.width <= 0f || size.height <= 0f) return@Canvas
            val barWidthPx = barWidth.toPx().let { if (it.isFinite() && it > 0f) it else size.width }
            val canvasTabWidthPx = barWidthPx / tabCount
            if (!canvasTabWidthPx.isFinite() || canvasTabWidthPx <= 0f) return@Canvas
            val maxIndexFloat = (tabCount - 1).toFloat()
            val rawIndex = if (isDragging) dragIndex else animatedIndex.value
            val index = rawIndex.finiteOr(safeSelectedIndex.toFloat()).coerceIn(0f, maxIndexFloat)
            val rawVelocity = if (isDragging) dragVelocityIndex else animatedIndex.velocity
            val velocity = rawVelocity.finiteOr(0f).coerceIn(-MAX_INDEX_VELOCITY, MAX_INDEX_VELOCITY)
            val centerX = canvasTabWidthPx * (index + 0.5f)
            // N5：5 項指示器寬度＝itemWidth − 16dp、高 32dp（保留水滴液態變形）
            val insetPx = PhantomMotion.NavIndicatorHorizontalInsetDp.toPx()
            val baseWidth = (canvasTabWidthPx - insetPx).coerceAtLeast(36.dp.toPx())
            val baseHeight = (if (compact) 36.dp else 44.dp).toPx()
            // 液態變形：移動速度越快，水滴沿移動方向拉得越長、高度越扁（Bézier 控制點隨之變形）
            val stretch = min(0.38f, abs(velocity) / 6f).finiteOr(0f).coerceIn(0f, 0.38f)
            val halfWidth = baseWidth * (1f + stretch) / 2f
            val halfHeight = baseHeight * (1f - stretch * 0.45f) / 2f
            val centerY = size.height / 2f
            if (!centerX.isFinite() || !centerY.isFinite() ||
                !halfWidth.isFinite() || !halfHeight.isFinite() ||
                halfWidth <= 0f || halfHeight <= 0f
            ) {
                return@Canvas
            }
            val left = centerX - halfWidth
            val right = centerX + halfWidth
            val top = centerY - halfHeight
            val bottom = centerY + halfHeight
            if (!left.isFinite() || !right.isFinite() || !top.isFinite() || !bottom.isFinite()) {
                return@Canvas
            }
            dropletPath.rewind()
            dropletPath.moveTo(left, centerY)
            dropletPath.cubicTo(left, top, left + halfWidth * 0.6f, top, centerX, top)
            dropletPath.cubicTo(right - halfWidth * 0.6f, top, right, top, right, centerY)
            dropletPath.cubicTo(right, bottom, right - halfWidth * 0.6f, bottom, centerX, bottom)
            dropletPath.cubicTo(left + halfWidth * 0.6f, bottom, left, bottom, left, centerY)
            dropletPath.close()

            val safeEndY = bottom.coerceAtLeast(top + 1f)
            val safeSheenEndY = (top + halfHeight).coerceAtLeast(top + 1f)
            if (brushCache.dropletBrush == null ||
                brushCache.lastTop != top ||
                brushCache.lastBottom != safeEndY ||
                brushCache.lastHalfH != halfHeight
            ) {
                brushCache.lastTop = top
                brushCache.lastBottom = safeEndY
                brushCache.lastHalfH = halfHeight
                brushCache.dropletBrush = Brush.verticalGradient(
                    colors = dropletColors,
                    startY = top,
                    endY = safeEndY,
                )
                brushCache.sheenBrush = Brush.verticalGradient(
                    colors = sheenColors,
                    startY = top,
                    endY = safeSheenEndY,
                )
            }
            drawPath(
                path = dropletPath,
                brush = brushCache.dropletBrush!!,
            )
            // 玻璃高光：水滴上緣一道細微的白色漸層
            drawPath(
                path = dropletPath,
                brush = brushCache.sheenBrush!!,
            )
            // 玻璃外緣 rim：同一條 path 描一圈細白邊
            drawPath(
                path = dropletPath,
                color = rimColor,
                style = rimStroke,
            )
        }
        Row(
            // S50：手勢掛在 Row（各分頁項 clickable 的祖先）——Compose 命中測試只沿「命中目標＋祖先」
            // 派發事件，掛在被 Row 覆蓋的兄弟層（Canvas）永遠收不到事件；拖曳過 touch slop 後
            // consume() 會讓子層 clickable 取消 tap，點擊行為零變更、拖曳不誤觸切頁。
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(tabCount) {
                    if (tabCount <= 0) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { _ ->
                            val maxIdx = (tabCount - 1).toFloat()
                            dragIndex = animatedIndex.value
                                .finiteOr(currentSelectedTabIndex.toFloat())
                                .coerceIn(0f, maxIdx)
                            isDragging = true
                            dragVelocityIndex = 0f
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            val maxIdxInt = tabCount - 1
                            val fallbackIdx = currentSelectedTabIndex.coerceIn(0, maxIdxInt)
                            val releaseIndex = dragIndex
                                .finiteOr(fallbackIdx.toFloat())
                                .coerceIn(0f, maxIdxInt.toFloat())
                            isDragging = false
                            // fling：以放手速度（px/秒 → 每秒索引數）投影目標分頁；慢速直接吸附最近分頁
                            val effectiveTabWidthPx = size.width.toFloat() / tabCount
                            val rawVelX = velocityTracker.calculateVelocity().x
                            val indexVelocity = if (effectiveTabWidthPx.isFinite() && effectiveTabWidthPx > 0f && rawVelX.isFinite()) {
                                (rawVelX / effectiveTabWidthPx)
                                    .finiteOr(0f)
                                    .coerceIn(-MAX_INDEX_VELOCITY, MAX_INDEX_VELOCITY)
                            } else {
                                0f
                            }
                            val projected = releaseIndex + indexVelocity * 0.25f
                            val target = when {
                                !projected.isFinite() -> fallbackIdx
                                abs(indexVelocity) < 1f -> releaseIndex.roundToInt().coerceIn(0, maxIdxInt)
                                else -> projected.roundToInt().coerceIn(0, maxIdxInt)
                            }
                            dragVelocityIndex = 0f
                            scope.launch {
                                animatedIndex.snapTo(releaseIndex)
                                animatedIndex.animateTo(
                                    target.toFloat(),
                                    dropletSpring,
                                )
                            }
                            if (target != currentSelectedTabIndex) currentOnSelect(target)
                        },
                        onDragCancel = {
                            val maxIdxInt = tabCount - 1
                            val fallbackIdx = currentSelectedTabIndex.coerceIn(0, maxIdxInt)
                            val cancelIndex = dragIndex
                                .finiteOr(fallbackIdx.toFloat())
                                .coerceIn(0f, maxIdxInt.toFloat())
                            isDragging = false
                            dragVelocityIndex = 0f
                            scope.launch {
                                animatedIndex.snapTo(cancelIndex)
                                animatedIndex.animateTo(
                                    fallbackIdx.toFloat(),
                                    dropletSpring,
                                )
                            }
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            val effectiveTabWidthPx = size.width.toFloat() / tabCount
                            if (!effectiveTabWidthPx.isFinite() || effectiveTabWidthPx <= 0f) {
                                return@detectHorizontalDragGestures
                            }
                            val x = change.position.x
                            if (!x.isFinite()) return@detectHorizontalDragGestures
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val rawVelX = velocityTracker.calculateVelocity().x
                            val nextVelocity = if (rawVelX.isFinite()) {
                                (rawVelX / effectiveTabWidthPx)
                                    .finiteOr(0f)
                                    .coerceIn(-MAX_INDEX_VELOCITY, MAX_INDEX_VELOCITY)
                            } else {
                                0f
                            }
                            dragVelocityIndex = nextVelocity
                            val nextIndex = x / effectiveTabWidthPx - 0.5f
                            if (nextIndex.isFinite()) {
                                dragIndex = nextIndex.coerceIn(0f, (tabCount - 1).toFloat())
                            }
                        },
                    )
                }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(if (compact) 56.dp else AuroraTokens.NavBarHeight),
        ) {
            tabs()
        }
    }
}

/** 單一分頁項：可點擊（無漣漪延遲，點擊立即回呼）＋圖示（或自訂頭像縮圖）＋標籤；選中色由呼叫端傳入。 */
@Composable
fun LiquidGlassTabBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    customIcon: (@Composable () -> Unit)? = null,
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 180),
        label = "tabContentColor",
    )
    // M8：選中時圖示輕彈放大（draw phase 讀取，不重組）
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = PhantomMotion.NavIndicatorSpring,
        label = "tabIconScale",
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        // S50：內容垂直置中（原本頂部對齊導致下方約 28dp 空、水滴置中後視覺空洞）
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = if (compact) 1.dp else 2.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = label,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier.graphicsLayer {
                scaleX = iconScale
                scaleY = iconScale
            },
            contentAlignment = Alignment.Center,
        ) {
            if (customIcon != null) {
                customIcon()
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = if (compact) Modifier.size(20.dp) else Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.height(if (compact) 1.dp else 2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            softWrap = false,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
