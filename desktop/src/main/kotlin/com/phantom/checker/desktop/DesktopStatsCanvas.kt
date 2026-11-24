package com.phantom.checker.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.StatsSnapshotDto

/**
 * #468 統計頁升級：Canvas 自繪 7 日量／今日每小時柱狀圖＋風險分布橫條（零新依賴）。
 */
@Composable
fun DesktopStatsCharts(
    stats: StatsSnapshotDto,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDesktopTokens.current
    var showHourly by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 7 日量 / 今日每小時切換柱狀圖
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showHourly) DesktopStrings.statsHourlyChart else DesktopStrings.stats7DayChart,
                        style = MaterialTheme.typography.titleSmall,
                        color = tokens.textPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !showHourly,
                            onClick = { showHourly = false },
                            label = { Text(DesktopStrings.stats7DayMode) }
                        )
                        FilterChip(
                            selected = showHourly,
                            onClick = { showHourly = true },
                            label = { Text(DesktopStrings.statsHourlyMode) }
                        )
                    }
                }

                val barValues: List<Pair<String, Int>> = remember(stats.hourlyCounts, stats.dailyCounts, showHourly) {
                    if (showHourly) {
                        if (stats.hourlyCounts.isNotEmpty()) {
                            stats.hourlyCounts.map { "${it.hour}" to it.count }
                        } else {
                            (0..23).map { "$it" to 0 }
                        }
                    } else {
                        if (stats.dailyCounts.isNotEmpty()) {
                            stats.dailyCounts.map { it.label to it.count }
                        } else {
                            listOf("D-6", "D-5", "D-4", "D-3", "D-2", "D-1", "Today").map { it to 0 }
                        }
                    }
                }

                val maxVal = remember(barValues) { (barValues.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1) }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .padding(top = 8.dp)
                ) {
                    val count = barValues.size.coerceAtLeast(1)
                    val gap = if (showHourly) 4.dp.toPx() else 14.dp.toPx()
                    val totalGap = gap * (count - 1)
                    val barW = ((size.width - totalGap) / count).coerceAtLeast(4f)
                    val chartH = size.height - 8.dp.toPx()

                    // 水平輔助線
                    for (step in 0..3) {
                        val y = chartH * (step / 3f)
                        drawLine(
                            color = tokens.chartGridLine,
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1f
                        )
                    }

                    barValues.forEachIndexed { idx, (_, value) ->
                        val ratio = value.toFloat() / maxVal.toFloat()
                        val h = (chartH * ratio).coerceAtLeast(if (value > 0) 6f else 2f)
                        val x = idx * (barW + gap)
                        val y = chartH - h
                        drawRoundRect(
                            color = if (value > 0) accentColor else tokens.chartGridLine,
                            topLeft = Offset(x, y),
                            size = Size(barW, h),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }

                // X 軸標籤
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (showHourly) {
                        listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach { lbl ->
                            Text(lbl, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                        }
                    } else {
                        barValues.forEach { (lbl, cnt) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(cnt.toString(), style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
                                Text(lbl, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            }
                        }
                    }
                }
            }
        }

        // 2. 風險分布橫條
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(DesktopStrings.statsRiskDist, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                val scam = stats.scam.coerceAtLeast(0)
                val susp = stats.suspicious.coerceAtLeast(0)
                val safe = (stats.total - scam - susp).coerceAtLeast(0)
                val sum = (safe + susp + scam).coerceAtLeast(1).toFloat()

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    val wSafe = size.width * (safe / sum)
                    val wSusp = size.width * (susp / sum)
                    val wScam = size.width * (scam / sum)
                    drawRect(DesktopPalette.GlassSuccess, Offset(0f, 0f), Size(wSafe, size.height))
                    drawRect(DesktopPalette.GlassWarning, Offset(wSafe, 0f), Size(wSusp, size.height))
                    drawRect(DesktopPalette.GlassError, Offset(wSafe + wSusp, 0f), Size(wScam, size.height))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    RiskLegendDot(DesktopPalette.GlassSuccess, "${DesktopStrings.riskLabelText("safe")}: $safe")
                    RiskLegendDot(DesktopPalette.GlassWarning, "${DesktopStrings.riskLabelText("suspicious")}: $susp")
                    RiskLegendDot(DesktopPalette.GlassError, "${DesktopStrings.riskLabelText("scam")}: $scam")
                }
            }
        }
    }
}

@Composable
private fun RiskLegendDot(color: Color, text: String) {
    val tokens = LocalDesktopTokens.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(text, style = MaterialTheme.typography.labelSmall, color = tokens.textSecondary)
    }
}
