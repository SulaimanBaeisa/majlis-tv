package io.freemytv;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Plain-Java test: the Arabic strings of the home cards (prayer, date, verse of the day). */
public class InfoTest {
    static final double LAT = 21.49012, LNG = 39.18624, TZ = 3.0;
    static int failed = 0;

    static void eq(String name, Object got, Object want) {
        if (!String.valueOf(want).equals(String.valueOf(got))) { failed++; System.out.println("FAIL " + name + ": got [" + got + "], want [" + want + "]"); }
    }

    public static void main(String[] a) {
        eq("time am", Info.time12(5 * 60 + 4), "5:04 ص");
        eq("time noon", Info.time12(12 * 60 + 8), "12:08 م");
        eq("time pm", Info.time12(15 * 60 + 27), "3:27 م");
        eq("time evening", Info.time12(17 * 60 + 56), "5:56 م");
        eq("time midnight", Info.time12(0), "12:00 ص");
        eq("time wraps", Info.time12(24 * 60 + 65), "1:05 ص");

        eq("cd now", Info.countdown(0.4), "الآن");
        eq("cd 1", Info.countdown(1), "بعد دقيقة");
        eq("cd 2", Info.countdown(2), "بعد دقيقتين");
        eq("cd 25", Info.countdown(25), "بعد 25 دقيقة");
        eq("cd 60", Info.countdown(60), "بعد ساعة");
        eq("cd 134", Info.countdown(134), "بعد ساعتين و14 دقيقة");
        eq("cd 181", Info.countdown(181), "بعد 3 ساعات ودقيقة");
        eq("cd rounds up", Info.countdown(24.2), "بعد 25 دقيقة");

        Info.Date d = Info.date(LocalDate.of(2026, 10, 20));
        eq("weekday", d.weekday, "الثلاثاء");
        eq("gregorian", d.gregorian, "20 أكتوبر 2026");
        eq("hijri", d.hijri, "9 جمادى الأولى 1448 هـ");
        eq("friday", Info.date(LocalDate.of(2026, 10, 23)).weekday, "الجمعة");
        eq("sunday", Info.date(LocalDate.of(2026, 10, 25)).weekday, "الأحد");

        // prayer card, Tuesday 2026-10-20: Fajr 05:04 Dhuhr 12:08 Asr 15:27 Maghrib 17:56 Isha 19:26
        Info.Prayer p = Info.prayer(LocalDateTime.parse("2026-10-20T14:00"), LAT, LNG, TZ, Schedule.DEFAULT_IQAMA);
        eq("next name", p.name, "العصر");
        eq("next time", p.time, "3:27 م");
        eq("next label", p.label, "الصلاة القادمة");
        eq("next sub", p.sub, "بعد ساعة و27 دقيقة");
        eq("next index", p.index, Schedule.ASR);
        eq("row fajr", p.times[Schedule.FAJR], "5:04 ص");
        eq("row isha", p.times[Schedule.ISHA], "7:26 م");

        p = Info.prayer(LocalDateTime.parse("2026-10-20T15:30"), LAT, LNG, TZ, Schedule.DEFAULT_IQAMA);
        eq("pending label", p.label, "حان وقت الصلاة");
        eq("pending sub", p.sub, "الإقامة 3:47 م · بعد 17 دقيقة");

        p = Info.prayer(LocalDateTime.parse("2026-10-23T11:00"), LAT, LNG, TZ, Schedule.DEFAULT_IQAMA);
        eq("friday name", p.name, "الجمعة");

        p = Info.prayer(LocalDateTime.parse("2026-10-20T22:00"), LAT, LNG, TZ, Schedule.DEFAULT_IQAMA);
        eq("tomorrow fajr", p.name, "الفجر");
        eq("tomorrow label", p.label, "الصلاة القادمة غدًا");

        // verse of the day: stable for a date, changes day to day, wraps
        List<String[]> items = new ArrayList<>();
        for (int i = 0; i < 5; i++) items.add(new String[] {"Q", "s" + i, "t" + i});
        String[] x = Info.daily(items, LocalDate.of(2026, 10, 20));
        String[] y = Info.daily(items, LocalDate.of(2026, 10, 20));
        String[] z = Info.daily(items, LocalDate.of(2026, 10, 21));
        eq("daily stable", x[1], y[1]);
        eq("daily moves", !x[1].equals(z[1]), true);
        eq("daily empty", Info.daily(new ArrayList<String[]>(), LocalDate.now()) == null, true);
        List<String[]> parsed = Info.parseDaily("# comment\nQ\tسورة الشرح · الآية 5\tفَإِنَّ مَعَ ٱلْعُسْرِ يُسْرًا\n\nH\tx\ty\n");
        eq("parse count", parsed.size(), 2);
        eq("parse source", parsed.get(0)[1], "سورة الشرح · الآية 5");

        System.out.println(failed == 0 ? "PASS InfoTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
