package io.freemytv;

/** Plain-Java test. Run: tests/run.sh */
public class LangTest {
    static int failed = 0;

    static void eq(String name, String got, String want) {
        if (!want.equals(got)) { failed++; System.out.println("FAIL " + name + ": got " + got + ", want " + want); }
    }

    public static void main(String[] a) {
        // explicit setting wins over the TV's language
        eq("explicit ar", Lang.pick("ar", "en"), "ar");
        eq("explicit pt", Lang.pick("pt", "ar"), "pt");
        eq("explicit es", Lang.pick("es", "ar"), "es");
        eq("explicit en", Lang.pick("en", "ar"), "en");
        // auto follows the TV
        eq("auto ar", Lang.pick("auto", "ar"), "ar");
        eq("auto pt", Lang.pick("auto", "pt"), "pt");
        eq("auto es", Lang.pick("auto", "es"), "es");
        eq("auto fr falls to en", Lang.pick("auto", "fr"), "en");
        // junk or empty settings behave like auto
        eq("junk", Lang.pick("xx", "ar"), "ar");
        eq("null setting", Lang.pick(null, "ar"), "ar");
        eq("null tv", Lang.pick("auto", null), "en");
        eq("upper case", Lang.pick("AR", "en"), "ar");
        // TMDB language tags
        eq("tmdb ar", Lang.tmdbTag("ar", "SA"), "ar-SA");
        eq("tmdb en", Lang.tmdbTag("en", "US"), "en-US");
        eq("tmdb pt", Lang.tmdbTag("pt", "BR"), "pt-BR");
        eq("tmdb es spain", Lang.tmdbTag("es", "ES"), "es-ES");
        eq("tmdb es other", Lang.tmdbTag("es", "AR"), "es-MX");
        System.out.println(failed == 0 ? "PASS LangTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
