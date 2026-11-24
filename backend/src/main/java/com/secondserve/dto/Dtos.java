package com.secondserve.dto;

import java.util.List;
import java.util.Map;

/** Response DTOs (records are serialised to JSON as-is). */
public final class Dtos {
    private Dtos() {}

    public record ValidationSummary(int donations, int ngos, int idsScreened, int duplicateIdsFound, int bloomFalsePositivesInCheck,
                                    int expiredBatches, int feasibleConnections, int totalConnections) {}

    public record Summary(int totalSupply, int usableSupply, int totalDemand, int effectiveDemand, int allocated, int remainingSurplus,
                          int remainingDemand, double efficiency, double supplyUtilisation, double demandCoverage, int maxFlow,
                          long minCutCapacity, int augmentations, int bfsRuns, double engineMillis) {}

    public record Row(String batchId, String donor, String ngoId, String ngoName, int meals, String status, String reason) {}

    public record BatchOutcome(String batchId, String donor, int supply, int allocated, int remaining, String status, String reason,
                               long minutesToDeadline) {}

    public record NgoOutcome(String ngoId, String name, int requiredMeals, int pickupCapacity, int effectiveCapacity, int allocated,
                             int unmet, double pickupUtilisation, String status, String reason) {}

    public record CutEdge(String kind, String id, String label, int capacity) {}

    public record Bottleneck(long cutCapacity, List<CutEdge> edges, String explanation) {}

    public record GNode(String id, String label, String sub, String type, String status) {}

    public record GEdge(String from, String to, Integer capacity, int flow, String state, String reason) {}

    public record Graph(List<GNode> nodes, List<GEdge> edges) {}

    public record Baseline(String name, int allocated, double millis, int gapMeals, String note) {}

    /** Quality metrics of one allocation strategy on the SAME instance (all strategies place the same number of meals except FCFS). */
    public record StrategyMetric(String key, String name, int allocated, double millis, long mealMinutes, double avgTravelMinutes,
                                 int tightMeals, Double avgLeftoverMinutesToExpiry, int leftoverMeals, double jain, int minReceipt,
                                 int ngosServed, int reachableNgos, String note) {}

    public record NgoStrategyRow(String ngoId, String name, int effectiveCapacity, int fcfs, int maxFlow, int minCost, int fair) {}

    /** One volunteer trip: round trip donor -> NGO -> back, plus a hand-over buffer. Large flows are split by vehicle capacity. */
    public record Trip(int tripId, String volunteer, String batchId, String donor, String ngoId, String ngoName, int meals,
                       String departAt, String arriveAt, String freeAt, int travelMinutes) {}

    public record Schedule(int vehicleCapacity, int handoverMinutes, int volunteersNeeded, int peakConcurrent, int tripCount,
                           boolean optimal, List<Trip> trips, String note) {}

    public record AllocationResult(long runId, String executedAt, String planningTime, String algorithm, boolean stale,
                                   ValidationSummary validation, Summary summary, List<Row> rows, List<BatchOutcome> batches,
                                   List<NgoOutcome> ngos, Bottleneck bottleneck, Graph graph, Baseline baseline,
                                   String strategy, String strategyNote, List<StrategyMetric> comparison, List<NgoStrategyRow> perNgo,
                                   Schedule schedule, boolean committed) {
        public AllocationResult withStale(boolean s) {
            return new AllocationResult(runId, executedAt, planningTime, algorithm, s, validation, summary, rows, batches, ngos,
                    bottleneck, graph, baseline, strategy, strategyNote, comparison, perNgo, schedule, committed);
        }
        public AllocationResult withCommitted(boolean c) {
            return new AllocationResult(runId, executedAt, planningTime, algorithm, false, validation, summary, rows, batches, ngos,
                    bottleneck, graph, baseline, strategy, strategyNote, comparison, perNgo, schedule, c);
        }
    }

    // ---- Algorithm Lab: Edmonds-Karp trace ----
    public record LabArc(int index, String from, String to, Integer capacity, int finalFlow) {}

    public record LabStep(int number, List<String> path, List<Integer> pathArcs, List<Boolean> reverse, int bottleneck,
                          int totalFlow, int[] flows) {}

    public record NetworkAnalysis(long runId, String planningTime, int maxFlow, int augmentations, int bfsRuns, int nodeCount,
                                  int edgeCount, List<String> nodes, List<LabArc> arcs, List<LabStep> steps, boolean truncated,
                                  List<String> sourceSide, List<Integer> cutArcs, long cutCapacity, Map<String, String> complexity) {}

    // ---- Bloom filter ----
    public record BloomStats(int bitSize, int hashCount, int expectedInsertions, double targetFpp, int insertions, int setBits,
                             double fillRatio, long memoryBytes, double expectedFppNow, double fillBasedFpp, int screened,
                             int definitelyNew, int possiblyPresent, int exactLookups, int exactLookupsAvoided,
                             int confirmedDuplicates, int falsePositives, double observedFpRate, List<BloomLogEntry> recent,
                             int[] setBitIndexes, List<BloomItem> items) {}

    public record BloomLogEntry(String id, boolean bloomPossiblyPresent, boolean exactChecked, boolean exactPresent, String outcome) {}

    public record BloomItem(String id, int[] positions) {}

    public record BloomCheck(String id, boolean bloomPossiblyPresent, int[] positions, boolean exactChecked, boolean exactPresent,
                             String outcome, String explanation) {}

    public record BloomDemo(int expectedInsertions, double targetFpp, int inserted, int queries, int bitSize, int hashCount,
                            int setBits, int possiblyPresent, int falsePositives, int confirmedDuplicates, double observedFpRate,
                            double theoreticalFpRate, List<String> sampleFalsePositives, int[] setBitIndexes, long memoryBytes) {}

    // ---- dashboard ----
    public record BatchPoint(String batchId, String donor, int meals, int allocated, long minutesToDeadline) {}
    public record NgoPoint(String ngoId, String name, int demand, int pickup, int received) {}

    public record Dashboard(int totalMeals, int expiredMeals, int totalDemand, int effectiveDemand, Integer allocated, Integer unallocated,
                            int donors, int batches, int ngos, int approachingDeadline, int approachingWindowMinutes,
                            int duplicatesDetected, int falsePositivesObserved, boolean hasRun, boolean stale,
                            String lastRunPlanningTime, String serverTime, String suggestedPlanningTime,
                            List<BatchPoint> batchChart, List<NgoPoint> ngoChart, BloomStats bloom) {}
}
