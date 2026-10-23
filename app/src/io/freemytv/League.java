package io.freemytv;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Saudi Pro League for the home drawer: matches (live, upcoming, recent) and the table, parsed from ESPN's public JSON
 * (site.api.espn.com .../soccer/ksa.1) and turned into Arabic text. Pure Java (tests/LeagueTest.java).
 */
final class League {
    private League() {}

    static final class Match {
        String home, away;               // Arabic club names
        int homeScore, awayScore;
        String id;
        String state;                    // "pre", "in", "post"
        String status;                   // ESPN's name, e.g. STATUS_SECOND_HALF
        String clock;                    // display clock, e.g. 67'
        long kickoffMs;
    }

    static final class Row {
        int rank, played, wins, draws, losses, gd, points;
        String team;
    }

    private static final Map<String, String> TEAMS = new HashMap<>();
    static {
        String[][] t = {
            {"Al Qadsiah", "القادسية"}, {"Al Hilal", "الهلال"}, {"Al Nassr", "النصر"}, {"Al Ahli", "الأهلي"},
            {"Al Ittihad", "الاتحاد"}, {"Neom SC", "نيوم"}, {"Al Ettifaq", "الاتفاق"}, {"Al Diriyah", "الدرعية"},
            {"Al Kholood", "الخلود"}, {"Al Khaleej", "الخليج"}, {"Al Hazem", "الحزم"}, {"Al Riyadh", "الرياض"},
            {"Al Shabab", "الشباب"}, {"Al Fayha", "الفيحاء"}, {"Al Faisaly", "الفيصلي"}, {"Al Fateh", "الفتح"},
            {"Al Taawoun", "التعاون"}, {"Abha", "أبها"},
            // clubs that may come back or arrive with promotion
            {"Damac", "ضمك"}, {"Al Okhdood", "الأخدود"}, {"Al Raed", "الرائد"}, {"Al Najma", "النجمة"},
            {"Al Orobah", "العروبة"}, {"Al Wehda", "الوحدة"}, {"Al Adalah", "العدالة"}, {"Al Batin", "الباطن"},
            {"Al Jabalain", "الجبلين"}, {"Al Tai", "الطائي"}, {"Al Ain", "العين"}, {"Jeddah Club", "نادي جدة"},
        };
        for (String[] p : t) TEAMS.put(p[0], p[1]);
    }

    /** The Arabic club name; an unknown club keeps ESPN's English name. */
    static String teamAr(String espn) {
        String ar = TEAMS.get(espn);
        return ar != null ? ar : espn;
    }

    static List<Match> parseMatches(String json) {
        List<Match> out = new ArrayList<>();
        Object root;
        try {
            root = Json.parse(json);
        } catch (IllegalArgumentException e) {
            return out;
        }
        Object events = Json.get(root, "events");
        if (!(events instanceof List)) return out;
        for (Object e : (List<?>) events) {
            Match m = new Match();
            Object comps = Json.get(e, "competitions", 0, "competitors");
            if (!(comps instanceof List)) continue;
            for (Object c : (List<?>) comps) {
                String side = Json.str(c, "homeAway"), name = teamAr(String.valueOf(Json.str(c, "team", "displayName")));
                double score = Json.num(c, "score");
                int sc = Double.isNaN(score) ? 0 : (int) score;
                if ("home".equals(side)) { m.home = name; m.homeScore = sc; }
                else if ("away".equals(side)) { m.away = name; m.awayScore = sc; }
            }
            m.id = Json.str(e, "id");
            m.state = Json.str(e, "status", "type", "state");
            m.status = Json.str(e, "status", "type", "name");
            m.clock = Json.str(e, "status", "displayClock");
            String date = Json.str(e, "date");
            if (m.home == null || m.away == null || m.state == null || date == null || date.length() < 16) continue;
            try {
                m.kickoffMs = LocalDateTime.parse(date.substring(0, 16)).toInstant(ZoneOffset.UTC).toEpochMilli();
            } catch (RuntimeException ex) {
                continue;
            }
            out.add(m);
        }
        return out;
    }

