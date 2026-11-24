package com.secondserve.benchmark;

import java.util.Random;

/** Deterministic (seeded) synthetic allocation instances for the benchmarks. */
public final class SyntheticData {
    private SyntheticData() {}

    public record Instance(int[] supply, int[] capacity, boolean[][] feasible, int edges) {}

    public static Instance generate(int donors, int ngos, long seed, double routeProbability) {
        Random r = new Random(seed);
        int[] supply = new int[donors], cap = new int[ngos];
        boolean[][] ok = new boolean[donors][ngos];
        int edges = 0;
        for (int i = 0; i < donors; i++) supply[i] = 20 + r.nextInt(101);   // 20..120 meals
        for (int j = 0; j < ngos; j++) cap[j] = 30 + r.nextInt(101);        // 30..130 meals
        for (int i = 0; i < donors; i++) for (int j = 0; j < ngos; j++) if (r.nextDouble() < routeProbability) { ok[i][j] = true; edges++; }
        return new Instance(supply, cap, ok, edges);
    }
}
