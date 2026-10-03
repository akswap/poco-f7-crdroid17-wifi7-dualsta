package com.aks.dualstaprofilemanager

import com.aks.dualstaprofilemanager.data.*
import org.junit.Assert.*
import org.junit.Test

class DualStaExtendedTest {

    @Test
    fun testScanSecurityClassification() {
        assertEquals(SecurityClassification.WPA3, ScanParser.classifySecurity("[WPA3-SAE-CCMP][ESS]"))
        assertEquals(SecurityClassification.WPA2, ScanParser.classifySecurity("[WPA2-PSK-CCMP][ESS]"))
        assertEquals(SecurityClassification.WPA2_WPA3, ScanParser.classifySecurity("[WPA2-PSK-CCMP][WPA3-SAE-CCMP][ESS]"))
        assertEquals(SecurityClassification.OPEN, ScanParser.classifySecurity("[ESS]"))
        assertEquals(SecurityClassification.OPEN, ScanParser.classifySecurity(""))
        assertEquals(SecurityClassification.ENTERPRISE, ScanParser.classifySecurity("[WPA2-EAP-CCMP][ESS]"))
        assertEquals(SecurityClassification.OWE, ScanParser.classifySecurity("[OWE][ESS]"))
    }

    @Test
    fun testDuplicateSsidsDifferentBssids() {
        val ap1 = DiscoveredAp("HomeWiFi", "11:22:33:44:55:66", 2412, -50, "[WPA2-PSK-CCMP]", SecurityClassification.WPA2)
        val ap2 = DiscoveredAp("HomeWiFi", "AA:BB:CC:DD:EE:FF", 5180, -60, "[WPA3-SAE-CCMP]", SecurityClassification.WPA3)

        assertNotEquals(ap1.bssid, ap2.bssid)
        assertEquals(ap1.ssid, ap2.ssid)
        assertTrue(ap1.bssid != ap2.bssid)
    }

    @Test
    fun testPreservingExistingProfilesAndOrdering() {
        val initialProfiles = listOf(
            WifiProfile(1, true, "ProfileA", "00:11:22:33:44:55", 2412, "WPA2", "password123"),
            WifiProfile(2, true, "ProfileB", "AA:BB:CC:DD:EE:FF", 5180, "WPA3", "password456")
        )

        val newProfile = WifiProfile(1, true, "ProfileNew", "11:22:33:44:55:66", 6215, "WPA3", "newpassword")
        val combined = mutableListOf(newProfile)
        combined.addAll(initialProfiles)

        val normalized = combined.mapIndexed { index, p -> p.copy(priority = index + 1) }

        assertEquals(3, normalized.size)
        assertEquals(1, normalized[0].priority)
        assertEquals("ProfileNew", normalized[0].ssid)
        assertEquals(2, normalized[1].priority)
        assertEquals("ProfileA", normalized[1].ssid)
        assertEquals(3, normalized[2].priority)
        assertEquals("ProfileB", normalized[2].ssid)
    }

    @Test
    fun testSaveAndConnectEnablesOnlySelectedProfile() {
        val existing = listOf(
            WifiProfile(1, true, "Old24", "00:11:22:33:44:55", 2412, "WPA2", "oldpassword"),
            WifiProfile(2, true, "Old6", "66:77:88:99:aa:bb", 6295, "WPA3", "oldpassword2")
        )
        val selected = WifiProfile(9, true, "Office5", "AA:BB:CC:DD:EE:FF", 5200, "WPA2", "newpassword")

        val result = ConfigParser.prepareExclusiveConnectProfiles(existing, selected)

        assertEquals(3, result.size)
        assertEquals("Office5", result.first().ssid)
        assertEquals("aa:bb:cc:dd:ee:ff", result.first().bssid)
        assertTrue(result.first().enabled)
        assertTrue(result.drop(1).all { !it.enabled })
        assertEquals(listOf(1, 2, 3), result.map { it.priority })
    }

    @Test
    fun testSaveAndConnectReplacesMatchingProfileAndPreservesPassword() {
        val existing = listOf(
            WifiProfile(1, true, "Office5", "aa:bb:cc:dd:ee:ff", 5180, "WPA2", "savedpassword"),
            WifiProfile(2, true, "Backup", "00:11:22:33:44:55", 2412, "WPA2", "backuppassword")
        )
        val selected = WifiProfile(1, true, "Office5", "AA:BB:CC:DD:EE:FF", 5200, "WPA2", "")

        val result = ConfigParser.prepareExclusiveConnectProfiles(existing, selected)

        assertEquals(2, result.size)
        assertEquals("savedpassword", result.first().passphrase)
        assertEquals(5200, result.first().frequencyMhz)
        assertTrue(result.first().enabled)
        assertFalse(result.last().enabled)
    }

    @Test
    fun testOpenProfileEmptyPassword() {
        assertTrue(Validators.isValidSecurityAndPassphrase("OPEN", ""))
        assertFalse(Validators.isValidSecurityAndPassphrase("OPEN", "notempty"))
    }

    @Test
    fun testDecimalIwFrequencyParsing() {
        val iwOutputNormal = "freq: 6215\nsignal: -55 dBm"
        val iwOutputDecimal = "freq: 6215.0\nsignal: -55 dBm"

        val statusNormal = StatusParser.parseIwLink(iwOutputNormal)
        val statusDecimal = StatusParser.parseIwLink(iwOutputDecimal)

        assertEquals(6215, statusNormal.frequencyMhz)
        assertEquals(6215, statusDecimal.frequencyMhz)
    }

    @Test
    fun testSelectedProfileConnectionVerification() {
        val selectedBssid = "02:11:22:33:44:55"
        val selectedFreq = 6215

        val linkOutput = "Connected to 02:11:22:33:44:55 (on wlan1)\nfreq: 6215.0\nSSID: Test6G"
        val parsed = StatusParser.parseIwLink(linkOutput)

        val bssidMatch = parsed.bssid?.equals(selectedBssid, ignoreCase = true) == true
        val freqMatch = parsed.frequencyMhz == selectedFreq

        assertTrue(parsed.isConnected)
        assertTrue(bssidMatch)
        assertTrue(freqMatch)
    }

    @Test
    fun testQualcommSecondaryHeMcs13IsDetectedAsWifi7() {
        val linkOutput = """
            Connected to a8:6e:84:e3:5d:f3 (on wlan1)
                SSID: TP-Link_5G_be
                freq: 5640.0
                rx bitrate: 2882.3 MBit/s 160MHz HE-MCS 13 HE-NSS 2 HE-GI 0 HE-DCM 0
                tx bitrate: 2882.3 MBit/s 160MHz HE-MCS 13 HE-NSS 2 HE-GI 0 HE-DCM 0
        """.trimIndent()

        val parsed = StatusParser.parseIwLink(linkOutput)

        assertEquals("Wi-Fi 7", parsed.wifiGeneration)
        assertEquals("2882.3 MBit/s 160MHz HE-MCS 13 HE-NSS 2 HE-GI 0 HE-DCM 0", parsed.rxBitrate)
        assertEquals("2882.3 MBit/s 160MHz HE-MCS 13 HE-NSS 2 HE-GI 0 HE-DCM 0", parsed.txBitrate)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMalformedConfigurationRejection() {
        val malformedConfig = "# priority\tenabled\tssid\n1\t1\tIncompleteFields"
        ConfigParser.parseConfig(malformedConfig)
    }
}
