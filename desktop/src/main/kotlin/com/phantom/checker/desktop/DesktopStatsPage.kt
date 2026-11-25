package com.phantom.checker.desktop

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.StatsSnapshotDto

/**
 * #520 §7 commit 11：桌面統計頁響應式重編
 * - COMPACT：KPI 2×2 格狀＋圖表單欄
 * - MEDIUM：KPI 1×4＋圖表單欄
 * - EXPANDED / LARGE：最大內容寬 1120/1280dp 置中、KPI 1×4＋圖表 2 欄格狀
 * - KPI 卡點擊直達訊息頁並套用對應篩選（全部／今日／未讀／詐騙）
 */
@Composable
internal fun DesktopStatsPage(
    windowClass: DesktopWindowClass,
    tokens: DesktopThemeTokens,
    accentColor: Color,
    stats: StatsSnapshotDto,
    scrollState: ScrollState,
    onNavigateMessagesWithFilter: (String) -> Unit
) {
    val maxW = windowClass.maxContentWidth
    val contentMod = if (maxW != Dp.Unspecified) Modifier.widthIn(max = maxW).fillMaxWidth() else Modifier.fillMaxWidth()

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = contentMod
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (windowClass == DesktopWindowClass.COMPACT) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBox(
                            label = DesktopStrings.statsTotal,
                            value = stats.total.toString(),
                            modifier = Modifier.weight(1f).clickable { onNavigateMessagesWithFilter("all") },
                            icon = DesktopIcons.Messages,
                            iconTint = tokens.accentText
                        )
                        StatBox(
                            label = DesktopStrings.statsToday,
                            value = stats.today.toString(),
                            modifier = Modifier.weight(1f).clickable { onNavigateMessagesWithFilter("all") },
                            icon = DesktopIcons.Stats,
                            iconTint = tokens.success
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        StatBox(
                            label = DesktopStrings.statsUnread,
                            value = stats.unread.toString(),
                            modifier = Modifier.weight(1f).clickable { onNavigateMessagesWithFilter("unread") },
                            icon = DesktopIcons.Bell,
                            iconTint = tokens.warning
                        )
                        StatBox(
                            label = DesktopStrings.statsScam,
                            value = stats.scam.toString(),
                            modifier = Modifier.weight(1f).clickable { onNavigateMessagesWithFilter("scam") },
                            icon = DesktopIcons.Warning,
                            iconTint = tokens.error
                        )
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    StatBox(
                        label = DesktopStrings.statsTotal,
                        value = stats.total.toString(),
                        modifier = Modifier.weight(1f).clickable { onNavigateMessagesWithFilter("all") },
                        icon = DesktopIcons.Messages,
                        iconTint = tokens.accentText
                    )
                    StatBox(
                        label = DesktopStrings.statsToday,
                        value = stats.today.toString(),
                        modifier = Modifier.weight(1f).clickable { onNavigateMessagesWithFilter("all") },
                        icon = DesktopIcons.Stats,
                        iconTint = tokens.success
                    )
                    StatBox(
                        label = DesktopStrings.statsUnread,
                        value = stats.unread.toString(),
                        modifier = Modifier.weight(1f).clickable { onNavigateMessagesWithFilter("unread") },
                        icon = DesktopIcons.Bell,
                        iconTint = tokens.warning
                    )
                    StatBox(
                        label = DesktopStrings.statsScam,
                        value = stats.scam.toString(),
                        modifier = Modifier.weight(1f).clickable { onNavigateMessagesWithFilter("scam") },
                        icon = DesktopIcons.Warning,
                        iconTint = tokens.error
                    )
                }
            }
            DesktopStatsCharts(
                stats = stats,
                accentColor = accentColor,
                windowClass = windowClass
            )
        }
    }
}
