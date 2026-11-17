package com.phantom.checker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class BackgroundLayer(val fileName: String, val label: String) {
    GLOBAL("global.jpg", "全域背景"),
    CHAT("chat.jpg", "AI 聊天背景"),
    CONVERSATION("conversation.jpg", "對話頁背景")
}

object BackgroundImageHelper {

    private val cacheLock = Any()
    private val bitmapCache = object : LruCache<String, ImageBitmap>(1) {}

    fun backgroundDir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "backgrounds")

    fun backgroundFile(ctx: Context, layer: BackgroundLayer): File =
        File(backgroundDir(ctx), layer.fileName)

    fun resolveBackgroundFile(ctx: Context, layer: BackgroundLayer): File? {
        val primary = backgroundFile(ctx, layer)
        if (PhantomPrefs.hasBackgroundImage(ctx, layer) && primary.exists() && primary.length() > 0L) {
            return primary
        }
        if (layer != BackgroundLayer.GLOBAL) {
            val fallback = backgroundFile(ctx, BackgroundLayer.GLOBAL)
            if (PhantomPrefs.hasBackgroundImage(ctx, BackgroundLayer.GLOBAL) &&
                fallback.exists() &&
                fallback.length() > 0L
            ) {
                return fallback
            }
        }
        return null
    }

    fun hasAnyBackground(ctx: Context): Boolean =
        BackgroundLayer.entries.any { layer ->
            PhantomPrefs.hasBackgroundImage(ctx, layer) && backgroundFile(ctx, layer).exists()
        }

    fun calculateInSampleSize(rawWidth: Int, rawHeight: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        val targetMax = maxOf(reqWidth, reqHeight).coerceAtLeast(1080)
        if (rawWidth > targetMax || rawHeight > targetMax) {
            while ((rawWidth / inSampleSize) > targetMax || (rawHeight / inSampleSize) > targetMax) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }

    suspend fun saveBackgroundFromUri(
        ctx: Context,
        layer: BackgroundLayer,
        uri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        try {
            val dm = appCtx.resources.displayMetrics
            val reqW = dm.widthPixels.coerceAtLeast(1080)
            val reqH = dm.heightPixels.coerceAtLeast(1920)

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val openedBounds = appCtx.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException("無法開啟圖片（可能是雲端圖未下載），請先將圖片下載至本機再選取"))
            openedBounds.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }

            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return@withContext Result.failure(IllegalStateException("不支援或已損毀的圖片格式"))
            }

            val sampleSize = calculateInSampleSize(
                rawWidth = boundsOpts.outWidth,
                rawHeight = boundsOpts.outHeight,
                reqWidth = reqW,
                reqHeight = reqH
            )
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = appCtx.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext Result.failure(IllegalStateException("圖片解碼失敗"))

            val dir = backgroundDir(appCtx)
            if (!dir.exists()) dir.mkdirs()
            val outFile = backgroundFile(appCtx, layer)
            try {
                FileOutputStream(outFile).use { fos ->
                    val ok = decoded.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                    if (!ok) {
                        return@withContext Result.failure(IllegalStateException("圖片壓縮儲存失敗"))
                    }
                }
            } finally {
                decoded.recycle()
            }

            PhantomPrefs.setHasBackgroundImage(appCtx, layer, true)
            trimMemory()
            Result.success(outFile)
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

    fun removeBackground(ctx: Context, layer: BackgroundLayer) {
        val appCtx = ctx.applicationContext
        try {
            val f = backgroundFile(appCtx, layer)
            if (f.exists()) f.delete()
        } catch (_: Exception) {
        }
        PhantomPrefs.setHasBackgroundImage(appCtx, layer, false)
        trimMemory()
    }

    suspend fun loadBackgroundBitmap(
        ctx: Context,
        file: File,
        targetW: Int,
        targetH: Int
    ): ImageBitmap? {
        val reqW = targetW.coerceAtLeast(360)
        val reqH = targetH.coerceAtLeast(640)
        val key = "${file.absolutePath}:${file.lastModified()}:${reqW}x${reqH}"
        synchronized(cacheLock) {
            bitmapCache.get(key)?.let { return it }
        }
        return withContext(Dispatchers.IO) {
            try {
                if (!file.exists() || file.length() <= 0L) {
                    android.util.Log.w("PhantomBackground", "loadBackgroundBitmap skipped: missing or empty file=${file.name}")
                    return@withContext null
                }
                val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
                if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                    android.util.Log.w("PhantomBackground", "loadBackgroundBitmap failed: invalid bounds for file=${file.name}")
                    return@withContext null
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = calculateInSampleSize(
                        boundsOpts.outWidth,
                        boundsOpts.outHeight,
                        reqW,
                        reqH
                    )
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
                val bmp = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                if (bmp == null) {
                    android.util.Log.w("PhantomBackground", "loadBackgroundBitmap failed: decodeFile returned null for file=${file.name}")
                    return@withContext null
                }
                val img = bmp.asImageBitmap()
                synchronized(cacheLock) {
                    bitmapCache.put(key, img)
                }
                img
            } catch (e: OutOfMemoryError) {
                android.util.Log.w("PhantomBackground", "loadBackgroundBitmap OOM for file=${file.name}", e)
                trimMemory()
                null
            } catch (e: Exception) {
                android.util.Log.w("PhantomBackground", "loadBackgroundBitmap exception for file=${file.name}", e)
                null
            }
        }
    }

    suspend fun loadPreviewThumbnail(file: File): ImageBitmap? = withContext(Dispatchers.IO) {
        try {
            if (!file.exists() || file.length() <= 0L) return@withContext null
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return@withContext null
            var sample = 1
            while ((boundsOpts.outWidth / sample) > 256 || (boundsOpts.outHeight / sample) > 256) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            BitmapFactory.decodeFile(file.absolutePath, decodeOpts)?.asImageBitmap()
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
