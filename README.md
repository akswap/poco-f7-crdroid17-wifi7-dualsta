# POCO F7 crDroid 17 Wi-Fi 7 Dual STA (Dual WI-FI) & 6Ghz 320Mhz Hotspot Work's 

ROM-specific Wi-Fi framework port, Magisk controller and profile-manager APK for **POCO F7 / onyx** running:

`crDroidAndroid-17.0-20261002-onyx-v13.0-BETA`

This repository contains the exact Port5 build that was tested on the device. It is not a universal Android or MIUI module.

## How It Works

- **Primary Wi-Fi (wlan0):** Supports 2.4 GHz, 5 GHz, 6 GHz, and MLO.
- **Secondary Wi-Fi (wlan1):** Can connect to a supported SSID on any available band.

### Automatic Failover Behavior

1. If the **Primary Wi-Fi loses connectivity**, Internet traffic automatically switches to the **Secondary Wi-Fi**.
2. If the **Secondary Wi-Fi disconnects**, the **Primary Wi-Fi continues providing Internet access**.
3. When both connections are available, the **Primary Wi-Fi is preferred**, while the **Secondary Wi-Fi remains connected as a backup**.
4. When the Primary Wi-Fi recovers, Internet traffic automatically switches back to it after connectivity is confirmed.

> Failover decisions are based on Internet reachability, not only Wi-Fi association status.


## Verified runtime

The complete 3 x 3 directed band matrix and three MLO-primary scenarios passed on the physical device:

| # | Primary (`wlan0`) | Secondary (`wlan1`) | Result |
|---|---|---|---|
| 1 | 2.4 GHz | 2.4 GHz, different AP/BSSID | PASS |
| 2 | 2.4 GHz | 5 GHz | PASS |
| 3 | 2.4 GHz | 6 GHz | PASS |
| 4 | 5 GHz | 2.4 GHz | PASS |
| 5 | 5 GHz | 5 GHz, different AP/BSSID | PASS |
| 6 | 5 GHz | 6 GHz | PASS |
| 7 | 6 GHz | 2.4 GHz | PASS |
| 8 | 6 GHz | 5 GHz | PASS |
| 9 | 6 GHz Wi-Fi 7, BE9300 | 6 GHz Wi-Fi 6E, AXE75 | PASS |
| 10 | Combined 5+6 GHz MLO | 2.4 GHz, different router | PASS |
| 11 | Combined 5+6 GHz MLO | 5 GHz, separate SSID/BSSID | PASS |
| 12 | Combined 5+6 GHz MLO | 6 GHz, different router/MLD | PASS |

**Unsupported same-MLD case:** combined 5+6 GHz MLO as primary plus the standalone 6 GHz SSID from the same physical BE9300/MLD is association-rejected. The same MLO+6 GHz layout passes when the secondary 6 GHz AP is the separate AXE75 router.

Additional verified behavior:

- `wlan0` remains the Android-managed primary STA.
- `wlan1` is created as the secondary STA and stays under the root controller.
- `wlan1` reconnects after reboot without a manual shell command.
- Scan, profile selection and secondary-only switching work from the manager APK.
- Selecting the active primary BSSID as the secondary target is rejected.
- Profile writes are atomic and verified through privileged read-back.
- The manager reports Qualcomm's secondary `HE-MCS 12/13` label as Wi-Fi 7 because MCS 12/13 are EHT-only rates.
- A secondary 6 GHz link operates as Wi-Fi 6E/HE at up to 160 MHz; primary 6 GHz can use Wi-Fi 7/EHT at 320 MHz.

Live verified example:

```text
wlan0: TP-Link_6G_be, 6295 MHz, 320 MHz EHT, 5764.6 Mbps
wlan1: TP-Link_5G_be, 5640 MHz, 160 MHz, 2882.3 Mbps
```

The full 12-scenario matrix, traffic checks and the same-MLD limitation are documented in [runtime verification](docs/VERIFICATION.md).

## Packages & int_boot (rooted & Wlan .ko inbuilt )

The `packages/` directory contains:

1. `POCO-F7-crDroid13-A17-WiFi7-6GHz-320MHz-v1.2-UNIFIED.zip`
2. `POCO-F7-crDroid17-Exact-Dual-STA-Framework-v1.5-port5-TEST.zip`
3. `POCO-F7-crDroid17-Exact-Dual-STA-Controller-v1.8-port5-TEST.zip`
4. `DualStaProfileManager-crDroid17-v1.5.2-wifi7-parser.apk`
5. `init_boot_a_crDroid17_DualSTA_TEST.img`

Install the three Magisk ZIPs, reboot, then install the APK. Keep a known-working `init_boot.img` and the previous Port4 modules available before testing.

## Profile manager behavior

- Scans nearby APs and fills SSID, BSSID and frequency.
- Saves the selected profile as the only enabled secondary target.
- Keeps other saved profiles, but disables them during an explicit connect operation.
- Rejects the primary BSSID as a secondary target.
- Verifies the atomic profile write through privileged read-back.
- Preserves passwords locally and never includes them in diagnostic output.
- Shows Wi-Fi 7 for Qualcomm secondary links reported as `HE-MCS 12/13`.

## Source layout

- `app-source/` — Android Studio project for the manager APK.
- `module-source/framework-port5/` — exact guarded framework module payload.
- `module-source/controller-port5/` — controller/helper/overlay payload with an empty public profile template.
- `docs/` — technical notes, verified hashes and rollback information.

## Security and privacy

No Wi-Fi SSID credentials or device profile database are included. `profiles.conf` is an empty template. Signing keystores, `local.properties`, build caches and device logs are excluded. Test strings in JVM unit tests are synthetic.

The prebuilt APK is signed with the original project key. The private signing key is intentionally not published. Public source builds use the local developer/debug key unless the builder configures their own signing.

## Warning

The framework module replaces `service-wifi.jar` only when the expected stock file hash matches. Do not flash it on another ROM build. A mismatch is intended to fail closed.

See [installation and rollback](docs/INSTALLATION.md) and [checksums](docs/SHA256SUMS.txt).
