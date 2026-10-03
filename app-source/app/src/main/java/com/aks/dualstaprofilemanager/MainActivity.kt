package com.aks.dualstaprofilemanager

import android.Manifest
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.aks.dualstaprofilemanager.data.*
import com.aks.dualstaprofilemanager.ui.ScanDialog
import com.aks.dualstaprofilemanager.ui.SaveConnectDialog
import com.aks.dualstaprofilemanager.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { MyApplicationTheme { DualStaScreen() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DualStaScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Requesting root & loading profiles...") }
    var profiles by remember { mutableStateOf<List<WifiProfile>>(emptyList()) }
    var wlan0 by remember { mutableStateOf("Not loaded") }
    var wlan1 by remember { mutableStateOf("Not loaded") }
    var wlan1Active by remember { mutableStateOf(false) }

    // Scan & Dialog states
    var showScanDialog by remember { mutableStateOf(false) }
    var discoveredAps by remember { mutableStateOf<List<DiscoveredAp>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }
    var scanMessage by remember { mutableStateOf("Tap scan to discover Wi-Fi networks.") }
    var selectedApForConfig by remember { mutableStateOf<DiscoveredAp?>(null) }
    var manualSsid by remember { mutableStateOf("") }
    var manualBssid by remember { mutableStateOf("") }
    var manualFreq by remember { mutableStateOf(2412) }
    var manualSecurity by remember { mutableStateOf("WPA2") }
    var showConfigDialog by remember { mutableStateOf(false) }

    val wifiManager = remember {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    fun refresh() = scope.launch {
        busy = true
        runCatching {
            withContext(Dispatchers.IO) {
                check(RootShellManager.isRootAvailable()) { "Root access was not granted" }
                check(RootShellManager.checkModuleExists()) { "onyx_dualsta_overlay v1.3 module was not found" }
                val loaded = ConfigParser.parseConfig(RootShellManager.readConfig())
                val link0 = RootShellManager.getWlanLink("wlan0")
                val link1 = RootShellManager.getWlanLink("wlan1")
                val active1 = RootShellManager.isInterfaceActive("wlan1")
                QuadData(loaded, link0, link1, active1)
            }
        }.onSuccess {
            profiles = it.first
            wlan0 = it.second
            wlan1 = it.third
            wlan1Active = it.fourth
            message = "Loaded ${profiles.size} profile(s)"
        }.onFailure { message = it.message ?: "Load failed" }
        busy = false
    }

    LaunchedEffect(Unit) {
        refresh()
        while (true) {
            delay(3000)
            runCatching {
                withContext(Dispatchers.IO) {
                    val link0 = RootShellManager.getWlanLink("wlan0")
                    val link1 = RootShellManager.getWlanLink("wlan1")
                    val active1 = RootShellManager.isInterfaceActive("wlan1")
                    Triple(link0, link1, active1)
                }
            }.onSuccess {
                wlan0 = it.first
                wlan1 = it.second
                wlan1Active = it.third
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            showScanDialog = true
            triggerWifiScan(context, wifiManager, wlan0, wlan1, { aps, msg ->
                discoveredAps = aps
                scanMessage = msg
                isScanning = false
            }, { isScanning = true })
        } else {
            scanMessage = "Location & Wi-Fi permissions required for scanning."
            isScanning = false
        }
    }

    fun saveConfigOnly(newProfiles: List<WifiProfile>) = scope.launch {
        busy = true
        runCatching {
            val validated = newProfiles.mapIndexed { i, p ->
                require(Validators.isValidSsid(p.ssid)) { "Profile ${i + 1}: invalid SSID" }
                require(Validators.isValidBssid(p.bssid)) { "Profile ${i + 1}: invalid BSSID" }
                require(Validators.isValidFrequency(p.frequencyMhz)) { "Profile ${i + 1}: invalid frequency" }
                require(Validators.isValidSecurityAndPassphrase(p.security, p.passphrase)) { "Profile ${i + 1}: invalid security/password" }
                p.copy(priority = i + 1, bssid = p.bssid.lowercase())
            }
            withContext(Dispatchers.IO) {
                check(RootShellManager.saveConfigAtomic(ConfigParser.serializeConfig(validated))) { "Atomic save failed" }
            }
            profiles = validated
        }.onSuccess { message = "Profiles saved successfully." }
         .onFailure { message = it.message ?: "Save failed" }
        busy = false
    }

    fun saveAndConnect(targetSsid: String, targetBssid: String, targetFreq: Int, targetSec: String, targetPass: String) = scope.launch {
        showConfigDialog = false
        busy = true
        message = "Saving profile & connecting..."

        runCatching {
            val normalizedBssid = targetBssid.lowercase()
            val primaryLink = withContext(Dispatchers.IO) {
                RootShellManager.getWlanLink("wlan0")
            }
            val primaryBssid = StatusParser.parseIwLink(primaryLink).bssid
            require(!primaryBssid.equals(normalizedBssid, ignoreCase = true)) {
                "Selected AP is already connected as primary. Choose a different BSSID for secondary STA."
            }

            val validated = ConfigParser.prepareExclusiveConnectProfiles(
                existingProfiles = profiles,
                selectedProfile = WifiProfile(
                    priority = 1,
                    enabled = true,
                    ssid = targetSsid,
                    bssid = normalizedBssid,
                    frequencyMhz = targetFreq,
                    security = targetSec,
                    passphrase = targetPass
                )
            )

            withContext(Dispatchers.IO) {
                check(RootShellManager.saveConfigAtomic(ConfigParser.serializeConfig(validated))) { "Atomic save failed" }
                check(RootShellManager.restartSecondaryHelper()) { "Helper restart failed" }
            }
            profiles = validated

            var connected = false
            for (attempt in 1..20) {
                delay(3000)
                val wlan1Status = withContext(Dispatchers.IO) {
                    RootShellManager.getWlanLink("wlan1")
                }
                wlan1 = wlan1Status
                val parsed = StatusParser.parseIwLink(wlan1Status)

                if (parsed.isConnected) {
                    val bssidMatch = parsed.bssid?.equals(normalizedBssid, ignoreCase = true) == true
                    val freqMatch = parsed.frequencyMhz == targetFreq
                    if (bssidMatch && freqMatch) {
                        connected = true
                        break
                    } else if (parsed.ssid != null) {
                        message = "Connected: ${parsed.ssid}"
                        break
                    }
                }
            }

            if (connected) {
                message = "Connected to $targetSsid (${targetFreq} MHz)"
            } else if (!message.startsWith("Connected:")) {
                message = "Connection timeout after 60 seconds. Check module logs."
            }
        }.onFailure { message = it.message ?: "Save and connect failed" }
        busy = false
    }

    fun bringWlan1UpAction() = scope.launch {
        busy = true
        val (success, msg) = withContext(Dispatchers.IO) {
            RootShellManager.bringWlan1Up()
        }
        message = msg
        val (newWlan1, active1) = withContext(Dispatchers.IO) {
            Pair(RootShellManager.getWlanLink("wlan1"), RootShellManager.isInterfaceActive("wlan1"))
        }
        wlan1 = newWlan1
        wlan1Active = active1
        busy = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dual STA Manager — crDroid 17", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            showScanDialog = true
                            triggerWifiScan(context, wifiManager, wlan0, wlan1, { aps, msg ->
                                discoveredAps = aps
                                scanMessage = msg
                                isScanning = false
                            }, { isScanning = true })
                        } else {
                            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        }
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan Wi-Fi")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                maxLines = 2
            )

            // Side-by-side Interface Status Cards (wlan0 & wlan1) with dynamic sizing & no excess gaps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatusCardCompact(
                    title = "Primary",
                    interfaceName = "wlan0",
                    rawOutput = wlan0,
                    isUp = null,
                    modifier = Modifier.weight(1f)
                )
                StatusCardCompact(
                    title = "Secondary",
                    interfaceName = "wlan1",
                    rawOutput = wlan1,
                    isUp = wlan1Active,
                    modifier = Modifier.weight(1f)
                )
            }

            // Action Buttons with high-contrast text and optimized colors
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { refresh() },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E293B), // Dark Slate Gray
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF1E293B).copy(alpha = 0.5f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Refresh",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Button(
                    onClick = { saveConfigOnly(profiles) },
                    enabled = !busy && profiles.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF0F766E), // Deep Teal
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF0F766E).copy(alpha = 0.4f),
                        disabledContentColor = Color.White.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Save",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                Button(
                    onClick = { bringWlan1UpAction() },
                    enabled = !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (wlan1Active) Color(0xFF16A34A) else Color(0xFFDC2626), // Green when UP, Red when DOWN
                        contentColor = Color.White,
                        disabledContainerColor = if (wlan1Active) Color(0xFF16A34A).copy(alpha = 0.5f) else Color(0xFFDC2626).copy(alpha = 0.5f),
                        disabledContentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = if (wlan1Active) "wlan1: UP" else "wlan1: DOWN",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Advanced Scan Button & Save+Connect
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasPermission) {
                            showScanDialog = true
                            triggerWifiScan(context, wifiManager, wlan0, wlan1, { aps, msg ->
                                discoveredAps = aps
                                scanMessage = msg
                                isScanning = false
                            }, { isScanning = true })
                        } else {
                            val perms = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_WIFI_STATE, Manifest.permission.CHANGE_WIFI_STATE)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                perms.add(Manifest.permission.NEARBY_WIFI_DEVICES)
                            }
                            permissionLauncher.launch(perms.toTypedArray())
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4338CA), // Indigo
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Scan & Select AP",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (profiles.isNotEmpty()) {
                    Button(
                        onClick = {
                            saveAndConnect(
                                profiles[0].ssid,
                                profiles[0].bssid,
                                profiles[0].frequencyMhz,
                                profiles[0].security,
                                profiles[0].passphrase
                            )
                        },
                        enabled = !busy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF0284C7), // Light Blue
                            contentColor = Color.White,
                            disabledContainerColor = Color(0xFF0284C7).copy(alpha = 0.4f),
                            disabledContentColor = Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Connect #1",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())

            // Configured Profiles Section
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Configured Profiles (${profiles.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            if (profiles.isEmpty()) {
                Text(
                    "No profiles configured. Use 'Scan & Select AP' above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                profiles.forEachIndexed { index, p ->
                    ProfileEditor(
                        p = p,
                        index = index,
                        onChange = { updated ->
                            profiles = profiles.toMutableList().also { it[index] = updated }
                        },
                        onDelete = {
                            profiles = profiles.toMutableList().also { it.removeAt(index) }
                        },
                        onUp = if (index > 0) {
                            {
                                val list = profiles.toMutableList()
                                val item = list.removeAt(index)
                                list.add(index - 1, item)
                                profiles = list
                            }
                        } else null,
                        onDown = if (index < profiles.size - 1) {
                            {
                                val list = profiles.toMutableList()
                                val item = list.removeAt(index)
                                list.add(index + 1, item)
                                profiles = list
                            }
                        } else null
                    )
                }
            }
        }
    }

    if (showScanDialog) {
        ScanDialog(
            discoveredAps = discoveredAps,
            isScanning = isScanning,
            scanMessage = scanMessage,
            onStartScan = {
                triggerWifiScan(context, wifiManager, wlan0, wlan1, { aps, msg ->
                    discoveredAps = aps
                    scanMessage = msg
                    isScanning = false
                }, { isScanning = true })
            },
            onSelectAp = { ap ->
                selectedApForConfig = ap
                manualSsid = ap.ssid
                manualBssid = ap.bssid
                manualFreq = ap.frequencyMhz
                manualSecurity = when (ap.securityClassification) {
                    SecurityClassification.WPA3, SecurityClassification.WPA2_WPA3 -> "WPA3"
                    SecurityClassification.OPEN -> "OPEN"
                    else -> "WPA2"
                }
                showScanDialog = false
                showConfigDialog = true
            },
            onManualAdd = { s, b, f, sec ->
                manualSsid = s
                manualBssid = b
                manualFreq = f
                manualSecurity = sec
                showScanDialog = false
                showConfigDialog = true
            },
            onDismiss = { showScanDialog = false }
        )
    }

    if (showConfigDialog) {
        SaveConnectDialog(
            ap = selectedApForConfig,
            manualSsid = manualSsid,
            manualBssid = manualBssid,
            manualFreq = manualFreq,
            manualSecurity = manualSecurity,
            existingProfiles = profiles,
            onDismiss = { showConfigDialog = false; selectedApForConfig = null },
            onSaveOnly = { ssid, bssid, freq, sec, pass ->
                val newP = WifiProfile(profiles.size + 1, true, ssid, bssid, freq, sec, pass)
                saveConfigOnly(profiles + newP)
                showConfigDialog = false
                selectedApForConfig = null
            },
            onSaveAndConnect = { ssid, bssid, freq, sec, pass ->
                saveAndConnect(ssid, bssid, freq, sec, pass)
                selectedApForConfig = null
            }
        )
    }
}

