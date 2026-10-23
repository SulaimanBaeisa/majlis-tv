#!/usr/bin/env python3
"""Mashrabiya TV - host-side installer and verifier. Python 3.9+, stdlib only.

Every device decision lives here: agents run the commands in AGENTS.md and report the
PASS/FAIL lines, they never write adb themselves.
"""
from __future__ import annotations

import argparse
import getpass
import hashlib
import json
import os
import platform
import re
import shlex
import shutil
import subprocess
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PRODUCT = "Mashrabiya"
PKG = "io.freemytv"
HOME_ACT = f"{PKG}/.HomeActivity"
REPORTS = ROOT / "reports"
PROFILES_DIR = ROOT / "tooling" / "profiles"
CITIES_PATH = ROOT / "tooling" / "cities.json"
ADB = shlex.split(os.environ.get("MASH_ADB", "adb"))
MIN_SDK = 28
CORE_PERMS = [
    "android.permission.INTERNET",
    "android.permission.ACCESS_NETWORK_STATE",
    "android.permission.RECEIVE_BOOT_COMPLETED",
]


class Blocked(Exception):
    """Something the agent cannot fix alone: TV offline, unauthorized, no baseline."""


def run(cmd, timeout=90, check=False):
    try:
        p = subprocess.run(cmd, text=True, capture_output=True, timeout=timeout)
    except FileNotFoundError as exc:
        raise Blocked(f"{cmd[0]} not found on PATH") from exc
    except subprocess.TimeoutExpired as exc:
        raise Blocked(f"timeout after {timeout}s: {' '.join(cmd)}") from exc
    if check and p.returncode != 0:
        raise Blocked(f"{' '.join(cmd)} failed: {(p.stderr or p.stdout).strip()[:300]}")
    return p


def adb(serial, *args, timeout=120):
    return run(ADB + ["-s", serial, *args], timeout=timeout)