    static List<Row> parseStandings(String json) {
        List<Row> out = new ArrayList<>();
        Object root;
        try {
            root = Json.parse(json);
        } catch (IllegalArgumentException e) {
            return out;
        }
        Object entries = Json.get(root, "children", 0, "standings", "entries");
        if (!(entries instanceof List)) return out;
        for (Object e : (List<?>) entries) {
            Row r = new Row();
            r.team = teamAr(String.valueOf(Json.str(e, "team", "displayName")));
            Object stats = Json.get(e, "stats");
            if (!(stats instanceof List)) continue;
            for (Object s : (List<?>) stats) {
                String n = Json.str(s, "name");
                double v = Json.num(s, "value");
                if (n == null || Double.isNaN(v)) continue;
                int x = (int) Math.round(v);
                switch (n) {
                    case "rank": r.rank = x; break;
                    case "points": r.points = x; break;
                    case "gamesPlayed": r.played = x; break;
                    case "wins": r.wins = x; break;
                    case "ties": r.draws = x; break;
                    case "losses": r.losses = x; break;
                    case "pointDifferential": r.gd = x; break;
                    default:
                }
            }
            out.add(r);
        }
        Collections.sort(out, new Comparator<Row>() {
            @Override public int compare(Row a, Row b) { return Integer.compare(a.rank, b.rank); }
        });
        return out;
    }

    /** {@code base} with the matches of {@code fresh} swapped in (same id) or added. */
    static List<Match> merge(List<Match> base, List<Match> fresh) {
        List<Match> out = new ArrayList<>(base);
        for (Match f : fresh) {
            boolean replaced = false;
            for (int i = 0; i < out.size() && f.id != null; i++) {
                if (f.id.equals(out.get(i).id)) { out.set(i, f); replaced = true; break; }
            }
            if (!replaced) out.add(f);
        }
        return out;
    }

    static List<Match> live(List<Match> all) {
        List<Match> out = new ArrayList<>();
        for (Match m : all) if ("in".equals(m.state)) out.add(m);
        return out;
    }

    /** Matches still to be played, soonest first. */
    static List<Match> upcoming(List<Match> all, long nowMs, int max) {
        List<Match> out = new ArrayList<>();
        for (Match m : all) if ("pre".equals(m.state)) out.add(m);
        Collections.sort(out, new Comparator<Match>() {
            @Override public int compare(Match a, Match b) { return Long.compare(a.kickoffMs, b.kickoffMs); }
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** Matches that finished in the last 20 hours, latest first. */
    static List<Match> recent(List<Match> all, long nowMs, int max) {
        List<Match> out = new ArrayList<>();
        for (Match m : all) if ("post".equals(m.state) && m.kickoffMs <= nowMs && nowMs - m.kickoffMs <= 20 * 3600_000L) out.add(m);
        Collections.sort(out, new Comparator<Match>() {
            @Override public int compare(Match a, Match b) { return Long.compare(b.kickoffMs, a.kickoffMs); }
        });
        return out.size() > max ? new ArrayList<>(out.subList(0, max)) : out;
    }

    /** A score belongs to one named team. Never use an RTL-ambiguous home-away score string. */
    static String scoreFor(Match m, boolean home) {
        return "pre".equals(m.state)?"–":Integer.toString(home?m.homeScore:m.awayScore);
    }

    /** The match clock in Arabic: minute for a match in play, "استراحة" at half-time, "انتهت" when over. */
    static String clockAr(Match m) {
        String s = m.status == null ? "" : m.status;
        if (s.equals("STATUS_HALFTIME")) return "استراحة";
        if (s.equals("STATUS_FULL_TIME") || "post".equals(m.state)) return "انتهت";
        if (s.contains("PENALT")) return "ركلات الترجيح";
        if (m.clock != null && !m.clock.isEmpty()) return m.clock;
        return "مباشر";
    }

    /** "اليوم 9:00 م", "غدًا 4:40 م", "الخميس 4:40 م" (within a week), else "5 نوفمبر 11:00 م". */
    static String whenAr(long kickoffMs, long nowMs, int tzOffsetMs) {
        long dayMs = 86_400_000L;
        long kd = Math.floorDiv(kickoffMs + tzOffsetMs, dayMs), nd = Math.floorDiv(nowMs + tzOffsetMs, dayMs);
        double minutes = Math.floorMod(kickoffMs + tzOffsetMs, dayMs) / 60_000.0;
        String t = Info.time12(minutes);
        long diff = kd - nd;
        if (diff == 0) return "اليوم " + t;
        if (diff == 1) return "غدًا " + t;
        java.time.LocalDate d = java.time.LocalDate.ofEpochDay(kd);
        if (diff > 1 && diff < 7) return Info.date(d).weekday + " " + t;
        String g = Info.date(d).gregorian;                 // "5 نوفمبر 2026"
        return g.substring(0, g.lastIndexOf(' ')) + " " + t;
    }
}
