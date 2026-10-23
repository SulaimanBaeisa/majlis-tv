#!/usr/bin/env bash
# Fails if the old brand leaks into the app's resources or Java sources.
set -euo pipefail
cd "$(dirname "$0")/.."
if grep -rn "Free My TV" app/res app/src 2>/dev/null | grep -v '^Binary'; then
  echo "BRAND LEAK: user-visible 'Free My TV' remains"; exit 1
fi
grep -q 'android:label="@string/app_name"' app/AndroidManifest.xml || { echo "BRAND LEAK: manifest label"; exit 1; }
echo BRAND-OK