private data class QuadData<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun triggerWifiScan(
    context: Context,
    wifiManager: WifiManager?,
    wlan0StatusStr: String,
    wlan1StatusStr: String,
    onResult: (List<DiscoveredAp>, String) -> Unit,
    onStart: () -> Unit
) {
    if (wifiManager == null) {
        onResult(emptyList(), "Error: WifiManager not available")
        return
    }
    if (!wifiManager.isWifiEnabled) {
        onResult(emptyList(), "Error: Wi-Fi is disabled. Please enable Wi-Fi.")
        return
    }

    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    if (locationManager?.isLocationEnabled != true) {
        onResult(emptyList(), "Location service is off. Turn Location on, then scan again.")
        return
    }

    onStart()

    val primaryBssid = StatusParser.parseIwLink(wlan0StatusStr).bssid
    val secondaryBssid = StatusParser.parseIwLink(wlan1StatusStr).bssid

    fun deliver(resultsUpdated: Boolean) {
      val results = runCatching { wifiManager.scanResults }.getOrNull() ?: emptyList()
      val aps = results.map { result ->
        val secClass = ScanParser.classifySecurity(result.capabilities ?: "")
        val bssid = result.BSSID ?: "00:00:00:00:00:00"
        val isPrimary = !primaryBssid.isNullOrEmpty() && bssid.equals(primaryBssid, ignoreCase = true)
        val isSecondary = !secondaryBssid.isNullOrEmpty() && bssid.equals(secondaryBssid, ignoreCase = true)
        val wifiStandard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { result.wifiStandard }.getOrNull()
        } else null

        val stdInfo = ScanParser.inspectScanResult(result)

        DiscoveredAp(
            ssid = result.SSID ?: "",
            bssid = bssid,
            frequencyMhz = result.frequency,
            signalDbm = result.level,
            capabilities = result.capabilities ?: "",
            securityClassification = secClass,
            wifiStandard = wifiStandard,
            isEht = stdInfo.isEht,
            isMlo = stdInfo.isMlo,
            isHe = stdInfo.isHe,
            isVht = stdInfo.isVht,
            isCurrentPrimary = isPrimary,
            isCurrentSecondary = isSecondary
        )
      }

      val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
      val scanType = if (resultsUpdated) "Fresh scan" else "Cached scan results (Android throttled the request)"
      onResult(aps, "$scanType at $timestamp — Found ${aps.size} network(s).")
    }

    var receiver: BroadcastReceiver? = null
    receiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            runCatching { context.unregisterReceiver(this) }
            val updated = intent?.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false) == true
            deliver(updated)
        }
    }
    val registered = runCatching {
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        true
    }.getOrDefault(false)
    val scanInitiated = runCatching { wifiManager.startScan() }.getOrDefault(false)
    if (!registered || !scanInitiated) {
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        deliver(false)
    }
}

