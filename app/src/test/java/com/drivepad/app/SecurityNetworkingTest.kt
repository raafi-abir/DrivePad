package com.drivepad.app

import com.drivepad.app.core.network.DrivePadPacket
import com.drivepad.app.core.network.MonotonicClock
import com.drivepad.app.core.network.NetworkRouteInspector
import com.drivepad.app.core.network.PacketCodec
import com.drivepad.app.core.network.PacketDecodeResult
import com.drivepad.app.core.network.PacketMessageType
import com.drivepad.app.core.network.UdpControllerConnectionManager
import com.drivepad.app.core.network.UdpReceiveResult
import com.drivepad.app.core.network.UdpTransport
import com.drivepad.app.core.preferences.UserPreferencesRepository
import com.drivepad.app.data.repository.ControllerInputRepository
import com.drivepad.app.domain.model.ConnectionStatus
import com.drivepad.app.domain.model.ControllerConfig
import com.drivepad.app.domain.model.LocalNetworkInterface
import com.drivepad.app.domain.model.NetworkRouteStatus
import com.drivepad.app.domain.model.NormalizedControllerState
import com.drivepad.app.domain.model.SteeringResponseCurve
import com.drivepad.app.domain.model.TransmissionMode
import com.drivepad.app.feature.racing.AutoGear
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

class SecurityNetworkingTest {

    private data class InboundDatagram(
        val bytes: ByteArray,
        val senderHost: String,
        val senderPort: Int
    )

    @Test
    fun packetCodec_fuzzing3000MalformedInputsNeverThrows() {
        val rng = Random(20261118L)
        val baseline = PacketCodec.encode(
            DrivePadPacket.controlPacket(PacketMessageType.PONG, 42u, 999_999L)
        )

        // Random lengths and bytes
        for (i in 0 until 1500) {
            val len = rng.nextInt(0, 257)
            val data = ByteArray(len).also { rng.nextBytes(it) }
            val decoded = PacketCodec.decode(data, len)
            if (len != DrivePadPacket.PACKET_SIZE_BYTES) {
                assertTrue(decoded is PacketDecodeResult.Invalid)
            }
        }

        // Targeted corrupted field permutations
        for (i in 0 until 1500) {
            val mutated = baseline.clone()
            when (i % 9) {
                0 -> mutated[0] = rng.nextInt(2, 255).toByte()
                1 -> mutated[1] = rng.nextInt(7, 255).toByte()
                2 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putFloat(14, Float.NaN)
                3 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putFloat(14, -1.01f)
                4 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putFloat(18, 0.33f)
                5 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putFloat(22, 0.75f)
                6 -> mutated[26] = rng.nextInt(2, 255).toByte()
                7 -> mutated[27] = (-3).toByte()
                8 -> ByteBuffer.wrap(mutated).order(ByteOrder.LITTLE_ENDIAN).putInt(29, Int.MIN_VALUE)
            }
            assertTrue("Corrupted variant ${i % 9} must reject cleanly", PacketCodec.decode(mutated) is PacketDecodeResult.Invalid)
        }
    }

