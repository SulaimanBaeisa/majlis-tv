package io.freemytv;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The TV maker, from Android's Build fields, with the same rules (and order) the installer uses to pick a profile
 * (tooling/profiles/*.json: brand, manufacturer, model and device, lowercased, searched with each profile's "device"
 * pattern). tests/TvBrandTest.java checks that the two stay the same. Pure Java.
 */
final class TvBrand {
    private TvBrand() {}

    static final String TCL = "tcl", SONY = "sony", CHANGHONG = "changhong", GENERIC = "generic";

    /** Profile id and its pattern, in the installer's order (profiles sorted by file name; generic is the fallback). */
    static final String[][] RULES = {
        {CHANGHONG, "changhong|classpro|ai pont|m7322"},
        {SONY, "sony|bravia|kd-|xr-"},
        {TCL, "tcl"},
    };

    static String profile(String brand, String manufacturer, String model, String device) {
        String hay = (s(brand) + " " + s(manufacturer) + " " + s(model) + " " + s(device)).toLowerCase(Locale.ROOT);
        for (String[] rule : RULES) if (Pattern.compile(rule[1]).matcher(hay).find()) return rule[0];
        return GENERIC;
    }

    /** The name to show for the maker: the profile's brand, or what the TV reports when it has no profile. */
    static String displayName(String profile, String brand, String manufacturer) {
        switch (profile) {
            case TCL: return "TCL";
            case SONY: return "Sony";
            case CHANGHONG: {
                String hay = (s(brand) + " " + s(manufacturer)).toLowerCase(Locale.ROOT);
                return hay.contains("classpro") ? "Classpro" : "Changhong";
            }
            default: {
                String name = !s(manufacturer).trim().isEmpty() && !"unknown".equalsIgnoreCase(s(manufacturer).trim())
                        ? s(manufacturer).trim() : s(brand).trim();
                if (name.isEmpty() || "unknown".equalsIgnoreCase(name)) return "";
                return name.length() <= 3 ? name.toUpperCase(Locale.ROOT)
                        : name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1);
            }
        }
    }

    /**
     * The remote's app-button key codes Majlis handles were read from TCL's log (Prefs.KEYS); on other makers the same
     * codes can be other buttons, so they are only handled, and only offered in settings, on TCL.
     */
    static boolean remoteKeys(String profile) { return TCL.equals(profile); }

    private static String s(String v) { return v == null ? "" : v; }
}
