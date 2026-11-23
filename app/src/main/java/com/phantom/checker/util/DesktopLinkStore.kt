package com.phantom.checker.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.phantom.checker.shared.LinkProtocol
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

data class PairedDesktop(
    val instanceId: String = "",
    val name: String = "",
    val hmacSecret: String = "",
    val pairedAt: Long = 0L,
    val lastSeenAt: Long = 0L
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

enum class PairCodeCheckResult {
    VALID,
    EXPIRED,
    INVALID
}

/**
 * #449 / #450 桌面端連線安全儲存層（EncryptedSharedPreferences AES256_SIV / AES256_GCM）：
 * - 儲存配對桌機 secret、TLS KeyStore 密碼、開關與連接埠、未知連線嘗試紀錄與 200 筆指令審計紀錄。
 * - 6 位數配對碼僅存於記憶體（TTL 5 分鐘、單次有效）。
 */
object DesktopLinkStore {

    private const val PREF_FILE = "phantom_desktop_link_secrets"
    private const val KEY_ENABLED = "desktop_link_enabled"
    private const val KEY_PORT = "desktop_link_port"
    private const val KEY_KS_PASSWORD = "desktop_link_ks_password"
    private const val KEY_PAIRED_DEVICES = "desktop_link_paired_devices"
    private const val KEY_UNKNOWN_ATTEMPTS = "desktop_link_unknown_attempts"
    private const val KEY_AUDIT_LOGS = "desktop_link_audit_logs"
    private const val MAX_UNKNOWN_ATTEMPTS = 40
    private const val MAX_AUDIT_LOGS = 200

    private val gson = Gson()
    private val secureRandom = SecureRandom()

    private data class ActivePairCode(val code: String, val expiresAtMs: Long)
    private val activePairCode = AtomicReference<ActivePairCode?>(null)

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
            } catch (_: Exception) {
                null
            }
        }
    }

    fun isLinkEnabled(ctx: Context): Boolean =
        securePrefs(ctx)?.getBoolean(KEY_ENABLED, false) ?: false

    fun setLinkEnabled(ctx: Context, enabled: Boolean) {
        securePrefs(ctx)?.edit()?.putBoolean(KEY_ENABLED, enabled)?.apply()
    }

    fun getServerPort(ctx: Context): Int =
        (securePrefs(ctx)?.getInt(KEY_PORT, LinkProtocol.DEFAULT_PORT) ?: LinkProtocol.DEFAULT_PORT)
            .coerceIn(1024, 65535)

    fun setServerPort(ctx: Context, port: Int) {
        securePrefs(ctx)?.edit()?.putInt(KEY_PORT, port.coerceIn(1024, 65535))?.apply()
    }

    fun getOrCreateKeystorePassword(ctx: Context): String {
        val prefs = securePrefs(ctx) ?: return randomHex(24)
        val existing = prefs.getString(KEY_KS_PASSWORD, null)
        if (!existing.isNullOrBlank()) return existing
        val created = randomHex(24)
        prefs.edit().putString(KEY_KS_PASSWORD, created).apply()
        return created
    }

    fun getPairedDevices(ctx: Context): List<PairedDesktop> {
        val raw = securePrefs(ctx)?.getString(KEY_PAIRED_DEVICES, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<PairedDesktop>>() {}.type
            gson.fromJson<List<PairedDesktop>>(raw, type).orEmpty()
        }.getOrDefault(emptyList())
    }

    fun findPairedDevice(ctx: Context, instanceId: String): PairedDesktop? =
        getPairedDevices(ctx).firstOrNull { it.instanceId == instanceId }

    @Synchronized
    fun upsertPairedDevice(ctx: Context, device: PairedDesktop) {
        val list = getPairedDevices(ctx).toMutableList()
        val idx = list.indexOfFirst { it.instanceId == device.instanceId }
        if (idx >= 0) {
            list[idx] = device
        } else {
            list.add(0, device)
        }
        securePrefs(ctx)?.edit()?.putString(KEY_PAIRED_DEVICES, gson.toJson(list))?.apply()
    }

    @Synchronized
    fun updateLastSeen(ctx: Context, instanceId: String, nowMs: Long = System.currentTimeMillis()) {
        val list = getPairedDevices(ctx).map {
            if (it.instanceId == instanceId) it.copy(lastSeenAt = nowMs) else it
        }
        securePrefs(ctx)?.edit()?.putString(KEY_PAIRED_DEVICES, gson.toJson(list))?.apply()
    }

    @Synchronized
    fun removePairedDevice(ctx: Context, instanceId: String) {
        val list = getPairedDevices(ctx).filterNot { it.instanceId == instanceId }
        val filteredAudit = getAuditLogs(ctx).filterNot { it.instanceId == instanceId }
        securePrefs(ctx)?.edit()
            ?.putString(KEY_PAIRED_DEVICES, gson.toJson(list))
            ?.putString(KEY_AUDIT_LOGS, gson.toJson(filteredAudit))
            ?.apply()
    }

    fun getAuditLogs(ctx: Context): List<CommandAuditEntry> {
        val raw = securePrefs(ctx)?.getString(KEY_AUDIT_LOGS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<CommandAuditEntry>>() {}.type
            gson.fromJson<List<CommandAuditEntry>>(raw, type).orEmpty()
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
        val list = getAuditLogs(ctx).toMutableList()
        list.add(0, CommandAuditEntry(nowMs, instanceId, command, ok, detail.take(80)))
        val trimmed = list.take(MAX_AUDIT_LOGS)
        securePrefs(ctx)?.edit()?.putString(KEY_AUDIT_LOGS, gson.toJson(trimmed))?.apply()
    }

    @Synchronized
    fun clearAuditLogs(ctx: Context) {
        securePrefs(ctx)?.edit()?.remove(KEY_AUDIT_LOGS)?.apply()
    }

    fun getUnknownAttempts(ctx: Context): List<UnknownAttempt> {
        val raw = securePrefs(ctx)?.getString(KEY_UNKNOWN_ATTEMPTS, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<UnknownAttempt>>() {}.type
            gson.fromJson<List<UnknownAttempt>>(raw, type).orEmpty()
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
        val list = getUnknownAttempts(ctx).toMutableList()
        list.add(0, UnknownAttempt(nowMs, remoteIp, reason, instanceId))
        val trimmed = list.take(MAX_UNKNOWN_ATTEMPTS)
        securePrefs(ctx)?.edit()?.putString(KEY_UNKNOWN_ATTEMPTS, gson.toJson(trimmed))?.apply()
    }

    @Synchronized
    fun clearUnknownAttempts(ctx: Context) {
        securePrefs(ctx)?.edit()?.remove(KEY_UNKNOWN_ATTEMPTS)?.apply()
    }

    fun generatePairCode(nowMs: Long = System.currentTimeMillis()): Pair<String, Long> {
        val num = secureRandom.nextInt(1_000_000)
        val code = "%06d".format(num)
        val expiresAt = nowMs + LinkProtocol.PAIR_CODE_TTL_MS
        activePairCode.set(ActivePairCode(code, expiresAt))
        return code to expiresAt
    }

    fun getCurrentPairCode(nowMs: Long = System.currentTimeMillis()): Pair<String, Long>? {
        val current = activePairCode.get() ?: return null
        if (nowMs > current.expiresAtMs) return null
        return current.code to current.expiresAtMs
    }

    fun getOrGeneratePairCode(nowMs: Long = System.currentTimeMillis()): Pair<String, Long> =
        getCurrentPairCode(nowMs) ?: generatePairCode(nowMs)

    fun consumePairCode(inputCode: String, nowMs: Long = System.currentTimeMillis()): PairCodeCheckResult {
        val current = activePairCode.get() ?: return PairCodeCheckResult.INVALID
        if (nowMs > current.expiresAtMs) {
            activePairCode.compareAndSet(current, null)
            return PairCodeCheckResult.EXPIRED
        }
        val cleanInput = inputCode.trim()
        if (cleanInput != current.code) {
            return PairCodeCheckResult.INVALID
        }
        // 單次有效：成功即作廢
        activePairCode.compareAndSet(current, null)
        return PairCodeCheckResult.VALID
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
