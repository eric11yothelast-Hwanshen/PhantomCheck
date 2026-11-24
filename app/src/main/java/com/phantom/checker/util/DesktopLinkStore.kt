package com.phantom.checker.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.phantom.checker.shared.LinkProtocol
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class PairedDesktop(
    val instanceId: String = "",
    val name: String = "",
    val hmacSecret: String = "",
    val pairedAt: Long = 0L,
    val lastSeenAt: Long = 0L,
    val host: String = "",
    val port: Int = LinkProtocol.DEFAULT_PORT,
    val fingerprint: String = ""
)

data class UnknownAttempt(
    val timestamp: Long = 0L,
    val remoteIp: String = "",
    val reason: String = "",
    val instanceId: String = ""
)

data class CommandAuditEntry(
    val timestamp: Long = 0L,
    val instanceId: String = "",
    val command: String = "",
    val ok: Boolean = true,
    val detail: String = ""
)

data class DiagnosticLogEntry(
    val timestamp: Long = 0L,
    val stage: String = "",
    val code: String = "",
    val detail: String = ""
)

/**
 * #476 手機端桌面連線安全儲存層（EncryptedSharedPreferences AES256_SIV / AES256_GCM）：
 * - 儲存「已配對電腦」清單（IP/埠/憑證 SHA-256 指紋/HMAC secret）、本機 phoneInstanceId、
 *   最後連線電腦 ID、未知事件與指令審計紀錄（上限 200 筆）、連線階段診斷日誌（上限 60 筆）。
 * - 絕不將訊息內容、配對碼或 HMAC 寫入診斷與審計紀錄。
 */
object DesktopLinkStore {

    private const val PREF_FILE = "phantom_desktop_link_secrets"
    private const val KEY_ENABLED = "desktop_link_enabled"
    private const val KEY_PHONE_INSTANCE_ID = "desktop_link_phone_instance_id"
    private const val KEY_LAST_DESKTOP_ID = "desktop_link_last_desktop_id"
    private const val KEY_PAIRED_DEVICES = "desktop_link_paired_devices"
    private const val KEY_UNKNOWN_ATTEMPTS = "desktop_link_unknown_attempts"
    private const val KEY_AUDIT_LOGS = "desktop_link_audit_logs"
    private const val KEY_DIAGNOSTICS = "desktop_link_diagnostics"
    private const val MAX_UNKNOWN_ATTEMPTS = 40
    private const val MAX_AUDIT_LOGS = 200
    private const val MAX_DIAGNOSTICS = 60

    private val gson = Gson()
    private val secureRandom = SecureRandom()

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun securePrefs(ctx: Context): SharedPreferences? {
        cachedPrefs?.let { return it }
        return synchronized(this) {
            cachedPrefs ?: try {
                val appCtx = ctx.applicationContext
                val masterKey = MasterKey.Builder(appCtx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    appCtx,
                    PREF_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                ).also { cachedPrefs = it }
            } catch (_: Throwable) {
                null
            }
        }
    }

    fun isLinkEnabled(ctx: Context): Boolean =
        runCatching { securePrefs(ctx)?.getBoolean(KEY_ENABLED, true) ?: true }.getOrDefault(true)

    fun setLinkEnabled(ctx: Context, enabled: Boolean) {
        runCatching { securePrefs(ctx)?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply() }
    }

