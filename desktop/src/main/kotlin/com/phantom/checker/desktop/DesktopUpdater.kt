package com.phantom.checker.desktop

import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

data class DesktopReleaseEntry(
    val tagName: String,
    val publishedDate: String,
    val htmlUrl: String,
    val body: String,
    val desktopAssetVersion: String?,
    val installerUrl: String?,
    val installerFileName: String?,
    val installerSha256Url: String? = null
)

sealed interface DesktopUpdateOutcome {
    data class UpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val tagName: String,
        val publishedDate: String,
        val htmlUrl: String,
        val releaseNotes: String,
        val installerUrl: String?,
        val installerFileName: String?,
        val installerSha256Url: String? = null
    ) : DesktopUpdateOutcome

    data class UpToDate(
        val currentVersion: String,
        val latestTag: String
    ) : DesktopUpdateOutcome

    data class SilentSkipped(val reason: String) : DesktopUpdateOutcome

    data class Error(val message: String) : DesktopUpdateOutcome
}

/**
 * #478 桌面端更新檢查與安裝輔助引擎（比照手機 AppUpdateHelper 慣例）：
 * - 進程級單次旗標 startupCheckDone（僅啟動一次，絕無背景輪詢；手動檢查共用同一引擎）
 * - 匿名呼叫 GitHub Releases API（嚴禁內嵌 token）；離線 / 403 / 429 於啟動檢查靜默、僅記診斷日誌
 * - 掃描 assets 之 PhantomCheckDesktop-<semver>-setup.exe / .msi 與 CURRENT_DESKTOP_VERSION 比對 semver
 * - 原生通知每日同版本去重（key = version|yyyy-MM-dd）並尊重勿擾時段
 */
object DesktopUpdater {

    const val REPO_URL = "https://github.com/eric11yothelast-Hwanshen/PhantomCheck"
    const val RELEASES_URL = "$REPO_URL/releases"
    const val LICENSE_URL = "$REPO_URL/blob/main/LICENSE"
    const val PRIVACY_URL = "$REPO_URL/blob/main/PRIVACY.md"
    const val DISCLAIMER_URL = "$REPO_URL/blob/main/DISCLAIMER.md"
    private const val RELEASES_API = "https://api.github.com/repos/eric11yothelast-Hwanshen/PhantomCheck/releases?per_page=10"

    private val desktopAssetRegex = Regex(
        """PhantomCheckDesktop-(\d+\.\d+\.\d+)(?:-setup)?\.(exe|msi)""",
        RegexOption.IGNORE_CASE
    )

    private val startupCheckDone = AtomicBoolean(false)

    suspend fun checkForUpdate(isAutoStartup: Boolean): DesktopUpdateOutcome {
        if (isAutoStartup && !startupCheckDone.compareAndSet(false, true)) {
            return DesktopUpdateOutcome.SilentSkipped("already_checked_this_process")
        }
        return withContext(Dispatchers.IO) {
            fetchReleasesInternal(isAutoStartup = isAutoStartup).fold(
                onSuccess = { releases ->
                    val currentVer = DesktopSettingsStore.CURRENT_DESKTOP_VERSION
                    val candidate = releases.firstOrNull { !it.desktopAssetVersion.isNullOrBlank() }
                    if (candidate == null || candidate.desktopAssetVersion.isNullOrBlank()) {
                        val latestTag = releases.firstOrNull()?.tagName.orEmpty()
                        DesktopDiagnostics.info("Updater", "No desktop installer asset found in recent releases (latestTag=$latestTag)")
                        return@fold DesktopUpdateOutcome.UpToDate(currentVer, latestTag)
                    }
                    val remoteVer = candidate.desktopAssetVersion
                    if (compareSemVer(remoteVer, currentVer) > 0) {
                        DesktopDiagnostics.info("Updater", "Update available: desktop $currentVer -> $remoteVer (${candidate.tagName})")
                        DesktopUpdateOutcome.UpdateAvailable(
                            currentVersion = currentVer,
                            latestVersion = remoteVer,
                            tagName = candidate.tagName,
                            publishedDate = candidate.publishedDate,
                            htmlUrl = candidate.htmlUrl.ifBlank { RELEASES_URL },
                            releaseNotes = candidate.body,
                            installerUrl = candidate.installerUrl,
                            installerFileName = candidate.installerFileName,
                            installerSha256Url = candidate.installerSha256Url
                        )
                    } else {
                        DesktopDiagnostics.info("Updater", "Desktop is up to date ($currentVer >= $remoteVer)")
                        DesktopUpdateOutcome.UpToDate(currentVer, candidate.tagName)
                    }
                },
                onFailure = { err ->
                    val msg = err.message ?: err.javaClass.simpleName
                    if (isAutoStartup) {
                        DesktopDiagnostics.info("Updater", "Startup check silently skipped: $msg")
                        DesktopUpdateOutcome.SilentSkipped(msg)
                    } else {
                        DesktopDiagnostics.warn("Updater", "Manual update check failed: $msg")
                        DesktopUpdateOutcome.Error(msg)
                    }
                }
            )
        }
    }

