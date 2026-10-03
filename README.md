# POCO F7 crDroid 17 Wi-Fi 7 Dual STA

ROM-specific Wi-Fi framework port, Magisk controller and profile-manager APK for **POCO F7 / onyx** running:

`crDroidAndroid-17.0-20261002-onyx-v13.0-BETA`

This repository contains the exact Port5 build that was tested on the device. It is not a universal Android or MIUI module.

## Verified runtime

- `wlan0`: primary STA remains managed by Android.
- `wlan1`: secondary STA is created and controlled by the root controller.
- Reboot auto-connect works without a manual shell command.
- Primary 6 GHz Wi-Fi 7, 320 MHz + secondary 5 GHz Wi-Fi 7, 160 MHz.
- Primary 5 GHz + secondary 5 GHz.
- Primary 5 GHz + secondary 2.4 GHz after selecting the profile in the manager.
- The manager reports Qualcomm's secondary `HE-MCS 12/13` label as Wi-Fi 7 because MCS 12/13 are EHT-only rates.

Live verified example:

```text
wlan0: TP-Link_6G_be, 6295 MHz, 320 MHz EHT, 5764.6 Mbps
wlan1: TP-Link_5G_be, 5640 MHz, 160 MHz, 2882.3 Mbps
```

## Packages

The `packages/` directory contains:

1. `POCO-F7-crDroid13-A17-WiFi7-6GHz-320MHz-v1.2-UNIFIED.zip`
2. `POCO-F7-crDroid17-Exact-Dual-STA-Framework-v1.5-port5-TEST.zip`
3. `POCO-F7-crDroid17-Exact-Dual-STA-Controller-v1.8-port5-TEST.zip`
4. `DualStaProfileManager-crDroid17-v1.5.2-wifi7-parser.apk`

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
