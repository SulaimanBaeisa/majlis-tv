package io.freemytv;

/** Geometry checks are not a substitute for screenshots of the actual Canvas layers. */
public class HomeLayoutTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for (int step = 0; step <= 1000; step++) {
            float drawer = step / 1000f;
            check(HomeLayout.tickerTop(drawer) > HomeLayout.shelfBottom(drawer), "ticker overlaps shelf at " + drawer);
            check(HomeLayout.tickerBottom(drawer) < HomeLayout.drawerTop(drawer), "ticker overlaps drawer at " + drawer);
            check(HomeLayout.tickerTop(drawer) >= HomeLayout.platterLayerTop(drawer), "ticker clipped at layer top");
            check(HomeLayout.tickerBottom(drawer) <= HomeLayout.platterLayerBottom(drawer), "ticker clipped at layer bottom");
            check(HomeLayout.tickerBottom(drawer) <= 1080 - HomeLayout.SAFE_EDGE, "ticker outside safe screen edge");
        }
        for (float scale : new float[] {0.5f, 1f, 2f}) {
            float screen = 1920 * scale, left = 1000 * scale, right = 1824 * scale;
            float shift = HomeLayout.rightColumnShift(screen, left, right, true);
            check(Math.abs((screen - right + shift) - left) < 0.01, "Arabic About must move physically right");
            check(Math.abs((screen - left + shift) - right) < 0.01, "About right edge outside target column");
            check(HomeLayout.rightColumnShift(screen, left, right, false) == 0, "LTR About already on right");
        }
        check(HomeLayout.tipCenter(180, 80, 48, 1872) == 180, "normal gear label not centred");
        check(HomeLayout.tipCenter(60, 130, 48, 1872) == 178, "left edge label clipped");
        check(HomeLayout.tipCenter(1840, 130, 48, 1872) == 1742, "right edge label clipped");
        float baseline = HomeLayout.centredBaseline(10, 60, -30, 8);
        check(Math.abs(((baseline - 30) + (baseline + 8)) / 2 - 35) < 0.001, "ink not vertically centred");
        check(Math.abs(HomeLayout.contrast(0xFFFFFFFF, 0x000000) - 21) < 0.001, "contrast formula wrong");
        for (int bg : HomeLayout.SETTINGS_SKY) {
            check(HomeLayout.contrast(0xB3FFFFFF, bg) >= 4.5, "notice contrast too low");
            check(HomeLayout.contrast(HomeLayout.ACCENT, bg) >= 4.5, "heading contrast too low");
        }
        System.out.println("PASS HomeLayoutTest (geometry sweep, label bounds, essential-text contrast)");
    }
}