    suspend fun fetchRecentReleases(): Result<List<DesktopReleaseEntry>> = withContext(Dispatchers.IO) {
        fetchReleasesInternal(isAutoStartup = false)
    }

    private fun fetchReleasesInternal(isAutoStartup: Boolean): Result<List<DesktopReleaseEntry>> = runCatching {
        val conn = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "PhantomCheckDesktop/${DesktopSettingsStore.CURRENT_DESKTOP_VERSION}")
        }
        val code = conn.responseCode
        if (code == 403 || code == 429) {
            DesktopDiagnostics.warn("Updater", "GitHub API rate limited (HTTP $code)")
            throw IllegalStateException(if (isAutoStartup) "rate_limited_$code" else "GitHub API rate limit (HTTP $code)")
        }
        if (code != 200) {
            throw IllegalStateException("HTTP $code")
        }
        val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = JsonParser.parseString(raw).asJsonArray
        val list = mutableListOf<DesktopReleaseEntry>()
        for (elem in arr) {
            val obj = elem.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            val draft = obj.get("draft")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false
            if (draft) continue
            val tag = obj.get("tag_name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim()
            val htmlUrl = obj.get("html_url")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim()
            val publishedRaw = obj.get("published_at")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim()
            val publishedDate = if (publishedRaw.length >= 10) publishedRaw.take(10) else publishedRaw
            val body = obj.get("body")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty().trim()

            var bestVer: String? = null
            var bestUrl: String? = null
            var bestName: String? = null
            val allAssets = mutableMapOf<String, String>()
            val assets = obj.getAsJsonArray("assets")
            if (assets != null) {
                for (aElem in assets) {
                    val aObj = aElem.takeIf { it.isJsonObject }?.asJsonObject ?: continue
                    val name = aObj.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    val dlUrl = aObj.get("browser_download_url")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                    if (name.isNotBlank() && dlUrl.isNotBlank()) {
                        allAssets[name.lowercase()] = dlUrl
                    }
                    val m = desktopAssetRegex.find(name) ?: continue
                    val ver = m.groupValues[1]
                    val ext = m.groupValues[2].lowercase()
                    // Prefer .exe (Inno Setup) over .msi if both exist for the same release
                    if (bestVer == null || (ext == "exe" && bestName?.endsWith(".exe", ignoreCase = true) != true)) {
                        bestVer = ver
                        bestUrl = dlUrl.ifBlank { null }
                        bestName = name
                    }
                }
            }
            val shaUrl = bestName?.let { n -> allAssets["${n.lowercase()}.sha256"] }
            list.add(
                DesktopReleaseEntry(
                    tagName = tag,
                    publishedDate = publishedDate,
                    htmlUrl = htmlUrl.ifBlank { RELEASES_URL },
                    body = body,
                    desktopAssetVersion = bestVer,
                    installerUrl = bestUrl,
                    installerFileName = bestName,
                    installerSha256Url = shaUrl
                )
            )
        }
        list
    }

    fun shouldSendDailyUpdateNotification(version: String): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val key = "$version|$today"
        val file = File(DesktopPaths.configDir, "update_notify_dedup.txt")
        val existing = runCatching { if (file.exists()) file.readText(Charsets.UTF_8).trim() else "" }.getOrDefault("")
        if (existing == key) return false
        runCatching {
            if (!file.parentFile.exists()) file.parentFile.mkdirs()
            file.writeText(key, Charsets.UTF_8)
        }
        return true
    }

    suspend fun downloadInstaller(
        url: String,
        fileName: String,
        onProgress: (Float) -> Unit,
        sha256Url: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val safeName = fileName.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val outFile = File(System.getProperty("java.io.tmpdir"), safeName)
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 12_000
                readTimeout = 30_000
                setRequestProperty("User-Agent", "PhantomCheckDesktop/${DesktopSettingsStore.CURRENT_DESKTOP_VERSION}")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Download failed: HTTP $code")
            }
            val total = conn.contentLengthLong.coerceAtLeast(-1L)
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        readTotal += n
                        if (total > 0L) {
                            onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            if (!outFile.exists() || outFile.length() <= 0L) {
                throw IllegalStateException("Downloaded installer file is empty")
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (!sha256Url.isNullOrBlank()) {
                val expectedSha = fetchExpectedSha256(sha256Url)
                if (expectedSha != null && !actualSha256.equals(expectedSha, ignoreCase = true)) {
                    runCatching { outFile.delete() }
                    DesktopDiagnostics.error("Updater", "SHA-256 mismatch! expected=${expectedSha.take(16)}…, actual=${actualSha256.take(16)}…")
                    throw IllegalStateException("Installer SHA-256 checksum mismatch (file deleted for safety)")
                }
                DesktopDiagnostics.info("Updater", "Verified installer SHA-256=${actualSha256.take(16)}… (${outFile.length()} bytes)")
            } else {
                // Honest limitation (#490 E3): GitHub Release does not publish a .sha256 sidecar asset yet; log local digest.
                DesktopDiagnostics.info("Updater", "Downloaded installer (${outFile.length()} bytes, local SHA-256=${actualSha256.take(16)}…; no remote .sha256 sidecar on release)")
            }
            outFile
        }.onFailure { err ->
            DesktopDiagnostics.error("Updater", "Installer download failed: ${err.message ?: err.javaClass.simpleName}")
        }
    }

    private fun fetchExpectedSha256(shaUrl: String): String? = runCatching {
        val conn = (URL(shaUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("User-Agent", "PhantomCheckDesktop/${DesktopSettingsStore.CURRENT_DESKTOP_VERSION}")
        }
        if (conn.responseCode !in 200..299) return@runCatching null
        val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
        Regex("[0-9a-fA-F]{64}").find(text)?.value?.lowercase()
    }.getOrNull()

    fun launchInstaller(file: File): Result<Unit> = runCatching {
        if (!file.exists()) throw IllegalStateException("Installer file missing: ${file.absolutePath}")
        val os = System.getProperty("os.name").orEmpty().lowercase()
        if (os.contains("win")) {
            if (file.name.endsWith(".msi", ignoreCase = true)) {
                ProcessBuilder("msiexec", "/i", file.absolutePath).start()
            } else {
                ProcessBuilder(file.absolutePath).start()
            }
        } else if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(file)
        } else {
            throw UnsupportedOperationException("Cannot launch installer on this OS")
        }
        DesktopDiagnostics.info("Updater", "Launched installer: ${file.name}")
    }

    fun openExternalUrl(url: String): Result<Unit> = runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url))
        } else {
            throw UnsupportedOperationException("Browser launch not supported")
        }
    }

    fun compareSemVer(remote: String, local: String): Int {
        val rParts = remote.trim().removePrefix("v").removePrefix("V").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val lParts = local.trim().removePrefix("v").removePrefix("V").substringBefore("-").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(rParts.size, lParts.size, 3)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r != l) return r.compareTo(l)
        }
        return 0
    }
}
