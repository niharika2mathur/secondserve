package com.secondserve.service;

import com.secondserve.algorithm.AllocationFlowModel;
import com.secondserve.algorithm.FcfsAllocator;
import com.secondserve.exception.NotFoundException;
import com.secondserve.exception.ValidationException;
import com.secondserve.model.Donation;
import com.secondserve.model.Ngo;
import com.secondserve.util.Fields;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Sensitivity analysis: "what single change would place more meals?" Each scenario edits a copy of the data, rebuilds the
 * feasibility matrix and re-runs maximum flow. This turns the minimum-cut bottleneck into concrete, ranked advice.
 */
public final class InsightService {
    public record Recommendation(String type, String ngoId, String title, String detail, int gainMeals) {}
    public record Insights(String planningTime, int baselineMeals, int baselineRoutes, List<Recommendation> recommendations, String note) {}
    public record WhatIfResult(String planningTime, int baselineMeals, int scenarioMeals, int delta, int baselineRoutes, int scenarioRoutes, String description) {}

    private final AllocationService allocation;
    private final FeasibilityService feasibility;

    public InsightService(AllocationService a, FeasibilityService f) { allocation = a; feasibility = f; }

    private record Outcome(int meals, int routes) {}

    private Outcome solve(List<Donation> ds, List<Ngo> ns, LocalDateTime planning) {
        if (ds.isEmpty() || ns.isEmpty()) return new Outcome(0, 0);
        FeasibilityService.Evaluation ev = feasibility.evaluate(ds, ns, planning);
        return new Outcome(FcfsAllocator.total(AllocationFlowModel.solve(ev.supply(), ev.capacity(), ev.matrix())), ev.feasibleCount());
    }

    private static List<Ngo> replace(List<Ngo> ns, int idx, Ngo n) { List<Ngo> c = new ArrayList<>(ns); c.set(idx, n); return c; }

    public Insights recommendations(LocalDateTime planning) {
        AllocationService.Inputs in = allocation.inputs();
        if (in.donations().isEmpty() || in.ngos().isEmpty()) throw new ValidationException("Add donation batches and NGOs first.");
        Outcome base = solve(in.donations(), in.ngos(), planning);
        List<Recommendation> recs = new ArrayList<>();
        for (int j = 0; j < in.ngos().size(); j++) {
            Ngo n = in.ngos().get(j);
            Outcome cap = solve(in.donations(), replace(in.ngos(), j, new Ngo(n.ngoId(), n.name(), n.requiredMeals() + 20, n.pickupCapacity() + 20, n.maxTravelMinutes(), n.contact())), planning);
            if (cap.meals() > base.meals()) recs.add(new Recommendation("CAPACITY", n.ngoId(), "Add 20 meals of pickup capacity at " + n.name(),
                    "If " + n.name() + " could collect 20 more meals in this window (an extra vehicle or volunteer), the system could place " + (cap.meals() - base.meals()) + " more meals.", cap.meals() - base.meals()));
            Outcome trav = solve(in.donations(), replace(in.ngos(), j, new Ngo(n.ngoId(), n.name(), n.requiredMeals(), n.pickupCapacity(), n.maxTravelMinutes() + 15, n.contact())), planning);
            if (trav.meals() > base.meals()) recs.add(new Recommendation("TRAVEL", n.ngoId(), "Accept 15 more minutes of travel at " + n.name(),
                    "Raising the travel limit of " + n.name() + " from " + n.maxTravelMinutes() + " to " + (n.maxTravelMinutes() + 15) + " minutes opens " + (trav.routes() - base.routes()) + " more route(s) and places " + (trav.meals() - base.meals()) + " more meals.", trav.meals() - base.meals()));
        }
        recs.sort(Comparator.comparingInt(Recommendation::gainMeals).reversed().thenComparing(Recommendation::title));
        if (recs.size() > 6) recs = new ArrayList<>(recs.subList(0, 6));
        String note = base.meals() == 0
                ? "Nothing is left to plan right now: every reachable meal is already committed to a dispatch plan, or no batch is feasible at this planning time."
                : recs.isEmpty()
                ? "None of the tested single changes (+20 pickup capacity or +15 minutes of travel at one NGO) places more meals: the limit is the supply that can reach the NGOs in time. Adding feasible routes or more donations would help."
                : "Each line re-runs maximum flow on a modified copy of the data, so the gains are exact for that single change, not estimates. Combined changes can interact.";
        return new Insights(planning.toString(), base.meals(), base.routes(), recs, note);
    }

