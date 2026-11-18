package com.drivepad.companion.safety

import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.MonotonicClock
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.domain.ControllerStateProcessor
import com.drivepad.companion.domain.GamepadBackend
import com.drivepad.companion.monitoring.CompanionConnectionStatus
import com.drivepad.companion.monitoring.ConnectionMonitor
import com.drivepad.companion.monitoring.LatencyMonitor
import com.drivepad.companion.monitoring.PacketStatistics
import com.drivepad.companion.monitoring.WatchdogStatus
import com.drivepad.companion.network.ConnectionManager
import com.drivepad.companion.network.ProtocolHandler
import com.drivepad.companion.protocol.PacketMessageType
import com.drivepad.companion.protocol.ProtocolConstants
import com.drivepad.companion.protocol.TransmissionMode
import com.drivepad.companion.protocol.VehicleButtonFlag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WatchdogAndProtocolHandlerTest {

    private class FakeClock(var nowNs: Long = 10_000_000_000L) : MonotonicClock {
        override fun nanoTime(): Long = nowNs
        fun advanceMs(ms: Long) {
            nowNs += ms * 1_000_000L
        }
    }

    private class RecordingGamepadBackend : GamepadBackend {
        override val backendName: String = "TestMockGamepadBackend"
        override val isReady: Boolean = true
        val publishedStates = mutableListOf<ControllerState>()
        val safeResetStates = mutableListOf<ControllerState>()

        override fun initialize() {}
        override fun publishState(state: ControllerState) {
            publishedStates.add(state)
        }
        override fun resetToSafeState(state: ControllerState) {
            safeResetStates.add(state)
        }
        override fun close() {}
    }

    private fun encodePacket(
        type: PacketMessageType,
        seq: Int,
        tsNanos: Long = 999L,
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
        bb.putLong(6, tsNanos)
        bb.putFloat(14, steering)
        bb.putFloat(18, throttle)
        bb.putFloat(22, brake)
        bb.put(26, if (handbrake) 1 else 0)
        bb.put(27, gear.toByte())
        bb.put(28, mode.wireValue.toByte())
        bb.putInt(29, flags.toInt())
        return bytes
    }

    private fun createFixture(): Triple<ProtocolHandler, InputWatchdog, RecordingGamepadBackend> {
        val clock = FakeClock()
        val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))
        val backend = RecordingGamepadBackend()
        val connMonitor = ConnectionMonitor()
        val connManager = ConnectionManager(connMonitor)
        val stateProcessor = ControllerStateProcessor(outputs = listOf(backend))
        val watchdog = InputWatchdog(
            timeoutMs = 1500L,
            stateProcessor = stateProcessor,
            connectionMonitor = connMonitor,
            onSessionTimedOut = { connManager.onWatchdogTimeout() },
            clock = clock,
            logger = silentLogger
        )
        val handler = ProtocolHandler(
            connectionManager = connManager,
            connectionMonitor = connMonitor,
            stateProcessor = stateProcessor,
            packetStatistics = PacketStatistics(),
            latencyMonitor = LatencyMonitor(),
            watchdog = watchdog,
            clock = clock,
            logger = silentLogger
        )
        return Triple(handler, watchdog, backend)
    }

    @Test
    fun `rejects controller updates before explicit HELLO handshake`() {
        val clock = FakeClock()
        val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))
        val connMonitor = ConnectionMonitor()
        val connManager = ConnectionManager(connMonitor)
        val stateProcessor = ControllerStateProcessor()
        val stats = PacketStatistics()
        val watchdog = InputWatchdog(1500L, stateProcessor, connMonitor, { connManager.onWatchdogTimeout() }, clock, silentLogger)
        val handler = ProtocolHandler(connManager, connMonitor, stateProcessor, stats, LatencyMonitor(), watchdog, clock, silentLogger)
        val client = InetSocketAddress("192.168.42.129", 51000)

        val preHelloState = encodePacket(PacketMessageType.CONTROLLER_STATE, 1, steering = 0.75f, throttle = 1.0f)
        assertNull(handler.handleIncomingBytes(preHelloState, preHelloState.size, client))
        assertEquals(CompanionConnectionStatus.DISCONNECTED, connMonitor.snapshot.value.status)
        assertEquals(1L, stats.snapshot().invalidPackets)
        assertTrue(stateProcessor.controllerState.value.isSafeNeutral)
    }

    @Test
    fun `accepts HELLO updates all controller fields and notifies GamepadBackend`() {
        val (handler, _, backend) = createFixture()
        val client = InetSocketAddress("192.168.42.129", 51001)

        val hello = encodePacket(PacketMessageType.HELLO, 1, tsNanos = 42_000L)
        val ack = handler.handleIncomingBytes(hello, hello.size, client)
        assertNotNull(ack)
        assertEquals(PacketMessageType.HELLO_ACK, ack!!.messageType)
        assertEquals(42_000L, ack.echoedTimestampNanos)

        val allFlags = VehicleButtonFlag.LIGHTS or
            VehicleButtonFlag.HORN or
            VehicleButtonFlag.WIPERS or
            VehicleButtonFlag.HAZARD_LIGHTS or
            VehicleButtonFlag.LEFT_INDICATOR or
            VehicleButtonFlag.RIGHT_INDICATOR or
            VehicleButtonFlag.CRUISE_CONTROL or
            VehicleButtonFlag.ENGINE

        val pkt = encodePacket(
            type = PacketMessageType.CONTROLLER_STATE,
            seq = 2,
            steering = -0.85f,
            throttle = 0.0f,
            brake = 1.0f,
            handbrake = true,
            gear = 7,
            mode = TransmissionMode.MANUAL,
            flags = allFlags
        )
        assertNull(handler.handleIncomingBytes(pkt, pkt.size, client))

        assertEquals(1, backend.publishedStates.size)
        val state = backend.publishedStates.last()
        assertEquals(-0.85f, state.steering, 0.0001f)
        assertEquals(0.0f, state.throttle, 0.0f)
        assertEquals(1.0f, state.brake, 0.0f)
        assertTrue(state.handbrake)
        assertEquals(7, state.currentGear)
        assertEquals(TransmissionMode.MANUAL, state.transmissionMode)
        assertTrue(state.lights)
        assertTrue(state.horn)
        assertTrue(state.wipers)
        assertTrue(state.hazardLights)
        assertTrue(state.leftIndicator)
        assertTrue(state.rightIndicator)
        assertTrue(state.cruiseControl)
        assertTrue(state.engine)
        assertFalse(state.isSafeNeutral)

        // Different IP is ignored while client is connected
        val rogue = InetSocketAddress("192.168.42.200", 51001)
        val roguePkt = encodePacket(PacketMessageType.CONTROLLER_STATE, 3, steering = 1.0f)
        handler.handleIncomingBytes(roguePkt, roguePkt.size, rogue)
        assertEquals(1, backend.publishedStates.size)
    }

    @Test
    fun `watchdog timeout resets controller to safe neutral and allows seamless reconnect`() {
        val clock = FakeClock(1_000_000_000L)
        val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))
        val backend = RecordingGamepadBackend()
        val connMonitor = ConnectionMonitor()
        val connManager = ConnectionManager(connMonitor)
        val stateProcessor = ControllerStateProcessor(outputs = listOf(backend))
        val stats = PacketStatistics()
        val watchdog = InputWatchdog(
            timeoutMs = 1500L,
            stateProcessor = stateProcessor,
            connectionMonitor = connMonitor,
            onSessionTimedOut = { connManager.onWatchdogTimeout() },
            clock = clock,
            logger = silentLogger
        )
        val handler = ProtocolHandler(connManager, connMonitor, stateProcessor, stats, LatencyMonitor(), watchdog, clock, silentLogger)
        val client1 = InetSocketAddress("192.168.42.129", 51001)

        // Connect & engage throttle/steering/gear/flags
        val hello = encodePacket(PacketMessageType.HELLO, 1)
        handler.handleIncomingBytes(hello, hello.size, client1)
        val activePkt = encodePacket(
            type = PacketMessageType.CONTROLLER_STATE,
            seq = 2,
            steering = 0.9f,
            throttle = 1.0f,
            handbrake = true,
            gear = 5,
            flags = VehicleButtonFlag.LIGHTS or VehicleButtonFlag.CRUISE_CONTROL
        )
        handler.handleIncomingBytes(activePkt, activePkt.size, client1)
        assertEquals(0.9f, stateProcessor.controllerState.value.steering, 0.001f)

        // Just before timeout -> safe neutral NOT triggered
        clock.advanceMs(1400L)
        assertFalse(watchdog.evaluateNow(clock.nanoTime()))
        assertEquals(CompanionConnectionStatus.CONNECTED, connMonitor.snapshot.value.status)

        // Past 1500ms timeout -> failsafe neutral triggers
        clock.advanceMs(200L)
        assertTrue(watchdog.evaluateNow(clock.nanoTime()))
        assertFalse(watchdog.isArmed())
        assertEquals(CompanionConnectionStatus.FAILSAFE_TIMEOUT, connMonitor.snapshot.value.status)
        assertEquals(WatchdogStatus.FAILSAFE_ACTIVE, connMonitor.snapshot.value.watchdogStatus)
        assertEquals(1L, connMonitor.snapshot.value.failsafeActivations)

        val safe = stateProcessor.controllerState.value
        assertTrue(safe.isSafeNeutral)
        assertEquals(0.0f, safe.steering, 0.0f)
        assertEquals(0.0f, safe.throttle, 0.0f)
        assertEquals(0.0f, safe.brake, 0.0f)
        assertFalse(safe.handbrake)
        assertEquals(0, safe.currentGear)
        assertFalse(safe.lights)
        assertFalse(safe.cruiseControl)
        assertEquals(1, backend.safeResetStates.size)

        // Reconnect from a new ephemeral port via HELLO -> resumes cleanly with reset seq tracking
        val client2 = InetSocketAddress("192.168.42.129", 55220)
        val reconnectHello = encodePacket(PacketMessageType.HELLO, 1)
        val reconnectAck = handler.handleIncomingBytes(reconnectHello, reconnectHello.size, client2)
        assertNotNull(reconnectAck)
        assertEquals(CompanionConnectionStatus.CONNECTED, connMonitor.snapshot.value.status)
        assertEquals(1L, connMonitor.snapshot.value.reconnectCount)
        assertEquals(55220, connMonitor.snapshot.value.clientPort)
        assertEquals(0L, stats.snapshot().sequenceGaps)
    }

    @Test
    fun `DISCONNECT resets state cleanly without incrementing failsafe count`() {
        val clock = FakeClock()
        val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))
        val connMonitor = ConnectionMonitor()
        val connManager = ConnectionManager(connMonitor)
        val stateProcessor = ControllerStateProcessor()
        val watchdog = InputWatchdog(1500L, stateProcessor, connMonitor, { connManager.onWatchdogTimeout() }, clock, silentLogger)
        val handler = ProtocolHandler(connManager, connMonitor, stateProcessor, PacketStatistics(), LatencyMonitor(), watchdog, clock, silentLogger)
        val client = InetSocketAddress("127.0.0.1", 49999)

        handler.handleIncomingBytes(encodePacket(PacketMessageType.HELLO, 1), 33, client)
        handler.handleIncomingBytes(encodePacket(PacketMessageType.CONTROLLER_STATE, 2, throttle = 1.0f, gear = 3), 33, client)
        assertFalse(stateProcessor.controllerState.value.isSafeNeutral)

        handler.handleIncomingBytes(encodePacket(PacketMessageType.DISCONNECT, 3), 33, client)
        assertEquals(CompanionConnectionStatus.DISCONNECTED, connMonitor.snapshot.value.status)
        assertEquals(0L, connMonitor.snapshot.value.failsafeActivations)
        assertFalse(watchdog.isArmed())
        assertTrue(stateProcessor.controllerState.value.isSafeNeutral)
    }
}
