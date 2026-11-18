package com.drivepad.companion.diagnostics

import com.drivepad.companion.config.LogLevel
import java.io.PrintStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

interface MonotonicClock {
    fun nanoTime(): Long
}

object SystemMonotonicClock : MonotonicClock {
    override fun nanoTime(): Long = System.nanoTime()
}

class StructuredLogger(
    private val minLevel: LogLevel = LogLevel.INFO,
    private val out: PrintStream = System.err,
    var enabled: Boolean = true
) {
    private val formatter = DateTimeFormatter.ISO_INSTANT

    fun error(component: String, message: String, vararg fields: Pair<String, Any?>) {
        log(LogLevel.ERROR, component, message, *fields)
    }

    fun warn(component: String, message: String, vararg fields: Pair<String, Any?>) {
        log(LogLevel.WARN, component, message, *fields)
    }

    fun info(component: String, message: String, vararg fields: Pair<String, Any?>) {
        log(LogLevel.INFO, component, message, *fields)
    }

    fun debug(component: String, message: String, vararg fields: Pair<String, Any?>) {
        log(LogLevel.DEBUG, component, message, *fields)
    }

    fun log(level: LogLevel, component: String, message: String, vararg fields: Pair<String, Any?>) {
        if (!enabled || level.priority > minLevel.priority) return
        val ts = formatter.format(Instant.now().atOffset(ZoneOffset.UTC))
        val line = buildString(128) {
            append(ts)
            append(" | ")
            append(level.name.padEnd(5))
            append(" | ")
            append(component)
            append(" | ")
            append(message)
            if (fields.isNotEmpty()) {
                append(" | ")
                fields.forEachIndexed { index, (k, v) ->
                    if (index > 0) append(' ')
                    append(k)
                    append('=')
                    append(v)
                }
            }
        }
        synchronized(out) {
            out.println(line)
        }
    }
}
