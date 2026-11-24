package com.secondserve.service;

import com.secondserve.algorithm.CountMinSketch;
import com.secondserve.algorithm.HeavyHitters;
import com.secondserve.model.Delivery;
import com.secondserve.repository.DeliveryRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Impact analytics over a ledger of completed deliveries: 12 weeks of seeded synthetic history (clearly labelled as such)
 * plus every delivery an NGO has confirmed in this session. Top donors / NGOs are found with a Count-Min Sketch
 * (streaming heavy hitters) and verified against exact counts.
 */
public final class AnalyticsService {
    public static final double KG_PER_MEAL = 0.4;           // stated assumption for the kg estimate, not a measurement
    public static final int HISTORY_DAYS = 84;
    private static final DateTimeFormatter LABEL = DateTimeFormatter.ofPattern("dd MMM");

    public record LedgerEntry(LocalDate date, String donor, String ngoId, String ngoName, int meals, Integer rating, boolean live) {}
    public record WeekPoint(String label, long meals) {}
    public record HeavyHitter(String name, long estimated, long exact, long overestimate) {}
    public record SketchInfo(double epsilon, double delta, int width, int depth, long sketchBytes, long exactMapBytesApprox, long events,
                             double errorBound, int distinctDonors, int distinctNgos, boolean donorTopMatchesExact, boolean ngoTopMatchesExact, String note) {}
    public record Summary(long totalMeals, int deliveries, int activeDonors, int activeNgos, double kg, double avgRating, int ratingCount,
                          long liveMeals, int historyDays, List<WeekPoint> weekly, List<HeavyHitter> topDonors, List<HeavyHitter> topNgos, SketchInfo sketch) {}
    public record DonorStats(long lifetimeMeals, int rank, int donorCount, double avgRating, int ratingCount, List<WeekPoint> weekly) {}
    public record NgoStats(long lifetimeMeals, List<WeekPoint> weekly) {}

    private static final String[] DONORS = {"Grand Orchid Hotel", "St. Xavier's College Canteen", "Spice Route Restaurant", "Lakeview Convention Centre",
            "Sunrise Bakery & Cafe", "TechPark Cafeteria", "Hotel Meridian", "Annapurna Mess"};
    private static final int[] DONOR_WEIGHT = {26, 16, 12, 24, 5, 10, 11, 6};
    private static final String[][] NGOS = {{"N-01", "Asha Community Kitchen"}, {"N-02", "Hope Shelter Home"}, {"N-03", "Roti Bank Foundation"},
            {"N-04", "Little Stars Orphanage"}, {"N-05", "Seva Night Shelter"}, {"N-06", "Greenfield Elder Care"}, {"N-07", "Street Kids Learning Centre"}};

    private final DeliveryRepository deliveries;
    private final List<LedgerEntry> history = new ArrayList<>();

    public AnalyticsService(DeliveryRepository deliveries) { this.deliveries = deliveries; seedHistory(2026); }

    private void seedHistory(long seed) {
        Random rnd = new Random(seed);
        int totalW = Arrays.stream(DONOR_WEIGHT).sum();
        LocalDate today = LocalDate.now();
        for (int d = HISTORY_DAYS; d >= 1; d--) {
            LocalDate day = today.minusDays(d);
            int events = 2 + rnd.nextInt(4);
            for (int e = 0; e < events; e++) {
                int roll = rnd.nextInt(totalW), di = 0;
                while (roll >= DONOR_WEIGHT[di]) { roll -= DONOR_WEIGHT[di]; di++; }
                int meals = 25 + rnd.nextInt(90) + (di == 3 ? 40 : 0);
                String[] ngo = NGOS[rnd.nextInt(NGOS.length)];
                int rating = 3 + (rnd.nextInt(10) < 3 ? 0 : rnd.nextInt(10) < 5 ? 1 : 2);
                history.add(new LedgerEntry(day, DONORS[di], ngo[0], ngo[1], meals, rating, false));
            }
        }
    }

    public List<LedgerEntry> ledger() {
        List<LedgerEntry> all = new ArrayList<>(history);
        for (Delivery d : deliveries.findAll())
            if (Delivery.DELIVERED.equals(d.status()))
                all.add(new LedgerEntry(LocalDateTime.parse(d.updatedAt()).toLocalDate(), d.donorName(), d.ngoId(), d.ngoName(),
                        d.receivedMeals() == null ? d.meals() : d.receivedMeals(), d.rating(), true));
        return all;
    }

