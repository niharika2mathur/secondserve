package com.secondserve.service;

import com.secondserve.dto.Dtos.*;
import com.secondserve.exception.ConflictException;
import com.secondserve.exception.ForbiddenException;
import com.secondserve.exception.NotFoundException;
import com.secondserve.exception.ValidationException;
import com.secondserve.model.Delivery;
import com.secondserve.model.Donation;
import com.secondserve.model.Principal;
import com.secondserve.model.Role;
import com.secondserve.repository.DeliveryRepository;
import com.secondserve.repository.DonationRepository;
import com.secondserve.util.Fields;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Turns an allocation result into a published dispatch plan (deliveries) and tracks each delivery through its life:
 * DISPATCHED -> PICKED_UP (donor) -> DELIVERED (NGO), or FAILED. Committed meals are subtracted from the next planning round.
 */
public final class DispatchService implements AllocationService.Commitments {
    public record CommitReport(long planId, int deliveries, int meals, int volunteers, List<Delivery> items) {}

    private final DeliveryRepository repo;
    private final AllocationService allocation;
    private final DonationRepository donations;
    private final NotificationService notifications;
    private final AuditService audit;

    public DispatchService(DeliveryRepository repo, AllocationService allocation, DonationRepository donations, NotificationService notifications, AuditService audit) {
        this.repo = repo; this.allocation = allocation; this.donations = donations; this.notifications = notifications; this.audit = audit;
    }

    @Override public int forBatch(String batchId) { return repo.heldByBatch().getOrDefault(batchId, 0); }
    @Override public int forNgo(String ngoId) { return repo.heldByNgo().getOrDefault(ngoId, 0); }

    private static String now() { return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(); }

    /** Publishes the latest allocation result as deliveries. */
    public synchronized CommitReport commit(Principal admin) {
        AllocationResult r = allocation.lastOrNull();
        if (r == null) throw new NotFoundException("No allocation has been run yet. Run the allocation first.");
        if (r.committed()) throw new ConflictException("Allocation run #" + r.runId() + " has already been published as a dispatch plan.");
        if (r.stale()) throw new ConflictException("Records changed after this allocation run. Run the allocation again before publishing.");
        List<Delivery> made = new ArrayList<>();
        int n = 0, meals = 0;
        Set<String> volunteers = new TreeSet<>();
        for (Row row : r.rows()) {
            if (!"ALLOCATED".equals(row.status())) continue;
            List<Trip> trips = r.schedule().trips().stream().filter(t -> t.batchId().equals(row.batchId()) && t.ngoId().equals(row.ngoId())).toList();
            String vols = String.join(", ", new TreeSet<>(trips.stream().map(Trip::volunteer).toList()));
            volunteers.addAll(trips.stream().map(Trip::volunteer).toList());
            String depart = trips.stream().map(Trip::departAt).min(Comparator.naturalOrder()).orElse(r.planningTime());
            String arrive = trips.stream().map(Trip::arriveAt).max(Comparator.naturalOrder()).orElse(depart);
            int travel = trips.isEmpty() ? 0 : trips.get(0).travelMinutes();
            String safeUntil = donations.find(row.batchId()).map(d -> d.safeUntil().toString()).orElse("");
            Delivery d = new Delivery("D-" + r.runId() + "-" + (++n), r.runId(), row.batchId(), row.donor(), row.ngoId(), row.ngoName(), row.meals(),
                    depart, arrive, safeUntil, travel, vols, Delivery.DISPATCHED, null, null, "", now());
            repo.save(d);
            made.add(d);
            meals += row.meals();
        }
        if (made.isEmpty()) throw new ValidationException("This allocation placed no meals, so there is nothing to publish.");
        allocation.markCommitted(r.runId());
        for (Delivery d : made) {
            notifications.toNgo(d.ngoId(), "DELIVERY", "Incoming delivery", d.meals() + " meals from " + d.donorName() + " (" + d.batchId() + ") are on the way. Expected by " + d.arriveBy().replace('T', ' ') + ".");
            notifications.toDonor(d.donorName(), "DELIVERY", "Batch dispatched", d.meals() + " meals of " + d.batchId() + " are assigned to " + d.ngoName() + ". Volunteer " + d.volunteers() + " will collect them from " + d.departAt().replace('T', ' ') + ".");
        }
        audit.record(admin.username(), "ADMIN", "COMMIT_PLAN", "Published run #" + r.runId() + " (" + r.strategy() + "): " + made.size() + " deliveries, " + meals + " meals, " + volunteers.size() + " volunteers");
        return new CommitReport(r.runId(), made.size(), meals, volunteers.size(), made);
    }

