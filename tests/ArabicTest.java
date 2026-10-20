package io.freemytv;

/** Plain-Java test. Run: tests/run.sh */
public class ArabicTest {
    static int failed = 0;

    static void eq(String name, String got, String want) {
        if (!want.equals(got)) { failed++; System.out.println("FAIL " + name + ": got [" + got + "], want [" + want + "]"); }
    }

    public static void main(String[] a) {
        // lookup by English key
        eq("settings", Arabic.text("Settings"), "الإعدادات");
        eq("live tv", Arabic.text("Live TV"), "البث المباشر");
        eq("unknown key stays English", Arabic.text("zzz not a key"), "zzz not a key");
        // "ago" with Arabic number agreement
        eq("now", Arabic.ago(0), "الآن");
        eq("1 min", Arabic.ago(1), "منذ دقيقة");
        eq("2 min", Arabic.ago(2), "منذ دقيقتين");
        eq("5 min", Arabic.ago(5), "منذ 5 دقائق");
        eq("10 min", Arabic.ago(10), "منذ 10 دقائق");
        eq("11 min", Arabic.ago(11), "منذ 11 دقيقة");
        eq("59 min", Arabic.ago(59), "منذ 59 دقيقة");
        eq("1 h", Arabic.ago(60), "منذ ساعة");
        eq("2 h", Arabic.ago(120), "منذ ساعتين");
        eq("3 h", Arabic.ago(180), "منذ 3 ساعات");
        eq("12 h", Arabic.ago(12 * 60), "منذ 12 ساعة");
        eq("2 days", Arabic.ago(48 * 60), "منذ يومين");
        eq("5 days", Arabic.ago(5 * 1440), "منذ 5 أيام");
        eq("15 days", Arabic.ago(15 * 1440), "منذ 15 يومًا");
        System.out.println(failed == 0 ? "PASS ArabicTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
