package io.freemytv;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Plain-Java test: the football catalogue (exactly 20, required ones present), the stored selection, and how an
 * answer is classified (empty / off-season is not "unsupported"). Live ESPN coverage is checked separately (it needs
 * the network); this test never claims a slug works.
 */
public class CompetitionsTest {
    static int failed = 0;

    static void eq(String name, Object got, Object want) {
        if (!String.valueOf(want).equals(String.valueOf(got))) { failed++; System.out.println("FAIL " + name + ": got [" + got + "], want [" + want + "]"); }
    }

    static String read(String f) throws Exception {
        return new String(Files.readAllBytes(Paths.get("tests/fixtures/" + f)), StandardCharsets.UTF_8);
    }

    public static void main(String[] a) throws Exception {
        eq("exactly 20", Competitions.ALL.size(), 20);
        Set<String> ids = new HashSet<>(), ar = new HashSet<>(), en = new HashSet<>();
        for (Competitions.Competition c : Competitions.ALL) {
            if (!ids.add(c.id)) { failed++; System.out.println("FAIL duplicate id " + c.id); }
            if (!ar.add(c.ar)) { failed++; System.out.println("FAIL duplicate Arabic name " + c.ar); }
            if (!en.add(c.en)) { failed++; System.out.println("FAIL duplicate English name " + c.en); }
            if (!c.id.matches("[a-z]+(\\.[a-z0-9_]+)+")) { failed++; System.out.println("FAIL odd slug " + c.id); }
            if (!c.ar.matches(".*[\\u0600-\\u06FF].*")) { failed++; System.out.println("FAIL no Arabic name for " + c.id); }
        }
        for (String need : new String[] {"ksa.1", "eng.1", "esp.1", "uefa.champions", "afc.champions"}) eq("has " + need, ids.contains(need), true);
        eq("saudi first", Competitions.ALL.get(0).id, "ksa.1");
        eq("saudi has table", Competitions.byId("ksa.1").hasTable(), true);
        eq("ucl is a cup", Competitions.byId("uefa.champions").hasTable(), false);
        eq("unknown id", Competitions.byId("xxx.9") == null, true);

        // selection: default, none, order, unknown ids, limit
        eq("never saved = saudi", Competitions.decode(null), Collections.singletonList("ksa.1"));
        eq("saved empty = none", Competitions.decode("").size(), 0);
        eq("catalogue order + drop unknown/dupes", Competitions.decode("esp.1,zzz,eng.1,esp.1"), Arrays.asList("eng.1", "esp.1"));
        List<String> round = Competitions.decode(Competitions.encode(Arrays.asList("uefa.champions", "ksa.1")));
        eq("round trip", round, Arrays.asList("ksa.1", "uefa.champions"));
        List<String> sel = Competitions.DEFAULT;
        sel = Competitions.toggle(sel, "eng.1");
        eq("toggle on", sel, Arrays.asList("ksa.1", "eng.1"));
        sel = Competitions.toggle(sel, "ksa.1");
        eq("toggle off", sel, Collections.singletonList("eng.1"));
        sel = Competitions.toggle(sel, "eng.1");
        eq("toggle to none", sel.size(), 0);
        for (Competitions.Competition c : Competitions.ALL) sel = Competitions.toggle(sel, c.id);
        eq("limit", sel.size(), Competitions.MAX_SELECTED);
        eq("toggle last off at full selection", Competitions.toggle(sel, Competitions.ALL.get(19).id).size(), Competitions.MAX_SELECTED - 1);
        eq("decode caps too", Competitions.decode(String.join(",", ids)).size(), Competitions.MAX_SELECTED);

        // answers: a real ESPN fixture is OK; an empty month is OK (off-season), not unsupported
        eq("fixture ok", Competitions.classify(200, read("espn-ksa1-month-202610.json")), Competitions.OK);
        eq("empty events ok", Competitions.classify(200, "{\"leagues\":[{\"name\":\"Serie A\"}],\"events\":[]}"), Competitions.OK);
        eq("empty events parse to none", League.parseMatches("{\"leagues\":[{\"name\":\"Serie A\"}],\"events\":[]}").size(), 0);
        eq("404 unsupported", Competitions.classify(404, null), Competitions.UNSUPPORTED);
        eq("400 unsupported", Competitions.classify(400, "{\"code\":400}"), Competitions.UNSUPPORTED);
        eq("no league unsupported", Competitions.classify(200, "{\"leagues\":[],\"events\":[]}"), Competitions.UNSUPPORTED);
        eq("500 failed", Competitions.classify(500, null), Competitions.FAILED);
        eq("garbage failed", Competitions.classify(200, "<html>"), Competitions.FAILED);
        eq("no events key failed", Competitions.classify(200, "{\"leagues\":[{\"name\":\"x\"}]}"), Competitions.FAILED);

        // paths and cache isolation
        eq("scoreboard path", Competitions.scoreboard("eng.1"), "/site/v2/sports/soccer/eng.1/scoreboard");
        eq("month path", Competitions.month("ksa.1", "202611"), "/site/v2/sports/soccer/ksa.1/scoreboard?dates=202611");
        eq("standings path", Competitions.standings("esp.1"), "/v2/sports/soccer/esp.1/standings");
        Set<String> keys = new HashSet<>();
        for (Competitions.Competition c : Competitions.ALL) for (String kind : new String[] {"month0", "month1", "table"}) keys.add(Competitions.cacheKey(c.id, kind));
        eq("cache keys distinct", keys.size(), 60);

        System.out.println(failed == 0 ? "PASS CompetitionsTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
