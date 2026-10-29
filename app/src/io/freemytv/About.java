package io.freemytv;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings > About: where every piece of data comes from and the licences of what ships in the app. Built from the
 * same constants the fetchers use (News.SOURCES, Competitions), never typed in twice. Pure Java (tests/AboutTest.java).
 */
final class About {
    private About() {}

    /** The TMDB notice TMDB's terms ask for (the English text is also the Arabic.java key). */
    static final String TMDB_NOTICE = "This product uses the TMDB API but is not endorsed or certified by TMDB.";

    /** {heading, paragraph} pairs, in order. */
    static List<String[]> sections(boolean ar) {
        List<String[]> out = new ArrayList<>();
        String sources = ar ? "مصادر البيانات" : "Data sources";
        out.add(new String[] {sources, News.credits(ar)});
        out.add(new String[] {"", ar
                ? "كرة القدم: ESPN ‏(site.api.espn.com)، بيانات عامة بلا مفتاح، لـ " + Competitions.ALL.size() + " بطولة يمكن اختيارها من الإعدادات."
                : "Football: ESPN (site.api.espn.com), public data with no key, for " + Competitions.ALL.size() + " competitions you can pick in Settings."});
        out.add(new String[] {"", ar
                ? "الطقس: Open-Meteo.com ‏(CC BY 4.0)."
                : "Weather: Open-Meteo.com (CC BY 4.0)."});
        out.add(new String[] {"", ar
                ? "أسعار العملات الرقمية: واجهة Binance العامة ‏(api.binance.com)، للعرض فقط وليست نصيحة مالية."
                : "Crypto prices: Binance public API (api.binance.com), for display only, not financial advice."});
        out.add(new String[] {"", ar
                ? "مواقيت الصلاة: تُحسب على التلفاز دون اتصال بطريقة أم القرى، للمدينة المضبوطة عند التثبيت."
                : "Prayer times: computed on the TV with no network, Umm al-Qura method, for the city set at install."});
        out.add(new String[] {"", ar
                ? "صور شاشة التوقف: ويكيميديا كومنز، ويظهر اسم المصوّر والرخصة مع كل صورة (PHOTO-CREDITS.md)."
                : "Screensaver photos: Wikimedia Commons; each photo shows its photographer and licence (PHOTO-CREDITS.md)."});
        out.add(new String[] {"", ar ? Arabic.text(TMDB_NOTICE) : TMDB_NOTICE});
        out.add(new String[] {ar ? "الرخص" : "Licences", ar
                ? "خط Inter: رخصة SIL Open Font License 1.1. رموز Material Symbols من Google: رخصة Apache 2.0. شعارات التطبيقات ملك لأصحابها."
                : "Inter typeface: SIL Open Font License 1.1. Google Material Symbols: Apache License 2.0. App logos belong to their owners."});
        return out;
    }
}
