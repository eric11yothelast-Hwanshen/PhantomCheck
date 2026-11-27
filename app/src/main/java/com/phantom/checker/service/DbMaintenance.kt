package com.phantom.checker.service

import android.content.Context
import android.os.StatFs
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * SQLite 空間維護與儲存空間統計工具（純唯讀政策，不動保留設定與 scam 證據）。
 * - 先檢查 StatFs 剩餘空間 > DB 大小 × 2.5，再執行 PRAGMA wal_checkpoint(TRUNCATE) 與 VACUUM。
 * - 背景排程一律套用 requiresDeviceIdle ＋ requiresBatteryNotLow 約束，避免於低電量或忙碌期執行。
 * - 若遇 SQLITE_BUSY / 鎖定例外則靜默放棄，待下次排程或手動重試。
 */
object DbMaintenance {
    private const val PREFS_NAME = "db_maintenance_prefs"
    private const val KEY_LAST_VACUUM_AT = "last_vacuum_at"
    internal const val UNIQUE_VACUUM_WORK = "phantom_db_vacuum"
    private const val DB_FILE_NAME = "phantomcheck.db"
    private const val RETENTION_VACUUM_ROW_THRESHOLD = 1000
    private const val VACUUM_FREE_SPACE_MULTIPLIER = 2.5
    private val THROTTLE_MS = TimeUnit.HOURS.toMillis(24)

    data class StorageStats(
        val dbBytes: Long,
        val backgroundsBytes: Long,
        val cacheBytes: Long
    ) {
        val totalBytes: Long get() = dbBytes + backgroundsBytes + cacheBytes
    }

    fun hasEnoughFreeSpaceForVacuum(ctx: Context): Boolean {
        val appCtx = ctx.applicationContext
        val dbBytes = measureDatabaseBytes(appCtx)
        if (dbBytes <= 0L) return true
        val dbDir = appCtx.getDatabasePath(DB_FILE_NAME).parentFile ?: appCtx.filesDir
        val availBytes = runCatching {
            StatFs(dbDir.path).availableBytes
        }.getOrDefault(0L)
        return availBytes.toDouble() > dbBytes.toDouble() * VACUUM_FREE_SPACE_MULTIPLIER
    }

    suspend fun checkpointTruncateAndVacuum(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        if (!hasEnoughFreeSpaceForVacuum(appCtx)) {
            android.util.Log.w("DbMaintenance", "Skip VACUUM: available storage <= 2.5x DB size")
            return@withContext false
        }
        try {
            val sqlDb = AppDatabaseHolder.db(appCtx).openHelper.writableDatabase
            sqlDb.query("PRAGMA wal_checkpoint(TRUNCATE)").use { cursor ->
                while (cursor.moveToNext()) {
                    // 完整讀取 checkpoint 回傳列以確保釋放資源
                }
            }
            sqlDb.execSQL("VACUUM")
            recordLastVacuumAt(appCtx)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // SQLITE_BUSY 或資料庫鎖定時靜默放棄，下次再試
            false
        }
    }

    suspend fun runIfThrottledAndNeeded(ctx: Context, deletedRows: Int): Boolean {
        if (deletedRows <= RETENTION_VACUUM_ROW_THRESHOLD) return false
        val appCtx = ctx.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastAt = prefs.getLong(KEY_LAST_VACUUM_AT, 0L)
        val now = System.currentTimeMillis()
        if (now - lastAt < THROTTLE_MS) return false
        if (!hasEnoughFreeSpaceForVacuum(appCtx)) return false
        // 交由帶 requiresDeviceIdle / requiresBatteryNotLow 約束之 VacuumWorker 於閒置且非低電量時執行
        enqueueVacuum(appCtx)
        return true
    }

    fun enqueueVacuum(ctx: Context): UUID {
        val appCtx = ctx.applicationContext
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<VacuumWorker>()
            .setConstraints(constraints)
            .build()
        WorkManager.getInstance(appCtx).enqueueUniqueWork(
            UNIQUE_VACUUM_WORK,
            ExistingWorkPolicy.KEEP,
            request
        )
        return request.id
    }

    fun measureDatabaseBytes(ctx: Context): Long {
        val appCtx = ctx.applicationContext
        val dbFile = appCtx.getDatabasePath(DB_FILE_NAME)
        val walFile = File("${dbFile.path}-wal")
        val shmFile = File("${dbFile.path}-shm")
        return fileSizeOrZero(dbFile) + fileSizeOrZero(walFile) + fileSizeOrZero(shmFile)
    }

    fun measureStorageStats(ctx: Context): StorageStats {
        val appCtx = ctx.applicationContext
        val dbBytes = measureDatabaseBytes(appCtx)
        val backgroundsBytes = dirSizeRecursive(File(appCtx.filesDir, "backgrounds"))
        val cacheBytes = dirSizeRecursive(appCtx.cacheDir)
        return StorageStats(
            dbBytes = dbBytes,
            backgroundsBytes = backgroundsBytes,
            cacheBytes = cacheBytes
        )
    }

    private fun recordLastVacuumAt(ctx: Context) {
        ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_VACUUM_AT, System.currentTimeMillis())
            .apply()
    }

    private fun fileSizeOrZero(file: File): Long =
        if (file.exists() && file.isFile) file.length().coerceAtLeast(0L) else 0L

    private fun dirSizeRecursive(dir: File): Long {
        if (!dir.exists()) return 0L
        if (dir.isFile) return dir.length().coerceAtLeast(0L)
        var total = 0L
        val children = dir.listFiles() ?: return 0L
        for (child in children) {
            total += if (child.isDirectory) dirSizeRecursive(child) else child.length().coerceAtLeast(0L)
        }
        return total
    }
}

class VacuumWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return try {
            DbMaintenance.checkpointTruncateAndVacuum(applicationContext)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("VacuumWorker", "doWork failed: ${e.javaClass.simpleName}", e)
            Result.failure()
        }
    }
}