def shell(serial, cmd, timeout=120):
    return adb(serial, "shell", cmd, timeout=timeout).stdout.strip()


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as fh:
        for chunk in iter(lambda: fh.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def slug(value):
    return re.sub(r"[^A-Za-z0-9._-]+", "_", str(value))


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8"))


# ---------------------------------------------------------------- profiles
def load_profiles():
    common, profiles = {}, []
    for path in sorted(PROFILES_DIR.glob("*.json")):
        data = read_json(path)
        if path.name.startswith("_"):
            common = data
            continue
        merged = dict(data)
        for key in ("allowed_disable", "must_stay_enabled"):
            merged[key] = sorted(set(common.get(key, [])) | set(data.get(key, [])))
        profiles.append(merged)
    return profiles


def load_profile(pid):
    for profile in load_profiles():
        if profile["id"] == pid:
            return profile
    return {"id": "generic", "label": "Generic Android TV", "appops": [],
            "extra_checks": [], "allowed_disable": [], "must_stay_enabled": []}


def match_profile(info):
    sdk = int(info.get("sdk") or 0)
    hay = " ".join(str(info.get(k, "")) for k in ("brand", "manufacturer", "model", "device")).lower()
    generic = None
    for profile in load_profiles():
        if profile.get("id") == "generic":
            generic = profile
            continue
        rule = profile.get("match", {})
        if "sdk_min" in rule and sdk < rule["sdk_min"]:
            continue
        if re.search(rule.get("device", "$^"), hay):
            return profile
    return generic or load_profile("generic")


# ---------------------------------------------------------------- device
def connect(ip, port=5555):
    serial = f"{ip}:{port}"
    run(ADB + ["connect", serial], timeout=25)
    state = run(ADB + ["-s", serial, "get-state"], timeout=25).stdout.strip()
    if state == "unauthorized":
        raise Blocked("the TV is asking 'Allow USB debugging?'. Accept it, tick 'Always allow', then rerun.")
    if state != "device":
        raise Blocked(f"cannot reach {serial} (state={state or 'none'}). Check the IP and that Developer options > USB debugging is on.")
    return serial


def current_home(serial):
    out = shell(serial, "cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME")
    for line in reversed(out.splitlines()):
        line = line.strip()
        if "/" in line and "priority=" not in line:
            return line
    out = shell(serial, "cmd role get-role-holders --user 0 android.app.role.HOME")
    for line in out.splitlines():
        if "/" in line:
            return line.strip()
    return ""


def probe(serial):
    props = {}
    for line in shell(serial, "getprop").splitlines():
        m = re.match(r"\[([^\]]+)\]: \[(.*)\]", line)
        if m:
            props[m.group(1)] = m.group(2)
    size = re.search(r"(\d+x\d+)", shell(serial, "wm size"))
    free_mb = 0
    df = shell(serial, "df /data").splitlines()
    if len(df) > 1:
        cols = df[-1].split()
        if len(cols) >= 4 and cols[3].isdigit():
            free_mb = int(cols[3]) // 1024
    installed = shell(serial, f"pm path {PKG}").startswith("package:")
    version = {}
    if installed:
        dump = shell(serial, f"dumpsys package {PKG}")
        code = re.search(r"versionCode=(\d+)", dump)
        name = re.search(r"versionName=(\S+)", dump)
        updated = re.search(r"lastUpdateTime=(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2})", dump)
        version = {"code": code.group(1) if code else "", "name": name.group(1) if name else "",
                   "updated": updated.group(1) if updated else ""}
    info = {
        "serial": serial,
        "sdk": int(props.get("ro.build.version.sdk") or 0),
        "brand": props.get("ro.product.brand", ""),
        "manufacturer": props.get("ro.product.manufacturer", ""),
        "model": props.get("ro.product.model", ""),
        "device": props.get("ro.product.device", ""),
        "abi": props.get("ro.product.cpu.abi", ""),
        "wm_size": size.group(1) if size else "",
        "density": shell(serial, "wm density").strip(),
        "free_mb": free_mb,
        "home": current_home(serial),
        "dreams": shell(serial, "settings get secure screensaver_components"),
        "disabled": sorted(l.split(":", 1)[1].strip() for l in shell(serial, "pm list packages -d").splitlines() if l.startswith("package:")),
        "installed": installed,
        "version": version,
    }
    info["profile"] = match_profile(info)["id"]
    return info


# ---------------------------------------------------------------- baseline
def baseline_path(serial):
    return REPORTS / f"baseline-{slug(serial)}.json"


def load_baseline(serial):
    path = baseline_path(serial)
    return read_json(path) if path.exists() else None


def save_baseline(info, force=False):
    REPORTS.mkdir(parents=True, exist_ok=True)
    path = baseline_path(info["serial"])
    if path.exists() and not force:
        return path
    keep = {k: info.get(k) for k in ("serial", "sdk", "brand", "model", "profile", "home", "dreams", "disabled", "version")}
    if str(info.get("home", "")).startswith(PKG + "/"):
        # The TV was already switched when this baseline was taken: the pre-switch home is unknown.
        keep["home"] = ""
        keep["note"] = "recorded after the switch; the pre-switch home is unknown"
    keep["recorded"] = time.strftime("%Y-%m-%dT%H:%M:%S")
    path.write_text(json.dumps(keep, ensure_ascii=False, indent=1), encoding="utf-8")
    return path


# ---------------------------------------------------------------- build
def set_prop(path, key, value):
    text = path.read_text(encoding="utf-8")
    pattern = re.compile(rf"^{re.escape(key)}\s*=.*$", re.M)
    line = f"{key}={value}"
    text = pattern.sub(line, text, count=1) if pattern.search(text) else text.rstrip("\n") + "\n" + line + "\n"
    path.write_text(text, encoding="utf-8")


def city_coords(name):
    data = read_json(CITIES_PATH)
    for key, value in data.items():
        if key.lower() == name.strip().lower():
            return value["lat"], value["lon"], key
    raise Blocked(f"unknown city '{name}'. Add it to tooling/cities.json or pass --lat and --lon.")


def build(city=None, lat=None, lon=None, unsigned=False):
    cfg = ROOT / "config.properties"
    if not cfg.exists():
        shutil.copy(ROOT / "config.example.properties", cfg)
    if city:
        if lat is None or lon is None:
            lat, lon, _ = city_coords(city)
        set_prop(cfg, "city", city)
    if lat is not None:
        set_prop(cfg, "latitude", lat)
    if lon is not None:
        set_prop(cfg, "longitude", lon)
    env = dict(os.environ)
    if unsigned:
        env["MASH_UNSIGNED"] = "1"
    proc = subprocess.run(["bash", str(ROOT / "build.sh")], cwd=str(ROOT), text=True,
                          capture_output=True, env=env)
    if proc.returncode != 0:
        raise Blocked("build failed:\n" + (proc.stdout + proc.stderr).strip()[-1500:])
    apk = ROOT / "Mashrabiya.apk"
    if not apk.exists():
        raise Blocked("build.sh succeeded but Mashrabiya.apk is missing")
    manifest = (ROOT / "app" / "AndroidManifest.xml").read_text(encoding="utf-8")
    code = re.search(r'android:versionCode="(\d+)"', manifest)
    name = re.search(r'android:versionName="([^"]+)"', manifest)
    result = {"apk": str(apk), "sha256": sha256(apk), "bytes": apk.stat().st_size,
              "versionCode": code.group(1) if code else "?", "versionName": name.group(1) if name else "?"}
    print(f"PASS build {apk.name} versionName={result['versionName']} versionCode={result['versionCode']} sha256={result['sha256'][:12]}")
    return result


# ---------------------------------------------------------------- install / switch
def install(ip, apk_path=None, switch=False, dry_run=False, port=5555, city=None, lat=None, lon=None):
    if dry_run:
        serial = f"{ip}:{port}"
        print("# dry run: no device is contacted; these are the commands")
        print(f"adb connect {serial}")
        print(f"adb -s {serial} install -r {apk_path or ROOT / 'Mashrabiya.apk'}")
        for op in ("SYSTEM_ALERT_WINDOW", "SCHEDULE_EXACT_ALARM"):
            print(f"adb -s {serial} shell appops set {PKG} {op} allow")
        return 0
    serial = connect(ip, port)
    info = probe(serial)
    profile = load_profile(info["profile"])
    save_baseline(info)
    if info["free_mb"] and info["free_mb"] < 150:
        print(f"WARN storage free={info['free_mb']} MB: install may fail; free some space on the TV")
    apk = Path(apk_path) if apk_path else Path(build(city, lat, lon)["apk"])
    if not apk.exists():
        raise Blocked(f"APK not found: {apk}")
    proc = adb(serial, "install", "-r", str(apk), timeout=600)
    text = (proc.stdout + proc.stderr).strip()
    if "Success" not in text:
        if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in text:
            raise Blocked("the installed app is signed with a different key. See docs/SIGNING.md: switch to the original home screen, uninstall, then install again.")
        raise Blocked("install failed: " + text[-400:])
    print(f"PASS apk.install {apk.name} sha256={sha256(apk)[:12]}")
    for op in profile.get("appops", []):
        shell(serial, f"appops set {PKG} {op} allow")
    if switch:
        return 0 if switch_mode(serial, "free") else 2
    if info["home"].startswith(PKG + "/"):
        print(f"Mashrabiya is already the home screen. Run: ./tv verify --ip {ip} --mode free --apk {apk.name}")
    else:
        print(f"NEXT on the TV: open {PRODUCT} -> Home Screen -> {PRODUCT}, then run: ./tv verify --ip {ip} --mode free")
    return 0


def switch_mode(serial, to, wait=180):
    if to not in ("free", "original"):
        raise Blocked("--to must be free or original")
    shell(serial, f"am start -n {PKG}/.SettingsActivity")
    label = PRODUCT if to == "free" else "Original"
    print(f"ACTION REQUIRED: on the TV choose '{label}' and press OK. Waiting up to {wait}s...")
    deadline = time.time() + wait
    while time.time() < deadline:
        home = current_home(serial)
        ours = home.startswith(PKG + "/")
        if (to == "free") == ours:
            print(f"PASS switch.mode home={home}")
            return True
        time.sleep(5)
    print(f'FAIL switch.mode timeout home={current_home(serial)} fix="open {PRODUCT} on the TV, Home Screen, choose {label}"')
    return False


# ---------------------------------------------------------------- verify
def add_check(checks, cid, status, detail="", fix=""):
    checks.append({"check": cid, "status": status, "detail": detail, "fix": fix})


def summarize(checks):
    return {s: sum(1 for c in checks if c["status"] == s) for s in ("PASS", "FAIL", "WARN", "SKIP")}


def write_report(ip, info, profile, mode, checks):
    REPORTS.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y%m%d-%H%M%S")
    stem = f"verify-{slug(info['serial'])}-{stamp}"
    payload = {"product": PRODUCT, "ip": ip, "serial": info["serial"], "mode": mode,
               "profile": profile["id"], "sdk": info["sdk"], "brand": info["brand"],
               "model": info["model"], "version": info.get("version"), "checks": checks,
               "summary": summarize(checks), "generated": stamp}
    json_path = REPORTS / f"{stem}.json"
    md_path = REPORTS / f"{stem}.md"
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=1), encoding="utf-8")
    lines = [f"# {PRODUCT} verify report", "",
             f"- TV: {info['brand']} {info['model']} (sdk {info['sdk']}, profile `{profile['id']}`)",
             f"- Mode: {mode} - APK {info.get('version', {}).get('name', '?')} ({info.get('version', {}).get('code', '?')})",
             f"- Generated: {stamp}", "", "| Check | Result | Detail | Fix |", "|---|---|---|---|"]
    for check in checks:
        lines.append(f"| `{check['check']}` | **{check['status']}** | {check['detail'][:140]} | {check['fix'][:140]} |")
    totals = summarize(checks)
    lines += ["", f"**{totals['PASS']} PASS / {totals['FAIL']} FAIL / {totals['WARN']} WARN / {totals['SKIP']} SKIP**", ""]
    md_path.write_text("\n".join(lines), encoding="utf-8")
    return json_path, md_path


