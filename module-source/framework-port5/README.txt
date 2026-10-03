POCO F7 / onyx, crDroid Android 17 v13.0-BETA-20261002 only.

This module bind-mounts a surgically patched copy of the exact crDroid
service-wifi.jar. Port2 includes the complete PixelOS pre-scan flow plus
the periodic-scan retry and single-AP channel-retention changes that were
missing from port1. Port4 keeps the Port3 deferral and replays the filtered cached scan as soon
as the pre-scan wlan1 manager is ready. This closes the no-callback timeout seen
when WifiScanningService reuses wlan0's scan implementation.
Its boot service keeps network-switch MBB disabled so a prefer-secondary
request stays on wlan1. It refuses installation at boot if the stock or
patched SHA-256 differs and disables itself on refusal.

Rollback:
  adb shell su -c 'touch /data/adb/modules/aks_crdroid17_exact_dualsta_wifi/disable'
  adb reboot

Do not reuse this ZIP after a ROM update. Re-port against the updated jar.
