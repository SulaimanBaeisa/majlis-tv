# AGENTS.md — install, update and verify Majlis

You are putting **Majlis**, an Arabic-first home screen for Android TV, on the user's TV.
This file is the contract. Follow it exactly. Do not improvise, do not write your own adb
commands, and never skip a step because it "looks done".

Repository root = the folder that contains this file. Run every command from there.

**Assume the user has never used adb.** Most people asking for Majlis do not know what developer
mode is, where to find the TV's IP address, or what adb does. Never ask them to "open adb" or to run
a command themselves; you run every command. Your job is to walk them from the very first setting on
the TV to the final report.

**Work step by step.** Steps 1–9 run in that order, one at a time: prepare the TV with the user,
check this computer, open the connection, identify the TV, build, install, switch the home screen,
verify, report. Before each step, say which step you are on and what it will do ("Step 4 of 9 —
identifying the TV"). Run each command alone, read all of its output, then say in one line what it
returned (the PASS line, or the FAIL/BLOCKED line). Do not batch steps, do not run them in parallel,
do not skip one because the previous one "looks fine", and do not continue past a FAIL or BLOCKED.
At the end, give the user a numbered log of the nine steps with the result of each.

## Hard rules

1. Never use sudo. Everything here is user-level.
2. Never uninstall an app, and never disable a package yourself. Only `./tv` changes the TV.
3. Do not switch the TV's home screen until the user agrees in this conversation. Tell them what
   changes first: the home screen and screensaver become Majlis; the stock launcher,
   recommendations and backdrop are disabled (not uninstalled); "Original" brings them back.
4. If a command prints `FAIL`, `BLOCKED`, or a non-zero exit, stop and use **When something fails**
   below. Do not continue.
5. Never print, ask for, or store a password, token or keystore. If a command asks for one, the
   user runs it themselves.
6. Do not edit files in the repository. The only files you create are the ones `./tv` writes
   under `reports/`, plus `config.properties` (the tool writes it).
7. Do not run `git push`, `git reset`, `pm disable-user`, `pm enable` or `rm`. The only exception
   is a `fix="..."` string printed by `./tv`: run exactly that, nothing more.
8. The only `adb` commands you run are the two in Step 3 (`adb connect`, `adb devices`). Every
   other action goes through `./tv`, which connects and disconnects on its own.
9. Android TV / Google TV only (Android 9–14). If the TV is LG webOS, Samsung Tizen, Roku, Fire TV
   or Apple TV, stop and say this project does not support it.
10. Never promise a 4K interface; video apps keep their own 4K plane.

## Step 1 — prepare the TV with the user (no commands yet)

Guide the user on the TV with the remote, one instruction per message, in their language. After each
one, wait until they say it is done or tell you what they see; do not send the whole list at once.

1. **Brand and system.** Ask the TV's brand, and whether its home screen says Android TV or
   Google TV. If it is LG webOS, Samsung Tizen, Roku, Fire TV or Apple TV, stop (rule 9).
2. **Same network.** The TV and this computer must be on the same Wi-Fi or router.
3. **Developer mode.** Settings → System → About → highlight **Android TV OS build** (or **Build**)
   and press OK 7 times, until the TV says "You are now a developer". Use the brand row in
   `docs/INSTALL.md` (Sony, TCL, Changhong/Classpro) when the menu names differ. If it says "already
   a developer", go on.
4. **USB debugging.** Settings → System → Developer options → **USB debugging** → On, and accept the
   warning. No cable is needed: this also allows the connection over the network. If the menu also
   has **Network debugging** or **ADB over network**, turn that on too.
5. **The IP address.** Settings → Network & Internet → the connected network → **IP address**
   (looks like `192.168.1.50`). Ask the user to read it to you. Never guess or scan for it.
6. **City, and permission.** Ask their city (prayer times and weather), and whether you may install
   Majlis and later make it the home screen.
7. **Keep the remote nearby.** In Step 3 the TV will show **Allow USB debugging?**; they will need to
   tick **Always allow** and press OK.

Done when you have the IP, the city and their permission to install.

## Step 2 — is this computer ready?

If this repository is not on the computer yet, get it first and work from inside it:

    git clone https://github.com/SulaimanBaeisa/majlis-tv && cd majlis-tv

Then:

    ./tv doctor

Expected: every line starts with `OK`, the last line is `RESULT doctor: N OK, 0 missing`, exit 0.
If a line starts with `FAIL`, run the `fix:` line under it (user-level only), then run `./tv doctor`
again. Do not continue until it is 0 missing. This is where adb gets installed if the computer does
not have it; the user does not install it by hand.

## Step 3 — open the connection

Tell the user to look at the TV now, then run:

    adb connect <IP>:5555

Expected: `connected to <IP>:5555`, or `already connected to <IP>:5555`. Then:

    adb devices

Expected: a line `<IP>:5555\tdevice` — the word `device`, not `offline` or `unauthorized`.

- `unauthorized` → ask the user to accept **Allow USB debugging?** on the TV, tick **Always allow**,
  then rerun `adb connect`.
- `cannot connect` / `connection refused` → the IP is wrong, USB debugging is off, or the TV and
  computer are on different networks. Go back to Step 1 items 2, 4 and 5 with the user; do not
  guess another address.
- Two TVs on one computer is fine: write the IP into every `./tv` command with `--ip`, never rely on
  "the only device".

## Step 4 — identify the TV

    ./tv probe --ip <IP>

Expected: a JSON block containing `"profile": "sony" | "tcl" | "changhong" | "generic"`, plus
`"sdk"`, `"model"`, `"home"` and `"disabled"`. Tell the user the brand, model and Android version
you found.

- `BLOCKED … unauthorized` → ask the user to accept **Allow USB debugging?** on the TV and tick
  **Always allow**, then rerun.
- `BLOCKED … cannot reach` → ask them to confirm the IP and that USB debugging is on.

## Step 5 — build

    ./tv build --city "<CITY>"

Expected: `PASS build Majlis.apk versionName=<x> versionCode=<n> sha256=<12 hex>`.
If it prints `BLOCKED unknown city`, ask for the nearest large city, or for latitude/longitude and run:

    ./tv build --lat <LAT> --lon <LON> --city "<CITY>"

Do not continue if the build fails.

## Step 6 — install

    ./tv install --ip <IP> --apk Majlis.apk

Expected: `PASS apk.install Majlis.apk sha256=<12 hex>` followed by a `NEXT on the TV:` line.
If it says `Majlis is already the home screen`, skip Step 7 and go straight to verify.
If it prints `BLOCKED … signed with a different key`, stop and follow `docs/SIGNING.md` with the
user. Do not uninstall anything on your own.

## Step 7 — make it the home screen (the user taps)

    ./tv switch --ip <IP> --to free --wait 180

The command opens Majlis's settings on the TV and prints `ACTION REQUIRED`. Tell the user,
in their language: *"On the TV, choose **Majlis** and press OK."* Then wait.
Expected: `PASS switch.mode home=io.freemytv/.HomeActivity`.
If it times out, the user can open the Majlis app on the TV → **Home Screen** → **Majlis**,
then rerun the same command.

## Step 8 — verify (this is the definition of done)

    ./tv verify --ip <IP> --mode free --apk Majlis.apk

Expected: `RESULT <n> PASS 0 FAIL <n> WARN <n> SKIP`, exit 0, and a `REPORT reports/verify-….md`
line. WARN and SKIP are fine. One FAIL means not done: use **When something fails**, fix, rerun.

## Step 9 — report to the user

    ./tv report --ip <IP>

Paste this, filled in, and attach/point to the report file and screenshot:

    Majlis is installed.
    TV: <brand> <model> · Android <version> · profile <sony|tcl|changhong|generic>
    APK: <versionName> (<versionCode>) · sha256 <first 12>
    Checks: <n> PASS / 0 FAIL / <n> WARN / <n> SKIP
    Report: reports/verify-<…>.md
    Screenshot: reports/shot-<…>.png

Then tell them how to go back: *Majlis app → Home Screen → Original*.

Finish with the step log, one line per step, so the user can see the whole path that ran:

    Step 1 prepare     developer mode + USB debugging on, IP 10.3.30.60, city Jeddah
    Step 2 doctor      6 OK, 0 missing
    Step 3 connection  adb connect 10.3.30.60:5555 → device
    Step 4 probe       sony · BRAVIA VH1 · Android 12
    Step 5 build       1.2.0 (4) sha256 bb1ec6f1399d
    Step 6 install     installed, hash matched
    Step 7 switch      already the home screen
    Step 8 verify      17 PASS / 0 FAIL / 1 WARN / 1 SKIP
    Step 9 report      reports/verify-….md

## Update an existing install

    ./tv update --ip <IP> --city "<CITY>"

Expected: `UPDATED <old sha> -> <new sha>`, then a verify table with 0 FAIL. If the repository has
local changes the command refuses on purpose; do not "fix" that by resetting the repo.

## Roll back

    ./tv rollback --ip <IP>

Expected: `PASS switch.mode`, `PASS rollback.home`, `PASS rollback.disabled`.
Only add `--uninstall` if the user explicitly asked to remove the app. Uninstalling alone does not
re-enable the stock launcher; rollback must run first.

## When something fails

| Output | What it means | What to do |
|---|---|---|
| `BLOCKED … unauthorized` | The TV has not trusted this computer | User accepts the prompt, tick Always allow, rerun |
| `BLOCKED … cannot reach` | Wrong IP or debugging off | Confirm IP, enable USB debugging, rerun |
| `FAIL dev.android` | Android older than 9 | Stop: unsupported TV |
| `FAIL apk.installed` | App missing | `./tv install --ip <IP> --apk Majlis.apk` |
| `FAIL apk.version` / `FAIL apk.hash` | Old or different build on the TV | Reinstall, then verify |
| `FAIL home.holder` | Not the home screen yet | `./tv switch --ip <IP> --to free --wait 180` |
| `FAIL dream.holder` | Screensaver not ours | Open Majlis → Home Screen → Majlis |
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

## What this changes on the TV (say this before Step 7)

- The home screen and screensaver become Majlis.
- Three stock packages are disabled with `pm disable-user`: the stock launcher, its recommendations,
  and the stock screensaver/backdrop. They stay installed.
- The overlay and exact-alarm permissions are granted so the adhan can appear over other apps.
- Nothing is uninstalled, no account is touched, no Google app is removed.