    @Test
    fun udpConnectionManager_ignoresSpoofedSendersAndForgedPongTimestamps() = runBlocking {
        val inboundQueue = LinkedBlockingQueue<InboundDatagram>()
        val fakeClockNs = AtomicLong(10_000_000_000L)
        val fakeTransport = object : UdpTransport {
            @Volatile
            var opened = false
            override val isOpen: Boolean
                get() = opened

            override fun open(targetIp: String, targetPort: Int, localBindPort: Int): Result<Int> {
                opened = true
                return Result.success(if (localBindPort > 0) localBindPort else 50123)
            }

            override fun sendBlocking(data: ByteArray, length: Int): Result<Unit> = Result.success(Unit)

            override fun receiveBlocking(buffer: ByteArray, timeoutMs: Int): UdpReceiveResult {
                if (!opened) return UdpReceiveResult.Closed
                val item = inboundQueue.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                    ?: return UdpReceiveResult.Timeout
                val copyLen = item.bytes.size.coerceAtMost(buffer.size)
                item.bytes.copyInto(buffer, endIndex = copyLen)
                return UdpReceiveResult.Packet(copyLen, item.senderHost, item.senderPort)
            }

            override fun close() {
                opened = false
            }
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val inputRepo = StubInputRepo()
        val manager = UdpControllerConnectionManager(
            preferencesRepository = StubPrefsRepo(ControllerConfig(targetHostIp = "192.168.42.1", udpPort = 42100)),
            inputRepository = inputRepo,
            routeInspector = StubRouteInspector(),
            transportFactory = { fakeTransport },
            clock = object : MonotonicClock {
                override fun nanoTime(): Long = fakeClockNs.get()
            },
            ioDispatcher = Dispatchers.IO,
            scope = scope,
            handshakeTimeoutMs = 2_000L,
            helloRetryIntervalMs = 200L,
            pingIntervalMs = 5_000L,
            watchdogTimeoutMs = 2_000L,
            receivePollTimeoutMs = 25
        )

        try {
            delay(50)
            manager.connect()
            withTimeout(1_500) {
                while (!fakeTransport.isOpen) delay(10)
            }

            // 1. Spoofed IP & spoofed source port HELLO_ACK must be completely ignored
            val spoofedHelloAck = PacketCodec.encode(DrivePadPacket.controlPacket(PacketMessageType.HELLO_ACK, 1u, fakeClockNs.get()))
            inboundQueue.offer(InboundDatagram(spoofedHelloAck, "192.168.42.99", 42100))
            inboundQueue.offer(InboundDatagram(spoofedHelloAck, "192.168.42.1", 49999))
            delay(120)
            assertEquals(ConnectionStatus.CONNECTING, manager.diagnosticsState.value.status)
            assertEquals(0L, manager.diagnosticsState.value.packetsReceived)

            // 2. Authenticate from expected sender IP/port using an unmatched timestamp so we can isolate PONG check
            val genuineHelloAck = PacketCodec.encode(DrivePadPacket.controlPacket(PacketMessageType.HELLO_ACK, 1u, 1L))
            inboundQueue.offer(InboundDatagram(genuineHelloAck, "192.168.42.1", 42100))
            withTimeout(1_500) {
                while (manager.diagnosticsState.value.status != ConnectionStatus.CONNECTED) delay(10)
            }
            assertNull("Unmatched HELLO_ACK timestamp must not fabricate RTT", manager.diagnosticsState.value.rttMs)

            // 3. Forged PONG with fabricated timestamp (does not match pendingPings) must NOT inject fake RTT
            val forgedPong = PacketCodec.encode(
                DrivePadPacket.controlPacket(
                    PacketMessageType.PONG,
                    2u,
                    fakeClockNs.get() - 500_000_000L
                )
            )
            inboundQueue.offer(InboundDatagram(forgedPong, "192.168.42.1", 42100))
            withTimeout(1_500) {
                while (manager.diagnosticsState.value.packetsReceived < 2L) delay(10)
            }
            assertNull("Forged/unmatched PONG timestamp must never populate RTT", manager.diagnosticsState.value.rttMs)

            // 4. Clean disconnect triggers input safety reset
            manager.disconnect()
            assertTrue(inputRepo.resetCount >= 1)
        } finally {
            scope.cancel()
        }
    }

    private class StubPrefsRepo(initial: ControllerConfig) : UserPreferencesRepository {
        private val flow = MutableStateFlow(initial)
        override val controllerConfigFlow: Flow<ControllerConfig> = flow.asStateFlow()
        override val selectedProfileIdFlow: Flow<String> = MutableStateFlow("ets2")
        override suspend fun updateSteeringSensitivity(sensitivity: Float) {}
        override suspend fun updateSteeringDeadzone(deadzone: Float) {}
        override suspend fun updateMaxSteeringAngle(angle: Float) {}
        override suspend fun updateSteeringResponseCurve(curve: SteeringResponseCurve) {}
        override suspend fun updateInvertSteering(invert: Boolean) {}
        override suspend fun updateMotionSteeringEnabled(enabled: Boolean) {}
        override suspend fun updateMotionSmoothing(factor: Float) {}
        override suspend fun updateHapticFeedback(enabled: Boolean) {}
        override suspend fun updateReturnToCenter(enabled: Boolean) {}
        override suspend fun updateDefaultTransmission(mode: TransmissionMode) {}
        override suspend fun updateNetworkSettings(port: Int, hostIp: String) {}
        override suspend fun updateTargetUpdateFrequency(frequencyHz: Int) {}
        override suspend fun updateSelectedProfileId(profileId: String) {}
        override suspend fun resetToDefaults() {}
    }

    private class StubInputRepo : ControllerInputRepository {
        private val _state = MutableStateFlow(NormalizedControllerState())
        override val controllerState: StateFlow<NormalizedControllerState> = _state.asStateFlow()
        override val isSensorAvailable: Boolean = true
        override val isMotionSteeringActive: Boolean = false
        override val isTouchInteracting: StateFlow<Boolean> = MutableStateFlow(false)
        @Volatile
        var resetCount: Int = 0
        override fun onSteeringDrag(deltaAngleDegrees: Float) {}
        override fun onSteeringReleased() {}
        override fun setAcceleratorPressed(isPressed: Boolean) {}
        override fun setBrakePressed(isPressed: Boolean) {}
        override fun toggleHandbrake() {}
        override fun setTransmissionMode(mode: TransmissionMode) {}
        override fun selectAutoGear(gear: AutoGear) {}
        override fun shiftUp(maxGear: Int) {}
        override fun shiftDown(minGear: Int) {}
        override fun toggleLights() {}
        override fun setHorn(isPressed: Boolean) {}
        override fun toggleWipers() {}
        override fun toggleHazard() {}
        override fun toggleCruise() {}
        override fun toggleEngine() {}
        override fun toggleLeftIndicator() {}
        override fun toggleRightIndicator() {}
        override fun toggleMotionSteering(enabled: Boolean) {}
        override fun calibrateSteering(): Boolean = true
        override fun resetSteeringCalibration() {}
        override fun safetyResetAllInputs() { resetCount++ }
        override fun startSensors() {}
        override fun stopSensors() {}
    }

    private class StubRouteInspector : NetworkRouteInspector {
        private val status = NetworkRouteStatus(
            hasLocalRoute = true,
            isLikelyUsbTether = true,
            onlyCellularActive = false,
            activeInterfaces = listOf(LocalNetworkInterface("usb0", "192.168.42.129", true)),
            targetHostIp = "192.168.42.1",
            isTargetIpValid = true,
            isTargetPortValid = true,
            routeSummary = "USB tether"
        )
        override val routeStatus: StateFlow<NetworkRouteStatus> = MutableStateFlow(status)
        override fun evaluateRoute(targetHostIp: String, targetPort: Int): NetworkRouteStatus = status
        override fun startMonitoring() {}
        override fun stopMonitoring() {}
    }
}
