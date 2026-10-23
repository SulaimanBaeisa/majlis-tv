#!/usr/bin/env bash
# Guard: the app's permission list and the hosts in its source must not change unnoticed.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f config.properties ] || cp config.example.properties config.properties
./build.sh >/dev/null 2>&1 || { echo "BUILD FAILED"; ./build.sh | tail -20; exit 1; }
PERMS=$(aapt2 dump permissions FreeMyTV.apk | grep -oE "uses-permission: name='[^']+'" | sort | tr '\n' ' ')
WANT="uses-permission: name='android.permission.ACCESS_NETWORK_STATE' uses-permission: name='android.permission.INTERNET' uses-permission: name='android.permission.RECEIVE_BOOT_COMPLETED' uses-permission: name='android.permission.SCHEDULE_EXACT_ALARM' uses-permission: name='android.permission.SYSTEM_ALERT_WINDOW' "  # +2 on purpose: prayer pop-up (granted by Free mode, reset by Original)
[ "$PERMS" = "$WANT" ] || { echo "PERMISSIONS CHANGED: $PERMS"; exit 1; }
HOSTS=$(grep -rhoE "https?://[A-Za-z0-9._-]+" app/src | sort -u | tr '\n' ' ')
WANT_H="https://android.googlesource.com https://api.binance.com https://api.open-meteo.com https://api.themoviedb.org https://image.tmdb.org https://news.google.com https://site.api.espn.com "  # espn: Saudi league card (public JSON, no key)
[ "$HOSTS" = "$WANT_H" ] || { echo "HOSTS CHANGED: $HOSTS"; exit 1; }
PKG=$(aapt2 dump badging FreeMyTV.apk | grep -oE "^package: name='[^']+'" )
[ "$PKG" = "package: name='io.freemytv'" ] || { echo "PACKAGE CHANGED: $PKG"; exit 1; }
echo SURFACE-OK
