package com.phantom.checker.ui.components

import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Phantom Aurora 動效語彙（#398）。
 *
 * 規範：
 * - 位置/尺寸/可被打斷的互動 → spring；純 alpha/color 淡入淡出 → 短 tween。
 * - 動畫值一律在 graphicsLayer / drawBehind lambda 內讀取（draw phase），不在 composition 每幀讀取。
 * - 系統「動畫時長縮放＝0」時：Compose 的 animate* / Animatable / Transition 自動跳到終值；
 *   手動 delay（stagger）由 [rememberMotionEnabled] 關閉。
 */
object PhantomMotion {
    /** M3 emphasized decelerate（進場） */
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** M3 emphasized accelerate（退場） */
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 收合區塊展開（M5）：彈性高度＋淡入 */
    val ExpandEnter: EnterTransition =
        expandVertically(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
                visibilityThreshold = IntSize.VisibilityThreshold,
            ),
            expandFrom = Alignment.Top,
        ) + fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 40))

    /** 收合區塊收起（M5） */
    val ExpandExit: ExitTransition =
        shrinkVertically(
            animationSpec = tween(durationMillis = 220, easing = EmphasizedAccelerate),
            shrinkTowards = Alignment.Top,
        ) + fadeOut(animationSpec = tween(durationMillis = 120))

    /** 清單進場 stagger 的單項延遲與最多參與項數（M3） */
    const val StaggerStepMillis = 36L
    const val StaggerMaxItems = 8

    // ── Aurora II 新增動效常數（N1–N7，#409，僅追加不改既有） ──

    /** N1：設定中心同 Activity 內分類頁切換（前進） */
    val PageEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialOffsetX = { it / 10 },
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 180,
                delayMillis = 40,
                easing = EmphasizedDecelerate,
            ),
        )

    /** N1：設定中心同 Activity 內分類頁切換（被覆蓋頁退場） */
    val PageExit: ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 200, easing = EmphasizedAccelerate),
            targetOffsetX = { -it / 20 },
        ) + fadeOut(animationSpec = tween(durationMillis = 120))

    /** N1：返回方向（進場） */
    val PagePopEnter: EnterTransition =
        slideInHorizontally(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
            initialOffsetX = { -it / 20 },
        ) + fadeIn(
            animationSpec = tween(
                durationMillis = 180,
                delayMillis = 40,
                easing = EmphasizedDecelerate,
            ),
        )

    /** N1：返回方向（退場） */
    val PagePopExit: ExitTransition =
        slideOutHorizontally(
            animationSpec = tween(durationMillis = 200, easing = EmphasizedAccelerate),
            targetOffsetX = { it / 10 },
        ) + fadeOut(animationSpec = tween(durationMillis = 120))

    /** N2：預測性返回縮放／圓角／平移常數（全在 graphicsLayer/clip draw-phase 讀取） */
    const val PredictiveBackMaxScaleDelta = 0.10f
    val PredictiveBackMaxCornerDp: Dp = 28.dp
    val PredictiveBackMaxTranslationDp: Dp = 8.dp
    val PredictiveBackCommitSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )
    val PredictiveBackCancelSpring = spring<Float>(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** N3：全域搜尋膠囊 → overlay 轉場（不用共享元素） */
    const val SearchPillFadeOutMillis = 120
    val SearchOverlayEnter: EnterTransition =
        fadeIn(animationSpec = tween(durationMillis = 200, easing = EmphasizedDecelerate)) +
            scaleIn(
                initialScale = 0.96f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
            )
    val SearchOverlayExit: ExitTransition =
        fadeOut(animationSpec = tween(durationMillis = 120)) +
            scaleOut(
                targetScale = 0.96f,
                animationSpec = tween(durationMillis = 160, easing = EmphasizedAccelerate),
            )

    /** N4：設定首頁 Large → Small 標題縮放比例（22sp / 28sp） */
    const val TopBarCollapseScaleRatio = 0.786f

    /** N5：底部導覽 5 項指示器常數 */
    val NavIndicatorSpring = spring<Float>(dampingRatio = 0.5f, stiffness = 400f)
    val NavIndicatorHorizontalInsetDp: Dp = 16.dp
    val NavIndicatorHeightDp: Dp = 32.dp

    /** N6：洞察／記憶／日曆二級分頁指示器彈簧 */
    val SecondaryTabIndicatorSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    /** N7：設定首頁釘選磁磚新增／移除／重排常數 */
    const val PinPopInInitialScale = 0.92f
    const val PinRemoveFadeOutMillis = 120
    val PinReorderSpring = spring<IntOffset>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    // ── Aurora III 手機連線頁動效常數（C1–C10，#520，僅追加不改既有 M/N 系列） ──

    const val LinkHeroTintMs = 240
    const val LinkPulseHalfCycleMs = 850
    const val LinkPulseMaxSec = 60
    const val LinkPopInAlphaMs = 160
    const val LinkSuccessRingMs = 480
    const val LinkStatusDotColorMs = 200
    const val LinkPowerSaverFadeMs = 80
}

