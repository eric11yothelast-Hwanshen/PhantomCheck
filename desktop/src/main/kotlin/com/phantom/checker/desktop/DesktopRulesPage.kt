package com.phantom.checker.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.phantom.checker.shared.LinkMsgType
import com.phantom.checker.shared.MutedAppDto
import com.phantom.checker.shared.MutedAppToggleReq
import com.phantom.checker.shared.RuleRemoveReq
import com.phantom.checker.shared.TagAddReq
import com.phantom.checker.shared.TagItemDto
import com.phantom.checker.shared.TagRemoveReq
import com.phantom.checker.shared.WhitelistAddReq
import com.phantom.checker.shared.RuleItemDto
import kotlinx.coroutines.launch

/**
 * #520 §7 commit 11：桌面規則頁響應式重編
 * - COMPACT / MEDIUM：單欄三卡
 * - EXPANDED / LARGE：最大內容寬 1120/1280dp 置中、雙欄（左：白名單；右：標籤＋靜音 App）
 * - 各卡標題附計數徽章膠囊；完整保留 rulesCollapse* 持久化與所有增刪開關功能
 */
@Composable
internal fun DesktopRulesPage(
    windowClass: DesktopWindowClass,
    settings: DesktopSettings,
    tokens: DesktopThemeTokens,
    accentColor: Color,
    whitelistRules: List<RuleItemDto>,
    tags: List<TagItemDto>,
    mutedApps: List<MutedAppDto>,
    newRuleSender: String,
    onNewRuleSenderChange: (String) -> Unit,
    newRulePkg: String,
    onNewRulePkgChange: (String) -> Unit,
    newTagName: String,
    onNewTagNameChange: (String) -> Unit,
    scrollState: ScrollState,
    sendOrNotify: suspend (String, Any) -> Boolean
) {
    val scope = rememberCoroutineScope()
    val maxW = windowClass.maxContentWidth
    val contentMod = if (maxW != Dp.Unspecified) Modifier.widthIn(max = maxW).fillMaxWidth() else Modifier.fillMaxWidth()

    val renderCountBadge: @Composable (Int) -> Unit = { count ->
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(tokens.selectedBg)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = tokens.accentText
            )
        }
    }

    val renderAllowlistCard: @Composable (Modifier) -> Unit = { cardMod ->
        GlassCard(modifier = cardMod) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { DesktopSettingsStore.update { it.copy(rulesCollapseAllowlist = !it.rulesCollapseAllowlist) } },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DesktopIcons.Rules, contentDescription = null, tint = tokens.accentText, modifier = Modifier.size(18.dp))
                        Text(DesktopStrings.whitelistTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                        renderCountBadge(whitelistRules.size)
                    }
                    Text(if (settings.rulesCollapseAllowlist) "▸" else "▾", color = tokens.textMuted)
                }
                AnimatedVisibility(visible = !settings.rulesCollapseAllowlist) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newRuleSender, onValueChange = onNewRuleSenderChange, label = { Text(DesktopStrings.whitelistSenderHint) }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(value = newRulePkg, onValueChange = onNewRulePkgChange, label = { Text(DesktopStrings.whitelistPkgHint) }, modifier = Modifier.weight(1f), singleLine = true)
                            SpringButton(onClick = {
                                if (newRuleSender.isNotBlank()) {
                                    scope.launch {
                                        if (sendOrNotify(LinkMsgType.WHITELIST_ADD, WhitelistAddReq(sender = newRuleSender, packageName = newRulePkg, ruleText = newRuleSender))) {
                                            onNewRuleSenderChange("")
                                            onNewRulePkgChange("")
                                        }
                                    }
                                }
                            }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.add) }
                        }
                        if (whitelistRules.isEmpty()) {
                            Text(DesktopStrings.emptyWhitelist, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                        } else {
                            whitelistRules.forEach { r ->
                                HorizontalDivider(color = tokens.border)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${r.ruleText} ${if (r.packageName.isNotBlank()) "(${r.packageName})" else ""}", style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                    TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.WHITELIST_REMOVE, RuleRemoveReq(r.id)) } }) {
                                        Text(DesktopStrings.remove, color = tokens.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val renderTagsCard: @Composable (Modifier) -> Unit = { cardMod ->
        GlassCard(modifier = cardMod) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { DesktopSettingsStore.update { it.copy(rulesCollapseTags = !it.rulesCollapseTags) } },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DesktopIcons.Tag, contentDescription = null, tint = tokens.accentText, modifier = Modifier.size(18.dp))
                        Text(DesktopStrings.tagsTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                        renderCountBadge(tags.size)
                    }
                    Text(if (settings.rulesCollapseTags) "▸" else "▾", color = tokens.textMuted)
                }
                AnimatedVisibility(visible = !settings.rulesCollapseTags) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(value = newTagName, onValueChange = onNewTagNameChange, label = { Text(DesktopStrings.tagNameHint) }, modifier = Modifier.weight(1f), singleLine = true)
                            SpringButton(onClick = {
                                if (newTagName.isNotBlank()) {
                                    scope.launch {
                                        if (sendOrNotify(LinkMsgType.TAGS_ADD, TagAddReq(name = newTagName))) onNewTagNameChange("")
                                    }
                                }
                            }, animationsEnabled = settings.animationsEnabled) { Text(DesktopStrings.add) }
                        }
                        if (tags.isEmpty()) {
                            Text(DesktopStrings.emptyTags, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                        } else {
                            tags.forEach { t ->
                                HorizontalDivider(color = tokens.border)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(t.name, style = MaterialTheme.typography.bodySmall, color = tokens.textSecondary)
                                    TextButton(onClick = { scope.launch { sendOrNotify(LinkMsgType.TAGS_REMOVE, TagRemoveReq(t.id)) } }) {
                                        Text(DesktopStrings.remove, color = tokens.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val renderMutedAppsCard: @Composable (Modifier) -> Unit = { cardMod ->
        GlassCard(modifier = cardMod) {
            Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { DesktopSettingsStore.update { it.copy(rulesCollapseMuted = !it.rulesCollapseMuted) } },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(DesktopIcons.Bell, contentDescription = null, tint = tokens.accentText, modifier = Modifier.size(18.dp))
                        Text(DesktopStrings.mutedAppsTitle, style = MaterialTheme.typography.titleMedium, color = tokens.textPrimary)
                        renderCountBadge(mutedApps.count { it.muted })
                    }
                    Text(if (settings.rulesCollapseMuted) "▸" else "▾", color = tokens.textMuted)
                }
                AnimatedVisibility(visible = !settings.rulesCollapseMuted) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (mutedApps.isEmpty()) {
                            Text(DesktopStrings.mutedAppsEmpty, style = MaterialTheme.typography.bodySmall, color = tokens.textMuted)
                        } else {
                            mutedApps.forEach { app ->
                                HorizontalDivider(color = tokens.border)
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(DesktopAppLabels.resolveLabel(app.packageName, app.appLabel), style = MaterialTheme.typography.bodySmall, color = tokens.textPrimary)
                                        Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = tokens.textMuted, fontFamily = FontFamily.Monospace)
                                    }
                                    Switch(
                                        checked = app.muted,
                                        onCheckedChange = { checked ->
                                            scope.launch { sendOrNotify(LinkMsgType.MUTED_APPS_TOGGLE, MutedAppToggleReq(packageName = app.packageName, muted = checked)) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = contentMod.verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (windowClass == DesktopWindowClass.EXPANDED || windowClass == DesktopWindowClass.LARGE) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    renderAllowlistCard(Modifier.weight(1f))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        renderTagsCard(Modifier.fillMaxWidth())
                        renderMutedAppsCard(Modifier.fillMaxWidth())
                    }
                }
            } else {
                renderAllowlistCard(Modifier.fillMaxWidth())
                renderTagsCard(Modifier.fillMaxWidth())
                renderMutedAppsCard(Modifier.fillMaxWidth())
            }
        }
    }
}
