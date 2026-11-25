package com.phantom.checker.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import com.phantom.checker.util.BackgroundImageHelper
import com.phantom.checker.util.BackgroundLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.phantom.checker.data.AppGroupRow
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.data.MessageTagCrossRef
import com.phantom.checker.data.MessageTagRow
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.zIndex
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.phantom.checker.ui.components.GlassCard
import com.phantom.checker.ui.components.glassSource
import com.phantom.checker.ui.components.glassSurface
import com.phantom.checker.ui.components.phantomEnter
import com.phantom.checker.ui.components.phantomPopIn
import com.phantom.checker.ui.components.phantomPressable
import com.phantom.checker.ui.components.phantomSheen
import com.phantom.checker.ui.components.pressableScale
import com.phantom.checker.util.PhantomPrefs
import dev.chrisbanes.haze.HazeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource

@Composable
fun Modifier.phantomGlassSource(state: HazeState): Modifier {
    val ctx = LocalContext.current
    val isPowerSave = remember(ctx) {
        try {
            (ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }
    return if (ThemePreferences.isGlassEffectEnabled(ctx) && !isPowerSave) {
        this.glassSource(state)
    } else {
        this
    }
}

@Composable
fun Modifier.phantomGlassSurface(
    state: HazeState,
    fallbackColor: Color,
    outlineColor: Color? = null,
    cornerRadius: Dp = 24.dp
): Modifier {
    val ctx = LocalContext.current
    val isPowerSave = remember(ctx) {
        try {
            (ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val highContrast = LocalHighContrast.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassStrength = ThemePreferences.getGlassStrengthPercent(ctx)
    val cardBgMode = ThemePreferences.getCardBgMode(ctx)
    val cardBgAlpha = (ThemePreferences.getCardBgAlphaPercent(ctx) / 100f).coerceAtLeast(0.15f)
    val effectiveCardBgMode = if (!isDark && cardBgMode == PhantomPrefs.CardBgMode.DARK) {
        PhantomPrefs.CardBgMode.FOLLOW_GLASS
    } else {
        cardBgMode
    }
    val darkCardColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    val lightSafeFallback = if (!isDark && fallbackColor.alpha < 0.92f) {
        fallbackColor.copy(alpha = 0.94f)
    } else {
        fallbackColor
    }
    val resolvedFallback = when {
        highContrast -> MaterialTheme.colorScheme.surface
        effectiveCardBgMode == PhantomPrefs.CardBgMode.FOLLOW_GLASS -> lightSafeFallback
        effectiveCardBgMode == PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(
            alpha = if (isDark) cardBgAlpha.coerceAtLeast(0.35f) else cardBgAlpha.coerceAtLeast(0.86f)
        )
        else -> darkCardColor
    }

    if (highContrast) {
        return this
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(width = 1.5.dp, color = MaterialTheme.colorScheme.outline, shape = shape)
    }

    return if (ThemePreferences.isGlassEffectEnabled(ctx) && !isPowerSave) {
        val clippedBase = this.glassSurface(
            state = state,
            fallbackColor = resolvedFallback,
            outlineColor = outlineColor,
            cornerRadius = cornerRadius
        ).clip(shape)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val overlayColor: Color? = when (effectiveCardBgMode) {
                PhantomPrefs.CardBgMode.FOLLOW_GLASS -> if (!isDark) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                } else if (glassStrength > 0) {
                    MaterialTheme.colorScheme.surface.copy(alpha = (glassStrength / 100f) * 0.45f)
                } else null
                PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(
                    alpha = if (isDark) cardBgAlpha else cardBgAlpha.coerceAtLeast(0.86f)
                )
                PhantomPrefs.CardBgMode.DARK -> darkCardColor
            }
            if (overlayColor != null) {
                clippedBase.background(overlayColor, shape)
            } else {
                clippedBase
            }
        } else {
            clippedBase
        }
    } else {
        val resolvedOutline = outlineColor ?: if (isDark) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
        this
            .clip(shape)
            .background(resolvedFallback, shape)
            .border(width = 1.dp, color = resolvedOutline, shape = shape)
    }
}

/**
 * #222 浮動層專用不透明容器（歷史對話抽屜、記事規則面板等）。
 * 不使用 glassSurface/blur，避免圓角外溢出與底層文字透出；.clip(shape) 必在 .background() 之前。
 */
@Composable
fun PhantomFloatingSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val highContrast = LocalHighContrast.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val bgColor = when {
        highContrast -> MaterialTheme.colorScheme.surface
        isDark -> Color(0xEB12151E)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    }
    Box(
        modifier = modifier
            .clip(shape)
            .background(bgColor, shape)
            .let { m -> if (highContrast) m else m.phantomSheen(cornerRadius, isDark) }
            .border(
                width = if (highContrast) 1.5.dp else 1.dp,
                color = if (highContrast) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant,
                shape = shape
            )
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun PhantomGlassCard(
    glassState: HazeState,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: Dp = 16.dp,
    tintColor: Color? = null,
    overlapOpaqueBoost: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val ctx = LocalContext.current
    val isPowerSave = remember(ctx) {
        try {
            (ctx.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager)
                ?.isPowerSaveMode == true
        } catch (_: Exception) {
            false
        }
    }
    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val highContrast = LocalHighContrast.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassStrength = ThemePreferences.getGlassStrengthPercent(ctx)
    val cardBgMode = ThemePreferences.getCardBgMode(ctx)
    val cardBgAlpha = (ThemePreferences.getCardBgAlphaPercent(ctx) / 100f).coerceAtLeast(0.15f)
    // #510 淺色模式下禁用深色卡面覆蓋，避免子元素使用 onSurface（深字）壓在深卡上造成低對比
    val effectiveCardBgMode = if (!isDark && cardBgMode == PhantomPrefs.CardBgMode.DARK) {
        PhantomPrefs.CardBgMode.FOLLOW_GLASS
    } else {
        cardBgMode
    }
    val darkCardColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
    // #243/#244 重疊／堆疊模式防透字：深色不透明底提高至 0.96 以上 (#F6161822)
    val boostedOverlay = when {
        highContrast -> MaterialTheme.colorScheme.surface
        isDark -> Color(0xF6161822)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
    }
    val borderWidth = if (highContrast) 1.5.dp else 1.dp
    val outline = when {
        highContrast -> MaterialTheme.colorScheme.outline
        overlapOpaqueBoost -> if (isDark) Color.White.copy(alpha = 0.22f) else MaterialTheme.colorScheme.outlineVariant
        isDark -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val cardContentColor = MaterialTheme.colorScheme.onSurface

    if (!highContrast && ThemePreferences.isGlassEffectEnabled(ctx) && !isPowerSave) {
        val fallbackColor = if (overlapOpaqueBoost) {
            boostedOverlay
        } else when (effectiveCardBgMode) {
            PhantomPrefs.CardBgMode.FOLLOW_GLASS -> MaterialTheme.colorScheme.surface.copy(
                alpha = if (isDark) 0.88f else 0.95f
            )
            PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(
                alpha = if (isDark) cardBgAlpha.coerceAtLeast(0.35f) else cardBgAlpha.coerceAtLeast(0.86f)
            )
            PhantomPrefs.CardBgMode.DARK -> darkCardColor
        }
        val overlayColor: Color? = if (overlapOpaqueBoost) {
            boostedOverlay
        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            when (effectiveCardBgMode) {
                PhantomPrefs.CardBgMode.FOLLOW_GLASS -> if (!isDark) {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.93f)
                } else if (glassStrength > 0) {
                    MaterialTheme.colorScheme.surface.copy(alpha = (glassStrength / 100f) * 0.45f)
                } else null
                PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(
                    alpha = if (isDark) cardBgAlpha else cardBgAlpha.coerceAtLeast(0.86f)
                )
                PhantomPrefs.CardBgMode.DARK -> darkCardColor
            }
        } else {
            null
        }

        Box(
            modifier = modifier
                .let { m -> if (overlapOpaqueBoost) m.shadow(elevation = 8.dp, shape = shape, clip = false) else m }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
                .glassSurface(
                    state = glassState,
                    fallbackColor = fallbackColor,
                    cornerRadius = cornerRadius
                )
                .clip(shape)
                .let { m -> if (overlayColor != null) m.background(overlayColor, shape) else m }
                .let { m -> if (tintColor != null) m.background(tintColor, shape) else m }
                .phantomSheen(cornerRadius, isDark)
                .let { m -> if (overlapOpaqueBoost) m.border(width = borderWidth, color = outline, shape = shape) else m }
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides cardContentColor
            ) {
                Column(
                    modifier = Modifier.padding(contentPadding),
                    content = content
                )
            }
        }
    } else {
        val solidBg = when {
            highContrast -> MaterialTheme.colorScheme.surface
            overlapOpaqueBoost -> boostedOverlay
            effectiveCardBgMode == PhantomPrefs.CardBgMode.FOLLOW_GLASS -> MaterialTheme.colorScheme.surface.copy(
                alpha = if (isDark) 0.94f else 0.97f
            )
            effectiveCardBgMode == PhantomPrefs.CardBgMode.TRANSPARENT -> MaterialTheme.colorScheme.surface.copy(
                alpha = if (isDark) cardBgAlpha.coerceAtLeast(0.35f) else cardBgAlpha.coerceAtLeast(0.88f)
            )
            else -> darkCardColor
        }
        Box(
            modifier = modifier
                .let { m -> if (overlapOpaqueBoost && !highContrast) m.shadow(elevation = 8.dp, shape = shape, clip = false) else m }
                .clip(shape)
                .background(solidBg, shape)
                .let { m -> if (tintColor != null) m.background(tintColor, shape) else m }
                .let { m -> if (highContrast) m else m.phantomSheen(cornerRadius, isDark) }
                .border(width = borderWidth, color = outline, shape = shape)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides cardContentColor
            ) {
                Column(
                    modifier = Modifier.padding(contentPadding),
                    content = content
                )
            }
        }
    }
}

/**
 * #270 / #276 中央聚焦垂直滾輪堆疊（Vertical Center-Focused Card Stack）：
 * - 以 LazyColumn + LazyListState(cacheWindow = LazyLayoutCacheWindow(behindFraction = 1f, aheadFraction = 0.5f))
 *   + rememberSnapFlingBehavior(snapPosition = SnapPosition.Start) 實作連續慣性滑動與單調統一吸附（s_i = i * exposure）。
 * - 使用 remember { LazyListState(...) } 不保存捲動位置，每次進頁自第 0 張開始。
 * - 裁切層唯一性（A1）：唯一 clipToBounds 只允許在最外層 BoxWithConstraints，內部焦點容器與單卡插槽不得再疊加 clipToBounds。
 * - 上方卡片組合與繪製（A2）：透過對稱的 top/bottom contentPadding（leadingPaddingDp ≥ 3 * exposure 且與 trailingPaddingDp 互補，
 *   維持總捲動區間 [0, (n-1)*exposure] 不變），確保焦點上方 2~3 張卡片位於 LazyColumn visibleItemsInfo 內並穩定繪製。
 * - native item 高度 = exposure（40.dp），內部卡片以 wrapContentHeight(unbounded = true) + requiredHeight(effectiveCardHeight)
 *   （92.dp；fontScale ≥ 1.3f 時 104.dp）繪製完整高度。
 * - 頂／中／底三態錨點（B2/C1）：zone = min(1.5f, maxIndexFloat / 2f)，以線性 lerp 過渡：
 *   headProgress = (focusFloat / zone).coerceIn(0f, 1f)，tailProgress = ((maxIndexFloat - focusFloat) / zone).coerceIn(0f, 1f)，
 *   anchor = lerp(lerp(topAnchorPx, centerAnchorPx, headProgress), bottomAnchorPx, 1f - tailProgress)，嚴禁布林跳變或雙區重疊。
 * - scale = (1f - scaleStep * |u|).coerceAtLeast(0.82f)，pivot = TransformOrigin(0.5f, 0f) 頂緣對齊消除深層累積誤差。
 * - viewport 內 alpha 一律 1f，僅對真正超出容器上下邊界的部分以 edgeFade 像素距離線性淡出。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> CenterFocusCardStack(
    items: List<T>,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    cardHeight: Dp = 92.dp,
    exposure: Dp = 40.dp,
    scaleStep: Float = 0.04f,
    edgeFade: Dp = 40.dp,
    edgeGap: Dp = 6.dp,
    cardShape: Shape = RoundedCornerShape(20.dp),
    focusShadowBoost: Dp = 6.dp,
    cardContent: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return
    val listState = remember {
        LazyListState(
            cacheWindow = LazyLayoutCacheWindow(
                behindFraction = 1f,
                aheadFraction = 0.5f
            )
        )
    }
    val density = LocalDensity.current
    val effectiveCardHeight = if (density.fontScale >= 1.3f && cardHeight < 104.dp) 104.dp else cardHeight
    val topOrigin = remember { TransformOrigin(0.5f, 0f) }

    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val viewportHeightDp = maxHeight
        val viewportHeightPx = with(density) { viewportHeightDp.toPx() }
        val cardHeightPx = with(density) { effectiveCardHeight.toPx() }
        val exposurePx = with(density) { exposure.toPx() }.coerceAtLeast(1f)
        val fadeWindowPx = with(density) { edgeFade.toPx() }.coerceAtLeast(1f)
        val edgeGapPx = with(density) { edgeGap.toPx() }
        val focusShadowBoostPx = with(density) { focusShadowBoost.toPx() }.coerceAtLeast(0f)

        val topAnchorPx = edgeGapPx
        val centerAnchorPx = ((viewportHeightPx - cardHeightPx) / 2f).coerceAtLeast(topAnchorPx)
        val bottomAnchorPx = (viewportHeightPx - cardHeightPx - edgeGapPx).coerceAtLeast(topAnchorPx)
        val leadingPaddingDp = maxOf(
            (viewportHeightDp - effectiveCardHeight) / 2f,
            exposure * 3f
        ).coerceAtMost((viewportHeightDp - exposure).coerceAtLeast(0.dp))
        val leadingPaddingPx = with(density) { leadingPaddingDp.toPx() }
        val trailingPaddingDp = (viewportHeightDp - exposure - leadingPaddingDp).coerceAtLeast(0.dp)
        val maxIndexFloat = (items.size - 1).coerceAtLeast(0).toFloat()
        val maxPitchPx = (0.98f * cardHeightPx).coerceAtLeast(exposurePx)
        val isShortList = if (maxIndexFloat > 0f) {
            val neededPitchPx = (viewportHeightPx - 2f * edgeGapPx - cardHeightPx) / maxIndexFloat
            neededPitchPx > maxPitchPx
        } else {
            false
        }

        val decaySpec = rememberSplineBasedDecay<Float>()
        val flingBehavior = remember(listState, decaySpec) {
            snapFlingBehavior(
                snapLayoutInfoProvider = SnapLayoutInfoProvider(listState, SnapPosition.Start),
                decayAnimationSpec = decaySpec,
                snapAnimationSpec = spring(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            flingBehavior = flingBehavior,
            contentPadding = PaddingValues(top = leadingPaddingDp, bottom = trailingPaddingDp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            itemsIndexed(items, key = { _, item -> key(item) }) { index, item ->
                val itemZ by remember(index, listState, exposurePx, maxIndexFloat) {
                    derivedStateOf {
                        val focusFloat = (
                            listState.firstVisibleItemIndex.toFloat() +
                                listState.firstVisibleItemScrollOffset.toFloat() / exposurePx
                            ).coerceIn(0f, maxIndexFloat)
                        1000f - kotlin.math.abs(index.toFloat() - focusFloat)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(exposure)
                        .zIndex(itemZ)
                        .graphicsLayer {
                            val firstIdx = listState.firstVisibleItemIndex
                            val firstOff = listState.firstVisibleItemScrollOffset.toFloat()
                            val focusFloat = (firstIdx.toFloat() + firstOff / exposurePx)
                                .coerceIn(0f, maxIndexFloat)

                            val anchorPx = if (maxIndexFloat <= 0f || isShortList) {
                                topAnchorPx
                            } else {
                                val zone = minOf(1.5f, maxIndexFloat / 2f).coerceAtLeast(0.0001f)
                                val headProgress = (focusFloat / zone).coerceIn(0f, 1f)
                                val distFromTail = (maxIndexFloat - focusFloat).coerceAtLeast(0f)
                                val tailProgress = (distFromTail / zone).coerceIn(0f, 1f)
                                val pTail = 1f - tailProgress
                                lerp(lerp(topAnchorPx, centerAnchorPx, headProgress), bottomAnchorPx, pTail)
                            }

                            val pAbove = if (maxIndexFloat <= 0f || isShortList) {
                                exposurePx
                            } else {
                                maxOf(exposurePx, anchorPx / maxOf(focusFloat, 0.0001f))
                                    .coerceAtMost(maxPitchPx)
                            }
                            val pBelow = if (maxIndexFloat <= 0f || isShortList) {
                                exposurePx
                            } else {
                                val belowSpan = viewportHeightPx - edgeGapPx - anchorPx - cardHeightPx
                                maxOf(
                                    exposurePx,
                                    belowSpan / maxOf(maxIndexFloat - focusFloat, 0.0001f)
                                ).coerceAtMost(maxPitchPx)
                            }

                            val u = index.toFloat() - focusFloat
                            val absU = kotlin.math.abs(u)
                            val closeness = (1f - absU).coerceIn(0f, 1f)
                            val eased = FastOutSlowInEasing.transform(closeness)
                            val s = if (absU <= 1f) {
                                lerp(0.94f, 1f, eased)
                            } else {
                                (0.94f - scaleStep * (absU - 1f)).coerceAtLeast(0.82f)
                            }
                            transformOrigin = topOrigin
                            scaleX = s
                            scaleY = s
                            shape = cardShape
                            shadowElevation = focusShadowBoostPx * eased

                            val effectivePitchPx = if (u < 0f) pAbove else pBelow
                            val visualTopPx = anchorPx + u * effectivePitchPx
                            val measuredOffset = listState.layoutInfo.visibleItemsInfo
                                .firstOrNull { it.index == index }
                                ?.offset
                                ?.toFloat()
                            val nativeOffsetPx = measuredOffset
                                ?: ((index - firstIdx).toFloat() * exposurePx - firstOff)
                            translationY = visualTopPx - (leadingPaddingPx + nativeOffsetPx)

                            val visualHeightPx = cardHeightPx * s
                            val visualBottomPx = visualTopPx + visualHeightPx
                            val overflowTopPx = (-visualTopPx).coerceAtLeast(0f)
                            val overflowBottomPx = (visualBottomPx - viewportHeightPx).coerceAtLeast(0f)
                            val overflowPx = maxOf(overflowTopPx, overflowBottomPx)
                            alpha = if (overflowPx <= 0f) {
                                1f
                            } else {
                                (1f - overflowPx / fadeWindowPx).coerceIn(0f, 1f)
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .wrapContentHeight(align = Alignment.Top, unbounded = true)
                            .requiredHeight(effectiveCardHeight)
                            .align(Alignment.TopCenter)
                    ) {
                        cardContent(item)
                    }
                }
            }
        }
    }
}

/**
 * #244 浮動彈窗專用 Dialog 外框：
 * - 外層系統視窗背景強制完全透明（window?.setBackgroundDrawable(ColorDrawable(TRANSPARENT))），
 *   圓角框線以外 100% 透出後方桌布，根除「直角長方形黑底」。
 * - 深色不透明底色（Alpha ≈ 0.92）只畫在帶細圓角邊框的這一層（PhantomFloatingSurface），
 *   裁切順序：clip(shape) → background → border，確保沒有雙層背景疊加。
 */
@Composable
fun PhantomDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    contentPadding: Dp = 18.dp,
    maxContentHeightFraction: Float = 0.7f,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        }
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            PhantomFloatingSurface(
                modifier = modifier
                    .phantomPopIn()
                    .widthIn(max = maxWidth)
                    .heightIn(max = maxHeight * maxContentHeightFraction),
                cornerRadius = cornerRadius,
                contentPadding = contentPadding
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    content = content
                )
            }
        }
    }
}

