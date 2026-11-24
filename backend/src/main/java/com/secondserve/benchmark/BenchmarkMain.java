package com.secondserve.benchmark;

import com.secondserve.service.BenchmarkService;
import com.secondserve.service.BenchmarkService.*;
import com.secondserve.util.MiniJson;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** CLI: runs the real benchmarks and saves JSON + CSV + Markdown. Usage: BenchmarkMain [outputDir] [seed] [repetitions] */
public final class BenchmarkMain {
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "../docs");
        long seed = args.length > 1 ? Long.parseLong(args[1]) : 2026;
        int reps = args.length > 2 ? Integer.parseInt(args[2]) : 7;
        Files.createDirectories(out);
        Report r = new BenchmarkService().run(seed, reps);
        Files.writeString(out.resolve("benchmark-results.json"), MiniJson.write(r));

        StringBuilder csv = new StringBuilder("experiment,size,ek_ms,greedy_ms,ek_meals,greedy_meals,gap_meals,gap_pct,augmentations,routes\n");
        StringBuilder md = new StringBuilder();
        md.append("# Benchmark results (measured)\n\n");
        md.append(String.format("Executed %s on Java %s, %d CPU(s), seed %d, median of %d runs.\n\n", r.executedAt(), r.javaVersion(), r.availableProcessors(), r.seed(), r.repetitions()));
        md.append("## Edmonds-Karp vs First-Come-First-Served\n\n| Donors x NGOs | Feasible routes | Supply | EK meals | FCFS meals | Gap (meals) | Gap % | EK ms | FCFS ms | Augmentations | Valid |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (FlowRow f : r.flow()) {
            md.append(String.format(Locale.ROOT, "| %dx%d | %d | %d | %d | %d | %d | %.1f%% | %.4f | %.4f | %d | %s |\n", f.donors(), f.ngos(), f.feasibleRoutes(), f.totalSupply(), f.edmondsKarpAllocated(),
                    f.greedyAllocated(), f.gapMeals(), f.gapPercent(), f.edmondsKarpMillis(), f.greedyMillis(), f.augmentations(), f.validatedAgainstConstraints()));
            csv.append(String.format(Locale.ROOT, "flow,%d,%.5f,%.5f,%d,%d,%d,%.3f,%d,%d\n", f.donors(), f.edmondsKarpMillis(), f.greedyMillis(), f.edmondsKarpAllocated(), f.greedyAllocated(), f.gapMeals(), f.gapPercent(), f.augmentations(), f.feasibleRoutes()));
        }
        md.append("\n## Bloom filter vs exact HashSet membership\n\n| Identifiers | Neg. queries | Bits (m) | k | Bloom bytes | HashSet bytes (approx) | Bloom ms | HashSet ms | Bloom+exact ms | False positives | Observed FP rate | Theoretical FP rate | False negatives | Exact lookups avoided |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (BloomRow b : r.bloom())
            md.append(String.format(Locale.ROOT, "| %d | %d | %d | %d | %d | %d | %.4f | %.4f | %.4f | %d | %.4f | %.4f | %d | %d |\n", b.identifiers(), b.negativeQueries(), b.bitSize(), b.hashCount(), b.bloomBytes(),
                    b.exactSetBytesApprox(), b.bloomQueryMillis(), b.exactQueryMillis(), b.twoStageMillis(), b.falsePositives(), b.observedFpRate(), b.theoreticalFpRate(), b.falseNegatives(), b.exactLookupsAvoided()));
        md.append("\n## Min-cost flow and fair share vs plain maximum flow\n\n| Size | Meals (all equal) | EK ms | Min-cost ms | Fair ms | Fair max-flow calls | Meal-minutes EK | Meal-minutes min-cost | Travel saving | Tight meals EK -> min-cost | Smallest NGO share EK -> fair | Jain EK -> fair | Valid |\n|---|---|---|---|---|---|---|---|---|---|---|---|---|\n");
        csv.append("experiment,size,ek_ms,mincost_ms,fair_ms,meals,travel_saving_pct,min_receipt_ek,min_receipt_fair\n");
        for (StrategyRow x : r.strategy()) {
            md.append(String.format(Locale.ROOT, "| %dx%d | %d | %.3f | %.3f | %.3f | %d | %d | %d | %.1f%% | %d -> %d | %d -> %d | %.3f -> %.3f | %s |\n", x.donors(), x.ngos(), x.meals(), x.ekMillis(), x.minCostMillis(), x.fairMillis(), x.fairMaxFlowCalls(),
                    x.ekMealMinutes(), x.minCostMealMinutes(), x.travelSavingPercent(), x.ekTightMeals(), x.minCostTightMeals(), x.ekMinReceipt(), x.fairMinReceipt(), x.ekJain(), x.fairJain(), x.sameTotal() && x.allValid()));
            csv.append(String.format(Locale.ROOT, "strategy,%d,%.5f,%.5f,%.5f,%d,%.3f,%d,%d\n", x.donors(), x.ekMillis(), x.minCostMillis(), x.fairMillis(), x.meals(), x.travelSavingPercent(), x.ekMinReceipt(), x.fairMinReceipt()));
        }
        md.append("\n## Count-Min Sketch vs exact HashMap (skewed donor stream)\n\n| Events | Distinct keys | Sketch bytes | HashMap bytes (approx) | Sketch ms | HashMap ms | Error bound (eps*N) | Max over-estimate | Keys within bound | Never under-estimates | Top-10 correct |\n|---|---|---|---|---|---|---|---|---|---|---|\n");
        for (SketchRow x : r.sketch())
            md.append(String.format(Locale.ROOT, "| %d | %d | %d | %d | %.2f | %.2f | %.0f | %d | %.2f%% | %s | %s |\n", x.events(), x.distinctKeys(), x.sketchBytes(), x.exactBytesApprox(), x.sketchMillis(), x.exactMillis(), x.errorBound(), x.maxOverestimate(), x.withinBoundPercent(), x.neverUnderestimates(), x.top10Correct()));
        md.append("\n## Volunteer scheduling: greedy interval partitioning (heap) vs linear first-fit\n\n| Trips | Volunteers used | Depth (lower bound) | Optimal | Heap ms | Linear scan ms |\n|---|---|---|---|---|---|\n");
        for (ScheduleRow x : r.schedule())
            md.append(String.format(Locale.ROOT, "| %d | %d | %d | %s | %.3f | %.3f |\n", x.trips(), x.volunteers(), x.depth(), x.optimal(), x.heapMillis(), x.linearScanMillis()));
        md.append("\n### Notes\n\n");
        for (String n : r.notes()) md.append("- ").append(n).append('\n');
        Files.writeString(out.resolve("benchmark-results.csv"), csv.toString());
        Files.writeString(out.resolve("benchmark-results.md"), md.toString());
        System.out.println(md);
        System.out.println("Saved to " + out.toAbsolutePath());
    }
}
