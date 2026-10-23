# Changelog

## 1.1.0 - 2026-10-23
- Renamed the product to **Majlis** (مجلس). The application id stays `io.freemytv`, so existing
  installs update in place.
- New README hero image: the approved design, rendered without Nuvio or Stremio.
- `./tv verify` now ignores crash-buffer entries older than the installed package update.
- CI uses the current action majors (checkout v7, setup-java v6, gh-release v3).

## 1.0.0 - 2026-10-23
- First public release: Arabic RTL home screen, screensaver and settings for Android TV.
- Umm al-Qura prayer times with iqama, Hijri date, Qur'an/hadith of the day, Saudi Pro League,
  Arabic news ticker, 39 Saudi wallpapers, adhan overlay.
- Agent installer: `./tv` (doctor, probe, build, install, switch, verify, update, rollback, report).
- Brand profiles for Sony BRAVIA, TCL Google TV, Changhong/Classpro, and generic Android TV.
- Security: 5 permissions, host allow-list, protected-package list, recorded baseline + rollback.

### Known gaps
- The app icon and TV banner are still upstream's mark; a Majlis icon is planned.
- No in-app updater: updates are agent/source-driven by design.
