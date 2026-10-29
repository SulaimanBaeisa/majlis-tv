package io.freemytv;

/** Plain-Java test: the matches refresh button never runs two fetches at once, rate-limits presses and shows honest states. */
public class RefreshGateTest {
    static int failed = 0;

    static void eq(String name, Object got, Object want) {
        if (!String.valueOf(want).equals(String.valueOf(got))) { failed++; System.out.println("FAIL " + name + ": got [" + got + "], want [" + want + "]"); }
    }

    public static void main(String[] a) {
        long t = 1_000_000;
        RefreshGate g = new RefreshGate();
        eq("starts idle", g.state(t), RefreshGate.IDLE);
        eq("no data is stale", g.stale(t), true);

        eq("press starts", g.press(t), true);
        eq("loading shown", g.state(t + 10), RefreshGate.LOADING);
        eq("second press while running refused", g.press(t + 100), false);
        eq("tick while running refused", g.begin(), false);
        eq("busy", g.busy(), true);
        g.end(t + 2_000, true);
        eq("done shown", g.state(t + 2_100), RefreshGate.DONE);
        eq("done fades back to arrow", g.state(t + 2_000 + RefreshGate.RESULT_MS + 1), RefreshGate.IDLE);
        eq("fresh after success", g.stale(t + 2_100), false);
        eq("last good time", g.lastGoodAt(), t + 2_000);

        eq("press within cooldown refused", g.press(t + 5_000), false);
        eq("cooldown press flashes last result", g.state(t + 5_001), RefreshGate.DONE);
        eq("press after cooldown starts", g.press(t + RefreshGate.COOLDOWN_MS + 1), true);
        g.end(t + RefreshGate.COOLDOWN_MS + 500, false);
        eq("failure shown", g.state(t + RefreshGate.COOLDOWN_MS + 600), RefreshGate.FAILED);
        eq("failure keeps last good time", g.lastGoodAt(), t + 2_000);
        eq("stale later", g.stale(t + 2_000 + RefreshGate.STALE_MS + 1), true);

        // a scheduled fetch shows nothing on the button, unless someone pressed while it ran
        RefreshGate s = new RefreshGate();
        eq("tick starts", s.begin(), true);
        eq("tick is quiet", s.state(t), RefreshGate.IDLE);
        s.end(t + 1, true);
        eq("tick result quiet", s.state(t + 2), RefreshGate.IDLE);
        eq("tick refreshes freshness", s.stale(t + 2), false);
        eq("tick again", s.begin(), true);
        eq("press during tick joins it", s.press(t + 3), false);
        eq("joined shows loading", s.state(t + 4), RefreshGate.LOADING);
        s.end(t + 5, false);
        eq("joined shows result", s.state(t + 6), RefreshGate.FAILED);

        RefreshGate latest = new RefreshGate();
        latest.press(t);
        latest.end(t + 100, true);
        latest.begin();
        latest.end(t + 200, false);
        latest.press(t + 300);
        eq("cooldown never replays success after newer failure", latest.state(t + 301), RefreshGate.FAILED);
        eq("failed update can retry after short backoff", latest.press(t + 2_500), true);

        RefreshGate r = new RefreshGate();
        r.restored(t - 1000);
        eq("restored cache counts", r.stale(t), false);

        System.out.println(failed == 0 ? "PASS RefreshGateTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
