package io.freemytv;

/** Plain-Java test (no Android, no libraries). Run: tests/run.sh */
public class SafeTest {
    static int failed = 0;

    static void check(String name, boolean ok) {
        if (!ok) { failed++; System.out.println("FAIL " + name); }
    }

    public static void main(String[] a) {
        // accepted: real component names
        check("plain", Safe.component("com.google.android.tvlauncher/.MainActivity"));
        check("full class", Safe.component("com.threethan.launcher/com.threethan.LightningLauncher"));
        check("inner class", Safe.component("com.a.b/com.a.b.Main$Inner"));
        check("fallback home", Safe.component("com.android.tv.settings/.system.FallbackHome"));
        // rejected: anything that could be shell syntax or is empty/odd
        check("semicolon", !Safe.component("x.y/.A;reboot"));
        check("space", !Safe.component("x.y/.A reboot"));
        check("pipe", !Safe.component("x.y/.A|id"));
        check("backtick", !Safe.component("x.y/.`id`"));
        check("dollar paren", !Safe.component("x.y/.$(id)"));
        check("newline", !Safe.component("x.y/.A\nreboot"));
        check("quote", !Safe.component("x.y/.A'"));
        check("no slash", !Safe.component("com.google.android.tvlauncher"));
        check("empty", !Safe.component(""));
        check("null", !Safe.component(null));
        check("slash only", !Safe.component("/"));
        // package names
        check("pkg ok", Safe.packageName("com.google.android.tvlauncher"));
        check("pkg bad", !Safe.packageName("a.b;c"));
        check("pkg empty", !Safe.packageName(""));
        System.out.println(failed == 0 ? "PASS SafeTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
