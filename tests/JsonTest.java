package io.freemytv;

import java.util.List;
import java.util.Map;

/** Plain-Java test for the small JSON reader used by the league feed. */
public class JsonTest {
    static int failed = 0;

    static void eq(String name, Object got, Object want) {
        if (!String.valueOf(want).equals(String.valueOf(got))) { failed++; System.out.println("FAIL " + name + ": got [" + got + "], want [" + want + "]"); }
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] a) {
        Object v = Json.parse("{\"a\":1,\"b\":[true,false,null,\"x\\n\\u0627\\\"y\"],\"c\":{\"d\":-2.5e1},\"e\":\"\"}");
        Map<String, Object> m = (Map<String, Object>) v;
        eq("number", m.get("a"), 1.0);
        List<Object> b = (List<Object>) m.get("b");
        eq("true", b.get(0), true);
        eq("false", b.get(1), false);
        eq("null", b.get(2), null);
        eq("escapes", b.get(3), "x\n\u0627\"y");
        eq("nested number", Json.get(v, "c", "d"), -25.0);
        eq("path through list", Json.get(v, "b", 3), "x\n\u0627\"y");
        eq("missing path", Json.get(v, "c", "zzz", "q"), null);
        eq("empty string", m.get("e"), "");
        eq("string helper", Json.str(v, "e"), "");
        eq("num helper", Json.num(v, "a"), 1.0);
        eq("num default", Json.num(v, "zzz"), Double.NaN);
        eq("whitespace", ((List<?>) Json.parse(" [ 1 , 2 ] ")).size(), 2);
        boolean threw = false;
        try { Json.parse("{\"a\":"); } catch (IllegalArgumentException e) { threw = true; }
        eq("bad json throws", threw, true);
        threw = false;
        try { Json.parse("[1,2] x"); } catch (IllegalArgumentException e) { threw = true; }
        eq("trailing junk throws", threw, true);
        System.out.println(failed == 0 ? "PASS JsonTest" : ("FAILED " + failed));
        if (failed != 0) System.exit(1);
    }
}