def verify(ip, mode="auto", expect_apk=None, port=5555, as_json=False):
    serial = connect(ip, port)
    info = probe(serial)
    profile = load_profile(info["profile"])
    base = load_baseline(serial)
    checks = []

    sdk = info["sdk"]
    add_check(checks, "dev.android", "PASS" if sdk >= MIN_SDK else "FAIL",
              f"sdk={sdk} min={MIN_SDK}", "" if sdk >= MIN_SDK else "Android 9 or newer is required")
    add_check(checks, "dev.profile", "PASS", f"profile={profile['id']} brand={info['brand']} model={info['model']}")
    add_check(checks, "apk.installed", "PASS" if info["installed"] else "FAIL", f"pm path {PKG}",
              "" if info["installed"] else f"run ./tv install --ip {ip}")
    if info["installed"]:
        version = info["version"]
        add_check(checks, "apk.version", "PASS" if version.get("code") else "FAIL",
                  f"versionName={version.get('name')} versionCode={version.get('code')}")
    else:
        add_check(checks, "apk.version", "SKIP", "not installed")

    if expect_apk and info["installed"]:
        want = sha256(expect_apk)
        REPORTS.mkdir(parents=True, exist_ok=True)
        local = REPORTS / f"base-{slug(serial)}.apk"
        remote = shell(serial, f"pm path {PKG}").split(":", 1)[-1].strip()
        got = "unreadable"
        try:
            with open(local, "wb") as fh:
                subprocess.run(ADB + ["-s", serial, "exec-out", "cat", remote], stdout=fh,
                               stderr=subprocess.DEVNULL, timeout=300, check=False)
            if local.stat().st_size:
                got = sha256(local)
        except Exception:
            pass
        add_check(checks, "apk.hash", "PASS" if got == want else "FAIL",
                  f"device={got[:12]} local={want[:12]}",
                  "" if got == want else "the installed APK differs from this build: reinstall it")
    else:
        add_check(checks, "apk.hash", "SKIP", "pass --apk Mashrabiya.apk to compare bytes")

    ours = info["home"].startswith(PKG + "/")
    if mode == "free":
        add_check(checks, "home.holder", "PASS" if ours else "FAIL",
                  f"expected={HOME_ACT} actual={info['home']}",
                  "" if ours else f"run ./tv switch --ip {ip} --to free")
    elif mode == "original":
        want_home = (base or {}).get("home", "")
        ok = (not ours) and (not want_home or info["home"] == want_home)
        add_check(checks, "home.holder", "PASS" if ok else "FAIL",
                  f"expected={want_home or 'stock'} actual={info['home']}",
                  "" if ok else f"run ./tv rollback --ip {ip}")
    else:
        ok = ours or (base is not None and info["home"] == base["home"])
        add_check(checks, "home.holder", "PASS" if ok else "WARN", f"home={info['home']}")

    dream_ours = info["dreams"].startswith(PKG) or (PKG + "/") in info["dreams"]
    if mode == "free":
        add_check(checks, "dream.holder", "PASS" if dream_ours else "FAIL",
                  f"screensaver_components={info['dreams']}",
                  "" if dream_ours else "open Mashrabiya > Home Screen and choose Mashrabiya again")
    elif mode == "original":
        add_check(checks, "dream.holder", "PASS" if not dream_ours else "FAIL",
                  f"screensaver_components={info['dreams']}")
    else:
        add_check(checks, "dream.holder", "PASS" if dream_ours else "SKIP", f"screensaver_components={info['dreams']}")

    if base is None:
        add_check(checks, "mode.disabled.preserved", "SKIP", "no baseline recorded for this TV")
        add_check(checks, "mode.disabled.added", "SKIP", "no baseline recorded for this TV")
    else:
        before, now = set(base.get("disabled", [])), set(info["disabled"])
        reenabled = sorted(before - now)
        add_check(checks, "mode.disabled.preserved", "PASS" if not reenabled else "FAIL",
                  f"re-enabled={reenabled}", "" if not reenabled else f"run ./tv rollback --ip {ip}")
        added = sorted(now - before)
        allowed = set(profile.get("allowed_disable", []))
        home_pkg = (base.get("home") or "").split("/")[0]
        if home_pkg:
            allowed.add(home_pkg)
        unexpected = [p for p in added if p not in allowed]
        add_check(checks, "mode.disabled.added", "PASS" if not unexpected else "FAIL",
                  f"added={added}", "" if not unexpected else f"unexpected disabled packages: {unexpected}")

    installed = {l.split(":", 1)[1].strip() for l in shell(serial, "pm list packages").splitlines() if l.startswith("package:")}
    disabled_now = set(info["disabled"])
    was_disabled = set((base or {}).get("disabled", []))
    broken = sorted(p for p in profile.get("must_stay_enabled", [])
                    if p in installed and p in disabled_now and p not in was_disabled)
    add_check(checks, "mode.protected", "PASS" if not broken else "FAIL", f"disabled={broken}",
              "" if not broken else f"run ./tv rollback --ip {ip} and report this as a bug")

    dump = shell(serial, f"dumpsys package {PKG}") if info["installed"] else ""
    missing = [p for p in CORE_PERMS if not re.search(re.escape(p) + r": granted=true", dump)]
    add_check(checks, "perm.granted", "PASS" if not missing else "FAIL", f"missing={missing}")

    for cid, op in (("appops.saw", "SYSTEM_ALERT_WINDOW"), ("appops.alarm", "SCHEDULE_EXACT_ALARM")):
        out = shell(serial, f"appops get {PKG} {op}")
        if not out or "No operations" in out:
            add_check(checks, cid, "SKIP", "appop not reported by this firmware")
        else:
            allowed = "allow" in out
            add_check(checks, cid, "PASS" if allowed else "FAIL", out.replace("\n", " ")[:120],
                      "" if allowed else f"adb shell appops set {PKG} {op} allow")

    crash = shell(serial, "logcat -d -b crash -t 400")
    updated = str(info.get("version", {}).get("updated", ""))

    def crash_is_new(line):
        """Ignore crash-buffer entries older than the installed package's last update."""
        stamp = re.match(r"(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})", line.strip())
        installed_at = re.match(r"\d{4}-(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})", updated)
        if not stamp or not installed_at:
            return True
        return tuple(int(x) for x in stamp.groups()) >= tuple(int(x) for x in installed_at.groups())

    entries = [l for l in crash.splitlines() if PKG in l and crash_is_new(l)]
    add_check(checks, "runtime.crash", "PASS" if not entries else "FAIL",
              entries[0][:160] if entries else "no crash buffer entries",
              "" if not entries else f"read reports/, then ./tv rollback --ip {ip} if the TV is unusable")
    anr = shell(serial, 'logcat -d -t 800 | grep -i "ANR in ' + PKG + '" || true')
    add_check(checks, "runtime.anr", "PASS" if not anr.strip() else "FAIL",
              anr.strip().splitlines()[0][:160] if anr.strip() else "no ANR entries")

    REPORTS.mkdir(parents=True, exist_ok=True)
    shot = REPORTS / f"shot-{slug(serial)}-{time.strftime('%Y%m%d-%H%M%S')}.png"
    shot_ok = False
    try:
        with open(shot, "wb") as fh:
            subprocess.run(ADB + ["-s", serial, "exec-out", "screencap", "-p"], stdout=fh,
                           stderr=subprocess.DEVNULL, timeout=60, check=False)
        shot_ok = shot.stat().st_size > 30_000
    except Exception:
        shot_ok = False
    add_check(checks, "ui.screenshot", "PASS" if shot_ok else "WARN",
              str(shot) if shot_ok else "screencap failed", "" if shot_ok else "check that the TV is awake")

    gfx = shell(serial, f"dumpsys gfxinfo {PKG}")
    frames = re.search(r"Total frames rendered:\s*(\d+)", gfx)
    janky = re.search(r"Janky frames:\s*(\d+)", gfx)
    add_check(checks, "perf.frames", "WARN" if frames else "SKIP",
              f"frames={frames.group(1) if frames else '?'} janky={janky.group(1) if janky else '?'}")
    mem = shell(serial, f"dumpsys meminfo {PKG} | grep TOTAL")
    add_check(checks, "mem.pss", "WARN" if mem else "SKIP", mem.replace("\n", " ")[:120] or "no meminfo")

    for quirk in profile.get("extra_checks", []):
        out = shell(serial, quirk["cmd"])
        if "expect" in quirk:
            ok = quirk["expect"] in out
        else:
            ok = all(value not in out for value in quirk.get("expect_absent", []))
        add_check(checks, f"brand.quirk.{quirk['id']}", "PASS" if ok else "FAIL",
                  out.replace("\n", " ")[:120], "" if ok else quirk.get("fix", ""))

    totals = summarize(checks)
    json_path, md_path = write_report(ip, info, profile, mode, checks)
    if as_json:
        print(json.dumps({"summary": totals, "checks": checks, "report": str(md_path)}, ensure_ascii=False, indent=1))
    else:
        for check in checks:
            fix = f' fix="{check["fix"]}"' if check["fix"] else ""
            print(f'{check["status"]} {check["check"]} {check["detail"]}{fix}')
        print(f"RESULT {totals['PASS']} PASS {totals['FAIL']} FAIL {totals['WARN']} WARN {totals['SKIP']} SKIP")
        print(f"REPORT {md_path}")
    return 2 if totals["FAIL"] else 0


