package com.aks.dualstaprofilemanager

import com.aks.dualstaprofilemanager.data.Validators
import org.junit.Assert.*
import org.junit.Test

class ValidatorsTest {

    @Test
    fun testSsidValidation() {
        assertTrue(Validators.isValidSsid("Example_6G"))
        assertTrue(Validators.isValidSsid("My Home Wi-Fi 5G"))
        assertFalse(Validators.isValidSsid(""))
        assertFalse(Validators.isValidSsid("SSID\tTAB"))
        assertFalse(Validators.isValidSsid("SSID\nNEWLINE"))
    }

    @Test
    fun testBssidValidation() {
        assertTrue(Validators.isValidBssid("02:11:22:33:44:55"))
        assertTrue(Validators.isValidBssid("AA:BB:CC:DD:EE:FF"))
        assertFalse(Validators.isValidBssid("ff:ff:ff:ff:ff:ff"))
        assertFalse(Validators.isValidBssid("00:00:00:00:00:00"))
        assertFalse(Validators.isValidBssid("01:80:C2:00:00:01"))
        assertFalse(Validators.isValidBssid("invalid-mac"))
    }

    @Test
    fun testFrequencyValidation() {
        assertTrue(Validators.isValidFrequency(2412))
        assertTrue(Validators.isValidFrequency(5180))
        assertTrue(Validators.isValidFrequency(6215))
        assertFalse(Validators.isValidFrequency(2300))
        assertFalse(Validators.isValidFrequency(8000))
    }

    @Test
    fun testSecurityAndPassphrase() {
        assertTrue(Validators.isValidSecurityAndPassphrase("OPEN", ""))
        assertFalse(Validators.isValidSecurityAndPassphrase("OPEN", "secret"))
        assertTrue(Validators.isValidSecurityAndPassphrase("WPA2", "testpass123"))
        assertTrue(Validators.isValidSecurityAndPassphrase("WPA3", "securepassword"))
        assertFalse(Validators.isValidSecurityAndPassphrase("WPA2", "short"))
        assertFalse(Validators.isValidSecurityAndPassphrase("WPA3", "pass\tword"))
    }
}
