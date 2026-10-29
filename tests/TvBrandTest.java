package io.freemytv;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Plain-Java test: the TV maker from Build fields, and that it uses the installer's own profile rules. */
public class TvBrandTest {
    static int failed = 0;

    static void eq(String name, Object got, Object want) {
        if (!String.valueOf(want).equals(String.valueOf(got))) { failed++; System.out.println("FAIL " + name + ": got [" + got + "], want [" + want + "]"); }
    }

    public static void main(String[] a) throws Exception {
        // Real Build values seen on the project's test TVs (README / probe reports) and plausible others.
        eq("tcl google tv", TvBrand.profile("TCL", "TCL", "Smart TV Pro", "mt9653"), TvBrand.TCL);
        eq("tcl lowercase", TvBrand.profile("tcl", "TCL", "65C745", "kona"), TvBrand.TCL);
        eq("sony bravia", TvBrand.profile("Sony", "Sony", "BRAVIA 4K VH2", "BRAVIA_VH2"), TvBrand.SONY);
        eq("sony model only", TvBrand.profile("", "", "KD-55X80K", ""), TvBrand.SONY);
        eq("classpro", TvBrand.profile("Classpro", "Changhong", "AI PONT", "m7322"), TvBrand.CHANGHONG);
        eq("changhong mstar", TvBrand.profile("MStar", "MStar Semiconductor", "m7322", "m7322"), TvBrand.CHANGHONG);
        eq("hisense with shared chipset is generic", TvBrand.profile("Hisense", "Hisense", "MStar TV", "mstar"), TvBrand.GENERIC);
        eq("tcl with shared chipset stays tcl", TvBrand.profile("TCL", "MStar", "TCL Smart TV", "mstar"), TvBrand.TCL);
        eq("chromecast is generic", TvBrand.profile("google", "Google", "Chromecast", "sabrina"), TvBrand.GENERIC);
        eq("xiaomi is generic", TvBrand.profile("Xiaomi", "Xiaomi", "MiTV-AXSO0", "machuca"), TvBrand.GENERIC);
        eq("nulls are generic", TvBrand.profile(null, null, null, null), TvBrand.GENERIC);

        eq("name tcl", TvBrand.displayName(TvBrand.TCL, "TCL", "TCL"), "TCL");
        eq("name sony", TvBrand.displayName(TvBrand.SONY, "Sony", "Sony"), "Sony");
        eq("name classpro", TvBrand.displayName(TvBrand.CHANGHONG, "Classpro", "Changhong"), "Classpro");
        eq("name changhong", TvBrand.displayName(TvBrand.CHANGHONG, "MStar", "Changhong"), "Changhong");
        eq("name generic", TvBrand.displayName(TvBrand.GENERIC, "xiaomi", "xiaomi"), "Xiaomi");
        eq("name short generic", TvBrand.displayName(TvBrand.GENERIC, "hisense", "lge"), "LGE");
        eq("name unknown", TvBrand.displayName(TvBrand.GENERIC, "unknown", "unknown"), "");

        eq("remote keys tcl", TvBrand.remoteKeys(TvBrand.TCL), true);
        eq("no remote keys sony", TvBrand.remoteKeys(TvBrand.SONY), false);
        eq("no remote keys changhong", TvBrand.remoteKeys(TvBrand.CHANGHONG), false);
        eq("no remote keys generic", TvBrand.remoteKeys(TvBrand.GENERIC), false);

        // Same rules, same order as the installer (tooling/tv.py match_profile over sorted tooling/profiles/*.json).
        File[] files = new File("tooling/profiles").listFiles((d, n) -> n.endsWith(".json") && !n.startsWith("_"));
        Arrays.sort(files);
        Pattern id = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\""), device = Pattern.compile("\"device\"\\s*:\\s*\"([^\"]+)\"");
        int rule = 0;
        for (File f : files) {
            String json = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
            Matcher mi = id.matcher(json), md = device.matcher(json);
            if (!mi.find() || !md.find()) { failed++; System.out.println("FAIL unreadable profile " + f); continue; }
            if (mi.group(1).equals(TvBrand.GENERIC)) continue;
            if (rule >= TvBrand.RULES.length) { failed++; System.out.println("FAIL profile missing in TvBrand: " + mi.group(1)); continue; }
            eq("rule order " + rule, TvBrand.RULES[rule][0], mi.group(1));
            eq("rule pattern " + mi.group(1), TvBrand.RULES[rule][1], md.group(1));
            rule++;
        }
        eq("rule count", rule, TvBrand.RULES.length);

        System.out.println(failed == 0 ? "PASS TvBrandTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
