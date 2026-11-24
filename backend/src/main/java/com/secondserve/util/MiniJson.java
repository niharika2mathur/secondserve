package com.secondserve.util;

import java.lang.reflect.RecordComponent;
import java.util.*;

/** Tiny JSON reader/writer (records, maps, lists, arrays). Used by the benchmark CLI and the standalone server. */
public final class MiniJson {
    private MiniJson() {}

    // ------------------------------ writer ------------------------------
    public static String write(Object o) { StringBuilder sb = new StringBuilder(); w(o, sb); return sb.toString(); }

    private static void w(Object o, StringBuilder sb) {
        try {
            if (o == null) sb.append("null");
            else if (o instanceof Boolean || o instanceof Integer || o instanceof Long || o instanceof Short) sb.append(o);
            else if (o instanceof Double || o instanceof Float) { double d = ((Number) o).doubleValue(); sb.append(Double.isNaN(d) || Double.isInfinite(d) ? "null" : Double.toString(d)); }
            else if (o instanceof Number n) sb.append(n);
            else if (o instanceof CharSequence || o instanceof Enum<?> || o instanceof java.time.temporal.Temporal) str(o.toString(), sb);
            else if (o instanceof Map<?, ?> m) {
                sb.append('{'); boolean first = true;
                for (Map.Entry<?, ?> e : m.entrySet()) { if (!first) sb.append(','); first = false; str(String.valueOf(e.getKey()), sb); sb.append(':'); w(e.getValue(), sb); }
                sb.append('}');
            } else if (o instanceof Iterable<?> it) {
                sb.append('['); boolean first = true;
                for (Object x : it) { if (!first) sb.append(','); first = false; w(x, sb); }
                sb.append(']');
            } else if (o.getClass().isArray()) {
                sb.append('['); int n = java.lang.reflect.Array.getLength(o);
                for (int i = 0; i < n; i++) { if (i > 0) sb.append(','); w(java.lang.reflect.Array.get(o, i), sb); }
                sb.append(']');
            } else if (o.getClass().isRecord()) {
                sb.append('{'); boolean first = true;
                for (RecordComponent rc : o.getClass().getRecordComponents()) {
                    if (!first) sb.append(','); first = false;
                    str(rc.getName(), sb); sb.append(':');
                    var acc = rc.getAccessor(); acc.setAccessible(true);
                    w(acc.invoke(o), sb);
                }
                sb.append('}');
            } else str(o.toString(), sb);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static void str(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> { if (c < 0x20) sb.append(String.format("\\u%04x", (int) c)); else sb.append(c); }
            }
        }
        sb.append('"');
    }

    // ------------------------------ parser ------------------------------
    public static Object parse(String text) {
        P p = new P(text);
        p.ws();
        Object v = p.value();
        p.ws();
        if (p.i != text.length()) throw p.err("Unexpected trailing characters");
        return v;
    }

    private static final class P {
        final String s; int i;
        P(String s) { this.s = s; }
        RuntimeException err(String m) { return new IllegalArgumentException("Invalid JSON: " + m + " at position " + i); }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        Object value() {
            if (i >= s.length()) throw err("unexpected end");
            char c = s.charAt(i);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return string();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return number();
        }
        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws(); String k = string(); ws();
                if (s.charAt(i++) != ':') throw err("expected ':'");
                ws(); m.put(k, value()); ws();
                char c = s.charAt(i++);
                if (c == '}') return m;
                if (c != ',') throw err("expected ',' or '}'");
            }
        }
        List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                ws(); l.add(value()); ws();
                char c = s.charAt(i++);
                if (c == ']') return l;
                if (c != ',') throw err("expected ',' or ']'");
            }
        }
        String string() {
            if (s.charAt(i) != '"') throw err("expected string");
            StringBuilder sb = new StringBuilder(); i++;
            while (true) {
                if (i >= s.length()) throw err("unterminated string");
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> sb.append('\n'); case 't' -> sb.append('\t'); case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b'); case 'f' -> sb.append('\f');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);
                    }
                } else sb.append(c);
            }
        }
        Object number() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (st == i) throw err("unexpected character '" + s.charAt(i) + "'");
            String t = s.substring(st, i);
            try {
                if (t.contains(".") || t.contains("e") || t.contains("E")) return Double.parseDouble(t);
                return Long.parseLong(t);
            } catch (NumberFormatException e) { throw err("bad number " + t); }
        }
    }
}
