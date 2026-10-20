#!/usr/bin/env bash
# Runs the plain-Java tests (pure classes only; Android classes are tested on the TV).
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf tests/out && mkdir -p tests/out
SRC="$(ls app/src/io/freemytv/Safe.java app/src/io/freemytv/Lang.java app/src/io/freemytv/Arabic.java app/src/io/freemytv/HijriDate.java app/src/io/freemytv/PrayerTimes.java app/src/io/freemytv/Schedule.java app/src/io/freemytv/Mirror.java app/src/io/freemytv/Info.java 2>/dev/null | tr "\n" " ")"
javac -encoding UTF-8 -d tests/out $SRC tests/*Test.java
for t in tests/*Test.java; do
  n=$(basename "$t" .java)
  java -ea -cp tests/out "io.freemytv.$n"
done
echo ALL-PASS
