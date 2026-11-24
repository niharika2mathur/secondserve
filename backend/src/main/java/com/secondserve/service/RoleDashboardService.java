package com.secondserve.service;

import com.secondserve.dto.Dtos.*;
import com.secondserve.exception.NotFoundException;
import com.secondserve.model.*;
import com.secondserve.repository.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** Builds the three role-specific dashboards. Each only ever reads data belonging to the authenticated principal (admin: everything). */
public final class RoleDashboardService {
    public record Alert(String level, String title, String message, String route) {}

    public record MyBatch(String batchId, int meals, String availableFrom, String safeUntil, long minutesLeft, int planned, int dispatched,
                          int delivered, int unplaced, String status, String hint, String notes) {}
    public record Impact(long lifetimeMeals, double kg, int rank, int donorCount, String tier, String nextTier, long mealsToNextTier, long nextTierAt,
                         double avgRating, int ratingCount) {}
    public record DonorDashboard(String orgName, int batches, int mealsOffered, int mealsDispatched, int mealsDelivered, int mealsAwaiting, int mealsExpired,
                                 List<MyBatch> myBatches, List<Delivery> deliveries, List<Alert> alerts, Impact impact,
                                 List<AnalyticsService.WeekPoint> history, int unread) {}

    public record ReachRow(String batchId, String donor, int meals, boolean feasible, String code, String reason, Integer travelMinutes) {}
    public record FairnessView(boolean hasPlan, String strategy, Integer fcfs, Integer maxFlow, Integer minCost, Integer fair, boolean committed) {}
    public record NgoDashboard(Ngo profile, int plannedMeals, int incomingMeals, int receivedMeals, long lifetimeMeals, double fulfilmentRate, double avgRatingGiven,
                               List<Delivery> deliveries, List<ReachRow> reach, List<String> tips, FairnessView fairness,
                               List<AnalyticsService.WeekPoint> history, int unread) {}

    public record AdminOverview(int pendingApprovals, int activeUsers, int deliveriesInFlight, int deliveredCount, int deliveredMeals, int failedDeliveries,
                                int volunteersNeeded, boolean planReady, List<Alert> alerts, Map<String, Integer> deliveryStatus, AnalyticsService.Summary analytics, int unread) {}

    private final DonationRepository donations;
    private final NgoRepository ngos;
    private final TravelRepository travel;
    private final DeliveryRepository deliveries;
    private final UserRepository users;
    private final AllocationService allocation;
    private final FeasibilityService feasibility;
    private final AnalyticsService analytics;
    private final NotificationService notifications;

    public RoleDashboardService(DonationRepository d, NgoRepository n, TravelRepository t, DeliveryRepository dl, UserRepository u, AllocationService a,
                                FeasibilityService f, AnalyticsService an, NotificationService nt) {
        donations = d; ngos = n; travel = t; deliveries = dl; users = u; allocation = a; feasibility = f; analytics = an; notifications = nt;
    }

