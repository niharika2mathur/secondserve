package com.secondserve.algorithm;

import java.util.*;

/**
 * Streaming top-k ("heavy hitters") on top of a Count-Min Sketch. Every event updates the sketch; a bounded candidate map
 * keeps the k keys with the largest ESTIMATED totals. O(d + k) per event, O(d*w + k) space in total, independent of the
 * number of distinct keys. Because Count-Min only over-estimates, a truly heavy key (total > N/k + eps*N) is never missed.
 */
public final class HeavyHitters {
    private final int k;
    private final CountMinSketch sketch;
    private final Map<String, Long> candidates = new HashMap<>();

    public HeavyHitters(int k, CountMinSketch sketch) {
        if (k < 1) throw new IllegalArgumentException("k must be >= 1");
        this.k = k; this.sketch = sketch;
    }

    public void update(String key, long count) {
        sketch.update(key, count);
        long est = sketch.estimate(key);
        if (candidates.containsKey(key) || candidates.size() < k) { candidates.put(key, est); return; }
        String minKey = null; long min = Long.MAX_VALUE;
        for (Map.Entry<String, Long> e : candidates.entrySet()) if (e.getValue() < min) { min = e.getValue(); minKey = e.getKey(); }
        if (est > min) { candidates.remove(minKey); candidates.put(key, est); }
    }

    /** Candidates by estimated total, largest first. */
    public List<Map.Entry<String, Long>> top() {
        List<Map.Entry<String, Long>> l = new ArrayList<>();
        for (Map.Entry<String, Long> e : candidates.entrySet()) l.add(Map.entry(e.getKey(), sketch.estimate(e.getKey())));
        l.sort((a, b) -> { int c = Long.compare(b.getValue(), a.getValue()); return c != 0 ? c : a.getKey().compareTo(b.getKey()); });
        return l;
    }

    public CountMinSketch sketch() { return sketch; }
}
