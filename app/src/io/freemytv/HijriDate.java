package io.freemytv;

import java.time.LocalDate;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;

/**
 * Hijri (Umm al-Qura) date from the JDK/Android table. Pure Java so tests/HijriDateTest.java can run it on a PC.
 * The table is the Umm al-Qura calendar used for civil dates in Saudi Arabia; it can differ by a day from
 * a local moon sighting around the start of a month.
 */
final class HijriDate {
    private HijriDate() {}

    static final String[] MONTHS_AR = {
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};

    /** {year, month (1-12), day}. */
    static int[] fromGregorian(LocalDate date) {
        HijrahDate h = HijrahDate.from(date);
        return new int[] {h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH)};
    }

    /** "9 جمادى الأولى 1448 هـ" (Western digits). */
    static String format(LocalDate date) {
        int[] h = fromGregorian(date);
        return h[2] + " " + MONTHS_AR[h[1] - 1] + " " + h[0] + " هـ";
    }
}