# ---------------------------------------------------------------- commands
def cmd_probe(args):
    serial = connect(args.ip, args.port)
    info = probe(serial)
    REPORTS.mkdir(parents=True, exist_ok=True)
    (REPORTS / f"probe-{slug(serial)}.json").write_text(json.dumps(info, ensure_ascii=False, indent=1), encoding="utf-8")
    print(json.dumps(info, ensure_ascii=False, indent=1))
    return 0


def cmd_build(args):
    build(args.city, args.lat, args.lon, args.unsigned)
    return 0


def cmd_install(args):
    return install(args.ip, args.apk, args.switch, args.dry_run, args.port, args.city, args.lat, args.lon)


def cmd_switch(args):
    serial = connect(args.ip, args.port)
    return 0 if switch_mode(serial, args.to, args.wait) else 2


def cmd_verify(args):
    return verify(args.ip, args.mode, args.apk, args.port, args.as_json)


def cmd_update(args):
    return update(args.ip, args.port, args.city)


def cmd_rollback(args):
    return rollback(args.ip, args.uninstall, args.port)


def cmd_report(args):
    serial = connect(args.ip, args.port)
    reports = sorted(REPORTS.glob(f"verify-{slug(serial)}-*.md"), key=lambda p: p.stat().st_mtime)
    if not reports:
        raise Blocked("no verify report for this TV yet; run ./tv verify first")
    print(reports[-1].read_text(encoding="utf-8"))
    return 0


