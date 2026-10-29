package io.freemytv;

/**
 * Home screen geometry that has to stay consistent between layers, in the 1920×1080 design space, plus colour
 * contrast. Pure Java (tests/HomeLayoutTest.java): the ticker must stay readable with the drawer open, the corner
 * button names must sit centred under their buttons, settings text must stay readable on the dark background.
 */
final class HomeLayout {
    private HomeLayout() {}

    /** Bottom drawer: everything above it lifts by SHIFT; the drawer layer slides up from SHIFT below. */
    static final float SHIFT = 560;
    /** The shelf (platter) layer: placed at PLATTER_LAYER_TOP, PLATTER_LAYER_H tall; it carries the shelf glass and the ticker. */
    static final float PLATTER_LAYER_TOP = 680, PLATTER_LAYER_H = 360;
    /** Shelf glass: top, tile height and padding (glass bottom = top + tile + 2 × pad), plus its drop shadow. */
    static final float SHELF_TOP = 716, SHELF_TILE_H = 126, SHELF_PAD = 17, SHELF_SHADOW = 18 + 26;
    /** News ticker (Arabic), in the platter layer. */
    static final float TICKER_LEFT = 96, TICKER_RIGHT = 1824, TICKER_TOP = 912, TICKER_BOTTOM = 988;
    /** Drawer: the "Inputs / Shortcuts" title baseline (its glyphs rise ~ROW_TITLE_ASCENT above it) and the tiles. */
    static final float DRAWER_LAYER_TOP = 470, ROW_TITLE = 518, ROW_TITLE_ASCENT = 26, ROW_TOP = 540;
    /** Overscan-safe edge used for text that may hang past the 96 px content margin (tvOS: 48 px action-safe). */
    static final float SAFE_EDGE = 48;

    static float shelfBottom(float drawer) { return SHELF_TOP + SHELF_TILE_H + 2 * SHELF_PAD - drawer * SHIFT; }

    static float tickerTop(float drawer) { return TICKER_TOP - drawer * SHIFT; }

    static float tickerBottom(float drawer) { return TICKER_BOTTOM - drawer * SHIFT; }

    /** Where the drawer's first text (row titles) starts, on screen, for a drawer value (0 closed, 1 open). */
    static float drawerTop(float drawer) { return ROW_TITLE - ROW_TITLE_ASCENT + (1 - drawer) * SHIFT; }

    /** The platter layer's on-screen span for a drawer value (it lifts with the shelf). */
    static float platterLayerTop(float drawer) { return PLATTER_LAYER_TOP - drawer * SHIFT; }

    static float platterLayerBottom(float drawer) { return PLATTER_LAYER_TOP + PLATTER_LAYER_H - drawer * SHIFT; }

    /**
     * Centre x for a button's name drawn centred under it: the button's centre, moved in only as far as needed to keep
     * the text inside [minX, maxX].
     */
    static float tipCenter(float buttonCenter, float halfText, float minX, float maxX) {
        float lo = minX + halfText, hi = maxX - halfText;
        if (lo > hi) return (minX + maxX) / 2;
        return Math.max(lo, Math.min(hi, buttonCenter));
    }

    /**
     * Baseline that centres a line of text vertically between {@code top} and {@code bottom}, from the text's real ink
     * bounds relative to its baseline (inkTop is negative: above the baseline).
     */
    static float centredBaseline(float top, float bottom, float inkTop, float inkBottom) {
        return (top + bottom) / 2 - (inkTop + inkBottom) / 2;
    }

    /** Translate an RTL-mirrored column back onto the physical right without reversing its text. */
    static float rightColumnShift(float screenWidth, float left, float right, boolean rtl) {
        return rtl ? left + right - screenWidth : 0;
    }


    // MARK: - Settings colours (dark Majlis identity: night ink with the Saudi green already used for accents)

    /** Background gradient stops (top, middle, bottom) for settings; Shaders.majlis uses them. */
    static final int[] SETTINGS_SKY = {0x05080A, 0x0A1F17, 0x123726};
    /** Accent: the same green as the live score and the next-prayer highlight on the home screen. */
    static final int ACCENT = 0xFF5BE37F;

    /** WCAG relative luminance of an opaque colour. */
    static double luminance(int rgb) {
        double[] c = {((rgb >> 16) & 255) / 255.0, ((rgb >> 8) & 255) / 255.0, (rgb & 255) / 255.0};
        for (int i = 0; i < 3; i++) c[i] = c[i] <= 0.03928 ? c[i] / 12.92 : Math.pow((c[i] + 0.055) / 1.055, 2.4);
        return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
    }

    /** WCAG contrast ratio of text colour {@code fg} (alpha blended over {@code bg}) on opaque {@code bg}. */
    static double contrast(int fg, int bg) {
        float a = (fg >>> 24) / 255f;
        int r = Math.round(((fg >> 16) & 255) * a + ((bg >> 16) & 255) * (1 - a));
        int g = Math.round(((fg >> 8) & 255) * a + ((bg >> 8) & 255) * (1 - a));
        int b = Math.round((fg & 255) * a + (bg & 255) * (1 - a));
        double l1 = luminance((r << 16) | (g << 8) | b), l2 = luminance(bg);
        return (Math.max(l1, l2) + 0.05) / (Math.min(l1, l2) + 0.05);
    }
}
