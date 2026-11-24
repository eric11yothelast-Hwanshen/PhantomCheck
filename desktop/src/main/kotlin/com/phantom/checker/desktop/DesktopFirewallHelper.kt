package com.phantom.checker.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Base64
import java.util.concurrent.TimeUnit

data class NetworkProfileInfo(
    val interfaceAlias: String,
    val name: String,
    val category: String
)

data class FirewallCheckResult(
    val isWindows: Boolean,
    val port: Int,
    val portRulePresent: Boolean = false,
    val programRulePresent: Boolean = false,
    val profiles: List<NetworkProfileInfo> = emptyList(),
    val hasPublicProfile: Boolean = false,
    val rawDetail: String = "",
    val error: String? = null
)

/**
 * #487 Windows Defender Firewall 自我檢查與一鍵放行輔助工具：
 * - 查詢規則與網路分類（Private/Public/Domain）免管理員權限
 * - 一鍵允許僅在使用者主動點擊按鈕時透過 Start-Process -Verb RunAs 觸發 UAC
 * - 嚴格僅新增/更新 PhantomCheck 專屬兩條 Inbound Allow 規則（Port 規則為主 + App 執行檔雙保險），
 *   絕不停用防火牆、絕不修改任何其他規則、絕不於背景自行提權。
 */
object DesktopFirewallHelper {

    const val PORT_RULE_NAME = "PhantomCheck Desktop (Link)"
    const val PROGRAM_RULE_NAME = "PhantomCheck Desktop (App)"

    private const val QUERY_TIMEOUT_SEC = 12L
    private const val ELEVATE_TIMEOUT_SEC = 60L

    fun isWindowsOs(): Boolean =
        System.getProperty("os.name").orEmpty().contains("win", ignoreCase = true)

    suspend fun checkFirewallStatus(port: Int): FirewallCheckResult = withContext(Dispatchers.IO) {
        val safePort = port.coerceIn(1024, 65535)
        if (!isWindowsOs()) {
            return@withContext FirewallCheckResult(
                isWindows = false,
                port = safePort
            )
        }

        val psScript = """
            ${'$'}ErrorActionPreference = 'SilentlyContinue'
            ${'$'}portOk = 'false'
            ${'$'}portRules = Get-NetFirewallRule -DisplayName '$PORT_RULE_NAME'
            if (${'$'}portRules) {
                foreach (${'$'}r in ${'$'}portRules) {
                    if (${'$'}r.Enabled -eq 'True' -and ${'$'}r.Direction -eq 'Inbound' -and ${'$'}r.Action -eq 'Allow') {
                        ${'$'}pf = ${'$'}r | Get-NetFirewallPortFilter
                        if (${'$'}pf -and (${'$'}pf.LocalPort -contains '$safePort' -or ${'$'}pf.LocalPort -eq '$safePort' -or ${'$'}pf.LocalPort -eq 'Any')) {
                            ${'$'}portOk = 'true'
                        }
                    }
                }
            }
            ${'$'}progOk = 'false'
            ${'$'}progRules = Get-NetFirewallRule -DisplayName '$PROGRAM_RULE_NAME'
            if (${'$'}progRules) {
                foreach (${'$'}r in ${'$'}progRules) {
                    if (${'$'}r.Enabled -eq 'True' -and ${'$'}r.Direction -eq 'Inbound' -and ${'$'}r.Action -eq 'Allow') {
                        ${'$'}progOk = 'true'
                    }
                }
            }
            Write-Output "PORT_RULE=${'$'}portOk"
            Write-Output "PROG_RULE=${'$'}progOk"
            ${'$'}profs = Get-NetConnectionProfile
            foreach (${'$'}p in ${'$'}profs) {
                Write-Output ("PROFILE=" + ${'$'}p.InterfaceAlias + "|" + ${'$'}p.Name + "|" + ${'$'}p.NetworkCategory)
            }
        """.trimIndent()

        runCatching {
            val encoded = encodePowerShellCommand(psScript)
            val proc = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", encoded
            ).redirectErrorStream(true).start()

            val finished = proc.waitFor(QUERY_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                throw IllegalStateException("Firewall query timed out (${QUERY_TIMEOUT_SEC}s)")
            }
            val output = proc.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() }

            var portRule = false
            var progRule = false
            val profiles = mutableListOf<NetworkProfileInfo>()
            for (line in lines) {
                when {
                    line.startsWith("PORT_RULE=") -> portRule = line.removePrefix("PORT_RULE=").equals("true", ignoreCase = true)
                    line.startsWith("PROG_RULE=") -> progRule = line.removePrefix("PROG_RULE=").equals("true", ignoreCase = true)
                    line.startsWith("PROFILE=") -> {
                        val parts = line.removePrefix("PROFILE=").split("|")
                        if (parts.size >= 3) {
                            profiles.add(
                                NetworkProfileInfo(
                                    interfaceAlias = parts[0].trim(),
                                    name = parts[1].trim(),
                                    category = parts[2].trim()
                                )
                            )
                        }
                    }
                }
            }

            // Fallback to netsh if PowerShell NetSecurity cmdlets produced no marker lines
            if (lines.none { it.startsWith("PORT_RULE=") }) {
                portRule = checkRuleViaNetsh(PORT_RULE_NAME, safePort)
                progRule = checkRuleViaNetsh(PROGRAM_RULE_NAME, null)
            }

