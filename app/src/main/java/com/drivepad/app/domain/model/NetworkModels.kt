package com.drivepad.app.domain.model

data class LocalNetworkInterface(
    val name: String,
    val ipv4Address: String,
    val isUsbTetherCandidate: Boolean
)

data class NetworkRouteStatus(
    val hasLocalRoute: Boolean = false,
    val isLikelyUsbTether: Boolean = false,
    val onlyCellularActive: Boolean = false,
    val activeInterfaces: List<LocalNetworkInterface> = emptyList(),
    val targetHostIp: String = "192.168.42.1",
    val isTargetIpValid: Boolean = true,
    val isTargetPortValid: Boolean = true,
    val routeSummary: String = "Checking local network interfaces...",
    val warningMessage: String? = null
)

data class ConnectionDiagnosticsState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    val targetHostIp: String = "192.168.42.1",
    val targetPort: Int = 42100,
    val targetUpdateHz: Int = 250,
    val actualUpdateHz: Double = 0.0,
    val packetsSent: Long = 0L,
    val packetsReceived: Long = 0L,
    val sequenceGapsDetected: Long = 0L,
    val outOfOrderPackets: Long = 0L,
    val estimatedPacketLossPercent: Double = 0.0,
    val rttMs: Double? = null,
    val smoothedRttMs: Double? = null,
    val jitterMs: Double? = null,
    val lastSuccessfulCommElapsedMs: Long? = null,
    val lastAckSequence: Long? = null,
    val lastError: String? = null,
    val watchdogTriggerCount: Long = 0L,
    val isTransmitting: Boolean = false,
    val networkRoute: NetworkRouteStatus = NetworkRouteStatus()
)
