package com.phantom.checker.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.ActiveAppStatDto
import com.phantom.checker.shared.StatsSnapshotDto

private enum class StatsChartMode { DAYS_7, DAYS_30, HOURLY }

/**
 * #468 / #498 統計頁升級：Canvas 7日/30日/每小時柱狀圖＋風險分布比例條＋今日活躍 App 名稱＋本週活躍 Top 5。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesktopStatsCharts(
    stats: StatsSnapshotDto,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalDesktopTokens.current
    var chartMode by remember { mutableStateOf(StatsChartMode.DAYS_7) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // 1. 7 日 / 30 日 / 今日每小時切換柱狀圖
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DesktopIcons.Stats, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Text(
                            text = when (chartMode) {
                                StatsChartMode.DAYS_7 -> DesktopStrings.stats7DayChart
                                StatsChartMode.DAYS_30 -> DesktopStrings.stats30DayChart
                                StatsChartMode.HOURLY -> DesktopStrings.statsHourlyChart
                            },
                            style = MaterialTheme.typography.titleSmall,
                            color = tokens.textPrimary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = chartMode == StatsChartMode.DAYS_7,
                            onClick = { chartMode = StatsChartMode.DAYS_7 },
                            label = { Text(DesktopStrings.stats7DayMode) }
                        )
                        FilterChip(
                            selected = chartMode == StatsChartMode.DAYS_30,
                            onClick = { chartMode = StatsChartMode.DAYS_30 },
                            label = { Text(DesktopStrings.stats30DayMode) }
                        )
                        FilterChip(
                            selected = chartMode == StatsChartMode.HOURLY,
                            onClick = { chartMode = StatsChartMode.HOURLY },
                            label = { Text(DesktopStrings.statsHourlyMode) }
                        )
                    }
                }

                val barValues: List<Pair<String, Int>> = remember(stats.hourlyCounts, stats.dailyCounts, stats.dailyCounts30, chartMode) {
                    when (chartMode) {
                        StatsChartMode.HOURLY -> {
                            if (stats.hourlyCounts.isNotEmpty()) stats.hourlyCounts.map { "${it.hour}" to it.count }
                            else (0..23).map { "$it" to 0 }
                        }
                        StatsChartMode.DAYS_30 -> {
                            if (stats.dailyCounts30.isNotEmpty()) stats.dailyCounts30.map { it.label to it.count }
                            else if (stats.dailyCounts.isNotEmpty()) stats.dailyCounts.map { it.label to it.count }
                            else (1..30).map { "D-$it" to 0 }
                        }
                        StatsChartMode.DAYS_7 -> {
                            if (stats.dailyCounts.isNotEmpty()) stats.dailyCounts.map { it.label to it.count }
                            else listOf("D-6", "D-5", "D-4", "D-3", "D-2", "D-1", "Today").map { it to 0 }
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
                    val gap = when (chartMode) {
                        StatsChartMode.DAYS_7 -> 14.dp.toPx()
                        StatsChartMode.DAYS_30 -> 3.dp.toPx()
                        StatsChartMode.HOURLY -> 4.dp.toPx()
                    }
                    val totalGap = gap * (count - 1)
                    val barW = ((size.width - totalGap) / count).coerceAtLeast(3f)
                    val chartH = size.height - 8.dp.toPx()

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
                            cornerRadius = CornerRadius(5f, 5f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    when (chartMode) {
                        StatsChartMode.HOURLY -> {
                            listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach { lbl ->
                                Text(lbl, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            }
                        }
                        StatsChartMode.DAYS_30 -> {
                            val step = (barValues.size / 5).coerceAtLeast(1)
                            barValues.filterIndexed { idx, _ -> idx % step == 0 || idx == barValues.lastIndex }.forEach { (lbl, _) ->
                                Text(lbl, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                            }
                        }
                        StatsChartMode.DAYS_7 -> {
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
        }

        // 2. 風險分布比例條 + 百分比圖例
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DesktopIcons.Rules, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Text(DesktopStrings.statsRiskDist, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                }
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

                val safePct = ((safe * 100f) / sum).toInt()
                val suspPct = ((susp * 100f) / sum).toInt()
                val scamPct = ((scam * 100f) / sum).toInt()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RiskLegendDot(DesktopPalette.GlassSuccess, "${DesktopStrings.riskLabelText("safe")}: $safe ($safePct%)")
                    RiskLegendDot(DesktopPalette.GlassWarning, "${DesktopStrings.riskLabelText("suspicious")}: $susp ($suspPct%)")
                    RiskLegendDot(DesktopPalette.GlassError, "${DesktopStrings.riskLabelText("scam")}: $scam ($scamPct%)")
                }
            }
        }

        // 3. #498 今日活躍來源 App（顯示 App 名稱而非 packageName + 活躍度排序 + 訊息數）
        val resolvedTodayApps: List<ActiveAppStatDto> = remember(stats.activeAppsToday, stats.recentPackages) {
            if (stats.activeAppsToday.isNotEmpty()) {
                stats.activeAppsToday.sortedByDescending { it.count }
            } else {
                stats.recentPackages
                    .filter { it.isNotBlank() }
                    .distinct()
                    .map { pkg -> ActiveAppStatDto(packageName = pkg, appLabel = DesktopAppLabels.resolveLabel(pkg), count = 0) }
            }
        }
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(DesktopIcons.Messages, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Text(DesktopStrings.statsRecentApps, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                }
                if (resolvedTodayApps.isEmpty()) {
                    Text("-", style = MaterialTheme.typography.bodyMedium, color = tokens.textMuted)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        resolvedTodayApps.forEach { app ->
                            val label = DesktopAppLabels.resolveLabel(app.packageName, app.appLabel)
                            val color = DesktopAppLabels.resolveColor(app.packageName, app.colorHex)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(color.copy(alpha = 0.14f))
                                    .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                Text(label, style = MaterialTheme.typography.labelMedium, color = tokens.textPrimary, fontWeight = FontWeight.Medium)
                                if (app.count > 0) {
                                    Text(
                                        text = DesktopStrings.statsMsgCount(app.count),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. #498 本週活躍 App Top 5
        val top5 = remember(stats.weeklyTopApps, resolvedTodayApps) {
            (if (stats.weeklyTopApps.isNotEmpty()) stats.weeklyTopApps else resolvedTodayApps)
                .sortedByDescending { it.count }
                .take(5)
        }
        if (top5.isNotEmpty()) {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DesktopIcons.Star, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Text(DesktopStrings.statsWeeklyTopApps, style = MaterialTheme.typography.titleSmall, color = tokens.textPrimary)
                    }
                    val maxAppCnt = (top5.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1).toFloat()
                    top5.forEachIndexed { idx, app ->
                        val label = DesktopAppLabels.resolveLabel(app.packageName, app.appLabel)
                        val color = DesktopAppLabels.resolveColor(app.packageName, app.colorHex)
                        val ratio = (app.count.toFloat() / maxAppCnt).coerceIn(0.06f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text("#${idx + 1}", style = MaterialTheme.typography.labelSmall, color = tokens.textMuted)
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                                    Text(label, style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (app.count > 0) DesktopStrings.statsMsgCount(app.count) else "-",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = tokens.textSecondary
                                )
                            }
                            Canvas(modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))) {
                                drawRect(tokens.chartGridLine, Offset.Zero, size)
                                drawRoundRect(color, Offset.Zero, Size(size.width * ratio, size.height), CornerRadius(3f, 3f))
                            }
                        }
                    }
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
