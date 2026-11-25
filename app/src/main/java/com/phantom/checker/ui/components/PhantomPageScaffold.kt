package com.phantom.checker.ui.components

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext

/**
 * Aurora II 設計語言版面常數（§2.1）。
 */
object AuroraTokens {
    val PagePaddingCompact: Dp = 16.dp
    val PagePaddingExpanded: Dp = 24.dp
    val MaxContentWidth: Dp = 840.dp
    val ExpandedBreakpoint: Dp = 600.dp

    val SpaceXs: Dp = 4.dp
    val SpaceSm: Dp = 8.dp
    val SpaceMd: Dp = 12.dp
    val SpaceLg: Dp = 16.dp
    val SpaceXl: Dp = 24.dp
    val Space2Xl: Dp = 32.dp

    val CornerLargeCard: Dp = 28.dp
    val CornerCard: Dp = 20.dp
    val CornerSegmentedOuter: Dp = 20.dp
    val CornerSegmentedInner: Dp = 6.dp
    val CornerChip: Dp = 12.dp
    val CornerInput: Dp = 16.dp

    val MinTouchTarget: Dp = 48.dp
    val MinRowHeight: Dp = 64.dp
    val NavBarHeight: Dp = 64.dp
    val SmallTopBarHeight: Dp = 56.dp

    /** 分段式群組清單圓角（首尾 20dp、中間 6dp；僅一項時四邊 20dp） */
    fun segmentedItemShape(index: Int, count: Int): RoundedCornerShape = when {
        count <= 1 -> RoundedCornerShape(CornerSegmentedOuter)
        index == 0 -> RoundedCornerShape(
            topStart = CornerSegmentedOuter,
            topEnd = CornerSegmentedOuter,
            bottomStart = CornerSegmentedInner,
            bottomEnd = CornerSegmentedInner,
        )
        index == count - 1 -> RoundedCornerShape(
            topStart = CornerSegmentedInner,
            topEnd = CornerSegmentedInner,
            bottomStart = CornerSegmentedOuter,
            bottomEnd = CornerSegmentedOuter,
        )
        else -> RoundedCornerShape(CornerSegmentedInner)
    }
}

/** 依視窗寬度回傳水平內距（<600dp: 16dp；≥600dp: 24dp） */
@Composable
fun auroraHorizontalPadding(): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return if (widthDp >= AuroraTokens.ExpandedBreakpoint.value) {
        AuroraTokens.PagePaddingExpanded
    } else {
        AuroraTokens.PagePaddingCompact
    }
}

/** 大螢幕（≥600dp）內容最大寬度 840dp 置中容器 */
@Composable
fun AuroraCenteredContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(modifier = Modifier.widthIn(max = AuroraTokens.MaxContentWidth).fillMaxWidth()) {
            content()
        }
    }
}

enum class PhantomTopBarStyle {
    Small,
    Medium,
    Large,
}

/**
 * Aurora II 共用頁面骨架（設定分類頁、獨立子 Activity 共用）。
 * 僅使用 M3 長期 stable API（Scaffold / TopAppBar / MediumTopAppBar / LargeTopAppBar）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhantomPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backContentDescription: String = stringResource(R.string.common_back),
    topBarStyle: PhantomTopBarStyle = PhantomTopBarStyle.Medium,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior: TopAppBarScrollBehavior? = when (topBarStyle) {
        PhantomTopBarStyle.Small -> TopAppBarDefaults.pinnedScrollBehavior()
        PhantomTopBarStyle.Medium, PhantomTopBarStyle.Large ->
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val topBarColors = TopAppBarDefaults.topAppBarColors(
        containerColor = if (containerColor == Color.Transparent) Color.Transparent else containerColor,
        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f),
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
        actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val navIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(AuroraTokens.MinTouchTarget),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = backContentDescription,
                )
            }
        }
    }
    val titleContent: @Composable () -> Unit = {
        Column {
            Text(
                text = title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    val nestedScrollModifier = if (scrollBehavior != null) {
        Modifier.nestedScroll(scrollBehavior.nestedScrollConnection)
    } else {
        Modifier
    }

    Scaffold(
        modifier = modifier.then(nestedScrollModifier),
        containerColor = containerColor,
        topBar = {
            when (topBarStyle) {
                PhantomTopBarStyle.Small -> TopAppBar(
                    title = titleContent,
                    navigationIcon = navIcon,
                    actions = actions,
                    colors = topBarColors,
                    scrollBehavior = scrollBehavior,
                )
                PhantomTopBarStyle.Medium -> MediumTopAppBar(
                    title = titleContent,
                    navigationIcon = navIcon,
                    actions = actions,
                    colors = topBarColors,
                    scrollBehavior = scrollBehavior,
                )
                PhantomTopBarStyle.Large -> LargeTopAppBar(
                    title = titleContent,
                    navigationIcon = navIcon,
                    actions = actions,
                    colors = topBarColors,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        content = content,
    )
}

/**
 * N2 預測性返回容器（PredictiveBackHandler spike：已驗證 activity-compose 1.13.0 可用）。
 *
 * 手勢進度 p ∈ [0, 1]：
 * - scale = 1 - 0.10p
 * - cornerRadius = 28dp * p
 * - translationX = ±(8dp * p)
 * 所有動畫數值僅在 graphicsLayer lambda（draw-phase）讀取，不觸發每幀重組。
 */
