import contextlib
import importlib.util
import io
import json
import os
import re
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FAKE = str(ROOT / "tests" / "tools" / "fake_adb.py")
spec = importlib.util.spec_from_file_location("tv", ROOT / "tooling" / "tv.py")
tv = importlib.util.module_from_spec(spec)
sys.modules["tv"] = tv
spec.loader.exec_module(tv)

PROPS = {
    "sony": ("Sony", "Sony", "BRAVIA 4K GB", "BRAVIA_ATV3_4K", 32),
    "tcl": ("TCL", "TCL", "Smart TV Pro G08", "TCL", 34),
    "changhong": ("Changhong", "Changhong", "AI PONT", "m7322", 30),
    "generic": ("Acme", "Acme", "Box", "box", 31),
}


def responses(profile="sony", home="io.freemytv/.HomeActivity", disabled=None, brand_override=None):
    brand, maker, model, device, sdk = PROPS[profile]
    if brand_override:
        brand = brand_override
    return {
        "get-state": "device",
        "getprop": "\n".join([
            f"[ro.product.brand]: [{brand}]", f"[ro.product.manufacturer]: [{maker}]",
            f"[ro.product.model]: [{model}]", f"[ro.product.device]: [{device}]",
            f"[ro.build.version.sdk]: [{sdk}]", "[ro.product.cpu.abi]: [armeabi-v7a]"]),
        "wm size": "Physical size: 1920x1080",
        "wm density": "Physical density: 320",
        "pm list packages -d": "\n".join("package:" + p for p in (disabled if disabled is not None else [
            "com.google.android.tvlauncher", "com.google.android.tvrecommendations", "com.google.android.backdrop"])),
        "pm path io.freemytv": "package:/data/app/~~x/io.freemytv-y/base.apk",
        "cmd package resolve-activity --brief --user 0 -a android.intent.action.MAIN -c android.intent.category.HOME": f"priority=0\n{home}",
        "settings get secure screensaver_components": "io.freemytv/io.freemytv.ScreensaverAlone",
        "df /data": "Filesystem 1K-blocks Used Available Use% Mounted on\n/dev/block/dm-0 1000000 500000 400000 56% /data",
        "dumpsys package io.freemytv": "\n".join([
            "versionCode=2 minSdk=28 targetSdk=33", "versionName=1.0.0",
            "android.permission.INTERNET: granted=true",
            "android.permission.ACCESS_NETWORK_STATE: granted=true",
            "android.permission.RECEIVE_BOOT_COMPLETED: granted=true"]),
        "pm list packages": "package:com.android.vending\npackage:com.google.android.gms\npackage:io.freemytv",
        "appops get io.freemytv SYSTEM_ALERT_WINDOW": "SYSTEM_ALERT_WINDOW: allow",
        "appops get io.freemytv SCHEDULE_EXACT_ALARM": "SCHEDULE_EXACT_ALARM: allow",
        "logcat -d -b crash -t 400": "",
        'logcat -d -t 800 | grep -i "ANR in io.freemytv" || true': "",
        "dumpsys gfxinfo io.freemytv": "Total frames rendered: 1200\nJanky frames: 20 (1.6%)",
        "dumpsys meminfo io.freemytv | grep TOTAL": "TOTAL 123456",
        "pm list packages -e com.sony.dtv.tvx": "package:com.sony.dtv.tvx",
        "appops get io.freemytv AUTO_START || appops get io.freemytv APP_AUTO_START": "AUTO_START: allow",
        "pm list packages -e com.mediatek.wwtv.tvcenter": "package:com.mediatek.wwtv.tvcenter",
    }


class Fake:
    def __init__(self, **overrides):
        self.tmp = tempfile.TemporaryDirectory()
        self.dir = Path(self.tmp.name)
        self.responses = responses()
        self.responses.update(overrides)

    def __enter__(self):
        (self.dir / "responses.json").write_text(json.dumps(self.responses))
        os.environ["MASH_FAKE_DIR"] = str(self.dir)
        tv.ADB = [sys.executable, FAKE]
        tv.REPORTS = self.dir / "reports"
        return self

    def __exit__(self, *exc):
        self.tmp.cleanup()


def baseline(home="com.google.android.tvlauncher/.tv.HomeActivity", disabled=None):
    return {"serial": "10.0.0.2:5555", "sdk": 32, "brand": "Sony", "model": "X", "profile": "sony",
            "home": home, "dreams": "null", "disabled": disabled or [], "version": {}}


class ProbeTests(unittest.TestCase):
    def test_sony(self):
        with Fake():
            info = tv.probe("10.0.0.2:5555")
        self.assertEqual(info["profile"], "sony")
        self.assertEqual(info["home"], "io.freemytv/.HomeActivity")
        self.assertEqual(info["sdk"], 32)

    def test_tcl(self):
        with Fake(**responses("tcl")):
            self.assertEqual(tv.probe("10.0.0.3:5555")["profile"], "tcl")

    def test_changhong(self):
        with Fake(**responses("changhong")):
            self.assertEqual(tv.probe("10.0.0.4:5555")["profile"], "changhong")

    def test_generic(self):
        with Fake(**responses("generic")):
            self.assertEqual(tv.probe("10.0.0.5:5555")["profile"], "generic")


class VerifyTests(unittest.TestCase):
    def test_free_mode_passes(self):
        with Fake():
            tv.save_baseline(baseline())
            rc = tv.verify("10.0.0.2", mode="free")
        self.assertEqual(rc, 0)

    def test_wrong_home_fails(self):
        with Fake(**responses(home="com.google.android.tvlauncher/.tv.HomeActivity")):
            tv.save_baseline(baseline())
            buf = io.StringIO()
            with contextlib.redirect_stdout(buf):
                rc = tv.verify("10.0.0.2", mode="free")
        self.assertEqual(rc, 2)
        self.assertIn("FAIL home.holder", buf.getvalue())

    def test_reenabled_package_fails(self):
        with Fake(**responses(disabled=["com.google.android.tvrecommendations"])):
            tv.save_baseline(baseline(disabled=["com.google.android.tvlauncher", "com.example.keep"]))
            rc = tv.verify("10.0.0.2", mode="free")
        self.assertEqual(rc, 2)


class CliTests(unittest.TestCase):
    def test_install_dry_run(self):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            rc = tv.main(["install", "--ip", "10.0.0.9", "--dry-run"])
        self.assertEqual(rc, 0)
        self.assertIn("adb connect 10.0.0.9:5555", buf.getvalue())

    def test_city_lookup(self):
        lat, lon, name = tv.city_coords("Jeddah")
        self.assertEqual(name, "Jeddah")
        self.assertLess(abs(lat - 21.49), 0.2)
        self.assertLess(abs(lon - 39.19), 0.2)

    def test_build_has_no_hardcoded_password(self):
        text = (ROOT / "build.sh").read_text()
        self.assertNotIn("pass:freemytv", text)
        self.assertNotIn("-storepass freemytv", text)

    def test_verify_reports_are_written(self):
        with Fake() as fake:
            tv.save_baseline(baseline())
            tv.verify("10.0.0.2", mode="free")
            reports = list((fake.dir / "reports").glob("verify-*.md"))
        self.assertEqual(len(reports), 1)


if __name__ == "__main__":
    unittest.main()
