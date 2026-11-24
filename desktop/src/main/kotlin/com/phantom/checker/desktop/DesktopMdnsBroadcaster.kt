package com.phantom.checker.desktop

import com.phantom.checker.shared.LinkProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo

data class MdnsBroadcastStatus(
    val isAdvertising: Boolean = false,
    val boundIp: String = "",
    val serviceName: String = "",
    val lastError: String? = null
)

/**
 * #476 桌面端 JmDNS 3.6.5 區網廣播（_phantomcheck._tcp.local.）：
 * - TXT 記錄帶 fn=<電腦名稱>、fp=<憑證指紋前8碼>、v=2
 * - 支援列出所有 site-local IPv4 位址供多網卡環境選擇
 */
class DesktopMdnsBroadcaster {

    private val _status = MutableStateFlow(MdnsBroadcastStatus())
    val status: StateFlow<MdnsBroadcastStatus> = _status.asStateFlow()

    @Volatile
    private var jmdns: JmDNS? = null

    @Volatile
    private var stopped = false

    private val serviceType = "${LinkProtocol.MDNS_SERVICE_TYPE}local."

    fun start(port: Int, fingerprint: String, desktopName: String) {
        stop()
        stopped = false
        Thread {
            runCatching {
                val bindAddr = findPreferredSiteLocalIpv4()
                val instance = if (bindAddr != null) JmDNS.create(bindAddr) else JmDNS.create()
                if (stopped) {
                    runCatching { instance.close() }
                    return@runCatching
                }
                val cleanHost = desktopName.replace(Regex("[^A-Za-z0-9_-]"), "").take(16).ifBlank { "PC" }
                val svcName = "PhantomCheck-$cleanHost"
                val txtProps = mapOf(
                    "fn" to desktopName.take(28),
                    "fp" to fingerprint.take(8),
                    "v" to LinkProtocol.PROTOCOL_VERSION.toString()
                )
                val info = ServiceInfo.create(serviceType, svcName, port, 0, 0, txtProps)
                instance.registerService(info)
                jmdns = instance
                val ipText = bindAddr?.hostAddress ?: listSiteLocalIpv4Addresses().firstOrNull().orEmpty()
                _status.value = MdnsBroadcastStatus(
                    isAdvertising = true,
                    boundIp = ipText,
                    serviceName = svcName,
                    lastError = null
                )
                DesktopDiagnostics.info("mDNS", "Registered $svcName ($serviceType) on $ipText:$port (v=${LinkProtocol.PROTOCOL_VERSION}, fp=${fingerprint.take(8)})")
            }.onFailure { err ->
                val msg = err.message ?: err.javaClass.simpleName
                DesktopDiagnostics.warn("mDNS", "Broadcast registration failed: $msg")
                _status.value = MdnsBroadcastStatus(
                    isAdvertising = false,
                    boundIp = listSiteLocalIpv4Addresses().firstOrNull().orEmpty(),
                    serviceName = "",
                    lastError = msg
                )
            }
        }.apply {
            isDaemon = true
            name = "phantom-mdns-broadcast"
            start()
        }
    }

    fun stop() {
        stopped = true
        val inst = jmdns
        jmdns = null
        _status.value = _status.value.copy(isAdvertising = false)
        if (inst != null) {
            Thread {
                runCatching {
                    inst.unregisterAllServices()
                    inst.close()
                }.onFailure { err ->
                    DesktopDiagnostics.warn("mDNS", "Broadcast stop error: ${err.javaClass.simpleName}")
                }
            }.apply {
                isDaemon = true
                name = "phantom-mdns-stop"
                start()
            }
        }
    }

    companion object {
        fun listSiteLocalIpv4Addresses(): List<String> {
            return runCatching {
                val result = mutableListOf<String>()
                val ifaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
                while (ifaces.hasMoreElements()) {
                    val nif = ifaces.nextElement()
                    if (!nif.isUp || nif.isLoopback || nif.isVirtual) continue
                    val addrs = nif.inetAddresses
                    while (addrs.hasMoreElements()) {
                        val addr = addrs.nextElement()
                        if (addr is Inet4Address && addr.isSiteLocalAddress && !addr.isLoopbackAddress) {
                            val ip = addr.hostAddress
                            if (!ip.isNullOrBlank() && ip !in result) {
                                result.add(ip)
                            }
                        }
                    }
                }
                result.sortedWith(
                    compareBy<String>(
                        {
                            when {
                                it.startsWith("192.168.") -> 0
                                it.startsWith("10.") -> 1
                                it.startsWith("172.") -> 2
                                else -> 3
                            }
                        },
                        { it }
                    )
                )
            }.getOrDefault(emptyList())
        }

        fun findPreferredSiteLocalIpv4(): InetAddress? {
            val preferred = listSiteLocalIpv4Addresses().firstOrNull() ?: return null
            return runCatching { InetAddress.getByName(preferred) }.getOrNull()
        }
    }
}