/** #520 手機端有效動效設定檔（BALANCED / POWER_SAVER / OFF 當系統 scale=0） */
enum class PhoneMotionProfile {
    BALANCED,
    POWER_SAVER,
    OFF;

    val isFull: Boolean get() = this == BALANCED
    val isReduced: Boolean get() = this != BALANCED
}

/**
 * 全程序單例且引用計數的系統 ANIMATOR_DURATION_SCALE 監聽器（#548）：
 * 避免 LazyColumn 每個 phantomItem / rememberMotionEnabled() 各自註冊 ContentObserver 與呼叫
 * Settings.Global.getFloat 造成 Binder IPC 洪流與 observer 洩漏；最後一個訂閱者 onDispose 時反註冊。
 */
private object SystemAnimatorScaleObserver {
    private val lock = Any()
    private val scaleState = androidx.compose.runtime.mutableFloatStateOf(1f)
    @Volatile
    private var initialized = false
    private var refCount = 0
    private var registeredObserver: android.database.ContentObserver? = null
    private var registeredResolver: android.content.ContentResolver? = null

    val currentScale: Float
        get() = scaleState.floatValue

    private fun readScale(resolver: android.content.ContentResolver): Float =
        try {
            Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        } catch (_: Exception) {
            1f
        }

    fun ensureInitialized(context: android.content.Context) {
        if (initialized) return
        val resolver = (context.applicationContext ?: context).contentResolver
        synchronized(lock) {
            if (!initialized) {
                scaleState.floatValue = readScale(resolver)
                initialized = true
            }
        }
    }

    fun acquire(context: android.content.Context) {
        val resolver = (context.applicationContext ?: context).contentResolver
        synchronized(lock) {
            if (refCount == 0) {
                scaleState.floatValue = readScale(resolver)
                initialized = true
                val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
                if (uri != null) {
                    val observer = object : android.database.ContentObserver(
                        android.os.Handler(android.os.Looper.getMainLooper())
                    ) {
                        override fun onChange(selfChange: Boolean) {
                            scaleState.floatValue = readScale(resolver)
                        }
                    }
                    val ok = runCatching {
                        resolver.registerContentObserver(uri, false, observer)
                        true
                    }.getOrDefault(false)
                    if (ok) {
                        registeredObserver = observer
                        registeredResolver = resolver
                    }
                }
            }
            refCount++
        }
    }

    fun release() {
        synchronized(lock) {
            if (refCount <= 0) return
            refCount--
            if (refCount == 0) {
                val obs = registeredObserver
                val res = registeredResolver
                registeredObserver = null
                registeredResolver = null
                if (obs != null && res != null) {
                    runCatching { res.unregisterContentObserver(obs) }
                }
            }
        }
    }
}

