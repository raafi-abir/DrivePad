package com.drivepad.app.core.network

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.net.SocketTimeoutException

sealed class UdpReceiveResult {
    data class Packet(
        val length: Int,
        val senderHost: String,
        val senderPort: Int
    ) : UdpReceiveResult()

    data object Timeout : UdpReceiveResult()
    data object Closed : UdpReceiveResult()
    data class Error(val message: String, val cause: Throwable? = null) : UdpReceiveResult()
}

interface UdpTransport {
    val isOpen: Boolean
    fun open(targetIp: String, targetPort: Int, localBindPort: Int = 0): Result<Int>
    fun sendBlocking(data: ByteArray, length: Int): Result<Unit>
    fun receiveBlocking(buffer: ByteArray, timeoutMs: Int): UdpReceiveResult
    fun close()
}

class DatagramSocketUdpTransport : UdpTransport {
    @Volatile
    private var socket: DatagramSocket? = null

    @Volatile
    private var targetAddress: InetAddress? = null

    @Volatile
    private var targetPort: Int = 0

    // Pre-allocated DatagramPackets reused across calls to minimize GC allocations in the 250 Hz loop
    private var sendPacket: DatagramPacket? = null
    private var receivePacket: DatagramPacket? = null
    private val sendLock = Any()

    override val isOpen: Boolean
        get() = socket?.let { !it.isClosed } == true

    override fun open(targetIp: String, targetPort: Int, localBindPort: Int): Result<Int> {
        return runCatching {
            close()
            val resolvedAddress = InetAddress.getByName(targetIp.trim())
            val newSocket = if (localBindPort > 0) {
                DatagramSocket(localBindPort)
            } else {
                DatagramSocket()
            }
            newSocket.trafficClass = 0x10 // IPTOS_LOWDELAY hint where supported
            targetAddress = resolvedAddress
            this.targetPort = targetPort
            sendPacket = DatagramPacket(ByteArray(DrivePadPacket.PACKET_SIZE_BYTES), DrivePadPacket.PACKET_SIZE_BYTES, resolvedAddress, targetPort)
            receivePacket = DatagramPacket(ByteArray(256), 256)
            socket = newSocket
            newSocket.localPort
        }
    }

    override fun sendBlocking(data: ByteArray, length: Int): Result<Unit> {
        val activeSocket = socket
        val destAddr = targetAddress
        if (activeSocket == null || activeSocket.isClosed || destAddr == null) {
            return Result.failure(IllegalStateException("UDP socket is closed"))
        }
        return synchronized(sendLock) {
            try {
                val pkt = sendPacket ?: DatagramPacket(data, length, destAddr, targetPort).also {
                    sendPacket = it
                }
                pkt.setData(data, 0, length)
                pkt.address = destAddr
                pkt.port = targetPort
                activeSocket.send(pkt)
                Result.success(Unit)
            } catch (e: IOException) {
                Result.failure(e)
            } catch (e: SecurityException) {
                Result.failure(e)
            }
        }
    }

    override fun receiveBlocking(buffer: ByteArray, timeoutMs: Int): UdpReceiveResult {
        val activeSocket = socket ?: return UdpReceiveResult.Closed
        if (activeSocket.isClosed) return UdpReceiveResult.Closed

        return try {
            if (activeSocket.soTimeout != timeoutMs) {
                activeSocket.soTimeout = timeoutMs
            }
            val pkt = receivePacket ?: DatagramPacket(buffer, buffer.size).also { receivePacket = it }
            pkt.setData(buffer, 0, buffer.size)
            activeSocket.receive(pkt)
            UdpReceiveResult.Packet(
                length = pkt.length,
                senderHost = pkt.address?.hostAddress ?: "",
                senderPort = pkt.port
            )
        } catch (_: SocketTimeoutException) {
            UdpReceiveResult.Timeout
        } catch (e: SocketException) {
            if (activeSocket.isClosed) UdpReceiveResult.Closed
            else UdpReceiveResult.Error(e.message ?: "Socket exception", e)
        } catch (e: IOException) {
            UdpReceiveResult.Error(e.message ?: "UDP receive I/O error", e)
        }
    }

    override fun close() {
        val existing = socket
        socket = null
        targetAddress = null
        sendPacket = null
        receivePacket = null
        try {
            existing?.close()
        } catch (_: Exception) {
        }
    }
}
