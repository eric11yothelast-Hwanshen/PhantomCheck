package com.phantom.checker.ui.components

import androidx.compose.animation.core.spring
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** 滑出動作列的兩個錨點狀態 */
enum class RevealValue { Settled, Revealed }

private val DefaultRevealDistance = 96.dp
private val ActionAreaHorizontalPadding = 12.dp
private val ActionSpacing = 8.dp

/**
 * 滑出動作列：內容可向左滑到 Revealed 錨點停住，露出右側動作鈕（不會消失、不會自動彈回）。
 * 單一展開由外部 revealed／onReveal／onCollapse 控制（同一時間只允許一列 Revealed）。
 * AnchoredDraggable 為水平手勢（Orientation.Horizontal），與垂直 LazyColumn 捲動不衝突。
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
    val state = remember {
        AnchoredDraggableState(
            initialValue = RevealValue.Settled,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 100.dp.toPx() } },
            snapAnimationSpec = spring(),
            decayAnimationSpec = decaySpec,
        )
    }
    val revealPx = with(density) { revealDistance.toPx() }

    SideEffect {
        state.updateAnchors(
            DraggableAnchors {
                RevealValue.Settled at 0f
                RevealValue.Revealed at -revealPx
            },
        )
    }

    // 停在 Revealed 時回報外部（單一展開狀態由外部 state 管理）
    LaunchedEffect(state.currentValue) {
        if (state.currentValue == RevealValue.Revealed) onReveal()
    }

    // 外部收回：點擊內容／列表空白處，或另一列展開時
    LaunchedEffect(revealed) {
        if (!revealed && state.currentValue == RevealValue.Revealed) {
            state.animateTo(RevealValue.Settled)
        }
    }

    val isActionInteractive by remember {
        derivedStateOf { revealed || (!state.offset.isNaN() && state.offset < -0.5f) }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = ActionAreaHorizontalPadding)
                .graphicsLayer {
                    val currentOffset = if (state.offset.isNaN()) 0f else state.offset
                    alpha = if (revealPx > 0f) (-currentOffset / revealPx).coerceIn(0f, 1f) else 0f
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
                    IntOffset(
                        if (state.offset.isNaN()) 0 else state.offset.roundToInt(),
                        0,
                    )
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = revealed,
                ) { onCollapse() }
                .anchoredDraggable(state, Orientation.Horizontal),
        ) {
            content()
        }
    }
}
