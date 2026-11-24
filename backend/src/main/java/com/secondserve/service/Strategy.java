package com.secondserve.service;

import com.secondserve.exception.ValidationException;

import java.util.Locale;

/** Which algorithm decides the official allocation. All three place the same number of meals; they differ in WHICH meals go where. */
public enum Strategy {
    MAX_FLOW("Maximum flow (Edmonds-Karp)",
            "Maximises the number of meals placed. Among equally large allocations it takes whichever the augmenting-path order produces."),
    MIN_COST("Smart: min-cost maximum flow",
            "Same number of meals as maximum flow, but chosen to minimise travel time, avoid tight delivery margins and use the most urgent batches first."),
    FAIR("Fair share: max-min fairness",
            "Same number of meals as maximum flow, but the smallest NGO share is as large as possible (progressive filling over repeated max-flow runs).");

    public final String label, description;
    Strategy(String label, String description) { this.label = label; this.description = description; }

    public static Strategy parse(String raw) {
        if (raw == null || raw.isBlank()) return MAX_FLOW;
        try { return valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) { throw new ValidationException("Strategy must be one of MAX_FLOW, MIN_COST, FAIR"); }
    }
}