/**
 * 頁面層背景圖片包裝：未設定任何背景圖時直接渲染 content（與 v2.1.0 逐像素一致）。
 * 已設定時於底層繪製降採樣裁切背景圖＋暗色可讀性遮罩（scrim），純裝飾、零觸控攔截。
 */
@Composable
fun PhantomBackgroundBox(
    layer: BackgroundLayer,
    glassState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val ctx = LocalContext.current
    val bgVersion = ThemePreferences.getBackgroundVersion()
    val bgFile = remember(ctx, layer, bgVersion) {
        BackgroundImageHelper.resolveBackgroundFile(ctx, layer)
    }
    val fallbackBg = MaterialTheme.colorScheme.background
    if (bgFile == null) {
        Box(
            modifier = modifier
                .background(fallbackBg)
                .phantomGlassSource(glassState),
            content = content
        )
        return
    }

    val highContrast = LocalHighContrast.current
    val isDark = fallbackBg.luminance() < 0.5f
    val scrimPref = ThemePreferences.getBackgroundScrimPercent(ctx)
    val scrimColor = if (isDark) Color.Black else fallbackBg
    val scrimAlpha = if (scrimPref < 0) {
        when {
            highContrast && isDark -> 0.84f
            highContrast && !isDark -> 0.94f
            isDark -> 0.60f
            else -> 0.84f
        }
    } else {
        val raw = (scrimPref / 100f).coerceIn(0f, 1f)
        when {
            highContrast && isDark -> raw.coerceAtLeast(0.78f)
            highContrast && !isDark -> raw.coerceAtLeast(0.88f)
            !isDark -> raw.coerceAtLeast(0.72f)
            else -> raw
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .background(fallbackBg)
            .phantomGlassSource(glassState)
    ) {
        val targetW = constraints.maxWidth
        val targetH = constraints.maxHeight
        var bitmap by remember(bgFile.absolutePath, bgFile.lastModified(), targetW, targetH, bgVersion) {
            mutableStateOf<ImageBitmap?>(null)
        }
        LaunchedEffect(bgFile.absolutePath, bgFile.lastModified(), targetW, targetH, bgVersion) {
            bitmap = BackgroundImageHelper.loadBackgroundBitmap(ctx, bgFile, targetW, targetH)
        }
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(scrimColor.copy(alpha = scrimAlpha))
            )
        }
        content()
    }
}