@Composable
private fun StatusCardCompact(
    title: String,
    interfaceName: String,
    rawOutput: String,
    isUp: Boolean?,
    modifier: Modifier = Modifier
) {
    val status = StatusParser.parseIwLink(rawOutput)
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF0F172A) // Sleek Dark Slate Card Background
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Header: Interface Name + Status Badge
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$interfaceName ($title)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF8FAFC) // Crisp White
                )
                if (isUp != null) {
                    Surface(
                        color = if (isUp) Color(0xFF16A34A) else Color(0xFFDC2626),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = if (isUp) "UP" else "DOWN",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (status.isConnected) {
                    Surface(
                        color = Color(0xFF0284C7),
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = "PRIMARY",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (status.isConnected) {
                // SSID + Wi-Fi Generation Badge (Wi-Fi 7+ MLO / 7 / 6E / 6 / 5 / 4)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = status.ssid ?: "Connected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8), // Bright Sky Blue
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    val genColor = when (status.wifiGeneration) {
                        "Wi-Fi 7+" -> Color(0xFFD97706) // Amber/Gold (MLO)
                        "Wi-Fi 7" -> Color(0xFFEA580C)  // Orange
                        "Wi-Fi 6E" -> Color(0xFF4F46E5) // Indigo
                        "Wi-Fi 6" -> Color(0xFF059669)  // Emerald
                        "Wi-Fi 5" -> Color(0xFF0284C7)  // Cyan
                        else -> Color(0xFF475569)       // Slate
                    }
                    Surface(
                        color = genColor,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = status.wifiGeneration,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }

                // Band & Signal Strength Row
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = status.bandAndChannelLabel.ifEmpty { status.frequencyMhz?.let { "$it MHz" } ?: "" },
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFA78BFA), // Vibrant Lavender/Violet
                        fontWeight = FontWeight.Bold
                    )
                    if (status.signalDbm != null) {
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "${status.signalDbm} dBm",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF4ADE80), // Bright Neon Green
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // BSSID
                if (!status.bssid.isNullOrBlank()) {
                    Text(
                        text = status.bssid,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFCBD5E1), // Crisp Light Slate
                        maxLines = 1
                    )
                }

                // Prominent RX & TX Link Speeds
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (!status.rxBitrate.isNullOrBlank()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "RX Speed:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF1F5F9), // Pure White
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = status.rxBitrate,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF34D399), // Emerald Green
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    if (!status.txBitrate.isNullOrBlank()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "TX Speed:",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFF1F5F9), // Pure White
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = status.txBitrate,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF38BDF8), // Cyan / Sky Blue
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                    if (status.rxBitrate.isNullOrBlank() && status.txBitrate.isNullOrBlank()) {
                        Text(
                            text = "Link: Active",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    text = if (isUp == false) "State: DOWN" else "Status: Disconnected",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isUp == false) Color(0xFFF87171) else Color(0xFFFBBF24),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileEditor(
    p: WifiProfile,
    index: Int,
    onChange: (WifiProfile) -> Unit,
    onDelete: () -> Unit,
    onUp: (() -> Unit)?,
    onDown: (() -> Unit)?
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (index == 0) "Auto #0 (first choice)" else "Fallback #$index",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Enabled", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.width(6.dp))
                Switch(p.enabled, { onChange(p.copy(enabled = it)) })
            }
            OutlinedTextField(
                value = p.ssid,
                onValueChange = { onChange(p.copy(ssid = it)) },
                label = { Text("SSID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = p.bssid,
                onValueChange = { onChange(p.copy(bssid = it)) },
                label = { Text("BSSID") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = p.frequencyMhz.toString(),
                onValueChange = { it.toIntOrNull()?.let { n -> onChange(p.copy(frequencyMhz = n)) } },
                label = { Text("Frequency MHz (${p.bandAndChannelLabel})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("WPA3", "WPA2", "OPEN").forEach { sec ->
                    FilterChip(
                        selected = p.security == sec,
                        onClick = { onChange(p.copy(security = sec, passphrase = if (sec == "OPEN") "" else p.passphrase)) },
                        label = { Text(sec) }
                    )
                }
            }
            if (p.security != "OPEN") {
                OutlinedTextField(
                    value = p.passphrase,
                    onValueChange = { onChange(p.copy(passphrase = it)) },
                    label = { Text("Password") },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(Icons.Default.Lock, contentDescription = "Toggle password")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onUp?.invoke() },
                    enabled = onUp != null,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Move Up")
                }
                OutlinedButton(
                    onClick = { onDown?.invoke() },
                    enabled = onDown != null,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Move Down")
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            }
        }
    }
}
