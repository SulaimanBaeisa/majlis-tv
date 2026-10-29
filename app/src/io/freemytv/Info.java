package io.freemytv;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** The Arabic strings of the home cards: next prayer, date (with Hijri), verse of the day. Pure Java (tests/InfoTest.java). */
final class Info {
    private Info() {}

    static String clock12(java.time.LocalTime t) {
        int h=t.getHour()%12;
        return (h==0?12:h)+":"+(t.getMinute()<10?"0":"")+t.getMinute();
    }

    static String ampm(java.time.LocalTime t) { return t.getHour()<12?"ص":"م"; }

    private static final String[] WEEKDAYS = {"الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"};   // Monday first
    private static final String[] MONTHS = {"يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس",
            "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"};

    /** "3:27 م": minutes after midnight (wraps), 12-hour clock with Arabic am/pm marks and Western digits. */
    static String time12(double minutes) {
        int m = (int) Math.round(minutes);
        m = ((m % 1440) + 1440) % 1440;
        int h = m / 60, min = m % 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return h12 + ":" + (min < 10 ? "0" : "") + min + (h < 12 ? " ص" : " م");
    }

    /** "بعد ساعتين و14 دقيقة": time left, rounded up to the minute; "الآن" when under a minute. */
    static String countdown(double minutesLeft) {
        if (minutesLeft <= 0.5) return "الآن";
        long m = (long) Math.ceil(minutesLeft - 1e-9);
        if (m < 60) return "بعد " + Arabic.unit(m, "دقيقة", "دقيقتين", "دقائق", "دقيقة");
        long h = m / 60, rest = m % 60;
        String s = "بعد " + Arabic.unit(h, "ساعة", "ساعتين", "ساعات", "ساعة");
        return rest == 0 ? s : s + " و" + Arabic.unit(rest, "دقيقة", "دقيقتين", "دقائق", "دقيقة");
    }

    static final class Date {
        String weekday, gregorian, hijri;
    }

    static Date date(LocalDate d) {
        Date r = new Date();
        r.weekday = WEEKDAYS[d.getDayOfWeek().getValue() - 1];
        r.gregorian = d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1] + " " + d.getYear();
        r.hijri = HijriDate.format(d);
        return r;
    }

    static final class Prayer {
        String label, name, time, sub;
        int index;
        boolean pending, tomorrow;
        String[] times = new String[5];
    }

    static Prayer prayer(LocalDateTime now, double lat, double lng, double tz, int[] iqama) {
        Schedule.Next n = Schedule.at(now, lat, lng, tz, iqama);
        double nowMin = now.getHour() * 60 + now.getMinute() + now.getSecond() / 60.0;
        Prayer p = new Prayer();
        p.index = n.prayer;
        p.pending = n.iqamaPending;
        p.tomorrow = n.tomorrow;
        p.name = n.jumuah ? Schedule.JUMUAH_AR : Schedule.NAMES_AR[n.prayer];
        p.time = time12(n.adhan);
        double base = n.tomorrow ? 1440 : 0;
        if (n.iqamaPending) {
            p.label = "حان وقت الصلاة";
            p.sub = "الإقامة " + time12(n.iqama) + " · " + countdown(n.iqama - nowMin);
        } else {
            p.label = n.tomorrow ? "الصلاة القادمة غدًا" : "الصلاة القادمة";
            p.sub = countdown(n.adhan + base - nowMin);
        }
        LocalDate day = now.toLocalDate().plusDays(n.tomorrow ? 1 : 0);
        double[] t = PrayerTimes.forDate(day, lat, lng, tz);
        int[] slots = {PrayerTimes.FAJR, PrayerTimes.DHUHR, PrayerTimes.ASR, PrayerTimes.MAGHRIB, PrayerTimes.ISHA};
        for (int i = 0; i < 5; i++) p.times[i] = time12(Math.round(t[slots[i]]));
        return p;
    }

    /**
     * The screensaver's prayer row, from the same {@link #prayer} the home card uses: {name, time} for the five prayers
     * of the day the card shows (today, or tomorrow after Isha), with «الجمعة» for Dhuhr on Friday, as on the card.
     */
    static String[][] row(Prayer p, LocalDate today) {
        LocalDate day = p.tomorrow ? today.plusDays(1) : today;
        String[][] out = new String[5][];
        for (int i = 0; i < 5; i++) {
            String name = i == Schedule.DHUHR && day.getDayOfWeek() == java.time.DayOfWeek.FRIDAY ? Schedule.JUMUAH_AR : Schedule.NAMES_AR[i];
            out[i] = new String[] {name, p.times[i]};
        }
        return out;
    }

    /** The row's heading: today's or tomorrow's times. */
    static String rowTitle(Prayer p) { return p.tomorrow ? "مواقيت صلاة الغد" : "مواقيت الصلاة اليوم"; }

    /** Alternating verse/hadith days, each kind advancing through its entire independent cycle. */
    static String[] daily(List<String[]> items, LocalDate d) {
        if (items == null || items.isEmpty()) return null;
        int verses = 0, hadith = 0;
        for (String[] item : items) {
            if ("Q".equals(item[0])) verses++;
            else if ("H".equals(item[0])) hadith++;
        }
        long day = d.toEpochDay();
        if (verses == 0 && hadith == 0) return null;
        boolean mixed = verses > 0 && hadith > 0;
        String kind = mixed ? (Math.floorMod(day, 2L) == 0 ? "Q" : "H") : (verses > 0 ? "Q" : "H");
        int count = "Q".equals(kind) ? verses : hadith;
        int index = (int) Math.floorMod(mixed ? Math.floorDiv(day, 2L) : day, (long) count);
        for (String[] item : items) {
            if (kind.equals(item[0]) && index-- == 0) return item;
        }
        return null;
    }

    /** Parses assets/daily.tsv: "kind TAB source TAB text" per line; '#' lines and blank lines are skipped. */
    static List<String[]> parseDaily(String tsv) {
        List<String[]> out = new ArrayList<>();
        for (String line : tsv.split("\n")) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] f = line.split("\t", 3);
            if (f.length == 3) out.add(f);
        }
        return out;
    }
}
