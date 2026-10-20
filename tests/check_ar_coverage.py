#!/usr/bin/env python3
"""Fails if an English string used with Ui.tr(...) (or a Spanish.java key) has no entry in Arabic.java."""
import json, re, subprocess, sys

keys = json.loads(subprocess.run([sys.executable, "tests/extract_keys.py", "--json"],
                                 capture_output=True, text=True, check=True).stdout)["keys"]
src = open("app/src/io/freemytv/Arabic.java", encoding="utf-8").read()

def unj(p):
    p = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), p)
    return p.replace("\\n", "\n").replace('\\"', '"').replace("\\\\", "\\")

have = {unj(k) for k in re.findall(r'case "((?:[^"\\]|\\.)*)":', src)}
missing = [k for k in keys if k not in have]
print(len(keys), "keys,", len(missing), "missing in Arabic.java")
for k in missing:
    print("  MISSING:", repr(k))
sys.exit(1 if missing else 0)
