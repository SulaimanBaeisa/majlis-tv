# Free My TV — Arabic / Saudi fork (`arabic-rtl` branch)

A private fork of [simbasjungles/free-my-tv](https://github.com/simbasjungles/free-my-tv) (MIT) for an Android TV in Jeddah.
It keeps upstream's design (a plain Android app, no Gradle, no libraries, no self-update) and adds an Arabic, right-to-left, Saudi home screen.
The original README (`README.md`) and licence (`LICENSE`) are unchanged.

## What is different from upstream

- **Arabic + RTL**: strings (`Arabic.java`, 101 keys, `tests/check_ar_coverage.py` fails on a missing key), mirrored layout (`Rtl.java`, `Mirror.java`), Noto Sans Arabic (SIL OFL, `app/assets/fonts/`). Western digits.
- **Daily Quran/hadith card without a TMDB key**: next prayer with the iqama countdown, the date (Gregorian and Umm al-Qura Hijri), and a daily Quran verse or hadith (`app/assets/daily.tsv`). The original 29 Quran verses are unchanged; 60 hadith texts/complete excerpts from Sahih al-Bukhari and Sahih Muslim are included. They alternate by local calendar day, with independent cycles covering every entry before that kind repeats (29 verse slots / 60 hadith slots). Collection/reference and narrator appear on separate lines. Exact source URLs, excerpt flags and cached-primary-page hashes: `docs/hadith-sources.json`. Excerpts are not presented as the full text of longer hadith.
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

- **Screensaver RTL**: clock/date/weather on the left, photo caption/credit on the right; scrims and brightness sampling follow the text, without flipping photos or glyphs. Verified from actual TV screenshots.
- No in-TV editor for the iqama minutes (edit `config.properties` and rebuild; intentionally not implemented).
- The drawer's league and weather panels were visually inspected on the TV. Hadith layout is checked for all 60 entries using the TV's Android text engine; source text and narrator fit checks are included in the on-device harness. Local-midnight selection is unit-tested; a real midnight rollover has not been observed.

## Credits

Upstream: simbasjungles/free-my-tv (MIT). Font: Noto Sans Arabic (OFL). Photos: see `PHOTO-CREDITS.md`. Prayer-time and Hijri results were checked against Aladhan (method 4) and Assabile; those sites are not used by the app.
