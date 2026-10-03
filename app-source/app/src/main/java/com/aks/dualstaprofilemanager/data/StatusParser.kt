package com.aks.dualstaprofilemanager.data

data class WlanLinkStatus(
    val isConnected: Boolean,
    val ssid: String?,
    val bssid: String?,
    val frequencyMhz: Int?,
    val signalDbm: Int?,
    val rxBitrate: String?,
    val txBitrate: String?
) {
    val channel: Int?
        get() = frequencyMhz?.let { freq ->
            when (freq) {
                2484 -> 14
                in 2412..2472 -> (freq - 2407) / 5
                in 5000..5900 -> (freq - 5000) / 5
                in 5935..7125 -> if (freq == 5935) 2 else ((freq - 5950) / 5).coerceAtLeast(1)
                else -> null
            }
        }

    val bandLabel: String?
        get() = frequencyMhz?.let { freq ->
            when (freq) {
                in 2400..2500 -> "2.4 GHz"
                in 4900..5925 -> "5 GHz"
                in 5925..7125 -> "6 GHz"
                else -> null
            }
        }

    val bandAndChannelLabel: String
        get() {
            val band = bandLabel ?: (frequencyMhz?.let { "$it MHz" } ?: "")
            val ch = channel?.let { "CH $it" }
            return when {
                band.isNotEmpty() && ch != null -> "$band $ch"
                band.isNotEmpty() -> band
                ch != null -> ch
                else -> ""
            }
        }

    val wifiGeneration: String
        get() {
            val combined = "${rxBitrate ?: ""} ${txBitrate ?: ""}"
            return ScanParser.determineWifiGeneration(
                wifiStandard = null,
                capabilities = null,
                frequencyMhz = frequencyMhz,
                bitrateInfo = combined
            )
        }
}

object StatusParser {
    fun parseIwLink(output: String): WlanLinkStatus {
        if (output.contains("Not connected", ignoreCase = true) || output.isBlank()) {
            return WlanLinkStatus(false, null, null, null, null, null, null)
        }

        var ssid: String? = null
        var bssid: String? = null
        var freq: Int? = null
        var signal: Int? = null
        var rxBitrate: String? = null
        var txBitrate: String? = null

        for (line in output.lines()) {
            val trimmed = line.trim()
            val lower = trimmed.lowercase()
            when {
                lower.startsWith("ssid:") -> {
                    ssid = trimmed.substringAfter(":").trim()
                }
                lower.startsWith("connected to") -> {
                    val parts = trimmed.split(Regex("\\s+"))
                    if (parts.size >= 3) {
                        bssid = parts[2].lowercase()
                    }
                }
                lower.startsWith("freq:") -> {
                    val freqStr = trimmed.substringAfter(":").trim()
                    freq = freqStr.toIntOrNull() ?: freqStr.toFloatOrNull()?.toInt()
                }
                lower.startsWith("signal:") -> {
                    val sigPart = trimmed.substringAfter(":").trim().replace("dBm", "", ignoreCase = true).trim().toIntOrNull()
                    signal = sigPart
                }
                lower.startsWith("rx bitrate:") -> {
                    rxBitrate = trimmed.substringAfter(":").trim()
                }
                lower.startsWith("tx bitrate:") -> {
                    txBitrate = trimmed.substringAfter(":").trim()
                }
                lower.startsWith("bitrate:") && txBitrate == null -> {
                    txBitrate = trimmed.substringAfter(":").trim()
                }
            }
        }

        return WlanLinkStatus(
            isConnected = bssid != null || ssid != null,
            ssid = ssid,
            bssid = bssid,
            frequencyMhz = freq,
            signalDbm = signal,
            rxBitrate = rxBitrate,
            txBitrate = txBitrate
        )
    }

    fun sanitizeLogLine(line: String): String {
        return line.replace(Regex("(?i)(pass(word)?[:=]\\s*)(\\S+)"), "$1[REDACTED]")
    }
}