    public WhatIfResult whatIf(Map<String, Object> body) {
        Map<String, Object> m = body == null ? new HashMap<>() : body;
        Fields f = new Fields(m);
        LocalDateTime planning = f.time("planningTime", "Planning date and time");
        String ngoId = f.str("ngoId", "NGO", false, 40), batchId = f.str("batchId", "Batch", false, 40);
        int extraPickup = opt(f, m, "extraPickup", "Extra pickup capacity", 0, 5000);
        int extraTravel = opt(f, m, "extraTravelMinutes", "Extra travel time", 0, 240);
        int extraMeals = opt(f, m, "extraMeals", "Extra meals", 0, 5000);
        f.throwIfInvalid();
        if (extraPickup + extraTravel > 0 && (ngoId == null || ngoId.isEmpty())) throw new ValidationException("Choose an NGO to apply the capacity or travel change to");
        if (extraMeals > 0 && (batchId == null || batchId.isEmpty())) throw new ValidationException("Choose a donation batch to add the extra meals to");
        if (extraPickup + extraTravel + extraMeals == 0) throw new ValidationException("Enter at least one change to test");
        AllocationService.Inputs in = allocation.inputs();
        Outcome base = solve(in.donations(), in.ngos(), planning);
        List<Donation> ds = new ArrayList<>(in.donations());
        List<Ngo> ns = new ArrayList<>(in.ngos());
        List<String> desc = new ArrayList<>();
        if (ngoId != null && !ngoId.isEmpty() && extraPickup + extraTravel > 0) {
            String id = DonationService.normalizeId(ngoId);
            int idx = -1;
            for (int j = 0; j < ns.size(); j++) if (ns.get(j).ngoId().equals(id)) idx = j;
            if (idx < 0) throw new NotFoundException("NGO " + id + " is not part of the current planning round");
            Ngo n = ns.get(idx);
            ns.set(idx, new Ngo(n.ngoId(), n.name(), n.requiredMeals() + extraPickup, n.pickupCapacity() + extraPickup, n.maxTravelMinutes() + extraTravel, n.contact()));
            if (extraPickup > 0) desc.add("+" + extraPickup + " meals of pickup capacity and demand at " + n.name());
            if (extraTravel > 0) desc.add("+" + extraTravel + " min travel limit at " + n.name());
        }
        if (batchId != null && !batchId.isEmpty() && extraMeals > 0) {
            String id = DonationService.normalizeId(batchId);
            int idx = -1;
            for (int i = 0; i < ds.size(); i++) if (ds.get(i).batchId().equals(id)) idx = i;
            if (idx < 0) throw new NotFoundException("Batch " + id + " is not part of the current planning round");
            Donation d = ds.get(idx);
            ds.set(idx, new Donation(d.batchId(), d.donorName(), d.meals() + extraMeals, d.availableFrom(), d.safeUntil(), d.notes()));
            desc.add("+" + extraMeals + " meals in batch " + d.batchId());
        }
        Outcome sc = solve(ds, ns, planning);
        return new WhatIfResult(planning.toString(), base.meals(), sc.meals(), sc.meals() - base.meals(), base.routes(), sc.routes(), String.join("; ", desc));
    }

    private static int opt(Fields f, Map<String, Object> m, String key, String label, int min, int max) {
        if (m.get(key) == null || String.valueOf(m.get(key)).isBlank()) return 0;
        Integer v = f.integer(key, label, min, max);
        return v == null ? 0 : v;
    }
}
