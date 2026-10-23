# Install

Mashrabiya runs on Android TV / Google TV 9–14. The agent path is [AGENTS.md](../AGENTS.md);
this page is the human version and the brand-specific details.

## Before you start

- The TV and the computer must be on the same network.
- You need the TV's IP: **Settings → Network & Internet →** the connected network.
- About 10 minutes and ~35 MB of disk on the computer (JDK + SDK if missing).

## 1. Developer mode on the TV

| Brand | Path |
|---|---|
| Sony BRAVIA | Settings → System → About → press **Android TV OS build** 7 times; then Settings → System → Developer options → **USB debugging** on |
| TCL Google TV | Settings → System → About → press **Android TV OS build** 7 times; then Settings → System → Developer options → **USB debugging** on |
| Changhong / Classpro | Settings → Device Preferences → About → press **Build** 7 times; then Developer options → **USB debugging** on |
| Other | Settings → System → About → press **Build** 7 times; Developer options → USB debugging |

Keep **USB debugging** on: Mashrabiya's Free/Original switch uses the TV's own loopback ADB
(`127.0.0.1:5555`), which only listens while debugging is on. If the switch ever says
"debugging is off on the TV", turn it back on.

## 2. Trust the computer

The first `adb connect` shows **Allow USB debugging?** on the TV. Tick **Always allow** and accept.

## 3. Build and install

```bash
git clone https://github.com/OWNER/mashrabiya-tv && cd mashrabiya-tv
./tv doctor
./tv build --city "Jeddah"
./tv install --ip 192.168.1.50
```

`./tv doctor` prints the exact install command for anything missing (user-level; no sudo).

## 4. Make it the home screen

On the TV: open **Mashrabiya** → **Home Screen** → **Mashrabiya** → OK.
The app will ask for debugging permission once more; accept it.

Or let the tool wait for you:

```bash
./tv switch --ip 192.168.1.50 --to free --wait 180
```

## 5. Verify

```bash
./tv verify --ip 192.168.1.50 --mode free --apk Mashrabiya.apk
```

The last lines must read `RESULT … 0 FAIL` and `REPORT reports/verify-….md`.

## Going back

Mashrabiya app → **Home Screen** → **Original** → OK. The stock launcher, recommendations,
backdrop and screensaver come back; nothing was uninstalled.

If the app will not open, use [EMERGENCY.md](EMERGENCY.md).

## Brand notes

- **Sony:** some sets leave five packages disabled at the factory; the baseline records them and
  `verify` will not "fix" them. The TV input (`com.sony.dtv.tvx`) stays enabled.
- **TCL:** `TclAppBoot` drops manifest broadcasts and blocks `ModeJob`; the app runs the switch
  in-process and keeps its adhan receiver in `:home`. `appops AUTO_START` (or `APP_AUTO_START`) is
  required and checked by the `tcl` profile.
- **Changhong / Classpro:** Android 11 uses the separate `ScreensaverAlone` component; the
  `changhong` profile checks that the MediaTek TV input stays enabled.
- **Everything else:** profile `generic`. Run `./tv verify` before trusting it.
