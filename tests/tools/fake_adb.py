#!/usr/bin/env python3
"""Stand-in for adb in unit tests. Reads $MAJLIS_FAKE_DIR/responses.json, logs every call."""
import json
import os
import sys
from pathlib import Path

args = sys.argv[1:]
if args and args[0] == "connect":
    print(f"connected to {args[1]}")
    sys.exit(0)
if len(args) >= 2 and args[0] == "-s":
    args = args[2:]
if args == ["get-state"]:
    print("device")
    sys.exit(0)
if args and args[0] == "shell":
    cmd = " ".join(args[1:])
elif args and args[0] == "exec-out":
    cmd = "exec-out " + " ".join(args[1:])
else:
    cmd = " ".join(args)
work = Path(os.environ["MAJLIS_FAKE_DIR"])
with (work / "calls.log").open("a") as fh:
    fh.write(cmd + "\n")
responses = json.loads((work / "responses.json").read_text())
if cmd in responses:
    sys.stdout.write(responses[cmd])
    sys.exit(0)
if cmd.startswith("exec-out"):
    sys.stdout.write("PNG" * 20000)   # > 30 KB so the screenshot check passes
    sys.exit(0)
print(f"fake-adb: no fixture for: {cmd}", file=sys.stderr)
sys.exit(1)
