package com.secondserve.service;

import com.secondserve.algorithm.*;
import com.secondserve.benchmark.SyntheticData;
import com.secondserve.exception.ValidationException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Runs real, seeded benchmark experiments. Nothing here is precomputed. */
public final class BenchmarkService {
    public record FlowRow(int donors, int ngos, int feasibleRoutes, int totalSupply, int totalCapacity,
                          double edmondsKarpMillis, int edmondsKarpAllocated, int edmondsKarpMatches, int augmentations,
                          double greedyMillis, int greedyAllocated, int greedyMatches, int gapMeals, double gapPercent,
                          long networkBytesEstimate, boolean validatedAgainstConstraints) {}

    public record BloomRow(int identifiers, int negativeQueries, int bitSize, int hashCount, long bloomBytes, long exactSetBytesApprox,
                           double bloomQueryMillis, double exactQueryMillis, double twoStageMillis, int falsePositives,
                           double observedFpRate, double theoreticalFpRate, int falseNegatives, int exactLookupsAvoided) {}

    /** Edmonds-Karp vs min-cost flow vs fair share on the same instance (costs: travel minutes + safety penalty + urgency). */
    public record StrategyRow(int donors, int ngos, int meals, double ekMillis, double minCostMillis, double fairMillis, long ekMealMinutes, long minCostMealMinutes,
                              double travelSavingPercent, int ekTightMeals, int minCostTightMeals, int ekMinReceipt, int fairMinReceipt, double ekJain, double fairJain,
                              int fairMaxFlowCalls, boolean sameTotal, boolean allValid) {}

    /** Count-Min Sketch vs exact HashMap on a skewed stream of (donor, meals) events. */
    public record SketchRow(int events, int distinctKeys, double epsilon, double delta, long sketchBytes, long exactBytesApprox, double sketchMillis, double exactMillis,
                            long totalMeals, double errorBound, long maxOverestimate, double withinBoundPercent, boolean neverUnderestimates, boolean top10Correct) {}

    /** Greedy interval partitioning (min-heap) vs a naive first-fit linear scan. */
    public record ScheduleRow(int trips, int volunteers, int depth, boolean optimal, double heapMillis, double linearScanMillis) {}

    public record Report(long seed, int repetitions, String executedAt, String javaVersion, int availableProcessors,
                         List<FlowRow> flow, List<BloomRow> bloom, List<String> notes,
                         List<StrategyRow> strategy, List<SketchRow> sketch, List<ScheduleRow> schedule) {}

    public static final int[] STRATEGY_SIZES = {10, 25, 50, 100};
    public static final int[] SKETCH_EVENTS = {10_000, 100_000, 1_000_000};
    public static final int[] SCHEDULE_TRIPS = {1_000, 10_000, 100_000};

    public static final int[] FLOW_SIZES = {10, 25, 50, 100};
    public static final int[] BLOOM_SIZES = {100, 1_000, 10_000};
    public static final int NEGATIVE_QUERIES = 20_000;
    private volatile Report latest;

    public Report latest() { return latest; }

    /** REST entry: optional {"seed": 2026, "repetitions": 7}. */
    public Report runRequest(Map<String, Object> body) {
        long seed = 2026; int reps = 7;
        try {
            if (body != null && body.get("seed") != null) seed = ((Number) body.get("seed")).longValue();
            if (body != null && body.get("repetitions") != null) reps = ((Number) body.get("repetitions")).intValue();
        } catch (ClassCastException e) { throw new ValidationException("seed and repetitions must be numbers"); }
        return run(seed, reps);
    }

