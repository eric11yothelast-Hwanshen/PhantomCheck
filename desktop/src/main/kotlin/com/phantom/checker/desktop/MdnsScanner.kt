package com.phantom.checker.desktop

import com.phantom.checker.shared.LinkProtocol
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

data class DiscoveredPhone(
    val serviceName: String,
    val host: String,
    val port: Int,
    val friendlyName: String,
    val fingerprintPrefix: String
)

/**
 * #449 JmDNS 3.6.4 區網服務掃描（_phantomcheck._tcp.local.）。
 */
class MdnsScanner {

    private val _devices = MutableStateFlow<List<DiscoveredPhone>>(emptyList())
    val devices: StateFlow<List<DiscoveredPhone>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    @Volatile
    private var jmdns: JmDNS? = null

    @Volatile
    private var stopped = false

    private val serviceType = "${LinkProtocol.MDNS_SERVICE_TYPE}local."

    fun start() {
        if (jmdns != null) return
        stopped = false
        _isScanning.value = true
        Thread {
            runCatching {
                val bindAddr = findSiteLocalIpv4()
                val instance = if (bindAddr != null) JmDNS.create(bindAddr) else JmDNS.create()
                if (stopped) {
                    runCatching { instance.close() }
                    return@runCatching
                }
                jmdns = instance
                instance.addServiceListener(serviceType, object : ServiceListener {
                    override fun serviceAdded(event: ServiceEvent) {
                        instance.requestServiceInfo(event.type, event.name, true)
                    }

                    override fun serviceRemoved(event: ServiceEvent) {
                        _devices.value = _devices.value.filterNot { it.serviceName == event.name }
                    }

                    override fun serviceResolved(event: ServiceEvent) {
                        val info = event.info ?: return
                        val host = info.inet4Addresses.firstOrNull()?.hostAddress
                            ?: info.hostAddresses.firstOrNull()
                            ?: return
                        val fn = info.getPropertyString("fn").orEmpty().ifBlank { event.name }
                        val fp = info.getPropertyString("fp").orEmpty()
                        val item = DiscoveredPhone(
                            serviceName = event.name,
                            host = host,
                            port = info.port,
                            friendlyName = fn,
                            fingerprintPrefix = fp
                        )
                        val updated = _devices.value.filterNot { it.serviceName == event.name } + item
                        _devices.value = updated
                    }
                })
            }.onFailure {
                _isScanning.value = false
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    fun stop() {
        stopped = true
        _isScanning.value = false
        val inst = jmdns
        jmdns = null
        if (inst != null) {
            Thread { runCatching { inst.close() } }.apply {
                isDaemon = true
                start()
            }
        }
    }

    private fun findSiteLocalIpv4(): java.net.InetAddress? {
        return runCatching {
            val ifaces = NetworkInterface.getNetworkInterfaces() ?: return null
            while (ifaces.hasMoreElements()) {
                val nif = ifaces.nextElement()
                if (!nif.isUp || nif.isLoopback) continue
                val addrs = nif.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && addr.isSiteLocalAddress && !addr.isLoopbackAddress) {
                        return addr
                    }
                }
            }
            null
        }.getOrNull()
    }
}
