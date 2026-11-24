package com.secondserve.algorithm;

import java.nio.charset.StandardCharsets;

/**
 * Bloom filter implemented from scratch (Unit 6 - randomized / probabilistic data structure).
 *
 * Sizing for n expected insertions and target false-positive probability p:
 *   m = ceil(-n ln p / (ln 2)^2) bits,   k = round((m / n) ln 2) hash functions.
 * Hashing: Kirsch-Mitzenmacher double hashing, index_i = (h1 + i*h2) mod m, with h1/h2 derived from
 * a 64-bit FNV-1a digest of the UTF-8 key passed through a SplitMix64 finaliser.
 *
 * Guarantees: no false negatives. A positive answer only means "possibly present".
 */
public final class BloomFilter {
    private final long[] words;
    private final int bitSize;
    private final int hashCount;
    private final int expectedInsertions;
    private final double targetFpp;
    private int insertions;

    public BloomFilter(int expectedInsertions, double falsePositiveProbability) {
        if (expectedInsertions <= 0) throw new IllegalArgumentException("expectedInsertions must be > 0");
        if (!(falsePositiveProbability > 0.0 && falsePositiveProbability < 1.0))
            throw new IllegalArgumentException("falsePositiveProbability must be in (0,1)");
        this.expectedInsertions = expectedInsertions;
        this.targetFpp = falsePositiveProbability;
        this.bitSize = optimalBits(expectedInsertions, falsePositiveProbability);
        this.hashCount = optimalHashes(expectedInsertions, bitSize);
        this.words = new long[(bitSize + 63) >>> 6];
    }

    public static int optimalBits(int n, double p) {
        long m = (long) Math.ceil(-n * Math.log(p) / (Math.log(2) * Math.log(2)));
        if (m > Integer.MAX_VALUE - 64) throw new IllegalArgumentException("Requested filter too large");
        return (int) Math.max(8, m);
    }

    public static int optimalHashes(int n, int m) {
        return Math.max(1, (int) Math.round((double) m / n * Math.log(2)));
    }

    /** Inserts the key; returns true if at least one bit changed (key was definitely new). */
    public boolean put(String key) {
        boolean changed = false;
        for (int idx : positions(key)) {
            if (!getBit(idx)) { words[idx >>> 6] |= 1L << (idx & 63); changed = true; }
        }
        insertions++;
        return changed;
    }

    /** false = definitely absent; true = POSSIBLY present (may be a false positive). */
    public boolean mightContain(String key) {
        for (int idx : positions(key)) if (!getBit(idx)) return false;
        return true;
    }

    public int[] positions(String key) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("Key must be non-empty");
        byte[] b = key.getBytes(StandardCharsets.UTF_8);
        long h1 = mix64(fnv1a64(b));
        long h2 = mix64(h1 ^ 0x9E3779B97F4A7C15L) | 1L; // odd step so probes differ
        int[] pos = new int[hashCount];
        for (int i = 0; i < hashCount; i++) pos[i] = (int) Math.floorMod(h1 + (long) i * h2, (long) bitSize);
        return pos;
    }

    private static long fnv1a64(byte[] data) {
        long h = 0xcbf29ce484222325L;
        for (byte x : data) { h ^= (x & 0xffL); h *= 0x100000001b3L; }
        return h;
    }

    private static long mix64(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    public boolean getBit(int idx) { return (words[idx >>> 6] & (1L << (idx & 63))) != 0; }

    public int setBitCount() {
        int c = 0;
        for (long w : words) c += Long.bitCount(w);
        return c;
    }

    public int[] setBitIndexes() {
        int[] out = new int[setBitCount()];
        int j = 0;
        for (int i = 0; i < bitSize; i++) if (getBit(i)) out[j++] = i;
        return out;
    }

    /** Theoretical false-positive probability given the number of insertions so far: (1 - e^(-kn/m))^k. */
    public double expectedFalsePositiveRate() {
        return Math.pow(1.0 - Math.exp(-(double) hashCount * insertions / bitSize), hashCount);
    }

    /** Estimate from the actual fill ratio: (set bits / m)^k. */
    public double fillBasedFalsePositiveRate() {
        return Math.pow((double) setBitCount() / bitSize, hashCount);
    }

    public int bitSize() { return bitSize; }
    public int hashCount() { return hashCount; }
    public int insertions() { return insertions; }
    public int expectedInsertions() { return expectedInsertions; }
    public double targetFalsePositiveRate() { return targetFpp; }
    public long memoryBytes() { return (long) words.length * Long.BYTES; }
}
