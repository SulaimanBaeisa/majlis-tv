# Architecture

A plain Android app in Java, built without Gradle by `build.sh`. No libraries: only the Android SDK.
The home screen is drawn by hand on one Canvas; the settings screen is drawn the same way.

## Runtime shape

```
HomeActivity (:home) ── HomeView ── Scene/Shaders/Frost ── Canvas
        │                  └── HomePanels, HomeApps, HomeData
        ├── DreamView / Screensaver(Alone)      screensaver
        └── Watchdog                            3 crashes in 5 min -> Original

SettingsActivity (:home) ── HomeMode (main process, ModeJob)
                                  │
                                  └── Adb (127.0.0.1:5555) -> pm disable-user / cmd package
```

- `minSdk 28` (Android 9), `targetSdk 33`, Java 8 language level.
- Android 9 has no `RenderNode`; `Recording` wraps it with a `Picture`. Android 9–11 (`Ui.LEGACY`)
  skips GPU layers and uses cached bitmap shadows and shader-rounded images.
- Android 11–12 run the screensaver as `ScreensaverAlone` in the main process
  (`res/values-v30|v31/bools.xml`); Android 13+ keeps `Screensaver` in `:home`.
- `HomeMode` runs every switch as a `ModeJob` in the main process. On TCL, `TclAppBoot` blocks the
  job binding, so the switch runs in-process and the adhan receiver lives in `:home`.
- `Watchdog` returns to the stock home screen after three crashes in five minutes.

## Module map

| Module | Files | Responsibility |
|---|---|---|
| Home UI | `HomeActivity`, `HomeView`, `HomePanels`, `HomeData`, `HomeApps` | the screen, shelf, tabs, drawer, cards |
| Rendering | `Scene`, `Shaders`, `Frost`, `Glyphs`, `Shadows`, `Spring`, `Recording`, `Ui` | hand-drawn canvas, glass, text, animation |
| Screensaver | `DreamView`, `Screensaver`, `ScreensaverAlone`, `Backdrop`, `SaudiPhotos`, `WallpaperCache`, `WallpaperKey` | dream service, daily wallpaper, cached blur |
| Mode switching | `HomeMode`, `ModeJob`, `Adb`, `Safe`, `HomeReceiver`, `Watchdog`, `Prefs` | Free/Original, boot checks, crash safety |
| Time & religion | `PrayerTimes`, `Schedule`, `PrayerAlarm`, `PrayerPopup`, `Info`, `HijriDate`, `DailyLayout` | Umm al-Qura, iqama, adhan, verse/hadith |
| Data feeds | `Json`, `League`, `LeagueFeed`, `News`, `NewsFeed`, `Recs` | ESPN, Google News, optional TMDB |
| Language | `Arabic`, `Lang`, `Rtl`, `Mirror`, `Spanish` | strings, coverage, mirroring |
| Remote | `RemoteKeys`, `RemoteKeyService` | Netflix/YouTube/Prime buttons from any app |
| Platform | `App`, `AppLog`, `TvCompat` | process policy per Android version |

54 files in `app/src/io/freemytv/`.

## Where do I change X?

| I want to… | Change |
|---|---|
| add or reorder a shelf app | `HomeApps.SHELF` (and `<queries>` in `AndroidManifest.xml` for Android 11+) |
| change which packages Free mode disables | `HomeMode.DEBLOAT` **and** `tooling/profiles/_common.json` (a test fails if they drift) |
| protect a brand's system app | `HomeMode.PROTECTED` and the brand profile's `must_stay_enabled` |
| change the home layout | `HomeView.onDraw` and its layout helpers; mirror changes in `Rtl`/`Mirror` |
| add a language | `Lang`, `Arabic.java` or `Spanish.java`; `tests/check_ar_coverage.py` guards Arabic |
| change prayer maths | `PrayerTimes` (Umm al-Qura), `Schedule` (iqama); tests pin both |
| add a network host | the source **and** `tests/check_surface.sh` (`WANT_H`) — it fails otherwise |
| change permissions | the manifest **and** `tests/check_surface.sh` (`WANT`); 5 is intentional |
| change the daily content | `app/assets/daily.tsv` + `docs/religious/hadith-sources.json`; `tests/check_daily_sources.py` pins the Qur'an rows |
| add a wallpaper | `app/assets/saudi/sN.jpg`, `credits.tsv`, `tests/SaudiPhotosTest.java`, `PHOTO-CREDITS.md` |

## Build and tests

```bash
source ~/tools/android-env.sh    # JDK 17 + Android SDK on this machine
./build.sh                       # -> Majlis.apk (signed)
MAJLIS_UNSIGNED=1 ./build.sh       # -> unsigned, for CI checks
bash tests/run.sh                # pure-Java tests
bash tests/check_surface.sh      # permissions, hosts, package id
python3 tests/check_daily_sources.py
python3 -m unittest discover -s tests/tools -v
```

## Installer

`tooling/tv.py` (Python 3.9+, stdlib) owns every device call: `doctor`, `probe`, `build`, `install`,
`switch`, `verify`, `update`, `rollback`, `report`, `sign`. Brand behaviour is data in
`tooling/profiles/*.json`. The full agent contract is [AGENTS.md](../AGENTS.md).
