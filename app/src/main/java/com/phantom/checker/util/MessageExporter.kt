package com.phantom.checker.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.service.AppDatabaseHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.phantom.checker.R

object MessageExporter {

    private const val CSV_HEADER = "時間,來源 App,寄件者,內容,風險分數,標籤,原因"

    suspend fun exportToDownloads(
        ctx: Context,
        packageName: String? = null,
        daysBack: Int? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext Result.failure(
                UnsupportedOperationException(ctx.getString(R.string.util_export_001))
            )
        }

        val appCtx = ctx.applicationContext
        val sinceTs = daysBack?.takeIf { it > 0 }?.let {
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it.toLong())
        }
        val messages = try {
            AppDatabaseHolder.db(appCtx).messageDao().exportQuery(
                packageName = packageName?.takeIf { it.isNotBlank() },
                sinceTs = sinceTs
            )
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }

        val csvContent = buildCsv(messages)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "PhantomCheck_messages_$timestamp.csv"

        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = appCtx.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext Result.failure(IllegalStateException(ctx.getString(R.string.util_export_002)))

        try {
            resolver.openOutputStream(uri)?.use { out ->
                // 寫入 UTF-8 BOM，確保 Excel 開啟中文不亂碼
                out.write(0xEF)
                out.write(0xBB)
                out.write(0xBF)
                out.write(csvContent.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException(ctx.getString(R.string.util_export_003))

            val doneValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, doneValues, null, null)
            Result.success(fileName)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Result.failure(e)
        }
    }

    internal fun buildCsv(messages: List<MessageEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine(CSV_HEADER)
        // 匯出包含使用者自己的回覆（isOwn = true），寄件者欄位固定標記為「我」
        for (msg in messages) {
            val timeStr = dateFormat.format(Date(msg.timestamp))
            val appName = msg.appLabel.ifBlank { msg.packageName }
            val row = listOf(
                timeStr,
                appName,
                msg.sender,
                msg.content,
                msg.riskScore.toString(),
                msg.riskLabel,
                msg.riskReason
            ).joinToString(",") { escapeCsv(it) }
            sb.appendLine(row)
        }
        return sb.toString()
    }

    internal fun escapeCsv(rawValue: String): String {
        // 防 CSV Formula Injection：以 =/+/-/@ 開頭的值加上單引號前綴
        val value = if (rawValue.isNotEmpty() && rawValue[0] in charArrayOf('=', '+', '-', '@')) {
            "'$rawValue"
        } else {
            rawValue
        }
        val needsQuotes = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
