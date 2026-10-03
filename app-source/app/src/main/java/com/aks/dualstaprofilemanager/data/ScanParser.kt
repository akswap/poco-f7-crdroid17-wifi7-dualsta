package com.aks.dualstaprofilemanager.data

import android.net.wifi.ScanResult
import android.os.Build

enum class SecurityClassification(val label: String, val isSupported: Boolean) {
    WPA3("WPA3", true),
    WPA2("WPA2", true),
    WPA2_WPA3("WPA2/WPA3 Transition", true),
    OPEN("OPEN", true),
    ENTERPRISE("Enterprise (802.1X)", false),
    OWE("OWE", false),
    UNKNOWN("Unknown", false)
}

data class DiscoveredAp(
    val ssid: String,
    val bssid: String,
    val frequencyMhz: Int,
    val signalDbm: Int,
    val capabilities: String,
    val securityClassification: SecurityClassification,
    val wifiStandard: Int? = null,
    val isEht: Boolean = false,
    val isMlo: Boolean = false,
    val isHe: Boolean = false,
    val isVht: Boolean = false,
    val isCurrentPrimary: Boolean = false,
    val isCurrentSecondary: Boolean = false
) {
    val channel: Int?
        get() = when (frequencyMhz) {
            2484 -> 14
            in 2412..2472 -> (frequencyMhz - 2407) / 5
            in 5000..5900 -> (frequencyMhz - 5000) / 5
            in 5935..7125 -> if (frequencyMhz == 5935) 2 else ((frequencyMhz - 5950) / 5).coerceAtLeast(1)
            else -> null
        }

    val bandLabel: String
        get() = when (frequencyMhz) {
            in 2400..2500 -> "2.4 GHz"
            in 4900..5925 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            else -> "Unknown"
        }

    val bandAndChannelLabel: String
        get() {
            val ch = channel?.let { "CH $it" }
            return if (ch != null) "$bandLabel $ch" else bandLabel
        }

    val wifiGeneration: String
        get() = ScanParser.determineWifiGeneration(
            isEht = isEht,
            isMlo = isMlo,
            isHe = isHe,
            isVht = isVht,
            wifiStandard = wifiStandard,
            capabilities = capabilities,
            frequencyMhz = frequencyMhz
        )

    val isHidden: Boolean
        get() = ssid.isEmpty() || ssid.equals("<unknown ssid>", ignoreCase = true)
}

data class ApStandardInfo(
    val isEht: Boolean,
    val isMlo: Boolean,
    val isHe: Boolean,
    val isVht: Boolean
)

object ScanParser {
    fun classifySecurity(capabilities: String): SecurityClassification {
        val caps = capabilities.uppercase()
        return when {
            caps.contains("EAP") || caps.contains("8021X") -> SecurityClassification.ENTERPRISE
            caps.contains("OWE") -> SecurityClassification.OWE
            caps.contains("WPA3") && caps.contains("WPA2") -> SecurityClassification.WPA2_WPA3
            caps.contains("WPA3") || caps.contains("SAE") || caps.contains("SUITE_B") -> SecurityClassification.WPA3
            caps.contains("WPA2") || caps.contains("PSK") -> SecurityClassification.WPA2
            caps.contains("WEP") -> SecurityClassification.UNKNOWN
            caps.isEmpty() || caps == "[ESS]" || (!caps.contains("WPA") && !caps.contains("WEP")) -> SecurityClassification.OPEN
            else -> SecurityClassification.UNKNOWN
        }
    }