    /** Newest first. Admin sees all; a donor sees deliveries of its own batches; an NGO sees deliveries addressed to it. */
    public List<Delivery> list(Principal p) {
        List<Delivery> out = new ArrayList<>();
        for (Delivery d : repo.findAll()) if (visibleTo(p, d)) out.add(d);
        Collections.reverse(out);
        return out;
    }

    private static boolean visibleTo(Principal p, Delivery d) {
        return switch (p.role()) {
            case ADMIN -> true;
            case DONOR -> p.orgName() != null && p.orgName().equalsIgnoreCase(d.donorName());
            case NGO -> p.ngoId() != null && p.ngoId().equals(d.ngoId());
        };
    }

    private Delivery require(String id) { return repo.find(id).orElseThrow(() -> new NotFoundException("Delivery " + id + " does not exist")); }

    /** Donor confirms the volunteer collected the packs. */
    public synchronized Delivery handover(Principal p, String id) {
        Delivery d = require(id);
        if (!visibleTo(p, d) || p.role() != Role.DONOR) throw new ForbiddenException("This delivery does not belong to your organisation.");
        if (!Delivery.DISPATCHED.equals(d.status())) throw new ConflictException("Only dispatched deliveries can be marked as picked up (this one is " + d.status() + ").");
        Delivery u = d.with(Delivery.PICKED_UP, null, null, null, now());
        repo.save(u);
        notifications.toNgo(d.ngoId(), "DELIVERY", "Food collected", d.meals() + " meals from " + d.donorName() + " have been collected and are on their way.");
        audit.record(p.username(), "DONOR", "HANDOVER", d.deliveryId() + " " + d.batchId() + " -> " + d.ngoId());
        return u;
    }

    /** NGO confirms receipt, optionally with the number actually received and a 1-5 rating of the food/hand-over. */
    public synchronized Delivery confirm(Principal p, String id, Map<String, Object> body) {
        Delivery d = require(id);
        if (!visibleTo(p, d) || p.role() != Role.NGO) throw new ForbiddenException("This delivery is not addressed to your organisation.");
        if (!Delivery.DISPATCHED.equals(d.status()) && !Delivery.PICKED_UP.equals(d.status()))
            throw new ConflictException("Only dispatched or picked-up deliveries can be confirmed (this one is " + d.status() + ").");
        Map<String, Object> m = body == null ? new HashMap<>() : body;
        Fields f = new Fields(m);
        Integer received = d.meals();
        if (m.get("receivedMeals") != null && !String.valueOf(m.get("receivedMeals")).isBlank()) received = f.integer("receivedMeals", "Meals received", 1, d.meals());
        Integer rating = null;
        if (m.get("rating") != null && !String.valueOf(m.get("rating")).isBlank()) rating = f.integer("rating", "Rating", 1, 5);
        String note = f.str("note", "Note", false, 200);
        f.throwIfInvalid();
        Delivery u = d.with(Delivery.DELIVERED, received, rating, note, now());
        repo.save(u);
        notifications.toDonor(d.donorName(), "DELIVERY", "Delivery confirmed", d.ngoName() + " received " + received + " of " + d.meals() + " meals from " + d.batchId() + (rating != null ? " and rated it " + rating + "/5." : "."));
        audit.record(p.username(), "NGO", "CONFIRM_DELIVERY", d.deliveryId() + " received " + received + "/" + d.meals());
        return u;
    }

    /** Cancels a delivery that cannot be completed (reason required); its meals go back to the pool for the next run. */
    public synchronized Delivery report(Principal p, String id, Map<String, Object> body) {
        Delivery d = require(id);
        if (!visibleTo(p, d)) throw new ForbiddenException("This delivery does not belong to you.");
        if (Delivery.DELIVERED.equals(d.status()) || Delivery.FAILED.equals(d.status())) throw new ConflictException("This delivery is already " + d.status() + ".");
        Fields f = new Fields(body == null ? new HashMap<>() : body);
        String reason = f.str("note", "Reason", true, 200);
        f.throwIfInvalid();
        Delivery u = d.with(Delivery.FAILED, null, null, reason, now());
        repo.save(u);
        String who = p.role() == Role.NGO ? d.ngoName() : p.role() == Role.DONOR ? d.donorName() : "The coordinator";
        notifications.toAdmins("DELIVERY", "Delivery failed", who + " reported " + d.deliveryId() + ": " + reason);
        if (p.role() != Role.NGO) notifications.toNgo(d.ngoId(), "DELIVERY", "Delivery cancelled", d.meals() + " meals from " + d.donorName() + " will not arrive: " + reason);
        if (p.role() != Role.DONOR) notifications.toDonor(d.donorName(), "DELIVERY", "Delivery cancelled", d.meals() + " meals of " + d.batchId() + " could not be delivered: " + reason + " They are available for re-allocation.");
        audit.record(p.username(), p.role().name(), "REPORT_DELIVERY", d.deliveryId() + ": " + reason);
        return u;
    }
}
