package com.secondserve.service;

import com.secondserve.algorithm.AllocationFlowModel;
import com.secondserve.algorithm.BloomFilter;
import com.secondserve.algorithm.EdmondsKarp;
import com.secondserve.algorithm.FcfsAllocator;
import com.secondserve.algorithm.FairAllocator;
import com.secondserve.algorithm.FlowNetwork;
import com.secondserve.algorithm.IntervalPartition;
import com.secondserve.algorithm.MinCostAllocator;
import com.secondserve.dto.Dtos.*;
import com.secondserve.exception.NotFoundException;
import com.secondserve.exception.ValidationException;
import com.secondserve.model.Donation;
import com.secondserve.model.Ngo;
import com.secondserve.repository.DataVersion;
import com.secondserve.repository.DonationRepository;
import com.secondserve.repository.NgoRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Orchestrates one allocation run: validate -> screen IDs -> feasibility -> flow network -> Edmonds-Karp -> results. */
public final class AllocationService {
    private static final int MAX_TRACE_STEPS = 200;

    private final DonationRepository donations;
    private final NgoRepository ngos;
    private final FeasibilityService feasibility;
    private final DataVersion version;
    private long runCounter;
    private AllocationResult last;
    private NetworkAnalysis lastAnalysis;
    private long lastVersion = -1;
    private Commitments commitments = new Commitments() {};

    public static final int DEFAULT_VEHICLE_CAPACITY = 100, HANDOVER_MINUTES = 10, TIGHT_SLACK_MINUTES = 30;

    /** Meals already promised in a published dispatch plan; they are removed from the next planning round. */
    public interface Commitments {
        default int forBatch(String batchId) { return 0; }
        default int forNgo(String ngoId) { return 0; }
    }

    public record Inputs(List<Donation> donations, List<Ngo> ngos) {}

    public AllocationService(DonationRepository d, NgoRepository n, FeasibilityService f, DataVersion v) {
        donations = d; ngos = n; feasibility = f; version = v;
    }

    public void setCommitments(Commitments c) { this.commitments = c == null ? new Commitments() {} : c; }

    /**
     * What the next allocation may use: stored batches/NGOs minus meals already committed in a dispatch plan
     * (rolling-horizon planning). Entities that are completely used up are left out.
     */
    public Inputs inputs() {
        List<Donation> ds = new ArrayList<>();
        for (Donation d : donations.findAll()) {
            int c = commitments.forBatch(d.batchId());
            if (c == 0) ds.add(d);
            else if (d.meals() - c > 0) ds.add(new Donation(d.batchId(), d.donorName(), d.meals() - c, d.availableFrom(), d.safeUntil(), d.notes()));
        }
        List<Ngo> ns = new ArrayList<>();
        for (Ngo n : ngos.findAll()) {
            int c = commitments.forNgo(n.ngoId());
            if (c == 0) ns.add(n);
            else {
                Ngo r = new Ngo(n.ngoId(), n.name(), n.requiredMeals() - c, n.pickupCapacity() - c, n.maxTravelMinutes(), n.contact());
                if (r.effectiveCapacity() > 0) ns.add(r);
            }
        }
        return new Inputs(ds, ns);
    }

    /** Entry point for REST bodies: {"planningTime": "2026-11-17T14:30", "strategy": "MIN_COST", "vehicleCapacity": 100}. */
    public AllocationResult runRequest(Map<String, Object> body) {
        Map<String, Object> m = body == null ? new HashMap<>() : body;
        com.secondserve.util.Fields f = new com.secondserve.util.Fields(m);
        LocalDateTime t = f.time("planningTime", "Planning date and time");
        Strategy st = Strategy.MAX_FLOW;
        try { st = Strategy.parse(m.get("strategy") == null ? null : String.valueOf(m.get("strategy"))); } catch (ValidationException e) { f.error(e.getMessage()); }
        int vc = DEFAULT_VEHICLE_CAPACITY;
        if (m.get("vehicleCapacity") != null && !String.valueOf(m.get("vehicleCapacity")).isBlank()) {
            Integer v = f.integer("vehicleCapacity", "Meals per volunteer trip", 10, 10_000);
            if (v != null) vc = v;
        }
        f.throwIfInvalid();
        return run(t, st, vc);
    }

