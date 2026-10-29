package io.freemytv;

public class AboutTest {
    static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    static String text(boolean ar) {
        StringBuilder result = new StringBuilder();
        for (String[] section : About.sections(ar)) {
            check(section.length == 2 && !section[1].isEmpty(), "empty About section");
            result.append(section[0]).append('\n').append(section[1]).append('\n');
        }
        return result.toString();
    }
    public static void main(String[] args) {
        for (boolean ar : new boolean[] {true, false}) {
            String content = text(ar);
            check(content.contains(News.credits(ar)), "news credit not derived from real source list");
            for (String[] source : News.SOURCES) {
                check(content.contains(source[0]), "missing source domain " + source[0]);
                check(content.contains(source[ar ? 1 : 2]), "missing publisher name");
                check(News.siteQuery(false).contains("site:" + source[0]), "credited source not queried");
                check(News.sourceName(source[0]) != null, "credited source not accepted by parser");
            }
            check(content.contains(ar ? Arabic.text(About.TMDB_NOTICE) : About.TMDB_NOTICE), "required TMDB notice missing");
            check(content.contains(String.valueOf(Competitions.ALL.size())), "competition count inconsistent");
            check(content.contains("Open-Meteo") && content.contains("Binance"), "data attribution missing");
            check(content.contains("SIL") && content.contains("Apache"), "licence attribution missing");
        }
        check(News.sourceName("alarabiya.net.evil.invalid") == null, "publisher suffix spoof accepted");
        System.out.println("PASS AboutTest (real news credits, licences and bilingual data attribution)");
    }
}