@Composable
fun rememberPhantomHaptic(): (HapticFeedbackType) -> Unit {
    val ctx = LocalContext.current
    return remember(ctx) {
        { type ->
            if (PhantomPrefs.isHapticFeedbackEnabled(ctx)) {
                try {
                    val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
                    } else {
                        @Suppress("DEPRECATION")
                        ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                    }
                    if (vibrator?.hasVibrator() == true) {
                        val isLong = type == HapticFeedbackType.LongPress
                        val effect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
                            vibrator.areAllEffectsSupported(
                                android.os.VibrationEffect.EFFECT_CLICK,
                                android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                            ) == android.os.Vibrator.VIBRATION_EFFECT_SUPPORT_YES
                        ) {
                            android.os.VibrationEffect.createPredefined(
                                if (isLong) android.os.VibrationEffect.EFFECT_HEAVY_CLICK
                                else android.os.VibrationEffect.EFFECT_CLICK
                            )
                        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            android.os.VibrationEffect.createWaveform(
                                if (isLong) longArrayOf(0L, 40L) else longArrayOf(0L, 25L),
                                if (isLong) intArrayOf(0, 255) else intArrayOf(0, 220),
                                -1
                            )
                        } else {
                            android.os.VibrationEffect.createOneShot(
                                if (isLong) 35L else 18L,
                                android.os.VibrationEffect.DEFAULT_AMPLITUDE
                            )
                        }
                        vibrator.vibrate(effect)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }
}

fun parseTagColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (_: Exception) {
        Color(0xFF7C3AED)
    }
}

fun hasSuspiciousUrlHint(riskReason: String): Boolean {
    if (riskReason.isBlank()) return false
    val lower = riskReason.lowercase(Locale.US)
    return listOf("連結", "網址", "url", "http", "短鏈", "短網", "ip", "表單", "釣魚網").any {
        lower.contains(it)
    }
}

private fun highlightMatches(text: String, query: String): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        var startIndex = 0
        while (startIndex < text.length) {
            val found = text.indexOf(q, startIndex = startIndex, ignoreCase = true)
            if (found < 0) break
            addStyle(
                style = SpanStyle(
                    background = Color(0xFF7C3AED),
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                ),
                start = found,
                end = found + q.length
            )
            startIndex = found + q.length
        }
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier
                    .phantomPopIn(initialScale = 0.6f)
                    .size(48.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .pressableScale()
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExportCsvDialog(
    appGroups: List<AppGroupRow>,
    onDismiss: () -> Unit,
    onConfirm: (packageName: String?, daysBack: Int?) -> Unit
) {
    var selectedPkg by remember { mutableStateOf<String?>(null) }
    var selectedDays by remember { mutableStateOf<Int?>(null) }
    var showAppMenu by remember { mutableStateOf(false) }

    val selectedAppLabel = if (selectedPkg == null) {
        stringResource(R.string.common_all_apps)
    } else {
        appGroups.firstOrNull { it.packageName == selectedPkg }?.appLabel ?: selectedPkg.orEmpty()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_s_07c286)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.common_s_2d2d5a), style = MaterialTheme.typography.labelMedium)
                Box {
                    OutlinedButton(
                        onClick = { showAppMenu = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(selectedAppLabel)
                    }
                    DropdownMenu(
                        expanded = showAppMenu,
                        onDismissRequest = { showAppMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.common_all_apps)) },
                            onClick = {
                                selectedPkg = null
                                showAppMenu = false
                            }
                        )
                        appGroups.forEach { app ->
                            DropdownMenuItem(
                                text = { Text(app.appLabel.ifBlank { app.packageName }) },
                                onClick = {
                                    selectedPkg = app.packageName
                                    showAppMenu = false
                                }
                            )
                        }
                    }
                }

                Text(stringResource(R.string.common_s_55f16d), style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedDays == 7,
                        onClick = { selectedDays = 7 },
                        label = { Text(stringResource(R.string.common_last_7_days)) }
                    )
                    FilterChip(
                        selected = selectedDays == 30,
                        onClick = { selectedDays = 30 },
                        label = { Text(stringResource(R.string.common_last_30_days)) }
                    )
                    FilterChip(
                        selected = selectedDays == null,
                        onClick = { selectedDays = null },
                        label = { Text(stringResource(R.string.common_all)) }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedPkg, selectedDays) }) {
                Text(stringResource(R.string.common_export))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
fun NotificationListenerGuideCard(
    onOpenSettings: () -> Unit,
    glassState: HazeState,
    modifier: Modifier = Modifier,
    onOpenAppDetails: (() -> Unit)? = null,
    shizukuReady: Boolean = false,
    onShizukuGrant: (() -> Unit)? = null,
    onReportBounds: ((Rect) -> Unit)? = null
) {
    val ctx = LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onReportBounds != null) {
                    Modifier.onGloballyPositioned { coords ->
                        onReportBounds(coords.boundsInRoot())
                    }
                } else {
                    Modifier
                }
            )
            .animateContentSize(spring())
            .phantomGlassSurface(
                state = glassState,
                fallbackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                outlineColor = MaterialTheme.colorScheme.error,
                cornerRadius = 20.dp
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.common_s_1356e9),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.common_s_493ced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.pressableScale(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(stringResource(R.string.common_s_dc348a))
                }
            }
            RestrictedSettingsTroubleshootSection(
                onOpenAppDetails = {
                    if (onOpenAppDetails != null) onOpenAppDetails()
                    else openAppDetailsSettings(ctx)
                },
                shizukuReady = shizukuReady,
                onShizukuGrant = { onShizukuGrant?.invoke() }
            )
        }
    }
}

