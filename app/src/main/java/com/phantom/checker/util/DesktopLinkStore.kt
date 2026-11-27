package com.phantom.checker.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.phantom.checker.shared.LinkProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.AEADBadTagException
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
 * - R1 (#545)：雙重檢查單例＋記憶體快取層＋StateFlow 暴露（供 R5/R10 零同步解密讀取）＋AEADBadTagException 記錄與清除重建。
 */
object DesktopLinkStore {

    private const val TAG = "DesktopLinkStore"
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
    private val prefsLock = Any()

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    @Volatile
    var lastKeystoreRecoveryAtMs: Long = 0L
        private set

    @Volatile
    var keystoreRecoveryCount: Int = 0
        private set

    // 記憶體快取層（寫入時同步更新，避免主執行緒與通知建構同步解密＋Gson 反序列化）
    @Volatile
    private var cachedEnabled: Boolean? = null
    @Volatile
    private var cachedPhoneInstanceId: String? = null
    @Volatile
    private var cachedLastDesktopId: String? = null
    @Volatile
    private var cachedPairedDevices: List<PairedDesktop>? = null
    @Volatile
    private var cachedAuditLogs: List<CommandAuditEntry>? = null
    @Volatile
    private var cachedUnknownAttempts: List<UnknownAttempt>? = null
    @Volatile
    private var cachedDiagnostics: List<DiagnosticLogEntry>? = null

    // R1 (#545) / R5 / R10: 供背景服務（StatsForegroundService）與 Compose UI（LinkHubContent）非阻塞觀察
    private val _pairedDevicesFlow = MutableStateFlow<List<PairedDesktop>>(emptyList())
    val pairedDevicesFlow: StateFlow<List<PairedDesktop>> = _pairedDevicesFlow.asStateFlow()

    private val _linkEnabledFlow = MutableStateFlow(true)
    val linkEnabledFlow: StateFlow<Boolean> = _linkEnabledFlow.asStateFlow()

    private val _lastDesktopIdFlow = MutableStateFlow("")
    val lastDesktopIdFlow: StateFlow<String> = _lastDesktopIdFlow.asStateFlow()

    private fun isKeystoreCorruption(err: Throwable): Boolean {
        var cur: Throwable? = err
        var depth = 0
        while (cur != null && depth < 12) {
            if (cur is AEADBadTagException ||
                cur is GeneralSecurityException ||
                cur.javaClass.name.contains("AEADBadTagException") ||
                cur.javaClass.name.contains("InvalidProtocolBufferException")
            ) {
                return true
            }
            cur = cur.cause
            depth++
        }
        return false
    }

    private fun clearMemoryCaches() {
        cachedEnabled = null
        cachedPhoneInstanceId = null
        cachedLastDesktopId = null
        cachedPairedDevices = null
        cachedAuditLogs = null
        cachedUnknownAttempts = null
        cachedDiagnostics = null
        _pairedDevicesFlow.value = emptyList()
        _linkEnabledFlow.value = true
        _lastDesktopIdFlow.value = ""
    }

    @Suppress("DEPRECATION")
    private fun createEncryptedPrefs(appCtx: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(appCtx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appCtx,
            PREF_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /** 清除損毀之 EncryptedSharedPreferences 檔案並重建；同時記錄復原次數與時間戳。 */
    fun resetCorruptedStore(ctx: Context, reason: Throwable? = null): SharedPreferences? {
        val appCtx = ctx.applicationContext ?: ctx
        return synchronized(prefsLock) {
            Log.w(
                TAG,
                "EncryptedSharedPreferences corrupted (${reason?.javaClass?.simpleName ?: "manual"}), clearing & recreating $PREF_FILE",
                reason
            )
            cachedPrefs = null
            clearMemoryCaches()
            runCatching { appCtx.deleteSharedPreferences(PREF_FILE) }
            runCatching {
                appCtx.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit().clear().commit()
            }
            keystoreRecoveryCount += 1
            lastKeystoreRecoveryAtMs = System.currentTimeMillis()
            try {
                createEncryptedPrefs(appCtx).also { cachedPrefs = it }
            } catch (recreateErr: Throwable) {
                Log.w(TAG, "EncryptedSharedPreferences recreate failed: ${recreateErr.javaClass.simpleName}", recreateErr)
                null
            }
        }
    }

    private fun securePrefs(ctx: Context): SharedPreferences? {
        cachedPrefs?.let { return it }
        return synchronized(prefsLock) {
            cachedPrefs ?: run {
                val appCtx = ctx.applicationContext ?: ctx
                try {
                    createEncryptedPrefs(appCtx).also { cachedPrefs = it }
                } catch (err: Throwable) {
                    if (isKeystoreCorruption(err)) {
                        resetCorruptedStore(appCtx, err)
                    } else {
                        Log.w(TAG, "EncryptedSharedPreferences init failed: ${err.javaClass.simpleName}")
                        null
                    }
                }
            }
        }
    }

    private fun readStringSafe(ctx: Context, key: String, default: String? = null): String? {
        val prefs = securePrefs(ctx) ?: return default
        return try {
            prefs.getString(key, default)
        } catch (err: Throwable) {
            if (isKeystoreCorruption(err)) {
                resetCorruptedStore(ctx, err)
            } else {
                Log.w(TAG, "EncryptedSharedPreferences read failed ($key): ${err.javaClass.simpleName}")
            }
            default
        }
    }

    private fun readBooleanSafe(ctx: Context, key: String, default: Boolean): Boolean {
        val prefs = securePrefs(ctx) ?: return default
        return try {
            prefs.getBoolean(key, default)
        } catch (err: Throwable) {
            if (isKeystoreCorruption(err)) {
                resetCorruptedStore(ctx, err)
            } else {
                Log.w(TAG, "EncryptedSharedPreferences readBoolean failed ($key): ${err.javaClass.simpleName}")
            }
            default
        }
    }

    private fun editSafe(ctx: Context, block: SharedPreferences.Editor.() -> Unit) {
        val prefs = securePrefs(ctx) ?: return
        try {
            prefs.edit().apply(block).apply()
        } catch (err: Throwable) {
            if (isKeystoreCorruption(err)) {
                val recovered = resetCorruptedStore(ctx, err) ?: return
                runCatching { recovered.edit().apply(block).apply() }
            } else {
                Log.w(TAG, "EncryptedSharedPreferences write failed: ${err.javaClass.simpleName}")
            }
        }
    }

    /** 預熱快取與 StateFlow（建議於背景 IO 執行緒呼叫，避免主執行緒首次觸發同步解密）。 */
    fun warmUp(ctx: Context) {
        isLinkEnabled(ctx)
        getLastConnectedDesktopId(ctx)
        getPairedDevices(ctx)
    }

    /** R1 (#545) / R5: 非阻塞取得記憶體快取之已配對裝置清單（不觸發磁碟 I/O 或解密）。 */
    fun peekPairedDevices(): List<PairedDesktop> = cachedPairedDevices ?: _pairedDevicesFlow.value

    fun isLinkEnabled(ctx: Context): Boolean {
        cachedEnabled?.let { return it }
        val loaded = readBooleanSafe(ctx, KEY_ENABLED, true)
        cachedEnabled = loaded
        _linkEnabledFlow.value = loaded
        return loaded
    }

    fun setLinkEnabled(ctx: Context, enabled: Boolean) {
        cachedEnabled = enabled
        _linkEnabledFlow.value = enabled
        editSafe(ctx) { putBoolean(KEY_ENABLED, enabled) }
    }

    @Synchronized
    fun getOrCreatePhoneInstanceId(ctx: Context): String {
        cachedPhoneInstanceId?.takeIf { it.isNotBlank() }?.let { return it }
        val existing = readStringSafe(ctx, KEY_PHONE_INSTANCE_ID, null)
        if (!existing.isNullOrBlank()) {
            cachedPhoneInstanceId = existing
            return existing
        }
        val created = UUID.randomUUID().toString()
        cachedPhoneInstanceId = created
        editSafe(ctx) { putString(KEY_PHONE_INSTANCE_ID, created) }
        return created
    }

    fun getLastConnectedDesktopId(ctx: Context): String {
        cachedLastDesktopId?.let { return it }
        val loaded = readStringSafe(ctx, KEY_LAST_DESKTOP_ID, null).orEmpty()
        cachedLastDesktopId = loaded
        _lastDesktopIdFlow.value = loaded
        return loaded
    }

    fun setLastConnectedDesktopId(ctx: Context, instanceId: String) {
        cachedLastDesktopId = instanceId
        _lastDesktopIdFlow.value = instanceId
        editSafe(ctx) { putString(KEY_LAST_DESKTOP_ID, instanceId) }
    }

    fun getPairedDevices(ctx: Context): List<PairedDesktop> {
        cachedPairedDevices?.let { return it }
        return synchronized(this) {
            cachedPairedDevices?.let { return@synchronized it }
            val loaded = runCatching {
                val raw = readStringSafe(ctx, KEY_PAIRED_DEVICES, null).orEmpty()
                if (raw.isBlank()) return@runCatching emptyList()
                val type = object : TypeToken<List<PairedDesktop?>>() {}.type
                gson.fromJson<List<PairedDesktop?>>(raw, type)
                    .orEmpty()
                    .filterNotNull()
                    .filter { !it.instanceId.isNullOrBlank() }
            }.getOrDefault(emptyList())
            cachedPairedDevices = loaded
            _pairedDevicesFlow.value = loaded
            loaded
        }
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
            val snapshot = list.toList()
            cachedPairedDevices = snapshot
            _pairedDevicesFlow.value = snapshot
            editSafe(ctx) { putString(KEY_PAIRED_DEVICES, gson.toJson(snapshot)) }
        }
    }

    @Synchronized
    fun updateLastSeen(ctx: Context, instanceId: String, nowMs: Long = System.currentTimeMillis()) {
        runCatching {
            val list = getPairedDevices(ctx).map {
                if (it.instanceId == instanceId) it.copy(lastSeenAt = nowMs) else it
            }
            cachedPairedDevices = list
            _pairedDevicesFlow.value = list
            editSafe(ctx) { putString(KEY_PAIRED_DEVICES, gson.toJson(list)) }
        }
    }

    @Synchronized
    fun removePairedDevice(ctx: Context, instanceId: String) {
        runCatching {
            val list = getPairedDevices(ctx).filterNot { it.instanceId == instanceId }
            val filteredAudit = getAuditLogs(ctx).filterNot { it.instanceId == instanceId }
            val clearLast = getLastConnectedDesktopId(ctx) == instanceId
            cachedPairedDevices = list
            _pairedDevicesFlow.value = list
            cachedAuditLogs = filteredAudit
            if (clearLast) {
                cachedLastDesktopId = ""
                _lastDesktopIdFlow.value = ""
            }
            editSafe(ctx) {
                putString(KEY_PAIRED_DEVICES, gson.toJson(list))
                putString(KEY_AUDIT_LOGS, gson.toJson(filteredAudit))
                if (clearLast) {
                    remove(KEY_LAST_DESKTOP_ID)
                }
            }
        }
    }

    fun getAuditLogs(ctx: Context): List<CommandAuditEntry> {
        cachedAuditLogs?.let { return it }
        return synchronized(this) {
            cachedAuditLogs?.let { return@synchronized it }
            val loaded = runCatching {
                val raw = readStringSafe(ctx, KEY_AUDIT_LOGS, null).orEmpty()
                if (raw.isBlank()) return@runCatching emptyList()
                val type = object : TypeToken<List<CommandAuditEntry?>>() {}.type
                gson.fromJson<List<CommandAuditEntry?>>(raw, type).orEmpty().filterNotNull()
            }.getOrDefault(emptyList())
            cachedAuditLogs = loaded
            loaded
        }
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
            cachedAuditLogs = trimmed
            editSafe(ctx) { putString(KEY_AUDIT_LOGS, gson.toJson(trimmed)) }
        }
    }

    @Synchronized
    fun clearAuditLogs(ctx: Context) {
        cachedAuditLogs = emptyList()
        editSafe(ctx) { remove(KEY_AUDIT_LOGS) }
    }

    fun getUnknownAttempts(ctx: Context): List<UnknownAttempt> {
        cachedUnknownAttempts?.let { return it }
        return synchronized(this) {
            cachedUnknownAttempts?.let { return@synchronized it }
            val loaded = runCatching {
                val raw = readStringSafe(ctx, KEY_UNKNOWN_ATTEMPTS, null).orEmpty()
                if (raw.isBlank()) return@runCatching emptyList()
                val type = object : TypeToken<List<UnknownAttempt?>>() {}.type
                gson.fromJson<List<UnknownAttempt?>>(raw, type).orEmpty().filterNotNull()
            }.getOrDefault(emptyList())
            cachedUnknownAttempts = loaded
            loaded
        }
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
            cachedUnknownAttempts = trimmed
            editSafe(ctx) { putString(KEY_UNKNOWN_ATTEMPTS, gson.toJson(trimmed)) }
        }
    }

    @Synchronized
    fun clearUnknownAttempts(ctx: Context) {
        cachedUnknownAttempts = emptyList()
        editSafe(ctx) { remove(KEY_UNKNOWN_ATTEMPTS) }
    }

    fun getDiagnostics(ctx: Context): List<DiagnosticLogEntry> {
        cachedDiagnostics?.let { return it }
        return synchronized(this) {
            cachedDiagnostics?.let { return@synchronized it }
            val loaded = runCatching {
                val raw = readStringSafe(ctx, KEY_DIAGNOSTICS, null).orEmpty()
                if (raw.isBlank()) return@runCatching emptyList()
                val type = object : TypeToken<List<DiagnosticLogEntry?>>() {}.type
                gson.fromJson<List<DiagnosticLogEntry?>>(raw, type).orEmpty().filterNotNull()
            }.getOrDefault(emptyList())
            cachedDiagnostics = loaded
            loaded
        }
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
            cachedDiagnostics = trimmed
            editSafe(ctx) { putString(KEY_DIAGNOSTICS, gson.toJson(trimmed)) }
        }
    }

    @Synchronized
    fun clearDiagnostics(ctx: Context) {
        cachedDiagnostics = emptyList()
        editSafe(ctx) { remove(KEY_DIAGNOSTICS) }
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
