package io.freemytv;

/** Favourite competition strip: every selection stays in view, including wraparound and empty settings. */
public class CompetitionTabsTest {
    private static void check(boolean ok, String why) { if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        check(CompetitionTabs.visible(0) == 0, "none selected");
        check(CompetitionTabs.visible(1) == 1, "one selected");
        check(CompetitionTabs.next(0, 0, 1) == -1, "empty has no next");
        check(CompetitionTabs.next(1, 0, 1) == 0, "single does not change");
        check(CompetitionTabs.next(4, 0, -1) == 3, "previous wraps");
        check(CompetitionTabs.next(4, 3, 1) == 0, "next wraps");
        for (int total = 1; total <= 20; total++) for (int selected = 0; selected < total; selected++) {
            int start = CompetitionTabs.start(total, selected), shown = CompetitionTabs.visible(total);
            check(start >= 0 && start + shown <= total, "window in bounds");
            check(selected >= start && selected < start + shown, "active competition must stay visible");
            for (int slot = 0; slot < shown; slot++) {
                float left = CompetitionTabs.left(100, 1000, shown, slot);
                float right = left + CompetitionTabs.width(100, 1000, shown);
                check(left >= 100 && right <= 1000, "chip inside card");
                if (slot > 0) check(right < CompetitionTabs.left(100, 1000, shown, slot - 1), "RTL chips overlap");
            }
        }
        check(CompetitionTabs.width(100, 1000, 1) <= CompetitionTabs.MAX_WIDTH, "single chip must stay small");
        check(CompetitionTabs.width(100, 1000, 5) < CompetitionTabs.MAX_WIDTH, "five chips share the row");
        check(CompetitionTabs.TOP + CompetitionTabs.HEIGHT < CompetitionTabs.MATCH_TOP, "tabs overlap matches");
        check(CompetitionTabs.chipLabel("دوري روشن السعودي", true).equals("روشن السعودي"), "Saudi league keeps its name");
        check(CompetitionTabs.chipLabel("الدوري الإنجليزي الممتاز", true).equals("الإنجليزي الممتاز"), "England differs from Spain");
        check(CompetitionTabs.chipLabel("الدوري الإسباني", true).equals("الإسباني"), "Spain keeps its name");
        check(CompetitionTabs.chipLabel("كأس خادم الحرمين الشريفين", true).startsWith("كأس"), "cups keep the cup word");
        check(CompetitionTabs.chipLabel("الدوري", true).equals("الدوري"), "prefix alone is not stripped");
        check(CompetitionTabs.chipLabel("دوري روشن السعودي", false).equals("دوري روشن السعودي"), "one chip keeps the full name");
        check(CompetitionTabs.chipLabel(null, true).isEmpty(), "no name, no label");
        check(CompetitionTabs.MATCH_TOP + 2 * CompetitionTabs.MATCH_STEP + CompetitionTabs.MATCH_HEIGHT <= 480, "last match clipped");
        System.out.println("PASS CompetitionTabsTest (empty, single, wrap, all selections and strip geometry)");
    }
}
