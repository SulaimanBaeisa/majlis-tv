package io.freemytv;

import java.util.Locale;

/** Language choice rules, pure Java so tests/LangTest.java can run them without Android. */
final class Lang {
    private Lang() {}

    /** "pt", "es", "ar" or "en": the setting when it names one, else the TV's own language, else English. */
    static String pick(String setting, String tvLanguage) {
        String s = setting == null ? "auto" : setting.toLowerCase(Locale.ROOT);
        if (s.equals("pt") || s.equals("es") || s.equals("ar") || s.equals("en")) return s;
        String tv = tvLanguage == null ? "" : tvLanguage.toLowerCase(Locale.ROOT);
        return tv.equals("pt") || tv.equals("es") || tv.equals("ar") ? tv : "en";
    }

    /** TMDB's language tag (ar-SA, en-US, pt-BR, es-ES / es-MX) from our language and the TV's country. */
    static String tmdbTag(String lang, String tvCountry) {
        if (lang.equals("ar")) return "ar-SA";
        if (lang.equals("es")) return "ES".equalsIgnoreCase(tvCountry) ? "es-ES" : "es-MX";
        return lang.equals("en") ? "en-US" : "pt-BR";
    }
}
