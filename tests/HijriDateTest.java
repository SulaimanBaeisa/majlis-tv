package io.freemytv;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;

/** Plain-Java test. Compares our Hijri date (JDK Umm al-Qura table) with Aladhan's, fetched into tests/fixtures. */
public class HijriDateTest {
    public static void main(String[] a) throws Exception {
        List<String> lines = Files.readAllLines(Paths.get("tests/fixtures/hijri-aladhan.tsv"));
        int rows = 0, wrong = 0;
        for (String l : lines) {
            if (l.startsWith("#") || l.trim().isEmpty()) continue;
            String[] f = l.split("\t");
            LocalDate g = LocalDate.parse(f[0]);
            int[] got = HijriDate.fromGregorian(g);
            int[] want = {Integer.parseInt(f[3]), Integer.parseInt(f[2]), Integer.parseInt(f[1])};   // y, m, d
            rows++;
            if (got[0] != want[0] || got[1] != want[1] || got[2] != want[2]) {
                wrong++;
                System.out.println("DIFF " + f[0] + " ours " + got[2] + "/" + got[1] + "/" + got[0]
                        + " aladhan " + want[2] + "/" + want[1] + "/" + want[0]);
            }
        }
        // names and format
        String s = HijriDate.format(LocalDate.of(2026, 10, 20));
        boolean fmt = s.equals("9 جمادى الأولى 1448 هـ");
        if (!fmt) System.out.println("FORMAT got [" + s + "]");
        boolean names = HijriDate.MONTHS_AR.length == 12 && HijriDate.MONTHS_AR[8].equals("رمضان");
        System.out.println("rows=" + rows + " wrong=" + wrong);
        if (rows < 60 || wrong > 0 || !fmt || !names) { System.out.println("FAILED HijriDateTest"); System.exit(1); }
        System.out.println("PASS HijriDateTest");
    }
}
