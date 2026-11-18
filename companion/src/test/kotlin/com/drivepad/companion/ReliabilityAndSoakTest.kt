package com.drivepad.companion

import com.drivepad.companion.backend.uinput.InputEventRecord
import com.drivepad.companion.backend.uinput.InputEventWriter
import com.drivepad.companion.backend.uinput.UInputAvailability
import com.drivepad.companion.backend.uinput.UInputAxisSpec
import com.drivepad.companion.backend.uinput.UInputDeviceConfig
import com.drivepad.companion.backend.uinput.UInputGamepadBackend
import com.drivepad.companion.backend.windows.ViGEmX360GamepadBackend
import com.drivepad.companion.backend.windows.WindowsAvailability
import com.drivepad.companion.backend.windows.WindowsVirtualGamepadDriver
import com.drivepad.companion.backend.windows.XusbReportData
import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.config.DiagnosticsMode
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.profile.ProfilePresets
import com.drivepad.companion.protocol.PacketDecoder
import com.drivepad.companion.protocol.PacketMessageType
import com.drivepad.companion.protocol.ProtocolConstants
import com.drivepad.companion.protocol.TransmissionMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.PrintStream
import java.lang.management.ManagementFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class ReliabilityAndSoakTest {

    private val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))

    @Test
    fun `250Hz localhost UDP loopback measures real throughput RTT jitter loss and overhead`() {
        val writer = LocalRecordingInputEventWriter()
        val backend = UInputGamepadBackend(
            deviceConfig = UInputDeviceConfig(),
            writerFactory = { writer },
            availabilityProbe = {
                UInputAvailability(
                    supportedOs = true,
                    osDescription = "Linux Loopback Harness",
                    deviceExists = true,
                    deviceWritable = true,
                    summary = "Mock Writer Ready"
                )
            },
            shiftPulseDurationMs = 20L,
            logger = silentLogger
        )

        val freePort = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val companion = DrivePadCompanionApp(
            config = CompanionConfig(
                udpPort = freePort,
                networkInterface = "127.0.0.1",
                watchdogTimeoutMs = 1500L,
                logLevel = LogLevel.ERROR,
                diagnosticsMode = DiagnosticsMode.SILENT,
                backendType = GamepadBackendType.UINPUT
            ),
            logger = silentLogger,
            backendOverride = backend
        )

        companion.start()
        val endpoint = InetSocketAddress("127.0.0.1", companion.udpServer.boundPort)

        val threadMx = ManagementFactory.getThreadMXBean()
        val runtime = Runtime.getRuntime()
        val rttSamplesMs = mutableListOf<Double>()

        try {
            DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { client ->
                client.soTimeout = 500
                var seq = 1
                val hello = buildWire(PacketMessageType.HELLO, seq++)
                client.send(DatagramPacket(hello, hello.size, endpoint))
                val ackBuf = ByteArray(64)
                client.receive(DatagramPacket(ackBuf, ackBuf.size))

                val startCpuNs = if (threadMx.isCurrentThreadCpuTimeSupported) threadMx.currentThreadCpuTime else 0L
                val heapBefore = runtime.totalMemory() - runtime.freeMemory()
                val startWallNs = System.nanoTime()

                val totalStatePackets = 1000 // 4 seconds @ 250 Hz
                val intervalNs = 4_000_000L
                var nextDeadlineNs = startWallNs

                for (i in 0 until totalStatePackets) {
                    val nowNs = System.nanoTime()
                    val waitNs = nextDeadlineNs - nowNs
                    if (waitNs > 1_000_000L) {
                        Thread.sleep(waitNs / 1_000_000L)
                    }
                    val steer = kotlin.math.sin(i * 0.05).toFloat().coerceIn(-1.0f, 1.0f)
                    val pkt = buildWire(
                        type = PacketMessageType.CONTROLLER_STATE,
                        seq = seq++,
                        steering = steer,
                        throttle = if (i % 10 < 7) 1.0f else 0.0f
                    )
                    client.send(DatagramPacket(pkt, pkt.size, endpoint))

                    if (i % 50 == 0) {
                        val pingTs = System.nanoTime()
                        val ping = buildWire(PacketMessageType.PING, seq++, timestampNs = pingTs)
                        client.send(DatagramPacket(ping, ping.size, endpoint))
                        val pongBuf = ByteArray(64)
                        val pongPkt = DatagramPacket(pongBuf, pongBuf.size)
                        client.receive(pongPkt)
                        val decoded = PacketDecoder.decode(pongBuf, pongPkt.length)
                        if (decoded is com.drivepad.companion.protocol.PacketDecodeResult.Valid) {
                            rttSamplesMs.add((System.nanoTime() - decoded.packet.monotonicTimestampNanos) / 1_000_000.0)
                        }
                    }
                    nextDeadlineNs += intervalNs
                }

                // Wait briefly for server drain (1 HELLO + 1000 CONTROLLER_STATE + 20 PING = 1021)
                val expectedTotalPackets = 1L + totalStatePackets.toLong() + rttSamplesMs.size.toLong()
                val drainDeadline = System.currentTimeMillis() + 1000L
                while (System.currentTimeMillis() < drainDeadline &&
                    companion.packetStatistics.snapshot().validPackets < expectedTotalPackets
                ) {
                    Thread.sleep(10)
                }

                val elapsedSec = (System.nanoTime() - startWallNs) / 1_000_000_000.0
                val endCpuNs = if (threadMx.isCurrentThreadCpuTimeSupported) threadMx.currentThreadCpuTime else 0L
                val heapAfter = runtime.totalMemory() - runtime.freeMemory()

                val stats = companion.packetStatistics.snapshot()
                val lat = companion.latencyMonitor.snapshot(System.nanoTime())
                val actualHz = (stats.validPackets - 1L).coerceAtLeast(0L) / elapsedSec
                val avgRttMs = rttSamplesMs.average()
                val maxRttMs = rttSamplesMs.maxOrNull() ?: 0.0

                println(
                    "[PHASE11_BENCHMARK] packets_valid=${stats.validPackets}, sequence_gaps=${stats.sequenceGaps}, " +
                        "invalid=${stats.invalidPackets}, loss_pct=${"%.3f".format(stats.estimatedPacketLossPercent)}, " +
                        "elapsed_s=${"%.2f".format(elapsedSec)}, measured_hz=${"%.1f".format(actualHz)}, " +
                        "client_avg_rtt_ms=${"%.3f".format(avgRttMs)}, client_max_rtt_ms=${"%.3f".format(maxRttMs)}, " +
                        "server_jitter_ms=${"%.3f".format(lat.interArrivalJitterMs)}, " +
                        "sender_cpu_ms=${(endCpuNs - startCpuNs) / 1_000_000L}, " +
                        "heap_delta_kb=${(heapAfter - heapBefore) / 1024L}"
                )

                assertTrue("Expected >= 1020 valid packets received, got ${stats.validPackets}", stats.validPackets >= 1020L)
                assertEquals(0L, stats.invalidPackets)
                assertEquals(0L, stats.sequenceGaps)
                assertTrue("Actual loopback Hz should be > 100 Hz, was $actualHz", actualHz > 100.0)
                assertTrue("RTT samples should be measured", rttSamplesMs.size >= 15)

                val disc = buildWire(PacketMessageType.DISCONNECT, seq)
                client.send(DatagramPacket(disc, disc.size, endpoint))
            }
        } finally {
            companion.stop()
        }
    }

    @Test
    fun `fast HELLO reconnect before watchdog expiry resets prior session inputs to SafeNeutral immediately`() {
        val driver = LocalRecordingWindowsDriver()
        val backend = ViGEmX360GamepadBackend(
            driverFactory = { driver },
            availabilityProbe = {
                WindowsAvailability(
                    supportedOs = true,
                    osDescription = "Windows 11 Loopback Mock",
                    clientLibraryFound = true,
                    clientLibraryPath = "ViGEmClient.dll",
                    summary = "Mock ViGEm Ready"
                )
            },
            profile = ProfilePresets.ets2(),
            logger = silentLogger
        )

        val freePort = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val companion = DrivePadCompanionApp(
            config = CompanionConfig(
                udpPort = freePort,
                networkInterface = "127.0.0.1",
                watchdogTimeoutMs = 5000L, // Intentionally longer than reconnect gap
                logLevel = LogLevel.ERROR,
                diagnosticsMode = DiagnosticsMode.SILENT,
                backendType = GamepadBackendType.VIGEM_X360,
                activeProfileId = "ets2"
            ),
            profile = ProfilePresets.ets2(),
            logger = silentLogger,
            backendOverride = backend
        )

        companion.start()
        val endpoint = InetSocketAddress("127.0.0.1", companion.udpServer.boundPort)

        try {
            // Client 1 connects and holds full throttle + steering, then crashes (no DISCONNECT)
            DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { client1 ->
                client1.soTimeout = 1000
                val hello1 = buildWire(PacketMessageType.HELLO, 1)
                client1.send(DatagramPacket(hello1, hello1.size, endpoint))
                client1.receive(DatagramPacket(ByteArray(64), 64))

                val active1 = buildWire(PacketMessageType.CONTROLLER_STATE, 2, steering = -0.9f, throttle = 1.0f)
                client1.send(DatagramPacket(active1, active1.size, endpoint))
                val deadline = System.currentTimeMillis() + 1500L
                while (System.currentTimeMillis() < deadline && companion.stateProcessor.controllerState.value.throttle < 1.0f) {
                    Thread.sleep(10)
                }
                assertTrue(backend.awaitIdle())
                assertEquals(1.0f, companion.stateProcessor.controllerState.value.throttle, 0.001f)
            }

            // Client 2 reconnects quickly (< 5s watchdog) -> must reset state to SafeNeutral on HELLO
            DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { client2 ->
                client2.soTimeout = 1000
                val hello2 = buildWire(PacketMessageType.HELLO, 1)
                client2.send(DatagramPacket(hello2, hello2.size, endpoint))
                client2.receive(DatagramPacket(ByteArray(64), 64))

                assertTrue(backend.awaitIdle())
                val stateAfterReconnect = companion.stateProcessor.controllerState.value
                assertTrue("HELLO reconnect must reset stateProcessor to SafeNeutral", stateAfterReconnect.isSafeNeutral)
                assertEquals(0.0f, stateAfterReconnect.throttle, 0.001f)
                assertEquals(0.0f, stateAfterReconnect.steering, 0.001f)
                assertEquals(XusbReportData.NEUTRAL, driver.submittedReports.last())
            }
        } finally {
            companion.stop()
        }
    }

    @Test
    fun `mid-stream virtual device IO failure surfaces diagnostic error without crashing UDP server`() {
        val failNextWrite = AtomicBoolean(false)
        val writeCount = AtomicInteger(0)
        val failingWriter = object : InputEventWriter {
            override fun open(devicePath: String) {}
            override fun enableEventType(eventType: Int) {}
            override fun enableKeyBit(keyCode: Int) {}
            override fun enableAbsBit(absCode: Int) {}
            override fun setupAbsAxis(axis: UInputAxisSpec) {}
            override fun setupDevice(config: UInputDeviceConfig) {}
            override fun createDevice() {}
            override fun writeEvents(events: List<InputEventRecord>) {
                writeCount.incrementAndGet()
                if (failNextWrite.get()) {
                    throw IOException("Simulated /dev/uinput kernel failure")
                }
            }
            override fun destroyDevice() {}
            override fun close() {}
        }

        val backend = UInputGamepadBackend(
            deviceConfig = UInputDeviceConfig(),
            writerFactory = { failingWriter },
            availabilityProbe = {
                UInputAvailability(true, "Linux Test", true, true, "Mock Available")
            },
            logger = silentLogger
        )

        val freePort = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val companion = DrivePadCompanionApp(
            config = CompanionConfig(
                udpPort = freePort,
                networkInterface = "127.0.0.1",
                watchdogTimeoutMs = 1500L,
                logLevel = LogLevel.ERROR,
                diagnosticsMode = DiagnosticsMode.SILENT,
                backendType = GamepadBackendType.UINPUT
            ),
            logger = silentLogger,
            backendOverride = backend
        )
        companion.start()
        val endpoint = InetSocketAddress("127.0.0.1", companion.udpServer.boundPort)

        try {
            DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { client ->
                client.soTimeout = 1000
                val hello = buildWire(PacketMessageType.HELLO, 1)
                client.send(DatagramPacket(hello, hello.size, endpoint))
                client.receive(DatagramPacket(ByteArray(64), 64))

                failNextWrite.set(true)
                val active = buildWire(PacketMessageType.CONTROLLER_STATE, 2, steering = 0.5f, throttle = 1.0f)
                client.send(DatagramPacket(active, active.size, endpoint))

                val deadline = System.currentTimeMillis() + 1500L
                while (System.currentTimeMillis() < deadline && backend.diagnostics().isVirtualDeviceActive) {
                    Thread.sleep(15)
                }
                assertFalse("Virtual device must mark inactive after write IOException", backend.diagnostics().isVirtualDeviceActive)
                assertNotNull("lastError must report failure reason", backend.diagnostics().lastError)
                assertTrue(companion.udpServer.isRunning())
            }
        } finally {
            companion.stop()
        }
    }

    private fun buildWire(
        type: PacketMessageType,
        seq: Int,
        timestampNs: Long = System.nanoTime(),
        steering: Float = 0.0f,
        throttle: Float = 0.0f,
        brake: Float = 0.0f,
        handbrake: Boolean = false,
        gear: Int = 1,
        mode: TransmissionMode = TransmissionMode.AUTO,
        flags: UInt = 0u
    ): ByteArray {
        val bytes = ByteArray(ProtocolConstants.PACKET_SIZE_BYTES)
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.put(0, ProtocolConstants.PROTOCOL_VERSION_V1.toByte())
        bb.put(1, type.wireValue.toByte())
        bb.putInt(2, seq)
        bb.putLong(6, timestampNs)
        bb.putFloat(14, steering)
        bb.putFloat(18, throttle)
        bb.putFloat(22, brake)
        bb.put(26, if (handbrake) 1 else 0)
        bb.put(27, gear.toByte())
        bb.put(28, mode.wireValue.toByte())
        bb.putInt(29, flags.toInt())
        return bytes
    }
}
