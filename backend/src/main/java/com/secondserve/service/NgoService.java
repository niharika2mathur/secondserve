package com.secondserve.service;

import com.secondserve.exception.ConflictException;
import com.secondserve.exception.NotFoundException;
import com.secondserve.model.Ngo;
import com.secondserve.repository.NgoRepository;
import com.secondserve.repository.TravelRepository;
import com.secondserve.util.Fields;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class NgoService {
    private static final Pattern ID = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{1,31}$");
    private final NgoRepository repo;
    private final TravelRepository travel;

    public NgoService(NgoRepository repo, TravelRepository travel) { this.repo = repo; this.travel = travel; }

    public List<Ngo> list() { return repo.findAll(); }

    private Ngo parse(Map<String, Object> body, String forcedId) {
        Fields f = new Fields(body);
        String id = forcedId;
        if (id == null) {
            id = DonationService.normalizeId(f.str("ngoId", "NGO ID", true, 40));
            if (id != null && !ID.matcher(id).matches()) f.error("NGO ID must be 2-32 letters, digits, - or _ (e.g. N-01)");
        }
        String name = f.str("name", "Organisation name", true, 80);
        Integer req = f.integer("requiredMeals", "Required meal packs", 1, 1_000_000);
        Integer pick = f.integer("pickupCapacity", "Maximum pickup capacity", 1, 1_000_000);
        Integer travelMax = f.integer("maxTravelMinutes", "Maximum acceptable travel time (minutes)", 1, 1440);
        String contact = f.str("contact", "Contact information", false, 100);
        f.throwIfInvalid();
        return new Ngo(id, name, req, pick, travelMax, contact == null ? "" : contact);
    }

    public Ngo create(Map<String, Object> body) {
        Ngo n = parse(body, null);
        if (repo.exists(n.ngoId())) throw new ConflictException("NGO ID " + n.ngoId() + " already exists");
        repo.save(n);
        return n;
    }

    public Ngo update(String rawId, Map<String, Object> body) {
        String id = DonationService.normalizeId(rawId);
        if (!repo.exists(id)) throw new NotFoundException("NGO " + id + " does not exist");
        Ngo n = parse(body, id);
        repo.save(n);
        return n;
    }

    public void delete(String rawId) {
        String id = DonationService.normalizeId(rawId);
        if (!repo.delete(id)) throw new NotFoundException("NGO " + id + " does not exist");
        travel.removeNgo(id);
    }
}
