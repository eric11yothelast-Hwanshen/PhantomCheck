package com.phantom.checker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min

/**
 * #315 B3 AI 助手頭像選圖、降採樣、圓形中心裁切與快取管理。
 *
 * 鐵律 7：
 * - 嚴禁全尺寸解碼：一律先以 `inJustDecodeBounds = true` 取得寬高，注意 `openInputStream` 先判 null，
 *   再進入 `use {}` 區塊（因 `inJustDecodeBounds` 下 `decodeStream` 必回傳 null）。
 * - 計算 2 的次方 `inSampleSize` 降採樣至 512px 以下，並明確捕捉 `OutOfMemoryError`。
 * - 裁切為正方形 256×256 JPEG 寫入 `filesDir/avatars/avatar.jpg`，並以 `LruCache(1)` 快取。
 */
object AssistantAvatarHelper {

    private const val AVATAR_OUTPUT_SIZE = 256
    private const val AVATAR_DECODE_TARGET = 512

    private val cacheLock = Any()
    private val bitmapCache = object : LruCache<String, ImageBitmap>(1) {}

    fun avatarDir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "avatars")

    fun avatarFile(ctx: Context): File =
        File(avatarDir(ctx), "avatar.jpg")

    fun calculateAvatarInSampleSize(rawWidth: Int, rawHeight: Int, targetMax: Int = AVATAR_DECODE_TARGET): Int {
        var inSampleSize = 1
        val safeTarget = targetMax.coerceAtLeast(128)
        if (rawWidth > safeTarget || rawHeight > safeTarget) {
            while ((rawWidth / inSampleSize) > safeTarget || (rawHeight / inSampleSize) > safeTarget) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    /**
     * 為圓形裁切對話框預先載入降採樣預覽圖（≤ 512px），防止 50MP 大圖 OOM。
     */
    suspend fun decodePreviewBitmapFromUri(ctx: Context, uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        try {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val openedBounds = appCtx.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("無法開啟圖片（可能是雲端圖未下載），請先下載至本機再選取"))
            openedBounds.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return@withContext Result.failure(IllegalStateException("不支援或已損毀的圖片格式"))
            }

            val sampleSize = calculateAvatarInSampleSize(
                rawWidth = boundsOpts.outWidth,
                rawHeight = boundsOpts.outHeight,
                targetMax = AVATAR_DECODE_TARGET
            )
            val decoded = decodeSampledStreamWithOomFallback(appCtx, uri, sampleSize)
                ?: return@withContext Result.failure(IllegalStateException("圖片解碼失敗"))
            Result.success(decoded)
        } catch (e: OutOfMemoryError) {
            trimMemory()
            Result.failure(IllegalStateException("圖片過大，記憶體不足（${e.message ?: "OOM"}）"))
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException("無權限讀取此圖片，請改選本地圖片", e))
        } catch (e: java.io.FileNotFoundException) {
            Result.failure(IllegalStateException("無法開啟圖片（可能是雲端圖未下載），請先將圖片下載至本機再選取", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun decodeSampledStreamWithOomFallback(appCtx: Context, uri: Uri, initialSampleSize: Int): Bitmap? {
        var sample = initialSampleSize.coerceAtLeast(1)
        repeat(3) {
            try {
                val opts = BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val opened = appCtx.contentResolver.openInputStream(uri) ?: return null
                var bmp: Bitmap? = null
                opened.use { stream ->
                    bmp = BitmapFactory.decodeStream(stream, null, opts)
                }
                if (bmp != null) return bmp
            } catch (_: OutOfMemoryError) {
                trimMemory()
                sample *= 2
            }
        }
        return null
    }

    /**
     * 從 URI 降採樣解碼並依圓形預覽的縮放與位移裁切為 256×256 頭像存入 `filesDir/avatars/avatar.jpg`。
     */
    suspend fun saveAvatarFromUri(
        ctx: Context,
        uri: Uri,
        zoomScale: Float = 1f,
        offsetXNorm: Float = 0f,
        offsetYNorm: Float = 0f
    ): Result<File> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        try {
            val decodedRes = decodePreviewBitmapFromUri(appCtx, uri)
            val decoded = decodedRes.getOrElse { return@withContext Result.failure(it) }

            val cropped = try {
                renderSquareCroppedAvatar(
                    source = decoded,
                    outputSize = AVATAR_OUTPUT_SIZE,
                    zoomScale = zoomScale.coerceIn(1f, 4f),
                    offsetXNorm = offsetXNorm.coerceIn(-1f, 1f),
                    offsetYNorm = offsetYNorm.coerceIn(-1f, 1f)
                )
            } finally {
                if (!decoded.isRecycled) decoded.recycle()
            }

            val dir = avatarDir(appCtx)
            if (!dir.exists()) dir.mkdirs()
            val outFile = avatarFile(appCtx)
            try {
                FileOutputStream(outFile).use { fos ->
                    val ok = cropped.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                    if (!ok) {
                        return@withContext Result.failure(IllegalStateException("頭像壓縮儲存失敗"))
                    }
                }
            } finally {
                if (!cropped.isRecycled) cropped.recycle()
            }

            PhantomPrefs.setHasAssistantAvatar(appCtx, true)
            trimMemory()
            PhantomSettingsBus.notifyChanged()
            Result.success(outFile)
        } catch (e: OutOfMemoryError) {
            trimMemory()
            Result.failure(IllegalStateException("頭像處理記憶體不足（${e.message ?: "OOM"}）"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun renderSquareCroppedAvatar(
        source: Bitmap,
        outputSize: Int,
        zoomScale: Float,
        offsetXNorm: Float,
        offsetYNorm: Float
    ): Bitmap {
        val srcW = source.width.coerceAtLeast(1).toFloat()
        val srcH = source.height.coerceAtLeast(1).toFloat()
        val out = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        // 基礎填滿正方形的縮放倍率（center-crop）
        val baseScale = outputSize.toFloat() / min(srcW, srcH)
        val finalScale = baseScale * zoomScale
        val scaledW = srcW * finalScale
        val scaledH = srcH * finalScale

        val maxDx = ((scaledW - outputSize) / 2f).coerceAtLeast(0f)
        val maxDy = ((scaledH - outputSize) / 2f).coerceAtLeast(0f)
        val dx = (outputSize - scaledW) / 2f + (offsetXNorm * maxDx)
        val dy = (outputSize - scaledH) / 2f + (offsetYNorm * maxDy)

        val matrix = Matrix().apply {
            postScale(finalScale, finalScale)
            postTranslate(dx, dy)
        }
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(source, matrix, paint)
        return out
    }

    fun removeAvatar(ctx: Context) {
        val appCtx = ctx.applicationContext
        try {
            val f = avatarFile(appCtx)
            if (f.exists()) f.delete()
        } catch (_: Exception) {
        }
        PhantomPrefs.setHasAssistantAvatar(appCtx, false)
        trimMemory()
        PhantomSettingsBus.notifyChanged()
    }

    suspend fun loadAvatarBitmap(ctx: Context): ImageBitmap? {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.hasAssistantAvatar(appCtx)) return null
        val file = avatarFile(appCtx)
        val key = "${file.absolutePath}:${file.lastModified()}"
        synchronized(cacheLock) {
            bitmapCache.get(key)?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            try {
                if (!file.exists() || file.length() <= 0L) return@withContext null
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@withContext null

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = calculateAvatarInSampleSize(
                        boundsOpts.outWidth,
                        boundsOpts.outHeight,
                        AVATAR_OUTPUT_SIZE
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts) ?: return@withContext null
                val img = bmp.asImageBitmap()
                synchronized(cacheLock) {
                    bitmapCache.put(key, img)
                }
                img
            } catch (_: OutOfMemoryError) {
                trimMemory()
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    fun trimMemory() {
        synchronized(cacheLock) {
            bitmapCache.evictAll()
        }
    }
}

/**
 * 圓形 AI 助手頭像元件（若有自訂頭像則顯示圓形圖片，否則顯示帶助手首字的預設圖示）。
 */
@Composable
fun AssistantAvatarCircle(
    avatarBitmap: ImageBitmap?,
    assistantName: String,
    size: Dp = 28.dp,
    modifier: Modifier = Modifier
) {
    if (avatarBitmap != null) {
        Image(
            bitmap = avatarBitmap,
            contentDescription = "$assistantName 頭像",
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
        )
    } else {
        val initial = assistantName.trim().firstOrNull()?.toString() ?: "幻"
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initial,
                style = if (size <= 26.dp) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

/**
 * 頭像圓形裁切對話框（支援雙指縮放、拖曳平移與滑桿縮放，並以 bounds-first 降採樣防 OOM）。
 */
@Composable
fun AssistantAvatarCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onLoadError: (String) -> Unit,
    onConfirmCrop: (zoomScale: Float, offsetXNorm: Float, offsetYNorm: Float) -> Unit
) {
    val ctx = LocalContext.current
    var previewBitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(uri) { mutableStateOf(true) }
    var zoomScale by remember(uri) { mutableFloatStateOf(1f) }
    var offsetXNorm by remember(uri) { mutableFloatStateOf(0f) }
    var offsetYNorm by remember(uri) { mutableFloatStateOf(0f) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(uri) {
        isLoading = true
        val res = AssistantAvatarHelper.decodePreviewBitmapFromUri(ctx, uri)
        res.fold(
            onSuccess = { bmp ->
                previewBitmap = bmp.asImageBitmap()
                isLoading = false
            },
            onFailure = { err ->
                isLoading = false
                onLoadError(err.message ?: "圖片載入失敗")
                onDismiss()
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "裁切 AI 助手圓形頭像",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "可拖曳移動位置或調整下方滑桿縮放，圓框內為最終顯示範圍：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                        .onSizeChanged { boxSize = it }
                        .pointerInput(uri) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newZoom = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                zoomScale = newZoom
                                val w = boxSize.width.coerceAtLeast(1).toFloat()
                                val h = boxSize.height.coerceAtLeast(1).toFloat()
                                offsetXNorm = (offsetXNorm + (pan.x / (w * 0.5f))).coerceIn(-1f, 1f)
                                offsetYNorm = (offsetYNorm + (pan.y / (h * 0.5f))).coerceIn(-1f, 1f)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        previewBitmap?.let { img ->
                            val w = boxSize.width.coerceAtLeast(1).toFloat()
                            val h = boxSize.height.coerceAtLeast(1).toFloat()
                            val maxPanX = (w * (zoomScale - 1f) / 2f).coerceAtLeast(16f)
                            val maxPanY = (h * (zoomScale - 1f) / 2f).coerceAtLeast(16f)
                            Image(
                                bitmap = img,
                                contentDescription = "頭像裁切預覽",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = zoomScale
                                        scaleY = zoomScale
                                        translationX = offsetXNorm * maxPanX
                                        translationY = offsetYNorm * maxPanY
                                    }
                            )
                            val primaryColor = MaterialTheme.colorScheme.primary
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val radius = size.minDimension / 2f - 4.dp.toPx()
                                drawCircle(
                                    color = primaryColor,
                                    radius = radius,
                                    center = Offset(size.width / 2f, size.height / 2f),
                                    style = Stroke(width = 2.5.dp.toPx())
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "縮放",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = zoomScale,
                        onValueChange = { zoomScale = it.coerceIn(1f, 3.5f) },
                        valueRange = 1f..3.5f,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            zoomScale = 1f
                            offsetXNorm = 0f
                            offsetYNorm = 0f
                        }
                    ) {
                        Text("置中")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isLoading && previewBitmap != null,
                onClick = {
                    onConfirmCrop(zoomScale, offsetXNorm, offsetYNorm)
                }
            ) {
                Text("儲存頭像")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
