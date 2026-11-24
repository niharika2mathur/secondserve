package com.secondserve.service;

import com.secondserve.algorithm.BloomFilter;
import com.secondserve.dto.Dtos.*;
import com.secondserve.exception.ValidationException;
import com.secondserve.repository.DonationRepository;
import com.secondserve.util.Fields;

import java.util.*;

/** Read-side views of the live Bloom filter plus the isolated "sandbox" experiment used by the Algorithm Lab. */
public final class BloomService {
    private final DuplicateScreen screening;
    private final DonationRepository donations;

    public BloomService(DuplicateScreen screening, DonationRepository donations) { this.screening = screening; this.donations = donations; }

    public BloomStats stats() {
        BloomFilter f = screening.filter();
        DuplicateScreen.Stats s = screening.stats();
        List<BloomLogEntry> log = new ArrayList<>();
        for (DuplicateScreen.Result r : screening.recent()) log.add(new BloomLogEntry(r.id(), r.bloomPossiblyPresent(), r.exactChecked(), r.exactPresent(), r.outcome().name()));
        List<BloomItem> items = new ArrayList<>();
        for (String id : donations.ids()) items.add(new BloomItem(id, f.positions(id)));
        return new BloomStats(f.bitSize(), f.hashCount(), f.expectedInsertions(), f.targetFalsePositiveRate(), f.insertions(), f.setBitCount(),
                (double) f.setBitCount() / f.bitSize(), f.memoryBytes(), f.expectedFalsePositiveRate(), f.fillBasedFalsePositiveRate(),
                s.screened(), s.definitelyNew(), s.possiblyPresent(), s.exactLookups(), s.exactLookupsAvoided(), s.confirmedDuplicates(),
                s.falsePositives(), s.observedFalsePositiveRate(), log, f.setBitIndexes(), items);
    }

    /** Membership query against the live filter; nothing is inserted or counted. */
    public BloomCheck check(Map<String, Object> body) {
        Fields f = new Fields(body);
        String id = DonationService.normalizeId(f.str("id", "Batch ID", true, 40));
        f.throwIfInvalid();
        DuplicateScreen.Result r = screening.peek(id);
        int[] pos = screening.filter().positions(id);
        String ex = switch (r.outcome()) {
            case NEW -> "At least one of the " + pos.length + " bit positions is 0, so the ID is DEFINITELY not present. No exact lookup is needed.";
            case DUPLICATE -> "All " + pos.length + " bits are 1 (possibly present). The exact repository lookup found the record: a genuine duplicate.";
            case FALSE_POSITIVE_ACCEPTED -> "All " + pos.length + " bits are 1 (possibly present) but the exact repository lookup found nothing: this is a Bloom filter FALSE POSITIVE. The ID would be accepted.";
        };
        return new BloomCheck(id, r.bloomPossiblyPresent(), pos, r.exactChecked(), r.exactPresent(), r.outcome().name(), ex);
    }

    /** Isolated experiment: build a fresh filter, insert n known IDs, query q unknown IDs, report observed false positives. */
    public BloomDemo demo(Map<String, Object> body) {
        Fields f = new Fields(body);
        Integer expected = f.integer("expectedInsertions", "Expected insertions", 1, 100_000);
        Integer inserts = f.integer("inserts", "Identifiers to insert", 0, 100_000);
        Integer queries = f.integer("queries", "Never-inserted queries", 1, 200_000);
        Object fo = body.get("falsePositiveRate");
        double p = 0;
        try { p = Double.parseDouble(String.valueOf(fo)); } catch (RuntimeException e) { f.error("False-positive rate must be a number"); }
        if (f.ok() && !(p > 0 && p < 1)) f.error("False-positive rate must be between 0 and 1 (exclusive)");
        f.throwIfInvalid();
        BloomFilter bf = new BloomFilter(expected, p);
        Set<String> exact = new HashSet<>();
        for (int i = 0; i < inserts; i++) { String id = "DEMO-" + i; bf.put(id); exact.add(id); }
        int possible = 0, fp = 0, dup = 0;
        List<String> samples = new ArrayList<>();
        for (int i = 0; i < queries; i++) {
            String id = "NEW-" + i;
            if (bf.mightContain(id)) {
                possible++;
                if (exact.contains(id)) dup++; else { fp++; if (samples.size() < 8) samples.add(id); }
            }
        }
        return new BloomDemo(expected, p, inserts, queries, bf.bitSize(), bf.hashCount(), bf.setBitCount(), possible, fp, dup,
                (double) fp / queries, bf.expectedFalsePositiveRate(), samples, bf.bitSize() <= 20_000 ? bf.setBitIndexes() : new int[0], bf.memoryBytes());
    }
}