    private static List<WeekPoint> weekly(List<LedgerEntry> entries, int weeks) {
        LocalDate thisMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        long[] sums = new long[weeks];
        for (LedgerEntry e : entries) {
            long w = java.time.temporal.ChronoUnit.WEEKS.between(e.date().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), thisMonday);
            if (w >= 0 && w < weeks) sums[(int) (weeks - 1 - w)] += e.meals();
        }
        List<WeekPoint> out = new ArrayList<>();
        for (int i = 0; i < weeks; i++) out.add(new WeekPoint(thisMonday.minusWeeks(weeks - 1 - i).format(LABEL), sums[i]));
        return out;
    }

    private static List<HeavyHitter> topK(List<LedgerEntry> entries, boolean byDonor, int k, CountMinSketch sketch, Map<String, Long> exactOut, boolean[] matchOut) {
        HeavyHitters hh = new HeavyHitters(k, sketch);
        for (LedgerEntry e : entries) {
            String key = byDonor ? e.donor() : e.ngoId() + "|" + e.ngoName();
            hh.update(key, e.meals());
            exactOut.merge(key, (long) e.meals(), Long::sum);
        }
        List<HeavyHitter> out = new ArrayList<>();
        for (Map.Entry<String, Long> t : hh.top()) {
            long ex = exactOut.getOrDefault(t.getKey(), 0L);
            out.add(new HeavyHitter(byDonor ? t.getKey() : t.getKey().substring(t.getKey().indexOf('|') + 1), t.getValue(), ex, t.getValue() - ex));
        }
        List<String> exactTop = exactOut.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue(), a.getValue())).limit(k).map(Map.Entry::getKey).toList();
        List<String> sketchTop = hh.top().stream().map(Map.Entry::getKey).toList();
        matchOut[0] = new HashSet<>(exactTop).equals(new HashSet<>(sketchTop));
        return out;
    }

    public Summary summary() {
        List<LedgerEntry> all = ledger();
        long total = 0, live = 0, ratingSum = 0; int ratings = 0;
        Set<String> donors = new HashSet<>(), ngos = new HashSet<>();
        for (LedgerEntry e : all) {
            total += e.meals(); if (e.live()) live += e.meals();
            donors.add(e.donor()); ngos.add(e.ngoId());
            if (e.rating() != null) { ratingSum += e.rating(); ratings++; }
        }
        CountMinSketch sd = new CountMinSketch(0.002, 0.01), sn = new CountMinSketch(0.002, 0.01);
        Map<String, Long> exactD = new HashMap<>(), exactN = new HashMap<>();
        boolean[] md = new boolean[1], mn = new boolean[1];
        List<HeavyHitter> td = topK(all, true, 5, sd, exactD, md), tn = topK(all, false, 5, sn, exactN, mn);
        long exactBytes = 0;
        for (String k : exactD.keySet()) exactBytes += 48 + 2L * k.length();
        SketchInfo si = new SketchInfo(sd.epsilon(), sd.delta(), sd.width(), sd.depth(), sd.memoryBytes(), exactBytes, all.size(), sd.errorBound(), donors.size(), ngos.size(), md[0], mn[0],
                "With only " + donors.size() + " donors an exact map is smaller than the sketch; the sketch's size does not grow with the number of donors or events (see the Benchmarks page for 1,000,000 events).");
        return new Summary(total, all.size(), donors.size(), ngos.size(), total * KG_PER_MEAL, ratings == 0 ? 0 : (double) ratingSum / ratings, ratings, live,
                HISTORY_DAYS, weekly(all, 12), td, tn, si);
    }

    public DonorStats donorStats(String org) {
        List<LedgerEntry> all = ledger();
        Map<String, Long> totals = new HashMap<>();
        for (LedgerEntry e : all) totals.merge(e.donor().toLowerCase(Locale.ROOT), (long) e.meals(), Long::sum);
        long mine = totals.getOrDefault(org.toLowerCase(Locale.ROOT), 0L);
        int rank = 1;
        for (Map.Entry<String, Long> e : totals.entrySet()) if (!e.getKey().equals(org.toLowerCase(Locale.ROOT)) && e.getValue() > mine) rank++;
        int donorCount = totals.size() + (totals.containsKey(org.toLowerCase(Locale.ROOT)) ? 0 : 1);
        List<LedgerEntry> own = all.stream().filter(e -> e.donor().equalsIgnoreCase(org)).toList();
        long rs = 0; int rc = 0;
        for (LedgerEntry e : own) if (e.rating() != null) { rs += e.rating(); rc++; }
        return new DonorStats(mine, rank, donorCount, rc == 0 ? 0 : (double) rs / rc, rc, weekly(own, 8));
    }

    public NgoStats ngoStats(String ngoId) {
        List<LedgerEntry> own = ledger().stream().filter(e -> e.ngoId().equals(ngoId)).toList();
        long total = 0;
        for (LedgerEntry e : own) total += e.meals();
        return new NgoStats(total, weekly(own, 8));
    }

    public static String tier(long meals) { return meals >= 5000 ? "Platinum" : meals >= 3000 ? "Gold" : meals >= 1500 ? "Silver" : meals >= 500 ? "Bronze" : "Starter"; }
    public static String nextTier(long meals) { return meals >= 5000 ? null : meals >= 3000 ? "Platinum" : meals >= 1500 ? "Gold" : meals >= 500 ? "Silver" : "Bronze"; }
    public static long nextTierAt(long meals) { return meals >= 5000 ? 0 : meals >= 3000 ? 5000 : meals >= 1500 ? 3000 : meals >= 500 ? 1500 : 500; }
}
