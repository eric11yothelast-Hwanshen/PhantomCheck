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
import androidx.annotation.StringRes
import com.phantom.checker.R

enum class BackgroundLayer(val fileName: String, @StringRes val labelRes: Int) {
    GLOBAL("global.jpg", R.string.bg_layer_global),
    CHAT("chat.jpg", R.string.bg_layer_chat),
    CONVERSATION("conversation.jpg", R.string.bg_layer_conv);

    val label: String get() = com.phantom.checker.PhantomApplication.str(labelRes)
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
                ?: return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.chat_img_open_failed)))
            openedBounds.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }

            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_a7ce49)))
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
            } ?: return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_06167a)))

            val dir = backgroundDir(appCtx)
            if (!dir.exists()) dir.mkdirs()
            val outFile = backgroundFile(appCtx, layer)
            try {
                FileOutputStream(outFile).use { fos ->
                    val ok = decoded.compress(Bitmap.CompressFormat.JPEG, 85, fos)
                    if (!ok) {
                        return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_10d888)))
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
            Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_3ed646, e.message ?: "OOM")))
        } catch (e: SecurityException) {
            Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.chat_img_no_permission), e))
        } catch (e: java.io.FileNotFoundException) {
            Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.chat_img_open_failed), e))
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

    /** #392 夥伴（助手）頁頂部橫幅背景：沿用 bounds-first inSampleSize 降採樣＋內部儲存防 OOM */
    fun assistantHeaderBackgroundFile(ctx: Context): File =
        File(backgroundDir(ctx), "assistant_header.jpg")

    fun resolveAssistantHeaderBackgroundFile(ctx: Context): File? {
        val f = assistantHeaderBackgroundFile(ctx)
        return if (PhantomPrefs.hasAssistantHeaderBackground(ctx) && f.exists() && f.length() > 0L) f else null
    }

    suspend fun saveAssistantHeaderBackgroundFromUri(
        ctx: Context,
        uri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        try {
            val dm = appCtx.resources.displayMetrics
            val reqW = dm.widthPixels.coerceAtLeast(1080)
            val reqH = (dm.density * 220f).toInt().coerceAtLeast(480)

            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val openedBounds = appCtx.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.chat_img_open_failed)))
            openedBounds.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_a7ce49)))
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
            } ?: return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_06167a)))

            val dir = backgroundDir(appCtx)
            if (!dir.exists()) dir.mkdirs()
            val outFile = assistantHeaderBackgroundFile(appCtx)
            try {
                FileOutputStream(outFile).use { fos ->
                    if (!decoded.compress(Bitmap.CompressFormat.JPEG, 85, fos)) {
                        return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_10d888)))
                    }
                }
            } finally {
                decoded.recycle()
            }

            PhantomPrefs.setHasAssistantHeaderBackground(appCtx, true)
            trimMemory()
            Result.success(outFile)
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

    fun removeAssistantHeaderBackground(ctx: Context) {
        val appCtx = ctx.applicationContext
        try {
            val f = assistantHeaderBackgroundFile(appCtx)
            if (f.exists()) f.delete()
        } catch (_: Exception) {
        }
        PhantomPrefs.setHasAssistantHeaderBackground(appCtx, false)
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
