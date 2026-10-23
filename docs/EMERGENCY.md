# Emergency: get the stock home screen back

Use this only if `./tv rollback` cannot run because the app will not open, or the TV shows no home
screen. It uses plain adb. **Read the whole page first.**

## 1. Find the stock launcher and screensaver

```bash
adb -s <IP>:5555 shell pm list packages | grep -E 'launcher|backdrop|dream'
adb -s <IP>:5555 shell settings get secure screensaver_default_component
```

Typical values:

| Brand | HOME package | Activity |
|---|---|---|
| Sony / most Android TV | `com.google.android.tvlauncher` | `com.google.android.tvlauncher/.tv.HomeActivity` |
| TCL / Google TV | `com.google.android.apps.tv.launcherx` | `com.google.android.apps.tv.launcherx/.home.HomeActivity` |
| Changhong / Classpro | `com.google.android.tvlauncher` | `com.google.android.tvlauncher/.tv.HomeActivity` |

If `cmd package resolve-activity` works, ask it instead:

```bash
adb -s <IP>:5555 shell cmd package resolve-activity --brief --user 0 \
  -a android.intent.action.MAIN -c android.intent.category.HOME
```

If the answer is `io.freemytv/...`, the stock one is in the list above.

## 2. Set HOME back

```bash
adb -s <IP>:5555 shell cmd package set-home-activity com.google.android.tvlauncher/.tv.HomeActivity
# TCL:
adb -s <IP>:5555 shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
```

Expected: `Success`.

## 3. Re-enable the packages Majlis disabled

```bash
adb -s <IP>:5555 shell pm enable com.google.android.tvlauncher
adb -s <IP>:5555 shell pm enable com.google.android.tvrecommendations
adb -s <IP>:5555 shell pm enable com.google.android.backdrop
# TCL also:
adb -s <IP>:5555 shell pm enable com.google.android.apps.tv.launcherx
```

Only enable packages that were disabled *by Majlis*. Do not enable a package that was already
disabled at the factory (the baseline in `reports/baseline-<serial>.json` lists them).

## 4. Clear our screensaver

```bash
adb -s <IP>:5555 shell settings delete secure screensaver_components
adb -s <IP>:5555 shell settings put secure screensaver_enabled 1
```

## 5. Press HOME

```bash
adb -s <IP>:5555 shell input keyevent KEYCODE_HOME
```

The stock launcher should appear.

## 6. Remove the app (optional)

```bash
adb -s <IP>:5555 uninstall io.freemytv
```

Do this **after** steps 2–4. Uninstalling first leaves the TV with no HOME app.

## If the TV is stuck on a black screen

1. Reboot the TV with the remote (hold power, or unplug for 30 seconds).
2. Connect adb as soon as the network is up and run steps 2–4.
3. If adb never comes back, a factory reset from the TV's recovery menu is the last resort. It
   removes all apps and accounts; only do it with the owner's explicit consent.
