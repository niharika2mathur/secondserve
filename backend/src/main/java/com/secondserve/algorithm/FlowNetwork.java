package com.secondserve.algorithm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Residual-graph representation of a flow network with integer capacities.
 *
 * Every added edge u->v creates TWO arcs stored side by side: arc 2k is the forward arc (residual =
 * capacity) and arc 2k+1 is its reverse arc (residual = 0). Pushing delta units along an arc is
 * residual[a] -= delta and residual[a^1] += delta, which is exactly how reverse edges let a later
 * augmenting path "undo" earlier routing decisions.
 */
public final class FlowNetwork {
    /** Stand-in for an unbounded capacity (middle edges of the allocation network). */
    public static final int INFINITE = 1_000_000_000;

    private final int nodeCount;
    private final List<List<Integer>> adjacency;
    private int[] head = new int[32];      // arc -> destination node
    private int[] residual = new int[32];  // arc -> remaining residual capacity
    private int[] original = new int[32];  // arc -> original capacity (0 for reverse arcs)
    private int arcCount;

    public FlowNetwork(int nodeCount) {
        if (nodeCount < 2) {
            throw new IllegalArgumentException("A flow network needs at least 2 nodes (source and sink), got " + nodeCount);
        }
        this.nodeCount = nodeCount;
        this.adjacency = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) adjacency.add(new ArrayList<>());
    }

    /** Adds edge from->to and returns the index of its forward arc. */
    public int addEdge(int from, int to, int capacity) {
        checkNode(from);
        checkNode(to);
        if (from == to) throw new IllegalArgumentException("Self-loops are not allowed (node " + from + ")");
        if (capacity < 0) throw new IllegalArgumentException("Capacity must be >= 0, got " + capacity);
        if (arcCount + 2 > head.length) {
            int n = head.length * 2;
            head = Arrays.copyOf(head, n);
            residual = Arrays.copyOf(residual, n);
            original = Arrays.copyOf(original, n);
        }
        head[arcCount] = to;       residual[arcCount] = capacity; original[arcCount] = capacity;
        adjacency.get(from).add(arcCount);
        head[arcCount + 1] = from; residual[arcCount + 1] = 0;    original[arcCount + 1] = 0;
        adjacency.get(to).add(arcCount + 1);
        arcCount += 2;
        return arcCount - 2;
    }

    private void checkNode(int v) {
        if (v < 0 || v >= nodeCount) throw new IllegalArgumentException("Node index out of range: " + v);
    }

    public int nodeCount() { return nodeCount; }
    public int arcCount() { return arcCount; }
    public int edgeCount() { return arcCount / 2; }
    public List<Integer> arcsFrom(int node) { return adjacency.get(node); }
    public int to(int arc) { return head[arc]; }
    public int from(int arc) { return head[arc ^ 1]; }
    public int residual(int arc) { return residual[arc]; }
    public int original(int arc) { return original[arc]; }
    public boolean isForward(int arc) { return (arc & 1) == 0; }
    /** Flow currently carried by a forward arc. */
    public int flow(int forwardArc) { return original[forwardArc] - residual[forwardArc]; }

    void push(int arc, int delta) {
        residual[arc] -= delta;
        residual[arc ^ 1] += delta;
    }

    /** Rough structural memory estimate in bytes (3 int arrays + adjacency references). */
    public long estimatedBytes() {
        return (long) arcCount * (3 * 4 + 4 + 16);
    }
}
