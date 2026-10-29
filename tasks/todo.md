# Majlis polish checklist — current state

Current authorization: Omr + Cloudy only. No further coding workers. No public release authorized.

- [x] Fresh TCL identification; preserve existing user README change.
- [x] Write and update acceptance/verification plan.
- [x] Fix ticker and inspect it with drawer open on TCL.
- [x] Dark Saudi-green settings observed on TCL.
- [x] Brand detection/profile parity and chipset false-positive regression tests; TCL shortcut observed. Other brands require separate hardware tests.
- [x] Prayer row observed on TCL photo screensaver; Friday/next-day unit cases pass.
- [x] D-pad refresh focus, activation and success observed; failure/cooldown logic tests pass. No TV network outage test performed.
- [x] Arabic settings label centred under gear on TCL.
- [x] Exactly20 supported competition endpoints checked; choice survives APK update/restart; Saudi-only test state restored.
- [x] Former lower information moved to physically right-side, scrollable About; top/bottom inspected on TCL.
- [x] News-source credits generated from actual sources; inspected and regression-tested.
- [x] Crypto-prices label corrected and observed in device settings; disabled state preserved.
- [x] Address Cloudy review findings and document remaining hardware coverage limits.
- [x] 18 Java test programs ALL-PASS, 17 Python installer tests OK; surface/brand/Arabic/religious-source gates pass.
- [x] Final preview APK built and installed; installed/local hash match.
- [x] Live TCL verify:17 PASS /0 FAIL /1 WARN /1 SKIP; actual UI-process profiling collected separately.
- [x] Package preview APK, evidence and report for review; leave TV on Majlis home.

- [x] Favourite-competition chips inside the matches card replace the text hint; window keeps the active chip visible (`CompetitionTabsTest`).
- [x] Version 1.2.0 (code 4), CHANGELOG entry; gates re-run (18 Java ALL-PASS, 17 Python OK, surface/brand/Arabic/religious).
- [x] Sony BRAVIA 10.3.30.60: installed 1.2.0, verify 17 PASS / 0 FAIL / 1 WARN / 1 SKIP; matches chip seen on screen.
- [ ] TCL 10.3.30.245: not reachable while writing this (off or off-network); needs installation and verify.
- [ ] Push to origin/main once both TVs are verified (Sulaiman's condition).

Public release remains unchecked:
- [ ] Physical offline/recovery, daylight-photo/no-photo screensaver and overnight checks.
- [ ] Other-brand smoke tests, sustained-use testing.
- [ ] Release documentation/provider-terms review, user publication approval.

Evidence, exact hash and limits:
`/home/sulaiman/OpenBot/Shared/majlis-public-polish/final-verification.md`

Build is version1.2.0 code4. No push/tag/release, HOME switch, package disable/enable, data reset, signing change or religious-source modification occurred.