@Composable
fun PostNotificationGuideCard(
    onRequestPermission: () -> Unit,
    glassState: HazeState,
    modifier: Modifier = Modifier
) {
    PhantomGlassCard(
        glassState = glassState,
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        contentPadding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.common_s_1a1f54),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.common_s_e1b948),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onRequestPermission,
            modifier = Modifier.pressableScale()
        ) {
            Text(stringResource(R.string.common_s_843dae))
        }
    }
}

@Composable
fun MessageCard(
    message: MessageEntity,
    glassState: HazeState,
    highlightAsScam: Boolean = false,
    matchedKeywords: List<String> = emptyList(),
    highlightQuery: String = "",
    hasNote: Boolean = false,
    hasTodo: Boolean = false,
    modifier: Modifier = Modifier
) {
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val isCompact = PhantomPrefs.getCardDensity(ctx) == com.phantom.checker.util.CardDensity.COMPACT
    val cardPad = if (isCompact) 8.dp else 12.dp
    val vertPad = if (isCompact) 2.dp else 4.dp
    val is24h = PhantomPrefs.is24HourFormat(ctx)
    val timeText = remember(message.timestamp, is24h) {
        PhantomPrefs.formatMessageTime(ctx, message.timestamp, includeDate = true)
    }

    val cardInner: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "[${message.appLabel}] ",
                    style = MaterialTheme.typography.labelSmall
                )
                Text(
                    text = highlightMatches(message.sender, highlightQuery),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (message.isStarred) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = stringResource(R.string.common_star),
                        tint = Color(0xFFE6A100),
                        modifier = Modifier.size(14.dp)
                    )
                }
                if (hasNote) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.common_noted),
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (hasTodo) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.common_todoed),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (message.riskLabel == "scam") {
                    Text(
                        text = stringResource(R.string.common_s_0fa4d6, message.riskScore),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                } else if (message.riskLabel == "suspicious") {
                    Text(
                        text = stringResource(R.string.common_s_a40ac4, message.riskScore),
                        color = Color(0xFFE6A100),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (message.isFiltered) {
                    Text(
                        text = stringResource(R.string.common_s_5be1f3),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            Text(
                text = timeText,
                style = MaterialTheme.typography.labelSmall,
                color = if (highlightAsScam) {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.75f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(if (isCompact) 2.dp else 4.dp))
        Text(
            text = highlightMatches(message.content, highlightQuery),
            style = MaterialTheme.typography.bodySmall,
            color = if (highlightAsScam) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = if (isCompact) 2 else 4,
            overflow = TextOverflow.Ellipsis
        )
        if (matchedKeywords.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.common_s_60e783, matchedKeywords.joinToString("、")),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (message.riskReason.isNotEmpty()) {
            Spacer(Modifier.height(if (isCompact) 2.dp else 4.dp))
            Text(
                text = stripMarkdown(message.riskReason),
                color = if (highlightAsScam) {
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = if (isCompact) 1 else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (message.remindAt != null && message.remindAt > System.currentTimeMillis()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.common_s_fabd99, PhantomPrefs.formatMessageTime(ctx, message.remindAt, includeDate = true)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        val otpCode = remember(message.content) {
            com.phantom.checker.util.OtpDetector.extractOtp(message.content)
        }
        if (otpCode != null) {
            Spacer(Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.pressableScale(0.95f),
                onClick = {
                    val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("otp_code", otpCode))
                    android.widget.Toast.makeText(ctx, resCtx.getString(R.string.common_s_8b8320, otpCode), android.widget.Toast.LENGTH_SHORT).show()
                }
            ) {
                Text(
                    text = stringResource(R.string.common_s_f1f3ce, otpCode),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }

    if (highlightAsScam) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = vertPad)
                .animateContentSize(spring())
                .phantomGlassSurface(
                    state = glassState,
                    fallbackColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f),
                    outlineColor = MaterialTheme.colorScheme.error,
                    cornerRadius = 20.dp
                )
                .phantomSheen(20.dp, MaterialTheme.colorScheme.background.luminance() < 0.5f)
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onErrorContainer
            ) {
                Column(modifier = Modifier.padding(cardPad), content = cardInner)
            }
        }
    } else {
        PhantomGlassCard(
            glassState = glassState,
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = vertPad),
            cornerRadius = 20.dp,
            contentPadding = cardPad,
            content = cardInner
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MessageTagChipRow(
    tags: List<MessageTagRow>,
    maxShow: Int = 3,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return
    val visible = tags.take(maxShow)
    val overflow = tags.size - visible.size
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        visible.forEach { tag ->
            val tagColor = parseTagColor(tag.colorHex)
            val isAuto = tag.source == MessageTagCrossRef.SOURCE_AUTO
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = tagColor.copy(alpha = 0.14f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(tagColor)
                    )
                    Text(
                        text = if (isAuto) "${tag.name} · AI" else tag.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        if (overflow > 0) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ReminderPickerDialog(
    onDismiss: () -> Unit,
    onSelectTimestamp: (Long) -> Unit
) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_s_1d7df5)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.common_s_039be4),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        onSelectTimestamp(System.currentTimeMillis() + 60L * 60_000L)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_s_dde716))
                }
                OutlinedButton(
                    onClick = {
                        val cal = java.util.Calendar.getInstance().apply {
                            set(java.util.Calendar.HOUR_OF_DAY, 20)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(java.util.Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        onSelectTimestamp(cal.timeInMillis)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_s_bee0be))
                }
                OutlinedButton(
                    onClick = {
                        val cal = java.util.Calendar.getInstance().apply {
                            add(java.util.Calendar.DAY_OF_YEAR, 1)
                            set(java.util.Calendar.HOUR_OF_DAY, 9)
                            set(java.util.Calendar.MINUTE, 0)
                            set(java.util.Calendar.SECOND, 0)
                            set(java.util.Calendar.MILLISECOND, 0)
                        }
                        onSelectTimestamp(cal.timeInMillis)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_s_e5bf9a))
                }
                Button(
                    onClick = {
                        val nowCal = java.util.Calendar.getInstance()
                        android.app.TimePickerDialog(
                            ctx,
                            { _, hourOfDay, minute ->
                                val target = java.util.Calendar.getInstance().apply {
                                    set(java.util.Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(java.util.Calendar.MINUTE, minute)
                                    set(java.util.Calendar.SECOND, 0)
                                    set(java.util.Calendar.MILLISECOND, 0)
                                    if (timeInMillis <= System.currentTimeMillis()) {
                                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                                    }
                                }
                                onSelectTimestamp(target.timeInMillis)
                            },
                            nowCal.get(java.util.Calendar.HOUR_OF_DAY),
                            nowCal.get(java.util.Calendar.MINUTE),
                            PhantomPrefs.is24HourFormat(ctx)
                        ).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.common_s_b22d53))
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * 兩段式加入白名單對話框：
 * 1. 選擇「此寄件者全部訊息」或「僅此則訊息」
 * 2. 顯示即將加入的規則內容（寄件者＋App＋內容摘錄）並確認寫入
 */
@Composable
fun WhitelistAddDialogFlow(
    sender: String,
    appLabel: String,
    packageName: String,
    content: String,
    onDismiss: () -> Unit,
    onConfirm: (isSenderScope: Boolean) -> Unit
) {
    val resCtx = LocalContext.current
    var selectedScopeIsSender by remember { mutableStateOf<Boolean?>(null) }

    if (selectedScopeIsSender == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.common_add_whitelist)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.common_s_35de80),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = { selectedScopeIsSender = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_s_791ca9, sender.ifBlank { resCtx.getString(R.string.common_s_877f9a) }))
                    }
                    OutlinedButton(
                        onClick = { selectedScopeIsSender = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.common_s_b8f568))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    } else {
        val isSender = selectedScopeIsSender == true
        val excerpt = content.replace("\n", " ").trim().take(80)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.common_s_76dd84)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (isSender) {
                            stringResource(R.string.common_s_25133c)
                        } else {
                            stringResource(R.string.common_s_52eee8)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(stringResource(R.string.common_s_c9cc80, sender.ifBlank { resCtx.getString(R.string.common_unknown) }), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(stringResource(R.string.common_s_4e959b, appLabel, packageName), style = MaterialTheme.typography.bodySmall)
                    if (excerpt.isNotBlank()) {
                        Text(stringResource(R.string.common_s_a3bca5, excerpt), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            },
            confirmButton = {
                Button(onClick = { onConfirm(isSender) }) {
                    Text(stringResource(R.string.common_confirm_add))
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedScopeIsSender = null }) {
                    Text(stringResource(R.string.common_back))
                }
            }
        )
    }
}

/**
 * #194 統一頂部迷你玻璃搜尋膠囊：36dp 高、wrap-content 寬、放大鏡＋「搜尋」（觸控目標 ≥ 48dp）。
 */
@Composable
fun PhantomSearchBarPill(
    glassState: HazeState,
    query: String,
    placeholder: String = stringResource(R.string.common_search),
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingActions: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null
) {
    val resCtx = LocalContext.current
    val shape = remember { RoundedCornerShape(18.dp) }
    val labelText = if (query.isNotBlank()) query else stringResource(R.string.common_search)
    Row(
        modifier = modifier.wrapContentWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .heightIn(min = 48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick
                )
                .semantics { contentDescription = resCtx.getString(R.string.common_s_330ac3, placeholder) },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier
                    .pressableScale(0.96f, shape)
                    .height(36.dp)
                    .widthIn(min = 92.dp, max = 160.dp)
                    .phantomGlassSurface(
                        state = glassState,
                        fallbackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                        cornerRadius = 18.dp
                    )
                    .phantomSheen(18.dp, MaterialTheme.colorScheme.background.luminance() < 0.5f)
                    .clip(shape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (query.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailingActions != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                content = trailingActions
            )
        }
    }
}

/**
 * #205 頂部下拉搜尋把手：22dp 高、無文字、中央 4dp×120dp 膠囊，支援下拉（anchoredDraggable）或點擊開啟搜尋。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhantomPullSearchHandle(
    glassState: HazeState,
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resCtx = LocalContext.current
    val density = LocalDensity.current
    val decaySpec = rememberSplineBasedDecay<Float>()
    val dragDistancePx = with(density) { 48.dp.toPx() }
    val state = remember {
        AnchoredDraggableState(
            initialValue = 0f,
            positionalThreshold = { distance: Float -> distance * 0.5f },
            velocityThreshold = { with(density) { 800.dp.toPx() } },
            snapAnimationSpec = spring(stiffness = 380f, dampingRatio = 0.85f),
            decayAnimationSpec = decaySpec
        )
    }

    SideEffect {
        state.updateAnchors(
            DraggableAnchors {
                0f at 0f
                1f at dragDistancePx
            }
        )
    }

    LaunchedEffect(state.currentValue) {
        if (state.currentValue == 1f) {
            onOpenSearch()
            state.snapTo(0f)
        }
    }

    val progress = if (dragDistancePx > 0f && !state.offset.isNaN()) {
        (state.offset / dragDistancePx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val pillBaseColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .anchoredDraggable(state, Orientation.Vertical)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onOpenSearch
            )
            .semantics {
                contentDescription = resCtx.getString(R.string.common_s_d048d1)
                role = Role.Button
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(4.dp)
                .graphicsLayer {
                    scaleX = 1f + 0.3f * progress
                    alpha = 0.45f + 0.35f * progress
                    translationY = progress * with(density) { 4.dp.toPx() }
                }
                .clip(RoundedCornerShape(2.dp))
                .phantomGlassSurface(
                    state = glassState,
                    fallbackColor = pillBaseColor.copy(alpha = 0.45f),
                    cornerRadius = 2.dp
                )
                .background(pillBaseColor.copy(alpha = 0.45f))
        )
    }
}

/**
 * #180 統一全螢幕下拉搜尋覆蓋層：fade+slide、自動聚焦、IME inset、LazyColumn 效能、AI 語意搜尋入口、返回手勢關閉。
 */
@Composable
fun PhantomFullScreenSearchOverlay(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    placeholder: String = stringResource(R.string.common_s_b3a15d),
    onAiSemanticSearch: (() -> Unit)? = null,
    aiQuotaHint: String = stringResource(R.string.common_s_da5adc),
    isAiSearching: Boolean = false,
    filterRow: (@Composable () -> Unit)? = null,
    resultsContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = com.phantom.checker.ui.components.PhantomMotion.SearchOverlayEnter,
        exit = com.phantom.checker.ui.components.PhantomMotion.SearchOverlayExit
    ) {
        val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
        LaunchedEffect(visible) {
            if (visible) {
                kotlinx.coroutines.delay(60)
                runCatching { focusRequester.requestFocus() }
            }
        }
        com.phantom.checker.ui.components.PhantomPredictiveBackContainer(
            enabled = visible,
            onBack = onDismiss
        ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_s_27c39c)
                        )
                    }
                    OutlinedTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(24.dp),
                        placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(
                                    onClick = { onQueryChange("") },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.common_s_59b34b))
                                }
                            }
                        }
                    )
                }
                if (filterRow != null) {
                    Spacer(Modifier.height(8.dp))
                    filterRow()
                }
                if (onAiSemanticSearch != null && query.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAiSemanticSearch,
                        enabled = !isAiSearching,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        if (isAiSearching) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.common_s_272973))
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(aiQuotaHint)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    content = resultsContent
                )
            }
        }
        }
    }
}

