package com.phantom.checker.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun backgroundDir(ctx: Context): File =
        File(ctx.applicationContext.filesDir, "backgrounds")

    fun backgroundFile(ctx: Context, layer: BackgroundLayer): File =
        File(backgroundDir(ctx), layer.fileName)

    /**
     * 主執行緒安全版本：僅檢查 PhantomPrefs 旗標回傳目標 File 路徑，不於主執行緒執行 File.exists()/length() 磁碟呼叫；
     * 實際存在性與非空檢查統一於 loadBackgroundBitmap(Dispatchers.IO) 進行。
     */
    fun resolveBackgroundFile(ctx: Context, layer: BackgroundLayer): File? {
        if (PhantomPrefs.hasBackgroundImage(ctx, layer)) {
            return backgroundFile(ctx, layer)
        }
        if (layer != BackgroundLayer.GLOBAL && PhantomPrefs.hasBackgroundImage(ctx, BackgroundLayer.GLOBAL)) {
            return backgroundFile(ctx, BackgroundLayer.GLOBAL)
        }
        return null
    }

    suspend fun resolveBackgroundFileIo(ctx: Context, layer: BackgroundLayer): File? = withContext(Dispatchers.IO) {
        val primary = backgroundFile(ctx, layer)
        if (PhantomPrefs.hasBackgroundImage(ctx, layer) && primary.exists() && primary.length() > 0L) {
            return@withContext primary
        }
        if (layer != BackgroundLayer.GLOBAL) {
            val fallback = backgroundFile(ctx, BackgroundLayer.GLOBAL)
            if (PhantomPrefs.hasBackgroundImage(ctx, BackgroundLayer.GLOBAL) &&
                fallback.exists() &&
                fallback.length() > 0L
            ) {
                return@withContext fallback
            }
        }
        null
    }

    fun hasAnyBackground(ctx: Context): Boolean =
        BackgroundLayer.entries.any { layer ->
            PhantomPrefs.hasBackgroundImage(ctx, layer)
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

    private fun writeBitmapAtomically(dir: File, outFile: File, bitmap: Bitmap): Boolean {
        if (!dir.exists()) dir.mkdirs()
        val tmpFile = File(dir, "${outFile.name}.tmp")
        return try {
            FileOutputStream(tmpFile).use { fos ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)) {
                    runCatching { tmpFile.delete() }
                    return false
                }
                fos.flush()
                runCatching { fos.fd.sync() }
            }
            if (!tmpFile.renameTo(outFile)) {
                tmpFile.copyTo(outFile, overwrite = true)
                runCatching { tmpFile.delete() }
            }
            true
        } catch (e: Exception) {
            runCatching { tmpFile.delete() }
            throw e
        }
    }

    suspend fun saveBackgroundFromUri(
        ctx: Context,
        layer: BackgroundLayer,
        uri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        try {
            val orientation = readExifOrientation(appCtx, uri)
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
            val rawDecoded = appCtx.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_06167a)))
            val decoded = AssistantAvatarHelper.applyExifOrientation(rawDecoded, orientation)

            val dir = backgroundDir(appCtx)
            val outFile = backgroundFile(appCtx, layer)
            try {
                if (!writeBitmapAtomically(dir, outFile, decoded)) {
                    return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_10d888)))
                }
            } finally {
                if (!decoded.isRecycled) decoded.recycle()
            }

            PhantomPrefs.setHasBackgroundImage(appCtx, layer, true)
            trimMemory()
            Result.success(outFile)
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

    fun removeBackground(ctx: Context, layer: BackgroundLayer) {
        val appCtx = ctx.applicationContext
        PhantomPrefs.setHasBackgroundImage(appCtx, layer, false)
        trimMemory()
        ioScope.launch {
            runCatching {
                val f = backgroundFile(appCtx, layer)
                if (f.exists()) f.delete()
                val tmp = File(backgroundDir(appCtx), "${f.name}.tmp")
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    suspend fun removeBackgroundSuspend(ctx: Context, layer: BackgroundLayer) = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        runCatching {
            val f = backgroundFile(appCtx, layer)
            if (f.exists()) f.delete()
            val tmp = File(backgroundDir(appCtx), "${f.name}.tmp")
            if (tmp.exists()) tmp.delete()
        }
        PhantomPrefs.setHasBackgroundImage(appCtx, layer, false)
        trimMemory()
    }

    /** #392 夥伴（助手）頁頂部橫幅背景：沿用 bounds-first inSampleSize 降採樣＋內部儲存防 OOM */
    fun assistantHeaderBackgroundFile(ctx: Context): File =
        File(backgroundDir(ctx), "assistant_header.jpg")

    fun resolveAssistantHeaderBackgroundFile(ctx: Context): File? =
        if (PhantomPrefs.hasAssistantHeaderBackground(ctx)) assistantHeaderBackgroundFile(ctx) else null

    suspend fun saveAssistantHeaderBackgroundFromUri(
        ctx: Context,
        uri: Uri
    ): Result<File> = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        try {
            val orientation = readExifOrientation(appCtx, uri)
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
            val rawDecoded = appCtx.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOpts)
            } ?: return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_06167a)))
            val decoded = AssistantAvatarHelper.applyExifOrientation(rawDecoded, orientation)

            val dir = backgroundDir(appCtx)
            val outFile = assistantHeaderBackgroundFile(appCtx)
            try {
                if (!writeBitmapAtomically(dir, outFile, decoded)) {
                    return@withContext Result.failure(IllegalStateException(com.phantom.checker.PhantomApplication.str(R.string.bg_img_s_10d888)))
                }
            } finally {
                if (!decoded.isRecycled) decoded.recycle()
            }

            PhantomPrefs.setHasAssistantHeaderBackground(appCtx, true)
            trimMemory()
            Result.success(outFile)
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

    fun removeAssistantHeaderBackground(ctx: Context) {
        val appCtx = ctx.applicationContext
        PhantomPrefs.setHasAssistantHeaderBackground(appCtx, false)
        trimMemory()
        ioScope.launch {
            runCatching {
                val f = assistantHeaderBackgroundFile(appCtx)
                if (f.exists()) f.delete()
                val tmp = File(backgroundDir(appCtx), "${f.name}.tmp")
                if (tmp.exists()) tmp.delete()
            }
        }
    }

    suspend fun removeAssistantHeaderBackgroundSuspend(ctx: Context) = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        runCatching {
            val f = assistantHeaderBackgroundFile(appCtx)
            if (f.exists()) f.delete()
            val tmp = File(backgroundDir(appCtx), "${f.name}.tmp")
            if (tmp.exists()) tmp.delete()
        }
        PhantomPrefs.setHasAssistantHeaderBackground(appCtx, false)
        trimMemory()
    }

    suspend fun loadBackgroundBitmap(
        ctx: Context,
        file: File,
        targetW: Int,
        targetH: Int
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        val reqW = targetW.coerceAtLeast(360)
        val reqH = targetH.coerceAtLeast(640)
        try {
            if (!file.exists() || file.length() <= 0L) {
                android.util.Log.w("PhantomBackground", "loadBackgroundBitmap skipped: missing or empty file=${file.name}")
                return@withContext null
            }
            val key = "${file.absolutePath}:${file.lastModified()}:${reqW}x${reqH}"
            synchronized(cacheLock) {
                bitmapCache.get(key)?.let { return@withContext it }
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            android.util.Log.w("PhantomBackground", "loadBackgroundBitmap OOM for file=${file.name}", e)
            trimMemory()
            null
        } catch (e: Exception) {
            android.util.Log.w("PhantomBackground", "loadBackgroundBitmap exception for file=${file.name}", e)
            null
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