def cmd_sign(args):
    source = Path(args.keystore).expanduser()
    if not source.exists():
        raise Blocked(f"keystore not found: {source}")
    dest = Path.home() / ".config" / "mashrabiya" / "release.keystore"
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, dest)
    os.chmod(dest, 0o600)
    print(f"Copied to {dest} (mode 600)")
    store_pass = getpass.getpass("Keystore password: ")
    key_pass = getpass.getpass("Key password (blank = same): ") or store_pass
    alias = input("Key alias [mashrabiya]: ").strip() or "mashrabiya"
    local = ROOT / "local.properties"
    local.write_text(f"keystore={dest}\nks_pass={store_pass}\nkey_alias={alias}\nkey_pass={key_pass}\n", encoding="utf-8")
    os.chmod(local, 0o600)
    print("Wrote local.properties (mode 600). Now run ./tv build and check the certificate line.")
    return 0


def cmd_version(args):
    manifest = (ROOT / "app" / "AndroidManifest.xml").read_text(encoding="utf-8")
    code = re.search(r'android:versionCode="(\d+)"', manifest)
    name = re.search(r'android:versionName="([^"]+)"', manifest)
    apk = ROOT / "Mashrabiya.apk"
    print(f"{PRODUCT} {name.group(1) if name else '?'} (versionCode {code.group(1) if code else '?'})")
    if apk.exists():
        print(f"APK {apk} sha256={sha256(apk)}")
    return 0


