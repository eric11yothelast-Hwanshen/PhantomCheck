package com.phantom.checker.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/** 滑出動作列的兩個錨點狀態 */
enum class RevealValue { Settled, Revealed }

private val DefaultRevealDistance = 96.dp
private val ActionAreaHorizontalPadding = 12.dp
private val ActionSpacing = 8.dp

/**
 * 滑出動作列：內容可向左滑到 Revealed 錨點停住，露出右側動作鈕（不會消失、不會自動彈回）。
 * 單一展開由外部 revealed／onReveal／onCollapse 控制（同一時間只允許一列 Revealed）。
 * AnchoredDraggable 為水平手勢（Orientation.Horizontal），與垂直 LazyColumn 捲動不衝突；
 * 展開狀態下由前景透明攔截層吸收點擊收回，避免與子層 clickable 互搶，同時讓水平滑回手勢透傳給 anchoredDraggable。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeToRevealRow(
    revealed: Boolean,
    onReveal: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    revealDistance: Dp = DefaultRevealDistance,
    actions: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val decaySpec = rememberSplineBasedDecay<Float>()
    val currentRevealed by rememberUpdatedState(revealed)
    val currentOnReveal by rememberUpdatedState(onReveal)
    val currentOnCollapse by rememberUpdatedState(onCollapse)

    val state = remember {
        AnchoredDraggableState(
            initialValue = if (revealed) RevealValue.Revealed else RevealValue.Settled,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = spring(),
            decayAnimationSpec = decaySpec,
        )
    }
    val rawRevealPx = with(density) { revealDistance.toPx() }
    val revealPx = if (rawRevealPx.isFinite() && rawRevealPx > 0f) rawRevealPx else 1f
    var lastRevealPx by remember { mutableFloatStateOf(Float.NaN) }

    SideEffect {
        if (lastRevealPx != revealPx) {
            lastRevealPx = revealPx
            state.updateAnchors(
                DraggableAnchors {
                    RevealValue.Settled at 0f
                    RevealValue.Revealed at -revealPx
                },
                newTarget = if (currentRevealed) RevealValue.Revealed else RevealValue.Settled,
            )
        }
    }

    // 雙向同步：向左滑開回報 onReveal，向右滑回 Settled 時回報 onCollapse，避免外部 revealed 卡在 true
    LaunchedEffect(state.currentValue) {
        when (state.currentValue) {
            RevealValue.Revealed -> if (!currentRevealed) currentOnReveal()
            RevealValue.Settled -> if (currentRevealed) currentOnCollapse()
        }
    }

    // 外部狀態同步：點擊內容／列表空白處、另一列展開或列表重用時對齊錨點
    LaunchedEffect(revealed) {
        if (!revealed && (state.currentValue != RevealValue.Settled || state.targetValue != RevealValue.Settled)) {
            state.animateTo(RevealValue.Settled)
        } else if (revealed && state.currentValue != RevealValue.Revealed) {
            state.animateTo(RevealValue.Revealed)
        }
    }

    val isActionInteractive by remember {
        derivedStateOf { revealed || (state.offset.isFinite() && state.offset < -0.5f) }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = ActionAreaHorizontalPadding)
                .graphicsLayer {
                    val currentOffset = if (state.offset.isFinite()) state.offset else 0f
                    alpha = (-currentOffset / revealPx).coerceIn(0f, 1f)
                },
            horizontalArrangement = Arrangement.spacedBy(ActionSpacing, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isActionInteractive) {
                actions()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset {
                    val currentOffset = if (state.offset.isFinite()) state.offset else 0f
                    IntOffset(currentOffset.roundToInt(), 0)
                }
                .anchoredDraggable(state, Orientation.Horizontal),
        ) {
            content()
            if (revealed) {
                // 展開時覆蓋在 content 之上：純點擊立即收回而不誤觸子層 clickable；
                // 水平滑回或垂直捲動會由祖先 anchoredDraggable / LazyColumn 消費，waitForUpOrCancellation 回傳 null。
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                val up = waitForUpOrCancellation()
                                if (up != null && !up.isConsumed) {
                                    up.consume()
                                    currentOnCollapse()
                                }
                            }
                        },
                )
            }
        }
    }
}
