package com.secondserve.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Min-cost max-flow checked against an exhaustive search on small random allocation instances. */
class MinCostFlowTest {

    @Test @DisplayName("MC-1 textbook network: max flow and its minimum cost")
    void textbook() {
        // s=0, a=1, b=2, t=3.  s->a (2,cost1) s->b (2,cost4) a->b (1,cost1) a->t (1,cost6) b->t (3,cost1)
        MinCostFlow g = new MinCostFlow(4);
        g.addEdge(0, 1, 2, 1); g.addEdge(0, 2, 2, 4); g.addEdge(1, 2, 1, 1); g.addEdge(1, 3, 1, 6); g.addEdge(2, 3, 3, 1);
        MinCostFlow.Result r = g.solve(0, 3);
        assertEquals(4, r.flow());
        // cheapest way to push 4: s-a-b-t (cost 3) x1, s-b-t (cost 5) x2, s-a-t (cost 7) x1 = 3+10+7 = 20
        assertEquals(20, r.cost());
    }

    @Test @DisplayName("MC-2 a negative-cost reverse arc is used to reroute (flow value unchanged, cost lower than greedy paths)")
    void reroutes() {
        MinCostFlow g = new MinCostFlow(4);
        int sa = g.addEdge(0, 1, 1, 0), sb = g.addEdge(0, 2, 1, 0);
        int at = g.addEdge(1, 3, 1, 5), bt = g.addEdge(2, 3, 1, 1);
        int ab = g.addEdge(1, 2, 1, 0);
        MinCostFlow.Result r = g.solve(0, 3);
        assertEquals(2, r.flow());
        assertEquals(6, r.cost());
        assertEquals(1, g.flow(at)); assertEquals(1, g.flow(bt)); assertEquals(0, g.flow(ab));
        assertEquals(1, g.flow(sa)); assertEquals(1, g.flow(sb));
    }

    @Test @DisplayName("MC-3 invalid input and boundary cases")
    void invalid() {
        assertThrows(IllegalArgumentException.class, () -> new MinCostFlow(1));
        MinCostFlow g = new MinCostFlow(3);
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 0, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 1, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 1, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 5, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> g.solve(1, 1));
        MinCostFlow.Result r = g.solve(0, 2); // no edges
        assertEquals(0, r.flow()); assertEquals(0, r.cost());
    }

    @Test @DisplayName("MC-4 400 random allocation instances: same total as Edmonds-Karp and cost equals the exhaustive optimum")
    void matchesBruteForce() {
        Random rnd = new Random(7);
        for (int it = 0; it < 400; it++) {
            int b = 2 + rnd.nextInt(2), n = 2 + rnd.nextInt(2);
            int[] supply = new int[b], cap = new int[n], src = new int[b];
            int[][] mid = new int[b][n];
            boolean[][] ok = new boolean[b][n];
            for (int i = 0; i < b; i++) { supply[i] = rnd.nextInt(5); src[i] = rnd.nextInt(4); }
            for (int j = 0; j < n; j++) cap[j] = rnd.nextInt(6);
            for (int i = 0; i < b; i++) for (int j = 0; j < n; j++) { ok[i][j] = rnd.nextInt(4) != 0; mid[i][j] = rnd.nextInt(9); }
            MinCostAllocator.Result r = MinCostAllocator.solve(supply, cap, ok, src, mid);
            int[][] ek = AllocationFlowModel.solve(supply, cap, ok);
            assertEquals(FcfsAllocator.total(ek), r.total(), "same number of meals as max flow");
            assertNull(FcfsAllocator.validate(supply, cap, ok, r.flow()));
            long[] best = {Long.MAX_VALUE}; int[] bestTotal = {-1};
            enumerate(supply, cap, ok, src, mid, new int[b][n], 0, 0, best, bestTotal);
            assertEquals(bestTotal[0], r.total(), "max total vs exhaustive");
            assertEquals(best[0], r.cost(), "min cost among maximum allocations vs exhaustive");
            long recomputed = 0;
            for (int i = 0; i < b; i++) for (int j = 0; j < n; j++) recomputed += (long) r.flow()[i][j] * (src[i] + mid[i][j]);
            assertEquals(r.cost(), recomputed, "reported cost matches the returned flow");
        }
    }

    private static void enumerate(int[] supply, int[] cap, boolean[][] ok, int[] src, int[][] mid, int[][] f, int cell, int total, long[] best, int[] bestTotal) {
        int b = supply.length, n = cap.length;
        if (cell == b * n) {
            for (int j = 0; j < n; j++) { int s = 0; for (int i = 0; i < b; i++) s += f[i][j]; if (s > cap[j]) return; }
            long c = 0;
            for (int i = 0; i < b; i++) for (int j = 0; j < n; j++) c += (long) f[i][j] * (src[i] + mid[i][j]);
            if (total > bestTotal[0] || (total == bestTotal[0] && c < best[0])) { bestTotal[0] = total; best[0] = c; }
            return;
        }
        int i = cell / n, j = cell % n;
        int used = 0; for (int jj = 0; jj < j; jj++) used += f[i][jj];
        int maxV = ok[i][j] ? supply[i] - used : 0;
        for (int v = 0; v <= maxV; v++) { f[i][j] = v; enumerate(supply, cap, ok, src, mid, f, cell + 1, total + v, best, bestTotal); }
        f[i][j] = 0;
    }
}
