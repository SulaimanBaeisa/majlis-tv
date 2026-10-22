package io.freemytv;

import java.time.LocalDate;

/**
 * Prayer times computed on the TV (no network), Umm al-Qura method: Fajr at 18.5 degrees, Isha 90 minutes after
 * Maghrib (120 in Ramadan), Asr by the standard (Shafi'i) shadow rule. Same parameters as Aladhan's method 4;
 * tests/PrayerTimesTest.java checks 15 dates against it. Pure Java.
 * Times are minutes after local midnight, in the order Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha.
 */
final class PrayerTimes {
    private PrayerTimes() {}

    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5;
    private static final double FAJR_ANGLE = 18.5, HORIZON = 0.833;

    static double[] forDate(LocalDate date, double lat, double lng, double tzHours) {
        boolean ramadan = HijriDate.fromGregorian(date)[1] == 9;
        return compute(date, lat, lng, tzHours, ramadan ? 120 : 90);
    }

    static double[] compute(LocalDate date, double lat, double lng, double tz, int ishaAfterMaghribMin) {
        double jd = julianDay(date) - lng / (15.0 * 24.0);
        double[] sun = sunPosition(jd);                      // {declination (deg), equation of time (h)}
        double noon = 12 + tz - lng / 15.0 - sun[1];
        double fajr = noon - angleTime(FAJR_ANGLE, lat, sun[0]);
        double sunrise = noon - angleTime(HORIZON, lat, sun[0]);
        double asr = noon + asrTime(1, lat, sun[0]);
        double maghrib = noon + angleTime(HORIZON, lat, sun[0]);
        double[] out = {fajr, sunrise, noon, asr, maghrib, 0};
        for (int i = 0; i < 5; i++) out[i] *= 60;
        out[ISHA] = out[MAGHRIB] + ishaAfterMaghribMin;
        return out;
    }

    /** "17:05": minutes after midnight rounded to the nearest minute (wraps past midnight). */
    static String hhmm(double minutes) {
        int m = (int) Math.round(minutes);
        m = ((m % 1440) + 1440) % 1440;
        return String.format(java.util.Locale.ROOT, "%02d:%02d", m / 60, m % 60);   // Western digits whatever the TV language
    }

    private static double julianDay(LocalDate d) {
        int y = d.getYear(), m = d.getMonthValue();
        if (m <= 2) { y -= 1; m += 12; }
        int a = y / 100, b = 2 - a + a / 4;
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d.getDayOfMonth() + b - 1524.5;
    }

    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double cos(double deg) { return Math.cos(Math.toRadians(deg)); }
    private static double tan(double deg) { return Math.tan(Math.toRadians(deg)); }
    private static double fixAngle(double a) { a = a % 360; return a < 0 ? a + 360 : a; }

    /** Low-precision solar position (PrayTimes.org / Astronomical Almanac), good to a few seconds. */
    private static double[] sunPosition(double jd) {
        double d = jd - 2451545.0;
        double g = fixAngle(357.529 + 0.98560028 * d);
        double q = fixAngle(280.459 + 0.98564736 * d);
        double l = fixAngle(q + 1.915 * sin(g) + 0.020 * sin(2 * g));
        double e = 23.439 - 0.00000036 * d;
        double ra = Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15.0;
        ra = ra < 0 ? ra + 24 : ra;
        double eqt = q / 15.0 - ra;
        eqt = eqt > 12 ? eqt - 24 : eqt < -12 ? eqt + 24 : eqt;
        double decl = Math.toDegrees(Math.asin(sin(e) * sin(l)));
        return new double[] {decl, eqt};
    }

    /** Hours from solar noon until the sun is `angle` degrees below the horizon. */
    private static double angleTime(double angle, double lat, double decl) {
        double x = (-sin(angle) - sin(lat) * sin(decl)) / (cos(lat) * cos(decl));
        return Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, x)))) / 15.0;
    }

    /** Hours from solar noon until an object's shadow is `factor` times its height plus its noon shadow. */
    private static double asrTime(double factor, double lat, double decl) {
        double angle = -Math.toDegrees(Math.atan(1.0 / (factor + tan(Math.abs(lat - decl)))));
        return angleTime(angle, lat, decl);
    }
}
