package com.secondserve.algorithm;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Edmonds-Karp maximum flow (Ford-Fulkerson with BFS augmenting paths), implemented from scratch.
 *
 * Time  O(V * E^2): BFS finds a shortest (fewest-edge) augmenting path in O(E); each augmentation
 *       saturates at least one critical edge and every edge can be critical at most V/2 times, so
 *       there are O(V*E) augmentations.
 * Space O(V + E) for the residual graph, BFS parents and queue.
 *
 * The network passed to solve() is modified in place and becomes the final residual graph.
 */
public final class EdmondsKarp {
    private EdmondsKarp() {}

    /** One augmenting path. flowsAfter holds the flow on every forward arc (ordered by arc index) after this step. */
    public record Step(int number, List<Integer> pathNodes, List<Integer> pathArcs, int bottleneck,
                       int totalFlowAfter, int[] flowsAfter) {}

    public record Result(int maxFlow, int augmentations, int bfsRuns, List<Step> steps,
                         boolean[] sourceSide, List<Integer> cutArcs, long cutCapacity) {}

    public static Result solve(FlowNetwork net, int source, int sink, boolean recordSteps) {
        if (net == null) throw new IllegalArgumentException("Network must not be null");
        int n = net.nodeCount();
        if (source < 0 || source >= n || sink < 0 || sink >= n) throw new IllegalArgumentException("Source/sink out of range");
        if (source == sink) throw new IllegalArgumentException("Source and sink must be different nodes");

        long total = 0;
        int augmentations = 0, bfsRuns = 0;
        List<Step> steps = new ArrayList<>();
        int[] parentArc = new int[n];
        boolean[] visited;

        while (true) {
            // ---- BFS over arcs with residual capacity > 0 (shortest augmenting path) ----
            bfsRuns++;
            visited = new boolean[n];
            java.util.Arrays.fill(parentArc, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            queue.add(source);
            visited[source] = true;
            while (!queue.isEmpty() && !visited[sink]) {
                int u = queue.poll();
                for (int arc : net.arcsFrom(u)) {
                    int v = net.to(arc);
                    if (!visited[v] && net.residual(arc) > 0) {
                        visited[v] = true;
                        parentArc[v] = arc;
                        queue.add(v);
                    }
                }
            }
            if (!visited[sink]) break; // no augmenting path: flow is maximum

            // ---- bottleneck = min residual along the path ----
            int bottleneck = Integer.MAX_VALUE;
            List<Integer> arcs = new ArrayList<>();
            for (int v = sink; v != source; v = net.from(parentArc[v])) {
                arcs.add(parentArc[v]);
                bottleneck = Math.min(bottleneck, net.residual(parentArc[v]));
            }
            Collections.reverse(arcs);
            // ---- augment: forward residual decreases, reverse residual increases ----
            for (int arc : arcs) net.push(arc, bottleneck);
            total += bottleneck;
            augmentations++;

            if (recordSteps) {
                List<Integer> nodes = new ArrayList<>();
                nodes.add(source);
                for (int arc : arcs) nodes.add(net.to(arc));
                int[] flows = new int[net.edgeCount()];
                for (int i = 0; i < flows.length; i++) flows[i] = net.flow(2 * i);
                steps.add(new Step(augmentations, nodes, arcs, bottleneck, (int) total, flows));
            }
        }

        // 'visited' from the final failed BFS is the source side S of a minimum cut.
        List<Integer> cut = new ArrayList<>();
        long cutCap = 0;
        for (int arc = 0; arc < net.arcCount(); arc += 2) {
            if (visited[net.from(arc)] && !visited[net.to(arc)] && net.original(arc) > 0) {
                cut.add(arc);
                cutCap += net.original(arc);
            }
        }
        if (total > Integer.MAX_VALUE) throw new ArithmeticException("Flow value overflow");
        return new Result((int) total, augmentations, bfsRuns, steps, visited, cut, cutCap);
    }
}
