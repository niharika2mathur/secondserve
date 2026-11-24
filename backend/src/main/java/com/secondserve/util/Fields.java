package com.secondserve.util;

import com.secondserve.exception.ValidationException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Reads and validates typed fields from a JSON-like map, collecting every problem before failing. */
public final class Fields {
    private final Map<String, Object> map;
    private final List<String> errors = new ArrayList<>();

    public Fields(Map<String, Object> map) {
        if (map == null) throw new ValidationException("Request body is missing");
        this.map = map;
    }

    public String str(String key, String label, boolean required, int maxLen) {
        Object o = map.get(key);
        String s = o == null ? "" : String.valueOf(o).trim();
        if (s.isEmpty()) { if (required) errors.add(label + " is required"); return required ? null : ""; }
        if (s.length() > maxLen) { errors.add(label + " must be at most " + maxLen + " characters"); return null; }
        return s;
    }

    public Integer integer(String key, String label, int min, int max) {
        Object o = map.get(key);
        if (o == null || String.valueOf(o).isBlank()) { errors.add(label + " is required"); return null; }
        try {
            double d = o instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(o).trim());
            if (d != Math.rint(d)) { errors.add(label + " must be a whole number"); return null; }
            if (d < min || d > max) { errors.add(label + " must be between " + min + " and " + max); return null; }
            return (int) d;
        } catch (NumberFormatException e) {
            errors.add(label + " must be a number");
            return null;
        }
    }

    public LocalDateTime time(String key, String label) {
        Object o = map.get(key);
        if (o == null || String.valueOf(o).isBlank()) { errors.add(label + " is required"); return null; }
        try { return LocalDateTime.parse(String.valueOf(o).trim()); }
        catch (DateTimeParseException e) { errors.add(label + " must look like 2026-11-17T14:30"); return null; }
    }

    public static LocalDateTime parseTime(String raw, String label) {
        if (raw == null || raw.isBlank()) throw new ValidationException(label + " is required");
        try { return LocalDateTime.parse(raw.trim()); }
        catch (DateTimeParseException e) { throw new ValidationException(label + " must look like 2026-11-17T14:30"); }
    }

    public void error(String message) { errors.add(message); }
    public boolean ok() { return errors.isEmpty(); }
    public void throwIfInvalid() { if (!errors.isEmpty()) throw new ValidationException(errors); }
}
