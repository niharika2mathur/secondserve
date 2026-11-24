package com.secondserve.service;

import com.secondserve.model.Donation;
import com.secondserve.model.Ngo;
import com.secondserve.repository.DonationRepository;
import com.secondserve.repository.NgoRepository;
import com.secondserve.repository.TravelRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

/** Decides whether a donation batch can feasibly be delivered to an NGO, with a human-readable reason. */
public final class FeasibilityService {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("dd MMM HH:mm");

    public record Pair(String donationId, String ngoId, boolean feasible, String code, String reason, Integer travelMinutes, String arrival) {}

    public static final class Evaluation {
        public final List<Donation> donations;
        public final List<Ngo> ngos;
        public final Pair[][] pairs;
        public final boolean[] expired;
        public final LocalDateTime planningTime;

        Evaluation(List<Donation> d, List<Ngo> n, Pair[][] p, boolean[] e, LocalDateTime t) { donations = d; ngos = n; pairs = p; expired = e; planningTime = t; }

        public boolean[][] matrix() {
            boolean[][] m = new boolean[donations.size()][ngos.size()];
            for (int i = 0; i < m.length; i++) for (int j = 0; j < m[i].length; j++) m[i][j] = pairs[i][j].feasible();
            return m;
        }
        /** Supply entering the network: expired batches are removed (supply 0). */
        public int[] supply() {
            int[] s = new int[donations.size()];
            for (int i = 0; i < s.length; i++) s[i] = expired[i] ? 0 : donations.get(i).meals();
            return s;
        }
        public int[] capacity() {
            int[] c = new int[ngos.size()];
            for (int j = 0; j < c.length; j++) c[j] = ngos.get(j).effectiveCapacity();
            return c;
        }
        public int feasibleCount() {
            int c = 0;
            for (Pair[] r : pairs) for (Pair p : r) if (p.feasible()) c++;
            return c;
        }
    }

    private final DonationRepository donations;
    private final NgoRepository ngos;
    private final TravelRepository travel;

    public FeasibilityService(DonationRepository d, NgoRepository n, TravelRepository t) { donations = d; ngos = n; travel = t; }

    public record View(String planningTime, List<String> expiredBatchIds, List<Pair> pairs, int feasibleRoutes, int totalRoutes) {}

    /** Flat view of every donor-NGO pair for the Travel & Feasibility page. */
    public View view(LocalDateTime planning) {
        Evaluation ev = evaluate(planning);
        List<Pair> flat = new java.util.ArrayList<>();
        List<String> expired = new java.util.ArrayList<>();
        for (int i = 0; i < ev.donations.size(); i++) {
            if (ev.expired[i]) expired.add(ev.donations.get(i).batchId());
            flat.addAll(java.util.Arrays.asList(ev.pairs[i]));
        }
        return new View(planning.toString(), expired, flat, ev.feasibleCount(), flat.size());
    }

    /** Evaluates an explicit (possibly adjusted or hypothetical) set of batches and NGOs against the stored travel times. */
    public Evaluation evaluate(List<Donation> ds, List<Ngo> ns, LocalDateTime planning) {
        return evaluate(ds, ns, travel::get, planning);
    }

    public Evaluation evaluate(LocalDateTime planning) {
        return evaluate(donations.findAll(), ngos.findAll(), travel::get, planning);
    }

    /** Pure function, directly unit-testable. */
    public static Evaluation evaluate(List<Donation> ds, List<Ngo> ns, BiFunction<String, String, Optional<Integer>> travel, LocalDateTime planning) {
        Pair[][] pairs = new Pair[ds.size()][ns.size()];
        boolean[] expired = new boolean[ds.size()];
        for (int i = 0; i < ds.size(); i++) {
            Donation d = ds.get(i);
            expired[i] = !d.safeUntil().isAfter(planning);
            LocalDateTime depart = planning.isAfter(d.availableFrom()) ? planning : d.availableFrom();
            for (int j = 0; j < ns.size(); j++) {
                Ngo n = ns.get(j);
                Optional<Integer> t = travel.apply(d.batchId(), n.ngoId());
                Integer tm = t.orElse(null);
                String arrival = tm == null ? null : depart.plus(Duration.ofMinutes(tm)).toString();
                String code, reason;
                if (d.meals() <= 0) { code = "BATCH_EMPTY"; reason = "Batch has no meal packs."; }
                else if (expired[i]) { code = "EXPIRED"; reason = "Safe delivery deadline " + d.safeUntil().format(HM) + " is not after the planning time " + planning.format(HM) + "."; }
                else if (n.effectiveCapacity() <= 0) { code = "NGO_NO_CAPACITY"; reason = "NGO has no remaining demand or pickup capacity."; }
                else if (tm == null) { code = "NO_TRAVEL_TIME"; reason = "No travel time has been entered for this donor-NGO pair."; }
                else if (tm > n.maxTravelMinutes()) { code = "NGO_TRAVEL_LIMIT"; reason = "Travel time " + tm + " min exceeds the NGO limit of " + n.maxTravelMinutes() + " min."; }
                else if (depart.plusMinutes(tm).isAfter(d.safeUntil())) { code = "ARRIVAL_AFTER_DEADLINE"; reason = "Arrival " + depart.plusMinutes(tm).format(HM) + " is after the safe delivery deadline " + d.safeUntil().format(HM) + "."; }
                else { code = "OK"; reason = "Feasible: arrives " + depart.plusMinutes(tm).format(HM) + " (deadline " + d.safeUntil().format(HM) + ")."; }
                pairs[i][j] = new Pair(d.batchId(), n.ngoId(), code.equals("OK"), code, reason, tm, arrival);
            }
        }
        return new Evaluation(ds, ns, pairs, expired, planning);
    }
}
