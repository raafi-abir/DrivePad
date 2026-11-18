package com.drivepad.companion

import com.drivepad.companion.backend.uinput.InputEventRecord
import com.drivepad.companion.backend.uinput.InputEventWriter
import com.drivepad.companion.backend.uinput.LinuxInputConstants
import com.drivepad.companion.backend.uinput.UInputAvailability
import com.drivepad.companion.backend.uinput.UInputAxisSpec
import com.drivepad.companion.backend.uinput.UInputDeviceConfig
import com.drivepad.companion.backend.uinput.UInputGamepadBackend
import com.drivepad.companion.backend.windows.ViGEmConstants
import com.drivepad.companion.backend.windows.ViGEmX360GamepadBackend
import com.drivepad.companion.backend.windows.WindowsAvailability
import com.drivepad.companion.backend.windows.WindowsVirtualGamepadDriver
import com.drivepad.companion.backend.windows.XusbReportData
import com.drivepad.companion.config.CompanionConfig
import com.drivepad.companion.config.DiagnosticsMode
import com.drivepad.companion.config.GamepadBackendType
import com.drivepad.companion.config.LogLevel
import com.drivepad.companion.diagnostics.StructuredLogger
import com.drivepad.companion.monitoring.CompanionConnectionStatus
import com.drivepad.companion.profile.ProfilePresets
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

internal class LocalRecordingInputEventWriter : InputEventWriter {
    var deviceDestroyed = false
    var closed = false
    val writtenBatches = mutableListOf<List<InputEventRecord>>()

    override fun open(devicePath: String) {}
    override fun enableEventType(eventType: Int) {}
    override fun enableKeyBit(keyCode: Int) {}
    override fun enableAbsBit(absCode: Int) {}
    override fun setupAbsAxis(axis: UInputAxisSpec) {}
    override fun setupDevice(config: UInputDeviceConfig) {}
    override fun createDevice() {}
    override fun writeEvents(events: List<InputEventRecord>) {
        writtenBatches.add(events.toList())
    }
    override fun destroyDevice() {
        deviceDestroyed = true
    }
    override fun close() {
        closed = true
    }
}

internal class LocalRecordingWindowsDriver : WindowsVirtualGamepadDriver {
    var closed = false
    val submittedReports = mutableListOf<XusbReportData>()

    override fun open(vendorId: Int?, productId: Int?) {}
    override fun submitReport(report: XusbReportData) {
        submittedReports.add(report)
    }
    override fun close() {
        closed = true
    }
}

class PlatformBackendEndToEndUdpTest {

    private val silentLogger = StructuredLogger(minLevel = LogLevel.ERROR, out = PrintStream(ByteArrayOutputStream()))

    @Test
    fun `UDP watchdog timeout propagates SafeNeutral to Linux UInputGamepadBackend cleanly`() {
        val writer = LocalRecordingInputEventWriter()
        val backend = UInputGamepadBackend(
            deviceConfig = UInputDeviceConfig(),
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
            shiftPulseDurationMs = 15L,
            logger = silentLogger
        )
        val freePort = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val companion = DrivePadCompanionApp(
            config = CompanionConfig(
                udpPort = freePort,
                networkInterface = "127.0.0.1",
                watchdogTimeoutMs = 180L,
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
                val rx = ByteArray(64)
                client.receive(DatagramPacket(rx, rx.size))

                val active = buildWire(
                    type = PacketMessageType.CONTROLLER_STATE,
                    seq = 2,
                    steering = -1.0f,
                    throttle = 1.0f,
                    handbrake = true,
                    flags = VehicleButtonFlag.LIGHTS or VehicleButtonFlag.CRUISE_CONTROL
                )
                client.send(DatagramPacket(active, active.size, endpoint))

                val deadline1 = System.currentTimeMillis() + 2000L
                while (System.currentTimeMillis() < deadline1 && writer.writtenBatches.isEmpty()) {
                    Thread.sleep(10)
                }
                assertTrue(backend.awaitIdle())
                assertTrue(writer.writtenBatches.isNotEmpty())

                val deadline2 = System.currentTimeMillis() + 2000L
                while (System.currentTimeMillis() < deadline2 &&
                    companion.connectionMonitor.snapshot.value.status != CompanionConnectionStatus.FAILSAFE_TIMEOUT
                ) {
                    Thread.sleep(15)
                }
                assertEquals(CompanionConnectionStatus.FAILSAFE_TIMEOUT, companion.connectionMonitor.snapshot.value.status)
                assertTrue(backend.awaitIdle())

                val lastBatch = writer.writtenBatches.last()
                assertTrue(lastBatch.any { it == InputEventRecord(LinuxInputConstants.EV_ABS, LinuxInputConstants.ABS_X, 0) })
                assertTrue(lastBatch.any { it == InputEventRecord(LinuxInputConstants.EV_ABS, LinuxInputConstants.ABS_Z, 0) })
                assertTrue(lastBatch.any { it == InputEventRecord(LinuxInputConstants.EV_KEY, LinuxInputConstants.BTN_EAST, 0) })
                assertTrue(lastBatch.any { it == InputEventRecord(LinuxInputConstants.EV_KEY, LinuxInputConstants.BTN_NORTH, 0) })
            }
        } finally {
            companion.stop()
        }
        assertTrue(writer.deviceDestroyed)
        assertTrue(writer.closed)
    }

