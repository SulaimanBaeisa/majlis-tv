# Majlis TV public-use polish

User approved implementation and testing of the collected channel notes. Not approval to publish, erase data, switch HOME, uninstall apps or alter signing keys.

## Baseline and ownership
- Repository `/srv/projects/free-my-tv-ar-src`, HEAD `1fec909` at inspection.
- Existing dirty README.md belongs to user; preserve byte-for-byte unless specifically requested.
- Fresh `./tv probe --ip 10.3.30.245`: TCL Smart TV Pro / Android SDK34; existing Majlis HOME/screensaver, version1.1.0 code3.
- `source /home/sulaiman/tools/android-env.sh && ./tv doctor`: 6 OK, 0 missing.
- Omr: scope, review and independent verification (incl. controlled TV install). Cloudy: independent read-only competition/news-source audit.

## Status
Current authorization: Omr and Cloudy only, per Sulaiman’s later explicit instruction. No Lammah, worker CLI or further external coding-provider execution. Earlier implementation-provider approvals are historical, not current authority.
Omr applied review corrections, expanded tests, built and installed the preview, and tested the TCL. Cloudy supplied independent read-only audits.
Evidence and remaining limitations: `/home/sulaiman/OpenBot/Shared/majlis-public-polish/final-verification.md`. No public release or commit.

## Design decisions (from code inspection)
- Ticker lives in the shelf (platter) layer, which lifts by SHIFT with the drawer. It was hidden by `drawer.value<0.01f`
  as soon as the platter was redrawn at the end of the slide. Geometry moved to pure `HomeLayout` so the gap between
  shelf, ticker and drawer rows is tested at every drawer position; the predicate is gone because the ticker and
  drawer move together and never overlap.
- Settings background: new dark `Shaders.majlis` palette (night ink with the Saudi green already used for accents),
  no day-blue sky in settings or the screensaver preview chrome.
- Brand: pure `TvBrand` mirrors `tooling/profiles/*.json` match rules (test asserts parity). TCL-only remote key
  rows (incl. "TCL Channel") only on TCL; other brands get an honest note with their real brand name. TV Settings
  uses `Settings.ACTION_SETTINGS`, falling back to `com.android.tv.settings` launch intent, else a toast.
- Screensaver prayer times: `Info.prayer` with `Prefs.where()` (same as home) recomputed with the corner chrome
  every minute (midnight/next-prayer roll-over), row under the clock (photos) or under the weather (no photos).
- Football: pure `Competitions` catalog of exactly 20 ESPN soccer slugs on the existing host
  `site.api.espn.com`; per-competition cache and timers in `LeagueFeed`; HTTP 400/404 = unsupported,
  empty events = no matches/offseason; manual refresh with single-flight + 20 s cooldown; settings multi-select
  persisted in SharedPreferences (Saudi default; none selected = matches tab says so, no network).
- About screen in settings: wrapped, right-aligned (RTL) scrollable text with version, data sources generated from
  `News.SOURCES`/`Competitions`, licences and the TMDB notice. Main settings footer wraps within the safe area.

## Acceptance per item (testable)
1. Ticker: `HomeLayoutTest` — ticker inside the platter layer, no overlap with shelf or drawer rows for drawer∈[0,1].
2. Settings dark: palette constants low-luminance; white/ink text contrast ≥ 4.5 (tested in `HomeLayoutTest`).
3. Brand: `TvBrandTest` — TCL/Sony/Changhong/Classpro/generic samples + parity with tooling profiles.
4. Screensaver: `InfoTest` additions — same times as home for Jeddah, roll-over after Isha.
5. Refresh: `LeagueFeedLogicTest` (pure `RefreshGate`) — single-flight, cooldown, states loading/ok/error/stale.
6. Gear label: `HomeLayoutTest` — tip centred under the button unless it would leave the 48 px overscan edge.
7. Competitions: `CompetitionsTest` — exactly 20, unique ids, required ones present, Saudi default, persistence
   encoding round-trip, empty vs unsupported classification, per-competition cache keys.
8/9. About: `NewsTest`-style checks that credits come from `News.SOURCES`; device screenshots still needed.
10. Crypto label: `ArabicTest` asserts «أسعار العملات الرقمية».

## Quality gates
`bash tests/run.sh`; `bash tests/check_surface.sh`; `python3 tests/check_daily_sources.py`; `python3 -m unittest discover -s tests/tools -v`; `bash tests/check_brand.sh`; `python3 tests/check_ar_coverage.py`. Build using existing SDK/signing/config; never print secrets.
Omr then reviews the diff, reruns gates, installs with `./tv`, `./tv verify --ip 10.3.30.245 --mode free --apk Majlis.apk` and does UI/screenshot checks. A test failure is not a pass. Tests alone do not prove public readiness.

## Delivery
APK plus exact hash, checked items/evidence, remaining limitations. No git push/tag/release. No provider-dependent output fabricated.

Full approved coder brief: `/home/sulaiman/OpenBot/Shared/majlis-public-polish/brief.md`.
