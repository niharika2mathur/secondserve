package com.secondserve.service;

import com.secondserve.exception.ConflictException;
import com.secondserve.exception.ForbiddenException;
import com.secondserve.exception.NotFoundException;
import com.secondserve.model.Donation;
import com.secondserve.model.Ngo;
import com.secondserve.model.Principal;
import com.secondserve.repository.DeliveryRepository;
import com.secondserve.repository.DonationRepository;
import com.secondserve.repository.NgoRepository;
import com.secondserve.repository.TravelRepository;

import java.util.*;

/**
 * Self-service for donors (their own batches) and NGOs (their own needs). Ownership is enforced here on every call:
 * the donor name / NGO id always comes from the authenticated principal, never from the request body.
 */
public final class PortalService {
    public record DonorCreateResult(Donation donation, DuplicateScreen.Result screening, String routesInheritedFrom, int routesInherited) {}

    private final DonationService donationService;
    private final NgoService ngoService;
    private final DonationRepository donations;
    private final NgoRepository ngos;
    private final TravelRepository travel;
    private final DeliveryRepository deliveries;
    private final AuditService audit;
    private final NotificationService notifications;

    public PortalService(DonationService ds, NgoService ns, DonationRepository d, NgoRepository n, TravelRepository t, DeliveryRepository dl, AuditService a, NotificationService nt) {
        donationService = ds; ngoService = ns; donations = d; ngos = n; travel = t; deliveries = dl; audit = a; notifications = nt;
    }

    private static String org(Principal p) {
        if (p.orgName() == null || p.orgName().isBlank()) throw new ForbiddenException("Your account is not linked to a donor organisation.");
        return p.orgName();
    }

    private Donation owned(Principal p, String rawId) {
        String id = DonationService.normalizeId(rawId);
        Donation d = donations.find(id).orElseThrow(() -> new NotFoundException("Donation batch " + id + " does not exist"));
        if (!d.donorName().equalsIgnoreCase(org(p))) throw new ForbiddenException("Batch " + id + " belongs to another donor.");
        return d;
    }

    private void requireUnlocked(Donation d, String verb) {
        if (deliveries.heldByBatch().getOrDefault(d.batchId(), 0) > 0)
            throw new ConflictException("Batch " + d.batchId() + " already has meals in a dispatch plan, so it can no longer be " + verb + ". Ask the coordinator to cancel the delivery first.");
    }

    // ------------------------------------------------------------------ donors
    public List<Donation> myDonations(Principal p) {
        String o = org(p);
        return donations.findAll().stream().filter(d -> d.donorName().equalsIgnoreCase(o)).toList();
    }

    public DonorCreateResult createDonation(Principal p, Map<String, Object> body) {
        Map<String, Object> m = new HashMap<>(body == null ? Map.of() : body);
        m.put("donorName", org(p));
        DonationService.CreateResult r = donationService.create(m);
        // The donor's kitchen does not move: reuse the route times entered for its most recent earlier batch.
        String from = null; int copied = 0;
        List<Donation> mine = new ArrayList<>(myDonations(p));
        Collections.reverse(mine);
        for (Donation prev : mine) {
            if (prev.batchId().equals(r.donation().batchId())) continue;
            Map<String, Integer> routes = travel.forDonation(prev.batchId());
            if (routes.isEmpty()) continue;
            for (Map.Entry<String, Integer> e : routes.entrySet()) if (ngos.exists(e.getKey())) { travel.put(r.donation().batchId(), e.getKey(), e.getValue()); copied++; }
            from = prev.batchId();
            break;
        }
        audit.record(p.username(), "DONOR", "CREATE_BATCH", r.donation().batchId() + " " + r.donation().meals() + " meals");
        notifications.toAdmins("BATCH", "New donation batch", org(p) + " registered " + r.donation().batchId() + " (" + r.donation().meals() + " meals).");
        return new DonorCreateResult(r.donation(), r.screening(), from, copied);
    }

    public Donation updateDonation(Principal p, String id, Map<String, Object> body) {
        Donation d = owned(p, id);
        requireUnlocked(d, "edited");
        Map<String, Object> m = new HashMap<>(body == null ? Map.of() : body);
        m.put("donorName", org(p));
        Donation u = donationService.update(d.batchId(), m);
        audit.record(p.username(), "DONOR", "UPDATE_BATCH", u.batchId());
        return u;
    }

    public void deleteDonation(Principal p, String id) {
        Donation d = owned(p, id);
        requireUnlocked(d, "deleted");
        donationService.delete(d.batchId());
        audit.record(p.username(), "DONOR", "DELETE_BATCH", d.batchId());
    }

    // -------------------------------------------------------------------- NGOs
    public Ngo myNgo(Principal p) {
        if (p.ngoId() == null) throw new ForbiddenException("Your account is not linked to an NGO record.");
        return ngos.find(p.ngoId()).orElseThrow(() -> new NotFoundException("Your NGO record no longer exists. Contact the coordinator."));
    }

    public Ngo updateMyNgo(Principal p, Map<String, Object> body) {
        Ngo n = myNgo(p);
        Map<String, Object> m = new HashMap<>(body == null ? Map.of() : body);
        m.put("name", n.name());          // the organisation name is fixed; only needs can be edited
        Ngo u = ngoService.update(n.ngoId(), m);
        audit.record(p.username(), "NGO", "UPDATE_NEEDS", u.ngoId() + ": need " + u.requiredMeals() + ", pickup " + u.pickupCapacity() + ", travel limit " + u.maxTravelMinutes() + " min");
        return u;
    }
}
