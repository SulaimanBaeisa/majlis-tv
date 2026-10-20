package io.freemytv;

import java.util.regex.Pattern;

/**
 * Whitelist checks for names that come back from the TV's own adbd and are later put into shell commands.
 * Pure Java on purpose (tests/SafeTest.java runs it without Android).
 */
final class Safe {
    private Safe() {}

    /** "package/Class" as printed by cmd package: letters, digits, '_', '.', '$' only. */
    private static final Pattern COMPONENT = Pattern.compile("[\\w.]+/[\\w.$]+");
    private static final Pattern PACKAGE = Pattern.compile("[\\w.]+");

    static boolean component(String s) {
        return s != null && COMPONENT.matcher(s).matches();
    }

    static boolean packageName(String s) {
        return s != null && PACKAGE.matcher(s).matches();
    }
}
