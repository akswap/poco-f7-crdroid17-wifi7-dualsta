#!/system/bin/sh

MODDIR=${0%/*}
SRC="$MODDIR/service-wifi.jar"
DST=/apex/com.android.wifi/javalib/service-wifi.jar
LOG=/data/local/tmp/crdroid17-dualsta-framework.log
EXPECTED_STOCK_SHA256=5762cc5e14e867639d3ac47928e1a4af509b4fe2cfc9f3c46447ec6cdf823a19
EXPECTED_PATCHED_SHA256=736528d2c6a650a737848e7afaa8cee153194d83a550adf1d74aed31a0b8c9c2

fail_closed() {
    echo "$(date '+%Y-%m-%d %H:%M:%S') REFUSED: $*" >> "$LOG"
    touch "$MODDIR/disable"
    exit 1
}

[ -f "$SRC" ] || fail_closed "patched jar missing"
[ -f "$DST" ] || fail_closed "Wi-Fi APEX jar missing"

stock_hash="$(sha256sum "$DST" 2>/dev/null | awk '{print $1}')"
patched_hash="$(sha256sum "$SRC" 2>/dev/null | awk '{print $1}')"
[ "$stock_hash" = "$EXPECTED_STOCK_SHA256" ] \
    || fail_closed "stock hash mismatch: $stock_hash"
[ "$patched_hash" = "$EXPECTED_PATCHED_SHA256" ] \
    || fail_closed "patched hash mismatch: $patched_hash"

chown root:root "$SRC" || fail_closed "chown failed"
chmod 0644 "$SRC" || fail_closed "chmod failed"
chcon u:object_r:system_file:s0 "$SRC" 2>/dev/null \
    || fail_closed "SELinux label failed"
mount -o bind "$SRC" "$DST" || fail_closed "bind mount failed"

live_hash="$(sha256sum "$DST" 2>/dev/null | awk '{print $1}')"
[ "$live_hash" = "$EXPECTED_PATCHED_SHA256" ] \
    || fail_closed "mounted readback hash mismatch: $live_hash"

echo "$(date '+%Y-%m-%d %H:%M:%S') mounted patched crDroid17 Wi-Fi framework $live_hash" > "$LOG"