/**
 * #520 反應式讀取系統 ANIMATOR_DURATION_SCALE（透過共用引用計數 ContentObserver）與使用者動畫模式（UiLayoutPrefs.version）。
 */
@Composable
fun rememberMotionProfile(): PhoneMotionProfile {
    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext ?: ctx }
    val prefsVersion = com.phantom.checker.ui.UiLayoutPrefs.version
    remember(appCtx) {
        SystemAnimatorScaleObserver.ensureInitialized(appCtx)
        true
    }
    androidx.compose.runtime.DisposableEffect(appCtx) {
        SystemAnimatorScaleObserver.acquire(appCtx)
        onDispose {
            SystemAnimatorScaleObserver.release()
        }
    }
    val scale = SystemAnimatorScaleObserver.currentScale
    val userMode = remember(appCtx, prefsVersion) { com.phantom.checker.ui.UiLayoutPrefs.getAnimationMode(appCtx) }
    return when {
        !scale.isFinite() || scale <= 0f -> PhoneMotionProfile.OFF
        userMode == com.phantom.checker.ui.UiLayoutPrefs.ANIM_MODE_POWER_SAVER -> PhoneMotionProfile.POWER_SAVER
        else -> PhoneMotionProfile.BALANCED
    }
}

/**
 * 讀取系統「動畫時長縮放」與使用者動畫模式（#520 反應式升級，保留原函式簽名與既有 M1–M13/N1–N7 呼叫點零改動）。
 */
@Composable
fun rememberMotionEnabled(): Boolean = rememberMotionProfile() == PhoneMotionProfile.BALANCED

/**
 * 進場播放紀錄（每個分頁組合一份，於 MainActivity 分頁容器提供）：
 * 已播放過的 key 在同一次分頁停留期間捲出再捲回時不重播；切離分頁再回來才會重新播放。
 */
val LocalPhantomEnterRegistry = staticCompositionLocalOf<MutableSet<Any>?> { null }

/**
 * 清單進場（M3）：前 [PhantomMotion.StaggerMaxItems] 項依 index 延遲上浮淡入。
 * 動畫值僅在 graphicsLayer lambda 讀取；Animatable 只在首次組合播放一次；
 * 動畫縮放＝0 或 index 超出範圍時直接以靜態呈現。
 * 提供 [key] 時會登記到 [LocalPhantomEnterRegistry]，避免捲動造成重播。
 */
@Composable
fun Modifier.phantomEnter(index: Int, enabled: Boolean = true, key: Any? = null): Modifier {
    val motionOn = rememberMotionEnabled()
    val registry = LocalPhantomEnterRegistry.current
    val alreadyPlayed = remember { key != null && registry?.contains(key) == true }
    val active = enabled && motionOn && !alreadyPlayed && index in 0 until PhantomMotion.StaggerMaxItems
    val progress = remember { Animatable(if (active) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (key != null) registry?.add(key)
        if (active) {
            delay(index * PhantomMotion.StaggerStepMillis)
            progress.animateTo(
                1f,
                spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }
    val dy = with(LocalDensity.current) { 14.dp.toPx() }
    return this.graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            alpha = p.coerceIn(0f, 1f)
            translationY = (1f - p) * dy
        }
    }
}

/**
 * LazyColumn 區塊項目：自帶進場動畫的 item（[order] 為視覺順序，前 8 項有 stagger）。
 * 內容以 Box 包一層承載動畫，其餘行為與 item() 完全相同。
 */
fun LazyListScope.phantomItem(
    order: Int,
    key: Any? = null,
    contentType: Any? = null,
    content: @Composable LazyItemScope.() -> Unit,
) {
    item(key = key, contentType = contentType) {
        val itemScope = this
        Box(modifier = Modifier.phantomEnter(order, key = key)) {
            itemScope.content()
        }
    }
}

/**
 * 彈出進場（M9／M10）：scale＋alpha，spring。對話框、空狀態圖示等使用。
 */
@Composable
fun Modifier.phantomPopIn(initialScale: Float = 0.92f, delayMillis: Int = 0): Modifier {
    val motionOn = rememberMotionEnabled()
    val progress = remember { Animatable(if (motionOn) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motionOn) {
            if (delayMillis > 0) delay(delayMillis.toLong())
            progress.animateTo(
                1f,
                spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            )
        }
    }
    return this.graphicsLayer {
        val p = progress.value
        if (p < 1f) {
            val s = initialScale + (1f - initialScale) * p
            scaleX = s
            scaleY = s
            alpha = p.coerceIn(0f, 1f)
        }
    }
}

/**
 * 旋轉 chevron（M6）：收合 0°，展開 180°，spring。取代 KeyboardArrowUp/Down 圖示切換。
 */
@Composable
fun PhantomChevron(
    expanded: Boolean,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = LocalContentColor.current,
    iconSize: Dp = 24.dp,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "phantomChevron",
    )
    Icon(
        imageVector = Icons.Default.KeyboardArrowDown,
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier
            .size(iconSize)
            .graphicsLayer { rotationZ = rotation },
    )
}

