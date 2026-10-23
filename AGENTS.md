# AGENTS.md — install, update and verify Mashrabiya

You are putting **Mashrabiya**, an Arabic-first home screen for Android TV, on the user's TV.
This file is the contract. Follow it exactly. Do not improvise, do not write your own adb
commands, and never skip a step because it "looks done".

Repository root = the folder that contains this file. Run every command from there.

## Hard rules

1. Never use sudo. Everything here is user-level.
2. Never uninstall an app, and never disable a package yourself. Only `./tv` changes the TV.
3. Do not switch the TV's home screen until the user agrees in this conversation. Tell them what
   changes first: the home screen and screensaver become Mashrabiya; the stock launcher,
   recommendations and backdrop are disabled (not uninstalled); "Original" brings them back.
4. If a command prints `FAIL`, `BLOCKED`, or a non-zero exit, stop and use §8. Do not continue.
5. Never print, ask for, or store a password, token or keystore. If a command asks for one, the
   user runs it themselves.
6. Do not edit files in the repository. The only files you create are the ones `./tv` writes
   under `reports/`, plus `config.properties` (the tool writes it).
7. Do not run `git push`, `git reset`, `pm disable-user`, `pm enable` or `rm`. The only exception
   is a `fix="..."` string printed by `./tv`: run exactly that, nothing more.
8. Android TV / Google TV only (Android 9–14). If the TV is LG webOS, Samsung Tizen, Roku, Fire TV
   or Apple TV, stop and say this project does not support it.
9. Never promise a 4K interface. Vendors cap the UI plane at 1080p; video apps still play in 4K.

## Ask the user once, in one message

- The TV's IP address (TV: Settings → Network & Internet → the connected network).
- Their city (for prayer times and weather).
- Permission to install and to make Mashrabiya the home screen.
- On the TV: Developer options → USB debugging ON (Settings → System → About → press the build
  number 7 times if Developer options is missing). Brand-specific paths: `docs/INSTALL.md`.

## Step 0 — is this computer ready?

    ./tv doctor

Expected: every line starts with `OK`, the last line is `RESULT doctor: N OK, 0 missing`, exit 0.
If a line starts with `FAIL`, run the `fix:` line under it (user-level only), then run `./tv doctor`
again. Do not continue until it is 0 missing.

## Step 1 — identify the TV

    ./tv probe --ip <IP>

Expected: a JSON block containing `"profile": "sony" | "tcl" | "changhong" | "generic"`, plus
`"sdk"`, `"model"`, `"home"` and `"disabled"`. Tell the user the brand, model and Android version
you found.

- `BLOCKED … unauthorized` → ask the user to accept **Allow USB debugging?** on the TV and tick
  **Always allow**, then rerun.
- `BLOCKED … cannot reach` → ask them to confirm the IP and that USB debugging is on.

## Step 2 — build

    ./tv build --city "<CITY>"

Expected: `PASS build Mashrabiya.apk versionName=<x> versionCode=<n> sha256=<12 hex>`.
If it prints `BLOCKED unknown city`, ask for the nearest large city, or for latitude/longitude and run:

    ./tv build --lat <LAT> --lon <LON> --city "<CITY>"

Do not continue if the build fails.

## Step 3 — install

    ./tv install --ip <IP> --apk Mashrabiya.apk

Expected: `PASS apk.install Mashrabiya.apk sha256=<12 hex>` followed by a `NEXT on the TV:` line.
If it says `Mashrabiya is already the home screen`, skip Step 4 and go straight to verify.
If it prints `BLOCKED … signed with a different key`, stop and follow `docs/SIGNING.md` with the
user. Do not uninstall anything on your own.

## Step 4 — make it the home screen (the user taps)

    ./tv switch --ip <IP> --to free --wait 180

The command opens Mashrabiya's settings on the TV and prints `ACTION REQUIRED`. Tell the user,
in their language: *"On the TV, choose **Mashrabiya** and press OK."* Then wait.
Expected: `PASS switch.mode home=io.freemytv/.HomeActivity`.
If it times out, the user can open the Mashrabiya app on the TV → **Home Screen** → **Mashrabiya**,
then rerun the same command.