/**
 * #204 「不再自動記事/待辦」三範圍二段確認對話框：僅此寄件者（預設）/ 僅此 App / 含此關鍵字。
 */
@Composable
fun NeverAutoMemoConfirmDialog(
    packageName: String?,
    appLabel: String,
    sender: String?,
    defaultKeyword: String,
    onDismiss: () -> Unit,
    onConfirm: (packageName: String?, sender: String?, keyword: String?) -> Unit
) {
    val hasSender = !sender.isNullOrBlank()
    val hasPkg = !packageName.isNullOrBlank()
    var selectedScope by remember(sender, packageName) {
        mutableStateOf(if (hasSender) 0 else if (hasPkg) 1 else 2)
    }
    var keywordText by remember(defaultKeyword) {
        mutableStateOf(defaultKeyword.trim().take(20))
    }
    val canConfirm = when (selectedScope) {
        0 -> hasSender
        1 -> hasPkg
        else -> keywordText.isNotBlank()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_s_5ad72e)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.common_s_f1eae5),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hasSender) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = 0 },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedScope == 0,
                            onClick = { selectedScope = 0 }
                        )
                        Text(
                            text = stringResource(R.string.common_s_ee4332, sender),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                if (hasPkg) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedScope = 1 },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = selectedScope == 1,
                            onClick = { selectedScope = 1 }
                        )
                        Text(
                            text = stringResource(R.string.common_s_5457d7, appLabel.ifBlank { packageName!! }),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedScope = 2 },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = selectedScope == 2,
                        onClick = { selectedScope = 2 }
                    )
                    Text(
                        text = stringResource(R.string.common_s_77d695),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (selectedScope == 2) {
                    OutlinedTextField(
                        value = keywordText,
                        onValueChange = { keywordText = it },
                        label = { Text(stringResource(R.string.common_keyword)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when (selectedScope) {
                        0 -> onConfirm(packageName?.takeIf { it.isNotBlank() }, sender?.trim(), null)
                        1 -> onConfirm(packageName?.trim(), null, null)
                        else -> onConfirm(null, null, keywordText.trim())
                    }
                },
                enabled = canConfirm
            ) {
                Text(stringResource(R.string.common_s_4c077c))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

/**
 * #367 判錯一鍵回饋卡（feedback_wrong，零 AI token）：
 * 使用者選擇誤判類型 → 產生對應規則庫建議並經明確確認寫入規則庫＋失效快取，避免同類再誤判。
 */
@Composable
fun DecisionFeedbackDialog(
    message: com.phantom.checker.data.MessageEntity,
    onDismiss: () -> Unit,
    onFeedbackApplied: (updatedMessage: com.phantom.checker.data.MessageEntity?, toastMsg: String) -> Unit
) {
    val resCtx = LocalContext.current
    val ctx = LocalContext.current
    val appCtx = ctx.applicationContext
    val scope = rememberCoroutineScope()
    // 0: 誤判詐騙(假陽性) 1: 漏判詐騙(假陰性) 2: 自動記事/待辦錯 3: 自動標籤錯
    var selectedType by remember { mutableStateOf(0) }
    var alsoAddMemoNever by remember { mutableStateOf(false) }
    var keywordInput by remember {
        mutableStateOf(
            message.content.trim().split(Regex("\\s+")).firstOrNull { it.length >= 2 }?.take(18)
                ?: message.sender.trim().take(18)
        )
    }
    var availableTags by remember { mutableStateOf<List<com.phantom.checker.data.TagEntity>>(emptyList()) }
    var selectedTagId by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.phantom.checker.service.AppDatabaseHolder.db(appCtx).tagDao().all()
        }
        availableTags = loaded
        selectedTagId = loaded.firstOrNull()?.id
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.common_s_c930fe)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.common_s_1213ce),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                listOf(
                    0 to stringResource(R.string.common_s_8cbad8),
                    1 to stringResource(R.string.common_s_351980),
                    2 to stringResource(R.string.common_s_8eaddb),
                    3 to stringResource(R.string.common_s_f6d066)
                ).forEach { (idx, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { selectedType = idx }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selectedType == idx, onClick = { selectedType = idx })
                        Spacer(Modifier.width(4.dp))
                        Text(text = label, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (selectedType == 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { alsoAddMemoNever = !alsoAddMemoNever },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = alsoAddMemoNever, onCheckedChange = { alsoAddMemoNever = it })
                        Text(stringResource(R.string.common_s_214f76), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (selectedType == 1 || selectedType == 2) {
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it },
                        label = { Text(if (selectedType == 1) stringResource(R.string.common_s_083c6a) else stringResource(R.string.common_s_2b854d)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedType == 3 && availableTags.isNotEmpty()) {
                    Text(stringResource(R.string.common_s_bf7a21), style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        availableTags.take(4).forEach { tag ->
                            FilterChip(
                                selected = selectedTagId == tag.id,
                                onClick = { selectedTagId = tag.id },
                                label = { Text(tag.name, maxLines = 1) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val (updatedMsg, feedbackText) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val db = com.phantom.checker.service.AppDatabaseHolder.db(appCtx)
                            val senderTrim = message.sender.trim()
                            when (selectedType) {
                                0 -> {
                                    val wlRes = com.phantom.checker.data.WhitelistRuleHelper.upsertWhitelist(
                                        dao = db.aiRuleDao(),
                                        ruleText = "白名單寄件者：${message.sender}（${message.appLabel}）",
                                        sender = senderTrim.ifEmpty { null },
                                        packageName = message.packageName,
                                        onCacheInvalidate = {
                                            com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                        }
                                    )
                                    if (alsoAddMemoNever && senderTrim.isNotEmpty()) {
                                        db.memoRuleDao().insert(
                                            com.phantom.checker.data.MemoRuleEntity(
                                                packageName = message.packageName,
                                                sender = senderTrim,
                                                keyword = null,
                                                action = com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER,
                                                source = com.phantom.checker.data.MemoRuleEntity.SOURCE_DETAIL
                                            )
                                        )
                                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                    }
                                    val safeMsg = message.copy(
                                        riskScore = 10,
                                        riskLabel = "safe",
                                        riskReason = "[feedback:false_positive] 使用者回報誤判並加入白名單"
                                    )
                                    db.messageDao().update(safeMsg)
                                    com.phantom.checker.util.PhantomPrefs.recordDecisionFeedback(appCtx, "false_positive")
                                    // #375 誤判回饋寫回發送者信譽與本地良性 URL host 清單（EncryptedSharedPreferences LRU 100）
                                    com.phantom.checker.util.PhantomPrefs.recordSenderLowRiskMessage(
                                        ctx = appCtx,
                                        packageName = message.packageName,
                                        sender = message.sender,
                                        bonus = 10
                                    )
                                    val extractedUrls = com.phantom.checker.network.IndicatorExtractor.extract(message.content).urls
                                    extractedUrls.forEach { u ->
                                        com.phantom.checker.network.IndicatorExtractor.extractHost(u)?.let { host ->
                                            com.phantom.checker.util.EncryptedByokStore.addBenignUrlHost(appCtx, host)
                                        }
                                    }
                                    safeMsg to resCtx.getString(R.string.common_s_1fbf7a, wlRes.message)
                                }
                                1 -> {
                                    val kw = keywordInput.trim().ifEmpty { senderTrim }
                                    if (kw.isNotEmpty()) {
                                        db.filterDao().insert(
                                            com.phantom.checker.data.FilterRule(
                                                keyword = kw,
                                                isActive = true,
                                                action = com.phantom.checker.data.FilterRule.ACTION_SCAM
                                            )
                                        )
                                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                    }
                                    val scamMsg = message.copy(
                                        riskScore = maxOf(message.riskScore, 90),
                                        riskLabel = "scam",
                                        riskReason = "[feedback:false_negative] [rule:$kw] 使用者回報漏判並建立詐騙過濾規則"
                                    )
                                    db.messageDao().update(scamMsg)
                                    com.phantom.checker.util.PhantomPrefs.recordDecisionFeedback(appCtx, "false_negative")
                                    scamMsg to resCtx.getString(R.string.common_s_4c29f8, kw)
                                }
                                2 -> {
                                    val kw = keywordInput.trim().takeIf { it.isNotEmpty() }
                                    db.memoRuleDao().insert(
                                        com.phantom.checker.data.MemoRuleEntity(
                                            packageName = message.packageName,
                                            sender = senderTrim.takeIf { it.isNotEmpty() },
                                            keyword = kw,
                                            action = com.phantom.checker.data.MemoRuleEntity.ACTION_NEVER,
                                            source = com.phantom.checker.data.MemoRuleEntity.SOURCE_DETAIL
                                        )
                                    )
                                    com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                    com.phantom.checker.util.PhantomPrefs.recordDecisionFeedback(appCtx, "memo_wrong")
                                    null to resCtx.getString(R.string.common_s_0d1404)
                                }
                                else -> {
                                    val tid = selectedTagId
                                    if (tid != null) {
                                        val tName = availableTags.firstOrNull { it.id == tid }?.name.orEmpty()
                                        db.userTagRuleDao().insert(
                                            com.phantom.checker.data.UserTagRuleEntity(
                                                description = "回饋修正：${senderTrim.ifEmpty { message.appLabel }} → $tName",
                                                appPackage = message.packageName,
                                                senderPattern = senderTrim.takeIf { it.isNotEmpty() },
                                                targetTagId = tid,
                                                isEnabled = true,
                                                source = "feedback"
                                            )
                                        )
                                        com.phantom.checker.service.NotificationCollectorService.invalidateRuleCaches()
                                        com.phantom.checker.util.PhantomPrefs.recordDecisionFeedback(appCtx, "tag_wrong")
                                        null to resCtx.getString(R.string.common_s_ef7c47, tName)
                                    } else {
                                        null to resCtx.getString(R.string.common_s_690930)
                                    }
                                }
                            }
                        }
                        onFeedbackApplied(updatedMsg, feedbackText)
                    }
                }
            ) {
                Text(stringResource(R.string.common_s_210791))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
