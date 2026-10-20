package io.freemytv;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Plain-Java test: Saudi Pro League data (ESPN JSON samples fetched 2026-10-20) turned into Arabic card text. */
public class LeagueTest {
    static int failed = 0;
    static final int TZ = 3 * 3600_000;

    static void eq(String name, Object got, Object want) {
        if (!String.valueOf(want).equals(String.valueOf(got))) { failed++; System.out.println("FAIL " + name + ": got [" + got + "], want [" + want + "]"); }
    }

    static String read(String f) throws Exception {
        return new String(Files.readAllBytes(Paths.get("tests/fixtures/" + f)), StandardCharsets.UTF_8);
    }

    static long ms(String iso) { return Instant.parse(iso).toEpochMilli(); }

    public static void main(String[] a) throws Exception {
        // standings
        List<League.Row> rows = League.parseStandings(read("espn-ksa1-standings.json"));
        eq("standings rows", rows.size(), 18);
        eq("rank 1 team", rows.get(0).team, "القادسية");
        eq("rank 1 points", rows.get(0).points, 25);
        eq("rank 1 played", rows.get(0).played, 10);
        eq("rank 2 team", rows.get(1).team, "الهلال");
        eq("sorted by rank", rows.get(0).rank + "," + rows.get(17).rank, "1,18");
        for (League.Row r : rows) if (r.team.matches(".*[A-Za-z].*")) { failed++; System.out.println("FAIL unmapped team: " + r.team); }
        eq("goal difference field", rows.get(0).gd == rows.get(0).gd, true);

        // matches (two months)
        List<League.Match> all = new ArrayList<>(League.parseMatches(read("espn-ksa1-month-202610.json")));
        all.addAll(League.parseMatches(read("espn-ksa1-month-202611.json")));
        eq("matches parsed", all.size(), 41 + 27);
        for (League.Match m : all) if (m.home.matches(".*[A-Za-z].*") || m.away.matches(".*[A-Za-z].*")) { failed++; System.out.println("FAIL unmapped: " + m.home + " / " + m.away); break; }

        long now = ms("2026-10-20T15:30:00Z");       // 18:30 in Jeddah
        List<League.Match> up = League.upcoming(all, now, 4);
        eq("upcoming count", up.size(), 4);
        eq("upcoming 1", up.get(0).home + " x " + up.get(0).away, "الاتفاق x الاتحاد");
        eq("upcoming 2", up.get(1).home + " x " + up.get(1).away, "النصر x نيوم");
        eq("upcoming 3", up.get(2).home + " x " + up.get(2).away, "الحزم x الفيحاء");
        eq("none live", League.live(all).size(), 0);

        List<League.Match> done = League.recent(all, ms("2026-10-20T17:00:00Z"), 4);
        eq("recent today", done.size(), 2);
        eq("recent score", done.get(0).home + " " + done.get(0).homeScore + "-" + done.get(0).awayScore + " " + done.get(0).away, "التعاون 0-1 القادسية");

        // kickoff labels
        eq("today", League.whenAr(ms("2026-10-20T18:00:00Z"), now, TZ), "اليوم 9:00 م");
        eq("tomorrow", League.whenAr(ms("2026-10-21T13:40:00Z"), now, TZ), "غدًا 4:40 م");
        eq("weekday", League.whenAr(ms("2026-10-22T13:40:00Z"), now, TZ), "الخميس 4:40 م");
        eq("date", League.whenAr(ms("2026-11-05T20:00:00Z"), now, TZ), "5 نوفمبر 11:00 م");
        eq("after midnight local", League.whenAr(ms("2026-10-20T21:30:00Z"), now, TZ), "غدًا 12:30 ص");

        // live match (synthetic, same shape as ESPN's)
        String liveJson = "{\"events\":[{\"id\":\"1\",\"date\":\"2026-10-20T18:00Z\",\"status\":{\"displayClock\":\"67'\",\"period\":2,"
                + "\"type\":{\"name\":\"STATUS_SECOND_HALF\",\"state\":\"in\",\"completed\":false,\"shortDetail\":\"67'\"}},"
                + "\"competitions\":[{\"competitors\":[{\"homeAway\":\"home\",\"score\":\"2\",\"team\":{\"displayName\":\"Al Hilal\"}},"
                + "{\"homeAway\":\"away\",\"score\":\"1\",\"team\":{\"displayName\":\"Al Nassr\"}}]}]}]}";
        List<League.Match> lv = League.parseMatches(liveJson);
        eq("live parsed", lv.size(), 1);
        eq("live state", League.live(lv).size(), 1);
        League.Match m = lv.get(0);
        eq("live teams", m.home + " " + m.homeScore + "-" + m.awayScore + " " + m.away, "الهلال 2-1 النصر");
        eq("live clock", League.clockAr(m), "67'");
        eq("live not upcoming", League.upcoming(lv, ms("2026-10-20T19:00:00Z"), 4).size(), 0);
        eq("halftime", League.clockAr(League.parseMatches(liveJson.replace("STATUS_SECOND_HALF", "STATUS_HALFTIME")).get(0)), "استراحة");
        eq("full time", League.clockAr(done.get(0)), "انتهت");
        eq("garbage json", League.parseMatches("{\"x\":1}").size(), 0);

        // merge: a fresher list replaces matches with the same id and adds new ones
        List<League.Match> base = League.parseMatches(liveJson.replace("STATUS_SECOND_HALF", "STATUS_FIRST_HALF").replace("\"in\"", "\"pre\""));
        List<League.Match> merged = League.merge(base, lv);
        eq("merge size", merged.size(), 1);
        eq("merge takes the fresh one", merged.get(0).state, "in");
        List<League.Match> mergedAll = League.merge(all, lv);
        eq("merge adds", mergedAll.size(), all.size() + 1);

        System.out.println(failed == 0 ? "PASS LeagueTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
