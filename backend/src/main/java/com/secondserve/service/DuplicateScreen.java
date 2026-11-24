package com.secondserve.service;

import com.secondserve.algorithm.BloomFilter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

/**
 * Two-stage duplicate screening: a Bloom filter first, then an authoritative exact lookup ONLY when the
 * filter says "possibly present". A Bloom hit alone never rejects a record.
 */
public final class DuplicateScreen {
    public enum Outcome { NEW, DUPLICATE, FALSE_POSITIVE_ACCEPTED }

    public record Result(String id, boolean bloomPossiblyPresent, boolean exactChecked, boolean exactPresent, Outcome outcome) {}

    public record Stats(int screened, int definitelyNew, int possiblyPresent, int exactLookups, int exactLookupsAvoided,
                        int confirmedDuplicates, int falsePositives, double observedFalsePositiveRate) {}

    private BloomFilter filter;
    private final Predicate<String> exactStore;
    private final int expected;
    private final double fpp;
    private int screened, definitelyNew, possiblyPresent, exactLookups, confirmedDuplicates, falsePositives;
    private final Deque<Result> recent = new ArrayDeque<>();

    public DuplicateScreen(int expectedInsertions, double fpp, Predicate<String> exactStore) {
        this.expected = expectedInsertions;
        this.fpp = fpp;
        this.filter = new BloomFilter(expectedInsertions, fpp);
        this.exactStore = exactStore;
    }

    /** Screens an identifier WITHOUT inserting it. */
    public synchronized Result screen(String id) {
        screened++;
        boolean possible = filter.mightContain(id);
        Result r;
        if (!possible) {
            definitelyNew++;
            r = new Result(id, false, false, false, Outcome.NEW);
        } else {
            possiblyPresent++;
            exactLookups++;
            boolean exact = exactStore.test(id);
            if (exact) { confirmedDuplicates++; r = new Result(id, true, true, true, Outcome.DUPLICATE); }
            else { falsePositives++; r = new Result(id, true, true, false, Outcome.FALSE_POSITIVE_ACCEPTED); }
        }
        recent.addFirst(r);
        while (recent.size() > 25) recent.removeLast();
        return r;
    }

    /** Read-only probe used by the Algorithm Lab: no counters, no log, no insertion. */
    public synchronized Result peek(String id) {
        boolean possible = filter.mightContain(id);
        if (!possible) return new Result(id, false, false, false, Outcome.NEW);
        boolean exact = exactStore.test(id);
        return new Result(id, true, true, exact, exact ? Outcome.DUPLICATE : Outcome.FALSE_POSITIVE_ACCEPTED);
    }

    public synchronized void register(String id) { filter.put(id); }

    /** Bloom filters cannot delete, so after removals the filter is rebuilt from the authoritative ids. */
    public synchronized void rebuild(Iterable<String> authoritativeIds) {
        filter = new BloomFilter(expected, fpp);
        for (String id : authoritativeIds) filter.put(id);
    }

    public synchronized BloomFilter filter() { return filter; }

    public synchronized Stats stats() {
        int negatives = definitelyNew + falsePositives;
        return new Stats(screened, definitelyNew, possiblyPresent, exactLookups, definitelyNew, confirmedDuplicates,
                falsePositives, negatives == 0 ? 0.0 : (double) falsePositives / negatives);
    }

    public synchronized List<Result> recent() { return new ArrayList<>(recent); }
}