@Composable
fun PhantomPredictiveBackContainer(
    enabled: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val motionOn = rememberMotionEnabled()
    val backProgress = remember { Animatable(0f) }
    var swipeEdge by remember { mutableIntStateOf(BackEventCompat.EDGE_LEFT) }

    PredictiveBackHandler(enabled = enabled) { progress: Flow<BackEventCompat> ->
        try {
            progress.collect { event ->
                swipeEdge = event.swipeEdge
                if (motionOn) {
                    backProgress.snapTo(event.progress.coerceIn(0f, 1f))
                }
            }
            if (motionOn) {
                backProgress.animateTo(0f, PhantomMotion.PredictiveBackCommitSpring)
            }
            onBack()
        } catch (e: CancellationException) {
            if (motionOn) {
                backProgress.animateTo(0f, PhantomMotion.PredictiveBackCancelSpring)
            }
            throw e
        }
    }

    val density = LocalDensity.current
    val maxTransPx = with(density) { PhantomMotion.PredictiveBackMaxTranslationDp.toPx() }
    val maxCornerDp = PhantomMotion.PredictiveBackMaxCornerDp

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = backProgress.value.coerceIn(0f, 1f)
                if (p > 0f) {
                    val s = 1f - PhantomMotion.PredictiveBackMaxScaleDelta * p
                    scaleX = s
                    scaleY = s
                    val dir = if (swipeEdge == BackEventCompat.EDGE_RIGHT) -1f else 1f
                    translationX = dir * maxTransPx * p
                    shape = RoundedCornerShape(maxCornerDp * p)
                    clip = true
                }
            },
    ) {
        content()
    }
}

/**
 * N6 自製二級分頁列（洞察「夥伴｜統計」、記憶「記憶｜規則｜防詐免疫」、記事日曆「日｜週｜月」共用）。
 * 指示器位移走 draw-phase（drawBehind），不觸發每幀重組；每項最小高 48dp，fontScale 1.3 不破版。
 */
@Composable
fun PhantomSecondaryTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<Int> = emptyList(),
) {
    val resCtx = LocalContext.current
    if (tabs.isEmpty()) return
    val safeIndex = selectedIndex.coerceIn(0, tabs.lastIndex)
    val animatedIndex = remember { Animatable(safeIndex.toFloat()) }
    LaunchedEffect(safeIndex) {
        animatedIndex.animateTo(
            targetValue = safeIndex.toFloat(),
            animationSpec = PhantomMotion.SecondaryTabIndicatorSpring,
        )
    }

    val containerColor = MaterialTheme.colorScheme.surfaceContainer
    val indicatorColor = MaterialTheme.colorScheme.primaryContainer
    val selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer
    val unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AuroraTokens.CornerCard))
            .background(containerColor)
            .padding(4.dp),
    ) {
        val tabCount = tabs.size
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val tabWidth = size.width / tabCount
                    val left = animatedIndex.value * tabWidth
                    drawRoundRect(
                        color = indicatorColor,
                        topLeft = Offset(left, 0f),
                        size = Size(tabWidth, size.height),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                    )
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, label ->
                val isSelected = index == safeIndex
                val badge = badges.getOrNull(index) ?: 0
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .semantics {
                            selected = isSelected
                            contentDescription = if (badge > 0) resCtx.getString(R.string.scaffold_s_22f25f, label, badge) else label
                        }
                        .clickable(role = Role.Tab) { onSelect(index) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) selectedTextColor else unselectedTextColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (badge > 0) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = if (badge > 99) "99+" else badge.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 統一內嵌提示條（§4.2 / §4.8）：圓角 20dp、左側色條、可選滑除／關閉。
 * 遵循鐵律①：誤判最壞後果＝可滑除提示。
 */
@Composable
fun PhantomInlineBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AuroraTokens.CornerCard),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accentColor),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        onClick = onAction,
                        shape = RoundedCornerShape(AuroraTokens.CornerChip),
                        color = accentColor.copy(alpha = 0.14f),
                    ) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.common_dismiss_tip),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
