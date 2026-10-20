package io.freemytv;

/** The pure part of the right-to-left layout: mirror arithmetic and bidi marks (tests/MirrorTest.java). */
final class Mirror {
    private Mirror() {}

    static final char RLM = '\u200F', LRM = '\u200E';

    /** x mirrored inside [l, r]. */
    static float x(float x, float l, float r) { return l + r - x; }

    /** New left edge of a rect (left..right) mirrored inside [l, r]. */
    static float left(float left, float right, float l, float r) { return l + r - right; }

    /** New right edge of a rect (left..right) mirrored inside [l, r]. */
    static float right(float left, float right, float l, float r) { return l + r - left; }

    /**
     * Where to draw a string mirrored inside [l, r], given where it is drawn left-to-right. {@code align}: 0 = the
     * paint is left aligned (x is the text's left edge), 1 = centered (x is its center), 2 = right aligned (x is its
     * right edge). Returns the x to hand to drawText with the same paint.
     */
    static float textX(int align, float x, float width, float l, float r) {
        switch (align) {
            case 1: return l + r - x;
            case 2: return l + r - x + width;
            default: return l + r - x - width;
        }
    }

    /** An arrow key's direction as seen in right-to-left. */
    static int key(int dx) { return -dx; }

    /**
     * Adds a right-to-left mark in front of a string that holds Arabic but starts with a Latin letter (an app name,
     * say), so the whole line is laid out right-to-left. Strings with no Arabic, or that start with Arabic, or are
     * only numbers, are returned as they are.
     */
    static String para(String s) {
        if (s == null || s.isEmpty()) return s;
        boolean hasRtl = false;
        int first = -1;   // directionality of the first strong character: 0 = none, 1 = left-to-right, 2 = right-to-left
        for (int i = 0; i < s.length(); i++) {
            byte d = Character.getDirectionality(s.charAt(i));
            boolean rtl = d == Character.DIRECTIONALITY_RIGHT_TO_LEFT || d == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
            boolean ltr = d == Character.DIRECTIONALITY_LEFT_TO_RIGHT;
            if (rtl) hasRtl = true;
            if (first < 0 && (rtl || ltr)) first = rtl ? 2 : 1;
        }
        return hasRtl && first == 1 ? RLM + s : s;
    }

    /** Keeps a number such as "-3°" or "+1.2%" in its order when it sits inside Arabic text. */
    static String ltr(String s) {
        return s == null || s.isEmpty() ? s : LRM + s + LRM;
    }
}