def update(ip, port=5555, city=None):
    dirty = run(["git", "-C", str(ROOT), "status", "--porcelain"]).stdout.strip()
    if dirty:
        raise Blocked("the repository has local changes; commit or stash them before ./tv update")
    before = run(["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"]).stdout.strip()
    run(["git", "-C", str(ROOT), "pull", "--ff-only"], check=True, timeout=180)
    after = run(["git", "-C", str(ROOT), "rev-parse", "--short", "HEAD"]).stdout.strip()
    built = build(city)
    code = install(ip, built["apk"], port=port)
    if code:
        return code
    print(f"UPDATED {before} -> {after}")
    return verify(ip, mode="auto", expect_apk=built["apk"], port=port)


def rollback(ip, uninstall=False, port=5555):
    serial = connect(ip, port)
    base = load_baseline(serial)
    if not base:
        raise Blocked("no baseline was recorded for this TV. Refusing to guess; see docs/EMERGENCY.md.")
    if not switch_mode(serial, "original"):
        return 2
    home = current_home(serial)
    if not base.get("home"):
        print("WARN rollback.baseline the original home was not recorded; checking only that Mashrabiya is no longer HOME")
    ok = bool(home) and (home == base.get("home") or not home.startswith(PKG + "/"))
    print(("PASS" if ok else "FAIL") + f" rollback.home expected={base.get('home')} actual={home}")
    if ok:
        now = {l.split(":", 1)[1].strip() for l in shell(serial, "pm list packages -d").splitlines() if l.startswith("package:")}
        extra = sorted(now - set(base.get("disabled", [])))
        print(("PASS" if not extra else "WARN") + f" rollback.disabled still_disabled={extra}")
    if uninstall:
        adb(serial, "uninstall", PKG)
        print("PASS rollback.uninstall")
    return 0 if ok else 2


