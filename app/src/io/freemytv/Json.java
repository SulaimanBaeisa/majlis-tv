package io.freemytv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small JSON reader (objects → Map, arrays → List, numbers → Double) so the feed parsing is plain Java and can be
 * tested on a PC. Throws IllegalArgumentException on malformed input.
 */
final class Json {
    private final String s;
    private int i;

    private Json(String s) { this.s = s; }

    static Object parse(String text) {
        Json j = new Json(text);
        Object v = j.value();
        j.ws();
        if (j.i != j.s.length()) throw new IllegalArgumentException("trailing characters at " + j.i);
        return v;
    }

    /** Follows keys (String) and indexes (Integer) through nested Map/List; null if any step is missing. */
    static Object get(Object root, Object... path) {
        Object cur = root;
        for (Object p : path) {
            if (cur instanceof Map && p instanceof String) cur = ((Map<?, ?>) cur).get(p);
            else if (cur instanceof List && p instanceof Integer) {
                List<?> l = (List<?>) cur;
                int k = (Integer) p;
                cur = k >= 0 && k < l.size() ? l.get(k) : null;
            } else return null;
            if (cur == null) return null;
        }
        return cur;
    }

    static String str(Object root, Object... path) {
        Object v = get(root, path);
        return v instanceof String ? (String) v : v == null ? null : String.valueOf(v);
    }

    /** A number at the path (JSON numbers, or numeric strings such as ESPN's "2"); NaN if absent. */
    static double num(Object root, Object... path) {
        Object v = get(root, path);
        if (v instanceof Double) return (Double) v;
        if (v instanceof String) {
            try { return Double.parseDouble((String) v); } catch (NumberFormatException e) { return Double.NaN; }
        }
        return Double.NaN;
    }

    private void ws() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private Object value() {
        ws();
        if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
        char c = s.charAt(i);
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
        if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
        if (s.startsWith("null", i)) { i += 4; return null; }
        return number();
    }

    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == '}') { i++; return m; }
        while (true) {
            ws();
            if (i >= s.length() || s.charAt(i) != '"') throw new IllegalArgumentException("expected key at " + i);
            String k = string();
            ws();
            if (i >= s.length() || s.charAt(i) != ':') throw new IllegalArgumentException("expected ':' at " + i);
            i++;
            m.put(k, value());
            ws();
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            char c = s.charAt(i++);
            if (c == '}') return m;
            if (c != ',') throw new IllegalArgumentException("expected ',' or '}' at " + (i - 1));
        }
    }

    private List<Object> array() {
        List<Object> l = new ArrayList<>();
        i++;
        ws();
        if (i < s.length() && s.charAt(i) == ']') { i++; return l; }
        while (true) {
            l.add(value());
            ws();
            if (i >= s.length()) throw new IllegalArgumentException("unexpected end");
            char c = s.charAt(i++);
            if (c == ']') return l;
            if (c != ',') throw new IllegalArgumentException("expected ',' or ']' at " + (i - 1));
        }
    }

    private String string() {
        StringBuilder b = new StringBuilder();
        i++;
        while (true) {
            if (i >= s.length()) throw new IllegalArgumentException("unterminated string");
            char c = s.charAt(i++);
            if (c == '"') return b.toString();
            if (c != '\\') { b.append(c); continue; }
            if (i >= s.length()) throw new IllegalArgumentException("bad escape");
            char e = s.charAt(i++);
            switch (e) {
                case 'n': b.append('\n'); break;
                case 't': b.append('\t'); break;
                case 'r': b.append('\r'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'u':
                    if (i + 4 > s.length()) throw new IllegalArgumentException("bad \\u escape");
                    b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                    i += 4;
                    break;
                default: b.append(e);
            }
        }
    }

    private Double number() {
        int st = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (st == i) throw new IllegalArgumentException("unexpected character at " + i);
        try {
            return Double.valueOf(s.substring(st, i));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("bad number at " + st);
        }
    }
}
