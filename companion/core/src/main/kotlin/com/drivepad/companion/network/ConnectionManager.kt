package com.drivepad.companion.network

import com.drivepad.companion.monitoring.CompanionConnectionStatus
import com.drivepad.companion.monitoring.ConnectionMonitor
import java.net.InetSocketAddress

/**
 * Manages the active single-client session on the Linux companion.
 * Requires explicit `HELLO` handshake before accepting controller state updates,
 * tracks client address/port, and supports seamless reconnection.
 */
class ConnectionManager(
    private val connectionMonitor: ConnectionMonitor,
    private val activeSessionGuardWindowNs: Long = 1_500_000_000L
) {
    private val lock = Any()

    private var activeClient: InetSocketAddress? = null
    private var connected: Boolean = false
    private var hasEverConnected: Boolean = false
    private var lastActivityNanos: Long = 0L
    private var serverTxSeq: Long = 1L

    /**
     * Attempts to accept a client `HELLO`. Rejects cross-IP takeover while another IP holds an active
     * session within [activeSessionGuardWindowNs], while permitting same-IP crash reconnects
     * (new ephemeral port) and handovers after clean `DISCONNECT` or watchdog expiry.
     */
    fun tryAcceptHandshake(
        clientAddress: InetSocketAddress,
        protocolVersion: Int,
        nowNanos: Long
    ): Long? {
        val isReconnect: Boolean
        val replySeq: Long
        synchronized(lock) {
            val current = activeClient
            if (connected &&
                current != null &&
                current.address != clientAddress.address &&
                (nowNanos - lastActivityNanos) in 0L until activeSessionGuardWindowNs
            ) {
                return null
            }
            isReconnect = hasEverConnected
            activeClient = clientAddress
            connected = true
            hasEverConnected = true
            lastActivityNanos = nowNanos
            serverTxSeq = 1L
            replySeq = serverTxSeq++
        }
        connectionMonitor.onClientConnected(
            address = clientAddress,
            protocolVersion = protocolVersion,
            nowNanos = nowNanos,
            isReconnect = isReconnect
        )
        return replySeq
    }

    fun acceptHandshake(clientAddress: InetSocketAddress, protocolVersion: Int, nowNanos: Long): Long =
        tryAcceptHandshake(clientAddress, protocolVersion, nowNanos) ?: 1L

    fun touchActivity(nowNanos: Long) {
        synchronized(lock) {
            lastActivityNanos = nowNanos
        }
    }

    fun activeClientSummary(): String? = synchronized(lock) {
        activeClient?.let { "${it.address?.hostAddress}:${it.port}" }
    }

    fun isConnectedTo(sender: InetSocketAddress): Boolean = synchronized(lock) {
        connected && activeClient == sender
    }

    fun isConnected(): Boolean = synchronized(lock) { connected }

    fun nextServerReplySequence(): Long = synchronized(lock) {
        val current = serverTxSeq
        serverTxSeq = if (current >= 0xFFFFFFFFL) 1L else current + 1L
        current
    }

    fun disconnectClient(sender: InetSocketAddress): Boolean {
        val wasMatch = synchronized(lock) {
            if (connected && activeClient == sender) {
                connected = false
                activeClient = null
                lastActivityNanos = 0L
                true
            } else {
                false
            }
        }
        if (wasMatch) {
            connectionMonitor.onClientDisconnected()
        }
        return wasMatch
    }

    fun onWatchdogTimeout() {
        synchronized(lock) {
            connected = false
            activeClient = null
            lastActivityNanos = 0L
        }
    }

    fun currentStatus(): CompanionConnectionStatus = connectionMonitor.snapshot.value.status
}
