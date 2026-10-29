# Changelog

## 1.2.0 - 2026-10-29
- Favourite competitions appear as glass chips inside the matches card; left/right on the remote
  switches between them and the strip keeps the one on screen visible. With three or more followed,
  the chips drop the repeated "الدوري" so two leagues never read the same.
- Matches tab: a D-pad refresh button with loading, success and failure states.
- Prayer times on the screensaver, using the same city and calculation as the home screen.
- Dark Saudi-green settings and About screens; the lower notices moved to a scrollable About page.
- Football settings with 20 supported competitions and a Saudi default that survives restarts.
- Brand-aware TV settings shortcut (Sony, TCL, Changhong/Classpro, generic) instead of a TCL label.
- News ticker stays visible with the drawer open; real news-source credits in About; the crypto
  prices label corrected in Arabic.

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
