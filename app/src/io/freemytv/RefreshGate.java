package io.freemytv;

/**
 * The matches tab's refresh button: one fetch at a time, at most one manual fetch per cooldown, and the state the
 * button shows (idle, loading, done, failed; stale when the last good data is old). Pure Java, clock passed in
 * (tests/RefreshGateTest.java).
 */
final class RefreshGate {
    static final int IDLE = 0, LOADING = 1, DONE = 2, FAILED = 3;
    /** Presses closer together than this reuse the last result instead of asking ESPN again. */
    static final long COOLDOWN_MS = 20_000;
    /** How long "done" / "failed" stays on the button before it goes back to the arrow. */
    static final long RESULT_MS = 4_000;
    /** Data older than this is shown as stale (the tab says when it was last updated). */
    static final long STALE_MS = 30 * 60_000L;

    private int shown = IDLE, lastResult = IDLE;
    private boolean inFlight, report;
    private long manualAt = Long.MIN_VALUE / 2, resultAt, lastGoodAt;

    /**
     * A press on the button. True if a fetch should start now. False if one is already running (its result will then
     * show on the button) or the last manual fetch was less than COOLDOWN_MS ago (the button flashes that result).
     */
    synchronized boolean press(long now) {
        if (inFlight) { report = true; return false; }
        // A failed request may be retried after a short backoff; never replay an older successful result.
        long cooldown = lastResult == FAILED ? 2_000 : COOLDOWN_MS;
        if (now >= manualAt && now - manualAt < cooldown) {
            shown = lastResult;
            resultAt = now;
            return false;
        }
        manualAt = now;
        report = true;
        inFlight = true;
        return true;
    }

    /** A scheduled refresh (the 15 s tick): true if it may start (never two at once; no cooldown). */
    synchronized boolean begin() {
        if (inFlight) return false;
        inFlight = true;
        report = false;
        return true;
    }

    /** The fetch that {@link #press} or {@link #begin} allowed has ended. */
    synchronized void end(long now, boolean ok) {
        inFlight = false;
        if (ok) lastGoodAt = now;
        lastResult = ok ? DONE : FAILED;
        shown = report ? lastResult : IDLE;
        if (report) resultAt = now;
        report = false;
    }

    synchronized boolean busy() { return inFlight; }

    /** What the button shows right now: loading only for a fetch someone asked for. */
    synchronized int state(long now) {
        if (inFlight) return report ? LOADING : IDLE;
        if ((shown == DONE || shown == FAILED) && now - resultAt < RESULT_MS) return shown;
        return IDLE;
    }

    /** True when there never was good data, or it is older than STALE_MS. */
    synchronized boolean stale(long now) {
        return lastGoodAt == 0 || now - lastGoodAt > STALE_MS;
    }

    synchronized long lastGoodAt() { return lastGoodAt; }

    /** Data restored from the cache at start counts as good data from the time it was saved. */
    synchronized void restored(long at) { if (at > lastGoodAt) lastGoodAt = at; }
}
