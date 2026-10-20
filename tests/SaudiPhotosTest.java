package io.freemytv;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

/** Plain-Java test: the screensaver's Saudi photo list (assets/saudi/credits.tsv) and its on-screen credit line. */
public class SaudiPhotosTest {
    static int failed = 0;

    static void eq(String name, Object got, Object want) {
        if (!String.valueOf(want).equals(String.valueOf(got))) { failed++; System.out.println("FAIL " + name + ": got [" + got + "], want [" + want + "]"); }
    }

    public static void main(String[] a) throws Exception {
        String tsv = new String(Files.readAllBytes(Paths.get("app/assets/saudi/credits.tsv")), StandardCharsets.UTF_8);
        List<SaudiPhotos.Photo> list = SaudiPhotos.parse(tsv);
        eq("count", list.size(), 9);
        SaudiPhotos.Photo p = list.get(0);
        eq("file", p.file, "s1.jpg");
        eq("title", p.title, "مسجد الرحمة (المسجد العائم)");
        eq("meta", p.meta(), "جدة · Sammy Six · CC BY 2.0");
        for (SaudiPhotos.Photo x : list) {
            if (!Files.exists(Paths.get("app/assets/saudi/" + x.file))) { failed++; System.out.println("FAIL missing file " + x.file); }
            if (x.license.isEmpty() || x.artist.isEmpty() || x.source.isEmpty()) { failed++; System.out.println("FAIL credit incomplete " + x.file); }
            if (!x.license.matches("CC0|Public domain|CC BY(-SA)? [0-9.]+")) { failed++; System.out.println("FAIL license not allowed: " + x.license); }
        }
        eq("rotate keeps all", SaudiPhotos.rotate(list, 3).size(), 9);
        eq("rotate first", SaudiPhotos.rotate(list, 3).get(0).file, "s4.jpg");
        eq("rotate wraps", SaudiPhotos.rotate(list, 9 * 100 + 1).get(0).file, "s2.jpg");
        // the home wallpaper: one photo per day, the next one tomorrow, every photo gets its turn
        eq("today is first of rotation", SaudiPhotos.today(list, 5).file, SaudiPhotos.rotate(list, 5).get(0).file);
        eq("today 0", SaudiPhotos.today(list, 0).file, "s1.jpg");
        eq("tomorrow differs", SaudiPhotos.today(list, 100).file.equals(SaudiPhotos.today(list, 101).file), false);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (long d = 20_000; d < 20_000 + 9; d++) seen.add(SaudiPhotos.today(list, d).file);
        eq("9 days show 9 different photos", seen.size(), 9);
        eq("same day same photo", SaudiPhotos.today(list, 20_500).file, SaudiPhotos.today(list, 20_500).file);
        eq("empty list", SaudiPhotos.today(new java.util.ArrayList<SaudiPhotos.Photo>(), 3), null);
        eq("parse skips comments and junk", SaudiPhotos.parse("# x\n\nonly\tthree\tfields\n").size(), 0);
        System.out.println(failed == 0 ? "PASS SaudiPhotosTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
