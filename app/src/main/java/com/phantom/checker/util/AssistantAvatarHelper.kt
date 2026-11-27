package com.phantom.checker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.media.ExifInterface
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt
import com.phantom.checker.R
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

/**
 * #315 B3 AI 助手頭像選圖、降採樣、圓形中心裁切與快取管理。
 *
 * 鐵律 7：
 * - 嚴禁全尺寸解碼：一律先以 `inJustDecodeBounds = true` 取得寬高，注意 `openInputStream` 先判 null，
 *   再進入 `use {}` 區塊（因 `inJustDecodeBounds` 下 `decodeStream` 必回傳 null）。
 * - 計算 2 的次方 `inSampleSize` 降採樣至 512px 以下，並明確捕捉 `OutOfMemoryError`。
 * - 裁切為正方形 256×256 JPEG 原子寫入（.tmp → rename）至 `filesDir/avatars/avatar.jpg`，並以 `LruCache(1)` 快取。
 */
object AssistantAvatarHelper {

    private const val AVATAR_OUTPUT_SIZE = 256
    private const val AVATAR_DECODE_TARGET = 512

    private val cacheLock = Any()
    private val bitmapCache = object : LruCache<String, ImageBitmap>(1) {}
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal data class CropTransform(
        val finalScale: Float,
        val scaledW: Float,
        val scaledH: Float,
        val maxDx: Float,
        val maxDy: Float,
        val dx: Float,
        val dy: Float
    )

    /**
     * 預覽視窗與實際輸出共用的裁切座標換算（確保預覽與成品像素級一致）。
     */
    internal fun computeCropTransform(
        srcW: Float,
        srcH: Float,
        viewportSize: Float,
        zoomScale: Float,
        offsetXNorm: Float,
        offsetYNorm: Float
    ): CropTransform {
        val safeSrcW = srcW.coerceAtLeast(1f)
        val safeSrcH = srcH.coerceAtLeast(1f)
        val safeViewport = viewportSize.coerceAtLeast(1f)
        val safeZoom = zoomScale.coerceIn(1f, 4f)
        val baseScale = safeViewport / min(safeSrcW, safeSrcH)
        val finalScale = baseScale * safeZoom
        val scaledW = safeSrcW * finalScale
        val scaledH = safeSrcH * finalScale
        val maxDx = ((scaledW - safeViewport) / 2f).coerceAtLeast(0f)
        val maxDy = ((scaledH - safeViewport) / 2f).coerceAtLeast(0f)
        val clampedX = if (maxDx > 0f) offsetXNorm.coerceIn(-1f, 1f) else 0f
        val clampedY = if (maxDy > 0f) offsetYNorm.coerceIn(-1f, 1f) else 0f
        val dx = (safeViewport - scaledW) / 2f + clampedX * maxDx
        val dy = (safeViewport - scaledH) / 2f + clampedY * maxDy
        return CropTransform(
            finalScale = finalScale,
            scaledW = scaledW,
            scaledH = scaledH,
            maxDx = maxDx,
            maxDy = maxDy,
            dx = dx,
            dy = dy
        )
    }

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

    private fun readExifOrientation(appCtx: Context, uri: Uri): Int {
        return runCatching {
            appCtx.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    internal fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap && !bitmap.isRecycled) {
            bitmap.recycle()
        }
        return rotated
    }

