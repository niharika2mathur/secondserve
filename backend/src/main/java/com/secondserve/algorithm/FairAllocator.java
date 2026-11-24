package com.secondserve.algorithm;

import java.util.ArrayList;
import java.util.List;

/**
 * Max-min fair (leximin) allocation by PROGRESSIVE FILLING over repeated Edmonds-Karp runs.
 *
 * Idea (water-filling): raise a common level t for every "active" NGO.  Level t is feasible iff a max-flow in which each
 * active NGO's sink edge has capacity min(cap_j, t) (and frozen NGOs their frozen amount) saturates all those edges.
 * Feasibility is monotone in t, so the largest feasible t is found by binary search.  At that level not everybody can
 * rise any more; because meals are whole numbers there may be ties (each NGO could take +1 alone, not all together), so a
 * greedy single-step raise lifts as many NGOs as possible to t+1 and freezes the others at t (they sit in a tight cut).
 *
 * The set of receipt vectors of such a network is a polymatroid, so the leximin-optimal vector is also a maximal one:
 * the total equals the ordinary maximum flow.  Fairness therefore costs 0 meals ("price of fairness = 0"), it only changes
 * WHO receives them.  Rounds <= N, each round does O(log C + N) max-flow calls.
 */
public final class FairAllocator {
    private FairAllocator() {}

    public record Round(int round, int level, List<Integer> frozenNgos, int maxFlowCalls) {}
    public record Result(int[][] flow, int total, int[] receipt, List<Round> rounds, int maxFlowCalls) {}

    public static Result solve(int[] supply, int[] ngoCap, boolean[][] feasible) {
        int n = ngoCap.length;
        boolean[] frozen = new boolean[n];
        int[] value = new int[n];
        int level = 0, calls = 0, roundNo = 0;
        List<Round> rounds = new ArrayList<>();
        int activeCount = n;
        while (activeCount > 0) {
            roundNo++;
            int roundCalls = 0;
            int maxCap = level;
            for (int j = 0; j < n; j++) if (!frozen[j]) maxCap = Math.max(maxCap, ngoCap[j]);
            int lo = level, hi = maxCap;          // lo is feasible (invariant); find the largest feasible common level in [lo, hi]
            while (lo < hi) {
                int mid = lo + (hi - lo + 1) / 2;
                roundCalls++;
                if (feasible(supply, ngoCap, feasible, frozen, value, mid, null)) lo = mid; else hi = mid - 1;
            }
            level = lo;
            List<Integer> newly = new ArrayList<>();
            for (int j = 0; j < n; j++) if (!frozen[j] && ngoCap[j] <= level) { frozen[j] = true; value[j] = ngoCap[j]; newly.add(j); }
            // No common raise is possible any more. Integer ties: raise as many NGOs as possible by ONE more meal, greedily
            // (the raisable sets form a matroid, so greedy reaches the maximum number); the rest are frozen at this level.
            boolean[] raise = new boolean[n];
            boolean any = false;
            for (int j = 0; j < n; j++) {
                if (frozen[j]) continue;
                raise[j] = true;
                roundCalls++;
                if (feasible(supply, ngoCap, feasible, frozen, value, level, raise)) any = true; else raise[j] = false;
            }
            for (int j = 0; j < n; j++) if (!frozen[j] && !raise[j]) { frozen[j] = true; value[j] = level; newly.add(j); }
            if (newly.isEmpty()) throw new IllegalStateException("Progressive filling made no progress (should be impossible)");
            activeCount -= newly.size();
            calls += roundCalls;
            rounds.add(new Round(roundNo, level, newly, roundCalls));
            if (any) level++;
        }
        // final flow with the frozen amounts as exact sink capacities
        AllocationFlowModel m = AllocationFlowModel.build(supply, value, feasible);
        EdmondsKarp.Result r = EdmondsKarp.solve(m.network, m.source, m.sink, false);
        calls++;
        return new Result(m.extractFlows(), r.maxFlow(), value, rounds, calls);
    }

    /** Is there a flow giving every frozen NGO its value and every active NGO min(cap, level [+1 if in raise])? */
    private static boolean feasible(int[] supply, int[] cap, boolean[][] ok, boolean[] frozen, int[] value, int level, boolean[] raise) {
        int n = cap.length;
        int[] target = new int[n];
        long need = 0;
        for (int j = 0; j < n; j++) {
            target[j] = frozen[j] ? value[j] : Math.min(cap[j], level + (raise != null && raise[j] ? 1 : 0));
            need += target[j];
        }
        AllocationFlowModel m = AllocationFlowModel.build(supply, target, ok);
        return EdmondsKarp.solve(m.network, m.source, m.sink, false).maxFlow() == need;
    }

    /** Jain's fairness index (sum x)^2 / (n * sum x^2) over the given values; 1 = perfectly equal, 1/n = one gets everything. */
    public static double jain(double[] x) {
        double s = 0, q = 0;
        for (double v : x) { s += v; q += v * v; }
        return q == 0 ? 1.0 : (s * s) / (x.length * q);
    }
}
