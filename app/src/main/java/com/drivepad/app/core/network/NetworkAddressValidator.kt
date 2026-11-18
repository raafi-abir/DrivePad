package com.drivepad.app.core.network

object NetworkAddressValidator {
    private val IPV4_REGEX = Regex("""^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""")

    fun isValidTargetIpv4(ip: String): Boolean {
        val trimmed = ip.trim()
        val match = IPV4_REGEX.matchEntire(trimmed) ?: return false
        val octets = match.groupValues.drop(1).map { part ->
            if (part.length > 1 && part.startsWith("0")) return false
            part.toIntOrNull() ?: return false
        }
        if (octets.any { it !in 0..255 }) return false
        // 0.0.0.0 is an unspecified bind address, not a valid UDP destination host
        if (octets.all { it == 0 }) return false
        return true
    }

    fun isValidPort(port: Int): Boolean = port in 1..65535

    fun isValidFrequencyHz(hz: Int): Boolean = hz in 20..500
}
