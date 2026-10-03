#!/system/bin/sh

MODDIR=${0%/*}
DATA_DIR=/data/adb/aks-dualsta
DATA_CONFIG="$DATA_DIR/profiles.conf"
MODULE_CONFIG="$MODDIR/profiles.conf"

mkdir -p "$DATA_DIR"
chmod 700 "$DATA_DIR"

# On the first v1.5 run, migrate the existing in-module profiles. On later
# module updates, keep the persistent copy instead of replacing it with the
# empty public template shipped in the ZIP.
# The manager saves atomically by renaming profiles.conf.tmp over profiles.conf.
# That intentionally replaces our link with a regular file. Import that file
# before recreating the link. The public ZIP template carries a marker so an
# update cannot replace an existing database with the empty shipped template.
if [ -f "$MODULE_CONFIG" ] && [ ! -L "$MODULE_CONFIG" ]; then
    if [ ! -f "$DATA_CONFIG" ] \
        || ! grep -Fq 'No private Wi-Fi credentials are shipped.' "$MODULE_CONFIG"; then
        cp -p "$MODULE_CONFIG" "$DATA_CONFIG"
    fi
fi

if [ ! -f "$DATA_CONFIG" ]; then
    cat > "$DATA_CONFIG" <<'EOF'
# priority	enabled	ssid	bssid	frequency_mhz	security	passphrase
# Add profiles with the manager app. No private Wi-Fi credentials are shipped.
EOF
fi

chmod 600 "$DATA_CONFIG"
rm -f "$MODULE_CONFIG"
ln -s "$DATA_CONFIG" "$MODULE_CONFIG"
