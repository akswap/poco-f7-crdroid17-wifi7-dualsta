# Dual STA Profile Manager for crDroid 17 / POCO F7 (onyx)

This build targets the verified crDroid 17 port3 pair:

- Framework module: `aks_crdroid17_exact_dualsta_wifi`
- Controller module: `onyx_dualsta_overlay` v1.6.0-crdroid17-port3
- Primary STA: `wlan0`
- Secondary STA: `wlan1`
- Persistent profiles: `/data/adb/aks-dualsta/profiles.conf`

The Android package is separate from the PixelOS build, so both APKs can be
installed together. The profile writer updates the controller's persistent
database instead of replacing the module-directory symlink.
