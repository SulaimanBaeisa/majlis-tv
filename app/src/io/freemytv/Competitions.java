package io.freemytv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The football competitions the matches tab can follow: exactly 20, each with its ESPN soccer slug on the host the
 * app already uses (site.api.espn.com, public JSON, no key). Settings > Football stores the chosen ids; with none
 * chosen the matches tab says so and nothing is fetched. Pure Java (tests/CompetitionsTest.java).
 *
 * A slug being listed here is not a promise that ESPN covers it right now: LeagueFeed tells "unsupported" (HTTP
 * 400/404 or no league in the answer) apart from "no matches" (a valid answer with no events: off-season, break).
 */
final class Competitions {
    private Competitions() {}

    static final int LEAGUE = 0, CUP = 1;

    static final class Competition {
        final String id, ar, en;
        final int kind;
        Competition(String id, String ar, String en, int kind) { this.id = id; this.ar = ar; this.en = en; this.kind = kind; }
        /** League competitions have a table; knockout/group tournaments may not. */
        boolean hasTable() { return kind == LEAGUE; }
    }

    static final String SAUDI = "ksa.1";

    /** Order = order in settings and in the matches tab. */
    static final List<Competition> ALL = Collections.unmodifiableList(java.util.Arrays.asList(
        new Competition(SAUDI, "دوري روشن السعودي", "Saudi Pro League", LEAGUE),
        new Competition("eng.1", "الدوري الإنجليزي الممتاز", "Premier League", LEAGUE),
        new Competition("esp.1", "الدوري الإسباني", "LaLiga", LEAGUE),
        new Competition("ita.1", "الدوري الإيطالي", "Serie A", LEAGUE),
        new Competition("ger.1", "الدوري الألماني", "Bundesliga", LEAGUE),
        new Competition("fra.1", "الدوري الفرنسي", "Ligue 1", LEAGUE),
        new Competition("por.1", "الدوري البرتغالي", "Primeira Liga", LEAGUE),
        new Competition("ned.1", "الدوري الهولندي", "Eredivisie", LEAGUE),
        new Competition("tur.1", "الدوري التركي", "Süper Lig", LEAGUE),
        new Competition("ksa.kings.cup", "كأس خادم الحرمين الشريفين", "Saudi King's Cup", CUP),
        new Competition("eng.fa", "كأس الاتحاد الإنجليزي", "FA Cup", CUP),
        new Competition("esp.copa_del_rey", "كأس ملك إسبانيا", "Copa del Rey", CUP),
        new Competition("uefa.champions", "دوري أبطال أوروبا", "UEFA Champions League", CUP),
        new Competition("uefa.europa", "الدوري الأوروبي", "UEFA Europa League", CUP),
        new Competition("uefa.europa.conf", "دوري المؤتمر الأوروبي", "UEFA Conference League", CUP),
        new Competition("afc.champions", "دوري أبطال آسيا للنخبة", "AFC Champions League Elite", CUP),
        new Competition("afc.cup", "دوري أبطال آسيا 2", "AFC Champions League Two", CUP),
        new Competition("fifa.world", "كأس العالم", "FIFA World Cup", CUP),
        new Competition("afc.asian.cup", "كأس آسيا", "AFC Asian Cup", CUP),
        new Competition("fifa.cwc", "كأس العالم للأندية", "FIFA Club World Cup", CUP)));

    /** First run: the Saudi league only (what the matches tab showed before this setting existed). */
    static final List<String> DEFAULT = Collections.singletonList(SAUDI);

    /** All entries can be followed; LeagueFeed fetches only the one currently on screen. */
    static final int MAX_SELECTED = ALL.size();

    static Competition byId(String id) {
        for (Competition c : ALL) if (c.id.equals(id)) return c;
        return null;
    }

    /** Stored form: ids joined by commas, in catalogue order. */
    static String encode(List<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String id : normalize(ids)) sb.append(sb.length() == 0 ? "" : ",").append(id);
        return sb.toString();
    }

    /**
     * The stored value back as ids: unknown or repeated ids dropped, catalogue order, at most MAX_SELECTED. null (never
     * saved) gives the default; "" (the user turned everything off) gives an empty list.
     */
    static List<String> decode(String stored) {
        if (stored == null) return DEFAULT;
        List<String> raw = new ArrayList<>();
        for (String p : stored.split(",")) if (!p.trim().isEmpty()) raw.add(p.trim());
        return normalize(raw);
    }

    static List<String> normalize(List<String> ids) {
        Set<String> want = new LinkedHashSet<>(ids);
        List<String> out = new ArrayList<>();
        for (Competition c : ALL) if (want.contains(c.id) && out.size() < MAX_SELECTED) out.add(c.id);
        return Collections.unmodifiableList(out);
    }

    /** The selection with {@code id} switched on or off; switching on past MAX_SELECTED is refused (same list back). */
    static List<String> toggle(List<String> selected, String id) {
        if (byId(id) == null) return selected;
        List<String> next = new ArrayList<>(selected);
        if (next.contains(id)) next.remove(id);
        else if (next.size() >= MAX_SELECTED) return selected;
        else next.add(id);
        return normalize(next);
    }

    // MARK: - ESPN paths (relative to https://site.api.espn.com/apis)

    static String scoreboard(String id) { return "/site/v2/sports/soccer/" + id + "/scoreboard"; }
    static String month(String id, String yyyymm) { return scoreboard(id) + "?dates=" + yyyymm; }
    static String standings(String id) { return "/v2/sports/soccer/" + id + "/standings"; }

    // MARK: - What an answer means

    /** The source answered and covers the competition, with these matches (possibly none: off-season). */
    static final int OK = 0;
    /** The source does not know this competition (HTTP 400/404, or the answer names no league). */
    static final int UNSUPPORTED = 1;
    /** Network or server trouble (timeouts, 5xx, garbage): try again later, keep what we had. */
    static final int FAILED = 2;

    /** Classifies one scoreboard answer. {@code body} may be null when there was no body. */
    static int classify(int httpStatus, String body) {
        if (httpStatus == 400 || httpStatus == 404) return UNSUPPORTED;
        if (httpStatus != 200 || body == null) return FAILED;
        Object root;
        try {
            root = Json.parse(body);
        } catch (RuntimeException e) {   // malformed or cut-off JSON: a failed fetch, not a verdict on coverage
            return FAILED;
        }
        Object leagues = Json.get(root, "leagues");
        if (!(leagues instanceof List) || ((List<?>) leagues).isEmpty()) return UNSUPPORTED;
        if (!(Json.get(root, "events") instanceof List)) return FAILED;
        return OK;
    }

    /** The cache file for one competition and one kind of data ("month", "today", "table"): never shared. */
    static String cacheKey(String id, String kind) {
        return "league-" + id.replaceAll("[^a-z0-9.]", "_") + "-" + kind;
    }
}
