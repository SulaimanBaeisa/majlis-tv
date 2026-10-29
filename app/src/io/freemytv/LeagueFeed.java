package io.freemytv;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Football from ESPN's public JSON (no key) for the competitions picked in Settings > Football (Competitions). Only
 * the competition on screen is fetched: the month's fixtures (every 30 min), today's scoreboard (every minute while a
 * match is on, else every 10 min) and, for leagues, the table (every 5 min while a match is on, else every 30 min).
 * Each competition has its own data, timers and cache files. One background thread, one fetch at a time
 * (RefreshGate); failures back off for 2 minutes, a competition ESPN doesn't know for 6 hours.
 * {@link Listener#onLeague} is called after every fetch.
 */
final class LeagueFeed {
    interface Listener { void onLeague(); }

    static final int UNKNOWN = -1;
    private static final String BASE = "https://site.api.espn.com/apis";
    private static final int MAX_BYTES = 3 * 1024 * 1024;     // a response bigger than this is dropped
    private static final long MIN = 60_000L, FAIL_RETRY = 2 * MIN, UNSUPPORTED_RETRY = 6 * 60 * MIN;

    private static final class Feed {
        final String id;
        volatile List<League.Match> month = Collections.emptyList(), today = Collections.emptyList();
        volatile List<League.Row> table = Collections.emptyList();
        volatile long monthAt, todayAt, tableAt, updatedAt, failedAt;
        volatile int status = UNKNOWN;
        volatile boolean restored;
        Feed(String id) { this.id = id; }
    }

    private static final class Response {
        final int code;
        final String body;
        Response(int code, String body) { this.code = code; this.body = body; }
    }

    private final Listener listener;
    private final File dir;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Map<String, Feed> feeds = new ConcurrentHashMap<>();
    final RefreshGate gate = new RefreshGate();
    private volatile String currentId;
    volatile long version;

    LeagueFeed(Listener listener, File cacheDir) {
        this.listener = listener;
        this.dir = new File(cacheDir, "league-v2");
    }

    /** The followed competitions (settings), in catalogue order. */
    List<String> followed() { return Prefs.competitions(); }

    /** The competition on screen; null when none is followed. */
    Competitions.Competition current() {
        List<String> f = followed();
        if (f.isEmpty()) return null;
        String id = currentId;
        if (id == null || !f.contains(id)) currentId = id = f.get(0);
        return Competitions.byId(id);
    }

    /** "2 / 4": which followed competition is on screen (1-based) and how many there are. */
    int position() { current(); return followed().indexOf(currentId) + 1; }
    int count() { return followed().size(); }

    /** ← / → in the matches tab: the previous or next followed competition. */
    boolean step(int dir) {
        List<String> f = followed();
        if (f.size() < 2 || current() == null) return false;
        int i = f.indexOf(currentId);
        currentId = f.get(Math.floorMod(i + dir, f.size()));
        version++;
        refreshIfStale();
        return true;
    }

    private Feed feed() {
        Competitions.Competition c = current();
        if (c == null) return null;
        Feed f = feeds.get(c.id);
        if (f == null) {
            f = new Feed(c.id);
            Feed had = feeds.putIfAbsent(c.id, f);
            if (had != null) f = had;
        }
        return f;
    }

    List<League.Match> matches() {
        Feed f = feed();
        return f == null ? Collections.<League.Match>emptyList() : League.merge(f.month, f.today);
    }

    List<League.Row> table() {
        Feed f = feed();
        return f == null ? Collections.<League.Row>emptyList() : f.table;
    }

    /** Competitions.OK / UNSUPPORTED / FAILED for the competition on screen, or UNKNOWN before its first answer. */
    int status() {
        Feed f = feed();
        return f == null ? UNKNOWN : f.status;
    }

    boolean hasData() {
        Feed f = feed();
        return f != null && (!f.month.isEmpty() || !f.today.isEmpty() || !f.table.isEmpty());
    }

    long lastUpdateMs() {
        Feed f = feed();
        return f == null ? 0 : f.updatedAt;
    }

    /** No good answer yet, or the last one is older than RefreshGate.STALE_MS. */
    boolean stale(long now) {
        long at = lastUpdateMs();
        return at == 0 || now - at > RefreshGate.STALE_MS;
    }

    /** Cheap; call it from the 15 s tick. */
    void refreshIfStale() { schedule(false); }

    /** The refresh button: true if a fetch started (see RefreshGate for when it doesn't). Never blocks. */
    boolean refreshNow() { return schedule(true); }

    private boolean schedule(boolean manual) {
        final Feed f = feed();
        if (f == null) return false;
        Competitions.Competition c = Competitions.byId(f.id);
        long now = System.currentTimeMillis();
        boolean live = !League.live(League.merge(f.month, f.today)).isEmpty();
        boolean month, today, table;
        if (manual) {
            month = now - f.monthAt > 5 * MIN;
            today = true;
            table = c.hasTable();
        } else {
            if (f.status == Competitions.UNSUPPORTED && now - f.failedAt < UNSUPPORTED_RETRY) return false;
            if (f.status == Competitions.FAILED && now >= f.failedAt && now - f.failedAt < FAIL_RETRY) return false;
            month = now - f.monthAt > 30 * MIN;
            today = now - f.todayAt > (live ? MIN : 10 * MIN);
            table = c.hasTable() && now - f.tableAt > (live ? 5 * MIN : 30 * MIN);
            if (!(month || today || table || !f.restored)) return false;
        }
        if (manual ? !gate.press(now) : !gate.begin()) return false;
        final boolean needMonth = month, needToday = today, needTable = table;
        io.execute(() -> run(f, needMonth, needToday, needTable));
        return true;
    }

    private void run(Feed f, boolean needMonth, boolean needToday, boolean needTable) {
        boolean ok = false;
        long now = System.currentTimeMillis();
        try {
            if (!f.restored) restore(f);
            if (needToday) {
                Response r = get(Competitions.scoreboard(f.id) + "?dates="
                        + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
                int kind = Competitions.classify(r.code, r.body);
                if (kind == Competitions.FAILED) throw new IOException("scoreboard HTTP " + r.code);
                if (kind == Competitions.UNSUPPORTED) {
                    f.status = Competitions.UNSUPPORTED;
                    f.failedAt = f.todayAt = f.monthAt = f.tableAt = now;
                    AppLog.w("league " + f.id + ": not covered by the source (HTTP " + r.code + ")");
                    return;
                }
                f.today = League.parseMatches(r.body);
                f.todayAt = now;
                f.status = Competitions.OK;
            }
            if (needMonth) {
                List<League.Match> all = new ArrayList<>();
                LocalDate d = LocalDate.now();
                for (int i = 0; i < 2; i++) {
                    Response r = get(Competitions.month(f.id, yyyymm(d.plusMonths(i))));
                    int kind = Competitions.classify(r.code, r.body);
                    if (kind == Competitions.FAILED) throw new IOException("fixtures HTTP " + r.code);
                    if (kind == Competitions.UNSUPPORTED) {
                        f.status = Competitions.UNSUPPORTED;
                        f.failedAt = f.monthAt = f.tableAt = now;
                        return;
                    }
                    all.addAll(League.parseMatches(r.body));
                    save(f.id, "month" + i, r.body);
                }
                f.month = Collections.unmodifiableList(all);   // empty is honest: no fixtures this month or next
                f.monthAt = now;
                f.status = Competitions.OK;
            }
            if (needTable) {
                Response r = get(Competitions.standings(f.id));
                if (r.code == 200) {
                    List<League.Row> rows = League.parseStandings(r.body);
                    f.table = Collections.unmodifiableList(rows);
                    save(f.id, "table", r.body);
                } else if (r.code != 400 && r.code != 404) {
                    throw new IOException("table HTTP " + r.code);
                }
                f.tableAt = now;
            }
            ok = f.status != Competitions.UNSUPPORTED;
            if (ok) {
                f.status = Competitions.OK;
                f.updatedAt = now;
            }
        } catch (IOException | RuntimeException e) {
            AppLog.w("league " + f.id + ": " + e.getMessage());
            f.failedAt = now;
            f.status = Competitions.FAILED;
            // Keep the timestamps of successful data; schedule() separately enforces retry backoff.
        } finally {
            gate.end(System.currentTimeMillis(), ok);
            version++;
            AppLog.w("league " + f.id + ": " + League.merge(f.month, f.today).size() + " matches, " + f.table.size() + " table rows, status " + f.status);
            listener.onLeague();
        }
    }

    // MARK: - Cache (one file per competition and kind; see Competitions.cacheKey)

    private void restore(Feed f) {
        f.restored = true;
        List<League.Match> all = new ArrayList<>();
        long at = 0;
        for (int i = 0; i < 2; i++) {
            File file = new File(dir, Competitions.cacheKey(f.id, "month" + i) + ".json");
            String body = read(file);
            if (body == null) continue;
            all.addAll(League.parseMatches(body));
            at = Math.max(at, file.lastModified());
        }
        if (!all.isEmpty() && f.month.isEmpty()) f.month = Collections.unmodifiableList(all);
        File tf = new File(dir, Competitions.cacheKey(f.id, "table") + ".json");
        String table = read(tf);
        if (table != null && f.table.isEmpty()) {
            List<League.Row> rows = League.parseStandings(table);
            if (!rows.isEmpty()) f.table = Collections.unmodifiableList(rows);
        }
        if (at > 0 && f.updatedAt == 0) {
            f.updatedAt = at;            // shown as stale once older than RefreshGate.STALE_MS
            if (f.status == UNKNOWN) f.status = Competitions.OK;
        }
    }

    private static String read(File f) {
        if (!f.isFile() || f.length() > MAX_BYTES) return null;
        try (FileInputStream in = new FileInputStream(f)) {
            return readAll(in);
        } catch (IOException e) {
            return null;
        }
    }

    private void save(String id, String kind, String body) {
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        File part = new File(dir, Competitions.cacheKey(id, kind) + ".json.part"), dest = new File(dir, Competitions.cacheKey(id, kind) + ".json");
        try (FileOutputStream out = new FileOutputStream(part)) {
            out.write(body.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            AppLog.w("league cache: " + e.getMessage());
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        if (!part.renameTo(dest)) part.delete();
    }

    private static String yyyymm(LocalDate d) {
        return d.getYear() + (d.getMonthValue() < 10 ? "0" : "") + d.getMonthValue();
    }

    private static Response get(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent", "Majlis");
        try {
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) return new Response(code, null);
            try (InputStream in = c.getInputStream()) {
                return new Response(code, readAll(in));
            }
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[16384];
        int n, total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > MAX_BYTES) throw new IOException("response too large");
            out.write(buf, 0, n);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
