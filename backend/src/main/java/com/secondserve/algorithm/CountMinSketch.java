package com.secondserve.algorithm;

import java.nio.charset.StandardCharsets;

/**
 * Count-Min Sketch (Cormode & Muthukrishnan): a randomized, sub-linear-space frequency estimator (Unit 6).
 *
 * d = ceil(ln(1/delta)) rows of w = ceil(e/epsilon) counters. update(x, c) adds c to one counter per row (chosen by d
 * pairwise-independent-style hashes); estimate(x) returns the MINIMUM of the d counters.
 * Guarantees: estimate >= true count (never underestimates), and with probability >= 1 - delta
 * estimate <= true count + epsilon * N, where N is the total of all updates. Space O(d*w) = O((1/eps) ln(1/delta)),
 * independent of the number of distinct keys; update and query O(d).
 */
public final class CountMinSketch {
    private final int width, depth;
    private final long[][] table;
    private final long[] seeds;
    private final double epsilon, delta;
    private long total;

    public CountMinSketch(double epsilon, double delta) {
        if (!(epsilon > 0 && epsilon < 1)) throw new IllegalArgumentException("epsilon must be in (0,1)");
        if (!(delta > 0 && delta < 1)) throw new IllegalArgumentException("delta must be in (0,1)");
        this.epsilon = epsilon; this.delta = delta;
        this.width = (int) Math.ceil(Math.E / epsilon);
        this.depth = (int) Math.ceil(Math.log(1.0 / delta));
        this.table = new long[depth][width];
        this.seeds = new long[depth];
        long s = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < depth; i++) { s += 0x9E3779B97F4A7C15L; seeds[i] = mix(s); }
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static long fnv(String key) {
        long h = 0xcbf29ce484222325L;
        for (byte b : key.getBytes(StandardCharsets.UTF_8)) { h ^= (b & 0xff); h *= 0x100000001b3L; }
        return h;
    }

    private int slot(long base, int row) { return (int) Long.remainderUnsigned(mix(base ^ seeds[row]), width); }

    public void update(String key, long count) {
        if (key == null || key.isEmpty()) throw new IllegalArgumentException("key must not be empty");
        if (count < 0) throw new IllegalArgumentException("count must be >= 0");
        long h = fnv(key);
        for (int r = 0; r < depth; r++) table[r][slot(h, r)] += count;
        total += count;
    }

    public long estimate(String key) {
        long h = fnv(key), best = Long.MAX_VALUE;
        for (int r = 0; r < depth; r++) best = Math.min(best, table[r][slot(h, r)]);
        return best;
    }

    public long total() { return total; }
    public int width() { return width; }
    public int depth() { return depth; }
    public double epsilon() { return epsilon; }
    public double delta() { return delta; }
    public long memoryBytes() { return (long) width * depth * 8; }
    /** Additive error bound epsilon * N that holds with probability >= 1 - delta. */
    public double errorBound() { return epsilon * total; }
}
