package com.secondserve.algorithm;

/**
 * Baseline: first-come-first-served greedy allocation. Batches are processed in registration order,
 * and for each batch eligible NGOs are processed in registration order; each pair receives
 * min(batch remaining, NGO remaining capacity). Uses exactly the same feasibility matrix as the flow engine.
 * It never reconsiders a decision, so it can block a larger total allocation.
 */
public final class FcfsAllocator {
    private FcfsAllocator() {}

    public static int[][] allocate(int[] supply, int[] ngoCapacity, boolean[][] feasible) {
        int b = supply.length, n = ngoCapacity.length;
        int[][] out = new int[b][n];
        int[] cap = ngoCapacity.clone();
        for (int i = 0; i < b; i++) {
            int left = supply[i];
            for (int j = 0; j < n && left > 0; j++) {
                if (!feasible[i][j]) continue;
                int q = Math.min(left, cap[j]);
                if (q > 0) { out[i][j] = q; left -= q; cap[j] -= q; }
            }
        }
        return out;
    }

    public static int total(int[][] flow) {
        int t = 0;
        for (int[] row : flow) for (int v : row) t += v;
        return t;
    }

    /** Verifies supply / capacity / feasibility constraints; returns null when valid, otherwise a message. */
    public static String validate(int[] supply, int[] ngoCapacity, boolean[][] feasible, int[][] flow) {
        for (int i = 0; i < supply.length; i++) {
            int s = 0;
            for (int j = 0; j < ngoCapacity.length; j++) {
                if (flow[i][j] < 0) return "negative flow at " + i + "," + j;
                if (flow[i][j] > 0 && !feasible[i][j]) return "flow on infeasible pair " + i + "," + j;
                s += flow[i][j];
            }
            if (s > supply[i]) return "batch " + i + " over-allocated";
        }
        for (int j = 0; j < ngoCapacity.length; j++) {
            int s = 0;
            for (int i = 0; i < supply.length; i++) s += flow[i][j];
            if (s > ngoCapacity[j]) return "NGO " + j + " over capacity";
        }
        return null;
    }
}
