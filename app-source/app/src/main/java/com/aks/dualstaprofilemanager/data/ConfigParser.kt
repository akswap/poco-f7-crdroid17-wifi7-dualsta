package com.aks.dualstaprofilemanager.data

data class WifiProfile(
    val priority: Int,
    val enabled: Boolean,
    val ssid: String,
    val bssid: String,
    val frequencyMhz: Int,
    val security: String,
    val passphrase: String
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
            wifiStandard = null,
            capabilities = security,
            frequencyMhz = frequencyMhz
        )
}

object ConfigParser {
    const val COMMENT_HEADER = "# priority\tenabled\tssid\tbssid\tfrequency_mhz\tsecurity\tpassphrase"

    fun parseConfig(rawContent: String): List<WifiProfile> {
        val lines = rawContent.lines()
        val profiles = mutableListOf<WifiProfile>()

        for ((index, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

            val parts = line.split("\t")
            if (parts.size != 7) {
                throw IllegalArgumentException("Malformed config line at ${index + 1}: expected 7 TAB-separated fields, got ${parts.size}")
            }

            val priority = parts[0].toIntOrNull() 
                ?: throw IllegalArgumentException("Invalid priority at line ${index + 1}")
            val enabledInt = parts[1].toIntOrNull() 
                ?: throw IllegalArgumentException("Invalid enabled flag at line ${index + 1}")
            if (enabledInt !in 0..1) throw IllegalArgumentException("Invalid enabled flag at line ${index + 1}")
            val ssid = parts[2]
            val bssid = parts[3].lowercase()
            val freq = parts[4].toIntOrNull() 
                ?: throw IllegalArgumentException("Invalid frequency at line ${index + 1}")
            val security = parts[5]
            val passphrase = parts[6]

            if (!Validators.isValidSsid(ssid)) throw IllegalArgumentException("Invalid SSID at line ${index + 1}")
            if (!Validators.isValidBssid(bssid)) {
                throw IllegalArgumentException("Invalid BSSID '$bssid' at line ${index + 1}")
            }
            if (!Validators.isValidFrequency(freq)) throw IllegalArgumentException("Invalid frequency at line ${index + 1}")
            if (!Validators.isValidSecurityAndPassphrase(security, passphrase)) throw IllegalArgumentException("Invalid security/password at line ${index + 1}")

            profiles.add(
                WifiProfile(
                    priority = priority,
                    enabled = enabledInt == 1,
                    ssid = ssid,
                    bssid = bssid,
                    frequencyMhz = freq,
                    security = security,
                    passphrase = passphrase
                )
            )
        }

        return profiles.sortedBy { it.priority }
    }

    fun serializeConfig(profiles: List<WifiProfile>): String {
        val sorted = profiles.sortedBy { it.priority }
        val sb = StringBuilder()
        sb.append(COMMENT_HEADER).append("\n")
        
        for ((index, p) in sorted.withIndex()) {
            val normalizedPriority = index + 1
            val enabledFlag = if (p.enabled) "1" else "0"
            sb.append("$normalizedPriority\t")
                .append("$enabledFlag\t")
                .append("${p.ssid}\t")
                .append("${p.bssid.lowercase()}\t")
                .append("${p.frequencyMhz}\t")
                .append("${p.security}\t")
                .append("${p.passphrase}\n")
        }
        return sb.toString()
    }

    /**
     * Builds the configuration used by Save & Connect. The selected AP is the
     * only enabled profile so the controller cannot fall through to a stale or
     * currently-primary network. Other saved profiles are retained, disabled.
     */
    fun prepareExclusiveConnectProfiles(
        existingProfiles: List<WifiProfile>,
        selectedProfile: WifiProfile
    ): List<WifiProfile> {
        val normalizedBssid = selectedProfile.bssid.lowercase()
        val previousMatch = existingProfiles.firstOrNull {
            it.bssid.equals(normalizedBssid, ignoreCase = true) ||
                it.ssid.equals(selectedProfile.ssid, ignoreCase = true)
        }
        val selectedPassphrase = selectedProfile.passphrase.ifEmpty {
            previousMatch?.passphrase.orEmpty()
        }
        val retained = existingProfiles.filterNot {
            it.bssid.equals(normalizedBssid, ignoreCase = true) ||
                it.ssid.equals(selectedProfile.ssid, ignoreCase = true)
        }

        return buildList {
            add(
                selectedProfile.copy(
                    priority = 1,
                    enabled = true,
                    bssid = normalizedBssid,
                    passphrase = selectedPassphrase
                )
            )
            retained.forEach { add(it.copy(enabled = false)) }
        }.mapIndexed { index, profile -> profile.copy(priority = index + 1) }
    }
}
