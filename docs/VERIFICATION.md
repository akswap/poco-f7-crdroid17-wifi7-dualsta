# Runtime verification

## Test target

- Device: POCO F7 (`onyx`)
- ROM: `crDroidAndroid-17.0-20261002-onyx-v13.0-BETA`
- Framework: Port5 exact-hash guarded module
- Controller: Port5 secondary-only controller
- Manager: `DualStaProfileManager-crDroid17-v1.5.2-wifi7-parser.apk`

These are physical-device runtime results. Unit-test results for the Android source are separate and do not replace radio verification.

## Concurrent STA matrix

| Test | Primary STA (`wlan0`) | Secondary STA (`wlan1`) | Result |
|---|---|---|---|
| A | 6 GHz Wi-Fi 7 / MLO, 320 MHz | 5 GHz Wi-Fi 7, 160 MHz | PASS |
| B | 5 GHz | 5 GHz | PASS |
| C | 5 GHz | 2.4 GHz | PASS |

The same-band 5 GHz test used separate AP profiles. The repository does not claim that both interfaces can associate to the same BSSID.

## Live radio evidence

The strongest captured simultaneous link state was:

```text
wlan0
  SSID: TP-Link_6G_be
  frequency: 6295 MHz
  RX/TX: 5764.6 MBit/s, 320 MHz, EHT-MCS 13, NSS 2

wlan1
  SSID: TP-Link_5G_be
  frequency: 5640 MHz
  RX/TX: 2882.3 MBit/s, 160 MHz, HE-MCS 13, NSS 2
```

The Qualcomm secondary-link formatter uses an `HE-MCS 13` label. MCS 12/13 are EHT-only rates, so manager v1.5.2 treats this specific 160 MHz / MCS 12-13 pattern as Wi-Fi 7 instead of displaying Wi-Fi 6.

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

## Evidence boundary

The following combinations are not claimed as verified on this exact crDroid 17 Port5 build until a physical-device result is recorded:

- primary 2.4 GHz + secondary 5 GHz
- primary 2.4 GHz + secondary 6 GHz
- primary 6 GHz + secondary 2.4 GHz
- primary 2.4 GHz + secondary 2.4 GHz
- primary 6 GHz + secondary 6 GHz

Older PixelOS, Infinity-X or crDroid 16 results are not counted as crDroid 17 Port5 verification.
