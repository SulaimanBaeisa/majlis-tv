#!/usr/bin/env bash
# Runs the plain-Java tests (pure classes only; Android classes are tested on the TV).
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf tests/out && mkdir -p tests/out
SRC="app/src/io/freemytv/Safe.java"
[ -f "$SRC" ] || SRC=""
javac -d tests/out $SRC tests/*Test.java
for t in tests/*Test.java; do
  n=$(basename "$t" .java)
  java -ea -cp tests/out "io.freemytv.$n"
done
echo ALL-PASS
