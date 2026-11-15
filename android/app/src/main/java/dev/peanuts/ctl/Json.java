package dev.peanuts.ctl;

import java.util.Map;

/**
 * The status file's writer: maps, lists, strings, numbers, booleans and null as JSON text, two spaces a level. Its own few lines instead of org.json so the
 * status and the results are built from plain maps, which the JVM tests can make and read without Android.
 */
public final class Json {
    private Json() {}

    public static String write(Object o) {
        StringBuilder b = new StringBuilder();
        value(b, o, 0);
        return b.append('\n').toString();
    }

    private static void value(StringBuilder b, Object o, int depth) {
        if (o == null) b.append("null");
        else if (o instanceof Map) {
            Map<?, ?> m = (Map<?, ?>) o;
            if (m.isEmpty()) { b.append("{}"); return; }
            b.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) b.append(',');
                first = false;
                line(b, depth + 1);
                string(b, String.valueOf(e.getKey()));
                b.append(": ");
                value(b, e.getValue(), depth + 1);
            }
            line(b, depth);
            b.append('}');
        } else if (o instanceof Iterable) {
            boolean first = true;
            b.append('[');
            for (Object x : (Iterable<?>) o) {
                if (!first) b.append(',');
                first = false;
                line(b, depth + 1);
                value(b, x, depth + 1);
            }
            if (!first) line(b, depth);
            b.append(']');
        } else if (o instanceof Boolean) b.append(o);
        else if (o instanceof Double || o instanceof Float) {
            double d = ((Number) o).doubleValue();
            if (Double.isNaN(d) || Double.isInfinite(d)) b.append("null");   // JSON has no such numbers
            else b.append(d);
        } else if (o instanceof Number) b.append(o);
        else string(b, o.toString());
    }

    private static void line(StringBuilder b, int depth) {
        b.append('\n');
        for (int i = 0; i < depth; i++) b.append("  ");
    }

    private static void string(StringBuilder b, String s) {
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20 || c == 0x2028 || c == 0x2029) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        b.append('"');
    }
}
