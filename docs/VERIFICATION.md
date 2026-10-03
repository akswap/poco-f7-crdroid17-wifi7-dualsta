# Runtime verification

## Test target

- Device: POCO F7 (`onyx`)
- ROM: `crDroidAndroid-17.0-20261002-onyx-v13.0-BETA`
- Framework: Port5 exact-hash guarded module
- Controller: Port5 secondary-only controller
- Manager: `DualStaProfileManager-crDroid17-v1.5.2-wifi7-parser.apk`

These are physical-device runtime results. A PASS requires both associations to reach `COMPLETED`, separate interface addresses, and interface-bound traffic. Unit tests do not replace radio verification.

## Complete directed band matrix

| Test | Primary STA (`wlan0`) | Secondary STA (`wlan1`) | Result |
|---|---|---|---|
| 1 | 2.4 GHz | 2.4 GHz, different AP/BSSID | PASS |
| 2 | 2.4 GHz | 5 GHz | PASS |
| 3 | 2.4 GHz | 6 GHz | PASS |
| 4 | 5 GHz | 2.4 GHz | PASS |
| 5 | 5 GHz | 5 GHz, different AP/BSSID | PASS |
| 6 | 5 GHz | 6 GHz | PASS |
| 7 | 6 GHz | 2.4 GHz | PASS |
| 8 | 6 GHz | 5 GHz | PASS |
| 9 | 6 GHz Wi-Fi 7 (BE9300) | 6 GHz Wi-Fi 6E (AXE75) | PASS |

The 6+6 test used separate routers, BSSIDs, frequencies and LAN subnets:

```text
wlan0: 6295 MHz, 11be, 192.168.0.x, BE9300
wlan1: 6375 MHz, 11ax, 192.168.2.x, AXE75
```

The AXE75 does not answer LAN ICMP echo, so its secondary data path was verified through its management service: HTTP returned 302 and HTTPS returned 200 when bound to `wlan1`.

## MLO-primary matrix

| Test | Primary STA (`wlan0`) | Secondary STA (`wlan1`) | Result |
|---|---|---|---|
| 10 | Combined 5+6 GHz MLO | 2.4 GHz, different router | PASS |
| 11 | Combined 5+6 GHz MLO | 5 GHz, separate SSID/BSSID | PASS |
| 12 | Combined 5+6 GHz MLO | 6 GHz, different router/MLD | PASS |

The MLO connection reported 802.11be, TID-to-link support and active 5 GHz and 6 GHz affiliated links. During several captures the 5 GHz affiliated link carried traffic while the active 6 GHz affiliated link was idle; this is valid MLO link steering behavior.

## Unsupported same-MLD case

This one combination failed and is not claimed as supported:

```text
Primary: combined 5+6 GHz MLO SSID on BE9300
Secondary: standalone 6 GHz SSID on the same BE9300/MLD
Result: association rejected (statusCode 1); wlan1 received no IP
```

The same MLO-primary plus 6 GHz-secondary layout passes when the secondary 6 GHz AP belongs to the separate AXE75 router. This isolates the failure to simultaneous use of the same physical AP/MLD, not to general MLO plus Dual STA capability.

## Link-mode observations

- Primary 6 GHz supports Wi-Fi 7/EHT at 320 MHz and up to 5764 Mbps in the captured setup.
- Secondary 6 GHz consistently operates as Wi-Fi 6E/HE at up to 160 MHz and 2401 Mbps.
- Qualcomm can format a secondary 5 GHz EHT-rate link as `HE-MCS 12/13`. Manager v1.5.2 recognizes this 2882 Mbps pattern as Wi-Fi 7 rather than displaying Wi-Fi 6.
- Same-band 2.4+2.4, 5+5 and 6+6 were verified with different APs/BSSIDs.

## Lifecycle and manager checks

| Check | Result |
|---|---|
| `wlan0` remains Android primary | PASS |
| `wlan1` is created and brought administratively up | PASS |
| `wlan1` associates to the selected exact profile | PASS |
| Secondary profile can be switched from the APK | PASS |
| Secondary reconnects automatically after reboot | PASS |
| Active primary BSSID is blocked as a secondary target | PASS |
| Profile update uses temporary file plus atomic replace | PASS |
| Privileged read-back matches the written profile | PASS |
| Password is omitted from diagnostics and public package | PASS |
| 6 GHz / 320 MHz hotspot remains provided by the unified module | PASS |

Older PixelOS, Infinity-X and crDroid 16 results are intentionally excluded from this crDroid 17 Port5 matrix.
