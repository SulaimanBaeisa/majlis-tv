package io.freemytv;

import java.time.LocalDateTime;

/** Plain-Java test: what to show next (adhan or iqama countdown) for Jeddah, Umm al-Qura times. */
public class ScheduleTest {
    static final double LAT = 21.49012, LNG = 39.18624, TZ = 3.0;
    static int failed = 0;

    static void eq(String name, Object got, Object want) {
        if (!String.valueOf(want).equals(String.valueOf(got))) { failed++; System.out.println("FAIL " + name + ": got " + got + ", want " + want); }
    }

    static Schedule.Next at(String iso) {
        return Schedule.at(LocalDateTime.parse(iso), LAT, LNG, TZ, Schedule.DEFAULT_IQAMA);
    }

    public static void main(String[] a) {
        // Tuesday 2026-10-20 times (Umm al-Qura, Jeddah): Fajr 05:04 Dhuhr 12:08 Asr 15:27 Maghrib 17:56 Isha 19:26
        Schedule.Next n = at("2026-10-20T04:00");
        eq("before fajr: prayer", n.prayer, Schedule.FAJR);
        eq("before fajr: adhan", Schedule.hhmm(n.adhan), "05:04");
        eq("before fajr: iqama", Schedule.hhmm(n.iqama), "05:29");
        eq("before fajr: pending", n.iqamaPending, false);

        n = at("2026-10-20T05:10");
        eq("after fajr adhan: prayer", n.prayer, Schedule.FAJR);
        eq("after fajr adhan: pending", n.iqamaPending, true);
        eq("after fajr adhan: iqama", Schedule.hhmm(n.iqama), "05:29");

        n = at("2026-10-20T05:30");
        eq("after fajr iqama -> dhuhr", n.prayer, Schedule.DHUHR);
        eq("dhuhr adhan", Schedule.hhmm(n.adhan), "12:08");
        eq("dhuhr iqama +20", Schedule.hhmm(n.iqama), "12:28");

        n = at("2026-10-20T17:57");
        eq("maghrib pending", n.prayer, Schedule.MAGHRIB);
        eq("maghrib pending flag", n.iqamaPending, true);
        eq("maghrib iqama +10", Schedule.hhmm(n.iqama), "18:06");

        n = at("2026-10-20T21:00");
        eq("after isha -> tomorrow fajr", n.prayer, Schedule.FAJR);
        eq("tomorrow flag", n.tomorrow, true);
        eq("tomorrow fajr adhan", Schedule.hhmm(n.adhan), "05:05");

        // Friday 2026-10-23: Dhuhr is Jumu'ah, no iqama countdown; after the adhan we move on to Asr
        n = at("2026-10-23T11:00");
        eq("friday dhuhr is jumuah", n.jumuah, true);
        eq("friday no iqama", Double.isNaN(n.iqama), true);
        n = at("2026-10-23T12:30");
        eq("friday after adhan -> asr", n.prayer, Schedule.ASR);

        // custom iqama minutes are honoured
        n = Schedule.at(LocalDateTime.parse("2026-10-20T04:00"), LAT, LNG, TZ, new int[] {30, 15, 15, 5, 15});
        eq("custom fajr iqama +30", Schedule.hhmm(n.iqama), "05:34");
        eq("names", Schedule.NAMES_AR[Schedule.ISHA] + "/" + Schedule.NAMES_AR[Schedule.FAJR], "العشاء/الفجر");

        // iqama minutes from config: "fajr,dhuhr,asr,maghrib,isha"
        eq("parse ok", java.util.Arrays.toString(Schedule.parseIqama("30, 15,15 ,5,15")), "[30, 15, 15, 5, 15]");
        eq("parse empty -> default", java.util.Arrays.toString(Schedule.parseIqama("")), "[25, 20, 20, 10, 20]");
        eq("parse null -> default", java.util.Arrays.toString(Schedule.parseIqama(null)), "[25, 20, 20, 10, 20]");
        eq("parse too few -> default", java.util.Arrays.toString(Schedule.parseIqama("1,2,3")), "[25, 20, 20, 10, 20]");
        eq("parse junk -> default", java.util.Arrays.toString(Schedule.parseIqama("a,b,c,d,e")), "[25, 20, 20, 10, 20]");
        eq("parse out of range -> default", java.util.Arrays.toString(Schedule.parseIqama("25,20,20,10,90")), "[25, 20, 20, 10, 20]");
        eq("parse zero allowed", java.util.Arrays.toString(Schedule.parseIqama("25,0,20,10,20")), "[25, 0, 20, 10, 20]");
        System.out.println(failed == 0 ? "PASS ScheduleTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
