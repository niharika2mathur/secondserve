package com.secondserve.service;

import com.secondserve.exception.ValidationException;
import com.secondserve.model.Donation;
import com.secondserve.model.Ngo;
import com.secondserve.repository.DonationRepository;
import com.secondserve.repository.NgoRepository;
import com.secondserve.repository.TravelRepository;

import java.util.*;

public final class TravelService {
    public record Axis(String id, String label) {}
    public record TravelView(List<Axis> donations, List<Axis> ngos, Map<String, Integer> minutes) {}

    private final DonationRepository donations;
    private final NgoRepository ngos;
    private final TravelRepository travel;

    public TravelService(DonationRepository d, NgoRepository n, TravelRepository t) { donations = d; ngos = n; travel = t; }

    public TravelView view() {
        List<Axis> d = new ArrayList<>(), n = new ArrayList<>();
        for (Donation x : donations.findAll()) d.add(new Axis(x.batchId(), x.donorName()));
        for (Ngo x : ngos.findAll()) n.add(new Axis(x.ngoId(), x.name()));
        return new TravelView(d, n, travel.snapshot());
    }

    /** Body: {"entries":[{"donationId":"BATCH-101","ngoId":"N-01","minutes":20}, ...]}; null/blank minutes clears the entry. */
    @SuppressWarnings("unchecked")
    public TravelView update(Map<String, Object> body) {
        if (body == null || !(body.get("entries") instanceof List<?> list)) throw new ValidationException("Body must contain an entries list");
        List<String> errors = new ArrayList<>();
        List<Object[]> ops = new ArrayList<>();
        int idx = 0;
        for (Object o : list) {
            idx++;
            if (!(o instanceof Map<?, ?> m)) { errors.add("Entry " + idx + " is not an object"); continue; }
            String d = DonationService.normalizeId(String.valueOf(m.get("donationId")));
            String n = DonationService.normalizeId(String.valueOf(m.get("ngoId")));
            if (!donations.exists(d)) { errors.add("Entry " + idx + ": unknown donation " + d); continue; }
            if (!ngos.exists(n)) { errors.add("Entry " + idx + ": unknown NGO " + n); continue; }
            Object mv = m.get("minutes");
            if (mv == null || String.valueOf(mv).isBlank()) { ops.add(new Object[]{d, n, null}); continue; }
            try {
                double v = mv instanceof Number num ? num.doubleValue() : Double.parseDouble(String.valueOf(mv).trim());
                if (v != Math.rint(v) || v < 1 || v > 1440) errors.add("Entry " + idx + " (" + d + " -> " + n + "): travel time must be a whole number of minutes between 1 and 1440");
                else ops.add(new Object[]{d, n, (int) v});
            } catch (NumberFormatException e) { errors.add("Entry " + idx + " (" + d + " -> " + n + "): travel time must be a number"); }
        }
        if (!errors.isEmpty()) throw new ValidationException("Travel times were not saved", errors);
        for (Object[] op : ops) { if (op[2] == null) travel.remove((String) op[0], (String) op[1]); else travel.put((String) op[0], (String) op[1], (Integer) op[2]); }
        return view();
    }
}
