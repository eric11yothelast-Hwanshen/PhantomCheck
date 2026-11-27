package com.phantom.checker.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.phantom.checker.R
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * 流體切換開關（FluidSwitch，#398 Aurora 版）：
 * - spring 物理位移（略帶回彈）；按壓時 thumb 變形（squish）
 * - 軌道／thumb 顏色與位移共用同一個 progress，於 draw phase 插值：點擊、拖曳、回彈全程連續變色
 * - 開啟時：軌道外暈＋thumb 內浮現勾勾（路徑隨 progress 描繪）
 * - 拖曳超過一半閾值才切換，放開回彈（行為語意與舊版完全相同）
 * - 所有動畫值在 drawBehind／graphicsLayer lambda 讀取，不觸發重組；動畫縮放＝0 時由 Compose 直接跳終值
 */
@Composable
fun FluidSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val resCtx = LocalContext.current
    val trackWidth = 56.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val travel = trackWidth - thumbSize - 8.dp

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scope = rememberCoroutineScope()
    val currentChecked by rememberUpdatedState(checked)
    val currentOnCheckedChange by rememberUpdatedState(onCheckedChange)

    val thumbSpring = remember {
        spring<Float>(
            dampingRatio = 0.55f,
            stiffness = 500f,
        )
    }

    // 單一進度來源（0f~1f）：點擊與拖曳放手皆以 spring 平滑過渡，避免拖曳中斷或回彈瞬跳
    val progressAnim = remember { Animatable(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragPos by remember { mutableFloatStateOf(if (checked) 1f else 0f) }
    val checkPath = remember { Path() }

    LaunchedEffect(checked, dragging) {
        if (!dragging) {
            progressAnim.animateTo(
                targetValue = if (checked) 1f else 0f,
                animationSpec = thumbSpring,
            )
        }
    }

    // 按壓 squish：壓力越大越扁（拖曳中也維持微按壓回饋）
    val isSquished = enabled && (pressed || dragging)
    val thumbWidth by animateDpAsState(
        targetValue = if (isSquished) thumbSize + 4.dp else thumbSize,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "thumbWidth",
    )
    val thumbHeight by animateDpAsState(
        targetValue = if (isSquished) thumbSize - 3.dp else thumbSize,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f),
        label = "thumbHeight",
    )

    fun commitDrag(finalChecked: Boolean) {
        val startPos = if (dragPos.isFinite()) dragPos.coerceIn(0f, 1f) else if (currentChecked) 1f else 0f
        dragging = false
        if (finalChecked != currentChecked) {
            currentOnCheckedChange(finalChecked)
        }
        scope.launch {
            progressAnim.snapTo(startPos)
            progressAnim.animateTo(
                targetValue = if (finalChecked) 1f else 0f,
                animationSpec = thumbSpring,
            )
        }
    }

    val colors = MaterialTheme.colorScheme
    val trackOn = colors.primary
    val trackOff = colors.surfaceVariant
    val thumbOn = colors.onPrimary
    val thumbOff = colors.outline
    val stateAlpha = if (enabled) 1f else 0.4f

    /** 統一進度（0..1）：draw phase 呼叫；拖曳中取同步手指進度，否則取 spring 動畫進度 */
    fun progressNow(): Float {
        val raw = if (dragging) dragPos else progressAnim.value
        return if (raw.isFinite()) raw.coerceIn(0f, 1f) else if (checked) 1f else 0f
    }

    Box(
        modifier = modifier
            .size(trackWidth, trackHeight)
            .drawBehind {
                val p = progressNow()
                drawRoundRect(
                    color = lerp(trackOff, trackOn, p).let { it.copy(alpha = it.alpha * stateAlpha) },
                    cornerRadius = CornerRadius(size.height / 2f),
                )
                if (p > 0.01f && enabled) {
                    val grow = 2.dp.toPx()
                    drawRoundRect(
                        color = trackOn.copy(alpha = 0.22f * p),
                        topLeft = Offset(-grow, -grow),
                        size = Size(size.width + grow * 2f, size.height + grow * 2f),
                        cornerRadius = CornerRadius(size.height / 2f + grow),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
            ) {
                if (enabled && !dragging) currentOnCheckedChange(!currentChecked)
            }
            .semantics {
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                stateDescription = if (checked) resCtx.getString(R.string.common_on) else resCtx.getString(R.string.common_close)
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = {
                        val base = if (progressAnim.value.isFinite()) {
                            progressAnim.value.coerceIn(0f, 1f)
                        } else if (currentChecked) {
                            1f
                        } else {
                            0f
                        }
                        dragPos = base
                        dragging = true
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val travelPx = travel.toPx()
                        if (travelPx.isFinite() && travelPx > 0f && dragAmount.isFinite()) {
                            dragPos = (dragPos + dragAmount / travelPx).coerceIn(0f, 1f)
                        }
                    },
                    onDragEnd = {
                        // 拖曳超過閾值（一半）才切換
                        commitDrag(dragPos > 0.5f)
                    },
                    onDragCancel = {
                        commitDrag(currentChecked)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(start = 4.dp)
                .graphicsLayer {
                    translationX = travel.toPx() * progressNow()
                }
                .size(thumbSize)
                .graphicsLayer {
                    // squish 變形：按壓時橫向拉長、縱向壓扁
                    scaleX = thumbWidth / thumbSize
                    scaleY = thumbHeight / thumbSize
                }
                .drawBehind {
                    val p = progressNow()
                    val c = lerp(thumbOff, thumbOn, p)
                    // thumb 柔影
                    drawCircle(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.16f * stateAlpha),
                        radius = size.minDimension / 2f,
                        center = Offset(size.width / 2f, size.height / 2f + 1.dp.toPx()),
                    )
                    drawCircle(color = c.copy(alpha = stateAlpha))
                    // 勾勾：隨進度描繪（後半段才出現，避免過渡時雜亂）
                    val check = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
                    if (check > 0f) {
                        val w = size.width
                        val h = size.height
                        checkPath.rewind()
                        checkPath.moveTo(w * 0.29f, h * 0.52f)
                        checkPath.lineTo(w * 0.44f, h * 0.67f)
                        checkPath.lineTo(w * 0.72f, h * 0.36f)
                        drawPath(
                            path = checkPath,
                            color = trackOn.copy(alpha = check * stateAlpha),
                            style = Stroke(
                                width = 2.2.dp.toPx(),
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round,
                            ),
                        )
                    }
                },
        )
    }
}