# ---------------------------------------------------------------- host checks
def java_version():
    candidates = []
    if os.environ.get("JAVA_HOME"):
        candidates.append(str(Path(os.environ["JAVA_HOME"]) / "bin" / "java"))
    if shutil.which("java"):
        candidates.append(shutil.which("java"))
    for exe in candidates:
        proc = run([exe, "-version"], timeout=20)
        match = re.search(r'version "(\d+)', proc.stderr + proc.stdout)
        if match:
            return int(match.group(1)), exe
    return 0, ""


def doctor():
    rows = []

    def add(tool, ok, detail, fix="", required=True):
        rows.append((tool, ok, detail, fix, required))

    add("python", sys.version_info >= (3, 9), platform.python_version(), "Install Python 3.9 or newer")
    adb_path = shutil.which(ADB[0])
    adb_detail = (run([adb_path, "version"]).stdout.splitlines() or ["unknown"])[0] if adb_path else "not found"
    add("adb", bool(adb_path), adb_detail, "Install Android platform-tools and put adb on PATH")
    major, exe = java_version()
    add("java", major >= 17, f"{major or 'not found'} ({exe or '-'})", "Install JDK 17 (Temurin) and set JAVA_HOME")
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    build_tools = sorted((Path(sdk) / "build-tools").glob("*")) if sdk and (Path(sdk) / "build-tools").is_dir() else []
    platforms = sorted((Path(sdk) / "platforms").glob("android-*")) if sdk and (Path(sdk) / "platforms").is_dir() else []
    hint = "Install cmdline-tools, then: sdkmanager 'platform-tools' 'build-tools;34.0.0' 'platforms;android-34'"
    if (Path.home() / "tools" / "android-env.sh").exists():
        hint += "; on this machine: source ~/tools/android-env.sh"
    add("sdk", bool(sdk and build_tools and platforms),
        f"{sdk or 'ANDROID_HOME unset'} bt={build_tools[-1].name if build_tools else '-'} platform={platforms[-1].name if platforms else '-'}",
        hint)
    add("git", bool(shutil.which("git")), shutil.which("git") or "not found", "Install git", required=False)
    add("os", platform.system() != "Windows", platform.system(), "Use WSL on Windows", required=False)

    for tool, ok, detail, fix, required in rows:
        print(f"{'OK  ' if ok else ('FAIL' if required else 'WARN')} {tool:<8} {detail}")
        if not ok and fix:
            print(f"     fix: {fix}")
    missing = [row for row in rows if row[4] and not row[1]]
    print(f"RESULT doctor: {len(rows) - len(missing)} OK, {len(missing)} missing")
    return 1 if missing else 0


