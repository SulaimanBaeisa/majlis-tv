package io.freemytv;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * What the drawer and screensaver show about prayer: the next adhan, or, between adhan and iqama, the iqama.
 * Pure Java (tests/ScheduleTest.java). Adhan times come from {@link PrayerTimes}, rounded to the minute like the
 * published tables; iqama = adhan + the minutes below.
 */
final class Schedule {
    private Schedule() {}

    static final int FAJR = 0, DHUHR = 1, ASR = 2, MAGHRIB = 3, ISHA = 4;
    static final String[] NAMES_AR = {"الفجر", "الظهر", "العصر", "المغرب", "العشاء"};
    /** The Friday name of the Dhuhr slot. */
    static final String JUMUAH_AR = "الجمعة";

    /**
     * Minutes from adhan to iqama, in the order above. Source: Saudi Ministry of Islamic Affairs circular of 22 Jun 2021
     * (moia.gov.sa, "20 minutes for all prayers, 25 for Fajr, 10 for Maghrib"). Mosques differ, and the ministry has
     * changed these numbers before, so the TV lets you edit them.
     */
    static final int[] DEFAULT_IQAMA = {25, 20, 20, 10, 20};

    /** "25,20,20,10,20" (Fajr, Dhuhr, Asr, Maghrib, Isha) from config.properties; anything odd gives the defaults. */
    static int[] parseIqama(String s) {
        if (s == null) return DEFAULT_IQAMA.clone();
        String[] f = s.trim().split("\\s*,\\s*");
        if (f.length != 5) return DEFAULT_IQAMA.clone();
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) {
            try {
                out[i] = Integer.parseInt(f[i].trim());
            } catch (NumberFormatException e) {
                return DEFAULT_IQAMA.clone();
            }
            if (out[i] < 0 || out[i] > 60) return DEFAULT_IQAMA.clone();
        }
        return out;
    }

    private static final int[] SLOT_TO_TIMES = {PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR,
            PrayerTimes.MAGHRIB, PrayerTimes.ISHA};

    static final class Next {
        int prayer;               // FAJR..ISHA
        double adhan, iqama;      // minutes after midnight of that prayer's own day; iqama is NaN on Fridays for Dhuhr
        boolean iqamaPending;     // the adhan has been called and the iqama is still to come
        boolean tomorrow;         // the prayer is tomorrow's
        boolean jumuah;           // Friday's Dhuhr slot
    }

    static Next at(LocalDateTime now, double lat, double lng, double tz, int[] iqamaMinutes) {
        double nowMin = now.getHour() * 60 + now.getMinute() + now.getSecond() / 60.0;
        for (int day = 0; day <= 1; day++) {
            LocalDate date = now.toLocalDate().plusDays(day);
            double[] t = PrayerTimes.forDate(date, lat, lng, tz);
            for (int p = 0; p < 5; p++) {
                Next n = new Next();
                n.prayer = p;
                n.tomorrow = day == 1;
                n.adhan = Math.round(t[SLOT_TO_TIMES[p]]);
                n.jumuah = p == DHUHR && date.getDayOfWeek() == DayOfWeek.FRIDAY;
                n.iqama = n.jumuah ? Double.NaN : n.adhan + iqamaMinutes[p];
                double end = n.jumuah ? n.adhan : n.iqama;         // when this slot stops being the next one
                if (nowMin < end + day * 1440) {
                    n.iqamaPending = nowMin >= n.adhan + day * 1440 && !n.jumuah;
                    return n;
                }
            }
        }
        throw new IllegalStateException("no prayer found");   // cannot happen: tomorrow's Fajr is always ahead
    }

    /** One adhan: which prayer, on which day, at how many minutes after that day's midnight. */
    static final class Adhan {
        int prayer;
        LocalDate date;
        double minutes;
        boolean jumuah;

        long epochMs(double tzHours) {
            long sec = date.toEpochDay() * 86_400L + Math.round(minutes * 60) - Math.round(tzHours * 3600);
            return sec * 1000;
        }
    }

    /** The first adhan strictly after {@code now} (today or tomorrow). */
    static Adhan nextAdhan(LocalDateTime now, double lat, double lng, double tz) {
        double nowMin = now.getHour() * 60 + now.getMinute() + now.getSecond() / 60.0;
        for (int day = 0; day <= 1; day++) {
            LocalDate date = now.toLocalDate().plusDays(day);
            double[] t = PrayerTimes.forDate(date, lat, lng, tz);
            for (int p = 0; p < 5; p++) {
                double m = Math.round(t[SLOT_TO_TIMES[p]]);
                if (m + day * 1440 > nowMin) {
                    Adhan a = new Adhan();
                    a.prayer = p;
                    a.date = date;
                    a.minutes = m;
                    a.jumuah = p == DHUHR && date.getDayOfWeek() == DayOfWeek.FRIDAY;
                    return a;
                }
            }
        }
        throw new IllegalStateException("no adhan found");
    }

    static String popupTitle(Adhan a) {
        return "حان الآن موعد أذان " + (a.jumuah ? JUMUAH_AR : NAMES_AR[a.prayer]);
    }

    static String popupSub(Adhan a, int[] iqamaMinutes) {
        if (a.jumuah) return "صلاة الجمعة";
        return "الإقامة بعد " + Arabic.unit(iqamaMinutes[a.prayer], "دقيقة", "دقيقتين", "دقائق", "دقيقة");
    }

    /** "05:29" from minutes after midnight (wraps). */
    static String hhmm(double minutes) {
        return PrayerTimes.hhmm(minutes);
    }
}
