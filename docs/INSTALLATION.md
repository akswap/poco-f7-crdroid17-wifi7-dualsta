# Installation and rollback

## Requirements

- POCO F7 / onyx
- `crDroidAndroid-17.0-20261002-onyx-v13.0-BETA`
- Root through Magisk
- A backup of the current working `init_boot.img`

## Install

1. Install the unified Wi-Fi 7/6 GHz module from `packages/` in Magisk.
2. Flash init_boot_a_crDroid17_DualSTA_TEST.img  ```fastboot flash init_boot init_boot_a_crDroid17_DualSTA_TEST.img```
4. Install the Port5 framework module.
5. Install the paired Port5 controller module.
6. Reboot once.
7. Install the v1.5.2 APK.
8. Open the manager, scan, choose an AP, enter its password and use **Connect #1**.
9. Verify both links with:

```sh
su
iw wlan0 link
iw wlan1 link
ip -4 -br addr show wlan0
ip -4 -br addr show wlan1
```

The selected secondary profile should reconnect automatically after reboot.

## Rollback

Keep the previous working Port4 framework/controller ZIPs. If Port5 is incompatible, install the paired Port4 ZIPs and reboot. If Android cannot boot, restore the known-working `init_boot.img` using fastboot.

Do not mix framework and controller versions because the controller checks the exact patched framework hash.
