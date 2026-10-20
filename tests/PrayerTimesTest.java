package io.freemytv;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

/** Plain-Java test. Our offline Umm al-Qura prayer times vs Aladhan (method 4) for Jeddah, within 2 minutes. */
public class PrayerTimesTest {
    static final double LAT = 21.49012, LNG = 39.18624, TZ = 3.0;

    static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    public static void main(String[] a) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("tests/fixtures/prayer-jeddah-aladhan.tsv"));
        String[] names = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
        int rows = 0, bad = 0;
        double worst = 0;
        for (String l : lines) {
            if (l.startsWith("#") || l.trim().isEmpty()) continue;
            String[] f = l.split("\t");
            LocalDate d = LocalDate.parse(f[0]);
            double[] got = PrayerTimes.forDate(d, LAT, LNG, TZ);
            rows++;
            for (int i = 0; i < 6; i++) {
                double err = Math.abs(got[i] - minutes(f[1 + i]));
                worst = Math.max(worst, err);
                if (err > 2.0) { bad++; System.out.println("OFF " + f[0] + " " + names[i] + " ours=" + PrayerTimes.hhmm(got[i]) + " aladhan=" + f[1 + i] + " err=" + String.format("%.1f", err)); }
            }
        }
        boolean fmt = PrayerTimes.hhmm(17 * 60 + 5.4).equals("17:05") && PrayerTimes.hhmm(7.6).equals("00:08");
        System.out.println("rows=" + rows + " times=" + rows * 6 + " off=" + bad + " worst=" + String.format("%.2f", worst) + " min");
        if (rows < 15 || bad > 0 || !fmt) { System.out.println("FAILED PrayerTimesTest (fmt=" + fmt + ")"); System.exit(1); }
        System.out.println("PASS PrayerTimesTest");
    }
}
