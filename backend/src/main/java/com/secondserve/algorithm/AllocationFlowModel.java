package com.secondserve.algorithm;

/**
 * Builds the SecondServe flow network   Source -> donor batches -> eligible NGOs -> Sink.
 *   source->batch : supply (meal packs available)
 *   batch->NGO    : only if feasible; unbounded capacity (the real limits sit on the outer edges)
 *   NGO->sink     : min(remaining demand, pickup capacity)
 * Node numbering: 0 = source, 1..B = batches, B+1..B+N = NGOs, B+N+1 = sink.
 */
public final class AllocationFlowModel {
    public final FlowNetwork network;
    public final int batches, ngos, source, sink;
    public final int[] sourceArc;   // per batch
    public final int[][] midArc;    // [batch][ngo] forward arc or -1 when infeasible
    public final int[] sinkArc;     // per NGO

    private AllocationFlowModel(int b, int n) {
        batches = b; ngos = n;
        network = new FlowNetwork(b + n + 2);
        source = 0; sink = b + n + 1;
        sourceArc = new int[b]; sinkArc = new int[n]; midArc = new int[b][n];
    }

    public int batchNode(int b) { return 1 + b; }
    public int ngoNode(int n) { return 1 + batches + n; }

    public static AllocationFlowModel build(int[] supply, int[] ngoCapacity, boolean[][] feasible) {
        int b = supply.length, n = ngoCapacity.length;
        AllocationFlowModel m = new AllocationFlowModel(b, n);
        for (int i = 0; i < b; i++) m.sourceArc[i] = m.network.addEdge(m.source, m.batchNode(i), supply[i]);
        for (int i = 0; i < b; i++)
            for (int j = 0; j < n; j++)
                m.midArc[i][j] = feasible[i][j] ? m.network.addEdge(m.batchNode(i), m.ngoNode(j), FlowNetwork.INFINITE) : -1;
        for (int j = 0; j < n; j++) m.sinkArc[j] = m.network.addEdge(m.ngoNode(j), m.sink, ngoCapacity[j]);
        return m;
    }

    /** Reads the batch->NGO quantities out of the solved residual network. */
    public int[][] extractFlows() {
        int[][] f = new int[batches][ngos];
        for (int i = 0; i < batches; i++)
            for (int j = 0; j < ngos; j++) if (midArc[i][j] >= 0) f[i][j] = network.flow(midArc[i][j]);
        return f;
    }

    public static int[][] solve(int[] supply, int[] ngoCapacity, boolean[][] feasible) {
        AllocationFlowModel m = build(supply, ngoCapacity, feasible);
        EdmondsKarp.solve(m.network, m.source, m.sink, false);
        return m.extractFlows();
    }
}
