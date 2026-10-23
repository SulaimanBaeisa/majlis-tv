# Troubleshooting

Every line below is a literal string the tools print. Search for the code, not the sentence.

## `BLOCKED` (exit 3)

| Message | Meaning | Fix |
|---|---|---|
| `BLOCKED the TV is asking 'Allow USB debugging?'` | The TV has not trusted this computer | Accept the prompt on the TV, tick **Always allow**, rerun |
| `BLOCKED cannot reach <ip>:5555` | Wrong IP, TV asleep, or debugging off | Check the IP, wake the TV, enable USB debugging |
| `BLOCKED unknown city '<name>'` | Not in `tooling/cities.json` | Use the nearest large city, or `--lat/--lon` |
| `BLOCKED the installed app is signed with a different key` | The APK on the TV was signed by another key | See [SIGNING.md](SIGNING.md); switch to Original, uninstall, reinstall |
| `BLOCKED no baseline was recorded for this TV` | `rollback` has no recorded original state | `./tv probe --ip <IP>` first, then roll back |
| `BLOCKED the repository has local changes` | `update` refuses a dirty tree | Commit or stash; do not reset |
| `BLOCKED build failed` | Toolchain or source error | The last 1,500 characters of the build log are printed |
| `BLOCKED adb not found on PATH` | Platform tools missing | `./tv doctor` prints the install hint |

## `FAIL` checks (exit 2)

| Code | Meaning | Fix |
|---|---|---|
| `FAIL dev.android` | Android older than 9 | Unsupported TV |
| `FAIL apk.installed` | `io.freemytv` is not installed | `./tv install --ip <IP> --apk Majlis.apk` |
| `FAIL apk.version` | The package has no readable version | Reinstall |
| `FAIL apk.hash` | The installed APK differs byte-for-byte from the build | Reinstall; if it persists, check the signing channel |
| `FAIL home.holder` | Majlis is not the HOME app | `./tv switch --ip <IP> --to free --wait 180` |
| `FAIL dream.holder` | The screensaver is not Majlis | Open Majlis → Home Screen → Majlis again |
| `FAIL mode.disabled.preserved` | A package that was disabled before is now enabled | `./tv rollback --ip <IP>`; report a bug |
| `FAIL mode.disabled.added` | A package outside the allow-list was disabled | `./tv rollback --ip <IP>`; report a bug |
| `FAIL mode.protected` | A critical app (Store, GMS, YouTube, TV input) was disabled | `./tv rollback --ip <IP>` immediately |
| `FAIL perm.granted` | A core permission is missing | Reinstall; if it persists, report a bug |
| `FAIL appops.saw` | Overlay permission not granted | Run the printed `fix=` command |
| `FAIL appops.alarm` | Exact alarm not granted | Run the printed `fix=` command |
| `FAIL runtime.crash` | `io.freemytv` appears in the crash buffer | `./tv rollback --ip <IP>`; attach the report to an issue |
| `FAIL runtime.anr` | `io.freemytv` hung | `./tv rollback --ip <IP>`; attach the report |
| `FAIL brand.quirk.sony.tvx` | Sony TV input is disabled | `adb shell pm enable com.sony.dtv.tvx` |
| `FAIL brand.quirk.tcl.autostart` | TCL blocks background jobs | `adb shell appops set io.freemytv AUTO_START allow` |
| `FAIL brand.quirk.changhong.inputs` | MediaTek TV input is disabled | `adb shell pm enable com.mediatek.wwtv.tvcenter` |

## Warnings (do not fail the run)

| Code | Meaning |
|---|---|
| `WARN ui.screenshot` | `screencap` failed; wake the TV and rerun |
| `WARN perf.frames` | Frame counters (informational only) |
| `WARN mem.pss` | Memory total (informational only) |
| `WARN rollback.baseline` | The original home was not recorded before the switch; only "not ours" is checked |

## Exit codes

| Code | Meaning |
|---|---|
| `0` | All checks passed (WARN/SKIP allowed) |
| `2` | At least one FAIL |
| `3` | Blocked: offline, unauthorized, missing baseline, dirty tree |

## TV-side messages

- **"debugging is off on the TV (port 5555 closed)"** — turn USB debugging back on.
- **"Android didn't accept the new home screen"** — another launcher holds the HOME role; open
  Settings → Apps → Default apps → Home and choose Majlis.
- **The switch times out** — open the app on the TV and tap **Home Screen → Majlis** yourself;
  then rerun `./tv switch`.
- **The TV updated itself and Majlis is gone** — an OTA reset the HOME role; open Majlis
  once; its boot receiver re-applies the mode.

## Nothing works

Use [EMERGENCY.md](EMERGENCY.md). It restores the stock launcher with plain adb, without the app.