## Step 5 — verify (this is the definition of done)

    ./tv verify --ip <IP> --mode free --apk Mashrabiya.apk

Expected: `RESULT <n> PASS 0 FAIL <n> WARN <n> SKIP`, exit 0, and a `REPORT reports/verify-….md`
line. WARN and SKIP are fine. One FAIL means not done: use §8, fix, rerun.

## Step 6 — report to the user

    ./tv report --ip <IP>

Paste this, filled in, and attach/point to the report file and screenshot:

    Mashrabiya is installed.
    TV: <brand> <model> · Android <version> · profile <sony|tcl|changhong|generic>
    APK: <versionName> (<versionCode>) · sha256 <first 12>
    Checks: <n> PASS / 0 FAIL / <n> WARN / <n> SKIP
    Report: reports/verify-<…>.md
    Screenshot: reports/shot-<…>.png

Then tell them how to go back: *Mashrabiya app → Home Screen → Original*.

## Update an existing install

    ./tv update --ip <IP> --city "<CITY>"

Expected: `UPDATED <old sha> -> <new sha>`, then a verify table with 0 FAIL. If the repository has
local changes the command refuses on purpose; do not "fix" that by resetting the repo.

## Roll back

    ./tv rollback --ip <IP>

Expected: `PASS switch.mode`, `PASS rollback.home`, `PASS rollback.disabled`.
Only add `--uninstall` if the user explicitly asked to remove the app. Uninstalling alone does not
re-enable the stock launcher; rollback must run first.

## 8. When something fails

| Output | What it means | What to do |
|---|---|---|
| `BLOCKED … unauthorized` | The TV has not trusted this computer | User accepts the prompt, tick Always allow, rerun |
| `BLOCKED … cannot reach` | Wrong IP or debugging off | Confirm IP, enable USB debugging, rerun |
| `FAIL dev.android` | Android older than 9 | Stop: unsupported TV |
| `FAIL apk.installed` | App missing | `./tv install --ip <IP> --apk Mashrabiya.apk` |
| `FAIL apk.version` / `FAIL apk.hash` | Old or different build on the TV | Reinstall, then verify |
| `FAIL home.holder` | Not the home screen yet | `./tv switch --ip <IP> --to free --wait 180` |
| `FAIL dream.holder` | Screensaver not ours | Open Mashrabiya → Home Screen → Mashrabiya |
| `FAIL mode.disabled.preserved` | Something you had disabled got re-enabled | `./tv rollback --ip <IP>`, then report a bug |
| `FAIL mode.disabled.added` | An unexpected package was disabled | `./tv rollback --ip <IP>`, then report a bug |
| `FAIL mode.protected` | A critical app (Store, YouTube…) got disabled | `./tv rollback --ip <IP>` immediately |
| `FAIL perm.granted` | A permission is missing | Reinstall; if it persists, report a bug |
| `FAIL appops.saw` / `appops.alarm` | Overlay/exact-alarm not granted | Run the `fix=` command the tool printed |
| `FAIL runtime.crash` / `runtime.anr` | The app crashed or hung | `./tv rollback --ip <IP>`; attach the report to an issue |
| `FAIL brand.quirk.*` | A brand-specific setting is wrong | Run the `fix=` command the tool printed |
| `WARN ui.screenshot` | Screen capture failed | Wake the TV and rerun verify |
| exit `3` with `BLOCKED no baseline` | This TV was never probed | `./tv probe --ip <IP>` first |

Never treat a FAIL as a pass, and never edit `reports/` to make it look green.

## What this changes on the TV (say this before Step 4)

- The home screen and screensaver become Mashrabiya.
- Three stock packages are disabled with `pm disable-user`: the stock launcher, its recommendations,
  and the stock screensaver/backdrop. They stay installed.
- The overlay and exact-alarm permissions are granted so the adhan can appear over other apps.
- Nothing is uninstalled, no account is touched, no Google app is removed.
