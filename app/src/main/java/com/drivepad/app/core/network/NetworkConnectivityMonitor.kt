package com.drivepad.app.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.drivepad.app.domain.model.LocalNetworkInterface
import com.drivepad.app.domain.model.NetworkRouteStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.net.NetworkInterface

interface NetworkRouteInspector {
    val routeStatus: StateFlow<NetworkRouteStatus>
    fun evaluateRoute(targetHostIp: String, targetPort: Int): NetworkRouteStatus
    fun startMonitoring()
    fun stopMonitoring()
}

class NetworkConnectivityMonitor(
    context: Context
) : NetworkRouteInspector {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _routeStatus = MutableStateFlow(NetworkRouteStatus())
    override val routeStatus: StateFlow<NetworkRouteStatus> = _routeStatus.asStateFlow()

    @Volatile
    private var lastTargetIp: String = "192.168.42.1"

    @Volatile
    private var lastTargetPort: Int = 42100

    @Volatile
    private var isRegistered: Boolean = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            refreshCurrentRoute()
        }

        override fun onLost(network: Network) {
            refreshCurrentRoute()
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            refreshCurrentRoute()
        }
    }

    override fun startMonitoring() {
        refreshCurrentRoute()
        val cm = connectivityManager ?: return
        if (isRegistered) return
        try {
            val request = NetworkRequest.Builder().build()
            cm.registerNetworkCallback(request, networkCallback)
            isRegistered = true
        } catch (_: SecurityException) {
            isRegistered = false
        } catch (_: RuntimeException) {
            isRegistered = false
        }
    }

    override fun stopMonitoring() {
        val cm = connectivityManager ?: return
        if (!isRegistered) return
        try {
            cm.unregisterNetworkCallback(networkCallback)
        } catch (_: IllegalArgumentException) {
            // Already unregistered
        } finally {
            isRegistered = false
        }
    }

    override fun evaluateRoute(targetHostIp: String, targetPort: Int): NetworkRouteStatus {
        lastTargetIp = targetHostIp.trim()
        lastTargetPort = targetPort
        val status = computeStatus(lastTargetIp, lastTargetPort)
        _routeStatus.value = status
        return status
    }

    private fun refreshCurrentRoute() {
        _routeStatus.value = computeStatus(lastTargetIp, lastTargetPort)
    }

    private fun computeStatus(targetHostIp: String, targetPort: Int): NetworkRouteStatus {
        val isIpValid = NetworkAddressValidator.isValidTargetIpv4(targetHostIp)
        val isPortValid = NetworkAddressValidator.isValidPort(targetPort)
        val interfaces = discoverLocalIpv4Interfaces()
        val hasUsbCandidate = interfaces.any { it.isUsbTetherCandidate }
        val isLoopbackTarget = targetHostIp == "127.0.0.1"
        val onlyCellular = detectCellularOnlyActive(interfaces, isLoopbackTarget)
        val hasLocal = interfaces.isNotEmpty() || isLoopbackTarget

        val summary = when {
            !isIpValid || !isPortValid -> "Invalid receiver IPv4 or UDP port"
            onlyCellular -> "Cellular data only — local USB tether interface not active"
            hasUsbCandidate -> {
                val tetherIf = interfaces.first { it.isUsbTetherCandidate }
                "USB tether route candidate: ${tetherIf.name} (${tetherIf.ipv4Address})"
            }
            interfaces.isNotEmpty() -> {
                val first = interfaces.first()
                "Local IPv4 route active: ${first.name} (${first.ipv4Address})"
            }
            isLoopbackTarget -> "Localhost loopback target (127.0.0.1)"
            else -> "No active local IPv4 route detected"
        }

        val warning = when {
            !isIpValid -> "Target IPv4 address '$targetHostIp' is invalid."
            !isPortValid -> "Target UDP port $targetPort must be between 1 and 65535."
            onlyCellular -> "Mobile data is active without a local USB/LAN interface. Cellular fallback is blocked; enable USB tethering."
            !hasLocal -> "Enable USB tethering (or connect to local subnet) before starting telemetry."
            else -> null
        }

        return NetworkRouteStatus(
            hasLocalRoute = hasLocal,
            isLikelyUsbTether = hasUsbCandidate,
            onlyCellularActive = onlyCellular,
            activeInterfaces = interfaces,
            targetHostIp = targetHostIp,
            isTargetIpValid = isIpValid,
            isTargetPortValid = isPortValid,
            routeSummary = summary,
            warningMessage = warning
        )
    }

    @Suppress("DEPRECATION")
    private fun detectCellularOnlyActive(
        localNonCellularInterfaces: List<LocalNetworkInterface>,
        isLoopbackTarget: Boolean
    ): Boolean {
        if (isLoopbackTarget || localNonCellularInterfaces.isNotEmpty()) return false
        val cm = connectivityManager ?: return false
        return try {
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            val hasCellular = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            val hasLocalTransport = caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                caps.hasTransport(NetworkCapabilities.TRANSPORT_USB)
            hasCellular && !hasLocalTransport
        } catch (_: SecurityException) {
            false
        }
    }

    private fun discoverLocalIpv4Interfaces(): List<LocalNetworkInterface> {
        val result = mutableListOf<LocalNetworkInterface>()
        try {
            val netInterfaces = NetworkInterface.getNetworkInterfaces() ?: return emptyList()
            while (netInterfaces.hasMoreElements()) {
                val netIf = netInterfaces.nextElement()
                if (!netIf.isUp || netIf.isLoopback) continue
                val ifName = netIf.name ?: ""
                val lowerName = ifName.lowercase()
                // Exclude typical cellular data interfaces so mobile data isn't mistaken for local USB tether
                if (isCellularInterfaceName(lowerName)) continue

                val addrs = netIf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val hostAddress = addr.hostAddress ?: continue
                        result.add(
                            LocalNetworkInterface(
                                name = ifName,
                                ipv4Address = hostAddress,
                                isUsbTetherCandidate = isUsbTetherInterfaceName(lowerName)
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Some sandboxed environments may throw SocketException enumerating interfaces
        }
        return result
    }

    companion object {
        fun isUsbTetherInterfaceName(lowerName: String): Boolean {
            return lowerName.startsWith("rndis") ||
                lowerName.startsWith("usb") ||
                lowerName.startsWith("ncm") ||
                lowerName.startsWith("ecm") ||
                lowerName.startsWith("enx") ||
                lowerName.startsWith("eth")
        }

        fun isCellularInterfaceName(lowerName: String): Boolean {
            return lowerName.startsWith("rmnet") ||
                lowerName.startsWith("ccmni") ||
                lowerName.startsWith("pdp") ||
                lowerName.startsWith("wwan") ||
                lowerName.startsWith("clat")
        }
    }
}
