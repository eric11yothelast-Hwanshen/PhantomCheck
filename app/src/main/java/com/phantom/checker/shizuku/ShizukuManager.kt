package com.phantom.checker.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.phantom.checker.BuildConfig
import com.phantom.checker.service.NotificationCollectorService
import com.phantom.checker.service.StatsForegroundService
import com.phantom.checker.util.PhantomPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

data class ShizukuState(
    val isInstalled: Boolean = false,
    val isBinderAlive: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val uid: Int = -1,
    val isWatchdogActive: Boolean = false
) {
    val isReady: Boolean
        get() = isInstalled && isBinderAlive && isPermissionGranted
}

data class ShellResult(
    val exitCode: Int,
    val output: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}

/**
 * Shizuku 連線、權限與 UserService 管理器。
 * 當 Shizuku 服務未啟動或重開機斷線時，誠實反映為停用狀態，不影響一般保活路徑。
 */
object ShizukuManager {

    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private const val REQ_CODE_SHIZUKU = 5701

    private val _state = MutableStateFlow(ShizukuState())
    val state: StateFlow<ShizukuState> = _state.asStateFlow()

    @Volatile
    private var initialized = false

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var shellService: IShizukuShellService? = null

    @Volatile
    private var pendingConnect: CompletableDeferred<IShizukuShellService?>? = null

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        appContext?.let { refreshState(it) }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        shellService = null
        pendingConnect?.complete(null)
        pendingConnect = null
        appContext?.let { refreshState(it) }
    }

    private val permissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, _ ->
        if (requestCode == REQ_CODE_SHIZUKU) {
            appContext?.let { refreshState(it) }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IShizukuShellService.Stub.asInterface(binder)
            shellService = svc
            pendingConnect?.complete(svc)
            pendingConnect = null
            appContext?.let { ctx ->
                if (PhantomPrefs.isShizukuWatchdogEnabled(ctx) &&
                    PhantomPrefs.isPersistentStatsEnabled(ctx)
                ) {
                    try {
                        svc?.startWatchdog(ctx.packageName, StatsForegroundService::class.java.name)
                    } catch (_: Exception) {
                    }
                }
                refreshState(ctx)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            shellService = null
            pendingConnect?.complete(null)
            pendingConnect = null
            appContext?.let { refreshState(it) }
        }
    }

    fun init(ctx: Context) {
        val appCtx = ctx.applicationContext
        appContext = appCtx
        if (!initialized) {
            initialized = true
            try {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                Shizuku.addBinderDeadListener(binderDeadListener)
                Shizuku.addRequestPermissionResultListener(permissionListener)
            } catch (_: Exception) {
            }
        }
        refreshState(appCtx)
    }

    fun refreshState(ctx: Context) {
        val appCtx = ctx.applicationContext
        appContext = appCtx
        val installed = isShizukuInstalled(appCtx)
        val binderAlive = try {
            installed && Shizuku.pingBinder()
        } catch (_: Exception) {
            false
        }
        val granted = if (binderAlive) {
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        val uid = if (binderAlive) {
            try {
                Shizuku.getUid()
            } catch (_: Exception) {
                -1
            }
        } else {
            -1
        }
        val watchdogActive = if (granted) {
            try {
                shellService?.isWatchdogRunning == true
            } catch (_: Exception) {
                false
            }
        } else {
            false
        }
        _state.value = ShizukuState(
            isInstalled = installed,
            isBinderAlive = binderAlive,
            isPermissionGranted = granted,
            uid = uid,
            isWatchdogActive = watchdogActive
        )
    }

    fun requestPermission(ctx: Context): Boolean {
        init(ctx)
        val cur = _state.value
        if (!cur.isBinderAlive) return false
        if (cur.isPermissionGranted) return true
        return try {
            Shizuku.requestPermission(REQ_CODE_SHIZUKU)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun userServiceArgs(ctx: Context): Shizuku.UserServiceArgs {
        return Shizuku.UserServiceArgs(
            ComponentName(ctx.packageName, ShizukuWatchdogService::class.java.name)
        )
            .daemon(false)
            .processNameSuffix("shizuku_watchdog")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
    }

    suspend fun ensureConnected(ctx: Context): IShizukuShellService? {
        val appCtx = ctx.applicationContext
        init(appCtx)
        if (!_state.value.isReady) return null
        shellService?.let { existing ->
            try {
                if (existing.asBinder().pingBinder()) return existing
            } catch (_: Exception) {
                shellService = null
            }
        }
        val deferred = CompletableDeferred<IShizukuShellService?>()
        pendingConnect = deferred
        withContext(Dispatchers.Main) {
            try {
                Shizuku.bindUserService(userServiceArgs(appCtx), serviceConnection)
            } catch (_: Exception) {
                deferred.complete(null)
            }
        }
        return withTimeoutOrNull(4000L) { deferred.await() }
    }

    suspend fun startWatchdog(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val svc = ensureConnected(appCtx) ?: return@withContext false
        return@withContext try {
            svc.startWatchdog(appCtx.packageName, StatsForegroundService::class.java.name)
            refreshState(appCtx)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun stopWatchdog(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val svc = ensureConnected(appCtx) ?: return@withContext false
        return@withContext try {
            svc.stopWatchdog()
            refreshState(appCtx)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun execCommand(ctx: Context, command: String): ShellResult = withContext(Dispatchers.IO) {
        val svc = ensureConnected(ctx)
            ?: return@withContext ShellResult(1, "Shizuku 尚未連線或未授權")
        return@withContext try {
            val raw = svc.exec(command) ?: "EXIT:1\nNo output"
            val firstNewline = raw.indexOf('\n')
            val header = if (firstNewline >= 0) raw.substring(0, firstNewline) else raw
            val body = if (firstNewline >= 0) raw.substring(firstNewline + 1).trim() else ""
            val code = header.removePrefix("EXIT:").toIntOrNull() ?: 1
            ShellResult(code, body)
        } catch (e: Exception) {
            ShellResult(1, e.message ?: "執行失敗")
        }
    }

    suspend fun grantNotificationListener(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val appCtx = ctx.applicationContext
        val result = execCommand(
            appCtx,
            "cmd notification allow_listener ${appCtx.packageName}/${NotificationCollectorService::class.java.name}"
        )
        val granted = result.isSuccess &&
            NotificationManagerCompat.getEnabledListenerPackages(appCtx).contains(appCtx.packageName)
        refreshState(appCtx)
        granted
    }

    private fun isShizukuInstalled(ctx: Context): Boolean {
        return try {
            ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
            true
        } catch (_: Exception) {
            false
        }
    }
}
