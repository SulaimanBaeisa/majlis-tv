package io.freemytv;

/**
 * The favourite-competition strip inside the wide matches card: up to {@link #MAX_VISIBLE} equal-width glass chips,
 * laid out right to left in catalogue order, with the window always containing the competition on screen. Pure
 * geometry (tests/CompetitionTabsTest.java); HomePanels draws the chips themselves.
 */
final class CompetitionTabs {
    private CompetitionTabs() {}

    /** Strip and match-row geometry in the card's design pixels: chips at TOP..TOP+HEIGHT, rows from MATCH_TOP. */
    static final float TOP = 28, HEIGHT = 46, GAP = 10;
    static final float MATCH_TOP = 100, MATCH_STEP = 125, MATCH_HEIGHT = 108;

    /** More chips than this would be unreadable at TV distance; the active one pages the rest into view. */
    static final int MAX_VISIBLE = 5;
    /** A chip never stretches across the card: with one or two followed it stays a small pill. */
    static final float MAX_WIDTH = 300;

    static int visible(int total) {
        return Math.max(0, Math.min(total, MAX_VISIBLE));
    }

    /** First chip of the window: keeps {@code selected} inside [start, start + visible). */
    static int start(int total, int selected) {
        int shown = visible(total);
        if (shown == 0) return 0;
        int s = selected - shown + 1;
        if (s < 0) s = 0;
        if (s > total - shown) s = total - shown;
        return s;
    }

    /** The chip {@code dir} steps from {@code selected}, wrapping at either end; -1 when nothing is followed. */
    static int next(int total, int selected, int dir) {
        if (total <= 0) return -1;
        return Math.floorMod(selected + dir, total);
    }

    /**
     * The chip caption. With three or more chips the leading "الدوري " / "دوري " goes, because that word alone eats
     * the room that tells two leagues apart ("دوري رو…", "الدوري الإ…" for both Spain and England). One or two chips
     * have the width for the full name, which is what the TV shows; a name without the prefix keeps its full text.
     */
    static String chipLabel(String ar, boolean dense) {
        if (ar == null) return "";
        if (!dense) return ar;
        for (String prefix : new String[]{"الدوري ", "دوري "}) {
            if (ar.startsWith(prefix) && ar.length() > prefix.length()) return ar.substring(prefix.length());
        }
        return ar;
    }

    static float width(float left, float right, int shown) {
        if (shown <= 0) return 0;
        return Math.min(MAX_WIDTH, (right - left - GAP * (shown - 1)) / shown);
    }

    /** Left edge of {@code slot}: 0 is the rightmost chip because Arabic reads right to left. */
    static float left(float left, float right, int shown, int slot) {
        float w = width(left, right, shown);
        return right - (slot + 1) * w - slot * GAP;
    }
}
