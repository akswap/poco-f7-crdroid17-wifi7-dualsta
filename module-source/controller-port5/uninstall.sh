#!/system/bin/sh

PIDFILE=/data/local/tmp/dualsta-helper.pid
pid="$(cat "$PIDFILE" 2>/dev/null)"
if [ -n "$pid" ] && [ -r "/proc/$pid/cmdline" ] \
    && tr '\000' ' ' < "/proc/$pid/cmdline" 2>/dev/null | grep -Fq 'DualStaRequest'; then
    kill "$pid" 2>/dev/null
fi
rm -f "$PIDFILE"
rm -rf /data/adb/aks-dualsta
cmd wifi force-overlay-config-value bool config_wifiAllowMultiInternetConnectDual5GFrequency disabled false >/dev/null 2>&1