            val hasPublic = profiles.any { it.category.equals("Public", ignoreCase = true) }
            DesktopDiagnostics.info(
                "Firewall",
                "Check port=$safePort portRule=$portRule progRule=$progRule publicProfile=$hasPublic profiles=${profiles.size}"
            )
            FirewallCheckResult(
                isWindows = true,
                port = safePort,
                portRulePresent = portRule,
                programRulePresent = progRule,
                profiles = profiles,
                hasPublicProfile = hasPublic,
                rawDetail = lines.take(8).joinToString("; ")
            )
        }.getOrElse { err ->
            val msg = err.message ?: err.javaClass.simpleName
            DesktopDiagnostics.warn("Firewall", "Check failed: $msg")
            FirewallCheckResult(
                isWindows = true,
                port = safePort,
                error = msg
            )
        }
    }

    private fun checkRuleViaNetsh(ruleName: String, expectedPort: Int?): Boolean {
        return runCatching {
            val proc = ProcessBuilder(
                "netsh", "advfirewall", "firewall", "show", "rule", "name=$ruleName"
            ).redirectErrorStream(true).start()
            if (!proc.waitFor(8, TimeUnit.SECONDS)) {
                proc.destroyForcibly()
                return@runCatching false
            }
            val text = proc.inputStream.bufferedReader().use { it.readText() }
            if (proc.exitValue() != 0) return@runCatching false
            if (expectedPort != null && !text.contains(expectedPort.toString())) return@runCatching false
            true
        }.getOrDefault(false)
    }

    /**
     * 一鍵允許防火牆（僅於使用者主動點擊按鈕時呼叫，透過 Start-Process -Verb RunAs 彈出 UAC）：
     * 1. 新增 Inbound TCP Port 規則（Profile Any = Domain+Private+Public），跨版本升級仍有效
     * 2. 若本機 exe 路徑存在，同步新增對應 Program Inbound TCP 規則作為雙保險
     */
    suspend fun allowInWindowsFirewall(port: Int): Result<FirewallCheckResult> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isWindowsOs()) {
                throw UnsupportedOperationException("Windows Firewall rules are only supported on Windows")
            }
            val safePort = port.coerceIn(1024, 65535)
            val exePath = DesktopSettingsStore.resolveCurrentWindowsExecutable()
            val exeFile = File(exePath)
            val validExe = if (exeFile.exists() && exeFile.isFile && exePath.endsWith(".exe", ignoreCase = true)) {
                exeFile.absolutePath.replace("'", "''")
            } else {
                null
            }

            val elevatedScript = buildString {
                appendLine("\$ErrorActionPreference = 'Stop'")
                appendLine("Remove-NetFirewallRule -DisplayName '$PORT_RULE_NAME' -ErrorAction SilentlyContinue")
                appendLine("New-NetFirewallRule -DisplayName '$PORT_RULE_NAME' -Direction Inbound -Protocol TCP -LocalPort $safePort -Action Allow -Profile Any -Enabled True | Out-Null")
                if (validExe != null) {
                    appendLine("Remove-NetFirewallRule -DisplayName '$PROGRAM_RULE_NAME' -ErrorAction SilentlyContinue")
                    appendLine("New-NetFirewallRule -DisplayName '$PROGRAM_RULE_NAME' -Direction Inbound -Protocol TCP -Program '$validExe' -Action Allow -Profile Any -Enabled True | Out-Null")
                }
            }

            val innerEncoded = encodePowerShellCommand(elevatedScript)
            val launcherScript = """
                ${'$'}ErrorActionPreference = 'Stop'
                ${'$'}p = Start-Process -FilePath 'powershell.exe' -Verb RunAs -Wait -PassThru -WindowStyle Hidden -ArgumentList @('-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass', '-EncodedCommand', '$innerEncoded')
                exit ${'$'}p.ExitCode
            """.trimIndent()
            val outerEncoded = encodePowerShellCommand(launcherScript)

            DesktopDiagnostics.info("Firewall", "Requesting UAC elevation to allow inbound TCP port $safePort (exePresent=${validExe != null})")
            val proc = ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-EncodedCommand", outerEncoded
            ).redirectErrorStream(true).start()

            val finished = proc.waitFor(ELEVATE_TIMEOUT_SEC, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                throw IllegalStateException("UAC elevation timed out (${ELEVATE_TIMEOUT_SEC}s)")
            }
            val out = proc.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
            val exitCode = proc.exitValue()
            if (exitCode != 0) {
                val detail = out.take(200).ifBlank { "exit=$exitCode" }
                DesktopDiagnostics.warn("Firewall", "Elevation or rule creation failed: $detail")
                throw IllegalStateException(detail)
            }

            val verified = checkFirewallStatus(safePort)
            DesktopDiagnostics.info(
                "Firewall",
                "Allow rule applied: portRule=${verified.portRulePresent}, progRule=${verified.programRulePresent}, port=$safePort"
            )
            verified
        }.onFailure { err ->
            DesktopDiagnostics.error("Firewall", "allowInWindowsFirewall failed: ${err.message ?: err.javaClass.simpleName}")
        }
    }

    private fun encodePowerShellCommand(script: String): String {
        val bytes = script.toByteArray(Charsets.UTF_16LE)
        return Base64.getEncoder().encodeToString(bytes)
    }
}
