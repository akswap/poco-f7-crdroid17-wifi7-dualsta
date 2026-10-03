#!/system/bin/sh

MODDIR=$(dirname "$0")
JAR="$MODDIR/dualsta-helper-config.jar"
CONFIG="$MODDIR/profiles.conf"
DATA_DIR=/data/adb/aks-dualsta
DATA_CONFIG="$DATA_DIR/profiles.conf"
LOG=/data/local/tmp/dualsta-autoconnect.log
HELPER_LOG=/data/local/tmp/dualsta-helper.log
PIDFILE=/data/local/tmp/dualsta-helper.pid
PROFILE_WAIT_SECONDS=45
HOTSPOT_POLL_SECONDS=1
HOTSPOT_CLEAR_SECONDS=6
FRAMEWORK_JAR=/apex/com.android.wifi/javalib/service-wifi.jar
EXPECTED_FRAMEWORK_SHA256=736528d2c6a650a737848e7afaa8cee153194d83a550adf1d74aed31a0b8c9c2

log_msg() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG"
}

secondary_connected() {
    /system/bin/iw dev wlan1 link 2>/dev/null | grep -q '^Connected to '
}

hotspot_active() {
    [ -e /sys/class/net/wlan2 ] || [ -e /sys/class/net/ap0 ] \
        || /system/bin/iw dev wlan1 info 2>/dev/null | grep -q 'type AP'
}

stop_helper() {
    [ -f "$PIDFILE" ] || return 0
    pid="$(cat "$PIDFILE" 2>/dev/null)"
    if [ -n "$pid" ] && kill -0 "$pid" 2>/dev/null; then
        kill "$pid" 2>/dev/null
        sleep 1
    fi
    rm -f "$PIDFILE"
}

enabled_count() {
    awk -F '	' '!/^#/ && NF == 7 && $2 == "1" {n++} END {print n+0}' "$CONFIG" 2>/dev/null
}

start_profile() {
    index="$1"
    [ -f "$JAR" ] || return 1
    [ -f "$CONFIG" ] || return 1
    cmd wifi force-overlay-config-value bool config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled enabled false >/dev/null 2>&1
    service call wifi 174 i32 2 >/dev/null 2>&1
    cmd wifi force-overlay-config-value bool config_wifiAllowMultiInternetConnectDual5GFrequency enabled true >/dev/null 2>&1
    CLASSPATH="$JAR" app_process /system/bin DualStaRequest "$CONFIG" "$index" >> "$HELPER_LOG" 2>&1 &
    echo "$!" > "$PIDFILE"
    log_msg "secondary profile index=$index requested pid=$!"
}

wait_hotspot_end() {
    stop_helper
    log_msg "hotspot detected; secondary STA suspended"
    clear_seconds=0
    while [ "$clear_seconds" -lt "$HOTSPOT_CLEAR_SECONDS" ]; do
        if hotspot_active; then
            clear_seconds=0
        else
            clear_seconds=$((clear_seconds + HOTSPOT_POLL_SECONDS))
        fi
        sleep "$HOTSPOT_POLL_SECONDS"
    done
    log_msg "hotspot ended; secondary STA resume scheduled"
}

mkdir -p "$DATA_DIR"
chmod 700 "$DATA_DIR" 2>/dev/null
if [ -f "$CONFIG" ] && [ ! -L "$CONFIG" ]; then
    if [ ! -f "$DATA_CONFIG" ] \
        || ! grep -Fq 'No private Wi-Fi credentials are shipped.' "$CONFIG"; then
        cp -p "$CONFIG" "$DATA_CONFIG" 2>/dev/null
    fi
fi
if [ -f "$DATA_CONFIG" ]; then
    rm -f "$CONFIG"
    ln -s "$DATA_CONFIG" "$CONFIG"
fi

until [ "$(getprop sys.boot_completed)" = "1" ]; do
    sleep 2
done
sleep 10
framework_hash="$(sha256sum "$FRAMEWORK_JAR" 2>/dev/null | awk '{print $1}')"
if [ "$framework_hash" != "$EXPECTED_FRAMEWORK_SHA256" ]; then
    log_msg "REFUSED: paired framework port5 hash mismatch: $framework_hash"
    exit 1
fi
cmd wifi force-overlay-config-value bool config_wifiMultiStaNetworkSwitchingMakeBeforeBreakEnabled enabled false >/dev/null 2>&1
cmd wifi force-overlay-config-value bool config_wifiAllowMultiInternetConnectDual5GFrequency enabled true >/dev/null 2>&1
cmd wifi set-multi-internet-mode 2 >/dev/null 2>&1
chmod 600 "$CONFIG" 2>/dev/null
log_msg "crDroid17 port5 secondary-only hotspot-aware watchdog started framework=$framework_hash timeout=${PROFILE_WAIT_SECONDS}s"

while true; do
    if hotspot_active; then
        wait_hotspot_end
        continue
    fi

    if secondary_connected; then
        sleep 1
        continue
    fi

    count="$(enabled_count)"
    if [ "$count" -lt 1 ]; then
        stop_helper
        sleep 10
        continue
    fi

    index=0
    hotspot_seen=0
    connected=0

    while [ "$index" -lt "$count" ]; do
        if hotspot_active; then
            hotspot_seen=1
            break
        fi

        stop_helper
        start_profile "$index"
        waited=0

        while [ "$waited" -lt "$PROFILE_WAIT_SECONDS" ]; do
            sleep 1
            if hotspot_active; then
                stop_helper
                hotspot_seen=1
                break
            fi
            if secondary_connected; then
                connected=1
                break
            fi
            waited=$((waited + 1))
        done

        [ "$hotspot_seen" = "1" ] && break
        [ "$connected" = "1" ] && break
        index=$((index + 1))
    done

    [ "$hotspot_seen" = "1" ] && continue
    [ "$connected" = "1" ] && continue
    sleep 2
done
