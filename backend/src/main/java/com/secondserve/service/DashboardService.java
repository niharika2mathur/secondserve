package com.secondserve.service;

import com.secondserve.dto.Dtos.*;
import com.secondserve.model.Donation;
import com.secondserve.model.Ngo;
import com.secondserve.repository.DonationRepository;
import com.secondserve.repository.NgoRepository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

public final class DashboardService {
    public static final int APPROACHING_MINUTES = 90;
    private final DonationRepository donations;
    private final NgoRepository ngos;
    private final AllocationService allocation;
    private final BloomService bloom;
    private final DuplicateScreen screening;

    public DashboardService(DonationRepository d, NgoRepository n, AllocationService a, BloomService b, DuplicateScreen s) {
        donations = d; ngos = n; allocation = a; bloom = b; screening = s;
    }

    public Dashboard build() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        List<Donation> ds = donations.findAll();
        List<Ngo> ns = ngos.findAll();
        AllocationResult last = allocation.lastOrNull();
        LocalDateTime ref = now;
        Map<String, Integer> bAlloc = new HashMap<>(), nAlloc = new HashMap<>();
        if (last != null) {
            ref = LocalDateTime.parse(last.planningTime());
            last.batches().forEach(b -> bAlloc.put(b.batchId(), b.allocated()));
            last.ngos().forEach(n -> nAlloc.put(n.ngoId(), n.allocated()));
        }
        int total = 0, expired = 0, approaching = 0, demand = 0, eff = 0;
        Set<String> donors = new HashSet<>();
        List<BatchPoint> bp = new ArrayList<>();
        for (Donation d : ds) {
            total += d.meals();
            donors.add(d.donorName().toLowerCase(Locale.ROOT));
            long left = Duration.between(ref, d.safeUntil()).toMinutes();
            if (left <= 0) expired += d.meals(); else if (left <= APPROACHING_MINUTES) approaching++;
            bp.add(new BatchPoint(d.batchId(), d.donorName(), d.meals(), bAlloc.getOrDefault(d.batchId(), 0), left));
        }
        List<NgoPoint> np = new ArrayList<>();
        for (Ngo n : ns) {
            demand += n.requiredMeals(); eff += n.effectiveCapacity();
            np.add(new NgoPoint(n.ngoId(), n.name(), n.requiredMeals(), n.pickupCapacity(), nAlloc.getOrDefault(n.ngoId(), 0)));
        }
        Integer allocated = last == null ? null : last.summary().allocated();
        Integer unalloc = last == null ? null : last.summary().remainingSurplus();
        DuplicateScreen.Stats st = screening.stats();
        return new Dashboard(total, expired, demand, eff, allocated, unalloc, donors.size(), ds.size(), ns.size(), approaching, APPROACHING_MINUTES,
                st.confirmedDuplicates(), st.falsePositives(), last != null, last != null && last.stale(),
                last == null ? null : last.planningTime(), LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS).toString(), now.toString(), bp, np, bloom.stats());
    }
}