    fun getOrCreatePhoneInstanceId(ctx: Context): String {
        return runCatching {
            val prefs = securePrefs(ctx) ?: return@runCatching UUID.randomUUID().toString()
            val existing = prefs.getString(KEY_PHONE_INSTANCE_ID, null)
            if (!existing.isNullOrBlank()) return@runCatching existing
            val created = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_PHONE_INSTANCE_ID, created).apply()
            created
        }.getOrElse { UUID.randomUUID().toString() }
    }

    fun getLastConnectedDesktopId(ctx: Context): String =
        runCatching { securePrefs(ctx)?.getString(KEY_LAST_DESKTOP_ID, null).orEmpty() }.getOrDefault("")

    fun setLastConnectedDesktopId(ctx: Context, instanceId: String) {
        runCatching { securePrefs(ctx)?.edit()?.putString(KEY_LAST_DESKTOP_ID, instanceId)?.apply() }
    }

    fun getPairedDevices(ctx: Context): List<PairedDesktop> {
        return runCatching {
            val raw = securePrefs(ctx)?.getString(KEY_PAIRED_DEVICES, null).orEmpty()
            if (raw.isBlank()) return@runCatching emptyList()
            val type = object : TypeToken<List<PairedDesktop?>>() {}.type
            gson.fromJson<List<PairedDesktop?>>(raw, type)
                .orEmpty()
                .filterNotNull()
                .filter { !it.instanceId.isNullOrBlank() }
        }.getOrDefault(emptyList())
    }

    fun findPairedDevice(ctx: Context, instanceId: String): PairedDesktop? =
        getPairedDevices(ctx).firstOrNull { it.instanceId == instanceId }

    @Synchronized
    fun upsertPairedDevice(ctx: Context, device: PairedDesktop) {
        runCatching {
            val list = getPairedDevices(ctx).toMutableList()
            val idx = list.indexOfFirst { it.instanceId == device.instanceId }
            if (idx >= 0) {
                list[idx] = device
            } else {
                list.add(0, device)
            }
            securePrefs(ctx)?.edit()?.putString(KEY_PAIRED_DEVICES, gson.toJson(list))?.apply()
        }
    }

    @Synchronized
    fun updateLastSeen(ctx: Context, instanceId: String, nowMs: Long = System.currentTimeMillis()) {
        runCatching {
            val list = getPairedDevices(ctx).map {
                if (it.instanceId == instanceId) it.copy(lastSeenAt = nowMs) else it
            }
            securePrefs(ctx)?.edit()?.putString(KEY_PAIRED_DEVICES, gson.toJson(list))?.apply()
        }
    }

    @Synchronized
    fun removePairedDevice(ctx: Context, instanceId: String) {
        runCatching {
            val list = getPairedDevices(ctx).filterNot { it.instanceId == instanceId }
            val filteredAudit = getAuditLogs(ctx).filterNot { it.instanceId == instanceId }
            val editor = securePrefs(ctx)?.edit()
                ?.putString(KEY_PAIRED_DEVICES, gson.toJson(list))
                ?.putString(KEY_AUDIT_LOGS, gson.toJson(filteredAudit))
            if (getLastConnectedDesktopId(ctx) == instanceId) {
                editor?.remove(KEY_LAST_DESKTOP_ID)
            }
            editor?.apply()
        }
    }

    fun getAuditLogs(ctx: Context): List<CommandAuditEntry> {
        return runCatching {
            val raw = securePrefs(ctx)?.getString(KEY_AUDIT_LOGS, null).orEmpty()
            if (raw.isBlank()) return@runCatching emptyList()
            val type = object : TypeToken<List<CommandAuditEntry?>>() {}.type
            gson.fromJson<List<CommandAuditEntry?>>(raw, type).orEmpty().filterNotNull()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun recordAuditLog(
        ctx: Context,
        instanceId: String,
        command: String,
        ok: Boolean,
        detail: String = "",
        nowMs: Long = System.currentTimeMillis()
    ) {
        runCatching {
            val list = getAuditLogs(ctx).toMutableList()
            list.add(0, CommandAuditEntry(nowMs, instanceId, command, ok, detail.take(80)))
            val trimmed = list.take(MAX_AUDIT_LOGS)
            securePrefs(ctx)?.edit()?.putString(KEY_AUDIT_LOGS, gson.toJson(trimmed))?.apply()
        }
    }

    @Synchronized
    fun clearAuditLogs(ctx: Context) {
        runCatching { securePrefs(ctx)?.edit()?.remove(KEY_AUDIT_LOGS)?.apply() }
    }

    fun getUnknownAttempts(ctx: Context): List<UnknownAttempt> {
        return runCatching {
            val raw = securePrefs(ctx)?.getString(KEY_UNKNOWN_ATTEMPTS, null).orEmpty()
            if (raw.isBlank()) return@runCatching emptyList()
            val type = object : TypeToken<List<UnknownAttempt?>>() {}.type
            gson.fromJson<List<UnknownAttempt?>>(raw, type).orEmpty().filterNotNull()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun recordUnknownAttempt(
        ctx: Context,
        remoteIp: String,
        reason: String,
        instanceId: String = "",
        nowMs: Long = System.currentTimeMillis()
    ) {
        runCatching {
            val list = getUnknownAttempts(ctx).toMutableList()
            list.add(0, UnknownAttempt(nowMs, remoteIp, reason, instanceId))
            val trimmed = list.take(MAX_UNKNOWN_ATTEMPTS)
            securePrefs(ctx)?.edit()?.putString(KEY_UNKNOWN_ATTEMPTS, gson.toJson(trimmed))?.apply()
        }
    }

    @Synchronized
    fun clearUnknownAttempts(ctx: Context) {
        runCatching { securePrefs(ctx)?.edit()?.remove(KEY_UNKNOWN_ATTEMPTS)?.apply() }
    }

    fun getDiagnostics(ctx: Context): List<DiagnosticLogEntry> {
        return runCatching {
            val raw = securePrefs(ctx)?.getString(KEY_DIAGNOSTICS, null).orEmpty()
            if (raw.isBlank()) return@runCatching emptyList()
            val type = object : TypeToken<List<DiagnosticLogEntry?>>() {}.type
            gson.fromJson<List<DiagnosticLogEntry?>>(raw, type).orEmpty().filterNotNull()
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun recordDiagnostic(
        ctx: Context,
        stage: String,
        code: String,
        detail: String = "",
        nowMs: Long = System.currentTimeMillis()
    ) {
        runCatching {
            val list = getDiagnostics(ctx).toMutableList()
            list.add(0, DiagnosticLogEntry(nowMs, stage.take(24), code.take(36), detail.take(100)))
            val trimmed = list.take(MAX_DIAGNOSTICS)
            securePrefs(ctx)?.edit()?.putString(KEY_DIAGNOSTICS, gson.toJson(trimmed))?.apply()
        }
    }

    @Synchronized
    fun clearDiagnostics(ctx: Context) {
        runCatching { securePrefs(ctx)?.edit()?.remove(KEY_DIAGNOSTICS)?.apply() }
    }

    fun randomHex(numBytes: Int = 32): String {
        val bytes = ByteArray(numBytes)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun computeHmacSha256Hex(secretHex: String, message: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val keyBytes = secretHex.toByteArray(Charsets.UTF_8)
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        val digest = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
