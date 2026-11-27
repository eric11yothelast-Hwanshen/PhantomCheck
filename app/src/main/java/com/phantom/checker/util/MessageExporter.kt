package com.phantom.checker.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.phantom.checker.data.MessageEntity
import com.phantom.checker.service.AppDatabaseHolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import com.phantom.checker.R

object MessageExporter {

    private const val CSV_HEADER = "時間,來源 App,寄件者,內容,風險分數,標籤,原因"
    private const val EXPORT_BATCH_SIZE = 500
    private const val WRITER_BUFFER_BYTES = 32 * 1024
    private const val DEFAULT_OWN_SENDER_LABEL = "我"
    private val FORMULA_PREFIX_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')

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
        val filterPkg = packageName?.takeIf { it.isNotBlank() }
        val sinceTs = daysBack?.takeIf { it > 0 }?.let {
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(it.toLong())
        }
        val ownSenderLabel = runCatching {
            AppLocaleHelper.localizedContext(appCtx).getString(R.string.chat_s_34f201)
        }.getOrDefault(DEFAULT_OWN_SENDER_LABEL).ifBlank { DEFAULT_OWN_SENDER_LABEL }

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
            val db = AppDatabaseHolder.db(appCtx)
            val sqliteDb = db.openHelper.readableDatabase
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

            resolver.openOutputStream(uri)?.use { out ->
                // 寫入 UTF-8 BOM，確保 Excel 開啟中文不亂碼
                out.write(0xEF)
                out.write(0xBB)
                out.write(0xBF)
                BufferedWriter(OutputStreamWriter(out, Charsets.UTF_8), WRITER_BUFFER_BYTES).use { writer ->
                    writer.write(CSV_HEADER)
                    writer.newLine()

                    var lastTs: Long? = null
                    var lastId: Long? = null
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val whereParts = ArrayList<String>(3)
                        val bindArgs = ArrayList<Any>(5)
                        if (filterPkg != null) {
                            whereParts.add("packageName = ?")
                            bindArgs.add(filterPkg)
                        }
                        if (sinceTs != null) {
                            whereParts.add("timestamp >= ?")
                            bindArgs.add(sinceTs)
                        }
                        if (lastTs != null && lastId != null) {
                            whereParts.add("(timestamp < ? OR (timestamp = ? AND id < ?))")
                            bindArgs.add(lastTs)
                            bindArgs.add(lastTs)
                            bindArgs.add(lastId)
                        }
                        val whereClause = if (whereParts.isEmpty()) "" else "WHERE ${whereParts.joinToString(" AND ")} "
                        val sql = "SELECT id, packageName, appLabel, sender, content, timestamp, riskScore, riskLabel, riskReason, isOwn " +
                            "FROM messages $whereClause" +
                            "ORDER BY timestamp DESC, id DESC LIMIT $EXPORT_BATCH_SIZE"

                        var batchCount = 0
                        sqliteDb.query(sql, bindArgs.toTypedArray()).use { cursor ->
                            while (cursor.moveToNext()) {
                                val id = cursor.getLong(0)
                                val pkg = cursor.getString(1).orEmpty()
                                val appLbl = cursor.getString(2).orEmpty()
                                val sender = cursor.getString(3).orEmpty()
                                val content = cursor.getString(4).orEmpty()
                                val ts = cursor.getLong(5)
                                val riskScore = cursor.getInt(6)
                                val riskLabel = cursor.getString(7).orEmpty()
                                val riskReason = cursor.getString(8).orEmpty()
                                val isOwn = cursor.getInt(9) != 0

                                writeCsvRow(
                                    writer = writer,
                                    dateFormat = dateFormat,
                                    timestamp = ts,
                                    appName = appLbl.ifBlank { pkg },
                                    sender = if (isOwn) ownSenderLabel else sender,
                                    content = content,
                                    riskScore = riskScore,
                                    riskLabel = riskLabel,
                                    riskReason = riskReason
                                )
                                writer.newLine()
                                lastTs = ts
                                lastId = id
                                batchCount++
                            }
                        }
                        if (batchCount < EXPORT_BATCH_SIZE) break
                    }
                    writer.flush()
                }
            } ?: throw IllegalStateException(ctx.getString(R.string.util_export_003))

            val doneValues = ContentValues().apply {
                put(MediaStore.Downloads.IS_PENDING, 0)
            }
            resolver.update(uri, doneValues, null, null)
            Result.success(fileName)
        } catch (e: CancellationException) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            Result.failure(e)
        }
    }

    internal fun writeCsvRow(
        writer: Appendable,
        dateFormat: SimpleDateFormat,
        timestamp: Long,
        appName: String,
        sender: String,
        content: String,
        riskScore: Int,
        riskLabel: String,
        riskReason: String
    ) {
        writer.append(escapeCsv(dateFormat.format(Date(timestamp))))
        writer.append(',').append(escapeCsv(appName))
        writer.append(',').append(escapeCsv(sender))
        writer.append(',').append(escapeCsv(content))
        writer.append(',').append(escapeCsv(riskScore.toString()))
        writer.append(',').append(escapeCsv(riskLabel))
        writer.append(',').append(escapeCsv(riskReason))
    }

    internal fun buildCsv(
        messages: List<MessageEntity>,
        ownSenderLabel: String = DEFAULT_OWN_SENDER_LABEL
    ): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()
        sb.appendLine(CSV_HEADER)
        // 匯出包含使用者自己的回覆（isOwn = true），寄件者欄位依 isOwn 統一標記為 ownSenderLabel（「我」）
        for (msg in messages) {
            writeCsvRow(
                writer = sb,
                dateFormat = dateFormat,
                timestamp = msg.timestamp,
                appName = msg.appLabel.ifBlank { msg.packageName },
                sender = if (msg.isOwn) ownSenderLabel else msg.sender,
                content = msg.content,
                riskScore = msg.riskScore,
                riskLabel = msg.riskLabel,
                riskReason = msg.riskReason
            )
            sb.appendLine()
        }
        return sb.toString()
    }

    internal fun escapeCsv(rawValue: String): String {
        // 防 CSV Formula Injection：攔截 =/+/-/@/\t/\r 開頭及前導空白（含 space/\t/\r/\n）後接公式前綴
        val trimmedStart = rawValue.trimStart(' ', '\t', '\r', '\n')
        val isFormulaRisk = (rawValue.isNotEmpty() && rawValue[0] in FORMULA_PREFIX_CHARS) ||
            (trimmedStart.isNotEmpty() && trimmedStart[0] in FORMULA_PREFIX_CHARS)
        val value = if (isFormulaRisk) "'$rawValue" else rawValue
        val needsQuotes = value.contains(',') ||
            value.contains('"') ||
            value.contains('\n') ||
            value.contains('\r')
        if (!needsQuotes) return value
        return "\"" + value.replace("\"", "\"\"") + "\""
    }
}
