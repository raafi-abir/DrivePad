package com.drivepad.companion.monitoring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetSocketAddress

enum class CompanionConnectionStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTED("Connected"),
    FAILSAFE_TIMEOUT("Watchdog Failsafe")
}

enum class WatchdogStatus(val label: String) {
    IDLE("IDLE"),
    ARMED("ARMED"),
    FAILSAFE_ACTIVE("FAILSAFE TRIGGERED")
}

data class ConnectionStatusSnapshot(
    val status: CompanionConnectionStatus = CompanionConnectionStatus.DISCONNECTED,
    val clientIp: String? = null,
    val clientPort: Int? = null,
    val protocolVersion: Int? = null,
    val connectedAtNanos: Long = 0L,
    val lastValidPacketNanos: Long = 0L,
    val watchdogStatus: WatchdogStatus = WatchdogStatus.IDLE,
    val failsafeActivations: Long = 0L,
    val reconnectCount: Long = 0L
)

class ConnectionMonitor {
    private val _snapshot = MutableStateFlow(ConnectionStatusSnapshot())
    val snapshot: StateFlow<ConnectionStatusSnapshot> = _snapshot.asStateFlow()

    fun onClientConnected(address: InetSocketAddress, protocolVersion: Int, nowNanos: Long, isReconnect: Boolean) {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            status = CompanionConnectionStatus.CONNECTED,
            clientIp = address.address?.hostAddress ?: address.hostString,
            clientPort = address.port,
            protocolVersion = protocolVersion,
            connectedAtNanos = nowNanos,
            lastValidPacketNanos = nowNanos,
            watchdogStatus = WatchdogStatus.ARMED,
            reconnectCount = if (isReconnect) current.reconnectCount + 1L else current.reconnectCount
        )
    }

    fun onValidPacketReceived(nowNanos: Long) {
        val current = _snapshot.value
        if (current.status == CompanionConnectionStatus.CONNECTED) {
            _snapshot.value = current.copy(lastValidPacketNanos = nowNanos)
        }
    }

    fun onClientDisconnected() {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            status = CompanionConnectionStatus.DISCONNECTED,
            watchdogStatus = WatchdogStatus.IDLE
        )
    }

    fun onWatchdogFailsafeTriggered() {
        val current = _snapshot.value
        _snapshot.value = current.copy(
            status = CompanionConnectionStatus.FAILSAFE_TIMEOUT,
            watchdogStatus = WatchdogStatus.FAILSAFE_ACTIVE,
            failsafeActivations = current.failsafeActivations + 1L
        )
    }
}