    public synchronized AllocationResult run(LocalDateTime planning) { return run(planning, Strategy.MAX_FLOW, DEFAULT_VEHICLE_CAPACITY); }

    public synchronized AllocationResult run(LocalDateTime planning, Strategy strategy, int vehicleCapacity) {
        // 1. validate the entire dataset
        List<String> errors = new ArrayList<>();
        if (planning == null) errors.add("Planning date and time is required");
        Inputs in = inputs();
        List<Donation> ds = in.donations();
        List<Ngo> ns = in.ngos();
        if (ds.isEmpty()) errors.add("No donation batches are registered. Add at least one donation before running the allocation.");
        if (ns.isEmpty()) errors.add("No recipient NGOs are registered. Add at least one NGO before running the allocation.");
        for (Donation d : ds) {
            if (d.meals() <= 0) errors.add("Batch " + d.batchId() + " has no meal packs");
            if (!d.safeUntil().isAfter(d.availableFrom())) errors.add("Batch " + d.batchId() + " has a deadline before its availability time");
        }
        for (Ngo n : ns) if (n.effectiveCapacity() <= 0) errors.add("NGO " + n.ngoId() + " has zero demand or pickup capacity");
        if (!errors.isEmpty()) throw new ValidationException("The dataset cannot be allocated yet", errors);

        // 2. duplicate-ID check with a fresh Bloom filter + exact verification
        BloomFilter bf = new BloomFilter(Math.max(100, ds.size() * 2), 0.01);
        Set<String> exact = new HashSet<>();
        int dup = 0, fp = 0;
        for (Donation d : ds) {
            String id = d.batchId();
            if (bf.mightContain(id)) { if (exact.contains(id)) dup++; else fp++; }
            bf.put(id); exact.add(id);
        }
        if (dup > 0) throw new ValidationException("Duplicate donation batch IDs found: " + dup);

        // 3. feasibility (expired batches are removed: supply 0, no edges)
        FeasibilityService.Evaluation ev = feasibility.evaluate(ds, ns, planning);
        int B = ds.size(), N = ns.size();
        int[] supply = ev.supply();
        int[] cap = ev.capacity();
        boolean[][] ok = ev.matrix();

        // 4-5. construct network and run the custom Edmonds-Karp
        AllocationFlowModel model = AllocationFlowModel.build(supply, cap, ok);
        long t0 = System.nanoTime();
        EdmondsKarp.Result r = EdmondsKarp.solve(model.network, model.source, model.sink, true);
        double engineMs = (System.nanoTime() - t0) / 1e6;
        int[][] flowEk = model.extractFlows();
        String bad = FcfsAllocator.validate(supply, cap, ok, flowEk);
        if (bad != null) throw new IllegalStateException("Internal error: invalid allocation (" + bad + ")");

        // 4b. cost model for the smart strategy (all integers, minutes)
        LocalDateTime[] depart = new LocalDateTime[B];
        int[][] travelM = new int[B][N], slack = new int[B][N], midCost = new int[B][N];
        int[] minsLeft = new int[B], srcCost = new int[B];
        for (int i = 0; i < B; i++) {
            Donation d = ds.get(i);
            depart[i] = planning.isAfter(d.availableFrom()) ? planning : d.availableFrom();
            minsLeft[i] = (int) Math.max(0, Duration.between(planning, d.safeUntil()).toMinutes());
            srcCost[i] = Math.min(minsLeft[i], 720) / 15;              // urgent batches are cheaper to drain
            for (int j = 0; j < N; j++) if (ok[i][j]) {
                int tm = ev.pairs[i][j].travelMinutes();
                travelM[i][j] = tm;
                slack[i][j] = (int) Duration.between(depart[i].plusMinutes(tm), d.safeUntil()).toMinutes();
                midCost[i][j] = tm + 2 * Math.max(0, TIGHT_SLACK_MINUTES - slack[i][j]); // travel + safety-margin penalty
            }
        }
        long m0 = System.nanoTime();
        MinCostAllocator.Result mc = MinCostAllocator.solve(supply, cap, ok, srcCost, midCost);
        double mcMs = (System.nanoTime() - m0) / 1e6;
        long f0 = System.nanoTime();
        FairAllocator.Result fair = FairAllocator.solve(supply, cap, ok);
        double fairMs = (System.nanoTime() - f0) / 1e6;
        if (mc.total() != r.maxFlow() || fair.total() != r.maxFlow()) throw new IllegalStateException("Internal error: strategies disagree on the maximum number of meals");
        for (int[][] x : List.of(mc.flow(), fair.flow())) {
            String b2 = FcfsAllocator.validate(supply, cap, ok, x);
            if (b2 != null) throw new IllegalStateException("Internal error: invalid allocation (" + b2 + ")");
        }
        int[][] flow = switch (strategy) { case MAX_FLOW -> flowEk; case MIN_COST -> mc.flow(); case FAIR -> fair.flow(); };

        // baseline on identical constraints
        long b0 = System.nanoTime();
        int[][] greedy = FcfsAllocator.allocate(supply, cap, ok);
        double greedyMs = (System.nanoTime() - b0) / 1e6;
        int greedyTotal = FcfsAllocator.total(greedy);

        // 6-7. outcomes
        int totalSupply = 0, usable = 0, totalDemand = 0, effDemand = 0, expiredCount = 0;
        for (int i = 0; i < B; i++) { totalSupply += ds.get(i).meals(); usable += supply[i]; if (ev.expired[i]) expiredCount++; }
        for (Ngo n : ns) { totalDemand += n.requiredMeals(); effDemand += n.effectiveCapacity(); }
        int allocated = r.maxFlow();

        int[] bAlloc = new int[B], nAlloc = new int[N];
        for (int i = 0; i < B; i++) for (int j = 0; j < N; j++) { bAlloc[i] += flow[i][j]; nAlloc[j] += flow[i][j]; }

        List<BatchOutcome> bOut = new ArrayList<>();
        List<Row> rows = new ArrayList<>();
        for (int i = 0; i < B; i++) {
            Donation d = ds.get(i);
            int rem = d.meals() - bAlloc[i];
            String status = ev.expired[i] ? "EXPIRED" : rem == 0 ? "FULLY_ALLOCATED" : bAlloc[i] > 0 ? "PARTIALLY_ALLOCATED" : "UNALLOCATED";
            String reason = rem == 0 ? "" : batchReason(i, ev, bAlloc[i]);
            bOut.add(new BatchOutcome(d.batchId(), d.donorName(), d.meals(), bAlloc[i], rem, status, reason, Duration.between(planning, d.safeUntil()).toMinutes()));
            for (int j = 0; j < N; j++) if (flow[i][j] > 0) rows.add(new Row(d.batchId(), d.donorName(), ns.get(j).ngoId(), ns.get(j).name(), flow[i][j], "ALLOCATED", ""));
            if (rem > 0) rows.add(new Row(d.batchId(), d.donorName(), "-", "-", rem, ev.expired[i] ? "EXPIRED" : "UNALLOCATED", reason));
        }
        List<NgoOutcome> nOut = new ArrayList<>();
        for (int j = 0; j < N; j++) {
            Ngo n = ns.get(j);
            int unmet = n.requiredMeals() - nAlloc[j];
            boolean anyFeasible = false;
            for (int i = 0; i < B; i++) anyFeasible |= ok[i][j];
            String status, reason = "";
            if (unmet == 0) status = "DEMAND_MET";
            else if (nAlloc[j] == n.pickupCapacity() && n.pickupCapacity() < n.requiredMeals()) {
                status = "AT_PICKUP_LIMIT";
                reason = "Pickup capacity of " + n.pickupCapacity() + " meals is reached; the remaining " + unmet + " cannot be collected in this window.";
            } else if (!anyFeasible) {
                status = "NOT_SERVED";
                reason = "No donation batch is feasible for this NGO (see Travel & Feasibility for the reasons).";
            } else {
                status = nAlloc[j] > 0 ? "PARTIALLY_SERVED" : "NOT_SERVED";
                reason = "All donor supply that can reach this NGO in time has already been allocated (supply-limited).";
            }
            nOut.add(new NgoOutcome(n.ngoId(), n.name(), n.requiredMeals(), n.pickupCapacity(), n.effectiveCapacity(), nAlloc[j], unmet,
                    n.pickupCapacity() == 0 ? 0 : (double) nAlloc[j] / n.pickupCapacity(), status, reason));
        }

        // bottleneck from the minimum cut
        Set<Integer> cutSet = new HashSet<>(r.cutArcs());
        List<CutEdge> cutEdges = new ArrayList<>();
        for (int arc : r.cutArcs()) {
            boolean found = false;
            for (int i = 0; i < B && !found; i++) if (model.sourceArc[i] == arc) {
                cutEdges.add(new CutEdge("SUPPLY", ds.get(i).batchId(), ds.get(i).donorName() + " (" + ds.get(i).batchId() + ") - all " + supply[i] + " meals are allocated", supply[i]));
                found = true;
            }
            for (int j = 0; j < N && !found; j++) if (model.sinkArc[j] == arc) {
                cutEdges.add(new CutEdge("NGO_CAPACITY", ns.get(j).ngoId(), ns.get(j).name() + " (" + ns.get(j).ngoId() + ") - intake limit of " + cap[j] + " meals is reached", cap[j]));
                found = true;
            }
            if (!found) cutEdges.add(new CutEdge("ROUTE", String.valueOf(arc), "Route edge", model.network.original(arc)));
        }
        String expl;
        if (cutEdges.isEmpty()) expl = allocated == 0
                ? "Maximum flow is 0: no donation batch has a feasible route to any NGO, so there is no capacity bottleneck, only feasibility constraints."
                : "No saturated edge separates source and sink.";
        else expl = "The minimum cut has capacity " + r.cutCapacity() + " = the maximum flow of " + allocated + " meals. Total allocation can only grow if capacity on at least one of these saturated edges grows (or a new feasible route is added). This is the min-cut closest to the source found from the final residual graph; other minimum cuts of equal capacity may exist.";
        Bottleneck bottleneck = new Bottleneck(r.cutCapacity(), cutEdges, expl);

        // graph for the UI
        List<GNode> gn = new ArrayList<>();
        List<GEdge> ge = new ArrayList<>();
        gn.add(new GNode("S", "Source", "", "source", "OK"));
        for (int i = 0; i < B; i++) gn.add(new GNode("B:" + ds.get(i).batchId(), ds.get(i).batchId(), ds.get(i).donorName() + " - " + ds.get(i).meals() + " meals", "batch", ev.expired[i] ? "EXPIRED" : "OK"));
        for (int j = 0; j < N; j++) gn.add(new GNode("N:" + ns.get(j).ngoId(), ns.get(j).ngoId(), ns.get(j).name() + " - cap " + cap[j], "ngo", "OK"));
        gn.add(new GNode("T", "Sink", "", "sink", "OK"));
        for (int i = 0; i < B; i++) {
            int f = bAlloc[i];
            ge.add(new GEdge("S", "B:" + ds.get(i).batchId(), supply[i], f, cutSet.contains(model.sourceArc[i]) ? "CUT" : f > 0 ? "FLOW" : "FEASIBLE", ""));
        }
        for (int i = 0; i < B; i++) for (int j = 0; j < N; j++) {
            FeasibilityService.Pair p = ev.pairs[i][j];
            ge.add(new GEdge("B:" + ds.get(i).batchId(), "N:" + ns.get(j).ngoId(), null, flow[i][j], !p.feasible() ? "UNAVAILABLE" : flow[i][j] > 0 ? "FLOW" : "FEASIBLE", p.reason()));
        }
        for (int j = 0; j < N; j++) {
            int f = nAlloc[j];
            ge.add(new GEdge("N:" + ns.get(j).ngoId(), "T", cap[j], f, cutSet.contains(model.sinkArc[j]) ? "CUT" : f > 0 ? "FLOW" : "FEASIBLE", ""));
        }

        int denom = Math.min(usable, effDemand);
        Summary sum = new Summary(totalSupply, usable, totalDemand, effDemand, allocated, totalSupply - allocated, totalDemand - allocated,
                denom == 0 ? 0 : (double) allocated / denom, usable == 0 ? 0 : (double) allocated / usable, effDemand == 0 ? 0 : (double) allocated / effDemand,
                r.maxFlow(), r.cutCapacity(), r.augmentations(), r.bfsRuns(), engineMs);
        ValidationSummary vs = new ValidationSummary(B, N, ds.size(), dup, fp, expiredCount, ev.feasibleCount(), B * N);
        Baseline base = new Baseline("First-Come-First-Served greedy", greedyTotal, greedyMs, allocated - greedyTotal,
                allocated > greedyTotal ? "Greedy committed supply early and blocked " + (allocated - greedyTotal) + " meals that flow rerouting could place."
                        : "On this dataset greedy reached the same total; the advantage of maximum flow only appears when early choices block later ones.");

        // lab trace
        List<String> nodeNames = new ArrayList<>();
        nodeNames.add("Source");
        for (Donation d : ds) nodeNames.add(d.batchId());
        for (Ngo n : ns) nodeNames.add(n.ngoId());
        nodeNames.add("Sink");
        FlowNetwork net = model.network;
        List<LabArc> arcs = new ArrayList<>();
        for (int a = 0; a < net.arcCount(); a += 2)
            arcs.add(new LabArc(a / 2, nodeNames.get(net.from(a)), nodeNames.get(net.to(a)), net.original(a) >= FlowNetwork.INFINITE ? null : net.original(a), net.flow(a)));
        List<LabStep> steps = new ArrayList<>();
        for (EdmondsKarp.Step s : r.steps()) {
            if (steps.size() >= MAX_TRACE_STEPS) break;
            List<String> path = new ArrayList<>();
            for (int v : s.pathNodes()) path.add(nodeNames.get(v));
            List<Integer> pa = new ArrayList<>();
            List<Boolean> rev = new ArrayList<>();
            for (int a : s.pathArcs()) { pa.add(a / 2); rev.add((a & 1) == 1); }
            steps.add(new LabStep(s.number(), path, pa, rev, s.bottleneck(), s.totalFlowAfter(), s.flowsAfter()));
        }
        List<String> side = new ArrayList<>();
        for (int v = 0; v < net.nodeCount(); v++) if (r.sourceSide()[v]) side.add(nodeNames.get(v));
        List<Integer> cutIdx = new ArrayList<>();
        for (int a : r.cutArcs()) cutIdx.add(a / 2);
        Map<String, String> cx = new LinkedHashMap<>();
        cx.put("Time", "O(V E^2) worst case: each BFS is O(E) and there are O(V E) augmentations");
        cx.put("Space", "O(V + E) residual graph, BFS parent array and queue");
        cx.put("This instance", "V = " + net.nodeCount() + ", E = " + net.edgeCount() + ", " + r.augmentations() + " augmentations, " + r.bfsRuns() + " BFS runs");

        // strategy comparison, per-NGO table and volunteer schedule
        List<StrategyMetric> comparison = List.of(
                metric("FCFS", "First-come-first-served (baseline)", greedy, greedyMs, supply, cap, ok, travelM, slack, minsLeft, "Greedy; never reconsiders a choice."),
                metric("MAX_FLOW", Strategy.MAX_FLOW.label, flowEk, engineMs, supply, cap, ok, travelM, slack, minsLeft, "Maximum number of meals; arbitrary among ties."),
                metric("MIN_COST", Strategy.MIN_COST.label, mc.flow(), mcMs, supply, cap, ok, travelM, slack, minsLeft, mc.augmentations() + " cheapest-path augmentations (SPFA); total cost " + mc.cost() + "."),
                metric("FAIR", Strategy.FAIR.label, fair.flow(), fairMs, supply, cap, ok, travelM, slack, minsLeft, fair.rounds().size() + " filling rounds, " + fair.maxFlowCalls() + " max-flow calls; price of fairness 0 meals."));
        List<NgoStrategyRow> perNgo = new ArrayList<>();
        int[] rf = receipts(greedy, N), re = receipts(flowEk, N), rm = receipts(mc.flow(), N), rr = receipts(fair.flow(), N);
        for (int j = 0; j < N; j++) perNgo.add(new NgoStrategyRow(ns.get(j).ngoId(), ns.get(j).name(), cap[j], rf[j], re[j], rm[j], rr[j]));
        Schedule schedule = buildSchedule(flow, ds, ns, depart, travelM, vehicleCapacity);

        runCounter++;
        last = new AllocationResult(runCounter, LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), planning.toString(),
                "Edmonds-Karp maximum flow (BFS augmenting paths, residual graph)", false, vs, sum, rows, bOut, nOut, bottleneck, new Graph(gn, ge), base,
                strategy.name(), strategy.label + ". " + strategy.description, comparison, perNgo, schedule, false);
        lastAnalysis = new NetworkAnalysis(runCounter, planning.toString(), r.maxFlow(), r.augmentations(), r.bfsRuns(), net.nodeCount(), net.edgeCount(),
                nodeNames, arcs, steps, r.steps().size() > steps.size(), side, cutIdx, r.cutCapacity(), cx);
        lastVersion = version.get();
        return last;
    }

    private String batchReason(int i, FeasibilityService.Evaluation ev, int allocated) {
        if (ev.expired[i]) return ev.pairs[i].length == 0 ? "Expired." : "Expired: safe delivery deadline " + ev.donations.get(i).safeUntil().toString().replace("T", " ") + " is not after the planning time; the batch was removed from the network.";
        Map<String, Integer> counts = new LinkedHashMap<>();
        int feasible = 0;
        for (FeasibilityService.Pair p : ev.pairs[i]) { if (p.feasible()) feasible++; else counts.merge(label(p.code()), 1, Integer::sum); }
        if (feasible == 0) {
            StringBuilder sb = new StringBuilder("No feasible NGO: ");
            int k = 0;
            for (Map.Entry<String, Integer> e : counts.entrySet()) { if (k++ > 0) sb.append("; "); sb.append(e.getValue()).append(" x ").append(e.getKey()); }
            return sb.append(".").toString();
        }
        return "Every NGO reachable in time is already at its intake capacity (NGO-side bottleneck); " + feasible + " feasible route(s) had no spare capacity.";
    }

    private static int[] receipts(int[][] flow, int n) {
        int[] r = new int[n];
        for (int[] row : flow) for (int j = 0; j < n; j++) r[j] += row[j];
        return r;
    }

    private static StrategyMetric metric(String key, String name, int[][] flow, double millis, int[] supply, int[] cap, boolean[][] ok,
                                         int[][] travel, int[][] slack, int[] minsLeft, String note) {
        int B = supply.length, N = cap.length;
        int total = 0, tight = 0;
        long mealMinutes = 0;
        int[] rec = new int[N];
        int[] used = new int[B];
        for (int i = 0; i < B; i++) for (int j = 0; j < N; j++) {
            int q = flow[i][j];
            if (q == 0) continue;
            total += q; used[i] += q; rec[j] += q;
            mealMinutes += (long) q * travel[i][j];
            if (slack[i][j] < TIGHT_SLACK_MINUTES) tight += q;
        }
        long leftover = 0, leftoverMinutes = 0;
        for (int i = 0; i < B; i++) if (supply[i] > 0) { int left = supply[i] - used[i]; leftover += left; leftoverMinutes += (long) left * minsLeft[i]; }
        List<Double> ratios = new ArrayList<>();
        int minRec = Integer.MAX_VALUE, served = 0;
        for (int j = 0; j < N; j++) {
            boolean reachable = false;
            for (int i = 0; i < B; i++) reachable |= ok[i][j] && supply[i] > 0;
            if (!reachable) continue;
            ratios.add(cap[j] == 0 ? 1.0 : (double) rec[j] / cap[j]);
            minRec = Math.min(minRec, rec[j]);
            if (rec[j] > 0) served++;
        }
        double[] rr = ratios.stream().mapToDouble(Double::doubleValue).toArray();
        return new StrategyMetric(key, name, total, millis, mealMinutes, total == 0 ? 0 : (double) mealMinutes / total, tight,
                leftover == 0 ? null : (double) leftoverMinutes / leftover, (int) leftover, FairAllocator.jain(rr),
                minRec == Integer.MAX_VALUE ? 0 : minRec, served, rr.length, note);
    }

    /**
     * Volunteer plan by greedy interval partitioning. Each batch->NGO flow is split into trips of at most vehicleCapacity meals;
     * a trip occupies a volunteer for depart .. depart + 2*travel + hand-over. The minimum number of volunteers equals the
     * maximum number of simultaneous trips (the depth), which the greedy algorithm provably achieves.
     */
    private static Schedule buildSchedule(int[][] flow, List<Donation> ds, List<Ngo> ns, LocalDateTime[] depart, int[][] travel, int vehicleCapacity) {
        record Raw(int b, int n, int meals, long start, long end) {}
        List<Raw> raw = new ArrayList<>();
        for (int i = 0; i < ds.size(); i++) for (int j = 0; j < ns.size(); j++) {
            int q = flow[i][j];
            if (q <= 0) continue;
            int chunks = (q + vehicleCapacity - 1) / vehicleCapacity;
            long start = depart[i].toEpochSecond(java.time.ZoneOffset.UTC) / 60;
            long end = start + 2L * travel[i][j] + HANDOVER_MINUTES;
            for (int c = 0; c < chunks; c++) raw.add(new Raw(i, j, q / chunks + (c < q % chunks ? 1 : 0), start, end));
        }
        List<IntervalPartition.Task> tasks = new ArrayList<>();
        for (int k = 0; k < raw.size(); k++) tasks.add(new IntervalPartition.Task(k, raw.get(k).start(), raw.get(k).end()));
        IntervalPartition.Result part = IntervalPartition.partition(tasks);
        List<Trip> trips = new ArrayList<>();
        for (int k = 0; k < raw.size(); k++) {
            Raw t = raw.get(k);
            LocalDateTime st = LocalDateTime.ofEpochSecond(t.start() * 60, 0, java.time.ZoneOffset.UTC);
            int tm = travel[t.b()][t.n()];
            trips.add(new Trip(k + 1, "V" + (part.resourceOfTask()[k] + 1), ds.get(t.b()).batchId(), ds.get(t.b()).donorName(), ns.get(t.n()).ngoId(),
                    ns.get(t.n()).name(), t.meals(), st.toString(), st.plusMinutes(tm).toString(), st.plusMinutes(2L * tm + HANDOVER_MINUTES).toString(), tm));
        }
        trips.sort(Comparator.comparing(Trip::volunteer, Comparator.comparingInt((String v) -> Integer.parseInt(v.substring(1)))).thenComparing(Trip::departAt));
        String note = trips.isEmpty() ? "Nothing to deliver, so no volunteers are needed."
                : "Interval partitioning (greedy, min-heap of volunteer free times) uses " + part.resources() + " volunteer(s); at most " + part.depth()
                + " trips overlap, so no schedule can use fewer. Trips carry at most " + vehicleCapacity + " meals and include " + HANDOVER_MINUTES + " min hand-over time.";
        return new Schedule(vehicleCapacity, HANDOVER_MINUTES, part.resources(), part.depth(), trips.size(), part.resources() == part.depth(), trips, note);
    }

    private static String label(String code) {
        return switch (code) {
            case "NO_TRAVEL_TIME" -> "no travel time entered";
            case "NGO_TRAVEL_LIMIT" -> "travel time above NGO limit";
            case "ARRIVAL_AFTER_DEADLINE" -> "arrival after deadline";
            case "EXPIRED" -> "expired";
            default -> code.toLowerCase();
        };
    }

    public synchronized AllocationResult results() {
        if (last == null) throw new NotFoundException("No allocation has been run yet. Run the allocation first.");
        return last.committed() ? last : last.withStale(lastVersion != version.get());
    }

    public synchronized boolean hasRun() { return last != null; }
    public synchronized AllocationResult lastOrNull() { return last == null ? null : last.committed() ? last : last.withStale(lastVersion != version.get()); }

    /** Called by the dispatch service once the plan has been published as deliveries; the result stops being "stale" because the data changes were the commit itself. */
    public synchronized void markCommitted(long runId) {
        if (last != null && last.runId() == runId) { last = last.withCommitted(true); lastVersion = version.get(); }
    }

    public synchronized NetworkAnalysis analysis() {
        if (lastAnalysis == null) throw new NotFoundException("No allocation has been run yet. Run the allocation first.");
        return lastAnalysis;
    }

    public synchronized void clear() { last = null; lastAnalysis = null; }
}