# ---------------------------------------------------------------- entry point
def main(argv=None):
    parser = argparse.ArgumentParser(prog="tv", description=f"{PRODUCT} installer and verifier")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("doctor", help="check the host tools")
    p.set_defaults(func=lambda a: doctor())

    p = sub.add_parser("probe", help="identify the TV and match a brand profile")
    p.add_argument("--ip", required=True)
    p.add_argument("--port", type=int, default=5555)
    p.set_defaults(func=cmd_probe)

    p = sub.add_parser("build", help="build Mashrabiya.apk")
    p.add_argument("--city")
    p.add_argument("--lat", type=float)
    p.add_argument("--lon", type=float)
    p.add_argument("--unsigned", action="store_true")
    p.set_defaults(func=cmd_build)

    p = sub.add_parser("install", help="install the APK on the TV")
    p.add_argument("--ip", required=True)
    p.add_argument("--apk")
    p.add_argument("--city")
    p.add_argument("--lat", type=float)
    p.add_argument("--lon", type=float)
    p.add_argument("--switch", action="store_true")
    p.add_argument("--dry-run", action="store_true")
    p.add_argument("--port", type=int, default=5555)
    p.set_defaults(func=cmd_install)

    p = sub.add_parser("switch", help="switch between Mashrabiya and the original home screen")
    p.add_argument("--ip", required=True)
    p.add_argument("--to", choices=["free", "original"], required=True)
    p.add_argument("--wait", type=int, default=180)
    p.add_argument("--port", type=int, default=5555)
    p.set_defaults(func=cmd_switch)

    p = sub.add_parser("verify", help="run the 19 device checks")
    p.add_argument("--ip", required=True)
    p.add_argument("--mode", choices=["auto", "free", "original"], default="auto")
    p.add_argument("--apk")
    p.add_argument("--json", action="store_true", dest="as_json")
    p.add_argument("--port", type=int, default=5555)
    p.set_defaults(func=cmd_verify)

    p = sub.add_parser("update", help="git pull, rebuild, reinstall, verify")
    p.add_argument("--ip", required=True)
    p.add_argument("--city")
    p.add_argument("--port", type=int, default=5555)
    p.set_defaults(func=cmd_update)

    p = sub.add_parser("rollback", help="restore the recorded original home screen")
    p.add_argument("--ip", required=True)
    p.add_argument("--uninstall", action="store_true")
    p.add_argument("--port", type=int, default=5555)
    p.set_defaults(func=cmd_rollback)

    p = sub.add_parser("report", help="print the last verify report")
    p.add_argument("--ip", required=True)
    p.add_argument("--port", type=int, default=5555)
    p.set_defaults(func=cmd_report)

    p = sub.add_parser("version", help="print the app version and APK hash")
    p.set_defaults(func=cmd_version)

    p = sub.add_parser("sign", help="adopt an existing signing key (run this yourself, not via an agent)")
    sign_sub = p.add_subparsers(dest="sign_command", required=True)
    adopt = sign_sub.add_parser("adopt")
    adopt.add_argument("--keystore", required=True)
    adopt.set_defaults(func=cmd_sign)

    args = parser.parse_args(argv)
    try:
        return args.func(args)
    except Blocked as exc:
        print(f"BLOCKED {exc}")
        return 3


if __name__ == "__main__":
    sys.exit(main())
