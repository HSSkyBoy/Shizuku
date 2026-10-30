package moe.shizuku.manager.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.lifecycle.Observer
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executor

data class AdbMdnsResult(val host: String, val port: Int) {
    val isValid: Boolean get() = port in 1..65535 && host.isNotBlank()
}

@RequiresApi(Build.VERSION_CODES.R)
class AdbMdns(
    context: Context,
    private val serviceType: String,
    private val observer: Observer<AdbMdnsResult>
) {

    private var registered = false
    private var running = false
    private var serviceName: String? = null
    private val listener = DiscoveryListener(this)
    private val nsdManager: NsdManager = context.getSystemService(NsdManager::class.java)
    private val executor: Executor = context.mainExecutor

    fun start() {
        if (running) return
        running = true
        if (!registered) {
            try {
                nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start service discovery", e)
                running = false
            }
        }
    }

    fun stop() {
        if (!running) return
        running = false
        if (registered) {
            nsdManager.stopServiceDiscovery(listener)
        }
    }

    private fun onDiscoveryStart() {
        registered = true
    }

    private fun onDiscoveryStop() {
        registered = false
    }

    private fun onServiceFound(info: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            nsdManager.resolveService(info, executor, ResolveListener(this))
        } else {
            @Suppress("DEPRECATION")
            nsdManager.resolveService(info, ResolveListener(this))
        }
    }

    private fun onServiceLost(info: NsdServiceInfo) {
        if (info.serviceName == serviceName) {
            observer.onChanged(AdbMdnsResult("", -1))
        }
    }

    private fun onServiceResolved(resolvedService: NsdServiceInfo) {
        val resolvedHosts = resolvedService.resolvedHosts()
        val matchedHost = findLocalMatchingHost(resolvedHosts) ?: return

        if (running && isPortAvailable(matchedHost, resolvedService.port)) {
            serviceName = resolvedService.serviceName
            observer.onChanged(AdbMdnsResult(matchedHost, resolvedService.port))
        }
    }

    private fun findLocalMatchingHost(resolvedHosts: List<InetAddress>): String? {
        val localAddresses = runCatching {
            NetworkInterface.getNetworkInterfaces()
                ?.asSequence()
                ?.flatMap { it.inetAddresses.asSequence() }
                ?.mapNotNull { it.hostAddress }
                ?.toSet()
        }.getOrNull() ?: emptySet()

        return resolvedHosts.firstOrNull { it.hostAddress != null && it.hostAddress in localAddresses }?.hostAddress
            ?: resolvedHosts.firstOrNull()?.hostAddress
    }

    private fun NsdServiceInfo.resolvedHosts(): List<InetAddress> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            hostAddresses
        } else {
            @Suppress("DEPRECATION")
            listOfNotNull(host)
        }
    }

    private fun isPortAvailable(host: String, port: Int): Boolean {
        return try {
            Socket().use {
                it.connect(InetSocketAddress(host, port), 200)
                true
            }
        } catch (_: IOException) {
            try {
                ServerSocket().use {
                    it.bind(InetSocketAddress(host, port), 1)
                    false
                }
            } catch (_: IOException) {
                true
            }
        }
    }

    internal class DiscoveryListener(private val adbMdns: AdbMdns) : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(serviceType: String) {
            Log.v(TAG, "onDiscoveryStarted: $serviceType")
            adbMdns.onDiscoveryStart()
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.v(TAG, "onStartDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.v(TAG, "onDiscoveryStopped: $serviceType")
            adbMdns.onDiscoveryStop()
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.v(TAG, "onStopDiscoveryFailed: $serviceType, $errorCode")
        }

        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceFound: ${serviceInfo.serviceName}")
            adbMdns.onServiceFound(serviceInfo)
        }

        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            Log.v(TAG, "onServiceLost: ${serviceInfo.serviceName}")
            adbMdns.onServiceLost(serviceInfo)
        }
    }

    internal class ResolveListener(private val adbMdns: AdbMdns) : NsdManager.ResolveListener {
        override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, i: Int) {}

        override fun onServiceResolved(nsdServiceInfo: NsdServiceInfo) {
            adbMdns.onServiceResolved(nsdServiceInfo)
        }
    }

    companion object {
        const val TLS_CONNECT = "_adb-tls-connect._tcp"
        const val TLS_PAIRING = "_adb-tls-pairing._tcp"
        const val TAG = "AdbMdns"
    }
}
