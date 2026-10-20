package io.freemytv;

import android.graphics.Paint;
import android.graphics.RectF;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;

/**
 * Right-to-left layout for Arabic. The layout code keeps every rect in screen coordinates, so the layout methods
 * mirror those rects once ({@link #flip}); images, shadows and glass then land in the right place without being
 * flipped. Text anchors are mirrored where they are drawn. The direction is frozen when the process starts
 * ({@link Ui#rtl}), like the font. {@link #ENABLED} = false is the fallback: Arabic text, left-to-right layout.
 */
final class Rtl {
    private Rtl() {}

    static final boolean ENABLED = true;

    static boolean on() { return ENABLED && Ui.rtl; }

    /** x mirrored inside [l, r] when right-to-left. */
    static float x(float x, float l, float r) { return on() ? Mirror.x(x, l, r) : x; }

    /** Mirrors a rect inside [l, r] when right-to-left. */
    static void flip(RectF a, float l, float r) {
        if (!on()) return;
        float left = Mirror.left(a.left, a.right, l, r), right = Mirror.right(a.left, a.right, l, r);
        a.left = left;
        a.right = right;
    }

    /**
     * x for drawText(s, x, y, p) when the text is laid out left-to-right inside [l, r]: unchanged normally,
     * mirrored inside [l, r] in right-to-left (the paint's alignment decides which edge x refers to).
     */
    static float tx(Paint p, String s, float x, float l, float r) {
        if (!on()) return x;
        Paint.Align a = p.getTextAlign();
        int align = a == Paint.Align.CENTER ? 1 : a == Paint.Align.RIGHT ? 2 : 0;
        return Mirror.textX(align, x, p.measureText(s), l, r);
    }

    /** The edge a line of text starts from (the right edge when right-to-left). */
    static float start(RectF a) { return on() ? a.right : a.left; }

    /** +1 left-to-right, -1 right-to-left: for cursors that run along a line. */
    static float sign() { return on() ? -1 : 1; }

    static int key(int dx) { return on() ? Mirror.key(dx) : dx; }

    static Paint.Align align(Paint.Align a) {
        if (!on() || a == Paint.Align.CENTER) return a;
        return a == Paint.Align.LEFT ? Paint.Align.RIGHT : Paint.Align.LEFT;
    }

    /** Text direction for Arabic paragraphs even when the layout is not mirrored (the fallback). */
    static StaticLayout.Builder dir(StaticLayout.Builder b) {
        return Ui.ar ? b.setTextDirection(TextDirectionHeuristics.RTL) : b;
    }

    static String para(String s) { return Ui.ar ? Mirror.para(s) : s; }

    static String ltr(String s) { return Ui.ar ? Mirror.ltr(s) : s; }
}
