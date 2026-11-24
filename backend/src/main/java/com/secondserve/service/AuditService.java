package com.secondserve.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Append-only (bounded) log of who did what; shown to the admin. */
public final class AuditService {
    public record Entry(long id, String time, String actor, String role, String action, String detail) {}
    private static final int MAX = 500;
    private final List<Entry> log = new ArrayList<>();
    private long seq;

    public synchronized void record(String actor, String role, String action, String detail) {
        log.add(new Entry(++seq, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), actor, role, action, detail));
        if (log.size() > MAX) log.remove(0);
    }

    /** Newest first. */
    public synchronized List<Entry> recent(int limit) {
        List<Entry> out = new ArrayList<>();
        for (int i = log.size() - 1; i >= 0 && out.size() < limit; i--) out.add(log.get(i));
        return out;
    }
}
