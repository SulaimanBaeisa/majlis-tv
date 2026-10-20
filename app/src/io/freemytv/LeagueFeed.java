package io.freemytv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches the Saudi Pro League from ESPN's public JSON (no key): the month's fixtures (every 30 min), today's
 * scoreboard (every minute while a match is on, else every 10 min) and the table (every 5 min while a match is on,
 * else every 30 min). Everything runs on one background thread; {@link Listener#onLeague} is called when data changed.
 */
final class LeagueFeed {
    interface Listener { void onLeague(); }

    private static final String BASE = "https://site.api.espn.com/apis";
    private static final int MAX_BYTES = 3 * 1024 * 1024;     // a response bigger than this is dropped

    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile List<League.Match> month = Collections.emptyList(), today = Collections.emptyList();
    private volatile List<League.Row> table = Collections.emptyList();
    private volatile long monthAt, todayAt, tableAt;
    volatile long version;

    LeagueFeed(Listener listener) { this.listener = listener; }

    List<League.Match> matches() { return League.merge(month, today); }

    List<League.Row> table() { return table; }

    boolean hasData() { return !month.isEmpty() || !table.isEmpty(); }

    /** Cheap; call it from the 15 s tick. */
    void refreshIfStale() {
        long now = System.currentTimeMillis();
        boolean live = !League.live(matches()).isEmpty();
        boolean needMonth = now - monthAt > 30 * 60_000L;
        boolean needToday = now - todayAt > (live ? 60_000L : 10 * 60_000L);
        boolean needTable = now - tableAt > (live ? 5 * 60_000L : 30 * 60_000L);
        if (!(needMonth || needToday || needTable) || !busy.compareAndSet(false, true)) return;
        io.execute(() -> {
            boolean changed = false;
            try {
                if (needMonth) {
                    List<League.Match> all = new ArrayList<>(League.parseMatches(get("/site/v2/sports/soccer/ksa.1/scoreboard?dates=" + yyyymm(LocalDate.now()))));
                    all.addAll(League.parseMatches(get("/site/v2/sports/soccer/ksa.1/scoreboard?dates=" + yyyymm(LocalDate.now().plusMonths(1)))));
                    if (!all.isEmpty()) { month = all; changed = true; }
                    monthAt = System.currentTimeMillis();
                }
                if (needToday) {
                    List<League.Match> t = League.parseMatches(get("/site/v2/sports/soccer/ksa.1/scoreboard"));
                    today = t;
                    changed = true;
                    todayAt = System.currentTimeMillis();
                }
                if (needTable) {
                    List<League.Row> rows = League.parseStandings(get("/v2/sports/soccer/ksa.1/standings"));
                    if (!rows.isEmpty()) { table = rows; changed = true; }
                    tableAt = System.currentTimeMillis();
                }
            } catch (IOException | RuntimeException e) {
                AppLog.w("league: " + e.getMessage());
                // try again in a minute rather than hammering
                long retry = System.currentTimeMillis() - 29 * 60_000L;
                if (needMonth) monthAt = Math.max(monthAt, retry);
                if (needToday) todayAt = Math.max(todayAt, System.currentTimeMillis() - 9 * 60_000L);
                if (needTable) tableAt = Math.max(tableAt, retry);
            } finally {
                busy.set(false);
            }
            if (changed) {
                AppLog.w("league: " + matches().size() + " matches (" + League.live(matches()).size() + " live), " + table.size() + " table rows");
                version++;
                listener.onLeague();
            }
        });
    }

    private static String yyyymm(LocalDate d) {
        return d.getYear() + (d.getMonthValue() < 10 ? "0" : "") + d.getMonthValue();
    }

    private static String get(String path) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(10_000);
        c.setReadTimeout(20_000);
        c.setRequestProperty("User-Agent", "FreeMyTV");
        try {
            if (c.getResponseCode() != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream()) {
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
        } finally {
            c.disconnect();
        }
    }
}
