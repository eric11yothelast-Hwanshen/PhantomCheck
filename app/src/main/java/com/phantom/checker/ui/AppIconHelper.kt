package com.phantom.checker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private fun drawableToBitmap(drawable: Drawable, sizePx: Int = 96): Bitmap {
    val targetSize = sizePx.coerceAtLeast(1)
    if (drawable is BitmapDrawable && drawable.bitmap != null) {
        val src = drawable.bitmap
        if (src.width >= targetSize &&
            src.height >= targetSize &&
            src.config == Bitmap.Config.ARGB_8888
        ) {
            return src
        }
        val w = maxOf(src.width, targetSize)
        val h = maxOf(src.height, targetSize)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, null, Rect(0, 0, w, h), paint)
        return out
    }
    val w = maxOf(drawable.intrinsicWidth, targetSize)
    val h = maxOf(drawable.intrinsicHeight, targetSize)
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

fun loadAppIconBitmap(context: Context, packageName: String): ImageBitmap? {
    return try {
        val drawable = context.packageManager.getApplicationIcon(packageName)
        drawableToBitmap(drawable).asImageBitmap()
    } catch (_: Exception) {
        null
    }
}

/**
 * 將最多 4 個最近通知 App 的圖示繪入高解析正方形 ARGB_8888 Bitmap（1×1 / 1×2 / 2×2 方格，
 * 每格至少 48dp×density、總尺寸 ≥ 256×256、保留 ≥ 8% 圓形裁切安全邊距），供常駐統計通知 setLargeIcon 使用。
 */
fun buildRecentAppsStripBitmap(
    context: Context,
    packageNames: List<String>,
    iconSizeDp: Int = 24,
    spacingDp: Int = 4
): Bitmap? {
    val pkgs = packageNames.distinct().take(4)
    if (pkgs.isEmpty()) return null
    val density = context.resources.displayMetrics.density
    val baseCellPx = maxOf((48f * density).roundToInt(), (iconSizeDp * density).roundToInt(), 96)
    val gapPx = (spacingDp * density).roundToInt().coerceAtLeast(4)
    val drawables = pkgs.mapNotNull { pkg ->
        try {
            context.packageManager.getApplicationIcon(pkg)
        } catch (_: Exception) {
            null
        }
    }
    if (drawables.isEmpty()) return null

    val cols = if (drawables.size == 1) 1 else 2
    val rows = if (drawables.size <= 2) 1 else 2
    val contentMax = maxOf(
        cols * baseCellPx + (cols - 1) * gapPx,
        rows * baseCellPx + (rows - 1) * gapPx
    )
    // 保留四週各 10%（≥ 8%）圓形裁切安全邊距，並確保畫布至少 256×256
    val safeMarginRatio = 0.10f
    val canvasSize = maxOf(256, (contentMax / (1f - 2f * safeMarginRatio)).roundToInt())
    val innerSize = (canvasSize * (1f - 2f * safeMarginRatio)).roundToInt()
    val cellPx = if (cols == 1) innerSize else ((innerSize - gapPx) / 2).coerceAtLeast(baseCellPx)

    val gridWidth = cols * cellPx + (cols - 1) * gapPx
    val gridHeight = rows * cellPx + (rows - 1) * gapPx
    val startX = (canvasSize - gridWidth) / 2
    val startY = (canvasSize - gridHeight) / 2

    val bitmap = Bitmap.createBitmap(canvasSize, canvasSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    drawables.forEachIndexed { idx, drawable ->
        val col = idx % cols
        val row = idx / cols
        val left = startX + col * (cellPx + gapPx)
        val top = startY + row * (cellPx + gapPx)
        val iconBmp = drawableToBitmap(drawable, cellPx)
        canvas.drawBitmap(iconBmp, null, Rect(left, top, left + cellPx, top + cellPx), paint)
    }
    return bitmap
}

@Composable
fun AppIconImage(
    packageName: String,
    contentDescription: String?,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val bitmap = remember(packageName) {
        loadAppIconBitmap(ctx, packageName)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(size * 0.55f)
            )
        }
    }
}
