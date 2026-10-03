# Port5 notes

Port4 successfully created `wlan1` and replayed cached scan results after the secondary client-mode manager became ready. New BSSIDs still required Android's one-time network-request approval, so unattended requests could time out even when the AP was visible.

Port5 keeps the Port4 scheduling behavior and bypasses the saved-AP approval check only when all of these conditions are true:

- the request UID is root (`0`);
- the request explicitly prefers the secondary STA;
- the request targets one exact SSID/BSSID profile.

Normal app requests and Android's primary Wi-Fi flow retain the stock approval behavior. The controller remains the owner of the root request and profile lifecycle.

Verified live framework SHA256:

`736528d2c6a650a737848e7afaa8cee153194d83a550adf1d74aed31a0b8c9c2`
