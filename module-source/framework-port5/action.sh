#!/system/bin/sh

echo "Framework module status:"
cat /data/local/tmp/crdroid17-dualsta-framework.log 2>/dev/null \
    || echo "No runtime log yet"
echo
cmd wifi status 2>/dev/null | grep -E 'WifiMultiInternetMode|STA \+ STA|STA \+ AP' \
    || true
