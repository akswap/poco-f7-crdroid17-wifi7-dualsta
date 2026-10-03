package com.aks.dualstaprofilemanager.data

import java.util.regex.Pattern

object Validators {
    private val BSSID_PATTERN = Pattern.compile("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")

    fun isValidSsid(ssid: String): Boolean {
        if (ssid.isEmpty()) return false
        for (element in ssid) {
            val c = element
            if (c == '\t' || c == '\r' || c == '\n' || c == '\u0000') {
                return false
            }
        }
        return true
    }

    fun isValidBssid(bssid: String): Boolean {
        if (!BSSID_PATTERN.matcher(bssid).matches()) return false
        val lower = bssid.lowercase()
        if (lower == "ff:ff:ff:ff:ff:ff" || lower == "00:00:00:00:00:00") return false
        val firstOctet = lower.substring(0, 2).toInt(16)
        if ((firstOctet and 0x01) != 0) return false
        return true
    }

    fun isValidFrequency(freq: Int): Boolean {
        return freq in 2400..7125
    }

    fun isCommonChannelFrequency(freq: Int): Boolean {
        val common24 = listOf(2412, 2417, 2422, 2427, 2432, 2437, 2442, 2447, 2452, 2457, 2462, 2467, 2472)
        val is5g = freq in 5150..5850
        val is6g = freq in 5925..7125
        return common24.contains(freq) || is5g || is6g
    }

    fun isValidSecurityAndPassphrase(security: String, passphrase: String): Boolean {
        return when (security) {
            "OPEN" -> passphrase.isEmpty()
            "WPA2", "WPA3" -> {
                if (passphrase.length < 8) return false
                for (element in passphrase) {
                    val c = element
                    if (c == '\t' || c == '\r' || c == '\n' || c == '\u0000') return false
                }
                true
            }
            else -> false
        }
    }
}
