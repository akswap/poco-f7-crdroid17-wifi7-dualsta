package com.aks.dualstaprofilemanager.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.aks.dualstaprofilemanager.data.DiscoveredAp
import com.aks.dualstaprofilemanager.data.SecurityClassification
import com.aks.dualstaprofilemanager.data.WifiProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanDialog(
    discoveredAps: List<DiscoveredAp>,
    isScanning: Boolean,
    scanMessage: String,
    onStartScan: () -> Unit,
    onSelectAp: (DiscoveredAp) -> Unit,
    onManualAdd: (String, String, Int, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var manualSsidInput by remember { mutableStateOf("") }
    var manualBssidInput by remember { mutableStateOf("") }
    var manualFreqInput by remember { mutableStateOf("2412") }
    var manualSecInput by remember { mutableStateOf("WPA2") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Wi-Fi Scanner", fontWeight = FontWeight.Bold)
                IconButton(onClick = onStartScan, enabled = !isScanning) {
                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Scan", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(480.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Tab Row to give full vertical space to scanned AP list
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Scanned (${discoveredAps.size})", fontWeight = FontWeight.SemiBold) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Manual Add", fontWeight = FontWeight.SemiBold) }
                    )
                }

                if (isScanning) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                if (selectedTab == 0) {
                    // Scanned Networks List (Takes full height for comfortable scrolling & visibility)
                    Text(
                        text = scanMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )

                    if (discoveredAps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "No networks found yet.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = onStartScan, enabled = !isScanning) {
                                    Text("Start Wi-Fi Scan")
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(discoveredAps) { ap ->
                                ApItemCard(ap = ap, onClick = { onSelectAp(ap) })
                            }
                        }
                    }
                } else {
                    // Manual Add Form
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Add Network Manually", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = manualSsidInput,
                            onValueChange = { manualSsidInput = it },
                            label = { Text("SSID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualBssidInput,
                            onValueChange = { manualBssidInput = it },
                            label = { Text("BSSID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = manualFreqInput,
                            onValueChange = { manualFreqInput = it },
                            label = { Text("Frequency MHz (e.g. 2412, 5180)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Security Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("WPA3", "WPA2", "OPEN").forEach { sec ->
                                FilterChip(
                                    selected = manualSecInput == sec,
                                    onClick = { manualSecInput = sec },
                                    label = { Text(sec) }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val freqInt = manualFreqInput.toIntOrNull() ?: 2412
                                onManualAdd(manualSsidInput.ifEmpty { "ManualAP" }, manualBssidInput, freqInt, manualSecInput)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = manualSsidInput.isNotBlank()
                        ) {
                            Text("Configure Manual AP")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close", fontWeight = FontWeight.Bold) }
        }
    )
}

@Composable
fun ApItemCard(ap: DiscoveredAp, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (ap.isCurrentSecondary) MaterialTheme.colorScheme.primaryContainer
            else Color(0xFF1E293B) // High-contrast dark container
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Row 1: SSID + Wi-Fi Gen Badge + Signal dBm Badge
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (ap.isHidden) "[Hidden SSID]" else ap.ssid,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF38BDF8), // Bright Sky Blue
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val genColor = when (ap.wifiGeneration) {
                        "Wi-Fi 7+" -> Color(0xFFD97706) // Amber/Gold (MLO)
                        "Wi-Fi 7" -> Color(0xFFEA580C)  // Orange
                        "Wi-Fi 6E" -> Color(0xFF4F46E5) // Indigo
                        "Wi-Fi 6" -> Color(0xFF059669)  // Emerald
                        "Wi-Fi 5" -> Color(0xFF0284C7)  // Cyan
                        else -> Color(0xFF475569)       // Slate
                    }
                    Surface(
                        color = genColor,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = ap.wifiGeneration,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = Color(0xFF0F172A),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "${ap.signalDbm} dBm",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF4ADE80), // Bright Neon Green
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Row 2: BSSID + Frequency/Band
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "BSSID: ${ap.bssid}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFCBD5E1)
                )
                Text(
                    text = "${ap.frequencyMhz} MHz (${ap.bandAndChannelLabel})",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFA78BFA), // Lavender
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Row 3: Prominent Security Badge + Interface Indicator
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Security Type Badge
                val (secBgColor, secTextColor) = when (ap.securityClassification) {
                    SecurityClassification.WPA3 -> Pair(Color(0xFF7C3AED), Color.White) // Purple
                    SecurityClassification.WPA2_WPA3 -> Pair(Color(0xFF6366F1), Color.White) // Indigo
                    SecurityClassification.WPA2 -> Pair(Color(0xFF0284C7), Color.White) // Cyan/Blue
                    SecurityClassification.OPEN -> Pair(Color(0xFF16A34A), Color.White) // Green
                    SecurityClassification.ENTERPRISE -> Pair(Color(0xFFDC2626), Color.White) // Red
                    else -> Pair(Color(0xFF64748B), Color.White)
                }

                Surface(
                    color = secBgColor,
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = "Security: ${ap.securityClassification.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = secTextColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                if (ap.isCurrentPrimary) {
                    Surface(
                        color = Color(0xFF0284C7),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "Active wlan0",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (ap.isCurrentSecondary) {
                    Surface(
                        color = Color(0xFF16A34A),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "Active wlan1",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SaveConnectDialog(
    ap: DiscoveredAp?,
    manualSsid: String,
    manualBssid: String,
    manualFreq: Int,
    manualSecurity: String,
    existingProfiles: List<WifiProfile>,
    onDismiss: () -> Unit,
    onSaveOnly: (String, String, Int, String, String) -> Unit,
    onSaveAndConnect: (String, String, Int, String, String) -> Unit
) {
    val initialSsid = ap?.ssid ?: manualSsid
    var ssid by remember { mutableStateOf(if (initialSsid.equals("<unknown ssid>", ignoreCase = true)) "" else initialSsid) }
    var bssid by remember { mutableStateOf(ap?.bssid ?: manualBssid) }
    var frequency by remember { mutableStateOf((ap?.frequencyMhz ?: manualFreq).toString()) }
    
    // Auto-select detected security type accurately
    val detectedSec = when (ap?.securityClassification) {
        SecurityClassification.WPA3 -> "WPA3"
        SecurityClassification.WPA2_WPA3 -> "WPA3"
        SecurityClassification.WPA2 -> "WPA2"
        SecurityClassification.OPEN -> "OPEN"
        else -> if (manualSecurity.isNotEmpty()) manualSecurity else "WPA2"
    }
    var security by remember { mutableStateOf(detectedSec) }
    var passphrase by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val exactMatch: WifiProfile? = existingProfiles.firstOrNull { profile ->
        profile.bssid.equals(bssid, ignoreCase = true) || (!ssid.isEmpty() && profile.ssid.equals(ssid, ignoreCase = true)) 
    }

    val isUnsupported = ap != null && !ap.securityClassification.isSupported

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (exactMatch != null) "Update Existing Profile" else "Configure Wi-Fi Profile", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isUnsupported) {
                    Text(
                        "Warning: AP security (${ap?.securityClassification?.label}) may not be fully supported by dualsta overlay.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (ap != null) {
                    Surface(
                        color = Color(0xFF1E293B),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            Text(
                                text = "Selected: ${ap.ssid.ifEmpty { ap.bssid }}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF38BDF8)
                            )
                            Text(
                                text = "Detected Security: ${ap.securityClassification.label} | Freq: ${ap.frequencyMhz} MHz",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
                if (exactMatch != null) {
                    Text(
                        "Note: This BSSID/SSID already exists in your profiles list and will be updated.",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                OutlinedTextField(
                    value = ssid,
                    onValueChange = { ssid = it },
                    label = { Text("SSID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = bssid,
                    onValueChange = { bssid = it },
                    label = { Text("BSSID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = frequency,
                    onValueChange = { frequency = it },
                    label = { Text("Frequency MHz") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Security Type", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("WPA3", "WPA2", "OPEN").forEach { sec ->
                        FilterChip(
                            selected = security == sec,
                            onClick = { security = sec; if (sec == "OPEN") passphrase = "" },
                            label = { Text(sec) }
                        )
                    }
                }

                if (security != "OPEN") {
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Password (min 8 chars)") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(Icons.Default.Lock, contentDescription = "Toggle password visibility")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                if (!isUnsupported) {
                    TextButton(onClick = {
                        val freqInt = frequency.toIntOrNull() ?: 2412
                        onSaveOnly(ssid, bssid, freqInt, security, passphrase)
                    }) { Text("Save") }
                    Button(
                        onClick = {
                            val freqInt = frequency.toIntOrNull() ?: 2412
                            onSaveAndConnect(ssid, bssid, freqInt, security, passphrase)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7), contentColor = Color.White)
                    ) { 
                        Text("Save & Connect", fontWeight = FontWeight.Bold) 
                    }
                }
            }
        }
    )
}
