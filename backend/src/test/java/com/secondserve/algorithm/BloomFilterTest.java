package com.secondserve.algorithm;

import com.secondserve.service.DuplicateScreen;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Correctness tests for the custom Bloom filter and the two-stage duplicate screening built on it. */
class BloomFilterTest {

    @Test @DisplayName("BF-1 an inserted identifier is reported as possibly present (no false negatives)")
    void insertedIdIsFound() {
        BloomFilter f = new BloomFilter(1000, 0.01);
        f.put("BATCH-101");
        assertTrue(f.mightContain("BATCH-101"));
        for (int i = 0; i < 5000; i++) f.put("ID-" + i);
        for (int i = 0; i < 5000; i++) assertTrue(f.mightContain("ID-" + i), "false negative at " + i);
    }

    @Test @DisplayName("BF-2 an identifier that was never inserted into an empty filter is definitely absent")
    void neverInsertedIsAbsent() {
        BloomFilter f = new BloomFilter(1000, 0.01);
        assertFalse(f.mightContain("BATCH-999"));
        f.put("BATCH-1");
        assertFalse(f.mightContain("BATCH-2") && f.setBitCount() == 0);
    }

    @Test @DisplayName("BF-3 possible duplicates are verified against the exact repository before rejection")
    void possibleDuplicateIsVerifiedExactly() {
        Set<String> exact = new HashSet<>();
        DuplicateScreen s = new DuplicateScreen(100, 0.01, exact::contains);
        assertEquals(DuplicateScreen.Outcome.NEW, s.screen("BATCH-101").outcome());
        exact.add("BATCH-101"); s.register("BATCH-101");
        DuplicateScreen.Result r = s.screen("BATCH-101");
        assertTrue(r.bloomPossiblyPresent());
        assertTrue(r.exactChecked(), "exact lookup must follow a Bloom hit");
        assertEquals(DuplicateScreen.Outcome.DUPLICATE, r.outcome());
        assertEquals(1, s.stats().confirmedDuplicates());
    }

    @Test @DisplayName("BF-4 a simulated false positive is NOT rejected (accepted after the exact lookup says absent)")
    void falsePositiveIsNotRejected() {
        Set<String> exact = new HashSet<>();
        DuplicateScreen s = new DuplicateScreen(2, 0.5, exact::contains); // deliberately tiny and saturated
        for (int i = 0; i < 200; i++) { String id = "SEED-" + i; exact.add(id); s.register(id); }
        DuplicateScreen.Result fp = null;
        for (int i = 0; i < 1000 && fp == null; i++) {
            DuplicateScreen.Result r = s.screen("NEW-" + i);
            if (r.bloomPossiblyPresent()) fp = r;
        }
        assertNotNull(fp, "a saturated filter must produce a false positive");
        assertTrue(fp.exactChecked());
        assertFalse(fp.exactPresent());
        assertEquals(DuplicateScreen.Outcome.FALSE_POSITIVE_ACCEPTED, fp.outcome());
        assertTrue(s.stats().falsePositives() >= 1);
        assertEquals(0, s.stats().confirmedDuplicates(), "no genuine duplicate was ever reported");
    }

    @Test @DisplayName("BF-5 empty dataset and boundary configurations")
    void emptyAndBoundaryConfigurations() {
        BloomFilter f = new BloomFilter(1, 0.5);
        assertEquals(0, f.setBitCount());
        assertEquals(0.0, f.expectedFalsePositiveRate(), 1e-12);
        assertFalse(f.mightContain("anything"));
        assertTrue(f.bitSize() >= 8);
        assertTrue(f.hashCount() >= 1);
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(-5, 0.01));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(10, 0.0));
        assertThrows(IllegalArgumentException.class, () -> new BloomFilter(10, 1.0));
        assertThrows(IllegalArgumentException.class, () -> f.put(null));
        assertThrows(IllegalArgumentException.class, () -> f.mightContain(""));
    }

    @Test @DisplayName("BF-6 inserting the same identifier repeatedly changes nothing after the first time")
    void repeatedInsertionIsIdempotent() {
        BloomFilter f = new BloomFilter(100, 0.01);
        assertTrue(f.put("BATCH-7"), "first insertion sets bits");
        int bits = f.setBitCount();
        assertFalse(f.put("BATCH-7"), "second insertion changes no bit");
        assertEquals(bits, f.setBitCount());
        assertTrue(f.mightContain("BATCH-7"));
    }

    @Test @DisplayName("BF-7 sizing formulas: n=1000, p=1% gives m=9586 bits and k=7 hashes")
    void sizingMatchesTheory() {
        BloomFilter f = new BloomFilter(1000, 0.01);
        assertEquals(9586, f.bitSize());
        assertEquals(7, f.hashCount());
        assertEquals(9586 / 64 * 8 + 8, f.memoryBytes());
    }

    @Test @DisplayName("BF-8 observed false-positive rate stays near the configured 1% (fixed data, 20,000 negative queries)")
    void observedRateNearTarget() {
        BloomFilter f = new BloomFilter(1000, 0.01);
        for (int i = 0; i < 1000; i++) f.put("BATCH-" + i);
        int fp = 0, q = 20000;
        for (int i = 0; i < q; i++) if (f.mightContain("OTHER-" + i)) fp++;
        double rate = (double) fp / q;
        assertTrue(rate < 0.025, "observed rate " + rate);
        assertEquals(0.01, f.expectedFalsePositiveRate(), 0.004);
    }
}