    fun inspectScanResult(result: ScanResult): ApStandardInfo {
        var isEht = false
        var isMlo = false
        var isHe = false
        var isVht = false

        val caps = (result.capabilities ?: "").uppercase()

        // 1. Check direct standard constant if available (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (result.wifiStandard) {
                8 -> isEht = true // WIFI_STANDARD_11BE (Wi-Fi 7)
                6 -> isHe = true  // WIFI_STANDARD_11AX (Wi-Fi 6 / 6E)
                5 -> isVht = true // WIFI_STANDARD_11AC (Wi-Fi 5)
            }
        }

        // 2. Check Channel Width (320 MHz width is exclusively Wi-Fi 7)
        if (result.channelWidth >= 5) {
            isEht = true
        }

        // 3. Inspect raw 802.11 Information Elements (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val ies = runCatching { result.informationElements }.getOrNull()
            if (ies != null) {
                for (ie in ies) {
                    val id = runCatching { ie.id }.getOrDefault(-1)
                    val idExt = runCatching { ie.idExt }.getOrDefault(-1)

                    // Element ID 255 = Extension Element in IEEE 802.11
                    if (id == 255) {
                        when (idExt) {
                            106, 107 -> isEht = true // EHT Capabilities / EHT Operation (Wi-Fi 7)
                            108 -> { isEht = true; isMlo = true } // Multi-Link Element (Wi-Fi 7+ MLO)
                            35, 36, 37 -> isHe = true // HE Capabilities / HE Operation (Wi-Fi 6 / 6E)
                        }
                    } else if (id == 191 || id == 192) {
                        isVht = true // VHT Capabilities / VHT Operation (Wi-Fi 5)
                    }
                }
            }
        }

        // 4. Check capabilities string keywords
        if (caps.contains("EHT") || caps.contains("11BE") || caps.contains("BE")) isEht = true
        if (caps.contains("MLO") || caps.contains("MULTI-LINK")) { isEht = true; isMlo = true }
        if (caps.contains("HE") || caps.contains("11AX") || caps.contains("AX")) isHe = true
        if (caps.contains("VHT") || caps.contains("11AC") || caps.contains("AC")) isVht = true

        return ApStandardInfo(isEht = isEht, isMlo = isMlo, isHe = isHe, isVht = isVht)
    }

    fun determineWifiGeneration(
        isEht: Boolean = false,
        isMlo: Boolean = false,
        isHe: Boolean = false,
        isVht: Boolean = false,
        wifiStandard: Int? = null,
        capabilities: String? = null,
        frequencyMhz: Int? = null,
        bitrateInfo: String? = null
    ): String {
        val caps = (capabilities ?: "").uppercase()
        val bitrates = (bitrateInfo ?: "").uppercase()
        val freq = frequencyMhz ?: 0

        // Qualcomm's secondary-STA link formatter can print "HE-MCS 12/13"
        // even though those MCS indexes only exist in EHT (802.11be). Treat
        // that impossible HE combination as Wi-Fi 7 while preserving the raw
        // driver text shown for RX/TX rates.
        val mislabeledEhtMcs = Regex("""\bHE-MCS\s+1[23]\b""").containsMatchIn(bitrates)

        // 1. Wi-Fi 7+ (MLO - Multi-Link Operation)
        if (isMlo || caps.contains("MLO") || caps.contains("MULTI-LINK") || bitrates.contains("MLO") || bitrates.contains("EHT-MLO")) {
            return "Wi-Fi 7+"
        }

        // 2. Wi-Fi 7 (802.11be / EHT / 320MHz)
        if (isEht || mislabeledEhtMcs || wifiStandard == 8 || caps.contains("EHT") || caps.contains("11BE") || bitrates.contains("EHT") || bitrates.contains("11BE") || bitrates.contains("320MHZ")) {
            return "Wi-Fi 7"
        }

        // 3. Wi-Fi 6E / Wi-Fi 6 (802.11ax / HE)
        if (isHe || wifiStandard == 6 || caps.contains("HE") || caps.contains("11AX") || bitrates.contains("HE") || bitrates.contains("11AX")) {
            return if (freq in 5925..7125) "Wi-Fi 6E" else "Wi-Fi 6"
        }

        // 4. Wi-Fi 5 (802.11ac / VHT)
        if (isVht || wifiStandard == 5 || caps.contains("VHT") || caps.contains("11AC") || bitrates.contains("VHT") || bitrates.contains("11AC")) {
            return "Wi-Fi 5"
        }

        // 5. Wi-Fi 4 (802.11n / HT)
        if (wifiStandard == 4 || caps.contains("HT") || caps.contains("11N") || bitrates.contains("HT") || bitrates.contains("11N")) {
            return "Wi-Fi 4"
        }

        // 6. 6 GHz frequency band fallback:
        // If bitrate or element indicates EHT/320MHz -> Wi-Fi 7, otherwise 6E
        if (freq in 5925..7125) {
            return if (bitrates.contains("EHT") || bitrates.contains("320")) "Wi-Fi 7" else "Wi-Fi 6E"
        }

        // 7. Frequency-based general fallback
        return when {
            freq in 4900..5925 -> "Wi-Fi 5"
            freq in 2400..2500 -> "Wi-Fi 4"
            else -> "Wi-Fi 4"
        }
    }
}
