package com.drivepad.companion

import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.config.DiagnosticsMode
import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.domain.GamepadBackend
import com.drivepad.companion.monitoring.CompanionConnectionStatus
import com.drivepad.companion.protocol.PacketDecodeResult
import com.drivepad.companion.protocol.PacketDecoder
import com.drivepad.companion.protocol.PacketMessageType
import com.drivepad.companion.protocol.ProtocolConstants
import com.drivepad.companion.protocol.TransmissionMode
import com.drivepad.companion.protocol.VehicleButtonFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

class CompanionEndToEndUdpTest {

    private class CountingBackend : GamepadBackend {
        override val backendName: String = "E2ECountingBackend"
        override val isReady: Boolean = true
        val updates = AtomicInteger(0)
        val resets = AtomicInteger(0)

        override fun initialize() {}
        override fun publishState(state: ControllerState) {
            updates.incrementAndGet()
        }
        override fun resetToSafeState(state: ControllerState) {
            resets.incrementAndGet()
        }
        override fun close() {}
    }

    private fun allocateFreePort(): Int = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { it.localPort }

    private fun buildWirePacket(
        type: PacketMessageType,
        seq: Int,
        timestampNanos: Long = System.nanoTime(),
        steering: Float = 0.0f,
        throttle: Float = 0.0f,
        brake: Float = 0.0f,
        handbrake: Boolean = false,
        gear: Int = 0,
        mode: TransmissionMode = TransmissionMode.AUTO,
        flags: UInt = 0u
    ): ByteArray {
        val bytes = ByteArray(ProtocolConstants.PACKET_SIZE_BYTES)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0, ProtocolConstants.PROTOCOL_VERSION_V1.toByte())
        bb.put(1, type.wireValue.toByte())
        bb.putInt(2, seq)
        bb.putLong(6, timestampNanos)
        bb.putFloat(14, steering)
        bb.putFloat(18, throttle)
        bb.putFloat(22, brake)
        bb.put(26, if (handbrake) 1 else 0)
        bb.put(27, gear.toByte())
        bb.put(28, mode.wireValue.toByte())
        bb.putInt(29, flags.toInt())
        return bytes
    }

    private fun waitUntil(timeoutMs: Long = 2000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    @Test
    fun `real UDP handshake 250Hz burst PING PONG watchdog timeout reconnect and graceful shutdown`() {
        val port = allocateFreePort()
        val backend = CountingBackend()
        val config = CompanionConfig(
            udpPort = port,
            networkInterface = "127.0.0.1",
            watchdogTimeoutMs = 200L,
            logLevel = LogLevel.ERROR,
            diagnosticsMode = DiagnosticsMode.SILENT
        )
        val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))
        val companion = DrivePadCompanionApp(
            config = config,
            outputs = listOf(backend),
            logger = silentLogger
        )

        companion.start()
        val serverEndpoint = InetSocketAddress("127.0.0.1", companion.udpServer.boundPort)

        DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { clientSocket ->
            clientSocket.soTimeout = 1000
            val rxBuf = ByteArray(64)
            val rxPacket = DatagramPacket(rxBuf, rxBuf.size)

            // 1. Handshake HELLO -> HELLO_ACK
            val helloTs = 123_456_789_000L
            val helloBytes = buildWirePacket(PacketMessageType.HELLO, 1, timestampNanos = helloTs)
            clientSocket.send(DatagramPacket(helloBytes, helloBytes.size, serverEndpoint))
            clientSocket.receive(rxPacket)
            val ack = (PacketDecoder.decode(rxBuf, rxPacket.length) as PacketDecodeResult.Valid).packet
            assertEquals(PacketMessageType.HELLO_ACK, ack.messageType)
            assertEquals(helloTs, ack.monotonicTimestampNanos)
            assertEquals(CompanionConnectionStatus.CONNECTED, companion.connectionMonitor.snapshot.value.status)

            // 2. PING -> PONG echo timestamp check
            val pingTs = 987_654_321_000L
            val pingBytes = buildWirePacket(PacketMessageType.PING, 2, timestampNanos = pingTs)
            clientSocket.send(DatagramPacket(pingBytes, pingBytes.size, serverEndpoint))
            clientSocket.receive(rxPacket)
            val pong = (PacketDecoder.decode(rxBuf, rxPacket.length) as PacketDecodeResult.Valid).packet
            assertEquals(PacketMessageType.PONG, pong.messageType)
            assertEquals(pingTs, pong.monotonicTimestampNanos)

            // 3. Burst 250 controller state packets sequentially and confirm all processed quickly
            val burstCount = 250
            val startNs = System.nanoTime()
            for (i in 1..burstCount) {
                val steer = -1.0f + (2.0f * i / burstCount.toFloat())
                val pkt = buildWirePacket(
                    type = PacketMessageType.CONTROLLER_STATE,
                    seq = 2 + i,
                    steering = steer.coerceIn(-1.0f, 1.0f),
                    throttle = 1.0f,
                    brake = 0.0f,
                    handbrake = false,
                    gear = 4,
                    mode = TransmissionMode.MANUAL,
                    flags = VehicleButtonFlag.LIGHTS or VehicleButtonFlag.ENGINE
                )
                clientSocket.send(DatagramPacket(pkt, pkt.size, serverEndpoint))
            }

            waitUntil(timeoutMs = 2000L) {
                backend.updates.get() >= burstCount
            }
            val elapsedMs = (System.nanoTime() - startNs) / 1_000_000L
            assertTrue("250-packet processing took too long: ${elapsedMs}ms", elapsedMs < 1500L)
            assertEquals(0L, companion.packetStatistics.snapshot().sequenceGaps)
            assertEquals(4, companion.stateProcessor.controllerState.value.currentGear)
            assertFalse(companion.stateProcessor.controllerState.value.isSafeNeutral)

            // 4. Stop sending and verify real async watchdog triggers failsafe neutral
            waitUntil(timeoutMs = 2000L) {
                companion.connectionMonitor.snapshot.value.status == CompanionConnectionStatus.FAILSAFE_TIMEOUT &&
                    companion.stateProcessor.controllerState.value.isSafeNeutral
            }
            assertTrue(backend.resets.get() >= 1)
        }

        // 5. Reconnect from a brand new socket cleanly after failsafe
        DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { clientSocket2 ->
            clientSocket2.soTimeout = 1000
            val hello2 = buildWirePacket(PacketMessageType.HELLO, 1)
            clientSocket2.send(DatagramPacket(hello2, hello2.size, serverEndpoint))
            val rx = ByteArray(64)
            val rxPacket = DatagramPacket(rx, rx.size)
            clientSocket2.receive(rxPacket)
            val ack2 = (PacketDecoder.decode(rx, rxPacket.length) as PacketDecodeResult.Valid).packet
            assertEquals(PacketMessageType.HELLO_ACK, ack2.messageType)
            assertEquals(1L, companion.connectionMonitor.snapshot.value.reconnectCount)

            val statePkt = buildWirePacket(PacketMessageType.CONTROLLER_STATE, 2, throttle = 1.0f, gear = 2)
            clientSocket2.send(DatagramPacket(statePkt, statePkt.size, serverEndpoint))
            waitUntil { companion.stateProcessor.controllerState.value.currentGear == 2 }
        }

        // 6. Graceful shutdown stops socket and resets state to safe neutral
        companion.stop()
        assertFalse(companion.udpServer.isRunning())
        assertTrue(companion.stateProcessor.controllerState.value.isSafeNeutral)
    }
}
