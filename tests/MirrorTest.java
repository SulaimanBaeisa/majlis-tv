package io.freemytv;

import java.text.Bidi;

/** Plain-Java test for the pure part of the right-to-left layout (Mirror). */
public class MirrorTest {
    static int failed = 0;

    static void ok(String name, boolean cond) {
        if (!cond) { failed++; System.out.println("FAIL " + name); }
    }

    static void eq(String name, Object got, Object want) {
        if (!String.valueOf(want).equals(String.valueOf(got))) { failed++; System.out.println("FAIL " + name + ": got [" + got + "], want [" + want + "]"); }
    }

    public static void main(String[] a) {
        // geometry: mirror inside 0..1920
        eq("x", Mirror.x(100, 0, 1920), 1820f);
        eq("x twice", Mirror.x(Mirror.x(123.5f, 0, 1920), 0, 1920), 123.5f);
        eq("left of mirrored rect", Mirror.left(100, 150, 0, 1920), 1770f);
        eq("right of mirrored rect", Mirror.right(100, 150, 0, 1920), 1820f);
        eq("inside a card", Mirror.x(10, 80, 1000), 1070f);
        // a shelf of 5 tiles of width 100, gap 20, starting at x=100: after mirroring, tile 0 has the largest left
        float[] lefts = new float[5];
        for (int i = 0; i < 5; i++) lefts[i] = Mirror.left(100 + i * 120, 200 + i * 120, 0, 1920);
        boolean desc = true;
        for (int i = 1; i < 5; i++) desc &= lefts[i] < lefts[i - 1];
        ok("tile 0 is rightmost", desc);
        // text drawn left-to-right at x (width 100) inside [0, 1000], mirrored: left aligned, centered, right aligned
        eq("text left aligned", Mirror.textX(0, 50, 100, 0, 1000), 850f);
        eq("text centered", Mirror.textX(1, 300, 100, 0, 1000), 700f);
        eq("text right aligned", Mirror.textX(2, 400, 100, 0, 1000), 700f);
        eq("text left aligned in a card", Mirror.textX(0, 134, 80, 100, 600), 486f);   // card 100..600, text 34 in from the left
        eq("arrow keys swap", Mirror.key(-1) + "," + Mirror.key(1) + "," + Mirror.key(0), "1,-1,0");

        // bidi marks
        eq("latin only unchanged", Mirror.para("Netflix"), "Netflix");
        eq("number only unchanged", Mirror.para("32°"), "32°");
        eq("empty", Mirror.para(""), "");
        eq("arabic first unchanged", Mirror.para("افتح Netflix"), "افتح Netflix");
        eq("latin first with arabic gets RLM", Mirror.para("Netflix فتح"), "\u200FNetflix فتح");
        Bidi b = new Bidi(Mirror.para("Netflix فتح"), Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        ok("paragraph is RTL after para()", !b.baseIsLeftToRight());
        Bidi plain = new Bidi("Netflix فتح", Bidi.DIRECTION_DEFAULT_LEFT_TO_RIGHT);
        ok("paragraph is LTR without para()", plain.baseIsLeftToRight());
        eq("ltr wraps", Mirror.ltr("-3°"), "\u200E-3°\u200E");
        // numbers keep their order inside an Arabic sentence once wrapped
        String s = "العظمى " + Mirror.ltr("-3°");
        Bidi sb = new Bidi(s, Bidi.DIRECTION_RIGHT_TO_LEFT);
        int idx = s.indexOf('-');
        ok("signed number has even (LTR) level", sb.getLevelAt(idx) % 2 == 0);

        System.out.println(failed == 0 ? "PASS MirrorTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
