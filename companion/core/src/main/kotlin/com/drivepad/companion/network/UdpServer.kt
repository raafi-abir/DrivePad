package com.drivepad.companion.network

import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.protocol.PacketDecoder
import com.drivepad.companion.protocol.ProtocolConstants
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException

/**
 * Low-overhead cross-platform UDP server built on `DatagramSocket`. Reuses a single receive
 * buffer, reply buffer, and `DatagramPacket` instances on the IO coroutine to avoid heap allocations
 * on the 250 Hz hot path.
 */
class UdpServer(
    private val port: Int,
    private val bindTarget: String = "0.0.0.0",
    private val protocolHandler: ProtocolHandler,
    private val logger: StructuredLogger = StructuredLogger(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : AutoCloseable {

    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var receiveJob: Job? = null

    val boundPort: Int
        get() = socket?.localPort ?: port

    val boundAddress: String
        get() = socket?.localAddress?.hostAddress ?: bindTarget

    fun isRunning(): Boolean = socket?.let { !it.isClosed } == true && receiveJob?.isActive == true

    fun start(scope: CoroutineScope) {
        if (isRunning()) return

        val inetAddress = resolveBindAddress(bindTarget)
        val listenPort = port
        val newSocket = DatagramSocket(null).apply {
            reuseAddress = true
            // Low-delay IPTOS hint where supported by OS socket stack
            runCatching { trafficClass = 0x10 }
            bind(InetSocketAddress(inetAddress, listenPort))
        }
        socket = newSocket

        logger.info(
            "UdpServer",
            "UDP server listening",
            "bind" to (newSocket.localAddress.hostAddress ?: bindTarget),
            "port" to newSocket.localPort
        )

        receiveJob = scope.launch(ioDispatcher) {
            val rxBuffer = ByteArray(256)
            val txBuffer = ByteArray(ProtocolConstants.PACKET_SIZE_BYTES)
            val rxPacket = DatagramPacket(rxBuffer, rxBuffer.size)
            val txPacket = DatagramPacket(txBuffer, txBuffer.size)

            while (isActive && !newSocket.isClosed) {
                try {
                    rxPacket.setData(rxBuffer, 0, rxBuffer.size)
                    newSocket.receive(rxPacket)

                    val sender = (rxPacket.socketAddress as? InetSocketAddress)
                        ?: InetSocketAddress(rxPacket.address, rxPacket.port)

                    val reply = protocolHandler.handleIncomingBytes(
                        bytes = rxBuffer,
                        length = rxPacket.length,
                        sender = sender
                    )

                    if (reply != null) {
                        val replyLen = PacketDecoder.encodeReplyInto(
                            messageType = reply.messageType,
                            sequenceNumber = reply.sequenceNumber,
                            echoedTimestampNanos = reply.echoedTimestampNanos,
                            outBuffer = txBuffer
                        )
                        txPacket.setData(txBuffer, 0, replyLen)
                        txPacket.socketAddress = sender
                        newSocket.send(txPacket)
                    }
                } catch (e: SocketException) {
                    if (newSocket.isClosed || !isActive) {
                        break
                    }
                    logger.error("UdpServer", "Socket error during UDP receive", "error" to e.message)
                } catch (e: IOException) {
                    if (newSocket.isClosed || !isActive) {
                        break
                    }
                    logger.error("UdpServer", "I/O error during UDP receive", "error" to e.message)
                }
            }
        }
    }

    fun stop() {
        val activeSocket = socket
        socket = null
        try {
            activeSocket?.close()
        } catch (_: Exception) {
        }
        receiveJob?.cancel()
        receiveJob = null
        logger.info("UdpServer", "UDP server stopped gracefully")
    }

    override fun close() {
        stop()
    }

    data class DiscoveredInterface(
        val name: String,
        val displayName: String,
        val ipv4Address: String
    )

    companion object {
        fun listUpIpv4Interfaces(): List<DiscoveredInterface> {
            val discovered = mutableListOf<DiscoveredInterface>()
            val all = runCatching { NetworkInterface.getNetworkInterfaces() }.getOrNull() ?: return emptyList()
            while (all.hasMoreElements()) {
                val netIf = all.nextElement()
                val isUp = runCatching { netIf.isUp }.getOrDefault(false)
                val isLoopback = runCatching { netIf.isLoopback }.getOrDefault(false)
                if (!isUp || isLoopback) continue

                val addrs = netIf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        discovered.add(
                            DiscoveredInterface(
                                name = netIf.name ?: host,
                                displayName = netIf.displayName ?: netIf.name ?: host,
                                ipv4Address = host
                            )
                        )
                    }
                }
            }
            return discovered
        }

        /**
         * Resolves an IPv4 address (e.g., `0.0.0.0`, `127.0.0.1`, `192.168.42.10`),
         * a Linux network interface name (`usb0`, `rndis0`, `enp0s20u1`), or a Windows
         * adapter short/display name (`eth3`, `Remote NDIS`, `Ethernet 2`) to an [InetAddress].
         */
        fun resolveBindAddress(bindTarget: String): InetAddress {
            val trimmed = bindTarget.trim()
            require(trimmed.isNotEmpty()) { "Network bind target must not be blank" }
            if (trimmed == "0.0.0.0" || trimmed == "::") {
                return InetAddress.getByName(trimmed)
            }

            // 1. Exact interface short name match (works on both Linux `usb0` and Windows `ethX`)
            val netIf = runCatching { NetworkInterface.getByName(trimmed) }.getOrNull()
            if (netIf != null) {
                val addrs = netIf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (addr is Inet4Address) {
                        return addr
                    }
                }
                throw IllegalArgumentException(
                    "Interface '$trimmed' found but has no IPv4 address assigned"
                )
            }

            // 2. Windows/macOS friendly adapter displayName match (exact or substring, case-insensitive)
            val discovered = listUpIpv4Interfaces()
            val byDisplayExact = discovered.firstOrNull {
                it.displayName.equals(trimmed, ignoreCase = true) ||
                    it.name.equals(trimmed, ignoreCase = true)
            }
            if (byDisplayExact != null) {
                return InetAddress.getByName(byDisplayExact.ipv4Address)
            }

            val byDisplayContains = discovered.firstOrNull {
                it.displayName.contains(trimmed, ignoreCase = true)
            }
            if (byDisplayContains != null) {
                return InetAddress.getByName(byDisplayContains.ipv4Address)
            }

            return InetAddress.getByName(trimmed)
        }
    }
}