    public synchronized Report run(long seed, int repetitions) {
        if (repetitions < 1 || repetitions > 50) throw new ValidationException("Repetitions must be between 1 and 50");
        flowBench(25, seed, 3); bloomBench(1_000, seed, 3); strategyBench(10, seed, 1); sketchBench(10_000, seed); scheduleBench(1_000, seed, 1); // discarded JIT warm-up pass
        List<FlowRow> flow = new ArrayList<>();
        for (int size : FLOW_SIZES) flow.add(flowBench(size, seed + size, repetitions));
        List<BloomRow> bloom = new ArrayList<>();
        for (int size : BLOOM_SIZES) bloom.add(bloomBench(size, seed + size, repetitions));
        List<String> notes = List.of(
                "Times are the median of " + repetitions + " timed repetitions after warm-up runs, measured with System.nanoTime on this machine; they vary between machines.",
                "Flow instances: random supply 20-120, NGO capacity 30-130, each donor-NGO route feasible with probability 0.25; seed = " + seed + " + size.",
                "Both allocators use the same feasibility matrix and capacities; every allocation is validated against supply, capacity and feasibility constraints.",
                "Network memory is a structural estimate (arc arrays), not a heap measurement. Exact-set memory is an estimate: bytes allocated while building a pre-sized HashSet plus its String objects.",
                "Bloom queries: all inserted IDs (to confirm zero false negatives) plus up to " + NEGATIVE_QUERIES + " never-inserted IDs per size.",
                "An in-memory HashSet is itself very fast, so Bloom screening is not expected to beat it on speed; its value is avoiding exact lookups against a slower authoritative store while using far less memory.");
        List<StrategyRow> strategy = new ArrayList<>();
        for (int size : STRATEGY_SIZES) strategy.add(strategyBench(size, seed + 7 * size, Math.min(repetitions, size >= 100 ? 1 : 3)));
        List<SketchRow> sketch = new ArrayList<>();
        for (int n : SKETCH_EVENTS) sketch.add(sketchBench(n, seed + n));
        List<ScheduleRow> schedule = new ArrayList<>();
        for (int n : SCHEDULE_TRIPS) schedule.add(scheduleBench(n, seed + n, Math.min(repetitions, 5)));
        List<String> all = new ArrayList<>(notes);
        all.add("Strategy instances: same generator as the flow benchmark plus travel 10-60 min and 60-300 min to deadline per batch; cost = travel + 2*max(0, 30 - slack) on routes and minutes-to-deadline/15 (capped at 720) on supply. Fair share is timed with min(repetitions, 3) runs (1 at 100x100) because it needs many max-flow calls.");
        all.add("Count-Min: epsilon 0.001, delta 0.01, keys skewed so that a few donors dominate; the exact map is a HashMap<String, Long>; its memory is an analytic estimate (node + Long + String objects), the sketch memory is exact (width x depth x 8 bytes).");
        all.add("Interval partitioning: trip durations 20-180 min over a 0-1,000,000 min window; the linear-scan baseline checks volunteers in order for the first free one. Both are optimal (resources = depth). The heap version is NOT faster in this experiment: the number of volunteers k stays small (at most a few dozen), so an O(n k) scan beats O(n log k) heap bookkeeping; the heap only pays off when k is large.");
        latest = new Report(seed, repetitions, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), System.getProperty("java.version"),
                Runtime.getRuntime().availableProcessors(), flow, bloom, all, strategy, sketch, schedule);
        return latest;
    }

    private FlowRow flowBench(int size, long seed, int reps) {
        SyntheticData.Instance in = SyntheticData.generate(size, size, seed, 0.25);
        for (int w = 0; w < 10; w++) {
            AllocationFlowModel.solve(in.supply(), in.capacity(), in.feasible());
            FcfsAllocator.allocate(in.supply(), in.capacity(), in.feasible());
        }
        double[] ek = new double[reps], gr = new double[reps];
        int[][] ekFlow = null, grFlow = null;
        int aug = 0;
        long bytes = 0;
        for (int k = 0; k < reps; k++) {
            AllocationFlowModel m = AllocationFlowModel.build(in.supply(), in.capacity(), in.feasible());
            long t0 = System.nanoTime();
            EdmondsKarp.Result r = EdmondsKarp.solve(m.network, m.source, m.sink, false);
            ek[k] = (System.nanoTime() - t0) / 1e6;
            aug = r.augmentations();
            ekFlow = m.extractFlows();
            bytes = m.network.estimatedBytes();
            long t1 = System.nanoTime();
            grFlow = FcfsAllocator.allocate(in.supply(), in.capacity(), in.feasible());
            gr[k] = (System.nanoTime() - t1) / 1e6;
        }
        int ekTotal = FcfsAllocator.total(ekFlow), grTotal = FcfsAllocator.total(grFlow);
        boolean valid = FcfsAllocator.validate(in.supply(), in.capacity(), in.feasible(), ekFlow) == null
                && FcfsAllocator.validate(in.supply(), in.capacity(), in.feasible(), grFlow) == null && ekTotal >= grTotal;
        int ts = Arrays.stream(in.supply()).sum(), tc = Arrays.stream(in.capacity()).sum();
        return new FlowRow(size, size, in.edges(), ts, tc, median(ek), ekTotal, matches(ekFlow), aug, median(gr), grTotal, matches(grFlow),
                ekTotal - grTotal, grTotal == 0 ? 0 : 100.0 * (ekTotal - grTotal) / grTotal, bytes, valid);
    }

    // ---------------------------------------------------------------- strategies
    private StrategyRow strategyBench(int size, long seed, int reps) {
        SyntheticData.Instance in = SyntheticData.generate(size, size, seed, 0.25);
        Random r = new Random(seed ^ 0x5DEECE66DL);
        int B = size, N = size;
        int[][] travel = new int[B][N], mid = new int[B][N], slack = new int[B][N];
        int[] src = new int[B];
        for (int i = 0; i < B; i++) {
            int left = 60 + r.nextInt(241);
            src[i] = Math.min(left, 720) / 15;
            for (int j = 0; j < N; j++) if (in.feasible()[i][j]) {
                travel[i][j] = 10 + r.nextInt(51);
                slack[i][j] = Math.max(0, left - travel[i][j]);
                mid[i][j] = travel[i][j] + 2 * Math.max(0, 30 - slack[i][j]);
            }
        }
        double[] ekT = new double[reps], mcT = new double[reps], faT = new double[reps];
        int[][] ek = null; MinCostAllocator.Result mc = null; FairAllocator.Result fa = null;
        for (int k = 0; k < reps; k++) {
            long t0 = System.nanoTime(); ek = AllocationFlowModel.solve(in.supply(), in.capacity(), in.feasible()); ekT[k] = (System.nanoTime() - t0) / 1e6;
            long t1 = System.nanoTime(); mc = MinCostAllocator.solve(in.supply(), in.capacity(), in.feasible(), src, mid); mcT[k] = (System.nanoTime() - t1) / 1e6;
            long t2 = System.nanoTime(); fa = FairAllocator.solve(in.supply(), in.capacity(), in.feasible()); faT[k] = (System.nanoTime() - t2) / 1e6;
        }
        boolean valid = FcfsAllocator.validate(in.supply(), in.capacity(), in.feasible(), ek) == null && FcfsAllocator.validate(in.supply(), in.capacity(), in.feasible(), mc.flow()) == null
                && FcfsAllocator.validate(in.supply(), in.capacity(), in.feasible(), fa.flow()) == null;
        long ekMm = 0, mcMm = 0; int ekTight = 0, mcTight = 0;
        for (int i = 0; i < B; i++) for (int j = 0; j < N; j++) {
            ekMm += (long) ek[i][j] * travel[i][j]; mcMm += (long) mc.flow()[i][j] * travel[i][j];
            if (slack[i][j] < 30) { ekTight += ek[i][j]; mcTight += mc.flow()[i][j]; }
        }
        int[] re = new int[N];
        for (int[] row : ek) for (int j = 0; j < N; j++) re[j] += row[j];
        double[] ratioEk = new double[N], ratioFa = new double[N];
        int minEk = Integer.MAX_VALUE, minFa = Integer.MAX_VALUE, counted = 0;
        for (int j = 0; j < N; j++) {
            boolean reach = false;
            for (int i = 0; i < B; i++) reach |= in.feasible()[i][j];
            if (!reach) continue;
            ratioEk[counted] = (double) re[j] / in.capacity()[j]; ratioFa[counted] = (double) fa.receipt()[j] / in.capacity()[j]; counted++;
            minEk = Math.min(minEk, re[j]); minFa = Math.min(minFa, fa.receipt()[j]);
        }
        double jEk = FairAllocator.jain(Arrays.copyOf(ratioEk, counted)), jFa = FairAllocator.jain(Arrays.copyOf(ratioFa, counted));
        int total = FcfsAllocator.total(ek);
        return new StrategyRow(size, size, total, median(ekT), median(mcT), median(faT), ekMm, mcMm, ekMm == 0 ? 0 : 100.0 * (ekMm - mcMm) / ekMm, ekTight, mcTight,
                minEk == Integer.MAX_VALUE ? 0 : minEk, minFa == Integer.MAX_VALUE ? 0 : minFa, jEk, jFa, fa.maxFlowCalls(), total == mc.total() && total == fa.total(), valid);
    }

    // ------------------------------------------------------------ Count-Min Sketch
    private SketchRow sketchBench(int events, long seed) {
        int distinct = Math.max(500, events / 20);
        Random r = new Random(seed);
        String[] names = new String[distinct];
        for (int i = 0; i < distinct; i++) names[i] = "DONOR-" + i;
        int[] key = new int[events], w = new int[events];
        for (int e = 0; e < events; e++) { key[e] = (int) (distinct * Math.pow(r.nextDouble(), 4)); w[e] = 1 + r.nextInt(120); }
        double eps = 0.001, delta = 0.01;
        CountMinSketch cms = new CountMinSketch(eps, delta);
        for (int e = 0; e < Math.min(events, 20_000); e++) cms.update(names[key[e]], w[e]);   // warm-up on a throw-away sketch
        cms = new CountMinSketch(eps, delta);
        long t0 = System.nanoTime();
        for (int e = 0; e < events; e++) cms.update(names[key[e]], w[e]);
        double cmsMs = (System.nanoTime() - t0) / 1e6;
        Map<String, Long> exact = new HashMap<>();
        long t1 = System.nanoTime();
        for (int e = 0; e < events; e++) exact.merge(names[key[e]], (long) w[e], Long::sum);
        double exactMs = (System.nanoTime() - t1) / 1e6;
        long maxOver = 0; int within = 0; boolean never = true;
        double bound = cms.errorBound();
        for (Map.Entry<String, Long> en : exact.entrySet()) {
            long est = cms.estimate(en.getKey()), over = est - en.getValue();
            if (over < 0) never = false;
            maxOver = Math.max(maxOver, over);
            if (over <= bound) within++;
        }
        List<String> exTop = exact.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(10).map(Map.Entry::getKey).toList();
        HeavyHitters hh = new HeavyHitters(10, new CountMinSketch(eps, delta));
        for (int e = 0; e < events; e++) hh.update(names[key[e]], w[e]);
        Set<String> skTop = new HashSet<>();
        for (Map.Entry<String, Long> t : hh.top()) skTop.add(t.getKey());
        long exactBytes = 0;
        for (String k : exact.keySet()) exactBytes += 32 + 16 + 24 + ((16 + k.length() + 7) / 8) * 8L;
        return new SketchRow(events, exact.size(), eps, delta, cms.memoryBytes(), exactBytes, cmsMs, exactMs, cms.total(), bound, maxOver, 100.0 * within / exact.size(), never, skTop.equals(new HashSet<>(exTop)));
    }

    // ------------------------------------------------------------ interval partitioning
    private ScheduleRow scheduleBench(int n, long seed, int reps) {
        Random r = new Random(seed);
        List<IntervalPartition.Task> tasks = new ArrayList<>(n);
        for (int i = 0; i < n; i++) { long st = r.nextInt(1_000_000); tasks.add(new IntervalPartition.Task(i, st, st + 20 + r.nextInt(161))); }
        double[] h = new double[reps], l = new double[reps];
        IntervalPartition.Result res = null;
        int linRes = 0;
        for (int k = 0; k < reps; k++) {
            long t0 = System.nanoTime(); res = IntervalPartition.partition(tasks); h[k] = (System.nanoTime() - t0) / 1e6;
            long t1 = System.nanoTime(); linRes = firstFit(tasks); l[k] = (System.nanoTime() - t1) / 1e6;
        }
        return new ScheduleRow(n, res.resources(), res.depth(), res.resources() == res.depth() && linRes == res.depth(), median(h), median(l));
    }

    private static int firstFit(List<IntervalPartition.Task> tasks) {
        List<IntervalPartition.Task> order = new ArrayList<>(tasks);
        order.sort(Comparator.comparingLong(IntervalPartition.Task::start));
        long[] freeAt = new long[order.size()];
        int used = 0;
        for (IntervalPartition.Task t : order) {
            int pick = -1;
            for (int i = 0; i < used; i++) if (freeAt[i] <= t.start()) { pick = i; break; }
            if (pick < 0) pick = used++;
            freeAt[pick] = t.end();
        }
        return used;
    }

    private static int matches(int[][] f) { int c = 0; for (int[] r : f) for (int v : r) if (v > 0) c++; return c; }

    private BloomRow bloomBench(int n, long seed, int reps) {
        Random r = new Random(seed);
        Set<String> insertedSet = new LinkedHashSet<>();
        while (insertedSet.size() < n) insertedSet.add("BATCH-" + Long.toString(r.nextLong() & 0xffffffffffL, 36).toUpperCase());
        List<String> inserted = new ArrayList<>(insertedSet);
        List<String> negatives = new ArrayList<>(NEGATIVE_QUERIES);
        for (int i = 0; i < NEGATIVE_QUERIES; i++) {
            String id = "NEW-" + Long.toString(r.nextLong() & 0xffffffffffL, 36).toUpperCase();
            if (!insertedSet.contains(id)) negatives.add(id);
        }
        BloomFilter bf = new BloomFilter(n, 0.01);
        for (String s : inserted) bf.put(s);

        Set<String> exact = new HashSet<>(inserted);
        long exactBytes = exactSetBytes(inserted);

        int fn = 0, fp = 0;
        for (String s : inserted) if (!bf.mightContain(s)) fn++;
        for (String s : negatives) if (bf.mightContain(s)) fp++;
        for (int w = 0; w < 15; w++) { sink(queryAll(bf, inserted, negatives)); sink(queryAll(exact, inserted, negatives)); sink(twoStage(bf, exact, inserted, negatives)); }
        double[] bq = new double[reps], eq = new double[reps], ts = new double[reps];
        for (int k = 0; k < reps; k++) {
            long t0 = System.nanoTime(); sink(queryAll(bf, inserted, negatives)); bq[k] = (System.nanoTime() - t0) / 1e6;
            long t1 = System.nanoTime(); sink(queryAll(exact, inserted, negatives)); eq[k] = (System.nanoTime() - t1) / 1e6;
            long t2 = System.nanoTime(); sink(twoStage(bf, exact, inserted, negatives)); ts[k] = (System.nanoTime() - t2) / 1e6;
        }
        return new BloomRow(n, negatives.size(), bf.bitSize(), bf.hashCount(), bf.memoryBytes(), exactBytes, median(bq), median(eq), median(ts), fp,
                (double) fp / negatives.size(), bf.expectedFalsePositiveRate(), fn, negatives.size() - fp);
    }

    private static volatile int blackhole;
    private static void sink(int v) { blackhole += v; }

    private static int queryAll(BloomFilter bf, List<String> pos, List<String> neg) {
        int c = 0;
        for (String s : pos) if (bf.mightContain(s)) c++;
        for (String s : neg) if (bf.mightContain(s)) c++;
        return c;
    }

    private static int queryAll(Set<String> set, List<String> pos, List<String> neg) {
        int c = 0;
        for (String s : pos) if (set.contains(s)) c++;
        for (String s : neg) if (set.contains(s)) c++;
        return c;
    }

    private static int twoStage(BloomFilter bf, Set<String> exact, List<String> pos, List<String> neg) {
        int c = 0;
        for (String s : pos) if (bf.mightContain(s) && exact.contains(s)) c++;
        for (String s : neg) if (bf.mightContain(s) && exact.contains(s)) c++;
        return c;
    }

    /** Bytes allocated by this thread to build a pre-sized HashSet of fresh String copies (deterministic; ~retained size). */
    private static long exactSetBytes(List<String> ids) {
        try {
            com.sun.management.ThreadMXBean mx = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
            long tid = Thread.currentThread().getId();
            String[] copies = new String[ids.size()];
            for (int i = 0; i < copies.length; i++) copies[i] = new String(ids.get(i).getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
            long before = mx.getThreadAllocatedBytes(tid);
            Set<String> copy = new HashSet<>(Math.max(16, (int) (ids.size() / 0.75f) + 1));
            for (String c : copies) copy.add(c);
            long after = mx.getThreadAllocatedBytes(tid);
            sink(copy.size());
            // add the String + byte[] objects themselves (header 24 + array header 16 + payload, 8-byte aligned)
            long strings = 0;
            for (String c : copies) strings += 24 + ((16 + c.length() + 7) / 8) * 8L;
            return (after - before) + strings;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static long usedHeap() {
        System.gc(); System.gc();
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static double median(double[] a) {
        double[] c = a.clone();
        Arrays.sort(c);
        return c.length % 2 == 1 ? c[c.length / 2] : (c[c.length / 2 - 1] + c[c.length / 2]) / 2;
    }
}
