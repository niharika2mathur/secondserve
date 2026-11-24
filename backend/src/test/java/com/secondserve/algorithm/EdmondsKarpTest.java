package com.secondserve.algorithm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Correctness tests for the custom Edmonds-Karp implementation (documented in docs/test-report.md). */
class EdmondsKarpTest {

    private static EdmondsKarp.Result run(FlowNetwork n, int s, int t) { return EdmondsKarp.solve(n, s, t, true); }

    /** EK-1: textbook (CLRS) network, known maximum flow = 23. */
    @Test @DisplayName("EK-1 basic known maximum-flow graph (CLRS) gives 23 and min-cut capacity 23")
    void basicKnownGraph() {
        FlowNetwork g = new FlowNetwork(6); // s=0 v1=1 v2=2 v3=3 v4=4 t=5
        g.addEdge(0, 1, 16); g.addEdge(0, 2, 13); g.addEdge(1, 2, 10); g.addEdge(2, 1, 4);
        g.addEdge(1, 3, 12); g.addEdge(3, 2, 9);  g.addEdge(2, 4, 14); g.addEdge(4, 3, 7);
        g.addEdge(3, 5, 20); g.addEdge(4, 5, 4);
        EdmondsKarp.Result r = run(g, 0, 5);
        assertEquals(23, r.maxFlow());
        assertEquals(23, r.cutCapacity(), "max-flow min-cut theorem");
        assertTrue(r.sourceSide()[0]);
        assertFalse(r.sourceSide()[5]);
    }

    /** EK-2: several augmenting paths must be combined. */
    @Test @DisplayName("EK-2 multiple augmenting paths (diamond with cross edge) sum to 20")
    void multipleAugmentingPaths() {
        FlowNetwork g = new FlowNetwork(4); // s=0 a=1 b=2 t=3
        g.addEdge(0, 1, 10); g.addEdge(0, 2, 10); g.addEdge(1, 3, 10); g.addEdge(2, 3, 10); g.addEdge(1, 2, 2);
        EdmondsKarp.Result r = run(g, 0, 3);
        assertEquals(20, r.maxFlow());
        assertTrue(r.augmentations() >= 2);
        int sum = r.steps().stream().mapToInt(EdmondsKarp.Step::bottleneck).sum();
        assertEquals(20, sum, "bottlenecks of the augmenting paths add up to the max flow");
    }

    /** EK-3: sink unreachable. */
    @Test @DisplayName("EK-3 disconnected destination yields zero flow and an empty cut")
    void disconnectedDestination() {
        FlowNetwork g = new FlowNetwork(4);
        g.addEdge(0, 1, 5); g.addEdge(2, 3, 5);
        EdmondsKarp.Result r = run(g, 0, 3);
        assertEquals(0, r.maxFlow());
        assertEquals(0, r.augmentations());
        assertEquals(0, r.cutCapacity());
    }

    /** EK-4: zero capacity edges are never used. */
    @Test @DisplayName("EK-4 zero-capacity edges carry no flow")
    void zeroCapacityEdges() {
        FlowNetwork g = new FlowNetwork(4); // s=0 a=1 b=2 t=3
        int sa = g.addEdge(0, 1, 0); g.addEdge(1, 3, 5); int sb = g.addEdge(0, 2, 7); g.addEdge(2, 3, 7);
        EdmondsKarp.Result r = run(g, 0, 3);
        assertEquals(7, r.maxFlow());
        assertEquals(0, g.flow(sa));
        assertEquals(7, g.flow(sb));
    }

    /** EK-5: the first (shortest) path s-u-v-t must later be partially undone through the reverse arc v->u. */
    @Test @DisplayName("EK-5 residual reverse-edge rerouting is required and used; max flow 2")
    void reverseEdgeRerouting() {
        FlowNetwork g = new FlowNetwork(6); // s=0 u=1 q=2 v=3 p=4 t=5
        g.addEdge(0, 1, 1); g.addEdge(0, 2, 1); int uv = g.addEdge(1, 3, 1); g.addEdge(1, 4, 1);
        g.addEdge(2, 3, 1); g.addEdge(3, 5, 1); g.addEdge(4, 5, 1);
        EdmondsKarp.Result r = run(g, 0, 5);
        assertEquals(2, r.maxFlow());
        boolean usedReverse = r.steps().stream().anyMatch(st -> st.pathArcs().stream().anyMatch(a -> a % 2 == 1));
        assertTrue(usedReverse, "a later augmenting path must traverse a reverse arc");
        assertEquals(0, g.flow(uv), "the shortcut edge u->v ends up unused after rerouting");
    }

