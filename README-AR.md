# Free My TV — Arabic / Saudi fork (`arabic-rtl` branch)

A private fork of [simbasjungles/free-my-tv](https://github.com/simbasjungles/free-my-tv) (MIT) for an Android TV in Jeddah.
It keeps upstream's design (a plain Android app, no Gradle, no libraries, no self-update) and adds an Arabic, right-to-left, Saudi home screen.
The original README (`README.md`) and licence (`LICENSE`) are unchanged.

## What is different from upstream

- **Arabic + RTL**: strings (`Arabic.java`, 101 keys, `tests/check_ar_coverage.py` fails on a missing key), mirrored layout (`Rtl.java`, `Mirror.java`), Noto Sans Arabic (SIL OFL, `app/assets/fonts/`). Western digits.
- **Home cards without a TMDB key**: next prayer with the iqama countdown, the date (Gregorian and Umm al-Qura Hijri), and a verse of the day (`app/assets/daily.tsv`).
- **Prayer times, offline**: Umm al-Qura method (Isha 90 min after Maghrib, 120 in Ramadan), computed on the TV for the latitude/longitude in `config.properties`. Iqama minutes: `iqama=25,20,20,10,20` (Fajr, Dhuhr, Asr, Maghrib, Isha; the Ministry of Islamic Affairs circular of 22 June 2021). Friday shows الجمعة with no countdown.
- **Saudi Pro League card** in the bottom drawer: live and next matches and the top six of the table, from ESPN's public JSON (`site.api.espn.com`, no key, not an official API).
- **Wallpaper of the day**: nine photos of Saudi Arabia (`app/assets/saudi/`, Wikimedia Commons, free licences, see `PHOTO-CREDITS.md`). The home background and the screensaver use them; the first one changes at local midnight.
- **Safety patch (M0)**: `Safe.java` whitelists the package and component names read back from the TV before they reach a shell command.
- Play Games and YouTube Music are no longer disabled by the "debloat" list. Only `com.google.android.tvlauncher`, `tvrecommendations` and `backdrop` are disabled (never uninstalled); "Original" in Settings reverses it.

## Build and test

```
source ~/tools/android-env.sh      # JDK 17 + Android SDK, user-level
cp config.example.properties config.properties   # then edit (city, coordinates, iqama=)
tests/run.sh                        # plain-Java tests, expect ALL-PASS
tests/check_surface.sh              # builds the APK; fails if permissions or hosts change
adb install -r FreeMyTV.apk
```

`tests/check_surface.sh` pins the three permissions (INTERNET, ACCESS_NETWORK_STATE, RECEIVE_BOOT_COMPLETED) and the host list
(android.googlesource.com, api.binance.com, api.open-meteo.com, api.themoviedb.org, image.tmdb.org, site.api.espn.com).
Add a host on purpose, in that file, or the build is rejected.

## Configuration

`config.properties` is gitignored. Jeddah defaults: `tmdb_region=SA`, `latitude=21.49012`, `longitude=39.18624`, `city=جدة`, `language=ar`, `show_crypto=false`, `currency=SAR`, `temperature=C`, `iqama=25,20,20,10,20`. `tmdb_key` stays empty on purpose: no key means no recommendation cards and no TMDB images.

## Known gaps

- Screensaver clock and caption are not mirrored yet.
- No hadith in the daily card: each one must be checked against its book and number first.
- No in-TV editor for the iqama minutes (edit `config.properties` and rebuild).
- The new screens were checked with pixel measurements and logs, not by eye.

## Credits

Upstream: simbasjungles/free-my-tv (MIT). Font: Noto Sans Arabic (OFL). Photos: see `PHOTO-CREDITS.md`. Prayer-time and Hijri results were checked against Aladhan (method 4) and Assabile; those sites are not used by the app.