/**
 * 玻璃高光邊（draw-phase）：上緣亮、下緣隱的漸層描邊＋頂部內暈。
 * 只畫在內容之後（drawBehind），不影響命中測試；亮色/暗色各自調光。
 */
fun Modifier.phantomSheen(cornerRadius: Dp, isDark: Boolean): Modifier =
    this.drawWithCache {
        val radius = cornerRadius.toPx()
        val strokeWidth = 1.dp.toPx()
        val rimBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.30f else 0.85f),
                Color.White.copy(alpha = if (isDark) 0.06f else 0.20f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = size.height,
        )
        val glowBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (isDark) 0.07f else 0.22f),
                Color.Transparent,
            ),
            startY = 0f,
            endY = minOf(size.height, 56.dp.toPx()),
        )
        val inset = strokeWidth / 2f
        val rimSize = Size(
            (size.width - strokeWidth).coerceAtLeast(0f),
            (size.height - strokeWidth).coerceAtLeast(0f),
        )
        val rimStroke = Stroke(width = strokeWidth)
        onDrawBehind {
            drawRoundRect(
                brush = glowBrush,
                cornerRadius = CornerRadius(radius),
            )
            drawRoundRect(
                brush = rimBrush,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = rimSize,
                cornerRadius = CornerRadius((radius - inset).coerceAtLeast(0f)),
                style = rimStroke,
            )
        }
    }

/**
 * 可點擊＋按壓回饋：微縮＋邊光（[pressableScale]）＋標準 clickable 語意（role / onClickLabel 透傳）。
 */
fun Modifier.phantomPressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    pressedScale: Float = 0.97f,
): Modifier =
    this
        .pressableScale(pressedScale, shape)
        .clip(shape)
        .clickable(
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )

/**
 * 數值滾動淡入文字（M13）：數值增減時依方向微幅上下滑動＋淡入；動畫縮放＝0 時直接靜態顯示。
 */
@Composable
fun PhantomAnimatedCount(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    format: (Int) -> String = { it.toString() },
) {
    val motionOn = rememberMotionEnabled()
    if (!motionOn) {
        Text(
            text = format(count),
            modifier = modifier,
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
        )
        return
    }
    AnimatedContent(
        targetState = count,
        modifier = modifier,
        transitionSpec = {
            val dir = if (targetState >= initialState) 1 else -1
            (slideInVertically(tween(220, easing = PhantomMotion.EmphasizedDecelerate)) { dir * it / 2 } +
                fadeIn(tween(200)))
                .togetherWith(
                    slideOutVertically(tween(160, easing = PhantomMotion.EmphasizedAccelerate)) { -dir * it / 2 } +
                        fadeOut(tween(140)),
                )
                .using(SizeTransform(clip = false))
        },
        label = "phantomAnimatedCount",
    ) { value ->
        Text(
            text = format(value),
            style = style,
            color = color,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
        )
    }
}
