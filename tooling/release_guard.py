#!/usr/bin/env python3
"""Refuse to publish an APK that contains assets we may not redistribute."""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
private_fonts = [p.name for p in (ROOT / "app" / "assets" / "fonts").glob("thmanyah*")]
tracked = subprocess.run(["git", "-C", str(ROOT), "ls-files"], text=True, capture_output=True).stdout
leaks = [line for line in tracked.splitlines()
         if line.endswith((".keystore", ".jks", ".apk", "config.properties")) or "thmanyah" in line]
if private_fonts or leaks:
    print("RELEASE BLOCKED:", private_fonts + leaks)
    sys.exit(1)
print("RELEASE-ASSETS-OK")
