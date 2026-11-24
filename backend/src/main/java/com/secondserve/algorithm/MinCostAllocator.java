package com.secondserve.algorithm;

/**
 * Cost-aware allocation: among ALL maximum allocations (same number of meals as Edmonds-Karp) pick the one with minimum
 * total cost.  Network = the usual Source -> batches -> NGOs -> Sink, with
 *   cost(source->batch) = sourceCost[i]   (small for urgent batches, so expiring food is used first)
 *   cost(batch->NGO)    = midCost[i][j]   (travel minutes + safety-margin penalty)
 */
public final class MinCostAllocator {
    private MinCostAllocator() {}

    public record Result(int[][] flow, int total, long cost, int augmentations) {}

    public static Result solve(int[] supply, int[] ngoCap, boolean[][] feasible, int[] sourceCost, int[][] midCost) {
        int b = supply.length, n = ngoCap.length;
        MinCostFlow g = new MinCostFlow(b + n + 2);
        int s = 0, t = b + n + 1;
        for (int i = 0; i < b; i++) g.addEdge(s, 1 + i, supply[i], sourceCost[i]);
        int[][] arc = new int[b][n];
        for (int i = 0; i < b; i++)
            for (int j = 0; j < n; j++) arc[i][j] = feasible[i][j] ? g.addEdge(1 + i, 1 + b + j, FlowNetwork.INFINITE, midCost[i][j]) : -1;
        for (int j = 0; j < n; j++) g.addEdge(1 + b + j, t, ngoCap[j], 0);
        MinCostFlow.Result r = g.solve(s, t);
        int[][] f = new int[b][n];
        for (int i = 0; i < b; i++) for (int j = 0; j < n; j++) if (arc[i][j] >= 0) f[i][j] = g.flow(arc[i][j]);
        return new Result(f, r.flow(), r.cost(), r.augmentations());
    }
}