    private static LocalDateTime now() { return LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES); }

    // ------------------------------------------------------------------- donor
    public DonorDashboard donor(Principal p) {
        if (p.orgName() == null) throw new NotFoundException("Your account is not linked to a donor organisation.");
        String org = p.orgName();
        LocalDateTime now = now();
        AllocationResult last = allocation.lastOrNull();
        Map<String, Integer> planned = new HashMap<>();
        if (last != null && !last.committed() && !last.stale()) last.batches().forEach(b -> planned.put(b.batchId(), b.allocated()));
        List<Delivery> mine = deliveries.findAll().stream().filter(d -> d.donorName().equalsIgnoreCase(org)).toList();
        List<MyBatch> out = new ArrayList<>();
        List<Alert> alerts = new ArrayList<>();
        int offered = 0, dispatchedTotal = 0, deliveredTotal = 0, awaiting = 0, expiredMeals = 0;
        for (Donation d : donations.findAll()) {
            if (!d.donorName().equalsIgnoreCase(org)) continue;
            int disp = 0, del = 0;
            for (Delivery x : mine) if (x.batchId().equals(d.batchId())) {
                if (Delivery.DISPATCHED.equals(x.status()) || Delivery.PICKED_UP.equals(x.status())) disp += x.meals();
                else if (Delivery.DELIVERED.equals(x.status())) del += x.receivedMeals() == null ? x.meals() : x.receivedMeals();
            }
            long left = Duration.between(now, d.safeUntil()).toMinutes();
            int unplaced = Math.max(0, d.meals() - disp - del);
            int pl = planned.getOrDefault(d.batchId(), 0);
            boolean hasRoutes = !travel.forDonation(d.batchId()).isEmpty();
            String status, hint;
            if (del >= d.meals() || (del > 0 && disp == 0 && unplaced == 0)) { status = "DELIVERED"; hint = "All meals reached an NGO."; }
            else if (disp > 0) { status = "IN_TRANSIT"; hint = disp + " meals are assigned and on their way."; }
            else if (left <= 0) { status = "EXPIRED"; hint = "The safe-delivery deadline has passed; the remaining meals cannot be placed."; }
            else if (pl > 0) { status = "PLANNED"; hint = pl + " meals are in the latest allocation, waiting for the coordinator to publish it."; }
            else if (!hasRoutes) { status = "NEEDS_ROUTES"; hint = "No travel times are known for this batch yet, so no NGO is reachable. The coordinator adds them."; }
            else { status = "WAITING"; hint = "Waiting for the next allocation run."; }
            out.add(new MyBatch(d.batchId(), d.meals(), d.availableFrom().toString(), d.safeUntil().toString(), left, pl, disp, del, unplaced, status, hint, d.notes()));
            offered += d.meals(); dispatchedTotal += disp; deliveredTotal += del;
            if (left <= 0) expiredMeals += unplaced; else awaiting += unplaced;
            if (left > 0 && left <= DashboardService.APPROACHING_MINUTES && unplaced > 0)
                alerts.add(new Alert("bad", d.batchId() + " expires in " + left + " min", unplaced + " meals are not in a dispatch plan yet.", "batches"));
            if ("NEEDS_ROUTES".equals(status)) alerts.add(new Alert("warn", d.batchId() + " has no routes", "Ask the coordinator to enter travel times for this batch.", "batches"));
        }
        for (Delivery x : mine) if (Delivery.DISPATCHED.equals(x.status()))
            alerts.add(new Alert("info", "Hand-over due: " + x.batchId() + " to " + x.ngoName(), "Volunteer " + x.volunteers() + " collects " + x.meals() + " meals from " + x.departAt().replace('T', ' ') + ". Confirm the hand-over in Deliveries.", "deliveries"));
        AnalyticsService.DonorStats st = analytics.donorStats(org);
        long life = st.lifetimeMeals();
        String next = AnalyticsService.nextTier(life);
        Impact imp = new Impact(life, life * AnalyticsService.KG_PER_MEAL, st.rank(), st.donorCount(), AnalyticsService.tier(life), next,
                next == null ? 0 : AnalyticsService.nextTierAt(life) - life, AnalyticsService.nextTierAt(life), st.avgRating(), st.ratingCount());
        List<Delivery> recent = new ArrayList<>(mine);
        Collections.reverse(recent);
        return new DonorDashboard(org, out.size(), offered, dispatchedTotal, deliveredTotal, awaiting, expiredMeals, out, recent, alerts, imp, st.weekly(), notifications.unread(p.userId()));
    }

    // --------------------------------------------------------------------- NGO
    public NgoDashboard ngo(Principal p) {
        if (p.ngoId() == null) throw new NotFoundException("Your account is not linked to an NGO record.");
        Ngo n = ngos.find(p.ngoId()).orElseThrow(() -> new NotFoundException("Your NGO record no longer exists. Contact the coordinator."));
        LocalDateTime now = now();
        List<Delivery> mine = deliveries.findAll().stream().filter(d -> d.ngoId().equals(n.ngoId())).toList();
        int incoming = 0, received = 0, rated = 0, ratingSum = 0;
        for (Delivery d : mine) {
            if (Delivery.DISPATCHED.equals(d.status()) || Delivery.PICKED_UP.equals(d.status())) incoming += d.meals();
            if (Delivery.DELIVERED.equals(d.status())) { received += d.receivedMeals() == null ? d.meals() : d.receivedMeals(); if (d.rating() != null) { rated++; ratingSum += d.rating(); } }
        }
        AllocationResult last = allocation.lastOrNull();
        int planned = 0;
        FairnessView fv = new FairnessView(false, null, null, null, null, null, false);
        if (last != null) {
            for (NgoStrategyRow r : last.perNgo()) if (r.ngoId().equals(n.ngoId())) {
                fv = new FairnessView(true, last.strategy(), r.fcfs(), r.maxFlow(), r.minCost(), r.fair(), last.committed());
                if (!last.committed() && !last.stale()) planned = last.ngos().stream().filter(o -> o.ngoId().equals(n.ngoId())).mapToInt(NgoOutcome::allocated).sum();
            }
        }
        FeasibilityService.Evaluation ev = feasibility.evaluate(donations.findAll(), List.of(n), now);
        List<ReachRow> reach = new ArrayList<>();
        int unlockable = 0, unlockMeals = 0;
        for (int i = 0; i < ev.donations.size(); i++) {
            FeasibilityService.Pair pr = ev.pairs[i][0];
            Donation d = ev.donations.get(i);
            reach.add(new ReachRow(d.batchId(), d.donorName(), d.meals(), pr.feasible(), pr.code(), pr.reason(), pr.travelMinutes()));
            if ("NGO_TRAVEL_LIMIT".equals(pr.code()) && pr.travelMinutes() != null && pr.travelMinutes() <= n.maxTravelMinutes() + 15) { unlockable++; unlockMeals += d.meals(); }
        }
        reach.sort(Comparator.comparing((ReachRow r) -> !r.feasible()).thenComparing(ReachRow::batchId));
        long feasibleNow = reach.stream().filter(ReachRow::feasible).count();
        List<String> tips = new ArrayList<>();
        if (feasibleNow == 0) tips.add("No donor batch can reach you right now. The table below shows the reason for each one (expired, no route entered, travel limit or deadline).");
        if (unlockable > 0) tips.add("Raising your travel limit by 15 minutes would make " + unlockable + " more donor batch(es) reachable (" + unlockMeals + " meals on offer).");
        if (n.pickupCapacity() < n.requiredMeals()) tips.add("Your pickup capacity (" + n.pickupCapacity() + ") is lower than your stated need (" + n.requiredMeals() + "). Meals above " + n.pickupCapacity() + " cannot be allocated in one window; more vehicle capacity raises what you can receive.");
        long toConfirm = mine.stream().filter(d -> Delivery.DISPATCHED.equals(d.status()) || Delivery.PICKED_UP.equals(d.status())).count();
        if (toConfirm > 0) tips.add("You have " + toConfirm + " delivery(ies) on the way. Confirm receipt, and rate the hand-over, once the meals arrive.");
        AnalyticsService.NgoStats st = analytics.ngoStats(n.ngoId());
        int denom = Math.max(1, n.requiredMeals());
        List<Delivery> recent = new ArrayList<>(mine);
        Collections.reverse(recent);
        return new NgoDashboard(n, planned, incoming, received, st.lifetimeMeals(), Math.min(1.0, (double) (incoming + received) / denom), rated == 0 ? 0 : (double) ratingSum / rated,
                recent, reach, tips, fv, st.weekly(), notifications.unread(p.userId()));
    }

    // ------------------------------------------------------------------- admin
    public AdminOverview admin(Principal p) {
        LocalDateTime now = now();
        AllocationResult last = allocation.lastOrNull();
        List<Delivery> all = deliveries.findAll();
        Map<String, Integer> status = new LinkedHashMap<>();
        for (String s : List.of("DISPATCHED", "PICKED_UP", "DELIVERED", "FAILED")) status.put(s, 0);
        int inFlight = 0, delivered = 0, deliveredMeals = 0, failed = 0;
        for (Delivery d : all) {
            status.merge(d.status(), 1, Integer::sum);
            if (Delivery.DISPATCHED.equals(d.status()) || Delivery.PICKED_UP.equals(d.status())) inFlight++;
            if (Delivery.DELIVERED.equals(d.status())) { delivered++; deliveredMeals += d.receivedMeals() == null ? d.meals() : d.receivedMeals(); }
            if (Delivery.FAILED.equals(d.status())) failed++;
        }
        int pending = 0, active = 0;
        for (User u : users.findAll()) { if ("PENDING".equals(u.status())) pending++; if ("ACTIVE".equals(u.status())) active++; }
        List<Alert> alerts = new ArrayList<>();
        List<Donation> ds = donations.findAll();
        if (!ds.isEmpty() && last == null) alerts.add(new Alert("warn", "No allocation has been run", "There are " + ds.size() + " donation batch(es) waiting. Run the allocation engine.", "allocation"));
        if (last != null && last.stale()) alerts.add(new Alert("warn", "Allocation is out of date", "Records changed after run #" + last.runId() + ". Run it again before publishing.", "allocation"));
        boolean ready = last != null && !last.committed() && !last.stale() && last.summary().allocated() > 0;
        if (ready) alerts.add(new Alert("info", "A dispatch plan is ready to publish", last.summary().allocated() + " meals, " + last.schedule().volunteersNeeded() + " volunteer(s) needed (" + last.strategy() + ").", "results"));
        Map<String, Integer> unplaced = new HashMap<>();
        if (last != null && !last.committed()) last.batches().forEach(b -> unplaced.put(b.batchId(), b.remaining()));
        for (Donation d : ds) {
            long left = Duration.between(now, d.safeUntil()).toMinutes();
            int held = deliveries.heldByBatch().getOrDefault(d.batchId(), 0);
            int rem = unplaced.getOrDefault(d.batchId(), d.meals() - held);
            if (left > 0 && left <= DashboardService.APPROACHING_MINUTES && rem > 0)
                alerts.add(new Alert("bad", d.batchId() + " expires in " + left + " min", rem + " meals from " + d.donorName() + " are not placed yet.", "allocation"));
            if (travel.forDonation(d.batchId()).isEmpty() && left > 0) alerts.add(new Alert("warn", d.batchId() + " has no routes", d.donorName() + " needs travel times before it can be allocated.", "travel"));
        }
        if (pending > 0) alerts.add(new Alert("info", pending + " registration(s) awaiting approval", "Approve or reject new donor and NGO accounts.", "users"));
        if (failed > 0) alerts.add(new Alert("bad", failed + " failed delivery(ies)", "Their meals are back in the pool; run the allocation again.", "deliveries"));
        int vol = last == null ? 0 : last.schedule().volunteersNeeded();
        return new AdminOverview(pending, active, inFlight, delivered, deliveredMeals, failed, vol, ready, alerts, status, analytics.summary(), notifications.unread(p.userId()));
    }
}
