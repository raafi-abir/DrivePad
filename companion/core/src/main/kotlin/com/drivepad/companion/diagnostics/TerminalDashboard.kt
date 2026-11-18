package com.drivepad.companion.diagnostics

import com.drivepad.companion.config.DiagnosticsMode
import com.drivepad.companion.domain.BackendDiagnosticsSnapshot
import com.drivepad.companion.domain.ControllerState
import com.drivepad.companion.domain.ControllerStateProvider
import com.drivepad.companion.monitoring.ConnectionMonitor
import com.drivepad.companion.monitoring.ConnectionStatusSnapshot
import com.drivepad.companion.monitoring.LatencyMonitor
import com.drivepad.companion.monitoring.LatencySnapshot
import com.drivepad.companion.monitoring.PacketStatistics
import com.drivepad.companion.monitoring.PacketStatsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.PrintStream
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Lightweight terminal diagnostics renderer. Runs on a separate low-frequency timer
 * (100 ms in dashboard mode, 1000 ms in non-interactive logging mode) so terminal output
 * never blocks the 250 Hz UDP receiver or backend writer threads.
 */
class TerminalDashboard(
    private val mode: DiagnosticsMode,
    private val listeningEndpoint: String,
    private val stateProvider: ControllerStateProvider,
    private val connectionMonitor: ConnectionMonitor,
    private val packetStatistics: PacketStatistics,
    private val latencyMonitor: LatencyMonitor,
    private val backendDiagnosticsProvider: () -> BackendDiagnosticsSnapshot? = { null },
    private val clock: MonotonicClock = SystemMonotonicClock,
    private val out: PrintStream = System.out,
    private val logger: StructuredLogger = StructuredLogger()
) {
    private var renderJob: Job? = null

    fun start(scope: CoroutineScope): Job? {
        if (mode == DiagnosticsMode.SILENT) return null
        renderJob?.cancel()

        val job = scope.launch(Dispatchers.Default) {
            val intervalMs = if (mode == DiagnosticsMode.DASHBOARD) 100L else 1_000L
            while (isActive) {
                val nowNs = clock.nanoTime()
                val conn = connectionMonitor.snapshot.value
                val ctrl = stateProvider.controllerState.value
                val stats = packetStatistics.snapshot()
                val latency = latencyMonitor.snapshot(nowNs)
                val backend = backendDiagnosticsProvider()

                when (mode) {
                    DiagnosticsMode.DASHBOARD -> {
                        val frame = formatDashboardFrame(listeningEndpoint, conn, ctrl, stats, latency, backend)
                        out.print("\u001B[2J\u001B[H")
                        out.print(frame)
                        out.flush()
                    }
                    DiagnosticsMode.LOGGING -> {
                        logger.info(
                            "Telemetry",
                            "Companion heartbeat",
                            "drivepad_version" to ReleaseMetadata.drivepadVersion,
                            "companion_version" to ReleaseMetadata.companionVersion,
                            "protocol_version" to ReleaseMetadata.protocolVersion,
                            "status" to conn.status.label,
                            "client" to (conn.clientIp?.let { "$it:${conn.clientPort}" } ?: "none"),
                            "rx" to stats.totalPacketsReceived,
                            "invalid" to stats.invalidPackets,
                            "loss_est_pct" to String.format(Locale.US, "%.2f", stats.estimatedPacketLossPercent),
                            "hz" to String.format(Locale.US, "%.1f", latency.actualUpdateHz),
                            "jitter_ms" to String.format(Locale.US, "%.2f", latency.interArrivalJitterMs),
                            "watchdog" to conn.watchdogStatus.label,
                            "failsafe_count" to conn.failsafeActivations,
                            "host" to (backend?.hostPlatform?.displayName ?: "unknown"),
                            "backend" to (backend?.backendType?.cliName ?: "none"),
                            "backend_ready" to (backend?.driverOrDeviceReady ?: false),
                            "profile" to (backend?.activeProfileId ?: "generic"),
                            "virtual_gamepad" to (if (backend?.isVirtualDeviceActive == true) "active" else "inactive"),
                            "backend_err" to (backend?.lastError ?: "none")
                        )
                    }
                    DiagnosticsMode.SILENT -> Unit
                }
                delay(intervalMs)
            }
        }
        renderJob = job
        return job
    }

    fun stop() {
        renderJob?.cancel()
        renderJob = null
    }

    companion object {
        fun formatDashboardFrame(
            listeningEndpoint: String,
            conn: ConnectionStatusSnapshot,
            ctrl: ControllerState,
            stats: PacketStatsSnapshot,
            latency: LatencySnapshot,
            backend: BackendDiagnosticsSnapshot? = null
        ): String {
            val gearStr = when (ctrl.currentGear) {
                -2 -> "P"
                -1 -> "R"
                0 -> "N"
                else -> ctrl.currentGear.toString()
            }
            val steerBar = formatSteeringBar(ctrl.steering)
            val throttleStr = if (ctrl.throttle >= 1.0f) "1.00 (100%)" else "0.00 (0%)"
            val brakeStr = if (ctrl.brake >= 1.0f) "1.00 (100%)" else "0.00 (0%)"
            val flags = formatActiveFlags(ctrl)
            val lastRxStr = latency.lastPacketAgeMs?.let { "${it} ms ago" } ?: "never"
            val rttStr = latency.rttMs?.let { String.format(Locale.US, "%.2f ms", it) }
                ?: latency.rttNote

            return buildString {
                appendLine("================================================================")
                appendLine(
                    "  DRIVEPAD COMPANION v${ReleaseMetadata.companionVersion}  |  Protocol v${ReleaseMetadata.protocolVersion}  |  $listeningEndpoint"
                )
                appendLine(
                    "  DrivePad Release : v${ReleaseMetadata.drivepadVersion}"
                )
                appendLine("================================================================")
                appendLine("Connection:")
                appendLine("  Status           : ${conn.status.label}")
                appendLine("  Client IP        : ${conn.clientIp ?: "—"}")
                appendLine("  Client UDP Port  : ${conn.clientPort?.toString() ?: "—"}")
                appendLine("  Protocol Version : ${conn.protocolVersion?.let { "v$it (33B Little-Endian)" } ?: "—"}")
                appendLine()
                appendLine("Controller:")
                appendLine(String.format(Locale.US, "  Steering         : %s %+6.3f", steerBar, ctrl.steering))
                appendLine("  Throttle (Digital): $throttleStr")
                appendLine("  Brake (Digital)  : $brakeStr")
                appendLine("  Handbrake        : ${if (ctrl.handbrake) "ENGAGED" else "RELEASED"}")
                appendLine("  Gear             : $gearStr (${ctrl.transmissionMode.displayName})")
                appendLine("  Cabin Functions  : $flags")
                appendLine()
                appendLine("Network:")
                appendLine("  Packets Received : ${stats.totalPacketsReceived} (Valid: ${stats.validPackets})")
                appendLine("  Invalid Packets  : ${stats.invalidPackets}")
                appendLine(
                    String.format(
                        Locale.US,
                        "  Packet Loss Est. : %.2f%% (Gaps: %d | Dup: %d | OOO: %d)",
                        stats.estimatedPacketLossPercent,
                        stats.sequenceGaps,
                        stats.duplicatePackets,
                        stats.outOfOrderPackets
                    )
                )
                appendLine(
                    String.format(
                        Locale.US,
                        "  Update Frequency : %.1f Hz (Avg interval: %.2f ms)",
                        latency.actualUpdateHz,
                        latency.averageIntervalMs
                    )
                )
                appendLine("  RTT              : $rttStr")
                appendLine(String.format(Locale.US, "  Inter-Arrival Jit: %.2f ms", latency.interArrivalJitterMs))
                appendLine()
                appendLine("Watchdog:")
                appendLine("  Status           : ${conn.watchdogStatus.label}")
                appendLine("  Last Packet Rx   : $lastRxStr")
                appendLine("  Failsafe Triggers: ${conn.failsafeActivations}")
                if (backend != null) {
                    appendLine()
                    appendLine("Virtual Gamepad Backend:")
                    appendLine("  Host Platform    : ${backend.hostPlatform.displayName}")
                    appendLine("  Selected Backend : ${backend.backendType.cliName} (${backend.backendName})")
                    if (!backend.activeProfileSummary.isNullOrBlank()) {
                        appendLine("  Active Profile   : ${backend.activeProfileSummary}")
                    } else if (!backend.activeProfileId.isNullOrBlank()) {
                        appendLine("  Active Profile   : ${backend.activeProfileId}")
                    }
                    appendLine("  Driver / Device  : ${backend.availabilitySummary} [Ready: ${if (backend.driverOrDeviceReady) "YES" else "NO"}]")
                    appendLine("  Device Status    : ${backend.statusLabel}")
                    appendLine("  Virtual Device   : ${if (backend.isVirtualDeviceActive) "ACTIVE" else "INACTIVE"}")
                    appendLine("  Device Name      : ${backend.deviceName ?: "—"}")
                    if (!backend.lastError.isNullOrBlank()) {
                        appendLine("  Last Error       : ${backend.lastError}")
                    }
                }
                appendLine("================================================================")
                appendLine("Press Ctrl+C to shut down gracefully.")
            }
        }

        private fun formatSteeringBar(steering: Float): String {
            val width = 17
            val center = width / 2
            val pos = ((steering + 1.0f) * 0.5f * (width - 1)).roundToInt().coerceIn(0, width - 1)
            return buildString {
                append('[')
                for (i in 0 until width) {
                    when {
                        i == pos -> append('#')
                        i == center -> append('|')
                        else -> append('-')
                    }
                }
                append(']')
            }
        }

        private fun formatActiveFlags(ctrl: ControllerState): String {
            val active = mutableListOf<String>()
            if (ctrl.lights) active.add("LIGHTS")
            if (ctrl.horn) active.add("HORN")
            if (ctrl.wipers) active.add("WIPERS")
            if (ctrl.hazardLights) active.add("HAZARD")
            if (ctrl.leftIndicator) active.add("LEFT_IND")
            if (ctrl.rightIndicator) active.add("RIGHT_IND")
            if (ctrl.cruiseControl) active.add("CRUISE")
            if (ctrl.engine) active.add("ENGINE")
            return if (active.isEmpty()) "NONE" else active.joinToString(", ")
        }
    }
}
