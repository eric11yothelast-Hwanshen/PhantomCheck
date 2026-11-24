package com.phantom.checker.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.io.File

/**
 * #478 桌面端更新對話框（版本號＋Release notes 雙語 Markdown 捲動瀏覽＋前往 Release 頁＋下載並執行安裝）。
 */
@Composable
fun DesktopUpdateDialog(
    update: DesktopUpdateOutcome.UpdateAvailable,
    accentColor: Color,
    containerColor: Color,
    animationsEnabled: Boolean,
    onDismiss: () -> Unit,
    onFeedback: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedFile by remember { mutableStateOf<File?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = containerColor,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(DesktopStrings.updateDialogTitle, style = MaterialTheme.typography.titleMedium, color = DesktopPalette.TextPrimary)
                Text(
                    text = "${update.currentVersion} → ${update.latestVersion} (${update.tagName} · ${update.publishedDate})",
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    DesktopMarkdownText(
                        markdown = update.releaseNotes.ifBlank { DesktopStrings.updateNoNotes },
                        accentColor = accentColor
                    )
                }

                if (downloading) {
                    HorizontalDivider(color = DesktopPalette.GlassBorder)
                    Text(
                        text = DesktopStrings.updateDownloading((downloadProgress * 100f).toInt()),
                        style = MaterialTheme.typography.labelSmall,
                        color = DesktopPalette.TextSecondary
                    )
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = accentColor,
                        trackColor = DesktopPalette.GlassBorder
                    )
                }

                downloadedFile?.let { f ->
                    HorizontalDivider(color = DesktopPalette.GlassBorder)
                    Text(
                        text = DesktopStrings.updateReadyToLaunch(f.name),
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopPalette.GlassSuccess
                    )
                }

                downloadError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = DesktopPalette.GlassError
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ready = downloadedFile
                if (ready != null) {
                    SpringButton(
                        onClick = {
                            DesktopUpdater.launchInstaller(ready)
                                .onSuccess {
                                    onFeedback(DesktopStrings.updateInstallerLaunched)
                                    onDismiss()
                                }
                                .onFailure { err ->
                                    downloadError = err.message ?: err.javaClass.simpleName
                                }
                        },
                        animationsEnabled = animationsEnabled,
                        containerColor = accentColor
                    ) {
                        Text(DesktopStrings.updateRunInstallerBtn)
                    }
                } else if (!update.installerUrl.isNullOrBlank() && !update.installerFileName.isNullOrBlank()) {
                    SpringButton(
                        enabled = !downloading,
                        onClick = {
                            downloading = true
                            downloadError = null
                            downloadProgress = 0f
                            scope.launch {
                                DesktopUpdater.downloadInstaller(
                                    url = update.installerUrl,
                                    fileName = update.installerFileName,
                                    onProgress = { p -> downloadProgress = p }
                                ).onSuccess { file ->
                                    downloading = false
                                    downloadedFile = file
                                }.onFailure { err ->
                                    downloading = false
                                    downloadError = err.message ?: err.javaClass.simpleName
                                }
                            }
                        },
                        animationsEnabled = animationsEnabled,
                        containerColor = accentColor
                    ) {
                        Text(DesktopStrings.updateDownloadInstallBtn)
                    }
                }
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = {
                        DesktopUpdater.openExternalUrl(update.htmlUrl)
                            .onFailure { onFeedback(it.message ?: DesktopStrings.openLinkFailed) }
                    }
                ) {
                    Text(DesktopStrings.updateOpenReleaseBtn)
                }
                TextButton(enabled = !downloading, onClick = onDismiss) {
                    Text(DesktopStrings.dismiss)
                }
            }
        }
    )
}