    @Test
    fun `UDP watchdog timeout propagates SafeNeutral to Windows ViGEmX360GamepadBackend cleanly`() {
        val driver = LocalRecordingWindowsDriver()
        val ets2 = ProfilePresets.ets2()
        val backend = ViGEmX360GamepadBackend(
            driverFactory = { driver },
            availabilityProbe = {
                WindowsAvailability(
                    supportedOs = true,
                    osDescription = "Windows 11 (amd64)",
                    clientLibraryFound = true,
                    clientLibraryPath = "ViGEmClient.dll",
                    summary = "Windows detected (ViGEmClient.dll found)"
                )
            },
            shiftPulseDurationMs = 15L,
            profile = ets2,
            logger = silentLogger
        )
        val freePort = DatagramSocket(0, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val companion = DrivePadCompanionApp(
            config = CompanionConfig(
                udpPort = freePort,
                networkInterface = "127.0.0.1",
                watchdogTimeoutMs = 180L,
                logLevel = LogLevel.ERROR,
                diagnosticsMode = DiagnosticsMode.SILENT,
                backendType = GamepadBackendType.VIGEM_X360,
                activeProfileId = "ets2"
            ),
            profile = ets2,
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
                val rx = ByteArray(64)
                client.receive(DatagramPacket(rx, rx.size))

                val active = buildWire(
                    type = PacketMessageType.CONTROLLER_STATE,
                    seq = 2,
                    steering = 1.0f,
                    throttle = 1.0f,
                    flags = VehicleButtonFlag.HORN
                )
                client.send(DatagramPacket(active, active.size, endpoint))

                val deadline1 = System.currentTimeMillis() + 2000L
                while (System.currentTimeMillis() < deadline1 && driver.submittedReports.size < 2) {
                    Thread.sleep(10)
                }
                assertTrue(backend.awaitIdle())
                assertTrue(
                    driver.submittedReports.any {
                        it.thumbLX == 32767 &&
                            it.rightTrigger == 255 &&
                            (it.buttonsMask and ViGEmConstants.XUSB_GAMEPAD_A) != 0
                    }
                )

                val deadline2 = System.currentTimeMillis() + 2000L
                while (System.currentTimeMillis() < deadline2 &&
                    companion.connectionMonitor.snapshot.value.status != CompanionConnectionStatus.FAILSAFE_TIMEOUT
                ) {
                    Thread.sleep(15)
                }
                assertEquals(CompanionConnectionStatus.FAILSAFE_TIMEOUT, companion.connectionMonitor.snapshot.value.status)
                assertTrue(backend.awaitIdle())
                assertEquals(XusbReportData.NEUTRAL, driver.submittedReports.last())
            }
        } finally {
            companion.stop()
        }
        assertTrue(driver.closed)
    }

    private fun buildWire(
        type: PacketMessageType,
        seq: Int,
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
