package com.phantom.checker.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ShareCardReportData(
    val todayCount: Int,
    val weekTotalCount: Int,
    val safeCount: Int,
    val suspiciousCount: Int,
    val scamCount: Int,
    val dailyLabels: List<String>,
    val dailyCounts: List<Int>,
    val topApps: List<Pair<String, Int>>
)

/**
 * 本機純 Canvas 產生靜態週報圖片並透過系統 ACTION_SEND 分享（不經 LLM、不上傳任何訊息內容）。
 */
object StatsShareCardGenerator {
    fun generateAndShare(ctx: Context, data: ShareCardReportData): Result<Unit> {
        return try {
            val width = 1080
            val height = 1350
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 深色漸層背景
            val bgPaint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, width.toFloat(), height.toFloat(),
                    intArrayOf(0xFF0F172A.toInt(), 0xFF1E1B4B.toInt(), 0xFF0F172A.toInt()),
                    null,
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 54f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFCBD5E1.toInt()
                textSize = 30f
            }
            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x26FFFFFF
            }
            val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = 0x44FFFFFF
            }

            // Header
            canvas.drawText("幻訊 PhantomCheck", 64f, 110f, titlePaint)
            val dateStr = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date())
            canvas.drawText("每週通訊與防詐統計報告 · $dateStr", 64f, 160f, subPaint)

            // 總覽卡片
            val overviewRect = RectF(64f, 210f, width - 64f, 410f)
            canvas.drawRoundRect(overviewRect, 32f, 32f, cardPaint)
            canvas.drawRoundRect(overviewRect, 32f, 32f, cardBorderPaint)

            val bigNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 68f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val metricLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF94A3B8.toInt()
                textSize = 28f
            }
            canvas.drawText("${data.todayCount}", 110f, 310f, bigNumPaint)
            canvas.drawText("今日訊息", 110f, 360f, metricLabelPaint)

            canvas.drawText("${data.weekTotalCount}", 420f, 310f, bigNumPaint)
            canvas.drawText("近 7 日總計", 420f, 360f, metricLabelPaint)

            val scamPaint = Paint(bigNumPaint).apply { color = 0xFFF87171.toInt() }
            canvas.drawText("${data.scamCount}", 760f, 310f, scamPaint)
            canvas.drawText("詐騙警示", 760f, 360f, metricLabelPaint)

            // 風險分布條
            val riskRect = RectF(64f, 450f, width - 64f, 650f)
            canvas.drawRoundRect(riskRect, 32f, 32f, cardPaint)
            canvas.drawRoundRect(riskRect, 32f, 32f, cardBorderPaint)

            val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 36f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("風險分布", 104f, 510f, sectionPaint)

            val totalRisk = (data.safeCount + data.suspiciousCount + data.scamCount).coerceAtLeast(1)
            val barLeft = 104f
            val barRight = width - 104f
            val barWidth = barRight - barLeft
            val safeW = barWidth * (data.safeCount.toFloat() / totalRisk)
            val suspW = barWidth * (data.suspiciousCount.toFloat() / totalRisk)
            val scamW = (barWidth - safeW - suspW).coerceAtLeast(0f)

            val barTop = 540f
            val barBottom = 576f
            val safeBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF34D399.toInt() }
            val suspBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFBBF24.toInt() }
            val scamBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF87171.toInt() }

            canvas.drawRect(barLeft, barTop, barLeft + safeW, barBottom, safeBarPaint)
            canvas.drawRect(barLeft + safeW, barTop, barLeft + safeW + suspW, barBottom, suspBarPaint)
            canvas.drawRect(barLeft + safeW + suspW, barTop, barLeft + safeW + suspW + scamW, barBottom, scamBarPaint)

            canvas.drawText(
                "安全 ${data.safeCount}  ·  疑似 ${data.suspiciousCount}  ·  詐騙 ${data.scamCount}",
                104f,
                624f,
                subPaint
            )

            // 7 日趨勢圖
            val chartRect = RectF(64f, 690f, width - 64f, 990f)
            canvas.drawRoundRect(chartRect, 32f, 32f, cardPaint)
            canvas.drawRoundRect(chartRect, 32f, 32f, cardBorderPaint)
            canvas.drawText("近 7 日訊息量", 104f, 750f, sectionPaint)

            val days = data.dailyCounts.take(7)
            val labels = data.dailyLabels.take(7)
            if (days.isNotEmpty()) {
                val maxDay = (days.maxOrNull() ?: 1).coerceAtLeast(1)
                val slotW = (width - 208f) / days.size
                val chartBaseY = 915f
                val maxBarH = 120f
                val dayBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF818CF8.toInt() }
                val dayLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xFFCBD5E1.toInt()
                    textSize = 24f
                    textAlign = Paint.Align.CENTER
                }
                days.forEachIndexed { idx, cnt ->
                    val cx = 104f + idx * slotW + slotW / 2f
                    val h = (cnt.toFloat() / maxDay) * maxBarH
                    val r = RectF(cx - 22f, chartBaseY - h.coerceAtLeast(8f), cx + 22f, chartBaseY)
                    canvas.drawRoundRect(r, 12f, 12f, dayBarPaint)
                    canvas.drawText("$cnt", cx, chartBaseY - h.coerceAtLeast(8f) - 10f, dayLabelPaint)
                    val lbl = labels.getOrNull(idx).orEmpty()
                    canvas.drawText(lbl, cx, 955f, dayLabelPaint)
                }
            }

            // 熱門 App
            val topRect = RectF(64f, 1030f, width - 64f, 1250f)
            canvas.drawRoundRect(topRect, 32f, 32f, cardPaint)
            canvas.drawRoundRect(topRect, 32f, 32f, cardBorderPaint)
            canvas.drawText("最活躍來源", 104f, 1090f, sectionPaint)

            val topList = data.topApps.take(3)
            if (topList.isEmpty()) {
                canvas.drawText("尚無足夠紀錄", 104f, 1150f, subPaint)
            } else {
                topList.forEachIndexed { i, (appName, cnt) ->
                    canvas.drawText("${i + 1}. $appName（$cnt 則）", 104f, 1142f + i * 44f, subPaint)
                }
            }

            val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF64748B.toInt()
                textSize = 24f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("本機 Canvas 產生・未上傳任何訊息內容", width / 2f, 1305f, footerPaint)

            val shareDir = File(ctx.cacheDir, "share").apply { mkdirs() }
            val outFile = File(shareDir, "phantom_weekly_report.png")
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, fos)
            }
            bitmap.recycle()

            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", outFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "幻訊 PhantomCheck 統計報告")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(shareIntent, "分享統計報告圖片"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
