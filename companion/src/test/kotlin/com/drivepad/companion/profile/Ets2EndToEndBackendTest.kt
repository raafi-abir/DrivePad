package com.drivepad.companion.profile

import com.drivepad.companion.DrivePadCompanionApp
import com.drivepad.companion.LocalRecordingInputEventWriter
import com.drivepad.companion.backend.uinput.InputEventRecord
import com.drivepad.companion.backend.uinput.LinuxInputConstants
import com.drivepad.companion.backend.uinput.UInputAvailability
import com.drivepad.companion.backend.uinput.UInputDeviceConfig
import com.drivepad.companion.backend.uinput.UInputGamepadBackend
import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.config.DiagnosticsMode
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.monitoring.CompanionConnectionStatus
import com.drivepad.companion.protocol.PacketMessageType
import com.drivepad.companion.protocol.ProtocolConstants
import com.drivepad.companion.protocol.TransmissionMode
import com.drivepad.companion.protocol.VehicleButtonFlag
import org.junit.Assert.assertEquals
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

class Ets2EndToEndBackendTest {

    private val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))

    @Test
    fun `ETS2 profile drives UDP to ProfileInputMapper to UInputGamepadBackend with pulses steering and failsafe reset`() {
        val writer = LocalRecordingInputEventWriter()
        val ets2Profile = ProfilePresets.ets2().copy(
            pulseDurationMs = 20L,
            pulseGapMs = 15L
        )
        val freePort = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val config = CompanionConfig(
            udpPort = freePort,
            networkInterface = "127.0.0.1",
            watchdogTimeoutMs = 220L,
            logLevel = LogLevel.ERROR,
            diagnosticsMode = DiagnosticsMode.SILENT,
            backendType = GamepadBackendType.UINPUT,
            activeProfileId = ets2Profile.id
        )
        val backend = UInputGamepadBackend(
            deviceConfig = UInputDeviceConfig.fromProfile(config, ets2Profile),
            writerFactory = { writer },
            availabilityProbe = {
                UInputAvailability(
                    supportedOs = true,
                    osDescription = "Linux (amd64)",
                    deviceExists = true,
                    deviceWritable = true,
                    summary = "Available (/dev/uinput writable)"
                )
            },
            profile = ets2Profile,
            logger = silentLogger
        )

        val app = DrivePadCompanionApp(
            config = config,
            profile = ets2Profile,
            logger = silentLogger,
            backendOverride = backend
        )

        app.start()
        val endpoint = InetSocketAddress("127.0.0.1", app.udpServer.boundPort)

        try {
            assertEquals("ets2", backend.diagnostics().activeProfileId)

            DatagramSocket(InetSocketAddress("127.0.0.1", 0)).use { client ->
                client.soTimeout = 1000
                val rx = ByteArray(64)

                // 1. Handshake
                val hello = buildWire(PacketMessageType.HELLO, 1)
                client.send(DatagramPacket(hello, hello.size, endpoint))
                client.receive(DatagramPacket(rx, rx.size))

                // 2. Active driving packet: progressive steering (0.5f), digital throttle, Horn (HOLD), Lights & Left Indicator (PULSE_ON_CHANGE)
                val p1 = buildWire(
                    type = PacketMessageType.CONTROLLER_STATE,
                    seq = 2,
                    steering = 0.5f,
                    throttle = 1.0f,
                    brake = 0.0f,
                    handbrake = false,
                    gear = 1,
                    mode = TransmissionMode.MANUAL,
                    flags = VehicleButtonFlag.HORN or VehicleButtonFlag.LIGHTS or VehicleButtonFlag.LEFT_INDICATOR
                )
                client.send(DatagramPacket(p1, p1.size, endpoint))
                waitUntil {
                    app.packetStatistics.snapshot().validPackets >= 2L &&
                        writer.writtenBatches.isNotEmpty() &&
                        backend.awaitIdle()
                }

                // 3. Shift up 1 -> 2 while keeping Horn held
                val p2 = buildWire(
                    type = PacketMessageType.CONTROLLER_STATE,
                    seq = 3,
                    steering = 0.5f,
                    throttle = 1.0f,
                    brake = 0.0f,
                    handbrake = false,
                    gear = 2,
                    mode = TransmissionMode.MANUAL,
                    flags = VehicleButtonFlag.HORN or VehicleButtonFlag.LIGHTS or VehicleButtonFlag.LEFT_INDICATOR
                )
                client.send(DatagramPacket(p2, p2.size, endpoint))

                waitUntil {
                    app.packetStatistics.snapshot().validPackets >= 3L && backend.awaitIdle()
                }

                val allEvents = writer.writtenBatches.flatten()
                // Lights (BTN_NORTH) and Left Indicator (BTN_TL) pulsed 1 then automatically released 0
                assertEquals(listOf(1, 0), allEvents.filter { it.code == LinuxInputConstants.BTN_NORTH }.map { it.value })
                assertEquals(listOf(1, 0), allEvents.filter { it.code == LinuxInputConstants.BTN_TL }.map { it.value })
                // Shift Up (BTN_DPAD_UP) pulsed 1 then 0
                assertEquals(listOf(1, 0), allEvents.filter { it.code == LinuxInputConstants.BTN_DPAD_UP }.map { it.value })
                // Horn (BTN_SOUTH) stayed held at 1 while still active!
                assertEquals(listOf(1), allEvents.filter { it.code == LinuxInputConstants.BTN_SOUTH }.map { it.value })
                // Throttle strictly 255
                assertTrue(allEvents.any { it == InputEventRecord(LinuxInputConstants.EV_ABS, LinuxInputConstants.ABS_Z, 255) })

                // 4. Stop sending -> Watchdog failsafe must release Horn + return Steering & Throttle to 0
                waitUntil(2500L) {
                    app.connectionMonitor.snapshot.value.status == CompanionConnectionStatus.FAILSAFE_TIMEOUT
                }
                assertTrue(backend.awaitIdle())
                val resetBatch = writer.writtenBatches.last()
                assertTrue(resetBatch.any { it == InputEventRecord(LinuxInputConstants.EV_ABS, LinuxInputConstants.ABS_X, 0) })
                assertTrue(resetBatch.any { it == InputEventRecord(LinuxInputConstants.EV_ABS, LinuxInputConstants.ABS_Z, 0) })
                assertTrue(resetBatch.any { it == InputEventRecord(LinuxInputConstants.EV_KEY, LinuxInputConstants.BTN_SOUTH, 0) })
            }
        } finally {
            app.stop()
        }
    }

    private fun waitUntil(timeoutMs: Long = 2000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(10)
        }
        assertTrue("Condition timed out after ${timeoutMs}ms", predicate())
    }

    private fun buildWire(
        type: PacketMessageType,
        seq: Int,
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
        bb.putLong(6, System.nanoTime())
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
