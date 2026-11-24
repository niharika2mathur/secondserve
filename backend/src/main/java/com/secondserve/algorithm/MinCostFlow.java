package com.secondserve.algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Minimum-cost maximum-flow by Successive Shortest Paths, implemented from scratch.
 *
 * Repeatedly find the cheapest source->sink path in the residual graph (SPFA, i.e. queue-based Bellman-Ford, because
 * reverse arcs carry NEGATIVE cost) and push the bottleneck along it. Because every augmenting path is a cheapest one,
 * after k units the flow is a minimum-cost flow of value k; running until no path remains gives a maximum flow of
 * minimum cost among all maximum flows (Unit 5: network flows, optimality via the absence of negative residual cycles).
 *
 * Time  O(F * V * E) worst case (F = max-flow value, one SPFA = O(V*E) worst case, typically O(E)); each augmentation
 *       pushes a whole bottleneck, so the number of augmentations is far below F in practice.
 * Space O(V + E).
 *
 * Arc layout is the same as {@link FlowNetwork}: arc 2k is forward, 2k+1 its reverse (cost = -cost).
 */
public final class MinCostFlow {
    private static final long INF = Long.MAX_VALUE / 4;

    public record Result(int flow, long cost, int augmentations) {}

    private final int n;
    private final List<List<Integer>> adj;
    private int[] head = new int[32], cap = new int[32], orig = new int[32];
    private long[] cost = new long[32];
    private int arcs;

    public MinCostFlow(int nodes) {
        if (nodes < 2) throw new IllegalArgumentException("A flow network needs at least 2 nodes, got " + nodes);
        n = nodes;
        adj = new ArrayList<>(nodes);
        for (int i = 0; i < nodes; i++) adj.add(new ArrayList<>());
    }

    /** Adds edge u->v and returns the index of its forward arc. Cost may be any non-negative value. */
    public int addEdge(int u, int v, int capacity, long unitCost) {
        if (u < 0 || v < 0 || u >= n || v >= n) throw new IllegalArgumentException("Node index out of range");
        if (u == v) throw new IllegalArgumentException("Self-loops are not allowed");
        if (capacity < 0) throw new IllegalArgumentException("Capacity must be >= 0");
        if (unitCost < 0) throw new IllegalArgumentException("Costs must be >= 0 (reverse arcs get the negative cost)");
        if (arcs + 2 > head.length) {
            int m = head.length * 2;
            head = Arrays.copyOf(head, m); cap = Arrays.copyOf(cap, m); orig = Arrays.copyOf(orig, m); cost = Arrays.copyOf(cost, m);
        }
        head[arcs] = v; cap[arcs] = capacity; orig[arcs] = capacity; cost[arcs] = unitCost; adj.get(u).add(arcs);
        head[arcs + 1] = u; cap[arcs + 1] = 0; orig[arcs + 1] = 0; cost[arcs + 1] = -unitCost; adj.get(v).add(arcs + 1);
        arcs += 2;
        return arcs - 2;
    }

    public int flow(int forwardArc) { return orig[forwardArc] - cap[forwardArc]; }

    public Result solve(int source, int sink) {
        if (source == sink || source < 0 || sink < 0 || source >= n || sink >= n) throw new IllegalArgumentException("Bad source/sink");
        long total = 0, totalCost = 0;
        int aug = 0;
        long[] dist = new long[n];
        int[] parent = new int[n];
        boolean[] inQ = new boolean[n];
        while (true) {
            Arrays.fill(dist, INF); Arrays.fill(parent, -1);
            dist[source] = 0;
            ArrayDeque<Integer> q = new ArrayDeque<>();
            q.add(source); inQ[source] = true;
            while (!q.isEmpty()) {
                int u = q.poll(); inQ[u] = false;
                for (int a : adj.get(u)) {
                    if (cap[a] > 0 && dist[u] + cost[a] < dist[head[a]]) {
                        dist[head[a]] = dist[u] + cost[a];
                        parent[head[a]] = a;
                        if (!inQ[head[a]]) { inQ[head[a]] = true; q.add(head[a]); }
                    }
                }
            }
            if (dist[sink] >= INF) break;
            int push = Integer.MAX_VALUE;
            for (int v = sink; v != source; v = head[parent[v] ^ 1]) push = Math.min(push, cap[parent[v]]);
            for (int v = sink; v != source; v = head[parent[v] ^ 1]) { cap[parent[v]] -= push; cap[parent[v] ^ 1] += push; }
            total += push; totalCost += (long) push * dist[sink]; aug++;
        }
        if (total > Integer.MAX_VALUE) throw new ArithmeticException("Flow value overflow");
        return new Result((int) total, totalCost, aug);
    }
}
