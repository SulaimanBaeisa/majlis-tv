package io.freemytv;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;

/** Real asset and rotation regression tests; no Android runtime needed. */
public class DailyTest {
    static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
    static String[] at(List<String[]> items, long day) {
        return Info.daily(items, LocalDate.ofEpochDay(day));
    }
    public static void main(String[] args) throws Exception {
        List<String[]> tiny = Info.parseDaily("Q\tq0\ta\nQ\tq1\tb\nH\th0\tc\nH\th1\td\nH\th2\te\nH\th3\tf\n");
        check(at(tiny, 0)[1].equals("q0"), "Q at even epoch day");
        check(at(tiny, 1)[1].equals("h0"), "H at odd epoch day");
        check(at(tiny, 3)[1].equals("h1"), "independent H index, not modulo stride");
        check(at(tiny, 7)[1].equals("h3"), "last H visited before repeat");
        check(at(tiny, 9)[1].equals("h0"), "H cycle wraps");
        check(at(tiny, -1)[1].equals("h3"), "negative day floor division");
        check(at(tiny, -2)[1].equals("q1"), "negative Q day");
        check(Info.daily(null, LocalDate.now()) == null, "null list");
        check(at(new ArrayList<String[]>(), 0) == null, "empty list");
        List<String[]> onlyH = Info.parseDaily("H\th0\tx\nH\th1\ty\n");
        check(at(onlyH, 0)[1].equals("h0") && at(onlyH, 1)[1].equals("h1"), "single kind changes every day");
        String raw = new String(Files.readAllBytes(Paths.get("app/assets/daily.tsv")), StandardCharsets.UTF_8);
        List<String[]> real = Info.parseDaily(raw);
        int q = 0, h = 0;
        Set<String> texts = new HashSet<>();
        for (String[] item : real) {
            if (item[0].equals("Q")) q++;
            else if (item[0].equals("H")) {
                h++;
                check(texts.add(item[2]), "unique hadith text");
                check(item[1].contains(" · "), "source and narrator required");
            } else throw new AssertionError("unknown kind");
        }
        check(q == 29 && h == 60, "real assets: 29 verses and 60 hadith");
        for (long base : new long[] {-20001, 0, 20746}) {
            Set<String> seenH = new HashSet<>(), seenQ = new HashSet<>();
            for (int i = 0; i < 120; i++) {
                String[] item = at(real, base + i);
                check(Arrays.equals(item, at(real, base + i)), "date stability");
                check(item[0].equals(Math.floorMod(base + i, 2) == 0 ? "Q" : "H"), "strict daily alternation");
                if (item[0].equals("H")) check(seenH.add(item[2]), "no H repeat before all 60");
                else seenQ.add(item[2]);
            }
            check(seenH.size() == 60 && seenQ.size() == 29, "full independent cycle coverage");
            check(Arrays.equals(at(real, base), at(real, base + (Math.floorMod(base, 2) == 0 ? 58 : 120))), "cycle wrap");
        }
        System.out.println("PASS DailyTest (29 Q, 60 H; alternation, full cycles, negative dates)");
    }
}