    /** EK-6: invalid inputs. */
    @Test @DisplayName("EK-6 invalid capacities, self loops, bad terminals and tiny graphs are rejected")
    void invalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> new FlowNetwork(0));
        assertThrows(IllegalArgumentException.class, () -> new FlowNetwork(1));
        FlowNetwork g = new FlowNetwork(3);
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 1, -1));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(1, 1, 3));
        assertThrows(IllegalArgumentException.class, () -> g.addEdge(0, 7, 3));
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.solve(g, 0, 0, false));
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.solve(g, -1, 2, false));
        assertThrows(IllegalArgumentException.class, () -> EdmondsKarp.solve(null, 0, 1, false));
    }

    /** EK-7: boundary - smallest legal graph, no edges. */
    @Test @DisplayName("EK-7 boundary: two nodes and no edges gives flow 0; single edge gives its capacity")
    void boundaryGraphs() {
        assertEquals(0, run(new FlowNetwork(2), 0, 1).maxFlow());
        FlowNetwork g = new FlowNetwork(2);
        g.addEdge(0, 1, 9);
        assertEquals(9, run(g, 0, 1).maxFlow());
    }

    /** EK-8: parallel edges add up. */
    @Test @DisplayName("EK-8 parallel edges are summed")
    void parallelEdges() {
        FlowNetwork g = new FlowNetwork(2);
        g.addEdge(0, 1, 3); g.addEdge(0, 1, 4);
        assertEquals(7, run(g, 0, 1).maxFlow());
    }

    /** EK-9: stress - 200 random networks; check conservation, capacity limits and the min-cut certificate. */
    @Test @DisplayName("EK-9 stress: 200 random graphs satisfy conservation, capacity and flow == cut capacity")
    void randomStress() {
        Random rnd = new Random(42);
        for (int iter = 0; iter < 200; iter++) {
            int n = 2 + rnd.nextInt(12);
            FlowNetwork g = new FlowNetwork(n);
            int edges = rnd.nextInt(n * 3 + 1);
            for (int e = 0; e < edges; e++) {
                int u = rnd.nextInt(n), v = rnd.nextInt(n);
                if (u != v) g.addEdge(u, v, rnd.nextInt(20));
            }
            EdmondsKarp.Result r = EdmondsKarp.solve(g, 0, n - 1, false);
            int[] net = new int[n];
            for (int a = 0; a < g.arcCount(); a += 2) {
                int f = g.flow(a);
                assertTrue(f >= 0 && f <= g.original(a), "capacity constraint");
                net[g.from(a)] -= f; net[g.to(a)] += f;
            }
            for (int v = 1; v < n - 1; v++) assertEquals(0, net[v], "conservation at node " + v);
            assertEquals(r.maxFlow(), net[n - 1]);
            assertEquals(r.maxFlow(), r.cutCapacity(), "certificate of optimality");
        }
    }

    /** EK-10: allocation model - the classic greedy trap. */
    @Test @DisplayName("EK-10 allocation model beats FCFS on a greedy-trap instance (20 vs 10 meals)")
    void allocationModelBeatsGreedyTrap() {
        int[] supply = {10, 10};
        int[] cap = {10, 10};
        boolean[][] ok = {{true, true}, {true, false}}; // batch0 -> N0,N1 ; batch1 -> N0 only
        int[][] greedy = FcfsAllocator.allocate(supply, cap, ok);
        int[][] flow = AllocationFlowModel.solve(supply, cap, ok);
        assertEquals(10, FcfsAllocator.total(greedy));
        assertEquals(20, FcfsAllocator.total(flow));
        assertNull(FcfsAllocator.validate(supply, cap, ok, flow));
        assertNull(FcfsAllocator.validate(supply, cap, ok, greedy));
    }
}