    /**
     * 為圓形裁切對話框預先載入降採樣預覽圖（≤ 512px，含 EXIF 旋轉校正），防止 50MP 大圖 OOM。
     */
    suspend fun decodePreviewBitmapFromUri(ctx: Context, uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        try {
            val orientation = readExifOrientation(appCtx, uri)
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val openedBounds = appCtx.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.avatar_s_02ee73)))
            openedBounds.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_a7ce49)))
            }

            val sampleSize = calculateAvatarInSampleSize(
                rawWidth = boundsOpts.outWidth,
                rawHeight = boundsOpts.outHeight,
                targetMax = AVATAR_DECODE_TARGET
            )
            val decoded = decodeSampledStreamWithOomFallback(appCtx, uri, sampleSize)
                ?: return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_06167a)))
            val oriented = applyExifOrientation(decoded, orientation)
            Result.success(oriented)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            trimMemory()
            Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_3ed646, e.message ?: "OOM")))
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.chat_img_no_permission), e))
        } catch (e: java.io.FileNotFoundException) {
            Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.chat_img_open_failed), e))
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
     * 從 URI 降採樣解碼並依圓形預覽的縮放與位移裁切為 256×256 頭像，
     * 以 `.tmp` 原子寫入替換至 `filesDir/avatars/avatar.jpg`。
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
            val tmpFile = File(dir, "${outFile.name}.tmp")
            try {
                FileOutputStream(tmpFile).use { fos ->
                    val ok = cropped.compress(Bitmap.CompressFormat.JPEG, 88, fos)
                    if (!ok) {
                        runCatching { tmpFile.delete() }
                        return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.avatar_s_b6e624)))
                    }
                    fos.flush()
                    runCatching { fos.fd.sync() }
                }
                if (!tmpFile.renameTo(outFile)) {
                    tmpFile.copyTo(outFile, overwrite = true)
                    runCatching { tmpFile.delete() }
                }
            } catch (e: Exception) {
                runCatching { tmpFile.delete() }
                throw e
            } finally {
                if (!cropped.isRecycled) cropped.recycle()
            }

            PhantomPrefs.setHasAssistantAvatar(appCtx, true)
            trimMemory()
            PhantomSettingsBus.notifyChanged()
            Result.success(outFile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            trimMemory()
            Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.avatar_s_0bafa5, e.message ?: "OOM")))
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
        val out = Bitmap.createBitmap(outputSize, outputSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val transform = computeCropTransform(
            srcW = source.width.toFloat(),
            srcH = source.height.toFloat(),
            viewportSize = outputSize.toFloat(),
            zoomScale = zoomScale,
            offsetXNorm = offsetXNorm,
            offsetYNorm = offsetYNorm
        )

        val matrix = Matrix().apply {
            postScale(transform.finalScale, transform.finalScale)
            postTranslate(transform.dx, transform.dy)
        }
        canvas.drawColor(android.graphics.Color.BLACK)
        canvas.drawBitmap(source, matrix, paint)
        return out
    }

    fun removeAvatar(ctx: Context) {
        val appCtx = ctx.applicationContext
        PhantomPrefs.setHasAssistantAvatar(appCtx, false)
        trimMemory()
        PhantomSettingsBus.notifyChanged()
        ioScope.launch {
            runCatching {
                val f = avatarFile(appCtx)
                if (f.exists()) f.delete()
                val tmp = File(avatarDir(appCtx), "${f.name}.tmp")
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    suspend fun removeAvatarSuspend(ctx: Context) = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        runCatching {
            val f = avatarFile(appCtx)
            if (f.exists()) f.delete()
            val tmp = File(avatarDir(appCtx), "${f.name}.tmp")
            if (tmp.exists()) tmp.delete()
        }
        PhantomPrefs.setHasAssistantAvatar(appCtx, false)
        trimMemory()
        PhantomSettingsBus.notifyChanged()
    }

    suspend fun loadAvatarBitmap(ctx: Context): ImageBitmap? = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        if (!PhantomPrefs.hasAssistantAvatar(appCtx)) return@withContext null
        try {
            val file = avatarFile(appCtx)
            if (!file.exists() || file.length() <= 0L) return@withContext null
            val key = "${file.absolutePath}:${file.lastModified()}"
            synchronized(cacheLock) {
                bitmapCache.get(key)?.let { return@withContext it }
            }
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
        } catch (e: CancellationException) {
            throw e
        } catch (_: OutOfMemoryError) {
            trimMemory()
            null
        } catch (_: Exception) {
            null
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
            contentDescription = stringResource(R.string.avatar_s_423457, assistantName),
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
        )
    } else {
        val initial = assistantName.trim().firstOrNull()?.toString() ?: stringResource(R.string.avatar_s_f96157)
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
 * 頭像圓形裁切對話框（支援雙指縮放、拖曳平移與滑桿縮放，並以 bounds-first 降採樣與 EXIF 旋轉校正防 OOM）。
 */
@Composable
fun AssistantAvatarCropDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onLoadError: (String) -> Unit,
    onConfirmCrop: (zoomScale: Float, offsetXNorm: Float, offsetYNorm: Float) -> Unit
) {
    val resCtx = LocalContext.current
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
                onLoadError(err.message ?: resCtx.getString(R.string.avatar_s_9309c6))
                onDismiss()
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.avatar_s_a7bfcb),
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
                    text = stringResource(R.string.avatar_s_ad8f08),
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
                        .pointerInput(uri, previewBitmap) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newZoom = (zoomScale * zoom).coerceIn(1f, 3.5f)
                                zoomScale = newZoom
                                val img = previewBitmap ?: return@detectTransformGestures
                                val viewport = min(boxSize.width, boxSize.height).coerceAtLeast(1).toFloat()
                                val t = AssistantAvatarHelper.computeCropTransform(
                                    srcW = img.width.toFloat(),
                                    srcH = img.height.toFloat(),
                                    viewportSize = viewport,
                                    zoomScale = newZoom,
                                    offsetXNorm = offsetXNorm,
                                    offsetYNorm = offsetYNorm
                                )
                                offsetXNorm = if (t.maxDx > 0f) {
                                    (offsetXNorm + (pan.x / t.maxDx)).coerceIn(-1f, 1f)
                                } else {
                                    0f
                                }
                                offsetYNorm = if (t.maxDy > 0f) {
                                    (offsetYNorm + (pan.y / t.maxDy)).coerceIn(-1f, 1f)
                                } else {
                                    0f
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator()
                    } else {
                        previewBitmap?.let { img ->
                            val primaryColor = MaterialTheme.colorScheme.primary
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val viewport = min(size.width, size.height).coerceAtLeast(1f)
                                val t = AssistantAvatarHelper.computeCropTransform(
                                    srcW = img.width.toFloat(),
                                    srcH = img.height.toFloat(),
                                    viewportSize = viewport,
                                    zoomScale = zoomScale,
                                    offsetXNorm = offsetXNorm,
                                    offsetYNorm = offsetYNorm
                                )
                                drawImage(
                                    image = img,
                                    dstOffset = IntOffset(t.dx.roundToInt(), t.dy.roundToInt()),
                                    dstSize = IntSize(
                                        t.scaledW.roundToInt().coerceAtLeast(1),
                                        t.scaledH.roundToInt().coerceAtLeast(1)
                                    )
                                )
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
                        text = stringResource(R.string.avatar_s_6d878f),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = zoomScale,
                        onValueChange = {
                            val clamped = it.coerceIn(1f, 3.5f)
                            zoomScale = clamped
                            previewBitmap?.let { img ->
                                val viewport = min(boxSize.width, boxSize.height).coerceAtLeast(1).toFloat()
                                val t = AssistantAvatarHelper.computeCropTransform(
                                    srcW = img.width.toFloat(),
                                    srcH = img.height.toFloat(),
                                    viewportSize = viewport,
                                    zoomScale = clamped,
                                    offsetXNorm = offsetXNorm,
                                    offsetYNorm = offsetYNorm
                                )
                                if (t.maxDx <= 0f) offsetXNorm = 0f
                                if (t.maxDy <= 0f) offsetYNorm = 0f
                            }
                        },
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
                        Text(stringResource(R.string.avatar_s_53708a))
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
                Text(stringResource(R.string.avatar_s_0afa34))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}
