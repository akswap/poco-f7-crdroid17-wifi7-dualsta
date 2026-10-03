package com.aks.dualstaprofilemanager

import com.aks.dualstaprofilemanager.data.ConfigParser
import com.aks.dualstaprofilemanager.data.WifiProfile
import org.junit.Assert.*
import org.junit.Test

class ConfigParserTest {

    private val sampleConfig = listOf(
        ConfigParser.COMMENT_HEADER,
        listOf("1", "1", "Example_6G", "02:11:22:33:44:55", "6215", "WPA3", "testpass123").joinToString("\t"),
        listOf("2", "0", "Guest_WiFi", "12:34:56:78:9a:bc", "2437", "OPEN", "").joinToString("\t")
    ).joinToString("\n")

    @Test
    fun testParseConfig() {
        val profiles = ConfigParser.parseConfig(sampleConfig)
        assertEquals(2, profiles.size)
        
        assertEquals(1, profiles[0].priority)
        assertTrue(profiles[0].enabled)
        assertEquals("Example_6G", profiles[0].ssid)
        assertEquals("02:11:22:33:44:55", profiles[0].bssid)
        assertEquals(6215, profiles[0].frequencyMhz)
        assertEquals("WPA3", profiles[0].security)
        assertEquals("testpass123", profiles[0].passphrase)
        assertEquals("6 GHz", profiles[0].bandLabel)

        assertEquals(2, profiles[1].priority)
        assertFalse(profiles[1].enabled)
        assertEquals("Guest_WiFi", profiles[1].ssid)
        assertEquals("2.4 GHz", profiles[1].bandLabel)
    }

    @Test
    fun testSerializeConfig() {
        val profiles = listOf(
            WifiProfile(1, true, "Example_6G", "02:11:22:33:44:55", 6215, "WPA3", "testpass123")
        )
        val serialized = ConfigParser.serializeConfig(profiles)
        assertTrue(serialized.contains("Example_6G"))
        assertTrue(serialized.contains("6215"))
    }
}
