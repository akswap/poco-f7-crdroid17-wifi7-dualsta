#!/system/bin/sh

LOG=/data/local/tmp/crdroid17-dualsta-framework.log
until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done

cmd wifi force-overlay-config-value bool \
    config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled enabled false \
    >/dev/null 2>&1
cmd wifi force-overlay-config-value bool \
    config_wifiAllowMultiInternetConnectDual5GFrequency enabled true \
    >/dev/null 2>&1
cmd wifi set-multi-internet-mode 2 >/dev/null 2>&1

echo "$(date '+%Y-%m-%d %H:%M:%S') boot completed; Multi-STA mode requested and network-switch MBB disabled" >> "$LOG"
